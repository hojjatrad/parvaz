#!/usr/bin/env bash
# Actual signed-APK upgrade fixture on a native arm64 Android 11 container.
# Requires: a running disposable container (name in PARVAZ_CONTAINER), adb on the
# host, release-artifacts/ holding the two signed candidate APKs, and the probe
# APKs in probe-apks/ (built on an x86_64 job because the Android SDK build tools
# have no linux-arm64 binaries).
set -euo pipefail

CONTAINER="${PARVAZ_CONTAINER:-redroid}"
SERIAL="${ANDROID_SERIAL:?ANDROID_SERIAL must pin the disposable container}"
HOST_IP="${PARVAZ_HOST_IP:-172.17.0.1}"
PRIOR="${PARVAZ_UPGRADE_PRIOR:-test42}"

mkdir -p .cache/upgrade-evidence .cache/upgrade-tls
VERSION=$(python3 -c 'import re;print(re.search(r"versionName\s+\"([^\"]+)\"",open("app/build.gradle").read())[1])')
CODE=$(python3 -c 'import re;print(re.search(r"versionCode\s+(\d+)",open("app/build.gradle").read())[1])')

# Ephemeral transport root, not the permanent APK signer. Never published.
openssl req -x509 -newkey rsa:2048 -nodes -days 1 \
  -keyout .cache/upgrade-tls/key.pem -out .cache/upgrade-tls/cert.pem \
  -subj '/CN=Parvaz disposable CI transport' \
  -addext 'basicConstraints=critical,CA:TRUE' \
  -addext 'subjectAltName=DNS:api.github.com,DNS:github.com' >/dev/null 2>&1

python3 -u tools/upgrade-test/fixture_proxy.py --artifacts release-artifacts --version "$VERSION" \
  --cert .cache/upgrade-tls/cert.pem --key .cache/upgrade-tls/key.pem \
  >.cache/upgrade-evidence/transport.log 2>&1 &
PROXY=$!
trap 'rm -f .cache/upgrade-tls/key.pem; kill "$PROXY" 2>/dev/null || true; adb -s "$SERIAL" shell settings put global http_proxy :0 >/dev/null 2>&1 || true' EXIT
sleep 3
grep -q STAGED_RELEASE_PROXY_READY .cache/upgrade-evidence/transport.log

python3 tools/upgrade-test/prepare_container.py --container "$CONTAINER" --serial "$SERIAL" \
  --cert .cache/upgrade-tls/cert.pem --host-ip "$HOST_IP" | tee .cache/upgrade-evidence/environment.log

# Immutable prior from the reviewed allowlist; never uninstall or reset app data.
python3 tools/upgrade-test/prior_release.py "$PRIOR" .cache/upgrade-evidence/prior.json
PRIOR_URL=$(python3 -c 'import json;print(json.load(open(".cache/upgrade-evidence/prior.json"))["url"])')
PRIOR_SHA=$(python3 -c 'import json;print(json.load(open(".cache/upgrade-evidence/prior.json"))["sha256"])')
PRIOR_CODE=$(python3 -c 'import json;print(json.load(open(".cache/upgrade-evidence/prior.json"))["version_code"])')
curl -fLsS --max-time 300 "$PRIOR_URL" -o .cache/upgrade-prior.apk
printf '%s  .cache/upgrade-prior.apk\n' "$PRIOR_SHA" | sha256sum -c

adb -s "$SERIAL" install --no-streaming .cache/upgrade-prior.apk
adb -s "$SERIAL" install -r --no-streaming probe-apks/probe.apk
adb -s "$SERIAL" install -r --no-streaming probe-apks/probe-test.apk
adb -s "$SERIAL" logcat -c

set +e
python3 tools/upgrade-test/instrument_results.py --serial "$SERIAL" \
  --arg "expectedCode=$CODE" --arg "priorCode=$PRIOR_CODE" --arg "hostIp=$HOST_IP"
STATUS=$?
set -e
adb -s "$SERIAL" logcat -d >.cache/upgrade-evidence/device.log
adb -s "$SERIAL" pull /sdcard/Android/data/com.parvaz.probe/files/parvaz-upgrade-evidence .cache/upgrade-evidence/ >/dev/null 2>&1 || true
adb -s "$SERIAL" shell dumpsys package com.parvaz.tunnel >.cache/upgrade-evidence/package.txt
[ "$STATUS" -eq 0 ] || exit "$STATUS"

grep -q 'STAGED_METADATA_REQUEST' .cache/upgrade-evidence/transport.log
grep -q 'SIGNED_CANDIDATE_TRANSFER_COMPLETE' .cache/upgrade-evidence/transport.log
grep -q "versionCode=$CODE" .cache/upgrade-evidence/package.txt
PID=$(adb -s "$SERIAL" shell pidof com.parvaz.tunnel | tr -d '\r')
test -n "$PID"
adb -s "$SERIAL" logcat -d --pid="$PID" >.cache/upgrade-evidence/app-device.log
if grep -E 'FATAL EXCEPTION|UnsatisfiedLinkError' .cache/upgrade-evidence/app-device.log; then
  echo 'Fatal/native load error during upgrade smoke'; exit 1
fi
export PARVAZ_FIXTURE_SCOPE="Unmodified permanent-signed ARM64 APKs executed natively on an Android 11 (API 30) arm64 container; staged exact-host HTTPS metadata; genuine in-app Update button and real system installer; no physical-handset assertion (no radio/vendor stack, SELinux not enforcing)"
python3 tools/upgrade-test/summarize.py
