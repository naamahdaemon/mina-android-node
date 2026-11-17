# Android application guide

[Mina Android Node](../README.md) runs Mina Rust locally through a Kotlin/Jetpack Compose application. The APK requires **Android 13+ and ARM64**. It is an experimental, sideloaded test application; Play Store distribution and its foreground-service policy review are outside the current build process.

## Install and update

Download `mina-android-devnet-apk` from a successful [Actions run](https://github.com/naamahdaemon/mina-android-node/actions). Extract the ZIP before opening `mina-android-release.apk`. The historical names do not restrict the application to devnet: mainnet is also selectable.

New artifacts contain a signed, non-debuggable **release** APK. Older debug artifacts cannot be used by the release-publication workflow. The existing certificate is retained so this build-type change does not require uninstalling the app.

The artifact contains:

| File | Purpose |
| --- | --- |
| `mina-android-release.apk` | Installable application. |
| `APK-SHA256SUMS` | APK checksum. |
| `APK-BUILD-INFO.txt` | App version, source/build information and signing certificate fingerprints. |

Allow installation from the specific browser or file manager if Android requests it. For an installation failure, verify Android version, ARM64 support, file integrity, installed version and signing certificate. Retain the exact installer error where possible. Manufacturer-specific installation restrictions may also apply; do not disable Play Protect or broad system security features as a generic workaround.

Normal updates preserve private data when the package identity and signing certificate match and the version is acceptable. A higher version cannot repair a signature mismatch. **Uninstalling or clearing data deletes chain storage and app-held keys**, including unexported generated keys. Export encrypted key backups before any destructive migration.

## First run and storage

1. Open **Settings**, choose devnet and leave block production and the SNARK worker disabled for the first test.
2. Allow notifications if you want the ongoing service notification and production alerts.
3. Choose **Start**, then inspect synchronization, peers and **Logs**. Initial synchronization needs network access and private storage.
4. After synchronization and a successful checkpoint, choose **Stop**, wait for the node to exit and restart with the same profile to test restoration.
5. Check behavior with the screen off and review device battery restrictions if the service stops.

The application uses its own private storage, separately for mainnet and devnet. Termux data is not automatically imported. The **Persistence** indicator distinguishes an empty store, saved chain height, loading/checking and errors. Ledger account files support the running node; frontier checkpoints enable chain restoration. A lock file alone is not a checkpoint.

The local API and embedded dashboard use `http://127.0.0.1:4300`. The default libp2p port is `8303` and can be changed in the profile. Loopback is not an authentication boundary against other local applications.

## Node profile

Stop the node before editing its profile. Settings include:

- Mainnet or devnet, with independent configuration and data.
- Block production, encrypted producer key and coinbase receiver. An empty receiver uses the producer's address.
- Minimum publication delay and the production-priority window relative to SNARK work.
- SNARK worker public key, fee, strategy and optional dashboard control.
- Peer limit, P2P port, log level and optional extended network timeouts.
- Chain persistence/restoration, checkpoint interval and restore timeout.

The default checkpoint interval is 30 minutes. Availability of a setting does not establish that every role or combination has been validated on hardware. Account funding, stake and epoch eligibility are distinct from simply enabling production.

## Producer keys and passwords

### Import an existing key

Enable block production and import a **password-encrypted Mina JSON key file** through Android's document picker. The application wraps that already encrypted file with AES-GCM using Android Keystore and stores it privately outside Android backup. Keep the original encrypted file and its password independently recoverable.

Every producer start requires a fresh password. It is not saved in preferences, Intents, command-line arguments or environment variables. The app passes it through a bounded private stdin frame to Rust. A short-lived, one-use in-memory authorization binds launch to the confirmed profile. The service does not automatically restart a producer after process loss or device reboot.

The password entry disables saved text, autofill and screenshots. Buffers are cleared where practical, but managed-runtime memory copies cannot be guaranteed erased. Rust decrypts the key in memory for signing; Keystore protects storage, not signing inside the Rust process.

### Generate a new key

With the node stopped and block production enabled, choose **Generate a new key**.

1. Read the recovery warnings and enter and confirm a password of 12â€“4096 characters. A further confirmation repeats the warning before generation.
2. The app invokes the native client's wallet generator through a private input/output channel, using Mina's encrypted key-file format. Passwords and plaintext keys are not written to the node log or plaintext files.
3. Explicitly acknowledge the one-time reveal to view the public address and plaintext private key. Copy controls are available; copying the private key requires another warning.
4. Optionally apply the generated encrypted file to the current network's producer profile. Replacing an existing key requires confirmation; the node must still be stopped.
5. Export the encrypted JSON through Android's document picker. Store it somewhere independently recoverable, with its password kept separately.

Closing or backgrounding the reveal clears the private-key display. Resuming offers the pending encrypted file rather than revealing the secret again. Pending encrypted output remains in private, non-backed-up storage until export and explicit completion; a new generation does not overwrite it. Uninstalling the app destroys that pending copy.

Private-key clipboard entries are marked sensitive and cleanup is attempted after 60 seconds if the entry has not been replaced. Clipboard history, keyboards and accessibility services can still expose copied secrets. Screenshot blocking and best-effort cleanup are not absolute guarantees.

Creating an account on-chain incurs a 1 MINA account-creation fee deducted from its first funding. Fund more than 1 MINA to leave a balance, allowing separately for transaction fees. Generation does not send a transaction, activate stake or guarantee immediate production eligibility. Check current [Mina account funding documentation](https://docs.minaprotocol.com/using-mina/how-to-send-and-receive) before funding.

The generation feature has passed CI checks; its complete device flow still needs validation. Use a newly generated test key on devnet first.

## Navigation and appearance

The fixed header contains **MINA / ANDROID** and the next announced won slot. Navigation below it follows **Node â†’ Monitor â†’ Dashboard â†’ Settings â†’ Logs**. Choose horizontal navigation or a hamburger menu in Settings. Horizontal navigation keeps arrow controls without a scrollbar. Android application settings are linked inside Settings.

A deliberate horizontal swipe on the title/header changes pages. Other screens also support body swipes where their controls do not consume gestures. Dashboard content keeps its own gestures to avoid switching pages during vertical scrolling or chart interaction; use the header or menu there.

Application settings provide English (default) or French, dark/light appearance and full-screen mode. Preferences apply without restarting the node. Full screen hides system bars and reclaims their space; an edge swipe can reveal them temporarily. Keyboard clearance and landscape side cutouts remain respected. Native logs and the upstream dashboard keep their own technical/English text.

## Dashboard and monitor

**Dashboard** embeds the upstream dashboard at the local API origin. Its WebView has no native JavaScript bridge or file/content access. Opening or renderer failures provide a retry/back path and diagnostics in Logs.

**Monitor** displays CPU, process RSS, profile storage, free storage, battery and battery temperature where Android makes measurements available. CPU **100% means one logical core**, so total use can exceed 100%. Missing measurements remain missing rather than becoming zero.

### Resource charts

- **Autoscale** changes the vertical scale using visible samples. Maximum scale uses device RAM and logical CPU capacity.
- Pinch changes the time span; horizontal dragging browses recorded time.
- **Live / reset** returns the time viewport to recent/live data. It does not select vertical autoscaling.
- Scale, time viewport and block-chart scroll positions survive navigation between screens.

General live resource history is held in memory for up to six hours. Very early zoom gestures leave live mode intact until enough measured history exists. A viewport outside the available recording returns to recent data; real gaps remain visible as missing data.

### Production attempts and block details

Transactions/zkApps and preparation/proof/publication-delay charts appear above the attempt list and scroll horizontally. The list shows five attempts per page, with status colors. Tap an attempt for timings, available SNARK/transaction counts, discard reason and resource charts around production.

Resource captures cover five seconds before proof start through five seconds after commit/discard, capped at ten minutes or 600 samples per attempt. They checkpoint to the profile's private `production-resources` directory at most once every five seconds. The archive retains at most 500 attempts and 16 MiB, pruning oldest slots first. A sudden process loss can lose the last capture interval. Invalid snapshots are ignored on load.

Block details merge archived and current-run samples. Old attempts without recorded measurements cannot acquire charts retroactively. Public attempt history and per-attempt resource snapshots survive restarts; general live charts do not.

The hash-based explorer link uses Minascan by default. Settings accepts a custom HTTPS template with `{hash}` required and `{network}` optional. **Canonical at height** first resolves that exact height in the local best chain and then opens its hash in the explorer. Heights outside the retained frontier may be unavailable. This is the node's current chain view, subject to reorganization, not a finality guarantee.

## Production notifications

The header shows the next announced won slot with a countdown and progress over the final hour: green, amber below five minutes, red below one minute.

In **Settings â†’ Application settings**, set the notification lead time to **1â€“3600 seconds** (default 60) and save. The running service reads the new setting without restarting. Each announced scheduled slot is checked independently; notifications are deduplicated, expire at slot time and are cancelled when the schedule changes or the node stops. Changing the lead time does not replay alerts already sent for that slot.

Android notification permission and the production-alert channel must be enabled. Late slot discovery, polling, OS scheduling and device suspension may shorten or prevent an alert. There is no exact-alarm or production guarantee. Public lock-screen text omits the slot, network and time.

## Logs and troubleshooting

Logs appear in a bounded, selectable text area with its own scrolling. **Export** saves logs through Android's document picker. Log files rotate at approximately 4 MiB each, with one previous file retained. Review exports before sharing: public addresses, peers and future production schedules can be operationally sensitive even though they are not private keys.

| Symptom | Checks |
| --- | --- |
| Synchronization appears stalled | Inspect sync/download progress and recent errors. Compare without a VPN; a VPN caused severe staged-ledger transfer delays during testing. |
| Old best tip during startup | Wait for checkpoint checks and replay. Inspect `PersistLoad`, `PersistRestored` or load-abandon/error events before concluding persistence failed. |
| No CPU/RAM graph for a block | Confirm samples covered its production period. Earlier unrecorded history cannot be reconstructed. |
| Node stops with the screen off | Review manufacturer battery/background limits and service logs. A wake lock does not override every OS policy. |
| Producer fails to unlock | Check the selected network, encrypted key file and password. Never share the password or plaintext key in a report. |
| Update will not install | Compare version, package/signing identity and checksum; obtain the actual installer error before deleting data. |

## Native lifecycle

The APK packages Rust as `lib/arm64-v8a/libmina.so`, extracted by Android into `nativeLibraryDir` and executed directly. It is not loaded with `dlopen` and needs no shell, Termux or proot. Native segments use 16 KiB alignment. Upstream licenses and third-party notices are included in APK assets.

A small C supervisor receives a stop command through a private pipe, sends SIGINT and waits up to 90 seconds before forcing termination. App/pipe loss takes the same shutdown path; the node also uses a parent-death signal to avoid an orphan process. Storage locks prevent a second concurrent opening of the same store.

The explicitly started foreground service uses Android's `specialUse` type and holds a CPU wake lock while running. Android may still terminate it. There is no boot-start or automatic password-free producer restart.

## Builds and signing

Build Android binaries through GitHub Actions, rather than locally. The full **Android native prototype** workflow validates Kotlin/lint and the supervisor before the expensive Rust build, uploads `mina-android-arm64`, then builds the APK.

For application-only work, run **Android application release**. Leave `native_run_id` empty to select an available compatible native artifact from `main`, or provide a native workflow run ID from `main`. Checksums, ancestry and native-input compatibility are checked. A stale, missing or expired artifact requires running the full workflow first. There is no Rust-build fallback. Native artifacts are retained for 90 days and remain reusable if a later APK step fails.

App paths trigger the application workflow; native paths trigger the full workflow. A mixed push leaves APK creation to the full workflow. Documentation-only pushes run CI checks but trigger neither signed distribution workflow. Workflow definitions and current path filters are authoritative:

- [Full native workflow](../.github/workflows/android-native.yml)
- [Application-only workflow](../.github/workflows/android-app.yml)
- [Native build script](../scripts/build-android.sh)
- [APK build script](../scripts/build-apk.sh)

The app toolchain uses JDK 17, Gradle 8.11.1, AGP 8.10.1, Kotlin 2.1.21 and compile/target SDK 36. See [upstream.json](../upstream.json) for pinned native inputs. CI runs relevant tests and Android Lint; device testing remains necessary. The separate **CI** workflow tests pull requests without signing secrets and does not compile Rust. Distribution workflows do not run on pull requests and accept only `main`.

Both workflows derive version codes from UTC seconds since 2020 rather than separate workflow counters. The version name includes the UTC build date. Signing requires durable Actions secrets `MINA_SIGNING_KEYSTORE_BASE64`, `MINA_SIGNING_STORE_PASSWORD`, `MINA_SIGNING_KEY_PASSWORD` and `MINA_SIGNING_ALIAS`. The certificate must match [android-signing.sha256](../android-signing.sha256). Missing or mismatched signing material fails the build instead of generating an incompatible replacement key. The temporary keystore is removed by the signing script.

A fork needs its own durable keystore and corresponding public certificate pin; its APK cannot update an installation signed with this project's certificate. Do not commit private signing material. Back up the keystore securely: losing it can prevent future compatible updates.

## Validation scope

On Xiaomi 14T Pro (Android 16, 12 GB RAM), synchronization, persistence restoration and mainnet block production have been observed. Xiaomi Pad 5 (Android 13) has passed installation and launch checks only. Validate new key workflows, worker behavior, stop/restart, battery/thermal behavior and screen-off operation on the intended device, initially on devnet.

## Platform references

- [Android foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Android executable restrictions](https://developer.android.com/about/versions/10/behavior-changes-10#execute-permission)
- [Android Gradle Plugin 8.10](https://developer.android.com/build/releases/agp-8-10-0-release-notes)

## Creating a GitHub release

Open **Actions â†’ Android release â†’ Run workflow**, on `main`:

1. Set a new tag, such as `v0.3.0` or `v0.3.0-rc.1`.
2. Set `apk_run_id` to a successful full-native or application-only run on `main` containing `mina-android-devnet-apk`. Use the numeric ID from `/actions/runs/<ID>`.
3. Set `native_run_id` to the successful full-native run that produced the Rust binary embedded in that APK. For a full build, both IDs are the same. For an app-only build, use its reused native run, shown in the build summary.
4. Keep **Publish immediately** disabled to create a draft for review. **Prerelease** defaults to enabled. Enable publication only when ready to publish the verified assets.

The workflow does not rebuild either binary or require private signing secrets. It checks run origin and success, source ancestry/native-input compatibility, artifact availability, checksums and the pinned APK signing certificate. It also compares the actual Rust executable and embedded native build information between the APK and Termux archive. A mismatch fails before release creation. Expired artifacts require new build runs.

The release targets the **APK source commit**, which can be older than the workflow checkout. Existing tags/releases are refused rather than overwritten; choose a fresh version for another attempt. If a network failure leaves a partial draft, inspect it in Releases before retrying. The release operation is not an atomic transaction across asset uploads.

Release attachments:

- `mina-android.apk`: same signed APK as the selected build, renamed for distribution.
- `mina-android-arm64`: standalone native executable; after downloading in Termux, use `chmod +x mina-android-arm64` before executing it.
- `mina-android-arm64.tar.gz`: complete Termux package, including launcher and upstream notices; recommended for standalone-node installation.
- `SHA256SUMS`: checksums for all attached files except the checksum file itself.
- `APK-BUILD-INFO.txt` and `RELEASE-INFO.json`: version, source/run provenance and native build information.
- `LICENSE.txt`: project Apache 2.0 license. Third-party notices remain in the native archive and APK.

The app's installed version still comes from the selected APK build; creating a release tag does not change its version or signature. Normal updates retain the same signing requirements. A published release in a private repository remains subject to that repository's visibility.

## Release APK and dependency notices

**Android application release** is the dedicated APK-only build action. It reuses the compatible native artifact, builds `assembleRelease`, signs with the durable certificate and verifies the packaged manifest is not debuggable. `Android release` publishes verified build outputs; it rejects older debug artifacts and checks the manifest again before creating a release.

Gradle exports the actual `releaseRuntimeClasspath` artifact graph and Maven POMs. The license generator inventories coordinates, versions, artifact hashes and declared license metadata, preserves embedded LICENSE/NOTICE/COPYRIGHT files (including nested AAR `classes.jar` notices), and bundles full license text. Missing metadata or an unreviewed license fails CI. See [the licensing guide](licenses.md).

The generated `ANDROID-DEPENDENCIES.json` and `ANDROID-THIRD-PARTY-NOTICES.txt` are inside `assets/app-licenses/`, attached to the APK artifact and included in new GitHub releases. Rust/circuits/dashboard notices remain separate under `assets/node/` and in the native archive.
