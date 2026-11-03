"""Validate canonical release tags and write numeric iOS metadata plus the visible release name."""
import argparse
import re
from dataclasses import dataclass
from pathlib import Path

_PATTERN = re.compile(r"v(?P<version>(?P<core>(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)\.(?:0|[1-9]\d*))(?:-beta\.(?P<beta>[1-9]\d*))?)\Z")

@dataclass(frozen=True)
class Release:
    version: str
    core: str
    beta: bool

def parse_release_tag(tag: str) -> Release:
    match = _PATTERN.fullmatch(tag)
    if not match:
        raise ValueError("Tag non valido: usa vX.Y.Z oppure vX.Y.Z-beta.N (N >= 1)")
    return Release(match['version'], match['core'], match['beta'] is not None)

def configure_ios(project: str, release: Release, build: str) -> str:
    if not build.isdecimal() or int(build) <= 0:
        raise ValueError("Numero di build iOS non valido")
    values = {'CFBundleShortVersionString': release.core, 'CFBundleVersion': build,
              'AILAReleaseVersion': release.version, 'AILAUpdateChannel': 'beta' if release.beta else 'stable'}
    for key, value in values.items():
        project, count = re.subn(r'(?m)^(\s*' + key + r':)\s*"[^"]*"$', lambda m: f'{m[1]} "{value}"', project)
        if count != 1:
            raise ValueError(f"Metadato iOS assente o duplicato: {key}")
    return project

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--tag', required=True)
    parser.add_argument('--ios-project')
    parser.add_argument('--build')
    parser.add_argument('--github-output')
    args = parser.parse_args()
    release = parse_release_tag(args.tag)
    if args.ios_project:
        path = Path(args.ios_project)
        path.write_text(configure_ios(path.read_text(), release, args.build or ''))
    if args.github_output:
        with open(args.github_output, 'a') as output:
            output.write(f'prerelease={str(release.beta).lower()}\nversion={release.version}\n')
