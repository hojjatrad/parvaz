"""Compiler dependency inventory bound to the exact output; not a call graph."""
import hashlib,json,subprocess

def objects(text):
 d=json.JSONDecoder();i=0
 while i<len(text):
  while i<len(text) and text[i].isspace():i+=1
  if i==len(text):break
  obj,i=d.raw_decode(text,i);yield obj

def package_names(text):
 records=list(objects(text))
 if not records or len(records)>20000:raise ValueError('Missing or oversized package inventory')
 names=[]
 for row in records:
  if not isinstance(row,dict) or row.get('Error') or row.get('DepsErrors') or row.get('Incomplete'):raise ValueError('Incomplete package inventory')
  name=row.get('ImportPath')
  if not isinstance(name,str) or not name or any(c.isspace() for c in name):raise ValueError('Invalid import path')
  names.append(name)
 if len(set(names))!=len(names):raise ValueError('Duplicate dependency records')
 return sorted(names)

def collect(source,env,core):
 cmd=['go','list','-deps','-json','-trimpath','-buildvcs=false']
 if env['GOOS']=='android':cmd+=['-buildmode=pie']
 if core['tags']:cmd+=['-tags',core['tags']]
 cmd.append(core['package'])
 return {'packages':package_names(subprocess.check_output(cmd,cwd=source,env=env,text=True)),'inventory_command':cmd,'goos':env['GOOS'],'goarch':env['GOARCH'],'cgo_enabled':env['CGO_ENABLED'],'core_spec_sha256':hashlib.sha256(json.dumps(core,sort_keys=True).encode()).hexdigest()}

def verify(proof,core,abi,binary_sha,go_version):
 expected_arch={'arm64-v8a':'arm64','armeabi-v7a':'arm'}
 p=proof.get('package_inventory',{})
 if abi not in expected_arch or proof.get('abi')!=abi or proof.get('sha256')!=binary_sha or proof.get('go_version')!='go'+go_version:raise ValueError('Package proof is not for this exact Android binary')
 if proof.get('base_commit')!=core['commit'] or p.get('goos')!='android' or p.get('goarch')!=expected_arch[abi] or p.get('cgo_enabled')!='1':raise ValueError('Package proof target mismatch')
 if p.get('core_spec_sha256')!=hashlib.sha256(json.dumps(core,sort_keys=True).encode()).hexdigest():raise ValueError('Package proof source/patch spec mismatch')
 packages=p.get('packages')
 if not isinstance(packages,list) or not packages or not all(isinstance(x,str) and x and not any(c.isspace() for c in x) for x in packages):raise ValueError('Missing package coverage')
 if len(set(packages))!=len(packages):raise ValueError('Duplicate package inventory')
 return packages
