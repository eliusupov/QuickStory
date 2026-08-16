"""Offline verification harness for the v84 HD patch set. No client launch.

For every patch operation it proves, against the two v84 memory dumps:

  ADDR    the v84 target address exists inside the dumped image
  SHAPE   the bytes there decode to the same instruction shape as the v83 site
  SLOT    the write lands exactly on that instruction's immediate/displacement,
          at the right width -- this is the check the three latent Ezorsia bugs
          fail, where a +1 is used and the immediate is really at +2
  CAVE    for code caves: the NOP count covers a whole number of instructions and
          the return address is the instruction boundary right after them
  DUAL    the same bytes appear in the second, independent dump
  FIT     the value being written still fits the operand width

Exit code is non-zero if any patch in the shipping set fails.

    python tools/hd/verify.py           full per-patch table
    python tools/hd/verify.py --fail    only the failures
"""
import collections
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import paths                                    # noqa: E402
from resolve import MD, classify, same_shape, shape   # noqa: E402

# Groups the architecture decision drops: the existing ijl15.dll + edits\ loader
# (bypass / redirect / skip-logo / window-mode / no-patcher / no-ad-balloon) already
# does these on a v84 client that boots today. Porting them means two injection
# paths racing each other.
DROP_GROUPS = {'A', 'B', 'L'}
# INI-driven gameplay caps, not resolution. Optional; verified but not required.
OPTIONAL_GROUPS = {'K'}


def insn_run(img, va, n):
    """Do whole instructions tile exactly n bytes starting at va?"""
    off, tot, seq = va - paths.BASE, 0, []
    while tot < n:
        got = None
        for ins in MD.disasm(img[off + tot:off + tot + 16], va + tot):
            got = ins.size
            seq.append(f'{ins.mnemonic} {ins.op_str}')
            break
        if not got:
            return False, seq
        tot += got
    return tot == n, seq


def fits(value, size):
    if value is None or isinstance(value, (str, list, float)):
        return True
    lo, hi = -(1 << (8 * size - 1)), (1 << (8 * size)) - 1
    return lo <= value <= hi


def main():
    only_fail = '--fail' in sys.argv
    paths.require(paths.V83, paths.V84_A, paths.V84_B, paths.PATCHES, paths.RESOLVED)
    V83, V84, V84B = paths.load(paths.V83), paths.load(paths.V84_A), paths.load(paths.V84_B)
    doc = json.load(open(paths.PATCHES))
    patches = doc['patches']
    J = json.load(open(paths.RESOLVED))
    sites = {r['v83']: r for r in J['sites']}
    norm = J['norm']
    man = json.load(open(paths.MANUAL)) if os.path.exists(paths.MANUAL) else \
        {'sites': {}, 'source_bugs': {}}

    # ---- source-bug corrections, applied to the generated table (task item 4)
    FIXED = {
        # P031: instruction at 0x004D59B2 is `cmp ecx,0x258`; imm32 is at +2, not +1
        'P031': {'off': 2},
        # P311: instruction at 0x00A448B0 is `add eax,0xFFFFFED4`; imm32 at +1, not +2
        'P311': {'off': 1},
        # P158: 0x0064061D is `idiv ecx` -- no immediate. The mov ecx,600 the comment
        # means is 0x00640618, already patched by P157. Drop the operation.
        'P158': {'drop': 'destructive duplicate of P157 (0x00640618+1)'},
        # P302/P304 are the second spelling of P301/P303 (0x9F7078+1 / 0x9F707D+1).
        'P302': {'drop': 'duplicate spelling of P301'},
        'P304': {'drop': 'duplicate spelling of P303'},
    }
    # v84's manifest literal is one byte longer than v83's
    for p in patches:
        if p['op'] == 'FillBytes' and p['site'] == 0x00C08459:
            FIXED[p['id']] = {'size': man['sites']['0x00C08459']['fill_count']}

    rows = []
    for p in patches:
        fx = FIXED.get(p['id'], {})
        p = dict(p, **{k: v for k, v in fx.items() if k in ('off', 'size')})
        p['target'] = p['site'] + p['off']
        anchor = norm[p['id']]['anchor']
        if fx.get('off') is not None:
            anchor = p['site']
        r = sites.get(anchor)
        row = {'id': p['id'], 'group': p['group'], 'op': p['op'],
               'v83': p['site'], 'off': p['off'], 'size': p['size'],
               'value': p['value'], 'comment': p['comment'],
               'drop': fx.get('drop'), 'fixed': bool(fx) and not fx.get('drop'),
               'tier': r and r['tier'], 'v84': None,
               'checks': {}, 'verdict': None}

        if row['drop']:
            row['verdict'] = 'DROPPED'
            rows.append(row)
            continue
        if not r or r['v84'] is None or r['status'] not in ('resolved', 'resolved-data'):
            row['verdict'] = 'UNRESOLVED'
            rows.append(row)
            continue

        v84_anchor = r['v84']
        v84_target = v84_anchor + (p['target'] - anchor)
        row['v84'] = v84_target
        c = row['checks']

        # ADDR
        c['ADDR'] = (0 <= v84_target - paths.BASE
                     and v84_target - paths.BASE + p['size'] <= len(V84))
        # DUAL
        a, b = v84_anchor - paths.BASE, v84_anchor - paths.BASE + max(p['size'], 8)
        c['DUAL'] = b <= len(V84B) and V84[a:b] == V84B[a:b]
        # FIT
        c['FIT'] = fits(p['value'], p['size'])

        if r.get('data'):
            c['SHAPE'] = c['SLOT'] = None            # data site: nothing to decode
        elif p['op'] == 'CodeCave':
            ok83, _ = insn_run(V83, p['site'], p['size'])
            ok84, seq = insn_run(V84, v84_anchor, p['size'])
            c['SHAPE'] = same_shape(shape(V83, p['site']), shape(V84, v84_anchor))
            c['CAVE83'], c['CAVE'] = ok83, ok84
            row['cave_v84_seq'] = seq
            row['cave_retn_v84'] = v84_anchor + p['size']
            c['SLOT'] = None
        elif p['op'] in ('FillBytes', 'WriteString', 'WriteByteArray'):
            ok84, seq = insn_run(V84, v84_anchor, p['size'])
            c['SHAPE'] = same_shape(shape(V83, p['site']), shape(V84, v84_anchor))
            c['BLOCK'] = ok84
            c['SLOT'] = None
        else:
            s84 = shape(V84, v84_anchor)
            c['SHAPE'] = same_shape(shape(V83, anchor), s84)
            cc = classify(V84, dict(p, site=v84_anchor, off=p['target'] - anchor))
            c['SLOT'] = cc['verdict'] in ('ok', 'partial', 'opcode')
            row['v84_insn'] = s84 and f"{s84['m']} {s84['ops']}"
            row['v84_verdict'] = cc['verdict']

        hard = [v for k, v in c.items() if v is not None and k not in ('CAVE83',)]
        row['verdict'] = 'PASS' if all(hard) else 'FAIL'
        rows.append(row)

    # ---------------------------------------------------------------- report
    def bucket(r):
        if r['group'] in DROP_GROUPS:
            return 'drop-group'
        if r['group'] in OPTIONAL_GROUPS:
            return 'optional'
        return 'shipping'

    shipping = [r for r in rows if bucket(r) == 'shipping' and r['verdict'] != 'DROPPED']
    print('=' * 78)
    print('v84 HD PATCH SET -- OFFLINE VERIFICATION')
    print(f'target resolution {doc["target_res"][0]}x{doc["target_res"][1]}')
    print('=' * 78)

    print('\n--- source-bug corrections applied to the generated table ---')
    for pid, fx in sorted(FIXED.items()):
        w = 'DROP' if fx.get('drop') else 'FIX '
        print(f'  {w} {pid}: {fx.get("drop") or fx}')

    print('\n--- per-category results (operations) ---')
    print(f'  {"cat":10} {"group":6} {"n":>4} {"PASS":>5} {"FAIL":>5} {"UNRES":>6}')
    for cat in ('shipping', 'optional', 'drop-group'):
        for g in sorted({r['group'] for r in rows if bucket(r) == cat}):
            sub = [r for r in rows if bucket(r) == cat and r['group'] == g]
            v = collections.Counter(r['verdict'] for r in sub)
            print(f'  {cat:10} {g:6} {len(sub):4} {v["PASS"]:5} {v["FAIL"]:5} '
                  f'{v["UNRESOLVED"]:6}')
    tot = collections.Counter(r['verdict'] for r in rows)
    print(f'\n  ALL 327 ops : PASS {tot["PASS"]}  FAIL {tot["FAIL"]}  '
          f'UNRESOLVED {tot["UNRESOLVED"]}  DROPPED {tot["DROPPED"]}')
    sv = collections.Counter(r['verdict'] for r in shipping)
    print(f'  SHIPPING SET: {len(shipping)} ops -> PASS {sv["PASS"]}  FAIL {sv["FAIL"]}'
          f'  UNRESOLVED {sv["UNRESOLVED"]}'
          f'   ({100.0 * sv["PASS"] / max(len(shipping), 1):.1f}% ready)')

    print('\n--- resolution tier of the shipping set ---')
    print(' ', dict(collections.Counter(r['tier'] for r in shipping if r['verdict'] == 'PASS')))

    fails = [r for r in rows if r['verdict'] == 'FAIL']
    if fails:
        print(f'\n--- FAILURES ({len(fails)}) ---')
        for r in fails:
            print(f'  {r["id"]} {r["group"]} 0x{r["v83"]:08X}+{r["off"]} -> '
                  f'0x{r["v84"]:08X} [{r["tier"]}] {r["checks"]}'
                  f' {r.get("v84_insn", "")}')

    unres = [r for r in rows if r['verdict'] == 'UNRESOLVED']
    print(f'\n--- STILL NEEDING MANUAL RE ({len(unres)} ops, '
          f'{len([r for r in unres if bucket(r) == "shipping"])} of them in the shipping set) ---')
    for r in sorted(unres, key=lambda r: (bucket(r) != 'shipping', r['group'], r['v83'])):
        print(f'  {bucket(r)[:4]:4} {r["group"]} {r["id"]} 0x{r["v83"]:08X}+{r["off"]:<2} '
              f'{r["op"]:14} {str(r["value"])[:12]:14} // {r["comment"][:44]}')

    if not only_fail:
        print('\n--- shipping patch table (PASS rows) ---')
        print(f'  {"id":5} {"g":1} {"v83":>10} {"v84":>10} {"+":>2} {"sz":>2} '
              f'{"value":>10}  tier          v84 instruction')
        for r in shipping:
            if r['verdict'] != 'PASS':
                continue
            print(f'  {r["id"]:5} {r["group"]:1} 0x{r["v83"]:08X} 0x{r["v84"]:08X} '
                  f'{r["off"]:2} {r["size"]:2} {str(r["value"])[:10]:>10}  '
                  f'{str(r["tier"]):13} {r.get("v84_insn", r["op"])}')

    out = os.path.join(paths.DATA, 'v84-patchset.json')
    with open(out, 'w') as f:
        json.dump({'target_res': doc['target_res'],
                   'drop_groups': sorted(DROP_GROUPS),
                   'optional_groups': sorted(OPTIONAL_GROUPS),
                   'rows': rows}, f, indent=1)
    print('\nwrote', out)
    return 1 if sv['FAIL'] else 0


if __name__ == '__main__':
    sys.exit(main())
