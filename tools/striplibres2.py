#!/usr/bin/env python3
"""Strip exactly those values-resources that the Maven AARs already define.

The earlier prefix-guessing version was wrong in both directions: it missed library
attrs with no prefix (tintMode, navigationMode) and it ate the app's own resources
whose names happened to start with a library-ish prefix (search_hint, tab_bar,
item_server).

This version reads the truth out of the AAR cache: every dependency's res/values/*.xml
is parsed and the (type, name) pairs it declares are collected. Anything in our tree
matching one of those pairs is a duplicate and gets removed; everything else -- i.e.
everything the app itself declares -- is left completely alone.
"""
import os
import re
import sys
import zipfile
import xml.etree.ElementTree as ET

RES = "/opt/pb/work/app/src/main/res"
CACHE = "/opt/pb/.gradle/caches/modules-2/files-2.1"

# <item type="id"> etc. carry the type as an attribute; other tags name it directly.
TAG_TO_TYPE = {
    "string": "string", "color": "color", "dimen": "dimen", "bool": "bool",
    "integer": "integer", "style": "style", "attr": "attr", "id": "id",
    "array": "array", "string-array": "array", "integer-array": "array",
    "plurals": "plurals", "fraction": "fraction", "declare-styleable": "styleable",
}


def lib_resources():
    """(type, name) pairs declared by every AAR on the compile classpath."""
    found = set()
    aars = []
    for root, dirs, files in os.walk(CACHE):
        for name in files:
            if name.endswith(".aar"):
                aars.append(os.path.join(root, name))

    for path in aars:
        try:
            with zipfile.ZipFile(path) as zf:
                for entry in zf.namelist():
                    if not re.match(r"res/values[^/]*/.*\.xml$", entry):
                        continue
                    try:
                        data = zf.read(entry)
                        root_el = ET.fromstring(data)
                    except Exception:
                        continue
                    if root_el.tag != "resources":
                        continue
                    for child in root_el:
                        name = child.get("name")
                        if not name:
                            continue
                        if child.tag == "item":
                            rtype = child.get("type")
                        else:
                            rtype = TAG_TO_TYPE.get(child.tag)
                        if rtype:
                            found.add((rtype, name))
        except Exception:
            continue
    print("scanned %d aars, %d library resource names" % (len(aars), len(found)))
    return found


def main():
    lib = lib_resources()
    if not lib:
        print("ERROR: no library resources found; is the gradle cache populated?")
        return 1

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
                if child.tag == "item":
                    rtype = child.get("type")
                else:
                    rtype = TAG_TO_TYPE.get(child.tag)
                if rtype and (rtype, name) in lib:
                    doomed.append(child)

            if not doomed:
                continue
            for child in doomed:
                resources.remove(child)
            removed += len(doomed)
            touched += 1
            tree.write(path, encoding="utf-8", xml_declaration=True)

    print("removed %d duplicate resources from %d files" % (removed, touched))
    return 0


if __name__ == "__main__":
    sys.exit(main())
