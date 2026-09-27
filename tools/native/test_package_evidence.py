import importlib.util,json,pathlib,unittest,hashlib,copy
s=importlib.util.spec_from_file_location('e',pathlib.Path(__file__).with_name('package_evidence.py'));m=importlib.util.module_from_spec(s);s.loader.exec_module(m)
class PackageEvidenceTest(unittest.TestCase):
 def test_valid_graph(self):self.assertEqual(m.package_names('{"ImportPath":"z"}\n{"ImportPath":"a"}'),['a','z'])
 def test_partial_graph_unknown_not_empty_proof(self):
  for text in ['','{','{"ImportPath":"a","Incomplete":true}','{"ImportPath":"a","Error":{"Err":"missing"}}','{"ImportPath":"a"}{"ImportPath":"a"}']:
   with self.assertRaises(ValueError):m.package_names(text)
 def proof(self):
  core={'commit':'abc','tags':''}
  return core,{'abi':'arm64-v8a','sha256':'digest','go_version':'go1.27.1','base_commit':'abc','package_inventory':{'packages':['main','runtime'],'goos':'android','goarch':'arm64','cgo_enabled':'1','core_spec_sha256':hashlib.sha256(json.dumps(core,sort_keys=True).encode()).hexdigest()}}
 def test_exact_android_binding(self):
  c,p=self.proof();self.assertEqual(m.verify(p,c,'arm64-v8a','digest','1.27.1'),['main','runtime'])
 def test_other_output_compiler_and_host_proof_rejected(self):
  for key,val in [('sha256','other'),('go_version','go1.26.7'),('abi','host'),('base_commit','old')]:
   c,p=self.proof();p[key]=val
   with self.assertRaises(ValueError):m.verify(p,c,'arm64-v8a','digest','1.27.1')
 def test_wrong_package_inventory_scope_rejected(self):
  for key,val in [('goos','linux'),('goarch','amd64'),('cgo_enabled','0'),('packages',[]),('core_spec_sha256','stale')]:
   c,p=self.proof();p['package_inventory'][key]=val
   with self.assertRaises(ValueError):m.verify(p,c,'arm64-v8a','digest','1.27.1')
if __name__=='__main__':unittest.main()
