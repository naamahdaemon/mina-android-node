import importlib.util
from pathlib import Path
import struct
import unittest

spec = importlib.util.spec_from_file_location("app_bundle", Path(__file__).resolve().parents[1] / "scripts/verify-app-bundle.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def elf(alignment=16384, address=0, machine=183):
    data = bytearray(120)
    data[:6] = b"\x7fELF\x02\x01"
    struct.pack_into("<H", data, 18, machine)
    struct.pack_into("<Q", data, 32, 64)
    struct.pack_into("<HH", data, 54, 56, 1)
    struct.pack_into("<I", data, 64, 1)
    struct.pack_into("<QQ", data, 72, 0, address)
    struct.pack_into("<Q", data, 112, alignment)
    return data


class BundleAlignmentTest(unittest.TestCase):
    def test_accepts_16k_and_larger(self):
        for alignment in (16384, 65536):
            module.verify_elf(elf(alignment), "node")

    def test_rejects_4k_and_non_power_of_two(self):
        for alignment in (4096, 24576):
            with self.assertRaisesRegex(ValueError, "16 KiB"):
                module.verify_elf(elf(alignment), "node")

    def test_rejects_wrong_architecture_and_truncation(self):
        for data in (elf(machine=62), elf()[:90], b"not an ELF"):
            with self.assertRaises(ValueError):
                module.verify_elf(data, "node")

    def test_rejects_incongruent_mapping(self):
        with self.assertRaisesRegex(ValueError, "16 KiB"):
            module.verify_elf(elf(address=4096), "node")

    def test_requires_load_segment(self):
        data = elf()
        struct.pack_into("<I", data, 64, 2)
        with self.assertRaisesRegex(ValueError, "No ELF LOAD"):
            module.verify_elf(data, "node")
