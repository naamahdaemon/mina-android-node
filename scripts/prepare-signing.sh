#!/usr/bin/env bash
# Sourced by build-apk.sh. Identity comes only from durable GitHub secrets.
: "${RUNNER_TEMP:?Run this build in GitHub Actions}"
: "${MINA_SIGNING_KEYSTORE_BASE64:?Configure the repository signing keystore secret}"
: "${MINA_SIGNING_STORE_PASSWORD:?Configure the repository signing password secret}"
: "${MINA_SIGNING_KEY_PASSWORD:?Configure the repository signing key password secret}"
: "${MINA_SIGNING_ALIAS:?Configure the repository signing alias secret}"
export MINA_SIGNING_STORE_PASSWORD MINA_SIGNING_KEY_PASSWORD MINA_SIGNING_ALIAS
export MINA_SIGNING_KEYSTORE="$RUNNER_TEMP/mina-signing/identity.p12"
umask 077
mkdir -p "$RUNNER_TEMP/mina-signing"
chmod 700 "$RUNNER_TEMP/mina-signing"
trap 'rm -f -- "$MINA_SIGNING_KEYSTORE"' EXIT
printf '%s' "$MINA_SIGNING_KEYSTORE_BASE64" | base64 --decode > "$MINA_SIGNING_KEYSTORE"
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
