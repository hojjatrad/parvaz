#!/usr/bin/env python3
"""Verify actual APK signatures, the pinned certificate, package and version before publishing."""
import glob
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]
config = (ROOT / 'app/build.gradle').read_text()
version = re.search(r'versionName\s+"([^"]+)"', config).group(1)
code = re.search(r'versionCode\s+(\d+)', config).group(1)
pin = json.loads((ROOT / 'docs/signing/release-signing.json').read_text())
build_tools = Path(os.environ.get('ANDROID_HOME', os.environ.get('ANDROID_SDK_ROOT', ''))) / 'build-tools/34.0.0'
apksigner = shutil.which('apksigner') or str(build_tools / 'apksigner')
aapt = shutil.which('aapt') or str(build_tools / 'aapt')
artifacts = ROOT / 'release-artifacts'
artifacts.mkdir(exist_ok=True)
expected = {
    'app-arm64-v8a-release.apk': f'Parvaz-{version}-arm64.apk',
    'app-universal-release.apk': f'Parvaz-{version}.apk',
}
verified = []
for original, destination in expected.items():
    apk = ROOT / 'app/build/outputs/apk/release' / original
    if not apk.is_file():
        raise SystemExit(f'Required APK is missing: {original}')
    if not 0 < apk.stat().st_size <= 128 * 1024 * 1024:
        raise SystemExit(f'APK exceeds the installed updater limit: {original}')
    print(f'APK_SIZE_OK {destination} {apk.stat().st_size}')
    signature = subprocess.check_output([apksigner, 'verify', '--verbose', '--print-certs', '--min-sdk-version', '24', str(apk)], text=True)
    fingerprints = re.findall(r'Signer #\d+ certificate SHA-256 digest:\s*([0-9a-fA-F:]+)', signature)
    if len(fingerprints) != 1 or fingerprints[0].replace(':', '').lower() != pin['certificate_sha256']:
        raise SystemExit(f'Signing certificate mismatch: {original}. Publishing refused.')
    badging = subprocess.check_output([aapt, 'dump', 'badging', str(apk)], text=True)
    match = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging)
    if not match or match.groups() != (pin['application_id'], code, version):
        raise SystemExit(f'Package/version mismatch: {original}. Publishing refused.')
    output = artifacts / destination
    shutil.copyfile(apk, output)
    verified.append(output)
    print(f'Verified {destination}: package/version/signature match the release pin.')
(artifacts / 'SHA256SUMS.txt').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest() + '  ' + p.name + '\n' for p in verified))
