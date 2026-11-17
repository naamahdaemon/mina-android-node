import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

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

    def test_unknown_license_requires_review(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            with self.assertRaisesRegex(ValueError, 'Review and add'):
                licenses.generate(self.fixture(root, 'Unreviewed license'), root / 'output', ROOT / 'LICENSE')


if __name__ == '__main__':
    unittest.main()
