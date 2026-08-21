#!/usr/bin/env python3
"""Remove specific <attr> declarations from our res/values tree.

apktool re-emits library <attr> definitions that carry <enum>/<flag> children (e.g.
attr/tintMode, attr/navigationMode). These are declared by appcompat/material and
collide with the Maven AAR copies at merge time. Unlike the plain library resources
handled by striplibres.py they have no distinguishing name prefix, so they are passed
in explicitly -- discovered by running aapt2 on the merged values.xml.

Usage: stripattrs.py attr/tintMode attr/navigationMode ...
"""
import os
import sys
import xml.etree.ElementTree as ET

RES = "/opt/pb/work/app/src/main/res"


def main(names):
    # Accept both "attr/foo" and bare "foo".
    wanted = set()
    for raw in names:
        wanted.add(raw.split("/", 1)[-1])

    removed = 0
    touched = 0
    for root, dirs, files in os.walk(RES):
        if not os.path.basename(root).startswith("values"):
            continue
        for fname in sorted(files):
            if not fname.endswith(".xml"):
                continue
            path = os.path.join(root, fname)
            try:
                tree = ET.parse(path)
            except ET.ParseError:
                continue
            resources = tree.getroot()
            if resources.tag != "resources":
                continue

            doomed = []
            for child in list(resources):
                name = child.get("name")
                if not name:
                    continue
                # Top-level <attr name="x"> declarations.
                if child.tag == "attr" and name in wanted:
                    doomed.append(child)
                # <declare-styleable> children are fine -- those are references, not
                # definitions -- but a styleable that *defines* the attr inline with
                # enum children also collides. Strip those children in place.
                elif child.tag == "declare-styleable":
                    for sub in list(child):
                        if sub.tag == "attr" and sub.get("name") in wanted and len(sub):
                            child.remove(sub)
                            removed += 1

            for child in doomed:
                resources.remove(child)
                removed += 1

            if doomed or removed:
                tree.write(path, encoding="utf-8", xml_declaration=True)
                touched += 1

    print("removed %d attr definitions across %d files" % (removed, touched))


if __name__ == "__main__":
    main(sys.argv[1:])
