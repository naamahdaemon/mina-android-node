import importlib.util
from datetime import datetime, timezone, timedelta
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("apk_version", Path(__file__).parents[1] / "scripts/apk-version.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class ApkVersionTest(unittest.TestCase):
    def test_versions_increase_and_exceed_old_run_counters(self):
        now = datetime(2026, 11, 16, 14, 0, tzinfo=timezone.utc)
        code, name = module.apk_version(now)
        self.assertGreater(code, 1_000_000)
        self.assertEqual(name, "0.2.20261116140000")
        self.assertEqual(module.apk_version(now + timedelta(seconds=1))[0], code + 1)

    def test_timezone_does_not_change_version(self):
        now = datetime(2026, 11, 16, 15, 0, tzinfo=timezone(timedelta(hours=1)))
        self.assertEqual(module.apk_version(now), module.apk_version(now.astimezone(timezone.utc)))

    def test_rejects_out_of_range_and_naive_times(self):
        for now in [datetime(2026, 1, 1), datetime(2019, 1, 1, tzinfo=timezone.utc)]:
            with self.assertRaises(ValueError):
                module.apk_version(now)
