"""Publish a strict allowlist of synthetic CI evidence. No APK, raw logs or key."""
import base64,json,os,pathlib,shutil,subprocess
root=pathlib.Path('.cache/public-review');repo=pathlib.Path('.cache/qa-publish').resolve();repo.mkdir(parents=True,exist_ok=True)
env=dict(os.environ);token=env.pop('GH_TOKEN');env.update(GIT_CONFIG_COUNT='1',GIT_CONFIG_KEY_0='http.https://github.com/.extraheader',GIT_CONFIG_VALUE_0='AUTHORIZATION: basic '+base64.b64encode(('x-access-token:'+token).encode()).decode())
def git(*args,check=True):return subprocess.run(['git',*args],cwd=repo,env=env,check=check,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
git('init');git('remote','add','origin','https://github.com/'+os.environ['GITHUB_REPOSITORY']+'.git')
if git('fetch','--depth=1','origin','qa/upgrade-evidence',check=False).returncode==0:git('checkout','-B','qa/upgrade-evidence','FETCH_HEAD')
else:git('checkout','--orphan','qa/upgrade-evidence')
run=os.environ['GITHUB_RUN_ID'];dest=repo/'runs'/run;dest.mkdir(parents=True,exist_ok=True)
allowed={'summary.json','sbom.cdx.json','vulnerability-review.json','binary-runtime.json'}
for p in root.rglob('*'):
 if not p.is_file() or p.is_symlink():continue
 if p.name not in allowed and not (p.suffix in ('.png','.xml') and p.stem in {'01-prior-data','02-upgraded-data','03-small-fa','04-tablet-fa','99-final-ui'}):continue
 if p.stat().st_size>12*1024*1024:raise SystemExit('Review evidence size bound')
 shutil.copyfile(p,dest/p.name)
receipt={'run_id':run,'source_commit':os.environ['GITHUB_SHA'],'scope':'Synthetic emulator evidence and public dependency inventory. No physical-device assertion. Presence is NOT a release approval.','files':sorted(p.name for p in dest.iterdir())}
(dest/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n');(repo/'latest.json').write_text(json.dumps(receipt,indent=2)+'\n')
git('config','user.name','Parvaz CI evidence');git('config','user.email','41898282+github-actions[bot]@users.noreply.github.com');git('add','runs/'+run,'latest.json');git('commit','-m','Review evidence for CI '+run);git('push','origin','HEAD:refs/heads/qa/upgrade-evidence');print('PUBLIC_SYNTHETIC_REVIEW_EVIDENCE runs/'+run)
