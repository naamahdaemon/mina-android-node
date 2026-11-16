# Git workflow

- Make changes in the local checkout and validate them before committing.
- Publish through local Git commits and `git push`; do not write repository
  files, commits or branch refs directly through the GitHub connector/API.
- Fetch and inspect the local and remote history before integrating changes.
- Keep the working tree clean and verify local HEAD matches origin after push.
- If authentication or push fails, diagnose the Git issue and report it. Do not
  create a separate remote history as a workaround.
- Preserve user changes. Never force-push or reset away commits to synchronize.
- Compile Android binaries in GitHub Actions, not on the local workstation.
