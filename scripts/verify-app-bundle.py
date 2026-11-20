"""Verify bundled native identity and ELF alignment before publishing an AAB."""
import struct
import sys
import zipfile


def verify_elf(data, name):
    if len(data) < 64 or data[:6] != b"\x7fELF\x02\x01":
        raise ValueError(f"Expected little-endian ELF64: {name}")
    if struct.unpack_from("<H", data, 18)[0] != 183:
        raise ValueError(f"Expected ARM64: {name}")
    offset = struct.unpack_from("<Q", data, 32)[0]
    size, count = struct.unpack_from("<HH", data, 54)
    if size < 56 or count == 0 or offset + count * size > len(data):
        raise ValueError(f"Invalid ELF program headers: {name}")
    loads = 0
    for index in range(count):
        header = offset + index * size
        if struct.unpack_from("<I", data, header)[0] == 1:
            loads += 1
            file_offset, address = struct.unpack_from("<QQ", data, header + 8)
            alignment = struct.unpack_from("<Q", data, header + 48)[0]
            if alignment < 16384 or alignment & (alignment - 1) or (file_offset - address) % 16384:
                raise ValueError(f"ELF LOAD is not 16 KiB aligned: {name}")
    if loads == 0:
        raise ValueError(f"No ELF LOAD segments: {name}")


def verify(bundle, apk):
    with zipfile.ZipFile(bundle) as aab, zipfile.ZipFile(apk) as package:
        for name in ("BundleConfig.pb", "base/manifest/AndroidManifest.xml", "base/dex/classes.dex"):
            if aab.getinfo(name).file_size == 0:
                raise ValueError(f"Empty bundle entry: {name}")
        libraries = [n for n in aab.namelist() if n.startswith("base/lib/") and n.endswith(".so")]
        expected = {"base/lib/arm64-v8a/libmina.so", "base/lib/arm64-v8a/libminasupervisor.so"}
        if not expected.issubset(libraries):
            raise ValueError("Missing native node/supervisor")
        apk_libraries = {"base/" + n for n in package.namelist() if n.startswith("lib/") and n.endswith(".so")}
        if set(libraries) != apk_libraries:
            raise ValueError("Bundle/APK library sets differ")
        for name in libraries:
            data = aab.read(name)
            if data != package.read(name.removeprefix("base/")):
                raise ValueError(f"Bundle/APK native mismatch: {name}")
            verify_elf(data, name)
        for name in ("assets/node/LICENSE", "assets/node/THIRD_PARTY_NOTICES", "assets/node/THIRD_PARTY_LICENSES",
                     "assets/node/BUILD-INFO.json", "assets/app-licenses/ANDROID-DEPENDENCIES.json",
                     "assets/app-licenses/ANDROID-THIRD-PARTY-NOTICES.txt"):
            if not aab.read("base/" + name) or aab.read("base/" + name) != package.read(name):
                raise ValueError(f"Bundle/APK license/provenance mismatch: {name}")
    print("AAB structure, identical native executables/licenses and 16 KiB ELF alignment verified")


if __name__ == "__main__":
    verify(*sys.argv[1:])
