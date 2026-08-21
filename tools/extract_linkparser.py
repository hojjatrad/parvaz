#!/usr/bin/env python3
"""Slice LinkParser's methods back out of the R8 host class.

R8 horizontally merged com.parvaz.tunnel.config.LinkParser into
androidx.work.impl.WorkManagerImplExtKt. Enigma does not undo class merging, so jadx
emits the parser's methods as members of that unrelated host under one-letter names.

This pulls the bodies out by brace-matching (string-literal aware, so a '}' inside a
string can't throw off the count) and renames the obfuscated call targets back to the
real names. Longest-name-first with a boundary guard, so `H` doesn't clobber part of
another identifier.

The three-way merged `H` (parseRawJson + parseMany + parseOne on one obf name) is
NOT recoverable this way -- jadx interleaves the bodies. It is written by hand in
LinkParser.java instead.
"""
import json
import os
import re
import sys

HOST = "/opt/pb/src5/sources/androidx/work/impl/WorkManagerImplExtKt.java"
OUT = "/opt/pb/lp"

# obfuscated -> real, per mapping17.txt lines 43460-44501
NAMES = {
    "F": "newProfile",
    "G": "parseHysteria2",
    "I": "parseShadowsocks",
    "J": "parseSocks",
    "K": "parseTuic",
    "L": "parseVlessLike",
    "M": "parseVmess",
    "N": "parseWireguard",
    "P": "q",
    "Y": "tryBase64",
    "Z": "urlDecode",
    "a0": "valid",
    "e": "applyQuery",
    "r": "firstNonEmpty",
}


def slice_body(text, start):
    """Return text[start:end] where end closes the first '{' after start."""
    depth = 0
    i = start
    in_str = False
    in_chr = False
    in_line_comment = False
    in_block_comment = False
    esc = False
    started = False

    while i < len(text):
        c = text[i]
        nxt = text[i + 1] if i + 1 < len(text) else ""

        if in_line_comment:
            if c == "\n":
                in_line_comment = False
        elif in_block_comment:
            if c == "*" and nxt == "/":
                in_block_comment = False
                i += 1
        elif in_str:
            if esc:
                esc = False
            elif c == "\\":
                esc = True
            elif c == '"':
                in_str = False
        elif in_chr:
            if esc:
                esc = False
            elif c == "\\":
                esc = True
            elif c == "'":
                in_chr = False
        else:
            if c == "/" and nxt == "/":
                in_line_comment = True
                i += 1
            elif c == "/" and nxt == "*":
                in_block_comment = True
                i += 1
            elif c == '"':
                in_str = True
            elif c == "'":
                in_chr = True
            elif c == "{":
                depth += 1
                started = True
            elif c == "}":
                depth -= 1
                if started and depth == 0:
                    return text[start:i + 1]
        i += 1
    return None


def main():
    src = open(HOST, encoding="utf-8").read()
    os.makedirs(OUT, exist_ok=True)
    bodies = {}

    for obf, real in NAMES.items():
        # public/private static <ret> <obf>(   -- signature line ends with ' {'
        pat = re.compile(
            r"\n(\s*)((?:public|private|protected)\s+static\s+[\w.\[\]<>, ]+?\s+)"
            + re.escape(obf) + r"\(", re.M)
        m = pat.search(src)
        if not m:
            print("  MISS", obf, "->", real)
            continue
        body = slice_body(src, m.start(2))
        if body is None:
            print("  UNTERMINATED", obf)
            continue
        bodies[real] = body
        print("  sliced %-18s %5d chars" % (real, len(body)))

    # Rename obfuscated internal calls to the real names. Longest first, with a guard
    # so we only touch standalone identifiers.
    ordered = sorted(NAMES.items(), key=lambda kv: -len(kv[0]))
    renamed = {}
    for real, body in bodies.items():
        out = body
        for obf, target in ordered:
            out = re.sub(r"(?<![A-Za-z0-9_.])" + re.escape(obf) + r"\(",
                         target + "(", out)
        # the method's own name got rewritten too; make sure the declaration is right
        renamed[real] = out

    with open(os.path.join(OUT, "bodies.json"), "w", encoding="utf-8") as fh:
        json.dump(renamed, fh, indent=1)
    print("wrote", len(renamed), "bodies to", os.path.join(OUT, "bodies.json"))


if __name__ == "__main__":
    main()
