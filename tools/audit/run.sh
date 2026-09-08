#!/usr/bin/env bash
# No Android SDK/native libraries; no actual subscription/server is contacted.
set -euo pipefail
cd "$(dirname "$0")/../.."
mkdir -p .cache/audit/classes
if [[ ! -f .cache/audit/json.jar ]]; then
  curl -fLsS https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar -o .cache/audit/json.jar
fi
echo '3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed  .cache/audit/json.jar' | sha256sum --check --status
javac -encoding UTF-8 -cp .cache/audit/json.jar -d .cache/audit/classes \
 $(find tools/audit/stubs -name '*.java') \
 app/src/main/java/com/parvaz/tunnel/model/Profile.java \
 app/src/main/java/com/parvaz/tunnel/config/{LinkParser,ClashParser,SingBoxParser,ProtocolNames,CustomOutbound,XrayConfigBuilder}.java \
 app/src/main/java/com/parvaz/tunnel/core/{ProtocolSupport,SubscriptionHttpClient}.java tools/audit/ParserAudit.java tools/audit/SubscriptionHttpClientTest.java
java -cp .cache/audit/classes:.cache/audit/json.jar ParserAudit
# Ephemeral loopback-only TEST certificate, never a release/app signing key.
rm -f .cache/audit/localhost.p12
KEYTOOL="$(command -v keytool || true)"
if [[ -z "$KEYTOOL" ]]; then
  KEYTOOL="$(dirname "$(readlink -f "$(command -v java)")")/keytool"
fi
"$KEYTOOL" -genkeypair -alias localhost -keyalg RSA -keysize 2048 -storetype PKCS12 \
 -keystore .cache/audit/localhost.p12 -storepass audit-only -keypass audit-only \
 -dname 'CN=localhost' -ext 'SAN=dns:localhost' -validity 2 -noprompt >/dev/null 2>&1
java -cp .cache/audit/classes:.cache/audit/json.jar com.parvaz.tunnel.core.SubscriptionHttpClientTest .cache/audit/localhost.p12
