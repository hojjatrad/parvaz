import importlib.util,json,pathlib,unittest
spec=importlib.util.spec_from_file_location('inspect_native_symbols',pathlib.Path(__file__).with_name('inspect_native_symbols.py'));inspection=importlib.util.module_from_spec(spec);spec.loader.exec_module(inspection)
class InspectionPrecisionTest(unittest.TestCase):
 def blob(self,body=None,header=None):
  return json.dumps(header or {'name':'govulncheck-extract','version':'0.1.0'})+'\n'+json.dumps(body if body is not None else {'goos':'android','goarch':'arm64','goVersion':'go1.27.1'})
 def test_missing_symbols_is_module_fallback_not_clean(self):
  r=inspection.extraction_precision(0,self.blob());self.assertEqual(r['recovered_symbol_count'],0);self.assertEqual(r['precision'],'MODULE_LEVEL_CONSERVATIVE_FALLBACK')
 def test_empty_symbols_is_module_fallback(self):
  self.assertEqual(inspection.extraction_precision(0,self.blob({'goos':'android','goarch':'arm','goVersion':'go1.26.7','pkgSymbols':[]}))['precision'],'MODULE_LEVEL_CONSERVATIVE_FALLBACK')
 def test_nonempty_symbols_never_claims_call_graph_or_release_approval(self):
  r=inspection.extraction_precision(0,self.blob({'goos':'android','goarch':'arm64','goVersion':'go1.27.1','pkgSymbols':[{'Pkg':'a','Name':'f'}]}));self.assertEqual(r['precision'],'RECOVERED_SYMBOLS_NOT_CALL_GRAPH');self.assertNotIn('release_status',r)
 def test_bad_protocol_nonzero_exit_stderr_and_truncation_are_unknown(self):
  for rc,text,stderr in [(1,self.blob(),''),(0,self.blob(),'unexpected warning'),(0,'{',''),(0,self.blob(header={'name':'other','version':'0.1.0'}),''),(0,self.blob()+'{}','')]:
   self.assertEqual(inspection.extraction_precision(rc,text,stderr)['status'],'UNKNOWN')
 def test_invalid_symbols_never_claims_recovery(self):
  for symbols in [None,'fake symbols',1,[1]]:
   self.assertEqual(inspection.extraction_precision(0,self.blob({'goos':'android','goarch':'arm64','goVersion':'go1.27.1','pkgSymbols':symbols}))['status'],'UNKNOWN')
 def test_missing_target_metadata_is_unknown(self):
  self.assertEqual(inspection.extraction_precision(0,self.blob({}))['status'],'UNKNOWN')
if __name__=='__main__':unittest.main()
