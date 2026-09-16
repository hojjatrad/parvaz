import importlib.util,pathlib,unittest
spec=importlib.util.spec_from_file_location('order',pathlib.Path(__file__).with_name('check_release_order.py'));order=importlib.util.module_from_spec(spec);spec.loader.exec_module(order)
class PreviewReservationTest(unittest.TestCase):
 def test_same_installed_code_or_name_must_not_become_final(self):
  for candidate in [{'code':40,'version':'1.28.7'},{'code':41,'version':'1.28.6'},{'code':40,'version':'1.28.6'}]:
   with self.assertRaises(ValueError):order.validate_preview_reservation(candidate,{'version':'1.28.6','version_code':40})
 def test_higher_code_and_version_is_upgradeable(self):
  order.validate_preview_reservation({'code':41,'version':'1.28.7'},{'version':'1.28.6','version_code':40})
if __name__=='__main__':unittest.main()
