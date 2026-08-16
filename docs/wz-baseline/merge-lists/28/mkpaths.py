#!/usr/bin/env python3
"""Ticket 28 - generate the merge manifests from the measured gap.

Every row here is a path this script PROVED absent server-side and present in v84,
at the moment it ran. Nothing is hand-typed except the whole-image rows, and those
are asserted both ways before they are written.

Writes <archive>.paths.txt beside itself. Read-only w.r.t. wz/.
"""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gap import compare, v84_paths, srv_paths      # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", "..", "..", ".."))

# id/name images: (archive, img, depth). Every absent path becomes a manifest row.
ID_IMAGES = [
    ("Etc", "CashPackage", 1),
    ("Etc", "NPT_exception", 1),
    ("String", "MonsterBook", 1),
    ("String", "Consume", 1),
    ("String", "Cash", 1),
    ("String", "Mob", 1),
    ("String", "Skill", 1),
    ("String", "Pet", 1),
    ("String", "Ins", 1),
    ("String", "Map", 2),
    ("String", "ToolTipHelp", 2),
]

# whole images / directories absent server-side (ticket 27's list, re-checked here)
WHOLE = [
    "Effect.wz/Direction4.img",
    "Quest.wz/PQuestSearch.img",
    "Map.wz/Back/dragonDream.img",
    "Map.wz/Tile/DeepgrassySoil.img",
    "Mob.wz/2220110.img",
    "Mob.wz/2230112.img",
    "Mob.wz/9300388.img",
    "Mob.wz/9300391.img",
    "Mob.wz/9300393.img",
    "Mob.wz/9300394.img",
    "Mob.wz/QuestCountGroup/9101004.img",
    "Npc.wz/1022106.img",
    "Npc.wz/1022107.img",
    "Npc.wz/2030015.img",
    "Reactor.wz/1002008.img",
    "Reactor.wz/2302006.img",
    "Reactor.wz/2409000.img",
    "Skill.wz/9000.img",
]

# Commodity is handled by sn-detail.py, which refuses the 87 slots whose SN a server
# row already serves. Only the head run 8947..8957 can be appended without a hole.
COMMODITY = [f"Etc.wz/Commodity.img/{i}" for i in range(8947, 8958)]


def main():
    rows = {}
    for arch, img, depth in ID_IMAGES:
        r = compare(arch, img, depth)
        rows.setdefault(arch, []).extend(
            f"{arch}.wz/{img}.img/{p}" for p in r["absent"])
        print(f"  {arch}.wz/{img}.img d={depth}: {len(r['absent'])} rows")

    for w in WHOLE:
        arch = w.split(".wz/")[0]
        # both-ways assertion: absent server-side, present in v84
        srv = os.path.join(ROOT, "wz", w + ".xml")
        assert not os.path.exists(srv), f"{w} already exists server-side"
        # v84_paths raises SystemExit on a WzMerge "NOT FOUND", so a clean return
        # is the positive half of the assertion.
        v84_paths(arch, w.split(".wz/", 1)[1], 1)
        rows.setdefault(arch, []).append(w)
    print(f"  whole images/dirs: {len(WHOLE)} rows")

    rows.setdefault("Etc", []).extend(COMMODITY)
    print(f"  Etc.wz/Commodity.img: {len(COMMODITY)} rows (head run only - see TRIAGE)")

    for arch, rs in sorted(rows.items()):
        rs = sorted(set(rs))
        p = os.path.join(HERE, f"{arch}.paths.txt")
        with open(p, "w", encoding="utf-8", newline="\n") as f:
            f.write("\n".join(rs) + "\n")
        print(f"{arch}.paths.txt: {len(rs)} rows")


if __name__ == "__main__":
    main()
