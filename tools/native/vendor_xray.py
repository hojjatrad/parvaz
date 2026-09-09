#!/usr/bin/env python3
"""Obtain the LGPL library's corresponding source and dependencies for redistribution."""
import hashlib,json,os,shutil,subprocess,tarfile,urllib.request
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
def vendor():
 lock=json.loads((ROOT/'tools/native/xray-source-lock.json').read_text());core=json.loads((ROOT/'tools/release/core-lock.json').read_text())
 if lock['repository']!='2dust/AndroidLibXrayLite' or lock['tag']!=core['tag'] or lock['url']!='https://codeload.github.com/2dust/AndroidLibXrayLite/tar.gz/'+lock['commit']:raise ValueError('Xray source pin mismatch')
 cache=ROOT/'.cache/native';cache.mkdir(parents=True,exist_ok=True);archive=cache/'xray-wrapper.tar.gz';source=cache/'xray-wrapper'
 if not archive.exists() or hashlib.sha256(archive.read_bytes()).hexdigest()!=lock['sha256']:
  with urllib.request.urlopen(lock['url'],timeout=60) as r,archive.open('wb') as f:shutil.copyfileobj(r,f)
 if hashlib.sha256(archive.read_bytes()).hexdigest()!=lock['sha256']:raise ValueError('Xray source hash mismatch')
 if source.exists():shutil.rmtree(source)
 source.mkdir()
 with tarfile.open(archive) as tar:
  for member in tar:
   parts=Path(member.name).parts[1:]
   if not parts:continue
   if member.issym() or member.islnk() or '..' in parts:raise ValueError('Unsafe source archive')
   member.name=str(Path(*parts));tar.extract(member,source,filter='data')
 subprocess.run(['go','mod','vendor'],cwd=source,env=dict(os.environ,GOTOOLCHAIN='auto',GOMAXPROCS='2'),check=True)
 return source
if __name__=='__main__':vendor()
