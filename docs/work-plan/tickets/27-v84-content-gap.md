# 27 — the measured v84 content gap

**What this is:** an audit, not a merge. It turns *"everything v84 had"* into a checklist with
counts, id ranges, and a data-vs-Java classification, so later tickets execute against numbers
instead of hope. Nothing under `wz/` was written. Nothing was merged.

**Status:** delivered — measurement complete. Every number below was taken by this ticket and
each instrument was pointed at a known answer before its output was believed.

**Snapshot pin.** All counts are against `HEAD = 845f91901`, taken `2026-08-16T18:23Z`.
This matters: two commits landed under `wz/` *during* the audit (`831e9d023` Evan's world,
`845f91901` ticket 26). Every WZ figure in this ticket was **re-measured after** those commits;
the pre-commit pass is discarded. Re-verify before executing if `wz/` has moved again.

Sources, all read-only:
- `D:\games\MapleStory\Server\porting-resources\wz-data\v84\` — stock v84, hash-verified genuine.
- `wz/` in this worktree — the server's XML-extracted tree (this is the "server" column).
- `docs/wz-baseline/` manifests — treated as **claims under test**, and two of them failed.

---

## Headline

**The v84 *data* gap is small and nearly closed.** Tickets 04/05/09/13/33 did most of the work
already. What remains is **20 whole missing images/directories, 1 missing map, 110 cash-shop rows,
and ~1,300 sub-node additions** — a day of merging, not a project.

**The expensive category is not v84 data at all.** It is **371 quests that declare a
`startscript`/`endscript` in `Check.img` and have no `.js` on disk**. Those quests exist in the
data, are offered by NPCs, and do nothing when clicked. That hole is pre-existing Cosmic, not a
v84 regression — but it is squarely inside *"everything v84 had"*, and it is the only item here
that costs real effort.

**Evan is not the gap.** Evan quest scripts are complete (49 declared, 49 present, 0 missing) and
the Evan map range is fully merged. Do not re-open it.

---

## Per-category gap table

Counts are id-level presence, v84 → server. "ABSENT" = present in stock v84, missing server-side.

| # | Category | v84 | server | **ABSENT** | id range of the gap | kind | size |
|---|---|---|---|---|---|---|---|
| 1 | Maps (`Map.wz/Map/*/*.img`) | 4,505 | 5,337 | **1** | `100030301` | data | trivial |
| 2 | Quests — `QuestInfo` | 3,015 | 3,016 | **0** | — | done | — |
| 3 | Quests — `Check` | 3,004 | 3,005 | **0** | — | done | — |
| 4 | Quests — `Act` | 3,021 | 3,022 | **0** | — | done | — |
| 5 | Quests — `Say` | 2,998 | 2,864 | **135** | `22000`–`22603` (Evan) | data, **client-only** | small |
| 6 | Mobs | 1,601 | 1,594 | **6** + 1 dir | see §Mobs | data | trivial |
| 7 | NPCs | 1,662 | 6,991 | **3** | `1022106`, `1022107`, `2030015` | data | trivial |
| 8 | Reactors | 425 | 424 | **3** | `1002008`, `2302006`, `2409000` | data | trivial |
| 9 | Skills (job imgs) | 89 | 87 | **2** | `9000.img`, `Dragon/` | data **+ Java** | small |
| 10 | Items (`Item.wz` ids) | 6,350 | 6,354 | **0** | — | done | — |
| 11 | Equips (`Character.wz`) | 7,342 | 7,346 | **0** | — | done | — |
| 12 | Cash shop — `Commodity` rows | 9,057 | 8,947 | **110** | index `8947`–`9056` | data | small |
| 13 | Cash shop — `CashPackage` | 446 | 435 | **11** | `9101608`, `9102282`–`9102294` | data | trivial |
| 14 | Cash shop — pet item data | — | — | **2** | `5000022`, `5000054` | data | trivial |
| 15 | `String.wz` names (all imgs) | — | — | **~167** | see §String | data, cosmetic | small |
| 16 | Mob `info/category` field | 1,168 mobs | 30 | **1,168** | all bands | data, **inert** | small |
| 17 | `Commodity/*/Bonus` field | 8,941 rows | 0 | **8,941** | all rows | data, **inert** | small |
| 18 | `Etc.wz/NPT_exception` | 2,142 | 2,089 | **53** | item ids | data | trivial |
| 19 | `Etc.wz/NpcLocation` | 1,662 | 1,695 | **42** | see §Etc | data | trivial |
| 20 | Map sub-nodes (portals/life) | — | — | **158** | see §Maps | data | small |
| 21 | Whole images/dirs absent | — | — | **20** | see §Root-level | data | small |

Rows 16 and 17 look enormous and are not. Both are a **single new field replicated across every
existing row** — v84 schema additions, not missing content. Neither is read by Cosmic's Java
(`grep "category"` and `grep '"Bonus"'` over `src/main/java` → zero hits). Merge them for client
correctness; nothing server-side changes.

---

## The 20 whole images/directories absent server-side

The complete list, verified by direct filesystem existence test:

```
Effect.wz/Direction4.img
Map.wz/Back/dragonDream.img
Map.wz/Map/Map1/100030301.img
Map.wz/Tile/DeepgrassySoil.img
Mob.wz/2220110.img          Crying Blue Mushroom
Mob.wz/2230112.img          Terrified Wild Boar
Mob.wz/9300388.img          Free Spirit
Mob.wz/9300391.img          Ice Wall
Mob.wz/9300393.img          Gentleman
Mob.wz/9300394.img          Delinquent Rudolph
Mob.wz/QuestCountGroup/9101004.img
Npc.wz/1022106.img          Christopher
Npc.wz/1022107.img          Perion Warning Post
Npc.wz/2030015.img          Hidden Rock
Quest.wz/PQuestSearch.img
Reactor.wz/1002008.img
Reactor.wz/2302006.img
Reactor.wz/2409000.img
Skill.wz/9000.img
Skill.wz/Dragon/            (2200, 2210-2218 — Evan dragon; ANOTHER AGENT OWNS THIS)
```

Names resolved from v84 `String.wz`. All are data merges except `Skill.wz/9000.img` (see below)
and `Quest.wz/PQuestSearch.img`, which nothing in `src/main/java` reads — merging it is inert
server-side and only affects the client's PQ-search UI.

---

## Events `[owner called this out explicitly]`

**Event *data* is not the gap. Event *scheduling* is.**

Data side — nothing meaningful missing:
- All 246 v84 Monster Carnival maps (`980xxxxxx`) are present server-side. **Zero absent.**
- The only v84 map absent anywhere is `100030301`.
- Missing event-adjacent data is 15 Monster Carnival map *names* in `String.wz/Map.img`
  (`980000700`–`980000704`, `980000800`–`980000804`, `980000900`–`980000904`) plus
  `String.wz/Map.img/event/683010000`. Cosmetic.

Code side — measured, and this is where the hole is:
- **108 scripts in `scripts/event/`**, every one auto-registered. `Channel.getEvents()`
  (`src/main/java/net/server/channel/Channel.java:452-464`) directory-scans `scripts/event` and
  registers every `.js` it finds. There is no whitelist and no config gate, so "present but not
  registered" is an empty set by construction.
- Binding is name-based reflection only — `EventInstanceManager.invokeScriptFunction` →
  `Invocable.invokeFunction` (`src/main/java/scripting/event/EventInstanceManager.java:~227`).
  No per-event Java class exists or is needed.
- 73 of the 108 are real instance scripts (PQs, boss battles); 35 are timer/transport shells.
- All 18 PQs have matching `scripts/npc/` entry points.
- **No seasonal or rotating event is live.** `scripts/event/2xEvent.js` is the only date-scheduled
  event and its entire `init()` scheduling block is commented out (lines 34-45). Christmas and
  Halloween references in Java are BGM filenames and item-id constants, not date gates. A grep of
  all of `scripts/` for date-comparison gating returns nothing.

**Classification: Java/script, not data.** Seasonal events need a scheduler and date-gated
activation written; the maps and NPCs they would run on are already in the tree.

---

## Quests

**Ticket 33's load-bearing claim is CONFIRMED, independently.** All v84 quest ids are present in
`QuestInfo`, `Check`, and `Act` — `v84_ABSENT = 0` on all three. The 135 Evan `22xxx` ids are
present in all three. Nothing from ticket 09's non-Evan set is missing.

**`Say.img` is the one image still short: 135 ids absent, all `22xxx`.** But it is **client-only**
and should not be treated as a server defect: `Quest.java:116-118` opens `QuestInfo.img`,
`Act.img` and `Check.img` and nothing else — `grep -rn "Say.img" src/main/java` returns **zero
matches**, a fact this tree's own tests already assert (`V84EvanQuestDataTest.java:44`). Merge it
only if `wz/` is also the source for a client build.

**The real quest hole — 371 uncoded scripted quests.** Measured directly from `Check.img`:

```
quests declaring a startscript/endscript : 660
scripts/quest/*.js on disk               : 308
DECLARE a script but have NO .js         : 371     <-- these are broken in-game
have a .js but declare no script         :  19     (dead files)
```

Id-range histogram of the 371: `10xxx` 180, `99xxx` 44, `28xxx` 39, `29xxx` 33, `98xxx` 24,
`96xxx` 16, `97xxx` 13, `20xxx`/`19xxx` 6 each, rest ≤3.

**Evan is clean:** 135 Evan quests, 49 declare a script, 49 `.js` present, **0 missing**. Earlier
framing of "49 of 135" was misleading — the other 86 run entirely off WZ data and need no script.

A missing script is only expensive when `Check.img` declares one; otherwise `quest.start()` /
`quest.complete()` apply `Act.img` rewards with no script involved
(`QuestActionHandler.java:88,102`). That is why the number that matters is 371, not 2,707.

**Silently-dropped quest types (Java gap).** `Quest.java:512-584` and `586-633` switch on the
requirement/action enums with a `default` branch whose error log is **commented out**
(`Quest.java:580`, `Quest.java:629`) — unhandled types vanish with no warning:
- requirement `DAY_BY_DAY`
- actions `YES`, `NO`, `NPC`, `MIN_LEVEL`, `NORMAL_AUTO_START`, `ZERO`

Un-commenting those two log lines is a one-line-each change that would tell us how many live
quests actually hit them. Do that before estimating the fix.

---

## Cash shop `[owner called this out explicitly]`

**Data gap: 110 catalogue rows + 11 packages + 2 pet items. Java gap: none.**

- `Commodity.img` index nodes `8947`–`9056` are absent — **110 rows**, contiguous.
- Those 110 rows sell **103 distinct item ids**, of which **101 already have full item data in the
  server tree**. Only **2 are missing data**: `5000022`, `5000054` (both pets).
- Item-id bands of the new stock: `40xxxxx` 31, `20xxxxx` 18, `91xxxxx` 11, `51xxxxx` 9,
  `50xxxxx` 8, `10xxxxx` 7, remainder ≤5 each.
- `CashPackage.img`: 11 absent — `9101608`, `9102282`, `9102283`, `9102287`–`9102294`.
- Distinct `SN` *values* absent = 116, slightly more than the 110 new rows, because v84 also
  re-pointed ~6 pre-existing rows to different SNs. Merge by index node, not by SN.

**No Java work is required to sell these.** `CashItemFactory.loadAllCashItems()`
(`src/main/java/server/CashShop.java:238-276`) reads `Etc.wz/Commodity.img` and `CashPackage.img`
generically, with no hardcoded blocklist and no DB catalogue table. The only filter is the WZ
`OnSale` flag, applied at purchase time (`CashOperationHandler.java:486-493`). 13 of the 14
`CASHSHOP_OPERATION` sub-actions are implemented; expiration and `SpecialCashItem` are both fully
wired. Merge the data and the items appear.

Two caveats worth a line in a later ticket, neither blocking:
- Most of the catalogue is `OnSale = 0` (2,010 on-sale vs 6,935 not, in the current tree). A
  "cash shop is empty" complaint is far more likely to be `OnSale` than a missing merge.
- Gift, name-change and world-transfer hardcode `NX_PREPAID` instead of reading the chosen
  currency (`CashOperationHandler.java` 0x04 / 0x2E / 0x31). Inconsistent, not broken.

---

## Items and equips

**Id-level gap is zero, and that result is real** — but it is also the most misread number here,
so state it precisely: *every v84 item and equip id already exists in the server tree.* What v84
added on top is **sub-node depth inside images that already existed**. The `add-list` manifest's
"391 Item copy roots / 442 Character copy roots" are paths like
`Character.wz/00002000.img/Awakening` — Evan dragon animation states added to an existing image,
not new items.

Tested every one of the 16,113 add-list copy roots against the server tree:

```
PRESENT   4,716        ABSENT  11,056        whole-image/dir roots  341 (321 present, 20 absent)
```

ABSENT by archive: `Etc.wz` 9,168 · `Mob.wz` 1,179 · `Map.wz` 158 · `Character.wz` 144 ·
`String.wz` 139 · `Quest.wz` 138 · `UI.wz` 47 · `Sound.wz` 38 · `Npc.wz` 21 · `Effect.wz` 21 ·
`Skill.wz` 3.

Of the 9,168 `Etc.wz` rows, **8,941 are the single `Bonus` field** added to every pre-existing
commodity row, and 110 are the genuinely new rows. Of the 1,179 `Mob.wz` rows, **1,168 are
`info/category`** on 1,168 distinct existing mobs. Read those two lines before quoting any
five-figure number from this audit.

37 equip *names* are absent from `String.wz/Eqp.img` (e.g. `1002655` "Versal Maro"), but the
corresponding items have no data in **v84 either** — they are unreleased-item name stubs. No
gameplay impact. Do not spend a ticket on them.

---

## Maps

- **1 v84 map absent: `100030301`.** The Evan `9000xxx` range is fully merged (20 v84, 20 server)
  — `831e9d023` landed it during this audit. Do not duplicate that work.
- **833 maps exist server-side that v84 deleted.** 810 of them (97%) are the `970xxxxxx` Boss Rush
  band, which Cosmic implements. The `97004` block runs `970040100`–`970042717`, 486 maps.
- The other 23 server-only maps, verbatim: `000000001-3`, `000020001`, `000040001-2`, `000050001`,
  `000060000-1`, `001000004-6`, `001010001-4`, `001020001`, `109090001-4`, `777777777`, `925020610`.
  That is the one deleted Dojo floor (`925020610`), the `109090xxx` cluster, and custom content.
- **158 map sub-node additions**: 36 are portal edits on existing maps (`portal/NN`,
  `portal/NN/script`, `horizontalImpact`), 122 are `life/NN` spawn additions plus
  `Map.wz/Effect.img/evan` and `Map.wz/Back/login.img/back/34` (the v84 login screen).

### Corrections preserved — do not let these regress `[FACT-measured]`

- **Monster Carnival was never deleted.** v84 ships 246 `980xxxxxx` maps and the server has all of
  them; server-only `980` count is **0**. Re-confirmed by this ticket independently.
- **`MapId.BOSS_RUSH_MAX` is six short.** `src/main/java/constants/id/MapId.java:219` says
  `970042711`; the real block ends `970042717`. Confirmed against the measured block max.
- **maplestory.io's map LIST endpoint lies** — it is built from `String.wz` names and reports
  deleted maps as present. Only the DETAIL endpoint is authoritative. No figure in this ticket
  came from that API; everything here is from local hash-verified WZ.

---

## String.wz name gaps

| image | v84 | server | ABSENT |
|---|---|---|---|
| `MonsterBook.img` | 384 | 344 | **41** |
| `Eqp.img` (depth 3) | 7,270 | 7,298 | **37** (all unreleased stubs) |
| `Consume.img` | 2,464 | 2,459 | **33** |
| `Cash.img` | 521 | 508 | **23** |
| `Mob.img` | 1,638 | 1,620 | **18** |
| `Skill.img` | 717 | 710 | **10** |
| `Pet.img` | 58 | 55 | **3** |
| `Ins.img` | 261 | 302 | **2** |
| `Map.img` (depth 2) | 4,504 | 4,469 | **40** |

The 40 map names: `100030301`, `100030320`, `683010000`, `910510300`, `922231001`,
`923020000`–`923020190` (14), `924900000`–`924900500` (6), and the 15 Monster Carnival names
listed under §Events. Note `100030320` — the map image was merged by `831e9d023` but its **name
was not**, along with 14 `ToolTipHelp` entries for the same `1000303xx` cluster. Worth telling
whoever owns that merge.

`String.wz/MonsterBook.img` matters more than the rest: Cosmic implements Monster Book (9 files in
`src/main/java` reference it), so 41 missing entries are visibly blank in-game.

---

## Skills

Only two root nodes absent, and they split cleanly:

- **`Skill.wz/Dragon/`** (2200, 2210–2218) — Evan's dragon. **Another agent owns this.** Not this
  ticket's to schedule.
- **`Skill.wz/9000.img`** — GM/admin skills `90000000`, `90001001`–`90001006`, plus 10 matching
  absent `String.wz/Skill.img` entries (`9000`, `90000000`, `90001001`–`90001006`, `9101006`,
  `9101007`). **This one needs Java**: merging the data gives the client icons and nothing else;
  the effects have to be implemented server-side to do anything. Small, and skippable — GM skills
  are not player-facing content.

All 10 Evan job images (`2200`, `2210`–`2218`) are already present server-side.

---

## What needs Java, not a merge — the expensive column

Ranked by cost. This is the list people underestimate.

1. **371 quests that declare a script and have none.** Largest content hole in the tree. Each
   needs a hand-written `.js`. Not a v84 regression; pre-existing Cosmic. Est. large — do not
   attempt as one ticket, slice by id band (`10xxx` alone is 180).
2. **Seasonal / rotating events.** Zero are live. Needs a date-gated scheduler and activation
   logic; the maps, NPCs and 108 scripts already exist. Est. medium.
3. **Silently-dropped quest requirement/action types** (`DAY_BY_DAY`; `YES`/`NO`/`NPC`/
   `MIN_LEVEL`/`NORMAL_AUTO_START`/`ZERO`). Un-comment `Quest.java:580,629` first to size it.
   Est. small to measure, unknown to fix.
4. **GM skills in `Skill.wz/9000.img`.** Data + effects. Est. small, low value.
5. **`PQuestSearch.img`.** Nothing reads it. Est. small, cosmetic, client-side only.

Everything else in this ticket is a WZ merge with no code attached.

---

## How each instrument was proved — and the four that failed first

Every counter below was pointed at a case whose answer was already known before its output was
believed. Four were wrong on first run. That is the point of the section.

**Proved good:**
- *Depth-1 XML extraction.* Indent histograms confirm exactly one nesting level at 2 spaces in
  every image parsed (`QuestInfo` 3,016 at indent 2, one root at 0). No image uses ragged indent.
- *Known-answer test.* Ticket 33 says 135 Evan quest ids were merged into three images. The
  extractor reports exactly 135 present in `QuestInfo`/`Check`/`Act` and exactly 135 absent from
  `Say` — an independent fact reproduced to the id.
- *Negative control.* Self-diffing a list against itself returns 0. Null results
  (`String/Map.img`, `Morph`, `TamingMob`, `ToolTipHelp` at depth 1) come back 0 both directions.
- *Boss Rush cross-check.* The measured block max `970042717` reproduces the known-wrong constant
  at `MapId.java:219` being 6 short, without being told where to look.

**Failed first, then fixed:**

1. **Map extractor counted `AreaCode.img` sub-properties as maps.** A depth-2 dump of `Map.wz/Map`
   picked up `AreaCode.img`'s children (`00`, `10`, `11`, …) and reported them as 30 missing maps.
   The real figure is **1**. Fixed by constraining ids to 6–9 digits. *Any map count of ~31 from
   an earlier pass is void.*
2. **A confirmation grep that could not discriminate.** `grep -c '"22000"' Say.img.xml` returned
   `1` for an id that is genuinely absent — it was matching `#p22000#` NPC references *inside
   dialogue text*. Re-run as an anchored depth-1 match (`^  <imgdir name="22000">`) it returns 0,
   while the same probe returns 1 against `QuestInfo.img.xml` where the id really is present. The
   135 figure survives only because of the corrected probe.
3. **Asymmetric node-type matching manufactured four phantom gaps.** The server-side extractor
   matched only `<imgdir>` while the v84 side matched every node type, so images built from
   `<string>` entries reported `srv=0`: `QuestCategory` 52 "missing", `Curse` 572, `ForbiddenName`
   179, `ScriptInfo` 264 — **805 phantom gaps**. Matching any `name=` element at depth 1 drops all
   four to a true gap of 0, 0, 0 and 5. *Do not quote those four from any earlier pass.*
4. **The first add-list checker reported 100% absent.** It called
   `Character.wz/00002000.img/Awakening` absent while a direct grep found it at line 3. Rewritten
   as one indent-aware pass per file; the same case then reports present.

**Known bias, stated deliberately:** the add-list checker matches `indent:name` per file, not the
full path. A node can therefore be called PRESENT because a same-named node exists at the same
depth elsewhere in that image. This biases toward **under-reporting** the gap, which is the
direction this audit was asked to err in. ABSENT results carry no such bias and are trustworthy.

**Manifest claims that failed under test:** `add-list/*.txt` files carry a 3-line comment header
**and a blank separator line**, so `wc -l` over-reports by 4 per file. `Item.txt` is 395 raw lines
= 3 comments + 1 blank + **391 real copy roots**, which reconciles exactly with `SUMMARY.md`'s
391. Stripping only `^#` gives 392 and is still wrong by one. This is the same class of error as
the 64-line protect-list discrepancy — and note it took two attempts here to land on the right
number, which is precisely why the reconciliation against an independent count was done.
Never `wc -l` a manifest in this repo; strip `^#` *and* `^$`.

---

## What I could NOT measure, and why

Stated plainly so no one mistakes silence for zero.

1. **Images v84 *edited* rather than added.** This is the largest blind spot. A presence diff
   cannot see a changed value. The manifest *claims* `Mob.wz` 1,173 edited images, `Map.wz` 128,
   `Character.wz` 43, `Item.wz` 30, `String.wz` 14, `Etc.wz` 12, `Npc.wz` 12, `Skill.wz` 6,
   `Quest.wz` 7. I did not verify these and cannot: the v84 side is binary WZ and the server side
   is extracted XML, so no byte comparison is possible, and `WzMerge` has no XML-vs-WZ value diff.
   **If v84 rebalanced mob stats, this audit would not see it.** Sizing that needs a new tool.
2. **The 6,248 live-edited images** identified in ticket 23 phase A. A presence diff is
   structurally blind to them by definition. Untouched here.
3. **`List.wz`** — MapleLib cannot open it in any tree ("WZ header FStart is outside the file").
   Zero measurement, in either direction.
4. **`Sound.wz/BgmGL.img`** — fails to parse in v83, v84 *and* live. Any Sound.wz figure is
   suspect; I did not report one.
5. **`UI.wz` and `Effect.wz` beyond root level.** 47 and 21 add-list rows respectively come back
   ABSENT, but I did not classify what they are or whether Cosmic reads them.
6. **Whether any event or PQ is actually reachable in-game.** Everything here is static
   presence. No server was started, no packet was traced — correctly, per this ticket's rules.
7. **Client-side archives** (`D:\games\MSv84\client\`, `D:\games\MapleStory\`). Out of bounds by
   rule; the "server" column is `wz/` only.
8. **Whether the 371 uncoded quests were ever functional in real GMS v84.** I measured that
   Cosmic declares and does not implement them. I did not verify GMS shipped working versions.

---

## Recommended execution order

Cheapest and most certain first. Steps 1–4 are pure data and could be one merge ticket.

1. **Wait for the Evan/`wz/` agent to finish.** Two commits landed mid-audit. Re-run the
   measurement scripts before merging anything — they are cheap and the tree is moving.
2. **One merge ticket for the 20 absent images/dirs + map `100030301` + 158 map sub-nodes**,
   excluding `Skill.wz/Dragon/` (another agent owns it). Small, mechanical, high confidence.
3. **Cash shop data merge**: 110 `Commodity` rows, 11 `CashPackage` rows, 2 pet items. No Java.
   This is the highest owner-visible value per hour in the whole list — the code already works.
4. **String.wz names**: `MonsterBook` 41 first (visibly blank in a feature Cosmic implements),
   then `Map.img` 40 incl. the `1000303xx` names the Evan merge left behind, then the rest.
   Skip the 37 `Eqp` stubs.
5. **Schema fields** `Mob info/category` and `Commodity Bonus` — merge for client correctness,
   expect no server-side change. Verify by asserting nothing moves.
6. **`Say.img` 135 Evan entries** — only if `wz/` feeds a client build. The server never opens it.
7. **Un-comment `Quest.java:580,629`**, run the server, and count what the dropped-type logs
   actually say. This is a two-line change that sizes item 3 of the Java list for free.
8. **Seasonal event scheduler.** Needs design; the content is already on disk.
9. **The 371 uncoded quests**, sliced by id band. Largest, do last, do not attempt whole.

---

## Files this ticket produced

Only this document. Nothing under `wz/`, `docs/wz-baseline/`, or `src/` was written, and
`WzMerge.exe` was used read-only (`dump` only — never `merge`, `xml`, or `guard`).
