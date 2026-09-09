#!/usr/bin/env python3
"""Refuse to replace latest with an older app/code/core during concurrent releases."""
import base64,json,os,re,urllib.request,urllib.error
from pathlib import Path
from urllib.parse import quote
ROOT=Path(__file__).resolve().parents[2]

def version(value):
    if not re.fullmatch(r'v?\d+\.\d+(?:\.\d+)?',value):raise ValueError('Unsupported release version')
    parts=tuple(map(int,value.lstrip('v').split('.')))
    return parts+(0,)*(3-len(parts))

def validate_order(candidate,previous):
    if version(candidate['version'])<=version(previous['version']):raise ValueError('Candidate must be newer than the published stable release')
    if candidate['code']<=previous['code']:raise ValueError('versionCode must increase over published stable release')
    if previous.get('core') and version(candidate['core'])<version(previous['core']):raise ValueError('Refusing a core downgrade from published stable release')

def api(path):
    request=urllib.request.Request('https://api.github.com/repos/'+os.environ['GITHUB_REPOSITORY']+path,headers={'Authorization':'Bearer '+os.environ['GH_TOKEN'],'Accept':'application/vnd.github+json','User-Agent':'Parvaz-release-order-guard'})
    with urllib.request.urlopen(request,timeout=30) as response:
        data=response.read(2*1024*1024+1)
        if len(data)>2*1024*1024:raise ValueError('Oversize metadata')
        return json.loads(data)

def source(path,tag):
    item=api('/contents/'+path+'?ref='+quote(tag,safe=''))
    if item.get('encoding')!='base64':raise ValueError('Unexpected source encoding')
    return base64.b64decode(item['content']).decode('utf-8')

def app(text):
    return {'version':re.search(r'versionName\s+"([^"]+)"',text)[1],'code':int(re.search(r'versionCode\s+(\d+)',text)[1])}

def main():
    candidate=app((ROOT/'app/build.gradle').read_text());candidate['core']=json.loads((ROOT/'tools/release/core-lock.json').read_text())['tag']
    try:latest=api('/releases/latest')
    except urllib.error.HTTPError as error:
        if error.code==404:return
        raise
    if latest.get('draft') or latest.get('prerelease'):raise ValueError('Unexpected stable channel metadata')
    tag=latest['tag_name'];version(tag)
    previous=app(source('app/build.gradle',tag))
    if version(previous['version'])!=version(tag):raise ValueError('Published tag/source version mismatch')
    try:previous['core']=json.loads(source('tools/release/core-lock.json',tag))['tag']
    except urllib.error.HTTPError as error:
        if error.code!=404:raise
    validate_order(candidate,previous)
    print('::notice title=RELEASE_ORDER_VERIFIED::Candidate app, versionCode and pinned core do not roll back the stable channel.')

if __name__=='__main__':main()
