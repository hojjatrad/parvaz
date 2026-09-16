"""Preserve full findings for the owner's explicitly requested experimental APK.
ONLY a completed known-advisory review may be accepted for manual testing.
Inventory, scan transport/parser errors and all stable-release gates remain fatal.
"""
import json,pathlib,subprocess,sys
root=pathlib.Path(__file__).resolve().parents[2]
subprocess.run([sys.executable,str(root/'tools/preview/guard.py')],check=True)
run=subprocess.run([sys.executable,str(root/'tools/security/dependency_audit.py'),'--native'],cwd=root,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
(root/'.cache/preview-scan.log').write_text(run.stdout)
if run.returncode not in (0,2):
 print(run.stdout[-4000:]);raise SystemExit(run.returncode)
report=json.loads((root/'.cache/supply-chain/vulnerability-review.json').read_text());binaries=json.loads((root/'.cache/supply-chain/binary-runtime.json').read_text())
commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip()
if report.get('source_commit')!=commit or not report.get('native_graphs_included') or not report.get('binary_runtime_verified') or len(binaries)!=6:raise SystemExit('Incomplete actual runtime review cannot become a test APK')
if run.returncode==2 and report.get('status')!='REVIEW_REQUIRED':raise SystemExit('Unexpected scanner failure')
count=sum(r.get('disposition')=='REVIEW_REQUIRED' for r in report['findings'])
print('::warning title=MANUAL TEST ONLY::Unresolved advisory coordinates='+str(count)+'; full report retained. NOT security-approved, NOT stable/latest. Owner requested experimental installation.')
