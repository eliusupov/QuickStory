# 61 - the 31 Map.wz reactor nodes the server actually reads and our tree does not have

**Class:** v84 parity
**Work rows:** R11 - `docs/work-plan/V84-WORK-ROWS.tsv`
**Blocked by:** None - can start immediately

`docs/work-plan/V84-COVERAGE.tsv` lists **40** rows with `archive=Map` and `read_by=GAP`. Three of
them belong to ticket 54. Of the remaining 37, only **31** are nodes the server reads: the reactor
arrays. The other six were audited out and the reasoning is recorded below so nobody re-adds them.

This ticket is **31 reactor entries across seven maps**. It is smaller than it looks and it is
almost entirely latent.

## R11 - 31 server-read reactor entries are missing

| map | missing | count | reactor id |
|---|---|---:|---|
| **109090300** | `reactor/14` through `reactor/31` | 18 | 1002002 / 1002003 |
| **230040200** | `reactor/5`, `/6`, `/7` | 3 | 2302006 |
| **230020000** | `reactor/1`, `/2` | 2 | 2302006 |
| **230040000** | `reactor/5`, `/6` | 2 | 2302006 |
| **230040100** | `reactor/5`, `/6` | 2 | 2302006 |
| **230040400** | `reactor/7`, `/8` | 2 | 2302006 |
| **230010400** | `reactor/1` | 1 | 2302006 |
| | | **31** | |

`MapFactory.java:294-298` reads the `reactor` array and `loadReactor:356-362` reads each entry's
`id`, `x`, `y`, `f` and `reactorTime`, so a missing index is a reactor that does not exist on the
running server. Values from the v84 carve via `WzPeek`; paths in `add-list/Map.txt`.

### What this actually buys, per map

**Be clear about the payoff before writing the edit - it is small.**

* **109090300** is "Sheep Ranch Event" (`String.wz/Map.img.xml:2361-2363`), an event map. 18 of the 31
  rows are here and nothing routes players to it today.
* **The 12 Aquarium rows are all reactor `2302006`, and `2302006` has zero `reactordrops` rows and
  no `scripts/reactor/2302006.js`.** Adding them spawns twelve reactors that drop nothing and run
  nothing. See the open question below - that may be the actual defect, but it is not this ticket's
  to fix.

There is **no live blocker** in this ticket. It is an array-length parity edit.

### Open question this ticket raises and does not answer

The earlier revision claimed the six Aquarium maps "carry the treasure chests ticket 43's quest
22407 route depends on". Measured, that is not so - but what is underneath it may matter more.

* Quest 22407's own text (`wz/Quest.wz/QuestInfo.img.xml:7356`) asks for `4032476` from
  "**Shipwreck** Treasure Chests deep in the oceans of Aquaroad".
* `wz/Reactor.wz/2302006.img.xml:4` = `난파선의 보물상자` = "**shipwreck's** treasure chest".
* `wz/Reactor.wz/2302001.img.xml:4` = `심해보물상자:심해먼지` = "**deep sea** treasure chest".
* `156-evan-chain-drop-data.sql:261-266` puts the 22407 drop row on **2302001** and justifies it by
  asserting 2302001's info string "is the Korean for exactly that". It is not - it says deep sea,
  and the quest says shipwreck.
* Our tree has all seven `2302001` placements on 230040400 and **zero** `2302006` placements
  anywhere. `2302006` appears in no changeSet and no script.

**So changeSet 156 may have put quest 22407's buckle on the wrong reactor, and the right one is
exactly what this ticket would add.** That is a question for whoever owns ticket 43 and changeSet
156. **Do not resolve it here** - this ticket adds the nodes and nothing else. If 156 is corrected
later, these 12 rows become load-bearing; until then they are inert.

## What was in this ticket and is now removed

Six rows were dropped after checking what reads them. Recorded so they are not re-added:

| dropped | why |
|---|---|
| `120000105/ladderRope/1` | **`ladderRope` is read nowhere.** Zero hits across `src/main/java` and `scripts/`. `MapFactory` never touches it. Client-only. |
| `220011001/info/fly`, `/swim`, `/noMapCmd` | **All three are read nowhere.** Zero hits in `src/main/java`. Carve values are `0`, `0`, `0` - the defaults - so even a reader would see no change. |
| `220011001/info/onUserEnter`, `/onFirstUserEnter` | Read by `MapFactory.java:157-161`, but **both are the empty string in the carve.** `MapFactory` does `onEnter.equals("") ? String.valueOf(mapid) : onEnter`, which is byte-for-byte the same result as the node being absent. Merging them is a literal no-op. |

Two more were never in scope despite being listed:

* `220011001/2/info/tS` and `/2/info/tSMag` are `read_by=**client**` at
  `V84-COVERAGE.tsv:10758-10759`, not `GAP`. The earlier revision defined its own scope as "rows
  with `archive=Map` and `read_by=GAP`" and then counted two rows its own tool excludes. That is
  also why its table summed to 39 while its title said 40.

### `102000003/life/1` is refused, permanently

The earlier revision presented this as the ticket's one live row and its safest edit. It is neither.

The carve's `102000003/life/1` is npc **9901000** - `String.wz/Npc.img.xml:8489` reads
*"I am /name, who achieved Lv. 200."* It is a Hall-of-Fame statue, not an NPC.

* **9901000 is allocated at runtime.** `PlayerNPCPodium.java:120-123` names it *"the branch's lowest
  scriptid, 9901000 for warriors, which quest 22402 needs"*.
* **changeSet 163 already seats a real PlayerNPC there.** `163-hall-of-fame-data.sql:35` inserts
  scriptid 9901000 on map **102000004** (Hall of Warriors), which is where
  `Etc.wz/NpcLocation.img` puts it - not on 102000003.
* **A static `life` row would be the wrong object type.** `QuestActionHandler.java:54-58`: *"A
  Hall-of-Fame PlayerNPC is a `MapObjectType.PLAYER_NPC` and never an `NPC`, so `getNPCById` cannot
  see one - quest 22402 names 9901000."* Adding a static row creates a plain `NPC` with that id, so
  `questNpcPosition` would resolve 9901000 to the static object on the wrong map instead of falling
  through to `getPlayerNPCByScriptId`.
* **Ticket 53 already refused this class of row permanently.** Section at `53:110-122`: *"v84's 96
  static `9901xxx` life rows on the seven Hall of Fame maps are refused, permanently"*, and it names
  9901000 explicitly at `:122` as one of the ids changeSet 163 seats.

The Warrior job instructor the earlier revision cited as the urgency is **npc 1022000, already
present as `life/0`** (`wz/Map.wz/Map/Map1/102000003.img.xml:37-47`). Nothing about that map is
broken.

## Precedent

- **Ticket 53** - the whole-image `foothold`/`life`/`portal` take across 52 maps. It states the rules
  this ticket inherits: v84 owns the client-facing fields and the slot position; `script` is
  server-only and ours wins; custom nodes are appended past v84's last index. It is also the
  standing refusal for `9901xxx` life rows.
- **Ticket 46** is the standing refusal that governs `life` arrays generally: taking v84's `life`
  array verbatim on `196000000` would have deleted the Cafe PQ stage-5 NPC.
- Values from `porting-resources/wz-data/v84/Map.wz`. **That path is not repo-relative** - the carve
  lives at `D:\games\MapleStory\Server\porting-resources\wz-data\v84\Map.wz`, one directory above
  the repo root. `SOURCES.md` tier 1, **read-only**.
- `Map.wz` in this tree is **CRLF**. `SOURCES.md` names the trap: a Python round-trip silently
  rewrites the whole file.

## The shared indices do not match, and that governs the edit

`reactor` is an array. A missing index N means our array is shorter than v84's. It does **not**
follow that a present index N holds the same content, and on the biggest map here it demonstrably
does not.

Measured on **109090300**, indices 0-13, ours against the carve:

| index | ours | carve |
|---:|---|---|
| 0, 1, 2, 3, 4, 9, 11 | *(match)* | *(match)* |
| 5 | 1002001 | **1002003** |
| 6 | 1002001 | **1002003** |
| 7 | 1002001 | **1009000** |
| 8 | 1002001 | **1009000** |
| 10 | 1002003 | **1002002** |
| 12 | 1009000 | **1002002** |
| 13 | 1009000 | **1002003** |

**7 of the 14 shared indices carry a different reactor id.** Appending 14-31 from the carve produces
an array that is v84's length and is still not v84's content. The earlier revision's acceptance
criterion - append 14-31 *and* keep 0-13 byte-identical - guarantees the map never reaches parity
and should not be written that way.

Ticket 53 is the precedent for resolving this: compare the shared indices on content before
appending, and decide per index. That decision is this ticket's real work, not the append.

## Acceptance criteria

- [ ] `109090300` has `reactor` indices 0-31 consecutive, with 14-31 matching the carve node for
      node. For indices 0-13, each of the seven divergences above is either taken from the carve or
      left as ours **with the reason recorded in the commit** - not silently left.
- [ ] Each of `230010400`, `230020000`, `230040000`, `230040100`, `230040200` and `230040400` has a
      `reactor` array whose length equals v84's, with the 12 added entries matching the carve and
      every pre-existing entry unchanged. All 12 added entries are id `2302006`.
- [ ] A `*RealLoad` test loads all seven maps through `MapFactory` and asserts the reactor count and
      the id at every index, so a later divergence fails rather than drifts.
- [ ] The test records that `2302006` has no `reactordrops` rows and no reactor script, so the 12
      added reactors are inert by design and not by accident.
- [ ] Each edited file's diff is confined to the reactor arrays, with line endings preserved (CRLF).
- [ ] Re-running `python tools/playthrough/v84coverage.py` drops the `Map` GAP count from **40** to
      **9**: the 3 ticket-54 portal/script rows, plus the 6 rows this ticket deliberately declines
      (`ladderRope/1`, `info/fly`, `info/swim`, `info/noMapCmd`, `info/onUserEnter`,
      `info/onFirstUserEnter`) - or the tool is taught to classify those 6 as `client`/benign, in
      which case it drops to 3. Either is acceptable; the commit says which was done.

## Do not

- Do not add `102000003/life/1`. It is npc 9901000, ticket 53 refuses it permanently, changeSet 163
  seats a real PlayerNPC with that id on 102000004, and quest 22402 depends on the PlayerNPC path.
- Do not re-add `ladderRope`, `info/fly`, `info/swim` or `info/noMapCmd`. Nothing reads any of them.
- Do not add `220011001/info/onUserEnter` or `/onFirstUserEnter`. Both are empty strings in the carve
  and `MapFactory` already produces the identical result from their absence.
- Do not add `2/info/tS` or `/2/info/tSMag`. They are `read_by=client`, not `GAP`.
- Do not merge `220011000/portal/4/script`, `220011000/portal/4/horizontalImpact` or
  `106010101/portal/5/script`. They are **ticket 54's**, all three of them. Note that ticket 54:44-49
  records the old "106010101 is a deliberate divergence" objection as **retired** - commits
  `80917ee28` and `6c7fb781d` settled it - so do not repeat that claim as a reason to refuse.
- Do not "fix" changeSet 156's reactor id for quest 22407 in this ticket. Record the question, add
  the nodes, stop.
- Do not take any reactor array wholesale. This is an append of the missing indices plus a per-index
  decision on the shared ones.
- Do not delete a row to make our array match v84's length. Ticket 46 governs.
- Do not renumber or compact an array. `characters.spawnpoint` stores an index and
  `PortalFactory.loadPortal` addresses by node name; a shift strands stored values.
- Do not round-trip a `Map.wz` XML file through a writer that normalises line endings.
