#!/usr/bin/env python3
"""Ticket 28 - id-level gap between the stock v84 archive and the server's wz/ XML.

Two instruments, both pointed at answers that were already known before their
output was used (--selftest). Read-only: never writes under wz/.

  v84 side    : WzMerge dump <v84>/<Name>.wz <Img>.img <depth>, parsed by indent.
  server side : wz/<Name>.wz/<Img>.img.xml, parsed by indent.

Both sides return FULL PATHS below the image, not bare names, so a depth-2 diff
cannot be confused by the same id appearing under two parents.

The server-side rule matches ANY element, not just <imgdir> - ticket 27 recorded
805 phantom gaps caused by matching <imgdir> on one side and everything on the other.
"""
import re, subprocess, sys, os, json

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "..", ".."))
WZMERGE = os.path.join(ROOT, "docs", "wz-baseline", "tool-merge", "bin", "Release",
                       "net10.0-windows", "WzMerge.exe")
V84 = r"D:\games\MapleStory\Server\porting-resources\wz-data\v84"

NAME_RE = re.compile(r'name="([^"]*)"')
# WzMerge dump prints "<indent><name> [WzType]" and optionally " = <value>".
# A value may contain newlines, so a continuation line is anything that does NOT
# match this shape; requiring the "[WzType]" bracket is what discriminates them.
DUMP_RE = re.compile(r"^( *)(\S.*?) \[Wz[A-Za-z]+\](?: = |$)")


def v84_paths(archive, imgpath, depth=1):
    """Slash-joined paths of every node exactly `depth` levels below <imgpath>."""
    out = subprocess.run([WZMERGE, "dump", os.path.join(V84, archive + ".wz"), imgpath, str(depth)],
                         capture_output=True, text=True, encoding="utf-8", errors="replace")
    if out.returncode != 0:
        raise SystemExit(f"dump failed for {archive}/{imgpath}:\n{out.stdout}\n{out.stderr}")
    paths, stack = [], {}
    for line in out.stdout.splitlines():
        m = DUMP_RE.match(line)
        if not m:
            continue                      # header or value-continuation line
        lvl = len(m.group(1)) // 2
        stack[lvl] = m.group(2)
        if lvl == depth:
            paths.append("/".join(stack[i] for i in range(1, depth + 1)))
    return paths


def srv_paths(archive, img, depth=1):
    """Same, from an already-extracted server .img.xml. None if the image is absent."""
    p = os.path.join(ROOT, "wz", archive + ".wz", img + ".img.xml")
    if not os.path.exists(p):
        return None
    paths, stack = [], {}
    with open(p, "r", encoding="utf-8", newline="") as f:
        for line in f:
            line = line.rstrip("\r\n")
            s = line.lstrip(" ")
            ind = len(line) - len(s)
            if ind % 2 or not s.startswith("<") or s.startswith("</") or s.startswith("<?"):
                continue
            m = NAME_RE.search(s)
            if not m:
                continue
            lvl = ind // 2
            stack[lvl] = m.group(1)
            if lvl == depth:
                paths.append("/".join(stack[i] for i in range(1, depth + 1)))
    return paths


def compare(archive, img, depth=1):
    v = v84_paths(archive, img + ".img", depth)
    s = srv_paths(archive, img, depth)
    if s is None:
        return dict(archive=archive, img=img, depth=depth, srv_missing_image=True,
                    v84=len(set(v)), srv=0, absent=sorted(set(v)), srv_only=0)
    vs, ss = set(v), set(s)
    return dict(archive=archive, img=img, depth=depth, v84=len(vs), srv=len(ss),
                absent=sorted(vs - ss), srv_only=len(ss - vs))


KNOWN = [
    # (archive, img, depth, expected_absent, why this answer is known INDEPENDENTLY)
    ("Quest", "QuestInfo", 1, 0,   "ticket 33 merged all 135; containment must now be exact"),
    ("Quest", "Say",       1, 135, "ticket 33 deliberately declined Say.img"),
    ("Etc",   "Commodity", 1, 110, "ticket 27 measured 110 absent cash-shop rows"),
    ("String", "Map",      2, 40,  "ticket 27 measured 40 absent map names (a DEPTH-2 case)"),
]


def selftest():
    ok = True
    for arch, img, d, want, why in KNOWN:
        got = len(compare(arch, img, d)["absent"])
        if got != want:
            ok = False
        print(f"  {'PASS' if got == want else 'FAIL'}  {arch}.wz/{img}.img depth={d} "
              f"absent={got} expected={want}  ({why})")
    # negative control: a set diffed against itself is empty in both directions
    v = set(v84_paths("Quest", "QuestInfo.img", 1))
    s = set(srv_paths("String", "Mob", 1))
    neg = not (v - v) and not (s - s) and len(v) > 0 and len(s) > 0
    ok = ok and neg
    print(f"  {'PASS' if neg else 'FAIL'}  negative control: {len(v)} v84 ids and {len(s)} server "
          f"ids each diff to 0 against themselves")
    # ticket 27 fault #2: an id can occur in dialogue TEXT without being a node.
    say = set(srv_paths("Quest", "Say", 1))
    qi = set(srv_paths("Quest", "QuestInfo", 1))
    with open(os.path.join(ROOT, "wz", "Quest.wz", "Say.img.xml"), encoding="utf-8") as f:
        in_text = "22000" in f.read()
    disc = in_text and "22000" not in say and "22000" in qi and len(say) > 2000
    ok = ok and disc
    print(f"  {'PASS' if disc else 'FAIL'}  '22000' occurs in Say.img.xml TEXT but is not one of its "
          f"{len(say)} nodes, while it IS one of QuestInfo's {len(qi)}")
    return ok


if __name__ == "__main__":
    if "--selftest" in sys.argv:
        print("gap.py selftest:")
        sys.exit(0 if selftest() else 1)
    spec = json.load(open(sys.argv[1], encoding="utf-8"))
    res = [compare(*a) for a in spec]
    with open(sys.argv[2], "w", encoding="utf-8") as f:
        json.dump(res, f, indent=1)
    for r in res:
        print(f"{r['archive']}.wz/{r['img']}.img d={r['depth']}: v84={r['v84']} srv={r['srv']} "
              f"ABSENT={len(r['absent'])} srvOnly={r['srv_only']}")
