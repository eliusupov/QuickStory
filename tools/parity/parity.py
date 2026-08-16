#!/usr/bin/env python3
"""Content-parity measurement: stock v84 client tree vs this server's wz/ + live DB.

    python tools/parity/parity.py all --dump <v84-tsv-dir> [--wz wz] [--out tools/parity/reports]

The v84 side comes from the TSVs that tools/parity/dump-v84.ps1 writes out of the packed
archives in D:\\games\\wz-stage\\v84-base.  The server side is read straight off the extracted
XML tree in wz/ -- that is the tree the server actually loads, so it is the only honest
definition of "what the server can serve".  The DB side is read with the mysql client.

ponytail: regex over the XML rather than ElementTree.  These files are HaRepacker output with
fixed two-space indentation and one property per line; a full DOM parse of 319 MB of Map.wz
buys nothing a line scan does not already give, and costs ~20x the time.  If the extractor's
formatting ever changes, LIFE_BLOCK below is the one thing to fix.
"""
from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
from collections import defaultdict
from pathlib import Path

MYSQL = r"C:\Program Files\MySQL\MySQL Server 9.4\bin\mysql.exe"

# Runtime-allocated PlayerNPC ids (PlayerNPC.java:66). Never merge, never report as a gap.
PLAYERNPC_RANGE = range(9901910, 9901920)
# Deliberately refused for the same reason, pinned by a test.
DENY_MAPS = {100030301}


# --------------------------------------------------------------------------- v84 TSV side

def load_tsv(path: Path):
    """Yield (path, type, value) from a WzValues dump."""
    with open(path, encoding="utf-8", errors="replace") as f:
        for line in f:
            parts = line.rstrip("\n").split("\t")
            if len(parts) == 3:
                yield parts[0], parts[1], parts[2]


# ------------------------------------------------------------------------ server XML side

IMGDIR = re.compile(rb'<imgdir name="([^"]+)"')
SCALAR = re.compile(rb'<(int|string|float|double|short|long|uol) name="([^"]+)" value="([^"]*)"')


def read_norm(p: Path) -> bytes:
    """The extracted tree is CRLF. Every regex below anchors on '\\n<indent>', so normalise
    once at the door rather than making each pattern carry an optional \\r."""
    return p.read_bytes().replace(b"\r\n", b"\n")


def server_item_ids(wz: Path) -> dict[int, str]:
    """item id -> Item.wz category.  Bucket images hold ids as their direct children;
    Item.wz/Pet holds one image per id."""
    out: dict[int, str] = {}
    root = wz / "Item.wz"
    for cat in sorted(p for p in root.iterdir() if p.is_dir()):
        for img in sorted(cat.glob("*.img.xml")):
            stem = img.name[: -len(".img.xml")]
            if stem.isdigit() and len(stem) >= 7:      # Pet/5000000.img
                out[int(stem)] = cat.name
                continue
            data = read_norm(img)
            # direct children of the root imgdir sit at exactly 2-space indent
            for m in re.finditer(rb'\n  <imgdir name="(\d+)"', data):
                out[int(m.group(1))] = cat.name
    return out


def server_equip_ids(wz: Path) -> dict[int, str]:
    """equip id -> Character.wz category (image name IS the id)."""
    out: dict[int, str] = {}
    root = wz / "Character.wz"
    for img in root.rglob("*.img.xml"):
        stem = img.name[: -len(".img.xml")]
        if stem.isdigit():
            out[int(stem)] = img.parent.name if img.parent != root else ""
    return out


def server_mob_stats(wz: Path) -> dict[int, dict[str, str]]:
    """mob id -> {info scalar: value}.  Only the top level of <imgdir name="info">;
    nested containers under info (e.g. skill/, revive/) are handled separately."""
    out: dict[int, dict[str, str]] = {}
    for img in sorted((wz / "Mob.wz").glob("*.img.xml")):
        stem = img.name[: -len(".img.xml")]
        if not stem.isdigit():
            continue
        out[int(stem)] = _xml_info_block(read_norm(img))
    return out


def _xml_info_block(data: bytes) -> dict[str, str]:
    """Scalars directly under the root's <imgdir name="info"> (indent 2 -> children at 4)."""
    i = data.find(b'\n  <imgdir name="info">')
    if i < 0:
        return {}
    end = data.find(b"\n  </imgdir>", i)
    block = data[i : end if end > 0 else len(data)]
    out: dict[str, str] = {}
    for line in block.split(b"\n"):
        if not line.startswith(b"    <"):        # exactly one level in
            continue
        m = SCALAR.match(line.strip(), 0)
        if m:
            out[m.group(2).decode()] = m.group(3).decode()
    return out


def server_npc_ids(wz: Path) -> set[int]:
    return {int(p.name[: -len(".img.xml")])
            for p in (wz / "Npc.wz").glob("*.img.xml")
            if p.name[: -len(".img.xml")].isdigit()}


def server_reactor_ids(wz: Path) -> set[int]:
    return {int(p.name[: -len(".img.xml")])
            for p in (wz / "Reactor.wz").rglob("*.img.xml")
            if p.name[: -len(".img.xml")].isdigit()}


def server_map_life(wz: Path):
    """(npc_placements, mob_placements): kind -> {id -> set(mapid)}, plus set of map ids."""
    npcs: dict[int, set[int]] = defaultdict(set)
    mobs: dict[int, set[int]] = defaultdict(set)
    maps: set[int] = set()
    for img in (wz / "Map.wz" / "Map").rglob("*.img.xml"):
        stem = img.name[: -len(".img.xml")]
        if not stem.isdigit():
            continue
        mapid = int(stem)
        maps.add(mapid)
        for kind, oid in _life_entries(read_norm(img)):
            (npcs if kind == "n" else mobs)[oid].add(mapid)
    return npcs, mobs, maps


LIFE_BLOCK = re.compile(rb'\n  <imgdir name="life">\n(.*?)\n  </imgdir>', re.S)
LIFE_ENTRY = re.compile(
    rb'name="(type|id)" value="([^"]*)"')


def _life_entries(data: bytes):
    """Yield (type, id) per life entry.  Order of the type/id lines is not assumed."""
    for blk in LIFE_BLOCK.finditer(data):
        # each entry is an <imgdir name="N"> at indent 4. The captured block starts at the
        # first entry with no leading newline, so put one back - without it the FIRST life
        # entry of every map is silently dropped, which is exactly the notable NPC.
        for ent in re.finditer(rb'\n    <imgdir name="\d+">\n(.*?)\n    </imgdir>',
                               b"\n" + blk.group(1), re.S):
            kind = oid = None
            for m in LIFE_ENTRY.finditer(ent.group(1)):
                if m.group(1) == b"type":
                    kind = m.group(2).decode()
                elif m.group(1) == b"id" and oid is None:
                    v = m.group(2).decode()
                    oid = int(v) if v.isdigit() else None
            if kind in ("n", "m") and oid is not None:
                yield kind, oid


def v84_map_life(dump: Path):
    """Same shape as server_map_life, but off the v84 TSV."""
    npcs: dict[int, set[int]] = defaultdict(set)
    mobs: dict[int, set[int]] = defaultdict(set)
    maps: set[int] = set()
    pending: dict[str, dict[str, str]] = defaultdict(dict)
    for p, _t, v in load_tsv(dump / "v84.Map.tsv"):
        m = re.match(r"Map/Map\d/(\d+)\.img(?:/life/(\d+)/(id|type))?$", p)
        if not m:
            continue
        mapid = int(m.group(1))
        maps.add(mapid)
        if m.group(2) is None:
            continue
        pending[f"{mapid}/{m.group(2)}"][m.group(3)] = v
    for key, kv in pending.items():
        mapid = int(key.split("/")[0])
        if kv.get("type") in ("n", "m") and kv.get("id", "").isdigit():
            (npcs if kv["type"] == "n" else mobs)[int(kv["id"])].add(mapid)
    return npcs, mobs, maps


# ------------------------------------------------------------------------------ DB access

def sql(query: str) -> list[list[str]]:
    r = subprocess.run([MYSQL, "-uroot", "-proot", "-D", "cosmic", "-B", "-N", "-e", query],
                       capture_output=True, text=True)
    if r.returncode != 0:
        raise SystemExit(f"mysql failed: {r.stderr}")
    return [ln.split("\t") for ln in r.stdout.splitlines() if ln]


# ------------------------------------------------------------------------------- reports

def w(out: Path, name: str, header: str, lines) -> int:
    out.mkdir(parents=True, exist_ok=True)
    lines = list(lines)
    (out / name).write_text(
        f"# {header}\n# {len(lines)} rows\n" + "\n".join(lines) + ("\n" if lines else ""),
        encoding="utf-8")
    return len(lines)


def cat_of(iid: int) -> str:
    """Coarse inventory type from the id's leading digits, as the server classifies it."""
    t = iid // 1000000
    return {1: "equip", 2: "use", 3: "setup", 4: "etc", 5: "cash"}.get(t, f"type{t}")


def cmd_items(a):
    dump, wz, out = Path(a.dump), Path(a.wz), Path(a.out)
    v84_items: dict[int, str] = {}
    for p, _t, _v in load_tsv(dump / "v84.Item.tsv"):
        m = re.match(r"([^/]+)/[^/]+\.img/(\d+)$", p)
        if m:
            v84_items[int(m.group(2))] = m.group(1)
            continue
        # Item.wz/Pet/5000000.img is a whole-image id; Item.wz/Consume/0200.img is a bucket
        # whose NAME is not an id. Length is what separates them, same rule as the server side.
        m = re.match(r"([^/]+)/(\d{7,})\.img$", p)
        if m:
            v84_items[int(m.group(2))] = m.group(1)
    for p, _t, _v in load_tsv(dump / "v84.Character.tsv"):
        m = re.match(r"(?:([^/]+)/)?(\d+)\.img$", p)
        if m:
            v84_items[int(m.group(2))] = m.group(1) or "Character"

    srv = server_item_ids(wz)
    srv.update(server_equip_ids(wz))

    only84 = sorted(set(v84_items) - set(srv))
    onlysrv = sorted(set(srv) - set(v84_items))

    def tally(ids):
        c: dict[str, int] = defaultdict(int)
        for i in ids:
            c[cat_of(i)] += 1
        return dict(sorted(c.items()))

    w(out, "items-v84-only.txt",
      "item ids the v84 client ships that the server's wz/ has no data for "
      "(server cannot serve them)",
      (f"{i}\t{cat_of(i)}\t{v84_items[i]}" for i in only84))
    w(out, "items-server-only.txt",
      "item ids the server has that stock v84 does not - CUSTOM CONTENT, PRESERVE. "
      "Never propose deletion.",
      (f"{i}\t{cat_of(i)}\t{srv[i]}" for i in onlysrv))

    print(f"items: v84={len(v84_items)} server={len(srv)} "
          f"shared={len(set(v84_items) & set(srv))}")
    print(f"  v84-only    {len(only84):6}  {tally(only84)}")
    print(f"  server-only {len(onlysrv):6}  {tally(onlysrv)}  (CUSTOM - preserve)")


# Stats the server actually reads off Mob.wz/<id>.img/info (MapleLifeFactory /
# MonsterStats): anything else in info is client-side presentation.
MOB_SERVER_STATS = [
    "maxHP", "maxMP", "level", "exp", "PADamage", "PDDamage", "MADamage", "MDDamage",
    "acc", "eva", "pushed", "speed", "fs", "boss", "undead", "bodyAttack", "firstAttack",
    "removeAfter", "hpTagColor", "hpTagBgcolor", "explosiveReward", "publicReward",
    "onlyNormalAttack", "noFlip", "invincible", "notAttack", "elemAttr", "charismaEXP",
    "summonType", "mobType", "hpRecovery", "mpRecovery", "selfDestruction", "flySpeed",
]


def cmd_mobs(a):
    dump, wz, out = Path(a.dump), Path(a.wz), Path(a.out)
    v84: dict[int, dict[str, str]] = defaultdict(dict)
    for p, _t, v in load_tsv(dump / "v84.Mob.tsv"):
        m = re.match(r"(\d+)\.img/info/([^/]+)$", p)
        if m:
            v84[int(m.group(1))][m.group(2)] = v
        elif re.match(r"(\d+)\.img$", p):
            v84.setdefault(int(p[: p.index(".img")]), {})
    srv = server_mob_stats(wz)

    only84 = sorted(set(v84) - set(srv))
    onlysrv = sorted(set(srv) - set(v84))
    shared = sorted(set(v84) & set(srv))

    diffs, diff_ids = [], set()
    per_stat: dict[str, int] = defaultdict(int)
    for mid in shared:
        for k in MOB_SERVER_STATS:
            a84, asrv = v84[mid].get(k), srv[mid].get(k)
            if a84 is None and asrv is None:
                continue
            if _num(a84) != _num(asrv):
                diffs.append(f"{mid}\t{k}\t{asrv if asrv is not None else '-'}\t"
                             f"{a84 if a84 is not None else '-'}")
                per_stat[k] += 1
                diff_ids.add(mid)

    w(out, "mobs-stat-diffs.txt",
      "mob id\tstat\tserver value\tv84 value  (shared ids only; server-read stats only)",
      diffs)
    w(out, "mobs-v84-only.txt", "mob ids in stock v84 that the server has no Mob.wz image for",
      (str(i) for i in only84))
    w(out, "mobs-server-only.txt",
      "mob ids the server has that stock v84 does not - CUSTOM, preserve",
      (str(i) for i in onlysrv))

    print(f"mobs: v84={len(v84)} server={len(srv)} shared={len(shared)}")
    print(f"  v84-only={len(only84)} server-only={len(onlysrv)}")
    print(f"  shared ids with >=1 server-read stat difference: {len(diff_ids)} / {len(shared)}")
    for k, n in sorted(per_stat.items(), key=lambda x: -x[1]):
        print(f"    {k:20} {n}")


def _num(s):
    """Compare 10 == 10.0 == '10' but keep None distinct from 0."""
    if s is None:
        return None
    try:
        return float(s)
    except ValueError:
        return s


def cmd_npcs(a):
    dump, wz, out = Path(a.dump), Path(a.wz), Path(a.out)
    v_npc, v_mob, v_maps = v84_map_life(Path(a.dump))
    s_npc, s_mob, s_maps = server_map_life(wz)
    v84_npc_imgs = {int(p[: p.index(".img")]) for p, _t, _v in load_tsv(dump / "v84.Npc.tsv")
                    if re.match(r"\d+\.img$", p)}
    s_npc_imgs = server_npc_ids(wz)

    shared_maps = v_maps & s_maps

    def placed_in(pl, maps):
        return {i for i, ms in pl.items() if ms & maps}

    v_only = sorted(placed_in(v_npc, shared_maps) - placed_in(s_npc, shared_maps))
    s_only = sorted(placed_in(s_npc, shared_maps) - placed_in(v_npc, shared_maps))
    v_only = [i for i in v_only if i not in PLAYERNPC_RANGE]
    s_only = [i for i in s_only if i not in PLAYERNPC_RANGE]

    # per-map detail for the v84-only side: which shared map loses the npc
    det = []
    for i in v_only:
        maps = sorted((v_npc[i] & shared_maps) - DENY_MAPS)
        have_img = "img" if i in s_npc_imgs else "NO-IMG"
        det.append(f"{i}\t{have_img}\t" + ",".join(str(m) for m in maps[:12])
                   + ("..." if len(maps) > 12 else ""))
    w(out, "npcs-v84-places-server-does-not.txt",
      "npc id\tserver has Npc.wz image?\tshared maps v84 places it on "
      "(PlayerNPC range 9901910-9901919 excluded)", det)
    w(out, "npcs-server-places-v84-does-not.txt",
      "npc ids this server places on maps that also exist in v84, where v84 places none - "
      "custom placements, preserve",
      (f"{i}\t" + ",".join(str(m) for m in sorted(s_npc[i] & shared_maps)[:12]) for i in s_only))
    w(out, "npcs-image-only-in-v84.txt",
      "npc ids with a v84 Npc.wz image the server's Npc.wz lacks",
      (str(i) for i in sorted(v84_npc_imgs - s_npc_imgs)))

    print(f"maps: v84={len(v_maps)} server={len(s_maps)} shared={len(shared_maps)} "
          f"v84-only={len(v_maps - s_maps)} server-only={len(s_maps - v_maps)}")
    print(f"npc images: v84={len(v84_npc_imgs)} server={len(s_npc_imgs)} "
          f"v84-only={len(v84_npc_imgs - s_npc_imgs)} server-only={len(s_npc_imgs - v84_npc_imgs)}")
    print(f"npc placements on SHARED maps: v84-only={len(v_only)} server-only={len(s_only)}")
    print(f"mob spawn ids on shared maps: v84={len(placed_in(v_mob, shared_maps))} "
          f"server={len(placed_in(s_mob, shared_maps))}")


def cmd_drops(a):
    wz, out = Path(a.wz), Path(a.out)
    items = set(server_item_ids(wz)) | set(server_equip_ids(wz))
    mobs = {int(p.name[: -len(".img.xml")]) for p in (wz / "Mob.wz").glob("*.img.xml")
            if p.name[: -len(".img.xml")].isdigit()}
    reactors = server_reactor_ids(wz)
    _, s_mob_life, _ = server_map_life(wz)
    spawned = set(s_mob_life)

    drops = sql("SELECT dropperid, itemid, questid, chance FROM drop_data")
    rdrops = sql("SELECT reactorid, itemid, questid FROM reactordrops")

    bad_item = sorted({int(d[1]) for d in drops if int(d[1]) != 0 and int(d[1]) not in items})
    bad_mob = sorted({int(d[0]) for d in drops if int(d[0]) not in mobs})
    bad_ritem = sorted({int(d[1]) for d in rdrops if int(d[1]) != 0 and int(d[1]) not in items})
    bad_react = sorted({int(d[0]) for d in rdrops if int(d[0]) not in reactors})
    unspawned = sorted({int(d[0]) for d in drops if int(d[0]) in mobs and int(d[0]) not in spawned})

    w(out, "drops-item-missing-from-wz.txt",
      "drop_data.itemid values with no Item.wz/Character.wz data - the drop cannot be created",
      (f"{i}\t{cat_of(i)}" for i in bad_item))
    w(out, "drops-mob-missing-from-wz.txt",
      "drop_data.dropperid values with no Mob.wz image - the row is dead weight",
      (str(i) for i in bad_mob))
    w(out, "reactordrops-item-missing-from-wz.txt",
      "reactordrops.itemid values with no Item.wz/Character.wz data", (str(i) for i in bad_ritem))
    w(out, "reactordrops-reactor-missing-from-wz.txt",
      "reactordrops.reactorid values with no Reactor.wz image", (str(i) for i in bad_react))
    # the inverse gap: a monster a player can actually meet that drops nothing at all
    has_drops = {int(d[0]) for d in drops}
    dry = sorted(spawned & mobs - has_drops)
    w(out, "drops-spawned-mob-with-no-drops.txt",
      "mob ids that spawn on a map and have ZERO drop_data rows - killing them yields "
      "nothing but exp", (str(i) for i in dry))

    w(out, "drops-mob-never-spawned.txt",
      "dropperids that exist in Mob.wz but are placed on no map's life list - "
      "their drops are unreachable unless a script summons them",
      (str(i) for i in unspawned))

    print(f"drop_data={len(drops)} reactordrops={len(rdrops)}")
    print(f"  itemid not in wz:      {len(bad_item)} distinct")
    print(f"  dropperid not in wz:   {len(bad_mob)} distinct")
    print(f"  reactordrop item n/a:  {len(bad_ritem)} distinct")
    print(f"  reactordrop reactor n/a: {len(bad_react)} distinct")
    print(f"  droppers never spawned: {len(unspawned)} distinct")
    print(f"  spawned mobs with no drops at all: {len(dry)} of {len(spawned & mobs)} spawned")


def _tsv_info(path: Path, pats: list[str]) -> dict[int, dict[str, str]]:
    """{entity id: {info key: value}} from a WzValues dump, given id/key capture patterns."""
    out: dict[int, dict[str, str]] = defaultdict(dict)
    rx = [re.compile(p) for p in pats]
    for p, _t, v in load_tsv(path):
        for r in rx:
            m = r.match(p)
            if m:
                out[int(m.group(1))][m.group(2)] = v
                break
    return out


def server_equip_info(wz: Path) -> dict[int, dict[str, str]]:
    out: dict[int, dict[str, str]] = {}
    for img in (wz / "Character.wz").rglob("*.img.xml"):
        stem = img.name[: -len(".img.xml")]
        if stem.isdigit():
            out[int(stem)] = _xml_info_block(read_norm(img))
    return out


def server_item_info(wz: Path) -> dict[int, dict[str, str]]:
    """Item.wz/<cat>/<bucket>.img/<id>/info/<k>  (id at indent 2, info at 4, keys at 6)."""
    out: dict[int, dict[str, str]] = {}
    for cat in sorted(p for p in (wz / "Item.wz").iterdir() if p.is_dir()):
        for img in sorted(cat.glob("*.img.xml")):
            stem = img.name[: -len(".img.xml")]
            data = read_norm(img)
            if stem.isdigit() and len(stem) >= 7:
                out[int(stem)] = _xml_info_block(data)
                continue
            cur = None
            in_info = False
            for line in data.split(b"\n"):
                m = re.match(rb'  <imgdir name="(\d+)"', line)
                if m:
                    cur, in_info = int(m.group(1)), False
                    out.setdefault(cur, {})
                    continue
                if line.startswith(b'    <imgdir name="info"'):
                    in_info = True
                    continue
                if line.startswith(b'    <') or line.startswith(b'    </'):
                    in_info = False
                if in_info and cur is not None and line.startswith(b"      <"):
                    s = SCALAR.match(line.strip(), 0)
                    if s:
                        out[cur][s.group(2).decode()] = s.group(3).decode()
    return out


# Stats a rebalance would move.  Deliberately narrow: presentation keys (icon origins,
# sfx names) differ constantly and drown the signal.
EQUIP_STATS = ["reqLevel", "reqSTR", "reqDEX", "reqINT", "reqLUK", "reqJob", "tuc", "price",
               "incSTR", "incDEX", "incINT", "incLUK", "incPAD", "incMAD", "incPDD", "incMDD",
               "incACC", "incEVA", "incSpeed", "incJump", "incMHP", "incMMP", "attackSpeed",
               "knockback", "cash", "only", "notSale", "tradeBlock", "islot", "vslot"]
ITEM_STATS = ["price", "slotMax", "reqLevel", "cash", "only", "quest", "timeLimited",
              "notSale", "tradeBlock", "mcType", "consumeOnPickup"]


def cmd_rebalance(a):
    """Three-way: v83-stock -> v84 (did NEXON move it?) vs v83-stock -> this server
    (did the OWNER move it?).  Two-way server-vs-v84 alone cannot tell those apart, and
    that ambiguity is the whole reason the mob question stayed open."""
    dump, wz, out = Path(a.dump), Path(a.wz), Path(a.out)
    kinds = [
        ("mob", "v83.Mob.tsv", "v84.Mob.tsv", [r"(\d+)\.img/info/(\w+)$"],
         MOB_SERVER_STATS, server_mob_stats),
        ("equip", "v83.CharacterInfo.tsv", "v84.CharacterInfo.tsv",
         [r"(?:[^/]+/)?(\d+)\.img/info/(\w+)$"], EQUIP_STATS, server_equip_info),
        ("item", "v83.Item.tsv", "v84.ItemInfo.tsv",
         [r"[^/]+/[^/]+\.img/(\d+)/info/(\w+)$", r"[^/]+/(\d{7,})\.img/info/(\w+)$"],
         ITEM_STATS, server_item_info),
    ]
    for name, f83, f84, pats, stats, srvfn in kinds:
        p83, p84 = dump / f83, dump / f84
        if not (p83.exists() and p84.exists()):
            print(f"{name}: SKIP (missing {f83 if not p83.exists() else f84})")
            continue
        v83, v84, srv = _tsv_info(p83, pats), _tsv_info(p84, pats), srvfn(wz)
        shared = set(v83) & set(v84) & set(srv)
        nexon, local, both = [], [], []
        for eid in sorted(shared):
            for k in stats:
                x, y, z = (_num(v83[eid].get(k)), _num(v84[eid].get(k)), _num(srv[eid].get(k)))
                if x == y == z:
                    continue
                row = (f"{eid}\t{k}\t{v83[eid].get(k, '-')}\t{v84[eid].get(k, '-')}\t"
                       f"{srv[eid].get(k, '-')}")
                if x != y and z != x:
                    both.append(row)
                elif x != y:
                    nexon.append(row)
                elif z != x:
                    local.append(row)
        hdr = "id\tstat\tv83-stock\tv84-stock\tthis server"
        w(out, f"rebalance-{name}-nexon.txt",
          f"{hdr} - NEXON changed it between v83 and v84 and this server still has v83's "
          f"value. This is the real v84 content gap.", nexon)
        w(out, f"rebalance-{name}-local.txt",
          f"{hdr} - v83 and v84 agree; this server differs. OWNER'S custom balance, preserve.",
          local)
        w(out, f"rebalance-{name}-both.txt",
          f"{hdr} - both moved. Merging v84 would overwrite an owner edit; hand-resolve.", both)
        print(f"{name}: shared={len(shared)}  nexon-only={len(nexon)}  "
              f"owner-only={len(local)}  both={len(both)}")


def _quest_tree(path: Path):
    """{questid: {state: element}} for Check.img / Act.img."""
    import xml.etree.ElementTree as ET
    root = ET.parse(path).getroot()
    out: dict[str, dict[str, object]] = {}
    for q in root.findall("imgdir"):
        out[q.get("name")] = {s.get("name"): s for s in q.findall("imgdir")}
    return out


def _item_reqs(state) -> list[tuple[int, int]]:
    if state is None:
        return []
    holder = state.find("imgdir[@name='item']")
    if holder is None:
        return []
    out = []
    for e in holder.findall("imgdir"):
        i = e.find("int[@name='id']")
        c = e.find("int[@name='count']")
        if i is not None:
            out.append((int(i.get("value")), int(c.get("value")) if c is not None else 0))
    return out


def cmd_quests(a):
    """Quest items with no source anywhere - the 4032498-class bug, swept across all quests."""
    wz, out = Path(a.wz), Path(a.out)
    check = _quest_tree(wz / "Quest.wz/Check.img.xml")
    act = _quest_tree(wz / "Quest.wz/Act.img.xml")

    # every source that can put an item in a player's inventory without a GM command
    from_drop = {int(r[0]) for r in sql("SELECT DISTINCT itemid FROM drop_data")}
    from_react = {int(r[0]) for r in sql("SELECT DISTINCT itemid FROM reactordrops")}
    from_shop = {int(r[0]) for r in sql("SELECT DISTINCT itemid FROM shopitems")}
    # Maker is a real source and an easy one to miss: Aran's 4032334 is CRAFTED, so without
    # this the tool reports two Aran quests as blocked when nothing is wrong with them.
    from_maker = {int(r[0]) for r in sql("SELECT DISTINCT itemid FROM makercreatedata")}
    from_act = {iid for q in act.values() for st in q.values()
                for iid, cnt in _item_reqs(st) if cnt > 0}
    # Scripts are scanned for a GRANT, not a mention: 4032449 and 4032451 appear all over
    # scripts/quest/2200*.js as *checks* and still have no source anywhere, so a plain token
    # scan hides exactly the bug being hunted. Mentions are kept as a weaker second signal.
    granted: set[int] = set()
    mentioned: set[int] = set()
    grant = re.compile(r"(?:gainItem|giveItem|addItem)\w*\s*\(\s*(\d{6,8})")
    for f in (Path("scripts")).rglob("*.js"):
        txt = f.read_text(encoding="utf-8", errors="replace")
        granted.update(int(m.group(1)) for m in grant.finditer(txt))
        mentioned.update(int(m.group()) for m in re.finditer(r"\b\d{7,8}\b", txt))

    s_npc, _s_mob, _maps = server_map_life(wz)
    placed_npcs = set(s_npc)
    # mobs that actually spawn, so "it drops off X" can be checked for reachability
    spawned = set(_s_mob)
    droppers: dict[int, set[int]] = defaultdict(set)
    for d, i in sql("SELECT dropperid, itemid FROM drop_data"):
        droppers[int(i)].add(int(d))

    import xml.etree.ElementTree as ET
    info = {}
    for q in ET.parse(wz / "Quest.wz/QuestInfo.img.xml").getroot().findall("imgdir"):
        n = q.find("string[@name='name']")
        ar = q.find("int[@name='area']")
        info[q.get("name")] = (n.get("value") if n is not None else "",
                               ar.get("value") if ar is not None else "-")

    # item names, so a "no source" row says what the item IS and a fix agent can guess the
    # intended dropper without opening the client. Optional: --dump may be absent.
    # The v84 description usually names the intended dropper outright ("A tree branch from a
    # Stump"), which is the whole fix for a missing drop_data row - so carry it, truncated.
    iname: dict[str, str] = {}
    if a.dump:
        parts: dict[str, dict[str, str]] = defaultdict(dict)
        for p, _t, v in load_tsv(Path(a.dump) / "v84.String.tsv"):
            m = re.match(r"(?:Etc\.img/Etc|Consume\.img|Ins\.img|Cash\.img)/(\d+)/(name|desc)$", p)
            if m:
                parts[m.group(1)][m.group(2)] = v
        for k, kv in parts.items():
            iname[k] = kv.get("name", "?") + " | " + kv.get("desc", "")[:110]

    rows, unreach = [], []
    for qid, states in sorted(check.items(), key=lambda kv: int(kv[0])):
        start_npc = None
        st0 = states.get("0")
        if st0 is not None:
            n = st0.find("int[@name='npc']")
            if n is not None:
                start_npc = int(n.get("value"))
        reachable = start_npc is None or start_npc in placed_npcs
        for iid, cnt in _item_reqs(states.get("1")):
            if cnt <= 0:
                continue                      # "must not hold" / removal, not a fetch
            src = []
            if iid in from_drop:
                src.append("drop-spawned" if droppers[iid] & spawned else "drop-UNSPAWNED")
            if iid in from_react:
                src.append("reactor")
            if iid in from_shop:
                src.append("shop")
            if iid in from_act:
                src.append("questreward")
            if iid in from_maker:
                src.append("maker")
            if iid in granted:
                src.append("script-grant")
            if src:
                continue
            note = "script-mention-only" if iid in mentioned else "NO-TRACE"
            note += "\t" + iname.get(str(iid), "?")
            qname, area = info.get(qid, ("", "-"))
            # Korean-named quests were never localised in this era and no English player
            # reaches them; same filter that collapsed the missing-script backlog.
            lang = "EN" if qname.isascii() else "KO"
            (rows if reachable else unreach).append(
                f"{qid}\t{iid}\t{cnt}\t{start_npc if start_npc is not None else '-'}\t"
                f"{note}\tarea{area}\t{lang}\t{qname}")

    w(out, "quest-items-no-source.txt",
      "quest\titem\tcount\tstart npc - completion item with NO drop / reactor / shop / quest "
      "reward / script mention, on a quest whose start NPC IS placed. Blocks completion.", rows)
    w(out, "quest-items-no-source-unreachable.txt",
      "same, but the quest's start NPC is placed on no map - nobody can start it, so the "
      "missing source is invisible", unreach)
    print(f"quests with a completion item and no source at all: "
          f"{len(rows)} reachable / {len(unreach)} unreachable")


def cmd_shops(a):
    """NPCs the client labels as a store but that the server cannot open a shop for.
    Shops are server-side data, so v84 cannot be diffed directly - the client's own
    String.wz 'func' label is the only statement of intent that exists on both sides."""
    dump, wz, out = Path(a.dump), Path(a.wz), Path(a.out)
    func, name = {}, {}
    for p, _t, v in load_tsv(dump / "v84.String.tsv"):
        m = re.match(r"Npc\.img/(\d+)/(func|name)$", p)
        if m:
            (func if m.group(2) == "func" else name)[int(m.group(1))] = v

    have = {int(r[0]) for r in sql("SELECT DISTINCT npcid FROM shops")}
    scripted = set()
    for f in Path("scripts/npc").glob("*.js"):
        if re.search(r"openShop|openNpcShop", f.read_text(encoding="utf-8", errors="replace")):
            if f.stem.isdigit():
                scripted.add(int(f.stem))

    s_npc, _m, _maps = server_map_life(wz)
    rows = []
    for nid, fn in sorted(func.items()):
        if not re.search(r"store|shop|merchant|vendor|weapon|armou?r|potion|scroll|general",
                         fn, re.I):
            continue
        if nid in have or nid in scripted:
            continue
        maps = sorted(s_npc.get(nid, ()))
        rows.append(f"{nid}\t{'PLACED' if maps else 'unplaced'}\t{name.get(nid,'?')}\t{fn}\t"
                    + ",".join(str(m) for m in maps[:6]))
    placed = [r for r in rows if "\tPLACED\t" in r]
    w(out, "shops-missing.txt",
      "npc\tplaced?\tname\tv84 func label\tmaps - the client calls this NPC a shop and the "
      "server has neither a shops row nor an openShop script for it", rows)
    print(f"npcs labelled as a shop with no server shop: {len(rows)} "
          f"({len(placed)} of them actually placed on a map)")


def cmd_selftest(a):
    """ponytail: one check, on the two parsers that can fail SILENTLY - a life block that
    drops its first entry, and an info block that reads the wrong nesting level, both look
    exactly like 'the data is not there' in every report downstream."""
    wz = Path(a.wz)
    life = list(_life_entries(read_norm(wz / "Map.wz/Map/Map1/100000000.img.xml")))
    assert len(life) == 30, f"Henesys life entries: {len(life)} != 30"
    assert ("n", 1012000) in life, "Henesys is missing NPC 1012000 - first-entry off-by-one"
    assert sum(1 for k, _ in life if k == "m") == 0, "Henesys has no mob spawns"

    info = _xml_info_block(read_norm(wz / "Mob.wz/0100100.img.xml"))
    assert info.get("maxHP") == "8" and info.get("level") == "1", f"snail info wrong: {info}"
    assert "zigzag" not in info, "info parser leaked a sibling container's child"

    items = server_item_ids(wz)
    assert items.get(2000000) == "Consume", "red potion missing from Item.wz bucket scan"
    assert 5000000 in items, "Item.wz/Pet whole-image id missing"
    assert 1002000 not in items, "Character.wz equip must not come from the Item.wz scan"
    assert server_equip_ids(wz).get(1002000) == "Cap", "equip id/category wrong"
    print("selftest OK")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("cmd", choices=["items", "mobs", "npcs", "drops", "quests", "rebalance", "shops", "selftest", "all"])
    ap.add_argument("--dump", default="")     # not needed by selftest/drops
    ap.add_argument("--wz", default="wz")
    ap.add_argument("--out", default="tools/parity/reports")
    a = ap.parse_args()
    cmds = {"selftest": cmd_selftest, "items": cmd_items, "mobs": cmd_mobs,
            "npcs": cmd_npcs, "drops": cmd_drops, "quests": cmd_quests, "rebalance": cmd_rebalance, "shops": cmd_shops}
    for name in (cmds if a.cmd == "all" else [a.cmd]):
        print(f"===== {name}")
        cmds[name](a)


if __name__ == "__main__":
    main()
