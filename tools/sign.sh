#!/bin/bash
# zipalign + apksigner (v1/v2/v3) using the permanent Parvaz key.
set -e
source /home/user/tools/env.sh
IN="$1"
OUT="$2"
zipalign -f -p 4 "$IN" /tmp/s2.apk
apksigner sign \
  --ks /home/user/.keys/parvaz.keystore \
  --ks-pass pass:parvaz2026 \
  --key-pass pass:parvaz2026 \
  --ks-key-alias parvaz \
  --min-sdk-version 24 \
  --v1-signing-enabled true \
  --v2-signing-enabled true \
  --v3-signing-enabled true \
  --out "$OUT" /tmp/s2.apk
rm -f "$OUT.idsig" /tmp/s2.apk
apksigner verify --print-certs "$OUT" | head -6
echo "SIGNED: $OUT  $(stat -c%s "$OUT") bytes"
