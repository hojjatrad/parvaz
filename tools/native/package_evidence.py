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

def inventory_command(core,goos):
 cmd=['go','list','-deps','-json','-trimpath','-buildvcs=false']
 if goos=='android':cmd+=['-buildmode=pie']
 if core['tags']:cmd+=['-tags',core['tags']]
 return cmd+[core['package']]

def validate_closure(packages,root):
 if not isinstance(root,dict) or root.get('name')!='main' or root.get('module_main') is not True:raise ValueError('Expected an actual main build root')
 if not isinstance(root.get('import_path'),str) or not root['import_path'] or not isinstance(root.get('module_path'),str) or not root['module_path']:raise ValueError('Missing root/module identity')
 deps=root.get('dependencies')
 if not isinstance(deps,list) or not all(isinstance(d,str) and d and not any(c.isspace() for c in d) for d in deps) or len(set(deps))!=len(deps) or 'runtime' not in deps:raise ValueError('Missing full root dependency closure')
 if root['import_path'] in deps or set(packages)!=set(deps+[root['import_path']]):raise ValueError('Package records do not equal the main root transitive dependency closure')

def validate_root(packages,root,core):
 validate_closure(packages,root)
 module='github.com/'+core['repository']
 suffix=core['package'].removeprefix('./') if core['package']!='.' else ''
 expected=module+('/'+suffix if suffix else '')
 if root['import_path'].lower()!=expected.lower() or root['module_path'].lower()!=module.lower():raise ValueError('Compiler root is not the pinned target')

def closed_import_graph(text):
 packages=package_names(text);records=list(objects(text))
 roots=[r for r in records if not r.get('DepOnly',False)]
 if len(roots)!=1:raise ValueError('Exactly one compiler root is required')
 r=roots[0];module=r.get('Module',{})
 if not isinstance(module,dict):raise ValueError('Missing main module metadata')
 root={'import_path':r['ImportPath'],'name':r.get('Name'),'module_path':module.get('Path'),'module_main':module.get('Main'),'dependencies':r.get('Deps')}
 validate_closure(packages,root)
 return {'coverage_schema':2,'packages':packages,'root':root}

def closed_graph(text,core):
 result=closed_import_graph(text)
 validate_root(result['packages'],result['root'],core)
 return result

def collect(source,env,core):
 cmd=inventory_command(core,env['GOOS'])
 result=closed_graph(subprocess.check_output(cmd,cwd=source,env=env,text=True),core)
 result.update(inventory_command=cmd,goos=env['GOOS'],goarch=env['GOARCH'],cgo_enabled=env['CGO_ENABLED'],core_spec_sha256=hashlib.sha256(json.dumps(core,sort_keys=True).encode()).hexdigest())
 return result

def verify(proof,core,abi,binary_sha,go_version):
 expected_arch={'arm64-v8a':'arm64','armeabi-v7a':'arm'}
 p=proof.get('package_inventory',{})
 if abi not in expected_arch or proof.get('abi')!=abi or proof.get('sha256')!=binary_sha or proof.get('go_version')!='go'+go_version:raise ValueError('Package proof is not for this exact Android binary')
 if proof.get('base_commit')!=core['commit'] or p.get('goos')!='android' or p.get('goarch')!=expected_arch[abi] or p.get('cgo_enabled')!='1':raise ValueError('Package proof target mismatch')
 if p.get('core_spec_sha256')!=hashlib.sha256(json.dumps(core,sort_keys=True).encode()).hexdigest():raise ValueError('Package proof source/patch spec mismatch')
 packages=p.get('packages')
 if not isinstance(packages,list) or not packages or not all(isinstance(x,str) and x and not any(c.isspace() for c in x) for x in packages):raise ValueError('Missing package coverage')
 if len(set(packages))!=len(packages):raise ValueError('Duplicate package inventory')
 if p.get('coverage_schema')!=2 or p.get('inventory_command')!=inventory_command(core,'android'):raise ValueError('Unknown or incomplete compiler coverage contract')
 validate_root(packages,p.get('root'),core)
 return packages
