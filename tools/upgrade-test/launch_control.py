"""Launch-only control on a disposable API33 emulator. NEVER an upgrade verdict."""
import hashlib,json,os,pathlib,re,subprocess,time,urllib.request
from prior_release import PRIORS
ROOT=pathlib.Path(__file__).resolve().parents[2]
OUT=ROOT/'.cache/launch-control';OUT.mkdir(parents=True,exist_ok=True)

def run(*cmd,check=True,timeout=90):
 return subprocess.run(cmd,check=check,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=timeout)
serial=run('adb','get-serialno').stdout.strip()
if not re.fullmatch(r'emulator-\d+',serial):raise SystemExit('Only disposable emulator serials allowed')
def adb(*args,check=True):return run('adb','-s',serial,*args,check=check).stdout.strip()
if adb('shell','getprop','ro.kernel.qemu')!='1' or adb('shell','getprop','ro.build.version.sdk')!='33':raise SystemExit('Unreviewed emulator target')
if 'arm64-v8a' not in adb('shell','getprop','ro.product.cpu.abilist').split(','):raise SystemExit('No actual ARM64 APK support')
adb('root');adb('wait-for-device')
if adb('shell','getenforce')!='Enforcing':raise SystemExit('SELinux must remain enforcing')
(OUT/'environment.txt').write_text(adb('shell','getprop'))
buildtools=pathlib.Path(os.environ['ANDROID_HOME'])/'build-tools/34.0.0'
items=[(name,p['version_code'],p) for name,p in PRIORS.items()]+[('candidate00b7046',42,None)]
results=[]
for name,code,prior in items:
 folder=OUT/name;folder.mkdir(exist_ok=True)
 if prior:
  apk=ROOT/'.cache'/('launch-'+name+'.apk')
  with urllib.request.urlopen(prior['url'],timeout=90) as r,apk.open('wb') as w:
   total=0
   while data:=r.read(1024*1024):
    total+=len(data)
    if total>60*1024*1024:raise ValueError('Prior download exceeds size bound')
    w.write(data)
 else:apk=ROOT/'release-artifacts/Parvaz-1.28.8.apk'
 digest=hashlib.file_digest(apk.open('rb'),'sha256').hexdigest()
 if prior and digest!=prior['sha256']:raise ValueError('Immutable prior hash mismatch')
 sig=run(str(buildtools/'apksigner'),'verify','--print-certs',str(apk)).stdout
 if 'd1383b8f34da5d3299b13634de421487289d82c1bb47ec3bc7f20ae8d02fe500' not in sig:raise ValueError('Wrong signer')
 badging=run(str(buildtools/'aapt'),'dump','badging',str(apk)).stdout
 if not re.search("package: name='com.parvaz.tunnel' versionCode='"+str(code)+"'",badging):raise ValueError('Unexpected APK identity')
 # Clean launch control only. This intentionally does NOT exercise migration.
 adb('uninstall','com.parvaz.tunnel',check=False)
 installed=adb('install','--no-streaming',str(apk));(folder/'install.txt').write_text(installed)
 adb('logcat','-c')
 launch=adb('shell','am','start','-W','-n','com.parvaz.tunnel/.MainActivity',check=False);(folder/'launch.txt').write_text(launch)
 time.sleep(15) # bounded observation in fixture, never a product startup delay
 pid=adb('shell','pidof','com.parvaz.tunnel',check=False)
 logs=adb('logcat','-d');(folder/'device.log').write_text(logs)
 adb('pull','/data/tombstones',str(folder/'tombstones'),check=False)
 adb('shell','uiautomator','dump','/sdcard/launch-control.xml',check=False)
 adb('pull','/sdcard/launch-control.xml',str(folder/'ui.xml'),check=False)
 result={'name':name,'code':code,'apk_sha256':digest,'pid_after_15s':pid,'status':'PROCESS_SURVIVED_LAUNCH' if pid and 'Status: ok' in launch else 'LAUNCH_FAILED','scope':'Launch control only. No updater, migration or candidate-repair approval.'}
 results.append(result)
 print('::notice title=ARM_LAUNCH_CONTROL::'+json.dumps(result))
 crash=[i for i,line in enumerate(logs.splitlines()) if 'Fatal signal' in line or ('backtrace:' in line)]
 for i in crash[:3]:
  text='\n'.join(logs.splitlines()[max(0,i-3):i+22])[:3000]
  print('::notice title=ARM_NATIVE_CRASH::'+text.replace('%','%25').replace('\n','%0A'))
(OUT/'summary.json').write_text(json.dumps(results,indent=2)+'\n')
if any(r['status']!='PROCESS_SURVIVED_LAUNCH' for r in results):raise SystemExit(2)
