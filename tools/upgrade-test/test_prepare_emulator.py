"""Command-boundary tests using a fake ADB; never contacts a device."""
import os,pathlib,runpy,subprocess,sys,tempfile,unittest
from unittest.mock import patch
HELPER=pathlib.Path(__file__).with_name('prepare_emulator.py')
class EmulatorBoundaryTest(unittest.TestCase):
 def run_helper(self,serial='emulator-5554',qemu='1',sdk='30',abis='x86_64,arm64-v8a',permission='1',mutations_allowed=False):
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
if __name__=='__main__':unittest.main()
