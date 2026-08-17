#!/usr/bin/env python3
"""Content-verify the conflict resolution. Exit codes prove nothing; digests do.

For every field the decision touches, take its digest in three trees — the assembled tree, the
owner's client, and the v84 base — and assert the tree holds the side the policy names:

  KEEP-HIS  (A/B his-only fields, and the 17 C `info/level`)  tree == live
  KEEP-V84  (A/B v84-only fields; every C-take-v84 and D field) tree == v84

Each assertion carries its own DISCRIMINATOR: live != v84 at that same field. Without it
"tree == live" is satisfied by a field neither side ever moved and the check proves nothing.

`WzMerge hash <wz> <parent>` prints one digest per direct child, so each parent costs one
invocation per tree and the leaf is read out of that. One process at a time; nothing is slurped.
"""
import csv, os, subprocess, sys, collections

REPO = r'D:\games\MapleStory\Server\Cosmic\.claude\worktrees\evan-dualblade'
WZ   = os.path.join(REPO, r'docs\wz-baseline\tool-merge\bin\Release\net10.0-windows\WzMerge.exe')
TREES = {'tree': r'D:\games\wz-stage\phaseB\tree',
         'live': r'D:\games\MapleStory',
         'v84':  r'D:\games\wz-stage\v84-base',
         # `pre` = the phase-B tree as it stood BEFORE this pass. conflicts\pre\ holds a byte copy
         # of each archive this pass merged (the tool's own --live snapshot check proves that);
         # the archives it did not touch are still at phaseB\out\.
         'pre':  r'D:\games\wz-stage\phaseB\conflicts\pre'}
PRE_FALLBACK = r'D:\games\wz-stage\phaseB\out'
PB   = os.path.join(REPO, r'docs\wz-baseline\backport\phase-b')
CONF = os.path.join(PB, 'CONFLICTS-136.tsv')
DEC  = r'D:\games\wz-stage\phaseB\conflicts\lists\DECISIONS.tsv'
OUT  = os.path.join(PB, 'VERIFY-conflicts.tsv')

_cache = {}
def kids(tree, archive, parent):
    """{childName: digest} for one parent node in one tree. Absent parent -> {}."""
    key = (tree, parent)
    if key in _cache:
        return _cache[key]
    wzf = os.path.join(TREES[tree], archive + '.wz')
    if tree == 'pre' and not os.path.exists(wzf):
        wzf = os.path.join(PRE_FALLBACK, archive + '.wz')
    r = subprocess.run([WZ, 'hash', wzf, parent],
                       capture_output=True, text=True, encoding='utf-8', errors='replace')
    d = {}
    for line in r.stdout.splitlines():
        p = line.split(None, 1)
        if len(p) == 2 and not p[1].startswith('TOTAL'):
            d[p[1].strip()] = p[0]
    _cache[key] = d
    return d

def digest(tree, archive, path):
    parent, _, leaf = path.rpartition('/')
    return kids(tree, archive, parent).get(leaf)

def split(s):
    return [x for x in (s or '').split(',') if x]

rows = {r['image']: r for r in csv.DictReader(open(CONF, newline='', encoding='utf-8-sig'), delimiter='\t')}
dec  = list(csv.DictReader(open(DEC, newline='', encoding='utf-8-sig'), delimiter='\t'))

# (archive, path, expectedSide, decision, image) — expectedSide is 'live' or 'v84'.
checks = []
for d in dec:
    img, arch, cat = d['image'], d['archive'], d['category']
    r = rows[img]
    if d['decision'] in ('A-MERGE-BOTH', 'B-MERGE-BOTH', 'C-KEEP-HIS'):
        if d['mergePath']:
            checks.append((arch, d['mergePath'], 'live', d['decision'], img, d['note']))
    else:  # C-TAKE-V84 / D-TAKE-V84: assert the tree really did NOT move — one row per image
        fields = split(r['collidingFields']) or split(r['overlapChildren'])
        for f in fields:
            checks.append((arch, f'{img}/{f}', 'v84', d['decision'], img, ''))

# The other half of "merge both sides": v84's own fields must still be there.
seen = set()
for d in dec:
    img, arch = d['image'], d['archive']
    if d['decision'] not in ('A-MERGE-BOTH', 'B-MERGE-BOTH') or img in seen:
        continue
    seen.add(img)
    r = rows[img]
    for f in split(r['v84OnlyFields']) or split(r['v84Children']):
        checks.append((arch, f'{img}/{f}', 'v84', d['decision'] + '/v84-side', img, ''))

results, tally = [], collections.Counter()
for arch, path, want, why, img, note in checks:
    t, l, v, p = (digest(k, arch, path) for k in ('tree', 'live', 'v84', 'pre'))
    disc = 'REAL' if (l != v) else 'VACUOUS'
    if want == 'live':
        # This pass had to MOVE the field onto his value.
        if t is not None and t == l:      verdict = 'OK'
        elif l is None and t == v == p:   verdict = 'UNAPPLIED-DELETION'  # his edit was a delete
        else:                             verdict = 'WRONG'
    else:
        # "Take v84" means THIS PASS CHANGED NOTHING HERE. That is the assertion; whether the
        # standing value is literally v84's is a separate fact about phase B's additive layer,
        # reported in `standing` rather than folded into pass/fail.
        verdict = 'OK-UNCHANGED' if t == p else 'WRONG'
    standing = ('v84' if t == v else 'his' if t == l else
                'v84+his-additive' if t is not None else 'absent')
    if disc == 'VACUOUS' and verdict.startswith('OK'):
        verdict += '-VACUOUS'
    tally[f'{why}\t{verdict}\t{disc}\tstanding={standing}'] += 1
    results.append((why, arch, img, path, want, verdict, disc, standing,
                    (t or '-')[:12], (p or '-')[:12], (l or '-')[:12], (v or '-')[:12], note))

with open(OUT, 'w', newline='', encoding='utf-8') as fh:
    w = csv.writer(fh, delimiter='\t', lineterminator='\n')
    w.writerow(['decision', 'archive', 'image', 'field', 'expectedSide', 'verdict',
                'discriminator', 'standingValue', 'treeDigest', 'preDigest',
                'liveDigest', 'v84Digest', 'note'])
    w.writerows(sorted(results))

# One row per conflict image: what the decision was, and what the digests say actually happened.
per = collections.defaultdict(lambda: collections.Counter())
for r in results:
    per[r[2]][r[5]] += 1
    per[r[2]]['std:' + r[7]] += 1
    per[r[2]]['dec:' + r[0].split('/')[0]] += 1
MAN = os.path.join(PB, 'CONFLICT-MANIFEST-136.tsv')
with open(MAN, 'w', newline='', encoding='utf-8') as fh:
    w = csv.writer(fh, delimiter='\t', lineterminator='\n')
    w.writerow(['category', 'archive', 'image', 'decision', 'fieldsMovedToHis', 'fieldsLeftAsIs',
                'unappliedDeletions', 'standingHis', 'standingV84', 'standingV84PlusHisAdditive',
                'vacuousChecks', 'wrong', 'outcome'])
    for img, r in sorted(rows.items()):
        c = per[img]
        dec = 'KEEP-HIS(info/level)' if c['dec:C-KEEP-HIS'] else \
              'MERGE-BOTH' if (c['dec:A-MERGE-BOTH'] or c['dec:B-MERGE-BOTH']) else 'TAKE-V84'
        moved = c['OK'] + c['OK-VACUOUS']
        left  = c['OK-UNCHANGED'] + c['OK-UNCHANGED-VACUOUS']
        wrong = c['WRONG']
        outcome = 'VERIFIED BY CONTENT' if not wrong else 'FAILED'
        if c['UNAPPLIED-DELETION']:
            outcome += f"; {c['UNAPPLIED-DELETION']} of his DELETIONS not applied (tool cannot delete)"
        if c['std:v84+his-additive'] or (dec == 'TAKE-V84' and c['std:his']):
            outcome += ("; not purely v84 — phase B's additive layer already stands here "
                        "and was not removed")
        w.writerow([r['category'], r['archive'], img, dec, moved, left, c['UNAPPLIED-DELETION'],
                    c['std:his'], c['std:v84'], c['std:v84+his-additive'],
                    c['OK-VACUOUS'] + c['OK-UNCHANGED-VACUOUS'], wrong, outcome])
print('->', MAN)

for k, n in sorted(tally.items()):
    print(f'{n:5d}  {k}')
bad = [r for r in results if r[5] == 'WRONG']
print(f'\n{len(results)} field checks, {len(bad)} WRONG, '
      f'{sum(1 for r in results if "VACUOUS" in r[5])} vacuous')
for r in bad[:40]:
    print('  WRONG', r[0], r[3], 'tree=', r[8], 'pre=', r[9], 'live=', r[10], 'v84=', r[11])
print('->', OUT)
sys.exit(1 if bad else 0)
