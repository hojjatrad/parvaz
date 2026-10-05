"""Only immutable published APKs may seed the disposable upgrade fixture."""
import json,pathlib,sys
PRIORS = {
    'stable39': {'version_code':39,'url':'https://github.com/hojjatrad/parvaz/releases/download/v1.28.5/Parvaz-1.28.5-arm64.apk','sha256':'71d99e7ab114639e85e5e2db21494ff34fa95fd4cc91719dac4b6baf11776205'},
    'stable43': {'version_code':43,'url':'https://github.com/hojjatrad/parvaz/releases/download/v1.28.9/Parvaz-1.28.9-arm64.apk','sha256':'5e930fe7030ebbca5c2431a7cc10bdc9b668caf50b5c720e0404dd955c644283'},
    'stable44': {'version_code':44,'url':'https://github.com/hojjatrad/parvaz/releases/download/v1.29.0/Parvaz-1.29.0-arm64.apk','sha256':'da7f546421c631bb27f6e9a1fddff721cc66bafac1c0912a7e1e67b8729e0569'},
    'stable45': {'version_code':45,'url':'https://github.com/hojjatrad/parvaz/releases/download/v1.30.0/Parvaz-1.30.0-arm64.apk','sha256':'63661ee06a50039f79f81f7958dc2f9754686e2be47a126d6c91aa985e61536a'},
    'stable46': {'version_code':46,'url':'https://github.com/hojjatrad/parvaz/releases/download/v1.30.1/Parvaz-1.30.1-arm64.apk','sha256':'ca6483fec5f13387a9791e82dbac9fbde86bf6c644ec7f23746d6eaf2882420a'},
    'stable47': {'version_code':47,'url':'https://github.com/hojjatrad/parvaz/releases/download/v1.31.0/Parvaz-1.31.0-arm64.apk','sha256':'98158ab77518cf284a86af7e3bbe7e81eba6b78504a3c1a7caf037b7ab7cc4cc'},
    'stable48': {'version_code':48,'url':'https://github.com/hojjatrad/parvaz/releases/download/v1.32.0/Parvaz-1.32.0-arm64.apk','sha256':'7521d00685241091eefdaa12295d54af947381a11b142320cc1a6e752aacf2cd'},
    'stable49': {'version_code':49,'url':'https://github.com/hojjatrad/parvaz/releases/download/v1.33.0/Parvaz-1.33.0-arm64.apk','sha256':'ada59b3fabc06eea027f9603db9b4ac8f71385a9fb96c68475777c98abf033a4'},
    'test42': {'version_code':42,'url':'https://github.com/hojjatrad/parvaz/releases/download/test/v1.28.8-r1/Parvaz-1.28.8-arm64.apk','sha256':'32a58e86ce0a260f7565161e8e65642f726ce4f214cf2f93ab39c5dde885ed3b'},
    'test41': {'version_code':41,'url':'https://github.com/hojjatrad/parvaz/releases/download/test/v1.28.7-r1/Parvaz-1.28.7-TEST-arm64.apk','sha256':'cdeced0b64a060dffcffa7e081d6eff487c3504fae8bbdce02afc54f73d120b0'},
}
def select(name):
    if name not in PRIORS:raise ValueError('Unknown prior APK; arbitrary devices/downloads are not permitted')
    return dict(PRIORS[name],fixture=name)
if __name__=='__main__':
    if len(sys.argv)!=3:raise SystemExit('Expected reviewed fixture name and evidence output')
    pathlib.Path(sys.argv[2]).write_text(json.dumps(select(sys.argv[1]),indent=2)+'\n')
