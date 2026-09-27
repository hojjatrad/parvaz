import copy,hashlib,importlib.util,pathlib,tempfile,unittest
spec=importlib.util.spec_from_file_location('bind',pathlib.Path(__file__).with_name('bind_installation.py'));m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
class BindInstallationTest(unittest.TestCase):
 def setUp(self):
  self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup);self.root=pathlib.Path(self.temp.name);hashes={}
  for suffix in ['-arm64','']:
   (self.root/('Parvaz-1.28.8-TEST'+suffix+'.apk')).write_bytes(b'dummy, not APK')
   hashes['Parvaz-1.28.8'+suffix+'.apk']=hashlib.sha256(b'dummy, not APK').hexdigest()
  self.results=[{'prior':m.prior.select(p),'source_commit':'fixture','candidate_apk_sha256':dict(hashes),'tests':[{'tests':1,'failures':0,'errors':0,'skipped':0}]} for p in ['stable39','test41']]
 def verify(self):m.verify(self.results,self.root,'fixture')
 def test_two_bound_fixtures(self):self.verify()
 def test_one_prior_is_not_enough(self):
  self.results.pop()
  with self.assertRaises(ValueError):self.verify()
 def test_duplicate_cannot_replace_missing_prior(self):
  self.results[1]=copy.deepcopy(self.results[0])
  with self.assertRaises(ValueError):self.verify()
 def test_different_source_is_rejected(self):
  self.results[0]['source_commit']='old'
  with self.assertRaises(ValueError):self.verify()
 def test_changed_apk_is_rejected(self):
  (self.root/'Parvaz-1.28.8-TEST.apk').write_bytes(b'different')
  with self.assertRaises(ValueError):self.verify()
 def test_skipped_failed_or_absent_test_rejected(self):
  for key in ['failures','errors','skipped']:
   self.results[0]['tests'][0][key]=1
   with self.assertRaises(ValueError):self.verify()
   self.results[0]['tests'][0][key]=0
  self.results[0]['tests']=[]
  with self.assertRaises(ValueError):self.verify()
