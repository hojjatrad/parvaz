#!/usr/bin/env python3
"""Regenerate /opt/pb/work (a buildable Gradle project) from /opt/pb/dec + /opt/pb/src5.

WIPES /opt/pb/work. Copies:
  * resources + manifest + assets from the apktool output (dec/)
  * com/parvaz/** java sources from the jadx output (src5/)
  * the libv2ray AAR and its extracted jniLibs

Known collisions handled:
  * assets/dexopt/baseline.prof collides with Gradle's own -- dropped
  * apktool re-emits library resources that clash with the Maven AARs -- see
    fixres.py / striplibres.py, run separately after this
"""
import os
import shutil
import subprocess
import sys

DEC = "/opt/pb/dec"
SRC = "/opt/pb/src5/sources"
WORK = "/opt/pb/work"
AAR = "/opt/pb/aar/libv2ray.aar"


def rm(path):
    if os.path.isdir(path):
        shutil.rmtree(path)
    elif os.path.exists(path):
        os.remove(path)


def main():
    rm(WORK)
    app_main = os.path.join(WORK, "app/src/main")
    os.makedirs(os.path.join(app_main, "java"), exist_ok=True)
    os.makedirs(os.path.join(WORK, "app/libs"), exist_ok=True)

    # --- resources / manifest / assets ---------------------------------------
    shutil.copytree(os.path.join(DEC, "res"), os.path.join(app_main, "res"))
    shutil.copy(os.path.join(DEC, "AndroidManifest.xml"),
                os.path.join(app_main, "AndroidManifest.xml"))

    dec_assets = os.path.join(DEC, "assets")
    if os.path.isdir(dec_assets):
        shutil.copytree(dec_assets, os.path.join(app_main, "assets"))
    # Gradle generates its own baseline profile; apktool's copy collides.
    rm(os.path.join(app_main, "assets/dexopt"))

    # --- java sources ---------------------------------------------------------
    shutil.copytree(os.path.join(SRC, "com/parvaz"),
                    os.path.join(app_main, "java/com/parvaz"))

    # --- native libs ----------------------------------------------------------
    jni = os.path.join(WORK, "app/libs/jni")
    os.makedirs(jni, exist_ok=True)
    for abi in ("arm64-v8a", "armeabi-v7a"):
        src = os.path.join(DEC, "lib", abi)
        if os.path.isdir(src):
            shutil.copytree(src, os.path.join(jni, abi))

    shutil.copy(AAR, os.path.join(WORK, "app/libs/libv2ray.aar"))

    print("work tree created")
    for root, dirs, files in os.walk(os.path.join(app_main, "java")):
        pass
    count = subprocess.run(
        ["bash", "-c",
         "find %s/java -name '*.java' | wc -l" % app_main],
        capture_output=True, text=True).stdout.strip()
    print("java files:", count)


if __name__ == "__main__":
    main()
