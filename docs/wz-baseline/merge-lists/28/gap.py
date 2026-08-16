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
# The trailing alternative matters: a canvas prints "0 [WzCanvasProperty] 1234x600, png N
# bytes" with no " = ", and requiring " = " or end-of-line would skip every canvas node
# and, with it, its whole subtree. Same fault fidelity28.py documents.
DUMP_RE = re.compile(r"^( *)(\S.*?) \[Wz[A-Za-z]+\](?: = | [^=]|$)")

# gap.py measures a gap that a merge then closes, so its known answers expire the moment
# the merge lands. The selftest therefore reads the SERVER side from this commit unless
# told otherwise - the tree state ticket 27 measured and ticket 28 merged into.
PRE_MERGE_REV = "cdaecd678"


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


def srv_paths(archive, img, depth=1, rev=None):
    """Same, from the server .img.xml. `rev` reads that commit's blob instead of the
    working tree, which is what lets the selftest keep asserting pre-merge answers after
    the merge has landed. None if the image is absent on that side."""
    rel = f"wz/{archive}.wz/{img}.img.xml"
    if rev:
        r = subprocess.run(["git", "-C", ROOT, "show", f"{rev}:{rel}"], capture_output=True)
        if r.returncode != 0:
            return None
        lines = r.stdout.decode("utf-8-sig").splitlines()
    else:
        p = os.path.join(ROOT, rel)
        if not os.path.exists(p):
            return None
        with open(p, "r", encoding="utf-8-sig", newline="") as f:
            lines = f.read().splitlines()
    paths, stack = [], {}
    for line in lines:
        s = line.rstrip("\r").lstrip(" ")
        ind = len(line.rstrip("\r")) - len(s)
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


def compare(archive, img, depth=1, rev=None):
    v = v84_paths(archive, img + ".img", depth)
    s = srv_paths(archive, img, depth, rev)
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


def selftest(rev=PRE_MERGE_REV):
    ok = True
    print(f"  (server side read from {rev or 'the working tree'})")
    for arch, img, d, want, why in KNOWN:
        got = len(compare(arch, img, d, rev)["absent"])
        if got != want:
            ok = False
        print(f"  {'PASS' if got == want else 'FAIL'}  {arch}.wz/{img}.img depth={d} "
              f"absent={got} expected={want}  ({why})")
    # Negative control. `v - v` would be a set-theory identity that prints PASS with the
    # readers deleted, so control one reader against the OTHER on a case whose answer is
    # known: v84's QuestInfo ids are a strict SUBSET of this tree's (ticket 33 merged all
    # 135 and this tree keeps its own quest 7778), so v84-minus-server must be empty while
    # server-minus-v84 must be exactly {7778}. Both readers have to be right to pass.
    v = set(v84_paths("Quest", "QuestInfo.img", 1))
    s = set(srv_paths("Quest", "QuestInfo", 1, rev))
    neg = len(v) > 0 and (v - s) == set() and (s - v) == {"7778"}
    ok = ok and neg
    print(f"  {'PASS' if neg else 'FAIL'}  cross-reader control: v84 {len(v)} ids minus server "
          f"{len(s)} ids = {sorted(v - s)}, and server minus v84 = {sorted(s - v)} "
          f"(must be [] and ['7778'])")
    # ticket 27 fault #2: an id can occur in dialogue TEXT without being a node.
    say = set(srv_paths("Quest", "Say", 1, rev))
    qi = set(srv_paths("Quest", "QuestInfo", 1, rev))
    with open(os.path.join(ROOT, "wz", "Quest.wz", "Say.img.xml"), encoding="utf-8") as f:
        in_text = "22000" in f.read()
    disc = in_text and "22000" not in say and "22000" in qi and len(say) > 2000
    ok = ok and disc
    print(f"  {'PASS' if disc else 'FAIL'}  '22000' occurs in Say.img.xml TEXT but is not one of its "
          f"{len(say)} nodes, while it IS one of QuestInfo's {len(qi)}")
    return ok


if __name__ == "__main__":
    if "--selftest" in sys.argv:
        rev = sys.argv[sys.argv.index("--rev") + 1] if "--rev" in sys.argv else PRE_MERGE_REV
        print("gap.py selftest:")
        sys.exit(0 if selftest(rev) else 1)
    spec = json.load(open(sys.argv[1], encoding="utf-8"))
    res = [compare(*a) for a in spec]
    with open(sys.argv[2], "w", encoding="utf-8") as f:
        json.dump(res, f, indent=1)
    for r in res:
        print(f"{r['archive']}.wz/{r['img']}.img d={r['depth']}: v84={r['v84']} srv={r['srv']} "
              f"ABSENT={len(r['absent'])} srvOnly={r['srv_only']}")
