#!/usr/bin/env python3
"""Explicit, pinned combined-candidate integration. No signing or publication authority.
The ordinary upstream fetch/source-bundle path remains unchanged.
"""
import argparse,hashlib,json,pathlib,shutil,subprocess,sys,zipfile
from package_evidence import validate_closure
from source_manifest import collect,canonical
ROOT=pathlib.Path(__file__).resolve().parents[2]
ARCH={'arm64-v8a':'arm64','armeabi-v7a':'arm'}
GRAPH_COMMAND=['go','list','-deps','-json','-buildmode=c-shared','-trimpath','./gobind']

def digest(path):
 with pathlib.Path(path).open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()

def validate_report(report,lock):
 if lock.get('schema')!=1:raise ValueError('Unknown candidate contract')
 for key in ['base_aar_sha256','aar_sha256','source_provenance']:
  if report.get(key)!=lock[key]:raise ValueError('Unreviewed Xray candidate '+key)
 if report.get('public_java_api_equal') is not True or report.get('all_java_class_bytes_equal') is not True:raise ValueError('Missing complete Java compatibility evidence')
 rows=report.get('binaries',[])
 if len(rows)!=2 or {b.get('abi') for b in rows}!=set(ARCH):raise ValueError('Both unique ARM targets required')
 for b in rows:
  abi=b['abi'];p=b['compiler_inventory'];ex=b.get('extraction',{})
  if b.get('sha256')!=lock['jni_sha256'][abi] or b.get('extraction_exit')!=0 or ex.get('goos')!='android' or ex.get('goarch')!=ARCH[abi] or ex.get('symbol_count',0)<=0:raise ValueError('Unverified exact Android JNI output')
  if p.get('coverage_schema')!=2 or p.get('inventory_command')!=GRAPH_COMMAND or p.get('goos')!='android' or p.get('goarch')!=ARCH[abi] or p.get('cgo_enabled')!='1':raise ValueError('Wrong compiler target or contract')
  if p.get('generated_go_mod_sha256')!=lock['generated_go_mod_sha256'][abi]:raise ValueError('Generated module changed')
  packages=p.get('packages')
  if not isinstance(packages,list) or not packages or any(not isinstance(x,str) or not x or any(c.isspace() for c in x) for x in packages) or len(set(packages))!=len(packages):raise ValueError('Invalid compiler package set')
  validate_closure(packages,p.get('root'))
  if p['root']['import_path']!='gobind/gobind' or p['root']['module_path']!='gobind' or not {'github.com/2dust/AndroidLibXrayLite','golang.org/x/mobile/bind/seq'}.issubset(packages):raise ValueError('Wrong generated JNI root')
 return rows

def verify(root=ROOT,aar=None):
 root=pathlib.Path(root);aar=pathlib.Path(aar) if aar else root/'app/libs/libv2ray.aar'
 lock=json.loads((root/'tools/native/xray-candidate-lock.json').read_text())
 original=json.loads((root/'tools/release/core-lock.json').read_text())
 source_lock=json.loads((root/'tools/native/xray-source-lock.json').read_text())
 if original['sha256']!=lock['base_aar_sha256'] or source_lock!=lock['source_provenance']['source_lock']:raise ValueError('Candidate/upstream source identity mismatch')
 evidence=root/'.cache/xray-security-review';report_path=evidence/'summary.json'
 report=json.loads(report_path.read_text());rows=validate_report(report,lock)
 if digest(aar)!=lock['aar_sha256']:raise ValueError('Not the reviewed rebuilt AAR')
 manifest=evidence/'source-tree-manifest.json';manifest_sha=lock['source_provenance']['source_tree_manifest_sha256']
 if digest(manifest)!=manifest_sha:raise ValueError('Source manifest changed')
 source=root/'.cache/native/xray-wrapper'
 if hashlib.sha256(canonical(collect(source))).hexdigest()!=manifest_sha:raise ValueError('Actual corresponding source changed; baseline re-vendoring is forbidden here')
 with zipfile.ZipFile(aar) as z:
  expected={'jni/'+abi+'/libgojni.so' for abi in ARCH}
  if {n for n in z.namelist() if n.endswith('.so')}!=expected:raise ValueError('Unexpected candidate native contents')
  for b in rows:
   if hashlib.sha256(z.read('jni/'+b['abi']+'/libgojni.so')).hexdigest()!=b['sha256']:raise ValueError('JNI differs from compiler proof')
 return {'report':report,'proof_sha256':digest(report_path),'source':source,'binaries':{b['abi']:b for b in rows},'lock':lock}

def build():
 # Reconstruct the baseline and review build using unchanged pinned producers.
 subprocess.run([sys.executable,str(ROOT/'tools/release/fetch_core.py')],check=True,cwd=ROOT)
 subprocess.run([sys.executable,str(ROOT/'tools/native/rebuild_xray_probe.py')],check=True,cwd=ROOT)
 aar=ROOT/'.cache/xray-security-review/libv2ray-review.aar'
 verify(ROOT,aar)
 target=ROOT/'app/libs/libv2ray.aar';temp=target.with_suffix('.candidate.tmp')
 try:
  shutil.copyfile(aar,temp);temp.replace(target)
 finally:temp.unlink(missing_ok=True)
 verify(ROOT)
 print('::notice title=XRAY_COMBINED_CANDIDATE::Pinned rebuilt AAR and exact corresponding source installed in this candidate workspace only; no release authority')

if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('mode',choices=['build','verify']);a=p.parse_args()
 if a.mode=='build':build()
 else:verify()
