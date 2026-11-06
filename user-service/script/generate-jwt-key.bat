#!/usr/bin/env bash
# Generates an RSA-3072 signing key for the user-service.
# Usage:  bash scripts/generate-jwt-key.sh
# Store the JWT_PRIVATE_KEY value in your secret manager. NEVER commit it.
set -euo pipefail

tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out "$tmp" 2>/dev/null

echo "JWT_PRIVATE_KEY=$(openssl pkcs8 -topk8 -nocrypt -in "$tmp" -outform DER | openssl base64 -A)"
echo
echo "# Public half. Keep it: when you rotate to a new key, put this value into"
echo "# JWT_PREVIOUS_PUBLIC_KEYS so tokens signed with the old key stay valid until they expire."
echo "JWT_PUBLIC_KEY=$(openssl pkey -in "$tmp" -pubout -outform DER | openssl base64 -A)"
