import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
import zipfile
from unittest.mock import patch
from urllib.error import HTTPError, URLError

ROOT = Path(__file__).resolve().parents[1]
def load(name):
    spec = importlib.util.spec_from_file_location(name, ROOT / 'scripts' / (name + '.py'))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module
licenses = load('android-licenses')
verify_apk = load('verify-apk-release')


class DistributionTests(unittest.TestCase):
    def test_rejects_debuggable_and_wrong_package(self):
        good = "package: name='org.minanode.android'\nnative-code: 'arm64-v8a'\n"
        verify_apk.verify(good)
        for text in [good + 'application-debuggable\n', good.replace('org.minanode.android', 'other'), good.replace('arm64-v8a', 'x86')]:
            with self.assertRaises(ValueError):
                verify_apk.verify(text)

    def fixture(self, root, license_name='Apache 2.0'):
        pom = root / 'library.pom'
        pom.write_text(f'<project xmlns="http://maven.apache.org/POM/4.0.0"><name>Library</name><licenses><license><name>{license_name}</name></license></licenses></project>')
        jar = io.BytesIO()
        with zipfile.ZipFile(jar, 'w') as archive:
            archive.writestr('META-INF/NOTICE', 'Copyright example owner')
        aar = root / 'library.aar'
        with zipfile.ZipFile(aar, 'w') as archive:
            archive.writestr('classes.jar', jar.getvalue())
        manifest = root / 'manifest.json'
        manifest.write_text(json.dumps([dict(coordinate='org.example:library:1', pom=str(pom), artifacts=[str(aar)])]))
        return manifest

    def test_preserves_nested_notice_and_records_exact_artifact(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            manifest = self.fixture(root)
            licenses.generate(manifest, root / 'output', ROOT / 'LICENSE')
            text = (root / 'output/ANDROID-THIRD-PARTY-NOTICES.txt').read_text(encoding='utf-8')
            self.assertIn('Copyright example owner', text)
            self.assertIn('TERMS AND CONDITIONS', text)
            inventory = json.loads((root / 'output/ANDROID-DEPENDENCIES.json').read_text())
            self.assertEqual(inventory[0]['coordinate'], 'org.example:library:1')
            self.assertEqual(len(inventory[0]['artifacts'][0]['sha256']), 64)

    def test_inherits_license_from_parent_pom(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            manifest = self.fixture(root)
            parent = root / 'library.pom'
            child = licenses.ET.fromstring('<project xmlns="http://maven.apache.org/POM/4.0.0"><parent><groupId>org.example</groupId><artifactId>parent</artifactId><version>1</version></parent></project>')
            resolved = []
            def resolver(coordinate):
                resolved.append(coordinate)
                return parent
            result = licenses.pom_licenses(child, resolver)
            self.assertEqual(result[0]['name'], 'Apache 2.0')
            self.assertEqual(resolved, ['org.example:parent:1'])

    def test_parent_download_retries_temporary_404_and_caches_success(self):
        url = 'https://repo.maven.apache.org/maven2/example/parent/1/parent-1.pom'
        response = io.BytesIO(b'<project/>')
        response.url = url
        with tempfile.TemporaryDirectory() as temp, patch.object(licenses, 'ROOT', Path(temp)), \
                patch.object(licenses.urllib.request, 'urlopen', side_effect=[
                    HTTPError(url, 404, 'temporary', {}, None), HTTPError(url, 404, 'missing', {}, None), response]) as fetch, \
                patch.object(licenses.time, 'sleep'):
            result = licenses.parent_pom('example:parent:1')
            self.assertEqual(result.read_bytes(), b'<project/>')
            self.assertEqual(fetch.call_count, 3)
            self.assertEqual(licenses.parent_pom('example:parent:1'), result)
            self.assertEqual(fetch.call_count, 3)

    def test_parent_download_still_fails_closed_when_missing(self):
        with tempfile.TemporaryDirectory() as temp, patch.object(licenses, 'ROOT', Path(temp)), \
                patch.object(licenses.urllib.request, 'urlopen', side_effect=HTTPError('https://repo.maven.apache.org/', 404, 'missing', {}, None)) as fetch, \
                patch.object(licenses.time, 'sleep'):
            with self.assertRaisesRegex(ValueError, 'unavailable after 3 attempts'):
                licenses.parent_pom('example:parent:1')
            self.assertEqual(fetch.call_count, 6)
            self.assertFalse(list(Path(temp).rglob('*.pom')))

    def test_parent_download_retries_connection_failures(self):
        response = io.BytesIO(b'<project/>')
        response.url = 'https://repo.maven.apache.org/example.pom'
        with tempfile.TemporaryDirectory() as temp, patch.object(licenses, 'ROOT', Path(temp)), \
                patch.object(licenses.urllib.request, 'urlopen', side_effect=[URLError('temporary'), response]), \
                patch.object(licenses.time, 'sleep'):
            self.assertEqual(licenses.parent_pom('example:parent:1').read_bytes(), b'<project/>')

    def test_parent_download_does_not_cache_invalid_xml_or_follow_untrusted_host(self):
        for data, url in [(b'<broken', 'https://repo.maven.apache.org/example.pom'),
                          (b'<project/>', 'https://untrusted.example/example.pom')]:
            response = io.BytesIO(data); response.url = url
            with tempfile.TemporaryDirectory() as temp, patch.object(licenses, 'ROOT', Path(temp)), \
                    patch.object(licenses.urllib.request, 'urlopen', return_value=response):
                with self.assertRaises((ValueError, licenses.ET.ParseError)):
                    licenses.parent_pom('example:parent:1')
                self.assertFalse(list(Path(temp).rglob('*.pom')))

    def test_pinned_guava_parent_is_verified_and_works_without_network(self):
        with patch.object(licenses.urllib.request, 'urlopen', side_effect=AssertionError('Network should not be used')):
            path = licenses.parent_pom('com.google.guava:guava-parent:26.0-android')
            metadata = licenses.pom_licenses(licenses.ET.parse(path).getroot())
            self.assertIn('Apache', metadata[0]['name'])

    def test_pinned_parent_tampering_fails_closed(self):
        with tempfile.TemporaryDirectory() as temp, patch.object(licenses, 'ROOT', Path(temp)):
            directory = Path(temp) / 'docs/licenses/parent-poms'
            directory.mkdir(parents=True)
            (directory / 'parent.pom').write_bytes(b'<project/>')
            (directory / 'manifest.json').write_text(json.dumps({'example:parent:1': {
                'file': 'parent.pom', 'sha256': '0' * 64}}))
            with self.assertRaisesRegex(ValueError, 'checksum mismatch'):
                licenses.parent_pom('example:parent:1')

    def test_verified_gradle_parent_cache_avoids_network(self):
        data = b'<project/>'
        with tempfile.TemporaryDirectory() as temp, patch.object(licenses, 'ROOT', Path(temp)), \
                patch.dict(licenses.os.environ, {'GRADLE_USER_HOME': temp}), \
                patch.object(licenses.urllib.request, 'urlopen', side_effect=AssertionError('Network should not be used')):
            path = Path(temp) / 'caches/modules-2/files-2.1/example/parent/1' / licenses.hashlib.sha1(data).hexdigest() / 'parent-1.pom'
            path.parent.mkdir(parents=True); path.write_bytes(data)
            self.assertEqual(licenses.parent_pom('example:parent:1'), path)
            path.write_bytes(b'<changed/>')
            with self.assertRaisesRegex(ValueError, 'checksum mismatch'):
                licenses.parent_pom('example:parent:1')

    def test_unknown_license_requires_review(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            with self.assertRaisesRegex(ValueError, 'Review and add'):
                licenses.generate(self.fixture(root, 'Unreviewed license'), root / 'output', ROOT / 'LICENSE')


if __name__ == '__main__':
    unittest.main()
