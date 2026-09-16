"""Actual Go build metadata from every shipped ARM ELF, not source go.mod guesses."""
import hashlib,json,pathlib,re,subprocess,tempfile,zipfile

def parse(text,local_root=None):
 lines=text.splitlines()
 if not lines:raise ValueError('Empty Go binary build metadata')
 match=re.search(r':\s+(go\d+\.\d+(?:\.\d+)?)(?:\s|$)',lines[0])
 if not match:raise ValueError('Unrecognized Go compiler metadata')
 modules=[];pending=None;main=None
 for line in lines[1:]:
  fields=line.strip().split()
  if not fields:continue
  if fields[0]=='dep':
   if len(fields)<3:raise ValueError('Incomplete binary dependency')
   pending={'name':fields[1],'version':fields[2]};modules.append(pending)
  elif fields[0]=='mod':
   if len(fields)<3:raise ValueError('Incomplete main-module metadata')
   main={'name':fields[1],'version':fields[2]};pending=None
  elif fields[0]=='=>':
   if pending is None or len(fields)<3:raise ValueError('Incomplete binary replacement')
   if fields[2]=='(devel)':
    if not local_root or pending['name']!=local_root['name'] or pending['version']!='v0.0.0-00010101000000-000000000000':raise ValueError('Unversioned binary dependency requires review')
    pending.update(version=local_root['version'],resolution='pinned official AAR root/source relationship; not reproducible-build proof',source_commit=local_root['commit'])
   else:
    pending['upstream_baseline']={'name':pending['name'],'version':pending['version']}
    pending.update(name=fields[1],version=fields[2])
  else:pending=None
 if not modules:raise ValueError('Missing binary dependency table; cannot infer non-applicability')
 if any(not r['version'].startswith('v') for r in modules):raise ValueError('Unversioned binary dependency requires review')
 result={'go_version':match[1].removeprefix('go'),'modules':modules}
 if main:result['main_module']=main
 return result

def scan(root):
 rows=[];locks=json.loads((root/'tools/native/engines-lock.json').read_text())
 def inspect(path,label,abi,local_root=None,engine=None):
  raw=subprocess.check_output(['go','version','-m',str(path)],text=True,stderr=subprocess.STDOUT)
  row=parse(raw,local_root);row.update(binary=label,abi=abi)
  if engine:
   main=row.get('main_module',{})
   if main.get('name','').lower()!='github.com/'+engine['repository'].lower():raise ValueError('Native root module does not match pinned source')
   row['modules'].append({'name':main['name'],'version':engine['tag'],'source_commit':engine['commit'],'resolution':'locally built pinned root plus documented app patches'})
  with path.open('rb') as stream:row['sha256']=hashlib.file_digest(stream,'sha256').hexdigest()
  rows.append(row)
 for abi in ['arm64-v8a','armeabi-v7a']:
  for name in ['libsingbox.so','libmihomo.so']:
   path=root/'app/libs/jni'/abi/name
   if not path.is_file():raise ValueError('Missing shipped native binary '+str(path))
   engine=next(e for e in locks if name=='lib'+e['name'].replace('-','')+'.so')
   inspect(path,'app/libs/jni/'+abi+'/'+name,abi,engine=engine)
 cache=root/'.cache';cache.mkdir(exist_ok=True)
 core=json.loads((root/'tools/release/core-lock.json').read_text());source=json.loads((root/'tools/native/xray-source-lock.json').read_text())
 with (root/'app/libs/libv2ray.aar').open('rb') as stream:aar_sha=hashlib.file_digest(stream,'sha256').hexdigest()
 if aar_sha!=core['sha256'] or core['repository']!=source['repository'] or core['tag']!=source['tag']:raise ValueError('Official AAR/source identity mismatch')
 local_root={'name':'github.com/'+source['repository'],'version':source['tag'],'commit':source['commit']}
 with tempfile.TemporaryDirectory(prefix='go-elf-review-',dir=cache) as temp,zipfile.ZipFile(root/'app/libs/libv2ray.aar') as aar:
  for abi in ['arm64-v8a','armeabi-v7a']:
   name='jni/'+abi+'/libgojni.so';info=aar.getinfo(name)
   if not 1024*1024<info.file_size<256*1024*1024:raise ValueError('Unexpected JNI binary size')
   output=pathlib.Path(temp)/(abi+'.so')
   with aar.open(name) as src,output.open('wb') as dst:
    import shutil;shutil.copyfileobj(src,dst,1024*1024)
   inspect(output,'libv2ray.aar!/'+name,abi,local_root=local_root)
 if len(rows)!=6:raise ValueError('All six shipped ARM engine binaries must be inventoried')
 return rows
