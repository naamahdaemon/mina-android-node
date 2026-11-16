#!/usr/bin/env bash
# Sourced by build-apk.sh; a cache miss must never silently change identity.
export MINA_SIGNING_KEYSTORE="$HOME/.android/debug.keystore"
export MINA_SIGNING_STORE_PASSWORD=${MINA_SIGNING_STORE_PASSWORD:-android}
export MINA_SIGNING_KEY_PASSWORD=${MINA_SIGNING_KEY_PASSWORD:-android}
export MINA_SIGNING_ALIAS=${MINA_SIGNING_ALIAS:-androiddebugkey}
mkdir -p "$HOME/.android"
if [[ -n "${MINA_SIGNING_KEYSTORE_BASE64:-}" ]]; then
  umask 077
  printf '%s' "$MINA_SIGNING_KEYSTORE_BASE64" | base64 --decode > "$MINA_SIGNING_KEYSTORE"
fi
test -s "$MINA_SIGNING_KEYSTORE" || {
  echo 'Signing identity missing. Configure MINA_SIGNING_KEYSTORE_BASE64 with the existing key; generating a replacement would break updates.' >&2
  exit 1
}
certificate=$(keytool -exportcert -keystore "$MINA_SIGNING_KEYSTORE" -alias "$MINA_SIGNING_ALIAS" \
  -storepass:env MINA_SIGNING_STORE_PASSWORD | sha256sum | cut -d ' ' -f1)
expected=$(tr -d '[:space:]' < "$root/android-signing.sha256")
[[ "$certificate" == "$expected" ]] || {
  echo "Signing certificate mismatch: expected $expected, got $certificate. APK build stopped to preserve update compatibility." >&2
  exit 1
}
unset MINA_SIGNING_KEYSTORE_BASE64
