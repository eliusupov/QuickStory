# 29 — make events actually run

**What this is:** an audit of all 108 `scripts/event/*.js`, a recurring config-driven replacement for
the one date-scheduled event, and the fix for a *second* hardcoded-2015 timestamp that nobody had
noticed — one that has been silently zeroing the PQ bonus EXP reward on every party quest.

**Status:** delivered. **0 failures throughout.** This ticket adds **14 tests**; the suite went
`2096 → 2110` on my changes alone. The final run in this worktree reported **2122 passed, 0 failed**
because other agents' work (`cdaecd678` ticket 31, plus an untracked `V84ContentMergeNodeTest`)
landed in the same tree while this ticket was in progress — those extra tests are not mine, and the
number is recorded here only so it reconciles rather than looking like an unexplained jump.

**Branch:** `worktree-evan-dualblade`. Files touched are event scripts, event Java, `config.yaml`
and one test. Nothing under `wz/`, `scripts/quest/**` or `tools/PacketCreator.java` was written.

---

## Instrument check first — the brief's correction was wrong, and ticket 27 was right

The dispatch for this ticket asserted that `2xEvent.js`'s scheduler calls were **live**, and that
ticket 27's "commented out" claim was a plausible-sounding error that would have led to the wrong
fix. That is backwards. Read the file at `HEAD` before this ticket's changes:

```js
function init() {
    /*
        if(em.getChannelServer().getId() == 1) { // Only run on channel 1.
        timer1 = em.scheduleAtTimestamp("start", 1428220800000);
        ...
        */
}
```

The `/*` opens on line 35 and the `*/` closes on line 44. The block **is** commented out. Verified
three ways: the file on disk in this worktree, `git show master:scripts/event/2xEvent.js` (identical),
and `git log -- scripts/event/2xEvent.js` (last touched by `a12feaf3e`, a reformat — the block has
been commented since `972517e7b`, the original import).

**Both descriptions were individually true and neither was the whole defect.** The timestamps really
are April 2015 epochs *and* the whole block is dead code. Ticket 27's wording ("its entire `init()`
scheduling block is commented out") is accurate.

**This matters because "just uncomment it" is actively harmful, not merely insufficient.**
`EventScriptScheduler.registerEntry` stores `currentTime + duration`, and `runBaseSchedule` fires any
entry whose stored time `< timeNow` (`src/main/java/scripting/event/scheduler/EventScriptScheduler.java:77-80`).
A past timestamp therefore does **not** silently never fire — it fires on the *next monitor tick*. So
uncommenting those four lines would, at every boot:

1. run `start()` → `world.setExpRate(8)` + a world-wide broadcast, then
2. run `stop()` a tick later → `world.setExpRate(4)` + another broadcast,

and leave the world permanently at **4x EXP**, because the old `stop()` hardcoded `4` rather than
restoring the configured rate (`config.yaml` `exp_rate: 1`). A "fix" that uncommented the block would
have quadrupled server EXP forever and looked like it worked.

---

## The bigger find: a second hardcoded 2015 epoch, live in Java, killing PQ bonus EXP

Auditing for the *class* of defect rather than the one named instance turned up this:

```java
// src/main/java/scripting/AbstractPlayerInteraction.java:850   (before this ticket)
if (YamlConfig.config.server.PQ_BONUS_EXP_RATE > 0 && System.currentTimeMillis() <= YamlConfig.config.server.EVENT_END_TIMESTAMP) {
    player.gainExp((int) (exp * YamlConfig.config.server.PQ_BONUS_EXP_RATE), true, true);
}
```

with `config.yaml:445` shipping `EVENT_END_TIMESTAMP: 1428897600000` — **2015-04-13**.

This one is not commented out. It is live, it is in Java, it runs on every `gainPartyExp` call, and
`System.currentTimeMillis() <= 1428897600000` has been false since April 2015. `PQ_BONUS_EXP_RATE`
was set to `0.5` and documented as "Rate for the PQ exp reward" — **and has never once paid out**.
This affects all 18 PQs, not a seasonal event, which makes it higher-value than the 2x EXP script.

A repo-wide sweep for any other 13-digit epoch literal in `scripts/`, `config.yaml` and
`src/main/java` returns only bitmask constants in `BuffStat.java` / `Disease.java`. These two were the
only hardcoded timestamps. The class of defect is now closed.

---

## Per-script audit — all 108

Classification was mechanical: comments stripped, then each script tested for a live `em.schedule(`
call, a live `scheduleAtTimestamp`, and whether any file outside `scripts/event/` names it (via
`getEventManager("X")`, `getEventManager("prefix" + n)`, or a plain string constant).

| class | count | how it runs | verdict |
|---|---|---|---|
| On-demand instance events | **70** | NPC/portal script calls `getEventManager(name)` | **fine as-is**, nothing to fix |
| Self-rescheduling timers | **35** | `init()` → `em.schedule(...)`, each run re-arms the next | **live**, working |
| Fallback template | **1** | `0_EXAMPLE`, removed from the map and used as the fallback EM (`EventScriptManager.java:66`) | by design |
| **Permanently dead** | **2** | see below | one fixed, one deliberately left |

**The 35 self-rescheduling ones are the proof that recurring scheduling already works on this
server.** 27 `AreaBoss*` spawners plus `AirPlane`, `Boats`, `Cabin`, `Elevator`, `Genie`, `Subway`,
`Trains`, `GuildQuest`. Pattern, from `AreaBossMano.js:29-33,59`:

```js
function init()  { scheduleNew(); }
function scheduleNew() { setupTask = em.schedule("start", 0); }
function start() { /* ...spawn... */ em.schedule("start", 30000); }   // re-arms itself
```

Relative delay, re-armed on each fire, no absolute timestamp anywhere. That is the mechanism this
ticket reuses rather than inventing one.

### The 2 dead scripts

| script | defect | action |
|---|---|---|
| `scripts/event/2xEvent.js` | only date-scheduled event in the tree; scheduling block commented out, and the timestamps inside it are April-2015 epochs | **rewritten** — see below |
| `scripts/event/BalrogBattle_Easy.js` | a complete, working Easy-mode Balrog expedition script with **zero references anywhere in the repo**. `scripts/npc/1061014.js:47` only ever asks for `"BalrogBattle"`; no difficulty selection exists | **deliberately left alone** — see "left alone" |

### Registration is not the problem, confirmed independently

`Channel.getEvents()` (`src/main/java/net/server/channel/Channel.java:454-466`) directory-scans
`scripts/event` with no whitelist and no config gate, so "present but unregistered" is empty by
construction. At boot `Channel` deliberately loads only `0_EXAMPLE`
(`Channel.java:142-150`) and the full set is loaded afterwards by
`Server.java:950-952` → `Channel.reloadEventScriptManager()` → `new EventScriptManager(this, getEvents())`,
whose constructor runs `init()` on all 108 (`EventScriptManager.java:58-66`). That path is sound.

---

## What I built: recurring, config-driven scheduling

**No new Java scheduler.** `EventManager` already offers everything needed and the 35 live scripts
already prove the pattern:

- `EventManager.schedule(method, delayMs)` — relative delay (`EventManager.java:164-181`)
- `EventManager.scheduleAtTimestamp(method, epochMs)` — absolute (`EventManager.java:183-194`)

`scheduleAtTimestamp` is the trap: an absolute epoch written into a script rots the moment it passes,
and fires immediately rather than never. **The rewritten `2xEvent.js` never calls it.** It computes
the next window's *start* at arming time and schedules a relative delay, then re-arms from `stop()`.
Nothing absolute is ever persisted, so it cannot go stale.

Schedule and rate live in `config.yaml`, not in the script:

```yaml
    TIMED_EXP_EVENT_DAYS: ""            #Comma-separated weekday names (MONDAY..SUNDAY). Empty = disabled.
    TIMED_EXP_EVENT_START_HOUR: 20      #Hour 0-23 the event starts on each listed day.
    TIMED_EXP_EVENT_DURATION_HOURS: 2   #How long the boosted rate lasts.
    TIMED_EXP_EVENT_MULTIPLIER: 2       #World exp rate is multiplied by this while the event runs.
```

Behaviour worth knowing:

- **Channel-1 only.** The exp rate is world-wide, so only one channel may drive it. The original had
  this guard inside the dead comment; it is preserved and now actually runs.
- **Multiplies, does not hardcode.** `start()` captures `world.getExpRate()` and sets
  `base * multiplier`; `stop()` restores the captured value. The old script's hardcoded `8`/`4` are
  gone, so the event can no longer contradict `config.yaml`'s `exp_rate`. The capture is guarded so a
  `start()` that somehow fired twice without an intervening `stop()` cannot capture the
  already-boosted rate and latch the multiplier in permanently — the one failure mode here that
  would need a manual rate reset to undo.
- **Survives a mid-window restart.** `nextWindowFrom` returns the first window whose *end* is still
  future, so booting at 21:00 during a 20:00–22:00 window resumes it with the remaining hour instead
  of skipping a week.
- **Zero cost when off.** An empty day list registers no scheduler entry at all, so
  `EventScriptScheduler`'s monitor task is never started.

### Three bugs review caught in my own rewrite

Worth recording, because two of them were worse than the bug this ticket set out to fix and none
would have shown up in a green test run:

1. **A week-long 2x EXP.** `start()` re-read `nextWindow()` and boosted unconditionally. The
   scheduler counts delays down on `Server.getCurrentTime()`, which is **not wall clock** —
   `CharacterDiseaseTask` advances it by a fixed `UPDATE_INTERVAL` step per tick and it only syncs to
   `System.currentTimeMillis()` once, at `TimerManager` start (`Server.java:178-190`,
   `TimerManager.java:88`). That clock only ever *lags*, and over a multi-day arm the lag accumulates,
   so `start` can fire after its window has closed. `nextWindow()` then returns **next week's**
   window: rate goes up immediately, `stop` gets scheduled up to seven days out. `start()` now
   re-arms instead of boosting when the window it finds has not opened yet.
2. **Midnight-crossing windows invisible.** `nextWindowFrom` searched from today forward only, so a
   23:00 start with a 2h duration could not be found at 00:30 — the server restarts half an hour into
   the event and the event silently never runs. Any duration over 24h had the same problem, worse.
   The search now starts `ceil(duration / 1 day)` days back; ascending offsets still return the
   earliest window and the `end > now` filter still drops finished ones.
3. **Compounding rate on `!reloadevents`.** `cancelSchedule()` cancelled the timers but left the rate
   boosted, and `Channel.reloadEventScriptManager()` then builds a **fresh script engine** where
   `baseExpRate` is back to `-1`. The next `start()` would capture the boosted rate as its base:
   2x → 4x → 8x, permanently, reachable from a GM command mid-event. `cancelSchedule()` now restores
   the rate first.

Each of the three has a test that was **confirmed to fail against the unfixed code** before the fix
was restored — `expected: <-1> but was: <2>` for the first, i.e. the rate really was boosted outside
its window. Also dropped the unused `timer3`/`timer4` carried over from the old script, and
`readSettings()` now rejects an out-of-range `START_HOUR` (24 would normalise into the next day and
shift the whole schedule) rather than running the event at the wrong time.

---

## Balance: everything defaults to today's actual in-game behaviour

Neither fix turns anything on. Both are explicitly the owner's call.

| knob | shipped now | effect | to enable |
|---|---|---|---|
| `TIMED_EXP_EVENT_DAYS` | `""` | 2x EXP event **never fires**; world rates untouched | set e.g. `"SATURDAY,SUNDAY"` |
| `PQ_BONUS_EXP_RATE` | `0` (was `0.5`) | PQ bonus EXP off — **identical to the last 11 years of real behaviour** | set `0.5` to restore the originally-intended bonus |
| `EVENT_END_TIMESTAMP` | `0` (was `1428897600000`) | `0` now means "no end date" instead of "expired in 2015" | set a future epoch to time-box a bonus |

`PQ_BONUS_EXP_RATE` was lowered from `0.5` to `0` **on purpose**. Once the 2015 gate is repaired the
`0.5` would have quietly started paying out — a +50% EXP buff on every party quest, arriving as a
side effect of a bug fix. Keeping the effective value (off) is the balance-neutral choice; the
comment in `config.yaml` says exactly this and names the one-character change to turn it on.

---

## Monster Carnival — the headline reachability case

**Verdict: entry is reachable and fully data-complete; two real Java/script defects were found and
fixed; the in-match loop depends on a launch flag that is not mine to change.**

First, the reachability sweep, because "is the NPC actually there" is the question that kills events
whose data is present. I extracted every NPC id placed in a `life` list across all **5,338**
`wz/Map.wz/Map/**/*.img.xml` files and checked it against every `scripts/npc/*.js` that calls
`getEventManager`:

```
MAP_FILES_SCANNED       = 5338
PLACED_NPC_IDS          = 1223
EVENT_ENTRY_NPC_SCRIPTS = 56
NOT_PLACED_ON_ANY_MAP   = 0
```

**All 56 event-entry NPCs are placed on a real map, including all 7 CPQ entry NPCs.** Nothing is
stranded.

That number is only trustworthy because the first version of the scan was wrong, so: the first pass
assumed life entries are ordered `type` then `id`, which is true in `100000000.img.xml` and false in
`103000000.img.xml` — the order varies per file. That parser reported `PLACED_NPC_IDS=1059` and
**three false positives** (Lakelis/KerningPQ, Amos/AmoriaPQ, and the AirPlane NPC) as "not placed
anywhere", which would have been three fabricated bugs. The rewritten parser is order-independent and
is calibrated against three known answers before its output is used (two ids verified placed by eye,
one nonexistent id verified absent). Instrument in the scratchpad, not committed.

### Defects found, and what I did

| # | defect | evidence | action |
|---|---|---|---|
| 1 | **Room reservation off by one.** `startCPQ`/`startCPQ2` derived the room from an arena *map id* (1-based within its series) but `fieldTaken`/`cpqLobby` and the NPC scripts use a 0-based index. Field 0 was therefore **never marked busy** (two parties could be dropped into the same arena) and field N+1 falsely read "currently full" whenever field N was running | `NPCConversationManager.java:805,857` vs `:652-667` and `Channel.java:1020-1033`; `field` arrives as `ldr.getMapId() + 1` from `MatchCheckerCPQChallenge.java:101,103` | **fixed** — both sites now call one shared `MonsterCarnival.roomOf(arenaMapId, cpq1)`; 3 new tests |
| 2 | **NPE on an offline party member.** `party.get(i).getPlayer().getMapId()` — `PartyCharacter.getPlayer()` is null when the member is offline, so the script threw and the NPC silently did nothing. Any leader with one offline party member could not open the field list at all | `scripts/npc/2042000.js:182` (and the byte-identical `2042001.js`/`2042002.js`), `2042005.js:47` | **fixed** — skip offline members, matching the `isOnline()` pattern already used in `MatchCheckerCPQChallenge.java:74` |
| 3 | **Dead party-size check.** `if (party >= 1)` where `party` is a `java.util.List` — coerces to `false`, so the branch is unreachable and the "you need 2–6 members" message never appears | same 4 files, `2042000.js:188`, `2042005.js:54` | **left alone**, deliberately — the *minimum* is already enforced at `2042000.js:215-218`; the only thing repairing it would add is the **maximum** party size of 6, which would start rejecting oversized parties that can enter today. That is a balance change, so it is the owner's call |

I verified defect 1 myself rather than taking it on report: `980000101 / 100 % 10 = 1` for field
index 0, against `fieldTaken(0)` querying room `0`. Confirmed for all 6 CPQ1 and all 3 CPQ2 fields.

### The thing I did not touch: `-Dopcode-version`

`launch.bat:4` runs `java -Xmx2048m -Dwz-path=wz -jar target\Cosmic.jar` with **no
`-Dopcode-version`**, so `OpcodeTable.java:48` falls back to its `"83"` default while
`ServerConstants.VERSION` is `84`. `MONSTER_CARNIVAL` recv is `0xDA` in `recvops-83.properties:168`
and `0xE0` in `recvops-84.properties:172`; `0xE0` has no entry at all in the 83 table, so a v84
client's summon/debuff/guardian packets hit no handler and the entire in-match loop is inert.

**I deliberately made no change here.** This ticket's hard rules forbid changing the
`-Dopcode-version` default, and this is not CPQ-specific — with the 83 table the send side is wrong
too (`SET_FIELD` `0x7D` vs `0x80`) and essentially nothing works, which means the owner is already
launching with the flag (`docs/work-plan/STATUS.md:8` says testing is done with
`-Dopcode-version=84`). Recording it because `launch.bat` disagrees with how the server is actually
being run, and that discrepancy will bite whoever uses the batch file next.

### Known CPQ defects left for a follow-up ticket

Found during the trace, all outside what "make events run" needs, none fixed here:

- `MonsterCarnival.java:84-98` — if `team1`/`team2` end up null the constructor `return`s *after*
  every player was already `forceChangeMap`'d into the arena and given `setMonsterCarnival(this)`.
  No timers, no dispose path: players are stranded in a disposable map with no exit. Needs a
  warp-out branch, not a one-liner.
- `MonsterCarnival.java:242-243` vs `:196-197` — `dispose()` nulls `map`, and a disconnect racing the
  match end can re-enter and NPE. A `if (map == null) return;` guard would do it.
- `Channel.java:100` — `usedMC` is an unsynchronised `HashSet` touched from both timer and script
  threads.
- `MonsterCarnivalParty.java` is entirely dead: `new MonsterCarnivalParty` has zero hits repo-wide.
  Its `warpOut()` encodes the *correct* 0-based room math, which is a decent tell for defect 1.
- `scripts/npc/2042005.js` has no `USE_CPQ` gate, unlike `2042000/1/2.js:28`, so CPQ2 stays enterable
  when CPQ is switched off.
- `MonsterCarnivalHandler.java:120-121` — `amount = size() - 1` then `random() * amount`, so a
  single-target debuff can never land on the last party member.

## Changes

| file | change |
|---|---|
| `scripts/event/2xEvent.js` | rewritten: config-driven recurring window, relative `em.schedule` re-arming, captures/restores the world exp rate instead of hardcoding 8/4 |
| `config.yaml` | added the 4 `TIMED_EXP_EVENT_*` keys (default off); `EVENT_END_TIMESTAMP` 2015 epoch → `0`; `PQ_BONUS_EXP_RATE` `0.5` → `0` with the reason inline |
| `src/main/java/config/ServerConfig.java` | 4 new fields for the above |
| `src/main/java/scripting/AbstractPlayerInteraction.java` | extracted `isWithinEventWindow()`; `EVENT_END_TIMESTAMP <= 0` now means "no end date" |
| `src/main/java/net/server/channel/Channel.java` | removed the redundant `eventSM.init()` at line 144 — the `EventScriptManager` constructor already inits, so every script's `init()` ran **twice** on any channel added while the server was online, double-arming all 35 self-rescheduling events |
| `src/main/java/server/partyquest/MonsterCarnival.java` | new `roomOf(arenaMapId, cpq1)` — one place that converts a CPQ arena map id to the 0-based field index the reservation set uses |
| `src/main/java/scripting/npc/NPCConversationManager.java` | `startCPQ`/`startCPQ2` call `roomOf` instead of their two off-by-one open-coded expressions |
| `scripts/npc/2042000.js`, `2042001.js`, `2042002.js`, `2042005.js` | skip offline party members instead of NPE-ing on `getPlayer()` |
| `src/test/java/scripting/TimedExpEventScheduleTest.java` | new, 12 tests |
| `src/test/java/server/partyquest/MonsterCarnivalRoomTest.java` | new, 2 tests |

### Tests

`TimedExpEventScheduleTest` loads the real `2xEvent.js` through `AbstractScriptManager`'s Graal
engine — no server, no channel, no `em` — and drives `nextWindowFrom(nowMillis, settings)`, which was
written as a pure function specifically so it could be tested this way. It covers: a window days
away, a window already underway (the restart case), roll-over to the following week once the window
closes, and the disabled case. Three more cover `readSettings()` — the config seam the owner will
actually touch when enabling the event: that the **shipped config parses to "disabled"** (which is
also what proves yamlbeans binds `TIMED_EXP_EVENT_DAYS: ""` the way the script expects), that
`" saturday , SUNDAY "` parses case- and whitespace-insensitively to the right day indices, and that
unusable settings (an unrecognised weekday, a zero-length window, a multiplier of 1) disable the
event rather than scheduling a no-op window. A final test pins `isWithinEventWindow()` against `0`,
the old 2015 epoch, and a future timestamp.

Four more drive the **arm/start/stop chain itself**, which is the part that was actually broken.
`loadScriptWithStubs()` injects a fake `em`, `getWorld` and `announce` into the same engine, so
`start()`, `stop()` and `cancelSchedule()` run with no server, channel or world behind them. Those
cover: `start()` firing outside its window must re-aim rather than boost; `start()` inside its window
must still boost (the control — otherwise the guard could be satisfied by never starting at all); and
`cancelSchedule()` must put the rate back. All three were confirmed red against the unfixed code.

`MonsterCarnivalRoomTest` walks every CPQ1 arena (`980000101`–`980000601`) and every CPQ2 arena
(`980031001`–`980033001`) and asserts each resolves to the 0-based field index the NPC scripts offer.
Both fail against the old `(field / 100) % 10`, and the field-0 case inside them is the one that
allowed double-booking.

---

## Deliberately left alone

- **`BalrogBattle_Easy.js`** — works, but nothing routes to it. Wiring it up means adding a difficulty
  prompt to `scripts/npc/1061014.js` and deciding Easy-mode rewards. That is content design, not a bug
  fix, so it needs the owner's call rather than my guess.
- **The other 105 scripts** — 70 on-demand and 35 self-rescheduling, all reachable. No edits.
- **Seasonal/holiday content.** `HolidayPQ_1/2/3` are reachable today via `scripts/npc/9105004.js:65`
  and are **not** date-gated — the NPC offers them year-round. Making them genuinely seasonal is a
  design decision (which dates, and should the PQ vanish outside them?), not a defect. Flagged, not
  changed. `wz/Etc.wz/Halloween.img.xml` exists in the tree; the only `Halloween` token anywhere in
  `src/main/java` is the `"HalloweenGL"` map-BGM string at `MapFactory.java:399`, so nothing reads that
  image and no date logic hangs off it.
- **`scheduleAtTimestamp`** — left in `EventManager`. It is now unused by every script in the tree,
  but it is public API and deleting it is churn. Nothing calls it; if something does later, this
  ticket is the warning.

## Needs the owner's decision

1. **Do you want the 2x EXP event on, and when?** Days, start hour, duration, multiplier — four
   config lines, currently off. Server local time; the machine's timezone is what players will see.
2. **Do you want PQ bonus EXP paying out?** `PQ_BONUS_EXP_RATE: 0.5` was the original intent and has
   never worked. Turning it on is a real +50% PQ EXP buff.
3. **Easy-mode Balrog** — wire it to an NPC difficulty choice, or delete the script?
4. **Should holiday PQs be date-gated** rather than always available?
5. **Should the CPQ maximum party size (6) be enforced?** It is unenforced today because of a dead
   check; repairing it would start rejecting oversized parties that can currently enter.
6. **`launch.bat` has no `-Dopcode-version=84`** while `ServerConstants.VERSION` is 84. Left alone per
   this ticket's rules, but the batch file does not match how the server is actually run.

## Not verifiable without a live client

- That the `serverNotice(6, ...)` broadcast renders as intended in the v84 client. The packet path is
  untouched by this ticket (same call the old script made), but no one has seen it fire.
- That a rate change mid-session updates a connected client's displayed EXP correctly.
  `World.setExpRate` reverts and re-applies per-character world rates
  (`World.java:363-379`), which is the same path the existing `!exprate` GM command uses, so it is as
  proven as that command is — no more.
- Wall-clock behaviour across a DST boundary. The window is computed from local calendar fields, so a
  20:00 start stays 20:00 local across a DST shift; the *duration* is fixed milliseconds, so a window
  spanning the shift is an hour long in wall-clock terms. Not worth code until someone cares.
