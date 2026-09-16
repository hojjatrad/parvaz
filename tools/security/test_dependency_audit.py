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
