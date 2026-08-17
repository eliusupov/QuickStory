#!/usr/bin/env python3
"""Derive client decode models for v84 from the CLIENT BINARY, and check the method on v83.

WHY THIS EXISTS ALONGSIDE derive-decode-models.py
-------------------------------------------------
That script builds models from the hand-annotated gms_v83 atlas export plus a table of v84 deltas
someone had already proven. It locks in what we know. It cannot discover anything: for a delta
nobody has found, its model is wrong in the same direction as PacketCreator and passes happily.

This script builds the model from the v84 client binary instead - the dispatcher chain is walked
with the opcode pinned to a concrete value, and the handler's control-flow graph is replayed to
collect the CInPacket::Decode* calls. No server code is read. So when this disagrees with
PacketCreator, the disagreement is evidence.

THREE MODES
  --selfcheck <atlas>   run the same extractor over the v83 binary and compare against the
                        hand-annotated gms_v83 export. If it cannot reproduce v83, where the
                        tables are known-good, its v84 output is worthless. This is the gate.
  --delta <atlas>       derive shapes from BOTH binaries and diff them. Every difference is a
                        candidate v84 structure change, found without reference to our code.
  (default)             write tools/v84/decode-models-v84-binary.tsv

Needs COSMIC_V84_IMAGE (and COSMIC_V83_IMAGE); see binmodel/images.py. The output TSV is committed
so the Java tests do not need either image.
"""
import argparse
import itertools
import os
import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from binmodel import cfgtrace, dispatch, model as pmodel  # noqa: E402

STAGES = ('CField', 'CLogin')
INCOMPLETE = ('BUDGET', 'TIMEOUT', 'PATH_EXPLOSION', 'DEPTH', 'CAP', 'RECURSION')
W = {'Decode1': 1, 'Decode2': 2, 'Decode4': 4, 'Decode8': 8, 'DecodeStr': 's'}
BUF = re.compile(r'\((\d+)\s*bytes?\)')


# ------------------------------------------------------------------ atlas access
def atlas_funcs(atlas, v):
    import json
    p = atlas / ('docs/packets/ida-exports/gms_v%s.json' % v)
    return json.loads(p.read_text(encoding='utf-8'))['functions']


def atlas_registry(atlas, v):
    text = (atlas / ('docs/packets/registry/gms_v%s.yaml' % v)).read_text(encoding='utf-8')
    out = {}
    for block in text.split('- op:')[1:]:
        op = block.splitlines()[0].strip()

        def f(key):
            m = re.search(r'^\s+%s:\s*(.+)$' % key, block, re.M)
            return m.group(1).strip() if m else None

        out.setdefault(op, dict(opcode=f('opcode'), fname=f('fname'), direction=f('direction')))
    return out


def export_shapes(entry, maxguard=8):
    """Every field-width sequence the hand-annotated export admits (cartesian over its guards)."""
    calls = entry.get('calls') or []
    guards = []
    for c in calls:
        g = c.get('guard')
        if g and g not in guards:
            guards.append(g)
    if len(guards) > maxguard:
        return None
    out = []
    for combo in itertools.product([False, True], repeat=len(guards)):
        pins = dict(zip(guards, combo))
        seq, ok = [], True
        for c in calls:
            g = c.get('guard')
            if g and not pins[g]:
                continue
            op = c['op']
            if op in W:
                seq.append(W[op])
            elif op in ('DecodeBuf', 'DecodeBuffer'):
                m = BUF.search(c.get('comment') or '')
                if not m:
                    ok = False
                    break
                seq.append(int(m.group(1)))
            else:
                ok = False
                break
        if ok:
            s = pmodel.widths(seq)
            if s not in out:
                out.append(s)
    return out


# ------------------------------------------------------------------ extraction
def shapes(v, opcode, otr, dtr, fixed_only=False):
    """(shapes, handler set, chain set, incomplete?)

    fixed_only drops shapes containing a variable-length field (a string, or a DecodeBuffer whose
    length could not be recovered). Emission needs that; the self-check must NOT use it, or every
    packet carrying a string would look like a miss."""
    out, handlers, chains, bad = [], set(), set(), False
    for stg in STAGES:
        for h, c, s, f in pmodel.model(v, opcode, stg, otr, dtr):
            if any(x.split('@')[0] in INCOMPLETE for x in f):
                bad = True
            if len(c) < 2:
                continue
            handlers.add(h)
            chains |= {x[1] for x in c}
            if not s or (fixed_only and dispatch.nbytes(s) is None):
                continue
            w = tuple(s)
            if w not in out:
                out.append(w)
    return sorted(out, key=lambda x: (dispatch.nbytes(x) or 9999, x)), handlers, chains, bad


def tracers(v, seconds, depth):
    return dispatch.Tracer(v), cfgtrace.Tracer(v, depth=depth, seconds=seconds)


# ------------------------------------------------------------------ modes
def selfcheck(atlas, seconds, depth, only):
    reg, funcs = atlas_registry(atlas, '83'), atlas_funcs(atlas, '83')
    otr, dtr = tracers('83', seconds, depth)
    fn_ok = fn_bad = sh_ok = sh_bad = na = 0
    for op, d in sorted(reg.items()):
        if d['direction'] != 'clientbound' or not d['opcode'] or not d['fname']:
            continue
        if only and op not in only:
            continue
        entry = funcs.get(d['fname'])
        want = int(entry['address'], 16) if (entry and entry.get('address')) else None
        got, handlers, chains, _ = shapes('83', int(d['opcode']), otr, dtr)
        if want is None:
            na += 1
        elif want in handlers or want in chains:
            fn_ok += 1
        else:
            fn_bad += 1
            print('  HANDLER %-38s want %08X got %s'
                  % (op, want, ','.join('%08X' % h for h in sorted(handlers)) or '-'))
        exp = export_shapes(entry) if entry else None
        if not exp:
            continue
        if any(pmodel.widths(list(g)) in exp for g in got):
            sh_ok += 1
        else:
            sh_bad += 1
            print('  SHAPE   %-38s export %s binary %s'
                  % (op, exp[:3], [pmodel.widths(list(g)) for g in got][:4]))
        sys.stdout.flush()
    print('\nv83 SELF-CHECK  handler %d ok / %d wrong (%d have no export address)'
          % (fn_ok, fn_bad, na))
    print('                shape   %d ok / %d wrong' % (sh_ok, sh_bad))
    return 1 if (fn_bad or sh_bad) else 0


def delta(atlas, seconds, depth, only):
    r83, r84 = atlas_registry(atlas, '83'), atlas_registry(atlas, '84')
    o83, d83 = tracers('83', seconds, depth)
    o84, d84 = tracers('84', seconds, depth)
    same = diff = novar = 0
    for op in sorted(set(r83) & set(r84)):
        a, b = r83[op], r84[op]
        if a['direction'] != 'clientbound' or not a['opcode'] or not b['opcode']:
            continue
        if only and op not in only:
            continue
        s83, _, _, bad83 = shapes('83', int(a['opcode']), o83, d83, fixed_only=True)
        s84, _, _, bad84 = shapes('84', int(b['opcode']), o84, d84, fixed_only=True)
        if not s83 or not s84:
            novar += 1
            continue
        if [pmodel.widths(list(x)) for x in s83] == [pmodel.widths(list(x)) for x in s84]:
            same += 1
            continue
        diff += 1
        mark = '   *** INCOMPLETE TRACE - NOT EVIDENCE ***' if (bad83 or bad84) else ''
        print('%-38s v83 op %-4s %s%s' % (op, a['opcode'], [dispatch.nbytes(x) for x in s83], mark))
        print('%-38s v84 op %-4s %s' % ('', b['opcode'], [dispatch.nbytes(x) for x in s84]))
        for x in s84:
            print('%-38s          %s' % ('', ','.join(x)))
        sys.stdout.flush()
    print('\nidentical in both binaries: %d   DIFFERENT: %d   no fixed shape on a side: %d'
          % (same, diff, novar))
    return 0


def emit(atlas, out, seconds, depth, only):
    """One row per opcode that the v84 binary gives exactly ONE fixed shape for."""
    reg = atlas_registry(atlas, '84')
    otr, dtr = tracers('84', seconds, depth)
    rows, rejected = [], {}
    for op, d in sorted(reg.items()):
        if d['direction'] != 'clientbound' or not d['opcode']:
            continue
        if only and op not in only:
            continue
        got, handlers, _, bad = shapes('84', int(d['opcode']), otr, dtr, fixed_only=True)
        if not got:
            rejected[op] = 'no fixed-size shape (variable-length or unresolved)'
            continue
        if len(got) > 1:
            rejected[op] = '%d shapes - the client branches on packet data' % len(got)
            continue
        if bad:
            rejected[op] = 'trace incomplete (budget/timeout/depth)'
            continue
        fields = ','.join('f%d:%s' % (i, t) for i, t in enumerate(got[0]))
        rows.append(('candidate', op, d['opcode'],
                     'v84 image handler ' + ' '.join('0x%08X' % h for h in sorted(handlers)),
                     fields))
    header = [
        '# Generated by tools/v84/derive-binary-models.py - DO NOT EDIT BY HAND.',
        '# Derived from the v84 CLIENT BINARY only: the dispatcher chain walked with the opcode',
        '# pinned, then the handler CFG replayed. No server code was read, so a disagreement with',
        '# PacketCreator is a candidate v84 delta, not a model to be corrected.',
        '# Only opcodes with exactly ONE fixed-size shape are listed. Everything else branches on',
        '# packet data and has no single model - see the reject list printed by that script.',
        '# Rows are `candidate` until a human has read the emitting PacketCreator method; the Java',
        '# harness loads only `verified`.',
        '# status\tmodel\topcode\tprovenance\tfields',
    ]
    out.write_text('\n'.join(header + ['\t'.join(map(str, r)) for r in rows]) + '\n',
                   encoding='utf-8')
    print('single-shape opcodes written : %d' % len(rows))
    print('rejected                     : %d' % len(rejected))
    counts = {}
    for why in rejected.values():
        k = re.sub(r'^\d+ shapes', 'N shapes', why)
        counts[k] = counts.get(k, 0) + 1
    for k, n in sorted(counts.items(), key=lambda kv: -kv[1]):
        print('    %4d  %s' % (n, k))
    print('wrote %s' % out)
    return 0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--selfcheck', metavar='ATLAS')
    ap.add_argument('--delta', metavar='ATLAS')
    ap.add_argument('--atlas', metavar='ATLAS')
    ap.add_argument('-o', '--out', type=pathlib.Path,
                    default=pathlib.Path(__file__).with_name('decode-models-v84-binary.tsv'))
    ap.add_argument('--seconds', type=float, default=12.0)
    ap.add_argument('--depth', type=int, default=6)
    ap.add_argument('--only', nargs='*', default=[])
    a = ap.parse_args()
    only = set(a.only)
    if a.selfcheck:
        return selfcheck(pathlib.Path(a.selfcheck), a.seconds, a.depth, only)
    if a.delta:
        return delta(pathlib.Path(a.delta), a.seconds, a.depth, only)
    if not a.atlas:
        ap.error('give --atlas <path> to emit, or --selfcheck/--delta')
    return emit(pathlib.Path(a.atlas), a.out, a.seconds, a.depth, only)


if __name__ == '__main__':
    sys.exit(main())
