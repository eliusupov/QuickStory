"""Resolve every Ezorsia v83 patch site to its v84 address, with shape proof.

Phase 0 measured that 80.7% of sites transfer mechanically. It did NOT check that
the byte pattern it matched is the same *instruction* -- a signature that matches a
different instruction shape is a false positive and would corrupt the client.
This adds that check, plus a second, independent anchor.

Techniques, in the measured order:
  T1 masked context signature   +-32/24/16/12B, operands wildcarded, unique in both images
  T2 neighbour-delta anchoring  predict from the nearest resolved neighbours, confirm locally
  T3 function-scoped            locate the ENCLOSING FUNCTION by its prologue signature, then
                                find the instruction inside that function only

Every accepted hit must additionally:
  * reproduce in the second, independent v84 dump      (dual_dump)
  * disassemble to the same instruction shape as v83   (shape)
      same mnemonic, same operand kinds/registers, and the immediate/displacement
      the patch writes must sit at the SAME byte offset and be the SAME width
  * where the enclosing function was located, land inside that function  (fn_ok)

Anything that matches but fails `shape` is reported as a FALSE POSITIVE, not accepted.

    python tools/hd/resolve.py
"""
import bisect
import collections
import json
import os
import sys

from capstone import CS_ARCH_X86, CS_MODE_32, Cs

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import paths  # noqa: E402

MD = Cs(CS_ARCH_X86, CS_MODE_32)
MD.detail = True

LO, HI = 0x400000, 0xC00000          # plausible absolute-VA range -> wildcard these

# Patch sites that are DATA, not code (verified by disassembly + content). Nothing
# here has an instruction shape to compare, so shape checks are skipped and
# resolution goes through a code cross-reference instead.
DATA_ANCHORS = {0x00AFE084,   # 3x 16-byte server IP string slots
                0x00AFE8A0,   # damage-cap double
                0x00BE2738, 0x00BE273C, 0x00BE2DF0, 0x00BE2DF4,
                0x0040013E,   # PE COFF Characteristics field
                0x00C08459, 0x00C08463}   # embedded manifest text
WINDOWS = [(32, 32), (24, 24), (16, 16), (12, 12)]


# ------------------------------------------------------------------ primitives
def mask_of(win, win_va):
    """1 = must match, 0 = wildcard. Absolute VAs and call/jmp rel32 targets move."""
    m = bytearray(b'\x01' * len(win))
    for i in range(len(win) - 3):
        dw = int.from_bytes(win[i:i + 4], 'little')
        if LO <= dw < HI:
            m[i:i + 4] = b'\x00' * 4
    for i in range(len(win) - 4):
        if win[i] in (0xE8, 0xE9):
            rel = int.from_bytes(win[i + 1:i + 5], 'little', signed=True)
            if LO <= (win_va + i + 5 + rel) & 0xFFFFFFFF < HI:
                m[i + 1:i + 5] = b'\x00' * 4
    return bytes(m)


def longest_run(mask, minlen=3):
    best, i = None, 0
    while i < len(mask):
        if mask[i]:
            j = i
            while j < len(mask) and mask[j]:
                j += 1
            if j - i >= minlen and (best is None or j - i > best[1] - best[0]):
                best = (i, j)
            i = j
        else:
            i += 1
    return best


def find_masked(img, pat, mask, rng, cap=8):
    """(count, [VAs of pattern start]) inside file-offset range rng."""
    r = longest_run(mask)
    if not r:
        return 0, []
    a, b = r
    seed, (lo, hi) = pat[a:b], rng
    n, hits, i = 0, [], lo
    while True:
        i = img.find(seed, i, hi)
        if i < 0:
            return n, hits
        st = i - a
        if st >= lo and st + len(pat) <= hi:
            if all(img[st + k] == pat[k] for k in range(len(pat)) if mask[k]):
                n += 1
                if n <= cap:
                    hits.append(st + paths.BASE)
        i += 1


def shape(img, va):
    """Instruction shape at VA, or None if it does not decode."""
    off = va - paths.BASE
    for ins in MD.disasm(img[off:off + 16], va):
        e = ins.encoding
        return {
            'm': ins.mnemonic, 'ops': ins.op_str, 'len': ins.size,
            'imm_off': e.imm_offset, 'imm_size': e.imm_size,
            'disp_off': e.disp_offset, 'disp_size': e.disp_size,
            'bytes': ins.bytes.hex(),
        }
    return None


def operand_slot(sh, delta, size):
    """Does a `size`-byte write at insn_start+delta land on an operand? -> 'imm'|'disp'|None"""
    if not sh:
        return None
    if sh['imm_size'] == size and sh['imm_off'] == delta:
        return 'imm'
    if sh['disp_size'] == size and sh['disp_off'] == delta:
        return 'disp'
    return None


def classify(img, p, back=15):
    """Where does this patch's write actually land, in the v83 image?

    Returns a dict with the anchor instruction and a verdict:
      ok            write covers exactly one operand of the instruction at `site`
      partial       write lies inside a wider operand (narrow byte poke) -- legal
      opcode        1-byte write at +0: a deliberate opcode rewrite (jz->jmp, ->mov)
      block         multi-byte overwrite (FillBytes/ByteArray/CodeCave) -- checked elsewhere
      BAD-OFFSET    instruction at `site` has an operand of the right width, but at a
                    DIFFERENT offset. The source's +N is wrong.  <-- latent bug class
      BAD-SITE      the operand belongs to an instruction that starts earlier
      NO-OPERAND    nothing there is an operand: data, or the site is not an instruction
    """
    site, off, size, op = p['site'], p['off'], p['size'], p['op']
    sh = shape(img, site)
    r = {'anchor': site, 'slot': None, 'verdict': None, 'correct_off': None,
         'shape': sh}

    if op in ('FillBytes', 'WriteString', 'WriteByteArray', 'CodeCave', 'WriteDouble'):
        r['verdict'] = 'block'
        return r
    if op == 'WriteByte' and off == 0:
        r['verdict'], r['slot'] = 'opcode', 'opcode'
        return r

    target = site + off
    if sh:
        slot = operand_slot(sh, off, size)
        if slot:
            r.update(slot=slot, verdict='ok')
            return r
        # right width, wrong place -> the source's +N is off
        for nm, o_, s_ in (('imm', sh['imm_off'], sh['imm_size']),
                           ('disp', sh['disp_off'], sh['disp_size'])):
            if s_ == size and o_ != off and s_:
                r.update(slot=nm, verdict='BAD-OFFSET', correct_off=o_)
                return r
        # narrower write inside a wider operand
        for nm, o_, s_ in (('imm', sh['imm_off'], sh['imm_size']),
                           ('disp', sh['disp_off'], sh['disp_size'])):
            if s_ > size and o_ <= off < o_ + s_:
                r.update(slot=nm + '-partial', verdict='partial')
                return r

    for k in range(1, back + 1):
        va = site - k
        s2 = shape(img, va)
        if not s2 or va + s2['len'] < target + size:
            continue
        slot = operand_slot(s2, target - va, size)
        if slot:
            r.update(anchor=va, slot=slot, verdict='BAD-SITE', shape=s2,
                     correct_off=target - va)
            return r

    r['verdict'] = 'NO-OPERAND'
    return r


def regs_of(op_str):
    """Register/mnemonic skeleton: strip every numeric literal."""
    import re
    return re.sub(r'0x[0-9a-f]+|\b\d+\b', '#', op_str)


def same_slot(a, b):
    """Same operand geometry: the write would land on the same operand, same width.

    Register allocation is deliberately NOT compared here. v84 recompiled some blocks
    with different registers (`mov eax,0x1FC` became `mov ecx,0x1FC`); the patch only
    ever rewrites the immediate, so a different destination register is harmless. It
    is still weaker evidence, so callers record it as its own class.
    """
    if not a or not b:
        return False
    return (a['m'] == b['m'] and a['len'] == b['len']
            and a['imm_off'] == b['imm_off'] and a['imm_size'] == b['imm_size']
            and a['disp_off'] == b['disp_off'] and a['disp_size'] == b['disp_size'])


def same_shape(a, b):
    if not a or not b:
        return False
    return (a['m'] == b['m'] and a['len'] == b['len']
            and regs_of(a['ops']) == regs_of(b['ops'])
            and a['imm_off'] == b['imm_off'] and a['imm_size'] == b['imm_size']
            and a['disp_off'] == b['disp_off'] and a['disp_size'] == b['disp_size'])


PROLOGUE = (b'\x55\x8b\xec', b'\x8b\xff\x55\x8b\xec', b'\x53\x8b', b'\x56\x8b',
            b'\x57\x8b', b'\x83\xec', b'\x81\xec', b'\x6a', b'\x8b\x44\x24',
            b'\x8b\x4c\x24', b'\xb8', b'\x51\x53', b'\x51\x56', b'\x51\x8b')


def call_targets(img, rng):
    """E8 rel32 destinations that also look like real function starts.

    A raw E8 sweep over 8MB finds ~50k targets, most of them E8 bytes that live
    inside some other instruction. Requiring the destination to be preceded by
    int3/nop/ret padding OR to open with a recognised MSVC prologue throws the
    junk out; what is left is a usable function-start set.
    """
    lo, hi, hits = rng[0], rng[1], collections.Counter()
    i = img.find(b'\xE8', lo, hi)
    while i >= 0 and i + 5 <= hi:
        rel = int.from_bytes(img[i + 1:i + 5], 'little', signed=True)
        t = i + 5 + rel
        if lo <= t < hi:
            hits[t] += 1
        i = img.find(b'\xE8', i + 1, hi)
    out = []
    for t, n in hits.items():
        padded = img[t - 1] in (0xCC, 0x90, 0xC3) or img[t - 3:t - 1] == b'\xc2\x00'
        proper = img[t:t + 5].startswith(PROLOGUE)
        if padded or (proper and n >= 2):
            out.append(t + paths.BASE)
    return sorted(out)


# ------------------------------------------------------------------ main
def main():
    paths.require(paths.V83, paths.V84_A, paths.V84_B, paths.PATCHES)
    V83, V84, V84B = paths.load(paths.V83), paths.load(paths.V84_A), paths.load(paths.V84_B)
    R83, R84 = paths.R83, paths.R84
    doc = json.load(open(paths.PATCHES))
    patches = doc['patches']

    # ---- normalise every operand write onto the instruction it really belongs to.
    # The source names `site` and writes at site+off; for 3 known (and any unknown)
    # bugs that is not where the operand is. Anchor on the real instruction instead,
    # which also collapses the duplicate 0x9F7078+1 / 0x9F7079 spellings of one patch.
    norm = {}
    for p in patches:
        c = classify(V83, p)
        c.pop('shape', None)
        norm[p['id']] = c

    verdicts = collections.Counter(norm[p['id']]['verdict'] for p in patches)
    print('write classification vs the v83 image:', dict(verdicts))
    for p in patches:
        c = norm[p['id']]
        if c['verdict'] in ('BAD-OFFSET', 'BAD-SITE', 'NO-OPERAND'):
            print(f'   {c["verdict"]:11} {p["id"]} 0x{p["site"]:08X}+{p["off"]} '
                  f'({p["op"]}/{p["size"]}B) anchor=0x{c["anchor"]:08X} '
                  f'correct_off={c["correct_off"]}  // {p["comment"][:46]}')

    sites = sorted({norm[p['id']]['anchor'] for p in patches})
    in_text = [s for s in sites if R83[0] + paths.BASE <= s < R83[1] + paths.BASE]
    print(f'distinct anchors {len(sites)}   in v83 .text {len(in_text)}   '
          f'outside .text {len(sites) - len(in_text)} (PE header / manifest)')

    F83 = call_targets(V83, R83)
    print(f'v83 call-target function starts: {len(F83)}')

    rows = {}
    for s in in_text:
        rows[s] = {'v83': s, 'v84': None, 'tier': None, 'status': 'unresolved',
                   'shape83': shape(V83, s), 'shape84': None, 'window': None,
                   'delta': None, 'dual_dump': None, 'fn_ok': None, 'note': ''}

    # ---------------- T1: global masked context signature
    for s, r in rows.items():
        o = s - paths.BASE
        for pre, post in WINDOWS:
            pat = V83[o - pre:o + post]
            msk = mask_of(pat, s - pre)
            if find_masked(V83, pat, msk, R83)[0] != 1:
                continue
            c84, h84 = find_masked(V84, pat, msk, R84)
            if c84 == 1:
                r.update(v84=h84[0] + pre, tier='T1-context', window=pre + post,
                         delta=h84[0] + pre - s, status='hit')
                break
    print(f'T1 masked context signature      : {sum(r["tier"] == "T1-context" for r in rows.values())}')

    # ---------------- T1b: context signature with the patch's OWN target masked
    # The bytes a patch overwrites are the one part of the site guaranteed not to
    # matter -- they are about to be replaced. And v84 often changed exactly those:
    #   0x0089AF33  push 0x122 -> push 0x15E   (gain-message canvas 290 -> 350)
    #   0x0089B6F7  push 0x1F8 -> push 0x1BC
    #   0x0045B97E  push 0x320 -> push 0x384   (avatar megaphone 800 -> 900)
    # Leaving the immediate in the signature makes every one of those a miss. Wildcard
    # the write range and the surrounding context still identifies the site.
    wr = {}
    for p in patches:
        a = norm[p['id']]['anchor']
        lo, hi = p['site'] + p['off'] - a, p['site'] + p['off'] - a + p['size']
        cur = wr.get(a)
        wr[a] = (min(cur[0], lo), max(cur[1], hi)) if cur else (lo, hi)

    n1b = 0
    for s, r in rows.items():
        if r['v84'] is not None or s not in wr:
            continue
        o = s - paths.BASE
        wlo, whi = wr[s]
        if whi - wlo > 8:
            continue          # a 46-NOP code cave would mask away the whole signature
        for pre, post in WINDOWS:
            pat = V83[o - pre:o + post]
            msk = bytearray(mask_of(pat, s - pre))
            for i in range(max(0, pre + wlo), min(len(msk), pre + whi)):
                msk[i] = 0
            msk = bytes(msk)
            if find_masked(V83, pat, msk, R83)[0] != 1:
                continue
            c84, h84 = find_masked(V84, pat, msk, R84)
            cb, hb = find_masked(V84B, pat, msk, R84)
            if c84 == 1 and hb == h84:
                r.update(v84=h84[0] + pre, tier='T1b-masked-target', window=pre + post,
                         delta=h84[0] + pre - s, status='hit',
                         note='context unique with the patch target wildcarded')
                n1b += 1
                break
    print(f'T1b context, own target masked   : {n1b}')

    # T1c was BUILT AND DELETED. It extended T1b from "mask this patch's own target" to
    # "mask every patch target that falls in the window", because v84 changed BOTH
    # immediates in the group-I pop-up blocks (0x1FC->0x1EC and 0x1D0->0x19D, 0x14 bytes
    # apart, i.e. inside one signature window) and T1b masks only one of them.
    # Measured: 0 group-I resolutions, 2 false positives (both caught by the shape check).
    # It fails for a structural reason, not a tuning one: the eleven blocks are
    # near-IDENTICAL, so wildcarding the only fields that distinguish them makes them
    # indistinguishable from each other. At +-32B the masked pattern matches 3 v83 sites
    # and 7 v84 sites; at +-24B it is unique in v83 but has 0 v84 hits, because a byte
    # that mask_of does not wildcard still differs. Group I is joined ORDINALLY instead --
    # see data/manual-sites.json, which records the four constraints that force the join.

    # ---------------- T2: neighbour-delta anchoring
    def nearby_deltas(va):
        got = sorted((r['v83'], r['delta']) for r in rows.values() if r['v84'] is not None)
        ks = [a for a, _ in got]
        i = bisect.bisect_left(ks, va)
        return sorted({got[j][1] for j in (i - 1, i) if 0 <= j < len(got)})

    SPAN = 0x1000
    for s, r in rows.items():
        if r['v84'] is not None:
            continue
        o = s - paths.BASE
        for d in nearby_deltas(s):
            pred = s + d - paths.BASE
            rng = (max(R84[0], pred - SPAN), min(R84[1], pred + SPAN))
            for pre, post in [(16, 16), (12, 12), (8, 8)]:
                pat = V83[o - pre:o + post]
                msk = mask_of(pat, s - pre)
                c, h = find_masked(V84, pat, msk, rng)
                if c == 1:
                    r.update(v84=h[0] + pre, tier='T2-anchored', window=pre + post,
                             delta=h[0] + pre - s, status='hit',
                             note=f'neighbour delta +0x{d:X}')
                    break
            if r['v84'] is not None:
                break
    print(f'T2 neighbour-delta anchoring     : {sum(r["tier"] == "T2-anchored" for r in rows.values())}')

    # ---------------- T2b: interval-identical bracketing
    # If two resolved anchors a < s < b carry the SAME delta d, and the whole span
    # V83[a..b] equals V84[a+d..b+d] once operands are wildcarded, then that span of
    # code is unchanged and s maps to s+d by arithmetic. This needs no uniqueness,
    # so it resolves exactly the repeated boilerplate (the 11 near-identical pop-up
    # request blocks) that a signature search can never disambiguate.
    def interval_pass():
        got = sorted((r['v83'], r['delta']) for r in rows.values() if r['v84'] is not None)
        kk = [a for a, _ in got]
        n = 0
        for s, r in rows.items():
            if r['v84'] is not None:
                continue
            i = bisect.bisect_left(kk, s)
            if i == 0 or i >= len(got):
                continue
            (a, da), (b, db) = got[i - 1], got[i]
            if da != db or b - a > 0x4000:
                continue
            oa, ob = a - paths.BASE, b - paths.BASE
            pat = V83[oa:ob]
            msk = mask_of(pat, a)
            tgt = V84[oa + da:ob + da]
            if len(tgt) != len(pat):
                continue
            if all(tgt[k] == pat[k] for k in range(len(pat)) if msk[k]):
                r.update(v84=s + da, tier='T2b-interval', window=b - a, status='hit',
                         delta=da, note=f'span 0x{a:08X}-0x{b:08X} identical at +0x{da:X}')
                n += 1
        return n

    # T2c: one-sided identity extension. Weaker precondition than T2b -- only the
    # stretch between the nearest anchor and the site has to be unchanged, not a
    # whole bracket -- and it is still a proof, not a guess.
    def extend_pass(reach=0x1000):
        got = sorted((r['v83'], r['delta']) for r in rows.values() if r['v84'] is not None)
        kk = [a for a, _ in got]
        n = 0
        for s, r in rows.items():
            if r['v84'] is not None:
                continue
            i = bisect.bisect_left(kk, s)
            for side in ('L', 'R'):
                j = i - 1 if side == 'L' else i
                if not (0 <= j < len(got)):
                    continue
                a, da = got[j]
                lo83, hi83 = (min(a, s) - paths.BASE, max(a, s) - paths.BASE + 16)
                if hi83 - lo83 > reach:
                    continue
                pat = V83[lo83:hi83]
                msk = mask_of(pat, lo83 + paths.BASE)
                tgt = V84[lo83 + da:hi83 + da]
                if len(tgt) != len(pat):
                    continue
                if all(tgt[k] == pat[k] for k in range(len(pat)) if msk[k]):
                    r.update(v84=s + da, tier='T2c-extend', window=hi83 - lo83,
                             status='hit', delta=da,
                             note=f'span unchanged from anchor 0x{a:08X} at +0x{da:X}')
                    n += 1
                    break
        return n

    total_iv = 0
    while True:
        got = interval_pass() + extend_pass()
        total_iv += got
        if not got:
            break
    print(f'T2b/c interval + extend          : {total_iv}')

    # ---------------- T3: function-scoped
    # Locate the enclosing function by a masked signature of its first 48 bytes,
    # then search for the instruction ONLY inside that function's v84 body.
    fn_cache = {}

    def locate_fn(fstart):
        if fstart in fn_cache:
            return fn_cache[fstart]
        o = fstart - paths.BASE
        res = None
        for n in (64, 48, 32):
            pat = V83[o:o + n]
            msk = mask_of(pat, fstart)
            if find_masked(V83, pat, msk, R83)[0] != 1:
                continue
            c, h = find_masked(V84, pat, msk, R84)
            if c == 1:
                res = h[0]
                break
        fn_cache[fstart] = res
        return res

    def enclosing(va):
        i = bisect.bisect_right(F83, va) - 1
        return F83[i] if i >= 0 else None

    for s, r in rows.items():
        f83 = enclosing(s)
        r['fn83'] = f83
        if f83 is None:
            continue
        f84 = locate_fn(f83)
        r['fn84'] = f84
        if f84 is None:
            continue
        # function body length: to the next call-target start in v83
        j = bisect.bisect_right(F83, f83)
        f83end = F83[j] if j < len(F83) else f83 + 0x800
        span = min(max(f83end - f83, 0x40), 0x2000)
        if r['v84'] is not None:
            r['fn_ok'] = f84 <= r['v84'] < f84 + span + 0x800
            continue
        o = s - paths.BASE
        ins = r['shape83']
        if not ins:
            continue
        pat = V83[o:o + ins['len']]
        if len(pat) < 3:
            continue
        rng = (f84 - paths.BASE, min(R84[1], f84 - paths.BASE + span + 0x400))
        hits = []
        i = V84.find(pat, *rng)
        while i >= 0:
            hits.append(i + paths.BASE)
            i = V84.find(pat, i + 1, rng[1])
        if len(hits) == 1:
            r.update(v84=hits[0], tier='T3-function', window=ins['len'], status='hit',
                     delta=hits[0] - s, fn_ok=True,
                     note=f'unique in located fn 0x{f84:08X}')
    print(f'T3 function-scoped               : {sum(r["tier"] == "T3-function" for r in rows.values())}')

    # T4 (widen T2's window to +-0x4000) was tried and DELETED: it produced 4 hits and
    # the injectivity check rejected all 4. A signature unique in an arbitrarily wide
    # window is not evidence; only a window derived from the monotone envelope is
    # (that is T6). Do not reintroduce it.

    # ---------------- T6: monotone-envelope bracketing
    # v84 only ever INSERTS code, so for a site bracketed by resolved neighbours the
    # true delta must lie between theirs. That turns the whole image into a window of
    # a few tens of KB, inside which the site's own instruction bytes are usually
    # unique -- and uniqueness inside a provably-correct window is a real result,
    # unlike uniqueness inside an arbitrary +-0x300. Uses several neighbours a side
    # so the ~7% of non-monotone deltas cannot pinch the band shut.
    K, MARGIN = 4, 0x1000
    blocklen = {}
    for p in patches:
        if p['op'] in ('CodeCave', 'FillBytes', 'WriteByteArray'):
            a = norm[p['id']]['anchor']
            blocklen[a] = max(blocklen.get(a, 0), min(p['size'], 24))

    def envelope(va):
        got = sorted((r['v83'], r['delta']) for r in rows.values()
                     if r['v84'] is not None and r['delta'] is not None)
        i = bisect.bisect_left([a for a, _ in got], va)
        below = [d for _, d in got[max(0, i - K):i]]
        above = [d for _, d in got[i:i + K]]
        if not below or not above:
            return None
        return min(below) - MARGIN, max(above) + MARGIN

    n6 = 0
    for s, r in sorted(rows.items()):
        if r['v84'] is not None or r['shape83'] is None:
            continue
        env = envelope(s)
        if not env:
            continue
        ins = r['shape83']
        # a code cave / fill replaces a whole run of bytes; that run is far more
        # distinctive than the 2-byte instruction that happens to start it
        plen = max(ins['len'], blocklen.get(s, 0))
        pat = V83[s - paths.BASE:s - paths.BASE + plen]
        if len(pat) < 4:
            continue
        lo = max(R84[0], s + env[0] - paths.BASE)
        hi = min(R84[1], s + env[1] - paths.BASE)
        hits = []
        i = V84.find(pat, lo, hi)
        while i >= 0:
            if V84B[i:i + len(pat)] == pat:
                hits.append(i + paths.BASE)
            i = V84.find(pat, i + 1, hi)
        if len(hits) == 1:
            r.update(v84=hits[0], tier='T6-envelope', window=ins['len'], status='hit',
                     delta=hits[0] - s,
                     note=f'unique in monotone envelope [+0x{env[0]:X},+0x{env[1]:X}]')
            n6 += 1
    print(f'T6 monotone-envelope             : {n6}')

    # ---------------- T7: idiom scoring inside the monotone envelope
    # When the site's instruction is a common one (`push 600`) the envelope alone
    # leaves several candidates. Disambiguate the way a human does: decode forward
    # from each candidate and compare the sequence of mnemonics to v83's. Forward
    # decoding is aligned and therefore reliable; backward decoding is not, so this
    # deliberately only looks ahead. Accept only a clear, unique winner.
    def fwd_seq(img, va, n=14):
        out, off = [], va - paths.BASE
        for ins in MD.disasm(img[off:off + n * 8], va):
            out.append(ins.mnemonic)
            if len(out) >= n:
                break
        return out

    def lcs(a, b):
        prev = [0] * (len(b) + 1)
        for x in a:
            cur = [0]
            for j, y in enumerate(b):
                cur.append(prev[j] + 1 if x == y else max(cur[j], prev[j + 1]))
            prev = cur
        return prev[-1]

    n7 = 0
    for s, r in sorted(rows.items()):
        if r['v84'] is not None or r['shape83'] is None:
            continue
        env = envelope(s)
        if not env:
            continue
        ins = r['shape83']
        # a code cave / fill replaces a whole run of bytes; that run is far more
        # distinctive than the 2-byte instruction that happens to start it
        plen = max(ins['len'], blocklen.get(s, 0))
        pat = V83[s - paths.BASE:s - paths.BASE + plen]
        if len(pat) < 4:
            continue
        lo = max(R84[0], s + env[0] - paths.BASE)
        hi = min(R84[1], s + env[1] - paths.BASE)
        want = fwd_seq(V83, s)
        scored = []
        i = V84.find(pat, lo, hi)
        while i >= 0:
            if V84B[i:i + len(pat)] == pat:
                va = i + paths.BASE
                scored.append((lcs(want, fwd_seq(V84, va)), va))
            i = V84.find(pat, i + 1, hi)
        if not scored:
            continue
        scored.sort(reverse=True)
        best = scored[0]
        second = scored[1][0] if len(scored) > 1 else -1
        if best[0] >= 8 and best[0] >= second + 2:
            r.update(v84=best[1], tier='T7-idiom', window=ins['len'], status='hit',
                     delta=best[1] - s,
                     note=f'forward-idiom LCS {best[0]}/{len(want)} vs next-best '
                          f'{second}, inside monotone envelope')
            n7 += 1
    print(f'T7 forward-idiom in envelope     : {n7}')

    # ---------------- T5: data sites, resolved through a code cross-reference
    # 0x00AFE084 (server IP strings), 0x00AFE8A0 (damage-cap double) and the
    # 0x00BE2xxx globals are DATA -- there is no instruction to sign. But the code
    # that references them is signable, and mask_of() already wildcards the absolute
    # address, so the matched v84 instruction hands us the new data address directly.
    def xref_resolve(addr):
        needle = addr.to_bytes(4, 'little')
        votes = collections.Counter()
        i = V83.find(needle, R83[0], R83[1])
        seen = 0
        while i >= 0 and seen < 30:
            seen += 1
            for pre, post in ((24, 24), (32, 32), (16, 16)):
                pat = V83[i - pre:i + post]
                msk = mask_of(pat, i + paths.BASE - pre)
                if find_masked(V83, pat, msk, R83)[0] != 1:
                    continue
                c, h = find_masked(V84, pat, msk, R84)
                if c != 1:
                    continue
                q = h[0] - paths.BASE + pre
                v = int.from_bytes(V84[q:q + 4], 'little')
                vb = int.from_bytes(V84B[q:q + 4], 'little')
                if LO <= v < HI and v == vb:
                    votes[v] += 1
                break
            i = V83.find(needle, i + 1, R83[1])
        return votes

    n5 = 0
    for s, r in rows.items():
        if r['v84'] is not None or s not in DATA_ANCHORS:
            continue
        v = xref_resolve(s)
        if v and v.most_common(1)[0][1] >= 2 and len(v) == 1:
            addr = v.most_common(1)[0][0]
            r.update(v84=addr, tier='T5-xref', status='hit', delta=addr - s,
                     window=0, note=f'{v.most_common(1)[0][1]} agreeing code xrefs',
                     data=True)
            n5 += 1
    print(f'T5 data-site xref                : {n5}')

    # T8 (propagate a sibling's delta to an exact predicted address) and T9 (same,
    # tolerating v84's register reallocation within +-0x40) were both TRIED AND
    # DELETED. T8 scored 0. T9 scored 1 net and produced 4 new injectivity collisions,
    # including one that displaced a previously good hit. The reason is a real finding,
    # not a tuning problem: v84 rebuilt the group-I pop-up handlers with different
    # registers (`mov eax,0x1FC ; sub eax,ecx` became `mov ecx,0x1FC ; sub ecx,eax`),
    # added an alternate branch (`mov ecx,0x1EC ; add edx,-0x33`) that v83 has no
    # counterpart for, and the surviving anchors there are non-monotone -- which points
    # at block reordering as well. Group I needs per-site RE, not another heuristic.


    # ---------------- M: hand-resolved sites (data/manual-sites.json)
    nm = 0
    if os.path.exists(paths.MANUAL):
        man = json.load(open(paths.MANUAL))['sites']
        for k, v in man.items():
            s = int(k, 16)
            r = rows.setdefault(s, {'v83': s, 'v84': None, 'tier': None,
                                    'status': 'unresolved', 'shape83': None,
                                    'shape84': None, 'window': None, 'delta': None,
                                    'dual_dump': None, 'fn_ok': None, 'note': ''})
            # Hand RE with written evidence outranks every search tier, so it
            # OVERRIDES an existing hit -- several of these sites were claimed by a
            # weak tier that the injectivity check would only have rejected later.
            # A clash with T1 is a real disagreement and gets shouted about.
            if r['tier'] == 'T1-context' and r['v84'] != int(v['v84'], 16):
                print(f'   !! manual 0x{s:08X} -> 0x{int(v["v84"], 16):08X} '
                      f'DISAGREES with T1 0x{r["v84"]:08X}; keeping manual')
            if r['v84'] != int(v['v84'], 16):
                r.update(v84=int(v['v84'], 16), tier=v['tier'], status='hit',
                         delta=int(v['v84'], 16) - s, note=v['evidence'][:120],
                         data=s in DATA_ANCHORS, regalloc=v.get('regalloc', False))
                nm += 1
    print(f'M  hand-resolved                 : {nm}')

    # ---------------- verification of every hit
    fp = []
    for s, r in rows.items():
        if r['v84'] is None:
            continue
        n = (r['shape83'] or {}).get('len', 8)
        a, b = r['v84'] - paths.BASE, r['v84'] - paths.BASE + n
        r['dual_dump'] = len(V84B) > b and V84[a:b] == V84B[a:b]
        if s in DATA_ANCHORS:
            r['data'] = True
        if r.get('data'):
            # no instruction to compare; the xref votes and dump B already agreed
            r['shape_ok'], r['status'] = None, 'resolved-data'
            continue
        r['shape84'] = shape(V84, r['v84'])
        r['shape_ok'] = same_shape(r['shape83'], r['shape84'])
        if not r['shape_ok'] and r.get('regalloc') and same_slot(r['shape83'], r['shape84']):
            r['shape_ok'] = 'regalloc'      # registers differ, operand geometry does not
        if not r['shape_ok'] or not r['dual_dump']:
            r['status'] = 'false-positive'
            fp.append(r)
        else:
            r['status'] = 'resolved'

    # ---------------- INJECTIVITY: two v83 sites cannot be one v84 site
    # A shape check cannot catch this on its own: `push 578` looks like `push 578`
    # wherever it is. Distinct sites colliding on one v84 address is proof that at
    # least one of them is wrong, so keep only a strictly better-evidenced tier and
    # reject the rest.
    TIER_RANK = {'T1-context': 0, 'M-manual': 0, 'T1b-masked-target': 1,
                 'T2b-interval': 1, 'T2c-extend': 1,
                 'T5-xref': 1, 'T2-anchored': 2, 'T6-envelope': 3, 'T7-idiom': 3,
                 'T3-function': 4}
    # T1 and the hand-resolved sites are the trustworthy skeleton; a claimant whose
    # delta exactly equals that of a T1 anchor bracketing it is far more likely right
    # than one that merely used a better-ranked search. Break ties on that FIRST --
    # ranking on tier alone threw away 0x00523FA3, whose delta +0xBC42 matches its T1
    # neighbours on both sides exactly.
    trusted = sorted((r['v83'], r['delta']) for r in rows.values()
                     if r['tier'] in ('T1-context', 'T1b-masked-target',
                                      'M-manual', 'T5-xref')
                     and r['delta'] is not None)
    tk = [a for a, _ in trusted]

    def matches_trusted_delta(r):
        i = bisect.bisect_left(tk, r['v83'])
        for j in (i - 1, i):
            if 0 <= j < len(trusted) and trusted[j][1] == r['delta']:
                return 0
        return 1

    byv84 = collections.defaultdict(list)
    for r in rows.values():
        if r['v84'] is not None and r['status'] != 'false-positive':
            byv84[r['v84']].append(r)
    coll = []
    for addr, rs in byv84.items():
        if len(rs) < 2:
            continue
        key = lambda r: (matches_trusted_delta(r), TIER_RANK.get(r['tier'], 9))  # noqa: E731
        rs.sort(key=key)
        losers = rs[1:] if key(rs[0]) < key(rs[1]) else rs
        for r in losers:
            r.update(status='false-positive',
                     reason=f'collision: {len(rs)} v83 sites claim 0x{addr:08X}')
            coll.append(r)
    print(f'\ninjectivity violations rejected  : {len(coll)}')
    for r in sorted(coll, key=lambda r: r['v83']):
        print(f'   0x{r["v83"]:08X} -> 0x{r["v84"]:08X} [{r["tier"]}] {r["reason"]}')

    # ---------------- MONOTONICITY: v84 only inserts code, so deltas rise with address
    ordered = sorted((r for r in rows.values()
                      if r['v84'] is not None and r['status'] != 'false-positive'),
                     key=lambda r: r['v83'])
    OUT = 0x800
    mono_bad = []
    for i, r in enumerate(ordered):
        lo = min((x['delta'] for x in ordered[max(0, i - 4):i]), default=None)
        hi = max((x['delta'] for x in ordered[i + 1:i + 5]), default=None)
        r['mono_ok'] = True
        if lo is not None and r['delta'] < lo - OUT:
            r['mono_ok'] = False
        if hi is not None and r['delta'] > hi + OUT:
            r['mono_ok'] = False
        if not r['mono_ok']:
            mono_bad.append(r)
    print(f'monotonicity outliers (>0x{OUT:X} outside neighbour band): {len(mono_bad)}')
    for r in mono_bad:
        r.update(status='false-positive', reason='delta outside neighbour envelope')
        print(f'   0x{r["v83"]:08X} -> 0x{r["v84"]:08X} +0x{r["delta"]:X} [{r["tier"]}]')
    fp += coll + mono_bad

    ok = [r for r in rows.values() if r['status'] in ('resolved', 'resolved-data')]
    N = len(rows)
    print()
    print(f'=== RESULT ({N} distinct patch anchors) ===')
    t = collections.Counter(r['tier'] for r in ok)
    for k in ("T1-context", "T1b-masked-target", "T2-anchored",
              "T2b-interval", "T2c-extend", "T3-function", "T6-envelope", "T7-idiom",
              "T5-xref", "M-manual"):
        print(f'  {k:20} {t[k]:3}  ({100.0 * t[k] / N:.1f}%)')
    print(f'  {"RESOLVED":16} {len(ok):3}  ({100.0 * len(ok) / N:.1f}%)')
    print(f'  {"false positive":16} {len(fp):3}')
    print(f'  {"unresolved":16} {N - len(ok) - len(fp):3}')
    fnc = collections.Counter(r['fn_ok'] for r in ok)
    print(f'  enclosing-function cross-check: ok={fnc[True]} mismatch={fnc[False]} '
          f'not-located={fnc[None]}')

    if fp:
        print('\n=== FALSE POSITIVES (matched bytes, wrong instruction) ===')
        for r in fp:
            print(f'  0x{r["v83"]:08X} -> 0x{r["v84"]:08X} [{r["tier"]}] '
                  f'v83={r["shape83"] and r["shape83"]["m"] + " " + r["shape83"]["ops"]}'
                  f' | v84={r["shape84"] and r["shape84"]["m"] + " " + r["shape84"]["ops"]}')

    os.makedirs(paths.DATA, exist_ok=True)
    with open(paths.RESOLVED, 'w') as f:
        json.dump({'sites': sorted(rows.values(), key=lambda r: r['v83']),
                   'norm': norm}, f, indent=1)
    print('\nwrote', paths.RESOLVED)


if __name__ == '__main__':
    main()
