#!/usr/bin/env bash
set -uo pipefail
mkdir -p .cache/launch-control
python3 tools/upgrade-test/launch_control.py >.cache/launch-control/run.log 2>&1
result=$?
cat .cache/launch-control/run.log
if [ "$result" -ne 0 ]; then
  python3 - <<'PYERROR'
from pathlib import Path
text=Path('.cache/launch-control/run.log').read_text(errors='replace')[-3000:]
print('::error title=ARM_LAUNCH_STAGE::'+text.replace('%','%25').replace('\n','%0A'))
p=Path('.cache/launch-control/environment.txt')
if p.exists():
 for line in p.read_text().splitlines():
  if any(line.startswith('['+x+']') for x in ['ro.kernel.qemu','ro.boot.qemu','ro.product.cpu.abilist','ro.product.cpu.abilist64','ro.dalvik.vm.native.bridge','ro.ndk_translation.version','ro.build.version.sdk','ro.hardware']):print('::notice title=ARM_ENVIRONMENT::'+line)
PYERROR
fi
exit "$result"
