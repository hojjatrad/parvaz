#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
python3 tools/release/fetch_core.py
