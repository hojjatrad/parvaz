"""Read-only diagnosis. Never print secret text or export recovered material."""
import base64
import hashlib
import json
import os
import re
import subprocess
import tempfile
from pathlib import Path

NAMES = ('PARVAZ_KEYSTORE_BASE64', 'PARVAZ_KEYSTORE_PASSWORD', 'PARVAZ_KEY_ALIAS', 'PARVAZ_KEY_PASSWORD')


def report(code, message):
    # All messages must be fixed text or public setting names, never secret-derived text.
    print('::notice title=' + code + '::' + message, flush=True)


def variants(value):
    values = [value]
    # Only formatting/reference wrappers; no password guessing, network retrieval or key creation.
    for _ in range(3):
        for item in list(values):
            clean = item.translate(dict.fromkeys(map(ord, '\ufeff\u200b\u200e\u200f\u2060'))).strip()
            extra = [clean]
            if len(clean) >= 2 and clean[0] == clean[-1] and clean[0] in "\"'":
                extra.append(clean[1:-1])
            if clean.startswith('```') and clean.endswith('```') and '\n' in clean:
                extra.append(clean.split('\n', 1)[1][:-3])
            assignment = re.fullmatch(r'(?:export\s+)?PARVAZ_KEYSTORE_BASE64\s*[:=]\s*(.*)', clean, re.S)
            if assignment:
                extra.append(assignment[1])
            if re.match(r'^data:[^,;]+;base64,', clean):
                extra.append(clean.split(',', 1)[1])
            try:
                parsed = json.loads(clean)
                if isinstance(parsed, dict) and isinstance(parsed.get(NAMES[0]), str):
                    extra.append(parsed[NAMES[0]])
                elif isinstance(parsed, str):
                    extra.append(parsed)
            except (ValueError, TypeError):
                pass
            for candidate in extra:
                if candidate not in values:
                    values.append(candidate)
        if len(values) > 24:
            break
    # A complete DER Base64 block embedded in a copied document may still be usable.
    values.extend(re.findall(r'MII[A-Za-z0-9+/=\s]{700,}', value)[:8])
    return values


def pfx_candidates(value):
    seen = set()
    for candidate in variants(value):
        try:
            data = base64.b64decode(''.join(candidate.split()), validate=True)
        except (ValueError, UnicodeError):
            continue
        if data in seen:
            continue
        seen.add(data)
        if len(data) < 512 or data[0] != 0x30:
            continue
        # PFX is a DER SEQUENCE starting with INTEGER version 3.
        offset = 2 + (data[1] & 0x7f) if data[1] & 0x80 else 2
        if offset > 6 or data[offset:offset + 3] != b'\x02\x01\x03':
            continue
        yield data


def main():
    env = os.environ.copy()
    for name in NAMES:
        value = env.get(name, '').strip().strip('"\'')
        if not value:
            report('EMPTY_SETTING', name + ' is empty.')
        elif value == name:
            report('SETTING_NAME_NOT_VALUE', name + ' contains its setting name rather than file contents.')
        elif len(value) < 240 and (value.replace('\\', '/').split('/')[-1] in (name + '.txt', 'parvaz-signing-backup.zip', 'parvaz-release.p12')):
            report('FILENAME_NOT_CONTENTS', name + ' contains a file reference rather than file contents.')
    value = env.get(NAMES[0], '')
    if len(value) < 512:
        report('KEYSTORE_TEXT_TOO_SHORT', 'The stored text is too short to contain this complete private keystore.')
    candidates = list(pfx_candidates(value))
    if not candidates:
        report('NO_RECOVERABLE_KEYSTORE', 'No complete PKCS12 payload found after checking recognized copy-format wrappers. No secret data was disclosed.')
        return 1
    report('PKCS12_PAYLOAD_FOUND', 'A structurally plausible PKCS12 payload was found; identity is not yet verified.')
    metadata = json.loads(Path('docs/signing/release-signing.json').read_text())
    if env.get(NAMES[2]) != metadata['key_alias']:
        report('ALIAS_SETTING_DIFFERS', 'The alias setting differs from the public signing metadata. Checking the known public alias without changing any settings.')
    opened = False
    with tempfile.TemporaryDirectory(prefix='parvaz-recovery-check-', dir=env.get('RUNNER_TEMP')) as directory:
        path = Path(directory) / 'candidate.p12'
        for data in candidates:
            path.write_bytes(data)
            path.chmod(0o600)
            args = ['keytool', '-keystore', str(path), '-storepass:env', NAMES[1], '-alias', metadata['key_alias']]
            cert = subprocess.run(args + ['-exportcert'], env=env, capture_output=True, timeout=30)
            if cert.returncode:
                continue
            opened = True
            if hashlib.sha256(cert.stdout).hexdigest() != metadata['certificate_sha256']:
                continue
            report('PERMANENT_CERTIFICATE_MATCH', 'Recovered payload matches the pinned permanent public certificate.')
            probe = subprocess.run(args + ['-certreq', '-keypass:env', NAMES[3]], env=env, capture_output=True, timeout=30)
            if probe.returncode:
                report('PRIVATE_KEY_USE_FAILED', 'The certificate matches, but use of the private key with the configured key password failed.')
                return 1
            report('PERMANENT_KEY_RECOVERABLE', 'The original permanent private key is usable from the stored payload. Nothing was published, uploaded or changed.')
            return 0
    report('NO_PIN_MATCH' if opened else 'PAYLOAD_OPEN_FAILED', 'No usable matching key was verified. Secret values and tool output were not disclosed.')
    return 1


if __name__ == '__main__':
    raise SystemExit(main())
