#!/usr/bin/env python3
"""Convert an R8/ProGuard mapping.txt into the Enigma format jadx can read.

jadx's --mappings-path rejects raw ProGuard output; it wants Enigma/Tiny. This walks
the R8 mapping and emits CLASS / METHOD / FIELD lines with obfuscated -> real names.

Gotchas handled here (learned the hard way):
  * skip "# {...}" comment lines
  * strip leading "123:456:" line-number prefixes
  * R8 inline frames list the outer method LAST and prefix inherited members with a
    holder class ("Outer.name") -- those must be dropped or they poison the mapping
  * one obfuscated symbol can cover both a field and a method
"""
import re
import sys


def desc_for(java_type):
    """Map a Java source type to a JVM descriptor."""
    arr = 0
    while java_type.endswith("[]"):
        arr += 1
        java_type = java_type[:-2]
    prims = {
        "void": "V", "boolean": "Z", "byte": "B", "char": "C", "short": "S",
        "int": "I", "long": "J", "float": "F", "double": "D",
    }
    if java_type in prims:
        base = prims[java_type]
    else:
        base = "L" + java_type.replace(".", "/") + ";"
    return "[" * arr + base


def main(src, dst):
    out = []
    current = None

    with open(src, encoding="utf-8", errors="replace") as fh:
        for raw in fh:
            line = raw.rstrip("\n")
            if not line.strip() or line.lstrip().startswith("#"):
                continue

            # Class line: "real.Name -> obf:"
            if not line.startswith(" ") and line.endswith(":"):
                body = line[:-1]
                if " -> " not in body:
                    continue
                real, obf = body.split(" -> ", 1)
                real = real.strip()
                obf = obf.strip()
                current = obf
                out.append("CLASS %s %s" % (obf.replace(".", "/"),
                                            real.replace(".", "/")))
                continue

            if current is None:
                continue

            member = line.strip()
            if " -> " not in member:
                continue
            left, obf_name = member.split(" -> ", 1)
            obf_name = obf_name.strip()
            left = left.strip()

            # Drop the "123:456:" source-line prefix R8 adds.
            left = re.sub(r"^\d+:\d+:", "", left)

            if "(" in left:
                # Method:  "rettype name(argtypes)"  (possibly with an inline suffix)
                head, _, tail = left.partition("(")
                args = tail.split(")")[0]
                parts = head.split()
                if len(parts) != 2:
                    continue
                ret, name = parts
                # Inherited/inlined frames come through as "Holder.method" -- skip them,
                # they belong to another class and would corrupt this one's mapping.
                if "." in name:
                    continue
                arg_descs = ""
                if args.strip():
                    for arg in args.split(","):
                        arg = arg.strip()
                        if arg:
                            arg_descs += desc_for(arg)
                desc = "(%s)%s" % (arg_descs, desc_for(ret))
                out.append("\tMETHOD %s %s %s" % (obf_name, name, desc))
            else:
                # Field: "type name"
                parts = left.split()
                if len(parts) != 2:
                    continue
                ftype, name = parts
                if "." in name:
                    continue
                out.append("\tFIELD %s %s %s" % (obf_name, name, desc_for(ftype)))

    with open(dst, "w", encoding="utf-8") as fh:
        fh.write("\n".join(out) + "\n")
    print("wrote %s lines to %s" % (len(out), dst))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
