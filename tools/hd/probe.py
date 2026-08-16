"""Hand-RE helper: disassemble / search the v83 and v84 images. Read-only.

    python tools/hd/probe.py dis 83 0x009F7B1D [n] [back]
    python tools/hd/probe.py dis 84 0x00A4127E
    python tools/hd/probe.py sig 0x009F7B1D          masked context of a v83 site, hits in v84
    python tools/hd/probe.py find 84 68580200006820030000
    python tools/hd/probe.py str 84 MapleStoryClass
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import paths                                          # noqa: E402
from resolve import MD, find_masked, mask_of          # noqa: E402

IMG = {'83': paths.V83, '84': paths.V84_A, '84b': paths.V84_B}


def dis(which, va, n=48, back=0):
    img = paths.load(IMG[which])
    o = va - paths.BASE - back
    for i in MD.disasm(img[o:o + n + back], va - back):
        mark = '  <==' if i.address == va else ''
        print(f'  0x{i.address:08X}  {i.bytes.hex():<20} {i.mnemonic} {i.op_str}{mark}')


def sig(va, pre=32, post=32):
    v83, v84, v84b = (paths.load(p) for p in (paths.V83, paths.V84_A, paths.V84_B))
    o = va - paths.BASE
    for pre, post in ((32, 32), (24, 24), (16, 16), (12, 12), (8, 8)):
        pat = v83[o - pre:o + post]
        m = mask_of(pat, va - pre)
        c83, _ = find_masked(v83, pat, m, paths.R83)
        c84, h84 = find_masked(v84, pat, m, paths.R84)
        cb, hb = find_masked(v84b, pat, m, paths.R84)
        print(f'  +-{pre:2}B  v83 hits={c83}  v84 hits={c84} {[hex(x + pre) for x in h84[:4]]}'
              f'  dumpB hits={cb} {[hex(x + pre) for x in hb[:4]]}')


def cand(va, span=0x20000):
    """v84 candidates for an unresolved v83 site, ranked by neighbour-delta plausibility.

    Prints the delta band implied by the nearest already-resolved sites, then every
    v84 address whose bytes equal the v83 instruction, with its delta and the two
    instructions either side so the surrounding idiom can be compared by eye.
    """
    import json
    v83, v84, v84b = (paths.load(p) for p in (paths.V83, paths.V84_A, paths.V84_B))
    J = json.load(open(paths.RESOLVED))
    res = sorted((r['v83'], r['delta']) for r in J['sites']
                 if r['v84'] is not None and r['delta'] is not None)
    near = [(a, d) for a, d in res if abs(a - va) < span]
    lo_d = min(d for _, d in near) if near else 0
    hi_d = max(d for _, d in near) if near else 0x60000
    print(f'  neighbour deltas within +-0x{span:X}: {len(near)} sites, '
          f'band 0x{lo_d:X}..0x{hi_d:X}')
    for a, d in near[:6] + near[-6:]:
        print(f'     0x{a:08X} +0x{d:X}')
    ins = next(MD.disasm(v83[va - paths.BASE:va - paths.BASE + 16], va))
    print(f'  v83 site: {ins.mnemonic} {ins.op_str}   bytes {ins.bytes.hex()}')
    pat = bytes(ins.bytes)
    i, n = v84.find(pat, paths.R84[0], paths.R84[1]), 0
    while i >= 0 and n < 60:
        t = i + paths.BASE
        d = t - va
        flag = 'IN-BAND ' if lo_d - 0x2000 <= d <= hi_d + 0x2000 else '        '
        if v84b[i:i + len(pat)] == pat:
            print(f'  {flag}0x{t:08X}  delta +0x{d:X}')
            n += 1
        i = v84.find(pat, i + 1, paths.R84[1])


def find(which, hexpat, lo=None, hi=None):
    img = paths.load(IMG[which])
    pat = bytes.fromhex(hexpat)
    rng = paths.R83 if which == '83' else paths.R84
    lo = lo if lo is not None else rng[0]
    hi = hi if hi is not None else rng[1]
    i, n = img.find(pat, lo, hi), 0
    while i >= 0:
        print(f'  0x{i + paths.BASE:08X}')
        n += 1
        if n > 40:
            print('  ...')
            return
        i = img.find(pat, i + 1, hi)
    if not n:
        print('  (none)')


def strings(which, text):
    img = paths.load(IMG[which])
    for enc in ('ascii', 'utf-16-le'):
        b = text.encode(enc)
        i, n = img.find(b), 0
        while i >= 0 and n < 20:
            print(f'  {enc:9} 0x{i + paths.BASE:08X}')
            n += 1
            i = img.find(b, i + 1)


if __name__ == '__main__':
    a = sys.argv[1:]
    if not a:
        print(__doc__)
    elif a[0] == 'dis':
        dis(a[1], int(a[2], 0), *(int(x, 0) for x in a[3:]))
    elif a[0] == 'sig':
        sig(int(a[1], 0))
    elif a[0] == 'cand':
        cand(int(a[1], 0), *(int(x, 0) for x in a[2:]))
    elif a[0] == 'find':
        find(a[1], a[2], *(int(x, 0) for x in a[3:]))
    elif a[0] == 'str':
        strings(a[1], a[2])
    else:
        print(__doc__)
