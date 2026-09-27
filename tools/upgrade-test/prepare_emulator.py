#!/usr/bin/env python3
"""Explicitly approved Android30 emulator only; no production APK trust change.
Use a RAM-only system-CA overlay in init's mount namespace, preserving existing
roots. Never disable verity, remount the disk image, or reboot the emulator.
"""
import os,pathlib,re,subprocess,sys,time

def adb(*args):
 try:
  return subprocess.check_output(['adb',*args],text=True,stderr=subprocess.STDOUT,timeout=60).strip()
 except subprocess.CalledProcessError as e:
  raise SystemExit('ADB fixture command failed: '+str(e.output)[-1500:]) from None

def require_target(device):
 if device('shell','getprop','ro.kernel.qemu')!='1':raise SystemExit('Disposable emulator required; refusing device changes')
 if device('shell','getprop','ro.build.version.sdk')!='30':raise SystemExit('Only the reviewed Android30 upgrade fixture is allowed')
 abis=device('shell','getprop','ro.product.cpu.abilist').split(',')
 if 'arm64-v8a' not in abis:raise SystemExit('ARM64 support unavailable; actual APK test cannot be claimed')

def prepare(cert):
 if os.environ.get('PARVAZ_DISPOSABLE_EMULATOR')!='1':raise SystemExit('Explicit disposable-emulator permission required')
 if not cert.is_file():raise SystemExit('Temporary transport certificate missing')
 serial=adb('get-serialno')
 if not re.fullmatch(r'emulator-\d+',serial):raise SystemExit('Disposable emulator serial required; refusing device changes')
 device=lambda *args:adb('-s',serial,*args)
 require_target(device)
 digest=subprocess.check_output(['openssl','x509','-in',str(cert),'-subject_hash_old','-noout'],text=True).splitlines()[0]
 if not re.fullmatch(r'[0-9a-fA-F]{8}',digest):raise SystemExit('Invalid temporary certificate hash')
 device('root');device('wait-for-device');require_target(device)
 work=device('shell','mktemp','-d','/data/local/tmp/parvaz-ca.XXXXXX')
 if not re.fullmatch(r'/data/local/tmp/parvaz-ca\.[A-Za-z0-9]{6}',work):raise SystemExit('Unexpected disposable staging directory')
 store='/system/etc/security/cacerts'
 device('shell','cp','-R',store+'/.',work)
 device('push',str(cert),work+'/'+digest+'.0')
 device('shell','chmod','644',work+'/'+digest+'.0')
 ns=('shell','nsenter','-t','1','-m','--')
 stopped=False
 try:
  # New zygote/app processes must inherit init's mount, not adbd's namespace.
  device('shell','stop');stopped=True
  device(*ns,'mount','-t','tmpfs','-o','mode=0755,uid=0,gid=0,context=u:object_r:system_file:s0','tmpfs',store)
  device(*ns,'cp','-R',work+'/.',store)
  if device(*ns,'cat',store+'/'+digest+'.0')!=cert.read_text().strip():raise SystemExit('Temporary CA readback mismatch')
  device('shell','setprop','sys.boot_completed','0')
 finally:
  if stopped:device('shell','start')
 deadline=time.monotonic()+180
 while device('shell','getprop','sys.boot_completed')!='1':
  if time.monotonic()>deadline:raise SystemExit('Framework restart deadline; no installation proof')
  time.sleep(2)
 require_target(device)
 device('shell','settings','put','global','http_proxy','10.0.2.2:8765')
 for setting in ['window_animation_scale','transition_animation_scale','animator_duration_scale']:
  device('shell','settings','put','global',setting,'0')
 print('CI_ONLY_STAGED_TLS_TRUST_READY (verified disposable emulator, RAM-only CA overlay; unmodified signed APK)')

if __name__=='__main__':
 if len(sys.argv)!=2:raise SystemExit('Usage: prepare_emulator.py temporary-certificate.pem')
 prepare(pathlib.Path(sys.argv[1]))
