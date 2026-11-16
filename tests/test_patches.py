"""Apply the whole patch series to untouched upstream files, including new files."""
from pathlib import Path
import re
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "upstream/mina-rust"


class PatchSeriesTest(unittest.TestCase):
    @unittest.skipUnless((SOURCE / ".git").exists(), "Run scripts/prepare-source.py first")
    def test_applies_to_clean_upstream_and_reapplies_in_reverse(self):
        patches = sorted((ROOT / "patches").glob("*.patch"))
        paths = set()
        for patch in patches:
            text = patch.read_text(encoding="utf-8")
            paths.update(re.findall(r"^diff --git a/(\S+) b/\S+$", text, re.MULTILINE))
            # Git-style patches must explicitly identify newly created files.
            for section in text.split("diff --git ")[1:]:
                if "\n--- /dev/null\n" in section:
                    self.assertIn("\nnew file mode 100644\n", section, patch.name)
        self.assertTrue(paths)
        with tempfile.TemporaryDirectory(prefix="mina-patches-") as directory:
            target = Path(directory)
            subprocess.run(["git", "init", "--quiet", directory], check=True)
            for option, value in (("core.autocrlf", "false"), ("core.longpaths", "true")):
                subprocess.run(["git", "-C", directory, "config", option, value], check=True)
            for name in paths:
                original = subprocess.run(["git", "-C", str(SOURCE), "show", f"HEAD:{name}"], capture_output=True)
                if original.returncode == 0:
                    destination = target / name
                    destination.parent.mkdir(parents=True, exist_ok=True)
                    destination.write_bytes(original.stdout)
            for patch in patches:
                for options in (("--check",), (), ("--reverse", "--check")):
                    result = subprocess.run(["git", "-C", directory, "apply", *options, str(patch)], capture_output=True)
                    self.assertEqual(0, result.returncode, f"{patch.name} {options}: {result.stderr.decode(errors='replace')}")
            credentials = target / "crates/cli/src/android_credentials.rs"
            self.assertIn("mot de passe é", credentials.read_text(encoding="utf-8"))
            node = (target / "crates/cli/src/commands/node/mod.rs").read_text(encoding="utf-8")
            self.assertIn('"0.12345678é"', node)


if __name__ == "__main__":
    unittest.main()
