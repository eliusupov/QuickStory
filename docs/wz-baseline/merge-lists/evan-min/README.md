# `evan-min` — the smallest install that makes Evan work

**109 rows across 5 `.wz` files. 0 refused, 0 denied, 0 forced, 0 nodes removed, 0 values
overwritten.** Staged and verified at `D:\games\MapleStory\Server\wz-merge\evan-min\`.

Built for ticket 10m. The composed install is **1,750 rows across 13 files**; this is 6% of it,
and it is the subset that Evan alone requires. Everything excluded is listed below by file, so a
follow-up increment can layer the rest on without re-deriving anything.

---

## The list

| file | rows | images the client actually re-reads | growth |
|---|---:|---|---:|
| `Skill.wz` | 12 | **21 wholly new images**, 0 existing images changed | +37,530,290 |
| `String.wz` | 70 | `Skill.img` | +43,896 |
| `Character.wz` | 20 | `00002000.img` | +7,966 |
| `UI.wz` | 3 | `Login.img`, `UIWindow.img` | +406,647 |
| `Etc.wz` | 4 | `MakeCharInfo.img` | +920 |
| **total** | **109** | **5 existing images + 21 new ones** | **+37,989,719** |

Five existing images in the entire 1.86 GiB client are re-serialised. Every other image is
memcpy'd verbatim by MapleLib and its directory-entry checksum is unchanged.

## Which files Evan does NOT need — the bisect datapoint

Of the composed set's thirteen files, **eight are not needed for Evan at all**:

| file | composed rows | what it is |
|---|---:|---|
| `Item.wz` | 391 | v84 cosmetics + consumables (ticket 04) |
| `Map.wz` | 133 | Crimson Sky / Neo City / misc areas (06, 07, 08) |
| `Mob.wz` | 28 | new mobs for those areas |
| `Morph.wz` | 25 | mounts (05) |
| `Npc.wz` | 15 | new NPCs |
| `Quest.wz` | 252 | the 63 v84 quests (09) |
| `Reactor.wz` | 3 | reactors for the new areas |
| `Sound.wz` | 24 | **28 of these are Evan skill sounds** — see "what Evan will not do" |
| | **871** | |

If `evan-min` installs and the client runs, those eight files are the remaining suspects for the
`_com_error` crash, along with the 771 rows excluded from the five files below. If `evan-min`
crashes, the search space is five files and 109 rows — and `Character.wz` can be dropped first
for free (see below).

## What was excluded from the five files this list DOES touch

Layer these back in a later increment; nothing here needs re-deriving.

- **`Character.wz` — 234 of 254 excluded.** Kept: the 20 `00002000.img/<action>` rows. Excluded:
  ticket 04's 226 cosmetic equip images (`Cap`, `Coat`, `Hair`, `Face`, `Longcoat`, `Weapon`, …)
  and ticket 05's 8 mount sprites. None is referenced by any Evan skill.
- **`String.wz` — 510 of 580 excluded.** Kept: the 70 `Skill.img` Evan rows. Excluded: 125 new map
  names, the `Eqp.img` cosmetic names, `Consume`/`Etc`/`Ins`/`Cash` names, `Mob.img`, `Npc.img`,
  and **all 41 force roots**. See "zero forces" below — this is deliberate and it is the single
  most important difference between this list and the composed one.
- **`Skill.wz` — 27 of 39 excluded.** Kept: `2001.img`, `2200`–`2218.img`, `Dragon/`. Excluded:
  ticket 05's 27 v84 mount skills.
- **`Etc.wz` — 0 excluded.** The composed `Etc.wz` list is only the 4 Evan `MakeCharInfo` rows.
- **`UI.wz` — this list is one row LARGER than composed.** Composed has ticket 10's `SkillEx` +
  `SkillMacroEx`; `evan-min` adds `Login.img/CharSelect/evan`. Rationale in `UI.paths.txt`.
  The other 58 `UI.wz` add-list roots stay out.

## Zero forces — and why that matters more than it looks

`--force` is not passed on any run in this ticket. The composed `String.wz` merge passes
**41 force roots**, and `CRASH-ANALYSIS.md` rank 1 records what that cost: `String.wz` was the
**only file in the whole composed set that removed live data** — 18 `desc` nodes deleted, because
those force roots sit at item-id level where v84's node carries only `name`, so forcing the id
replaced the subtree and took the live `desc` with it.

`evan-min` inherits none of that. Every one of its 109 rows is a name that does not exist in the
target, so the additive-only gate alone is sufficient and nothing can be replaced.

**What that costs, stated plainly:** Evan's dragon equips (`Eqp/Dragon`, 12 ids) and Mir plus its
saddles (`Eqp/Taming/190204x`, `191203x`, 6 ids) keep their live names, which are the literal
string `MISSING NAME`. That is cosmetic text in the equip window. It is repairable later with a
**narrowed** force root — `<id>/name` rather than `<id>` — which is what the composed list should
have used and did not.

## Deny-list

`--deny docs\wz-baseline\merge-lists\COLLISION-DENY.txt` (188 roots) on every run, dry runs
included. None of the 109 rows is at or beneath a deny root; `denied 0` on all ten runs.

## Positional-array check (procedure 4.4)

No row targets an array. Checked by dumping every parent:

- `String.wz/Skill.img` — 2,000+ gapped integer skill ids, not a consecutive run.
- `Character.wz/00002000.img` — children are action names (`walk1`, `stand1`, `info`), not integers.
- `Etc.wz/MakeCharInfo.img` and `/Name` — all children named.
- `UI.wz/UIWindow.img`, `UI.wz/Login.img/CharSelect` — all children named.
- `Skill.wz` root — the 12 rows are whole new images / one whole new directory.

## Verification actually run

Base for every merge is the **pristine v83 backup**, `_backup\client-v83-EzorsiaV2-2026-08-15\`
— *not* the current live client, which another ticket overwrote mid-run (see "State of the live
client" below).

1. **Dry run**, all five, `--deny`: `added {4,3,70,12,20} refused 0`, exit 0 each.
2. **Real merge**, all five, `--deny --live <backup>\<Name>.wz`: same counts, `verified OK`,
   exit 0 each. Post-write content digests: `0 drifted`, `0 requested paths missing`,
   `0 unparseable` (7,207 images parsed in `Character.wz`, 97 in `Skill.wz`).
3. **Census** (`docs\wz-baseline\tool-census\`), backup vs merged, full node walk:

   ```
   Etc.wz        NEW=0  CHANGED=1  REMOVEDimg=0  onlyB-kinds=0  added=84   REMOVED=0  VALCHG=0
   UI.wz         NEW=0  CHANGED=2  REMOVEDimg=0  onlyB-kinds=0  added=557  REMOVED=0  VALCHG=0
   String.wz     NEW=0  CHANGED=1  REMOVEDimg=0  onlyB-kinds=0  added=879  REMOVED=0  VALCHG=0
   Skill.wz      NEW=21 CHANGED=0  REMOVEDimg=0  onlyB-kinds=1  added=0    REMOVED=0  VALCHG=0
   Character.wz  NEW=0  CHANGED=1  REMOVEDimg=0  onlyB-kinds=0  added=653  REMOVED=0  VALCHG=0
   ```

   **`REMOVED=0` and `VALCHG=0` on every file** — that is the zero-removal / zero-overwrite proof.

   The one `onlyB-kind` is `canvas:dim<=2048`, first at
   `Skill.wz/2218.img/skill/22181002/effect/1`. Investigated: the canvases are 1193×319 up to
   1198×584 — an ordinary PNG that happens to be the first thing in *`Skill.wz`* over 1024 px. The
   live client already ships a **2497×343** canvas at `Map.wz/Back/grassySoil.img/7`, so this is a
   new bucket within one file, not a shape the client has no branch for. No new pixel format, no
   `_inlink`, no UOL escape.
4. **Gate refire** (procedure 6.3) — re-merged each output against itself:
   `added 0, refused {4,3,70,12,20}`, **exit 5** on all five. The gate works.
5. **Output hashes** (procedure 6.4) — record these; the merge is deterministic, so a re-run from
   the same backup must reproduce them exactly:

   ```
   Etc.wz         1,803,928  10F19943398838E821B43890074EC1F0BEADBF41CEDC1EE3C8940576EC65C89A
   UI.wz         28,721,928  755A9551540EF2B928D7D98EDD9ABE539D2ED349A7B74CC29EE8C19E5A834FB0
   String.wz      3,605,181  46AEFAC5904EBEC75CB2D927275BE50EF22352702C8564BF866F9AE3B02655E9
   Skill.wz     114,035,663  A334579124C8928949D7A6E3C3556B4440EB6800255D0F6546FF10A63AA510AD
   Character.wz 206,275,297  3FFA06F6C24587F8C2ECF5E8FCAEFFB8AE8E8764C42158F53D6BF0A38E2C77D2
   ```

6. **Server XML** — nothing to do. `wz/Skill.wz/{2001,2200,2210..2218}.img.xml` and the Evan
   `String`/`Etc`/`UI` splices are already committed by ticket 10, `wz/Character.wz/00002000.img.xml`
   already carries `fireCircle` from ticket 04, and `git status wz/` is clean. The 20
   `Character.wz` action rows are client-render data the server never reads anyway.

## Install order — and the one free bisect step

**First restore all 18 `.wz` from the backup — the live client is currently raw v84 and will not
run. See "State of the live client" at the bottom of this file; it is not optional reading.**

Then, client closed, copy from `D:\games\MapleStory\Server\wz-merge\evan-min\`:

```
Etc.wz  ->  UI.wz  ->  String.wz  ->  Skill.wz  ->  Character.wz
```

**`Character.wz` last, on purpose.** Evan is created, renders, walks, and uses all of job 2001's
own skills (`20011xxx`, whose actions are `alert2` / `bamboo` / `pyramid`) without it — the live
client already has those. `Character.wz` is only needed from the first job advance onward, when
`fireCircle` and 16 other body actions start being named. So if the five-file install fails, retest
with the first four: that halves the search space at the cost of one file copy, not one merge.

## Extending this list

Row files are plain manifests with the same shape as everything else under `merge-lists\`. To ship
the next increment, add a new directory beside this one with the rows you want on top; do **not**
edit these files. `evan-min` is what a rollback returns to, so it has to stay exactly what was
proven here. The eight untouched files above are each a standalone increment with no dependency on
any other — that is what makes them the cheap next steps.

## State of the live client — STOP AND READ THIS BEFORE INSTALLING ANYTHING

Nothing in this ticket wrote to `D:\games\MapleStory\`. Everything below was *observed* there
while this ticket ran, by two SHA-256 sweeps of all 18 `.wz` against the backup.

**Sweep 1, at the start of the ticket: all 18 files MATCH the backup.** Pristine v83.

**Sweep 2, ~40 minutes later: 16 of 18 DIFFER — and the live client is now RAW v84.** Every
`.wz` in `D:\games\MapleStory\` is byte-identical to `porting-resources\wz-data\v84\<Name>.wz`,
timestamps reset to `2010-03-29`. Verified on `Base, Effect, TamingMob, Character, Skill, String,
UI, Etc` — including `Base.wz`, `Effect.wz` and `TamingMob.wz`, which **no ticket in this project
touches at all** and which have 0-row add-lists.

That is not a merge. **Those files are `patchVersion=84`; the client is a v83 client.** Procedure
section 8 exists specifically to forbid this. The client will not mount them.

An intermediate state was also observed at 13:24–13:25: thirteen files byte-identical to
`D:\games\MapleStory\Server\wz-merge\03k\` (the full re-composed set, 1,750 rows, still merged
with 41 `String.wz` forces). That state was then itself overwritten by the raw v84 copy.

**Recovery, and it is complete — the backup is intact.** Every file in
`_backup\client-v83-EzorsiaV2-2026-08-15\` still hashes exactly as it did at the start of this
ticket. Restore all 18:

```
copy D:\games\MapleStory\Server\_backup\client-v83-EzorsiaV2-2026-08-15\*.wz D:\games\MapleStory\
```

Do **not** restore `config.ini` from that directory — it carries a remote `ServerIP_Address`
(ticket 01). Use `tools\evan-gate-dll\config.ini.pre-01b`.

Consequences for this list:

- `evan-min` was merged from the **backup**, not from the live client, precisely because the live
  client was moving. Its `pre\` snapshots hash-match the pristine v83 values recorded above.
- Install order is therefore: **restore all 18 from the backup first**, then copy the five
  `evan-min` files over it.
- `evan-min` is an **alternative to** the composed set, not an addition. Every one of its 109 rows
  was already present in the 03k state, so merging it onto that would have exited 5.
