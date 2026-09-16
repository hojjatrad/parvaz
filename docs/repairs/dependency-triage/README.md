# Full dependency scan — release remains BLOCKED

Run35073227338 /31ee0a8 completed the first full Java/Go graph scan:758 coordinates,10 matched dependency coordinates, with multiple advisory aliases. It correctly stopped before signing or actual APK installation. Java-only87-component review had no known matches. A source-module match does not establish that the module is linked into the Android APK.

Raw public Go advisory JSONs are retained here, not suppressed. No runtime-reachable finding has been waived.

The next validation additionally inventories the embedded Go compiler and actual dependency/version tables of all six shipped ARM ELF inputs (Xray JNI, sing-box, Mihomo × ARM64/ARM32). Unknown/missing metadata fails closed. Every actually linked module/compiler is queried, even when it differs from source go.mod. All original source graph matches remain in the report. Only a coordinate absent from every complete shipped binary table may be marked NOT_IN_ANY_SHIPPED_BINARY_MODULE_TABLE; matching coordinates present in a binary remain REVIEW_REQUIRED, not automatically waived by an assumed feature flag.

APK packaging must then match all six vulnerability-scanned ELF hashes exactly. Any unresolved match or differing native bytes blocks publication.

## Verified local inspection
The pinned official Xray AAR SHA256 matched core-lock.json. Its ARM64 JNI ELF reports Go1.27.1 and50 dependency records. The actual wrapper root is a gomobile local `(devel)` replacement. Only that explicitly pinned official AAR root is mapped to its separately pinned source tag/commit; arbitrary local dependency replacements are rejected. This records the official AAR/source relationship, not a claim of reproducible source-to-binary equivalence. Real ARM64 build metadata is retained as JSON here. The new six-binary review has NOT yet completed in CI.

A source-PURL formatting bug in5f97b88 (the locks use owner/repository rather than full HTTPS URLs) is corrected and covered by both input-shape tests. No dependency or production engine version was changed merely to silence the scan.

## Further actual-binary findings — do not waive the release
The immutable published1.28.5 universal APK was downloaded and SHA256 checked against its public release manifest. All six prior ELF module tables were read without executing an engine. Of the first10 matched coordinates,9 exact coordinates were absent from those prior tables; chi/v5@v5.2.5 was present in both sing-box ABIs. Absence of a coordinate is NOT sufficient where a fork replaces it: the scanner now preserves and queries conservative upstream replacement coordinates as well as actual fork coordinates. Fork patch applicability requires separate review.

A separately installed official `golang.org/x/vuln/cmd/govulncheck@v1.8.0` binary-mode symbol scan of PRIOR1.28.5 ARM64 sing-box produced14 advisory IDs, including symbol-level matches for chi middleware, x/crypto, x/mod and gRPC. Raw JSON stream, tool identity, summary and advisory records are retained. Binary symbol presence does not establish an exploitable call path; it does establish that an unqualified clean-security claim is unsupported. These are PRIOR-binary results, not a completed candidate scan. No runtime finding has been waived.

A direct OSV query independently matched x/crypto@v0.54.0 and grpc@v1.79.1, demonstrating why the source `go list -m all` graph alone was insufficient to inventory actually compiled versions. Do not assume scanner-feed mismatch was the cause.

Latest official stable sources were inspected but NOT promoted: sing-box1.14.1 (1ac1a339cb1223e9c70eae14c44411c75033c02d) still declares chi5.2.5, crypto0.54.0 and grpc1.79.1; Mihomo1.19.31 (ab405bad5beeeac8b003bb01f60f134f6df54471) declares upstream crypto0.33.0, with fork applicability requiring review. A blind official-tag upgrade therefore does not close these findings.

Next: complete candidate binary review; determine affected call paths/fork patches; implement the necessary bounded remediation and rerun native/protocol/Android/security gates; then execute real signed APK upgrade and review screenshots. Only after that can a final APK/release be delivered. Current status remains BLOCKED; no new release/tag/main update or signer rotation.
