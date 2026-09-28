"""Bind publication to the exact successful, signed, installed candidate bytes."""
import hashlib,json,os,pathlib,shutil
# A finding may only be non-actionable on evidence taken from the shipped binaries.
# Rows excluded per advisory must carry, for every single advisory, either a
# withdrawal, a GOOS exclusion, or proof that the vulnerable import paths are not
# in the compiled package graph of the exact shipped .so files. Anything else,
# including one REVIEW_REQUIRED advisory inside an otherwise excluded row, blocks
# publication.
ROW_EVIDENCE=('WITHDRAWN','NOT_IN_ANY_SHIPPED_BINARY_MODULE_TABLE','NOT_APPLICABLE_TO_VERIFIED_BINARY_OS','NOT_APPLICABLE_TO_VERIFIED_BINARY_SCOPE')
ADVISORY_EVIDENCE=('WITHDRAWN','NOT_APPLICABLE_TO_VERIFIED_BINARY_OS','NOT_IN_VERIFIED_COMPILED_PACKAGE_GRAPH')
def check_non_applicability(row):
 disposition=row.get('disposition')
 if disposition not in ROW_EVIDENCE:raise SystemExit('Non-applicability lacks binary evidence')
 if disposition in ('WITHDRAWN','NOT_IN_ANY_SHIPPED_BINARY_MODULE_TABLE'):return
 dispositions=row.get('advisory_dispositions') or {}
 if not dispositions or any(value not in ADVISORY_EVIDENCE for value in dispositions.values()):raise SystemExit('Non-applicability lacks binary evidence')
 sources=[s for s in (row.get('component') or {}).get('sources',[]) if isinstance(s,dict) and 'binary' in s]
 if not sources or any(s.get('package_coverage_verified') is not True or s.get('goos')!='android' for s in sources):raise SystemExit('Non-applicability lacks binary evidence')
def verify(root,version,commit):
 reports=list((root/'.cache/upgrade-validated').rglob('summary.json'))
 if len(reports)!=1:raise SystemExit('Exactly one upgrade proof required')
 proof=json.loads(reports[0].read_text());review=json.loads((root/'.cache/supply-chain/vulnerability-review.json').read_text())
 if proof.get('source_commit')!=commit or review.get('source_commit')!=commit:raise SystemExit('Evidence must match exact source commit')
 if not review.get('native_graphs_included') or review.get('status') not in ('NO_KNOWN_MATCHES_IN_SCANNED_SCOPE','NO_APPLICABLE_MATCHES_IN_BUILT_RUNTIME'):raise SystemExit('Dependency review incomplete or unresolved')
 if review.get('status')=='NO_APPLICABLE_MATCHES_IN_BUILT_RUNTIME':
  if not review.get('binary_runtime_verified'):raise SystemExit('Non-applicability lacks binary evidence')
  for row in review.get('findings',[]):check_non_applicability(row)
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
