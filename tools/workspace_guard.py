#!/usr/bin/env python3
"""Run between build operations, not during builds. No background monitoring.
Default: report only. --clean removes allowlisted, untracked generated directories
ONLY when usage >=80% or free space <3 GiB. Never removes Git history, releases,
source, native dependencies, signing keys or unpushed commits.
"""
import argparse
import shutil
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CANDIDATES = ('.cache/audit', 'app/build', 'build')
GIB = 1024 ** 3

def low_space():
    total, used, free = shutil.disk_usage(ROOT)
    print(f'Disk: used={used/total:.1%}; free={free/GIB:.2f} GiB')
    return used / total >= .8 or free < 3 * GIB

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--clean', action='store_true')
    args = parser.parse_args()
    if not low_space():
        print('Space sufficient; nothing deleted.')
        return
    candidates = [ROOT / name for name in CANDIDATES if (ROOT / name).exists()]
    for path in sorted(candidates, key=lambda p: p.lstat().st_mtime):
        if path.is_symlink() or not path.is_dir() or any(p.is_symlink() for p in path.parents if p != ROOT.parent):
            print(f'SKIP unsafe path: {path}')
            continue
        tracked = subprocess.check_output(['git', '-C', str(ROOT), 'ls-files', '--', str(path.relative_to(ROOT))])
        if tracked.strip():
            print(f'SKIP contains tracked files: {path}')
            continue
        print(('DELETE: ' if args.clean else 'WOULD DELETE with --clean: ') + str(path))
        if args.clean:
            shutil.rmtree(path)
            if not low_space():
                return
    print('No additional authorized files to delete; stop large downloads/builds if space is still low.')

if __name__ == '__main__':
    main()
