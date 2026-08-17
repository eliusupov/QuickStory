#!/usr/bin/env python3
"""Every quest whose Check.img declares a startscript/endscript that has no scripts/quest/<id>.js.

AbstractScriptManager.getInvocableScriptEngine returns null for a missing file and
QuestScriptManager disposes with a lone log.warn - the player sees nothing at all.

    python tools/playthrough/missingscripts.py
"""
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

sys.stdout.reconfigure(errors="replace")

root = ET.parse("wz/Quest.wz/Check.img.xml").getroot()
info = {q.get("name"): q for q in ET.parse("wz/Quest.wz/QuestInfo.img.xml").getroot()}

missing = []
declared = 0
for q in root:
    qid = q.get("name")
    names = []
    for side in q:
        if side.tag != "imgdir":
            continue
        for c in side:
            if c.get("name") in ("startscript", "endscript"):
                names.append(c.get("name") + "=" + c.get("value"))
    if not names:
        continue
    declared += 1
    if not (Path("scripts/quest") / f"{qid}.js").exists():
        i = info.get(qid)
        nm = ""
        if i is not None:
            for c in i:
                if c.get("name") == "name":
                    nm = c.get("value")
        missing.append((qid, names, nm))

print(f"quests declaring a script: {declared}")
print(f"of those, with no scripts/quest/<id>.js: {len(missing)}")
for qid, names, nm in sorted(missing, key=lambda x: int(x[0])):
    print(f"  {qid:>6}  {nm[:44]:<46} {names}")
