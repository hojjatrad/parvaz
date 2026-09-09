#!/usr/bin/env bash
# Canonical setup: use an installed Android SDK; never download obsolete/unverified core binaries.
set -euo pipefail
cd "$(dirname "$0")/.."
: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-}}"
if [[ -z "$ANDROID_HOME" ]]; then echo 'Set ANDROID_HOME to your Android Studio/command-line SDK installation.' >&2; exit 1; fi
export ANDROID_HOME ANDROID_SDK_ROOT="$ANDROID_HOME"
SDKMANAGER="$(command -v sdkmanager || true)"
[[ -n "$SDKMANAGER" ]] || SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
[[ -x "$SDKMANAGER" ]] || { echo 'Install official Android SDK Command-line Tools first.' >&2; exit 1; }
./gradlew --version
"$SDKMANAGER" 'platforms;android-34' 'build-tools;34.0.0'
bash tools/release/fetch_core.sh
