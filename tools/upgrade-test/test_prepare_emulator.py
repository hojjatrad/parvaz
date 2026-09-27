"""Command-boundary tests using a fake ADB; never contacts a device."""
import os,pathlib,runpy,subprocess,sys,tempfile,unittest
from unittest.mock import patch
HELPER=pathlib.Path(__file__).with_name('prepare_emulator.py')
class EmulatorBoundaryTest(unittest.TestCase):
 def run_helper(self,serial='emulator-5554',qemu='1',sdk='30',abis='x86_64,arm64-v8a',permission='1',mutations_allowed=False,remount_requests_reboot=False):
  self.mutations=[];self.commands=[]
  def fake(cmd,**kwargs):
   self.commands.append(cmd)
   if cmd[0]=='openssl':return '1234abcd\n'
   self.assertEqual(cmd[0],'adb');args=cmd[1:]
   if args==['get-serialno']:return serial
   bound=args[:2]==['-s',serial]
   if bound:args=args[2:]
   if args[:2]==['shell','getprop']:
    return {'ro.kernel.qemu':qemu,'ro.build.version.sdk':sdk,'ro.product.cpu.abilist':abis,'sys.boot_completed':'1','ro.dalvik.vm.native.bridge':'fixture-bridge'}.get(args[2],'')
   self.mutations.append(args)
   if not mutations_allowed:raise AssertionError('An unapproved device mutation was attempted')
   self.assertTrue(bound,'Every mutation must stay bound to the verified emulator')
   if args==['remount'] and remount_requests_reboot and self.mutations.count(['remount'])==1:return 'Now reboot your device for settings to take effect'
   return ''
  with tempfile.TemporaryDirectory() as temp:
   cert=pathlib.Path(temp)/'cert.pem';cert.write_text('synthetic fixture, not a certificate')
   with patch.dict(os.environ,{'PARVAZ_DISPOSABLE_EMULATOR':permission}),patch.object(sys,'argv',[str(HELPER),str(cert)]),patch.object(subprocess,'check_output',side_effect=fake):
    runpy.run_path(str(HELPER),run_name='__main__')
 def refused(self,**kwargs):
  with self.assertRaises(SystemExit):self.run_helper(**kwargs)
  self.assertEqual(self.mutations,[])
 def test_physical_device_is_rejected_before_any_changes(self):self.refused(serial='physical-device',qemu='0')
 def test_missing_explicit_permission_is_rejected_before_adb(self):
  self.refused(permission='');self.assertEqual(self.commands,[])
 def test_qemu_property_must_confirm_emulator(self):self.refused(qemu='0')
 def test_unknown_device_is_rejected(self):self.refused(serial='unknown')
 def test_unreviewed_android_version_is_rejected(self):self.refused(sdk='34')
 def test_abi_must_be_an_exact_token(self):self.refused(abis='x86_64,not-arm64-v8a')
 def test_approved_emulator_uses_same_target_for_every_mutation(self):
  self.run_helper(mutations_allowed=True);self.assertIn(['root'],self.mutations);self.assertTrue(any(c[:1]==['push'] for c in self.mutations))
 def test_writable_avd_does_not_disable_verity_or_reboot(self):
  self.run_helper(mutations_allowed=True)
  self.assertNotIn(['disable-verity'],self.mutations);self.assertNotIn(['reboot'],self.mutations)
 def test_explicit_remount_reboot_request_uses_same_verified_target(self):
  self.run_helper(mutations_allowed=True,remount_requests_reboot=True)
  self.assertIn(['disable-verity'],self.mutations);self.assertIn(['reboot'],self.mutations);self.assertEqual(self.mutations.count(['remount']),2)
class RunnerCleanupBoundaryTest(unittest.TestCase):
 def test_refused_physical_target_is_not_mutated_by_exit_trap(self):
  import shutil
  with tempfile.TemporaryDirectory() as temp:
   root=pathlib.Path(temp);tools=root/'tools/upgrade-test';tools.mkdir(parents=True);(root/'app').mkdir();(root/'bin').mkdir()
   (root/'app/build.gradle').write_text('versionName "1.28.8"\nversionCode 42\n')
   shutil.copyfile(HELPER,tools/'prepare_emulator.py');shutil.copyfile(HELPER.with_name('run.sh'),tools/'run.sh')
   (tools/'fixture_proxy.py').write_text('raise SystemExit(0)\n')
   fake="#!"+sys.executable+"\nimport os,sys,pathlib\n"
   (root/'bin/adb').write_text(fake+"with open(os.environ['ADB_TRACE'],'a') as f:f.write(' '.join(sys.argv[1:])+'\\n')\nprint('physical-device')\n")
   (root/'bin/openssl').write_text(fake+"for flag in ['-out','-keyout']:\n if flag in sys.argv:pathlib.Path(sys.argv[sys.argv.index(flag)+1]).write_text('synthetic fixture')\n")
   (root/'bin/curl').write_text('#!/bin/sh\nexit 91\n')
   for p in (root/'bin').iterdir():p.chmod(0o700)
   trace=root/'adb-trace.txt';env=dict(os.environ,PATH=str(root/'bin')+os.pathsep+os.environ['PATH'],ADB_TRACE=str(trace))
   result=subprocess.run(['bash','tools/upgrade-test/run.sh'],cwd=root,env=env,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True,timeout=10)
   self.assertNotEqual(result.returncode,0);self.assertIn('Disposable emulator serial required',result.stderr)
   self.assertEqual(trace.read_text().splitlines(),['get-serialno','get-serialno'])
if __name__=='__main__':unittest.main()
