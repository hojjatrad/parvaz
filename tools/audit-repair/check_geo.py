#!/usr/bin/env python3
"""Reproduce structural validation of bundled and pinned official Geo fixtures.
Desktop parser evidence only; not Android startup/download/power-loss testing.
"""
import hashlib,json,pathlib,subprocess,tempfile,urllib.request
root=pathlib.Path(__file__).resolve().parents[2]
fixture=json.loads((root/'docs/repairs/geo-fixture.json').read_text())
cache=root/'.cache/repair';cache.mkdir(parents=True,exist_ok=True)
with tempfile.TemporaryDirectory(prefix='geo-validation-',dir=cache) as directory:
 work=pathlib.Path(directory);inputs=[root/'app/src/main/assets'/n for n in ('geoip.dat','geosite.dat')]
 for asset in fixture['assets']:
  output=work/asset['name'];digest=hashlib.sha256();size=0
  with urllib.request.urlopen(asset['browser_download_url'],timeout=45) as response,output.open('wb') as stream:
   while block:=response.read(65536):
    size+=len(block)
    if size>asset['size']:raise ValueError('Fixture size mismatch')
    digest.update(block);stream.write(block)
  assert size==asset['size'] and 'sha256:'+digest.hexdigest()==asset['digest']
  inputs.append(output)
 harness=work/'GeoCheck.java'
 harness.write_text('''import com.parvaz.tunnel.core.GeoData;import java.io.File;
public class GeoCheck{public static void main(String[]args)throws Exception{for(String path:args){boolean ip=path.endsWith("geoip.dat");java.util.Set<String> tags=GeoData.tags(new File(path),ip);if(!tags.contains(ip?"ir":"category-ir"))throw new AssertionError("Required tag absent");System.out.println(new File(path).getName()+": "+tags.size()+" validated tags");}}}
''')
 subprocess.run(['javac','-d',str(work),str(root/'app/src/main/java/com/parvaz/tunnel/core/GeoData.java'),str(harness)],check=True)
 subprocess.run(['java','-cp',str(work),'GeoCheck',*map(str,inputs)],check=True)
print('GEO_FIXTURES_OK: bundled pair and digest-pinned official pair; desktop parser only')
