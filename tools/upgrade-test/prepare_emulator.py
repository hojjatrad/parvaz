#!/usr/bin/env python3
"""Disposable CI emulator only: install a temporary CA, NEVER change APK trust."""
import pathlib,subprocess,time,sys
cert=pathlib.Path(sys.argv[1])
def adb(*args):return subprocess.check_output(['adb',*args],text=True,stderr=subprocess.STDOUT,timeout=60).strip()
print('ABI',adb('shell','getprop','ro.product.cpu.abilist'));print('NATIVE_BRIDGE',adb('shell','getprop','ro.dalvik.vm.native.bridge'))
if 'arm64-v8a' not in adb('shell','getprop','ro.product.cpu.abilist'):raise SystemExit('ARM64 native bridge unavailable; actual APK test cannot be claimed')
adb('root');adb('wait-for-device');print(adb('disable-verity'));adb('reboot');adb('wait-for-device')
deadline=time.monotonic()+150
while adb('shell','getprop','sys.boot_completed')!='1':
 if time.monotonic()>deadline:raise SystemExit('Emulator boot deadline')
 time.sleep(2)
adb('root');adb('wait-for-device');print(adb('remount'))
hashname=subprocess.check_output(['openssl','x509','-in',str(cert),'-subject_hash_old','-noout'],text=True).splitlines()[0]+'.0'
adb('push',str(cert),'/system/etc/security/cacerts/'+hashname);adb('shell','chmod','644','/system/etc/security/cacerts/'+hashname)
adb('shell','settings','put','global','http_proxy','10.0.2.2:8765');adb('shell','settings','put','global','window_animation_scale','0');adb('shell','settings','put','global','transition_animation_scale','0');adb('shell','settings','put','global','animator_duration_scale','0')
print('CI_ONLY_STAGED_TLS_TRUST_READY (unmodified signed APK; temporary emulator CA)')
