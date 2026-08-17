#!/usr/bin/env python3
"""Dump a map's info / portals / life straight out of the merged wz/Map.wz XML.

Read-only analysis aid for the early-game play-order walk. Not part of the build.

    python tools/playthrough/mapdump.py 900010000 100030100 ...
    python tools/playthrough/mapdump.py --inbound 900020110      # who warps INTO this map
"""
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

MAPS = Path("wz/Map.wz/Map")


def path_of(mapid):
    return MAPS / f"Map{int(mapid) // 100000000}" / f"{int(mapid):09d}.img.xml"


def kids(node, name):
    for c in node:
        if c.get("name") == name:
            return c
    return None


def vals(node):
    return {c.get("name"): c.get("value") for c in node if c.tag != "imgdir"}


def dump(mapid):
    p = path_of(mapid)
    print("=" * 70)
    if not p.exists():
        print(f"MAP {mapid}  !! NO Map.wz IMAGE ({p})")
        return
    root = ET.parse(p).getroot()
    info = vals(kids(root, "info") or [])
    keep = ("returnMap", "forcedReturn", "onUserEnter", "onFirstUserEnter", "fieldLimit",
            "town", "mobRate", "bgm", "cloud", "swim", "scrollDisable")
    print(f"MAP {mapid}  " + "  ".join(f"{k}={info[k]}" for k in keep if k in info))
    ports = kids(root, "portal")
    if ports is not None:
        for pt in ports:
            v = vals(pt)
            if v.get("pn") == "sp" and v.get("tm") in (None, "999999999"):
                continue
            print(f"   portal {v.get('pn'):<14} type={v.get('pt')} tm={v.get('tm')} "
                  f"tn={v.get('tn')} script={v.get('script', '')}")
    life = kids(root, "life")
    npcs, mobs = [], []
    if life is not None:
        for e in life:
            v = vals(e)
            (npcs if v.get("type") == "n" else mobs).append(v.get("id"))
    print(f"   npcs {sorted(set(npcs))}")
    from collections import Counter
    print(f"   mobs {dict(Counter(mobs))}")


def inbound(target):
    """Every map whose portal tm points at target."""
    hits = []
    for p in MAPS.rglob("*.img.xml"):
        try:
            root = ET.parse(p).getroot()
        except ET.ParseError:
            continue
        ports = kids(root, "portal")
        if ports is None:
            continue
        for pt in ports:
            v = vals(pt)
            if v.get("tm") == str(target):
                hits.append((p.stem.replace(".img", ""), v.get("pn"), v.get("tn"),
                             v.get("script", "")))
    print(f"INBOUND portals -> {target}: {len(hits)}")
    for h in hits:
        print("  ", h)


if __name__ == "__main__":
    args = sys.argv[1:]
    if args and args[0] == "--inbound":
        for a in args[1:]:
            inbound(a)
    else:
        for a in args:
            dump(a)
