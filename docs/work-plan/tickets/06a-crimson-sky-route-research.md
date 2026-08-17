# 06a — Crimson Sky: the access route, resolved from data

**Companion to [`06-crimson-sky.md`](06-crimson-sky.md), whose criterion 5 ("travel route works")
was left unchecked as an owner decision.** This file answers *how v84 intended the route to work*.
It ships no code. Every claim below is a read of a pristine archive or of this repo; nothing is
inferred from how MapleStory "usually" works.

Evidence tool: `docs/wz-baseline/tool-peek/` (WzPeek), modes `dump / portals / life / scan`.
Archives: `porting-resources/wz-data/v84/` and `.../v83-stock/`.

---

## The headline, and it overturns the working hypothesis

The hypothesis under test was: *Crimson Sky is entered by flying in on Evan's dragon mount, so it is
Evan-only content.* **Refuted.** The flying key is a different skill, granted by a different quest,
to every class in the game.

| | dragon mount | Crimson Sky flight |
|---|---|---|
| skill | `20011004` | `1026` / `10001026` / `20001026` / `20011026` |
| granted by | `Act.img/22402` | `Act.img/3759` "Towards the Sky 2" |
| `job` list on the grant | 2200, 2210–2218 — **Evan only** | jobs 0–522 + 1000–1512 + 2100–2112 + 2200–2218 + 2001 — **every job** |
| skill name in v84 | (mount) | `플라잉` (Flying); English reward text calls it **"Soaring"** |

`Act.img/3759/1/skill` holds four entries, one per beginner block, each with its own `job` list:

- `1026` → job `0`, and every Explorer job `100`–`522` (42 entries)
- `10001026` → every Cygnus job `1000`–`1512` (21 entries)
- `20001026` → every Aran job `2100`–`2112` (4 entries)
- `20011026` → every Evan job `2200`–`2218`, plus `2001` (11 entries)

That is the standard Nexon "one reward, split across the four job families" shape. **Crimson Sky is
all-class content.** The mount chain 22400–22413 and its PlayerNPC `9901000` blocker are unrelated
to it.

All four skill ids are v84-new (absent from `v83-stock/Skill.wz`) and **all four are already merged
into this repo**: `wz/Skill.wz/000.img.xml:2368` (`0001026`), `1000.img.xml:2432`,
`2000.img.xml:2904`, `2001.img.xml:2196`. Note the ids are zero-padded in the Explorer block —
`000.img/skill/0001026`, not `1026`; searching for the unpadded form finds nothing and reads as a
false negative.

---

## The quest chain is the route, and it is already in this repo

Quests **3756–3761**, all absent from `v83-stock/Quest.wz`, all present in `wz/Quest.wz/`
(`QuestInfo.img.xml:16336+`, `Check.img.xml:52924+`, `Act.img.xml:5464+`, `Say.img.xml:4413+`).

| id | name | start | complete | needs |
|---|---|---|---|---|
| 3756 | The Dragon Rider's Identity 1 | 2081000 Tatamo, `autoStart`, **lvmin 100** | 2081000 | — |
| 3757 | The Dragon Rider's Identity 2 | 2081000 | **2085000 Matada** | — |
| 3758 | Towards the Sky 1 | 2085000 | 2085000 | 4000240 ×1, 2020015 ×1, **4001402 ×10** |
| 3759 | Towards the Sky 2 | 2085000 | 2085000, `endscript q3759e` | **4032531 ×1** → grants Soaring |
| 3760 | Dragonica's Horn | 2081000 | 2081000 | 4001401 ×1 |
| 3761 | Tears of Repentance | **2085003 Dragon Rider** | 2081000 | — |

`Check.img` carries **no `job` field on any of the six**. The only gate is 3756's `lvmin 100`.

The prose states the route in as many words. `Say.img/3757/0/2`: the Dragon Rider "was last seen in
**The Forest That Disappeared**" (`240030102`). `Say.img/3757/1/yes/0` is Matada answering, on the
far side: the Rider "headed with the Dragon toward the **&lt;Crimson Sky Doorway&gt;**".
`Say.img/3758/0/0`: "you'll have to enter through the **&lt;Crimson Sky Doorway&gt;** to the Crimson
Sky area". And `Say.img/3759/1/yes/1`, which is the whole design in one line:

> "the Soaring skill **can only be used in the Crimson Sky area, including the &lt;Crimson Sky
> Dock&gt;**. Also, your MP will be continuously depleted while you're flying"

So: walk to `240030102`, meet Matada on `240080000` Crimson Sky Dock, do 3758/3759 for Soaring, then
the Doorway NPCs take you through the stages.

---

## The gap: v84 ships no edge into `240080000`, and this is measured, not sampled

`WzPeek scan v84/Map.wz tm 240080000` — **every one of the 4,848 images in v84's `Map.wz`** —
returns **zero hits**. No portal anywhere in v84 targets Crimson Sky Dock. The scan is not
vacuous: the same scan for `tm 240030102` returns the two real edges
(`240030100/portal/13`, `240080000/portal/1`), so the method finds what is there.

The only edge v84 declares is **one-way, and points outward**:

```
240080000/portal/1   pn=left00  pt=2  x=-512  y=80  tm=240030102  tn=right00
```

`240030102` has five portals — four `sp` and `out00 → 240030100/in01` — and is **byte-identical
between `v83-stock` and `v84`** (absent from both `add-list/Map.txt` and `modified-list/Map.txt`;
portal dumps of the two archives are line-for-line equal). **There is no `right00`.** Nexon shipped
the return half of a portal pair and never shipped the entrance.

The one Leafre-block map v84 *did* edit is `240000000` Leafre town, and the edit is not a route: v84
**adds `unityPortal2`** at index 4 (shifting every later index by one) and **removes** life entry
`1022101`. Diffed portal-by-portal and life-by-life against `v83-stock`. No new destination.

Everything else was ruled out by direct read, not assumption:

- `onUserEnter = Sky_GateMapEnter` occurs on **exactly one map in all of v84**, and it is
  `240080000` itself — it runs *after* arrival, so it cannot be the way in.
- No v84 quest references any `240080xxx` map: `scan Quest.wz map 240080000` and `fieldEnter
  240080000` both return zero, as do the same scans for `240080100`, `240080800`, `683010000`.
- No `mapNo` (world-map) node references `240080000`.
- Tatamo `2081000`'s `info/script` is `job4_item` in **both** v83 and v84 — unchanged, so v84 did
  not hang the route off an existing NPC.
- The three Crimson Sky scripts are carried by three NPCs and nothing else, all inside the area:
  `scan v84/Npc.wz script SkyGate|Sky_Train|skyquest` → `2085001`, `2085000`, `2085002`, one hit each.
- `scan v84/Map.wz id <npc>` places `2085000` on `240080000` only; `2085001` on `240080000` and
  `240080100`; `2085002` on `240080800`; **`2085003` on nothing at all**.

**Conclusion.** The authentic route is a walk-in portal `240030102 → 240080000`, and the node for it
does not exist in either vendor's data. Authoring it means inventing an `x`/`y` on `240030102`,
which is exactly the fabrication this project has twice refused. It also cannot be done server-side
alone: a `pt=2` portal the client does not draw is a portal the client never sends an enter packet
for, so a server-only XML splice is inert. **This remains an owner decision, now with the evidence
attached rather than a guess.**

---

## What actually blocks each quest today, named precisely

Quests here are data-driven. `QuestActionHandler` cases 1/2 start and complete a quest without any
script; only `Check.img`'s `startscript`/`endscript` route to `scripts/quest/<id>.js`. Of the six,
**only 3759 declares one** (`endscript q3759e`) and **`scripts/quest/3759.js` already exists**
(committed by ticket 09/10) — it reads the player's job and calls `qm.teachSkill` with the matching
one of the four Soaring ids. So **missing NPC scripts are not the blocker**; none of
`2085000`–`2085003` needs one for the chain to function.

| quest | blocked by |
|---|---|
| 3756 | nothing — startable and completable at Tatamo today, at level 100 |
| 3757 | **completes at Matada `2085000`, who exists only on the unreachable `240080000`** |
| 3758 | same, plus **`4001402` "Dragon's Essence" has no drop row anywhere in `db/data/`** |
| 3759 | same, plus **`4032531` "Dragon Moss Extract" has no source** — `Say.img/3759/0/yes/0` says Tatamo supplies it, so it wants a branch on `scripts/npc/2081000.js`, whose "Do something for Leafre" option currently answers "Under development..." |
| 3760 | `4001401` "Dragonica's Horn" drops from `8300006` Dragonica at 500000 (`160-monsterbook-drop-data.sql:229`) — Dragonica is on `240080600`, i.e. inside |
| 3761 | **NPC `2085003` Dragon Rider is placed by no map in v84** |

Items `4000240` Small Flaming Feather and `2020015` Sunset Dew are ordinary pre-existing items.

**One root blocker, then: reaching `240080000`.** Everything else is downstream of it.

---

## Does this server support flying maps? No, and it does not need to

`MapFactory.loadMapFromWz` (`src/main/java/server/maps/MapFactory.java:133-336`) is the sole reader
of a map's `info` node. It reads `link`, `mobRate`, `returnMap`, `onFirstUserEnter`, `onUserEnter`,
`fieldLimit`, `createMobInterval`, `timeMob`, `VRTop/Bottom/Left/Right`, `everlast`, `town`, `decHP`,
`protectItem`, `forcedReturn`, `timeLimit`, `fieldType`, `fixedMobCapacity`, `recovery`.
**`fly` and `needSkillForFly` are never read**, anywhere in `src/main/java`. Movement handling has no
flying state either: `AbstractMovementPacketHandler.parseMovement` switches on raw command bytes and
stores the stance verbatim; there is no `FLY` constant and no vehicle branch. `FieldLimit` has no
flying bit.

This is not a defect. `200090500` / `200090510` ("In Flight", Leafre ↔ Temple of Time) are v83 maps
carrying `fly=1` **without** `needSkillForFly`, they work today, and their server-side support is
nothing but two ordinary portal scripts (`scripts/portal/undodraco.js`, `templeenter.js`) —
`undodraco.js` is five lines. Flight is drawn and validated by the client; `needSkillForFly` is the
client checking your own job block's Soaring skill before it lets you off the ground, which is
exactly what `Say.img/3759/1/yes/1` describes. **So no flying mechanic has to be built.** Grant the
skill and the client does the rest.

All 17 `needSkillForFly=1` maps in the tree are the Crimson Sky block and nothing else.

## `20011004` today

Fully wired as a mount, not a no-op: `constants/skills/Evan.java:10`, recognised by
`StatEffect.isMonsterRidingSkill` (`StatEffect.java:1704-1717`), cast path
`SpecialMoveHandler.java:131-141` → `applyBuffEffect` (`:1343-1367`), which rides whatever taming-mob
item sits in slot -18 (mount id 0 if the slot is empty). Irrelevant to Crimson Sky.

---

## A defect found on the way, reported not fixed

`src/main/java/server/quest/actions/SkillAction.java:82`

```java
boolean shouldLearn = skill.jobsContains(chr.getJob()) || skillObject.isBeginnerSkill();
```

`Skill.isBeginnerSkill()` is `id % 10000000 < 10000` (`client/Skill.java:85-87`). For the Soaring
ids that is `true` for `1026`, `10001026` and `20001026`, and `false` for `20011026` (`% 10^7` =
11026 — the same `% 10000000` blind spot already documented at `StatEffect.java:1705-1709`). So the
`||` **defeats any `job` list Nexon declared on a beginner-block skill**: a data-driven quest grant
would hand every class the Cygnus and Aran flying skills.

It does not affect Crimson Sky, because `scripts/quest/3759.js` bypasses `SkillAction` and picks the
id by job itself. Left alone deliberately: quest/skill code is another agent's lane this sprint, and
the fix (honour a declared job list; fall back to `isBeginnerSkill()` only when the entry declares no
`job` node) changes behaviour for every quest in the game, not just this one. Worth its own ticket.

---

## The Dragon Rider PQ — what it would take, recorded so nobody re-researches

Out of scope by owner decision. The apparatus, read off the data:

**Maps** (`String.wz/Map.img/ossyria`, all `streetName = Leafre`): `240080000` Crimson Sky Dock →
`240080100`–`240080500` Crimson Sky 1–5 → `240080600` Crimson Sky Edge → `240080700` Crimson Sky
Nest Entrance → `240080800` Crimson Sky Nest. Plus `240080040`/`041` Crimson Sky Resurrection Site
and `240080050`/`051` Cave of the Deceased.

**The `x00`/`x01` pairing.** Each `x01` is an `info/link` to its `x00` — same geometry, own `info`.
The `x01` variants carry `mobRate` 2.5–3.0 (vs 1.0), `fieldLimit` 303865 (vs the `x00` value),
`returnMap = 240080041` (the resurrection site), `forcedReturn = 240080000`, and
`onUserEnter = Sky_StageEnter` where the `x00` maps have none. `240080701` adds
`onFirstUserEnter = Sky_TrapFEnter`. `240080801` is the boss room: `fly=0`, `lvLimit=100`,
`consumeItemCoolTime=20`, `onUserEnter = Sky_BossEnter`. **So `x00` is the walk-through version and
`x01` is the run instance** — that pairing is the instancing scheme, and it is the first thing to
get right.

**Server scripts v84 names and this repo does not have** (none of these files exists):

| kind | name | where |
|---|---|---|
| portal | `Sky_Enter` | `240080000/enter00` |
| portal | `Sky_Next` | `240080100`–`240080700`, the forward edge |
| portal | `Sky_Previous` | `240080200`–`240080600`, the back edge |
| portal | `Sky_Out` | `240080100/out00` |
| portal | `SkyCave+out` | `240080700/out00` |
| portal | `Sky_BossOut` | `240080800/out00` |
| portal | `Sky_BossSummon` | `240080800/boss00`, `boss01` |
| portal | `Sky_ReviveOut` | all four resurrection / cave maps |
| map `onUserEnter` | `Sky_GateMapEnter` | `240080000` |
| map `onUserEnter` | `Sky_StageEnter` | every `x01` |
| map `onUserEnter` | `Sky_BossEnter` | `240080801` |
| map `onFirstUserEnter` | `Sky_TrapFEnter` | `240080701` |
| map `onUserEnter` | `dragonLair_GL` | `683010000` |
| npc | `Sky_Train` | `2085000` Matada |
| npc | `SkyGate` | `2085001` Crimson Sky Doorway |
| npc | `skyquest` | `2085002` Crimson Sky Doorway |

A missing map script is a **silent** no-op (`MapScriptManager.java:72-74` returns before logging), so
none of these announces itself at runtime. A missing portal script is equally silent
(`PortalScriptManager.java` returns `false`, no log).

**Population.** `8300000`–`8300006` are placed and drop-tabled (ticket 06). `9500374`–`9500382` are
placed by nothing and summoned by nothing. `8300007` Dragon Rider is placed by nothing and has no
drop table, yet quest 3761 needs NPC `2085003` "Dragon Rider" which no map places either — the
boss-to-NPC transition after `Sky_BossSummon` is the missing piece there.
