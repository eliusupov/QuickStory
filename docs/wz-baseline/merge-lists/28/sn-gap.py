#!/usr/bin/env python3
"""Ticket 28 - the cash-shop gap measured at SN level, which is the level the server reads.

`Commodity.img` slot indices are NOT comparable between the two trees: array-align.py
shows they diverge from slot 2322 onward. `CashItemFactory` keys by SN, so the only
meaningful question is which SNs v84 sells that this tree does not - and, for the 11
absent CashPackage entries, whether every SN they name is sellable here.

Read-only.
"""
import os, sys, collections
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sn_collide import v84_rows, srv_rows                      # noqa: E402
from gap import v84_paths, srv_paths, WZMERGE, V84, DUMP_RE    # noqa: E402
import subprocess, re                                          # noqa: E402


def packages(side):
    """{packageId: [SN, ...]} from v84 or from the server tree."""
    out = {}
    if side == "v84":
        r = subprocess.run([WZMERGE, "dump", os.path.join(V84, "Etc.wz"), "CashPackage.img", "3"],
                           capture_output=True, text=True, encoding="utf-8", errors="replace")
        cur = None
        for line in r.stdout.splitlines():
            m = DUMP_RE.match(line)
            if not m:
                continue
            ind = len(m.group(1))
            if ind == 2:
                cur = m.group(2)
                out[cur] = []
            elif ind == 6 and cur:
                out[cur].append(line.split(" = ")[-1].strip())
    else:
        cur = None
        p = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                         "..", "..", "..", "..", "wz", "Etc.wz", "CashPackage.img.xml")
        for line in open(os.path.abspath(p), encoding="utf-8", newline=""):
            s = line.rstrip("\r\n")
            b = s.lstrip(" ")
            ind = len(s) - len(b)
            nm = re.search(r'name="([^"]*)"', b)
            if not nm or b.startswith("</"):
                continue
            if ind == 2:
                cur = nm.group(1)
                out[cur] = []
            elif ind == 6 and cur:
                v = re.search(r'value="([^"]*)"', b)
                out[cur].append(v.group(1) if v else "")
    return out


if __name__ == "__main__":
    v, s = v84_rows(), srv_rows()
    vsn = {r.get("SN"): r for r in v.values()}
    ssn = {r.get("SN"): r for r in s.values()}
    absent_sn = sorted(set(vsn) - set(ssn), key=int)
    print(f"v84 distinct SN={len(vsn)}  server distinct SN={len(ssn)}  "
          f"SN ABSENT server-side={len(absent_sn)}  server-only SN={len(set(ssn) - set(vsn))}")
    band = collections.Counter(sn[:3] for sn in absent_sn)
    print("  absent SN by leading 3 digits:", dict(sorted(band.items())))

    # which v84 SLOT holds each absent SN, and is that slot free in the server tree?
    slot_of = {r.get("SN"): k for k, r in v.items()}
    free = [sn for sn in absent_sn if slot_of[sn] not in s]
    taken = [sn for sn in absent_sn if slot_of[sn] in s]
    print(f"  of those, {len(free)} sit in a v84 slot this tree does NOT have (mergeable by slot)")
    print(f"            {len(taken)} sit in a v84 slot this tree ALREADY uses for another SN")
    print(f"            -> NOT mergeable by slot: the additive gate refuses the row, and forcing")
    print(f"               it would overwrite a row this server sells today.")

    vp, sp = packages("v84"), packages("srv")
    missing_pkgs = sorted(set(vp) - set(sp), key=int)
    print(f"\nv84 packages={len(vp)}  server packages={len(sp)}  absent={len(missing_pkgs)}")
    bad = 0
    for p in sorted(vp, key=int):
        unresolved = [sn for sn in vp[p] if sn not in ssn]
        if unresolved:
            bad += 1
            if p in missing_pkgs:
                print(f"  package {p} (ABSENT here) names {len(unresolved)}/{len(vp[p])} SNs "
                      f"this tree cannot sell: {','.join(unresolved)}")
    print(f"  v84 packages naming at least one SN absent from this tree: {bad} of {len(vp)}")

    # the check that matters for what is already merged
    for p in sorted(sp, key=int):
        unresolved = [sn for sn in sp[p] if sn not in ssn]
        if unresolved:
            print(f"  MERGED-STATE FAULT: server package {p} names unsellable SNs {unresolved}")
