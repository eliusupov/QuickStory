"""PASS 3: function-scoped constant search, the tier the InitializeGr2D case suggests.

v83 site 0x009F7B1D (`push 600`) has no matching 64B context in v84 -- the function body
changed -- yet the instruction itself is alive and unique at v84 0x00A4127E, +0x60 from
where a same-offset prediction would put it. So: predict from the neighbour delta, then
look for the SAME instruction+immediate in a +-0x300 window. Unique hit = resolved.

Lower confidence than a context signature, so it is reported as its own tier.
"""
import json, os, re, collections

S = os.path.dirname(os.path.abspath(__file__))
V83 = open('D:/games/MapleStory/localhome.exe', 'rb').read()
V84 = open(f'{S}/v84_mem.bin', 'rb').read()
V84B = open(f'{S}/v84_mem2.bin', 'rb').read()
rows = json.load(open(f'{S}/v84-sites.json'))
res = [r for r in rows if r['status'] in ('resolved', 'anchored')]
byaddr = sorted(res, key=lambda r: r['v83'])

DROP = {0x009F1C04, 0x009F242F, 0x009F7A9B, 0x00AFE084, 0x0049C2CD, 0x0049CFE8,
        0x00496633, 0x00485C32}
U5 = {0x008C3304, 0x008C4286, 0x00780743, 0x0094D91E, 0x00AFE8A0}

def nearest_delta(va):
    lo = hi = None
    for r in byaddr:
        if r['v83'] <= va: lo = r
        elif hi is None: hi = r
    return [x['delta'] for x in (lo, hi) if x]

SPAN = 0x300
def scan(img, lo, hi, pat):
    out, i = [], lo - 0x400000
    while True:
        i = img.find(pat, i, hi - 0x400000)
        if i < 0: return out
        out.append(i + 0x400000); i += 1

new, tiers = 0, collections.Counter()
for r in rows:
    if r['status'] in ('resolved', 'anchored'):
        tiers[r['status']] += 1; continue
    va = r['v83']
    if va in DROP: tiers['drop-anyway'] += 1; continue
    if va in U5:   tiers['U5-baseline'] += 1; continue
    o = va - 0x400000
    # the instruction at the site: opcode byte(s) + the 4-byte immediate that follows it
    done = False
    for insn_len in (5, 6, 7, 3):
        pat = V83[o:o+insn_len]
        if len(pat) < insn_len: continue
        for d in sorted(set(nearest_delta(va))):
            p = va + d
            hits = scan(V84, p - SPAN, p + SPAN, pat)
            hits2 = scan(V84B, p - SPAN, p + SPAN, pat)
            if len(hits) == 1 and hits2 == hits:
                r.update(status='window-constant', v84=hits[0], delta=hits[0]-va,
                         window=insn_len, evidence=f'neighbour-delta+0x{d:X}/unique-in-+-0x300')
                new += 1; tiers['window-constant'] += 1; done = True
                break
        if done: break
    if not done:
        tiers['unresolved'] += 1

n = len(rows)
print('=== FINAL TIERS over the 316 in-.text sites ===')
order = ['resolved', 'anchored', 'window-constant', 'drop-anyway', 'U5-baseline', 'unresolved']
for k in order:
    print(f'  {k:16} {tiers[k]:3}  ({100.0*tiers[k]/n:.1f}%)')
auto = tiers['resolved'] + tiers['anchored'] + tiers['window-constant']
print(f'\n  mechanically located: {auto}/{n} = {100.0*auto/n:.1f}%')
real = n - tiers['drop-anyway']
print(f'  of the {real} sites that actually need porting: {100.0*auto/real:.1f}%')
print(f'  genuinely needing manual RE: {tiers["unresolved"]} ({100.0*tiers["unresolved"]/n:.1f}%)')

print('\n=== the ' + str(tiers['unresolved']) + ' sites that need manual RE ===')
srcmap = {}
for l in open(f'{S}/ezorsia/ezorsia/Client.cpp', encoding='utf-8', errors='replace'):
    if l.strip().startswith('//'): continue
    m = re.search(r'(0x00[0-9A-Fa-f]{6})', l.split('//')[0])
    if m: srcmap.setdefault(int(m.group(1), 16), l.strip()[:100])
for r in rows:
    if r['status'] not in order[:3] and r['v83'] not in DROP and r['v83'] not in U5:
        print(f'  0x{r["v83"]:08X}  {srcmap.get(r["v83"], "(via AddyLocations const)")}')

json.dump(rows, open(f'{S}/v84-sites.json', 'w'), indent=1)
print(f'\nwrote {S}/v84-sites.json')
