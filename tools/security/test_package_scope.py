import importlib.util,pathlib,unittest,copy
s=importlib.util.spec_from_file_location('audit',pathlib.Path(__file__).with_name('dependency_audit.py'));m=importlib.util.module_from_spec(s);s.loader.exec_module(m)
class PackageScopeTest(unittest.TestCase):
 def setUp(self):
  self.c={'ecosystem':'Go','name':'example.org/crypto','sources':[{'binary':abi,'package_coverage_verified':True,'package_proof_sha256':'abc','compiled_packages':['example.org/crypto/ssh']} for abi in ['arm64','arm']]}
  self.a={'affected':[{'package':{'ecosystem':'Go','name':'example.org/crypto'},'ecosystem_specific':{'imports':[{'path':'example.org/crypto/openpgp'}]}}]}
 def result(self):return m.excludes_verified_compiled_packages(self.c,self.a)
 def test_absence_from_all_proven_android_graphs(self):self.assertTrue(self.result())
 def test_one_affected_abi_stays_open(self):self.c['sources'][1]['compiled_packages'].append('example.org/crypto/openpgp');self.assertFalse(self.result())
 def test_one_missing_proof_stays_open(self):self.c['sources'][1].pop('package_proof_sha256');self.assertFalse(self.result())
 def test_host_or_raw_scanner_results_do_not_qualify(self):self.c['sources'][0]['package_coverage_verified']=False;self.assertFalse(self.result())
 def test_fork_baseline_stays_open(self):self.c['sources'][0]['fork_target']='other/fork';self.assertFalse(self.result())
 def test_missing_or_wildcard_advisory_scope_stays_open(self):
  for imports in [[],None,[{}],[{'path':'example.org/crypto/*'}]]:
   self.a['affected'][0]['ecosystem_specific']['imports']=imports;self.assertFalse(self.result())
 def test_different_module_cannot_borrow_proof(self):self.a['affected'][0]['package']['name']='other';self.assertFalse(self.result())
 def test_another_affected_package_keeps_entire_advisory_open(self):self.a['affected'][0]['ecosystem_specific']['imports'].append({'path':'example.org/crypto/ssh'});self.assertFalse(self.result())
 def test_no_binary_is_not_compiler_evidence(self):self.c['sources']=[];self.assertFalse(self.result())
if __name__=='__main__':unittest.main()
