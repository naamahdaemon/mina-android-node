# Mina Android Node

Run a native [Mina Rust](https://github.com/minanode/mina-rust) node on an ARM64 Android device, either through the Android application or directly in Termux. The application runs the Rust executable locally; it does not require Termux, proot or a remote node.

This is an experimental community port, inspired by [Mina Win Node](https://github.com/naamahdaemon/mina-win-node). It is not an official Mina release or a security-audited wallet. Start with devnet and keep independent backups before using a production key.

## Features

- Separate mainnet and devnet profiles, local chain storage and checkpoint restoration.
- Optional block production with an imported, password-encrypted Mina key file; a password is required at every producer start.
- Native wallet generation, one-time private-key display and encrypted key-file export.
- SNARK worker configuration, coinbase receiver and peer/network settings.
- Embedded upstream dashboard and a native monitor with CPU/RAM charts, production attempts, timings and explorer links.
- Persisted resource snapshots for captured production attempts; five attempts per monitor page.
- Upcoming production countdown and configurable notification lead time.
- English (default) and French, light/dark themes, full-screen mode and horizontal or hamburger navigation.
- Exportable logs in a dedicated tab.

## Requirements and validation

The **application requires Android 13 or later and ARM64 (`arm64-v8a`)**. It does not support 32-bit ARM or x86 devices. The standalone native binary is built for Android API 28; that build target is not a guarantee of support on every older device.

Allow sufficient free storage, sustained network access and cooling. Resource use varies with network state and enabled roles; this project does not yet establish a general minimum RAM requirement. Android and manufacturer battery policies can stop the node even while its foreground service is active.

| Device | Observed results |
| --- | --- |
| Xiaomi 14T Pro, Android 16, 12 GB RAM | Native synchronization, checkpoint restoration and application operation; mainnet block production with a confirmed canonical block. |
| Xiaomi Pad 5, Android 13 | APK installation and application launch. Node synchronization and production have not been established on this device. |

These are device observations, not a compatibility certification. The newest wallet-generation flow still needs end-to-end device validation. SNARK worker configuration is available, but sustained worker operation has not been validated here. A passing CI build alone does not establish correct on-device behavior.

## Install the Android application

1. Open the [latest available release](https://github.com/naamahdaemon/mina-android-node/releases), including prereleases, and download **`mina-android.apk`** from its assets.
2. Open the APK on your Android device to install it. Download `SHA256SUMS` to verify its checksum and `APK-BUILD-INFO.txt` to check its version, source commit and signing certificate.
3. If prompted, allow installation from the browser or file manager used to open the APK.
4. Open **Mina Node**, allow notifications if desired, choose a network in **Settings**, then start the node. Begin on devnet with block production disabled.
5. Wait for synchronization. Stop the node cleanly before changing its profile.

The application supports **both mainnet and devnet**. These are signed experimental APKs, not a Play Store release. Download only from a trusted release/build; a checksum supplied alongside a binary detects corruption but does not independently establish its origin.

For development builds not yet released, open [GitHub Actions](https://github.com/naamahdaemon/mina-android-node/actions), select a successful **Android native prototype** or **Android application release** run and download **`mina-android-devnet-apk`**. Extract its ZIP before installing `mina-android-release.apk`; `APK-SHA256SUMS` contains its checksum. These historical artifact names do not restrict the selected network. GitHub may require sign-in to download Actions artifacts.

Normal updates use the same signing identity and retain application data. Do not uninstall to troubleshoot an update until you have exported your encrypted keys: uninstalling or clearing app data removes private chain data, imported key storage and pending generated keys.

See the [Android application guide](docs/android-app.md) for settings, production, backups, monitoring and troubleshooting.

## Run directly in Termux

Download **`mina-android-arm64`** from **Android native prototype**. It contains a native archive and checksums. Extract and run it inside Termux's private home directory, not Android shared storage. No proot environment is needed.

The supplied launcher starts **devnet**, stores data under `$HOME/.mina-android/devnet` and serves the local API/dashboard at `http://127.0.0.1:3000`. See the [Termux guide](docs/termux.md) for complete installation and persistence checks.

Application and Termux storage are separate. Installing the APK does not import an existing Termux chain or key.

## GitHub releases

The manually triggered **Android release** workflow assembles existing successful builds without compiling Rust or the app. Supply a new version tag and the APK/native workflow run IDs. It verifies source compatibility, checksums, the APK signing certificate and byte-identical Rust executables before creating a draft by default. Assets include the signed APK, standalone Mina ARM64 executable, full Termux archive, checksums, license and build provenance. See the [release guide](docs/android-app.md#creating-a-github-release).

## Builds and updates

All Android compilation is handled by GitHub Actions. Distribution APKs use the non-debuggable `release` variant, with the existing durable signing identity. CI verifies the actual packaged manifest and certificate.

Pull requests run the separate **CI** workflow (tests, lint, supervisor checks and license inventory) without signing secrets or a Rust rebuild. Signed builds run only on trusted `main` pushes or manual runs on `main`.

| Workflow | Purpose |
| --- | --- |
| [Android native prototype](.github/workflows/android-native.yml) | Validate the app, build the patched Rust node, upload the reusable native package, then build the APK. Use after changing native inputs or when no compatible native artifact remains. |
| [Android application release](.github/workflows/android-app.yml) | Reuse a compatible native artifact from `main`, verify checksums, and rebuild the app and small C supervisor without recompiling Rust. |

The **CI** checks run on every pull request and `main` push. App-only changes on `main` trigger the application release workflow. Native-input changes trigger the full workflow. For a mixed push, the application workflow routes APK creation to the full workflow so Rust finishes first. Both workflows can also be started manually. Documentation-only changes run CI checks but do not trigger signed APK or Rust builds.

The app-only workflow deliberately fails if the selected artifact is incompatible, missing or expired; it never silently rebuilds Rust. Native artifacts are retained for 90 days and uploaded before APK compilation. Diagnostics are available as `android-build-diagnostics` or `android-app-diagnostics`.

[upstream.json](upstream.json) pins Mina Rust revision `5ee3c569bc00a2ea8631f6373bd151322a4566d1`, Rust `1.92.0`, Android NDK `27.3.13750724` and target `aarch64-linux-android` (API 28). [Source preparation](scripts/prepare-source.py) applies the ordered [Android patches](patches). The legacy frontend is excluded from the source checkout for Windows filesystem compatibility. Windows-specific patches are not applied.

The binary uses libp2p, embedded circuits and the embedded dashboard, without WebRTC. Android uses its system allocator; native ELF segments are configured for 16 KiB alignment. Actual device compatibility still requires testing.

### Building a fork

Configure a durable signing identity through GitHub Actions secrets:

- `MINA_SIGNING_KEYSTORE_BASE64`
- `MINA_SIGNING_STORE_PASSWORD`
- `MINA_SIGNING_KEY_PASSWORD`
- `MINA_SIGNING_ALIAS`

The signing certificate must match [android-signing.sha256](android-signing.sha256). A fork using its own keystore must update this public certificate pin accordingly. Never commit the keystore or passwords. A different certificate cannot update an already installed APK signed by this project. Keep the same signing identity for subsequent builds; see the [build and signing guide](docs/android-app.md#builds-and-signing).

## Security and operational limits

- Back up the **encrypted Mina key file** outside the application and keep its password separately. Android Keystore protection is not a recovery mechanism. Losing both the usable app copy and your backup can make the key unrecoverable.
- The producer password is not saved by the app. Rust must decrypt the key in memory to sign; this is not hardware-isolated signing. Clipboard and managed-memory copies cannot be guaranteed to disappear completely.
- The application API binds to `127.0.0.1:4300`. Loopback avoids exposing it directly to the LAN but is not per-application authentication; other local software may reach it. Do not expose the API through a public proxy or port-forward without reviewing its access controls.
- Review exported logs before publishing them. They can contain public addresses, peer/network information and future production opportunities. Never publish private keys, passwords or signing secrets in issues.
- A VPN caused severe synchronization delays during device testing. If synchronization stalls, compare behavior without it and inspect download progress rather than assuming a protocol failure.
- A foreground service and CPU wake lock do not guarantee continuous operation, timely notifications or block production. There is no unattended producer restart without a fresh password.
- Canonical-chain labels reflect the node's current view and can change during a reorganization.

## Documentation and contributions

- [Android application: installation, settings, keys, monitoring and CI](docs/android-app.md)
- [Termux: native package, launch commands and persistence testing](docs/termux.md)
- [Contributor workflow](AGENTS.md)

For bug reports, include the app version, source commit, Android version, device model, selected network and reproducible steps. Share only reviewed, redacted diagnostics. Keep changes focused and validate the relevant behavior in CI and on a device where appropriate.

## Attribution and licensing

The node engine and protocol implementation come from [Mina Rust](https://github.com/minanode/mina-rust); this repository supplies Android adaptations, application code and build tooling. Native packages and APK assets include the upstream license and third-party notices. The APK also contains a generated Android runtime dependency inventory, full license text and embedded attribution notices under `assets/app-licenses/`; these files accompany APK artifacts and GitHub releases. See [Android dependency licensing](docs/licenses.md).

This project is licensed under the **Apache License 2.0**, matching Mina Rust. See [LICENSE](LICENSE) for the complete text, copied unchanged from the pinned upstream revision. Upstream copyright notices and third-party license terms remain applicable; the project license does not replace the individual licenses of bundled dependencies.
