#!/usr/bin/env python3
"""Validate the existing pinned key and optionally build; never generate signing keys."""
import argparse
import base64
import hashlib
import json
import os
import subprocess
import tempfile
from pathlib import Path

NAMES = ('PARVAZ_KEYSTORE_BASE64', 'PARVAZ_KEYSTORE_PASSWORD', 'PARVAZ_KEY_ALIAS', 'PARVAZ_KEY_PASSWORD')


def report(code, message, fail=False):
    message = message.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
    print(('::error' if fail else '::notice') + ' title=' + code + '::' + message, flush=True)
    if fail:
        raise SystemExit(1)


def inputs(env):
    for name in NAMES:
        value = env.get(name, '')
        if not value:
            report('MISSING_SIGNING_SECRET', name + ' is missing.', True)
        if value.strip().strip('"\'') in (name, name + '.txt'):
            report('SETTING_NAME_NOT_VALUE', 'A signing setting contains a name instead of the private file contents. Use the offline setup form to copy the value.', True)
    try:
        raw = base64.b64decode(''.join(env[NAMES[0]].split()), validate=True)
    except (ValueError, UnicodeError):
        report('INVALID_KEYSTORE_BASE64', 'The keystore setting is not valid Base64. Copy the complete value, not its name.', True)
    if len(raw) < 512:
        report('KEYSTORE_TOO_SHORT', 'The decoded value cannot contain the complete permanent keystore.', True)
    metadata = json.loads(Path('docs/signing/release-signing.json').read_text())
    if env[NAMES[2]] != metadata['key_alias']:
        report('WRONG_ALIAS_SETTING', 'The configured alias differs from the public signing metadata.', True)
    # This project's PKCS12 uses one password for the store and private key.
    if env[NAMES[1]] != env[NAMES[3]]:
        report('PASSWORD_SETTINGS_DIFFER', 'The two password settings must match the supplied permanent PKCS12 backup.', True)
    return raw, metadata


def verify(raw, metadata, env, path):
    path.write_bytes(raw)
    path.chmod(0o600)
    env['PARVAZ_KEYSTORE_PATH'] = str(path)
    args = ['keytool', '-keystore', str(path), '-storepass:env', NAMES[1], '-alias', env[NAMES[2]]]
    cert = subprocess.run(args + ['-exportcert'], env=env, capture_output=True, timeout=30)
    if cert.returncode:
        report('KEYSTORE_OPEN_FAILED', 'The keystore cannot be opened with the configured password and alias. No secret/tool output was disclosed.', True)
    if hashlib.sha256(cert.stdout).hexdigest() != metadata['certificate_sha256']:
        report('CERTIFICATE_PIN_MISMATCH', 'The key does not match the pinned permanent signing identity. No fallback is allowed.', True)
    proof = subprocess.run(args + ['-certreq', '-keypass:env', NAMES[3]], env=env, capture_output=True, timeout=30)
    if proof.returncode:
        report('PRIVATE_KEY_USE_FAILED', 'The pinned certificate was found but its private key could not sign with the configured password.', True)
    report('PERMANENT_SIGNING_KEY_OK', 'Keystore, alias, password, pinned certificate and private-key signing proof passed.')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--inputs-only', action='store_true')
    parser.add_argument('--verify-only', action='store_true')
    options = parser.parse_args()
    env = os.environ.copy()
    raw, metadata = inputs(env)
    if options.inputs_only:
        report('SIGNING_INPUT_FORMAT_OK', 'Input format passed; actual key verification is still required.')
        return
    with tempfile.TemporaryDirectory(prefix='parvaz-signing-', dir=env.get('RUNNER_TEMP')) as directory:
        verify(raw, metadata, env, Path(directory) / 'release.p12')
        if options.verify_only:
            return
        log = Path(directory) / 'build.log'
        with log.open('wb') as output:
            result = subprocess.run([str(ROOT / 'gradlew'), '--no-daemon', '--max-workers=2', ':app:assembleRelease'], env=env, stdout=output, stderr=subprocess.STDOUT)
        if result.returncode:
            text = log.read_text(errors='replace')[-10000:]
            for name in NAMES:
                text = text.replace(env[name], '[REDACTED]')
            text = text.replace(directory, '[TEMP_SIGNING_DIR]')
            report('RELEASE_BUILD_FAILED', text, True)
        report('SIGNED_BUILD_OK', 'Release assembly passed. APK signature/package/hash verification is the next gate.')


if __name__ == '__main__':
    main()
