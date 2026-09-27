"""Publish only the same bytes that passed both prior-installation fixtures."""
import hashlib,json,pathlib,shutil,subprocess,importlib.util
ROOT=pathlib.Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('prior_release',ROOT/'tools/upgrade-test/prior_release.py');prior=importlib.util.module_from_spec(spec);spec.loader.exec_module(prior)
def digest(path):
    with path.open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()
def verify(results, artifacts, commit):
    if len(results)!=2:raise ValueError('Both prior upgrade summaries required')
    expected={name:digest(artifacts/new) for name,new in [('Parvaz-1.28.8-arm64.apk','Parvaz-1.28.8-TEST-arm64.apk'),('Parvaz-1.28.8.apk','Parvaz-1.28.8-TEST.apk')]}
    seen=set()
    for report in results:
        p=report['prior'];fixture=p['fixture']
        if fixture in seen or p!=prior.select(fixture):raise ValueError('Duplicate or unreviewed prior installation')
        seen.add(fixture)
        if report['source_commit']!=commit or report['candidate_apk_sha256']!=expected:raise ValueError('Installed APK/source mismatch')
        if report['tests']!=[{'tests':1,'failures':0,'errors':0,'skipped':0}]:raise ValueError('Installation test not successful exactly once')
    if seen!={'stable39','test41'}:raise ValueError('Missing prior coverage')
if __name__=='__main__':
    artifacts=ROOT/'preview-artifacts';paths=list((ROOT/'.cache/test-install-results').rglob('summary.json'))
    results=[json.loads(p.read_text()) for p in paths]
    commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
    verify(results,artifacts,commit)
    for p,r in zip(paths,results):shutil.copyfile(p,artifacts/('install-'+r['prior']['fixture']+'.json'))
    status=json.loads((artifacts/'TEST-STATUS.json').read_text())
    if status['source_commit']!=commit:raise SystemExit('Receipt/source mismatch')
    status['installation']={'verified':'Stable39 and TEST41 to permanent-signed TEST42 on disposable Android30 ARM native-bridge emulator. Same APK bytes; staged HTTPS; real Update button and system installer; one imported profile preserved.','not_verified':'Physical devices, ARM32 runtime, every lock/backup/migration scenario, or real public stable-update discovery.'}
    status['release_status']='BLOCKED_FOR_STABLE'
    (artifacts/'TEST-STATUS.json').write_text(json.dumps(status,indent=2)+'\n')
    (artifacts/'SHA256SUMS.txt').write_text(''.join(digest(p)+'  '+p.name+'\n' for p in sorted(artifacts.iterdir()) if p.is_file() and p.name!='SHA256SUMS.txt'))
    print('TEST_INSTALLATION_BOUND_TO_EXACT_BYTES; STABLE_REMAINS_BLOCKED')
