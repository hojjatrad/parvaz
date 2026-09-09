#!/usr/bin/env python3
"""Real HY2/TUIC TCP+UDP exchanges through loopback servers with verified TLS.
All credentials/certificates are ephemeral fixtures. This is not a private-panel or Android VPN test."""
import contextlib,http.server,json,os,socket,subprocess,tempfile,threading,time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];ENGINE=ROOT/'.cache/native/host/sing-box'
CP='.cache/audit/classes:.cache/audit/json.jar:.cache/audit/snakeyaml.jar'
subprocess.run(['javac','-cp',CP,'-d','.cache/audit/classes','tools/native/FixtureConfig.java'],cwd=ROOT,check=True)
def port(kind=socket.SOCK_STREAM):
 with socket.socket(type=kind) as s:s.bind(('127.0.0.1',0));return s.getsockname()[1]
def exact(s,n):
 data=b''
 while len(data)<n:
  chunk=s.recv(n-len(data))
  if not chunk:raise OSError('Early EOF')
  data+=chunk
 return data
def auth(p):
 s=socket.create_connection(('127.0.0.1',p),timeout=3);s.settimeout(5);s.sendall(b'\5\1\2');assert exact(s,2)==b'\5\2';u=b'test';pw=b'integration-local-password';s.sendall(b'\1'+bytes([len(u)])+u+bytes([len(pw)])+pw);assert exact(s,2)==b'\1\0';return s
def address(s):
 head=exact(s,4);assert head[:2]==b'\5\0',head
 n={1:4,4:16}.get(head[3]);host=exact(s,n if n else exact(s,1)[0]);p=int.from_bytes(exact(s,2),'big');return p
class Echo(http.server.BaseHTTPRequestHandler):
 def do_GET(self):
  self.send_response(200);self.end_headers();self.wfile.write(b'PARVAZ-TUNNELED-TCP-OK')
 def log_message(self,*args):pass
server=http.server.ThreadingHTTPServer(('127.0.0.1',0),Echo);threading.Thread(target=server.serve_forever,daemon=True).start()
udp=socket.socket(type=socket.SOCK_DGRAM);udp.bind(('127.0.0.1',0));udp.settimeout(.2);stop=threading.Event()
def echo_udp():
 while not stop.is_set():
  try:data,addr=udp.recvfrom(4096);udp.sendto(data,addr)
  except socket.timeout:pass
threading.Thread(target=echo_udp,daemon=True).start()
try:
 with tempfile.TemporaryDirectory() as temp:
  temp=Path(temp);cert=temp/'cert.pem';key=temp/'key.pem'
  subprocess.run(['openssl','req','-x509','-newkey','rsa:2048','-nodes','-keyout',str(key),'-out',str(cert),'-days','1','-subj','/CN=localhost','-addext','subjectAltName=DNS:localhost,IP:127.0.0.1'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,check=True)
  for protocol in ['hysteria2','tuic']:
   remote,local=port(socket.SOCK_DGRAM),port();user={'password':'integration-only-secret'}
   if protocol=='tuic':user['uuid']='11111111-1111-4111-8111-111111111111'
   conf={'log':{'level':'error'},'inbounds':[{'type':protocol,'listen':'127.0.0.1','listen_port':remote,'users':[user],'tls':{'enabled':True,'alpn':['h3'],'certificate_path':str(cert),'key_path':str(key)}}],'outbounds':[{'type':'direct'}]}
   client=json.loads(subprocess.check_output(['java','-cp',CP,'com.parvaz.tunnel.config.FixtureConfig',protocol,str(remote),str(local),str(cert)],cwd=ROOT,text=True))
   processes=[]
   try:
    for name,config in [('server',conf),('client',client)]:
     file=temp/(protocol+'-'+name+'.json');file.write_text(json.dumps(config));processes.append(subprocess.Popen([str(ENGINE),'run','-c',str(file)],stdout=subprocess.DEVNULL,stderr=subprocess.PIPE))
    deadline=time.monotonic()+15
    while True:
     if any(p.poll() is not None for p in processes):raise RuntimeError('Fixture core exited: '+str([p.stderr.read().decode() for p in processes if p.poll() is not None]))
     try:s=auth(local);s.close();break
     except OSError:
      if time.monotonic()>deadline:raise
      time.sleep(.1)
    with auth(local) as s:
     s.sendall(b'\5\1\0\1'+socket.inet_aton('127.0.0.1')+server.server_port.to_bytes(2,'big'));address(s);s.sendall(b'GET / HTTP/1.0\r\nHost: localhost\r\n\r\n');response=b''
     while True:
      chunk=s.recv(4096)
      if not chunk:break
      response+=chunk
     assert b'PARVAZ-TUNNELED-TCP-OK' in response
    with auth(local) as control,socket.socket(type=socket.SOCK_DGRAM) as datagram:
     control.sendall(b'\5\3\0\1\0\0\0\0\0\0');relay=address(control);datagram.bind(('127.0.0.1',0));datagram.settimeout(5)
     frame=b'\0\0\0\1'+socket.inet_aton('127.0.0.1')+udp.getsockname()[1].to_bytes(2,'big')+b'PARVAZ-TUNNELED-UDP-OK';datagram.sendto(frame,('127.0.0.1',relay));assert datagram.recv(4096).endswith(b'PARVAZ-TUNNELED-UDP-OK')
    processes[0].terminate();processes[0].wait(timeout=5)
    with auth(local) as s:
     s.sendall(b'\5\1\0\1'+socket.inet_aton('127.0.0.1')+server.server_port.to_bytes(2,'big'))
     try:reply=exact(s,2);assert reply!=b'\5\0','Unexpected direct fallback'
     except (OSError,TimeoutError):pass
    print('::notice title=PROTOCOL_SMOKE_OK::'+protocol+' verified TLS + TCP + UDP + no direct fallback',flush=True)
   finally:
    for p in processes:
     p.kill() if p.poll() is None else None
     p.wait(timeout=5)
finally:stop.set();server.shutdown();udp.close()
