import importlib.util,pathlib,unittest
from unittest.mock import patch
s=importlib.util.spec_from_file_location('floors',pathlib.Path(__file__).with_name('dependency_floors.py'));m=importlib.util.module_from_spec(s);s.loader.exec_module(m)
class FloorsTest(unittest.TestCase):
 def test_targeted_upgrade(self):self.assertEqual(m.upgrades({'a':{'Version':'v1.2.3'}},{'a':'v1.2.4'}),['a@v1.2.4'])
 def test_preserve_higher(self):self.assertEqual(m.upgrades({'a':{'Version':'v1.3.0'}},{'a':'v1.2.4'}),[])
 def test_no_unrelated_addition(self):self.assertEqual(m.upgrades({}, {'a':'v1.2.4'}),[])
 def test_equal_is_unchanged(self):self.assertEqual(m.upgrades({'a':{'Version':'v1.2.4'}},{'a':'v1.2.4'}),[])
 def test_fork_requires_review(self):
  with self.assertRaises(ValueError):m.upgrades({'a':{'Version':'v1.2.3','Replace':{'Version':'v1.2.4'}}},{'a':'v1.2.4'})
 def test_pseudo_versions_compare_timestamp(self):self.assertLess(m.version_key('v0.0.0-20250109001534-8abf58130905'),m.version_key('v0.0.0-20260719225207-c76316d4aa82'))
 def test_unknown_versions_fail_closed(self):
  for x in ['(devel)','v1.3.0-rc1','latest','1.2.3','v1.2.3;echo bad']:
   with self.assertRaises(ValueError):m.version_key(x)
 def test_transitive_upgrade_satisfies_lower_floor_without_forcing_downgrade(self):
  before={'a':{'Version':'v1.0.0'},'b':{'Version':'v1.0.0'}}
  after={'a':{'Version':'v1.2.0'},'b':{'Version':'v1.5.0'}}
  with patch.object(m,'read_modules',side_effect=[before,after]),patch.object(m.subprocess,'run') as run:
   result=m.apply(pathlib.Path('.'),{}, {'a':'v1.2.0','b':'v1.3.0'})
   self.assertEqual(result['requested_changes'],['a@v1.2.0']);self.assertEqual(run.call_count,1)
 def test_nonconvergent_resolver_fails(self):
  with patch.object(m,'read_modules',return_value={'a':{'Version':'v1.0.0'}}),patch.object(m.subprocess,'run'):
   with self.assertRaises(ValueError):m.apply(pathlib.Path('.'),{}, {'a':'v1.2.0'})
 def test_unintended_transitive_downgrade_fails(self):
  before={'a':{'Version':'v1.0.0'},'b':{'Version':'v1.5.0'}}
  after={'a':{'Version':'v1.2.0'},'b':{'Version':'v1.4.0'}}
  with patch.object(m,'read_modules',side_effect=[before,after]),patch.object(m.subprocess,'run'):
   with self.assertRaises(ValueError):m.apply(pathlib.Path('.'),{}, {'a':'v1.2.0'})
if __name__=='__main__':unittest.main()
