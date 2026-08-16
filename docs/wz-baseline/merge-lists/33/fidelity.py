#!/usr/bin/env python3
"""Ticket 33 fidelity check: prove each ADDED quest subtree in wz/Quest.wz/*.img.xml
is node-for-node identical to the v84 source Quest.wz.

Ticket 33's own Proof A ignores the added id ranges by construction and Proof B compares
names only.  This closes that gap: id by id, node by node, name / type / value / order.

Ground truth comes from `WzMerge dump <v84 Quest.wz> <Img>.img 20`, which is a DIFFERENT
code path from the `xml` writer that produced the merged files (MapleLib reader + a 6-line
Print, vs. MapleLib's XmlSerializer).  Read-only: this script never writes wz/.

Run:
  python fidelity.py <dumpDir> <wzQuestDir> <idsDir> [--out report.txt]

Self-check (always runs, before the real comparison): four deliberate mutations of a real
added subtree -- changed value, dropped child, extra child, reordered children -- each must
be REPORTED, and the unmutated control must compare clean.  A comparator that cannot fail
is not evidence.
"""
import hashlib
import re
import sys
import xml.etree.ElementTree as ET

IMAGES = ("QuestInfo", "Check", "Act")

# dump type name -> XML element tag written by MapleLib's XmlSerializer
TYPE2TAG = {
    "WzSubProperty": "imgdir",
    "WzImage": "imgdir",
    "WzIntProperty": "int",
    "WzShortProperty": "short",
    "WzLongProperty": "long",
    "WzStringProperty": "string",
    "WzNullProperty": "null",
    "WzFloatProperty": "float",
    "WzDoubleProperty": "double",
    "WzUOLProperty": "uol",
    "WzVectorProperty": "vector",
    "WzCanvasProperty": "canvas",
    "WzSoundProperty": "sound",
    "WzConvexProperty": "extended",
    "WzLuaProperty": "lua",
}
# tags whose XML form carries no value= attribute; the dump prints ToString() for them
# anyway when they happen to be childless, so the value is not comparable.
VALUELESS = {"imgdir", "null", "canvas", "sound", "vector", "extended", "lua"}

LINE = re.compile(r"^((?:  )*)(.*?) \[(Wz[A-Za-z]+)\](?: = (.*))?$")


class Node:
    __slots__ = ("tag", "name", "value", "kids")

    def __init__(self, tag, name, value):
        self.tag, self.name, self.value = tag, name, value
        self.kids = []


# ---------------------------------------------------------------- source dump

def parse_dump(path):
    """Parse WzMerge's indented dump into {questId: Node}.

    Hazard handled: a string value may itself contain a newline, which Console.WriteLine
    emits verbatim.  Such a continuation line is only treated as a new node if it both
    matches LINE and sits at a legal indent (<= parent depth + 1); otherwise it is glued
    back onto the previous value.
    """
    raw = open(path, "rb").read()
    assert not raw.startswith(b"\xef\xbb\xbf"), f"{path}: BOM"
    text = raw.decode("utf-8")  # raises on a mangled console codepage
    lines = text.split("\r\n")
    assert lines[0].startswith("Quest.wz") or "iv=" in lines[0], f"{path}: bad header"

    roots, stack, last = {}, [], None
    stats = {"nodes": 0, "continuations": 0, "maxdepth": 0}
    for ln in lines[1:]:
        if ln == "":
            continue
        m = LINE.match(ln)
        depth = len(m.group(1)) // 2 if m else -1
        if m is None or depth > len(stack):
            # continuation of the previous node's value
            assert last is not None, f"{path}: orphan continuation {ln!r}"
            last.value = (last.value or "") + "\n" + ln
            stats["continuations"] += 1
            continue
        _, name, wztype, value = m.groups()
        tag = TYPE2TAG.get(wztype)
        assert tag, f"{path}: unmapped wz type {wztype}"
        node = Node(tag, name, None if tag in VALUELESS else value)
        del stack[depth:]
        if depth == 0:
            assert node.name not in roots, f"{path}: duplicate root {node.name}"
            roots[node.name] = node
        else:
            stack[depth - 1].kids.append(node)
        stack.append(node)
        last = node
        stats["nodes"] += 1
        stats["maxdepth"] = max(stats["maxdepth"], depth)
    # instrument proof: every non-blank dump line was either a node or a continuation --
    # nothing was silently dropped by the indent rule.
    body = [l for l in lines[1:] if l != ""]
    assert len(body) == stats["nodes"] + stats["continuations"], (
        f"{path}: {len(body)} lines but {stats['nodes']} nodes "
        f"+ {stats['continuations']} continuations -- parser lost lines")
    # depth 20 was requested from `dump`; anything at 19+ may have been truncated
    assert stats["maxdepth"] < 19, f"{path}: maxdepth {stats['maxdepth']} -- dump may be truncated"
    # a whole-image dump has a single root, the WzImage itself; the quests are its children
    if len(roots) == 1 and next(iter(roots)).endswith(".img"):
        return {k.name: k for k in next(iter(roots.values())).kids}, stats
    return roots, stats


# ------------------------------------------------------------------ merged xml

def parse_xml(path):
    raw = open(path, "rb").read()
    assert not raw.startswith(b"\xef\xbb\xbf"), f"{path}: BOM"

    def conv(el):
        tag = el.tag
        assert tag in VALUELESS or tag in ("int", "short", "long", "string", "float",
                                           "double", "uol"), f"{path}: tag {tag}"
        n = Node(tag, el.get("name"), None if tag in VALUELESS else el.get("value"))
        n.kids = [conv(c) for c in el]
        return n

    root = ET.fromstring(raw)
    return {c.get("name"): conv(c) for c in root}


# ------------------------------------------------------------------ comparison

def compare(a, b, path, out):
    """a = v84 source, b = merged.  Order-sensitive, depth-first."""
    if a.tag != b.tag:
        out.append(f"{path}: TYPE  v84={a.tag} merged={b.tag}")
        return
    if a.value != b.value:
        out.append(f"{path}: VALUE v84={a.value!r} merged={b.value!r}")
    an = [k.name for k in a.kids]
    bn = [k.name for k in b.kids]
    if an != bn:
        if sorted(an) == sorted(bn):
            out.append(f"{path}: CHILD ORDER v84={an} merged={bn}")
        else:
            for miss in [x for x in an if x not in bn]:
                out.append(f"{path}/{miss}: MISSING in merged")
            for extra in [x for x in bn if x not in an]:
                out.append(f"{path}/{extra}: EXTRA in merged (not in v84)")
            dup = [x for x in set(bn) if bn.count(x) != an.count(x) and x in an]
            for d in dup:
                out.append(f"{path}/{d}: COUNT v84={an.count(d)} merged={bn.count(d)}")
    bmap = {}
    for k in b.kids:
        bmap.setdefault(k.name, []).append(k)
    seen = {}
    for k in a.kids:
        i = seen.get(k.name, 0)
        seen[k.name] = i + 1
        cand = bmap.get(k.name, [])
        if i < len(cand):
            compare(k, cand[i], f"{path}/{k.name}", out)
    return


def count(n):
    return 1 + sum(count(k) for k in n.kids)


def clone(n):
    c = Node(n.tag, n.name, n.value)
    c.kids = [clone(k) for k in n.kids]
    return c


# ------------------------------------------------------------------- self-test

def selftest(src, log):
    """Deliberately damage a real added subtree four ways; every one must be caught."""
    ok = True

    def check(label, mutate, expect):
        nonlocal ok
        dmg = clone(src)
        where = mutate(dmg)
        out = []
        compare(src, dmg, "SELFTEST", out)
        hit = any(expect in o for o in out)
        log.append(f"  {'CAUGHT ' if hit else 'MISSED!'} {label} ({where}) -> "
                   + ("; ".join(out) if out else "NO FINDINGS"))
        ok = ok and hit

    # control: an untouched clone must be clean, or the mutation tests prove nothing
    out = []
    compare(src, clone(src), "SELFTEST", out)
    log.append(f"  {'CLEAN  ' if not out else 'DIRTY! '} control (unmutated clone) -> "
               + ("no findings" if not out else "; ".join(out)))
    ok = ok and not out

    def find_leaf(n, p=""):
        for k in n.kids:
            if k.value is not None:
                return k, f"{p}/{k.name}"
            r = find_leaf(k, f"{p}/{k.name}")
            if r:
                return r
        return None

    leaf, lpath = find_leaf(src)

    def mut_value(d):
        t, p = find_leaf(d)
        t.value = (t.value or "") + "_CORRUPTED"
        return p

    def mut_drop(d):
        n = d
        while n.kids and len(n.kids) < 2:
            n = n.kids[0]
        n.kids.pop()
        return "dropped last child"

    def mut_extra(d):
        d.kids.append(Node("int", "bogusChild", "999"))
        return "appended bogusChild"

    def mut_order(d):
        n = d
        while len(n.kids) < 2 and n.kids:
            n = n.kids[0]
        n.kids[0], n.kids[1] = n.kids[1], n.kids[0]
        return "swapped two children"

    check("changed leaf value", mut_value, "VALUE")
    check("dropped child", mut_drop, "MISSING in merged")
    check("extra child", mut_extra, "EXTRA in merged")
    check("reordered children", mut_order, "CHILD ORDER")
    return ok


# ----------------------------------------------------------------------- main

def main():
    dumpdir, wzdir, idsdir = sys.argv[1], sys.argv[2], sys.argv[3]
    outpath = sys.argv[sys.argv.index("--out") + 1] if "--out" in sys.argv else None
    log = []

    def say(s):
        print(s)
        log.append(s)

    say("== inputs")
    findings, total_nodes, total_ids = [], 0, 0
    for img in IMAGES:
        for p in (f"{dumpdir}/v84-{img}.txt", f"{wzdir}/{img}.img.xml"):
            b = open(p, "rb").read()
            say(f"  {p}  {len(b)} bytes  sha256={hashlib.sha256(b).hexdigest()[:16]}")

    for img in IMAGES:
        src, st = parse_dump(f"{dumpdir}/v84-{img}.txt")
        mrg = parse_xml(f"{wzdir}/{img}.img.xml")
        ids = [l.strip() for l in open(f"{idsdir}/{img}.new-ids.txt", encoding="utf-8")
               if l.strip() and not l.startswith("#")]
        say(f"\n== {img}: v84 roots={len(src)} merged roots={len(mrg)} claimed-added={len(ids)}")
        say(f"   dump parse: {st['nodes']} nodes, {st['continuations']} value-continuation "
            f"lines, max depth {st['maxdepth']} (all lines accounted for)")
        say(f"   arithmetic: merged {len(mrg)} = pre-merge {len(mrg) - len(ids)} + {len(ids)} added"
            f"  -> {'ok' if len(set(ids)) == len(ids) else 'DUPLICATE IDS IN LIST'}")

        if img == "QuestInfo":
            say("== comparator self-check (mutations of a real added subtree)")
            probe = src[ids[0]]
            say(f"  probe = {img}.img/{ids[0]}  ({count(probe)} nodes)")
            if not selftest(probe, log):
                say("SELF-CHECK FAILED -- comparator cannot detect damage; results void")
                return 2
            print("\n".join(log[-5:]))

        for qid in ids:
            total_ids += 1
            if qid not in src:
                findings.append(f"{img}.img/{qid}: claimed added but ABSENT from v84 source")
                continue
            if qid not in mrg:
                findings.append(f"{img}.img/{qid}: claimed added but ABSENT from merged XML")
                continue
            total_nodes += count(src[qid])
            compare(src[qid], mrg[qid], f"{img}.img/{qid}", findings)

    say(f"\n== compared {total_ids} added quest subtrees, {total_nodes} nodes total")
    if findings:
        say(f"== {len(findings)} DIVERGENCES")
        for f in findings:
            say("  " + f)
    else:
        say("== 0 divergences: every added subtree is node-for-node identical to v84")
    if outpath:
        with open(outpath, "w", encoding="utf-8", newline="\n") as fh:
            fh.write("\n".join(log) + "\n")
    return 1 if findings else 0


if __name__ == "__main__":
    sys.exit(main())
