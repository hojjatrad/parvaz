#!/usr/bin/env python3
import hashlib,struct,zipfile
from pathlib import Path
for apk in Path('app/build/outputs/apk/release').glob('*.apk'):
 abis=['arm64-v8a'] if 'arm64-v8a' in apk.name else ['armeabi-v7a'] if 'armeabi-v7a' in apk.name else ['arm64-v8a','armeabi-v7a']
 with zipfile.ZipFile(apk) as z:
  for abi in abis:
   for engine in ['singbox','mihomo']:
    name='lib/'+abi+'/lib'+engine+'.so';data=z.read(name)
    if data[:4]!=b'\x7fELF' or struct.unpack('<H',data[16:18])[0]!=3:raise SystemExit('Not an ELF PIE: '+name)
    if struct.unpack('<H',data[18:20])[0]!={'arm64-v8a':183,'armeabi-v7a':40}[abi]:raise SystemExit('Wrong ELF architecture')
    bits=data[4]
    phoff=struct.unpack_from('<Q' if bits==2 else '<I',data,32 if bits==2 else 28)[0]
    phsize,phnum=struct.unpack_from('<HH',data,54 if bits==2 else 42)
    for i in range(phnum):
     pos=phoff+i*phsize
     if struct.unpack_from('<I',data,pos)[0]!=1:continue
     offset,vaddr=struct.unpack_from('<QQ' if bits==2 else '<II',data,pos+(8 if bits==2 else 4))
     alignment=struct.unpack_from('<Q' if bits==2 else '<I',data,pos+(48 if bits==2 else 28))[0]
     if alignment<16384 or (vaddr-offset)%16384:raise SystemExit('Engine lacks 16 KiB load alignment: '+name)
    original=Path('app/libs/jni')/abi/('lib'+engine+'.so')
    if hashlib.sha256(data).digest()!=hashlib.sha256(original.read_bytes()).digest():raise SystemExit('Packaged engine differs from verified source build')
    if len(data)<1024*1024:raise SystemExit('Engine unexpectedly small: '+name)
    print('PACKAGED_ENGINE_OK',apk.name,name,hashlib.sha256(data).hexdigest())
