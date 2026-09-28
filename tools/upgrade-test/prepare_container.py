#!/usr/bin/env python3
"""Prepare the disposable native-ARM64 Android 11 container for the actual APK
upgrade fixture.

Why a container and not the QEMU emulator: the published APKs ship only
armeabi-v7a/arm64-v8a. On x86_64 runners the signed release cannot execute
honestly (it aborts under ARM translation), and GitHub's free arm64 runners
expose no /dev/kvm, so the emulator cannot run there at all. An Android 11
container on an arm64 runner executes the unmodified signed arm64 code natively.

Honest scope, asserted here so it can never be silently widened:
  * real Android 11 (API 30) userspace, arm64-v8a primary ABI, real PackageManager,
    real PackageInstaller UI, real app process;
  * NOT a physical handset: no radio, no vendor OEM stack, and SELinux is not
    enforcing in this image.
The temporary CA is installed into a RAM-only copy of the system store inside the
disposable container only; nothing about the production trust store changes.
"""
import argparse
import json
import pathlib
import re
import subprocess
import sys
import time

CERT_NAME_RE = re.compile(r"[0-9a-fA-F]{8}")


def sh(*args, check=True, timeout=180):
    proc = subprocess.run(args, text=True, capture_output=True, timeout=timeout)
    if check and proc.returncode != 0:
        raise SystemExit("Fixture command failed: %s\n%s\n%s" % (args, proc.stdout[-1500:], proc.stderr[-1500:]))
    return (proc.stdout or "").strip()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--container", required=True)
    ap.add_argument("--serial", required=True)
    ap.add_argument("--cert", required=True)
    ap.add_argument("--host-ip", required=True)
    ap.add_argument("--proxy-port", type=int, default=8765)
    ap.add_argument("--out", default=".cache/upgrade-evidence/environment.json")
    args = ap.parse_args()

    cert = pathlib.Path(args.cert)
    if not cert.is_file():
        raise SystemExit("Temporary transport certificate missing")

    def adb(*rest, check=True):
        return sh("adb", "-s", args.serial, *rest, check=check)

    def inside(*rest, check=True):
        return sh("docker", "exec", args.container, *rest, check=check)

    # ---- environment guards -------------------------------------------------
    sdk = adb("shell", "getprop", "ro.build.version.sdk")
    abis = adb("shell", "getprop", "ro.product.cpu.abilist").split(",")
    if sdk != "30":
        raise SystemExit("Only the reviewed Android 11 fixture is allowed, got sdk=%s" % sdk)
    if "arm64-v8a" not in abis:
        raise SystemExit("ARM64 ABI unavailable; an actual signed-APK test cannot be claimed")
    if adb("shell", "getprop", "ro.build.characteristics") == "":
        pass  # informational only
    running = sh("docker", "inspect", "-f", "{{.State.Running}}", args.container)
    if running != "true":
        raise SystemExit("Disposable Android container is not running")
    # The fixture must never touch a real device: the target has to be the
    # throwaway container we started in this job.
    if adb("shell", "getprop", "ro.product.brand").lower() not in ("redroid", "android", "google"):
        raise SystemExit("Unexpected target brand; refusing device changes")

    # ---- RAM-only system CA overlay, container scope only -------------------
    digest = sh("openssl", "x509", "-in", str(cert), "-subject_hash_old", "-noout").splitlines()[0]
    if not CERT_NAME_RE.fullmatch(digest):
        raise SystemExit("Invalid temporary certificate hash")
    staging = "/data/local/tmp/parvaz-ca"
    inside("/system/bin/rm", "-rf", staging, check=False)
    inside("/system/bin/mkdir", "-p", staging)
    inside("/system/bin/sh", "-c", "cp -a /system/etc/security/cacerts/. %s/" % staging)
    existing = set(inside("/system/bin/ls", staging).split())
    name = next((digest + "." + str(i) for i in range(100) if digest + "." + str(i) not in existing), None)
    if name is None:
        raise SystemExit("No free CA hash slot; original roots must not be overwritten")
    adb("push", str(cert), staging + "/" + name)
    inside("/system/bin/chmod", "644", staging + "/" + name)
    inside("/system/bin/sh", "-c", "mount --bind %s /system/etc/security/cacerts" % staging)
    readback = inside("/system/bin/cat", "/system/etc/security/cacerts/" + name)
    if readback.strip() != cert.read_text().strip():
        raise SystemExit("Temporary CA readback mismatch")
    roots = len(inside("/system/bin/ls", "/system/etc/security/cacerts").split())
    if roots <= len(existing):
        raise SystemExit("Existing roots must be preserved alongside the temporary CA")

    # ---- deterministic UI + staged transport --------------------------------
    adb("shell", "settings", "put", "global", "http_proxy", "%s:%d" % (args.host_ip, args.proxy_port))
    for setting in ("window_animation_scale", "transition_animation_scale", "animator_duration_scale"):
        adb("shell", "settings", "put", "global", setting, "0")
    adb("shell", "svc", "power", "stayon", "true", check=False)
    adb("shell", "input", "keyevent", "82", check=False)
    # Restart the framework-facing app surface so the new trust store and proxy
    # are picked up by freshly forked app processes. Never reboots the device.
    adb("shell", "am", "force-stop", "com.parvaz.tunnel", check=False)
    time.sleep(2)

    env = {
        "sdk": sdk,
        "abis": abis,
        "selinux": adb("shell", "getenforce", check=False),
        "serial": args.serial,
        "container_image": sh("docker", "inspect", "-f", "{{.Config.Image}}", args.container),
        "ca_slot": name,
        "system_roots": roots,
        "proxy": "%s:%d" % (args.host_ip, args.proxy_port),
        "scope": (
            "Native arm64 Android 11 container on an arm64 runner: unmodified signed "
            "arm64 APK executes natively, real PackageManager/PackageInstaller UI. "
            "Not a physical handset: no radio/vendor stack, SELinux not enforcing."
        ),
    }
    out = pathlib.Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(env, indent=2) + "\n")
    print("CI_ONLY_STAGED_TLS_TRUST_READY " + json.dumps(env))
    return 0


if __name__ == "__main__":
    sys.exit(main())
