"""opcode -> field shapes, entirely from the client binary.

  resolve()  walks the dispatcher chain with the opcode pinned  -> handler VA + prefix fields
  dtrace()   walks the handler's CFG                            -> body fields per path

usage: python pmodel.py <83|84|84b> <opcode> [stage]
"""
import sys
from . import dispatch as optrace
from . import cfgtrace as dtrace


def model(v, opcode, stage='CField', otr=None, dtr=None):
    """[(handler_va, chain, shape, flags)] - one entry per distinct reachable shape."""
    otr = otr or optrace.Tracer(v)
    dtr = dtr or dtrace.Tracer(v, depth=8)
    dtr.arm()          # one budget for this opcode, shared by every candidate handler
    out = []
    for status, handler, prefix, chain in optrace.resolve_all(v, opcode, stage, otr):
        if status != 'OK' or len(chain) < 2:
            continue
        pre = [t for t in prefix][1:]          # drop the opcode Decode2
        # Always trace the handler itself. The reachability whitelist is a pruning aid for SUB-calls
        # and has false negatives; using it to skip the handler turns a missing field into a
        # confident empty model, which is the one failure mode this tool must not have.
        r = dtr.summary(handler)
        if r['shapes'] is None:
            out.append((handler, chain, pre, r['flags'] | {'UNRESOLVED'}))
            continue
        for s in r['shapes']:
            out.append((handler, chain, pre + list(s), set(r['flags'])))
    # dedupe on shape, keep the one with the richest chain
    best = {}
    for h, c, s, f in out:
        k = tuple(s)
        if k not in best or len(c) > len(best[k][1]):
            best[k] = (h, c, s, f)
    return list(best.values())


def widths(shape):
    """Collapse to a comparable byte-width sequence: adjacent fixed fields merge, so a client that
    reads 4+4 and one that reads 8 compare equal."""
    out = []
    for t in shape:
        n = None
        if isinstance(t, int):
            n = t
        elif t in ('1', '2', '4'):
            n = int(t)
        elif t.startswith('b') and t[1:].isdigit():
            n = int(t[1:])
        if n is None:
            out.append(t)
        elif out and isinstance(out[-1], int):
            out[-1] += n
        else:
            out.append(n)
    return out


