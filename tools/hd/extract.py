"""Extract the DEFINITIVE Ezorsia v2 patch table from the public source.

Ground truth for what the mod actually does to a v83 client. Output is
data/ezorsia-v83-patches.json so nothing downstream has to re-scrape C++.

Per record:
  site      the v83 address the source names (the instruction the patch belongs to)
  off       the +N the source adds
  target    site + off -- where the write actually lands
  size      bytes written
  value     the operand, evaluated at 1280x720 with config.ini defaults
  comment   the source's own trailing comment (its claim about the instruction)

`off` and `comment` are the pair the three latent source bugs get wrong; verify.py
checks `off` against what the instruction at `site` really encodes.

    python tools/hd/extract.py
"""
import json
import math
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import paths  # noqa: E402

# config.ini defaults + the HD target. Change WIDTH/HEIGHT to re-target.
WIDTH, HEIGHT = 1280, 720
ENV = {
    'm_nGameWidth': WIDTH, 'm_nGameHeight': HEIGHT,
    'MsgAmount': 26, 'msgAmnt': 26, 'msgAmntOffset': 26 * 14,
    'reqPopOffset': 41,
    'setDamageCap': 199999.0, 'setDamageCapInt': 199999,
    'speedMovementCap': 140,
    'resmanLoadAMNT': 15,                      # len(resmanLoadOrder) - 1, codecaves.h
    'myHeight': (HEIGHT - 600) // 2, 'myWidth': (WIDTH - 800) // 2,
    'a1y': -250,
    'nCipherHash': 0,
    'floor': math.floor, 'int': int,
}

# Only these two Client:: members are actually called at runtime (MainMain.cpp).
# EnableNewIGCipher and UpdateLogin are dead code the author kept for reference.
LIVE_FUNCS = {'UpdateGameStartup', 'UpdateResolution'}

# Ticket 30 s2 group taxonomy, by v83 address range / intent.
GROUPS = [
    ('B', lambda a, c: 0x00AFE084 <= a <= 0x00AFE0C0),
    ('K', lambda a, c: a in (0x00485C32, 0x00AFE8A0, 0x008C3304, 0x008C4286,
                             0x00780743, 0x0094D91E)),
    ('L', lambda a, c: a in (0x009F7A9B, 0x0062EE54)),
    ('A', lambda a, c: a in (0x00A63FF3, 0x005F6994, 0x005F6B87, 0x005F6BA0,
                             0x005F6BA4, 0x00496633, 0x009F1C04, 0x009F242F,
                             0x009F6EDC, 0x009F74EA, 0x009F753C, 0x00C08459,
                             0x00C08463, 0x0049C2CD, 0x0049CFE8, 0x0049D398,
                             0x0040013E)),
    ('F', lambda a, c: 0x00469340 <= a <= 0x00469500 or a in (0x00776B5F, 0x004AB10F)),
    ('G', lambda a, c: 0x00554000 <= a <= 0x00555600 or a == 0x0053500A),
    ('I', lambda a, c: 0x00522C00 <= a <= 0x005244000 and 0x00522C00 <= a <= 0x00524400),
    ('E', lambda a, c: (0x005F4000 <= a <= 0x00631000) or a in (0x0060D849,)),
    ('H', lambda a, c: 0x00897000 <= a <= 0x0089C000),
    ('D', lambda a, c: (0x008CF000 <= a <= 0x008E0000) or 0x007B2C00 <= a <= 0x007B3100),
    ('J', lambda a, c: a in (0x00533B03, 0x00534370, 0x0045B337, 0x0045B417,
                             0x0045B898, 0x0045B97E, 0x0045A5CB, 0x005362B2,
                             0x005364AA, 0x007E15BE, 0x007E16B9, 0x007E16BE,
                             0x007E1CB7, 0x007E1E07, 0x007E19CA)),
]


def group_of(addr, comment):
    for g, pred in GROUPS:
        try:
            if pred(addr, comment):
                return g
        except Exception:
            pass
    return 'C'


def split_args(s):
    """Top-level comma split, paren/brace/quote aware."""
    out, depth, cur, q = [], 0, '', None
    for ch in s:
        if q:
            cur += ch
            if ch == q:
                q = None
            continue
        if ch in '"\'':
            q = ch
        elif ch in '([{':
            depth += 1
        elif ch in ')]}':
            if depth == 0:
                break
            depth -= 1
        elif ch == ',' and depth == 0:
            out.append(cur)
            cur = ''
            continue
        cur += ch
    out.append(cur)
    return [a.strip() for a in out]


def parse_consts(text):
    """const DWORD/int NAME = value;  (value may reference an earlier const)"""
    consts = {}
    for m in re.finditer(r'^\s*const\s+(?:DWORD|int|unsigned char)\s+(\w+)\s*=\s*([^;]+);',
                         text, re.M):
        name, expr = m.group(1), m.group(2).strip()
        try:
            consts[name] = int(eval(expr, {'__builtins__': {}}, dict(consts)))
        except Exception:
            pass
    return consts


def build(width=WIDTH, height=HEIGHT):
    """Parse the source and return the patch list evaluated at width x height."""
    global ENV
    ENV = dict(ENV, m_nGameWidth=width, m_nGameHeight=height,
               myHeight=(height - 600) // 2, myWidth=(width - 800) // 2)
    return _parse()


def main():
    ops = _parse()
    os.makedirs(paths.DATA, exist_ok=True)
    with open(paths.PATCHES, 'w') as f:
        json.dump({'source': 'github.com/444Ro666/MapleEzorsia-v2',
                   'target_res': [WIDTH, HEIGHT],
                   'patches': ops}, f, indent=1)

    import collections
    print(f'patch operations : {len(ops)}')
    print(f'distinct sites   : {len(set(r["site"] for r in ops))}')
    print('by op            :', dict(collections.Counter(r['op'] for r in ops)))
    print('by group         :', dict(sorted(collections.Counter(r['group'] for r in ops).items())))
    print('wrote', paths.PATCHES)


def _parse():
    paths.require(paths.EZORSIA)
    src = open(os.path.join(paths.EZORSIA, 'Client.cpp'),
               encoding='utf-8', errors='replace').read()
    addy = open(os.path.join(paths.EZORSIA, 'AddyLocations.h'),
                encoding='utf-8', errors='replace').read()
    caves = open(os.path.join(paths.EZORSIA, 'codecaves.h'),
                 encoding='utf-8', errors='replace').read()

    consts = parse_consts(addy)
    consts.update(parse_consts(caves))
    consts['resmanLoadAMNT'] = 15

    lines = src.splitlines()
    # map line number -> enclosing Client:: member
    fname, fmap = None, {}
    for i, l in enumerate(lines, 1):
        m = re.match(r'\w[\w:*&<>\s]*\bClient::(\w+)\s*\(', l)
        if m:
            fname = m.group(1)
        fmap[i] = fname

    # byte-array literals: unsigned char NAME[] = { 0x.., .. };
    arrays = {}
    for m in re.finditer(r'unsigned char (\w+)\[\]\s*=\s*\{([^}]*)\}', src):
        arrays[m.group(1)] = [int(x, 0) for x in m.group(2).split(',') if x.strip()]

    ops = []
    for i, l in enumerate(lines, 1):
        if fmap.get(i) not in LIVE_FUNCS:
            continue
        code = l.split('//')[0]
        comment = l.split('//', 1)[1].strip() if '//' in l else ''
        if l.strip().startswith('//') or 'Memory::' not in code:
            continue
        m = re.search(r'Memory::(\w+)\(', code)
        if not m:
            continue
        op = m.group(1)
        args = split_args(code[m.end():])

        # ---- arg 0: address expression -> (site, off)
        a0 = args[0] if op != 'CodeCave' else args[1]
        am = re.match(r'^(0x[0-9A-Fa-f]+|\w+)\s*(?:\+\s*(\w+))?$', a0.strip())
        if not am:
            continue
        base_tok = am.group(1)
        site = int(base_tok, 16) if base_tok.startswith('0x') else consts.get(base_tok)
        if site is None:
            continue
        off = 0
        if am.group(2):
            t = am.group(2)
            off = int(t, 0) if re.match(r'^\d|^0x', t) else consts.get(t, 0)

        rec = {
            'op': op, 'site': site, 'off': off, 'target': site + off,
            'src_line': i, 'comment': comment, 'func': fmap[i],
            'addr_expr': a0.strip(),
        }

        # ---- value + size
        def ev(e):
            return eval(re.sub(r'\(int\)', '', e), {'__builtins__': {}}, dict(ENV, **consts))

        try:
            if op == 'WriteInt':
                rec['size'], rec['kind'] = 4, 'imm'
                rec['value'] = int(ev(args[1]))
                rec['value_expr'] = args[1].strip()
            elif op == 'WriteByte':
                rec['size'], rec['kind'] = 1, 'imm'
                rec['value'] = int(ev(args[1])) & 0xFF
                rec['value_expr'] = args[1].strip()
            elif op == 'WriteShort':
                rec['size'], rec['kind'] = 2, 'imm'
                rec['value'] = int(ev(args[1])) & 0xFFFF
                rec['value_expr'] = args[1].strip()
            elif op == 'WriteDouble':
                rec['size'], rec['kind'] = 8, 'double'
                rec['value'] = float(ev(args[1]))
                rec['value_expr'] = args[1].strip()
            elif op == 'FillBytes':
                rec['size'], rec['kind'] = int(ev(args[2])), 'fill'
                rec['value'] = int(ev(args[1])) & 0xFF
                rec['value_expr'] = f'{args[1].strip()} x {args[2].strip()}'
            elif op == 'WriteString':
                s = args[1].strip()
                lit = re.match(r'^"(.*)"$', s)
                text = lit.group(1) if lit else '127.0.0.1'   # ServerIP_Address default
                rec['size'], rec['kind'] = len(text), 'string'
                rec['value'] = text
                rec['value_expr'] = s
            elif op == 'WriteByteArray':
                arr = arrays.get(args[1].strip(), [])
                rec['size'], rec['kind'] = len(arr), 'bytes'
                rec['value'] = arr
                rec['value_expr'] = args[1].strip()
            elif op == 'CodeCave':
                nops = args[2].strip()
                rec['size'] = int(nops, 0) if re.match(r'^\d|^0x', nops) else consts.get(nops, 5)
                rec['kind'] = 'cave'
                rec['cave'] = args[0].strip()
                rec['value'] = None
                rec['value_expr'] = f'jmp -> {args[0].strip()}'
                # matching return-address const, if the author declared one
                # the author's naming is inconsistent; a few need an explicit alias
                ALIAS = {'dwStatusBarVPos': 'dwStatusBarPosRetn',
                         'dwStatusBarBackgroundVPos': 'dwStatusBarBackgroundPosRetn',
                         'dwStatusBarInputVPos': 'dwStatusBarInputPosRetn',
                         'dwAlwaysViewRestoreFix': 'dwAlwaysViewRestorerFixRtm'}
                a1 = args[1].strip().split('+')[0].strip()
                if a1 in ALIAS and ALIAS[a1] in consts:
                    rec['retn'] = consts[ALIAS[a1]]
                    rec['retn_const'] = ALIAS[a1]
                stem = re.sub(r'^dw', '', a1)
                for suf in ('Retn', 'Rtm', 'Rtn', 'FixRetn', 'Ret'):
                    for pre in ('dw', ''):
                        k = pre + stem + suf
                        if k in consts:
                            rec['retn'] = consts[k]
                            rec['retn_const'] = k
                            break
                    if 'retn' in rec:
                        break
                # 0x005F6994 is a DELIBERATE long jump into a later basic block of the
                # same function, so retn != site+nops there. Not a bug.
                if 'retn' in rec and rec['retn'] != site + rec['size']:
                    rec['long_jump'] = True
            else:
                continue
        except Exception as e:                                    # noqa: BLE001
            rec['size'], rec['kind'] = 0, 'unparsed'
            rec['value'], rec['value_expr'] = None, f'!! {e}'

        rec['group'] = group_of(site, comment)
        ops.append(rec)

    ops.sort(key=lambda r: (r['site'], r['off']))
    for n, r in enumerate(ops):
        r['id'] = f'P{n:03d}'
    return ops


if __name__ == '__main__':
    main()
