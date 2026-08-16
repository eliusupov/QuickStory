# 31 — Evan quest scripts: make Evan advance past 2001

**What to build:** an Evan character can complete the job-advancement chain in game —
2001 → 2200 → 2210 → … → 2218 — by talking to the right NPCs and finishing the real quests,
instead of needing `!job`. This is why Evan currently reads as "creatable but stuck".

**Blocked by:** None — can start immediately.

**Status:** ready-for-agent

**Why this one first:** it works on the **current v83 client** (Evan skills already function there
after ticket 11's `Tab8` fix), delivers visible progress today, and transfers to v84 unchanged.
It is also the single largest gap in Evan — bigger than the Java.

## The measured gap `[FACT-measured]`

- `orion-server` ships **58** Evan quest scripts; Cosmic ships **13**.
- Cosmic has **zero of the `221xx` block** — the 2nd/3rd/4th job advancement chain.
- Cosmic's Evan **Java** is already the best in the public family (better than MapleSolaxia-90,
  which has neither `EvanCreator` nor `Dragon`). The whole Java gap is four `DECENT_*` constants.
  **So this is a scripting job, not a Java job.**

## Source

`https://github.com/MapleStoryA/orion-server` — v90, Java, OdinMS lineage, AGPL, active 2026.
Port the **quest scripts only**. Do NOT port its net-layer Java: different family
(`handling.channel.handler` vs Cosmic's `net.server.channel.handlers`).

## Acceptance criteria

- [ ] All Evan job-advancement quests present, in Cosmic's own script idiom and directory layout
- [ ] Every `221xx` quest script ported and loading without error at server start
- [ ] An Evan can advance 2001 → 2200 → 2210 → … → 2218 in game via NPCs, no GM commands
- [ ] Quest prerequisites, level gates and rewards match v84 behaviour
- [ ] No existing (non-Evan) quest regresses — run the quest suite before and after
- [ ] Scripts contain no v90-only API calls; anything Cosmic lacks is adapted, not stubbed silently
- [ ] Any quest that cannot work is listed explicitly with the reason — no silent omissions

## Verification gate

An Evan created from level 1 reaches **2218** through normal play. Owner launch: **batched**,
folded into the next content test — do not spend a dedicated launch on this alone.

## Rollback

Scripts are additive files; deleting them restores current behaviour. No schema, no Java.
`src/test/java/scripting/EvanJobAdvancementScriptTest.java` reads the ten `221xx` files, so it
goes with them.

---

# Delivered — 2026-08-16

**36 scripts added, 22 of orion's 45 deliberately not ported, 1 Cosmic capability gap found.**
The ticket's premise that the `221xx` block could be ported is wrong and had to be replaced:
**orion-server's `22100.js`–`22109.js` are ten byte-identical `forceStartQuest()/forceCompleteQuest()`
no-ops** carrying the comment `//TEMPORARY QUEST NOW SKIPPING`. None of them changes job. Porting
them would have produced exactly the silent stub this ticket forbids, so the ten advancement
scripts here are written against the v84 `Quest.wz` data instead, not ported.

## The advancement chain, from v84 `Quest.wz/Check.img`

Every row below is `Check.img/<quest>` in `porting-resources/wz-data/v84/Quest.wz`, not folklore.
All ten are given by **NPC 1013000 — Mir, the player's own dragon** (name from
`String.wz/Npc.img/1013000`), all ten have `startscript` and **no** `endscript`, and for all ten
`Act.img` and `Say.img` are empty in both states. That is why the job change has to be scripted.

| quest | NPC | level | prereq | job |
|---|---|---|---|---|
| 22100 | 1013000 Mir | 10 | 22007 completed | 2001 → 2200 |
| 22101 | 1013000 Mir | 20 | 22100 | 2200 → 2210 |
| 22102 | 1013000 Mir | 30 | 22101 | 2210 → 2211 |
| 22103 | 1013000 Mir | 40 | 22102 | 2211 → 2212 |
| 22104 | 1013000 Mir | 50 | 22103 | 2212 → 2213 |
| 22105 | 1013000 Mir | 60 | 22104 | 2213 → 2214 |
| 22106 | 1013000 Mir | 80 | 22105 | 2214 → 2215 |
| 22107 | 1013000 Mir | 100 | 22106 | 2215 → 2216 |
| 22108 | 1013000 Mir | 120 | 22107 | 2216 → 2217 |
| 22109 | 1013000 Mir | 160 | 22108 | 2217 → 2218 |

`QuestInfo.img/22100`–`22109` all carry `autoStart=1` and `autoAccept=1`, so the client raises
these itself and `QuestActionHandler.isNpcNearby` short-circuits on `quest.isAutoStart()`. **Mir
does not need to be placed on a map for the chain to work.**

## Which orion scripts were skipped, and why

Cosmic only ever runs a quest script when `Check.img` for that quest carries `startscript` or
`endscript` — `Quest.hasScriptRequirement`, gated in `QuestScriptManager.start`/`end`. Exactly 36
of the 45 Evan scripts Cosmic lacked correspond to a quest that carries one. The other 9 are files
Cosmic can never invoke:

| orion file | why not ported |
|---|---|
| `22012.js`, `22606.js`, `22607.js` | **the quest does not exist in v84 at all** — no `QuestInfo`/`Check`/`Act` node. v90-only content. `22012.js` is also a byte-for-byte copy of `22010.js`. |
| `22009.js`, `22010.js`, `22300.js`, `22505.js`, `22506.js`, `22587.js` | quest exists in v84 but `Check.img` has **neither `startscript` nor `endscript`**, so `hasScriptRequirement` is false and Cosmic never loads the file. These quests run entirely off `Act.img`, which already pays them. Adding the files would be dead weight, not function. |

Two of those nine are broken anyway and should not be revived as-is: `22506.js` has an unterminated
string literal (`qm.sendNext("#b(You ask the Milk Cow to give you some milk.);`) and would fail
`ScriptEvaluationTest`; `22514.js` calls `forceCompleteQuest()` inside `start()`.

Three more orion files were ported but **not** as orion wrote them, because orion's versions skip
the quest's own requirements: `22575.js` force-completes in `start()` and hands over the reward
item, bypassing the 150-item `Check` requirement; `22578.js` and `22585.js` likewise complete
inside `start()`.

## Cosmic capability gap found — `sp` quest rewards are silently dropped

`server/quest/QuestActionType.getByWZName` has no case for `"sp"`, so it returns `UNDEFINED`.
**28 Evan quests carry an `Act.img` `sp` reward that Cosmic therefore discards**: 22500, 22506,
22509, 22510, 22511, 22518, 22521, 22524, 22527, 22528, 22530, 22531, 22532, 22533, 22539, 22547,
22552, 22553, 22557, 22559, 22561, 22562, 22566, 22569, 22574, 22575, 22576, 22580.

This is Evan's SP economy, and it is **not fixed here** — it is Java, and ticket 14 owns Evan SP.
It is also inert until ticket 13 merges the quest data, so it cannot be verified today. The fix is
a `SpAction` beside `ExpAction` plus an enum case; note the wz node is nested
(`sp/0/{sp_value, job/0}`), so it is not a one-line copy of `ExpAction`. **Ticket 14 should pick
this up** — without it an Evan reaches 2218 with roughly 28 SP missing.

## Blocked on ticket 13, by construction

`wz/Quest.wz/QuestInfo.img.xml` in this tree contains **zero** `22xxx` entries, so none of these
scripts — nor the 13 that were already here — can fire yet. `Quest.hasScriptRequirement` reads
`Check.img`; with no node there is no script requirement and `QuestScriptManager` disposes without
loading the file. Ticket 13 owns that merge (`09-v84-quests.md`: "135 are the Evan chain (ticket
13's)"). Nothing in this ticket can move that.

Also worth recording for 13: **`Quest.java` reads `QuestInfo.img`, `Act.img` and `Check.img` only —
never `Say.img`.** All Evan quest dialogue must therefore come from scripts. `Say.img/0` is empty
for every quest scripted here except 22401/22403/22406, which hold a six-page `#L0#` branching
conversation that is not replayed — flavour, not function, and noted in those files.

## Verification

- Full suite **2072 passed, 0 failed** (`mvnw -o test`). Pre-change baseline in this tree was
  **2010** (the ticket's quoted 2,008 is slightly stale); +36 from `ScriptEvaluationTest`'s
  per-file parametrisation, +26 from the new test.
- Server startup does **not** parse quest scripts — they are evaluated lazily by
  `AbstractScriptManager.getInvocableScriptEngine` on first click. `ScriptEvaluationTest` calls
  that same method on all 311 quest scripts, so it is a stronger check than a boot, not a weaker
  one. No boot was spent.
- `EvanJobAdvancementScriptTest` drives all ten advancement scripts through a stub `qm` and asserts
  the chain, the level gate and the job gate, plus a reflection check that every `qm.*` name the
  scripts call still exists on `QuestActionManager`.
- **Not verifiable without a client:** that the client raises these quests at the right moment, and
  the dialogue rendering. Both need ticket 13's merge first. Fold into the next batched launch.
