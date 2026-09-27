"""Preserve full findings for the owner's explicitly requested experimental APK.
ONLY a completed known-advisory review may be accepted for manual testing.
Inventory, scan transport/parser errors and all stable-release gates remain fatal.
"""
import json,pathlib,subprocess,sys
root=pathlib.Path(__file__).resolve().parents[2]
subprocess.run([sys.executable,str(root/'tools/preview/guard.py')],check=True)
run=subprocess.run([sys.executable,str(root/'tools/security/dependency_audit.py'),'--native','--candidate-xray'],cwd=root,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
(root/'.cache/preview-scan.log').write_text(run.stdout)
if run.returncode not in (0,2):
 print(run.stdout[-4000:]);raise SystemExit(run.returncode)
report=json.loads((root/'.cache/supply-chain/vulnerability-review.json').read_text());binaries=json.loads((root/'.cache/supply-chain/binary-runtime.json').read_text())
commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip()
from security_boundary import verify
boundary=json.loads((root/'tools/preview/accepted-test-boundary.json').read_text())
try:count=verify(report,binaries,boundary,commit,run.returncode)
except (ValueError,KeyError) as e:raise SystemExit('TEST_SECURITY_BOUNDARY_FAILED: '+str(e))
print('::warning title=MANUAL TEST ONLY::Unresolved advisory coordinates='+str(count)+'; full report retained. NOT security-approved, NOT stable/latest. Owner requested experimental installation.')
