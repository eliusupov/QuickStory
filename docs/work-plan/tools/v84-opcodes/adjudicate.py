"""Adjudicate the stale rows in gms_v84.yaml (v84 opcode still == v83 opcode above 0x3E).

Instrument
----------
The v84 opcode shift is monotonic non-decreasing in the v83 opcode (discover_gms_v84.md
derives it from IDB switch-case constants). A row whose v84 value was re-derived from the
IDB is an ANCHOR; a row still carrying its v83 value shows up as a delta=0 island in an
otherwise rising curve. That island, not the `provenance` field, is what identifies a
stale row -- task-100 reshifted 188 rows without updating provenance, so provenance alone
finds the wrong set.

Anchors, in order of authority:
  P1  rows at opcode <= 0x3E with delta 0   -- proven unshifted (ticket 20 reached login)
  P2  rows where v84 != v83                 -- re-derived from the v84 IDB by task-100
  S   rows the live v84 routing table (template_gms_84_1.json) puts at the yaml's opcode
      via an fname unique to one opcode. Independent of the registry, and the only thing
      that proves the serverbound 0x3F-0x75 band genuinely did not shift. Accepted only
      where it does not contradict the P1/P2 envelope -- a template row claiming delta 0
      inside a +7 region is rejected as evidence, not believed.

A stale row is CORRECTED only when its nearest anchor below and nearest anchor above
agree on a delta, the target slot is free once every correction is applied, and the
template does not disagree. Everything else is UNRESOLVED. No guessing.
"""
from lib import *

v84rows = parse_yaml('gms_v84.yaml')
v83map = {key(r): r for r in parse_yaml('gms_v83.yaml')}
cb, sb = load_template()

_tpl = collections.defaultdict(set)
for op, fn, w in cb:
    if fn: _tpl[('clientbound', fn)].add(op)
for op, fn, w in sb:
    if fn: _tpl[('serverbound', fn)].add(op)
# an fname serving several opcodes (CUser::OnChat covers CHATTEXT and CHATTEXT1,
# CUIFadeYesNo::OnButtonClicked covers half the confirm dialogs) cannot pin one opcode
_reg_fname = collections.Counter((r['direction'], r.get('fname', '')) for r in v84rows)
tpl_unique = {k: sorted(v)[0] for k, v in _tpl.items() if len(v) == 1 and _reg_fname[k] == 1}
def tpl_op(r): return tpl_unique.get((r['direction'], r.get('fname', '')))

results = []
final = {}
ANCHORS = {}
rejected_tpl_anchors = []
monotonicity_violations = []

def near(anchor_list, x):
    lo = [(o, d) for o, d in anchor_list if o < x]
    hi = [(o, d) for o, d in anchor_list if o > x]
    return (lo[-1][1] if lo else None), (hi[0][1] if hi else None)

for D in ('clientbound', 'serverbound'):
    rows = [r for r in v84rows if r['direction'] == D]
    primary, candidates = [], []
    for r in rows:
        v = v83map.get(key(r))
        final[key(r)] = r['opcode']
        if v is None:
            continue                       # v84-only row: nothing to compare against
        o83, dl = v['opcode'], r['opcode'] - v['opcode']
        if dl != 0 or o83 <= 0x3E:
            primary.append((o83, dl))
        else:
            candidates.append((r, o83))
    primary.sort()

    prev_o = prev_d = None
    for o, d in primary:
        if prev_d is not None and d < prev_d:
            monotonicity_violations.append((D, hex(prev_o), prev_d, hex(o), d))
        prev_o, prev_d = o, d

    # promote template-confirmed rows to anchors, but only where they do not contradict
    # the primary envelope (nearest primary below must already be at delta 0)
    anchors = list(primary)
    stale = []
    for r, o83 in candidates:
        if tpl_op(r) == r['opcode']:
            lo, hi = near(primary, o83)
            if (lo in (None, 0)) and (hi is None or hi >= 0):
                anchors.append((o83, 0))
                continue
            rejected_tpl_anchors.append((D, r['op'], hex(o83), lo, hi))
        stale.append((r, o83))
    anchors.sort()
    ANCHORS[D] = anchors

    pending = []
    for r, o83 in stale:
        lo, hi = near(anchors, o83)
        rec = {'dir': D, 'op': r['op'], 'v83': o83, 'yaml84': r['opcode'],
               'fname': r.get('fname', ''), 'prov': r.get('provenance', ''),
               'lo': lo, 'hi': hi, 'tpl': tpl_op(r), 'pred': None}
        if lo is None or hi is None or lo != hi:
            rec['verdict'] = 'UNRESOLVED'
            rec['reason'] = 'shift curve ambiguous: nearest anchor below is +%s, above is +%s' % (lo, hi)
        else:
            rec['pred'] = o83 + lo
        pending.append((rec, r))

    # occupancy judged against the post-correction map, so a run of stale rows all moving
    # up by the same amount does not report itself as a pile of collisions
    taken = collections.defaultdict(list)
    for k, v in final.items():
        if k[0] == D: taken[v].append(k[1])
    for rec, r in pending:
        if rec['pred'] is not None:
            taken[r['opcode']].remove(r['op'])
            taken[rec['pred']].append(r['op'])

    for rec, r in pending:
        if rec['pred'] is None:
            continue
        others = [o for o in taken[rec['pred']] if o != r['op']]
        if others:
            rec['verdict'] = 'UNRESOLVED'
            rec['reason'] = 'shift +%d predicts 0x%X but %s already holds it' % (
                rec['lo'], rec['pred'], ', '.join(others))
        elif rec['tpl'] == rec['v83'] and rec['lo'] != 0:
            # the template row is sitting at this op's *v83* opcode inside a region the IDB
            # says shifted -- that is the same staleness the registry has, so the template
            # is not evidence here. (Kites: the template puts SpawnKite at its v83 0x10F yet
            # puts its sibling DestroyKite at the IDB-derived 0x117, contradicting itself.)
            rec['verdict'] = 'CORRECTED'
            rec['reason'] = 'shift +%d between IDB anchors; 0x%X vacant (template still carries the v83 value here, so it is stale too, not evidence)' % (rec['lo'], rec['pred'])
            final[key(r)] = rec['pred']
        elif rec['tpl'] is not None and rec['tpl'] != rec['pred']:
            rec['verdict'] = 'UNRESOLVED'
            rec['reason'] = 'live v84 template says 0x%X, shift curve says 0x%X' % (rec['tpl'], rec['pred'])
        else:
            rec['verdict'] = 'CORRECTED'
            rec['reason'] = 'shift +%d between IDB anchors; 0x%X vacant%s' % (
                rec['lo'], rec['pred'], '; template agrees' if rec['tpl'] == rec['pred'] else '')
            final[key(r)] = rec['pred']

    for rec, r in pending: results.append(rec)

STALE = {(r['dir'], r['op']): r for r in results}
CORRECTED = {k: r for k, r in STALE.items() if r['verdict'] == 'CORRECTED'}
UNRESOLVED = {k: r for k, r in STALE.items() if r['verdict'] == 'UNRESOLVED'}

if __name__ == '__main__':
    print('primary-anchor monotonicity violations:', monotonicity_violations or 'none')
    print('template anchors rejected as contradicting the IDB envelope:')
    for x in rejected_tpl_anchors: print('   ', x)
    print()
    print('stale rows %d -> corrected %d, unresolved %d' % (len(results), len(CORRECTED), len(UNRESOLVED)))
    print(collections.Counter((r['dir'], r['verdict']) for r in results))
    print()
    r = STALE[('clientbound', 'SERVERMESSAGE')]
    print('SMOKE TEST SERVERMESSAGE:', r['verdict'], hex(r['yaml84']), '->',
          hex(r['pred']) if r['pred'] else None, '|', r['reason'])
    print()
    print('--- CORRECTED (%d)' % len(CORRECTED))
    for r in results:
        if r['verdict'] == 'CORRECTED':
            print('  %-12s %-38s 0x%X -> 0x%X   %s' % (r['dir'], r['op'], r['yaml84'], r['pred'], r['reason']))
    print()
    print('--- UNRESOLVED (%d)' % len(UNRESOLVED))
    for r in results:
        if r['verdict'] == 'UNRESOLVED':
            print('  %-12s %-38s stays 0x%X   %s' % (r['dir'], r['op'], r['yaml84'], r['reason']))
