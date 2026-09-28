#!/usr/bin/env python3
"""Run the upgrade instrumentation with plain adb (no Gradle/SDK on arm64) and
write a JUnit report in the same location the emulator path produces, so the
existing evidence verifier keeps working unchanged.

The raw instrumentation stream is preserved verbatim as evidence; the report is
derived from it, never asserted independently.
"""
import argparse
import pathlib
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

RESULTS = pathlib.Path("tools/android-probe/build/outputs/androidTest-results/container")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--serial", required=True)
    ap.add_argument("--runner", default="com.parvaz.probe.test/androidx.test.runner.AndroidJUnitRunner")
    ap.add_argument("--class", dest="klass", default="com.parvaz.probe.PublishedUpgradeTest")
    ap.add_argument("--arg", action="append", default=[], help="key=value instrumentation argument")
    ap.add_argument("--timeout", type=int, default=1800)
    args = ap.parse_args()

    cmd = ["adb", "-s", args.serial, "shell", "am", "instrument", "-w", "-r", "-e", "class", args.klass]
    for pair in args.arg:
        key, _, value = pair.partition("=")
        cmd += ["-e", key, value]
    cmd.append(args.runner)
    proc = subprocess.run(cmd, text=True, capture_output=True, timeout=args.timeout)
    raw = (proc.stdout or "") + (proc.stderr or "")
    RESULTS.mkdir(parents=True, exist_ok=True)
    (RESULTS / "instrumentation-raw.txt").write_text(raw)
    sys.stdout.write(raw[-8000:])

    # -1 = error, -2 = failure, 0 = ok in the instrumentation status protocol.
    codes = [int(m) for m in re.findall(r"^INSTRUMENTATION_STATUS_CODE: (-?\d+)\s*$", raw, re.M)]
    stack = ""
    stack_match = re.search(r"INSTRUMENTATION_STATUS: stack=(.*?)(?=\nINSTRUMENTATION_STATUS(?:_CODE)?:)", raw, re.S)
    if stack_match:
        stack = stack_match.group(1).strip()
    ok = "INSTRUMENTATION_CODE: -1" in raw and "OK (1 test)" in raw and -2 not in codes and -1 not in codes[1:]
    failures = 0 if ok else 1

    suite = ET.Element(
        "testsuite",
        {"name": args.klass, "tests": "1", "failures": str(failures), "errors": "0", "skipped": "0"},
    )
    case = ET.SubElement(suite, "testcase", {"classname": args.klass, "name": "updateButtonInstallsPermanentCandidateAndKeepsProfile"})
    if not ok:
        failure = ET.SubElement(case, "failure", {"message": (stack or raw)[-2800:]})
        failure.text = (stack or raw)[-2800:]
    ET.ElementTree(suite).write(RESULTS / "TEST-container.xml", encoding="utf-8", xml_declaration=True)
    if not ok:
        print("::error title=APK upgrade fixture::" + (stack or raw)[-2800:].replace("%", "%25").replace("\n", "%0A"))
        return 1
    print("CONTAINER_INSTRUMENTATION_PASSED")
    return 0


if __name__ == "__main__":
    sys.exit(main())
