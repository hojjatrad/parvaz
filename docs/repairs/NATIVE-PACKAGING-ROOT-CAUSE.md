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
