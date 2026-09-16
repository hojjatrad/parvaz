# Native helper packaging failure — VERIFIED

Run35071841227 on16aae94 with `--stacktrace` reports:
`java.lang.OutOfMemoryError: Java heap space` in Zipflinger
`NoCopyByteArrayOutputStream` / `Compressor.deflate` / `ApkFlinger.writeFile`.

The standalone helper had only `android.useAndroidX=true` in its own Gradle
properties, unlike the production app's explicitly sized heap. Packaging three
large native ELF files exhausted the helper's default heap. No native Android
test had run at that point. This is not evidence of a phone VPN/core failure.

Small correction: helper heap2048MiB, metaspace512MiB, no project parallelism,
max2 workers. No production APK/runtime memory setting is changed. Re-run both
SDK29 andSDK34 actual native-engine tests; do not mark them passed from host TLS
results. Earlier disk-pressure cleanup was precautionary, not a proven cause.

## Rerun result — verified
Run35072640666 on27427d7 completed SUCCESS:18 tests on SDK29 and18 on SDK34, zero failures/errors/skips. All three engines completed real HTTPS RTT with hostname checks/no direct fallback. Native lifecycle/cancellation/restart and Hysteria2/TUIC strict-TLS/TCP/UDP cases passed. Host protocol/authentication tests also passed. Public annotations are archived in `NATIVE-27427d7-RESULT.txt`. This is emulator native-engine evidence, not physical VPN/TUN/DNS proof.
