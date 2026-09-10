#!/usr/bin/env python3
"""Isolated source laboratory. NEVER builds/signs/publishes an APK or changes app pins.
A successful run confirms reproductions/tests; promotion remains BLOCKED.
"""
import hashlib,json,os,shutil,subprocess,tarfile,urllib.request
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];LAB=ROOT/'tools/dns-lab';WORK=ROOT/'.cache/dns-lab/run'
WORK.mkdir(parents=True,exist_ok=True)
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
input_files=[LAB/'run.py',LAB/'source-pin.json',LAB/'wrapper-source-pin.json',LAB/'xray-cache-generation.patch',LAB/'xray-udp-transport.patch',LAB/'xray-udp-loopback-fixture.patch',*sorted((LAB/'tests').glob('*.go'))]
input_hashes={str(p.relative_to(ROOT)):digest(p) for p in input_files}
def extract(pin,name):
 if pin['url']!='https://codeload.github.com/'+pin['repository']+'/tar.gz/'+pin['commit']:raise ValueError('Unpinned source URL')
 archive=WORK/(name+'.tar.gz')
 if not archive.exists() or digest(archive)!=pin['sha256']:
  with urllib.request.urlopen(pin['url'],timeout=90) as response,archive.open('wb') as out:
   size=0
   while block:=response.read(1024*1024):
    size+=len(block)
    if size>32*1024*1024:raise ValueError('Source archive exceeds limit')
    out.write(block)
 if digest(archive)!=pin['sha256']:raise ValueError('Source digest mismatch')
 destination=WORK/name
 if destination.exists():shutil.rmtree(destination)
 destination.mkdir()
 with tarfile.open(archive) as tar:
  for member in tar:
   parts=Path(member.name).parts
   if member.name.startswith('/') or '..' in parts or not (member.isfile() or member.isdir()):raise ValueError('Unsafe archive member')
   parts=parts[1:]
   if not parts:continue
   member.name=str(Path(*parts));tar.extract(member,destination,filter='data')
 return destination
xray=json.loads((LAB/'source-pin.json').read_text());wrapper=json.loads((LAB/'wrapper-source-pin.json').read_text())
assert wrapper==json.loads((ROOT/'tools/native/xray-source-lock.json').read_text()),'App wrapper pin changed; rebase/review lab first'
assert wrapper['tag']==json.loads((ROOT/'tools/release/core-lock.json').read_text())['tag']
source=extract(xray,'baseline');wrapper_source=extract(wrapper,'wrapper-baseline')
assert xray['commit'][:12] in (wrapper_source/'go.mod').read_text(),'Wrapper no longer uses the laboratory Xray commit'
patched=WORK/'patched'
if patched.exists():shutil.rmtree(patched)
shutil.copytree(source,patched)
subprocess.run(['patch','--batch','--fuzz=0','-p1','-i',str(LAB/'xray-cache-generation.patch')],cwd=patched,check=True)
shutil.copyfile(LAB/'tests/parvaz_naive_flush_test.go',source/'app/dns/parvaz_naive_flush_test.go')
for name in ['parvaz_cache_generation_test.go','parvaz_transport_limit_test.go']:
 shutil.copyfile(LAB/'tests'/name,patched/'app/dns'/name)
hardened=WORK/'udp-isolated'
if hardened.exists():shutil.rmtree(hardened)
shutil.copytree(patched,hardened)
(hardened/'app/dns/parvaz_transport_limit_test.go').unlink()
for patch in ['xray-udp-transport.patch','xray-udp-loopback-fixture.patch']:
 subprocess.run(['patch','--batch','--fuzz=0','-p1','-i',str(LAB/patch)],cwd=hardened,check=True)
shutil.copyfile(LAB/'tests/parvaz_udp_transport_test.go',hardened/'app/dns/parvaz_udp_transport_test.go')
wrapper_hardened=WORK/'wrapper-udp-isolated'
if wrapper_hardened.exists():shutil.rmtree(wrapper_hardened)
shutil.copytree(wrapper_source,wrapper_hardened)
wrapper_patched=WORK/'wrapper-patched'
if wrapper_patched.exists():shutil.rmtree(wrapper_patched)
shutil.copytree(wrapper_source,wrapper_patched)
env=dict(os.environ,GOMAXPROCS='2',GOTOOLCHAIN='local',GOCACHE=str(ROOT/'.cache/dns-lab/go-build'),GOMODCACHE=str(ROOT/'.cache/dns-lab/go-mod'))
stages=[]
for name,module,target,pattern,count in [
 ('baseline',wrapper_source,source,'^TestParvazNaiveFlush',1),
 ('patched',wrapper_patched,patched,'^TestParvaz|^TestFqdn$|^Test_parseResponse$|^Test_buildReqMsgs$|^Test_genEDNS0Options$|^TestStaticHosts$',3),
 ('udp-isolated',wrapper_hardened,hardened,'^TestParvaz|^TestFqdn$|^Test_parseResponse$|^Test_buildReqMsgs$|^Test_genEDNS0Options$|^TestStaticHosts$|^TestUDPServer$|^TestUDPServerSubnet$',3)]:
 subprocess.run(['go','mod','edit','-replace=github.com/xtls/xray-core='+str(target)],cwd=module,env=env,check=True)
 cmd=['go','test','-mod=mod','-race','-p','2','-count='+str(count),'-timeout=8m','-json','-run',pattern,'github.com/xtls/xray-core/app/dns']
 print('STAGE',name,flush=True);log=WORK/(name+'.jsonl')
 with log.open('w') as out:subprocess.run(cmd,cwd=module,env=env,stdout=out,check=True)
 events=[json.loads(line) for line in log.read_text().splitlines() if line.strip()]
 assert any(e.get('Action')=='pass' and 'Test' not in e for e in events),'No package PASS'
 assert not any(e.get('Action') in ['fail','skip'] for e in events),'Unexpected failure or skip'
 text=''.join(e.get('Output','') for e in events)
 assert 'WARNING: DATA RACE' not in text,'Race detector failure'
 expected='NAIVE_FLUSH_REPRODUCED:' if name=='baseline' else 'PROMOTION_BLOCKED:'
 assert expected in text,'Required negative-control/known-limit evidence absent'
 passes=[e['Test'] for e in events if e.get('Action')=='pass' and e.get('Test') and '/' not in e['Test']]
 if name=='baseline':assert len(passes)==1,'Baseline control changed'
 if name=='patched':assert len(set(passes))==18 and len(passes)==54,'Test set changed; review expected coverage'
 if name=='udp-isolated':
  assert len(set(passes))==39 and len(passes)==117,'UDP test set changed; review expected coverage'
  assert 'UDP_OLD_TRANSPORT_REPLAY_REJECTED:' in text and 'UDP_NEW_CHANNEL_REPLAY_OUT_OF_SCOPE:' in text,'Missing UDP boundary proofs'
 graph=subprocess.check_output(['go','list','-mod=mod','-m','all'],cwd=module,env=env,text=True)
 graph=graph.replace(str(target),'<pinned-xray-source>')
 (WORK/(name+'-modules.txt')).write_text(graph)
 stages.append({'stage':name,'top_level_pass_events':len(passes),'unique_top_level_tests':sorted(set(passes)),'repetitions':count,'race_detector':True,'module_graph_sha256':hashlib.sha256(graph.encode()).hexdigest(),'effective_go_mod_sha256':digest(module/'go.mod'),'log_sha256':digest(log)})
assert len({s['module_graph_sha256'] for s in stages})==1,'Stages resolved different dependency graphs'
assert input_hashes=={str(p.relative_to(ROOT)):digest(p) for p in input_files},'Laboratory inputs changed while tests ran'
result={'tested_inputs_sha256':input_hashes,'ci_commit':os.environ.get('GITHUB_SHA'),'experiment':'Xray cache generation and DNS-local UDP transport ownership','promotion_allowed':False,'production_apk_changed':False,
 'source':xray,'wrapper_source':wrapper,'patch_sha256':digest(LAB/'xray-cache-generation.patch'),'transport_patch_sha256':digest(LAB/'xray-udp-transport.patch'),'test_fixture_patch_sha256':digest(LAB/'xray-udp-loopback-fixture.patch'),
 'go_version':subprocess.check_output(['go','version'],env=env,text=True).strip(),'stages':stages,
 'historical_reproductions':['NAIVE_CLEAR_REPOPULATES_CACHE','CACHE_ONLY_UDP_ID_REUSE_BINDS_OLD_CALLBACK_TO_NEW_REQUEST'],
 'transport_boundary_tested':'Retired routing-link callbacks cannot access fresh pending requests, including same ID and same question. New-link wire authenticity is outside this primitive.',
 'blocked_by':['UDP_REPLAY_INJECTED_INTO_NEW_TRANSPORT_NOT_AUTHENTICATED','NO_ENGINE_WIDE_RESOLVER_RELOAD','OTHER_ENGINE_AND_SYSTEM_CACHES_NOT_COVERED','NO_ANDROID_WRAPPER_BRIDGE_OR_NATIVE_VPN_TEST'],
 'scope':'Real pinned upstream DNS source, wrapper-root dependency resolution, synthetic/in-memory DNS fixtures plus two upstream loopback UDP integration cases with RFC-conforming test replies; not the shipped AAR, not Android, not physical DNS/leak/battery validation.'}
(WORK/'result.json').write_text(json.dumps(result,indent=2)+'\n')
print('::notice title=DNS_CACHE_LAB_RESULT::Cache source tests/reproductions succeeded; PROMOTION_BLOCKED; baseline=1 patched=54 udp-isolated=117 top-level PASS events; no APK publication')
