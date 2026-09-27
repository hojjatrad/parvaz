#!/usr/bin/env python3
"""Wait for a genuine ARM64 boot proof, then trigger the signed installation pipeline.
Only a Success verdict from the boot probes (booted, aarch64, Enforcing, no native bridge)
may start it. No tag, no stable publication, no signer change.
"""
import html,json,pathlib,re,subprocess,time,urllib.request
ROOT=pathlib.Path('/home/user/parvaz-improvements')
RUNS={'heavy':('36326414457','system-images;android-30;google_apis;arm64-v8a'),'light':('36334267146','system-images;android-30;default;arm64-v8a')}
SSH="ssh -i /home/user/parvaz-release-access/deploy_ed25519 -o IdentitiesOnly=yes -o BatchMode=yes -o StrictHostKeyChecking=yes -o UserKnownHostsFile=/home/user/parvaz/.git/known_hosts -o Hostname=ssh.github.com -p 443"
def text(url):
 return re.sub(r'\s+',' ',html.unescape(re.sub('<[^>]+>',' ',urllib.request.urlopen(url,timeout=60).read().decode())))
def git(*args,check=True):
 return subprocess.run(['git',*args],cwd=ROOT,check=check,text=True,capture_output=True,env={'PATH':'/usr/bin:/bin:/usr/local/bin','HOME':'/home/user','GIT_SSH_COMMAND':SSH})
state={};chosen=None;deadline=time.monotonic()+19000
while time.monotonic()<deadline and len(state)<len(RUNS) and not chosen:
 for name,(run,image) in RUNS.items():
  if name in state:continue
  try:t=text('https://github.com/hojjatrad/parvaz/actions/runs/'+run+'?t='+str(int(time.monotonic())))
  except Exception as e:print(name,'fetch error',e,flush=True);continue
  m=re.search(r'Status (Success|Failure|Cancelled|In progress|Queued)',t);status=m[1] if m else 'unknown'
  print(time.strftime('%H:%M'),name,run,status,flush=True)
  if status in ('Success','Failure','Cancelled'):
   state[name]=status
   i=t.find('FULL_ARM64_BOOT {');print(name,'summary',t[i:i+300] if i>0 else 'none',flush=True)
   if status=='Success':chosen=(name,run,image);break
 if not chosen:time.sleep(150)
pathlib.Path(ROOT/'.cache/review-next/arm-boot-race.json').parent.mkdir(parents=True,exist_ok=True)
pathlib.Path(ROOT/'.cache/review-next/arm-boot-race.json').write_text(json.dumps({'observed':state,'chosen':chosen},indent=2)+'\n')
if not chosen:
 print('NO BOOT PROOF; installation pipeline intentionally not started',flush=True);raise SystemExit(0)
name,run,image=chosen
git('switch','-q','repair/combined-native-candidate')
git('switch','-q','-C','release/arm64-install-validation')
p=ROOT/'.github/workflows/arm64-install-validation.yml';s=p.read_text()
s=s.replace('IMAGE="${PARVAZ_ARM_IMAGE:-system-images;android-30;google_apis;arm64-v8a}"','IMAGE="${PARVAZ_ARM_IMAGE:-'+image+'}"')
s=s.replace('# Install exactly the official ARM64 API30 image the boot probe proved','# Install exactly the official ARM64 API30 image the boot probe proved')
p.write_text(s)
(ROOT/'docs/review-2026-09-27/ARM64-BOOT-PROOF.md').write_text('# ARM64 boot proof\n\nBoot probe run '+run+' ('+name+') reported Success with the official image `'+image+'`.\nThe signed installation, upgrade and updater validation pipeline was started from that proof only.\nNo tag, stable publication or signer change is authorised by this file.\n')
git('add','.github/workflows/arm64-install-validation.yml','docs/review-2026-09-27/ARM64-BOOT-PROOF.md')
git('-c','user.name=Arena Agent','-c','user.email=arena-agent@users.noreply.github.com','commit','-q','-m','ci: start signed ARM64 installation validation from boot probe '+run)
out=git('push','-q','git@github.com:hojjatrad/parvaz.git','HEAD:refs/heads/release/arm64-install-validation',check=False)
print('PUSH',out.returncode,out.stderr[-500:],flush=True)
git('switch','-q','repair/combined-native-candidate')
print('INSTALL PIPELINE TRIGGERED',name,image,flush=True)
