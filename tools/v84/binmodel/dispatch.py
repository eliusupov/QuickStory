"""Per-opcode packet-structure extractor, straight from the client binary.

Walks CClientSocket::ProcessPacket with the opcode pinned to a concrete value, through every
dispatcher in the chain, into the handler, and records the ordered CInPacket::Decode* calls.
Because the walk starts at ProcessPacket, dispatcher-consumed prefix fields (mob id, character id,
...) are included automatically - which is the thing the atlas exports are inconsistent about.

Abstract interpretation over an explicit stack, so [ebp+8] / [ebp-0x14] resolve; values are
constants, opcode-affine (OP+k), or unknown. Branches with resolvable conditions are followed
deterministically; unresolvable ones fork, and every reachable shape is reported.

Nothing here reads the server. A disagreement between this and PacketCreator is evidence.
"""
import sys
from capstone import *
from capstone.x86 import X86_OP_IMM, X86_OP_REG, X86_OP_MEM
from .images import img, IMG
from .cfgtrace import DECODES

md = Cs(CS_ARCH_X86, CS_MODE_32)
md.detail = True

# ---------------------------------------------------------------- version facts
# All established in this session by disassembly; see dispatchers.md notes in the report.
ROOT = {'83': 0x004965F1, '84': 0x0049B502}          # CClientSocket::ProcessPacket
STAGE_CALL = {'83': 0x00496662, '84': 0x0049B573}    # `call [edx]` -> m_pStage->OnPacket
STAGE = {
    # The stage is CLogin at the login/channel screens and CField once in a map, so the same
    # opcode number can mean two different packets. Both are traced and reported separately.
    '83': {'CField': 0x00531325, 'CLogin': 0x005F80FF},
    '84': {'CField': 0x0053D5A7, 'CLogin': 0x0060D075},
}
# CWvsContext::OnPacket handles this inclusive opcode range directly inside ProcessPacket.
WVSCTX = {'83': (0x1D, 0x7C), '84': (0x1D, 0x7F)}
# MSVC __EH_prolog: `mov eax, <scopetable> ; call __EH_prolog` builds the frame in the CALLEE.
# Modelled as an intrinsic: without it every SEH-using function looks like it has no frame and
# [ebp+8] stops resolving, which silently loses the opcode.
EH_PROLOG = {'83': 0x00A60B98, '84': 0x00AACD18}

REGS = ['eax', 'ecx', 'edx', 'ebx', 'esi', 'edi']
SUB32 = {'ax': 'eax', 'cx': 'ecx', 'dx': 'edx', 'bx': 'ebx', 'si': 'esi', 'di': 'edi',
         'al': 'eax', 'cl': 'ecx', 'dl': 'edx', 'bl': 'ebx',
         'ah': 'eax', 'ch': 'ecx', 'dh': 'edx', 'bh': 'ebx'}

COND = {'jo', 'jno', 'jb', 'jae', 'je', 'jne', 'jbe', 'ja', 'js', 'jns', 'jp', 'jnp',
        'jl', 'jge', 'jle', 'jg'}

# Total instructions the dispatch resolver may execute for one opcode, shared across every path it
# forks, and how many forks may be queued. Cost is superlinear in queued forks (each carries a
# copied machine state), so these are set to the smallest values that still reproduce all 27
# v83 self-check opcodes exactly - see --selfcheck. Exceeding either marks the result incomplete
# rather than reporting a short model.
STEP_BUDGET = 30000
# Queued forks. Each carries a copied State, so an unbounded queue is a memory problem as well as a
# time one; the attack packets fork tens of thousands of times.
FORK_CAP = 300


def is_c(v):
    """Concrete. ('OP', n) is the opcode: also concrete, but tagged so the dispatch resolver can
    tell which argument is the opcode being forwarded to the next dispatcher."""
    return v is not None and v[0] in ('C', 'OP')


def add(v, k):
    if v is None or v[0] not in ('C', 'OP'):
        return None
    return v[0], (v[1] + k) & 0xFFFFFFFF


class State:
    """esp/ebp are plain integer frame offsets, not Values; everything else is a Value."""
    __slots__ = ('r', 'stk', 'flags', 'esp', 'ebp', 'pushed')

    def __init__(self):
        self.r = {x: None for x in REGS}
        self.stk = {}
        self.flags = None
        self.esp = 0
        self.ebp = None
        self.pushed = ()   # values pushed since the last call: the next call's arguments

    def copy(self):
        s = State.__new__(State)
        s.r = dict(self.r)
        s.stk = dict(self.stk)
        s.flags = self.flags
        s.esp = self.esp
        s.ebp = self.ebp
        s.pushed = self.pushed
        return s

    def key(self):
        rk = tuple(sorted((k, v) for k, v in self.r.items() if v is not None and v[0] == 'OP'))
        sk = tuple(sorted((k, v) for k, v in self.stk.items() if v is not None and v[0] == 'OP'))
        return rk, sk, self.esp, self.ebp


class Tracer:
    def __init__(self, v, budget=600000, maxdepth=32, cap=32):
        self.v = v
        self.B = img(v)
        i = 0 if v == '83' else 1
        self.dec = {va[i]: n for n, va in DECODES.items()}
        self.budget = budget
        self.maxdepth = maxdepth
        self.cap = cap
        self.overrides = {}
        self.eh = EH_PROLOG[v]
        from .reach import decoding_set
        self.decoding = decoding_set(v, set(self.dec))
        self._icache = {}
        self._ret = {}

    def ret_pop(self, tgt):
        """How many argument bytes a callee pops (`ret imm16`). Needed for every call we do NOT
        step into: get this wrong and esp drifts, [ebp+8]/[esp+N] stop resolving, and the opcode
        silently becomes unknown - which is exactly how a dispatcher walk goes quiet."""
        if tgt in self._ret:
            return self._ret[tgt]
        self._ret[tgt] = 0
        va, n = tgt, 0
        while n < 600:
            i = self.ins_at(va)
            if i is None:
                break
            n += 1
            if i.mnemonic in ('ret', 'retf'):
                ops = i.operands
                self._ret[tgt] = ops[0].imm if (ops and ops[0].type == X86_OP_IMM) else 0
                break
            if i.mnemonic == 'jmp':
                if i.operands and i.operands[0].type == X86_OP_IMM:
                    va = i.operands[0].imm
                    continue
                break
            va += i.size
        return self._ret[tgt]

    def eh_prolog(self, st):
        """`call __EH_prolog` with esp==E leaves esp=E-16, ebp=E-4, old ebp saved at [ebp]."""
        e = st.esp
        st.stk[e - 4] = ('FRAME', st.ebp)
        st.ebp = e - 4
        st.esp = e - 16
        return st

    # -------------------------------------------------------------- helpers
    def ins_at(self, va):
        if va in self._icache:
            return self._icache[va]
        r = None
        o = va - IMG
        if 0 <= o < len(self.B):
            for i in md.disasm(self.B[o:o + 16], va):
                r = i
                break
        self._icache[va] = r
        return r

    def u32(self, va):
        o = va - IMG
        if o < 0 or o + 4 > len(self.B):
            return None
        return int.from_bytes(self.B[o:o + 4], 'little')

    def rd(self, st, op):
        if op.type == X86_OP_IMM:
            return 'C', op.imm & 0xFFFFFFFF
        if op.type == X86_OP_REG:
            n = md.reg_name(op.reg)
            if n in st.r:
                return st.r[n]
            if n in SUB32:
                v = st.r[SUB32[n]]
                if v is None or v[0] not in ('C', 'OP'):
                    return None
                if n.endswith('h'):
                    return None
                mask = {1: 0xFF, 2: 0xFFFF, 4: 0xFFFFFFFF}.get(op.size, 0xFFFFFFFF)
                return v[0], v[1] & mask
            return None
        if op.type == X86_OP_MEM:
            a = self.addr(st, op)
            if a and a[0] == 'stack':
                v = st.stk.get(a[1])
                return v if (v is None or v[0] in ('C', 'OP')) else None
            if a and a[0] == 'image':
                # MSVC's two-level switch: `movzx eax, byte [eax + <index table>]` then
                # `jmp [eax*4 + <jump table>]`. Without this the second level is unreadable and
                # every densely-dispatched opcode looks unresolvable.
                o = a[1] - IMG
                n = {1: 1, 2: 2, 4: 4}.get(op.size, 4)
                if 0 <= o and o + n <= len(self.B):
                    return 'C', int.from_bytes(self.B[o:o + n], 'little')
            return None
        return None

    def addr(self, st, op):
        m = op.mem
        if m.index != 0 or m.base == 0:
            return None
        bn = md.reg_name(m.base)
        if bn == 'ebp':
            return None if st.ebp is None else ('stack', st.ebp + m.disp)
        if bn == 'esp':
            return ('stack', st.esp + m.disp)
        b = st.r.get(bn)
        if is_c(b):
            # A concrete base can only come from a constant or the opcode, never from an object
            # pointer, so this resolves switch index tables and nothing mutable.
            a = (b[1] + m.disp) & 0xFFFFFFFF
            if IMG < a < IMG + len(self.B):
                return 'image', a
        return None

    def wr(self, st, op, val):
        if op.type == X86_OP_REG:
            n = md.reg_name(op.reg)
            if n in st.r:
                st.r[n] = val
            elif n in SUB32:
                st.r[SUB32[n]] = val if op.size == 4 else None
            return
        if op.type == X86_OP_MEM:
            a = self.addr(st, op)
            if a and a[0] == 'stack':
                st.stk[a[1]] = val

    # -------------------------------------------------------------- main walk
    def run(self, opcode, start=None):
        v = self.v
        entry = start if start is not None else ROOT[v]
        results, flags, seen = [], set(), set()
        work = [(entry, State(), (), (), 0)]
        steps = 0
        while work:
            va, st, toks, cs, d2 = work.pop()
            while True:
                steps += 1
                if steps > self.budget:
                    flags.add('BUDGET')
                    break
                if len(results) >= self.cap:
                    flags.add('CAP')
                    break
                # Dedup on the thing being computed, not the whole machine state: two paths that
                # reach the same instruction having consumed the same bytes cannot diverge in a way
                # that matters here, and this is what makes loops converge instead of exploding.
                k = (va, len(cs), toks, st.key()[0])
                if k in seen:
                    break
                seen.add(k)
                ins = self.ins_at(va)
                if ins is None:
                    flags.add('BADCODE@%08X' % va)
                    break
                nxt = va + ins.size
                m = ins.mnemonic
                ops = ins.operands
                imm = ops[0].imm if (ops and ops[0].type == X86_OP_IMM) else None

                if m == 'call':
                    tgt = self.overrides.get(va, imm)
                    if tgt == self.eh:
                        st = self.eh_prolog(st)
                        va = nxt
                        continue
                    if tgt is None:
                        flags.add('INDIRECT_CALL@%08X' % va)
                        st = st.copy()
                        st.r['eax'] = None
                        va = nxt
                        continue
                    if tgt in self.dec:
                        name = self.dec[tgt]
                        toks = toks + (self.dtok(name, st),)
                        st = st.copy()
                        st.pushed = ()
                        if name == 'Decode2' and not d2 and not cs:
                            st.r['eax'] = ('OP', opcode)   # ProcessPacket reads the opcode here
                            d2 = 1
                        else:
                            st.r['eax'] = None
                        if name == 'DecodeBuffer':
                            st.esp += 8
                        elif name == 'DecodeStr':
                            st.esp += 4
                        va = nxt
                        continue
                    if tgt not in self.decoding or len(cs) >= self.maxdepth:
                        if len(cs) >= self.maxdepth:
                            flags.add('DEPTH@%08X' % tgt)
                        st = st.copy()
                        st.r['eax'] = None
                        st.esp += self.ret_pop(tgt)
                        st.pushed = ()
                        va = nxt
                        continue
                    st = st.copy()
                    st.pushed = ()
                    st.esp -= 4
                    st.stk[st.esp] = ('RET', nxt)
                    cs = cs + (nxt,)
                    va = tgt
                    continue

                if m in ('ret', 'retf'):
                    if not cs:
                        if toks not in results:
                            results.append(toks)
                        break
                    st = st.copy()
                    st.esp += 4 + (imm or 0)
                    va, cs = cs[-1], cs[:-1]
                    continue

                if m == 'jmp':
                    if imm is not None:
                        va = imm
                        continue
                    t = self.jmp_table(st, ins)
                    if t is None:
                        flags.add('INDIRECT_JMP@%08X' % va)
                        break
                    va = t
                    continue

                if m in COND:
                    taken = self.eval_cc(m, st)
                    if taken is True:
                        va = imm
                        continue
                    if taken is False:
                        va = nxt
                        continue
                    work.append((nxt, st.copy(), toks, cs, d2))
                    va = imm
                    continue

                if m in ('int3', 'ud2', 'hlt'):
                    break

                st = self.exec1(st, ins)
                va = nxt
        return results, flags

    # -------------------------------------------------------------- semantics
    def dtok(self, name, st):
        if name in ('Decode1', 'Decode2', 'Decode4'):
            return name[-1]
        if name == 'DecodeStr':
            return 's'
        ln = st.stk.get(st.esp + 4)  # DecodeBuffer(dst, len)
        return 'b%d' % ln[1] if is_c(ln) else '?buf'

    def jmp_table(self, st, ins):
        op = ins.operands[0]
        if op.type != X86_OP_MEM:
            return None
        m = op.mem
        if m.index == 0 or m.scale != 4 or m.base != 0:
            return None
        idx = st.r.get(md.reg_name(m.index))
        if not is_c(idx):
            return None
        t = self.u32(m.disp + idx[1] * 4)
        return t if (t and IMG < t < IMG + len(self.B)) else None

    def eval_cc(self, m, st):
        if st.flags is None:
            return None
        a, b = st.flags
        if not (is_c(a) and is_c(b)):
            return None
        x, y = a[1], b[1]
        sx = x - (1 << 32) if x >= (1 << 31) else x
        sy = y - (1 << 32) if y >= (1 << 31) else y
        return {'je': x == y, 'jne': x != y,
                'jb': x < y, 'jae': x >= y, 'jbe': x <= y, 'ja': x > y,
                'jl': sx < sy, 'jge': sx >= sy, 'jle': sx <= sy, 'jg': sx > sy,
                'js': (sx - sy) < 0, 'jns': (sx - sy) >= 0}.get(m)

    def exec1(self, st, ins):
        # Mutates in place. Copying the State on every instruction is what makes a long walk
        # quadratic: `stk` grows with the call depth and gets duplicated per step. Every fork
        # site copies explicitly, so in-place is safe here.
        m = ins.mnemonic
        ops = ins.operands
        dn = md.reg_name(ops[0].reg) if (ops and ops[0].type == X86_OP_REG) else None
        sn = md.reg_name(ops[1].reg) if (len(ops) > 1 and ops[1].type == X86_OP_REG) else None

        if m == 'push':
            v = ('FRAME', st.ebp) if dn == 'ebp' else self.rd(st, ops[0])
            st.esp -= 4
            st.stk[st.esp] = v
            st.pushed = (st.pushed + (v,))[-6:]
            return st
        if m == 'pop':
            v = st.stk.get(st.esp)
            st.esp += 4
            if dn == 'ebp':
                st.ebp = v[1] if (v and v[0] == 'FRAME') else None
            elif dn == 'esp':
                pass  # esp stays an int; imprecision here only affects DecodeBuffer lengths
            else:
                self.wr(st, ops[0], v)
            return st
        if m == 'leave':
            if st.ebp is not None:
                v = st.stk.get(st.ebp)
                st.esp = st.ebp + 4
                st.ebp = v[1] if (v and v[0] == 'FRAME') else None
            return st
        if m in ('mov', 'movzx', 'movsx'):
            if dn in ('esp', 'ebp') or sn in ('esp', 'ebp'):
                if dn == 'ebp' and sn == 'esp':
                    st.ebp = st.esp
                elif dn == 'esp' and sn == 'ebp':
                    st.esp = st.ebp
                elif dn == 'ebp':
                    st.ebp = None
                elif dn == 'esp':
                    pass  # esp stays an int; imprecision here only affects DecodeBuffer lengths
                else:
                    self.wr(st, ops[0], None)
                return st
            self.wr(st, ops[0], self.rd(st, ops[1]))
            return st
        if m == 'lea':
            mm = ops[1].mem
            if mm.index == 0 and mm.base != 0 and md.reg_name(mm.base) not in ('ebp', 'esp'):
                self.wr(st, ops[0], add(st.r.get(md.reg_name(mm.base)), mm.disp))
            elif dn in ('esp', 'ebp'):
                if dn == 'esp':
                    pass  # esp stays an int; imprecision here only affects DecodeBuffer lengths
                else:
                    st.ebp = None
            else:
                self.wr(st, ops[0], None)
            return st
        if m in ('add', 'sub'):
            if dn in ('esp', 'ebp'):
                if ops[1].type == X86_OP_IMM:
                    d = ops[1].imm if m == 'add' else -ops[1].imm
                    if dn == 'esp':
                        st.esp += d
                    else:
                        st.ebp = None if st.ebp is None else st.ebp + d
                elif dn == 'esp':
                    pass  # esp stays an int; imprecision here only affects DecodeBuffer lengths
                else:
                    st.ebp = None
                st.flags = None
                return st
            a, b = self.rd(st, ops[0]), self.rd(st, ops[1])
            self.wr(st, ops[0], add(a, (b[1] if m == 'add' else -b[1])) if is_c(b) else None)
            st.flags = (a, b) if m == 'sub' else None
            return st
        if m in ('inc', 'dec'):
            a = add(self.rd(st, ops[0]), 1 if m == 'inc' else -1)
            self.wr(st, ops[0], a)
            st.flags = (a, ('C', 0))
            return st
        if m == 'cmp':
            st.flags = (self.rd(st, ops[0]), self.rd(st, ops[1]))
            return st
        if m == 'test':
            a, b = self.rd(st, ops[0]), self.rd(st, ops[1])
            st.flags = (a, ('C', 0)) if (a is not None and a == b) else None
            return st
        if m == 'xor' and dn and dn == sn:
            self.wr(st, ops[0], ('C', 0))
            st.flags = (('C', 0), ('C', 0))
            return st
        if m == 'nop':
            return st
        if ops:
            if dn == 'esp':
                pass  # esp stays an int; imprecision here only affects DecodeBuffer lengths
            elif dn == 'ebp':
                st.ebp = None
            elif ops[0].type in (X86_OP_REG, X86_OP_MEM):
                self.wr(st, ops[0], None)
        st.flags = None
        return st


def nbytes(shape):
    n = 0
    for t in shape:
        if t in ('1', '2', '4'):
            n += int(t)
        elif t.startswith('b') and t[1:].isdigit():
            n += int(t[1:])
        else:
            return None
    return n


def fmt(shape):
    b = nbytes(shape)
    return '%-64s [%s bytes]' % (','.join(shape), b if b is not None else '?')


def resolve(v, opcode, stage='CField', tracer=None):
    """Follow the dispatch chain for ONE opcode and stop at the handler.

    Returns (status, handler_va, prefix_tokens, chain). `prefix_tokens` are the bytes the
    dispatchers themselves consume before the handler runs - the opcode Decode2 plus things like
    the mob/character id that pool dispatchers read. Those are exactly the fields the atlas exports
    sometimes include and sometimes do not.

    A call is treated as "still a dispatcher" while it is handed the opcode as an argument; the
    first opcode-selected call that is NOT handed the opcode is the handler.
    """
    t = tracer or Tracer(v)
    t.overrides = {STAGE_CALL[v]: STAGE[v][stage]}
    out, other, seen = [], [], set()
    budget = [STEP_BUDGET]
    work = [(ROOT[v], State(), (), (), (), (False,))]
    for _ in range(200000):
        if not work or len(out) >= 8 or budget[0] <= 0:
            break
        va, st, cs, toks, chain, armed = work.pop()
        cs, toks, chain, armed = list(cs), list(toks), list(chain), list(armed)
        r = _resolve1(t, opcode, va, st, cs, toks, chain, armed, work, seen, budget)
        if r is None:
            continue
        (out if r[0] == 'OK' else other).append(r)
    if out:
        return out[0] if len(out) == 1 else ('OK', out[0][1], out[0][2], out[0][3])
    return other[0] if other else ('NO_PATH', 0, [], [])


def resolve_all(v, opcode, stage='CField', tracer=None):
    """Every distinct handler this opcode can reach, as [(status, handler, prefix_tokens, chain)]."""
    t = tracer or Tracer(v)
    t.overrides = {STAGE_CALL[v]: STAGE[v][stage]}
    out, other, seen, keys = [], [], set(), set()
    budget = [STEP_BUDGET]
    work = [(ROOT[v], State(), (), (), (), (False,))]
    for _ in range(400000):
        if not work or len(out) >= 24 or budget[0] <= 0:
            break
        va, st, cs, toks, chain, armed = work.pop()
        # `seen` is PER PATH. Sharing it across branches looks like a cheap win and is not: a
        # branch that reconverges onto an address the first path already walked gets thrown away
        # whole, and with it the dispatch arm it was about to find.
        r = _resolve1(t, opcode, va, st, list(cs), list(toks), list(chain), list(armed), work,
                      set(), budget)
        if r is None:
            continue
        k = (r[0], r[1], tuple(r[2]))
        if k in keys:
            continue           # dedupe as we go, or duplicates of the early-out arm eat the budget
        keys.add(k)
        (out if r[0] == 'OK' else other).append(r)
    return out or other


def _resolve1(t, opcode, va, st, cs, toks, chain, armed, work, seen, budget=None):
    d2 = 1 if toks else 0
    for _ in range(200000):
        if budget is not None:
            budget[0] -= 1
            if budget[0] <= 0:
                return 'BUDGET', va, toks, chain
        ins = t.ins_at(va)
        if ins is None:
            return 'BADCODE', va, toks, chain
        k = (va, len(cs), tuple(toks))
        if k in seen:
            return None
        seen.add(k)
        nxt = va + ins.size
        m, ops = ins.mnemonic, ins.operands
        imm = ops[0].imm if (ops and ops[0].type == X86_OP_IMM) else None
        if m == 'call':
            tgt = t.overrides.get(va, imm)
            if tgt == t.eh:
                st = t.eh_prolog(st)
                va = nxt
                continue
            args = st.pushed
            st.pushed = ()
            if tgt is None:
                return 'INDIRECT_CALL', va, toks, chain
            if tgt in t.dec:
                n = t.dec[tgt]
                toks.append(t.dtok(n, st))
                if n == 'Decode2' and not d2 and not cs:
                    st.r['eax'] = ('OP', opcode)
                    d2 = 1
                else:
                    st.r['eax'] = None
                if n == 'DecodeBuffer':
                    st.esp += 8
                elif n == 'DecodeStr':
                    st.esp += 4
                va = nxt
                continue
            passes_op = any(a is not None and a[0] == 'OP' for a in args)
            # A call handed the opcode is always another dispatcher - including a bare forwarding
            # thunk, which has no opcode branch of its own and so never arms this frame.
            if not passes_op and not armed[-1]:
                st.r['eax'] = None
                st.esp += t.ret_pop(tgt)
                va = nxt
                continue
            chain.append((va, tgt))
            if not passes_op:
                return 'OK', tgt, toks, chain
            st.esp -= 4
            st.stk[st.esp] = ('RET', nxt)
            cs.append(nxt)
            armed.append(False)
            va = tgt
            continue
        if m in ('ret', 'retf'):
            if not cs:
                return 'NO_HANDLER', va, toks, chain
            st.esp += 4 + (imm or 0)
            st.stk = {k: x for k, x in st.stk.items() if k >= st.esp - 0x400}
            va = cs.pop()
            armed.pop()
            continue
        if m == 'jmp':
            if imm is not None:
                va = imm
                continue
            tt = t.jmp_table(st, ins)
            if tt is None:
                return 'INDIRECT_JMP', va, toks, chain
            armed[-1] = True
            va = tt
            continue
        if m in COND:
            r = t.eval_cc(m, st)
            if st.flags and any(x is not None and x[0] == 'OP' for x in st.flags):
                armed[-1] = True
            if r is None:
                # data-dependent (e.g. `if (!m_pStage) return`): explore both
                if len(work) < FORK_CAP:
                    fk = st.copy()
                    fk.stk = {k: x for k, x in fk.stk.items() if k >= fk.esp - 0x200}
                    work.append((nxt, fk, tuple(cs), tuple(toks), tuple(chain), tuple(armed)))
                va = imm
                continue
            va = imm if r else nxt
            continue
        st = t.exec1(st, ins)
        va = nxt
    return 'LOOP', va, toks, chain


def shapes(v, opcode, stage='CField', tracer=None):
    """([tokens...], flags). Tokens INCLUDE the 2-byte opcode read by ProcessPacket."""
    t = tracer or Tracer(v)
    t.overrides = {STAGE_CALL[v]: STAGE[v][stage]}
    return t.run(opcode)


def body(shape):
    """Drop the leading opcode Decode2 so the result lines up with PacketCreator's body."""
    s = list(shape)
    return s[1:] if s and s[0] == '2' else s


