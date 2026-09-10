#!/usr/bin/env python3
"""Sign tested unsigned APKs in an isolated job. Never execute candidate engines,
Gradle scripts or code from build artifacts while signing secrets are present."""
import os,subprocess,tempfile,shutil
from pathlib import Path
from build_signed import inputs,verify

def main():
 env=os.environ.copy();raw,pin=inputs(env)
 signer=Path(env['ANDROID_HOME'])/'build-tools/34.0.0/apksigner'
 output=Path('app/build/outputs/apk/release');output.mkdir(parents=True,exist_ok=True)
 with tempfile.TemporaryDirectory(prefix='parvaz-prebuilt-sign-',dir=env.get('RUNNER_TEMP')) as directory:
  key=Path(directory)/'release.p12';verify(raw,pin,env,key)
  for abi in ['arm64-v8a','universal']:
   source=Path('.cache/core-candidate')/('app-'+abi+'-release-unsigned.apk')
   if not source.is_file() or not 0<source.stat().st_size<=128*1024*1024:raise ValueError('Invalid unsigned APK size')
   destination=output/('app-'+abi+'-release.apk')
   args=[str(signer),'sign','--ks',str(key),'--ks-key-alias',env['PARVAZ_KEY_ALIAS'],'--ks-pass','env:PARVAZ_KEYSTORE_PASSWORD','--key-pass','env:PARVAZ_KEY_PASSWORD','--out',str(destination),str(source)]
   result=subprocess.run(args,env=env,capture_output=True,timeout=120)
   if result.returncode:raise RuntimeError('PREBUILT_SIGNING_FAILED') # never disclose secret-bearing tool output
 print('PREBUILT_SIGNED: unchanged permanent identity; archive checks follow')
if __name__=='__main__':main()
