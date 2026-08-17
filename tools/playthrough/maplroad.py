#!/usr/bin/env python3
"""List every quest whose start OR end npc is a Maple Road / Maple Island tutorial npc,
with its scripts, item/mob requirements and whether a scripts/quest/<id>.js exists.

    python tools/playthrough/maplroad.py
"""
import xml.etree.ElementTree as ET
from pathlib import Path

WZ = Path("wz/Quest.wz")
# Every npc placed on 10000/20000/30000/40000/50000/1000000/1010000/1020000/2000000.
ISLAND_NPCS = {2000, 2001, 2002, 2003, 2004, 2005, 2006, 2007, 2100, 2101, 2102, 2103,
               10000, 12000, 12100, 20001, 20002, 20100, 21000, 22000, 12101, 10200,
               10201, 10202, 10203, 10204, 2104}


def kid(node, name):
    for c in node:
        if c.get("name") == name:
            return c
    return None


def val(node, name, default=None):
    c = kid(node, name)
    return c.get("value") if c is not None else default


def main():
    check = ET.parse(WZ / "Check.img.xml").getroot()
    act = ET.parse(WZ / "Act.img.xml").getroot()
    acts = {a.get("name"): a for a in act}
    for q in check:
        qid = q.get("name")
        npcs, scripts, items, mobs, lv = set(), [], [], [], {}
        for side in ("0", "1"):
            s = kid(q, side)
            if s is None:
                continue
            n = val(s, "npc")
            if n:
                npcs.add(int(n))
            for sc in ("startscript", "endscript"):
                if val(s, sc):
                    scripts.append(val(s, sc))
            for tag, bucket in (("item", items), ("mob", mobs)):
                node = kid(s, tag)
                if node is not None:
                    for e in node:
                        bucket.append((val(e, "id"), val(e, "count", "0")))
            for g in ("lvmin", "lvmax", "job"):
                if val(s, g):
                    lv[g] = val(s, g)
        if not (npcs & ISLAND_NPCS):
            continue
        js = Path("scripts/quest") / f"{qid}.js"
        a = acts.get(qid)
        rewards = []
        if a is not None:
            for side in ("0", "1"):
                s = kid(a, side)
                if s is None:
                    continue
                it = kid(s, "item")
                if it is not None:
                    for e in it:
                        rewards.append((val(e, "id"), val(e, "count", "0")))
        print(f"{qid:>6} npc={sorted(npcs)} scripts={scripts} js={'yes' if js.exists() else 'NO'} "
              f"gates={lv} needItems={items} needMobs={mobs} rewards={rewards}")


if __name__ == "__main__":
    main()
