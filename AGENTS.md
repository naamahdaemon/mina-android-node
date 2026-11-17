# Git workflow

- Make changes in the local checkout and validate them before committing.
- Publish feature work through local Git commits and `git push` on `codex/` branches.
- Main is intended to be protected: use a pull request and wait for the required
  Repository checks and Android checks. The maintainer can merge their own PR
  after checks pass; no separate approving reviewer is required.
- Use GitHub's normal pull-request merge operation for protected-branch integration.
  Do not create ad hoc repository files, commits or branch refs through the API.
- After a PR merges, fetch and fast-forward the local main checkout; do not reset
  or force-push to synchronize it.
- Fetch and inspect the local and remote history before integrating changes.
- Keep the working tree clean and verify local HEAD matches origin after push.
- If authentication or push fails, diagnose the Git issue and report it. Do not
  create a separate remote history as a workaround.
- Preserve user changes. Never force-push or reset away commits to synchronize.
- Compile Android binaries in GitHub Actions, not on the local workstation.
