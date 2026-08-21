#!/bin/bash
# 3-second javac census over the parvaz sources. Writes /tmp/jc.out, prints error count.
# Requires /opt/pb/cp.txt (from the gradle `dumpCp` task).
source /home/user/tools/env.sh
cd /opt/pb/work/app/src/main/java || exit 1
CP="/opt/pb/sdk/platforms/android-34/android.jar:$(cat /opt/pb/cp.txt)"
find . -name '*.java' > /tmp/jc.files
javac -d /tmp/jcout -encoding UTF-8 -source 17 -target 17 -nowarn \
      -Xmaxerrs 2000 -cp "$CP" @/tmp/jc.files > /tmp/jc.out 2>&1
grep -c "error:" /tmp/jc.out
