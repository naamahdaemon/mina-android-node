#!/usr/bin/env bash
set -euo pipefail
root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
cd "$root"
python3 -m unittest discover -s tests -p test_apk_version.py
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
gradle --no-daemon --console=plain testDebugUnitTest lintDebug assembleDebug \
  2>&1 | tee "$root/logs/apk-build.log"
cp app/build/outputs/apk/debug/app-debug.apk "$root/dist/mina-android-devnet-debug.apk"
python3 - "$root/dist/mina-android-devnet-debug.apk" <<'PY'
import sys, zipfile
with zipfile.ZipFile(sys.argv[1]) as apk:
    for name in ("lib/arm64-v8a/libmina.so", "lib/arm64-v8a/libminasupervisor.so", "assets/node/LICENSE"):
        assert apk.getinfo(name).file_size > 0, name
print("APK native executables and license verified")
PY
cd "$root/dist"
{
  printf 'Version code: %s\nVersion name: %s\nApp source: %s\n' "$APK_VERSION_CODE" "$APK_VERSION_NAME" "${GITHUB_SHA:-local}"
  "$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --print-certs mina-android-devnet-debug.apk
} > APK-BUILD-INFO.txt
sha256sum mina-android-devnet-debug.apk > APK-SHA256SUMS
