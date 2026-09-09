import unittest
from check_release_order import validate_order,version
class ReleaseOrderTests(unittest.TestCase):
    def test_normal_upgrade(self):validate_order({'version':'1.21','code':22,'core':'v26.9.9'},{'version':'1.20','code':21})
    def test_patch_upgrade(self):validate_order({'version':'1.21.1','code':23,'core':'v26.9.10'},{'version':'1.21','code':22,'core':'v26.9.9'})
    def test_concurrent_newer_manual_release_blocks_old_bot_candidate(self):
        with self.assertRaises(ValueError):validate_order({'version':'1.21.1','code':23,'core':'v26.9.10'},{'version':'1.22','code':23,'core':'v26.9.9'})
    def test_code_must_increase(self):
        with self.assertRaises(ValueError):validate_order({'version':'1.22','code':22,'core':'v26.9.10'},{'version':'1.21','code':22,'core':'v26.9.9'})
    def test_no_core_downgrade(self):
        with self.assertRaises(ValueError):validate_order({'version':'1.22','code':24,'core':'v26.9.9'},{'version':'1.21.1','code':23,'core':'v26.9.10'})
    def test_normalized_version_comparison(self):self.assertEqual(version('v1.21'),version('1.21.0'))
