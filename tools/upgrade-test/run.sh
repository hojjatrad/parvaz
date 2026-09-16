#!/usr/bin/env bash
set -euo pipefail
mkdir -p .cache/upgrade-evidence .cache/upgrade-tls
VERSION=$(python3 -c 'import re;print(re.search(r"versionName\s+\"([^\"]+)\"",open("app/build.gradle").read())[1])')
CODE=$(python3 -c 'import re;print(re.search(r"versionCode\s+(\d+)",open("app/build.gradle").read())[1])')
# Ephemeral transport root, not the permanent APK signer. Never publish its key.
openssl req -x509 -newkey rsa:2048 -nodes -days 1 -keyout .cache/upgrade-tls/key.pem -out .cache/upgrade-tls/cert.pem -subj '/CN=Parvaz disposable CI transport' -addext 'basicConstraints=critical,CA:TRUE' -addext 'subjectAltName=DNS:api.github.com,DNS:github.com' >/dev/null 2>&1
python3 -u tools/upgrade-test/fixture_proxy.py --artifacts release-artifacts --version "$VERSION" --cert .cache/upgrade-tls/cert.pem --key .cache/upgrade-tls/key.pem >.cache/upgrade-evidence/transport.log 2>&1 &
PROXY=$!
trap 'kill "$PROXY" 2>/dev/null || true; adb shell settings put global http_proxy :0 >/dev/null 2>&1 || true' EXIT
python3 tools/upgrade-test/prepare_emulator.py .cache/upgrade-tls/cert.pem | tee .cache/upgrade-evidence/environment.log
# Prior published release remains immutable, never repackaged or re-signed.
curl -fLsS --max-time 180 https://github.com/hojjatrad/parvaz/releases/download/v1.28.5/Parvaz-1.28.5-arm64.apk -o .cache/upgrade-prior.apk
echo '71d99e7ab114639e85e5e2db21494ff34fa95fd4cc91719dac4b6baf11776205 .cache/upgrade-prior.apk' | sha256sum -c
adb install --no-streaming .cache/upgrade-prior.apk
adb logcat -c
if ! ./gradlew -p tools/android-probe -PupgradeProbe --no-daemon --max-workers=2 --stacktrace connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.parvaz.probe.PublishedUpgradeTest -Pandroid.testInstrumentationRunnerArguments.expectedCode="$CODE" >.cache/upgrade-evidence/gradle.log 2>&1; then
  python3 - <<'PY'
from pathlib import Path
text=Path('.cache/upgrade-evidence/gradle.log').read_text(errors='replace')[-2800:]
print('::error title=Actual signed APK upgrade test::'+text.replace('%','%25').replace('\n','%0A'))
PY
  adb logcat -d >.cache/upgrade-evidence/device.log
  exit 1
fi
adb logcat -d >.cache/upgrade-evidence/device.log
adb pull /sdcard/Android/data/com.parvaz.probe/files/parvaz-upgrade-evidence .cache/upgrade-evidence/ >/dev/null
adb shell dumpsys package com.parvaz.tunnel > .cache/upgrade-evidence/package.txt
grep -q 'STAGED_METADATA_REQUEST' .cache/upgrade-evidence/transport.log
grep -q 'SIGNED_CANDIDATE_TRANSFER_COMPLETE' .cache/upgrade-evidence/transport.log
grep -q "versionCode=$CODE" .cache/upgrade-evidence/package.txt
PID=$(adb shell pidof com.parvaz.tunnel | tr -d '\r')
test -n "$PID"
adb logcat -d --pid="$PID" >.cache/upgrade-evidence/app-device.log
if grep -E 'FATAL EXCEPTION|UnsatisfiedLinkError' .cache/upgrade-evidence/app-device.log; then echo 'Fatal/native load error during upgrade smoke';exit 1;fi
python3 tools/upgrade-test/summarize.py
