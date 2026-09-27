"""Review-only patched wrapper build. Does NOT replace the app's pinned AAR."""
import hashlib,io,json,os,pathlib,re,shutil,subprocess,zipfile
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
command=['gomobile','bind','-v','-work','-x','-androidapi','24','-target=android/arm,android/arm64','-trimpath','-ldflags=-w -buildid= -checklinkname=0','-o',str(output),'./']
# Retain symbol tables for direct binary inspection. No production replacement,
# TLS changes, signing operations, or execution of APK/native ARM bytes occurs.
built=subprocess.run(command,cwd=source,env=env,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
(OUT/'gomobile.log').write_text(built.stdout)
if built.returncode:
 print(built.stdout[-4000:]);raise SystemExit(built.returncode)
work_paths=re.findall(r'^WORK=(/[^\n]+)$',built.stdout,re.M)
if not work_paths:raise ValueError('Missing preserved compiler workspace')
work=pathlib.Path(work_paths[-1]).resolve()
if not re.fullmatch(r'gomobile-work-[0-9]+',work.name):raise ValueError('Unexpected compiler workspace')
# Compare the complete generated public Java interface with the immutable AAR.
api={};class_hashes={}
for label,aar in [('baseline',baseline),('rebuilt',output)]:
 with zipfile.ZipFile(aar) as z:jar=z.read('classes.jar')
 jarfile=OUT/(label+'-classes.jar');jarfile.write_bytes(jar)
 with zipfile.ZipFile(io.BytesIO(jar)) as j:
  classes=sorted(n[:-6].replace('/','.') for n in j.namelist() if n.endswith('.class'))
  class_hashes[label]={n:hashlib.sha256(j.read(n)).hexdigest() for n in j.namelist() if n.endswith('.class')}
 api[label]=subprocess.check_output(['javap','-classpath',str(jarfile),'-public','-s',*classes],text=True)
 (OUT/(label+'-public-api.txt')).write_text(api[label]);jarfile.unlink()
if api['baseline']!=api['rebuilt']:raise ValueError('Generated public Java API mismatch')
compiler_inventories={}
from package_evidence import closed_import_graph
ndk=pathlib.Path(env['ANDROID_NDK_HOME'])/'toolchains/llvm/prebuilt/linux-x86_64/bin'
gopath=subprocess.check_output(['go','env','GOPATH'],cwd=source,env=env,text=True).strip()
for abi,arch,cc in [('arm64-v8a','arm64','aarch64-linux-android24-clang'),('armeabi-v7a','arm','armv7a-linux-androideabi24-clang')]:
 generated=work/('src-android-'+arch)
 if not (generated/'go.mod').is_file():raise ValueError('Missing actual generated JNI module')
 targetenv=dict(env,GOOS='android',GOARCH=arch,GOARM='7',CGO_ENABLED='1',CC=str(ndk/cc),GOPATH=str(work)+os.pathsep+gopath,GOFLAGS='-mod=readonly')
 graph_command=['go','list','-deps','-json','-buildmode=c-shared','-trimpath','./gobind']
 graph=closed_import_graph(subprocess.check_output(graph_command,cwd=generated,env=targetenv,text=True))
 if graph['root']['import_path']!='gomobile.bind/gobind' or graph['root']['module_path']!='gomobile.bind':raise ValueError('Unexpected generated JNI main root')
 packages=graph['packages']
 if 'golang.org/x/mobile/bind/seq' not in packages or 'github.com/2dust/AndroidLibXrayLite' not in packages:raise ValueError('Incomplete JNI dependency roots')
 compiler_inventories[abi]={**graph,'inventory_command':graph_command,'cgo_enabled':'1','goos':'android','goarch':arch,'generated_go_mod_sha256':hashlib.file_digest((generated/'go.mod').open('rb'),'sha256').hexdigest(),'scope':'Complete Go import graph of actual preserved generated JNI module; not a call graph'}
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
  binaries.append({'compiler_inventory':compiler_inventories[abi],'extraction':extraction,'finding_groups':grouped,'abi':abi,'sha256':hashlib.sha256(data).hexdigest(),'extraction_exit':extract.returncode,'scanner_exit_NOT_clean_verdict':scan.returncode,'stderr':(extract.stderr+scan.stderr)[-2000:]})
  file.unlink()
from source_manifest import collect as source_tree_manifest,canonical
source_bytes=canonical(source_tree_manifest(source))
(OUT/'source-tree-manifest.json').write_bytes(source_bytes)
source_provenance={'source_lock':json.loads((ROOT/'tools/native/xray-source-lock.json').read_text()),'go_version':subprocess.check_output(['go','env','GOVERSION'],cwd=source,env=env,text=True).strip(),'ndk':'29.0.14206865','mobile':mobile,'source_tree_manifest_sha256':hashlib.sha256(source_bytes).hexdigest(),'source_tree_file_count':json.loads(source_bytes)['file_count'],'go_mod_sha256':hashlib.file_digest((source/'go.mod').open('rb'),'sha256').hexdigest(),'go_sum_sha256':hashlib.file_digest((source/'go.sum').open('rb'),'sha256').hexdigest(),'vendor_modules_sha256':hashlib.file_digest((source/'vendor/modules.txt').open('rb'),'sha256').hexdigest(),'scope':'Exact review-build source tree inventory, not production source-bundle adoption or approval'}
report={'source_provenance':source_provenance,'public_java_api_equal':True,'all_java_class_bytes_equal':class_hashes['baseline']==class_hashes['rebuilt'],'scope':'Review-only rebuilt AAR, NOT used by the app or approved for release','base_aar_sha256':lock['sha256'],'aar_sha256':hashlib.file_digest(output.open('rb'),'sha256').hexdigest(),'patch':patch,'gomobile_version':mobile,'command':command,'baseline_asset_hashes':asset_hashes,'binaries':binaries}
(OUT/'summary.json').write_text(json.dumps(report,indent=2)+'\n')
print('::notice title=XRAY_PUBLIC_JAVA_API::'+json.dumps({'public_api_equal':True,'class_bytes_equal':report['all_java_class_bytes_equal']}))
for b in binaries:
 print('::notice title=XRAY_JNI_COMPILER_COVERAGE::'+json.dumps({'abi':b['abi'],'sha256':b['sha256'],'package_count':len(b['compiler_inventory']['packages']),'openpgp_packages':[p for p in b['compiler_inventory']['packages'] if p.startswith('golang.org/x/crypto/openpgp')],'finding_groups':b['finding_groups'],'extraction':b['extraction']}))
