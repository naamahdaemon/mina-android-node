#!/usr/bin/env bash
set -euo pipefail
root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
cd "$root"
source "$root/scripts/prepare-signing.sh"
python3 -m unittest discover -s tests -p test_apk_version.py
python3 -m unittest discover -s tests -p test_localization.py
readarray -t apk_version < <(python3 scripts/apk-version.py)
export APK_VERSION_CODE=${apk_version[0]}
export APK_VERSION_NAME=${apk_version[1]}
ndk_version=$(python3 -c 'import json; print(json.load(open("upstream.json"))["ndk"])')
llvm="$ANDROID_HOME/ndk/$ndk_version/toolchains/llvm/prebuilt/linux-x86_64/bin"
native="$root/android/app/src/main/jniLibs/arm64-v8a"
assets="$root/android/app/src/main/assets/node"
mkdir -p "$native" "$assets" "$root/target" "$root/dist" "$root/logs"
test -s dist/mina-android-arm64/mina
cp dist/mina-android-arm64/mina "$native/libmina.so"
cp dist/mina-android-arm64/{LICENSE,THIRD_PARTY_NOTICES,THIRD_PARTY_LICENSES,BUILD-INFO.json} "$assets/"
"$llvm/aarch64-linux-android28-clang" -std=c11 -Wall -Wextra -Werror \
  -fPIE -pie -Wl,-z,max-page-size=16384 android/native/supervisor.c \
  -o "$native/libminasupervisor.so"
cc -std=c11 -Wall -Wextra -Werror android/native/supervisor.c -o target/supervisor-host
python3 tests/supervisor_test.py target/supervisor-host 2>&1 | tee logs/supervisor-tests.log
# Verify dynamic dependencies rather than accidentally shipping a host library.
"$llvm/llvm-readelf" -d "$native/libmina.so" | tee logs/apk-native-dependencies.log
if grep -q 'libc++_shared.so' logs/apk-native-dependencies.log; then
  cp "$ANDROID_HOME/ndk/$ndk_version/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so" "$native/"
fi
cd android
gradle --no-daemon --console=plain :app:exportReleaseDependencies
cd "$root"
python3 scripts/android-licenses.py android/app/build/reports/licenses/release-dependencies.json
cd android
gradle --no-daemon --console=plain testReleaseUnitTest lintRelease assembleRelease bundleRelease \
  2>&1 | tee "$root/logs/apk-build.log"
python3 "$root/scripts/verify-android-tests.py" app/build/test-results
cp app/build/outputs/apk/release/app-release.apk "$root/dist/mina-android-release.apk"
python3 - "$root/dist/mina-android-release.apk" <<'PY'
import sys, zipfile
with zipfile.ZipFile(sys.argv[1]) as apk:
    for name in ("lib/arm64-v8a/libmina.so", "lib/arm64-v8a/libminasupervisor.so", "assets/node/LICENSE"):
        assert apk.getinfo(name).file_size > 0, name
    assert apk.getinfo("assets/app-licenses/ANDROID-DEPENDENCIES.json").file_size > 0
    assert apk.getinfo("assets/app-licenses/ANDROID-THIRD-PARTY-NOTICES.txt").file_size > 0
print("APK native executables and licenses verified")
PY
"$ANDROID_HOME/build-tools/36.0.0/aapt2" dump badging "$root/dist/mina-android-release.apk" > "$root/logs/apk-badging.txt"
python3 "$root/scripts/verify-apk-release.py" "$root/logs/apk-badging.txt"
cd "$root/dist"
{
  printf 'Build type: release\nDebuggable: false\nVersion code: %s\nVersion name: %s\nApp source: %s\n' "$APK_VERSION_CODE" "$APK_VERSION_NAME" "${GITHUB_SHA:-local}"
  "$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --print-certs mina-android-release.apk
} > APK-BUILD-INFO.txt
sha256sum mina-android-release.apk > APK-SHA256SUMS

# Google Play consumes a signed AAB; keep the existing APK artifact unchanged.
cp "$root/android/app/build/outputs/bundle/release/app-release.aab" mina-android-release.aab
jarsigner -J-Duser.language=en -verify mina-android-release.aab > "$root/logs/aab-signature.txt"
grep -q 'jar verified\.' "$root/logs/aab-signature.txt"
if grep -q 'unsigned entries' "$root/logs/aab-signature.txt"; then
  echo 'Unsigned bundle entries detected' >&2; exit 1
fi
keytool -J-Duser.language=en -printcert -jarfile mina-android-release.aab > "$root/logs/aab-certificate.txt"
actual=$(sed -n 's/^[[:space:]]*SHA256: //p' "$root/logs/aab-certificate.txt" | head -n 1 | tr -d ':[:space:]' | tr '[:upper:]' '[:lower:]')
expected=$(tr -d '[:space:]' < "$root/android-signing.sha256")
test -n "$actual" && test "$actual" = "$expected"
python3 "$root/scripts/verify-app-bundle.py" mina-android-release.aab mina-android-release.apk
{
  printf 'Build type: release\nVersion code: %s\nVersion name: %s\nApp source: %s\nNative run: %s\n' "$APK_VERSION_CODE" "$APK_VERSION_NAME" "${GITHUB_SHA:-local}" "${MINA_NATIVE_RUN_ID:-same-workflow}"
  printf 'Bundle signing certificate SHA-256: %s\n' "$actual"
  printf 'Native binaries match APK: true\nELF LOAD alignment: at least 16 KiB\n'
} > AAB-BUILD-INFO.txt
sha256sum mina-android-release.aab > AAB-SHA256SUMS
