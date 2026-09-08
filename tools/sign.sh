#!/usr/bin/env bash
# Sign with the EXISTING Parvaz release key injected securely by the operator/CI.
# Never copy the release key into this repository or put its password on the command line.
set +x
set -euo pipefail
if [[ $# -ne 2 ]]; then
  echo 'Usage: tools/sign.sh INPUT.apk OUTPUT.apk' >&2
  exit 2
fi
: "${PARVAZ_KEYSTORE_PATH:?PARVAZ_KEYSTORE_PATH is required}"
: "${PARVAZ_KEYSTORE_PASSWORD:?PARVAZ_KEYSTORE_PASSWORD is required}"
: "${PARVAZ_KEY_ALIAS:?PARVAZ_KEY_ALIAS is required}"
: "${PARVAZ_KEY_PASSWORD:?PARVAZ_KEY_PASSWORD is required}"
export PARVAZ_KEYSTORE_PASSWORD PARVAZ_KEY_PASSWORD
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/env.sh"
IN="$1"
OUT="$2"
[[ -f "$IN" && -f "$PARVAZ_KEYSTORE_PATH" ]] || { echo 'Input APK or signing key is missing' >&2; exit 2; }
umask 077
mkdir -p "$SCRIPT_DIR/../.cache/signing"
ALIGNED="$(mktemp "$SCRIPT_DIR/../.cache/signing/aligned-XXXXXX.apk")"
trap 'rm -f "$ALIGNED"' EXIT
zipalign -f -p 4 "$IN" "$ALIGNED"
apksigner sign \
  --ks "$PARVAZ_KEYSTORE_PATH" \
  --ks-pass env:PARVAZ_KEYSTORE_PASSWORD \
  --key-pass env:PARVAZ_KEY_PASSWORD \
  --ks-key-alias "$PARVAZ_KEY_ALIAS" \
  --min-sdk-version 24 \
  --v1-signing-enabled true \
  --v2-signing-enabled true \
  --v3-signing-enabled true \
  --out "$OUT" "$ALIGNED"
apksigner verify --print-certs "$OUT"
echo "SIGNED: $OUT  $(stat -c%s "$OUT") bytes"
