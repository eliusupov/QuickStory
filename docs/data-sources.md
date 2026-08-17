# External data source catalogue (v83/v84 GMS)

Research date: 2026-08-17. Every "PROVEN" claim below was produced by a real fetch from this
machine; the exact command shape is recorded. "REPORTED" means read about, not verified.

## Headline findings (read this first)

1. **The monster-book card gap is not a sourcing problem — it is already in our own WZ.**
   **Our own `wz/Item.wz/Consume/0238.img.xml` contains 382 card entries, and all 382 carry
   `<int name="mob" value="..."/>`** next to `<int name="monsterBook" value="1"/>`.
   (The HeavenMS v83 copy has 343, likewise 343/343 — our v84 tree is the richer one.)
   Sample: `2380000 → 100100`, `2380001 → 100101`, `2380002 → 120100`.
   **No external source needed. Close this gap with a ~10-line parser.**

2. **The "mobs with no drops" list is mostly not a data gap at all.**
   `tools/parity/reports/drops-spawned-mob-with-no-drops.txt` has 177 mobids. Composition by prefix:

   | prefix | count | what they are |
   |---|---|---|
   | 93xxxxx | 76 | event / quest mobs |
   | 94xxxxx | 38 | event mobs |
   | 97xxxxx | 24 | Monster Carnival |
   | 95xxxxx | 11 | event mobs |
   | 91xxxxx | 9 | event bosses (Snowman etc.) |
   | 88xxxxx | 5 | boss **body parts** (Horntail segments 8820019–8820023) |
   | 81/82xxxxx | 6 | boss / boss parts |
   | 12/40/61/71/72/90 | 8 | ordinary field mobs |

   158 of 177 are event/PQ/Carnival/body-part mobs that **legitimately drop nothing in GMS** —
   HeavenMS's own `db_drops.sql` explicitly `DELETE`s PQ/summon mobs and dedupes boss clones.
   No database anywhere has drops for them because they never had any. Triage this list before
   sourcing anything.

3. **The best drop sources are other servers' SQL dumps on GitHub, not any website.**
   Four dumps carrying a numeric `chance` column cover **31 of the 177** between them (286 rows) —
   more than every website in this catalogue combined. Full union across all sources: **39 of 177
   covered, 138 with nothing anywhere.** See §8b and the ranking.

4. **Drop rates exist and are recoverable — but only for mobs we already have.** DreamMS publishes
   numeric rates that are a clean **3× multiple of our own existing chances** (measured, §5).
   That is a calibration result, not a new data source: it confirms DreamMS and our v83 lineage
   share one ancestral table, so DreamMS adds nothing we do not already have.

---

## 1. Hidden Street (bbb.hidden-street.net) — via Wayback only

- **Reachable: PROVEN, but only through the Wayback Machine.**
  - Direct fetch `https://bbb.hidden-street.net/monster/blue-mushroom` → **HTTP 403** (5,453 B Cloudflare
    interstitial). A browser `User-Agent` does **not** help — still 403. Same for `hidden-street.net`
    and `global.hidden-street.net`.
  - Working shape (confirmed):
    ```
    curl -sL "https://web.archive.org/web/2020id_/https://bbb.hidden-street.net/monster/blue-mushroom"
    → HTTP 200, 41,472 B, resolved to .../20210415013950id_/...
    ```
    The `id_` suffix is what returns the original un-rewritten HTML. No auth, no special headers.
- **Era: pre-Big-Bang GMS, roughly v92–v94.** `bbb` = "before big bang". The nav carries Rien,
  Ellin Forest, Cygnus Knights and Legends (Aran/Evan), so the snapshot is *later* than v84 but
  before the Big Bang recalculation. Stats match v83: Blue Mushroom Lv 20 / HP 350 / EXP 32.
  **This is a superset of v84, not a hazard** — and since this branch is Evan/Dual Blade work,
  the extra pre-BB content is a bonus.
- **Data classes carried** (all verified on a real page):
  - Mob: level, HP, MP, EXP, **meso range** (`36-54`), KB, W.Att/M.Att/W.Def/M.Def, accuracy,
    avoidability, speed, magic element weak/normal/strong/immune, status immunities, HP/MP recovery.
  - Mob drops, **categorised**: `Etc. drop`, `Ore drop`, `Useable drop`, `Maker item`, and equipment
    split by Common / Warrior / Magician / Bowman / Thief / Pirate.
  - Spawn maps per mob (17 listed for Blue Mushroom).
  - Quests: level requirement, prerequisite quest, items needed, NPCs involved, prose procedure,
    rewards (exp / mesos / items).
  - Maker recipes with meso cost and ingredient counts (`/item-maker/{warrior,magician,bowman,thief,pirate,etc,useable}`).
  - Gachapon pools (`/cash-shop/gachapon`), boss list (`/monster/boss-list`), exp and pet-closeness tables.
- **Rates: ABSENT.** This is the decisive limitation. Drop entries are bare item-name links with an
  internal node id (`alt="/tip.php?nid=75"`) — **no numeric rate, no rare/common category, nothing**.
  Meso *range* is given; item drop probability is not. Hidden Street halves the drop problem, it does
  not solve it. The `nid` is a Hidden Street CMS id, **not** a game item id, so every name needs
  resolving through maplestory.io or `handbook/*.txt`.
- **NPC shop inventories: NOT PRESENT.** `/npc/{slug}` pages carry only Name / Location / Function /
  Quests involved. Verified on `/npc/ace-of-hearts`. There is no shop section anywhere on the site.
- **Bulk access: HTML scraping only, but enumeration is easy.** Index pages are archived:
  | page | HTTP | size | records |
  |---|---|---|---|
  | `/monster/list` | 200 | 393,351 B | ~919 mob slugs |
  | `/quest/list` | 200 | 660,023 B | 1,150 quest slugs |
  | `/npc/npc-list` | 200 | 72,916 B | paginated, 49/page |
  | `/item-maker/warrior` | 200 | 132,374 B | recipe list |

  The Wayback CDX API enumerates in bulk without scraping the site:
  ```
  curl -s "http://web.archive.org/cdx/search/cdx?url=bbb.hidden-street.net/monster/*&output=text&fl=original&collapse=urlkey&limit=20000"
  → HTTP 200, 50,928 B, 963 archived monster URLs
  ```
- **ToS / rate limits:** the live site blocks non-browser clients outright, so scraping it directly is
  off the table. Wayback has no published hard limit but throttles bursts; keep to ~1 req/s and
  expect occasional 429/503. Content is fan-compiled, no license stated.
- **Verdict: the best source for QUESTS, and a usable cross-check for drop *membership*, spawn maps
  and maker recipes. Useless for drop rates.**

## 2. DreamMS (dreamms.gg) — v92 private server

- **Reachable: PROVEN.** No Cloudflare block, plain GET.
- **Two independent surfaces, and they disagree — this matters:**
  - **JSON API** `https://api.dreamms.gg/api/gms/latest/mob/{id}` → 200. Keys:
    `id, meta, name, description, framebooks, drops`. The `drops` array holds real game item ids
    (`4000008`) with `name`/`desc`/`typeInfo` — and **no rate field of any kind**. Key union across
    all entries: `desc, id, isCash, name, requiredGender, typeInfo`.
    Bulk list `/api/gms/latest/mob` → 200, 147,703 B, **2,168 mobs** as `{id,name,level,isBoss}`.
    Also `/npc/{id}`, `/map/{id}` (109 KB), `/item/{id}`.
  - **Rendered HTML** `https://dreamms.gg/monsters/{id}` → 200, **carries numeric rates**.
    The slug is optional and arbitrary: `/monsters/100130`, `/monsters/100130/muru` and
    `/monsters/100130/zzz` all return the identical 30,716 B page. **Bulk scraping needs mob ids only.**
    Server-rendered, no hydration required. Markup is stable:
    ```html
    <a class="mob-link" href="/equipment/1040010/grey-t-shirt">
      <div class="mon-drop">
        <span class="mon-drop__name"> Grey T-Shirt </span>
        <span class="mon-drop__qty">x 1</span>
        <span class="mon-drop__rate">0.24%</span>
    ```
    **The item id is in the href**, so no name resolution is needed. Category comes from the href
    prefix (`/equipment/`, `/items/`). Mesos render as their own row with a range and 100%.
    Parsed Muru (100130) cleanly: 11 item rows + `Mesos 62-90 @ 100%`, including `2380015 Muru Card @ 2.55%`.
- **Rates: NUMERIC (percent, 2 decimal places) — but they are DreamMS's own tuning, not GMS.**
  See §5: they are our own rates × 3.
- **Coverage of our gap: effectively nil.** All 177 gap mobs fetched (175 ok, 2 hard 429s after retry):
  **6 mobs yielded any drop row, 7 rows total.** The API surface did marginally better on
  a different 3 mobs (63 rows, dominated by 8220013 Nibelung at 42) — and note the contradiction:
  `/monsters/8220013` renders a page with **zero** `mon-drop` markup while the API reports 42 drops.
  Do not trust one surface to stand for the other.
- **Bulk access:** HTML scrape, 1 request per mob, ~2,168 mobs. **Rate limiting is real** — 0.35 s
  spacing produced 87 failures; 0.8 s + retry/backoff produced 2. Budget ~1 req/s, ~40 min for a full crawl.
- **ToS:** none published. Private server, unlicensed fan data.
- **Verdict: excellent mechanics, wrong data. Use it as a CALIBRATION reference, not a source.**

## 3. MapleRoyals drop tracker (royals-droppy.netlify.app) — v89

- **Reachable: PROVEN, and the whole dataset is one request.**
  The SPA shell is 482 B, but the data is **baked into the JS bundle** — it is not fetched at runtime:
  ```
  curl -sL "https://royals-droppy.netlify.app/assets/index-Qiq7CF7a.js"   → HTTP 200, 4,205,002 B
  ```
  (The `royals-drops.herokuapp.com` URL in the bundle is a **credit line to an unrelated site**, not a
  backend — it returns 503, Heroku free dynos are retired. Do not chase it.)
- **Contents, extracted and counted:** the bundle defines `data_MB`, `data_Mob`, `data_MobMap`,
  `data_Map`, `data_item`, `data_GearStats` and writes them to `localStorage`.
  `data_MB` is the drop table: **495 mobs, 12,604 drop rows**, shape `mobid: ["itemid", ...]`
  with **real game ids** (`100100: ["4000019","2000000",...]`).
- **Rates: ABSENT.** Bare item-id arrays. The site's own footer states the source: *"The drop data
  used for this website was taken from the Monster Book data in the Data folder of the MapleRoyals
  client."* Monster Book data is a display list, which is exactly why it has no probabilities.
  Footer also states **"Game Version : 89"**.
- **Version: v89, not v83.** The owner's assumption that Royals is v83 does not hold for *this*
  tracker. Still pre-Big-Bang, so the id space lines up with ours without translation.
- **Coverage of our gap: 2 of 177 mobs (23 rows).**
- **Bulk access: one 4.2 MB GET, no rate limit, no scraping.** Cheapest bulk pull of any source here.
- **Verdict: trivially easy to ingest, but no rates and near-zero coverage of what we are missing.
  Useful only as a third opinion on drop membership for mobs we already have.**

## 4. maplestory.io — WZ mirror API

- **Reachable: PROVEN.** `https://maplestory.io/api/GMS/83/mob/2230101` → 200. **GMS 83 and 84 are
  both present and `isReady`** (`/api/wz` → 200, 63,934 B, 625 version entries).
- **Era: exactly ours.** Not a hazard.
- **Data classes:** it is a WZ mirror, so it carries exactly what WZ carries.
  | endpoint | result |
  |---|---|
  | `/api/GMS/83/mob` | 200, 1,597 mobs |
  | `/api/GMS/83/mob/{id}` | 200, keys `id, meta, name, description, framebooks, foundAt` — **no drops** |
  | `/api/GMS/83/mob/{id}/drops` | **404** |
  | `/api/GMS/83/item` | 200, 4.6 MB, 12,578 items; `?searchFor=` name search works |
  | `/api/GMS/83/item/2380000` | 200, `metaInfo.mob: 100100`, `monsterBook: true` — **card→mob works** |
  | `/api/GMS/83/npc/{id}` | 200, but `isShop:false` even on genuine shop NPCs; **no inventory** |
  | `/api/GMS/83/map/{id}` | 200, 97 KB, full mob + NPC spawn placements and `mobRate` (drops `mobTime`) |
  | `/api/wz/GMS/83/Etc/ItemMake.img/...` | 200, maker recipes readable node by node |
  | `/swagger/v1/swagger.json` | **404** — no machine-readable API doc exists |
- **Rates: ABSENT, and provably so.** The upstream source ([crrio/maplestory.io](https://github.com/crrio/maplestory.io))
  has a `MobDrop.cs` with a `decimal Probability` field, but it is `[JsonIgnore]`d and the WZ nodes it
  reads (`prob`) **do not exist in GMS v83 Mob.wz**: `/api/wz/GMS/83/Mob/0100100.img/info` has no
  reward or drop child at all. Confirmed independently below (§6).
- **Bulk access:** per-id JSON, plus large bulk lists for item/mob/npc/map. **No rate limiting
  observed** — 15 rapid sequential fetches all 200, Cloudflare-cached with `max-age=86400`.
- **ToS:** homepage disclaimer defers to Nexon's ToU. The GitHub repo has **no license** and its
  README says treat it as-is. No attribution rules published.
- **Verdict: the best general-purpose id/name/stat/spawn resolver. Fills 1 of 5 gaps (card→mob),
  and only with data our local WZ already has. Zero drop data.**

## 5. Rate calibration: DreamMS vs our existing table (measured)

Sampled 40 mobs we already have ≥5 drop rows for, scraped their DreamMS pages, matched on
`(mobid, itemid)`. Our chance is per-million; DreamMS renders percent.

```
our droppers loaded: 981, rows: 22,461
sampled mobs: 40, DreamMS rendered a table for 39
matched (mob,item) pairs: 920 | only-ours: 81 | only-DreamMS: 34

ratio ours / (dm_pct * 10,000):
  min 0.018 | p25 0.333 | median 0.333 | p75 0.333 | max 8.333
exact 1:1 matches: 0 / 920
```

**The relationship is a hard constant: `dreamms_pct = our_chance / 10,000 * 3`.** Allowing for
DreamMS's 2-decimal display rounding:

```
exact 3x (within display rounding): 803 / 920 (87.3%)
saturated at 100% (base >= 33.3%):   24 / 920
other / genuinely different:         93 / 920
```

Median ratio is 0.333 in **every** item class independently (equip n=415, use n=286, etc n=192,
cards n=27 at 0.314). It is not category-dependent and it is not noisy.

Worked example, mob 9300274:
| item | ours (per-million) | DreamMS | ratio |
|---|---|---|---|
| 2000000 | 20,000 (2%) | 6.00% | 3.0 |
| 4010000 | 9,000 (0.9%) | 2.70% | 3.0 |
| 2040705 | 300 (0.03%) | 0.09% | 3.0 |
| 4000001 | 600,000 (60%) | 100% | capped |

**Interpretation, and it is the important part:** DreamMS is running our ancestral v83 drop table with
a flat 3× rate multiplier. It is not an independent witness. Importing DreamMS rates for mobs we
already have would be importing our own numbers back with a ×3 error. To use a DreamMS rate:
`our_chance = round(dm_pct * 10000 / 3)`, and discard any row displaying 100% (saturated — the true
base is only known to be ≥33.3%).

## 6. Where drop tables actually live (the structural answer)

Rebirth95's own extraction script,
`porting-resources/reference-sources/Rebirth95-csharp/doc/miner.py::scrape_reward_data()`, reads
**`Etc.wz/Server/Reward.img`** and emits `mobId;item;itemId;prob` and `mobId;money;amount;prob`.
Edelstein's `MobRewardTemplate.cs` confirms the same node schema (`item`, `money`, `min`, `max`, `prob`).
So in the v95 KMS lineage, per-mob drop tables **with Nexon-authored probabilities are shipped inside
Etc.wz**.

**They are not in ours.** `HeavenMS-v83-upstream/wz/Etc.wz/` contains 25 `.img.xml` files and there is
**no `Server/` directory and no `Reward.img`** (contents: BlockReason, CashPackage, Category,
ChatBlockReason, Commodity, Curse, DeveloperNpc, EmotionEffect, ForbiddenName, Halloween, ItemMake,
MakeCharInfo, MapNeighbors, MedalQuestCategory, NPT_exception, NpcLocation, OXQuiz, QuestCategory,
RecommendSkill, ScanBlock, ScriptInfo, Swindle, Tips, VegaSpell). A filesystem-wide search of
`reference-sources/` for `Reward.img*` returned nothing.

This confirms the project's premise: GMS v83/v84 clients do not ship drop tables. It also points at
the only path to *authentic* rates — **a v95-era KMS/GMS `Etc.wz` containing `Server/Reward.img`**.
If one can be obtained, `miner.py` is a 25-line recipe against it and the rates are Nexon's, not a
private server's. Nothing else in this catalogue can make that claim.

## 7. Local sources (already on disk — no network needed)

| source | drop rows | mobs | rates? | other |
|---|---|---|---|---|
| `Rebirth95-csharp/data/sql/rebirth_world0-2021_01_06_12_05_19-dump.sql` (13.6 MB) | **20,017** | **935** | **YES — numeric `chance`** | 3,478 shopitems / 104 shops, 832 reactordrops, 24,564-row name table |
| `HeavenMS-v83-upstream/sql/db_drops.sql` (935 KB) | 23,036 | 969 | YES | + 352 reactordrops |
| `HeavenMS-v83-upstream/sql/db_database.sql` | — | — | — | 3,795 shopitems / 110 shops, 824 reactordrops, 6 global drops |
| `HeavenMS-v83-upstream/tools/MapleSkillMakerFetcher/lib/MakerData.sql` | — | — | — | **2,858 maker recipes** |
| `Rebirth95-csharp/doc/NpcShop_OutPut_SQL.sql` | — | — | — | 3,783 shopitems, generated from `NpcShop.img.xml` |

Rebirth95 schema (note `mobid`, not our `dropperid`):
```sql
CREATE TABLE rebirth.drop_data (id bigint NOT NULL, mobid integer, itemid integer,
    minimum_quantity integer, maximum_quantity integer, questid integer, chance integer);
```
Verified sample: `VALUES (11491, 9400013, 0, 697, 1020, 0, 200000)` — itemid 0 = mesos, chance per-million.
Chance distribution is plausible and un-tuned-looking (modes at 800, 300, 1000, 10000, 5000).

**Two caveats on Rebirth95:**
- Its `drop_data` contains **zero monster-card items** (nothing in 2380000–2390000). It cannot help the card gap.
- Its `monsterbook` table is *per-character card collections*, not a card→mob map.

**Nobody ships card→mob as authored data** — HeavenMS derives it
(`SELECT itemid, min(dropperid) FROM drop_data WHERE itemid BETWEEN 2380000 AND 2389999 GROUP BY itemid`),
Rebirth95 derives it arithmetically (`cardId - 2380000`). Both are inferior to reading
`Item.wz/Consume/0238.img` `info/mob` directly, which is exact for all 343 cards.

Dead ends locally, checked and confirmed empty: **Edelstein-v95.1-csharp** (reads everything from NX,
no drop/reactor/maker tables in any EF migration), **Henesys-v95-java** (drop loading is a commented-out
TODO; no `DropData` class exists), **LucianMS-v83** (schema DDL only, `INSERT INTO` count = 0),
**MapleResearch-v95-RE** (single README of HackShield bypass notes, purely client-side),
**awesome-maplestory** (link list; contains no wiki, no drop database, no data dump — the only "db"
in it is a BGM database).

## 8b. Open-source server SQL dumps on GitHub — the strongest drop source

All fetched via the GitHub contents API (`Accept: application/vnd.github.raw`), which is
byte-identical to raw. `raw.githubusercontent.com` **429s from this host**, so use the API form:
```
curl -sL -H "Accept: application/vnd.github.raw" \
  "https://api.github.com/repos/{owner}/{repo}/contents/{path}"
```

| source | proven URL path | bytes | drop rows / droppers | rate scale | **covers of our 177** |
|---|---|---|---|---|---|
| **`ahao0150/MapleStory-Server-079-vscode` (v79 TMS)** | `ms_20210813_234816.sql` | 2,026,405 | 13,557 / 904 | /1,000,000 | **26 mobs, 196 rows** |
| **`EricSoftTM/DEV` (v117 "Ascension")** | `SQL/Development 20141203 1907.sql` | 17,131,180 | 19,961 / 926 | /1,000,000 | **13 mobs, 38 rows** |
| **`nikitabuyevich/osm-database` (v83, OSM)** | `osm_data.sql.gz` | 61,944,585 gz | 9,886 / 498 | **/1,000,000,000** | **5 mobs, 52 rows** |
| `Fraysa/Destiny` (self-declared **GMS v83**) | `sql/MCDB.sql` | 24,713,742 | 10,323 / 468 | /1,000,000, hard-capped | **0** |
| `Minato1123/maplestory-drops` | `scripts/rawData/dropdata.json` | 3,715,525 | 22,157 / 1,004 | /1,000,000 | **0** — re-export of our own lineage |

**Union of the GitHub dumps over the 177: 31 mobs, 286 rows, all with numeric chance.**
```
1210111 4090000 8220013 9300013 9300051 9300052 9300053 9300062 9300063 9300081 9300082
9300145 9300175 9300287 9300387 9400316 9400317 9400318 9400559 9400584 9400618 9400619
9400620 9400621 9400622 9400657 9700001 9700002 9700006 9700011 9700014
```
Notably this is the only thing that reaches **8220013 Nibelung** (which DreamMS renders as an empty
page despite its API claiming 42 drops) and the **9700xxx Monster Carnival** mobs.

`EricSoftTM/DEV`, `zhuomingliang/OpenMS` and `ergothvs/Lucid2.0` all ship the *same* dump (identical
`AUTO_INCREMENT=45195` and identical first row) — pick whichever is cheapest to fetch. Schema is
exactly ours (`dropperid`, `itemid`, `minimum_quantity`, `maximum_quantity`, `questid`, `chance`),
and `itemid=0` means a meso drop with min/max as the range, same convention we use.

**Hazards, per source:**
- **v117 dump** is a live custom server: 38 rows have `chance > 1e6`, and some rows carry
  post-Big-Bang item ids (2510xxx cubes). **Filter every row by "does this itemid exist in our v84
  String.wz" before import**, and clamp the over-1e6 rows.
- **OSM** uses a **/1,000,000,000** denominator (its `reactor_drops` use 1e9 for 100%) and is
  additionally inflated by that server's own rates. Rescale before use.
- **v79 dump** is TMS/CMS flavour with Chinese text in comment columns, but its era is closer to v84
  than the v117 dump is.
- None of these are GMS-authentic rates. They are third-party tuning; label them as such.

**`Fraysa/Destiny`'s `MCDB.sql` earns a place for a different reason** — it adds almost no drop
coverage, but it is the only source shipping our other four gaps as normalised tables:
`monster_card_data` (343), `maker_recipes` (1,921) + `maker_creation_data` (832),
`shop_data` (97) + `shop_items` (3,366), `reactor_data` (419) + `reactor_events` (1,392).
Its rates are hard-capped at exactly 1e6 and an order of magnitude more granular than HeavenMS's
hand-tuned values, which makes it the best available *rate reference*.
It also **disagrees with our card→mob table on 7 entries** — worth adjudicating against
`0238.img`, which is authoritative:

| card | ours | MCDB |
|---|---|---|
| 2388011 | 9300105 | 9300119 |
| 2388017 | 6400006 | 8150000 |
| 2388026 | 6400008 | 8130100 |
| 2388043 | 8820001 | 8820000 |
| 2388068 | 3300006 | 3300007 |
| 2388069 | 3300007 | 3300006 |
| 2383045 | 6130102 | 6130103 |

**Confirmed dead ends in this class:** `Kaioru/Edelstein` (v95.1) and `Descended/Henesys` (v95) ship
no drop data at all — **v95 is not the goldmine it sounds like, nobody in that era shipped a drop
table**. `v3921358/MapleStory-2` sqlite and `Minato1123/maplestory-drops` are strict re-exports of
our own lineage (0 new mobs). `Hucaru/Valhalla` is v28, `Maple-Story/monster-drop` is a v62 GUI tool
with no data. HeavenMS/Cosmic forks (`Mercstory`, `SiriusMS`, `conchlin/boswell`, `MapleSolaxiaV2`)
are all the same upstream data. GitHub's **code search API is unusable** here (returns
`total_count: 510` with an empty `items` array), and grep.app is behind a bot wall — so this sweep is
repo-search + tree-scan, not exhaustive content search.

## 8. Sources checked and rejected

| source | status | why |
|---|---|---|
| **Southperry** (`southperry.net`) | **DEAD** | connection failure (curl exit 7 / HTTP 000), with and without browser UA |
| **MapleLegends library** (`maplelegends.com/lib/...`) | **UNREACHABLE** | HTTP 000 from this machine. Also v62 — wrong era, would be a hazard even if up |
| **MapleTip** (`mapletip.com`) | **UNUSABLE** | 200, but the site is now a React/Vite SPA; `/database/` returns a 416-byte shell. The 1.75 MB JS bundle contains **no API paths at all** — only `images.mapletip.com` asset URLs. No data surface found |
| **MapleStory Fandom** | reachable, **WRONG ERA** | 200 (450 KB) with a browser UA, 403 without. But it is current-version: its Blue Mushroom is **Lv 14 / HP 225 / EXP 24** vs our v83 **Lv 20 / HP 350 / EXP 32**. Post-Big-Bang stats and drops, no rates. **Hazard — do not mine for stats** |
| **StrategyWiki** | **BLOCKED** | 403 with and without browser UA |
| **royals.ms** (official) | reachable, no library | 200 on root but 1,460 B with no navigable links; `/library/` → 404 |

## 9. Ranked recommendation

**Gap: monster book card → mob (39 unmapped)**
1. **`wz/Item.wz/Consume/0238.img.xml` — LOCAL, exact, all 382 cards in our own v84 tree.**
   Parse `info/mob`. Done. Nothing else is needed or better.

**Gap: NPC shop inventories**
1. **`HeavenMS-v83-upstream/sql/db_database.sql`** — 3,795 rows / 110 shops, v83, LOCAL.
2. **`Rebirth95-csharp/doc/NpcShop_OutPut_SQL.sql`** — 3,783 rows, v95, LOCAL, and generated from
   `NpcShop.img.xml` so it is Nexon-derived rather than hand-tuned. Best for anything v84 added.
3. `Fraysa/Destiny` MCDB `shop_data`/`shop_items` (97 / 3,366) as a third opinion.
   No *website* carries shop inventories at all — Hidden Street, maplestory.io and DreamMS all lack them.

**Gap: maker recipes**
1. **`Etc.wz/ItemMake.img`, parsed locally** — Nexon data, already on disk, exact.
2. `HeavenMS/tools/MapleSkillMakerFetcher/lib/MakerData.sql` (2,858 rows) if a ready-made SQL is preferred.
3. `Fraysa/Destiny` MCDB `maker_recipes` (1,921) + `maker_creation_data` (832) as a normalised cross-check.
4. Hidden Street `/item-maker/*` only as a human-readable cross-check.

**Gap: reactor drops**
1. **`HeavenMS-v83-upstream`** — 824 + 352 rows, v83, LOCAL. Only real option.
2. `Rebirth95` 832 rows as a v95 supplement (note: nothing in Rebirth's own code ever reads that table).
3. `Fraysa/Destiny` MCDB `reactor_data` (419) + `reactor_events` (1,392), and `ahao0150` v79 (871 rows).

**Gap: quests beyond Quest.wz**
1. **Hidden Street via Wayback** — 1,150 quest pages, prerequisites, items needed, NPCs, rewards.
   The owner is right that this is the site's strength. Enumerate via CDX, fetch with the `2020id_` prefix, ~1 req/s.

**Gap: mob drop tables for the 177 listed mobs**
1. **Triage the list first.** 158 of 177 are event / PQ / Monster Carnival / boss-body-part mobs that
   correctly have no drops in GMS. Deciding which of those *should* stay empty is a bigger win than
   any scrape, and it is free.
2. **`ahao0150/MapleStory-Server-079-vscode` (v79)** — best single source: **26 of the 177, 196 rows,
   numeric chance /1e6, our exact schema.** Reaches Nibelung and the Monster Carnival mobs.
3. **`EricSoftTM/DEV` (v117)** — 13 mobs, 38 rows. Filter post-BB itemids and clamp `chance > 1e6`.
4. **`Rebirth95` (v95, LOCAL)** — 15 mobs, 40 rows, numeric chance. Rename `mobid → dropperid`.
5. **`nikitabuyevich/osm-database` (v83)** — 5 mobs, 52 rows. Rescale from /1e9 to /1e6.
6. **DreamMS HTML** — 6 mobs, 7 rows. Marginal, and divide the rate by 3 first (§5).
7. **MapleRoyals bundle** — 2 mobs, 23 rows, no rates. Marginal.

**Union across every source tested: 39 of 177 covered. 138 have no source anywhere.**

Separately worth knowing: measured against our *whole* `drop_data` (23,025 rows / 1,026 droppers)
rather than just the spawning-mob gap list, those four GitHub dumps carry **339 droppers we have no
row for at all**. Most are not currently spawning, so they are lower priority — but if the goal is
table completeness rather than fixing live mobs, that is the bigger number.

Do **not** bother with `Minato1123/maplestory-drops` or `v3921358/MapleStory-2` — both are
re-exports of our own lineage and add zero mobs.

## 10. Gaps with no viable source

- **Authentic GMS v84 drop *rates* for anything not already in our table.** They exist nowhere on the
  open web. Every numeric rate found is either our own lineage reflected back (DreamMS, ×3) or a
  private server's tuning. The only real path is obtaining a v95-era `Etc.wz` containing
  `Server/Reward.img` and mining it with `Rebirth95-csharp/doc/miner.py` — that is Nexon's data and
  the only thing that would meet an "as it was in GMS v84" bar.
- **Drop tables for the 138 residual mobs.** After triage most of these should turn out to be
  correct-as-empty; whatever survives triage has no source and will need to be authored or derived
  from sibling mobs by level and item class.
- **Any rate provenance claim.** Nothing in this catalogue outside `Reward.img` can be labelled
  GMS-authentic. Keep imported rates in a separate migration, tagged by source, so they stay
  separable from anything WZ-derived.
