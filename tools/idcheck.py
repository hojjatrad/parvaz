#!/usr/bin/env python3
"""Cross-check findViewById(R.id.X) calls in a source tree against the IDs that
actually exist in the built APK's layouts. A missing/never-inflated id is the
classic cause of an NPE on launch."""
import os, re, sys, zipfile
sys.path.insert(0, '/home/user/tools')
from axml import decode

apk = sys.argv[1]
src = sys.argv[2]
resdump = sys.argv[3]   # output of `aapt2 dump resources apk`

# id name -> numeric
id_name = {}
cur_type = None
for line in open(resdump, errors='replace'):
    m = re.match(r'\s+resource (0x[0-9a-f]+) (\w+)/([\w.]+)', line)
    if m:
        id_name.setdefault(m.group(2), {})[m.group(3)] = int(m.group(1), 16)

ids = id_name.get('id', {})
layouts = id_name.get('layout', {})

# which file backs each layout
layout_file = {}
cur = None
for line in open(resdump, errors='replace'):
    m = re.match(r'\s+resource (0x[0-9a-f]+) layout/([\w.]+)', line)
    if m:
        cur = m.group(2); continue
    if cur:
        m2 = re.search(r'\(file\) (res/\S+) type=XML', line)
        if m2:
            layout_file[cur] = m2.group(1)
        cur = None

z = zipfile.ZipFile(apk)
# numeric ids present in each compiled layout
inflated = {}
for name, path in layout_file.items():
    try:
        x = decode(z.read(path))
    except Exception as e:
        continue
    found = set()
    for m in re.finditer(r'android:id="@(0x[0-9a-f]+)"', x):
        found.add(int(m.group(1), 16))
    # <include layout="@0x..."> pulls in another layout
    inflated[name] = found

num_to_layout = {}
for lname, s in inflated.items():
    for n in s:
        num_to_layout.setdefault(n, []).append(lname)

rev_id = {v: k for k, v in ids.items()}

problems = []
for root, dirs, files in os.walk(src):
    for f in files:
        if not f.endswith('.java'): continue
        p = os.path.join(root, f)
        txt = open(p, errors='replace').read()
        for m in re.finditer(r'findViewById\(\s*R\.id\.(\w+)\s*\)', txt):
            nm = m.group(1)
            if nm not in ids:
                problems.append(('NO_SUCH_ID', p, nm))
                continue
            num = ids[nm]
            if num not in num_to_layout:
                problems.append(('ID_IN_NO_LAYOUT', p, nm))

print("ids declared      :", len(ids))
print("layouts inspected :", len(inflated))
print("PROBLEMS          :", len(problems))
seen = set()
for kind, p, nm in problems:
    k = (kind, nm)
    if k in seen: continue
    seen.add(k)
    print("  %-16s %-28s %s" % (kind, nm, p.split('/')[-1]))
