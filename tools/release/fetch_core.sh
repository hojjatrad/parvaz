#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
mkdir -p app/libs
CORE='app/libs/libv2ray.aar'
SHA='a6b51b525b72a114c10e34288392439b1897c9ff7ce2f8afd7ffdce9d91e93b3'
if ! [[ -f "$CORE" ]] || ! echo "$SHA  $CORE" | sha256sum --check --status; then
  curl --fail --location --silent --show-error --retry 2 \
    'https://github.com/2dust/AndroidLibXrayLite/releases/download/v26.7.31/libv2ray.aar' \
    -o "$CORE"
fi
echo "$SHA  $CORE" | sha256sum --check --status
printf 'Pinned Xray core verified.\n'
