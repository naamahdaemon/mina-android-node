#!/usr/bin/env bash
# Linux host; Android NDK installed by the GitHub Actions workflow.
set -euo pipefail
root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
cd "$root"
readarray -t settings < <(python3 -c 'import json; c=json.load(open("upstream.json")); print(c["toolchain"]); print(c["target"]); print(c["api"]); print(c["ndk"])')
toolchain=${settings[0]}
target=${settings[1]}
api=${settings[2]}
ndk=${ANDROID_HOME:?ANDROID_HOME is required}/ndk/${settings[3]}
llvm="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin"
test -x "$llvm/aarch64-linux-android${api}-clang"
export PATH="$llvm:$PATH"
command -v protoc
rustup toolchain install "$toolchain" --profile minimal --component rustfmt
rustup run "$toolchain" rustfmt --version
rustup target add "$target" --toolchain "$toolchain"
export CARGO_TARGET_DIR="$root/target"
export CARGO_BUILD_JOBS=${CARGO_BUILD_JOBS:-2}
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$llvm/aarch64-linux-android${api}-clang"
export CC_aarch64_linux_android="$CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER"
export CXX_aarch64_linux_android="$llvm/aarch64-linux-android${api}-clang++"
export AR_aarch64_linux_android="$llvm/llvm-ar"
export RANLIB_aarch64_linux_android="$llvm/llvm-ranlib"
# Support both 4 KiB and 16 KiB Android kernels, including native libraries.
export CARGO_TARGET_AARCH64_LINUX_ANDROID_RUSTFLAGS='-C link-arg=-Wl,-z,max-page-size=16384'
export CFLAGS_aarch64_linux_android='-fPIC'
export CXXFLAGS_aarch64_linux_android='-fPIC'
export ANDROID_NDK_HOME="$ndk"
export ANDROID_NDK_ROOT="$ndk"
mkdir -p logs dist target
python3 scripts/prepare-source.py 2>&1 | tee logs/prepare.log
python3 -m unittest discover -s tests -p test_patches.py -v \
  2>&1 | tee logs/patch-tests.log
cargo +"$toolchain" test --manifest-path tests/credentials/Cargo.toml \
  2>&1 | tee logs/credential-tests.log
# Test the exact Android flock implementation on Linux without building a
# second copy of the node. Also ship its Android test executable for Termux.
rustup run "$toolchain" rustc --edition 2021 --test tests/android-file-lock.rs \
  -o target/frontier-lock-tests-host
target/frontier-lock-tests-host --nocapture 2>&1 | tee logs/frontier-lock-tests.log
rustup run "$toolchain" rustc --edition 2021 --test tests/android-file-lock.rs \
  --target "$target" -C linker="$CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER" \
  -C link-arg=-Wl,-z,max-page-size=16384 -o target/frontier-lock-tests
cd upstream/mina-rust
(
  cd ui
  npm ci
  npm run build:dashboard
  test -s dashboard/dist/index.html
) 2>&1 | tee "$root/logs/dashboard-build.log"
cargo +"$toolchain" build --release --locked --target "$target" \
  -p mina-cli --bin mina --no-default-features \
  --features p2p-libp2p,embedded-circuits,exit-on-invalid-peers,dashboard \
  2>&1 | tee "$root/logs/cargo-build.log"
cd "$root"
package="$root/dist/mina-android-arm64"
mkdir -p "$package"
cp "target/$target/release/mina" "$package/mina"
cp target/frontier-lock-tests "$package/"
"$llvm/llvm-strip" "$package/mina"
"$llvm/llvm-readelf" -h -l -d "$package/mina" | tee logs/elf.txt
cp upstream/mina-rust/{LICENSE,THIRD_PARTY_NOTICES,THIRD_PARTY_LICENSES} "$package/"
cp docs/termux.md "$package/TERMUX.md"
cp upstream.json "$package/BUILD-INFO.json"
cp scripts/run-termux.sh "$package/"
chmod +x "$package/mina" "$package/frontier-lock-tests" "$package/run-termux.sh"
(cd "$package"; sha256sum mina frontier-lock-tests run-termux.sh BUILD-INFO.json > SHA256SUMS)
tar -C dist -czf dist/mina-android-arm64.tar.gz mina-android-arm64
(cd dist; sha256sum mina-android-arm64.tar.gz > SHA256SUMS)
