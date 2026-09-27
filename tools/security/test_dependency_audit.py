import importlib.util,json,pathlib,tempfile,unittest
from unittest.mock import patch
spec=importlib.util.spec_from_file_location('dependency_audit',pathlib.Path(__file__).with_name('dependency_audit.py'));audit=importlib.util.module_from_spec(spec);spec.loader.exec_module(audit)
class DependencyAuditTest(unittest.TestCase):
 def test_concatenated_go_objects(self):
  self.assertEqual(list(audit.objects(' {"Path":"one"}\n {"Path":"two"}\n')), [{'Path':'one'},{'Path':'two'}])
 def test_malformed_graph_not_silently_accepted(self):
  with self.assertRaises(ValueError):list(audit.objects('{"Path":"one"}\n bad'))
 def test_package_coordinates_keep_transitive_identity(self):
  self.assertEqual(audit.purl({'ecosystem':'Maven','name':'a.b:transitive','version':'1.2'}),'pkg:maven/a.b/transitive@1.2')
  self.assertEqual(audit.purl({'ecosystem':'Go','name':'example.org/a/b','version':'v1.2.3'}),'pkg:golang/example.org/a/b@v1.2.3')
 def test_git_source_purl_is_a_package_not_an_embedded_url(self):
  self.assertEqual(audit.purl({'ecosystem':'Git','name':'https://github.com/owner/project','version':'abcd'}),'pkg:github/owner/project@abcd')
  self.assertEqual(audit.purl({'ecosystem':'Git','name':'SagerNet/sing-box','version':'abcd'}),'pkg:github/sagernet/sing-box@abcd')
 def test_inventory_contains_platform_nodes_and_resolved_artifacts(self):
  with tempfile.TemporaryDirectory() as temp:
   out=pathlib.Path(temp);(out/'java-runtime.json').write_text(json.dumps({'components':[{'group':'a','name':'bom','version':'1','artifacts':[]},{'group':'a','name':'nested','version':'2','artifacts':[{'file':'nested.aar','sha256':'0'*64}]}]}))
   with patch.object(audit,'OUT',out):rows=audit.components(False)
   self.assertEqual({r['name'] for r in rows},{'a:bom','a:nested'})
 def test_missing_native_graph_fails_closed(self):
  with tempfile.TemporaryDirectory() as temp:
   root=pathlib.Path(temp);(root/'tools/native').mkdir(parents=True);(root/'java-runtime.json').write_text('{"components":[]}');(root/'tools/native/engines-lock.json').write_text('[{"name":"missing"}]');(root/'tools/native/xray-source-lock.json').write_text('{}')
   with patch.object(audit,'ROOT',root),patch.object(audit,'OUT',root):
    with self.assertRaisesRegex(ValueError,'Missing shipped native binary'):audit.components(True)
if __name__=='__main__':unittest.main()

class AdvisoryPlatformScopeTest(unittest.TestCase):
 def setUp(self):
  self.c={'ecosystem':'Go','name':'example.org/sys','sources':[{'binary':'arm64.so','goos':'android'},{'binary':'arm.so','goos':'android'}]}
  self.a={'affected':[{'package':{'name':'example.org/sys','ecosystem':'Go'},'ecosystem_specific':{'imports':[{'path':'example.org/sys/windows','goos':['windows']}]}}]}
 def check(self):return audit.excludes_verified_binary_os(self.c,self.a)
 def test_explicit_windows_only_cannot_apply_to_both_android_binaries(self):self.assertTrue(self.check())
 def test_android_linux_overlap_is_not_excluded(self):
  for os in ['android','linux']:
   self.a['affected'][0]['ecosystem_specific']['imports'][0]['goos']=[os];self.assertFalse(self.check())
 def test_unknown_binary_os_keeps_finding_open(self):
  del self.c['sources'][1]['goos'];self.assertFalse(self.check())
 def test_one_affected_abi_keeps_finding_open(self):
  self.c['sources'][1]['goos']='windows';self.assertFalse(self.check())
 def test_no_binary_is_not_os_evidence(self):
  self.c['sources']=[];self.assertFalse(self.check())
 def test_missing_or_empty_advisory_scope_keeps_finding_open(self):
  self.a['affected'][0]['ecosystem_specific']['imports'][0]['goos']=[];self.assertFalse(self.check())
  self.a['affected'][0]['ecosystem_specific']['imports']=[];self.assertFalse(self.check())
 def test_one_unscoped_import_keeps_finding_open(self):
  self.a['affected'][0]['ecosystem_specific']['imports'].append({'path':'example.org/sys/shared'});self.assertFalse(self.check())
 def test_fork_or_foreign_module_cannot_inherit_an_exclusion(self):
  self.c['sources'][0]['fork_target']='fork/version';self.assertFalse(self.check());del self.c['sources'][0]['fork_target']
  self.c['name']='other';self.assertFalse(self.check())
