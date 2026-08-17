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


def test_archive_hooks_match_dumps():
    """The v2 archive hooks are hand-written addresses, not generated rows.

    Nothing upstream of the compiler checks them, so check them here: every address in
    archive.h's kHdHooks (plus the ZXString::Assign it calls) must still read exactly the
    five guard bytes recorded beside it, in BOTH v84 dumps. If this fails, the DLL will
    refuse to hook at runtime -- which is safe, but silent -- so fail loudly offline.
    """
    hdr = os.path.join(paths.HD, 'loader', 'archive.h')
    if not os.path.exists(hdr):
        print('  archive hooks: SKIPPED (no archive.h)')
        return
    src = open(hdr).read()
    rows = re.findall(r'\{\s*"([^"]+)",\s*(0x[0-9A-Fa-f]+),\s*\{([^}]*)\}', src)
    assign = re.search(r'g_Assign\s*=\s*\(Assign_t\)(0x[0-9A-Fa-f]+)', src)
    kassign = re.search(r'kAssign\[5\]\s*=\s*\{([^}]*)\}', src)
    assert rows, 'no kHdHooks rows parsed out of archive.h'
    assert assign and kassign, 'could not parse the ZXString::Assign address/guard'
    rows.append(('ZXString<char>::Assign', assign.group(1), kassign.group(1)))

    a, b = paths.load(paths.V84_A), paths.load(paths.V84_B)
    for name, addr, byts in rows:
        va = int(addr, 16)
        exp = bytes(int(x, 16) for x in byts.replace(' ', '').split(',') if x)
        assert len(exp) == 5, f'{name}: guard must be 5 bytes, got {len(exp)}'
        o = va - paths.BASE
        assert a[o:o + 5] == exp, \
            f'{name} @0x{va:08X}: dump A reads {a[o:o+5].hex()}, archive.h says {exp.hex()}'
        assert b[o:o + 5] == exp, \
            f'{name} @0x{va:08X}: dump B reads {b[o:o+5].hex()}, archive.h says {exp.hex()}'
    print(f'  archive hooks: ok ({len(rows)} addresses, guard bytes match both dumps)')


def test_archive_mount_site():
    """The mount is a 4-byte operand write, so its guard has to be exactly right.

    MountArchive() overwrites the operand of `push offset L"Base.wz"` at 0x00A405CB. Two
    things must hold in BOTH dumps or the write lands somewhere it should not:
      1. the five bytes at the patch site really are that push, and
      2. the operand really points at L"Base.wz" -- 16 bytes of UTF-16LE.
    Check (2) is what catches a guard that was transcribed from the wrong dump: the push
    encoding is common enough to collide, the string it points at is not.
    """
    hdr = os.path.join(paths.HD, 'loader', 'archive.h')
    if not os.path.exists(hdr):
        print('  archive mount: SKIPPED (no archive.h)')
        return
    src = open(hdr).read()
    guard = re.search(r'kPushBaseWz\[5\]\s*=\s*\{([^}]*)\}', src)
    site = re.search(r'memcmp\(\(const void\*\)(0x[0-9A-Fa-f]+), kPushBaseWz', src)
    poke = re.search(r'Poke\((0x[0-9A-Fa-f]+), &p, 4\)', src)
    assert guard and site and poke, 'could not parse the mount site out of archive.h'
    exp = bytes(int(x, 16) for x in guard.group(1).replace(' ', '').split(',') if x)
    va, wr = int(site.group(1), 16), int(poke.group(1), 16)
    assert len(exp) == 5, f'mount guard must be 5 bytes, got {len(exp)}'
    assert wr == va + 1, f'operand write 0x{wr:08X} must be guard site 0x{va:08X} + 1'
    assert exp[0] == 0x68, f'mount guard is not a push imm32: {exp.hex()}'

    target = int.from_bytes(exp[1:5], 'little')
    want = 'Base.wz'.encode('utf-16-le') + b'\x00\x00'
    for tag, buf in (('A', paths.load(paths.V84_A)), ('B', paths.load(paths.V84_B))):
        o = va - paths.BASE
        assert buf[o:o + 5] == exp, \
            f'mount @0x{va:08X}: dump {tag} reads {buf[o:o+5].hex()}, archive.h says {exp.hex()}'
        t = target - paths.BASE
        assert buf[t:t + len(want)] == want, \
            f'mount operand 0x{target:08X}: dump {tag} does not hold L"Base.wz"'
    print(f'  archive mount: ok (0x{va:08X} push -> 0x{target:08X} L"Base.wz", both dumps)')


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
    test_archive_hooks_match_dumps()
    test_archive_mount_site()
    print('OK')
