"""Review-only patched wrapper build. Does NOT replace the app's pinned AAR."""
import hashlib,json,os,pathlib,shutil,subprocess,zipfile
from vendor_xray import vendor
from dependency_floors import apply
ROOT=pathlib.Path(__file__).resolve().parents[2]
OUT=ROOT/'.cache/xray-security-review';OUT.mkdir(parents=True,exist_ok=True)
source=vendor()
env=dict(os.environ,GOTOOLCHAIN='auto',GOMAXPROCS='2',GOWORK='off',GOFLAGS='-mod=mod',ANDROID_NDK_HOME=os.environ['ANDROID_HOME']+'/ndk/29.0.14206865')
patch=apply(source,env,{'github.com/klauspost/compress':'v1.18.7'})
# Use the tool version recorded by the reviewed source, never @latest.
mobile='v0.0.0-20260908204917-8b95e45f8d3e'
subprocess.run(['go','get','-tool','golang.org/x/mobile/cmd/gobind@'+mobile],cwd=source,env=env,check=True)
for tool in ['gomobile','gobind']:
 subprocess.run(['go','install','golang.org/x/mobile/cmd/'+tool+'@'+mobile],cwd=source,env=env,check=True)
# The baseline AAR is verified by fetch_core before this script. Reuse only its
# data assets, not arbitrary current downloads from an unpinned update service.
baseline=ROOT/'app/libs/libv2ray.aar'
lock=json.loads((ROOT/'tools/release/core-lock.json').read_text())
if hashlib.file_digest(baseline.open('rb'),'sha256').hexdigest()!=lock['sha256']:raise ValueError('Baseline AAR identity mismatch')
assets=source/'assets';assets.mkdir(exist_ok=True)
asset_hashes={}
with zipfile.ZipFile(baseline) as z:
 for name in z.namelist():
  if not name.startswith('assets/') or name.endswith('/'):continue
  path=pathlib.PurePosixPath(name)
  if '..' in path.parts or z.getinfo(name).file_size>64*1024*1024:raise ValueError('Invalid baseline asset')
  data=z.read(name);output=source/path;output.parent.mkdir(parents=True,exist_ok=True);output.write_bytes(data);asset_hashes[name]=hashlib.sha256(data).hexdigest()
# vendor() fetched the baseline tree. Re-vendor after the targeted patch.
subprocess.run(['go','mod','vendor'],cwd=source,env=env,check=True)
subprocess.run(['gomobile','init'],cwd=source,env=env,check=True)
output=OUT/'libv2ray-review.aar'
command=['gomobile','bind','-v','-androidapi','24','-target=android/arm,android/arm64','-trimpath','-ldflags=-w -buildid= -checklinkname=0','-o',str(output),'./']
# Retain symbol tables for direct binary inspection. No production replacement,
# TLS changes, signing operations, or execution of APK/native ARM bytes occurs.
subprocess.run(command,cwd=source,env=env,check=True)
for name in ['go.mod','go.sum']:shutil.copyfile(source/name,OUT/name)
subprocess.run(['go','install','golang.org/x/vuln/cmd/govulncheck@v1.8.0'],cwd=source,env=env,check=True)
binaries=[]
with zipfile.ZipFile(output) as z:
 for abi in ['arm64-v8a','armeabi-v7a']:
  name='jni/'+abi+'/libgojni.so';data=z.read(name);file=OUT/(abi+'.so');file.write_bytes(data)
  extract=subprocess.run(['govulncheck','-mode=extract',str(file)],capture_output=True,text=True,timeout=180)
  (OUT/(abi+'-extract.jsons')).write_text(extract.stdout)
  scan=subprocess.run(['govulncheck','-json','-mode=binary',str(file)],capture_output=True,text=True,timeout=180)
  (OUT/(abi+'-govuln.jsons')).write_text(scan.stdout)
  from package_evidence import objects
  try:
   records=list(objects(extract.stdout));body=records[1]
   extraction={'goos':body.get('goos'),'goarch':body.get('goarch'),'symbol_count':len(body.get('pkgSymbols',[]))} if extract.returncode==0 and records[0]=={'name':'govulncheck-extract','version':'0.1.0'} else {'status':'UNKNOWN'}
  except Exception:extraction={'status':'UNKNOWN'}
  findings=[x['finding'] for x in objects(scan.stdout) if 'finding' in x]
  grouped={}
  for finding in findings:
   counts=grouped.setdefault(finding['osv'],{'module':0,'package':0,'function_named_NOT_execution':0})
   frames=finding.get('trace',[]);level='function_named_NOT_execution' if any(f.get('function') for f in frames) else ('package' if any(f.get('package') for f in frames) else 'module');counts[level]+=1
  binaries.append({'extraction':extraction,'finding_groups':grouped,'abi':abi,'sha256':hashlib.sha256(data).hexdigest(),'extraction_exit':extract.returncode,'scanner_exit_NOT_clean_verdict':scan.returncode,'stderr':(extract.stderr+scan.stderr)[-2000:]})
  file.unlink()
report={'scope':'Review-only rebuilt AAR, NOT used by the app or approved for release','base_aar_sha256':lock['sha256'],'aar_sha256':hashlib.file_digest(output.open('rb'),'sha256').hexdigest(),'patch':patch,'gomobile_version':mobile,'command':command,'baseline_asset_hashes':asset_hashes,'binaries':binaries}
(OUT/'summary.json').write_text(json.dumps(report,indent=2)+'\n')
print('::notice title=XRAY_REBUILD_PROBE::'+json.dumps(report)[:3000])
