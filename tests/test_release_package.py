import importlib.util
from pathlib import Path
import tempfile
import unittest
import io
import tarfile
import zipfile

spec = importlib.util.spec_from_file_location('release', Path(__file__).parents[1] / 'scripts/prepare-release.py')
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class ReleaseTests(unittest.TestCase):
    def test_manifests_reject_corruption_missing_and_unexpected_files(self):
        good = f'{release.digest(b"ok")}  mina\n'
        release.verify_checksums(good, lambda _: b'ok', {'mina'})
        for text, data in [(good, b'bad'), ('', b'ok'), (good + good, b'ok'),
                           (good.replace('mina', '../mina'), b'ok')]:
            with self.assertRaises(ValueError):
                release.verify_checksums(text, lambda _: data, {'mina'})

    def fixture(self, root, apk_binary=b'native'):
        app, native = root / 'artifacts/apk', root / 'artifacts/native'
        app.mkdir(parents=True)
        native.mkdir(parents=True)
        (root / 'LICENSE').write_text('Apache-2.0')
        files = {'mina': b'native', 'frontier-lock-tests': b'test', 'run-termux.sh': b'launch',
                 'BUILD-INFO.json': b'{"revision":"upstream"}', 'LICENSE': b'license',
                 'THIRD_PARTY_NOTICES': b'notices', 'THIRD_PARTY_LICENSES': b'licenses'}
        files['SHA256SUMS'] = ''.join(f'{release.digest(files[n])}  {n}\n' for n in
            ('mina', 'frontier-lock-tests', 'run-termux.sh', 'BUILD-INFO.json')).encode()
        archive_path = native / 'mina-android-arm64.tar.gz'
        with tarfile.open(archive_path, 'w:gz') as archive:
            for name, data in files.items():
                member = tarfile.TarInfo('mina-android-arm64/' + name)
                member.size = len(data)
                archive.addfile(member, io.BytesIO(data))
        (native / 'SHA256SUMS').write_text(f'{release.digest(archive_path.read_bytes())}  {archive_path.name}\n')
        apk_path = app / 'mina-android-release.apk'
        with zipfile.ZipFile(apk_path, 'w') as apk:
            apk.writestr('lib/arm64-v8a/libmina.so', apk_binary)
            apk.writestr('assets/node/BUILD-INFO.json', files['BUILD-INFO.json'])
            apk.writestr('assets/app-licenses/ANDROID-DEPENDENCIES.json', '[]')
            apk.writestr('assets/app-licenses/ANDROID-THIRD-PARTY-NOTICES.txt', 'License notices')
        (app / 'APK-SHA256SUMS').write_text(f'{release.digest(apk_path.read_bytes())}  {apk_path.name}\n')
        (app / 'APK-BUILD-INFO.txt').write_text('Build type: release\nDebuggable: false\nApp source: appsha\n')

    def test_release_has_both_binaries_and_verifiable_checksums(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self.fixture(root)
            release.prepare(root, 'appsha', 'nativesha', 'v0.3.0', '1', '2')
            output = root / 'release'
            required = {p.name for p in output.iterdir()} - {'SHA256SUMS', 'RELEASE-NOTES.md'}
            release.verify_checksums((output / 'SHA256SUMS').read_text(),
                                     lambda n: (output / n).read_bytes(), required)
            self.assertEqual((output / 'mina-android-arm64').read_bytes(), b'native')
            self.assertTrue((output / 'mina-android.apk').exists())

    def test_different_embedded_binary_is_rejected_before_release_assets(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self.fixture(root, apk_binary=b'other')
            with self.assertRaisesRegex(ValueError, 'mismatch'):
                release.prepare(root, 'appsha', 'nativesha', 'v0.3.0', '1', '2')
            self.assertFalse((root / 'release').exists())

    def test_bundle_checksum_is_verified(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self.fixture(root)
            bundle = root / 'artifacts/bundle'
            bundle.mkdir()
            (bundle / 'mina-android-release.aab').write_bytes(b'corrupt')
            (bundle / 'AAB-SHA256SUMS').write_text(f'{release.digest(b"original")}  mina-android-release.aab\n')
            with self.assertRaisesRegex(ValueError, 'Checksum mismatch'):
                release.prepare(root, 'appsha', 'nativesha', 'v0.3.0', '1', '2')

    def test_bundle_cannot_come_from_another_source(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self.fixture(root)
            bundle = root / 'artifacts/bundle'
            bundle.mkdir()
            data = b'bundle'
            (bundle / 'mina-android-release.aab').write_bytes(data)
            (bundle / 'AAB-SHA256SUMS').write_text(f'{release.digest(data)}  mina-android-release.aab\n')
            (bundle / 'AAB-BUILD-INFO.txt').write_text('App source: wrong\n')
            with self.assertRaisesRegex(ValueError, 'source or version mismatch'):
                release.prepare(root, 'appsha', 'nativesha', 'v0.3.0', '1', '2')

    def test_incorrect_app_source_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self.fixture(root)
            with self.assertRaisesRegex(ValueError, 'metadata'):
                release.prepare(root, 'wrong', 'nativesha', 'v0.3.0', '1', '2')


if __name__ == '__main__':
    unittest.main()
