import importlib.util,pathlib,unittest
spec=importlib.util.spec_from_file_location('prior',pathlib.Path(__file__).with_name('prior_release.py'));prior=importlib.util.module_from_spec(spec);spec.loader.exec_module(prior)
class PriorReleaseTest(unittest.TestCase):
 def test_only_reviewed_priors(self):self.assertEqual(set(prior.PRIORS),{'stable39','stable43','stable44','stable45','stable46','stable47','stable48','stable49','stable50','test41','test42'})
 def test_owner_installed_test_identity(self):
  p=prior.select('test42');self.assertEqual(p['version_code'],42);self.assertIn('test/v1.28.8-r1/',p['url']);self.assertEqual(p['sha256'],'32a58e86ce0a260f7565161e8e65642f726ce4f214cf2f93ab39c5dde885ed3b')
 def test_stable_identity(self):
  p=prior.select('stable39');self.assertEqual(p['version_code'],39);self.assertIn('/v1.28.5/',p['url']);self.assertEqual(p['sha256'],'71d99e7ab114639e85e5e2db21494ff34fa95fd4cc91719dac4b6baf11776205')
  p=prior.select('stable44');self.assertEqual(p['version_code'],44);self.assertIn('/v1.29.0/',p['url']);self.assertEqual(p['sha256'],'da7f546421c631bb27f6e9a1fddff721cc66bafac1c0912a7e1e67b8729e0569')
  p=prior.select('stable45');self.assertEqual(p['version_code'],45);self.assertIn('/v1.30.0/',p['url']);self.assertEqual(p['sha256'],'63661ee06a50039f79f81f7958dc2f9754686e2be47a126d6c91aa985e61536a')
  p=prior.select('stable46');self.assertEqual(p['version_code'],46);self.assertIn('/v1.30.1/',p['url']);self.assertEqual(p['sha256'],'ca6483fec5f13387a9791e82dbac9fbde86bf6c644ec7f23746d6eaf2882420a')
  p=prior.select('stable47');self.assertEqual(p['version_code'],47);self.assertIn('/v1.31.0/',p['url']);self.assertEqual(p['sha256'],'98158ab77518cf284a86af7e3bbe7e81eba6b78504a3c1a7caf037b7ab7cc4cc')
  p=prior.select('stable48');self.assertEqual(p['version_code'],48);self.assertIn('/v1.32.0/',p['url']);self.assertEqual(p['sha256'],'7521d00685241091eefdaa12295d54af947381a11b142320cc1a6e752aacf2cd')
  p=prior.select('stable49');self.assertEqual(p['version_code'],49);self.assertIn('/v1.33.0/',p['url']);self.assertEqual(p['sha256'],'ada59b3fabc06eea027f9603db9b4ac8f71385a9fb96c68475777c98abf033a4')
  p=prior.select('stable50');self.assertEqual(p['version_code'],50);self.assertIn('/v1.34.0/',p['url']);self.assertEqual(p['sha256'],'9b50c5f98464eb4bc2b3c0bf8cba39b5126ee8c838dda5e56f73192ff5601ac7')
 def test_accepted_test_identity(self):
  p=prior.select('test41');self.assertEqual(p['version_code'],41);self.assertIn('test/v1.28.7-r1/',p['url']);self.assertEqual(p['sha256'],'cdeced0b64a060dffcffa7e081d6eff487c3504fae8bbdce02afc54f73d120b0')
 def test_arbitrary_input_rejected(self):
  for v in ['latest','https://example.org/a.apk','../../private','',None]:
   with self.assertRaises(ValueError):prior.select(v)
 def test_return_is_not_mutable_global_state(self):
  p=prior.select('test41');p['version_code']=99;self.assertEqual(prior.select('test41')['version_code'],41)
