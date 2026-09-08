# Release signing and Android 10+ validation

This branch is work in progress, NOT a released APK. Version code/name remain 18/1.18.
Device validation targets Android 10 (API 29) and above; minSdk 24 is unchanged to
avoid unnecessarily dropping existing users. Native/Gradle/device validation is pending.

## Signing secrets

Provide these through a trusted CI secret store or a private operator environment:

- `PARVAZ_KEYSTORE_PATH`: path to the existing release keystore outside this repository
- `PARVAZ_KEYSTORE_PASSWORD`
- `PARVAZ_KEY_ALIAS`
- `PARVAZ_KEY_PASSWORD`

Never put values in chat, Git, shell command history, screenshots, or public build logs.
Do not check in `local.properties`, `.env`, signing properties, keystores, backups,
or real subscription fixtures. Do not enable shell tracing in signing jobs.

`app/build.gradle` reads these environment variables. Release packaging/signing tasks
have a prerequisite that rejects missing credentials; debug/test configuration does
not need the release key. **The Gradle task integration has not yet been executed
against a complete Android SDK build on this branch.**

`tools/sign.sh INPUT.apk OUTPUT.apk` uses the same environment variables. Passwords
are passed to apksigner using its `env:` mechanism, not literal command-line arguments.
The script still uses the legacy toolchain locations in `tools/env.sh`; portable
bootstrap/Gradle Wrapper setup is a separate work item.

Use the SAME private signing key as the installed release to preserve normal Android
upgrade compatibility. Merely removing a password from source does not remove it from
Git history. If the private key itself was exposed, password rotation does not undo
that exposure; investigate key custody and an appropriate signing-key migration.
Do not force-push a rewritten history without owner approval and verified backups.

## Lightweight tests (no Android SDK)

```sh
bash tools/audit/run.sh
python3 tools/workspace_guard.py
```

The test suite requires a JDK, curl and sha256sum. It verifies the pinned org.json JAR
hash and creates a fresh, localhost-only test certificate under ignored `.cache/audit`.
That certificate is NOT an application signing key and must never be used in production.

These tests compile actual parsers, Profile, outbound builder and subscription HTTP
transport with minimal Android/Prefs/GeoIndex stubs. They exercise desktop JVM HTTP/TLS,
NOT Android UI, Android Uri, native Xray, full Gradle packaging or device networking.
The existing Android/Robolectric tests still need to be run with the correct toolchain.

## Sharing the work without providing a token

The combined mailbox patch `parvaz-review-and-phase-a.patch` is based on the public
1.18 commit `0c2b08f78bbcd699558894dbeac2c05c53f08614`. It includes the previous audit
commit and the new foundation fixes. In a clean owner-controlled clone:

```sh
git switch -c review/subscription-foundation 0c2b08f78bbcd699558894dbeac2c05c53f08614
git am /path/to/parvaz-review-and-phase-a.patch
# Inspect/test first, then use your normal GitHub authentication:
git push -u origin review/subscription-foundation
```

If you already applied the audit commit, do NOT apply it twice; use the phase-A-only
patch `parvaz-phase-a-only.patch` on top of audit commit `b95a437`. Do not force-push
main. Neither push command nor a Pull Request was executed by the assistant.

## Latest phase-B work

`parvaz-through-phase-b.patch` is the combined mailbox patch containing the initial
audit, phase-A fixes and phase-B fixes, starting at the public 1.18 commit
`0c2b08f78bbcd699558894dbeac2c05c53f08614`. Use it instead of the phase-A combined patch
in the clean-branch instructions above. If phase A was already applied, use only
`parvaz-phase-b-only.patch` on that branch. Do not apply the earlier commits twice.

Phase-B status: `docs/progress/PHASE-B-fa.md`. The added GitHub workflow runs JVM
regressions only with read-only permissions and three-day artifact retention.
It has not run on GitHub yet; full Android compilation, device testing and signed
release publication are still pending. These patches do not contain release keys.
