#!/usr/bin/env python3
"""Only data crosses from native execution to isolated signing. Candidate metadata
is regenerated with trusted repository code and compared before signing starts."""
import hashlib,json,shutil,sys,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];ART=ROOT/'.cache/core-candidate'
FIXED=['tools/native/engines-lock.json','tools/native/xray-source-lock.json','tools/release/core-lock.json','tools/release/auto-core-release.json','app/build.gradle']
def digest(path):
 h=hashlib.sha256()
 with path.open('rb') as f:
  for block in iter(lambda:f.read(1024*1024),b''):h.update(block)
 return h.hexdigest()
def metadata(version):
 if not __import__('re').fullmatch(r'\d+\.\d+\.\d+',version):raise ValueError('Invalid version')
 return FIXED+['docs/releases/v'+version+'.md']
def main():
 mode,version=sys.argv[1:];files=metadata(version);ART.mkdir(parents=True,exist_ok=True)
 if mode=='pack':
  manifest={path:digest(ROOT/path) for path in files if (ROOT/path).is_file()}
  for abi in ['arm64-v8a','universal']:
   name='app-'+abi+'-release-unsigned.apk';shutil.copyfile(ROOT/'app/build/outputs/apk/release'/name,ART/name);manifest[name]=digest(ART/name)
  name='Parvaz-'+version+'-source.tar.gz';shutil.copyfile(ROOT/'release-artifacts'/name,ART/name);manifest[name]=digest(ART/name)
  manifest['_tree']=subprocess.check_output(['git','rev-parse','HEAD^{tree}'],cwd=ROOT,text=True).strip()
  (ART/'manifest.json').write_text(json.dumps(manifest,sort_keys=True)+'\n')
 elif mode=='verify':
  raw=(ART/'manifest.json').read_bytes()
  if len(raw)>16384:raise ValueError('Manifest too large')
  manifest=json.loads(raw)
  expected={p for p in files if (ROOT/p).is_file()}|{'app-arm64-v8a-release-unsigned.apk','app-universal-release-unsigned.apk','Parvaz-'+version+'-source.tar.gz'}
  if set(manifest)!=expected|{'_tree'}:raise ValueError('Unexpected candidate artifact fields')
  subprocess.run(['git','add','--']+[p for p in files if (ROOT/p).is_file()],cwd=ROOT,check=True)
  if manifest.pop('_tree')!=subprocess.check_output(['git','write-tree'],cwd=ROOT,text=True).strip():raise ValueError('Candidate source tree differs from tested source')
  for name,sha in manifest.items():
   path=ROOT/name if name in files else ART/name
   if path.is_symlink() or digest(path)!=sha:raise ValueError('Candidate differs from independently prepared metadata or tested artifact')
  output=ROOT/'release-artifacts';output.mkdir(exist_ok=True);name='Parvaz-'+version+'-source.tar.gz';shutil.copyfile(ART/name,output/name)
 else:raise ValueError('Unknown mode')
if __name__=='__main__':main()
