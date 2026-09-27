#!/usr/bin/env python3
"""Explicitly approved disposable Android30 emulator only; no APK trust change.
Every device command is pinned to the emulator selected before modification.
Importing this module has no ADB or filesystem side effects.
"""
import os,pathlib,re,subprocess,sys,time

def adb(*args):
 return subprocess.check_output(['adb',*args],text=True,stderr=subprocess.STDOUT,timeout=60).strip()

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
 # Validate the certificate before root, reboot or any trust-store modification.
 digest=subprocess.check_output(['openssl','x509','-in',str(cert),'-subject_hash_old','-noout'],text=True).splitlines()[0]
 if not re.fullmatch(r'[0-9a-fA-F]{8}',digest):raise SystemExit('Invalid temporary certificate hash')
 device('root');device('wait-for-device');device('disable-verity');device('reboot');device('wait-for-device')
 deadline=time.monotonic()+150
 while device('shell','getprop','sys.boot_completed')!='1':
  if time.monotonic()>deadline:raise SystemExit('Emulator boot deadline')
  time.sleep(2)
 require_target(device)
 device('root');device('wait-for-device');device('remount')
 destination='/system/etc/security/cacerts/'+digest+'.0'
 device('push',str(cert),destination);device('shell','chmod','644',destination)
 device('shell','settings','put','global','http_proxy','10.0.2.2:8765')
 for setting in ['window_animation_scale','transition_animation_scale','animator_duration_scale']:
  device('shell','settings','put','global',setting,'0')
 print('CI_ONLY_STAGED_TLS_TRUST_READY (verified disposable emulator; unmodified signed APK)')

if __name__=='__main__':
 if len(sys.argv)!=2:raise SystemExit('Usage: prepare_emulator.py temporary-certificate.pem')
 prepare(pathlib.Path(sys.argv[1]))
