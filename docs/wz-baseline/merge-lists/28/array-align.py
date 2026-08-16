#!/usr/bin/env python3
"""Ticket 28 - are Etc.wz's index containers still ALIGNED between v84 and the server?

`Commodity.img` and `NPT_exception.img` are consecutive integer runs, i.e.
POSITIONAL ARRAYS by WZ-MERGE-PROCEDURE 4.4: the child name is a slot, not an id.
Appending v84's slots 8947.. onto the server's 0..8946 is only correct if slot `k`
means the SAME ROW in both trees for every shared k. If v84 had INSERTED a row
anywhere below 8947, every later slot would be the server's own content shifted,
and the "new" tail would be duplicates.

The XML merge path is a line-text scan and cannot digest two nodes to compare
them (4.4.2, "one gap"), so this check is what closes that gap by hand.

Compares a per-slot fingerprint over every shared slot. Read-only.
"""
import re, subprocess, sys, os

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "..", ".."))
WZMERGE = os.path.join(ROOT, "docs", "wz-baseline", "tool-merge", "bin", "Release",
                       "net10.0-windows", "WzMerge.exe")
V84 = r"D:\games\MapleStory\Server\porting-resources\wz-data\v84"
DUMP_RE = re.compile(r"^( *)(\S.*?) \[Wz[A-Za-z]+\](?: = (.*))?$")


def v84_slots(archive, img, keys):
    out = subprocess.run([WZMERGE, "dump", os.path.join(V84, archive + ".wz"), img, "2"],
                         capture_output=True, text=True, encoding="utf-8", errors="replace")
    if out.returncode != 0:
        raise SystemExit(out.stdout + out.stderr)
    slots, cur = {}, None
    for line in out.stdout.splitlines():
        m = DUMP_RE.match(line)
        if not m:
            continue
        ind = len(m.group(1))
        if ind == 2:
            cur = m.group(2)
            slots[cur] = {} if keys else (m.group(3) or "")
        elif ind == 4 and cur is not None and keys and m.group(2) in keys:
            slots[cur][m.group(2)] = m.group(3)
    return slots


def srv_slots(archive, img, keys):
    p = os.path.join(ROOT, "wz", archive + ".wz", img + ".xml")
    slots, cur = {}, None
    with open(p, encoding="utf-8", newline="") as f:
        for line in f:
            s = line.rstrip("\r\n")
            body = s.lstrip(" ")
            ind = len(s) - len(body)
            if not body.startswith("<") or body.startswith("</") or body.startswith("<?"):
                continue
            nm = re.search(r'name="([^"]*)"', body)
            if not nm:
                continue
            val = re.search(r'value="([^"]*)"', body)
            if ind == 2:
                cur = nm.group(1)
                slots[cur] = {} if keys else (val.group(1) if val else "")
            elif ind == 4 and cur is not None and keys and nm.group(1) in keys:
                slots[cur][nm.group(1)] = val.group(1) if val else ""
    return slots


def check(archive, img, keys, label):
    v = v84_slots(archive, img, keys)
    s = srv_slots(archive, img, keys)
    shared = sorted(set(v) & set(s), key=lambda x: int(x) if x.isdigit() else -1)
    bad = [k for k in shared if v[k] != s[k]]
    print(f"{label}: shared slots={len(shared)}  MISALIGNED={len(bad)}")
    for k in bad[:12]:
        print(f"    slot {k}: v84={v[k]}  srv={s[k]}")
    if len(bad) > 12:
        print(f"    ... and {len(bad) - 12} more")
    print(f"    server max slot={max((int(k) for k in s if k.isdigit()), default=-1)}, "
          f"v84 max slot={max((int(k) for k in v if k.isdigit()), default=-1)}")
    return bad, shared


if __name__ == "__main__":
    # SELF-CHECK: the comparator must be able to report a difference. Shift the
    # server side by one slot and it has to light up on (almost) every slot.
    v = v84_slots("Etc", "Commodity.img", {"SN", "ItemId"})
    s = srv_slots("Etc", "Commodity.img", {"SN", "ItemId"})
    keys = sorted(set(v) & set(s), key=int)
    shifted = {k: s[str(int(k) + 1)] for k in keys if str(int(k) + 1) in s}
    diff = sum(1 for k in shifted if v[k] != shifted[k])
    print(f"SELF-CHECK: comparing v84 slot k against server slot k+1 -> {diff} of "
          f"{len(shifted)} differ  (must be a large number, or the comparator is inert)")
    if diff < len(shifted) // 2:
        sys.exit(2)

    bad1, sh1 = check("Etc", "Commodity.img", {"SN", "ItemId"}, "Commodity.img (SN+ItemId per slot)")
    bad2, sh2 = check("Etc", "NPT_exception.img", None, "NPT_exception.img (leaf value per slot)")
    sys.exit(0 if not bad1 and not bad2 else 1)
