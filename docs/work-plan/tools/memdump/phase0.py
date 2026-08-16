"""Ticket 30 phase 0/1: MEASURED v83->v84 signature transfer, on correctly-parsed sites.

Two instruments had to be fixed before any number here is believable:
  1. ticket 30's sig.py parsed `0x00XXXXXX` as XXXXXX and then ADDED 0x400000, so every
     site it measured was +0x400000 off, and every true site >= 0x7F9000 was dropped by
     its range filter. Its s2.6 table (158/172 unique @64B) is therefore void.
  2. exact byte matching cannot cross a relocated image: identical code carries absolute
     VAs and call rel32 displacements. Operands must be wildcarded.

v83: D:/games/MapleStory/localhome.exe (unpacked dump, offset = VA-0x400000)
v84: v84_mem.bin (ReadProcessMemory of the live client, offset = VA-0x400000)
"""
import re, os, json, collections

S = os.path.dirname(os.path.abspath(__file__))
EZ = f'{S}/ezorsia/ezorsia'
V83 = open('D:/games/MapleStory/localhome.exe', 'rb').read()
V84 = open(f'{S}/v84_mem.bin', 'rb').read()
V84B = open(f'{S}/v84_mem2.bin', 'rb').read()
R83 = (0x1000, 0x1000 + 0x7F8000)
R84 = (0x1000, 0x1000 + 0x851000)
LO, HI = 0x400000, 0xC00000

# ---------------------------------------------------------------- site extraction
consts = {}
for l in open(f'{EZ}/AddyLocations.h', encoding='utf-8'):
    m = re.match(r'\s*const DWORD (\w+)\s*=\s*(0x00[0-9A-Fa-f]{6})', l)
    if m:
        consts[m.group(1)] = int(m.group(2), 16)

src = open(f'{EZ}/Client.cpp', encoding='utf-8', errors='replace').read().splitlines()
starts = {}
for i, l in enumerate(src, 1):
    m = re.match(r'void Client::(\w+)\(\)', l)
    if m:
        starts[i] = m.group(1)

def fn(ln):
    cur = None
    for s in sorted(starts):
        if ln >= s:
            cur = starts[s]
    return cur

LIVE = {'UpdateGameStartup', 'UpdateResolution'}
ops = []
for i, l in enumerate(src, 1):
    if l.strip().startswith('//'):
        continue
    c = l.split('//')[0]
    if 'Memory::' not in c or fn(i) not in LIVE:
        continue
    op = re.search(r'Memory::(\w+)\(', c).group(1)
    arg = c.split('Memory::' + op + '(', 1)[1]
    a = None
    m = re.match(r'\s*(0x00[0-9A-Fa-f]{6})', arg)      # full literal, prefix included
    if m:
        a = int(m.group(1), 16)
    else:
        for p in [x.strip() for x in arg.split(',')]:
            if p in consts:
                a = consts[p]; break
            mm = re.match(r'(\w+)\s*\+', p)
            if mm and mm.group(1) in consts:
                a = consts[mm.group(1)]; break
    if a:
        ops.append((op, a, i))

addrs = sorted(set(a for _, a, _ in ops))
sites = [a for a in addrs if 0x401000 <= a < 0x00BF9000]
print(f'live patch operations : {len(ops)}   (ticket s2.1 says 327)')
print(f'distinct addresses    : {len(addrs)} (ticket s2.1 says 319)')
print(f'   of which in v83 .text: {len(sites)}   <- the sites this measurement is about')
opcount = collections.Counter(o for o, _, _ in ops)
print(f'   by op: {dict(opcount)}')

# ------------------------------------------- INSTRUMENT PROOF: sites hold 800/600 constants
CONSTS = {800: 'W', 600: 'H', 400: 'W/2', 300: 'H/2', 0xFFFFFE70: '-W/2', 0xFFFFFED4: '-H/2'}
wi = sorted(set(a for o, a, _ in ops if o == 'WriteInt' and 0x401000 <= a < 0xBF9000))
hit = collections.Counter()
nores = []
for a in wi:
    o = a - 0x400000
    found = None
    for k in range(0, 7):
        d = int.from_bytes(V83[o+k:o+k+4], 'little')
        if d in CONSTS:
            found = (CONSTS[d], k); break
    if found:
        hit[found[0]] += 1
    else:
        nores.append(a)
tot = len(wi)
print(f'\n=== INSTRUMENT PROOF (ticket s2.4): resolution constant within 6B of a WriteInt site ===')
print(f'  WriteInt sites in .text: {tot}')
print(f'  carry a vanilla 800x600 constant: {sum(hit.values())} ({100.0*sum(hit.values())/tot:.0f}%)  {dict(hit)}')
print(f'  no resolution constant nearby: {len(nores)}  {[hex(x) for x in nores[:12]]}')
print('  (ticket s2.4 measured 220/230 = 96% -- if this reproduces, sites are parsed right)')

# ---------------------------------------------------------------- masked matching
def mask_of(win, win_va):
    m = bytearray(b'\x01' * len(win))
    for i in range(len(win) - 3):
        dw = int.from_bytes(win[i:i+4], 'little')
        if LO <= dw < HI:
            m[i:i+4] = b'\x00' * 4
    for i in range(len(win) - 4):
        if win[i] in (0xE8, 0xE9):
            rel = int.from_bytes(win[i+1:i+5], 'little', signed=True)
            if LO <= (win_va + i + 5 + rel) & 0xFFFFFFFF < HI:
                m[i+1:i+5] = b'\x00' * 4
    return bytes(m)

def runs(mask, minlen=3):
    out, i = [], 0
    while i < len(mask):
        if mask[i]:
            j = i
            while j < len(mask) and mask[j]:
                j += 1
            if j - i >= minlen:
                out.append((i, j))
            i = j
        else:
            i += 1
    return sorted(out, key=lambda r: r[1]-r[0], reverse=True)

def find_masked(img, pat, mask, rng, cap=8):
    rs = runs(mask)
    if not rs:
        return -1, []
    a, b = rs[0]
    seed, (lo, hi) = pat[a:b], rng
    n, hits, i = 0, [], lo
    while True:
        i = img.find(seed, i, hi)
        if i < 0:
            return n, hits
        st = i - a
        if st >= lo and st + len(pat) <= hi:
            if all(img[st+k] == pat[k] for k in range(len(pat)) if mask[k]):
                n += 1
                if n <= cap:
                    hits.append(st + 0x400000)
        i += 1

WINDOWS = [(32, 32), (24, 24), (16, 16), (12, 12)]
print('\n=== signature uniqueness INSIDE v83, correct sites ===')
for tag, masked in (('exact ', False), ('masked', True)):
    line = []
    for pre, post in WINDOWS:
        u = 0
        for va in sites:
            o = va - 0x400000
            pat = V83[o-pre:o+post]
            msk = mask_of(pat, va-pre) if masked else b'\x01'*len(pat)
            c, _ = find_masked(V83, pat, msk, R83)
            u += (c == 1)
        line.append(f'+-{pre}B:{u}')
    print(f'  {tag} unique of {len(sites)}: ' + '  '.join(line))

# ---------------------------------------------------------------- transfer
print('\n=== TRANSFER v83 -> unpacked v84 (masked, widest usable window wins) ===')
rows = []
for va in sites:
    o = va - 0x400000
    r = {'v83': va, 'status': 'no-unique-v83-key', 'v84': None, 'delta': None, 'window': None}
    for pre, post in WINDOWS:
        pat = V83[o-pre:o+post]
        msk = mask_of(pat, va-pre)
        c83, _ = find_masked(V83, pat, msk, R83)
        if c83 != 1:
            continue
        c84, h84 = find_masked(V84, pat, msk, R84)
        if c84 == 1:
            r.update(status='resolved', v84=h84[0]+pre, window=pre+post, delta=h84[0]+pre-va)
            break
        r['status'] = 'absent-from-v84' if c84 == 0 else 'ambiguous-in-v84'
    rows.append(r)

n = len(rows)
cnt = collections.Counter(r['status'] for r in rows)
for k in ('resolved', 'absent-from-v84', 'ambiguous-in-v84', 'no-unique-v83-key'):
    print(f'  {k:20} {cnt[k]:3} ({100.0*cnt[k]/n:.1f}%)')
res = [r for r in rows if r['status'] == 'resolved']
print(f'\n  *** MEASURED SIGNATURE-TRANSFER RATE: {len(res)}/{n} = {100.0*len(res)/n:.1f}% ***')
print('  resolved by window:', dict(collections.Counter(r['window'] for r in res)))

print('\n=== CROSS-CHECKS ===')
env = [r for r in res if not (0 <= r['delta'] <= 0x59688)]
print(f'  1 delta outside anchor envelope [0,+0x59688]: {len(env)}')
for r in env[:6]:
    print(f'      0x{r["v83"]:08X} -> 0x{r["v84"]:08X} +0x{r["delta"]:X}')
hi_, inv = 0, []
for r in sorted(res, key=lambda r: r['v83']):
    if r['delta'] < hi_:
        inv.append(r)
    hi_ = max(hi_, r['delta'])
print(f'  2 non-monotonic deltas (code is only inserted): {len(inv)}')
mis = 0
for r in res:
    pre = r['window']//2
    o = r['v83']-0x400000
    pat = V83[o-pre:o+pre]
    c, h = find_masked(V84B, pat, mask_of(pat, r['v83']-pre), R84)
    if c != 1 or h[0]+pre != r['v84']:
        mis += 1
print(f'  3 disagreements with 2nd independent dump: {mis}')
same = diff = na = 0
for r in res:
    o83, o84 = r['v83']-0x400000, r['v84']-0x400000
    f = None
    for k in range(0, 7):
        d = int.from_bytes(V83[o83+k:o83+k+4], 'little')
        if d in CONSTS:
            f = (d, int.from_bytes(V84[o84+k:o84+k+4], 'little')); break
    if f is None: na += 1
    elif f[0] == f[1]: same += 1
    else: diff += 1
print(f'  4 resolution constant preserved at resolved v84 site: same={same} differs={diff} n/a={na}')

# per-group delta agreement: sites sharing a delta cluster corroborate each other
dc = collections.Counter(r['delta'] for r in res)
print(f'  5 distinct deltas among {len(res)} resolved sites: {len(dc)}; '
      f'largest clusters: {dc.most_common(6)}')

# ---------------------------------------------------------------- PASS 2: anchored recovery
# A global-unique signature is a luxury. The real method: predict the address from the
# nearest resolved neighbours' delta, then confirm with a SHORT masked signature inside a
# +-0x1000 window around the prediction. Local uniqueness is all that is needed there.
print('\n=== PASS 2: anchored recovery of the misses (neighbour delta + local signature) ===')
byaddr = sorted(res, key=lambda r: r['v83'])
def bracket(va):
    lo = hi = None
    for r in byaddr:
        if r['v83'] <= va: lo = r
        elif hi is None: hi = r
    return lo, hi

SPAN = 0x1000
rec = 0
for r in rows:
    if r['status'] == 'resolved':
        continue
    lo, hi = bracket(r['v83'])
    cands = [x['delta'] for x in (lo, hi) if x]
    if not cands:
        continue
    o = r['v83'] - 0x400000
    done = False
    for d in sorted(set(cands)):
        pred = r['v83'] + d
        for pre, post in [(16, 16), (12, 12), (8, 8)]:
            pat = V83[o-pre:o+post]
            msk = mask_of(pat, r['v83']-pre)
            lo_off = max(0x1000, pred - 0x400000 - SPAN)
            hi_off = min(R84[1], pred - 0x400000 + SPAN)
            c, h = find_masked(V84, pat, msk, (lo_off, hi_off))
            if c == 1:
                r.update(status='anchored', v84=h[0]+pre, window=pre+post,
                         delta=h[0]+pre-r['v83'], evidence=f'neighbour-delta+0x{d:X}/local-sig')
                rec += 1
                done = True
                break
        if done:
            break
print(f'  recovered by anchoring: {rec}')
tot_res = len(res) + rec
print(f'  *** COMBINED RESOLUTION: {tot_res}/{n} = {100.0*tot_res/n:.1f}% '
      f'(signature {100.0*len(res)/n:.1f}% + anchored {100.0*rec/n:.1f}%) ***')

# residual misses, attributed to their Ezorsia source line
srcline = {}
for op, a, i in ops:
    srcline.setdefault(a, src[i-1].strip()[:120])
miss = [r for r in rows if r['status'] not in ('resolved', 'anchored')]
print(f'\n=== RESIDUAL MISS LIST ({len(miss)}) — the named list phase 1 owes ===')
for r in miss:
    print(f'  0x{r["v83"]:08X}  {r["status"]:18} {srcline.get(r["v83"],"")}')
json.dump(rows, open(f'{S}/v84-sites.json', 'w'), indent=1)
print(f'\nwrote {S}/v84-sites.json')
