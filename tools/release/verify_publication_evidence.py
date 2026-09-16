"""Bind publication to the exact successful, signed, installed candidate bytes."""
import hashlib,json,os,pathlib,shutil
def verify(root,version,commit):
 reports=list((root/'.cache/upgrade-validated').rglob('summary.json'))
 if len(reports)!=1:raise SystemExit('Exactly one upgrade proof required')
 proof=json.loads(reports[0].read_text());review=json.loads((root/'.cache/supply-chain/vulnerability-review.json').read_text())
 if proof.get('source_commit')!=commit or review.get('source_commit')!=commit:raise SystemExit('Evidence must match exact source commit')
 if not review.get('native_graphs_included') or review.get('status')!='NO_KNOWN_MATCHES_IN_SCANNED_SCOPE':raise SystemExit('Dependency review incomplete or unresolved')
 tests=proof.get('tests',[])
 if sum(r['tests'] for r in tests)!=1 or any(r[k] for r in tests for k in ['failures','errors','skipped']):raise SystemExit('Upgrade test did not pass exactly once')
 artifacts=root/'release-artifacts';expected={f'Parvaz-{version}-arm64.apk': 'app-arm64-v8a-release.apk',f'Parvaz-{version}.apk':'app-universal-release.apk'}
 if set(proof.get('candidate_apk_sha256',{}))!=set(expected):raise SystemExit('Missing candidate APK identity')
 outputs=root/'app/build/outputs/apk/release';outputs.mkdir(parents=True,exist_ok=True)
 for name,original in expected.items():
  apk=artifacts/name
  with apk.open('rb') as stream:actual=hashlib.file_digest(stream,'sha256').hexdigest()
  if actual!=proof['candidate_apk_sha256'][name]:raise SystemExit('Publish bytes differ from verified candidate')
  shutil.copyfile(apk,outputs/original)
 for source,suffix in [('sbom.cdx.json','sbom.cdx.json'),('vulnerability-review.json','dependency-review.json')]:shutil.copyfile(root/'.cache/supply-chain'/source,artifacts/f'Parvaz-{version}-{suffix}')
 shutil.copyfile(reports[0],artifacts/f'Parvaz-{version}-upgrade-verification.json')
 print('EXACT_TESTED_SIGNED_APKS_AND_SOURCE_BOUND_TO_PUBLICATION')
if __name__=='__main__':verify(pathlib.Path(__file__).resolve().parents[2],os.environ['RELEASE_VERSION'],os.environ['GITHUB_SHA'])
