# Subscription foundation regression tests

Run from the repository:

```sh
bash tools/audit/run.sh
```

Requires a JDK 11+ (including keytool), curl and sha256sum on Linux. Downloads the
project's org.json test dependency (20240303) from Maven Central into ignored
`.cache/audit/` and verifies its pinned SHA-256. No Android SDK or native core is used.

## What actually runs

- Actual Profile, LinkParser, ClashParser, SingBoxParser, ProtocolNames,
  CustomOutbound, ProtocolSupport and XrayConfigBuilder classes.
- Actual SubscriptionHttpClient, fake HTTP connections for deterministic policy
  checks, and an actual local HTTPS server for certificate/hostname tests.
- 35 parser/outbound assertions and 63 HTTP/TLS assertions: 98 in total.

Minimal Android Context/SharedPreferences/Prefs stubs return defaults. GeoIndex
methods throw because geographical routing is outside this suite. Android Uri is a
compile-only stub that throws; Base64 delegates to the Java standard implementation;
Log is a no-op. Standard JSON-style VMess share links are tested, but URI-based
VLESS/Trojan/SS/etc. parsing is NOT validated by these stubs.

The script creates a fresh localhost-only test certificate in `.cache/audit`. The
HTTPS server binds only to loopback and is stopped in finally. A trusted TLS context
is injected into individual test connections only; no global hostname verifier or
trust store is changed. No real panel/subscription/user server is contacted.

## Limitations

Passing this suite does NOT validate Android UI/lifecycle, Android Uri, preferences
persistence, native Xray config acceptance, geographical routing, Gradle packaging,
real VPN connectivity, Android TLS stores or battery/network-change behavior.
The existing Android/Robolectric suite still needs to run in a complete toolchain.
Full Clash YAML and complete Sing-box field mapping remain unfinished.

Current results: `docs/audit/phase-a-results.txt`.
Current Persian progress report: `docs/progress/PHASE-A-fa.md`.

## Historical audit

Audit commit `b95a437` had ten characterization observations, including nine defects
and one healthy baseline. That code deliberately asserted buggy behavior. The current
suite replaces it with positive regression assertions for the fixes made in this
branch. The historical output remains at `docs/audit/parser-results.txt`; do not
interpret its `REPRODUCED` lines as the current behavior or as release readiness.
