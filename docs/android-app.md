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

At each explicit launch, the confirmed Android SNARK-worker setting takes precedence over the dashboard startup file. When disabled, the app removes only `snark-worker.json` from the selected network before starting Rust, preventing an old dashboard setting from silently re-enabling the worker. Keys, chain data and `snark-worker-included.json` history are preserved. The node will not start if that setting cannot be cleared safely. Dashboard changes during a run remain available when worker control is enabled; the Android profile is applied again at the next launch.

The default checkpoint interval is 30 minutes. Availability of a setting does not establish that every role or combination has been validated on hardware. Account funding, stake and epoch eligibility are distinct from simply enabling production.

## Producer keys and passwords

### Import an existing key

Enable block production and import a **password-encrypted Mina JSON key file** through Android's document picker. The application wraps that already encrypted file with AES-GCM using Android Keystore and stores it privately outside Android backup. Keep the original encrypted file and its password independently recoverable.

Every producer start requires a fresh password. It is not saved in preferences, Intents, command-line arguments or environment variables. The app passes it through a bounded private stdin frame to Rust. A short-lived, one-use in-memory authorization binds launch to the confirmed profile. The service does not automatically restart a producer after process loss or device reboot.

The password entry disables saved text, autofill and screenshots. Buffers are cleared where practical, but managed-runtime memory copies cannot be guaranteed erased. Rust decrypts the key in memory for signing; Keystore protects storage, not signing inside the Rust process.

### Generate a new key

With the node stopped and block production enabled, choose **Generate a new key**.

1. Read the recovery warnings and enter and confirm a password of 12Ã¢â‚¬â€œ4096 characters. A further confirmation repeats the warning before generation.
2. The app invokes the native client's wallet generator through a private input/output channel, using Mina's encrypted key-file format. Passwords and plaintext keys are not written to the node log or plaintext files.
3. Explicitly acknowledge the one-time reveal to view the public address and plaintext private key. Copy controls are available; copying the private key requires another warning.
4. Optionally apply the generated encrypted file to the current network's producer profile. Replacing an existing key requires confirmation; the node must still be stopped.
5. Export the encrypted JSON through Android's document picker. Store it somewhere independently recoverable, with its password kept separately.

Closing or backgrounding the reveal clears the private-key display. Resuming offers the pending encrypted file rather than revealing the secret again. Pending encrypted output remains in private, non-backed-up storage until export and explicit completion; a new generation does not overwrite it. Uninstalling the app destroys that pending copy.

Private-key clipboard entries are marked sensitive and cleanup is attempted after 60 seconds if the entry has not been replaced. Clipboard history, keyboards and accessibility services can still expose copied secrets. Screenshot blocking and best-effort cleanup are not absolute guarantees.

Creating an account on-chain incurs a 1 MINA account-creation fee deducted from its first funding. Fund more than 1 MINA to leave a balance, allowing separately for transaction fees. Generation does not send a transaction, activate stake or guarantee immediate production eligibility. Check current [Mina account funding documentation](https://docs.minaprotocol.com/using-mina/how-to-send-and-receive) before funding.

The generation feature has passed CI checks; its complete device flow still needs validation. Use a newly generated test key on devnet first.

## Navigation and appearance

The fixed header contains **MINA / ANDROID** and the next announced won slot. Navigation below it follows **Node Ã¢â€ â€™ Monitor Ã¢â€ â€™ Dashboard Ã¢â€ â€™ Settings Ã¢â€ â€™ Logs**. Choose horizontal navigation or a hamburger menu in Settings. Horizontal navigation keeps arrow controls without a scrollbar. Android application settings are linked inside Settings.

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

In **Settings > Application settings**, set the initial production alert lead time to **1-3600 seconds** (default 60). Optionally enter a reminder interval in seconds: it must be positive and strictly smaller than the initial lead time. Leave it blank to keep one notification per slot. Save applies both settings without restarting the node. Reminders begin after the actual initial notification, stop at slot time or when the slot leaves the schedule, and reuse the same notification instead of stacking entries. The last emission time is retained across service restarts; missed reminders are not replayed in a burst. Android may silence or throttle repeated sounds. Changing the lead time does not replay the initial alert.

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

The app toolchain uses JDK 17, Gradle 9.6.0, AGP 9.5.1, built-in Kotlin/Compose compiler 2.4.21, compile SDK 37 and target SDK 36. See [upstream.json](../upstream.json) for pinned native inputs. CI runs relevant tests and Android Lint; device testing remains necessary. The separate **CI** workflow tests pull requests without signing secrets and does not compile Rust. Distribution workflows do not run on pull requests and accept only `main`.

Both workflows derive version codes from UTC seconds since 2020 rather than separate workflow counters. The version name includes the UTC build date. Signing requires durable Actions secrets `MINA_SIGNING_KEYSTORE_BASE64`, `MINA_SIGNING_STORE_PASSWORD`, `MINA_SIGNING_KEY_PASSWORD` and `MINA_SIGNING_ALIAS`. The certificate must match [android-signing.sha256](../android-signing.sha256). Missing or mismatched signing material fails the build instead of generating an incompatible replacement key. The temporary keystore is removed by the signing script.

A fork needs its own durable keystore and corresponding public certificate pin; its APK cannot update an installation signed with this project's certificate. Do not commit private signing material. Back up the keystore securely: losing it can prevent future compatible updates.

## Validation scope

On Xiaomi 14T Pro (Android 16, 12 GB RAM), synchronization, persistence restoration and mainnet block production have been observed. Xiaomi Pad 5 (Android 13) has passed installation and launch checks only. Validate new key workflows, worker behavior, stop/restart, battery/thermal behavior and screen-off operation on the intended device, initially on devnet.

## Platform references

- [Android foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Android executable restrictions](https://developer.android.com/about/versions/10/behavior-changes-10#execute-permission)
- [Android Gradle Plugin 8.10](https://developer.android.com/build/releases/agp-8-10-0-release-notes)

## Creating a GitHub release

Open **Actions Ã¢â€ â€™ Android release Ã¢â€ â€™ Run workflow**, on `main`:

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

The AGP 9 migration uses built-in Kotlin rather than the legacy Android Kotlin plugin. Compile SDK 37 supports the updated AndroidX/Compose libraries; target SDK 36 and minimum Android 13 remain unchanged. Native Rust inputs and the durable APK signing identity are unchanged.


## Experimental BP power saving

This option is **off by default**. With it off, synchronization, CPU wake-lock management and notification behavior stay continuous as before. It only applies to a running block producer.

In **Settings > BP power saving**, enable suspension and save a separate resume lead time (60-7200 seconds, default 600). This setting is separate from the production notification lead time. If the notification lead is longer, resume early enough for that initial notification as well. Grant **Alarms & reminders** access using the settings link; without permission the node stays running. Android can still delay execution, restrict networking, or kill the app: this mode does not guarantee production.

The app sends SIGSTOP to the native node process group (including workers), retaining its RAM and unlocked key, then releases its CPU wake lock. An exact idle-allowed wake-up resumes the **same process** with SIGCONT and reacquires the wake lock. No password is saved or resent. RAM is not freed. If Android kills the app or the process, a new explicit launch and password are required, just as in continuous mode. Revoking exact-alarm access can itself terminate the app on Android.

The next ten announced slots are saved privately per network in `power-upcoming-slots.json` when they change. A cold launch always refreshes the schedule from the running node; a disk snapshot never authorizes a cold resume. The app does not suspend without a fresh, known future schedule and a synchronized node. It stays awake for the configured minimum awake time after launch/resume (default 120 seconds) and requires stable synchronization (default 30 seconds). A production window starts at the configured resume lead and lasts until at least the configured production grace (default five minutes) after its slot. The post-production guard also checks the latest recorded production, proof completion, commit or discard timestamp and keeps the node awake for the configured grace after that event, even if the slot has disappeared from the future schedule. A slot already entering its protected window is retained until its grace period expires. Exception: a confirmed Discarded attempt with a discard timestamp and no proof-start, proof-end or commit timestamp does not require this post-production wait. A discard during/after proof still does, and any slot still freshly announced as Scheduled remains protected. Recent unfinished production attempts extend awake time. Overlapping/nearby windows and gaps at or below the configured minimum (default 15 minutes) do not cause a pause. The independent synchronization wake-up interval is configurable from 1 to 120 minutes (default 10), measured from each suspension. The earlier of that deadline and the production resume deadline wakes the node. The configured minimum gap still applies to production windows, not to the periodic Sync interval. A periodic wake-up refreshes synchronization and discovers new slots; an empty schedule keeps the node awake.

The home screen and ongoing notification show **SUSPENDED / Waiting for next block production slot time**, with the next synchronization wake-up on the home screen. API and resource polling pause while the native process is stopped; height and uptime retain the last observed values, while peers show zero. Block age continues from the last block timestamp, and the header/monitor next-slot countdowns continue from the cached schedule. These display clocks run only while the UI is visible; they do not poll or wake the native node. Stop and app-death cleanup resume the group before requesting a clean shutdown. Disable the option and save to resume immediately; this does not restart the process. The home screen has a third **Suspend / Resume** button alongside Start and Stop. Resume wakes the same process without a password and without changing the saved automatic setting; automatic suspension waits at least two minutes afterward. Suspend requires the option to be enabled, precise-alarm access, a known fresh schedule, stable synchronization, no nearby production window or unfinished production, and a safe future wake-up. These constraints are rechecked by the service when the request is processed. An explicit manual Suspend may end the automatic two-minute awake hold early, but cannot bypass the other safeguards. Neither manual action writes power-saving preferences.

Before relying on this mode, test on your device with the screen off and check `[app] Node suspended` / `[app] Node resumed` in exported logs. Confirm the same node PID, synchronization before the slot, no repeat password prompt, and successful Stop while suspended. Battery savings and timing reliability have not yet been measured on physical devices.


### Graph gaps and time zoom

CPU/RAM rendering does not connect periods where no measurements were collected. Missing values and intervals longer than 15 seconds split each metric into separate segments; suspension therefore leaves a real recording gap after resume. Screen-size reduction preserves segment endpoints and extrema (including valleys), rather than connecting selected peaks across missing data. The same rendering is used for block-detail graphs.

Pinching uses horizontal finger separation for the time scale and ignores pointer-count transitions. A selected range with no measurements keeps its axes and gesture surface, with a message: zoom out, pan or reset to return to recorded data. It does not invent readings or automatically move an intentionally selected historical interval. Sampling, suspension behavior and the saved zoom/scale controls are unchanged.


### Home suspension indicator

The home timing table has three equal-width columns for block age, uptime and automatic-suspension status. Labels appear above fixed-size monospace values; neither the column widths nor the font size changes as the counters tick. The table respects the page margins. A countdown appears only when known time gates (minimum awake time, stable synchronization or the post-production grace) lead to a valid future suspension under the actual policy. It is recalculated as fresh node data arrives, not a promise that synchronization or production conditions cannot change.

If no reliable deadline exists, the line shows a short reason instead: `sync`, `plan`, `alarm`, `slot` (including insufficient time for the minimum sleep interval), or `work`. Off states are shown explicitly. While suspended, the same position changes to `Wake` and counts down to the actual scheduled wake-up (including periodic synchronization refreshes), rather than to the production slot itself. A pending decision at zero displays `check` rather than a stuck zero timer. The indicator runs only while the UI is visible, adds no API requests or wake-ups, and changes no suspension preferences or timing rules. CPU/RAM recording remains frozen while the native node is suspended.


### Persistent monitor view

Autoscale versus maximum scale, the time zoom and the horizontal position are saved in private app preferences per network. They survive leaving the monitor page, app switching and Activity/process recreation. Pinch/pan writes are debounced; the latest view is flushed when leaving the page or backgrounding the app. Live/reset saves a live window without changing the selected vertical scale. A zoomed view at the live edge is stored as a live-following window, not as a fixed timestamp: it follows new measurements after return while preserving its zoom span. An explicitly historical view retains its absolute end time. If a saved absolute position falls outside a new recording or retained history, the existing viewport logic recenters on available data while retaining the zoom span. This changes view preferences only, not resource collection or node settings.

The home “Wake-up at” line always includes the scheduled reason: **Sync** for periodic synchronization, or **Block** for the production resume window (also used when both deadlines coincide). Saving changed power settings while suspended resumes the node so the next suspension uses the new interval. Existing installations use the new 10-minute default unless they save another value.

### Configurable suspension guards

All policy timing settings are persisted by the app and shared by automatic suspension, manual safety checks and the home forecast. Saving while suspended resumes the existing process; it does not relaunch it or request a password. Power saving disabled keeps the normal node behavior.

| Setting | Default | Allowed range | Meaning |
| --- | --- | --- | --- |
| Production resume lead | 600 s | 60–7200 s | Resume before the next announced production slot; the notification lead can require an earlier wake. |
| Sync interval | 10 min | 1–120 min | Maximum suspension before a periodic sync wake-up. |
| Minimum awake time | 120 s | 10–7200 s | Automatic hold after start/resume; manual Suspend can end this hold if all other guards pass. |
| Stable sync | 30 s | 5–600 s | Continuous healthy synchronization required before suspension. |
| Production grace | 300 s | 0–7200 s | Protection after the slot and latest production/proof/commit/discard event; confirmed pre-proof discards retain their exception. |
| Minimum production gap | 900 s | 0–7200 s | Stay awake if the production resume deadline is this close; independent of the Sync interval. |
| Unfinished production | 900 s | 60–7200 s | Protection measured from the latest unfinished production event. |
| Maximum schedule age | 10 s | 5–300 s | Freshness required for the production schedule. |
| Maximum best-tip age | 1800 s | 60–86400 s | Maximum tip age while considering the node synchronized enough to suspend. |

The wake-up line identifies **Sync** or **Block**. The earlier deadline wins; ties are labeled Block. These settings control the app policy, not Rust networking timeouts or Android alarm delivery guarantees.

### Google Play bundle

The Android application workflow builds both the existing release APK and a signed release Android App Bundle, using the same source, version code/name and pinned signing identity. It reuses the compatible native artifact; adding a bundle does not rebuild Rust. The native workflow also publishes a bundle after its normal build.

Download the `mina-android-play-bundle` artifact from a successful main run. Verify `AAB-SHA256SUMS`, then upload `mina-android-release.aab` in Play Console. `AAB-BUILD-INFO.txt` records provenance and signing identity. The bundle is available as a separate Actions artifact and is also attached to new GitHub releases as `mina-android.aab`, alongside the matching APK and native package.

CI verifies the bundle signature and certificate, compares every packaged native library and license against the APK, and rejects ELF LOAD segments below 16 KiB alignment. This checks binary alignment, not a full runtime test on a 16 KiB device or Google Play policy acceptance. Play App Signing must use the intended app-signing certificate for compatibility with GitHub APKs. No Play upload or rollout is automatic.
