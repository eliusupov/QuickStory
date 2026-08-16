# 33 — v84 quest data in the server's own WZ, additively

**What to build:** the server knows about every quest v84 shipped. Today it knows 2,881 and
**none of them is an Evan quest**, so all 49 Evan quest scripts in this tree are dead files —
`Quest.hasScriptRequirement` reads `Check.img`, finds no node, and `QuestScriptManager` disposes
without ever loading the script. This ticket is what makes quests exist at all.

**Blocked by:** None — can start immediately.

**Status:** ready-for-agent

## Why this is carved out of 09 and 13

Tickets 09 and 13 mix two different jobs: putting quest data in the **client** archives, and
putting it in the **server's** `wz/` XML. Only the second one is route-independent.

The client half is now decided by the v84 migration (ticket 17): the client will be **stock v84**,
which already ships every v84 quest, so merging quest data into v83 client archives is work the
migration deletes. The server half is needed **either way** — on v83 and on v84 — because the
server never reads the client's files. So this ticket takes the server half only, and 09/13 keep
the client half.

Do not touch `D:\games\MapleStory\` in this ticket. Nothing here reaches the client.

## The measured gap `[FACT-measured]` — verified by the orchestrator, not taken on report

```
wz/Quest.wz/QuestInfo.img.xml   2881 quests, 103 x 21xxx (Aran), 0 x 22xxx (Evan)
wz/Quest.wz/Check.img.xml       2870
wz/Quest.wz/Act.img.xml         2887
```

Source data is the stock v84 archive already on disk:
`D:\games\MapleStory\Server\porting-resources\wz-data\v84\Quest.wz` (6,319,933 bytes).
It is **not extracted yet** — no `QuestInfo.img.xml` exists anywhere under `porting-resources`.
Extracting it is the first step of this ticket.

## What to build

Add to `wz/Quest.wz/{QuestInfo,Check,Act}.img.xml` **every quest id that v84 has and this tree
does not.** That is Evan's ~135 and every other quest v84 added since v83, in one pass, one
procedure, one verification.

`Quest.java` reads `QuestInfo.img`, `Act.img` and `Check.img` **only — never `Say.img`.** Do not
merge `Say.img`; it would be dead weight. (Evan quest dialogue comes from the scripts, which
ticket 31 already wrote.)

### The one hard rule

**Additive only. A quest id that already exists in this tree is never modified, in any of the
three files, for any reason** — not to "fix" a level gate, not to align a reward with v84, not
even when v84 is demonstrably more correct. The owner's standing rule is *"i want to add things,
not remove"*, and this tree carries custom content that stock v84 would silently overwrite.

The project's own doctrine, proved wrong four separate times before it was believed:
**an empty conflicts list is not evidence of safety — writes into existing records are the
danger.** `Check.img` `lvmax` is a known hazard class here. So the deliverable is not just the
merged file, it is the **proof** that every pre-existing id is byte-identical to what it was.

Suggested shape, but use your judgement: parse both sides, compute
`v84_ids - ours`, emit only those subtrees, and then re-parse the result and diff every
pre-existing id against `git show HEAD:wz/Quest.wz/...`. Automate the proof — do not eyeball it.

### Report, do not silently drop

Any quest present in v84 that you decline to add, list with the reason. Any quest that exists in
one of the three files but not the others, list — v84 itself is inconsistent that way and the
script layer cares (`hasScriptRequirement` keys off `Check` alone).

## Acceptance criteria

- [ ] Every v84 quest id absent from this tree is present in `QuestInfo`, and in `Check`/`Act`
      wherever v84 has one
- [ ] All ~135 `22xxx` Evan ids present, including `22100`–`22109` with their `startscript`
- [ ] **Zero pre-existing quest ids changed** — proven by an automated id-by-id diff against
      `HEAD`, output included in the delivery, not asserted
- [ ] The three files still parse, and the server starts and loads quests without error
- [ ] Full suite green (baseline in this tree: **2072 passed, 0 failed**)
- [ ] Every declined or inconsistent quest listed with its reason — no silent omissions

## Verification gate

`Quest.hasScriptRequirement(22100)` returns true and `QuestScriptManager` loads `22100.js`.
Ticket 31 delivered all ten advancement scripts plus 26 others; they stay inert until this lands,
so a green `EvanJobAdvancementScriptTest` is **not** sufficient — it stubs `qm`. Prove the data
path with a real `Quest` load.

No owner client launch. This is server-side only and folds into the next batched launch.

## Rollback

Three text files. `git checkout wz/Quest.wz/` restores current behaviour. No schema, no Java.
