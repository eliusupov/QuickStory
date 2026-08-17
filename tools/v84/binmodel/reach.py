"""Whitelist of functions that can (transitively) consume packet bytes.

Function starts are approximated by the set of direct call targets in the image; a function's
extent is [target, next target). A function "decodes" if some call site inside its extent targets
a decoding function. Iterated to a fixpoint.

This is only a PRUNING aid for dtrace: a false negative would hide fields, so the extent
approximation is deliberately generous (it over-includes rather than under-includes), and
dtrace_selfcheck.py checks that pruning does not change any v83 result.
"""
import bisect
import struct
from .images import img, IMG

_cache = {}


def prologue(B, off):
    """Does `off` look like a real function entry? A raw 0xE8 byte scan produces ~1 bogus edge per
    256 bytes of image; without this filter the reachable set degenerates to 'everything'."""
    if off < 0 or off + 6 > len(B):
        return False
    b = B[off:off + 6]
    if b[0:3] == b'\x55\x8b\xec':                 # push ebp ; mov ebp, esp
        return True
    if b[0] == 0xB8 and b[5] == 0xE8:             # mov eax, imm32 ; call __EH_prolog
        return True
    if b[0:2] == b'\x8b\xff':                     # hotpatch pad
        return True
    if b[0:2] in (b'\x83\xec', b'\x81\xec'):      # sub esp, N  (frame-pointer omission)
        return True
    if b[0] in (0x51, 0x53, 0x56, 0x57) and b[1] in (
            0x8b, 0x33, 0x55, 0x56, 0x57, 0x53, 0x51, 0x8d, 0x0f, 0xff, 0x83):
        return True
    return False


def call_edges(v):
    """(sorted target list, [(site, target)]) - direct rel32 calls to plausible function entries."""
    if ('e', v) in _cache:
        return _cache[('e', v)]
    B = img(v)
    edges = []
    targets = set()
    o = 0
    n = len(B)
    while True:
        o = B.find(b'\xE8', o)
        if o < 0 or o + 5 > n:
            break
        rel = struct.unpack_from('<i', B, o + 1)[0]
        t = IMG + o + 5 + rel
        if IMG < t < IMG + n and prologue(B, t - IMG):
            edges.append((IMG + o, t))
            targets.add(t)
        o += 1
    r = (sorted(targets), edges)
    _cache[('e', v)] = r
    return r


def decoding_set(v, seeds):
    key = ('d', v, tuple(sorted(seeds)))
    if key in _cache:
        return _cache[key]
    targets, edges = call_edges(v)
    # bucket call sites by containing function (nearest target start at or below the site)
    buckets = {}
    for site, t in edges:
        i = bisect.bisect_right(targets, site) - 1
        if i < 0:
            continue
        buckets.setdefault(targets[i], []).append(t)
    dec = set(seeds)
    changed = True
    while changed:
        changed = False
        for f, ts in buckets.items():
            if f in dec:
                continue
            for t in ts:
                if t in dec:
                    dec.add(f)
                    changed = True
                    break
    _cache[key] = dec
    return dec
