#!/usr/bin/env bash
# Isolated Android helper, not a full phone/VPN acceptance test.
set -euo pipefail
sdk="${1:?SDK label required}"
mkdir -p .cache
log=".cache/native-probe-${sdk}.log"
if ! ./gradlew -p tools/android-probe -PnativeProbe --no-daemon --max-workers=2 --stacktrace connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.parvaz.probe.NativeEngineTest >"$log" 2>&1; then
  tail -100 "$log"
  python3 - "$log" <<'PY'
import pathlib,sys
full=pathlib.Path(sys.argv[1]).read_text(errors='replace')
causes=[line for line in full.splitlines() if any(k in line for k in ('Caused by:', 'Suppressed:', 'No space left', 'Duplicate', 'Failed to'))]
text=('\n'.join(causes[-12:])+'\n'+full[-1700:])[-3400:]
print('::error title=Native helper build and execution::'+text.replace('%','%25').replace('\r','%0D').replace('\n','%0A'))
PY
  exit 1
fi
tail -35 "$log"
