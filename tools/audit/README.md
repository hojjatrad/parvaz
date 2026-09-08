# Lightweight parser characterization audit

Baseline: `0c2b08f78bbcd699558894dbeac2c05c53f08614` (Parvaz 1.18).

Run from the repository:

```sh
bash tools/audit/run.sh
```

Requires JDK 11+ and curl. Downloads the project's existing org.json test dependency
(version 20240303) from Maven Central into ignored `.cache/audit/`. No Android SDK,
actual subscription, VPN server or native core is used.

The harness compiles the actual Profile, LinkParser, ClashParser, SingBoxParser and
ProtocolSupport classes. Android Uri is a **compile-only stub that throws**; Android
Log is a no-op, and Base64 delegates to Java's standard implementation. Therefore
these results do not validate Android URI decoding, real share links, Android
activities, storage, native configuration acceptance or actual VPN connectivity.

Ten observations (including one healthy single-Xray-JSON baseline) are asserted.
`REPRODUCED` means the observation matches current behavior; it does NOT mean the
behavior is correct. These are characterization tests and will deliberately stop
matching after the corresponding bug is fixed. Convert each to a positive behavior
regression test when implementing that fix; do not use their present success as a
release quality gate. All sample domains/credentials are fictional.

Captured results: `docs/audit/parser-results.txt`.
Persian findings and implementation plan: `docs/audit/REVIEW-fa.md`.
