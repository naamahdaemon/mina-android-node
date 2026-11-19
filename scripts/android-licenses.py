"""Package license metadata and notices from Gradle's resolved release artifacts."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET
import zipfile
import urllib.request
import urllib.error
import urllib.parse
import time

ROOT = Path(__file__).resolve().parents[1]


def parent_pom(coordinate):
    parts = coordinate.split(":")
    if len(parts) != 3 or any(not re.fullmatch(r"[A-Za-z0-9_.-]+", part) for part in parts):
        raise ValueError("Invalid parent POM coordinate")
    group, artifact, version = parts
    relative = f"{group.replace('.', '/')}/{artifact}/{version}/{artifact}-{version}.pom"
    cache = ROOT / 'android/app/build/reports/licenses/parent-poms' / relative
    if cache.is_file():
        return cache
    failures = []
    # A repository/CDN can briefly return 404 for an existing parent. Retry a
    # bounded number of times; never substitute or omit required license data.
    for attempt in range(3):
        for repository in ('https://repo.maven.apache.org/maven2/', 'https://dl.google.com/dl/android/maven2/'):
            url = repository + relative
            try:
                with urllib.request.urlopen(url, timeout=30) as response:
                    if urllib.parse.urlparse(response.url).hostname not in ('repo.maven.apache.org', 'dl.google.com'):
                        raise ValueError('Unexpected parent POM redirect')
                    data = response.read(1024 * 1024 + 1)
                    if len(data) > 1024 * 1024:
                        raise ValueError('Parent POM too large')
                ET.fromstring(data)  # Do not cache an invalid/partial XML response.
                cache.parent.mkdir(parents=True, exist_ok=True)
                cache.write_bytes(data)
                return cache
            except urllib.error.HTTPError as error:
                if error.code not in (404, 408, 429) and not 500 <= error.code <= 599:
                    raise
                failures.append(f'{url}: HTTP {error.code}')
            except (urllib.error.URLError, TimeoutError) as error:
                failures.append(f'{url}: {type(error).__name__}')
        if attempt < 2:
            time.sleep(2 ** attempt)
    raise ValueError(f'Parent POM unavailable after 3 attempts: {coordinate}; ' + '; '.join(failures))


def pom_licenses(pom, resolver=parent_pom, depth=0):
    if depth > 8:
        raise ValueError('Parent POM inheritance exceeds limit')
    ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
    def value(element, name):
        return (element.findtext('m:' + name, namespaces=ns) or '').strip()
    licenses = [{'name': value(item, 'name'), 'url': value(item, 'url')}
                for item in pom.findall('m:licenses/m:license', ns)]
    if licenses:
        return licenses
    parent = pom.find('m:parent', ns)
    if parent is None:
        return []
    coordinate = ':'.join(value(parent, name) for name in ('groupId', 'artifactId', 'version'))
    return pom_licenses(ET.parse(resolver(coordinate)).getroot(), resolver, depth + 1)


def artifact_notices(path):
    found = []
    def inspect(data, prefix, nested=True):
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            for entry in archive.infolist():
                if entry.is_dir():
                    continue
                name = entry.filename
                base = name.rsplit('/', 1)[-1].lower()
                if re.match(r'^(license|notice|copying|copyright)(?:[._-].*)?$', base):
                    if entry.file_size > 2 * 1024 * 1024:
                        raise ValueError(f'Unexpectedly large notice: {prefix}/{name}')
                    text = archive.read(entry).decode('utf-8-sig').strip()
                    if text:
                        found.append({'path': f'{prefix}/{name}', 'text': text})
                elif nested and name == 'classes.jar':
                    inspect(archive.read(entry), f'{prefix}/{name}', False)
    inspect(path.read_bytes(), path.name)
    return found


def generate(manifest, output, apache_license):
    rows = json.loads(manifest.read_text(encoding='utf-8-sig'))
    if not rows:
        raise ValueError('Empty Android runtime inventory')
    inventory = []
    notices = ['ANDROID APPLICATION THIRD-PARTY NOTICES',
               'Generated from resolved releaseRuntimeClasspath artifacts and Maven POM metadata.',
               'Rust, embedded circuits and dashboard notices are supplied separately under assets/node/.',
               'Apache License 2.0 (full text):\n\n' + apache_license.read_text(encoding='utf-8-sig')]
    for row in sorted(rows, key=lambda item: item['coordinate']):
        pom = ET.parse(row['pom']).getroot()
        ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
        def value(element, name):
            return (element.findtext('m:' + name, namespaces=ns) or '').strip()
        licenses = pom_licenses(pom)
        if not licenses or any(not item['name'] for item in licenses):
            raise ValueError(f"Missing license metadata: {row['coordinate']}")
        # Current runtime dependencies are Apache 2.0. Fail closed when a new license
        # needs its own full text/review instead of silently shipping an incomplete bundle.
        for item in licenses:
            if not (re.search(r'apache.*2(?:\.0)?', item['name'], re.I)
                    or re.search(r'apache\.org/licenses/LICENSE-2\.0', item['url'], re.I)):
                raise ValueError(f"Review and add license text for {row['coordinate']}: {item['name']}")
        artifacts = []
        embedded = []
        for name in row['artifacts']:
            path = Path(name)
            artifacts.append({'name': path.name, 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()})
            embedded.extend(artifact_notices(path))
        owners = [value(item, 'name') for item in pom.findall('m:developers/m:developer', ns)]
        organization = pom.find('m:organization', ns)
        if organization is not None and value(organization, 'name'):
            owners.append(value(organization, 'name'))
        record = {'coordinate': row['coordinate'], 'name': value(pom, 'name'),
                  'project_url': value(pom, 'url'), 'licenses': licenses,
                  'attribution': sorted(set(filter(None, owners))), 'artifacts': artifacts,
                  'embedded_notices': [item['path'] for item in embedded]}
        inventory.append(record)
        notices.append('\n=== ' + row['coordinate'] + ' ===\n' + json.dumps(record, indent=2, ensure_ascii=False))
        for item in embedded:
            notices.append('\n--- ' + item['path'] + ' ---\n' + item['text'])
    output.mkdir(parents=True, exist_ok=True)
    (output / 'ANDROID-DEPENDENCIES.json').write_text(json.dumps(inventory, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    (output / 'ANDROID-THIRD-PARTY-NOTICES.txt').write_text('\n\n'.join(notices) + '\n', encoding='utf-8')
    print(f'Inventoried {len(inventory)} Android runtime components with full license text and embedded notices')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('manifest', type=Path)
    parser.add_argument('--output', type=Path, default=ROOT / 'android/app/src/main/assets/app-licenses')
    args = parser.parse_args()
    generate(args.manifest, args.output, ROOT / 'LICENSE')
    # Ship the same reviewable inventory alongside APK artifacts.
    distribution = ROOT / 'dist'
    distribution.mkdir(exist_ok=True)
    for path in args.output.iterdir():
        if path.is_file():
            (distribution / path.name).write_bytes(path.read_bytes())
