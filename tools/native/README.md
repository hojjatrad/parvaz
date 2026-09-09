# Native engines and licensing

Owner authorization for GPLv3 publication was provided in the 2026-09-09 session. Parvaz-owned code is GPL-3.0-or-later; third-party components retain their own licenses. Parvaz is independent and does not imply endorsement by upstream projects.

`engines-lock.json` pins complete source commits and archive SHA-256 values. `build.py` builds Android PIE executables using NDK 27.2.12479018, with linker alignment for 16 KiB pages. Only the parent-death guard in `parvaz_parent_android.go` is added to upstream sources. It kills a child engine if its Android application process dies.

Install Go (at least the versions required by upstream go.mod) and the specified Android NDK, then run `python3 tools/native/build.py`. The resulting native executables are packaged under Android's native library directory, not executed from writable downloaded files. `--probe` builds x86_64 for emulator tests; `--host` builds Linux executables for synthetic loopback protocol tests.

No Android release signing material is involved in native builds. Source bundles for redistribution must include upstream engine trees plus vendored modules and the build instructions; release CI creates these alongside APKs.

The bundle also includes AndroidLibXrayLite (LGPLv3) and its vendored Xray dependencies. `xray-source-lock.json` must match `tools/release/core-lock.json`; automated Xray upgrades update both pins. Rebuild that library following its included upstream build workflow/README, place the compatible AAR at `app/libs/libv2ray.aar`, then build the app without re-fetching the original AAR. Users can modify these build-time pins and sign a modified app with their own key. Export an encrypted backup before replacing an installation signed by a different key; Android will not accept a different signer as an in-place update. The owner's private release key is neither needed nor included.

Full profiles intentionally cannot reproduce desktop listener/process semantics on Android. App-owned loopback/TUN interfaces replace imported listeners; external administration and executable hooks are disabled. File-based inputs cannot escape the private runtime directory. Direct routing rules explicitly present in an advanced file remain direct rules; importing a full profile is not an assertion that it is leak-free. Ordinary subscription import remains server extraction and does not automatically execute imported routing policies.
