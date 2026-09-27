"""Evidence collection only: never treats a stripped/partial scan as release approval."""
import hashlib,json,pathlib,subprocess,zipfile,sys
ROOT=pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/security'))
from dependency_audit import objects
from binary_inventory import parse
out=ROOT/'.cache/native-symbol-evidence';out.mkdir(parents=True,exist_ok=True)
boundary=json.loads((ROOT/'tools/preview/accepted-test-boundary.json').read_text())['binary_sha256']
apk=ROOT/'release-artifacts/Parvaz-1.28.8.apk';summaries=[]
with zipfile.ZipFile(apk) as z:
 for abi in ['arm64-v8a','armeabi-v7a']:
  for lib in ['libgojni.so','libsingbox.so','libmihomo.so']:
   name='lib/'+abi+'/'+lib;label=('libv2ray.aar!/jni/'+abi+'/'+lib) if lib=='libgojni.so' else ('app/libs/jni/'+abi+'/'+lib)
   data=z.read(name);digest=hashlib.sha256(data).hexdigest();assert digest==boundary[label+'|'+abi], 'Native identity mismatch'
   binary=out/(abi+'-'+lib);binary.write_bytes(data)
   metadata=subprocess.check_output(['go','version','-m',str(binary)],text=True)
   (out/(binary.name+'.buildinfo.txt')).write_text(metadata)
   proc=subprocess.run(['govulncheck','-json','-mode=binary',str(binary)],text=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
   (out/(binary.name+'.govuln.jsons')).write_text(proc.stdout)
   (out/(binary.name+'.stderr.txt')).write_text(proc.stderr)
   try:records=list(objects(proc.stdout))
   except Exception:records=[]
   summary={'binary':label,'abi':abi,'sha256':digest,'scanner_exit':proc.returncode,'config':[r['config'] for r in records if 'config' in r],'progress':[r['progress'] for r in records if 'progress' in r],'findings':[r['finding'] for r in records if 'finding' in r],'stderr':proc.stderr[-2000:]}
   summaries.append(summary);binary.unlink() # only this generated, hash-verified CI extraction
   text=json.dumps({'binary':label,'exit':proc.returncode,'config':summary['config'],'finding_count':len(summary['findings']),'stderr':proc.stderr[-1000:]})
   print('::notice title=NATIVE_SYMBOL_SCAN::'+text.replace('%','%25').replace('\n','%0A'))
(out/'summary.json').write_text(json.dumps({'scope':'Actual unchanged six binaries from signed source 00b7046; static binary inspection, NOT installation, execution, reachability proof or stable approval.','tool':'golang.org/x/vuln/cmd/govulncheck@v1.8.0','reports':summaries},indent=2)+'\n')
