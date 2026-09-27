"""Targeted security source patches. Preserve higher versions; reject unknown forks."""
import json,re,subprocess

def version_key(value):
 m=re.fullmatch(r'v(\d+)\.(\d+)\.(\d+)(?:-(\d{14})-([0-9a-f]{12}))?',value)
 if not m:raise ValueError('Unreviewed version syntax: '+str(value))
 return tuple(map(int,m.group(1,2,3)))+(1 if m[4] is None else 0,m[4] or '',m[5] or '')

def upgrades(modules,floors):
 selected=[]
 for path,floor in sorted(floors.items()):
  version_key(floor)
  if path not in modules:continue # never add an unrelated dependency
  module=modules[path]
  if module.get('Replace'):raise ValueError('Targeted dependency is replaced; explicit fork review required: '+path)
  if version_key(module.get('Version',''))<version_key(floor):selected.append(path+'@'+floor)
 return selected

def read_modules(source,env):
 raw=subprocess.check_output(['go','list','-m','-json','all'],cwd=source,env=dict(env,GOFLAGS='-mod=mod'),text=True)
 decoder=json.JSONDecoder();i=0;result={}
 while i<len(raw):
  while i<len(raw) and raw[i].isspace():i+=1
  if i==len(raw):break
  module,i=decoder.raw_decode(raw,i);result[module['Path']]=module
 return result

def apply(source,env,floors):
 before=read_modules(source,env);after=before;changes=[]
 # A floor is a minimum, not an exact simultaneous constraint. Let Go's MVS
 # raise dependent modules, then recalculate before requesting another upgrade.
 for _ in range(len(floors)+1):
  pending=upgrades(after,floors)
  if not pending:break
  requested=pending[0]
  subprocess.run(['go','get',requested],cwd=source,env=env,check=True)
  changes.append(requested);after=read_modules(source,env)
 else:raise ValueError('Security floor resolution failed to converge')
 if upgrades(after,floors):raise ValueError('Security floors were not satisfied')
 for path,old in before.items():
  if path in after and old.get('Version') and after[path].get('Version') and not old.get('Replace'):
   try:lower=version_key(after[path]['Version'])<version_key(old['Version'])
   except ValueError:continue # uncommon unrelated versions are not used to waive security floors
   if lower:raise ValueError('Unintended dependency downgrade: '+path)
 return {'requested_changes':changes,'resolved_floors':{p:after[p]['Version'] for p in floors if p in after}}
