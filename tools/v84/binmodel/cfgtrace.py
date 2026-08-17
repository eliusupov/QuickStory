"""Static CInPacket::Decode* tracer.

Walks the CFG of a client function and reports, per control-flow path, the ordered sequence of
bytes the client consumes from the packet. This is derived from the BINARY only - no atlas export,
no server code - so a disagreement with PacketCreator is evidence, not noise.

Decode entry points were located by their shared bounds-check shape (findec.py / findbuf.py /
findstr.py); each is verified in dtrace_selfcheck.py.

usage: python dtrace.py <83|84|84b> <entry_va_hex> [--depth N] [--paths N] [-v]
"""
import sys
from capstone import *
from capstone.x86 import X86_OP_IMM, X86_OP_REG, X86_OP_MEM
from .images import img, IMG

# name -> (va83, va84)  -- 84b is the same image layout as 84
DECODES = {
    'Decode1':     (0x4065F3, 0x4066C9),
    'Decode2':     (0x42470C, 0x425200),
    'Decode4':     (0x406629, 0x4066FF),
    'DecodeBuffer':(0x432257, 0x432EBE),
    'DecodeStr':   (0x46F30C, 0x471DED),
}


def decode_map(v):
    i = 0 if v == '83' else 1
    return {va[i]: n for n, va in DECODES.items()}


md = Cs(CS_ARCH_X86, CS_MODE_32)
md.detail = True

SUB32 = {'ax': 'eax', 'cx': 'ecx', 'dx': 'edx', 'bx': 'ebx', 'si': 'esi', 'di': 'edi',
         'al': 'eax', 'cl': 'ecx', 'dl': 'edx', 'bl': 'ebx'}
STOP = {'ret', 'retf', 'iret', 'hlt'}
ALT_CAP = 12   # per-block shape alternatives before falling back to a '?multi' marker
COND = {'jo', 'jno', 'jb', 'jae', 'je', 'jne', 'jbe', 'ja', 'js', 'jns', 'jp', 'jnp',
        'jl', 'jge', 'jle', 'jg', 'jcxz', 'jecxz', 'loop', 'loope', 'loopne'}


class Budget:
    def __init__(self, insns=60000):
        self.insns = insns


def _pushes_before(B, addr_list, idx):
    """Return the immediate operands of the pushes immediately preceding index idx."""
    out = []
    for j in range(idx - 1, max(-1, idx - 8), -1):
        ins = addr_list[j]
        if ins.mnemonic != 'push':
            if ins.mnemonic in ('mov', 'lea', 'xor', 'movzx'):
                continue
            break
        if ins.operands and ins.operands[0].type == X86_OP_IMM:
            out.append(ins.operands[0].imm)
        else:
            out.append(None)
    return out


class Tracer:
    def __init__(self, v, depth=4, budget=None, verbose=False, prune=True, seconds=20.0):
        self.v = v
        self.B = img(v)
        self.dec = decode_map(v)
        self.depth = depth
        self.budget = budget or Budget()
        self.memo = {}
        self.verbose = verbose
        self.seconds = seconds
        self.deadline = None
        if prune:
            from .reach import decoding_set
            self.decoding = decoding_set(v, set(self.dec))
        else:
            self.decoding = None

    # ---- CFG ------------------------------------------------------------
    def cfg(self, entry, d):
        """entry -> {blk_va: (tokens, [succ...], flags)} using single-instruction granularity
        collapsed into blocks. tokens is a list of decode tokens produced by that block."""
        blocks = {}
        work = [entry]
        seen = set()
        flags = set()
        while work:
            va = work.pop()
            if va in seen:
                continue
            seen.add(va)
            off = va - IMG
            if off < 0 or off >= len(self.B):
                flags.add('OOB')
                blocks[va] = ([()], [], {'OOB'})
                continue
            alts, succ, bflags = [()], [], set()
            instrs = []
            cur = va
            done = False
            while not done:
                chunk = self.B[cur - IMG: cur - IMG + 0x200]
                any_ins = False
                for ins in md.disasm(chunk, cur):
                    any_ins = True
                    self.budget.insns -= 1
                    if self.budget.insns % 4096 == 0 and self.deadline and __import__('time').time() > self.deadline:
                        self.budget.insns = 0
                    if self.budget.insns <= 0:
                        bflags.add('BUDGET')
                        done = True
                        break
                    instrs.append(ins)
                    m = ins.mnemonic
                    tgt = None
                    if ins.operands and ins.operands[0].type == X86_OP_IMM:
                        tgt = ins.operands[0].imm
                    if m == 'call':
                        if tgt is None:
                            # indirect call: virtual dispatch. Assume it consumes nothing but flag.
                            bflags.add('INDIRECT_CALL@%08X' % ins.address)
                        elif tgt in self.dec:
                            tk = self.tok(self.dec[tgt], instrs, len(instrs) - 1)
                            alts = [a + (tk,) for a in alts]
                        elif self.decoding is not None and tgt not in self.decoding:
                            pass  # provably consumes no packet bytes
                        else:
                            sub = self.summary(tgt, d + 1)
                            if sub is None:
                                bflags.add('DEPTH@%08X' % ins.address)
                            elif sub['shapes'] is None:
                                bflags.add('UNRESOLVED_CALL@%08X' % tgt)
                                alts = [a + ('?call:%08X' % tgt,) for a in alts]
                            elif len(sub['shapes']) == 1:
                                alts = [a + tuple(sub['shapes'][0]) for a in alts]
                                bflags |= sub['flags']
                            elif len(sub['shapes']) > 1:
                                bflags |= sub['flags']
                                nxt = [a + tuple(x) for a in alts for x in sub['shapes']]
                                if len(nxt) <= ALT_CAP:
                                    alts = nxt
                                else:
                                    bflags.add('MULTISHAPE_CALL@%08X' % tgt)
                                    alts = [a + ('?multi:%08X' % tgt,) for a in alts]
                        continue
                    if m in COND:
                        if tgt is not None:
                            succ.append(tgt)
                        succ.append(ins.address + ins.size)
                        done = True
                        break
                    if m == 'jmp':
                        if tgt is not None:
                            succ.append(tgt)
                        else:
                            arms = self.switch_arms(ins, instrs)
                            if arms:
                                succ.extend(arms)
                            else:
                                bflags.add('INDIRECT_JMP@%08X' % ins.address)
                        done = True
                        break
                    if m in STOP:
                        done = True
                        break
                    if m in ('int3', 'ud2'):
                        done = True
                        break
                if done:
                    break
                if not any_ins:
                    bflags.add('BADCODE@%08X' % cur)
                    break
                cur = instrs[-1].address + instrs[-1].size
            blocks[va] = (alts, succ, bflags)
            for s in succ:
                if s not in seen:
                    work.append(s)
        return blocks

    def tok(self, name, instrs, idx):
        if name == 'Decode1':
            return '1'
        if name == 'Decode2':
            return '2'
        if name == 'Decode4':
            return '4'
        if name == 'DecodeStr':
            return 's'
        # DecodeBuffer(dst, len): push len ; push dst ; call
        pushes = _pushes_before(self.B, instrs, idx)
        # pushes[0] is nearest = dst, pushes[1] = len
        if len(pushes) >= 2 and pushes[1] is not None:
            return 'b%d' % pushes[1]
        n = self.buflen(instrs[idx].address)
        return 'b%d' % n if n is not None else '?buf'

    def back(self, site, window=0x30):
        """Instructions immediately preceding `site`, recovered by finding a start offset whose
        disassembly lands exactly on `site` (x86 has no backwards decode)."""
        # Largest window first: a short window can land on a misaligned decode by accident, a long
        # one essentially cannot.
        for d in range(window, 3, -1):
            o = site - d - IMG
            if o < 0:
                continue
            got = list(md.disasm(self.B[o:o + d], site - d))
            if got and got[-1].address + got[-1].size == site and len(got) >= 2:
                return got
        return []

    def switch_arms(self, ins, instrs):
        """`jmp [reg*4 + table]`: every arm is a successor. The case count comes from the guarding
        `cmp reg, N` a few instructions back. Without this, any handler that switches on a type byte
        read from the packet (the whole NPC_TALK / SHOW_STATUS_INFO family) traces as one arm."""
        op = ins.operands[0]
        if op.type != X86_OP_MEM or op.mem.index == 0 or op.mem.scale != 4 or op.mem.base != 0:
            return None
        idxreg = md.reg_name(op.mem.index)
        # `cmp eax, N / ja default / jmp [eax*4+T]`: the `ja` ends the basic block, so the guarding
        # cmp is usually NOT in this block's instruction list. Look back across the boundary too.
        n = None
        for prev in reversed(list(instrs[-12:]) + self.back(ins.address)):
            if prev.mnemonic == 'cmp' and prev.operands and prev.operands[-1].type == X86_OP_IMM \
                    and prev.operands[0].type == X86_OP_REG \
                    and md.reg_name(prev.operands[0].reg) in (idxreg, SUB32.get(idxreg, idxreg)):
                n = prev.operands[-1].imm + 1
                break
        if not n or not (0 < n <= 256):
            return None
        table = op.mem.disp - IMG
        out = []
        for k in range(n):
            o = table + 4 * k
            if o < 0 or o + 4 > len(self.B):
                return None
            t = int.from_bytes(self.B[o:o + 4], 'little')
            if not (IMG < t < IMG + len(self.B)):
                return None
            out.append(t)
        return out

    def buflen(self, site):
        """`push <len>` is often in a different basic block from the DecodeBuffer call (the compiler
        hoists it above the branch). Re-disassemble backwards: accept only a start that decodes
        cleanly onto the call site with exactly one intervening push (the destination)."""
        for d in range(3, 0x40):
            start = site - d - IMG
            if start < 0:
                break
            ins = list(md.disasm(self.B[start:start + d + 8], site - d))
            if not ins or ins[0].mnemonic != 'push':
                continue
            if not (ins[0].operands and ins[0].operands[0].type == X86_OP_IMM):
                continue
            addrs = [i.address for i in ins]
            if site not in addrs:
                continue
            between = [i for i in ins if ins[0].address < i.address < site]
            if sum(1 for i in between if i.mnemonic == 'push') != 1:
                continue
            if any(i.mnemonic in ('call', 'ret', 'jmp') for i in between):
                continue
            return ins[0].operands[0].imm
        return None

    # ---- summarisation --------------------------------------------------
    def arm(self):
        """Start one opcode's budget. The caller owns this, not summary(): resolving an opcode can
        produce a dozen candidate handlers, and re-arming per handler multiplies the wall-clock
        bound by a dozen - which is how a sweep ends up hanging on the attack packets."""
        import time
        self.deadline = time.time() + self.seconds
        self.budget.insns = 60000

    def summary(self, va, d=0):
        if d == 0 and self.deadline is None:
            self.arm()
        if d > self.depth:
            return None
        key = va
        if key in self.memo:
            return self.memo[key]
        self.memo[key] = {'shapes': None, 'flags': {'RECURSION'}}  # cycle guard
        blocks = self.cfg(va, d)
        flags = set()
        for _, _, bf in blocks.values():
            flags |= bf
        shapes = self.paths(blocks, va, flags)
        r = {'shapes': shapes, 'flags': flags}
        if any(f.split('@')[0] in ('BUDGET', 'TIMEOUT', 'PATH_EXPLOSION') for f in flags):
            # A truncated summary must not be cached: it would silently shorten every later opcode
            # that calls the same helper, and a model that is short in the wrong direction is
            # exactly the failure this tool exists to catch.
            del self.memo[key]
        else:
            self.memo[key] = r
        return r

    def paths(self, blocks, entry, flags, cap=64, states=200000):
        """Worklist over (block, token-sequence-so-far) with per-block dedup. Blocks that consume
        nothing converge immediately, so this does not blow up the way naive path enumeration does."""
        seen = {}
        out = []
        work = [(entry, ())]
        n = 0
        while work:
            va, acc = work.pop()
            n += 1
            if n > states or len(out) > cap:
                flags.add('PATH_EXPLOSION')
                break
            if n % 8192 == 0 and self.deadline and __import__('time').time() > self.deadline:
                flags.add('TIMEOUT')
                break
            if va not in blocks:
                continue
            s = seen.setdefault(va, set())
            if acc in s:
                continue
            if len(s) > cap:
                flags.add('PATH_EXPLOSION')
                break
            s.add(acc)
            balts, succ, _ = blocks[va]
            for alt in balts:
                acc2 = acc + tuple(alt)
                if not succ:
                    if acc2 not in out:
                        out.append(acc2)
                    continue
                for t in succ:
                    work.append((t, acc2))
        # any block whose own tokens feed back into itself is a decode loop
        for va, (balts, succ, _) in blocks.items():
            if any(balts) and va in self._reachable_from(blocks, succ):
                flags.add('DECODE_LOOP@%08X' % va)
                break
        return [list(x) for x in out]

    @staticmethod
    def _reachable_from(blocks, starts):
        seen, work = set(), list(starts)
        while work:
            v = work.pop()
            if v in seen or v not in blocks:
                continue
            seen.add(v)
            work.extend(blocks[v][1])
        return seen


def nbytes(shape):
    n = 0
    for t in shape:
        if t == '1':
            n += 1
        elif t == '2':
            n += 2
        elif t == '4':
            n += 4
        elif t.startswith('b') and t[1:].isdigit():
            n += int(t[1:])
        else:
            return None
    return n


def fmt(shape):
    return ','.join(shape) + '   [%s bytes]' % (nbytes(shape) if nbytes(shape) is not None else '?')


