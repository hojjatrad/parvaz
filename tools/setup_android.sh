#!/bin/bash
# Bootstrap the Android build toolchain in /opt/pb after a sandbox restart.
# Assumes: JDK 21 installed, swap on, /opt/pb exists and is owned by user.
set -x
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
mkdir -p /opt/pb/tools /opt/pb/sdk /opt/pb/tmpwork /opt/pb/dl

cd /opt/pb/dl

# --- Gradle 8.9 ---
if [ ! -d /opt/pb/tools/gradle-8.9 ]; then
  wget -q https://services.gradle.org/distributions/gradle-8.9-bin.zip
  unzip -q gradle-8.9-bin.zip -d /opt/pb/tools
fi

# --- Android commandline tools ---
if [ ! -d /opt/pb/sdk/cmdline-tools ]; then
  wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O cmdline.zip
  mkdir -p /opt/pb/sdk/cmdline-tools
  unzip -q cmdline.zip -d /opt/pb/sdk/cmdline-tools
  mv /opt/pb/sdk/cmdline-tools/cmdline-tools /opt/pb/sdk/cmdline-tools/latest
fi

export ANDROID_SDK_ROOT=/opt/pb/sdk
export ANDROID_HOME=/opt/pb/sdk
export PATH=$PATH:/opt/pb/sdk/cmdline-tools/latest/bin

yes | sdkmanager --licenses > /dev/null 2>&1
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"

# --- apktool + jadx (for decompiling the shipped APK) ---
cd /opt/pb/tools
if [ ! -f apktool.jar ]; then
  wget -q https://github.com/iBotPeaches/Apktool/releases/download/v2.9.3/apktool_2.9.3.jar -O apktool.jar
fi
if [ ! -d jadx ]; then
  wget -q https://github.com/skylot/jadx/releases/download/v1.5.0/jadx-1.5.0.zip -O jadx.zip
  mkdir -p jadx && unzip -q jadx.zip -d jadx
  chmod +x jadx/bin/jadx
fi

# --- libv2ray.aar (Xray core) ---
mkdir -p /opt/pb/aar
if [ ! -f /opt/pb/aar/libv2ray.aar ]; then
  wget -q https://github.com/2dust/AndroidLibXrayLite/releases/download/v26.7.31/libv2ray.aar -O /opt/pb/aar/libv2ray.aar
fi

echo "SETUP_RC=$?"
ls -la /opt/pb/tools /opt/pb/sdk /opt/pb/aar
