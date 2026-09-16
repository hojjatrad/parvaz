#!/usr/bin/env bash
# Isolated Android helper, not a full phone/VPN acceptance test.
set -euo pipefail
sdk="${1:?SDK label required}"
mkdir -p .cache
log=".cache/native-probe-${sdk}.log"
if ! ./gradlew -p tools/android-probe -PnativeProbe --no-daemon --max-workers=2 connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.parvaz.probe.NativeEngineTest >"$log" 2>&1; then
  tail -100 "$log"
  python3 - "$log" <<'PY'
import pathlib,sys
text=pathlib.Path(sys.argv[1]).read_text(errors='replace')[-3200:]
print('::error title=Native helper build and execution::'+text.replace('%','%25').replace('\r','%0D').replace('\n','%0A'))
PY
  exit 1
fi
tail -35 "$log"
