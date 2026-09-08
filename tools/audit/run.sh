#!/usr/bin/env bash
# No Android SDK/native libraries; no actual subscription/server is contacted.
set -euo pipefail
cd "$(dirname "$0")/../.."
mkdir -p .cache/audit/classes
if [[ ! -f .cache/audit/json.jar ]]; then
  curl -fLsS https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar -o .cache/audit/json.jar
fi
javac -encoding UTF-8 -cp .cache/audit/json.jar -d .cache/audit/classes \
 tools/audit/stubs/android/net/Uri.java tools/audit/stubs/android/util/*.java \
 app/src/main/java/com/parvaz/tunnel/model/Profile.java \
 app/src/main/java/com/parvaz/tunnel/config/{LinkParser,ClashParser,SingBoxParser}.java \
 app/src/main/java/com/parvaz/tunnel/core/ProtocolSupport.java tools/audit/ParserAudit.java
java -cp .cache/audit/classes:.cache/audit/json.jar ParserAudit
