"""Self-check for the HD port tooling. `python tools/hd/test_hd.py`

Small and assert-based on purpose. It guards the three things that would silently
produce a client-corrupting patch set:
  1. the resolution-formula fit must reproduce the source's own values exactly
  2. the write classifier must still flag the three known Ezorsia source bugs
  3. the shipped patch set must contain no FAIL rows and no duplicate v84 addresses
Checks 2 and 3 need the v83/v84 images; they skip (loudly) if those are absent.
"""
import json
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


if __name__ == '__main__':
    test_fit()
    have = all(os.path.exists(p) for p in (paths.V83, paths.V84_A, paths.EZORSIA))
    if not have:
        print('  SKIPPED image-backed checks (set HD_V83 / HD_V84_A / HD_EZORSIA)')
        sys.exit(0)
    test_formulas_match_source()
    test_known_source_bugs()
    test_patchset_clean()
    print('OK')
