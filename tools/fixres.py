#!/usr/bin/env python3
"""Rename apktool-emitted resource files whose names aapt2 rejects.

apktool writes files like "$avd_hide_password__0.xml" for AnimatedVectorDrawable inner
resources. aapt2 refuses them: "'$' is not a valid file-based resource name character".
They are library internals we never reference, so the fix is to drop them and strip any
references from the values files.
"""
import os
import re
import sys

RES = "/opt/pb/work/app/src/main/res"


def main():
    removed = []
    for root, dirs, files in os.walk(RES):
        for name in files:
            if "$" in name:
                path = os.path.join(root, name)
                os.remove(path)
                # Resource name is the filename minus extension.
                removed.append(os.path.splitext(name)[0])
    print("removed %d '$' files" % len(removed))

    if not removed:
        return

    # Drop <item>/<...> entries in values/*.xml that point at the deleted files.
    pattern = re.compile(r"^.*\$.*$", re.M)
    for root, dirs, files in os.walk(RES):
        if not os.path.basename(root).startswith("values"):
            continue
        for name in files:
            if not name.endswith(".xml"):
                continue
            path = os.path.join(root, name)
            with open(path, encoding="utf-8") as fh:
                text = fh.read()
            if "$" not in text:
                continue
            cleaned = pattern.sub("", text)
            with open(path, "w", encoding="utf-8") as fh:
                fh.write(cleaned)
            print("cleaned refs in", path)


if __name__ == "__main__":
    main()
