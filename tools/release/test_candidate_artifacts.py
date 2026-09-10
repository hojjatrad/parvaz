import unittest,tempfile,subprocess,sys
from pathlib import Path
from unittest.mock import patch
import core_candidate_artifacts as artifacts
class CandidateArtifacts(unittest.TestCase):
 def run_case(self,tamper):
  with tempfile.TemporaryDirectory() as directory:
   root=Path(directory);version='1.27.0'
   for name in artifacts.metadata(version):
    p=root/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text('fixture '+name)
   subprocess.run(['git','init','-q',str(root)],check=True)
   subprocess.run(['git','add','.'],cwd=root,check=True)
   subprocess.run(['git','-c','user.name=Fixture','-c','user.email=fixture@example.invalid','commit','-qm','fixture'],cwd=root,check=True)
   if tamper=='regenerate':
    (root/'app/build.gradle').write_text('new candidate metadata')
    subprocess.run(['git','add','.'],cwd=root,check=True)
    subprocess.run(['git','-c','user.name=Fixture','-c','user.email=fixture@example.invalid','commit','-qm','prepared candidate'],cwd=root,check=True)
   for abi in ['arm64-v8a','universal']:
    p=root/'app/build/outputs/apk/release'/('app-'+abi+'-release-unsigned.apk');p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(b'fixture unsigned '+abi.encode())
   p=root/'release-artifacts'/('Parvaz-'+version+'-source.tar.gz');p.parent.mkdir();p.write_bytes(b'fixture source')
   with patch.object(artifacts,'ROOT',root),patch.object(artifacts,'ART',root/'.cache/core-candidate'):
    with patch.object(sys,'argv',['tool','pack',version]):artifacts.main()
    if tamper=='regenerate':
     subprocess.run(['git','checkout','-q','HEAD~1'],cwd=root,check=True)
     (root/'app/build.gradle').write_text('new candidate metadata')
    if tamper=='apk':(artifacts.ART/'app-universal-release-unsigned.apk').write_bytes(b'changed')
    if tamper=='metadata':(root/'app/build.gradle').write_text('changed')
    with patch.object(sys,'argv',['tool','verify',version]):
     if tamper and tamper!='regenerate':
      with self.assertRaises(ValueError):artifacts.main()
     else:artifacts.main()
 def test_separate_signer_regenerates_same_candidate_tree(self):self.run_case('regenerate')
 def test_matching_prepared_tree_and_tested_bytes(self):self.run_case(None)
 def test_artifact_tamper_is_refused(self):self.run_case('apk')
 def test_regenerated_candidate_mismatch_is_refused(self):self.run_case('metadata')
 def test_version_cannot_escape_directory(self):
  with self.assertRaises(ValueError):artifacts.metadata('../../private')
