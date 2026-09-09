#!/usr/bin/env python3
"""Build pinned Android PIE engines from source, never execute downloaded opaque binaries.
NDK 27.2.12479018 and a compatible Go toolchain are prerequisites. Test/release sources stay in CI cache."""
import argparse,hashlib,json,os,shutil,subprocess,tarfile,urllib.request
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
parser=argparse.ArgumentParser();parser.add_argument('--probe',action='store_true');parser.add_argument('--host',action='store_true');args=parser.parse_args()
cache=ROOT/'.cache/native';cache.mkdir(parents=True,exist_ok=True)
ndk=Path(os.environ.get('ANDROID_HOME',''))/'ndk/27.2.12479018/toolchains/llvm/prebuilt/linux-x86_64/bin'
for core in json.loads((ROOT/'tools/native/engines-lock.json').read_text()):
 archive=cache/(core['name']+'.tar.gz');source=cache/core['name']
 if not archive.exists() or hashlib.sha256(archive.read_bytes()).hexdigest()!=core['sha256']:
  with urllib.request.urlopen(core['url'],timeout=120) as r,archive.open('wb') as f:shutil.copyfileobj(r,f,1024*1024)
 if hashlib.sha256(archive.read_bytes()).hexdigest()!=core['sha256']:raise SystemExit('Source digest mismatch')
 if source.exists():shutil.rmtree(source)
 source.mkdir()
 with tarfile.open(archive) as tar:
  for member in tar.getmembers():
   parts=Path(member.name).parts[1:]
   if not parts:continue
   if member.issym() or member.islnk() or '..' in parts or member.name.startswith('/'):raise SystemExit('Unsafe source archive')
   member.name=str(Path(*parts));tar.extract(member,source,filter='data')
 shutil.copyfile(ROOT/'tools/native/parvaz_parent_android.go',source/core['patchdir']/'parvaz_parent_android.go')
 env=dict(os.environ,GOMAXPROCS='2',GOTOOLCHAIN='auto')
 subprocess.run(['go','mod','download'],cwd=source,env=env,check=True)
 targets=[('x86_64','amd64','x86_64-linux-android24-clang')] if args.probe else [('arm64-v8a','arm64','aarch64-linux-android24-clang'),('armeabi-v7a','arm','armv7a-linux-androideabi24-clang')]
 if args.host:targets=[('host','amd64','')]
 for abi,arch,compiler in targets:
  output=(cache/'host'/core['name']) if args.host else (ROOT/('.cache/native-probe' if args.probe else 'app/libs/jni')/abi/('lib'+core['name'].replace('-','')+'.so'))
  output.parent.mkdir(parents=True,exist_ok=True)
  buildenv=dict(env,GOOS='linux' if args.host else 'android',GOARCH=arch,CGO_ENABLED='0' if args.host else '1',GOARM='7')
  if not args.host:buildenv.update(CC=str(ndk/compiler),CGO_LDFLAGS='-Wl,-z,max-page-size=16384')
  cmd=['go','build','-p','2','-trimpath','-buildvcs=false']
  if not args.host:cmd+=['-buildmode=pie']
  if core['tags']:cmd+=['-tags',core['tags']]
  cmd+=['-ldflags','-s -w','-o',str(output),core['package']]
  subprocess.run(cmd,cwd=source,env=buildenv,check=True)
  print('ENGINE_BUILT',core['name'],core['tag'],abi,hashlib.sha256(output.read_bytes()).hexdigest(),flush=True)
 if not args.probe and not args.host:
  # Preserve corresponding dependency source for the release's source bundle.
  subprocess.run(['go','mod','vendor'],cwd=source,env=env,check=True)
