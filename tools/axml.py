#!/usr/bin/env python3
"""Minimal Android binary XML (AXML) decoder - enough to read a manifest."""
import struct, sys, zipfile

def read_strings(buf, off):
    (chunk_type, hdr_size, chunk_size) = struct.unpack_from('<HHI', buf, off)
    (string_count, style_count, flags, strings_start, styles_start) = struct.unpack_from('<IIIII', buf, off+8)
    utf8 = (flags & (1<<8)) != 0
    offsets = struct.unpack_from('<%dI' % string_count, buf, off+28)
    base = off + strings_start
    out = []
    for o in offsets:
        p = base + o
        if utf8:
            # u16len (maybe 2 bytes), u8len
            n = buf[p]; p += 1
            if n & 0x80: p += 1
            n = buf[p]; p += 1
            if n & 0x80:
                n = ((n & 0x7f) << 8) | buf[p]; p += 1
            out.append(buf[p:p+n].decode('utf-8', 'replace'))
        else:
            n = struct.unpack_from('<H', buf, p)[0]; p += 2
            if n & 0x8000:
                n2 = struct.unpack_from('<H', buf, p)[0]; p += 2
                n = ((n & 0x7fff) << 16) | n2
            out.append(buf[p:p+n*2].decode('utf-16-le', 'replace'))
    return out, off + chunk_size

def val(strs, typ, data):
    t = typ >> 24
    if t == 0x03:
        return strs[data] if data < len(strs) else '?'
    if t == 0x10:
        return str(data)
    if t == 0x12:
        return 'true' if data else 'false'
    if t == 0x01:
        return '@0x%08x' % data
    if t == 0x02:
        return '?0x%08x' % data
    return '0x%08x' % data

def decode(buf):
    (magic, size) = struct.unpack_from('<II', buf, 0)
    off = 8
    strs, off = read_strings(buf, off)
    resids = []
    lines = []
    depth = 0
    while off < len(buf):
        try:
            (ct, hs, cs) = struct.unpack_from('<HHI', buf, off)
        except struct.error:
            break
        if cs == 0: break
        if ct == 0x0180:  # RESOURCE_MAP
            resids = list(struct.unpack_from('<%dI' % ((cs-8)//4), buf, off+8))
        elif ct == 0x0102:  # START_TAG
            (ns, name) = struct.unpack_from('<iI', buf, off+16)
            (attr_start, attr_size, attr_count) = struct.unpack_from('<HHH', buf, off+24)
            tag = strs[name]
            attrs = []
            ao = off + 16 + attr_start
            for i in range(attr_count):
                (a_ns, a_name, a_raw, a_typ, a_data) = struct.unpack_from('<iiiII', buf, ao + i*20)
                nm = strs[a_name] if a_name >= 0 else '?'
                if not nm:
                    idx = a_name
                    nm = 'attr_0x%08x' % (resids[idx] if idx < len(resids) else 0)
                pre = 'android:' if a_ns >= 0 and 'android.com' in strs[a_ns] else ''
                attrs.append('%s%s="%s"' % (pre, nm, val(strs, a_typ, a_data)))
            lines.append('  '*depth + '<' + tag + (' ' + ' '.join(attrs) if attrs else '') + '>')
            depth += 1
        elif ct == 0x0103:  # END_TAG
            depth -= 1
            (ns, name) = struct.unpack_from('<iI', buf, off+16)
            lines.append('  '*depth + '</' + strs[name] + '>')
        off += cs
    return '\n'.join(lines)

if __name__ == '__main__':
    src = sys.argv[1]
    inner = sys.argv[2] if len(sys.argv) > 2 else 'AndroidManifest.xml'
    if src.endswith(('.apk', '.zip')):
        buf = zipfile.ZipFile(src).read(inner)
    else:
        buf = open(src, 'rb').read()
    print(decode(buf))
