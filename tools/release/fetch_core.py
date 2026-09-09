#!/usr/bin/env python3
"""Fetch exactly the reviewed lock. Never silently replace the core with /latest."""
import hashlib,json,re,urllib.request
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
def validate(lock):
    tag=lock['tag']
    if lock.get('repository')!='2dust/AndroidLibXrayLite' or not re.fullmatch(r'v\d+\.\d+\.\d+',tag):raise ValueError('Invalid core identity')
    expected='https://github.com/2dust/AndroidLibXrayLite/releases/download/'+tag+'/libv2ray.aar'
    if lock.get('url')!=expected or lock.get('asset')!='libv2ray.aar' or not re.fullmatch(r'[0-9a-f]{64}',lock.get('sha256','')):raise ValueError('Invalid core pin')
    return lock

def fetch(lock):
    validate(lock);target=ROOT/'app/libs/libv2ray.aar';target.parent.mkdir(parents=True,exist_ok=True)
    def digest(p):
        h=hashlib.sha256()
        with p.open('rb') as f:
            for block in iter(lambda:f.read(1024*1024),b''):h.update(block)
        return h.hexdigest()
    if target.exists() and digest(target)==lock['sha256']:return
    temp=target.with_suffix('.aar.part')
    try:
        request=urllib.request.Request(lock['url'],headers={'User-Agent':'Parvaz-core-fetch'})
        with urllib.request.urlopen(request,timeout=90) as response,temp.open('wb') as output:
            size=0
            while True:
                block=response.read(1024*1024)
                if not block:break
                size+=len(block)
                if size>160*1024*1024:raise ValueError('Core artifact exceeds size limit')
                output.write(block)
        if digest(temp)!=lock['sha256']:raise ValueError('Core SHA-256 mismatch')
        temp.replace(target)
    finally:temp.unlink(missing_ok=True)

if __name__=='__main__':
    lock=validate(json.loads((ROOT/'tools/release/core-lock.json').read_text()))
    fetch(lock);print('Pinned Android Xray library verified:',lock['tag'])
