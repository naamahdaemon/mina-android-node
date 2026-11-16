"""CI smoke test: clean Stop and application-death EOF reach the child."""
import select
from pathlib import Path
import subprocess
import sys
import tempfile

supervisor = str(Path(sys.argv[1]).resolve())
with tempfile.TemporaryDirectory() as directory:
    child = Path(directory) / "child.py"
    child.write_text("import signal,time\nsignal.signal(signal.SIGINT, lambda *_: exit(0))\nprint('READY', flush=True)\nwhile True: time.sleep(0.1)\n")
    for eof in (False, True):
        process = subprocess.Popen([supervisor, sys.executable, str(child)],
                                   stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                   stderr=subprocess.PIPE, text=True)
        try:
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
