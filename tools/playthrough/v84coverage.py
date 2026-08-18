#!/usr/bin/env python3
"""v84 coverage matrix: what v84 added, and whether our tree carries it.

`docs/wz-baseline/add-list/*.txt` is the computed v84-minus-v83 diff and is this
project's authority on what v84 actually added. Each line there is a COPY ROOT:
no listed path is an ancestor of another. This tool asks, for every one of them,
the only question that can be answered mechanically:

    does that exact node exist in our own wz/ tree?

That is the MERGE dimension, and it is exact. It is deliberately NOT the same
question as "the server supports it" -- a merged node the server never reads is
still a gap, and a missing node the server never reads is not one. So every miss
is also classified by whether the SERVER reads that part of the archive, using
the table in SERVER_SECTIONS below. Anything the table does not recognise comes
out as `unclassified` rather than being guessed at.

Run:
    python tools/playthrough/v84coverage.py

Writes:
    docs/work-plan/V84-COVERAGE.tsv   one row per missing node
and prints the matrix.

Known limits, stated rather than hidden:

  * **Arrays are index-keyed.** `life`, `portal`, `reactor` and friends are
    arrays whose child names are ordinals. A missing `reactor/14` means our
    array is shorter; it does NOT follow that a PRESENT `reactor/3` holds the
    same content as v84's. This tool therefore undercounts array divergence.
    Ticket 53 is the precedent for how that class is actually resolved.
  * Presence is not correctness. A node can exist with a v83 value.
  * `add-list` covers v84-minus-v83-stock. It says nothing about content this
    project added itself; `docs/wz-baseline/protect-list/` is that side.
"""
import os
import sys
import collections
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
ADD = os.path.join(ROOT, "docs", "wz-baseline", "add-list")
WZ = os.path.join(ROOT, "wz")
OUT = os.path.join(ROOT, "docs", "work-plan", "V84-COVERAGE.tsv")

# Which parts of each archive the SERVER reads. Second element is the set of
# top-level sections that are server-read; "*" means the whole archive is.
# Everything not listed is client-only art/text and a miss there is not a gap.
SERVER_SECTIONS = {
    "Reactor": "*",
    "Skill": "*",
    "Morph": None,        # client
    "Effect": None,       # client
    "Sound": None,        # client
    "UI": None,           # client
    "Base": None,         # client
    "TamingMob": None,    # client
    # Map.wz/Map/MapN/<id>.img/<section>/...
    "Map": {"info", "life", "portal", "foothold", "reactor", "ladderRope",
            "seat", "area", "monsterCarnival", "timeMob", "clock", "buff",
            "shipObj", "snowMan", "healer", "pulley"},
    # Item.wz/<cat>/<img>.img/<id>/<section>/...
    "Item": {"info", "spec", "reward", "mob"},
    # Character.wz/<slot>/<id>.img/<section>/... -- server reads info only
    "Character": {"info"},
    "Mob": {"info", "skill", "attack"},
    "Npc": {"info"},
    # Quest.wz/<img>/...  keyed on the IMAGE, not a section
    "Quest": {"Check.img", "Act.img", "QuestInfo.img"},
    # Etc.wz/<img>/...
    "Etc": {"Commodity.img", "ItemMake.img", "CashPackage.img"},
    # String.wz is read for names only; treat the whole archive as advisory
    "String": {"Npc.img", "Map.img", "Eqp.img", "Etc.img", "Consume.img",
               "Ins.img", "Cash.img", "Pet.img", "Mob.img", "Skill.img"},
}

# Second pass. A section can be server-read while a specific leaf inside it is
# not, and two whole classes of miss are already-settled non-defects. Each entry
# is (predicate on the add-list path, why it is not a gap). Keeping the reason
# in the code is the point: these are the misses that keep getting re-filed.
BENIGN = [
    (lambda p: p.startswith("Mob.wz/") and p.endswith("/info/category"),
     "mobType->category rename; dead field before and after, swept clean"),
    (lambda p: p.startswith("Etc.wz/Commodity.img/")
     and p.rsplit("/", 1)[-1] not in
     ("SN", "ItemId", "Price", "Period", "Count", "OnSale"),
     "CashShop.java:243-248 reads only SN/ItemId/Price/Period/Count/OnSale"),
    (lambda p: p.startswith("String.wz/Npc.img/")
     and p.rsplit("/", 1)[-1] not in ("name", "d0"),
     "the server reads name (LifeFactory.java:295) and d0 (:299) only; "
     "func/d1/n0/n1 are drawn by the client from its own archive"),
    (lambda p: p.startswith("Npc.wz/")
     and p.rsplit("/", 1)[-1] not in ("trunkPut", "trunkGet"),
     "Storage.java:318,336 read info/trunkPut and info/trunkGet and nothing "
     "else from Npc.wz; names come from String.wz, scripts from the npc id"),
    (lambda p: p.startswith("Mob.wz/") and p.endswith("/info/default"),
     "animation default; MonsterStats reads no such leaf"),
    (lambda p: p.startswith("String.wz/Map.img/")
     and p.rsplit("/", 1)[-1] not in ("mapName", "streetName"),
     "map blurb text; the server reads mapName/streetName only"),
]


def benign(path):
    for pred, why in BENIGN:
        if pred(path):
            return why
    return ""


def split_root(path):
    """'Item.wz/Consume/0202.img/02022539/spec' -> ('Item.wz/Consume/0202.img.xml', [...])."""
    parts = path.split("/")
    for i, p in enumerate(parts):
        if p.endswith(".img"):
            return "/".join(parts[: i + 1]) + ".xml", parts[i + 1:]
    return None, parts


def server_reads(archive, add_path, node):
    """True / False / None(unclassified) for whether the server reads this node."""
    rule = SERVER_SECTIONS.get(archive, "unset")
    if rule == "unset":
        return None
    if rule is None:
        return False
    if rule == "*":
        return True
    if archive in ("Quest", "Etc", "String"):
        parts = add_path.split("/")
        return parts[1] in rule if len(parts) > 1 else None
    if archive == "Item":
        # Item.wz/<cat>/<img>.img/<itemid>/<section>/... -- the id comes first,
        # so the section is one deeper than in every other archive.
        return node[1] in rule if len(node) > 1 else True
    if archive in ("Map", "Character", "Mob", "Npc"):
        # node is the path *inside* the image; its first element is the section
        return node[0] in rule if node else True
    return None


def node_paths(xmlfile):
    """Every '/'-joined node path inside one WZ XML (image root name dropped)."""
    out = set()
    stack = []
    try:
        for ev, el in ET.iterparse(xmlfile, events=("start", "end")):
            if ev == "start":
                stack.append(el.get("name"))
                if stack[-1] is not None:
                    out.add("/".join(p for p in stack[1:] if p is not None))
            else:
                if stack:
                    stack.pop()
                el.clear()
    except ET.ParseError as exc:
        print("  PARSE FAIL %s: %s" % (xmlfile, exc), file=sys.stderr)
        return None
    return out


def main():
    rows = []
    matrix = []
    for fn in sorted(os.listdir(ADD)):
        if not fn.endswith(".txt"):
            continue
        archive = fn[:-4]
        with open(os.path.join(ADD, fn), encoding="utf-8") as fh:
            roots = [ln.strip() for ln in fh
                     if ln.strip() and not ln.startswith("#")]
        if not roots:
            matrix.append((archive, 0, 0, 0, 0, 0, 0))
            continue

        by_file = collections.defaultdict(list)
        unresolved = []
        for r in roots:
            f, node = split_root(r)
            (unresolved if f is None else by_file[f]).append((r, node))

        present = 0
        misses = []
        for f, entries in sorted(by_file.items()):
            full = os.path.join(WZ, f.replace("/", os.sep))
            if not os.path.exists(full):
                misses += [(r, node, "IMAGE_ABSENT") for r, node in entries]
                continue
            inner = [e for e in entries if e[1]]
            present += len(entries) - len(inner)  # root WAS the image; it exists
            if not inner:
                continue
            paths = node_paths(full)
            if paths is None:
                misses += [(r, node, "PARSE_FAIL") for r, node in inner]
                continue
            for r, node in inner:
                if "/".join(node) in paths:
                    present += 1
                else:
                    misses.append((r, node, "NODE_ABSENT"))
        misses += [(r, node, "UNRESOLVED_PATH") for r, node in unresolved]

        srv = cli = ben = unk = 0
        for r, node, reason in misses:
            verdict = server_reads(archive, r, node)
            why = benign(r) if verdict is True else ""
            if why:
                label, ben = "benign", ben + 1
            else:
                label = {True: "GAP", False: "client", None: "unclassified"}[verdict]
                srv += verdict is True
                cli += verdict is False
                unk += verdict is None
            rows.append((archive, r, reason, label, why))
        matrix.append((archive, len(roots), present, srv, ben, cli, unk))

    hdr = ("archive", "v84_added", "in_our_wz", "GAP", "benign", "client", "unclass")
    print("%-12s %10s %10s %8s %8s %8s %8s" % hdr)
    tot = [0] * 6
    for a, t, p, s, b, c, u in matrix:
        print("%-12s %10d %10d %8d %8d %8d %8d" % (a, t, p, s, b, c, u))
        for i, v in enumerate((t, p, s, b, c, u)):
            tot[i] += v
    print("%-12s %10d %10d %8d %8d %8d %8d" % ("TOTAL", *tot))
    print("\nOnly the GAP column is open work. `benign` is a miss on a node the\n"
          "server reads nothing from -- each carries its reason in the TSV.\n"
          "`client` is art and text the server never opens.")

    with open(OUT, "w", encoding="utf-8", newline="") as fh:
        fh.write("archive\tadd_list_path\treason\tread_by\tbenign_because\n")
        for row in rows:
            fh.write("\t".join(row) + "\n")
    print("\n%d missing nodes -> %s" % (len(rows), OUT))


if __name__ == "__main__":
    main()
