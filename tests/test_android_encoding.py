"""Reject common UTF-8 text accidentally decoded as Windows-1252."""
from pathlib import Path
import unittest


class AndroidEncodingTest(unittest.TestCase):
    def test_android_sources_have_no_mojibake(self):
        root = Path(__file__).resolve().parents[1] / "android/app/src"
        # Use sequences rather than individual letters: French legitimately uses Â.
        markers = ("\u00e2\u20ac", "\u00e2\u02dc", "\u00c2\u00b7", "\u00c3\u00a9", "\ufffd")
        for path in root.rglob("*"):
            if path.suffix not in (".kt", ".xml"):
                continue
            with self.subTest(path=str(path.relative_to(root))):
                text = path.read_text(encoding="utf-8")
                for marker in markers:
                    self.assertNotIn(marker, text)
