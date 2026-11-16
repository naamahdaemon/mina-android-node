"""Fetch a pinned upstream and apply Android patches without resetting local edits."""
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
CONFIG = json.loads((ROOT / "upstream.json").read_text())
SOURCE = ROOT / "upstream/mina-rust"


def git(*args, data=None):
    return subprocess.run(["git", "-C", str(SOURCE), *args], input=data,
                          stdout=subprocess.PIPE, check=True).stdout


def prepare():
    SOURCE.mkdir(parents=True, exist_ok=True)
    if not (SOURCE / ".git").exists():
        subprocess.run(["git", "init", str(SOURCE)], check=True)
        git("config", "core.longpaths", "true")
        git("config", "core.autocrlf", "false")
        git("remote", "add", "origin", CONFIG["repository"])
    revision = CONFIG["revision"]
    exists = subprocess.run(["git", "-C", str(SOURCE), "cat-file", "-e", revision],
                            capture_output=True).returncode == 0
    if not exists:
        cache = ROOT / "upstream/mina-rust.git"
        git("fetch", "--depth", "1", str(cache) if cache.exists() else "origin", revision)
    # Exclude legacy frontend paths invalid on Windows, exactly as in mina-win-node.
    # Preserve the upstream revision as parent; never change upstream refs.
    entries = git("ls-tree", revision).splitlines(keepends=True)
    tree = git("mktree", data=b"".join(e for e in entries
                                     if e.split(b"\t", 1)[1].strip() != b"frontend")).strip()
    date = git("show", "-s", "--format=%cI", revision).decode().strip()
    import os
    env = dict(os.environ, GIT_AUTHOR_DATE=date, GIT_COMMITTER_DATE=date)
    commit = subprocess.run(
        ["git", "-C", str(SOURCE), "-c", "user.name=Mina Android build",
         "-c", "user.email=build@mina-android-node.invalid", "commit-tree",
         tree.decode(), "-p", revision, "-m", "Build source without legacy frontend"],
        env=env, capture_output=True, check=True).stdout.decode().strip()
    git("checkout", "--detach", commit)
    for patch in sorted((ROOT / "patches").glob("*.patch")):
        reverse = subprocess.run(["git", "-C", str(SOURCE), "apply", "--reverse",
                                  "--check", str(patch)], capture_output=True)
        if reverse.returncode:
            git("apply", "--check", str(patch))
            git("apply", str(patch))
    print(f"Prepared {revision} at {SOURCE}")


if __name__ == "__main__":
    prepare()
