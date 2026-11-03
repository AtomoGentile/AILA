"""Only complete, correctly marked stable releases may enter the stable SideStore source."""
import json
import sys
from prepare_release import parse_release_tag

def select_release(payload):
    candidates = payload if isinstance(payload, list) else [payload]
    valid = []
    for release in candidates:
        if not isinstance(release, dict) or release.get('draft') is not False or release.get('prerelease') is not False:
            continue
        try:
            version = parse_release_tag(release.get('tag_name', ''))
        except (ValueError, TypeError):
            continue
        if version.beta:
            continue
        if not any(a.get('name') == 'AILA.ipa' and a.get('state') == 'uploaded' and
                   str(a.get('browser_download_url', '')).startswith('https://')
                   for a in release.get('assets', [])):
            continue
        valid.append((tuple(map(int, version.core.split('.'))), release))
    return max(valid, key=lambda candidate: candidate[0])[1] if valid else None

def validate_ios_metadata(metadata, tag):
    release = parse_release_tag(tag)
    if release.beta or metadata.get('CFBundleShortVersionString') != release.core:
        raise ValueError("L'IPA non corrisponde alla release stabile selezionata")
    modern = tuple(map(int, release.core.split('.'))) >= (1, 6, 3)
    has_channel = 'AILAReleaseVersion' in metadata or 'AILAUpdateChannel' in metadata
    if (modern or has_channel) and (metadata.get('AILAUpdateChannel') != 'stable' or metadata.get('AILAReleaseVersion') != release.version):
        raise ValueError("L'IPA non dichiara il canale stabile o la versione attesa")

if __name__ == '__main__':
    json.dump(select_release(json.load(sys.stdin)), sys.stdout)
