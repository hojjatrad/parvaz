import unittest
from auto_core_update import next_version,version_tuple
from fetch_core import validate

class CorePolicyTests(unittest.TestCase):
    def test_next_version(self):
        self.assertEqual(next_version('1.21'),'1.21.1')
        self.assertEqual(next_version('1.21.9'),'1.21.10')
    def test_numeric_order(self):self.assertGreater(version_tuple('v26.10.1'),version_tuple('v26.9.9'))
    def test_refuse_other_artifact(self):
        with self.assertRaises(ValueError):validate({'repository':'evil/repo','tag':'v1.2.3'})
    def test_refuse_bad_tag(self):
        with self.assertRaises(ValueError):validate({'repository':'2dust/AndroidLibXrayLite','tag':'v1.2.3;echo nope'})
    def test_refuse_wrong_url_or_missing_hash(self):
        with self.assertRaises(ValueError):validate({'repository':'2dust/AndroidLibXrayLite','tag':'v26.9.9','url':'http://evil.invalid/file','asset':'libv2ray.aar','sha256':'a'*64})
if __name__=='__main__':unittest.main()

class CandidatePreparationTests(unittest.TestCase):
    def test_candidate_bumps_code_and_preserves_signing_files(self):
        import tempfile,json,os
        from pathlib import Path
        from unittest.mock import patch
        import auto_core_update as updater
        with tempfile.TemporaryDirectory() as d:
            root=Path(d)
            for directory in ['app','tools/release','docs/releases','docs/signing']:(root/directory).mkdir(parents=True)
            lock={'repository':'2dust/AndroidLibXrayLite','tag':'v26.7.31','asset':'libv2ray.aar','url':'https://github.com/2dust/AndroidLibXrayLite/releases/download/v26.7.31/libv2ray.aar','sha256':'a'*64}
            (root/'tools/release/core-lock.json').write_text(json.dumps(lock))
            (root/'app/build.gradle').write_text('versionName "1.21"\nversionCode 22\n')
            (root/'docs/signing/release-signing.json').write_text('unchanged-public-signing-pin')
            latest={'tag_name':'v26.9.9','draft':False,'prerelease':False,'assets':[{'name':'libv2ray.aar','browser_download_url':'https://github.com/2dust/AndroidLibXrayLite/releases/download/v26.9.9/libv2ray.aar','digest':'sha256:'+'b'*64}]}
            with patch.object(updater,'ROOT',root),patch.object(updater,'api',return_value=latest),patch.object(updater,'fetch') as fetch,patch.dict(os.environ,{'GITHUB_OUTPUT':str(root/'outputs'),'CORE_CHECK_ONLY':'false'}):
                updater.prepare();fetch.assert_called_once()
            self.assertIn('versionCode 23',(root/'app/build.gradle').read_text())
            self.assertIn('versionName "1.21.1"',(root/'app/build.gradle').read_text())
            self.assertEqual((root/'docs/signing/release-signing.json').read_text(),'unchanged-public-signing-pin')
            self.assertIn('mode=new',(root/'outputs').read_text())
            self.assertTrue((root/'docs/releases/v1.21.1.md').is_file())
