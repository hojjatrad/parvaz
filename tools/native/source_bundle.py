#!/usr/bin/env python3
"""Corresponding-source bundle: tracked app/build scripts, modified engine trees, vendored modules.
No workspace credentials, signing keys, caches outside the two explicit source trees, or APKs."""
import io,json,subprocess,tarfile,re
from vendor_xray import vendor
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];version=re.search(r'versionName\s+"([^"]+)"',(ROOT/'app/build.gradle').read_text())[1]
output=ROOT/'release-artifacts'/('Parvaz-'+version+'-source.tar.gz');output.parent.mkdir(exist_ok=True)
xray=vendor()
# Ship the license/NOTICE texts of vendored runtime dependencies inside the APK too.
notices=ROOT/'app/src/main/assets/licenses/native-dependencies.txt'
with notices.open('w',encoding='utf-8') as out:
 out.write('Vendored native dependencies. Original authors retain their licenses.\nCorresponding source accompanies this release.\n')
 goroot=Path(subprocess.check_output(['go','env','GOROOT'],cwd=ROOT,text=True).strip())
 out.write('\n=== Go runtime LICENSE ===\n'+(goroot/'LICENSE').read_text())
 for name in [c['name'] for c in json.loads((ROOT/'tools/native/engines-lock.json').read_text())]+['xray-wrapper']:
  source=ROOT/'.cache/native'/name
  for file in sorted(source.rglob('*')):
   base=file.name.upper()
   if file.is_file() and (base.startswith(('LICENSE','COPYING','NOTICE','COPYRIGHT','AUTHORS','PATENTS')) or base.endswith('.LICENSE')):
    if file.stat().st_size>2*1024*1024:raise ValueError('Unexpectedly large license text')
    out.write('\n\n=== '+name+'/'+str(file.relative_to(source))+' ===\n')
    out.write(file.read_text(encoding='utf-8',errors='replace'))
print('NATIVE_NOTICES_OK',notices.stat().st_size)
tracked=subprocess.check_output(['git','archive','HEAD'],cwd=ROOT)
with tarfile.open(output,'w:gz') as archive,tarfile.open(fileobj=io.BytesIO(tracked)) as app:
 for member in app:
  if member.name.endswith(('.p12','.jks','.keystore')):raise SystemExit('Unexpected credential file in Git tree')
  member.name='parvaz/'+member.name;archive.addfile(member,app.extractfile(member) if member.isfile() else None)
 for core in json.loads((ROOT/'tools/native/engines-lock.json').read_text()):
  source=ROOT/'.cache/native'/core['name']
  if not (source/'vendor/modules.txt').is_file():raise SystemExit('Vendored corresponding source missing')
  archive.add(source,arcname='engines/'+core['name'])
 archive.add(xray,arcname='engines/xray-wrapper')
print('SOURCE_BUNDLE_OK',output.name,output.stat().st_size)
