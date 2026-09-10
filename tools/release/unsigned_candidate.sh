#!/usr/bin/env bash
# Secret-free phase only. A failure exposes bounded build/artifact diagnostics.
set -Eeuo pipefail
exec 3>&1
mkdir -p .cache
on_failure() {
  python3 - >&3 <<'PY'
from pathlib import Path
text=Path('.cache/unsigned-candidate.log').read_text(errors='replace')[-6500:]
print('::error title=UNSIGNED_CANDIDATE_FAILURE::'+text.replace('%','%25').replace('\r','%0D').replace('\n','%0A'))
PY
}
trap on_failure ERR
{
  ./gradlew --no-daemon --max-workers=2 :app:assembleRelease
  find app/build/outputs/apk/release -maxdepth 1 -name '*.apk' -printf '%f %s\n'
  python3 tools/native/verify_packaging.py
  VERSION=$(python3 -c 'import re;print(re.search(r"versionName\s+\"([^\"]+)\"",open("app/build.gradle").read())[1])')
  python3 tools/release/core_candidate_artifacts.py pack "$VERSION"
  python3 tools/release/core_candidate_artifacts.py verify "$VERSION"
} > .cache/unsigned-candidate.log 2>&1
cat .cache/unsigned-candidate.log
