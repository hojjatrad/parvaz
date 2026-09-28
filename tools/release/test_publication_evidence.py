import hashlib,importlib.util,json,pathlib,tempfile,unittest
spec=importlib.util.spec_from_file_location('publication',pathlib.Path(__file__).with_name('verify_publication_evidence.py'));gate=importlib.util.module_from_spec(spec);spec.loader.exec_module(gate)
class PublicationEvidenceTest(unittest.TestCase):
 def setUp(self):
  self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup);self.root=pathlib.Path(self.temp.name)
  for p in ['.cache/upgrade-validated','.cache/supply-chain','release-artifacts']:(self.root/p).mkdir(parents=True)
  self.proof={'source_commit':'commit','tests':[{'tests':1,'failures':0,'errors':0,'skipped':0}],'candidate_apk_sha256':{}}
  for name in ['Parvaz-1.28.6-arm64.apk','Parvaz-1.28.6.apk']:
   (self.root/'release-artifacts'/name).write_bytes(b'test-only, not an APK');self.proof['candidate_apk_sha256'][name]=hashlib.sha256(b'test-only, not an APK').hexdigest()
  self.review={'source_commit':'commit','native_graphs_included':True,'status':'NO_KNOWN_MATCHES_IN_SCANNED_SCOPE'}
  (self.root/'.cache/supply-chain/sbom.cdx.json').write_text('{}');self.save()
 def save(self):
  (self.root/'.cache/upgrade-validated/summary.json').write_text(json.dumps(self.proof));(self.root/'.cache/supply-chain/vulnerability-review.json').write_text(json.dumps(self.review))
 def test_matching_bytes_and_proof_pass_identity_gate(self):
  gate.verify(self.root,'1.28.6','commit');self.assertTrue((self.root/'app/build/outputs/apk/release/app-arm64-v8a-release.apk').is_file())
 def test_rebuilt_or_mutated_apk_rejected(self):
  (self.root/'release-artifacts/Parvaz-1.28.6-arm64.apk').write_bytes(b'changed')
  with self.assertRaises(SystemExit):gate.verify(self.root,'1.28.6','commit')
 def test_other_source_commit_rejected(self):
  with self.assertRaises(SystemExit):gate.verify(self.root,'1.28.6','different')
 def test_java_only_or_unresolved_review_rejected(self):
  for change in [{'native_graphs_included':False},{'status':'REVIEW_REQUIRED'}]:
   self.review.update(change);self.save()
   with self.assertRaises(SystemExit):gate.verify(self.root,'1.28.6','commit')
 def test_nonapplicability_without_complete_binary_evidence_rejected(self):
  self.review['status']='NO_APPLICABLE_MATCHES_IN_BUILT_RUNTIME';self.save()
  with self.assertRaises(SystemExit):gate.verify(self.root,'1.28.6','commit')
  self.review.update(binary_runtime_verified=True,findings=[{'disposition':'REVIEW_REQUIRED'}]);self.save()
  with self.assertRaises(SystemExit):gate.verify(self.root,'1.28.6','commit')
 def test_missing_or_skipped_upgrade_rejected(self):
  for tests in [[],[{'tests':1,'failures':0,'errors':0,'skipped':1}]]:
   self.proof['tests']=tests;self.save()
   with self.assertRaises(SystemExit):gate.verify(self.root,'1.28.6','commit')

 def _compiled_graph_finding(self,advisory='NOT_IN_VERIFIED_COMPILED_PACKAGE_GRAPH',coverage=True,goos='android'):
  return {'disposition':'NOT_APPLICABLE_TO_VERIFIED_BINARY_SCOPE','advisory_dispositions':{'GO-0000-0001':advisory},
          'component':{'ecosystem':'Go','sources':[{'binary':'libgojni.so','goos':goos,'package_coverage_verified':coverage}]}}
 def test_compiled_package_graph_exclusion_accepted(self):
  self.review.update(status='NO_APPLICABLE_MATCHES_IN_BUILT_RUNTIME',binary_runtime_verified=True,findings=[self._compiled_graph_finding()]);self.save()
  gate.verify(self.root,'1.28.6','commit')
 def test_review_required_advisory_inside_excluded_row_rejected(self):
  self.review.update(status='NO_APPLICABLE_MATCHES_IN_BUILT_RUNTIME',binary_runtime_verified=True,
                     findings=[self._compiled_graph_finding(advisory='REVIEW_REQUIRED')]);self.save()
  with self.assertRaises(SystemExit):gate.verify(self.root,'1.28.6','commit')
 def test_exclusion_without_verified_android_binary_coverage_rejected(self):
  for finding in [self._compiled_graph_finding(coverage=False),self._compiled_graph_finding(goos='linux'),
                  {'disposition':'NOT_APPLICABLE_TO_VERIFIED_BINARY_SCOPE','advisory_dispositions':{},'component':{'sources':[]}}]:
   self.review.update(status='NO_APPLICABLE_MATCHES_IN_BUILT_RUNTIME',binary_runtime_verified=True,findings=[finding]);self.save()
   with self.assertRaises(SystemExit):gate.verify(self.root,'1.28.6','commit')
if __name__=='__main__':unittest.main()
