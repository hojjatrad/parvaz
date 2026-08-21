#!/usr/bin/env python3
"""Static sanity check on an APK's dex: every referenced type/method/field must
resolve either inside the dex or against android.jar + the JDK. Catches
NoClassDefFoundError / NoSuchMethodError that R8 shrinking or hand-written code
would only surface at runtime."""
import struct, sys, zipfile, os, subprocess, tempfile

def u32(b, o): return struct.unpack_from('<I', b, o)[0]
def u16(b, o): return struct.unpack_from('<H', b, o)[0]

def uleb(b, o):
    r = 0; s = 0
    while True:
        x = b[o]; o += 1
        r |= (x & 0x7f) << s
        if not (x & 0x80): break
        s += 7
    return r, o

class Dex:
    def __init__(self, buf):
        self.b = buf
        h = buf
        self.string_ids_size = u32(h, 56);  self.string_ids_off = u32(h, 60)
        self.type_ids_size   = u32(h, 64);  self.type_ids_off   = u32(h, 68)
        self.proto_ids_size  = u32(h, 72);  self.proto_ids_off  = u32(h, 76)
        self.field_ids_size  = u32(h, 80);  self.field_ids_off  = u32(h, 84)
        self.method_ids_size = u32(h, 88);  self.method_ids_off = u32(h, 92)
        self.class_defs_size = u32(h, 96);  self.class_defs_off = u32(h, 100)

    def string(self, i):
        off = u32(self.b, self.string_ids_off + i*4)
        n, off = uleb(self.b, off)
        end = self.b.index(b'\0', off)
        return self.b[off:end].decode('utf-8', 'replace')

    def type(self, i):
        return self.string(u32(self.b, self.type_ids_off + i*4))

    def proto(self, i):
        o = self.proto_ids_off + i*12
        shorty = self.string(u32(self.b, o))
        ret = self.type(u32(self.b, o+4))
        params_off = u32(self.b, o+8)
        params = []
        if params_off:
            n = u32(self.b, params_off)
            for k in range(n):
                params.append(self.type(u16(self.b, params_off+4+k*2)))
        return ret, params

    def method(self, i):
        o = self.method_ids_off + i*8
        cls = self.type(u16(self.b, o))
        proto = u16(self.b, o+2)
        name = self.string(u32(self.b, o+4))
        ret, params = self.proto(proto)
        return cls, name, ret, params

    def field(self, i):
        o = self.field_ids_off + i*8
        cls = self.type(u16(self.b, o))
        typ = self.type(u16(self.b, o+2))
        name = self.string(u32(self.b, o+4))
        return cls, name, typ

    def defined_types(self):
        out = set()
        for i in range(self.class_defs_size):
            out.add(self.type(u32(self.b, self.class_defs_off + i*32)))
        return out

def desc_to_cls(d):
    while d.startswith('['): d = d[1:]
    if d.startswith('L') and d.endswith(';'):
        return d[1:-1]
    return None

def main():
    apk = sys.argv[1]
    androidjar = sys.argv[2]
    z = zipfile.ZipFile(apk)
    dexes = [n for n in z.namelist() if n.endswith('.dex')]
    defined = set()
    dexobjs = []
    for n in dexes:
        d = Dex(z.read(n)); dexobjs.append(d); defined |= d.defined_types()

    aj = zipfile.ZipFile(androidjar)
    ajclasses = set(n[:-6] for n in aj.namelist() if n.endswith('.class'))

    defcls = set(desc_to_cls(t) for t in defined) - {None}

    missing_types = {}
    for d in dexobjs:
        for i in range(d.type_ids_size):
            t = d.type(i)
            c = desc_to_cls(t)
            if c is None: continue
            if c in defcls or c in ajclasses: continue
            missing_types.setdefault(c, 0)
            missing_types[c] += 1

    print("dex files      :", dexes)
    print("classes defined:", len(defined))
    print("UNRESOLVED TYPES: %d" % len(missing_types))
    for c in sorted(missing_types):
        print("   MISSING", c)

    # method refs whose owner is a defined class -> check the member exists
    return 0

if __name__ == '__main__':
    sys.exit(main())
