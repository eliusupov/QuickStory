#!/usr/bin/env python3
"""Dump Check.img / Act.img / QuestInfo.img for a list of quest ids.

Read-only analysis aid for the early-game play-order walk. Not part of the build.

    python tools/playthrough/questdump.py 22000 22001 ...
"""
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

WZ = Path("wz/Quest.wz")


def load(name):
    return ET.parse(WZ / name).getroot()


def render(node, indent=0):
    pad = "  " * indent
    out = []
    for c in node:
        n = c.get("name")
        if c.tag == "imgdir":
            out.append(f"{pad}{n}:")
            out.extend(render(c, indent + 1))
        else:
            out.append(f"{pad}{n} = {c.get('value')}")
    return out


def find(root, qid):
    for c in root:
        if c.get("name") == str(qid):
            return c
    return None


def main(ids):
    check, act, info = load("Check.img.xml"), load("Act.img.xml"), load("QuestInfo.img.xml")
    for qid in ids:
        print("=" * 70)
        print(f"QUEST {qid}")
        i = find(info, qid)
        if i is None:
            print("  !! NOT IN QuestInfo.img")
        else:
            for c in i:
                if c.get("name") in ("name", "parent", "area", "autoStart", "autoComplete",
                                     "autoPreComplete", "0", "1", "demandSummary", "rewardSummary"):
                    print(f"  info {c.get('name')} = {c.get('value')}")
        for label, root in (("CHECK", check), ("ACT", act)):
            n = find(root, qid)
            print(f"  --- {label} ---")
            if n is None:
                print("    (absent)")
            else:
                for line in render(n, 2):
                    print(line)


if __name__ == "__main__":
    main(sys.argv[1:])
