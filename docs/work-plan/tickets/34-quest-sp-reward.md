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
