import hashlib,json,pathlib,shutil,subprocess
root=pathlib.Path(__file__).resolve().parents[2];src=root/'release-artifacts';dest=root/'preview-artifacts';dest.mkdir(exist_ok=True)
for original,new in [('Parvaz-1.28.7-arm64.apk','Parvaz-1.28.7-TEST-arm64.apk'),('Parvaz-1.28.7.apk','Parvaz-1.28.7-TEST.apk'),('Parvaz-1.28.7-source.tar.gz','Parvaz-1.28.7-TEST-source.tar.gz')]:shutil.copyfile(src/original,dest/new)
for name in ['sbom.cdx.json','vulnerability-review.json','binary-runtime.json']:shutil.copyfile(root/'.cache/supply-chain'/name,dest/name)
receipt={'status':'EXPERIMENTAL_NOT_APPROVED_FOR_STABLE','version':'1.28.7','version_code':41,'next_final_version_must_exceed':'1.28.7','next_final_code_minimum':42,'source_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip(),'signer_sha256':json.loads((root/'docs/signing/release-signing.json').read_text())['certificate_sha256'],'installation':'Owner-requested manual testing; actual installation/data migration NOT yet verified','security':'Unresolved findings retained in vulnerability-review.json. Not waived. Not a clean-security claim.'}
(dest/'TEST-STATUS.json').write_text(json.dumps(receipt,indent=2)+'\n')
lines=[]
for p in sorted(dest.iterdir()):
 with p.open('rb') as f:lines.append(hashlib.file_digest(f,'sha256').hexdigest()+'  '+p.name)
(dest/'SHA256SUMS.txt').write_text('\n'.join(lines)+'\n')
print('TEST_ASSETS_READY; stable channel remains unchanged')
