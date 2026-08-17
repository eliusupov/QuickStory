#!/usr/bin/env python3
"""Turn CONFLICTS-136.tsv into merge lists for the owner's "Maximum v84 parity" decision.

Policy, decided by the owner on 2026-08-17 (see CONFLICT-RESOLUTION.md):
  A + B (79 rows)  -> merge both sides. The two edits are disjoint, so his fields are
                      written on top of a tree that already carries v84's.
  C, collidingFields == "info/level" on a Character.wz equip (17 rows) -> keep HIS.
  C, everything else (23 rows) -> take v84  => NO ROW EMITTED (the tree already is v84).
  D (17 rows)      -> take v84                => NO ROW EMITTED.

Every emitted row is also force-listed: his field usually already exists in the target
holding v83's (== v84's, since v84 did not move it), and additive-only would refuse it.
`--force` is consulted only when the node already exists, so force-listing a row that is a
genuine addition costs nothing.

Writes <outdir>/<Archive>.paths.txt, <Archive>.force.txt and DECISIONS.tsv.
"""
import csv, os, sys, collections

TSV = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), 'CONFLICTS-136.tsv')
OUT = sys.argv[2] if len(sys.argv) > 2 else r'D:\games\wz-stage\phaseB\conflicts\lists'

CAT_A, CAT_B = 'A DISJOINT at child level', 'B DISJOINT at field level'
CAT_C, CAT_D = 'C TRUE OVERLAP (same field, both moved)', 'D WHOLESALE (overlap too wide to drill)'

rows = list(csv.DictReader(open(TSV, newline='', encoding='utf-8-sig'), delimiter='\t'))
counts = collections.Counter(r['category'] for r in rows)
assert counts[CAT_A] == 8 and counts[CAT_B] == 71 and counts[CAT_C] == 40 and counts[CAT_D] == 17, counts
assert sum(counts.values()) == 136, counts

kept_level = [r for r in rows if r['category'] == CAT_C and r['collidingFields'] == 'info/level']
assert len(kept_level) == 17, f'expected 17 C/info-level rows, got {len(kept_level)}'
assert all(r['image'].startswith('Character.wz/') for r in kept_level), 'a kept row is not Character.wz'

# Rows that are already satisfied in the phase-B tree and must NOT be re-merged.
# Mob.wz/6300005.img/die1: phase B landed the protect row `6300005.img/die1/speak`, and that one
# node was the whole of his edit — `WzMerge hash` gives die1 = 8553f7a6… in BOTH the phase-B tree
# and his client, so re-forcing it changes nothing. It is dropped because forcing it anyway trips a
# tool bug: the pre-save digest of the DeepCloned canvas subtree (69a48b9b…) matches neither the
# source's nor the saved copy's (63c0690c…), so the merge's own content check reports CONTENT
# DRIFT and exits 4 on an output that is provably correct. Reproduce with a one-row probe; the
# written image's seven children are digest-identical to the tree's. Residual of PHASE-B.md §7,
# in the clone path rather than the read path. Remove this entry once the tool is fixed.
SKIP = {'Mob.wz/6300005.img/die1'}


def split(s):
    return [x for x in (s or '').split(',') if x]

paths = collections.defaultdict(list)   # archive -> [(path, decision, image)]
decisions = []

for r in rows:
    cat, img, arch = r['category'], r['image'], r['archive']
    if cat == CAT_A:
        fields, why = split(r['hisChildren']), 'A-MERGE-BOTH'
    elif cat == CAT_B:
        fields, why = split(r['hisOnlyFields']), 'B-MERGE-BOTH'
    elif cat == CAT_C and r['collidingFields'] == 'info/level':
        fields, why = ['info/level'], 'C-KEEP-HIS'
    else:
        why = 'C-TAKE-V84' if cat == CAT_C else 'D-TAKE-V84'
        decisions.append((cat, arch, img, why, '', 'no row emitted; tree already carries v84'))
        continue
    assert fields, f'{cat} row with nothing to merge: {img}'
    for f in fields:
        p = f'{img}/{f}'
        if p in SKIP:
            decisions.append((cat, arch, img, why, p,
                              'SKIPPED: phase-B tree already holds his value here (see SKIP in resolve-conflicts-lists.py)'))
            continue
        paths[arch].append(p)
        decisions.append((cat, arch, img, why, p, ''))

os.makedirs(OUT, exist_ok=True)
for arch, ps in sorted(paths.items()):
    ps = sorted(set(ps))
    hdr = f'# {arch}.wz conflict resolution: {len(ps)} of HIS field/child roots merged onto the v84 tree.'
    open(os.path.join(OUT, f'{arch}.paths.txt'), 'w', encoding='utf-8').write(
        hdr + '\n' + '\n'.join(ps) + '\n')
    open(os.path.join(OUT, f'{arch}.force.txt'), 'w', encoding='utf-8').write(
        hdr.replace('merged onto', 'subtree-replaced onto') + '\n' +
        '\n'.join(f'{p}\t# conflict resolution: his value over v84/v83' for p in ps) + '\n')

with open(os.path.join(OUT, 'DECISIONS.tsv'), 'w', newline='', encoding='utf-8') as fh:
    w = csv.writer(fh, delimiter='\t', lineterminator='\n')
    w.writerow(['category', 'archive', 'image', 'decision', 'mergePath', 'note'])
    w.writerows(decisions)

print(f'{len(rows)} conflicts: ' + ', '.join(f'{k[0]}={v}' for k, v in sorted(counts.items())))
print(f'kept-his C/info/level rows: {len(kept_level)}')
emitted = sum(len(v) for v in paths.values())
print(f'emitted {emitted} merge rows over {len(paths)} archives: ' +
      ', '.join(f'{a}={len(set(p))}' for a, p in sorted(paths.items())))
print(f'take-v84 rows (nothing emitted): {sum(1 for d in decisions if d[3].endswith("TAKE-V84"))}')
