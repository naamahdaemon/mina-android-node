# Android dependency licensing

The project is Apache-2.0; see [LICENSE](../LICENSE). Dependencies retain their own copyright and license terms.

The Android dependency inventory is generated from the **resolved release runtime graph**, rather than a handwritten list of direct dependencies. It covers the Android variants selected by Gradle and their transitive artifacts, including AndroidX Activity/Core/Lifecycle, Compose and Material 3, Kotlin, coroutines, annotations and any additional runtime components that resolution selects.

## Published inventory

Every new signed APK artifact and GitHub release includes:

- `ANDROID-DEPENDENCIES.json`: exact resolved coordinates/versions, artifact SHA-256 hashes, Maven-declared license names/URLs (including inherited parent POM licenses), available attribution metadata and embedded notice paths.
- `ANDROID-THIRD-PARTY-NOTICES.txt`: full applicable license text and preserved attribution/notice text from the resolved AAR/JAR files, including nested `classes.jar` entries.

Identical copies are packaged inside the APK at `assets/app-licenses/`. The CI `android-checks` artifact also contains the files, without requiring signing secrets. See [Releases](https://github.com/naamahdaemon/mina-android-node/releases) for the inventory corresponding to a distributed APK; older releases predate this addition.

The generator currently supports the Apache 2.0 license used by the runtime dependencies. It **fails** on missing license metadata or a new license requiring review and a full text, rather than silently classifying that dependency as Apache-2.0. Embedded notices are preserved even when Maven metadata already names the license. Attribution metadata is not a substitute for the original embedded notices.

## Generation and review

GitHub CI runs `:app:exportReleaseDependencies` to export actual artifact/POM paths, followed by `scripts/android-licenses.py`. This resolves dependencies without compiling Rust. Signed app builds perform the same generation before packaging.

When updating dependencies:

1. Review the resolved inventory and notice changes from CI.
2. Add support and the complete upstream text if a new license is introduced; do not bypass the generator's failure.
3. Retain upstream copyright and NOTICE content. Investigate missing or inconsistent metadata against the dependency's official source before distributing it.

Gradle plugins, the JDK, SDK/NDK, test-only libraries and other build tools are not application runtime components and are not represented as packaged dependencies in this inventory. Their own distribution/toolchain licenses still apply. The native Rust binary, embedded circuits and dashboard keep their separately generated upstream `LICENSE`, `THIRD_PARTY_NOTICES` and `THIRD_PARTY_LICENSES` under APK `assets/node/` and in the Termux archive.

This is an automated inventory with fail-closed checks, not a legal opinion or a guarantee that a third party's Maven metadata is complete.
