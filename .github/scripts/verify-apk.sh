#!/usr/bin/env bash
# Checks a built APK before anyone installs it.
#
#   verify-apk.sh <apk>              the APK must request no Android permissions
#   verify-apk.sh <apk> --release    ...and must be signed with the release key whose
#                                    SHA-256 fingerprint is in .github/release-cert-sha256
#
# AutoSkip promises to need no permissions at all. Above all it must never get INTERNET:
# an accessibility service that can read the screen must have no way to send what it reads
# anywhere. This script makes that promise fail the build if it is ever broken.
set -euo pipefail

apk="${1:?usage: verify-apk.sh <apk> [--release]}"
mode="${2:-}"
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[ -n "$sdk" ] || { echo "::error::Android SDK not found (ANDROID_HOME is not set)"; exit 1; }
bt="$sdk/build-tools/36.1.0"

echo "== Permissions requested by $(basename "$apk")"
dump="$("$bt/aapt2" dump permissions "$apk")"
echo "$dump"

if grep -q "android.permission.INTERNET" <<<"$dump"; then
  echo "::error::The APK requests INTERNET. AutoSkip must never have network access."
  exit 1
fi

requested="$(grep -E "^uses-permission" <<<"$dump" || true)"
if [ -n "$requested" ]; then
  echo "::error::The APK requests permissions, but AutoSkip promises to need none:"
  echo "$requested"
  exit 1
fi
echo "OK: no permissions requested."

echo "== Signature"
certs="$("$bt/apksigner" verify --print-certs "$apk")"
echo "$certs"
actual="$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' <<<"$certs" | tr 'A-F' 'a-f')"

if [ "$mode" = "--release" ]; then
  expected="$(tr -d '[:space:]' < "$(dirname "$0")/../release-cert-sha256" | tr 'A-F' 'a-f')"
  if [ "$actual" != "$expected" ]; then
    echo "::error::Signed with $actual, expected the release key $expected."
    exit 1
  fi
  echo "OK: signed with the AutoSkip release key."
fi
