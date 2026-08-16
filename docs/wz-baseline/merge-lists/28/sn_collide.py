#!/usr/bin/env python3
"""Ticket 28 - do the 110 new Commodity slots collide on SN with rows the server already has?

`CashItemFactory.loadAllCashItems` (src/main/java/server/CashShop.java:241-250) does
`loadedItems.put(SN, ...)` - the node NAME (the slot index) is never read, so
server-side `Commodity.img` is an SN table, not a positional array. Index
misalignment between the two trees is therefore inert HERE, but an SN that already
exists is NOT: the second `put` silently replaces a row the server already sells,
in HashMap iteration order. That would be a MODIFICATION, which this ticket forbids.

Also reports the OnSale split the owner asked about. Read-only.
"""
import re, subprocess, os, sys, collections

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "..", ".."))
WZMERGE = os.path.join(ROOT, "docs", "wz-baseline", "tool-merge", "bin", "Release",
                       "net10.0-windows", "WzMerge.exe")
V84 = r"D:\games\MapleStory\Server\porting-resources\wz-data\v84"
DUMP_RE = re.compile(r"^( *)(\S.*?) \[Wz[A-Za-z]+\](?: = (.*))?$")
FIELDS = ("SN", "ItemId", "OnSale", "Price", "Count", "Period")


def v84_rows():
    out = subprocess.run([WZMERGE, "dump", os.path.join(V84, "Etc.wz"), "Commodity.img", "2"],
                         capture_output=True, text=True, encoding="utf-8", errors="replace")
    # A failed dump would give rows={} -> new_slots=[] -> collide=[] -> "no collisions",
    # i.e. this detector's silence would mean "the tool broke", not "it is safe".
    assert out.returncode == 0, f"dump Etc.wz/Commodity.img failed:\n{out.stdout}{out.stderr}"
    rows, cur = {}, None
    for line in out.stdout.splitlines():
        m = DUMP_RE.match(line)
        if not m:
            continue
        if len(m.group(1)) == 2:
            cur = m.group(2)
            rows[cur] = {}
        elif len(m.group(1)) == 4 and cur and m.group(2) in FIELDS:
            rows[cur][m.group(2)] = m.group(3)
    return rows


def srv_rows():
    rows, cur = {}, None
    with open(os.path.join(ROOT, "wz", "Etc.wz", "Commodity.img.xml"), encoding="utf-8",
              newline="") as f:
        for line in f:
            s = line.rstrip("\r\n")
            b = s.lstrip(" ")
            ind = len(s) - len(b)
            nm = re.search(r'name="([^"]*)"', b)
            if not nm or b.startswith("</") or b.startswith("<?"):
                continue
            val = re.search(r'value="([^"]*)"', b)
            if ind == 2:
                cur = nm.group(1)
                rows[cur] = {}
            elif ind == 4 and cur and nm.group(1) in FIELDS:
                rows[cur][nm.group(1)] = val.group(1) if val else ""
    return rows


if __name__ == "__main__":
    v, s = v84_rows(), srv_rows()
    new_slots = sorted(set(v) - set(s), key=int)
    srv_sn = collections.Counter(r.get("SN") for r in s.values())
    new_sn = [v[k].get("SN") for k in new_slots]

    span = f"({new_slots[0]}..{new_slots[-1]})" if new_slots else "(none - already merged)"
    print(f"server slots={len(s)}  v84 slots={len(v)}  slots absent server-side="
          f"{len(new_slots)}  {span}")
    print(f"distinct SN server-side={len(srv_sn)}  duplicate SNs ALREADY in the server tree="
          f"{sum(1 for c in srv_sn.values() if c > 1)}")

    collide = sorted(sn for sn in set(new_sn) if sn in srv_sn)
    print(f"NEW rows={len(new_sn)}  distinct new SN={len(set(new_sn))}  "
          f"SN COLLISIONS WITH EXISTING ROWS={len(collide)}")
    for sn in collide[:20]:
        print(f"    SN {sn} already served by a row in the server tree")

    # SELF-CHECK. Run the ACTUAL detector expression over two planted inputs: one SN the
    # server certainly has (must be reported) and one it certainly does not (must not be).
    # Asserting `probe in srv_sn` for a probe drawn from srv_sn would be a tautology that
    # prints PASS even with the detector deleted - and this is the script that decided the
    # cash-shop merge was unsafe, so it does not get to be one.
    def detect(candidates):
        return sorted(sn for sn in set(candidates) if sn in srv_sn)

    present = next(iter(srv_sn))
    absent = "999999999"
    assert absent not in srv_sn, "the negative probe is not actually absent"
    pos, neg = detect([present]), detect([absent])
    fired = pos == [present] and neg == []
    print(f"SELF-CHECK: the detector reports a planted present SN ({present}) and stays "
          f"silent on a planted absent one ({absent}) = {fired}")

    onsale = collections.Counter(r.get("OnSale", "0") for r in s.values())
    print(f"OnSale split, server tree: on={onsale.get('1', 0)}  off/absent="
          f"{sum(c for k, c in onsale.items() if k != '1')}")
    v_on = collections.Counter(v[k].get("OnSale", "0") for k in new_slots)
    print(f"OnSale split, the {len(new_slots)} new rows: on={v_on.get('1', 0)}  "
          f"off/absent={sum(c for k, c in v_on.items() if k != '1')}")
    onsale_v = collections.Counter(r.get("OnSale", "0") for r in v.values())
    print(f"OnSale split, stock v84 whole file: on={onsale_v.get('1', 0)}  off/absent="
          f"{sum(c for k, c in onsale_v.items() if k != '1')}")

    sys.exit(1 if collide or not fired else 0)
