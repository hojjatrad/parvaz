"""This opt-in testing path cannot be used by the stable publication workflow."""
import json,os,pathlib,re
root=pathlib.Path(__file__).resolve().parents[2]
if os.environ.get('GITHUB_REF')!='refs/heads/qa/install-preview':raise SystemExit('Manual testing branch required')
config=(root/'app/build.gradle').read_text()
if re.search(r'versionName\s+"([^"]+)"',config)[1]!='1.28.6' or re.search(r'versionCode\s+(\d+)',config)[1]!='40':raise SystemExit('Review a new preview version explicitly')
for language,label in [('values','Parvaz (TEST)'),('values-fa','پرواز (آزمایشی)')]:
 if '<string name="app_name">'+label+'</string>' not in (root/'app/src/main/res'/language/'strings.xml').read_text():raise SystemExit('Visible TEST label required')
print('MANUAL_TEST_ONLY; NOT_SECURITY_APPROVED; NOT_LATEST; FINAL_MUST_EXCEED_1.28.6_CODE40')
