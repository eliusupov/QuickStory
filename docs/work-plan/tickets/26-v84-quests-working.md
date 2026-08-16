# 26 — Quests end to end on the v84 client

Branch `worktree-evan-dualblade`. Suite **2096 passed, 0 failed**.

---

## TL;DR

Quest 1021 is **not** broken by data, requirements, NPC placement, opcodes or packet shape. All of
those are now measured, not assumed. The server-side path is correct end to end: a fresh beginner
standing next to Roger, fed the owner's exact capture bytes, gets an NPC talk packet back
(`Quest1021RealLoad`, passing).

What the path *does* have is **six gates that return without sending anything and without logging
anything**. That is the whole reason eight QUEST_ACTIONs produced silence — not because nothing
happened, but because whichever gate closed had no voice. Every one of them now names itself at
`DEBUG`, and root is already `trace` in `log4j2.xml`, so the next live click prints the answer with
no config change.

One of those six is a **proven, reproducible permanent lockout** and is my leading candidate — see
[The wedge](#the-wedge). It is proven to produce this exact symptom; it is *not* proven to be the
one that bit him. That distinction is deliberate.

---

## Ruled out, with evidence

Everything the dispatch listed as already ruled out held up. These are the ones I re-measured or
newly killed:

| Hypothesis | Verdict | Evidence |
|---|---|---|
| `Check.img/1021` doesn't survive the WZ load | **dead** | Real `Quest.getInstance(1021)` off the real tree yields `startReqs=[JOB, NPC, SCRIPT]`, `completeReqs=[ITEM, NPC, SCRIPT]`, `hasScriptRequirement(false)=true`, `getNpcRequirement(false)=2000` |
| `scripts/quest/1021.js` missing / uncoded | **dead** | File present; loads through the real Graal manager and reaches `sendNext` |
| NPC 2000 not in map 20000's `life` | **dead** | `wz/Map.wz/Map/Map0/000020000.img.xml`, `life/0` = npc `0002000` at (233, 58) |
| `QUEST_ACTION` opcode wrong at v84 | **dead** | `0x6B` in *both* `recvops-83.properties:93` and `recvops-84.properties:97` |
| Server running the v83 opcode table under a v84 handshake | **dead** | `tools/v84/cutover-server.log` — `Loaded 307 sendops opcodes from classpath:opcodes/sendops-84.properties`. Independently: `SET_FIELD` moved `0x7D → 0x80`, so a v83 table would strand him at character select, and it doesn't |
| `Job.getById(2001)` returns null, killing every Evan job gate | **dead** | `Job.java:59` — `EVAN(2001)` exists |
| Quest id overflows the `readShort` at `QuestActionHandler.java:73` | **dead** | Max id in `QuestInfo.img.xml` is 29940 of 3016 ids, all numeric; nothing over 32767, so `loadAllQuests`' `parseInt` is safe too |
| `qm.showInfo("UI/tutorial.img/28")` in 1021.js step 5 crashes the v84 client | **dead** | `wz/UI.wz/tutorial.img.xml` carries top-level nodes 0–29, 31, 32 |
| Quest send-side packets are version-sensitive | **dead** | Audited every quest builder in `PacketCreator` (forfeit/complete/updateQuest/updateQuestInfo/time-limit/NPC talk/showInfo/special effect/`addQuestInfo`). **Zero** version gates; layouts unconditional. Only the opcode *numbers* move, and those resolve by name |

## What was measured to work

`src/test/java/server/Quest1021RealLoad.java` — new, run with
`mvnw.cmd test -Dtest=Quest1021RealLoad`. Not a `*Test` class, same `WZFiles.DIRECTORY`
per-JVM-static race that `V84EvanQuestRealLoad` documents.

It feeds `QuestActionHandler.handlePacket` the **verbatim capture bytes**
`04 FD 03 D0 07 00 00 B5 FF 13 01` with a real `Quest` load and the real script manager, only
`Client`/`Character`/`MapleMap` stubbed:

1. `scriptedStartOf1021AnswersWithNpcTalk` — beginner at (205,215), Roger at (233,58), no
   `queststatus` row → a packet comes back. **Passes.**
2. `wrongJobStartsNothing` — negative control, same bytes from job 2200 → nothing. **Passes**, so
   test 1 is not a handler that answers unconditionally.
3. `closingTheFirstDialogueWedgesEveryLaterQuestAction` — see below. **Passes.**

## The wedge

The one gate that turns a single mistake into permanent silence.

`QuestScriptManager.start` returns immediately if `qms.containsKey(c)` — no packet, no log, no
expiry. A session gets stuck there like this:

- 1021.js step 0 sends `sendNext`. The dialogue has a **Next button and no Prev**.
- The player closes it with the window X instead of clicking Next.
- The client sends NPC_TALK_MORE with `lastMsg=0, action=0`; `NPCMoreTalkHandler.java:58-70`
  forwards it as `mode=0, type=0`.
- 1021.js disposes only on `mode == -1` or `mode == 0 && type > 0`. Here it takes `status--`, lands
  on `-1`, matches no branch, and **returns without disposing**.
- The `QuestActionManager` stays in `qms` forever.

From then on: every QUEST_ACTION returns at `qms.containsKey(c)` (silent), **and** `NPCTalkHandler`
refuses to start any NPC conversation at all (`NPCTalkHandler.java:63` — `if (c.getCM() != null ||
c.getQM() != null) return;`), **and** every quest update is queued instead of sent
(`Character.java:9528` puts it in `npcUpdateQuests`, drained only by `flushDelayedUpdateQuests`,
whose only two callers are the two script-manager dispose paths). A map change or a relog clears it
(`Character.closePlayerInteractions` → `Client.closePlayerScriptInteractions`, `Client.java:1562`) —
and map 20000 is a tiny room a tutorial player has little reason to leave.

Test 3 above reproduces exactly this and asserts the following two QUEST_ACTIONs send nothing.

**Why I did not fix it tonight.** The fix is not one line. `mode == 0` is the *same value* for "Prev"
and for "End chat" — the server cannot tell them apart without recording whether the dialogue it
last sent had a Prev button. That is a change to `NPCConversationManager`'s send methods plus the
continuation path in both script managers, touching every one of the 311 quest scripts and ~1000 NPC
scripts, on a night when a confident-but-wrong fix already cost a live test cycle. The log line
settles whether it is the real cause first; see [Next](#next).

## What changed

Behaviour is unchanged. No packet bytes move, so the v83 path is byte-exact and no
`ServerConstants.VERSION >= 84` gate is needed.

- **`QuestActionHandler.java`** — one `DEBUG` line on entry naming action/quest/player (QUEST_ACTION
  is in `LoggingUtil.ignoredDebugRecvPackets`, so even `USE_DEBUG_SHOW_RCVD_PACKET` would not have
  shown it), plus a line each for the two `isNpcNearby` denials (npc not spawned / npc too far).
- **`Quest.java`** — `canStart` and `canComplete` now name the *specific* unmet
  `QuestRequirementType`, or the status, or the infoEx mismatch. Iterating `entrySet` instead of
  `values` is the whole cost. Logging in the shared method, not the handler, covers every caller.
  `canComplete` suppresses it when `npcid == null`, because that is
  `Character.raiseQuestMobCount` probing on **every mob kill** — one line per quest per kill
  otherwise. (Root is `trace`; this would have been the spammiest log in the server.)
- **`QuestScriptManager.java`** — a `DEBUG` line on each of the five silent returns in `start`/`end`:
  session already open (naming the wedged quest id), npc-click cooldown, no `startscript`/`endscript`
  in `Check.img`, and the `end` precondition (status / npc-on-map / autoComplete, with the values).
  Inverted `if (c.canClickNPC())` to an early return so the body stops being doubly nested.
- **`OpcodeTable.verify()`** — warns if the loaded table version differs from
  `ServerConstants.VERSION`. `launch.bat` does not pass `-Dopcode-version`, so a build that moves
  `VERSION` without it handshakes as one version and speaks the other; recv ids largely agree, so
  packets still arrive and decode and only the send side silently lands on the wrong client handler.
  Warn rather than throw — a mismatched pair is legitimate while bisecting. Default unchanged,
  `launch.bat` untouched.

### Proving the instrument

The suite runs under `src/test/resources/log4j2-test.xml`, whose root is `off` — so "tests pass" says
nothing about whether these lines emit. Re-run under the production config:

```
mvnw.cmd -o test -Dtest=Quest1021RealLoad "-DargLine=-Dlog4j2.configurationFile=file:src/main/resources/log4j2.xml"
```

Actual output:

```
DEBUG handlers.QuestActionHandler - QUEST_ACTION action 4 quest 1021 from Tester
DEBUG quest.QuestScriptManager - START quest 1021 ignored: Tester already has quest script 1021 open
DEBUG quest.Quest - Quest 1021 start denied for Tester: unmet JOB requirement (npc 2000)
```

## Quests as a whole

- **Evan chain can fire, and its entry gate is 22007, not 22100.** `Check.img/22100/0` wants job
  **2001**, level **10**, npc 1013000, and **quest 22007 in state 2 (completed)**. 22007 in turn
  needs 22006, wants item 4032451, and completes through `endscript q22007e`. New gate
  `the1stAdvancementsPrerequisiteQuestExistsAndIsFinishable` asserts that link and that
  `scripts/quest/22007.js` exists — without it the ten advancement scripts are unreachable however
  correct they are.
- **Script coverage is complete.** Of the 135 merged Evan ids, 49 declare a `startscript`/`endscript`
  and **all 49 have their file**; the other 86 are data-driven through `Act.img`. Zero would hit
  `"Quest N is uncoded"`.
- **The `sp` reward is wired on a real load.** `SpActionTest` only ever drove hand-written XML. New
  gate `anSpRewardQuestLoadsAnSpActionIntoItsCompleteActs` asserts `Quest.getInstance(22500)` puts a
  real `SpAction` into `completeActs` — i.e. that `Quest.getAction`'s switch, not just the class,
  works. 28 quests carry `sp`, all in `22500`–`22580`, all in phase `1`.
- **Send side is version-clean.** See the ruled-out table. The opcode *numbers* that move between the
  tables and matter to quests: `UPDATE_QUEST_INFO 0xD3→0xD7`, `NPC_TALK 0x130→0x137`,
  `SHOW_ITEM_GAIN_INCHAT 0xCE→0xD2`, `SHOW_FOREIGN_EFFECT 0xC6→0xCA`, `SET_FIELD 0x7D→0x80`. All
  resolve by name; the live server has the v84 table loaded.

## Cannot be verified without a client

1. **Which of the six gates actually closed on his machine.** The instrument answers this in one
   click. Everything above narrows it to: session already open (the wedge), npc-click cooldown, NPC
   not spawned server-side, or a requirement that his character genuinely fails. It is no longer
   possible for the answer to be "nothing at all".
2. **`addQuestInfo`'s count widths** (`PacketCreator.java:393-419`). Both the started and completed
   counts are `writeShort`. If v84 widened either to int, everything after the quest block in the
   character-data packet desyncs — and the trock block right after it is 60 fixed bytes, which would
   absorb the slip into a crash much later and much less obviously. Not audited by ticket 25.
   **This is the best remaining candidate for "crashed on completing his first quest"**, since that
   packet is rewritten on the next map change / relog after a quest moves.
3. Whether the v84 client renders `UI/tutorial.img/28`. The server tree has the node; the client's
   own `UI.wz` is the one that matters and is out of bounds.

## Next

The smallest discriminating experiment, in order:

1. Click Roger once on the live server and read the log. One line names the gate.
2. If it says **"already has quest script N open"** — the wedge is confirmed and the fix is: record
   on `NPCConversationManager` whether the dialogue just sent carried a Prev button, and in
   `QuestScriptManager.start/end(mode, type, selection)` and `NPCScriptManager.action` treat
   `mode == 0` on a no-Prev dialogue as end-chat and dispose. Ship it with a test that walks 1021.js
   forward and back through `sendNextPrev` to prove Prev still works.
3. If it says **"npc 2000 is not spawned on map 20000"** — the map's `life` node is in the tree, so
   the fault is in map loading, and that is the `wz/`-owning agent's ground.
4. If it says **"unmet JOB requirement"** — his character is not job 0, whatever the DB row reads.

## Deferred

- **`AbstractScriptManager` script-engine cache is a no-op with a leak.** `getInvocableScriptEngine`
  reads with key `"scripts/" + path` (`:71`) but writes with key `path` (`:74`), and `resetContext`
  removes `"scripts/" + path` (`:90`). So the cache never hits, the removal never removes, and
  `Client.engines` grows one dead entry per distinct script forever. Harmless today precisely
  *because* it never hits — a fresh engine per conversation is what the scripts' module-level
  `var status` needs. Fixing the key would silently start reusing engines across conversations and
  is exactly the kind of change not to make blind. If it gets fixed, the safe direction is to delete
  the caching, not to repair it.
- **`Quest.complete` swallows an action failure silently** (`Quest.java:364-366`): after `canComplete`
  passes, any `AbstractQuestAction.check` returning false aborts with nothing sent. Same class of
  bug as the ones instrumented here; not on 1021's path, so not touched.
- **`ScriptRequirement`'s constructor passes `QuestRequirementType.BUFF` to `super`**
  (`ScriptRequirement.java:35`). Harmless — `Quest` keys the map by the type it parsed from the WZ
  name, never by `req.getType()` — but it is a landmine for anyone who later trusts that field.
