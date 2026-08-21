#!/bin/bash
# Source before any gradle/javac/apktool/jadx invocation.
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_SDK_ROOT=/opt/pb/sdk
export ANDROID_HOME=/opt/pb/sdk
export GRADLE_USER_HOME=/opt/pb/.gradle
export PATH=$JAVA_HOME/bin:/opt/pb/tools/gradle-8.9/bin:/opt/pb/sdk/build-tools/34.0.0:/opt/pb/sdk/platform-tools:$PATH
