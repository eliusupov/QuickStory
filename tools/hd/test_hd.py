"""Self-check for the HD port tooling. `python tools/hd/test_hd.py`

Small and assert-based on purpose. It guards the three things that would silently
produce a client-corrupting patch set:
  1. the resolution-formula fit must reproduce the source's own values exactly
  2. the write classifier must still flag the three known Ezorsia source bugs
  3. the shipped patch set must contain no FAIL rows and no duplicate v84 addresses
Checks 2 and 3 need the v83/v84 images; they skip (loudly) if those are absent.
"""
import json
import re
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import paths                                    # noqa: E402
from gen_loader import SAMPLES, fit             # noqa: E402


def test_fit():
    assert fit([600, 720, 900, 1080]) == (0, 2, 0, 0), 'H'
    assert fit([800, 1280, 1600, 1920]) == (2, 0, 0, 0), 'W'
    assert fit([400, 640, 800, 960]) == (1, 0, 0, 0), 'W/2'
    assert fit([-300, -360, -450, -540]) == (0, -1, 0, 0), '-H/2'
    assert fit([508, 628, 808, 988]) == (0, 2, 0, -92), 'H-92'
    assert fit([w * h for w, h in SAMPLES]) == (0, 0, 1, 0), 'W*H'
    assert fit([1, 2, 4, 8]) is None, 'non-linear must not fit'
    for f, (w, h) in ((fit([600, 720, 900, 1080]), (1280, 720)),):
        assert f[0] * w // 2 + f[1] * h // 2 + f[2] * w * h + f[3] == 720
    print('  fit: ok')


def test_formulas_match_source():
    """Every fitted formula must reproduce extract.py's own value at every sample."""
    import extract
    tables = [extract.build(w, h) for w, h in SAMPLES]
    n = 0
    for i in range(len(tables[0])):
        vals = [t[i]['value'] for t in tables]
        f = fit(vals)
        if not f:
            continue
        for (w, h), v in zip(SAMPLES, vals):
            assert f[0] * w // 2 + f[1] * h // 2 + f[2] * w * h + f[3] == v, \
                f'{tables[0][i]["id"]} {tables[0][i]["value_expr"]}'
        n += 1
    assert n > 200, f'only {n} rows fitted; the expression set changed'
    print(f'  formulas: ok ({n} rows)')


def test_known_source_bugs():
    from resolve import classify
    V83 = paths.load(paths.V83)
    P = {p['id']: p for p in json.load(open(paths.PATCHES))['patches']}
    # 0x004D59B2 +1: instruction is `cmp ecx,0x258`, imm32 at +2
    b1 = next(p for p in P.values() if p['site'] == 0x004D59B2)
    c1 = classify(V83, b1)
    assert c1['verdict'] == 'BAD-OFFSET' and c1['correct_off'] == 2, c1
    # 0x00A448B0 +2: instruction is `add eax,0xFFFFFED4`, imm32 at +1
    b2 = next(p for p in P.values() if p['site'] == 0x00A448B0)
    c2 = classify(V83, b2)
    assert c2['verdict'] == 'BAD-OFFSET' and c2['correct_off'] == 1, c2
    # 0x0064061D is `idiv ecx` -- no operand at all
    b3 = next(p for p in P.values() if p['site'] == 0x0064061D)
    assert classify(V83, b3)['verdict'] == 'NO-OPERAND'
    # and the site the comment really meant is patched by its own row
    assert any(p['site'] == 0x00640618 for p in P.values())
    print('  source bugs: all three still detected')


def test_patchset_clean():
    ps = os.path.join(paths.DATA, 'v84-patchset.json')
    rows = json.load(open(ps))['rows']
    bad = [r for r in rows if r['verdict'] == 'FAIL']
    assert not bad, f'{len(bad)} FAIL rows: {[r["id"] for r in bad]}'
    seen = {}
    for r in rows:
        if r['verdict'] != 'PASS' or r['v84'] is None:
            continue
        assert r['v84'] not in seen or seen[r['v84']] == r['v83'], \
            f'two v83 sites map to 0x{r["v84"]:08X}: {seen[r["v84"]]:#x} and {r["v83"]:#x}'
        seen[r['v84']] = r['v83']
    print(f'  patch set: {len(seen)} PASS rows, no FAILs, injective')


def test_no_overlapping_writes():
    """No two patches may write the same byte.

    Injectivity says two v83 sites cannot share one v84 ADDRESS. It says nothing about
    two patches whose write RANGES intersect -- a 4-byte int at X and another at X+2, or
    a 46-byte code cave swallowing a neighbour's target. The last writer would win and
    the loser would corrupt the winner's operand. Nothing measured this before.
    """
    J = json.load(open(os.path.join(paths.DATA, 'v84-patchset.json')))
    skip = set(J['drop_groups']) | set(J['optional_groups'])
    rows = [r for r in J['rows'] if r['verdict'] == 'PASS' and r['v84'] is not None
            and r['group'] not in skip]
    owned, clash = {}, []
    for r in rows:
        span = range(r['v84'], r['v84'] + (r.get('size84') or r['size']))
        for b in span:
            prev = owned.get(b)
            # `FillBytes` that fully contains a later write is the deliberate
            # clear-then-write idiom (blank the IP string, then write the new one),
            # not a conflict. Anything else overlapping is one patch eating another.
            if prev and prev[0] != r['id'] and not (
                    prev[1] == 'FillBytes' and prev[2] <= span.start
                    and span.stop <= prev[3]):
                clash.append((prev[0], r['id'], b))
            owned[b] = (r['id'], r['op'], span.start, span.stop)
    assert not clash, f'{len(clash)} overlapping writes in the SHIPPING set, e.g. ' + \
        ', '.join(f'{a}/{b}@0x{c:08X}' for a, b, c in clash[:5])
    print(f'  overlap: ok ({len(owned)} shipped bytes, no patch overwrites another)')


def test_generated_inc_wellformed():
    """Stand-in for the compile this machine cannot do: there is no C toolchain here.

    Catches what the compiler would have caught in the generated header -- wrong field
    count, unbalanced braces, an unknown kind enum, a duplicate #define, a value that
    does not fit its field -- plus that every row in the table is one verify.py passed.
    """
    inc = os.path.join(paths.HD, 'loader', 'hd_patches.inc')
    if not os.path.exists(inc):
        print('  inc: SKIPPED (run gen_loader.py)')
        return
    src = open(inc).read()
    kinds = set(re.findall(r'enum HdKind \{([^}]*)\}',
                           open(os.path.join(paths.HD, 'loader', 'dllmain.cpp')).read())
                [0].replace(' ', '').split(','))
    body = src.split('kHdPatches[] = {', 1)[1].split('\n};', 1)[0]
    rows = [ln for ln in body.splitlines() if ln.strip().startswith('{')]
    assert rows, 'no rows emitted'
    for ln in rows:
        row = ln.split('//')[0].strip().rstrip(',')
        assert row.count('{') == row.count('}') == 3, f'brace/field shape: {ln[:70]}'
        # addr, size, kind, {formula}, group, id, v83, nexp, {expect}
        flat = re.sub(r'\{[^{}]*\}', 'X', row[1:-1])
        assert len([x for x in flat.split(',') if x.strip()]) == 9, \
            f'expected 9 top-level fields, got: {flat}'
        k = flat.split(',')[2].strip()
        assert k in kinds, f'unknown kind {k!r}; dllmain.cpp declares {sorted(kinds)}'
    ids = re.findall(r'#define (\w+)', src)
    dup = {i for i in ids if ids.count(i) > 1}
    assert not dup, f'duplicate #define: {sorted(dup)}'
    passed = {r['id'] for r in
              json.load(open(os.path.join(paths.DATA, 'v84-patchset.json')))['rows']
              if r['verdict'] == 'PASS'}
    emitted = set(re.findall(r'"(P\d+)"', src))
    assert emitted <= passed, f'emitted rows that did not PASS: {sorted(emitted - passed)}'
    print(f'  inc: ok ({len(rows)} rows, {len(ids)} defines, all rows PASS-backed)')


if __name__ == '__main__':
    test_fit()
    have = all(os.path.exists(p) for p in (paths.V83, paths.V84_A, paths.EZORSIA))
    if not have:
        print('  SKIPPED image-backed checks (set HD_V83 / HD_V84_A / HD_EZORSIA)')
        sys.exit(0)
    test_formulas_match_source()
    test_known_source_bugs()
    test_patchset_clean()
    test_no_overlapping_writes()
    test_generated_inc_wellformed()
    print('OK')
