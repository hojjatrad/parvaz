# Actual APK upgrade and publication gate

Version1.28.6/code40 remains unpublished until actual validation finishes.

- Disposable Android30 Google APIs x86_64 emulator must expose ARM64 native translation. Install immutable, hash-verified published1.28.5/code39 as baseline.
- Separate debug helper UID, NOT permanently signed instrumentation, drives a synthetic profile import, the real app Update button and Android's installer confirmation. The candidate APK itself is unchanged and signed with generation3.
- Temporary emulator-only CA stages exact GitHub HTTPS metadata and the unchanged APK bytes. Background checks initially see1.28.5; an explicit test-control socket enables the candidate before the Update button. No production cleartext/TLS policy is changed. This is not public-release discovery proof.
- Require exactly one passing upgrade test, version40, retained synthetic profile, installer interaction, metadata and APK transfer, and no candidate-process fatal/JNI load error. Capture actual small/tablet Persian UI screenshots for review.
- Publisher now calls candidate signing/installation, native SDK29/34, and archive/recovery SDK29/34 as required jobs. Publication reuses the exact installed candidate bytes, not a rebuild. Source-tree sealing, dependency review, signer/package/ABI checks and APK hashes must match.
- All assets upload to a draft before it is made latest. Stable1.28.5 is never overwritten. Anonymous public latest/download/hash verification still follows publication.
- `qa/upgrade-evidence` contains only an allowlist of synthetic screenshots/UI trees, summary and public dependency inventory; no APKs, signing key, temporary CA key, raw device log or private user data. Evidence presence is not approval.
- Local checks:4 staged-transport tests (dummy bytes, not APK proof);5 publication identity/gating tests;6 dependency-tool tests;41 total release-tool tests. Workflow syntax validated with actionlint1.7.7.

Known/unrun: physical devices, full production VPN/TUN/DNS/IPv6/Doze/handover; those must not be inferred from an installation test. Paused DNS lab stays PROMOTION_BLOCKED. The real upgrade test and visual review are still pending at this commit.
