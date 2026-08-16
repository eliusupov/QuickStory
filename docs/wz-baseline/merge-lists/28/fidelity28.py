#!/usr/bin/env python3
"""Ticket 28 fidelity check: every node this ticket ADDED is node-for-node what the
stock v84 archive holds.

verify28.py proves nothing pre-existing moved; it says nothing about whether the new
material is correct. This closes that half, the same way ticket 33's follow-up did:
ground truth is `WzMerge dump <v84>.wz <img> 30` - MapleLib's reader plus a six-line
Print - compared against the committed `.img.xml`, which was produced by MapleLib's
XmlSerializer. Two different code paths over the same source.

The comparator is ticket 33's (docs/wz-baseline/merge-lists/33/fidelity.py), imported
rather than re-implemented, together with its four-mutation self-check.

Read-only. Run: python fidelity28.py
"""
import os, re, sys, glob, subprocess, collections
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", "..", "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "docs", "wz-baseline", "merge-lists", "33"))
from fidelity import Node, LINE, TYPE2TAG, VALUELESS, compare, count, selftest  # noqa: E402

WZMERGE = os.path.join(ROOT, "docs", "wz-baseline", "tool-merge", "bin", "Release",
                       "net10.0-windows", "WzMerge.exe")
V84 = r"D:\games\MapleStory\Server\porting-resources\wz-data\v84"
DEPTH = 30
# MapleLib renamed WzSoundProperty -> WzBinaryProperty; ticket 33's table predates it
# and never met one (Quest.wz has no sound). Both serialise as <sound>, confirmed
# against wz/Effect.wz/Direction4.img.xml, whose only non-scalar tags are
# imgdir/canvas/sound/uol/vector.
TYPE2TAG.setdefault("WzBinaryProperty", "sound")

# Ticket 33's LINE anchors at $ after the optional " = value", so a canvas/sound line -
# "0 [WzCanvasProperty] 1234x600, png 341206 bytes" - does not match it at all and is
# swallowed as a value continuation, taking its whole subtree with it. Quest.wz has no
# canvases, so ticket 33 never met this. Allow a trailing descriptor instead; canvas and
# sound are both in VALUELESS, so the descriptor is discarded either way.
LINE = re.compile(r"^((?:  )*)(\S.*?) \[(Wz[A-Za-z]+)\](?: = (.*)| ([^=].*))?$")

# `vector` and `canvas` are in ticket 33's VALUELESS set because Quest.wz has neither.
# They are NOT valueless: both sides carry real data in different shapes, and leaving them
# in VALUELESS silently drops 1,028 vector coordinates and 599 canvas dimensions from the
# comparison. Normalise both sides to one string instead.
#   vector  dump "X: 400, Y: 300"                     xml  x="400" y="300"   -> "400,300"
#   canvas  dump "1234x600, png 341206 bytes"         xml  width= height=    -> "1234x600"
# The png byte count is deliberately NOT compared: this tree is serialised with
# exportbase64:false (procedure section 9), so the XML carries no pixel payload to compare
# it against. Dimensions are the whole of what both sides hold.
VALUELESS = VALUELESS - {"canvas", "vector"}
VEC = re.compile(r"X:\s*(-?\d+),\s*Y:\s*(-?\d+)")
DIM = re.compile(r"^(\d+)x(\d+)")


def dump_value(tag, after_eq, trailing):
    """The comparable value of a dump line, per tag."""
    if tag == "vector":
        m = VEC.search(after_eq or "")
        return f"{m.group(1)},{m.group(2)}" if m else None
    if tag == "canvas":
        m = DIM.match(trailing or "")
        return f"{m.group(1)}x{m.group(2)}" if m else None
    return None if tag in VALUELESS else after_eq


def xml_value(el):
    """The same value, read off the serialised XML element."""
    if el.tag == "vector":
        return f"{el.get('x')},{el.get('y')}"
    if el.tag == "canvas":
        return f"{el.get('width')}x{el.get('height')}"
    return None if el.tag in VALUELESS else el.get("value")


ARCHIVES = ("Effect", "Etc", "Map", "Mob", "Npc", "Quest", "Reactor", "Skill", "String")


def dump_tree(archive, imgpath):
    """Parse `WzMerge dump` output into the root Node for <imgpath>."""
    # `chcp 65001` first: the .NET console otherwise writes the OEM codepage and any
    # non-ASCII character in a String.wz description comes back mangled. Decoding
    # STRICTLY (no errors="replace") is what makes a mangled codepage raise instead of
    # silently comparing equal.
    cmd = (f'chcp 65001>nul & "{WZMERGE}" dump "{os.path.join(V84, archive + ".wz")}" '
           f'"{imgpath}" {DEPTH}')
    r = subprocess.run(cmd, shell=True, capture_output=True)
    assert r.returncode == 0, f"dump {archive}/{imgpath}: {r.stdout}{r.stderr}"
    lines = r.stdout.decode("utf-8").split("\n")
    assert "iv=" in lines[0], f"{imgpath}: unexpected dump header {lines[0]!r}"
    root, stack, last = None, [], None
    nodes = conts = maxd = 0
    body = [l.rstrip("\r") for l in lines[1:] if l.strip("\r") != ""]
    for ln in body:
        m = LINE.match(ln)
        d = len(m.group(1)) // 2 if m else -1
        if m is None or d > len(stack):
            assert last is not None, f"{imgpath}: orphan continuation {ln!r}"
            last.value = (last.value or "") + "\n" + ln
            conts += 1
            continue
        _, name, wztype, value, trailing = m.groups()
        tag = TYPE2TAG.get(wztype)
        assert tag, f"{imgpath}: unmapped wz type {wztype}"
        n = Node(tag, name, dump_value(tag, value, trailing))
        del stack[d:]
        if d == 0:
            assert root is None, f"{imgpath}: two roots in one dump"
            root = n
        else:
            stack[d - 1].kids.append(n)
        stack.append(n)
        last = n
        nodes += 1
        maxd = max(maxd, d)
    assert len(body) == nodes + conts, (
        f"{imgpath}: {len(body)} lines vs {nodes} nodes + {conts} continuations "
        "- the parser lost lines")
    assert maxd < DEPTH - 1, f"{imgpath}: max depth {maxd} - the dump may be truncated"
    return root


def xml_tree(relpath):
    raw = open(os.path.join(ROOT, relpath), "rb").read()
    assert not raw.startswith(b"\xef\xbb\xbf"), f"{relpath}: BOM"
    # An XML parser normalises a raw TAB inside an attribute value to a space
    # (XML 1.0 3.3.3). String.wz/Cash.img/5240019/name really does contain eleven
    # tabs, and reading it normally would silently compare equal to spaces. Escaping
    # them first makes the comparison exact instead of blind. Indentation in this tree
    # is spaces, so no structural tab is touched.
    raw = raw.replace(b"\t", b"&#9;")

    def conv(el):
        n = Node(el.tag, el.get("name"), xml_value(el))
        n.kids = [conv(c) for c in el]
        return n

    return conv(ET.fromstring(raw))


def reconcile(src, mrg, stats):
    """Two KNOWN, MEASURED differences between the two readers - not between the two
    trees. Both are reconciled here explicitly rather than being tolerated silently,
    and each is counted so the report states how much it stopped checking.

      float : the dump prints .NET's float ToString ("10"); MapleLib's XmlSerializer
              writes "10.0". Compared numerically instead.
      uol   : `dump` FOLLOWS a WzUOLProperty and prints the RESOLVED target's children
              with no link string (WZ-MERGE-PROCEDURE 6.1, note 2), while the XML holds
              the literal <uol value="../0/0"/>. Type and presence are checked; the link
              string cannot be checked from a dump, so both sides are pruned and counted.
    """
    # A regex regression here would return None on BOTH sides, and None == None compares
    # equal - the failure mode that would quietly un-check every coordinate again. Count
    # them and require every one to have parsed.
    if src.tag in ("vector", "canvas"):
        stats[src.tag] += 1
        if src.value is None or mrg.value is None:
            stats["unparsed"] += 1
    if src.tag == "float" and mrg.tag == "float":
        try:
            if float(src.value) == float(mrg.value):
                src.value = mrg.value
                stats["float"] += 1
        except (TypeError, ValueError):
            pass
    if src.tag == "uol":
        if mrg.tag == "uol" and mrg.value:
            src.kids, mrg.kids = [], []
            src.value = mrg.value
            stats["uol"] += 1
        return
    bym = {}
    for k in mrg.kids:
        bym.setdefault(k.name, []).append(k)
    seen = {}
    for k in src.kids:
        i = seen.get(k.name, 0)
        seen[k.name] = i + 1
        c = bym.get(k.name, [])
        if i < len(c):
            reconcile(k, c[i], stats)


def descend(node, sub):
    for seg in sub.split("/"):
        nxt = [k for k in node.kids if k.name == seg]
        if not nxt:
            return None
        node = nxt[0]
    return node


def main():
    rows = collections.defaultdict(list)   # (archive, img) -> [sub, ...]
    # Etc-appended is excluded: those rows deliberately do NOT sit at their v84 path, so
    # they are compared through their recorded mapping further down instead.
    for p in sorted(glob.glob(os.path.join(HERE, "*.paths.txt"))):
        if os.path.basename(p) == "Etc-appended.paths.txt":
            continue
        for line in open(p, encoding="utf-8"):
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            arch, rest = line.split(".wz/", 1)
            img, sep, sub = rest.partition(".img/")
            if not sep:
                img, sub = rest[:-4] if rest.endswith(".img") else rest, ""
            rows[(arch, img)].append(sub)

    log, findings, total_nodes, total_rows = [], [], 0, 0
    recon = collections.Counter()

    def say(s):
        print(s)
        log.append(s)

    # -- comparator self-check first; a comparator that cannot fail proves nothing --
    probe_src = dump_tree("String", "MonsterBook.img/3400000")
    say("== comparator self-check (mutations of a real added subtree "
        f"String.wz/MonsterBook.img/3400000, {count(probe_src)} nodes)")
    if not selftest(probe_src, log):
        say("SELF-CHECK FAILED - results void")
        return 2
    print("\n".join(log[-6:]))

    say("")
    for (arch, img), subs in sorted(rows.items()):
        src_img = dump_tree(arch, img + ".img")
        mrg_img = xml_tree(f"wz/{arch}.wz/{img}.img.xml")
        n_here = 0
        for sub in sorted(subs):
            total_rows += 1
            s = descend(src_img, sub) if sub else src_img
            m = descend(mrg_img, sub) if sub else mrg_img
            label = f"{arch}.wz/{img}.img" + (f"/{sub}" if sub else "")
            if s is None:
                findings.append(f"{label}: manifest row ABSENT from the v84 source")
                continue
            if m is None:
                findings.append(f"{label}: manifest row ABSENT from the merged XML")
                continue
            # count AFTER reconcile: it prunes the descendants `dump` invented by
            # following a UOL, and those are not nodes in the merged tree, so counting
            # them would overstate what was compared.
            reconcile(s, m, recon)
            n_here += count(s)
            compare(s, m, label, findings)
        total_nodes += n_here
        say(f"  {arch}.wz/{img}.img: {len(subs)} rows, {n_here} nodes compared")

    # -- the re-slotted cash-shop rows --------------------------------------------------
    # append-commodity.py could not keep v84's slot names (they are taken here by other
    # rows), so these are compared v84-slot -> new-slot by the mapping it recorded. The
    # comparator ignores the node's own name, only its type, value and children, which is
    # exactly the right granularity for a row that was deliberately re-indexed.
    appended = os.path.join(HERE, "Etc-Commodity.APPENDED.txt")
    if os.path.exists(appended):
        src_img = dump_tree("Etc", "Commodity.img")
        mrg_img = xml_tree("wz/Etc.wz/Commodity.img.xml")
        n = rows_n = 0
        for line in open(appended, encoding="utf-8"):
            if line.startswith("#") or not line.strip():
                continue
            newslot, sn, v84slot, _ = line.strip().split("\t")
            rows_n += 1
            s, m = descend(src_img, v84slot), descend(mrg_img, newslot)
            if s is None or m is None:
                findings.append(f"Commodity re-slot {v84slot}->{newslot}: missing side")
                continue
            reconcile(s, m, recon)
            n += count(s)
            compare(s, m, f"Etc.wz/Commodity.img[{v84slot}->{newslot}]", findings)
        total_rows += rows_n
        total_nodes += n
        say(f"  Etc.wz/Commodity.img (re-slotted): {rows_n} rows, {n} nodes compared")

    say(f"\n== {total_rows} manifest rows, {total_nodes} nodes compared against stock v84")
    say(f"   values compared by shape: {recon['vector']} vector coordinates, "
        f"{recon['canvas']} canvas dimensions  (unparsed on either side: {recon['unparsed']})")
    say(f"   reader reconciliations: {recon['float']} float values compared numerically, "
        f"{recon['uol']} UOL nodes compared by type+presence only (dump resolves the link)")
    if recon["unparsed"]:
        say("== a vector or canvas value failed to parse on one side; None==None would have "
            "compared equal, so this result is VOID")
        return 2
    if findings:
        say(f"== {len(findings)} DIVERGENCES")
        for f in findings[:60]:
            say("  " + f)
    else:
        say("== 0 divergences: every added node is what v84 holds "
            "(name, type, value, child order)")
    with open(os.path.join(HERE, "fidelity28-report.txt"), "w", encoding="utf-8",
              newline="\n") as fh:
        fh.write("\n".join(log) + "\n")
    return 1 if findings else 0


if __name__ == "__main__":
    sys.exit(main())
