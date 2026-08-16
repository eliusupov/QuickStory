#!/usr/bin/env python3
"""Ticket 28 - detail for the Commodity SN collisions, and the safe subset.

Emits, for every one of v84's 110 absent slots, whether its SN is already served
by a row in the server tree and - if so - what that row sells today. Writes
Etc-Commodity.SAFE.txt (the slots whose SN is genuinely new) and
Etc-Commodity.COLLIDING.txt (the ones that would overwrite). Read-only w.r.t. wz/.
"""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sn_collide import v84_rows, srv_rows          # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))

if __name__ == "__main__":
    v, s = v84_rows(), srv_rows()
    by_sn = {}
    for slot, r in s.items():
        by_sn.setdefault(r.get("SN"), []).append((slot, r))
    new_slots = sorted(set(v) - set(s), key=int)

    safe, coll, same_item, diff_item = [], [], 0, 0
    lines = []
    for slot in new_slots:
        r = v[slot]
        sn = r.get("SN")
        if sn in by_sn:
            oslot, o = by_sn[sn][0]
            coll.append(slot)
            tag = "SAME-ITEM" if o.get("ItemId") == r.get("ItemId") else "DIFFERENT-ITEM"
            if tag == "SAME-ITEM":
                same_item += 1
            else:
                diff_item += 1
            lines.append(f"{slot}\tSN={sn}\tCOLLIDES with server slot {oslot}\t{tag}\t"
                         f"v84 ItemId={r.get('ItemId')} price={r.get('Price')} "
                         f"onSale={r.get('OnSale')}\tsrv ItemId={o.get('ItemId')} "
                         f"price={o.get('Price')} onSale={o.get('OnSale')}")
        else:
            safe.append(slot)
            lines.append(f"{slot}\tSN={sn}\tNEW\tItemId={r.get('ItemId')} "
                         f"price={r.get('Price')} onSale={r.get('OnSale')}")

    with open(os.path.join(HERE, "Etc-Commodity.TRIAGE.txt"), "w", encoding="utf-8",
              newline="\r\n") as f:
        f.write("# ticket 28 - triage of v84's 110 absent Commodity slots, by SN\n")
        f.write(f"# {len(safe)} slots carry an SN no server row uses -> safe to add\n")
        f.write(f"# {len(coll)} slots carry an SN a server row ALREADY uses -> adding them\n")
        f.write("#   makes CashItemFactory's put(SN,..) overwrite an existing row. REFUSED.\n")
        f.write("\n".join(lines) + "\n")

    # No "safe to merge by slot" list is emitted on purpose. Only the head run 8947..8957
    # is appendable without a hole; the rest were re-slotted by append-commodity.py, and a
    # list of bare slot numbers would read as an invitation to merge them at v84's indices.
    print(f"absent slots={len(new_slots)}  safe(new SN)={len(safe)}  colliding={len(coll)}"
          f"  of which same ItemId={same_item}, different ItemId={diff_item}")
    print("safe slots:", ",".join(safe))
