#!/usr/bin/env bash
# Checks SIGNING_KEY / SIGNING_KEY_ID / SIGNING_KEY_PASSWORD before Gradle uses them, and names the
# secret that is wrong; Gradle only reports "Could not read PGP secret key". The key may be
# ASCII-armored or the base64 one-liner kept in gradle.properties as signingInMemoryKey.
set -uo pipefail

fail() { echo "::error::$1"; exit 1; }

export GNUPGHOME="$(mktemp -d)"
trap 'rm -rf "$GNUPGHOME"' EXIT
key_file="$GNUPGHOME/key"

if [[ "$SIGNING_KEY" == *"BEGIN PGP PRIVATE KEY BLOCK"* ]]; then
  printf '%s\n' "$SIGNING_KEY" > "$key_file"
elif ! printf '%s' "$SIGNING_KEY" | tr -d '[:space:]' | base64 -d > "$key_file" 2>/dev/null; then
  fail "SIGNING_KEY is neither an ASCII-armored private key nor base64."
fi

gpg --batch --quiet --import "$key_file" 2>/dev/null \
  || fail "SIGNING_KEY is not a PGP private key."
gpg --batch --list-secret-keys --with-colons | grep -q '^sec' \
  || fail "SIGNING_KEY holds a public key only; export the private key."

key_id="$(printf '%s' "$SIGNING_KEY_ID" | tr -d '[:space:]' | tr '[:lower:]' '[:upper:]')"
key_ids="$(gpg --batch --list-secret-keys --with-colons | awk -F: '$1 == "sec" || $1 == "ssb" { print $5 }')"
grep -q "${key_id}\$" <<< "$key_ids" \
  || fail "SIGNING_KEY_ID does not match SIGNING_KEY, whose key IDs are: $(tr '\n' ' ' <<< "$key_ids")(the last 8 characters work too)."

echo ok | gpg --batch --quiet --pinentry-mode loopback --passphrase "$SIGNING_KEY_PASSWORD" \
  --local-user "$key_id" --sign > /dev/null 2>&1 \
  || fail "SIGNING_KEY_PASSWORD does not unlock SIGNING_KEY."

echo "Signing key ${key_id} is usable."
