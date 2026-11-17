"""Verify existing CI artifacts and assemble release assets; never build binaries."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import tarfile
import zipfile


def digest(data):
    return hashlib.sha256(data).hexdigest()


def verify_checksums(text, read, required):
    seen = set()
    for line in text.splitlines():
        match = re.fullmatch(r'([0-9a-f]{64}) [ *]([^\r\n]+)', line)
        if not match:
            raise ValueError('Invalid checksum manifest')
        expected, name = match.groups()
        if name not in required or name in seen:
            raise ValueError(f'Unexpected or duplicate checksum entry: {name}')
        if digest(read(name)) != expected:
            raise ValueError(f'Checksum mismatch: {name}')
        seen.add(name)
    if seen != set(required):
        raise ValueError('Incomplete checksum manifest')


def prepare(root, app_sha, native_sha, tag, apk_run, native_run):
    apk_dir, native_dir = root / 'artifacts/apk', root / 'artifacts/native'
    apk_name = 'mina-android-release.apk'
    archive_name = 'mina-android-arm64.tar.gz'
    verify_checksums((apk_dir / 'APK-SHA256SUMS').read_text(),
                     lambda name: (apk_dir / name).read_bytes(), {apk_name})
    verify_checksums((native_dir / 'SHA256SUMS').read_text(),
                     lambda name: (native_dir / name).read_bytes(), {archive_name})
    info = (apk_dir / 'APK-BUILD-INFO.txt').read_text()
    if not {'Build type: release', 'Debuggable: false'} <= set(info.splitlines()):
        raise ValueError('A verified non-debuggable release APK is required')
    if f'App source: {app_sha}' not in info.splitlines():
        raise ValueError('APK build metadata does not match selected source')
    with tarfile.open(native_dir / archive_name, 'r:gz') as archive:
        prefix = 'mina-android-arm64/'
        def read(name):
            matches = [m for m in archive.getmembers() if m.name == prefix + name]
            if len(matches) != 1 or not matches[0].isfile():
                raise ValueError(f'Missing, duplicate or non-regular archive member: {name}')
            return archive.extractfile(matches[0]).read()
        verify_checksums(read('SHA256SUMS').decode(), read,
                         {'mina', 'frontier-lock-tests', 'run-termux.sh', 'BUILD-INFO.json'})
        binary = read('mina')
        native_info = read('BUILD-INFO.json')
        # Preserve the complete archive (including upstream notices); never extract paths from it.
        for name in ('LICENSE', 'THIRD_PARTY_NOTICES', 'THIRD_PARTY_LICENSES'):
            if not read(name):
                raise ValueError(f'Empty license notice: {name}')
    with zipfile.ZipFile(apk_dir / apk_name) as apk:
        for name, expected in [('lib/arm64-v8a/libmina.so', binary),
                               ('assets/node/BUILD-INFO.json', native_info)]:
            if apk.namelist().count(name) != 1 or digest(apk.read(name)) != digest(expected):
                raise ValueError(f'APK/native package mismatch: {name}')
    output = root / 'release'
    output.mkdir()  # Refuse to mix new assets with a previous attempt.
    shutil.copyfile(apk_dir / apk_name, output / 'mina-android.apk')
    shutil.copyfile(native_dir / archive_name, output / archive_name)
    shutil.copyfile(apk_dir / 'APK-BUILD-INFO.txt', output / 'APK-BUILD-INFO.txt')
    shutil.copyfile(root / 'LICENSE', output / 'LICENSE.txt')
    (output / 'mina-android-arm64').write_bytes(binary)
    (output / 'mina-android-arm64').chmod(0o755)
    for name in ('ANDROID-DEPENDENCIES.json', 'ANDROID-THIRD-PARTY-NOTICES.txt'):
        with zipfile.ZipFile(apk_dir / apk_name) as apk:
            data = apk.read('assets/app-licenses/' + name)
        (output / name).write_bytes(data)
    provenance = dict(tag=tag, app_source=app_sha, native_source=native_sha,
                      apk_run_id=apk_run, native_run_id=native_run,
                      native_binary_sha256=digest(binary), upstream=json.loads(native_info))
    (output / 'RELEASE-INFO.json').write_text(json.dumps(provenance, indent=2) + '\n')
    (output / 'SHA256SUMS').write_text(''.join(
        f'{digest(path.read_bytes())}  {path.name}\n' for path in sorted(output.iterdir())))
    (output / 'RELEASE-NOTES.md').write_text(
        f'Experimental Mina Android Node {tag}\n\n'
        'Android application: Android 13+ / ARM64. Supports mainnet and devnet.\n\n'
        '- `mina-android.apk`: signed application update.\n'
        '- `mina-android-arm64.tar.gz`: complete Termux package with launcher and license notices.\n'
        '- `mina-android-arm64`: standalone executable; prefer the complete archive for Termux.\n'
        '- `SHA256SUMS`: checksums for all attached assets.\n\n'
        'Preserve encrypted key backups. Normal same-signature app updates retain data; '
        'uninstalling removes private app data.\n\n'
        f'App source: `{app_sha}`; native source: `{native_sha}`.\n'
        f'Build runs: APK `{apk_run}`, native `{native_run}`.\n'
        'The APK and standalone package contain the same verified Rust executable.\n')


if __name__ == '__main__':
    prepare(Path('.'), os.environ['APK_SOURCE_SHA'], os.environ['NATIVE_SOURCE_SHA'],
            os.environ['RELEASE_TAG'], os.environ['APK_RUN_ID'], os.environ['NATIVE_RUN_ID'])
