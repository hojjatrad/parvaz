"""Tests use synthetic bytes only, never a production private key."""
import base64
import contextlib
import hashlib
import io
import json
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
import build_signed


class SigningGuards(unittest.TestCase):
    def setUp(self):
        self.raw = b'synthetic-not-a-keystore-' * 30
        self.env = dict(zip(build_signed.NAMES, (base64.b64encode(self.raw).decode(), 'test-password-not-private', 'parvaz', 'test-password-not-private')))
        self.cert = b'public-certificate-test-fixture'
        self.metadata = {'key_alias': 'parvaz', 'certificate_sha256': hashlib.sha256(self.cert).hexdigest()}
        self.output = io.StringIO()
        self.capture = contextlib.redirect_stdout(self.output)
        self.capture.__enter__()
        self.addCleanup(self.capture.__exit__, None, None, None)

    def read_inputs(self):
        with patch.object(Path, 'read_text', return_value=json.dumps(self.metadata)):
            return build_signed.inputs(self.env)

    def test_valid_format_and_whitespace(self):
        self.env[build_signed.NAMES[0]] = '\n ' + self.env[build_signed.NAMES[0]] + '\r\n'
        self.assertEqual(self.read_inputs()[0], self.raw)

    def test_missing_field(self):
        del self.env[build_signed.NAMES[1]]
        with self.assertRaises(SystemExit): self.read_inputs()

    def test_name_and_filename_placeholders(self):
        for name in build_signed.NAMES:
            for value in (name, name + '.txt'):
                with self.subTest(name=name, filename=value.endswith('.txt')):
                    old = self.env[name]; self.env[name] = value
                    with self.assertRaises(SystemExit): self.read_inputs()
                    self.env[name] = old

    def test_invalid_base64(self):
        self.env[build_signed.NAMES[0]] = 'invalid!'
        with self.assertRaises(SystemExit): self.read_inputs()

    def test_too_short(self):
        self.env[build_signed.NAMES[0]] = base64.b64encode(b'short').decode()
        with self.assertRaises(SystemExit): self.read_inputs()

    def test_wrong_alias(self):
        self.env[build_signed.NAMES[2]] = 'wrong-alias'
        with self.assertRaises(SystemExit): self.read_inputs()

    def test_different_passwords(self):
        self.env[build_signed.NAMES[3]] = 'different-test-password'
        with self.assertRaises(SystemExit): self.read_inputs()

    def verify(self, responses):
        with tempfile.TemporaryDirectory() as directory, patch.object(subprocess, 'run', side_effect=responses) as run:
            path = Path(directory) / 'test.p12'
            build_signed.verify(self.raw, self.metadata, self.env.copy(), path)
            self.assertEqual(path.stat().st_mode & 0o777, 0o600)
            self.assertEqual(run.call_count, 2)

    def test_tool_failure_does_not_disclose_output(self):
        with self.assertRaises(SystemExit):
            self.verify([subprocess.CompletedProcess([], 1, b'private-test-text', b'private-test-text')])
        self.assertNotIn('private-test-text', self.output.getvalue())

    def test_wrong_certificate_rejected(self):
        with self.assertRaises(SystemExit):
            self.verify([subprocess.CompletedProcess([], 0, b'wrong-certificate', b'')])

    def test_private_key_proof_required(self):
        with self.assertRaises(SystemExit):
            self.verify([subprocess.CompletedProcess([], 0, self.cert, b''), subprocess.CompletedProcess([], 1, b'', b'')])

    def test_verified_certificate_and_signing_proof(self):
        self.verify([subprocess.CompletedProcess([], 0, self.cert, b''), subprocess.CompletedProcess([], 0, b'public-csr', b'')])
        self.assertIn('PERMANENT_SIGNING_KEY_OK', self.output.getvalue())
        self.assertNotIn(self.env[build_signed.NAMES[1]], self.output.getvalue())


if __name__ == '__main__':
    unittest.main()
