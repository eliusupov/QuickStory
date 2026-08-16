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
    seen = 0
    body = [l for l in r.stdout.splitlines() if l.strip()]
    for line in body:
        m = DUMP_RE.match(line)
        if not m:
            continue
        seen += 1
        if len(m.group(1)) == 2:
            cur = m.group(2)
            rows[cur] = []
            order.append(cur)
        elif len(m.group(1)) == 4 and cur is not None:
            wztype = re.search(r"\[(Wz[A-Za-z]+)\]", line).group(1)
            rows[cur].append((m.group(2), wztype, m.group(3)))
    # Every dump line must have been recognised as a node. A value containing a newline
    # would otherwise be silently truncated at the splice, and the header is line 1.
    # -1 for the "…Etc.wz  iv=GMS  patchVersion=84" banner, the only line that is not a node
    assert seen == len(body) - 1, (
        f"parsed {seen} of {len(body) - 1} Commodity dump lines - "
        "a value may span lines and would be truncated by this writer")
    return rows, order


def esc(v):
    return (v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
             .replace('"', "&quot;").replace("'", "&apos;"))


MAPPING = "Etc-Commodity.APPENDED.txt"


def append_base(fresh=None):
    """First slot this script owns. Persisted in the mapping file's header rather than
    hardcoded: after the documented rollback (`git checkout -- wz/`) the WzMerge head run
    at 8947-8957 is gone too, so the next append legitimately starts lower and a constant
    would silently drop eleven rows from both generated lists."""
    if fresh is not None:
        return fresh
    p = os.path.join(HERE, MAPPING)
    if os.path.exists(p):
        first = None
        for line in open(p, encoding="utf-8"):
            if line.startswith("# base "):
                return int(line.split()[2])
            if first is None and line.strip() and not line.startswith("#"):
                first = int(line.split("\t")[0])
        if first is not None:
            return first        # a mapping file written before the header existed
    raise SystemExit(f"{MAPPING} names no base slot and no fresh base was computed")


def emit_lists(full, fresh=None):
    """Derived from the file on disk, not from what this run happened to do, so the two
    lists are correct whether the append just ran or ran in an earlier session."""
    base = append_base(fresh)
    sn_to_v84 = {}
    for slot, kids in full.items():
        sn_to_v84.setdefault(dict((n, v) for n, _, v in kids).get("SN"), slot)
    srv = srv_rows()
    mine = sorted((k for k in srv if k.isdigit() and int(k) >= base), key=int)
    with open(os.path.join(HERE, MAPPING), "w", encoding="utf-8", newline="\r\n") as f:
        f.write("# ticket 28 - v84 cash-shop SNs appended to Commodity.img at FRESH slots.\n")
        f.write("# v84's own slot indices are NOT usable here - see append-commodity.py.\n")
        f.write(f"# base {base}\n")
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
    # The ROOT close tag sits at column 0. Matching a bare "</imgdir>\r\n" would also match
    # an indented "  </imgdir>\r\n", and on a file missing its root close the fragment
    # would splice INSIDE the last pre-existing record - still valid XML, wrong tree, and
    # exactly the class this ticket forbids.
    close = b"\r\n</imgdir>\r\n"
    assert raw.endswith(close), "target does not end with an unindented root close tag"
    body = ("\r\n" + "\r\n".join(frag)).encode("utf-8")
    out = raw[:-len(close)] + body + close
    open(TARGET, "wb").write(out)

    # Additive-only, checked against WHAT IS NOW ON DISK. Asserting a property of a value
    # built three lines above would be a no-op that reads like a safety gate.
    after = open(TARGET, "rb").read()
    assert after[:len(raw) - len(close)] == raw[:-len(close)], \
        "the append altered pre-existing bytes"
    assert after.endswith(close) and len(after) == len(raw) + len(body), \
        "the written file is not the target plus exactly the fragment"

    emit_lists(full, fresh=int(mapping[0][1]))
    print(f"appended {len(mapping)} rows, slots {mapping[0][1]}..{mapping[-1][1]}, "
          f"{len(body)} bytes")
    print(f"SN band: {dict(sorted(collections.Counter(sn[:3] for _, _, sn in mapping).items()))}")


if __name__ == "__main__":
    main()
