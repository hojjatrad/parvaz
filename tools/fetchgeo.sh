#!/bin/bash
# Download the Iran-tuned geo databases. The -lite files get bundled into the APK;
# the full ones are what GeoAssets fetches at runtime (downloaded here only to verify
# the URLs still resolve).
mkdir -p /opt/pb/geo
cd /opt/pb/geo
B=https://github.com/chocolate4u/Iran-v2ray-rules/releases/latest/download
for f in geoip-lite.dat geosite-lite.dat; do
  curl -sSL --retry 3 -o "$f" "$B/$f"
  echo "$f RC=$? $(stat -c%s "$f" 2>/dev/null) bytes"
done
