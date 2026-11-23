#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
sim=$(xcrun simctl list devices available -j | python3 -c '
import json,sys
devices=json.load(sys.stdin)["devices"]
for runtime in reversed(sorted(k for k in devices if ".iOS-" in k)):
    phones=[d for d in devices[runtime] if d["name"].startswith("iPhone")]
    if phones:
        print(phones[0]["udid"])
        break')
test -n "$sim"
xcrun simctl boot "$sim" || xcrun simctl list devices booted | grep -F "$sim"
xcrun simctl bootstatus "$sim" -b
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
<key>UILaunchScreen</key><dict/>
</dict></plist>
PLIST
xcrun --sdk iphonesimulator swiftc -sdk "$(xcrun --sdk iphonesimulator --show-sdk-path)" \
  -target "$(uname -m)-apple-ios18.0-simulator" main.swift -o "$app/Startup"
codesign --force --sign - "$app"
xcrun simctl install "$sim" "$app"
SIMCTL_CHILD_AILA_TEST_CASE="${2:-normal}" SIMCTL_CHILD_AILA_URL="$1" xcrun simctl launch "$sim" "$id"
data=$(xcrun simctl get_app_container "$sim" "$id" data)
for i in {1..100}; do
  if [[ -f "$data/Documents/result" ]]; then
    cat "$data/Documents/result"
    grep -qx PASS "$data/Documents/result"
    exit
  fi
  sleep 1
done
exit 1
