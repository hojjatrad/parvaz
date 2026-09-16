#!/usr/bin/env python3
"""CI-only HTTPS release fixture. Never shipped in an APK.
A disposable CA trusted only by a disposable emulator serves exact GitHub host
names and unchanged, permanently signed candidate APK bytes before publication.
TLS hostname verification and the APK signature/hash checks remain enabled.
Only public package metadata is handled; no general forward proxy is provided.
"""
import argparse,hashlib,http.server,json,pathlib,re,ssl,threading,urllib.parse
p=argparse.ArgumentParser();p.add_argument('--artifacts',required=True);p.add_argument('--cert',required=True);p.add_argument('--key',required=True);p.add_argument('--version',required=True);p.add_argument('--port',type=int,default=8765);a=p.parse_args()
root=pathlib.Path(a.artifacts);files={}
for f in root.glob('*.apk'):
 h=hashlib.sha256()
 with f.open('rb') as stream:
  while block:=stream.read(65536):h.update(block)
 files[f.name]=(f,f.stat().st_size,h.hexdigest())
if set(files)!={f'Parvaz-{a.version}.apk',f'Parvaz-{a.version}-arm64.apk'}:raise SystemExit('Exactly the two expected verified APK variants required')
base='https://github.com/hojjatrad/parvaz/releases/download/v'+a.version+'/'
metadata=json.dumps({'tag_name':'v'+a.version,'draft':False,'prerelease':False,'body':'CI-only staged release transport. Permanent signed bytes; not yet published.','assets':[{'name':n,'size':size,'digest':'sha256:'+digest,'browser_download_url':base+n} for n,(f,size,digest) in files.items()]}).encode()
enabled=threading.Event()
prior=json.dumps({'tag_name':'v1.28.5','draft':False,'prerelease':False,'assets':[{'name':'Parvaz-1.28.5-arm64.apk','size':45568570,'digest':'sha256:71d99e7ab114639e85e5e2db21494ff34fa95fd4cc91719dac4b6baf11776205','browser_download_url':'https://github.com/hojjatrad/parvaz/releases/download/v1.28.5/Parvaz-1.28.5-arm64.apk'}]}).encode()
class Control(http.server.BaseHTTPRequestHandler):
 def log_message(self,*args):pass
 def do_POST(self):
  if self.path!='/enable':self.send_error(404);return
  enabled.set();self.send_response(204);self.end_headers()
control=http.server.ThreadingHTTPServer(('0.0.0.0',8766),Control)
threading.Thread(target=control.serve_forever,daemon=True).start()
context=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER);context.minimum_version=ssl.TLSVersion.TLSv1_2;context.load_cert_chain(a.cert,a.key)
class API(http.server.BaseHTTPRequestHandler):
 protocol_version='HTTP/1.1'
 def log_message(self,*args):pass
 def do_GET(self):
  path=urllib.parse.urlsplit(self.path).path
  if self.headers.get('Host','').split(':')[0]=='api.github.com' and path=='/repos/hojjatrad/parvaz/releases/latest':
   body=metadata if enabled.is_set() else prior
   self.send_response(200);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body);print('STAGED_METADATA_REQUEST enabled='+str(enabled.is_set()),flush=True);return
  prefix='/hojjatrad/parvaz/releases/download/v'+a.version+'/'
  name=path[len(prefix):] if path.startswith(prefix) else ''
  if self.headers.get('Host','').split(':')[0]!='github.com' or name not in files:self.send_error(404);return
  f,size,digest=files[name];start=0
  if self.headers.get('Range'):
   m=re.fullmatch(r'bytes=(\d+)-',self.headers['Range'])
   if not m or int(m[1])>=size:self.send_error(416);return
   start=int(m[1])
  self.send_response(206 if start else 200);self.send_header('Content-Length',str(size-start));self.send_header('ETag','"'+digest+'"');self.send_header('Accept-Ranges','bytes')
  if start:self.send_header('Content-Range',f'bytes {start}-{size-1}/{size}')
  self.end_headers()
  with f.open('rb') as stream:
   stream.seek(start)
   while block:=stream.read(65536):self.wfile.write(block)
  print('SIGNED_CANDIDATE_TRANSFER_COMPLETE '+name,flush=True)
class Proxy(http.server.BaseHTTPRequestHandler):
 def log_message(self,*args):pass
 def do_CONNECT(self):
  if self.path not in ('api.github.com:443','github.com:443'):self.send_error(403);return
  self.send_response(200,'Connection established');self.end_headers()
  try:
   with context.wrap_socket(self.connection,server_side=True) as secure:API(secure,self.client_address,self.server)
  except (OSError,ssl.SSLError):pass
 def do_GET(self):self.send_error(403)
server=http.server.ThreadingHTTPServer(('0.0.0.0',a.port),Proxy);server.daemon_threads=True
print('STAGED_RELEASE_PROXY_READY',flush=True);server.serve_forever()
