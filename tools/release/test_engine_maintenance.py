import unittest,tempfile,json,os
from pathlib import Path
from unittest.mock import patch
import auto_core_update as updater
from check_release_order import validate_order

class Engines(unittest.TestCase):
 def fixture(self,root):
  for p in ['app','tools/release','tools/native','docs/releases']:(root/p).mkdir(parents=True)
  lock={'repository':'2dust/AndroidLibXrayLite','tag':'v26.9.9','asset':'libv2ray.aar','url':'https://github.com/2dust/AndroidLibXrayLite/releases/download/v26.9.9/libv2ray.aar','sha256':'a'*64}
  (root/'tools/release/core-lock.json').write_text(json.dumps(lock));(root/'app/build.gradle').write_text('versionName "1.27.0"\nversionCode 33\n')
  native=[{'name':'sing-box','repository':'SagerNet/sing-box','tag':'v1.14.0','patches':['keep.patch'],'tags':'with_quic'}, {'name':'mihomo','repository':'MetaCubeX/mihomo','tag':'v1.19.30'}]
  (root/'tools/native/engines-lock.json').write_text(json.dumps(native))
 def api(self,path):
  return {'tag_name':'v1.14.1' if 'sing-box' in path else 'v1.19.30' if 'mihomo' in path else 'v26.9.9','draft':False,'prerelease':False,'assets':[]}
 def test_native_only_update_bumps_once_and_preserves_build_policy(self):
  with tempfile.TemporaryDirectory() as directory:
   root=Path(directory);self.fixture(root)
   pin={'tag':'v1.14.1','commit':'b'*40,'url':'https://codeload.github.com/SagerNet/sing-box/tar.gz/'+'b'*40,'sha256':'c'*64}
   with patch.object(updater,'ROOT',root),patch.object(updater,'api',side_effect=self.api),patch.object(updater,'pin_source',return_value=pin) as source,patch.object(updater,'fetch') as fetch,patch.dict(os.environ,{'GITHUB_OUTPUT':str(root/'output'),'CORE_CHECK_ONLY':'false'}):updater.prepare()
   source.assert_called_once_with('v1.14.1','SagerNet/sing-box');fetch.assert_not_called()
   self.assertIn('versionCode 34',(root/'app/build.gradle').read_text())
   engine=json.loads((root/'tools/native/engines-lock.json').read_text())[0]
   self.assertEqual(engine['tag'],'v1.14.1');self.assertEqual(engine['patches'],['keep.patch']);self.assertEqual(engine['tags'],'with_quic')
 def test_check_only_never_downloads_builds_or_edits_candidates(self):
  with tempfile.TemporaryDirectory() as directory:
   root=Path(directory);self.fixture(root);before={str(p):p.read_bytes() for p in root.rglob('*') if p.is_file()}
   with patch.object(updater,'ROOT',root),patch.object(updater,'api',side_effect=self.api),patch.object(updater,'pin_source') as source,patch.dict(os.environ,{'GITHUB_OUTPUT':str(root/'output'),'CORE_CHECK_ONLY':'true'}):updater.prepare()
   source.assert_not_called()
   for p,data in before.items():self.assertEqual(Path(p).read_bytes(),data)
 def test_unrecognized_repository_and_prerelease_are_refused(self):
  for bad in ['repository','prerelease']:
   with tempfile.TemporaryDirectory() as directory:
    root=Path(directory);self.fixture(root)
    if bad=='repository':
     p=root/'tools/native/engines-lock.json';d=json.loads(p.read_text());d[0]['repository']='unknown/core';p.write_text(json.dumps(d))
    def api(path):
     data=self.api(path)
     if bad=='prerelease' and 'sing-box' in path:data['prerelease']=True
     return data
    with patch.object(updater,'ROOT',root),patch.object(updater,'api',side_effect=api),patch.dict(os.environ,{'GITHUB_OUTPUT':str(root/'output'),'CORE_CHECK_ONLY':'false'}):
     with self.assertRaises(ValueError):updater.prepare()
 def test_each_native_engine_is_rollback_protected(self):
  old={'version':'1.27.0','code':33,'native':{'sing-box':'v1.14.1','mihomo':'v1.19.30'}}
  for native in [{},{'sing-box':'v1.14.0','mihomo':'v1.19.30'},{'sing-box':'v1.14.1','mihomo':'v1.19.29'}]:
   with self.assertRaises(ValueError):validate_order({'version':'1.27.1','code':34,'native':native},old)
  validate_order({'version':'1.27.1','code':34,'native':old['native']},old)
 def test_signer_does_not_execute_candidate_engines_or_gradle(self):
  root=Path(__file__).resolve().parents[2]
  text=(root/'.github/workflows/maintain-xray-core.yml').read_text();sign=text.split('  sign-and-publish:')[1]
  self.assertNotIn('gradlew',sign);self.assertNotIn('tools/native/build.py',sign);self.assertIn('needs: test-candidate',sign)
  self.assertNotIn('secrets.PARVAZ_',text.split('  sign-and-publish:')[0]);self.assertIn("23 */6 * * *",text)
  self.assertIn('core_candidate_artifacts.py verify',sign);self.assertIn('sign_prebuilt.py',sign)
  self.assertIn('git commit -m "candidate:',text)
if __name__=='__main__':unittest.main()
