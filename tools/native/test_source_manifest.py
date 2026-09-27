import importlib.util,pathlib,tempfile,unittest
s=importlib.util.spec_from_file_location('manifest',pathlib.Path(__file__).with_name('source_manifest.py'));m=importlib.util.module_from_spec(s);s.loader.exec_module(m)
class ManifestTest(unittest.TestCase):
 def test_exact_bytes_and_paths_are_deterministic(self):
  with tempfile.TemporaryDirectory() as d:
   root=pathlib.Path(d);(root/'go.mod').write_text('module example\n');first=m.canonical(m.collect(root));self.assertEqual(first,m.canonical(m.collect(root)))
   (root/'go.mod').write_text('module changed\n');self.assertNotEqual(first,m.canonical(m.collect(root)))
 def test_empty_is_not_corresponding_source(self):
  with tempfile.TemporaryDirectory() as d:
   with self.assertRaises(ValueError):m.collect(d)
 def test_symlink_is_not_followed(self):
  with tempfile.TemporaryDirectory() as d:
   p=pathlib.Path(d);(p/'outside').symlink_to('/etc/hosts')
   with self.assertRaises(ValueError):m.collect(p)
if __name__=='__main__':unittest.main()
