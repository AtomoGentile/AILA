#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
url="$1"
device_prefix="${2:-iPhone}"

sim=$(xcrun simctl list devices available -j | python3 -c '
import json, sys
prefix = sys.argv[1]
devices = json.load(sys.stdin)["devices"]
for runtime in reversed(sorted(k for k in devices if k.endswith(".iOS-27-1"))):
    matches = [d for d in devices[runtime] if d["name"].startswith(prefix)]
    if matches:
        # Avoid dual-display simulators (e.g. iPhone Duo) when a single-display model exists.
        single = [d for d in matches if "Duo" not in d["name"] and "Fold" not in d["name"]]
        chosen = single[0] if single else matches[0]
        print(chosen["udid"])
        break
' "$device_prefix")
test -n "$sim"
xcrun simctl list devices available 'iOS 27.1' | grep -F "$sim"
xcrun simctl boot "$sim" || xcrun simctl list devices booted | grep -F "$sim"
xcrun simctl bootstatus "$sim" -b

capture_screen() {
  local out="$1"
  xcrun simctl io "$sim" screenshot "$out" 2>/dev/null \
    || xcrun simctl io "$sim" screenshot --display=internal "$out" 2>/dev/null \
    || xcrun simctl io "$sim" screenshot --display=1 "$out"
}

tmp=$(mktemp -d)
app="$tmp/Startup.app"
mkdir -p "$app"
id=dev.aila.StartupCheck
cat >"$app/Info.plist" <<PLIST
<plist version="1.0"><dict>
<key>CFBundleIdentifier</key><string>$id</string>
<key>CFBundleExecutable</key><string>Startup</string>
<key>CFBundleName</key><string>Startup</string>
<key>CFBundlePackageType</key><string>APPL</string>
<key>CFBundleVersion</key><string>1</string>
<key>CFBundleShortVersionString</key><string>1.0</string>
<key>UIApplicationSceneManifest</key><dict>
<key>UIApplicationSupportsMultipleScenes</key><false/>
<key>UISceneConfigurations</key><dict><key>UIWindowSceneSessionRoleApplication</key><array><dict>
<key>UISceneConfigurationName</key><string>Startup</string>
</dict></array></dict></dict>
<key>UILaunchScreen</key><dict/>
</dict></plist>
PLIST
xcrun --sdk iphonesimulator swiftc -sdk "$(xcrun --sdk iphonesimulator --show-sdk-path)" \
  -target "$(uname -m)-apple-ios18.0-simulator" main.swift -o "$app/Startup"
codesign --force --sign - "$app"
xcrun simctl install "$sim" "$app"
SIMCTL_CHILD_AILA_URL="$url" xcrun simctl launch --stdout="$tmp/stdout" --stderr="$tmp/stderr" "$sim" "$id"
data=$(xcrun simctl get_app_container "$sim" "$id" data)
for i in {1..200}; do
  if [[ -f "$data/Documents/result" ]]; then
    cat "$data/Documents/result"
    if ! grep -qx PASS "$data/Documents/result"; then
      capture_screen "$GITHUB_WORKSPACE/screen-fail.png" || true
      exit 1
    fi
    for delay in 2 5; do
      sleep "$delay"
      shot="$GITHUB_WORKSPACE/screen-$delay.png"
      capture_screen "$shot"
      swift visible.swift "$shot"
    done
    exit 0
  fi
  sleep 1
done
capture_screen "$GITHUB_WORKSPACE/screen-timeout.png" || true
cat "$tmp/stdout" "$tmp/stderr" || true
ls -t ~/Library/Logs/DiagnosticReports/Startup* 2>/dev/null | head -2 | while read -r report; do head -c 6000 "$report"; done || true
exit 1
