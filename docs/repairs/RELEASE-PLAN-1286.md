# 1.28.6 release plan — 2026-09-16

User explicitly requests completed necessary checks, a signed APK, GitHub publication and discovery/installation through the app update button. This authorizes publication after gates; it does not authorize claiming unrun tests passed or rotating the permanent signer.

## Discovery and scope (VERIFIED repository inspection)
Android Java/Gradle/AGP8.5.2, AndroidX/Material, Xray JNI plus isolated sing-box/Mihomo executables. Persian/English resources, RTL Android layouts. Encrypted SharedPreferences records and recovery journal; no SQL/web backend. GitHub Actions build/sign/source/publication pipeline and HTTPS GitHub update integration. Previously completed audit and repair diffs are the starting point; no wholesale rewrite.

New user attachments: README plus23 engineering rule files00–22 all read. PHP/web-server/database-specific checks are N/A here. Phone access remains unavailable; emulator/helper evidence must not be described as physical-device proof.

## MUST / acceptance gates
1. Diagnose the native helper packageDebug failure with actual nested cause; smallest fix, then real engine emulator tests SDK29/34. No guesses based on truncated errors.
2. Latest Android compile/unit/R8/lint, host regression and targeted repair tests pass. Preserve raw evidence tied to source commit.
3. Inventory resolved Java runtime and native Go module graphs, scan known vulnerabilities, triage any matches, publish SBOM/scan scope. No blind dependency upgrade.
4. Exercise install/upgrade and critical production UI/update contracts in a representative Android environment; distinguish actual APK install from source-equivalent helper tests. Verify expected version, application ID, source, permanent signer and data-preserving update behavior.
5. Verify native contents, both final APK hashes and source bundle. Generation3 key is immutable. Existing release1.28.5 and archived source/user data remain intact.
6. Publish a new immutable1.28.6 release only when critical software gates pass. Confirm anonymous latest-release metadata and both download URLs agree with UpdateChecker selection, package/signature/version rules. Download the final APK for the user.

## SHOULD
RTL/small-screen regression of modified controls, clear permission/error states, bounded work/cleanup and source ownership review. Existing DNS full-chain lab stays PROMOTION_BLOCKED; never transform UNKNOWN leak status into a safety claim.

## COULD / not required for this release
New connection-detail dashboard, optional report editor, optional pre-upgrade backup UI, broader OEM device matrix and broad MainActivity decomposition. These must not delay essential corrections or be claimed implemented without evidence.

## Architecture boundaries
LockedActivity/AppLock: UI authorization. ProfileStore: encrypted data/atomic undo/source fencing. ProxyMeasurement/VerifiedProbe: authenticated selected-route measurements. GeoAssets/GeoData: immutable bounded validated rules. NetworkAutomation: process-lifetime rules, explicit Android background-start constraints. UpdateFlow/UpdateInstallActivity/UpdateChecker: offer, transfer, verify and installer handoff. CI signing: separate permanent-secret boundary.

## Ordered work / rollback
Diagnose failing packaging → fix/test → dependency inventory/scan → installation/update verification → final review → signed artifacts → immutable publication → anonymous verification. Keep small reviewable commits on fix/audit-repairs; CI refs do not publish. Revert individual changes before release if necessary; never replace old release assets or delete encrypted records. Final status stays BLOCKED while a critical gate is failed/unknown.
