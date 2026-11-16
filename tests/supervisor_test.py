"""CI smoke test: clean Stop and application-death EOF reach the child."""
import select
from pathlib import Path
import subprocess
import sys
import tempfile
import struct

supervisor = str(Path(sys.argv[1]).resolve())
with tempfile.TemporaryDirectory() as directory:
    child = Path(directory) / "child.py"
    child.write_text("import signal,time,sys\nassert sys.stdin.buffer.read(4)==bytes(4)\nsignal.signal(signal.SIGINT, lambda *_: sys.exit(0))\nprint('READY', flush=True)\nwhile True: time.sleep(0.1)\n")
    for eof in (False, True):
        process = subprocess.Popen([supervisor, sys.executable, str(child)],
                                   stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                   stderr=subprocess.PIPE, text=True)
        try:
            process.stdin.write("\u0000" * 4)
            process.stdin.flush()
            assert select.select([process.stdout], [], [], 5)[0], "Child did not start"
            assert process.stdout.readline().strip() == "READY"
            if not eof:
                process.stdin.write("stop\n")
                process.stdin.flush()
            process.stdin.close()
            assert process.wait(timeout=10) == 0, process.stderr.read()
        finally:
            if process.poll() is None:
                process.kill()
                process.wait()
print("Supervisor Stop/EOF tests passed")

# Rejected credential input must never start an executable.
for frame in (struct.pack("<I", 16385), b"\x03\x00\x00\x00x", b"\x00"):
    result = subprocess.run([supervisor, "/bin/echo", "UNEXPECTED_START"], input=frame, capture_output=True, timeout=5)
    assert result.returncode == 65, result
    assert b"UNEXPECTED_START" not in result.stdout

with tempfile.TemporaryDirectory() as directory:
    child = Path(directory) / "credentials.py"
    child.write_text("import struct,sys\nn=struct.unpack('<I',sys.stdin.buffer.read(4))[0]\nassert sys.stdin.buffer.read(n).decode()=='  test é  '\nassert sys.stdin.buffer.read()==b''\nprint('FRAME_OK',flush=True)\n")
    payload = "  test é  ".encode()
    process = subprocess.Popen([supervisor, sys.executable, str(child)], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    try:
        process.stdin.write(struct.pack("<I",len(payload))+payload); process.stdin.flush()
        assert process.wait(timeout=5) == 0, process.stderr.read()
        assert process.stdout.read().strip() == b"FRAME_OK"
    finally:
        process.stdin.close()
        if process.poll() is None:
            process.kill(); process.wait()
print("Supervisor credential framing tests passed")
