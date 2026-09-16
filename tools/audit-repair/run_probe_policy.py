#!/usr/bin/env python3
"""Execute actual VerifiedProbe with a deliberately fake HTTPS transport.
Policy evidence only: not a TLS, proxy, phone, or engine connectivity test.
"""
import pathlib,subprocess
root=pathlib.Path(__file__).resolve().parents[2];out=root/'.cache/repair/probe-policy';out.mkdir(parents=True,exist_ok=True)
(out/'HttpsLatency.java').write_text('''package com.parvaz.tunnel.core;
final class HttpsLatency {static final long BUSY=-5;static final java.util.List<String> seen=new java.util.ArrayList<>();
static final class Session implements AutoCloseable{Session(int port){}long measure(String endpoint,int samples){seen.add(endpoint);return endpoint.contains("youtube")?-9:37;}public void close(){}}}
''')
(out/'ReadinessMonitor.java').write_text('''package com.parvaz.tunnel.core;
final class ReadinessMonitor{static String[] endpoints(String first){return new String[]{first,"https://cp.cloudflare.com/generate_204"};}}
''')
(out/'ProbePolicy.java').write_text('''package com.parvaz.tunnel.core;
public class ProbePolicy{public static void main(String[]args)throws Exception{String target="https://www.youtube.com/generate_204";
long strict=VerifiedProbe.measureDetailed(10080,target,true,true);if(strict!=-9||HttpsLatency.seen.size()!=1||VerifiedProbe.lastTarget()!=null)throw new AssertionError("target fallback must fail closed");
HttpsLatency.seen.clear();long normal=VerifiedProbe.measureDetailed(10080,target,true,false);if(normal!=37||HttpsLatency.seen.size()!=2||!"Cloudflare".equals(VerifiedProbe.lastTarget()))throw new AssertionError("general readiness fallback preserved");
System.out.println("PROBE_POLICY_OK strict target rejects alternate-site success; normal readiness fallback still works (6 assertions; fake transport)");}}
''')
source=root/'app/src/main/java/com/parvaz/tunnel/core/VerifiedProbe.java'
subprocess.run(['javac','-d',str(out),str(source),*[str(out/n) for n in ['HttpsLatency.java','ReadinessMonitor.java','ProbePolicy.java']]],check=True)
subprocess.run(['java','-cp',str(out),'com.parvaz.tunnel.core.ProbePolicy'],check=True)
