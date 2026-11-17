# Native Mina Rust in Termux

This route runs the Android ARM64 executable directly in Termux, without proot or the Android application. For the standalone application, use the [Android guide](android-app.md).

The binary is built for `aarch64-linux-android`, API 28. Device validation was performed on a Xiaomi 14T Pro running Android 16 with 12 GB RAM; the API target alone does not establish compatibility with every older device. Start on devnet without a production key.

## Download and verify

From a successful **Android native prototype** [Actions run](https://github.com/naamahdaemon/mina-android-node/actions), download `mina-android-arm64` and extract the artifact ZIP. Copy **both** `mina-android-arm64.tar.gz` and its accompanying `SHA256SUMS` to the phone's Downloads directory. Adjust paths if the file manager placed them in a subdirectory.

In Termux:

```bash
termux-setup-storage
cd "$HOME/storage/downloads"
sha256sum -c SHA256SUMS
mkdir -p "$HOME/mina"
tar -xzf mina-android-arm64.tar.gz -C "$HOME/mina"
cd "$HOME/mina/mina-android-arm64"
sha256sum -c SHA256SUMS
./mina --help
getconf PAGESIZE
```

Install `coreutils` or `tar` through Termux's package manager if the required commands are missing. Only use trusted build artifacts: checksums detect corruption but do not independently authenticate a maliciously replaced package.

Extract into Termux's **private home directory**. Android shared storage is suitable for downloads and exports, but not for executing this binary. The package includes the node, launcher, lock tests, build information and upstream license notices.

## Start devnet

```bash
cd "$HOME/mina/mina-android-arm64"
bash run-termux.sh
```

The launcher selects devnet, uses `$HOME/.mina-android/devnet` and binds the HTTP API to `127.0.0.1:3000`. The embedded dashboard is available at `http://127.0.0.1:3000/` in a browser on the device.

In another Termux session:

```bash
cd "$HOME/mina/mina-android-arm64"
./mina node status --node http://127.0.0.1:3000
curl --max-time 15 -fsS http://127.0.0.1:3000/stats/sync/progress
curl --max-time 15 -fsS http://127.0.0.1:3000/state/message-progress
```

Install `curl` if necessary. Progress reports may follow a moving target as new blocks arrive; compare download/application progress and logs rather than only the best-tip display during bootstrap.

Override the launcher's data directory or HTTP port with environment variables:

```bash
MINA_WORK_DIR="$HOME/.mina-android/devnet" MINA_HTTP_PORT=4000 \
  bash run-termux.sh --log-level info
```

Use the corresponding port in status requests. `run-termux.sh` is a devnet convenience launcher. For another network, use the client directly and a separate data directory:

```bash
./mina --network mainnet node \
  --work-dir "$HOME/.mina-android/mainnet" \
  --http-addr 127.0.0.1 --port 4000
```

Review `./mina node --help` for the flags supported by the packaged revision. Do not run two processes against the same data directory. The APK uses its own private storage and local HTTP port 4300; it does not reuse Termux's files.

## Stop and keep running safely

Stop with **Ctrl+C** and wait for process exit before restarting or replacing files. Preserve the same work directory to test restoration.

For a longer session, use `termux-wake-lock` if available, and release it afterward with `termux-wake-unlock`. Review Android background/battery settings. Neither this command nor external power guarantees continuous operation. Monitor temperature, RAM, free storage and network use.

If synchronization repeatedly times out, compare behavior without a VPN. VPN-related staged-ledger transfer delays were observed on the validation device. Keep the HTTP API on loopback; loopback is not per-app authentication.

## Validate Android storage locking

The package includes `frontier-lock-tests`, exercising the same Android `flock` behavior used by storage:

```bash
cd "$HOME/mina/mina-android-arm64"
chmod +x frontier-lock-tests
TMPDIR="$PREFIX/tmp" ./frontier-lock-tests --nocapture
```

Tests use temporary files, independently of the real chain store. They check exclusion between openings/processes and lock release after close. A passing lock test is not itself proof that a checkpoint exists or restores correctly.

## Test checkpoint restoration

Stop any earlier instance. For this test, retain the same data directory and use a short checkpoint interval:

```bash
cd "$HOME/mina/mina-android-arm64"
MINA_HTTP_PORT=4000 bash run-termux.sh --checkpoint-interval 60 --log-level debug \
  2>&1 | tee "$HOME/mina/persistence-write.log"
```

Wait for `Synced` and a successful `PersistCheckpoint` / `TransitionFrontierPersistCheckpointSuccess` event. Stop with Ctrl+C and wait for exit. Restart without changing or deleting the work directory:

```bash
MINA_HTTP_PORT=4000 bash run-termux.sh --checkpoint-interval 60 --log-level debug \
  2>&1 | tee "$HOME/mina/persistence-restore.log"
```

Inspect the new log from another session:

```bash
grep -Ei 'persist|checkpoint|restor' "$HOME/mina/persistence-restore.log" | tail -n 80
./mina node status --node http://127.0.0.1:4000
```

A successful load and `PersistRestored` indicate restored chain replay. Investigate `PersistLoadAbandoned`, `PersistOpenError` and checkpoint errors if the node bootstraps instead. An initially old displayed tip during verification/replay does not alone prove restoration failed. Do not use `--frontier-store-reset` during this test. A `LOCK` file alone is not a backup. Return to an appropriate checkpoint interval after testing.

Logs and chain files remain inside Termux's private filesystem. To export a diagnostic file deliberately:

```bash
cp "$HOME/mina/persistence-restore.log" "$HOME/storage/downloads/"
```

Review it before sharing; logs can expose public addresses, peer details and production schedules. Never copy plaintext private keys or passwords into a public diagnostic bundle.

## Updating and keys

Stop the old node, verify the new artifact and retain the existing work directory. Do not delete storage to resolve a version problem without first understanding what will be lost.

The standard native CLI remains available, including `mina wallet` commands and its existing password-input behavior. The application's private credential-input flags are internal integration details, not a recommended interactive interface. Avoid putting passwords in shell history, command-line arguments or shared logs. Keep an independently recoverable encrypted key backup and password before enabling production.
