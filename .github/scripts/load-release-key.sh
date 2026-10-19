#!/usr/bin/env bash
# Turns the two signing secrets into a key file Gradle can use, and checks them first.
#
# Reads:   KEYSTORE_BASE64    (secret AUTOSKIP_KEYSTORE_BASE64)
#          KEYSTORE_PASSWORD  (secret AUTOSKIP_KEYSTORE_PASSWORD)
# Writes:  $RUNNER_TEMP/autoskip-release.p12, and sets AUTOSKIP_KEYSTORE and
#          AUTOSKIP_KEYSTORE_PASSWORD for the following steps.
#
# Copy-paste slips are forgiven: spaces, line breaks, and the BEGIN/END marker lines from
# the backup file are removed. Anything else wrong is reported as an error that says which
# secret to fix, without ever printing a secret.
set -euo pipefail

fail() { echo "::error title=Signing key::$1"; exit 1; }

b64="${KEYSTORE_BASE64:-}"
pw="${KEYSTORE_PASSWORD:-}"
[ -n "$b64" ] || fail "The AUTOSKIP_KEYSTORE_BASE64 secret is missing or empty. Add it under Settings > Secrets and variables > Actions."
[ -n "$pw" ]  || fail "The AUTOSKIP_KEYSTORE_PASSWORD secret is missing or empty. Add it under Settings > Secrets and variables > Actions."

# Forgive the usual copy-paste extras.
b64="$(printf '%s' "$b64" | sed -E 's/-----(BEGIN|END)[^-]*-----//g' | tr -d '[:space:]')"
pw="$(printf '%s' "$pw" | tr -d '[:space:]')"
echo "::add-mask::$pw"
[ -n "$b64" ] || fail "AUTOSKIP_KEYSTORE_BASE64 holds only the BEGIN/END marker lines or blank space. The long line of the key itself is missing: paste it as the secret's value."

bad="$(printf '%s' "$b64" | tr -d 'A-Za-z0-9+/=' | wc -c | tr -d ' ')"
if [ "$bad" -gt 0 ]; then
  fail "AUTOSKIP_KEYSTORE_BASE64 contains $bad character(s) that can't be part of the key (length ${#b64}). Paste only the long line between the BEGIN and END markers."
fi
if [ $(( ${#b64} % 4 )) -ne 0 ]; then
  fail "AUTOSKIP_KEYSTORE_BASE64 looks cut off (length ${#b64}, should be a multiple of 4). Copy the whole long line again."
fi

key="$RUNNER_TEMP/autoskip-release.p12"
printf '%s' "$b64" | base64 -d > "$key" 2>/dev/null \
  || fail "AUTOSKIP_KEYSTORE_BASE64 could not be decoded (length ${#b64}). Copy the whole long line again."

# A PKCS12 file is one DER SEQUENCE: byte 0x30, then its length. Use that to tell a
# truncated paste apart from a wrong password.
read -r b0 b1 b2 b3 b4 < <(head -c 5 "$key" | od -An -tu1; echo)
size="$(wc -c < "$key" | tr -d ' ')"
looks_like_key=false
if [ "${b0:-}" = 48 ]; then
  case "${b1:-}" in
    130) want=$(( (${b2:-0} << 8 | ${b3:-0}) + 4 )) ;;
    131) want=$(( (${b2:-0} << 16 | ${b3:-0} << 8 | ${b4:-0}) + 5 )) ;;
    *)   want=0 ;;
  esac
  if [ "$want" -gt 0 ] && [ "$size" -lt "$want" ]; then
    fail "AUTOSKIP_KEYSTORE_BASE64 looks cut off (it holds $size of $want bytes). Copy the whole long line again."
  fi
  [ "$want" -gt 0 ] && looks_like_key=true
fi

if ! listing="$(keytool -list -v -keystore "$key" -storetype PKCS12 -storepass "$pw" 2>/dev/null)"; then
  if [ "$looks_like_key" = true ]; then
    fail "AUTOSKIP_KEYSTORE_PASSWORD does not unlock the key. Paste the 32-character password from the backup file again (password length received: ${#pw})."
  fi
  hint="It is ${#b64} characters long; the key is about 3,500."
  if [ "$b64" = "$pw" ]; then
    hint="It holds the same value as AUTOSKIP_KEYSTORE_PASSWORD, so the password was pasted into both."
  elif [ "${#b64}" -eq 64 ] && [[ "$b64" =~ ^[0-9a-fA-F]+$ ]]; then
    hint="It holds the 64-character certificate fingerprint, not the key."
  elif [ "${#b64}" -lt 200 ] && [ "${#pw}" -gt 1000 ]; then
    hint="The two secrets look swapped: the key is in AUTOSKIP_KEYSTORE_PASSWORD."
  elif [ "${#b64}" -lt 200 ]; then
    hint="It is only ${#b64} characters long (the key is about 3,500), so it is probably the password or another short line from the file."
  fi
  fail "AUTOSKIP_KEYSTORE_BASE64 is not a valid key file. $hint Paste the long line between the BEGIN and END markers."
fi

actual="$(sed -n 's/^[[:space:]]*SHA256: //p' <<<"$listing" | head -1 | tr -d ':' | tr 'A-F' 'a-f')"
expected="$(tr -d '[:space:]' < "$(dirname "$0")/../release-cert-sha256" | tr 'A-F' 'a-f')"
[ "$actual" = "$expected" ] \
  || fail "The key in AUTOSKIP_KEYSTORE_BASE64 is a different key from the AutoSkip release key (fingerprint $actual, expected $expected)."

{
  echo "AUTOSKIP_KEYSTORE=$key"
  echo "AUTOSKIP_KEYSTORE_PASSWORD=$pw"
} >> "$GITHUB_ENV"
echo "OK: release key loaded and matches .github/release-cert-sha256."
