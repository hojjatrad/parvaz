"""Evidence collection only: never treats a stripped/partial scan as release approval."""
import hashlib,json,pathlib,subprocess,zipfile,sys
ROOT=pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/security'))
from dependency_audit import objects
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
   extracted=subprocess.run(['govulncheck','-mode=extract',str(binary)],text=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
   (out/(binary.name+'.extract.jsons')).write_text(extracted.stdout)
   try:
    extracted_records=list(objects(extracted.stdout))
    assert extracted.returncode==0 and len(extracted_records)==2 and extracted_records[0]=={'name':'govulncheck-extract','version':'0.1.0'}
    body=extracted_records[1]
    extraction={'status':'VERIFIED_TOOL_OUTPUT','goos':body.get('goos'),'goarch':body.get('goarch'),'go_version':body.get('goVersion'),'recovered_symbol_count':len(body.get('pkgSymbols',[]))}
    extraction['precision']='MODULE_LEVEL_CONSERVATIVE_FALLBACK' if extraction['recovered_symbol_count']==0 else 'RECOVERED_SYMBOLS_NOT_CALL_GRAPH'
   except Exception:
    extraction={'status':'UNKNOWN','precision':'UNKNOWN','stderr':extracted.stderr[-1000:]}
   proc=subprocess.run(['govulncheck','-json','-mode=binary',str(binary)],text=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
   (out/(binary.name+'.govuln.jsons')).write_text(proc.stdout)
   (out/(binary.name+'.stderr.txt')).write_text(proc.stderr)
   try:records=list(objects(proc.stdout))
   except Exception:records=[]
   summary={'extraction':extraction,'binary':label,'abi':abi,'sha256':digest,'scanner_exit':proc.returncode,'config':[r['config'] for r in records if 'config' in r],'progress':[r['progress'] for r in records if 'progress' in r],'findings':[r['finding'] for r in records if 'finding' in r],'stderr':proc.stderr[-2000:]}
   groups={}
   for finding in summary['findings']:
    entry=groups.setdefault(finding.get('osv','?'),{'module_records':0,'package_records':0,'function_named_records_NOT_presence_proof':0,'reported_function_names_NOT_presence_proof':[]})
    frames=finding.get('trace',[])
    symbols=[f.get('package','')+'.'+f.get('function','') for f in frames if f.get('function')]
    key='function_named_records_NOT_presence_proof' if symbols else ('package_records' if any(f.get('package') for f in frames) else 'module_records')
    entry[key]+=1
    for symbol in symbols:
     if symbol not in entry['reported_function_names_NOT_presence_proof']:entry['reported_function_names_NOT_presence_proof'].append(symbol)
   summary['finding_groups']=groups
   if abi=='arm64-v8a':
    for advisory,group in groups.items():
     info=json.dumps({'binary':label,'advisory':advisory,**group})[:2800]
     print('::notice title=NATIVE_FINDING_SCOPE::'+info.replace('%','%25').replace('\n','%0A'))
   summaries.append(summary);binary.unlink() # only this generated, hash-verified CI extraction
   text=json.dumps({'binary':label,'exit':proc.returncode,'config':summary['config'],'extraction':extraction,'finding_count':len(summary['findings']),'stderr':proc.stderr[-1000:]})
   print('::notice title=NATIVE_SYMBOL_SCAN::'+text.replace('%','%25').replace('\n','%0A'))
(out/'summary.json').write_text(json.dumps({'limitations':'govulncheck v1.8.0 emits every known vulnerable function for stripped binaries with empty pkgSymbols. Function-bearing JSON findings and config.scan_level=symbol do NOT prove recovery or execution. JSON exit zero does NOT mean clean. See pinned internal/vulncheck/binary.go allKnownVulnerableSymbols.', 'scope':'Actual unchanged six binaries from signed source 00b7046; static binary inspection, NOT installation, execution, reachability proof or stable approval.','tool':'golang.org/x/vuln/cmd/govulncheck@v1.8.0','reports':summaries},indent=2)+'\n')
