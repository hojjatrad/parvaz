# Group ping repair after owner testing of1.28.6-TEST

Owner reports cancelled and unconfirmed rows during GROUP ping with BOTH Parvaz and other VPN apps disconnected. Do not attribute this report to a second VPN, label all nodes offline, or fabricate positive results.

## Reproduced software fault (not a complete phone diagnosis)
MainActivity starts an automatic subscription refresh on resume/every5 minutes; WorkManager also refreshes in the background. That refresh replaces records or writes quota metadata. Both invalidate pending ProfileStore measurement tickets. A batch has tickets for queued as well as running rows. A fetch finishing after a manual test starts could therefore invalidate its tests without a user cancellation.

The new host regression failed against4664510 before the fix (`background fetch is deferred while a manual row is queued`). After the fix17 assertions pass: defer before fetch; defer in-flight full/partial/quota-only response commits; preserve ticket and genuine result; resume ordinary refresh when idle; preserve explicit-refresh invalidation and A→B→A source fencing. Real production store/refresh classes are tested with local Android API substitutes, not real user servers or device installation.

## Changes
- Automatic (non-waiting) refresh checks for pending manual measurements both before duplicate cleanup and immediately before commits. Each check+write is atomic under the same store monitor as beginMeasurement. No network operation is added under that monitor.
- The foreground automatic quota timer avoids starting a request while manual measurements exist. Deferred responses are retryable; no automatic report dialogs are added.
- Explicit user refresh/source changes retain strict ownership invalidation. No stale result is accepted just because endpoint text matches.
- Local engine startup exceptions, unprovable test routes, and network-scope changes now have distinct non-positive states instead of collapsing into generic “unconfirmed”. No TLS policy, target proof, native concurrency, or startup wait has been weakened/changed.

## Evidence
- Host suite:321 assertions, including17 new deferral/fencing checks. Release tooling:45 tests after adding the follow-up reservation test.
- Android source `ea122cd`: [35085573481](https://github.com/hojjatrad/parvaz/actions/runs/35085573481) SUCCESS,563 tests /0 failures /0 errors /0 skipped; debug compilation and release R8 passed.
- Exact visibly labelled preview source `d14268c`: [35085574848](https://github.com/hojjatrad/parvaz/actions/runs/35085574848) SUCCESS, same563 /0 /0 /0. ManualLatencyTest30 executions include queued/running and in-flight refresh on SDK29 and34; LatencyResultTest6 executions.
- Separate preview publication [35085574767](https://github.com/hojjatrad/parvaz/actions/runs/35085574767) SUCCESS from the exact preview source. Lint, unit tests, inventory, packaging, permanent-key signing and TEST publication passed.12 unresolved advisory coordinates remain reported; not security approval.
- Follow-up TEST version1.28.7/code41 is reserved. Any later final must exceed both (at least1.28.8/code42); do not promote an identically numbered preview.

## Delivered TEST, not final approval
- Public prerelease: https://github.com/hojjatrad/parvaz/releases/tag/test/v1.28.7-r1 ; immutable tag resolved independently over Git SSH to `d14268ce236a85299d6958cae84698ecec09a3b7`.
- Downloaded universal `Parvaz-1.28.7-TEST.apk`,88,938,083 bytes, SHA256 `5392bab6f8a362fafcc6200e741d17db0f8f0785abb60a4caefc555bc0a6d6bc`; delivered through the file viewer.
- Independent apksig verification on SDK29–35 matches permanent generation3 signer; manifest package `com.parvaz.tunnel`, version1.28.7/code41, nondebuggable. All six packaged native hashes match the release's hashed inventory.
- Latest independently still points to v1.28.5. Manual TEST is not discovered by the stable updater. Corresponding source and retained supply-chain review accompany the public release.
- REST API monitoring reached its anonymous rate limit and was stopped; public GitHub workflow/asset pages were used for completion and download verification, without requesting or using a PAT.

Remaining: actual owner retest of this new candidate, full signed-upgrade/data-preservation/device evidence, and outstanding security review. The cause of every “unconfirmed” phone row is NOT proven by this race reproduction; new statuses should narrow that diagnosis. Security review remains blocked for stable release independently of this ping fix.
