"""Allowlisted synthetic Robolectric renders; not installed APK/device evidence."""
import base64,hashlib,json,os,pathlib,shutil,struct,subprocess
if os.environ.get('GITHUB_REF')!='refs/heads/improve/full-review' or os.environ.get('GITHUB_EVENT_NAME')!='push':raise SystemExit('Only the approved improvement branch may publish synthetic renders')
source=pathlib.Path('.cache/ui-review');expected={f'{page}-fa-320-large-{mode}.png' for page in ['home','server'] for mode in ['light','dark']}
files={p.name:p for p in source.rglob('*.png')}
if set(files)!=expected:raise SystemExit('Exact synthetic fixture allowlist required')
record={'source_commit':os.environ['GITHUB_SHA'],'run_id':os.environ['GITHUB_RUN_ID'],'scope':'SYNTHETIC_ROBOLECTRIC_RENDER_ONLY; static home and bound server row, not installed APK, physical device, TalkBack or full application navigation proof','files':{}}
for name,p in files.items():
 if p.is_symlink() or p.stat().st_size>2*1024*1024:raise SystemExit('Unsafe render size/type')
 data=p.read_bytes()
 if data[:8]!=b'\x89PNG\r\n\x1a\n':raise SystemExit('Expected PNG')
 width,height=struct.unpack('>II',data[16:24])
 if width!=320 and width!=292:raise SystemExit('Unexpected synthetic viewport')
 if not 1<=height<=3000:raise SystemExit('Unexpected fixture height')
 record['files'][name]={'sha256':hashlib.sha256(data).hexdigest(),'width':width,'height':height}
repo=pathlib.Path('.cache/ui-review-publish').resolve();repo.mkdir(parents=True,exist_ok=True)
env=dict(os.environ);token=env.pop('GH_TOKEN');env.update(GIT_CONFIG_COUNT='1',GIT_CONFIG_KEY_0='http.https://github.com/.extraheader',GIT_CONFIG_VALUE_0='AUTHORIZATION: basic '+base64.b64encode(('x-access-token:'+token).encode()).decode())
def git(*args,check=True):return subprocess.run(['git',*args],cwd=repo,env=env,check=check,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
git('init');git('remote','add','origin','https://github.com/'+os.environ['GITHUB_REPOSITORY']+'.git')
if git('fetch','--depth=1','origin','qa/ui-review',check=False).returncode==0:git('checkout','-B','qa/ui-review','FETCH_HEAD')
else:git('checkout','--orphan','qa/ui-review')
for name,p in files.items():shutil.copyfile(p,repo/name)
(repo/'latest.json').write_text(json.dumps(record,indent=2)+'\n')
(repo/'README.md').write_text('# Synthetic layout review\n\nRobolectric rendering of real resources on SDK34, Persian, 320dp, large font. Not a physical-device screenshot or final installation proof. Source and hashes: latest.json.\n\n'+''.join('![Synthetic '+n+']('+n+')\n\n' for n in sorted(files)))
git('config','user.name','Parvaz synthetic UI review');git('config','user.email','41898282+github-actions[bot]@users.noreply.github.com');git('add',*sorted(expected),'latest.json','README.md');git('commit','-m','Synthetic UI review for '+record['source_commit']);git('push','origin','HEAD:refs/heads/qa/ui-review')
print('SYNTHETIC_UI_REVIEW_PUBLISHED; not device installation evidence')
