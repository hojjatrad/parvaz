import copy,importlib.util,pathlib,unittest
spec=importlib.util.spec_from_file_location('boundary',pathlib.Path(__file__).with_name('security_boundary.py'));m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
class SecurityBoundaryTest(unittest.TestCase):
 def setUp(self):
  self.b=[{'binary':'fixture-'+str(i),'abi':'arm','sha256':str(i)*64} for i in range(6)]
  self.p={'binary_sha256':{b['binary']+'|'+b['abi']:b['sha256'] for b in self.b},'known_go_findings':[['example/module','v1.0.0','GO-fixture']]}
  self.r={'source_commit':'fixture','native_graphs_included':True,'binary_runtime_verified':True,'status':'REVIEW_REQUIRED','findings':[{'disposition':'REVIEW_REQUIRED','component':{'ecosystem':'Go','name':'example/module','version':'v1.0.0'},'vulnerabilities':[{'id':'GO-fixture'}]}],'advisories':{'GO-fixture':{}}}
 def verify(self,code=2):return m.verify(self.r,self.b,self.p,'fixture',code)
 def test_known_same_bytes_only(self):self.assertEqual(self.verify(),1)
 def test_changed_native_binary(self):
  self.b[0]['sha256']='new'
  with self.assertRaises(ValueError):self.verify()
 def test_missing_or_duplicate_binary(self):
  self.b[-1]=copy.deepcopy(self.b[0])
  with self.assertRaises(ValueError):self.verify()
 def test_new_advisory_same_coordinate(self):
  self.r['findings'][0]['vulnerabilities'].append({'id':'GO-new'});self.r['advisories']['GO-new']={}
  with self.assertRaises(ValueError):self.verify()
 def test_new_version(self):
  self.r['findings'][0]['component']['version']='v1.0.1'
  with self.assertRaises(ValueError):self.verify()
 def test_java_rejected(self):
  self.r['findings'][0]['component']['ecosystem']='Maven'
  with self.assertRaises(ValueError):self.verify()
 def test_stale_commit(self):
  self.r['source_commit']='old'
  with self.assertRaises(ValueError):self.verify()
 def test_incomplete_scan(self):
  self.r['native_graphs_included']=False
  with self.assertRaises(ValueError):self.verify()
 def test_scanner_failure_is_fatal(self):
  for code in [1,3,124]:
   with self.assertRaises(ValueError):self.verify(code)
 def test_mismatched_exit_is_fatal(self):
  with self.assertRaises(ValueError):self.verify(0)
 def test_missing_advisory_details_is_fatal(self):
  self.r['advisories']={}
  with self.assertRaises(KeyError):self.verify()
 def test_clean_result_not_claimed_with_unknown_status(self):
  self.r['findings']=[]
  with self.assertRaises(ValueError):self.verify(0)
  self.r['status']='NO_KNOWN_MATCHES_IN_SCANNED_SCOPE';self.assertEqual(self.verify(0),0)
