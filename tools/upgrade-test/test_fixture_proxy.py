"""Transport-only tests with dummy bytes. NOT Android installation/signature proof."""
import hashlib,http.client,json,pathlib,socket,ssl,subprocess,sys,tempfile,time,unittest
class FixtureTransportTest(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.temp=tempfile.TemporaryDirectory();root=pathlib.Path(cls.temp.name);cls.root=root
  cls.payload=b'not-an-apk; transport fixture only'*1024
  for name in ['Parvaz-1.28.6.apk','Parvaz-1.28.6-arm64.apk']:(root/name).write_bytes(cls.payload)
  subprocess.run(['openssl','req','-x509','-newkey','rsa:2048','-nodes','-days','1','-keyout',str(root/'key.pem'),'-out',str(root/'cert.pem'),'-subj','/CN=fixture','-addext','subjectAltName=DNS:api.github.com,DNS:github.com'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,check=True)
  cls.context=ssl.create_default_context(cafile=str(root/'cert.pem'))
  with socket.socket() as s:s.bind(('127.0.0.1',0));cls.port=s.getsockname()[1]
  cls.proc=subprocess.Popen([sys.executable,str(pathlib.Path(__file__).with_name('fixture_proxy.py')),'--artifacts',str(root),'--version','1.28.6','--cert',str(root/'cert.pem'),'--key',str(root/'key.pem'),'--port',str(cls.port)],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
  for _ in range(50):
   try:
    with socket.create_connection(('127.0.0.1',cls.port),timeout=.2):return
   except OSError:time.sleep(.05)
  raise AssertionError('Fixture proxy startup')
 @classmethod
 def tearDownClass(cls):cls.proc.terminate();cls.proc.wait(timeout=5);cls.temp.cleanup()
 def get(self,host,path,headers=None):
  c=http.client.HTTPSConnection('127.0.0.1',self.port,context=self.context,timeout=5);c.set_tunnel(host,443)
  c.request('GET',path,headers={'Host':host,**(headers or {})});r=c.getresponse();result=r.status,dict(r.headers),r.read();c.close();return result
 def test_01_staging_is_not_enabled_by_background_checks(self):
  status,_,body=self.get('api.github.com','/repos/hojjatrad/parvaz/releases/latest');self.assertEqual(status,200);self.assertEqual(json.loads(body)['tag_name'],'v1.28.5')
 def test_02_explicit_control_activates_exact_candidate_hashes(self):
  c=http.client.HTTPConnection('127.0.0.1',8766,timeout=5);c.request('POST','/enable');r=c.getresponse();self.assertEqual(r.status,204);r.read();c.close()
  status,_,body=self.get('api.github.com','/repos/hojjatrad/parvaz/releases/latest');data=json.loads(body);self.assertEqual(status,200);self.assertEqual(data['tag_name'],'v1.28.6');self.assertEqual(len(data['assets']),2)
  self.assertTrue(all(a['digest']=='sha256:'+hashlib.sha256(self.payload).hexdigest() for a in data['assets']))
 def test_03_unchanged_bytes_and_resumable_range(self):
  path='/hojjatrad/parvaz/releases/download/v1.28.6/Parvaz-1.28.6-arm64.apk'
  status,_,body=self.get('github.com',path);self.assertEqual((status,body),(200,self.payload))
  status,headers,body=self.get('github.com',path,{'Range':'bytes=12-'});self.assertEqual((status,body),(206,self.payload[12:]));self.assertEqual(headers['Content-Range'],f'bytes 12-{len(self.payload)-1}/{len(self.payload)}')
  self.assertEqual(self.get('github.com',path,{'Range':'bytes=9999999-'})[0],416)
 def test_04_wrong_host_path_and_arbitrary_forwarding_rejected(self):
  self.assertEqual(self.get('github.com','/repos/hojjatrad/parvaz/releases/latest')[0],404)
  self.assertEqual(self.get('api.github.com','/repos/attacker/parvaz/releases/latest')[0],404)
  self.assertEqual(self.get('github.com','/hojjatrad/parvaz/releases/download/v1.28.6/../key.pem')[0],404)
  with self.assertRaises(OSError):self.get('example.com','/')
if __name__=='__main__':unittest.main()
