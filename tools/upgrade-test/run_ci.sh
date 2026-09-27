#!/usr/bin/env bash
# Bounded, public-fixture diagnostics even when preparation fails before Gradle.
set -uo pipefail
mkdir -p .cache/upgrade-evidence
bash tools/upgrade-test/run.sh >.cache/upgrade-evidence/fixture-run.log 2>&1
result=$?
tail -60 .cache/upgrade-evidence/fixture-run.log
if [ "$result" -ne 0 ]; then
  python3 - <<'PY'
from pathlib import Path
text=Path('.cache/upgrade-evidence/fixture-run.log').read_text(errors='replace')[-3000:]
print('::error title=UPGRADE_FIXTURE_STAGE::'+text.replace('%','%25').replace('\r','%0D').replace('\n','%0A'))
PY
fi
exit "$result"
