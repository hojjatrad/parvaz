#!/usr/bin/env python3
"""Repair R8-extracted inner classes that lost their `Outer.this` reference.

R8 hoists anonymous/inner classes into standalone top-level classes, keeping the
enclosing instance only as a synthetic captured field. jadx then emits `Outer.this`,
which no longer compiles because the class is no longer nested.

The fix is two-sided:
  1. add a public `outer()` accessor to the *still-nested* class that holds the
     enclosing instance, and
  2. in the extracted class, rewrite `Outer.this` -> `this.<capturedField>.outer()`.

The captured field is found by looking for a field in the extracted class whose type
is the outer class or one of its inner classes.
"""
import os
import re
import sys

ROOT = "/opt/pb/work/app/src/main/java/com/parvaz/tunnel"


def add_outer(outer_file, outer_name, inner_name):
    """Give `Outer.Inner` a public outer() accessor. Returns True if added/present."""
    path = os.path.join(ROOT, outer_file)
    if not os.path.exists(path):
        print("  ! no such file", outer_file)
        return False
    s = open(path, encoding="utf-8").read()

    # Already there?
    pat_have = re.compile(
        r"class\s+%s\b[^{]*\{(?:[^{}]|\{[^{}]*\})*?public\s+%s\s+outer\(\)"
        % (re.escape(inner_name), re.escape(outer_name)), re.S)
    if pat_have.search(s):
        return True

    # Insert right after the inner class's opening brace.
    m = re.search(r"(\n(\s*)(?:public|private|protected)?\s*(?:final\s+)?class\s+%s\b[^\n{]*\{\n)"
                  % re.escape(inner_name), s)
    if not m:
        print("  ! could not locate class", inner_name, "in", outer_file)
        return False
    indent = m.group(2) + "    "
    acc = ("\n%spublic %s outer() {\n%s    return %s.this;\n%s}\n"
           % (indent, outer_name, indent, outer_name, indent))
    s = s[:m.end()] + acc + s[m.end():]
    open(path, "w", encoding="utf-8").write(s)
    print("  + outer() added to %s.%s" % (outer_name, inner_name))
    return True


def rewrite(extracted_file, outer_name):
    """Rewrite `Outer.this` in an extracted class to go through its captured field."""
    path = os.path.join(ROOT, extracted_file)
    if not os.path.exists(path):
        print("  ! no such file", extracted_file)
        return None
    s = open(path, encoding="utf-8").read()
    if "%s.this" % outer_name not in s:
        return None

    # Find a field whose declared type is the outer class or one of its inners.
    m = re.search(r"public\s+final\s+%s(?:\.(\w+))?\s+(\w+);" % re.escape(outer_name), s)
    if not m:
        print("  ! no captured field of type %s in %s" % (outer_name, extracted_file))
        return None
    inner = m.group(1)
    field = m.group(2)

    if inner:
        s = s.replace("%s.this" % outer_name, "this.%s.outer()" % field)
    else:
        # Field is the outer instance itself; no accessor needed.
        s = s.replace("%s.this" % outer_name, "this.%s" % field)
    open(path, "w", encoding="utf-8").write(s)
    print("  ~ %s: %s.this -> this.%s%s"
          % (extracted_file, outer_name, field, ".outer()" if inner else ""))
    return (inner, field)


def main():
    # extracted file -> outer class whose `this` it references
    targets = {}
    for root, dirs, files in os.walk(ROOT):
        for fn in files:
            if not fn.endswith(".java"):
                continue
            path = os.path.join(root, fn)
            rel = os.path.relpath(path, ROOT)
            text = open(path, encoding="utf-8").read()
            cls = os.path.splitext(os.path.basename(fn))[0]
            for m in re.finditer(r"\b([A-Z]\w+)\.this\b", text):
                owner = m.group(1)
                if owner == cls:
                    continue  # genuine nested usage
                targets.setdefault(rel, set()).add(owner)

    outer_files = {}
    for root, dirs, files in os.walk(ROOT):
        for fn in files:
            if fn.endswith(".java"):
                outer_files[os.path.splitext(fn)[0]] = os.path.relpath(
                    os.path.join(root, fn), ROOT)

    for rel in sorted(targets):
        for owner in sorted(targets[rel]):
            print("%s -> %s.this" % (rel, owner))
            got = rewrite(rel, owner)
            if got and got[0]:
                inner, field = got
                if owner in outer_files:
                    add_outer(outer_files[owner], owner, inner)


if __name__ == "__main__":
    main()
