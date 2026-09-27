import copy,hashlib,importlib.util,json,pathlib,sys,tempfile,unittest,zipfile
sys.path.insert(0,str(pathlib.Path(__file__).parent))
import xray_candidate as m
from source_manifest import collect,canonical
class CandidateTest(unittest.TestCase):
 def fixture(self):
  packages=['runtime','github.com/2dust/AndroidLibXrayLite','golang.org/x/mobile/bind/seq','gobind/gobind']
  lock={'schema':1,'base_aar_sha256':'a'*64,'aar_sha256':'b'*64,'source_provenance':{'go_version':'go1.27.1'},'jni_sha256':{a:hashlib.sha256(a.encode()).hexdigest() for a in m.ARCH},'generated_go_mod_sha256':{a:'c'*64 for a in m.ARCH}}
  report={'base_aar_sha256':lock['base_aar_sha256'],'aar_sha256':lock['aar_sha256'],'source_provenance':copy.deepcopy(lock['source_provenance']),'public_java_api_equal':True,'all_java_class_bytes_equal':True,'binaries':[]}
  for abi,arch in m.ARCH.items():
   p={'coverage_schema':2,'inventory_command':m.GRAPH_COMMAND,'goos':'android','goarch':arch,'cgo_enabled':'1','generated_go_mod_sha256':'c'*64,'packages':packages.copy(),'root':{'name':'main','module_main':True,'module_path':'gobind','import_path':'gobind/gobind','dependencies':packages[:-1]}}
   report['binaries'].append({'abi':abi,'sha256':lock['jni_sha256'][abi],'extraction_exit':0,'extraction':{'goos':'android','goarch':arch,'symbol_count':1},'compiler_inventory':p})
  return lock,report
 def test_exact_contract(self):l,r=self.fixture();self.assertEqual(len(m.validate_report(r,l)),2)
 def test_other_aar_or_source_rejected(self):
  for field in ['base_aar_sha256','aar_sha256','source_provenance']:
   l,r=self.fixture();r[field]='changed'
   with self.assertRaises(ValueError):m.validate_report(r,l)
 def test_missing_java_compatibility_rejected(self):
  l,r=self.fixture();r['all_java_class_bytes_equal']=False
  with self.assertRaises(ValueError):m.validate_report(r,l)
 def test_duplicate_abi_rejected(self):
  l,r=self.fixture();r['binaries'][1]=copy.deepcopy(r['binaries'][0])
  with self.assertRaises(ValueError):m.validate_report(r,l)
 def test_wrong_target_or_generated_module_rejected(self):
  for key,value in [('goarch','amd64'),('goos','linux'),('cgo_enabled','0'),('generated_go_mod_sha256','d'*64),('coverage_schema',1),('inventory_command',['go','list'])]:
   l,r=self.fixture();r['binaries'][0]['compiler_inventory'][key]=value
   with self.assertRaises(ValueError):m.validate_report(r,l)
 def test_truncated_graph_rejected(self):
  l,r=self.fixture();r['binaries'][0]['compiler_inventory']['packages'].remove('runtime')
  with self.assertRaises(ValueError):m.validate_report(r,l)
 def test_source_must_match_aar_after_integration(self):
  with tempfile.TemporaryDirectory() as d:
   root=pathlib.Path(d);l,r=self.fixture();source=root/'.cache/native/xray-wrapper';source.mkdir(parents=True);(source/'go.mod').write_text('module fixture\n')
   evidence=root/'.cache/xray-security-review';evidence.mkdir(parents=True)
   manifest=canonical(collect(source));(evidence/'source-tree-manifest.json').write_bytes(manifest)
   l['source_provenance'].update(source_lock={'commit':'fixture'},source_tree_manifest_sha256=hashlib.sha256(manifest).hexdigest());r['source_provenance']=copy.deepcopy(l['source_provenance'])
   aar=root/'app/libs/libv2ray.aar';aar.parent.mkdir(parents=True)
   with zipfile.ZipFile(aar,'w') as z:
    for abi in m.ARCH:z.writestr('jni/'+abi+'/libgojni.so',abi.encode())
   l['aar_sha256']=r['aar_sha256']=m.digest(aar)
   for path,data in [('tools/native/xray-candidate-lock.json',l),('tools/native/xray-source-lock.json',l['source_provenance']['source_lock']),('tools/release/core-lock.json',{'sha256':l['base_aar_sha256']}),('.cache/xray-security-review/summary.json',r)]:
    p=root/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(data))
   self.assertEqual(len(m.verify(root)['binaries']),2)
   (source/'go.mod').write_text('baseline reset\n')
   with self.assertRaises(ValueError):m.verify(root)
if __name__=='__main__':unittest.main()
