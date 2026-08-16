# 44 — Medal-quest fallback, the "missing 7631 writer", and the orphan quest scripts

**Status:** two of three findings are measurement + correction; the third (quests 1048–1054) is a
deliberate **stop**, not a fix. Everything below is reproducible against `wz/` and the test class
`src/test/java/server/MedalQuestFallbackRealLoad.java`.

```
mvnw.cmd -o test -Dtest=MedalQuestFallbackRealLoad
```

---

## 1. Quests 1048–1054 are retired event content. No writer was implemented, and none should be.

The premise handed to this ticket was that quests 1048–1054 gate on quest-progress keys **7631**
(an Explorer job-recommendation survey) and **1055** (a Cygnus recommendation), that nothing in the
server writes those keys, and that a Java writer is the missing piece.

The keys are real. `wz/Quest.wz/Check.img.xml` gives 1049–1053 `infoNumber 7631` with `infoex`
values `100`/`200`/`300`/`400`/`500` — Warrior / Magician / Bowman / Thief / Pirate — and 1054
`infoNumber 1055` with `infoex "clear"`. So the survey shape is exactly as described.

**But every one of the seven carries an expired `end` date, and the end date is checked first.**

| quest | name | `Check.img/<id>/0/end` |
|---|---|---|
| 1048 | Job Recommendation | `2009010100` |
| 1049 | Becoming a Warrior | `2009010100` |
| 1050 | Becoming a Magician | `2009010100` |
| 1051 | Becoming a Bowman | `2009010100` |
| 1052 | Becoming a Thief | `2009010100` |
| 1053 | Becoming a Pirate | `2009010100` |
| 1054 | Cygnus Knights | `2009020200` |

- `server/quest/QuestRequirementType.java:76` maps the WZ key `end` to `END_DATE`.
- `server/quest/requirements/EndDateRequirement.java:54-58` parses that string and returns
  `false` once the date is past.
- `server/quest/Quest.java:288-302` — `canStart` walks **every** start requirement (the loop at
  295-301) and refuses on the first unmet one.
- `net/server/channel/handlers/QuestActionHandler.java:140-146` (action 4, scripted start) calls
  `quest.canStart` *before* `QuestScriptManager.start`.

GMS v84 shipped in late 2009. These dates are January and February **2009** — they were already
dead when the client this server targets was current. `wz/Quest.wz/Act.img.xml` confirms it from
the other side: `1048/0`, `1048/1`, `1049/0`, `1049/1`, `1054/0`, `1054/1` are all empty imgdirs.
No rewards, no actions, nothing on the far side to reach.

**Decision: stop.** Writing a setter for 7631 would produce a survey GMS had already retired,
feeding quests that `canStart` refuses and that award nothing. That is invention, not parity. The
writing mechanism itself is not missing, for the record — `Character.setQuestProgress(int, int,
String)` (`client/Character.java:9460`) exposed to scripts as
`AbstractPlayerInteraction.setQuestProgress` (`scripting/AbstractPlayerInteraction.java:406-411`)
is exactly it, and live quests use it. The v84 startscripts `q1048s`–`q1054s` would have called it.
They are absent because the content is.

Pinned by `MedalQuestFallbackRealLoad#quests1048To1054AreRetiredEventContent`.

### Side observation, deliberately not fixed

`EndDateRequirement` line 56 calls `Calendar.set(year, month, ...)` with the WZ month verbatim.
`Calendar` months are 0-based, so `...01...` means February and `...12...` rolls into January of
the next year — every end date is honoured roughly one month late. Immaterial to this ticket (all
the dates involved are years past) but it is a real off-by-one. Left alone because correcting it
*removes* access to any quest currently living inside that one-month grace window, and this branch
is additive-only.

---

## 2. Medal-quest fallback: the routing was right, the collapse of two NPC visits into one was not.

90 quests in `QuestInfo.img` carry `viewMedalItem`; `Quest.java:141-144` loads it into `medals`,
which is what `GameConstants.isMedalQuest` (`constants/game/GameConstants.java:610`) tests.
**39** of them declare a start or end script in `Check.img` and ship no `.js` of their own, so
`QuestScriptManager.getQuestScriptEngine` (`scripting/quest/QuestScriptManager.java:52-55`) routes
them to `scripts/quest/medalQuest.js`.

**The fallback's grant was already authentic, and the previous claim that it "grants the medal
anyway" is not accurate — it grants nothing at all.** `Act.img` is empty for all 39. What
`forceStartQuest`/`forceCompleteQuest` do is flip the quest status, which is what registers the
medal in the client's Medal tab. The gate is in `Check.img` and `QuestActionHandler` enforces it
before the script loads:

| gate | count | example |
|---|---|---|
| requires the medal item itself | 24 | 29910 Gallant Warrior requires item `1142009`, which *is* the Gallant Warrior medal |
| requires a finished prerequisite chain | 9 | 29904 Noblesse requires quest 20000 completed, job 1000-1512 |
| level / interval only | 6 | 29400 Veteran Hunter, 29503 Donation King, 29508 Outstanding Citizen |

So for 33 of 39 the fallback is the whole real flow and the only wrong thing in the file was the
line telling the player the quest was "not coded".

**What was genuinely wrong:** eight of the 39 declare **both** a `startscript` and an `endscript` —
29002, 29400, 29500, 29501, 29502, 29503, 29505, 29506, the Title Challenges from Dalair
(`9000040`/`9000066`) and Spiegelmann. Two scripts is two NPC visits: accept the challenge, then
come back and claim it. 29501's own `QuestInfo` text says it outright — *"Dalair said that if I
hunt a Horned Tail and don't report it to him, it won't count."* None of the eight is
`autoStart`. The old `start()` called `forceCompleteQuest()` unconditionally, collapsing accept
and claim into one click.

`scripts/quest/medalQuest.js` now completes inside `start()` only when
`Quest.hasScriptRequirement(true)` is false — i.e. only when there is no second visit to complete
on. `end()` is unchanged in effect. No Java was added: the script reaches `Quest` through
`Java.type`, the same interop five other quest scripts already use (e.g.
`scripts/quest/20514.js:34`).

### Known gap, deliberately not invented

What those eight challenges actually measure — a million monsters in 30 days, +1,000 fame, the
server-wide Horned Tail / Pink Bean kill leaderboard, Monster Carnival win rate, marriage + guild +
one Junior for 29508 — is **not in `Quest.wz` at all** and is modelled nowhere on this server.
Nexon held it in their own server script. The two-visit shape is what the data proves; the counter
is not, and nothing here fakes one. Any player who reaches the NPC still gets the title on the
second click.

`29508` Outstanding Citizen is the only medal quest of the 39 whose `QuestInfo` declares a
`rewardSummary` (`#v1142081:# 1`). It has an endscript and no startscript, so it was never affected
by the collapse.

---

## 3. Orphan quest scripts: 7, not 16–19, and one of the eight suspects is not an orphan at all.

`scripts/quest/` holds 315 `.js`: 312 named after an id, plus `medalQuest.js`,
`unidentifiedQuest.js` and `3414_free10rate.js`. **8** of the 312 name an id that does not exist in
`Check.img`, `QuestInfo.img` or `Act.img`, and that no quest declares as an `infoNumber` (82
infoNumbers are declared in this tree; none of the eight appears).

**`20514.js` is live and is not an orphan.** Quest scripts are not reached only by quest id.
`RaiseUIStateHandler` (`net/server/channel/handlers/RaiseUIStateHandler.java:19-27`) reads an
*infoNumber* straight off the packet and passes it to `QuestScriptManager.raiseOpen`
(`scripting/quest/QuestScriptManager.java:187-208`), which loads `quest/<that number>.js`. The
client takes that number from the item: `wz/Item.wz/Etc/0422.img.xml:2237` gives Mimiana's Egg
(`4220137`) `questId 20514`. So opening the egg UI loads `scripts/quest/20514.js`, whose
`raiseOpen()` is the only handler for the Mimiana raising chain. Deleting it — or "fixing" it to a
GMS-numbered quest — would have broken working content. **Any future orphan sweep must cross-check
`Item.wz` `questId` and `Check.img` `infoNumber`, not just quest ids.**

The remaining 7 are content this WZ tree never had, all of it post-v84:

| file | what it is | why absent |
|---|---|---|
| `2560.js`, `2561.js`, `2568.js`, `2570.js`, `2573.js` | Cannoneer intro — the shipwreck island, the monkey, the Ignition Device, Kyrin's `changeJobById(501)`, Skipper's warp to Lith Harbor | Cannoneer is GMS 2011, ~v1.2.99. No quest in 2500–2599 exists in this `Check.img`. |
| `23011.js` | Battle Mage 2nd job advancement (`changeJobById(3200)`) | Resistance is GMS Dec 2010, post-Big-Bang. No quest in 23000–23099 exists. |
| `10940.js` | an "event season" gift quest handing out `2430191` | no quest in 10900–10999 exists |

**No misnamings found.** Not one of the seven is a near-miss for a quest that does exist — the
whole id block is absent in every case, which is the signature of un-merged content rather than a
typo. Nothing renamed, nothing deleted (additive-only). The only cross-reference any of them has
outside itself is `scripts/portal/bedroom_out.js:2`, which gates on quest 2570 — also dead, and
owned by another agent.

---

## Verification

| | |
|---|---|
| new test | `src/test/java/server/MedalQuestFallbackRealLoad.java`, 6 tests |
| mutation check, fix disabled | 2 failures — `aMedalQuestWithItsOwnEndScriptIsOnlyStarted` (got `[STARTED, COMPLETED]`) and `theFallbackSourceNoLongerTellsThePlayerTheQuestIsNotCoded` |
| mutation check, fix re-enabled | 6/6 pass |
| full suite | 2162 run, 0 failures, 0 errors |

`theFallbackSourceNoLongerTellsThePlayerTheQuestIsNotCoded` asserts the **source**, not behaviour,
and the test says so in its javadoc. The natural behavioural assertion — `verify(chr,
never()).message(...)` — cannot fail here: the line before it in the old fallback was
`qm.getMedalName()`, which initialises `ItemInformationProvider`, whose constructor reads the
database (`loadCardIdData`, `ItemInformationProvider.java:1546`). With no database the class fails
to initialise, the script dies at that point and `QuestScriptManager`'s catch-all disposes, so the
mock never sees `message` whichever version of the script is on disk. A test that cannot fail is
not evidence, so it was replaced by one that can, honestly labelled. The same limitation is why
`aMedalQuestWithoutAnEndScriptStillCompletesOnTheSpot` asserts no dispose — the status capture
happens before the throw and is real; a dispose assertion there would be vacuous.
