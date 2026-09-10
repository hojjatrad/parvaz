#!/usr/bin/env python3
"""Prepare a candidate only. CI must pass all gates before committing/publishing it."""
import json,os,re,subprocess,urllib.request,urllib.error,hashlib
from pathlib import Path
from fetch_core import ROOT,validate,fetch
BASE='https://api.github.com'
REPO=os.environ.get('GITHUB_REPOSITORY','hojjatrad/parvaz')

def api(path):
    req=urllib.request.Request(BASE+path,headers={'Authorization':'Bearer '+os.environ['GH_TOKEN'],'Accept':'application/vnd.github+json','User-Agent':'Parvaz-core-maintenance'})
    with urllib.request.urlopen(req,timeout=30) as r:return json.load(r)
def version_tuple(tag):return tuple(map(int,tag.lstrip('v').split('.')))
def next_version(current):
    if not re.fullmatch(r'\d+\.\d+(?:\.\d+)?',current):raise ValueError('Unsupported version format')
    parts=list(map(int,current.split('.')))
    return '.'.join(map(str,parts[:2]+[(parts[2] if len(parts)==3 else 0)+1]))
def emit(**values):
    with open(os.environ['GITHUB_OUTPUT'],'a') as f:
        for k,v in values.items():f.write(k+'='+str(v)+'\n')
def pin_source(tag,repository="2dust/AndroidLibXrayLite"):
    if repository not in {"2dust/AndroidLibXrayLite","SagerNet/sing-box","MetaCubeX/mihomo"}:raise ValueError("Untrusted source repository")
    obj=api('/repos/'+repository+'/git/ref/tags/'+tag)['object']
    for _ in range(5):
        if obj['type']=='commit':break
        if obj['type']!='tag':raise ValueError('Invalid source ref')
        obj=api('/repos/'+repository+'/git/tags/'+obj['sha'])['object']
    if obj['type']!='commit' or not re.fullmatch(r'[0-9a-f]{40}',obj['sha']):raise ValueError('Unresolved source commit')
    source_url='https://codeload.github.com/'+repository+'/tar.gz/'+obj['sha']
    digest=hashlib.sha256();size=0
    with urllib.request.urlopen(source_url,timeout=60) as response:
        for block in iter(lambda:response.read(1024*1024),b''):
            size+=len(block)
            if size>64*1024*1024:raise ValueError('Source archive exceeds limit')
            digest.update(block)
    return dict(repository=repository,tag=tag,commit=obj['sha'],url=source_url,sha256=digest.hexdigest())

def prepare():
    lock=validate(json.loads((ROOT/'tools/release/core-lock.json').read_text()))
    latest=api('/repos/2dust/AndroidLibXrayLite/releases/latest')
    tag=latest.get('tag_name','')
    if latest.get('draft') or latest.get('prerelease') or not re.fullmatch(r'v\d+\.\d+\.\d+',tag):raise ValueError('Untrusted upstream release metadata')
    engines_path=ROOT/'tools/native/engines-lock.json'
    engines=json.loads(engines_path.read_text()) if engines_path.exists() else []
    newer_engines=[]
    approved={'sing-box':'SagerNet/sing-box','mihomo':'MetaCubeX/mihomo'}
    for engine in engines:
        if approved.get(engine['name'])!=engine['repository']:raise ValueError('Unknown native engine')
        release=api('/repos/'+engine['repository']+'/releases/latest')
        remote_tag=release.get('tag_name','')
        if release.get('draft') or release.get('prerelease') or not re.fullmatch(r'v\d+\.\d+\.\d+',remote_tag):raise ValueError('Invalid native stable release')
        if version_tuple(remote_tag)>version_tuple(engine['tag']):newer_engines.append((engine,remote_tag))
    if os.environ.get('CORE_CHECK_ONLY')=='true':
        print('::notice title=CORE_DISCOVERY_OK::Official stable metadata checked for Xray and '+str(len(engines))+' native engines; newer native candidates='+str(len(newer_engines))+'. Check-only run never publishes.')
        emit(changed='false');return
    gradle=ROOT/'app/build.gradle';text=gradle.read_text()
    current=re.search(r'versionName\s+"([^"]+)"',text)[1];code=int(re.search(r'versionCode\s+(\d+)',text)[1])
    marker=ROOT/'tools/release/auto-core-release.json'
    # If a previous push succeeded but upload failed, retry the EXISTING tagged source, not a retag.
    if marker.exists():
        pending=json.loads(marker.read_text())
        if pending.get('version')==current:
            try:
                published=api('/repos/'+REPO+'/releases/tags/v'+current)
                if published.get('draft') or not {'Parvaz-'+current+'.apk','Parvaz-'+current+'-arm64.apk','SHA256SUMS.txt'}.issubset({a['name'] for a in published.get('assets',[])}):
                    raise ValueError('Incomplete existing release needs review; refusing silent overwrite')
            except urllib.error.HTTPError as e:
                if e.code!=404:raise
                if subprocess.run(['git','rev-parse','--verify','refs/tags/v'+current],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL).returncode:
                    subprocess.run(['git','fetch','--no-tags','--depth=1','origin','refs/tags/v'+current+':refs/tags/v'+current],check=True)
                subprocess.run(['git','checkout','--detach','v'+current],check=True)
                emit(changed='true',mode='resume',version=current);return
    xray_changed=version_tuple(tag)>version_tuple(lock['tag'])
    if not xray_changed and not newer_engines:emit(changed='false');return
    candidate=lock;source=None;changes=[]
    if xray_changed:
        assets=[a for a in latest['assets'] if a.get('name')=='libv2ray.aar']
        if len(assets)!=1:raise ValueError('Ambiguous core artifact')
        a=assets[0];digest=a.get('digest','')
        if not re.fullmatch(r'sha256:[0-9a-f]{64}',digest):raise ValueError('Upstream digest is required')
        candidate=validate({'repository':'2dust/AndroidLibXrayLite','tag':tag,'asset':'libv2ray.aar','url':a['browser_download_url'],'sha256':digest[7:]})
        fetch(candidate);source=pin_source(tag);changes.append('Xray '+tag)
    for engine,new_tag in newer_engines:
        pin=pin_source(new_tag,engine['repository']);engine.update({key:pin[key] for key in ['tag','commit','url','sha256']});changes.append(engine['name']+' '+new_tag)
    # Only write after all candidate metadata/artifacts were validated. CI gates
    # patch application, real native protocol/TLS checks, lint and APKs later.
    if source:
        source_path=ROOT/'tools/native/xray-source-lock.json';source_path.parent.mkdir(parents=True,exist_ok=True)
        source_path.write_text(json.dumps(source,indent=2)+'\n')
    if newer_engines:engines_path.write_text(json.dumps(engines,indent=2)+'\n')
    (ROOT/'tools/release/core-lock.json').write_text(json.dumps(candidate,indent=2)+'\n')
    version=next_version(current)
    text=re.sub(r'versionCode\s+\d+','versionCode '+str(code+1),text,count=1)
    text=re.sub(r'versionName\s+"[^"]+"','versionName "'+version+'"',text,count=1);gradle.write_text(text)
    marker.write_text(json.dumps({'version':version,'version_code':code+1,'core':candidate['tag'],'native':{e['name']:e['tag'] for e in engines}},indent=2)+'\n')
    (ROOT/('docs/releases/v'+version+'.md')).write_text('# Parvaz '+version+' — tested engine updates\n\n'
        +'Updated: '+', '.join(changes)+'. Stable upstream sources only.\n\n'
        +'Same permanent signing certificate; Android approval is required to install. CI compilation, regressions and APK checks gate publication. '
        +'Physical-device connectivity is not guaranteed by CI. Failed builds never publish.\n\n'
        +'به‌روزرسانی خودکار هسته پس از موفقیت آزمون‌ها؛ امضای برنامه ثابت است. نصب با تأیید کاربر انجام می‌شود و آزمون خودکار جایگزین تست اتصال واقعی نیست.\n')
    emit(changed='true',mode='new',version=version)

if __name__=='__main__':prepare()
