"""Deterministic inventory of the exact review source tree; never a release verdict."""
import hashlib,json,pathlib

def collect(root):
 root=pathlib.Path(root)
 if root.is_symlink() or not root.is_dir():raise ValueError('Expected a real source directory')
 rows=[];total=0
 for path in sorted(root.rglob('*')):
  if path.is_symlink():raise ValueError('Symlinks are not accepted in the source provenance tree')
  if path.is_dir():continue
  if not path.is_file():raise ValueError('Non-regular source entry')
  size=path.stat().st_size;total+=size
  if len(rows)>=50000 or total>1024**3:raise ValueError('Source manifest budget exceeded')
  with path.open('rb') as f:digest=hashlib.file_digest(f,'sha256').hexdigest()
  rows.append({'path':path.relative_to(root).as_posix(),'bytes':size,'sha256':digest})
 if not rows:raise ValueError('Empty source tree')
 return {'schema':1,'files':rows,'total_bytes':total,'file_count':len(rows)}

def canonical(report):return (json.dumps(report,sort_keys=True,separators=(',',':'))+'\n').encode()
