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

Remaining: Android compilation/unit checks, candidate build, actual owner retest. The cause of every “unconfirmed” phone row is NOT proven by this race reproduction; new statuses should narrow that diagnosis. Security review remains blocked for stable release independently of this ping fix.
