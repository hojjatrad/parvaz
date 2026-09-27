import fcntl,importlib.util,pathlib,subprocess,tempfile,unittest
from unittest.mock import patch
spec=importlib.util.spec_from_file_location('guard',pathlib.Path(__file__).with_name('guard.py'));guard=importlib.util.module_from_spec(spec);spec.loader.exec_module(guard)
class WorkspaceGuardTest(unittest.TestCase):
 def setUp(self):
  self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup);self.root=pathlib.Path(self.temp.name);subprocess.run(['git','init','-q',str(self.root)],check=True);self.cache=self.root/'.cache/audit';self.cache.mkdir(parents=True)
 def test_known_reproducible_output_is_removed_but_source_and_backup_survive(self):
  (self.cache/'classes').mkdir();(self.cache/'classes/A.class').write_bytes(b'fixture');(self.cache/'json.jar').write_bytes(b'download fixture');(self.root/'source.java').write_text('source');(self.root/'backup.zip').write_bytes(b'backup')
  result=guard.clean_audit_cache(self.root);self.assertEqual(result['removed_files'],2);self.assertTrue((self.root/'source.java').exists());self.assertTrue((self.root/'backup.zip').exists())
 def test_unknown_file_refuses_entire_cleanup(self):
  (self.cache/'json.jar').write_bytes(b'cache');(self.cache/'notes.txt').write_text('unique')
  with self.assertRaises(ValueError):guard.clean_audit_cache(self.root)
  self.assertTrue((self.cache/'json.jar').exists());self.assertTrue((self.cache/'notes.txt').exists())
 def test_tracked_output_is_protected(self):
  (self.cache/'json.jar').write_bytes(b'tracked');subprocess.run(['git','-C',str(self.root),'add','-f','.cache/audit/json.jar'],check=True)
  with self.assertRaises(ValueError):guard.clean_audit_cache(self.root)
 def test_symlink_inside_cache_is_protected(self):
  secret=self.root/'backup';secret.write_text('fixture');(self.cache/'json.jar').symlink_to(secret)
  with self.assertRaises(ValueError):guard.clean_audit_cache(self.root)
  self.assertEqual(secret.read_text(),'fixture')
 def test_symlink_cache_root_is_rejected(self):
  self.cache.rmdir();other=self.root/'source';other.mkdir();self.cache.symlink_to(other,target_is_directory=True)
  with self.assertRaises(ValueError):guard.clean_audit_cache(self.root)
 def test_busy_audit_cannot_be_deleted(self):
  with (self.root/'.cache/audit.lock').open('a') as lock:
   fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
   with self.assertRaises(ValueError):guard.clean_audit_cache(self.root)
  self.assertTrue(self.cache.exists())
 def test_budget_check_is_read_only_and_stops_when_too_large(self):
  before=set(self.root.rglob('*'));self.assertEqual(guard.budget(self.root,limit_mib=0)['status'],'STOP_HEAVY_WORK');self.assertEqual(before,set(self.root.rglob('*')))
 def test_low_free_space_stops_heavy_work(self):
  with patch.object(guard.shutil,'disk_usage',return_value=type('Disk',(),{'free':1})()):self.assertEqual(guard.budget(self.root)['status'],'STOP_HEAVY_WORK')
if __name__=='__main__':unittest.main()
