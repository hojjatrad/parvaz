# Full dependency scan — release remains BLOCKED

Run35073227338 /31ee0a8 completed the first full Java/Go graph scan:758 coordinates,10 matched dependency coordinates, with multiple advisory aliases. It correctly stopped before signing or actual APK installation. Java-only87-component review had no known matches. A source-module match does not establish that the module is linked into the Android APK.

Raw public Go advisory JSONs are retained here, not suppressed. No runtime-reachable finding has been waived.

The next validation additionally inventories the embedded Go compiler and actual dependency/version tables of all six shipped ARM ELF inputs (Xray JNI, sing-box, Mihomo × ARM64/ARM32). Unknown/missing metadata fails closed. Every actually linked module/compiler is queried, even when it differs from source go.mod. All original source graph matches remain in the report. Only a coordinate absent from every complete shipped binary table may be marked NOT_IN_ANY_SHIPPED_BINARY_MODULE_TABLE; matching coordinates present in a binary remain REVIEW_REQUIRED, not automatically waived by an assumed feature flag.

APK packaging must then match all six vulnerability-scanned ELF hashes exactly. Any unresolved match or differing native bytes blocks publication.

## Verified local inspection
The pinned official Xray AAR SHA256 matched core-lock.json. Its ARM64 JNI ELF reports Go1.27.1 and50 dependency records. The actual wrapper root is a gomobile local `(devel)` replacement. Only that explicitly pinned official AAR root is mapped to its separately pinned source tag/commit; arbitrary local dependency replacements are rejected. This records the official AAR/source relationship, not a claim of reproducible source-to-binary equivalence. Real ARM64 build metadata is retained as JSON here. The new six-binary review has NOT yet completed in CI.

A source-PURL formatting bug in5f97b88 (the locks use owner/repository rather than full HTTPS URLs) is corrected and covered by both input-shape tests. No dependency or production engine version was changed merely to silence the scan.
