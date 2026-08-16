#!/usr/bin/env python3
"""Ticket 28 - append v84's missing cash-shop SNs to Etc.wz/Commodity.img.xml at FRESH slots.

WHY THIS IS NOT A WzMerge RUN. `WzMerge xml` copies a source node to the SAME path.
That works only while the two trees' slot indices mean the same thing, and they do not:
array-align.py measures `Commodity.img` diverging from slot 2322 onward, so 93 of the
105 SNs this tree is missing live in v84 slots that this tree already uses for a
DIFFERENT row. Merging by slot would either be refused by the additive gate or, if
forced, overwrite rows the server sells today. Both are wrong.

Slot index is not read by anything server-side - `CashItemFactory.loadAllCashItems`
(src/main/java/server/CashShop.java) iterates the children and keys its map by SN - so
the correct operation is to append each missing SN at a fresh, unused slot. That is
strictly additive: no existing child is touched, no SN is duplicated, and the file's
existing child order is preserved because the fragment goes in immediately before the
closing tag.

This writes wz/. Run verify28.py and fidelity28.py afterwards - they are what prove it,
and they do not trust this script.
"""
import os, sys, re, subprocess, collections

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", "..", "..", ".."))
sys.path.insert(0, HERE)
from sn_collide import v84_rows, srv_rows, DUMP_RE, WZMERGE, V84  # noqa: E402

TARGET = os.path.join(ROOT, "wz", "Etc.wz", "Commodity.img.xml")
# dump type -> the XML tag MapleLib's serializer writes. Only these are accepted; a row
# carrying anything else aborts rather than being guessed at.
TAG = {"WzIntProperty": "int", "WzShortProperty": "short", "WzStringProperty": "string"}


def v84_full():
    """slot -> [(childName, wzType, value), ...] for every Commodity row, in file order."""
    r = subprocess.run([WZMERGE, "dump", os.path.join(V84, "Etc.wz"), "Commodity.img", "2"],
                       capture_output=True, text=True, encoding="utf-8", errors="strict")
    assert r.returncode == 0, r.stdout + r.stderr
    rows, order, cur = {}, [], None
    for line in r.stdout.splitlines():
        m = DUMP_RE.match(line)
        if not m:
            continue
        if len(m.group(1)) == 2:
            cur = m.group(2)
            rows[cur] = []
            order.append(cur)
        elif len(m.group(1)) == 4 and cur is not None:
            wztype = re.search(r"\[(Wz[A-Za-z]+)\]", line).group(1)
            rows[cur].append((m.group(2), wztype, m.group(3)))
    return rows, order


def esc(v):
    return (v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
             .replace('"', "&quot;").replace("'", "&apos;"))


APPEND_BASE = 8958   # first slot this script owns; 0..8957 predate it


def emit_lists(full):
    """Derived from the file on disk, not from what this run happened to do, so the two
    lists are correct whether the append just ran or ran in an earlier session."""
    sn_to_v84 = {}
    for slot, kids in full.items():
        sn_to_v84.setdefault(dict((n, v) for n, _, v in kids).get("SN"), slot)
    srv = srv_rows()
    mine = sorted((k for k in srv if k.isdigit() and int(k) >= APPEND_BASE), key=int)
    with open(os.path.join(HERE, "Etc-Commodity.APPENDED.txt"), "w", encoding="utf-8",
              newline="\r\n") as f:
        f.write("# ticket 28 - v84 cash-shop SNs appended to Commodity.img at FRESH slots.\n")
        f.write("# v84's own slot indices are NOT usable here - see append-commodity.py.\n")
        f.write("# newslot\tSN\tv84slot\tItemId\n")
        for k in mine:
            sn = srv[k].get("SN")
            f.write(f"{k}\t{sn}\t{sn_to_v84.get(sn, '?')}\t{srv[k].get('ItemId')}\n")
    with open(os.path.join(HERE, "Etc-appended.paths.txt"), "w", encoding="utf-8",
              newline="\n") as f:
        f.write("# NOT a WzMerge manifest - these slots were APPENDED by append-commodity.py\n")
        f.write("# at indices this tree chose, because v84's own indices are already taken\n")
        f.write("# here by different rows. verify28.py reads this as the expected-new set.\n")
        for k in mine:
            f.write(f"Etc.wz/Commodity.img/{k}\n")
    print(f"lists: {len(mine)} appended slots recorded")


def main():
    full, order = v84_full()
    srv = srv_rows()
    srv_sn = {r.get("SN") for r in srv.values()}
    used_slots = {int(k) for k in srv if k.isdigit()}
    next_slot = max(used_slots) + 1

    def sn_of(slot):
        return dict((n, v) for n, _, v in full[slot]).get("SN")

    todo, seen = [], set()
    for slot in order:
        sn = sn_of(slot)
        # skip what the server already sells, and never add the same SN twice
        if sn in srv_sn or sn in seen:
            continue
        seen.add(sn)
        todo.append(slot)

    frag, mapping = [], []
    for slot in todo:
        newslot = next_slot
        next_slot += 1
        frag.append(f'  <imgdir name="{newslot}">')
        for name, wztype, value in full[slot]:
            tag = TAG.get(wztype)
            assert tag, f"v84 slot {slot} child {name} has type {wztype} - not handled"
            frag.append(f'    <{tag} name="{esc(name)}" value="{esc(value)}"/>')
        frag.append("  </imgdir>")
        mapping.append((slot, str(newslot), sn_of(slot)))

    if not todo:
        print("nothing to append - every v84 SN is already sellable here")
        emit_lists(full)
        return

    raw = open(TARGET, "rb").read()
    assert not raw.startswith(b"\xef\xbb\xbf"), "target has a BOM"
    assert raw.count(b"\n") == raw.count(b"\r\n"), "target is not CRLF throughout"
    close = b"</imgdir>\r\n"
    assert raw.endswith(close), "target does not end with the root close tag"
    body = ("\r\n".join(frag) + "\r\n").encode("utf-8")
    out = raw[:-len(close)] + body + close

    # additive-only, checked before writing: the pre-existing bytes are a strict prefix
    assert out.startswith(raw[:-len(close)]), "the append would alter pre-existing bytes"
    open(TARGET, "wb").write(out)

    emit_lists(full)
    print(f"appended {len(mapping)} rows, slots {mapping[0][1]}..{mapping[-1][1]}, "
          f"{len(body)} bytes")
    print(f"SN band: {dict(sorted(collections.Counter(sn[:3] for _, _, sn in mapping).items()))}")


if __name__ == "__main__":
    main()
