#!/usr/bin/env python3
"""Read-only budget check by default. Explicit cleanup covers ONLY known audit outputs."""
import argparse,datetime,fcntl,json,os,pathlib,shutil,subprocess
MIB=1024*1024

def allocated_bytes(root):
 def unreadable(error):raise error
 total=0;seen=set()
 for parent,dirs,files in os.walk(root,followlinks=False,onerror=unreadable):
  for p in [pathlib.Path(parent),*(pathlib.Path(parent)/n for n in files)]:
   info=p.lstat();key=(info.st_dev,info.st_ino)
   if key not in seen:total+=info.st_blocks*512;seen.add(key)
 return total

def budget(root,limit_mib=120,min_free_mib=256):
 used=allocated_bytes(root);free=shutil.disk_usage(root).free
 return {'used_bytes':used,'free_bytes':free,'limit_mib':limit_mib,'status':'OK' if used<=limit_mib*MIB and free>=min_free_mib*MIB else 'STOP_HEAVY_WORK'}

def clean_audit_cache(repo):
 repo=repo.resolve();cache=repo/'.cache';audit=cache/'audit'
 # Never follow a symlink into user data, other worktrees or key directories.
 if cache.is_symlink() or audit.is_symlink():raise ValueError('Symlink cache rejected')
 if not audit.exists():return {'removed_files':0,'removed_bytes':0}
 top=subprocess.check_output(['git','-C',str(repo),'rev-parse','--show-toplevel'],text=True).strip()
 if pathlib.Path(top).resolve()!=repo:raise ValueError('Expected exact repository root')
 tracked=subprocess.check_output(['git','-C',str(repo),'ls-files','-z','--','.cache/audit','.cache/audit.lock'])
 if tracked:raise ValueError('Tracked files must never be cleaned')
 lockpath=cache/'audit.lock'
 if lockpath.is_symlink():raise ValueError('Symlink lock rejected')
 with lockpath.open('a') as lock:
  try:fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
  except BlockingIOError:raise ValueError('Audit is running; cleanup refused') from None
  files=[]
  for p in audit.rglob('*'):
   if p.is_symlink():raise ValueError('Symlink in audit outputs rejected')
   if p.is_dir():continue
   rel=p.relative_to(audit)
   known=rel.as_posix() in {'json.jar','snakeyaml.jar','localhost.p12'} or (len(rel.parts)>1 and rel.parts[0]=='classes' and p.suffix=='.class')
   if not p.is_file() or not known:raise ValueError('Unknown file in audit cache; nothing deleted')
   files.append(p)
  result={'removed_files':len(files),'removed_bytes':sum(p.stat().st_size for p in files)}
  shutil.rmtree(audit)
  return result

def main():
 parser=argparse.ArgumentParser();parser.add_argument('--workspace',type=pathlib.Path,default=pathlib.Path('/home/user'));parser.add_argument('--repo',type=pathlib.Path,default=pathlib.Path(__file__).resolve().parents[2]);parser.add_argument('--clean-audit-cache',action='store_true');args=parser.parse_args()
 if args.clean_audit_cache:
  result=clean_audit_cache(args.repo)
  # Permanent, content-free cleanup record; never delete this as a cache/log.
  record=dict(result,at=datetime.datetime.now(datetime.timezone.utc).isoformat(),scope='known JVM audit outputs only')
  log=args.repo/'docs/workspace-cleanup.jsonl';log.parent.mkdir(parents=True,exist_ok=True)
  with log.open('a') as out:out.write(json.dumps(record)+'\n')
  print(json.dumps(record))
 state=budget(args.workspace);print(json.dumps(state));return 0 if state['status']=='OK' else 2
if __name__=='__main__':raise SystemExit(main())
