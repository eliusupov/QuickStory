"""Emit Cosmic's v84 opcode tables plus the v83<->v84 diff report.

Cosmic keys are mapped to atlas rows by **v83 opcode position**, not by name: Cosmic's
v83 table and atlas's v83 registry agree on the opcode for all 471 name-matched entries
(0 disagreements clientbound, 2 serverbound), so position is the stronger join and it
carries the aliases (Cosmic SERVERLIST == atlas WORLD_INFORMATION, Cosmic REPORT ==
atlas CLAIM_REQUEST, ...) for free. Names go in the report, not the join.
"""
import io, os
from lib import *
from adjudicate import final, STALE, v84rows, v83map, near, ANCHORS

OUT = r'D:\games\MapleStory\Server\Cosmic\.claude\worktrees\evan-dualblade\src\main\resources\opcodes'
DOC = r'D:\games\MapleStory\Server\Cosmic\.claude\worktrees\evan-dualblade\docs\work-plan'
UNRESOLVED_SENTINEL = 0xFFFF   # same idiom as Cosmic's existing MAPLETV = 0xFFFE

# ---- atlas v83 opcode -> adjudicated v84 opcode, per direction -----------------------
pos = {}
by_v83 = {}
for r in v84rows:
    v = v83map.get(key(r))
    if v is None:
        continue
    k = (r['direction'], v['opcode'])
    assert k not in pos or pos[k] == final[key(r)], k
    pos[k] = final[key(r)]
    by_v83[k] = r

# v84-only atlas rows, matched to Cosmic by name as a last resort
v84_only = {(r['direction'], r['op']): r for r in v84rows if key(r) not in v83map}

DIR = {'send': 'clientbound', 'recv': 'serverbound'}

rows = {}            # kind -> list of report rows
cosmic_unmatched = []
atlas_used = set()

for kind in ('send', 'recv'):
    D = DIR[kind]
    table = load_props('%sops-83.properties' % kind)
    out = []
    for name, v83op in sorted(table.items(), key=lambda kv: (kv[1], kv[0])):
        k = (D, v83op)
        if k in pos:
            src = by_v83[k]
            atlas_used.add(key(src))
            adj = STALE.get((D, src['op']))
            v84op = pos[k]
            if adj is None:
                st = 'ok'
                if v84op != v83op:
                    why = 'atlas %s (%s); %+d, re-derived from the v84 IDB dispatch' % (
                        src['op'], src['provenance'], v84op - v83op)
                elif v83op <= 0x3E:
                    why = 'atlas %s (%s); inside the 0x00-0x3E band ticket 20 proved unchanged' % (
                        src['op'], src['provenance'])
                else:
                    why = 'atlas %s (%s); unchanged, and the live v84 routing table puts it here too' % (
                        src['op'], src['provenance'])
            elif adj['verdict'] == 'CORRECTED':
                why = 'atlas %s; %s' % (src['op'], adj['reason'])
                st = 'corrected' if v84op != v83op else 'confirmed'
            else:
                why = 'atlas %s; UNRESOLVED: %s' % (src['op'], adj['reason'])
                st = 'unresolved'
                v84op = UNRESOLVED_SENTINEL
            out.append((name, v83op, v84op, st, why))
            continue

        # no atlas row at this v83 opcode
        if v83op > 0x0200:
            out.append((name, v83op, v83op, 'cosmic-internal',
                        'Cosmic-internal sentinel, never on the wire; carried over verbatim'))
            cosmic_unmatched.append((kind, name, v83op, 'kept verbatim (Cosmic-internal sentinel)'))
            continue
        vo = v84_only.get((D, name))
        if vo is not None:
            out.append((name, v83op, vo['opcode'], 'corrected',
                        'no atlas v83 row; atlas v84 has a row of this exact name at 0x%X' % vo['opcode']))
            atlas_used.add((D, name))
            cosmic_unmatched.append((kind, name, v83op,
                                     'resolved to 0x%X by name against a v84-only atlas row' % vo['opcode']))
            continue
        lo, hi = near(ANCHORS[D], v83op)
        pred = v83op + lo if (lo is not None and hi is not None and lo == hi) else None
        occupied = pred is not None and any(v == pred for (d, _), v in final.items() if d == D)
        if pred is not None and not occupied:
            out.append((name, v83op, pred, 'corrected',
                        'no atlas row at v83 0x%X; shift +%d between IDB anchors, 0x%X vacant' % (v83op, lo, pred)))
            cosmic_unmatched.append((kind, name, v83op,
                                     'resolved to 0x%X by the shift curve (+%d), target vacant' % (pred, lo)))
        else:
            reason = ('shift curve ambiguous here: anchors below +%s, above +%s' % (lo, hi)) if not occupied \
                     else ('shift +%d predicts 0x%X, already taken' % (lo, pred))
            out.append((name, v83op, UNRESOLVED_SENTINEL, 'unresolved',
                        'no atlas row at v83 0x%X; %s' % (v83op, reason)))
            cosmic_unmatched.append((kind, name, v83op, 'UNRESOLVED - %s' % reason))
    rows[kind] = out

# ---- checks --------------------------------------------------------------------------
problems = []
for kind, out in rows.items():
    for name, v83op, v84op, st, why in out:
        if v83op <= 0x3E and v84op != v83op:
            problems.append('LOW BAND MOVED: %s %s 0x%X -> 0x%X' % (kind, name, v83op, v84op))
        if not (0 <= v84op <= 0xFFFF):
            problems.append('OUT OF RANGE: %s %s' % (kind, name))
    # a v84 collision is only real if the two keys did NOT already share a v83 opcode
    # (Cosmic's WEDDING_TALK / WEDDING_TALK_MORE are both 0x8B in v83 and stay paired)
    seen = collections.defaultdict(set)
    for name, v83op, v84op, st, why in out:
        if v84op != UNRESOLVED_SENTINEL and st != 'cosmic-internal':
            seen[v84op].add((name, v83op))
    for v, names in sorted(seen.items()):
        if len({o for _, o in names}) > 1:
            problems.append('COLLISION: %s 0x%X <- %s' % (kind, v, sorted(n for n, _ in names)))

# ---- write properties ----------------------------------------------------------------
HEAD = """# %(kind)sops for GMS v84 - derived from the Chronicle20/atlas gms_v84 registry
# (D:\\games\\MSv84\\opcodes\\gms_v84.yaml) cross-checked against the live v84 routing table
# template_gms_84_1.json and the v84 IDB export, then joined to Cosmic's keys by v83 opcode
# position. Loaded only under -Dopcode-version=84; the default is still 83.
#
# Entries marked UNRESOLVED carry 0xFFFF, a value that never appears on the wire, rather
# than a v83 value that would be silently wrong. See docs/work-plan/v84-opcode-diff.md.
"""
os.makedirs(OUT, exist_ok=True)
for kind in ('send', 'recv'):
    buf = io.StringIO()
    buf.write(HEAD % {'kind': kind})
    for name, v83op, v84op, st, why in rows[kind]:
        if st == 'unresolved':
            buf.write('# UNRESOLVED (%s): %s\n' % (name, why))
        buf.write('%s = 0x%02X\n' % (name, v84op))
    open(os.path.join(OUT, '%sops-84.properties' % kind), 'w', encoding='utf-8', newline='\n').write(buf.getvalue())

# ---- diff report ---------------------------------------------------------------------
atlas_unmatched = [(r['direction'], r['op'], r['opcode']) for r in v84rows if key(r) not in atlas_used]
counts = {k: collections.Counter(x[3] for x in v) for k, v in rows.items()}

d = io.StringIO()
d.write('# v83 -> v84 opcode diff (ticket 21)\n\n')
d.write('Generated from the Chronicle20/atlas `gms_v84` registry, cross-checked against the live v84\n'
        'routing table `template_gms_84_1.json` and the v84 IDB export. Cosmic keys are joined to atlas\n'
        'rows by **v83 opcode position**, not by name.\n\n')
d.write('| direction | keys | corrected | confirmed/unchanged | unresolved | cosmic-internal |\n|---|---|---|---|---|---|\n')
for kind in ('send', 'recv'):
    c = counts[kind]
    d.write('| %s (%s) | %d | %d | %d | %d | %d |\n' % (
        kind, DIR[kind], len(rows[kind]), c['corrected'], c['ok'] + c['confirmed'],
        c['unresolved'], c['cosmic-internal']))

d.write('\n## Registry adjudication\n\n')
d.write('Rows in `gms_v84.yaml` above opcode `0x3E` whose v84 value still equalled the v83 value -\n'
        'the delta=0 islands in an otherwise monotonically rising shift curve. `provenance` does *not*\n'
        'identify these (task-100 reshifted 188 rows without updating it), the curve does.\n\n')
d.write('| | clientbound | serverbound | total |\n|---|---|---|---|\n')
for label, pred in (('stale rows found', lambda r: True),
                    ('corrected', lambda r: r['verdict'] == 'CORRECTED' and r['pred'] != r['yaml84']),
                    ('confirmed unchanged', lambda r: r['verdict'] == 'CORRECTED' and r['pred'] == r['yaml84']),
                    ('UNRESOLVED', lambda r: r['verdict'] == 'UNRESOLVED')):
    cb = sum(1 for r in STALE.values() if r['dir'] == 'clientbound' and pred(r))
    sb = sum(1 for r in STALE.values() if r['dir'] == 'serverbound' and pred(r))
    d.write('| %s | %d | %d | %d |\n' % (label, cb, sb, cb + sb))

for kind in ('send', 'recv'):
    d.write('\n## %sops (%s) - %d keys\n\n' % (kind, DIR[kind], len(rows[kind])))
    d.write('| key | v83 | v84 | delta | status | evidence |\n|---|---|---|---|---|---|\n')
    for name, v83op, v84op, st, why in rows[kind]:
        delta = '' if st in ('unresolved', 'cosmic-internal') else '%+d' % (v84op - v83op)
        d.write('| `%s` | `0x%02X` | `0x%02X` | %s | %s | %s |\n' % (name, v83op, v84op, delta, st, why))

d.write('\n## Registry rows left UNRESOLVED (%d)\n\n' % len(
    [r for r in STALE.values() if r['verdict'] == 'UNRESOLVED']))
d.write('Stale atlas rows the evidence could not pin down. Every Cosmic key that lands on one of\n'
        'these carries `0xFFFF` in the emitted table.\n\n')
d.write('| direction | atlas op | v83 | why not resolved |\n|---|---|---|---|\n')
for r in sorted(STALE.values(), key=lambda x: (x['dir'], x['v83'])):
    if r['verdict'] == 'UNRESOLVED':
        d.write('| %s | `%s` | `0x%02X` | %s |\n' % (r['dir'], r['op'], r['v83'], r['reason']))

d.write('\n## Unmatched: Cosmic keys with no atlas row\n\n')
if cosmic_unmatched:
    d.write('| table | key | v83 | outcome |\n|---|---|---|---|\n')
    for kind, name, v83op, outcome in cosmic_unmatched:
        d.write('| %sops | `%s` | `0x%02X` | %s |\n' % (kind, name, v83op, outcome))
else:
    d.write('_none_\n')

d.write('\n## Unmatched: atlas rows with no Cosmic key (%d)\n\n' % len(atlas_unmatched))
d.write('| direction | atlas op | v84 opcode |\n|---|---|---|\n')
for dr, op, o in sorted(atlas_unmatched):
    d.write('| %s | `%s` | `0x%02X` |\n' % (dr, op, final[(dr, op)]))

open(os.path.join(DOC, 'v84-opcode-diff.md'), 'w', encoding='utf-8', newline='\n').write(d.getvalue())

print('problems:', problems or 'none')
for kind in ('send', 'recv'):
    print(kind, len(rows[kind]), dict(counts[kind]))
print('cosmic keys unmatched:', cosmic_unmatched)
print('atlas rows unmatched:', len(atlas_unmatched))
print('SET_FIELD ->', hex(dict((n, v) for n, _, v, _, _ in rows['send'])['SET_FIELD']))
print('SERVERMESSAGE ->', hex(dict((n, v) for n, _, v, _, _ in rows['send'])['SERVERMESSAGE']))
