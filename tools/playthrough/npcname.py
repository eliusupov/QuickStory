#!/usr/bin/env python3
"""Print String.wz names for npc ids, plus whether scripts/npc/<id>.js exists.

    python tools/playthrough/npcname.py 2000 2002 2004
"""
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

root = ET.parse("wz/String.wz/Npc.img.xml").getroot()
want = set(sys.argv[1:])
found = set()
for c in root:
    if c.get("name") in want:
        d = {k.get("name"): k.get("value") for k in c}
        js = Path("scripts/npc") / (c.get("name") + ".js")
        found.add(c.get("name"))
        print(f"{c.get('name'):>9}  {d.get('name')!r:24} func={d.get('func')!r:24} "
              f"script={'yes' if js.exists() else 'NO'}")
for miss in sorted(want - found):
    print(f"{miss:>9}  !! not in String.wz/Npc.img")
