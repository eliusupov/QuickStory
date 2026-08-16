# 42 — v84 content parity: what the client ships vs what the server can serve

**Status:** measurement complete. No fix applied — this ticket defines the work, it does not do it.

**Snapshot:** wz tree as of commit `94e66d80c` + working tree; DB read 2026-08-16 ~21:20–21:35 UTC.
`drop_data` grew 23,013 → 23,014 *during* the run (another agent seeded `130100 → 4032498`
for quest 22004 mid-measurement), so every DB figure here is a point-in-time snapshot, not a
constant. Re-run the tool rather than trusting an aged number.

## Reproduce

```
dotnet build -c Release --project tools/parity/WzValues
powershell -File tools/parity/dump-v84.ps1 -OutDir <scratch>     # ~4 min, packed v84 -> TSV
python tools/parity/parity.py all --dump <scratch> --wz wz --out tools/parity/reports
```

`parity.py selftest` is the guard: it pins Henesys' 30 life entries (incl. NPC 1012000), the
snail's `info` block, and the Item/Character id split. Both parsers it covers fail *silently* —
an off-by-one that dropped the first life entry of every map originally reported **518** phantom
missing NPC placements instead of the true 156. Run selftest before believing any number below.

Sides compared:
- **v84 stock** = `D:\games\wz-stage\v84-base` (hash-pinned by `docs/wz-baseline/backport/v84-base-tree.sha256`)
- **v83 stock** = `D:\games\MapleStory\Server\porting-resources\wz-data\v83-stock` (for attribution)
- **server**    = this repo's `wz/` XML tree — the tree the server actually loads
- **DB**        = live `cosmic`

---

## Headline: the mob-rebalance blind spot is CLOSED

**Verdict: v84 did NOT rebalance mob stats. Not one HP, EXP, level, damage, accuracy or
avoidability value moved between v83 and v84 on any of the 1,564 mobs all three trees share.**

Three-way attribution (`tools/parity/reports/rebalance-mob-*.txt`), over the 34 `info` keys
`LifeFactory`/`MonsterStats` actually read:

| what changed | rows | stat keys involved |
|---|---|---|
| Nexon changed v83→v84, server still on v83 | 831 | `mobType` 669, `elemAttr` 160, `summonType` 2 |
| Owner changed v83→server (v83 and v84 agree) | 277 | `maxHP` 23, `exp` 16, `eva` 16, `boss` 16, `level` 13, `PADamage`/`PDDamage` 11 each, … |
| **both moved (conflict)** | **0** | — |

Reading the three Nexon keys:

- **`mobType` (669 mobs) — v84 DELETED the field.** `grep -rn mobType src/main/java` returns
  nothing: the server never reads it. Zero gameplay impact in either direction.
- **`summonType` (2 mobs: 2300100, 4250000) — v84 deleted it.** Read by the server, but two mobs.
- **`elemAttr` (160 mobs) — the only real change, and it is purely additive.** All 160 rows are
  `v84 == v83 + suffix`, and the suffix is always a `D<n>` term: `H3` → `H3D2`, `H2F3` → `H2F3D3`,
  `I2L2F2H2` → `I2L2F2H2D2`. v84 gave 160 monsters a **Darkness** resistance they did not have in
  v83. Nothing else in the string moved.

Why that one matters here specifically: `Element.getFromChar('D')` → `DARKNESS` already exists
(`src/main/java/server/life/Element.java:55-56`), and `LifeFactory.java:189` decodes `elemAttr` into
`MonsterStats` effectiveness, so a merge needs **no code change**. And the only skills in this tree
with `elemAttr="d"` are **22151002, 22161002, 22181002** — three *Evan* skills
(`wz/Skill.wz/2215.img.xml:427`, `2216.img.xml:369`, `2218.img.xml:638`). So the 160-mob delta is
precisely the balance data for Evan's dark-element attacks, currently missing.

Direction of the effect: with no `D` term the server defaults those mobs to NORMAL effectiveness,
so **Evan's dark skills currently hit 160 monsters harder than v84 intends**. Merging makes Evan
weaker, not stronger. That is correctness, not a blocker — rank it accordingly.

Merge safety: `rebalance-mob-both.txt` is **empty**. No mob whose `elemAttr` the owner edited is
also one Nexon edited, so a targeted `elemAttr`-only merge on 160 images cannot clobber an owner
edit. It is the safest merge in the whole backport.

Same three-way for equips and items, for completeness — both are near-noise:

| kind | shared ids | Nexon-only rows | owner-only rows | conflicts |
|---|---|---|---|---|
| equip (`Character.wz/*/info`) | 7,186 | 32 (7 ids) | 122 | 25 |
| item (`Item.wz/*/*/info`) | 5,424 | 17 (7 ids) | 216 | 10 |

One landmine worth recording even though it is currently harmless: **1382058, 1452058, 1492024**
carry v84's stats (`incPAD` 77/98/76, `attackSpeed` 5–8) on top of v83's requirements
(`reqLevel 0`, `reqSTR/DEX/INT 0`, `cash 1`) — a half-finished merge that would make three
level-105 weapons wearable at level 1. **They have no drop, no shop row, no reactor drop, no
script and no Commodity entry, so nobody can obtain them.** Invisible today; a landmine the day
someone adds them to a shop. (1472069 is the same shape but kept `reqLevel 40`, so it is not in
the same class.)

---

## Ranked gap list

Ranking is by *what stops someone playing*, not by row count. Rows 1–3 block a player mid-quest.
Rows 4–6 are correctness. Rows 7+ are invisible and are listed so nobody re-measures them.

### 1. Evan quest chain — 9 quests dead on a required item with no source anywhere

`tools/parity/reports/quest-items-no-source.txt`, filter `area7`. This is the exact class of bug the
owner named. The v84 quest text names the intended dropper outright, quoted here so the fix is
mechanical:

| quest | needs | ×  | intended source, from the v84 quest text |
|---|---|---|---|
| 22524 Strange Puppet | 4032459 Blue Mushroom Doll | 1 | hunt 100 × mob **2220100** |
| 22531 A Guard's Fourth Assignment | 4032461 Zombie Mushroom Doll | 1 | mob **2230101** / **2230131** in 105050300 |
| 22532 A Guard's Fifth Assignment | 4032462 Wild Boar Doll | 1 | mob **2230112** — *and see row 2, it does not spawn* |
| 22548 Clue about the Thief | 4032463 Document with Clue | 1 | mob **3110100** |
| 22559 Eliminate the Golems | 4032466 Golem Doll | 1 | "Enraged Golems" behind the door — no v84 mob has that name; GUESS: 5130101/5130102 |
| 22407 Making a Bigger Saddle | 4032476 Captain Alpha's Buckle | 2 | Shipwreck Treasure Chests in Aquaroad → a **reactor** drop, not a mob drop |
| 22410 The Lost Big Saddle | 4032504 Lycanthrope Leather / 4032505 | 10 / 2 | same materials as 22407 |
| 22408 Obtaining the Unbreakable Porcelain | 4032497 "Potter" | 1 | rescue NPC **2092100 Potter** in the Pirate Hideout — script, not a drop |
| 28351 Dragon Master's Treasure Chest | 4000566–4000571 | 1 each | inside the "Evan Gift Box" from Cassandra — an item-in-box, not a drop |

Precedent for the fix shape already exists in the DB: the Aran equivalents
(`9400614–9400617`, `9400655`, `9400656`) carry rows like
`dropperid, itemid, 1, 1, questid=28205, chance=80000`.

**22004 (Thick Branch, 4032498) is already fixed** — another agent inserted `130100 → 4032498`
during this run. Row `drop_data.id=23015`. Its two siblings 4032449/4032451 are granted by
`scripts/npc/1013200.js:5` and `scripts/npc/1013104.js:4`, so they are sourced. UNPROVEN concern:
both calls read `cm.gainItem(4032451, true)` — a boolean where a quantity is expected. Worth one
look by whoever owns scripts/.

### 2. Seven NPCs v84 places that this server does not — two of them gate Evan quests

`npcs-v84-places-server-does-not.txt`. 156 rows, of which **149 are 9901000–9901849** — Nexon's own
static PlayerNPC display slots. Cosmic does not use them: it drives PlayerNPCs from
`playernpcs`/`playernpcs_field` and allocates ids at runtime.
`src/main/java/server/life/PlayerNPC.java:66-67` states the band outright — *"NPCs 9901910-9906599
and 9977777 are custom additions to HeavenMS"* — and this server's `Npc.wz` carries 5,331 images
across 9901020–9906599 plus 9977777, none of which stock v84 has.

**Recommendation: do not merge any of the 149.** They would place inert statues that duplicate the
DB-driven system. Note the nuance: they sit *below* the documented 9901910 allocator floor, so the
existing 9901910–9901919 deny rule does not cover them — the reason to refuse them is the
PlayerNPC subsystem, not the deny list. Widening the deny rule to 9901000–9906599 would encode
that once.

The real list is seven:

| npc | name | maps v84 places it on | why it matters |
|---|---|---|---|
| 2092100 | Potter | 251000000 | quest 22408 needs him rescued |
| 1022106 | Christopher | 106000000/100/200 | known Evan blocker |
| 1011101 | General Mau (Street Vendor) | 100000100 | Henesys market vendor |
| 1022107 | Perion Warning Post | 101030000–101030400 | signpost, cosmetic |
| 2030015 | Hidden Rock | 211040400 | — |
| 9010012 | Star Pixie | 200010000 | — |
| 9010013 | Hengki | 240010200 | — |

**Correction to a stated assumption:** *1013000 (Mir) is placed on no map in v84 either.* Both trees
agree: zero placements. Whatever blocks Mir is not a Map.wz merge — he must be script-summoned.

Five NPCs go the other way (server places, v84 does not): 1052012 Mong from Kong, 9010021 Wolf
Spirit Ryko, 9100110 Gachapon, 9105003 Snow Spirit. Custom placements — **preserve**.

### 3. Four mob spawns v84 places that this server does not

`2220110` Crying Blue Mushroom (106010000, 106010100), **`2230112` Terrified Wild Boar (101030001)**,
`9200018` Jr. Yetti + `9200019` White Fang (196000000). 2230112 is the quest-22532 target from row 1,
so that quest is blocked **twice over**: the mob does not spawn *and* has no drop.
Server-only spawn: `9500102` Orange Mushroom on 1010400 — custom, preserve.

### 4. `elemAttr` on 160 mobs — see the headline

Correctness, not a blocker. Zero-conflict merge. Affects Evan's three dark skills only.

### 5. 35 non-event English quests outside Evan blocked the same way

Same report, filter `$7 != "area50" && $8 == "EN"`. Notable clusters:
- **4001038–4001043 + 4001115/4001116** feed *four* quests at once (9412 Hughes Needs the Eraser,
  9413 A Revenge on Gray the Alien, 9414 An Immovable Sword, 9415 Hectagon Crystal Necklace).
  One fix, four quests.
- area41 Leafre: 3758 Towards the Sky 1, 3760 Dragonica's Horn, 7301/7303 Secret Medicine.
- area44 Magatia: 3361 Zenumist, 3362 Alcadno.
- area30 rows are almost all seasonal (Anniversary / Thanksgiving / Festival of Lights / Amoria).

### 6. Seven NPCs the client labels a shop that the server cannot open

`shops-missing.txt`. 25 candidates, 12 placed, and of those 12 five already have an
`scripts/npc/*.js`. The genuinely silent ones are **1002104 / 1204005 (Tru, Info Merchant)** and
**9209002–9209006 (Amoria Special Item Merchants, maps 680100000–680100003)**. Clicking them does
nothing. Weak signal by construction: shop inventories are server-side, so v84 can only tell us the
`func` label, never the stock list.

### 7 and below — measured, and NOT worth acting on

| finding | count | why it is invisible |
|---|---|---|
| **item ids v84 has that the server cannot serve** | **0** | `Item.wz` + `Character.wz` id sets: v84 13,639, server 13,647, shared 13,639. The item data gap is *closed*. |
| item ids the server has that v84 lacks | 8 | faces 20816/20817/21817/21820 and use-items 2023000–2023003. **CUSTOM — preserve, never propose deletion.** |
| mob ids missing either way | 0 / 0 | 1,600 = 1,600, identical sets |
| NPC images missing from the server | 0 | server has all 1,662 of v84's, plus 5,332 PlayerNPC-band images |
| Reactor images missing from the server | 0 | v84 425, server 427 (extra: 9400300, 9400301 — custom) |
| maps in v84 but not the server | 1 | `100030301` Forest Hall — the deliberately-refused, test-pinned map. The server's map set is otherwise a strict superset (833 extra = v84's removed set). |
| `drop_data.itemid` with no item data | 0 | every drop can be created |
| `reactordrops.itemid` with no item data | 0 | — |
| `drop_data.dropperid` with no `Mob.wz` image | 24 | dead rows; includes `dropperid=2000`, which is not a mob id at all |
| `reactordrops.reactorid` with no `Reactor.wz` image | 13 | 200000–200009 exist in *neither* tree — legacy seed junk |
| droppers that exist but spawn on no map | 392 | summon-only bosses; their drops are reachable via scripts |
| spawned mobs with zero drop rows | 177 of 785 | overwhelmingly tutorial / PQ / event / boss-summon mobs that are *meant* to drop nothing (Golden Pig ×10, Target Slime, Tutorial Leatty, Black Mage Wyvern…). The 7 that are not: `9400618–9400622`, `9400657`, `1210111` — the "Strange …" mobs, i.e. row 1 again. |
| quest items with no source on *unreachable* quests | 74 | start NPC placed on no map — nobody can start them |
| quest items with no source on Event-tab quests | 294 (162 EN / 132 KO) | seasonal rotation, same class already dismissed for missing scripts |

---

## What I could NOT measure, and why

- **Shop inventories.** Shops are server-side; v84 ships no shop data. The `func` label is the only
  cross-tree statement of intent, and it says nothing about stock, price or rotation. Row 6 is
  therefore a *candidate* list, not a gap list.
- **Whether a required item drops at the right rate.** Presence of a `drop_data` row is all that is
  checkable. A row with `chance=1` and a row with `chance=80000` both read as "has a source".
- **Item-in-box sources.** 28351's 4000566–4000571 come out of an "Evan Gift Box"; there is no table
  that models box contents, so any such item reads as sourceless. Rate of this false positive across
  the 368: UNPROVEN, not sampled.
- **Sub-`info` mob data.** Only scalars directly under `<imgdir name="info">` are compared. Nested
  containers (`skill/`, `revive/`, `attack*/`) are not. A v84 change to a boss's skill list would be
  invisible to this ticket. GUESS: unlikely, given that not one scalar moved — but it is a guess.
- **`List.wz`** — not a WZ archive; **`Sound.wz/BgmGL.img`** — unparseable by MapleLib in all three
  trees. Both were already known and neither was re-attempted.
- **Quest-chain transitive reachability.** "Reachable" here means only "the start NPC is placed on
  some map". A quest whose prerequisite quest is itself blocked still counts as reachable, so rows 1
  and 5 are upper bounds on what a player can actually walk into.
- **Whether v84 edited map layouts** (footholds, portals, spawn points) on shared maps. Only `life`
  entries and a few `info` keys were pulled from `Map.wz`. `modified-list/Map.txt` reports which
  images moved; nobody has diffed their contents.

## Files

- `tools/parity/WzValues/` — C# value-dumper (packed `.wz` → TSV). Binds to the MapleLib that
  `docs/wz-baseline/tool` already built, so it writes nothing outside `tools/parity/`.
- `tools/parity/dump-v84.ps1` — the sweep. ~4 min.
- `tools/parity/parity.py` — subcommands `items mobs npcs drops quests rebalance shops selftest all`.
- `tools/parity/reports/` — 26 generated manifests, every one carrying its own row count.
