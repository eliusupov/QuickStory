# 09 — v84 non-Evan quests accept and complete

**Blocked by:** 03, 06, 07, 08

**Status:** delivered — the data is merged, verified and scripted to the limit of what an agent can
verify, and the scoping judgement this ticket exists to make is made and listed. **The headline is
not the merge, it is what the data turned out to contain:** of v84's 198 new quests, **135 are the
Evan chain (ticket 13's) and 63 are this ticket's**, and **48 of those 63 carry an `end` date that
had already passed when v84 shipped**, so Cosmic's `EndDateRequirement` refuses to start them.
In-game acceptance and completion are human steps (`## Human steps — staged, not performed`).

**Merge procedure: [`../WZ-MERGE-PROCEDURE.md`](../WZ-MERGE-PROCEDURE.md)** — executed as written
under the post-03e tool (`--deny` mandatory, per-ticket `pre\`, `--live` hash check). Staging
directory: `D:\games\MapleStory\Server\wz-merge\09\`. **The header line this ticket originally
carried showed the pre-03e CLI and is superseded** — that form has no `--deny` and would exit 2
today.

## What to build

The v84 quests that are not part of Evan's chain can be accepted, progressed and completed.

## Acceptance criteria

- [x] **Quest data merged into `Quest.wz` (QuestInfo, Check, Act, Say) and into the server XML
      tree** — 252 rows, `added 252, refused 0, denied 0, forced 0` on both sides, read back through
      the server's own `XMLWZFile`. **The client half is staged, not installed**: per the run-order
      directive the client ships from one composed merge of every ticket's path list.
- [ ] **Quests appear in the in-game quest list with correct text** — human step. All 63 have a
      non-blank `QuestInfo/name`, but **8 of them are untranslated Korean in v84 itself** (listed
      below), so "correct text" is only as correct as Nexon shipped.
- [x] **Quests requiring scripts have them written** — **30 of the 63 carry a script requirement;
      9 of those are medal quests that `QuestScriptManager:53` routes to the existing
      `medalQuest.js`; the remaining 22 are written**, one file per quest id, all new files, zero
      overwrites.
- [ ] **A representative sample across the new areas is accepted and completed end to end** —
      **cannot be met by this ticket's data, and the reason is a measurement, not an excuse.** 48 of
      63 are behind an expired `end`; of the 15 that are not, 7 are gated on Evan-chain quests that
      do not exist and 5 on an `infoex` event counter nothing in this tree produces. **Three quests
      — `2344`, `3540`, `19011` — have no unmet gate.** Staged as human steps.
- [x] **Existing quests still work — no regression from the `Quest.wz` merge** — this is the
      criterion that drove the biggest decision here. `add-list/Quest.txt` offers **132 rows that
      write into quests the live client already has**, including 108 that would cap working
      beginner quests at Lv.40 and 13 that would switch five working quests off. **All 132 are
      refused**, measured one by one, in
      [`docs/wz-baseline/merge-lists/09/DEEP-ROWS.md`](../../wz-baseline/merge-lists/09/DEEP-ROWS.md).
      Asserted: every category image still holds every pre-v84 id.

---

## What the quest data actually contains — 198, and only 63 of them are mine

The ticket's "198" is right, and it is also the number that hides the whole scoping question.
Derived, not assumed:

| | |
|---|---:|
| `add-list/Quest.txt` copy roots | **924** |
| whole new quest nodes (`<Img>.img/<id>`) | 792 |
| distinct new quest ids (792 / 4 category images) | **198** |
| …of which `22xxx`, the Evan chain → **ticket 13** | **135** |
| …of which this ticket's | **63** |
| rows writing into an existing live quest, or into a container the two trees group differently | **132** |

**`Act`, `Check`, `QuestInfo` and `Say` agree exactly** — the id sets are identical in all four,
diffed, no exceptions. That is the "798 vs 198" discrepancy the frontier flagged, resolved: it was
198 quests × 4 images and never a defect.

The 63, by block:

| ids | n | what |
|---|---:|---|
| `3756`–`3761` | 6 | **the Crimson Sky chain** — ticket 06's area, and the chain that grants Soaring |
| `2344` | 1 | Mushking Empire in Danger (Aran, Lv.30–38) |
| `3540` | 1 | In Search of Lost Memories (Aran / Evan beginner, needs quest `3507`) |
| `10480`–`10521` | 28 | Evan launch-event quests (Maple Administrator / Cassandra) |
| `19011` | 1 | The 3rd Honorable Mesoranger (medal) |
| `28346`–`28365` | 19 | Evan gift + Dragon's Nest quests, incl. `28353`/`28354` on **06's NPC `9201144`** |
| `29934`–`29940` | 7 | Aran/Evan medal quests |

**Most of the 63 are Evan-*adjacent* without being Evan-*chain*.** The ticket's exclusion is by id
(`22xxx`), and these are not; drawing a second boundary on vibes would have been inventing one. They
are claimed here, and the handoff below says so explicitly so ticket 13 does not re-add them.

### The dependency the ticket predicted does not exist

> *"A quest asking you to kill a Skelegon cannot be verified before Skelegons spawn."*

**Not one of the 63 quests has a `mob` requirement.** Zero, across all 63 `Check.img` nodes,
both steps. Asserted (`noneOfThese63QuestsRequiresKillingAnything`), because an absence that is not
asserted gets rediscovered. The blocking relationship to 06/07/08 is real but runs through **NPCs**,
not mobs.

And the NPC side is clean: **every NPC any of the 63 names has an image in the server tree**, with
exactly one exception — `2001006`, one of the Christmas NPCs ticket 08 gave a `String.wz` name but
no `Npc.wz` image. Only quest `10487` names it, and `10487` is date-expired, so nothing is lost. The
test asserts that gap as a list of exactly one, so a second gap fails rather than blends in.

**All 54 items** the 63 quests demand or reward resolve in `wz/Item.wz` or `wz/Character.wz` — the
Crimson Sky moss `4032531`, the seven `4032533`–`4032539` remnants, and every Evan gift equip. Zero
missing.

## The finding: 48 of 63 are switched off by v84's own data

`Check.img/<id>/0/end` is an END_DATE start requirement. `EndDateRequirement.check` builds a
`Calendar` from the string and returns `cal >= now`, and `Quest.canStart` fails the quest if any
start requirement fails (`QuestActionHandler:87` and `:115` are the only two callers, so this gates
both the plain and the scripted accept path).

**48 of the 63 carry one, and every value is in the past.** The latest is `201005050000`
(2010-05-05). The Crimson Sky chain's is `2000010100` — the same sentinel shape the live client
already uses 43 times as `2000010200`.

| gate | quests |
|---|---|
| expired `end` | **48** — `3756`–`3761`, `10480`–`10487`, `10490`, `10496`, `10500`–`10507`, `10510`, `10514`, `10516`, `10520`, `10521`, `28346`–`28365` |
| gated on an Evan-chain quest (`22300`/`22511`/`22527`/`22552`/`22566`/`22602`/`22603`) **and** Evan job ids | 7 — `29934`–`29940` |
| gated on `infoex` value `99999`, an event counter nothing in this tree produces | 5 — `10491`–`10494`, `10497` |
| **no unmet gate** | **3 — `2344`, `3540`, `19011`** |

This is not a merge fault and it is not fixable by merging: the merge tool only copies, and the
node to remove is one v84 ships. **The one-node fix, stated so it is an owner decision rather than a
silent loss:** delete `Check.img/375{6,7,8,9}/0/end`, `Check.img/376{0,1}/0/end` from
`wz/Quest.wz/Check.img.xml` and from the composed client `Quest.wz`, and the Crimson Sky chain
becomes acceptable. That is a hand-authored edit in both trees — the same shape as ticket 06's
travel-route problem and ticket 08's twelve refused portal rows, and for the same reason.

**Why it matters beyond six quests:** `3759` "Towards the Sky 2" is what grants **Soaring**
(`Act.img/3759/1/skill` = `1026` / `10001026` / `20001026` / `20011026`, level 1). Ticket 06's
Crimson Sky maps carry `info/needSkillForFly = 1`. So the chain that unlocks flight over 06's area
is merged, scripted and sitting behind one expired date node.

## The 132 refused rows — the positional-array hazard, in `Quest.wz`

Full measurement in
**[`docs/wz-baseline/merge-lists/09/DEEP-ROWS.md`](../../wz-baseline/merge-lists/09/DEEP-ROWS.md)**.
Every one was dumped from both trees before the decision. The short version:

- **108 × `Check.img/<id>/0/lvmax = 40`** onto live beginner/training quests `28162`–`28325`
  ("Meeting the Training Instructor", "Secret of Astaroth", "Dirty Treasure Map", …). The value is
  **uniform 40 across all 108**, checked rather than sampled. The live node is the v84 node minus
  `lvmax`, so the additive gate writes every one of them without a word. Effect: **108 currently
  startable quests become unavailable to any character above Lv.40.** Refused; criterion 5 forbids
  exactly this.
- **12 rows on `Check.img/{2208,2209,2210,2211}/0/{start,end,interval}`** — v84 gives four
  currently-ungated live quests (NPC `1092011`) a **24-hour window in January 2008**. Refused: it
  turns four working quests off permanently.
- **1 row `Check.img/3845/0/end = 2010010100`** — same, on a working Lv.60–80 quest. Refused.
- **2 inert rows** (`10109/0/interval`, `9260/0/dayByDay`) onto quests that are already date-dead.
  Refused anyway: "inert" is the argument ticket 08's `enterDollcave.js` mistake was made on, and
  neither buys anything.
- **5 `QuestInfo` display rows** (`1008`, `9260`, `20012`, `20311`) — client-side text on quests
  this ticket does not own. Refused; zero cost.
- **3 × `Exclusive.img/{0,1,2}`** — **the positional-array finding.** The live client holds one
  named group, `medal`, with 14 ids. v84 **replaced** it with three numeric groups holding a
  different partition. The add-list offers the numeric groups as additions, and they are — the
  target has no `0`/`1`/`2` — so the gate passes them and the result is an image holding `medal`
  **and** `0`/`1`/`2`, with seven ids (`29002`, `29500`, `29300`–`29304`) in two mutually-exclusive
  groups at once. Neither vendor ever shipped that. Refused. Nothing in `src/` reads
  `Exclusive.img` (grepped, zero hits), so the cost is that v84's new `10415`–`10420` group is
  unenforced client-side, which beats a self-contradicting one.
- **1 × `PQuestSearch.img`** — a whole new image indexing a party-search window the v83 client does
  not have and the server never opens. Left unclaimed rather than imported for nobody.

**The rule this adds to 08's.** 08's rule was `<array>/<n>` and `<array>/<n>/<field>`. `Quest.wz`
shows the same class with no numeric index at all: *any row whose parent chain reaches a node the
live client already has is an edit, not an addition.* In `Quest.wz` the tell is depth — a row deeper
than `Quest.wz/<Img>.img/<id>` is by definition inside an existing quest, and 128 of the 132 are
exactly that. The other four are a container whose **child names** changed meaning between trees,
which depth alone will not catch. Reported to 03g rather than waited on.

## Path list — the authoritative deliverable

`docs/wz-baseline/merge-lists/09/`.

| file | rows | contents |
|---|---:|---|
| `Quest.paths.txt` | **252** | 63 quest ids × `QuestInfo` / `Check` / `Act` / `Say` |
| `DEEP-ROWS.md` | — | the 132 refusals, measured |
| **total** | **252** | |

**No `String.wz` rows and no other file at all.** Quest names live in `QuestInfo.img/<id>/name`,
not in `String.wz`; there is no `String.wz/Quest.img`. **No SQL, no changeSet** — the 63 quests
carry no drop tables and no mob requirements, so there is nothing for `155` to hold. (Had drops been
owed, `155` is the next free id: `153` is 06's, `154` is 07's, 08 added none.)

**No force rows.** Nothing on the list collides with anything — `addlist-dryrun-Quest.conflicts.txt`
has been `0 refused` since 02g and it still is. `COLLISION-FORCE.txt` was not passed, and no
`09/String.force.txt` exists because no v84 quest node has a live counterpart to overwrite.

**Overlap with 04–08: zero, and structurally so.** No other ticket's path list contains a single row
rooted at `Quest.wz` — checked mechanically across all of
`merge-lists/{04,05,06,07,08}/*.paths.txt`. This is the first and only ticket to open the file.

## Dry runs, conflicts, force decisions

`--deny docs\wz-baseline\merge-lists\COLLISION-DENY.txt` on every `merge` and every `xml`, dry runs
included, both sides. **The deny list read 40 roots, not 28** — 03g added 12 while this ran
(08's refused portal rows), and none of the 40 intersects these 252 paths.

| run | requested | added | refused | denied | forced | exit |
|---|---:|---:|---:|---:|---:|---|
| binary dry | 252 | 252 | 0 | 0 | 0 | 0 |
| binary real | 252 | 252 | 0 | 0 | 0 | 0 |
| xml dry | 252 | 252 | 0 | 0 | 0 | 0 |
| xml real | 252 | 252 | 0 | 0 | 0 | 0 |

Every `*.conflicts.txt` in the staging directory is header-only.

## Merge results

Live vs backup SHA-256, before anything (§5.0), equal:

```
Quest.wz    EFC12B2D6366FBD43ACDF92A58A3F792C25074982299813F19560B9F865DEA24
```

The per-ticket `pre\` snapshot hash-matches it, and the real merge passed `--live`. Staged output
(§6.4):

| file | exit | SHA-256 | bytes |
|---|---|---|---:|
| `Quest.wz` | 0 | `5F37E5F56970FFD01DC5B28D082BF02CE130FF7B56C7C1B592C67D77996F04FE` | 6,083,413 |

## Verification

**§6.2 diff tool**, pre vs post:

```
add: 252, removed: 0, protect: 0, modified v84: 4, modified live: 0
18 images parsed, 0 parse failures
```

`add-list/Quest.txt` from that run is **byte-for-byte the path list** (diffed, empty).
`removed-list` is empty. `modified-list` is exactly the four images inserted into and nothing else.

**§6.1 content digest**, per direct child, pre vs post, for all four images:

| image | only in post | only in pre | pre-existing children that moved |
|---|---:|---:|---:|
| `Act.img` | 64 (63 ids + `TOTAL`) | 1 (`TOTAL`) | **0** |
| `Check.img` | 64 | 1 | **0** |
| `QuestInfo.img` | 64 | 1 | **0** |
| `Say.img` | 64 | 1 | **0** |

So every one of the ~2,818 pre-existing children of every one of the four images is
digest-identical. And the added nodes digest **identical to their v84 source**: comparing
`WzMerge hash <v84>\Quest.wz <img>` against the post file, **0 of 252 differ** — the version trap
proven on content, not headers.

**§6.3 the gate fires**, both sides:

```
binary: added 0 (forced 0), refused 252   exit 5
xml:    added 0 (forced 0), refused 252   exit 5
```

**§5.6 server XML** — and this ticket is the first to hit a documented expectation that is wrong:

```
wz/Quest.wz/Act.img.xml       | 827 insertions, 1 deletion
wz/Quest.wz/Check.img.xml     | 2076 insertions, 232 deletions
wz/Quest.wz/QuestInfo.img.xml | 602 insertions, 0 deletions
wz/Quest.wz/Say.img.xml       | 1741 insertions, 583 deletions
```

The procedure says to expect `N insertions(+), 0 deletions(-)` and to stop on any other deletion.
**These 816 deletions are not deletions.** Inserting 63 blocks of highly repetitive XML into a
2,800-child file makes git's diff re-anchor: every "deleted" line is re-added verbatim elsewhere in
the same hunk. Verified three ways rather than assumed:

- every deleted line in `Check.img.xml` is present among the added lines — set difference **empty**;
- **not one line** of the four files at `HEAD` is missing from the files on disk (CR-normalised
  comparison, all four → **0**);
- the top-level `<imgdir name>` sets: `Act` 2,824 → 2,887, `Check` 2,807 → 2,870,
  `QuestInfo` 2,818 → 2,881, `Say` 2,801 → 2,864 — **each gained exactly the 63 ids on the path
  list and lost none.**

All four files are still CRLF throughout with no BOM. **This is a note the procedure should carry:
`git diff --stat` deletions on a large insert into a repetitive XML file are a diff artefact; the
check that actually discriminates is "is every old line still there", not the stat line.**

**Live client, after the ticket:** all **18** `.wz` in `D:\games\MapleStory\` still SHA-256-match
`_backup\client-v83-EzorsiaV2-2026-08-15\` — re-checked file by file at the end, **0 mismatches** —
and no `.partial`, `.TEMP` or `.merged` exists beside them. `porting-resources\wz-data\**` was
opened read-only. Nothing was written to `D:\games\MapleStory\` at any point.

**§5.8 server-side**, `src/test/java/server/V84QuestNodeTest.java`, **18 tests, all green**;
**full suite 1,989 tests, 0 failures, 0 errors** (`./mvnw -o test` → BUILD SUCCESS). The arithmetic
reconciles exactly: **1,949** (STATUS's baseline) + 18 in the new class + **22 free**, because
`ScriptEvaluationTest` walks `scripts/quest/` and now evaluates the 22 new files — so a syntax error
in any of them fails the suite. It uses `V84Wz.wz`; no copy was made.

What the class proves:

- the path list is 252 unique rows, 63 unique ids, **the same 63 in all four category images**, and
  **no row is deeper than `Quest.wz/<Img>.img/<id>`** — the structural guard against the hazard above
- `924 = 252 + 540 + 132` reading `add-list/Quest.txt` itself, so a regenerated manifest that moves
  the split fails here instead of three tickets later; and no `22xxx` row is on this list
- all 63 parse in all four images with a non-blank, non-`MISSING NAME` name, and every category image
  still holds more than 2,800 quests, including `3749` (07's Neo City gate) and `3507` (3540's
  prerequisite)
- **the refusals are asserted, not just documented** — `28162`/`28200`/`28266`/`28282`/`28325` have
  no `lvmax`; `2208`–`2211` have no `start`/`end`/`interval` and `3845` no `end`; `Exclusive.img`
  still has its 14-id `medal` group and none of `0`/`1`/`2`
- every NPC these quests name has an image, with `2001006` asserted as the single known gap
- **no quest has a `mob` requirement** — and the same traversal is asserted non-empty on the NPC
  side, so the mob assertion is not vacuous
- **48 of 63 are date-gated**, derived from the tree, with the `3756`–`3761` chain asserted to be
  among them
- the set of quests needing a hand-written script is re-derived from the merged WZ and must equal the
  22 files written; each file defines exactly the half the WZ asks for and disposes
- the nine medal quests deliberately have **no** file, and `medalQuest.js` still exists
- `3759` names all four Soaring variants and its script teaches one; `Skill.wz/2001.img` is asserted
  **absent**, so the Evan guard inside `3759.js` becomes a failing test the moment ticket 12/13 lands
  it — which is when the guard should be replaced
- **`22515`–`22518` are absent and are ticket 13's**, and `scripts/npc/1012118.js` still gates on
  them, untouched

**It failed twice for real while being written**, both times on my arithmetic rather than the data
(`String.split("/")` length off by one in two assertions). Recorded because the fix was mine and not
the tree's.

## The scripts — 22 written, 9 declined, and why the ticket's estimate was low

The ticket predicted "on the order of 18". The data says **30 of the 63 carry a script requirement**
— 20 `startscript`, 13 `endscript`, 33 script names across 30 quests. The estimate was low because
it extrapolated from the 9%-of-2,818 repo average, and this delta is event and medal quests, which
are script-heavy.

**Two things about how Cosmic resolves these, neither of which is what the WZ says.** First, the
`startscript` / `endscript` **value** (`q2344s`, `q3759e`) is ignored: `QuestScriptManager:52` loads
`quest/<questid>.js`, so only the field's *presence* matters — it is what sets
`hasScriptRequirement`. Second, **`qm.forceCompleteQuest()` does not run the `Act.img` rewards**
(`Quest.forceComplete` only flips the status; `Quest.complete` is what runs the actions, and the
scripted path at `QuestActionHandler:125` never calls it). So a script owns its own rewards, which
is why the existing `scripts/quest/2001.js` hands out items by hand.

**Nine of the 30 are medal quests** (`viewMedalItem`), and `QuestScriptManager:53` already routes
those to `scripts/quest/medalQuest.js`. Writing nine more files would have shadowed a working
fallback for no gain. Asserted as an absence so it stays a decision.

**The 22 written**, one file per quest id, all new (`git status` shows 22 × `??`, zero `M`):

| script | half | what it does |
|---|---|---|
| `2344.js` | start + end | state only — `Act.img/2344` is **empty on both sides**; v84 ships no reward |
| `3540.js` | start | state only — Act empty |
| `3759.js` | end | exp 11,000, consumes `4032531`, **teaches Soaring** on the job-matched id |
| `10480.js` `10481.js` | start | state only — Act empty |
| `10490.js` | start | Say dialogue + start; completion is behind `infoex 99999` |
| `10491`–`10494.js` | end | state only — Act empty, and the WZ does **not** consume the `3994185` |
| `10497.js` | start | state only |
| `10500.js` | start | yes/no dialogue from `Say.img/10500`, then start |
| `10510.js` | start + end | state only both halves — Act empty |
| `10514.js` `10516.js` | start | the one line `Say.img` carries, then start |
| `28353.js` | start | state only; the exp 2,000 is on the **data-driven** complete half |
| `28354.js` | start + end | consumes `4032639`, grants **fame 3** |
| `28361`–`28365.js` | end | the launch-gift equip named in `rewardSummary`, with a `canHold` guard; `28362` picks by gender |

**Where a script hands out something the `Act` node does not declare, that is stated in the file and
is never invented.** Three cases, all sourced from `QuestInfo/<id>/rewardSummary`, which is v84's own
text: `28354`'s "Popularity 3", and `28361`–`28365`'s five gift equips (`1702268`, `1050168` /
`1051209`, `1003089`, `1072443`, `1082272` — all six verified present in `wz/Character.wz`). GMS put
those in its own scripts too; the alternative was a quest that completes and gives nothing.

**One guard worth knowing about.** `3759.js` picks Soaring by job from the four `job` arrays in the
WZ (explorers → `1026`, Cygnus → `10001026`, Aran `2100`–`2112` → `20001026`, Evan `2001`/
`2200`–`2218` → `20011026`). **`20011026` lives in `Skill.wz/2001.img`, which is not in this tree** —
ticket 12/13 owns it — so the Evan branch reports instead of teaching a skill that cannot resolve.
The test asserts `2001.img.xml` is absent, so that guard fails the suite the day it should be removed.

## Handoffs

- **Ticket 13 (Evan).** The **540 rows for the 135 `22xxx` ids are untouched and are yours.**
  Beyond them, **do not re-add** `10480`–`10521`, `28346`–`28365` or `29934`–`29940`: they are Evan
  *content* on non-Evan ids and are merged here. Note `29934`–`29940` are gated on `22300`, `22511`,
  `22527`, `22552`, `22566`, `22602`, `22603` — merging your chain makes seven medals live with no
  further work here. Same for `10491`–`10494`/`10497`, which need an `infoex` counter.
- **Ticket 08's `910060100` handoff — corrected.** 08 wrote that `910060100` becomes reachable "the
  moment ticket 09/13 merges quests `22515`–`22518`". **All four are `22xxx`, i.e. squarely in the
  135, i.e. ticket 13's alone.** This ticket does not and must not merge them, so **`910060100`
  remains staged-but-unreachable and its unblocking belongs entirely to 13.** Asserted in the test
  so the correction is in the tree, not only here.
- **Ticket 06 / owner — the Crimson Sky chain and the Soaring gate.** `3756`–`3761` are merged and
  `3759.js` is written; the chain is one expired `end` node away from being acceptable, and that node
  is a hand-authored deletion in both trees (§"48 of 63"). Until then 06's fly-gated maps have no
  in-game route to the skill that opens them.
- **Ticket 03g.** Two things from here for the tool check you are adding: the shape in `Quest.wz` has
  **no numeric index** (`Check.img/<id>/<step>/<field>` where `<step>` is semantically fixed at 0/1),
  so a check keyed on "array index" would miss all 128; and `Exclusive.img` is a fourth variant again
  — a container whose **child names** were re-partitioned between trees, which only a both-sides dump
  catches. The 132/792 split here is available as a second oracle beside 08's 6/12.
- **The composed install pass.** Add `09` to `compose.ps1`'s source list. **This ticket contributes
  no force rows**, so the composed `FORCE.txt` stays at 41 (38 + 08's 3). `Quest.wz` is not currently
  in any composed manifest at all.

## Human steps — staged, not performed

I cannot launch the client, open a quest window or talk to an NPC. Everything below is unverified.

**Before anything: close MapleStory and any HaRepacker window.**

1. **Do NOT install this ticket's staged `Quest.wz`.** It is merged from the live base, as every
   other ticket's is, and staged merges from the same base do not compose. `Quest.wz` is touched by
   no other ticket, so it is the one file where a direct copy would in fact be safe — but the run
   order says the client ships from one composed merge, and making an exception for one file is how
   the next person gets it wrong. Its hash is in "Merge results" so the merge can be reproduced and
   checked rather than trusted.
2. **No DB migration.** This ticket adds no SQL and registers no changeSet.
3. **Check the three quests that have no unmet gate.** These are the only end-to-end runs the data
   supports today.
   - **`19011` "The 3rd Honorable Mesoranger"** — talk to the **Maple Administrator (`9010000`)**
     holding medal `1142170`. **Pass:** the quest is offered and completes through `medalQuest.js`,
     which pops "<name> is not coded" and awards the title. That message is Cosmic's existing
     behaviour for every medal quest, not a fault of this merge.
   - **`2344` "Mushking Empire in Danger"** — an **Aran** (job `2210`–`2218`) at **Lv.30–38**. Accept
     from **Manji (`1040001`)**, complete at **`1300005`** holding 1 `#t4032375#`. **Pass:** both
     halves complete. **Expect no reward** — `Act.img/2344` is empty in v84 and `2344.js` deliberately
     hands out nothing. A reward appearing would mean someone "improved" the script.
   - **`3540` "In Search of Lost Memories"** — an Aran or Evan-beginner with quest `3507` **started**
     (not completed). Both halves at NPC `1012003`. Same: no reward, by design.
4. **Confirm the quest list renders.** Open the quest window on any character and look for the new
   entries. **8 of the 63 will read as Korean** — `10487`, `10490`–`10494`, `10496`, `10497` — because
   **v84 itself ships them untranslated**, exactly as ticket 08 found for the Christmas NPCs. That is
   not a merge fault and there is nothing to import that fixes it. If they should not ship at all,
   deleting those eight ids from `09/Quest.paths.txt` (32 rows) is the whole change.
5. **The regression check, and it is the important one.** This ticket's biggest decision was refusing
   132 rows to protect live content. Verify the protection held:
   - Take a character **above Lv.40** to the three NPCs the refused rows would have capped —
     `1022000` (quest `28162` "Meeting the Training Instructor"), `1061011` (`28266` "Secret of
     Astaroth", `28282` "How to Avoid the Stink") and `1090000` (`28325` "Dirty Treasure Map") — and
     confirm each quest is **still offered**. If any has vanished above Lv.40, the `lvmax` rows got
     merged.
   - Confirm quests `2208`–`2211` at NPC `1092011` and `3845` at NPC `2092001` are **still
     acceptable**. If they are gone, the date rows got merged.
   - Confirm the medal exclusivity still behaves: holding one `290xx` medal should still block its
     group-mates.
6. **If the owner takes the Crimson Sky decision** (§"48 of 63"): after removing the six
   `Check.img/375x|376x/0/end` nodes, run `3756` → `3761` from **Chief Tatamo (`2081000`)** and
   `2085000`, at **Lv.100+**. **Pass:** `3759` completes and the character gains **Soaring**, which
   should then work in ticket 06's Crimson Sky maps and nowhere else.
7. **Rollback.** Nothing was installed, so the client needs none. On the server side revert **only**
   `wz/Quest.wz/{Act,Check,QuestInfo,Say}.img.xml` and `git clean` the 22 new
   `scripts/quest/<id>.js` files — all 22 are new, nothing existing was touched. **Never
   `git checkout -- wz/` wholesale**; other tickets have uncommitted XML in the same tree.

## What I could not do

- **Launch the game.** Every claim about the quest list, dialogue, rendering and end-to-end
  completion is staged, not observed.
- **Make 60 of the 63 quests acceptable.** 48 are behind an `end` date v84 itself shipped already
  expired, 7 behind Evan-chain quests that are ticket 13's, and 5 behind an `infoex` event counter
  nothing in this tree produces. Merging cannot fix any of the three: the tool copies, and the fix
  for the first is a deletion. Stated as an owner decision with the exact node named.
- **Unblock `910060100`.** Ticket 08 handed this ticket the `22515`–`22518` gate. The data says all
  four are Evan-chain ids and therefore ticket 13's, so the handoff was addressed to the wrong
  ticket and the map stays unreachable. **This is the one place this ticket was expected to deliver
  a win and the data says it cannot** — recorded rather than fudged by merging four `22xxx` ids
  outside my scope.
- **Give the eight Korean-named quests English text.** v84 ships them in Korean. Imported as-is and
  flagged, the same call ticket 08 made on the Christmas NPCs, and reversible in one edit.
- **Verify a script end to end.** `ScriptEvaluationTest` proves all 22 parse and evaluate under the
  same engine the server uses; nothing proves the dialogue flow, and nothing can without a client.
- **Adopt v84's 108 `lvmax` caps.** They are a faithful part of the patch and they are also a
  regression against criterion 5. Refused, measured, and left as a one-grep decision for an owner
  who wants GMS parity more than Cosmic's current behaviour.
