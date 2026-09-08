# Subscription regression suite (phases A + B)

Run from the repository root:

```sh
bash tools/audit/run.sh
```

Requires JDK 11+ (including keytool), curl and sha256sum on Linux. The runner downloads
org.json 20240303 and SnakeYAML 2.4 into ignored `.cache/audit/` and checks pinned
SHA-256 values. It needs neither an Android SDK nor a native VPN core.

## Actual code under test

- Profile, Subscription, the link/Clash/Sing-box parsers and shared schema helpers.
- Protocol capability checks and the actual Xray outbound builder.
- ProfileIdentity, SubscriptionReconciler, ProfileStore and SubscriptionRefresh.
- SubscriptionUpdater adapters and actual SubscriptionWorker.doWork logic.
- Actual SubscriptionHttpClient with deterministic fake HTTP cases and real loopback
  HTTP/HTTPS servers for protocol/certificate/hostname and Worker result tests.

The current suite passes **191 assertions**: 35 phase-A parser/builder assertions,
63 HTTP/TLS assertions, and 93 phase-B import/storage/refresh/Worker assertions.
Fixtures in `app/src/test/resources/import/` are synthetic schema examples, NOT real
panel credentials and NOT certification of any particular panel version.

## Test substitutes and limits

Context, SharedPreferences, Handler and WorkManager APIs are limited JVM substitutes.
SharedPreferences is an in-memory map with injectable apply failures: it is NOT an
Android disk durability/crash test. Handler runs callbacks immediately: it does NOT
validate main-thread lifecycle. WorkManager substitutes allow actual doWork result
logic to run, not Android scheduling, foreground-service or battery behavior.

Prefs provides builder defaults. GeoIndex methods throw because geographical routing
is outside this suite. Android Uri is a compile-only throwing stub; Base64 delegates
to Java, and Log is a no-op. JSON-style VMess share links are tested, but Android URI
semantics for VLESS/Trojan/SS/etc. still require Android/Robolectric tests.

The runner creates a fresh localhost-only TEST certificate under `.cache/audit`.
Loopback servers stop in finally; individual TLS test connections receive test trust
without changing global verifiers/trust stores. No live user panel is contacted.

The suite is not an APK build and does NOT establish native Xray acceptance, actual
VPN connectivity, Android UI, real disk persistence, Android TLS stores, or complete
Clash/Sing-box runtime compatibility. Existing Android/Robolectric tests still need a
complete toolchain. Some legacy raw generics produce javac unchecked warnings.

## Records and CI

- Latest output: `docs/audit/phase-b-results.txt`.
- Latest Persian status/limitations: `docs/progress/PHASE-B-fa.md`.
- Earlier outputs remain in `parser-results.txt` and `phase-a-results.txt` as historical
  records, not claims about the current implementation.
- `.github/workflows/subscription-regressions.yml` runs this suite on push/PR/manual
  dispatch with read-only repository permissions and three-day result retention.
  It does not build/sign/publish APKs and has not yet run remotely in this session.

The original audit at `b95a437` deliberately reproduced faulty behavior. Positive
regression tests replaced those characterization assertions when fixes were made;
`REPRODUCED` in that historical output does not describe current release readiness.
