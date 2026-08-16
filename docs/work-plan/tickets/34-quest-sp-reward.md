# 34 — quests can award SP

**What to build:** a quest whose `Act.img` carries an `sp` reward actually gives the player that
SP. Today it is silently discarded, and **28 Evan quests carry one**, so an Evan who plays the
whole chain arrives at 2218 roughly **28 SP short** with no error anywhere.

**Blocked by:** None — can start immediately. (Ticket 33 makes it *observable*; it does not gate
the code.)

**Status:** ready-for-agent

## The gap `[FACT-measured]` — verified by the orchestrator

`src/main/java/server/quest/QuestActionType.java` `getByWZName` has cases for `exp`, `money`,
`item`, `skill`, `nextQuest`, `pop`, `buffItemID`, `petskill`, `no`, `yes`, `npc`, `lvmin`,
`normalAutoStart`, `pettameness`, `petspeed`, `info`, `0` — and **no case for `"sp"`**, so it
falls through to `UNDEFINED`. There is no `SpAction.java` in
`src/main/java/server/quest/actions/` (13 action classes, none of them SP).

The 28 affected Evan quests, from ticket 31's audit of the v84 `Act.img`:

```
22500 22506 22509 22510 22511 22518 22521 22524 22527 22528 22530 22531 22532 22533
22539 22547 22552 22553 22557 22559 22561 22562 22566 22569 22574 22575 22576 22580
```

## The thing that makes this not a copy-paste

`ExpAction` reads a flat int. **The `sp` node is nested** — `sp/0/{sp_value, job/0}` — a list of
awards, each carrying its own value *and a job filter*, because a quest shared across job branches
pays different SP to each. Copying `ExpAction`'s shape will produce something that either crashes
on the nested node or pays every job the first entry. Read the real nodes in
`porting-resources\wz-data\v84\Quest.wz/Act.img` before writing the class, and handle the job
filter — the player gets the entry matching their job, and nothing when none matches.

Cosmic already stores Evan's SP in the extended ten-slot `sp VARCHAR(128)` column, so the award
must go through whatever `Character` already uses for that, not a naive `setRemainingSp`. Find the
existing path; do not invent a second one.

## Acceptance criteria

- [ ] `getByWZName("sp")` returns a real type, and an `SpAction` beside `ExpAction` applies it
- [ ] The nested `sp/0/{sp_value, job/0}` shape is parsed correctly, including multiple entries
- [ ] The job filter is honoured — a player whose job matches gets that entry; no match, no award
- [ ] SP lands in the extended-SP column for Evan and persists across relog
- [ ] Non-Evan quests that carry `sp` still behave — check whether any pre-v84 quest in this tree
      already has an `sp` node that has been silently dropped until now, and say so either way
- [ ] Full suite green (baseline **2072 passed, 0 failed**), plus a test that pins the nested
      parse and the job filter

## Verification gate

A unit test driving a real `Act.img` `sp` node through the action. Live confirmation waits on
ticket 33 (until the quest data exists, no quest can pay anything) and folds into the next
batched owner launch.

## Rollback

One new class plus one enum case. Deleting both restores today's silent-drop behaviour.

## Note for whoever owns ticket 14

14 (`Evan progression — SP, HP, dragon evolution`) claimed this gap. It is split out because it is
unblocked and 14 is not: 14's blockers are 12 and 13. The HP/MP half of 14 is **already done** —
`Character.levelUp()` gained a Magician-curve branch for `Job.EVAN1` and `isBeginnerJob()` now
includes 2001. What remains in 14 is dragon evolution and the fixed-job-level SP award rules.

---

## Delivered

**Status:** done. Four files, ~100 lines, no schema change — the `sp VARCHAR(128)` column already
exists and the award goes through the code path that already writes it.

### What was built

| File | Change |
|---|---|
| `src/main/java/server/quest/QuestActionType.java` | `SP(17)` + `case "sp": return SP;` |
| `src/main/java/server/quest/actions/SpAction.java` | new — parses the nested node, applies the job filter |
| `src/main/java/server/quest/Quest.java` | `case SP: ret = new SpAction(this, data);` + import |
| `src/test/java/server/quest/actions/SpActionTest.java` | new — 8 tests |

`SpAction.run` resolves the player's job to a skill book with `GameConstants.getSkillBook(jobId)` and
calls `Character.gainSp(delta, skillbook, false)` — the same extended ten-slot path
`AbstractCharacterObject.remainingSp[]` already uses for job advancement (`Character.java:1166`,
`Character.java:6286`). No second SP path was invented and `setRemainingSp` is never touched.

The first award whose `job` list contains the player's job wins and iteration stops — the list is a
set of job branches, not a sum. An award with no `job` child is unfiltered and applies to anyone; if
nothing matches, nothing is paid.

### The real wz node `[FACT-measured]`

`porting-resources/wz-data/v84/Quest.wz` is a packed `PKG1` archive and was not extracted anywhere in
the tree, so it was decoded directly: fsize 6319873, fstart 60, encVersionHeader 173 → **version 84**,
hash 1877, **GMS IV `4D 23 C7 2B`**. Root: `QuestInfo.img, Exclusive.img, Check.img, PQuestSearch.img,
PQuest.img, Act.img, Say.img` — note `PQuestSearch.img`, which v83 does not have. `Act.img` holds 3021
quest children.

*Instrument proved before the measurement was trusted:* the reader's output for quests **1000** and
**3000** is identical — structure, node names, types and values — to this tree's v83
`wz/Quest.wz/Act.img.xml`, and **2270 of 3021** shared quests match in bulk; the 751 that differ are
coherent v84 content deltas (e.g. quest 2000's `count -15 → -30`), not noise. Unicode strings decode
to valid text (`dateExpire = "2009090923"`).

`Act.img/22500/1` (phase `1` = act/end), verbatim, in this tree's XML dialect:

```xml
<imgdir name="22500">
  <imgdir name="0"/>
  <imgdir name="1">
    <int name="exp" value="1270"/>
    <int name="nextQuest" value="22501"/>
    <imgdir name="sp">
      <imgdir name="0">
        <int name="sp_value" value="1"/>
        <imgdir name="job">
          <int name="0" value="2200"/>
        </imgdir>
      </imgdir>
    </imgdir>
  </imgdir>
</imgdir>
```

Types: `sp` and its index are `imgdir`; `sp_value` and `job/<i>` are `int`. Nothing other than
`sp_value` and `job` ever appears under an index.

Facts about the 28, measured across the whole v84 `Act.img`:

- **28** quests carry `sp`, all under phase `1` (act/end). **Zero** under phase `0` (act/start).
- **Every one has exactly `sp/0`** — no v84 quest has a second award index.
- **Every index has a `job` child, each with exactly `job/0`** — none is unfiltered.
- `sp_value` is `1` everywhere except **22574, which is `2`** — so the chain is worth **29 SP**, not 28.
  The ticket's "roughly 28 SP short" is 29.
- Job ids used: 2200 (7 quests), 2210 (7), 2211 (7), 2212 (2), 2213 (4), 2214 (1). All Evan.
- **No Dual Blade (43x) quest awards SP through an `sp` node** — DB SP is not delivered this way.

So the multi-entry path is not exercised by v84's own data; it exists because the acceptance criteria
asked for it and the shape permits it. Five lines.

### The answer to the open question: **zero** `[FACT-measured]`

**No pre-v84 quest in this tree has ever carried an `sp` node.** This bug is exactly as old and as
wide as Evan, and it cost the live server nothing before now.

| Source | `name="sp"` | control: `name="exp"` |
|---|---|---|
| `git show HEAD:wz/Quest.wz/Act.img.xml` (pre-merge) | **0** | 1665 |
| `porting-resources/.../HeavenMS-v83-upstream/wz/Quest.wz/Act.img.xml` | **0** | 1651 |
| working tree, after ticket 13's Evan merge landed mid-task | 28 | 1749 |

The `exp` column is the control: a grep that finds 1665 `exp` nodes and 0 `sp` nodes is measuring,
not failing silently. `SpActionTest.noQuestOutsideTheEvanChainCarriesAnSpNode` pins this durably —
it walks the real `Act.img` and asserts every `sp`-carrying id is `22xxx`, tallying `exp` rewards in
the same walk and requiring `> 1000` so the assertion cannot pass vacuously on a broken walk.

### The test was made to fail on purpose first

`SpActionTest` drives the real `XMLDomMapleData` reader over the nested shape, not a hand-built stub.
Both halves were broken deliberately and watched to fail:

- **Job filter neutered** (`appliesTo` → `return true`): **4 of 7 failed** —
  `aLaterEntryIsReachedAndItsOwnValueIsUsed`, `everyJobInAnEntrysListMatchesIt`,
  `aJobOnNoListGetsNothing`, `theAwardGoesThroughTheExtendedSpPathAndNothingElse`.
- **Flat-int parse** (`getChildren().subList(0, 1)` — the `ExpAction` copy-paste mistake this ticket
  warns about): **3 of 7 failed**.

Both reverted. 8 of 8 green after.

### Test result

`SpActionTest` — **8 passed, 0 failed**.

Full suite — **2059 run, 4 failed**. **None of the four belongs to this ticket.** All four are ticket
13's Evan quest merge, which landed in `wz/Quest.wz/*.xml` *while this ticket was being worked*: the
file's `sp` count went 0 → 28 between two greps, and the suite total moved 2087 → 2059 between two
runs as that agent added and removed test classes. The 2072 baseline is not meaningful in this tree
while 13 is mid-merge.

- `V84QuestNodeTest.thePreExistingQuestsSurvivedTheMerge` — `QuestInfo.img child count after the merge ==> expected: <2881> but was: <3016>`
- `V84QuestNodeTest.the22515To22518GateIsTicket13sAndIsStillUnmet` — 22515 is now present
- `V84RegressionTest.the2818QuestsThatPredateTheMergeAreAllStillPresent` — `Act.img ==> expected: <2824> but was: <2959>` (2824 + 135 Evan ids)
- `V84EvanQuestDataTest.questLoadsThroughTheRealStaticProvider` — the `WZFiles.DIRECTORY` static race
  its own failure message describes: *"another test class won the WZFiles.DIRECTORY race, so this
  says nothing about the merge"*

Proved rather than asserted: with all four of this ticket's files removed from the tree
(`SpAction.java` and `SpActionTest.java` moved out, `QuestActionType.java` and `Quest.java` reset to
`HEAD`), `V84QuestNodeTest` and `V84RegressionTest` **still fail the same three assertions**.

### Not verified without a client

- **SP landing in the `sp VARCHAR(128)` column and surviving relog.** The test verifies that
  `gainSp(delta, skillbook, false)` is the call made; it does not exercise persistence. The extended
  column round-tripping is pre-existing behaviour, not new here, but it was not driven end to end.
- **A live quest paying out.** Ticket 33 gates this — until the quest data loads, no quest can pay
  anything. Folds into the next batched owner launch.
- **`getSkillBook` for Evan1.** `GameConstants.getSkillBook` maps 2210–2218 → books 1–9 and everything
  else → 0, so `Job.EVAN1` (2200) — **7 of the 28 quests** — writes into **book 0**, the slot every
  other class uses. That is the pre-existing mapping and was deliberately left alone; whether Evan1
  deserves its own slot is ticket 14's call, not this one's.
- **Whether the client renders a book-N SP gain mid-quest.** `gainSp(..., silent=false)` announces
  `Stat.AVAILABLESP`; not observed against a real client.
