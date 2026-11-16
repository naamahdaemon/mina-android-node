"""Validate translation completeness and Android formatting before APK compilation."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "android/app/src/main/res"

def catalog(directory):
    return {item.attrib["name"]: item.text or "" for item in ET.parse(RES / directory / "strings.xml").getroot()}

class LocalizationTest(unittest.TestCase):
    def test_english_and_french_have_matching_keys_and_format_arguments(self):
        english = catalog("values")
        french = catalog("values-fr")
        self.assertEqual(english.keys(), french.keys())
        for key in english:
            self.assertTrue(english[key].strip(), key)
            self.assertTrue(french[key].strip(), key)
            self.assertEqual(sorted(re.findall(r"%\d+\$[a-zA-Z]", english[key])),
                             sorted(re.findall(r"%\d+\$[a-zA-Z]", french[key])), key)
        self.assertEqual("Settings", english["settings"])
        self.assertEqual("Réglages", french["settings"])

    def test_every_string_reference_has_a_default_translation(self):
        defined = catalog("values")
        for source in (ROOT / "android/app/src").rglob("*.kt"):
            for name in re.findall(r"(?<!android\.)\bR\.string\.(\w+)", source.read_text(encoding="utf-8")):
                self.assertIn(name, defined, f"{source.name}: {name}")

    def test_declared_languages_match_supported_translations(self):
        root = ET.parse(RES / "xml/locales_config.xml").getroot()
        tags = [item.attrib["{http://schemas.android.com/apk/res/android}name"] for item in root]
        self.assertEqual(["en", "fr"], tags)

if __name__ == "__main__":
    unittest.main()
