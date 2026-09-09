#!/usr/bin/env bash
# No privileged changes or unverified tool downloads. Install JDK 17 and Android SDK first.
set -euo pipefail
exec bash "$(dirname "$0")/setup_android.sh" "$@"
