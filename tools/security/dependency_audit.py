#!/usr/bin/env python3
"""Resolved Java runtime + Go native module graphs, known-vulnerability review.
No private profiles, URLs, keys or user data are read/sent. OSV receives public
third-party package coordinates only. Inventory is conservative pre-R8/module
scope, NOT a claim every vulnerable function is reachable or every flaw known.
"""
import argparse,datetime,hashlib,json,os,pathlib,re,subprocess,time,urllib.request,urllib.parse
ROOT=pathlib.Path(__file__).resolve().parents[2]
OUT=ROOT/'.cache/supply-chain'

def sha(path):
 h=hashlib.sha256()
 with path.open('rb') as f:
  while b:=f.read(65536):h.update(b)
 return h.hexdigest()
def objects(text):
 decoder=json.JSONDecoder();i=0
 while i<len(text):
  while i<len(text) and text[i].isspace():i+=1
  if i==len(text):break
  value,end=decoder.raw_decode(text,i);yield value;i=end

def components(native):
 data=json.loads((OUT/'java-runtime.json').read_text());result={}
 def add(ecosystem,name,version,extra=None):
  if not version:raise ValueError('Missing dependency version: '+name)
  key=(ecosystem,name,version)
  value=result.setdefault(key,{'ecosystem':ecosystem,'name':name,'version':version,'sources':[]})
  if extra:value['sources'].append(extra)
 for c in data['components']:add('Maven',c['group']+':'+c['name'],c['version'],{'artifacts':c['artifacts']})
 if native:
  import importlib.util
  spec=importlib.util.spec_from_file_location('binary_inventory',pathlib.Path(__file__).with_name('binary_inventory.py'));binary_inventory=importlib.util.module_from_spec(spec);spec.loader.exec_module(binary_inventory)
  binaries=binary_inventory.scan(ROOT)
  (OUT/'binary-runtime.json').write_text(json.dumps(binaries,indent=2)+'\n')
  for binary in binaries:
   evidence={'binary':binary['binary'],'abi':binary['abi'],'sha256':binary['sha256']}
   add('Go','stdlib',binary['go_version'],evidence)
   for module in binary['modules']:
    add('Go',module['name'],module['version'],evidence)
    upstream=module.get('upstream_baseline')
    if upstream:
     add('Go',upstream['name'],upstream['version'],dict(evidence,fork_target=module['name']+'@'+module['version'],scope='conservative upstream baseline; fork patch applicability requires review'))
  sources=ROOT/'.cache/native';locks=json.loads((ROOT/'tools/native/engines-lock.json').read_text())
  locks.append(dict(json.loads((ROOT/'tools/native/xray-source-lock.json').read_text()),name='xray-wrapper'))
  for lock in locks:
   source=sources/lock['name']
   if not (source/'vendor/modules.txt').is_file():raise ValueError('Missing vendored graph '+lock['name'])
   env=dict(os.environ,GOTOOLCHAIN='auto',GOFLAGS='-mod=mod')
   raw=subprocess.check_output(['go','list','-m','-json','all'],cwd=source,env=env,text=True)
   (OUT/(lock['name']+'-modules.jsons')).write_text(raw)
   for module in objects(raw):
    if module.get('Main'):continue
    resolved=module.get('Replace',module)
    if not resolved.get('Version'):raise ValueError('Unversioned replacement requires review: '+module['Path'])
    add('Go',resolved['Path'],resolved['Version'],{'engine':lock['name'],'module_sum':resolved.get('Sum','')})
   version=subprocess.check_output(['go','env','GOVERSION'],cwd=source,env=env,text=True).strip().removeprefix('go')
   add('Go','stdlib',version,{'engine':lock['name'],'scope':'source toolchain; verify binary compiler separately'})
   add('Git',lock['repository'],lock['commit'],{'tag':lock['tag'],'source_archive_sha256':lock['sha256']})
 return list(result.values())

def request(path,payload=None):
 req=urllib.request.Request('https://api.osv.dev/v1/'+path,data=None if payload is None else json.dumps(payload).encode(),headers={'Content-Type':'application/json','User-Agent':'Parvaz-public-dependency-review'})
 for attempt in range(3):
  try:
   with urllib.request.urlopen(req,timeout=90) as response:
    raw=response.read(16*1024*1024+1)
    if len(raw)>16*1024*1024:raise ValueError('Vulnerability response limit')
    return json.loads(raw)
  except Exception:
   if attempt==2:raise
   time.sleep(2**attempt)

def purl(c):
 if c['ecosystem']=='Maven':
  group,name=c['name'].split(':',1);return 'pkg:maven/'+group+'/'+name+'@'+c['version']
 if c['ecosystem']=='Git':
  parsed=urllib.parse.urlsplit(c['name'])
  if parsed.hostname=='github.com':name=parsed.path.strip('/').removesuffix('.git')
  elif not parsed.scheme and re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+',c['name']):name=c['name']
  else:raise ValueError('Unsupported Git source PURL host')
  return 'pkg:github/'+name.lower()+'@'+c['version']
 return 'pkg:golang/'+c['name']+'@'+c['version']

def run(native):
 OUT.mkdir(parents=True,exist_ok=True);items=components(native);now=datetime.datetime.now(datetime.timezone.utc).isoformat()
 comp=[]
 for c in items:
  comp.append({'type':'library','name':c['name'],'version':c['version'],'purl':purl(c),'bom-ref':purl(c),'properties':[{'name':'parvaz:inventory-evidence','value':json.dumps(c['sources'],sort_keys=True)}]})
 local=ROOT/'app/libs/libv2ray.aar'
 if local.is_file():comp.append({'type':'library','name':'libv2ray.aar','version':json.loads((ROOT/'tools/release/core-lock.json').read_text())['tag'],'hashes':[{'alg':'SHA-256','content':sha(local)}]})
 bom={'bomFormat':'CycloneDX','specVersion':'1.5','version':1,'metadata':{'timestamp':now,'properties':[{'name':'parvaz:scope','value':'resolved release Java modules'+('; three native Go dependency graphs and toolchains' if native else '; JAVA ONLY, native scan not performed')},{'name':'parvaz:limitations','value':'Conservative pre-R8/module inventory. Android OS/NDK system libraries and unknown vulnerabilities are not covered; All six shipped ARM engine module tables and embedded Go compilers are checked. Source-only module matches remain listed with explicit non-shipped disposition; this is not function reachability or hardware evidence.'}]},'components':comp}
 (OUT/'sbom.cdx.json').write_text(json.dumps(bom,indent=2)+'\n')
 findings=[]
 for start in range(0,len(items),100):
  batch=items[start:start+100];queries=[{'commit':c['version']} if c['ecosystem']=='Git' else {'package':{'ecosystem':c['ecosystem'],'name':c['name']},'version':c['version']} for c in batch]
  answer=request('querybatch',{'queries':queries})
  if len(answer.get('results',[]))!=len(batch):raise ValueError('Incomplete vulnerability response')
  for c,r in zip(batch,answer['results']):
   if r.get('next_page_token'):raise ValueError('Unreviewed paginated vulnerability results')
   if r.get('vulns'):findings.append({'component':c,'vulnerabilities':r['vulns']})
 details={}
 for row in findings:
  for v in row['vulnerabilities']:
   if v['id'] not in details:details[v['id']]=request('vulns/'+urllib.parse.quote(v['id'],safe=''))
 actionable=[]
 for row in findings:
  active=any(not details[v['id']].get('withdrawn') for v in row['vulnerabilities'])
  c=row['component']
  not_built=native and c['ecosystem']=='Go' and not any('binary' in source for source in c['sources'])
  row['disposition']='WITHDRAWN' if not active else ('NOT_IN_ANY_SHIPPED_BINARY_MODULE_TABLE' if not_built else 'REVIEW_REQUIRED')
  if active and not not_built:actionable.append(row)
 report={'timestamp':now,'source_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),'native_graphs_included':native,'components_queried':len(items),'binary_runtime_verified':native,'status':'REVIEW_REQUIRED' if actionable else ('NO_APPLICABLE_MATCHES_IN_BUILT_RUNTIME' if findings else 'NO_KNOWN_MATCHES_IN_SCANNED_SCOPE'),'findings':findings,'advisories':details}
 (OUT/'vulnerability-review.json').write_text(json.dumps(report,indent=2)+'\n')
 print('::notice title=DEPENDENCY_REVIEW::'+report['status']+' components='+str(len(items))+' native_graphs='+str(native))
 for row in actionable:
  print('::error title=Dependency requires triage::'+row['component']['name']+' '+row['component']['version']+' '+','.join(v['id'] for v in row['vulnerabilities']))
 return 2 if actionable else 0
if __name__=='__main__':
 parser=argparse.ArgumentParser();parser.add_argument('--native',action='store_true');args=parser.parse_args()
 raise SystemExit(run(args.native))
