#!/usr/bin/env python3
"""Corresponding-source bundle: tracked app/build scripts, modified engine trees, vendored modules.
No workspace credentials, signing keys, caches outside the two explicit source trees, or APKs."""
import io,json,subprocess,tarfile,re
from vendor_xray import vendor
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];version=re.search(r'versionName\s+"([^"]+)"',(ROOT/'app/build.gradle').read_text())[1]
output=ROOT/'release-artifacts'/('Parvaz-'+version+'-source.tar.gz');output.parent.mkdir(exist_ok=True)
xray=vendor()
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
