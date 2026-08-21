#!/bin/bash
# Rebuild /opt/pb/dec (apktool resources) and /opt/pb/src5 (jadx sources, real names).
set -x
source /home/user/tools/env.sh
mkdir -p /opt/pb/tmpwork

APK=/home/user/Parvaz-1.7.apk

# --- resources (apktool). Never use -r: res must stay editable. ---
rm -rf /opt/pb/dec
java -Djava.io.tmpdir=/opt/pb/tmpwork -jar /opt/pb/tools/apktool.jar d \
     -f -o /opt/pb/dec "$APK"
echo "APKTOOL_RC=$?"

# apktool re-emits a baseline profile that collides with Gradle's own.
rm -rf /opt/pb/dec/assets/dexopt

# --- mapping -> Enigma ---
python3 /home/user/tools/r8_to_enigma.py /opt/pb/mapping17.txt /opt/pb/parvaz.mapping
echo "ENIGMA_RC=$?"

# --- sources (jadx with the Enigma mapping applied) ---
rm -rf /opt/pb/src5
/opt/pb/tools/jadx/bin/jadx \
    --mappings-path /opt/pb/parvaz.mapping \
    --mappings-mode read \
    -d /opt/pb/src5 "$APK" > /tmp/jadx.log 2>&1
echo "JADX_RC=$?"
tail -5 /tmp/jadx.log
ls /opt/pb/src5/sources/com/parvaz/tunnel/ | head
find /opt/pb/src5/sources/com/parvaz -name '*.java' | wc -l
echo "DECOMPILE_DONE"
