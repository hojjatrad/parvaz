import importlib.util,pathlib,unittest
spec=importlib.util.spec_from_file_location('binary_inventory',pathlib.Path(__file__).with_name('binary_inventory.py'));inventory=importlib.util.module_from_spec(spec);spec.loader.exec_module(inventory)
class BinaryInventoryTest(unittest.TestCase):
 def test_realistic_embedded_compiler_and_dependencies(self):
  value=inventory.parse('/tmp/engine.so: go1.26.7\n\tpath\texample.org/engine\n\tmod\texample.org/engine\t(devel)\n\tdep\texample.org/library\tv1.2.3\th1:sum\n\tbuild\tGOOS=android\n')
  self.assertEqual(value,{'go_version':'1.26.7','modules':[{'name':'example.org/library','version':'v1.2.3'}],'main_module':{'name':'example.org/engine','version':'(devel)'}})
 def test_replacement_uses_actual_module_not_original(self):
  value=inventory.parse('engine.so: go1.26.7\n\tdep\texample.org/original\tv1.0.0\th1:x\n\t=>\texample.org/fork\tv1.0.2\th1:y\n')
  self.assertEqual(value['modules'],[{'name':'example.org/fork','version':'v1.0.2'}])
 def test_only_explicit_pinned_wrapper_root_can_resolve_gomobile_local_main(self):
  text='engine.so: go1.27.1\n dep github.com/2dust/AndroidLibXrayLite v0.0.0-00010101000000-000000000000\n => /runner/source (devel)\n'
  with self.assertRaises(ValueError):inventory.parse(text)
  root={'name':'github.com/2dust/AndroidLibXrayLite','version':'v26.9.9','commit':'abcd'}
  self.assertEqual(inventory.parse(text,root)['modules'][0]['source_commit'],'abcd')
  with self.assertRaises(ValueError):inventory.parse(text.replace('github.com/2dust/AndroidLibXrayLite','attacker/module'),root)
 def test_absent_metadata_is_not_a_clean_bill_of_health(self):
  for text in ['', 'not a Go executable','engine.so: go1.26.7\n path main']:
   with self.assertRaises(ValueError):inventory.parse(text)
 def test_unversioned_or_incomplete_replacement_blocks(self):
  for text in ['engine.so: go1.26.7\n dep x (devel)','engine.so: go1.26.7\n => x v1.0.0']:
   with self.assertRaises(ValueError):inventory.parse(text)
if __name__=='__main__':unittest.main()
