import importlib.util,json,pathlib,unittest,hashlib,copy
s=importlib.util.spec_from_file_location('e',pathlib.Path(__file__).with_name('package_evidence.py'));m=importlib.util.module_from_spec(s);s.loader.exec_module(m)
class PackageEvidenceTest(unittest.TestCase):
 def test_valid_graph(self):self.assertEqual(m.package_names('{"ImportPath":"z"}\n{"ImportPath":"a"}'),['a','z'])
 def test_partial_graph_unknown_not_empty_proof(self):
  for text in ['','{','{"ImportPath":"a","Incomplete":true}','{"ImportPath":"a","Error":{"Err":"missing"}}','{"ImportPath":"a"}{"ImportPath":"a"}']:
   with self.assertRaises(ValueError):m.package_names(text)
 def proof(self):
  core={'commit':'abc','tags':'','repository':'Example/main','package':'.'}
  return core,{'abi':'arm64-v8a','sha256':'digest','go_version':'go1.27.1','base_commit':'abc','package_inventory':{'coverage_schema':2,'inventory_command':m.inventory_command(core,'android'),'root':{'import_path':'github.com/Example/main','name':'main','module_path':'github.com/Example/main','module_main':True,'dependencies':['runtime']},'packages':['github.com/Example/main','runtime'],'goos':'android','goarch':'arm64','cgo_enabled':'1','core_spec_sha256':hashlib.sha256(json.dumps(core,sort_keys=True).encode()).hexdigest()}}
 def test_exact_android_binding(self):
  c,p=self.proof();self.assertEqual(m.verify(p,c,'arm64-v8a','digest','1.27.1'),['github.com/Example/main','runtime'])
 def test_other_output_compiler_and_host_proof_rejected(self):
  for key,val in [('sha256','other'),('go_version','go1.26.7'),('abi','host'),('base_commit','old')]:
   c,p=self.proof();p[key]=val
   with self.assertRaises(ValueError):m.verify(p,c,'arm64-v8a','digest','1.27.1')
 def test_wrong_package_inventory_scope_rejected(self):
  for key,val in [('goos','linux'),('goarch','amd64'),('cgo_enabled','0'),('packages',[]),('core_spec_sha256','stale')]:
   c,p=self.proof();p['package_inventory'][key]=val
   with self.assertRaises(ValueError):m.verify(p,c,'arm64-v8a','digest','1.27.1')
 def test_nonempty_but_truncated_graph_rejected(self):
  c,p=self.proof();p['package_inventory']['packages'].remove('runtime')
  with self.assertRaises(ValueError):m.verify(p,c,'arm64-v8a','digest','1.27.1')
 def test_unrelated_root_and_legacy_proof_rejected(self):
  for key,value in [('coverage_schema',1),('inventory_command',['go','list','runtime']),('root',{'import_path':'other','name':'main'})]:
   c,p=self.proof();p['package_inventory'][key]=value
   with self.assertRaises(ValueError):m.verify(p,c,'arm64-v8a','digest','1.27.1')
 def test_actual_root_transitive_closure(self):
  c,_=self.proof();rows=[{'ImportPath':'runtime','DepOnly':True},{'ImportPath':'github.com/Example/main','Name':'main','Module':{'Path':'github.com/Example/main','Main':True},'Deps':['runtime']}]
  text=lambda:''.join(json.dumps(r) for r in rows)
  self.assertEqual(m.closed_graph(text(),c)['coverage_schema'],2)
  rows[1]['Deps'].append('missing/package')
  with self.assertRaises(ValueError):m.closed_graph(text(),c)
 def test_multiple_roots_rejected(self):
  c,_=self.proof()
  with self.assertRaises(ValueError):m.closed_graph('{\"ImportPath\":\"a\"}{\"ImportPath\":\"b\"}'.replace('\x1f','"'),c)
 def test_generated_jni_root_uses_the_same_closure_contract(self):
  rows=[{'ImportPath':'runtime','DepOnly':True},{'ImportPath':'gobind/gobind','Name':'main','Module':{'Path':'gobind','Main':True},'Deps':['runtime']}]
  graph=m.closed_import_graph(''.join(json.dumps(r) for r in rows));self.assertEqual(graph['root']['module_path'],'gobind')
  rows[1]['Deps'].append('omitted/package')
  with self.assertRaises(ValueError):m.closed_import_graph(''.join(json.dumps(r) for r in rows))
if __name__=='__main__':unittest.main()
