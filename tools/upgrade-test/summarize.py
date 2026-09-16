import pathlib,xml.etree.ElementTree as ET,json,hashlib,subprocess
root=pathlib.Path('.cache/upgrade-evidence');results=[]
for p in pathlib.Path('tools/android-probe/build/outputs/androidTest-results').rglob('*.xml'):
 suite=ET.parse(p).getroot()
 if suite.tag=='testsuite':results.append({k:int(suite.get(k,'0')) for k in ['tests','failures','errors','skipped']})
if sum(r['tests'] for r in results)!=1 or any(r[k] for r in results for k in ['failures','errors','skipped']):raise SystemExit('Actual upgrade test did not pass exactly once')
report={'source_commit':subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),'tests':results,'candidate_apk_sha256':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in pathlib.Path('release-artifacts').glob('*.apk')},'scope':'Unmodified permanent-signed ARM64 APKs on Android30 ARM native-bridge emulator; staged exact-host HTTPS metadata; genuine app Update button and system installer; no physical-device assertion','sha256':{str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in root.rglob('*') if p.is_file()}}
(root/'summary.json').write_text(json.dumps(report,indent=2)+'\n');print('::notice title=ACTUAL_APK_UPGRADE::'+json.dumps(report['tests'])+'; staged HTTPS; data preserved; real installer; Android30 emulator')
