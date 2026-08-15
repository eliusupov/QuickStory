# Collision triage — what to do about every node the additive-only merge refuses

Ticket 03c. Companion machine-readable output: `COLLISION-FORCE.txt` in this directory.
`addlist-dryrun-*.conflicts.txt` says *what* was refused; this file says *what to do*, so the
"names resolve correctly — no blank labels" acceptance criterion in tickets 04–09 has an
answer instead of a hand-edit.

## First: the count in the ticket is wrong

The brief says **41 collisions**. The committed dry-run files say **735**.

```
$ grep -c 'already exists in target' merge-lists/addlist-dryrun-*.conflicts.txt
String 711   Etc 6   Character 6   Npc 10   Map 2      = 735
```

The 41 figure predates the tool rewrite that raised `EXPAND_DEPTH` to 3
(`tool/Program.cs:33`, commit `c4c3e77a0`) — at depth 1 you never see inside
`String.wz/MonsterBook.img/<mob>/reward` or `Eqp.img/Eqp/<category>`, which is where 684 of the
735 live. Two of the four per-file counts in the brief are also low (`Character.wz` is 6, not 2;
`Map.wz` has 2 and was not listed at all). Triage below covers all 735.

Reconciling with the **759** figure now circulating: that is the total *refusal* count, which is
these 735 `already exists in target` collisions plus the 24 `unsupported shape` refusals on
`Npc.wz/9000021.img` (`759 = 735 + 24`). The 24 are not id collisions and are not triaged into a
bucket — they are a merge-tool capability gap, described under "Deeper problems" below. Only the
735 are decisions.

## Buckets

| bucket | rows | one-line rule |
|---|---:|---|
| no-op | 35 | live and v84 values are byte-identical; the refusal costs nothing |
| keep local | 653 | live is Cosmic/Ezorsia content or a strict superset of v84's |
| adopt v84 | 37 | live is the literal string `MISSING NAME` / `MISSING INFO` |
| ambiguous | 10 | needs the owner |

Classification method — and why the ticket's suggested signal does not work:

> `modified-list/*.live.txt` distinguishes paths the live client changed from stock v83

It cannot, for two reasons. `modified-list/*.live.txt` is **image-level** (`BlockSize` per
`.img`), so it says "live edited `String.wz/Cash.img`" and stops — it cannot tell you whether
live edited node `5530001` inside it. And `protect-list/` is worse than useless here: it is
defined as *live AND NOT v83 AND NOT v84*, so **by construction no collision can ever appear in
it** — every collision is in v84. Using it silently mislabels live-custom nodes as stock
leftovers (it labels all six Cosmic `Commodity.img/894x` cash-shop rows "stock").

What was actually done: a three-way **value** compare of all 735 paths across v83-stock, v84 and
the live client, flattening each node's subtree to depth 4 and diffing the signatures. Result:
733 of 735 are absent from v83-stock entirely (they are all v84 *additions* that Cosmic
independently invented on the same path), so "stock leftover" is not the axis. The axis that
holds is **is the live value a name-table stub or real content**.

---

## Keep local — 653

### `99019xx` player-NPC block — 20 rows (10 `Npc.wz` + 10 `String.wz`)

| | live | v84 |
|---|---|---|
| `String.wz/Npc.img/9901910` | `n0` "I am /name, who has reached Lv. 120." + `d0` Empress blessing + `n1` "speak with Hawkeye … Thunder Breaker" | `n0` "I am /name, who has reached Lv. 200." only |
| `Npc.wz/9901910.img` | same as v84 **plus** `info/speak/0`,`info/speak/1`, `say/speak/1`, `stand/delay=7000` | subset |

Live wins on both: strictly more content, and the ids are server-owned (see the verdict section).
Same for `9901911`–`9901919`.

### `Character.wz/Accessory/0114215{3,4}.img` — 2 rows

Live and v84 have **identical** `info` scalars (`medalTag=82`, `reqLevel=21`, `incMHP=80`, …).
The only difference is that live carries an extra `info/level` subtree — Cosmic turned these two
medals into level-up medals. Live is a strict superset. Refusing costs zero.

### `Map.wz/WorldMap/WorldMap010.img/MapList/9{3,4}` — 2 rows

| | live | v84 |
|---|---|---|
| `/93` | `mapNo/0=103000100`, `spot=(-249,-65)`, `type=3` | `mapNo/0..4=1000301xx`, `spot=(5,88)`, `title="Utah's House"`, `type=0` |
| `/94` | `mapNo/0=103000101`, `spot=(-267,-73)`, `type=1` | `mapNo/0..4=1000303xx/100030400`, `spot=(26,86)`, `title="Farm Center"`, `type=1` |

Live's are Cosmic-placed custom world-map markers. Cost of keeping: v84's Utah/farm region has no
world-map entry — cosmetic only, and the maps are still reachable.

### `String.wz/Npc.img/9110002/n2` — 1 row

Live `"Welcome to Mushroom Shrine!"`, v84 `"Musssshhhhroooom Shrine~~~"` (a duplicate of the same
NPC's `d0`). Live is better. Keep.

### `String.wz/MonsterBook.img/<mob>/reward/<n>` — 628 rows

Cosmic rewrote the monster-book drop tables (`MonsterBook.img` 271350 → 295409 bytes, the largest
single live-side edit in `modified-list/String.live.txt`). Example
`MonsterBook.img/3110100/reward/23`: live `2382002`, v84 `4130008`.

These are **positional array slots, not identities**. `reward/23` means "the 24th entry of this
mob's drop list", so overwriting a slot mid-list yields a list that is neither Cosmic's nor
Nexon's. Keep all 628. (There is a live hazard here that is *not* a collision — see
"Deeper problems" below.)

---

## Adopt v84 — 37

Every row: live is the literal `MISSING NAME` / `MISSING INFO`, the stub Cosmic writes for ids it
has no string for. v84 has real text. No judgement needed. Full list with both values is in
`COLLISION-FORCE.txt`.

The Evan-relevant ones, called out because tickets 04–09 fail their "no blank labels" criterion on
exactly these:

- `String.wz/Eqp.img/Eqp/Dragon` — 12 dragon-equip names (Silver/Gold/Reverse × Mask/Pendant/Wings/Tail)
- `String.wz/Eqp.img/Eqp/Taming/190204{0,1,2}` + `19120 3{3,4,5}` — Stage 1–3 Dragon and saddles
- `String.wz/Eqp.img/Eqp/Glove/1082262` — "Dragon Master's Proof"
- `String.wz/Etc.img/Etc/41610{49,50,51}` — the two dragon lore books and the voyage log

This bucket is a superset of the 18 live ids independently found to read literal `MISSING NAME`:
all 12 `Eqp/Dragon` equips (`1942000`–`1972002`) and the 6 Evan mount/saddle names
(`1902040`–`1902042`, `1912033`–`1912035`) are here, plus 19 more. Note that `Eqp/Dragon` is one
row in `COLLISION-FORCE.txt` because it is a collapsed copy root — forcing it covers all 12 ids
beneath it, which is exactly why the add-list never showed them individually.

Caveat on 8 of the 37 (`Accessory/1142143,1142145,1142149,1142150,1142151`,
`Longcoat/1051176`, `Ins.img/3994179,3994180`): v84's text is untranslated KMS Korean, not English.
Adopting is still right — a Korean name beats `MISSING NAME` in every UI — but do not expect
English there.

---

## Ambiguous — 10

Both of these need the owner. Neither was padded into "adopt".

### 1. `Character.wz/Dragon/019{4,5,6,7}2002.img/info/level` — 4 rows

| | value |
|---|---|
| live | `info/<1..N>/exp = 10000` for every level. Nothing else — no stat increments. |
| v84 | `info/1/exp=70`, `info/2/exp=75`, … plus `incSTRMin/Max`, `incDEXMin/Max`, `incINTMin/Max`, `incLUKMin/Max`, `incPDDMin/Max`, `incMDDMin/Max` per level, plus `case/0/prob=1` |

**Trade-off:** v84 is the authentic Evan dragon-growth table and is very likely what ticket 04–09
needs; but Cosmic's flat 10000-exp table is a deliberate shape, not a stub, and if the server
reads this node the flat curve may be load-bearing tuning that the owner chose.

**What the owner needs to know to decide:** whether the Cosmic server reads
`Character.wz/Dragon/*/info/level` at all, or whether dragon levelling is computed server-side. If
the server ignores it, adopt v84 (client-side display only, strictly better). If the server reads
it, adopting changes Evan dragon progression for existing characters.

### 2. `Etc.wz/Commodity.img/894{1..6}` — 6 rows

| id | live | v84 |
|---|---|---|
| 8941 | `SN=60001000 ItemId=5000013 Price=5900 OnSale=1` | `SN=70000365 ItemId=9102234 Price=11000 OnSale=0` |
| 8942 | `SN=60001001 ItemId=5000039 Price=5800 OnSale=1` | `SN=70000366 ItemId=9102235 Price=11400 OnSale=0` |
| 8943 | `SN=60001002 ItemId=5000044 Price=6900 OnSale=1` | `SN=70000367 ItemId=9102236 Price=11000 OnSale=0` |
| 8944 | `SN=60001003 ItemId=5000045 Price=5600 OnSale=1` | `SN=70000368 ItemId=9102237 Price=11400 OnSale=0` |
| 8945 | `SN=60001004 ItemId=5000034 Price=5800 OnSale=1` | `SN=70000369 ItemId=9102238 Price=11000 OnSale=0` |
| 8946 | `SN=60001005 ItemId=5000060 Price=20000 OnSale=1` | `SN=70000370 ItemId=9102190 Price=7000 OnSale=1` |

Live's `SN` block `60001000`–`60001005` is a hand-allocated Cosmic/Ezorsia range selling pets
(`5000013`…`5000060`), all `OnSale=1`. v84's are stock Nexon listings, five of six `OnSale=0`
(not even purchasable).

**Trade-off:** adopting v84 deletes six working cash-shop listings and their SNs; keeping local
loses six listings that are mostly disabled anyway. This looks like keep-local, and the default in
`COLLISION-FORCE.txt` is keep — but it is listed ambiguous because **`SN` is a server-side key**,
not display data, and I did not verify that nothing in the server pins SNs `70000365`–`70000370`.

**What the owner needs to know to decide:** whether `CashItemFactory` / the commodity loader reads
`Commodity.img` from the client wz at all (Cosmic may load cash-shop data from the DB), and
whether SNs `6000100x` are referenced anywhere outside this file. If cash-shop data is DB-driven,
both sides are inert and the rows are a no-op — take keep-local and move on.

---

## Verdict on drop-vs-re-id for the ten `99019xx` NPCs

**Agree with the reviewer: drop is correct, and it is not a close call.** But the reason is
stronger than "the merge tool has no rename".

`src/main/java/server/life/PlayerNPC.java:66-67`:

```java
// TODO: remove dependency on custom Npc.wz. All NPCs with id 9901910 and above are custom
// additions for player npcs.
// In summary: NPCs 9901910-9906599 and 9977777 are custom additions to HeavenMS ...
```

`9901910` is not one Cosmic NPC that happens to sit on a v84 id. It is the **base of a ~4,700-id
range (`9901910`–`9906599`) the server allocates from at runtime** for player-rank NPCs. v84
collides with the first ten of it. Re-iding would mean relocating a server-owned allocator range,
not renaming ten nodes — and the payoff is a cosmetic Lv.200 tier of a fame NPC whose Lv.120 tier
Cosmic already ships. Drop.

Two things the reviewer's framing missed, both of which make drop *more* correct:

1. Live is a **superset** of v84 on these paths, not a divergent version. v84's `9901910.img` has
   no `info/speak/1`, no `say/speak/1`, no `stand/delay`. Even a successful re-id would import
   a node poorer than the one already there.
2. The real hazard is not the refusal at all — see below.

---

## Deeper problems the collision list does not show

Two of these are additions, so `conflicts.txt` is silent on them by design. Both would sail
straight through an additive-only merge.

### 1. `Etc.wz/NpcLocation.img/9901910`–`9901919` would be written, and must not be

They are on `add-list/Etc.txt:10569-10578` and are **absent from the live client**, so the merge
adds them with no complaint. v84's value is `NpcLocation.img/9901910/0 = 100030301` — a fixed world
placement for Nexon's Lv.200 fame NPC. The live client would then place a static NPC on ids the
Cosmic server hands out dynamically to player-rank NPCs (`PlayerNPC.java:66`). This is the
`99019xx` clash for real, and refusing the ten `Npc.wz` images does nothing about it.

**Action for whoever owns the merge procedure: these 10 paths must be excluded from
`add-list/Etc.txt` before any Etc.wz merge runs.** That is a deny, not a force, so it is out of
scope for `COLLISION-FORCE.txt` — it needs an exclusion mechanism the tool does not have either.

### 2. 36 monster-book reward slots would be spliced into 17 Cosmic drop lists

```
add-list  MonsterBook reward slots: 689
refused   (already exists):         654   -> 36 would actually be WRITTEN
mobs hit: 3100101 3110301 3210203 3210206 3210207 4230125 4230400 4230503 4230504
          4250001 5110300 5120501 6130202 7130102 8140100 8140701 8810018
```

Where Cosmic's list is shorter than Nexon's, the tail indices are not collisions, so they get
appended onto Cosmic's list. The result is a hybrid drop table: Cosmic's entries at indices 0–22,
Nexon's at 23–28. Neither vendor ever shipped that list. **Additive-only is not merely
conservative on positional-array nodes — it is actively corrupting**, and the same shape exists
wherever a v84 array is longer than the live one.

Suggested minimum fix, cheapest first: exclude `String.wz/MonsterBook.img/*/reward/*` from the
add-list wholesale. Cosmic's tables are the live ones; there is no partial answer.

### 3. `Npc.wz/9000021.img` — 24 refusals the brief never mentions

`addlist-dryrun-Npc.conflicts.txt:9-32` refuses 24 nodes not with "already exists" but with
`unsupported shape: parent=WzUOLProperty source=WzIntProperty`. v84 rebuilt this NPC
(`modified-list/Npc.txt:7` — 60238 → 21146 bytes, it shrank by two thirds) by replacing inline
frames with UOL links, and the merge cannot write a child into a UOL. Not a name clash and not
triaged here, but it is a merge-tool capability gap sitting in the same file, and this NPC is
partially merged today — worse than either whole version.

---

## Reproducing this

The committed `tool/` has no node-dump mode, so the three-way value compare was done with a
throwaway `cmp3` built against the same MapleLib, run read-only against
`wz-data/v83-stock`, `wz-data/v84` and `D:/games/MapleStory`. It is not committed — nothing here
depends on re-running it, and the values above are quoted verbatim. If it is wanted as a permanent
tool, it belongs as a mode on `tool/Program.cs`, which another agent owns.
