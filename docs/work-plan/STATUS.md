# STATUS — v84 parity

**Last recomputed 2026-08-18.** This is the front page. Everything else in `docs/work-plan/` is
detail hanging off it.

The standard, in the owner's words: **"we are currently only doing v84 parity."** The operational
test is one question — **is it in the v84 data?** If yes the server should support it. If no, we do
not build it, however broken it looks.

---

## Where the project is

The v84 cutover happened on 2026-08-16 and an Evan plays on a real GMS v84 client. Since then the
work has been closing the gap between what v84 ships and what this server serves.

| | |
|---|---|
| v84 nodes added over v83 | **16,113** |
| of those, present in our `wz/` | 3,485 explicitly, **plus every single v84 image** — zero missing |
| genuine server-side data gaps | **395** |
| open work rows (all kinds) | **52**, of which **29 are in scope** |
| tickets | 67 — 42 done, 13 partial, 7 research-only, 3 superseded, 2 refused |

**The WZ merge is complete at the image level.** Not one v84-new map, equip, item, NPC or mob image
is missing from our tree. What remains is leaf-level and small.

---

## The four files that matter

| file | question it answers | how to refresh |
|---|---|---|
| **`V84-COVERAGE.md`** / `.tsv` | what v84 added, and whether we carry it | `python tools/playthrough/v84coverage.py` |
| **`V84-WORK-ROWS.tsv`** | every open unit of work, one row each, with its acceptance test and the evidence to use | by hand, as rows close |
| **`TICKET-LEDGER.tsv`** | the state of all 67 tickets | by hand |
| **`V84-OPEN-ITEMS.md`** | the narrative tracker — kept for its reasoning, no longer the source of truth | by hand |

Three sweeps are complete and should be **cited, not repeated**:

- `V84-QUEST-SWEEP.{md,tsv}` — all 198 v84-added quests, five checks each. 0 mechanical fixes, 7
  scripts written, 0 owner decisions needed.
- `V84-QUEST-DROPPER-SWEEP.{md,tsv}` — does the dropper live where the quest sends you. 12 flagged,
  12 cleared on the quest text, 0 rows changed.
- `V84-ITEM-SOURCE-SWEEP.{md,tsv}` — does every v84-new item have a source at all. **394 v84-new
  items** (the old tracker's "332" is unreproducible): 121 sourced, 127 actionable `PARITY_GAP`,
  37 container-only, 15 cash-only, 46 with no source v84 ever stated, 48 cosmetic hair. **Zero
  live blockers** — only 3 of the 273 unsourced rows are wanted by a quest without an expired end
  date, and all 3 were already settled. No new drop row is proposed, and none should be added
  from it.

---

## State by area

| area | state |
|---|---|
| **WZ merge** | Complete at image level. 395 leaf gaps remain, decomposed in `V84-COVERAGE.md`; the largest single one is 108 missing `lvmax` values. |
| **Evan, creation to job advancement** | Works. Creation, 2001, all four advancements, stats, SP scoping, dragon-on-arrival. |
| **Evan quest chain** | Walkable to level 68, then **hard-stops at quest 22583** — its mob lives on a map behind a portal we route past. Work rows R01/R02. |
| **Slumbering Dragon Island** | Unreachable. Both doors shut: the ferry needs `move_RitSDI`, the Frog House route needs only `enterSDI`. Rows R03, R46, R47. |
| **Evan skills** | 8 of 12 closed. Mounts are a 3-line fix plus three new `Evan.java` constants (R14); Magic Resistance is blocked on a client disassembly (R22); two are WONTFIX with reasons. |
| **Evan character stats** | **Closed.** Both defects the old tracker never recorded are fixed: an Evan now gains the magician's 6 HP / 18 MP per AP point (R41, `f7657c736`), and Evan and the 2000/2001 beginner jobs have their AP-reset floor (R42, `48a413961` + `a4f804f78`) - the floors turned out to be derivable. Job 2001 takes the beginner floor by owner decision. R43's claimed persisted stat damage does not exist in this database: charid 50 has no `monsterbook` rows at all, so `n = 0`. All that remains is deleting two dead `skills` rows for 20000012 (R43). |
| **Drops, cards, maker** | Closed. 214 mob-drop rows, 39/39 monster cards, 6/6 maker. Residue is seven stale `monstercarddata` rows (R20), which ship as a new changeSet. R19 is **withdrawn** - quest 8732 has no data in any `Quest.wz` archive, so its `UPDATE` would make item 4031405 permanently unlootable, and `156-evan-chain-drop-data.sql:186-187` had already rejected that row. R49's contradiction is resolved: `153`'s refusal was scoped to Crimson Sky, so the 8300007 rows stay - and there are **17** of them, not six. |
| **Cash shop** | Opens and works — the v84 mode-enum shift was the cause. One live protocol bug left: gachapon sends v83 mode bytes (R15), and the correct values are already on disk. |
| **Packets / protocol** | Login, field entry, combat and cash shop all fixed and version-gated. One unexplained client crash on map 40000 (R44). |
| **HD client** | SIBLING AGENT OWNS THIS. Loader builds and loads; appearance unproven, needs a client launch. |
| **Dragon equipment slots** | SIBLING AGENT OWNS THIS. |
| **WZ phase B tree** | Built at `D:\games\wz-stage\phaseB\tree`, 2.0 GB, **not installed**. 2,870 refused rows and 57 conflict images open. Owner call. |
| **Dual Blade** | **Not started, and deliberately not sized.** Owner: *"i want evan first, dual blade can be after."* It is a whole job — client route, skills, quest chain, creation — and belongs in its own project, not on this tracker. |

---

## What is NOT v84 parity, and is therefore not being built

Recorded so it stops being re-filed as work. All of these are real defects; none is a v84 gap.
Details and the known fix for each are in `V84-WORK-ROWS.tsv`, rows R24-R33.

- Chaos scrolls 2049103/104/112/113/114 burn the slot and apply nothing — **v83 legacy**, zero
  `add-list` rows.
- Potions 2022359-2022365 grant only their flat half — **v83 legacy**. (The old tracker said "zero
  stats"; that was wrong, they carry both a flat and a `*Rate` node and the flat one is read.)
- The six `BFSkill` items — **v83 legacy**. Only `spec/consumeOnPickup` is v84-new on them and we
  have it. The `BFSkill` value is an index 0-5 into a client table we do not have.
- Shadow Web's damage-over-time — **removed on purpose upstream** in the same commit as the MaxHP
  overhaul, and kept dead deliberately since. Not dead code to restore.
- Echo of Hero's +4% watk never reaching local stats — **v83 bug**, but genuinely 4 lines.
- Chairs all healing identically — **v83 legacy**. The old tracker's "several v84-new" is wrong;
  `Install/0301` has zero `add-list` rows.
- Packet-validator coverage (33/307) and the in-memory CLIENT_START_ERROR de-dup — **engineering
  debt with no parity consequence**.

Two more that are simply already correct: `spec/onlyPickup` (every affected item is already covered
by `consumeOnPickup` or `tradeBlock`), and the `incMaxHP`/`incMaxMP`/`incReqLevel` claim (Maker
already translates the first two and skips the third on purpose).

---

## Refusals worth keeping visible

Decisions, not gaps. Do not re-open these without new evidence.

- **Crimson Sky** (ticket 06) — v84 ships all 21 maps and no entrance anywhere in its own `Map.wz`.
  A server-side portal would be inert because the client never sends an enter packet for a portal it
  does not draw. Owner: *"lets focus on v84 for now."*
- **Ereve / Rien ferry portals** (ticket 37) — the "stranded passenger" trap is not real; three
  working exits exist. Adding the 6 dead portal names would create a ride-skip exploit.
- **Map 196000000** (ticket 46) — taking v84's `life` array verbatim would delete the Cafe PQ
  stage-5 NPC and mobs.
- **Hall of Fame static PlayerNPC rows** (ticket 53) — the feature is DB-driven here and every
  character in this database is a GM by design, so the auto-deploy guards are working correctly.
- **`summonIceWall` / `stopIceWall2`** (row R47) — writing them means inventing a mob count and
  coordinates. Mob 9300391 is placed in none of v84's 4,848 map images.
- **Iron Hook 4090000's drop table** (row R34) — v84's own `Etc.wz` has no `Server/Reward.img` and
  Nexon never shipped drop tables in any client version. Permanently unknowable.

---

## Standing constraints

- The owner plays on this server. **Never restart it, never kill it, never launch a client.**
- **Do not run maven** while sibling agents are active — they collide on `target/`.
- MySQL `C:\Program Files\MySQL\MySQL Server 9.4\bin\mysql.exe`, db `cosmic`, root/root, **SELECT
  only** from agents. Changes go through a new Liquibase changeSet.
- Read-only: `D:\games\MapleStory\`, `D:\games\MSv84\client\`, `D:\games\dreamms\`, and the pristine
  carve at `porting-resources/wz-data/v84/`.
- Never `git add -A`, never a bare `git stash` or `git reset`. Explicit pathspecs only.
- `USE_DEBUG_SHOW_RCVD_PACKET` toggles **live** via the GM5 `!showpackets` command. Check that
  before anyone schedules a restart for packet visibility.
- What may be cited as evidence, and for what, is in **`SOURCES.md`**. Read it before deriving a
  value.
