# 05 — v84 mounts rideable

**Blocked by:** 03

**Status:** ready-for-agent

**Merge procedure: [`../WZ-MERGE-PROCEDURE.md`](../WZ-MERGE-PROCEDURE.md)** — established by ticket 03 and proven end to end. Use its tool (`docs/wz-baseline/tool-merge/`); do not invent a second way. Start with a dry run (`WzMerge merge <v84>/X.wz <live>/X.wz - <add-list> <conflicts>`) and read the conflicts before merging anything.

## What to build

The eight mounts v84 added — Soaring Hawk, Soaring Eagle, Soaring Red/Blue/Black Wyvern, Soaring Griffey, Dragonica, Dragon Rider (`8300000`–`8300007`) — can be obtained, mounted, ridden and dismounted.

A mount is a vertical slice in its own right: it needs the mount data, the mount item, the riding skill, and the server-side monster-riding buff to line up. Cosmic already handles monster riding for every existing mount, so the server work should be configuration rather than new code — confirm that early, because if it is not true this ticket is larger than it looks.

## Where the data actually is — corrected 2026-08-15 (ticket 02f)

> **Do not look in `TamingMob.wz`.** It is a **797-byte stub** — seven near-empty images
> `0001.img`–`0007.img` — in v83, v84 *and* the live client, with **zero** v84 additions. It has
> never held mount definitions in this era. v83's and the live client's copies are byte-identical;
> v84's differs only in header bytes. It serves only as an index target: `info/tamingMob` is an
> integer pointing into it.

Mounts live in **`Character.wz/TamingMob/`** (v83 47 → v84 55). The eight v84 additions are
`Character.wz/TamingMob/019320{06,07,08,09,11,18,19,20}.img` — see
`docs/wz-baseline/add-list/Character.txt:140-147`. All eight are absent from the live client and
are real equips, not stubs: e.g. `01932006.img/info` = `islot=Tm, vslot=Tm, reqLevel=13,
tamingMob=6, tradeBlock=1, notSale=1, only=1`, with a full
`walk1/walk2/stand1/stand2/tired/jump/prone/ladder/rope/fly` animation set. **None has a
`String.wz` name in either stock tree** — naming them is part of this ticket.

Three other places this ticket must touch, none of them obvious:

1. **`String.wz/Eqp.img/Eqp/Taming`** (v83 33 → v84 39 names).
2. **Evan's Mir is here and a `Character.wz` diff cannot see it.** v84 *names* six ids whose
   sprites already shipped in v83: `1902040/41/42` = "Stage 1/2/3 Dragon" and `1912033/34/35` =
   their saddles. Presence-only diffing misses this entirely because the sprites are not new — only
   the `String.wz` entries are. This is the patch's player-facing mount and it overlaps ticket 13.
3. **`Morph.wz`** — v84 adds `fly2` / `fly2Move` / `fly2Skill` to every morph image plus
   `0050`–`0053.img` (flying-mount morph states). Skipping this likely means a flying mount that
   renders wrong in flight.

Also newly visible now that `Sound.wz` and `Effect.wz` are baselined:
`Sound.wz/Bgm00.img/DragonDream`, `Bgm14.img/DragonRider`; `Effect.wz/BasicEff.img/DragonChanged`,
`dragonFury`, `Direction4.img`, `SetEff.img/101`–`115`.

**The ids in the paragraph above (`8300000`–`8300007`) were written from the naming manifest and
have not been verified against the node-level data.** The count of eight matches, but reconcile the
id sets before building anything.

## Acceptance criteria

- [x] All eight mounts present in client WZ and server XML
- [ ] Each can be obtained, mounted, ridden across a map, and dismounted — **human**
- [ ] Riding buff applies and expires correctly — **human**
- [ ] Existing mounts still work — no regression to the shared riding path — **human in game.**
      Server side: guarded by `V84MountNodeTest.v83MountsStillMapToTheSameSprites`, written against
      the `constants.skills` constants. (This line used to name
      `v84MountSkillsMapToTheirSprites` and the literals `20001017 -> 1932003` /
      `10001019 -> 1932005`; §3 shows both literals are wrong — `20001017` is not a skill and
      `10001019` is Cygnus's Yeti, not a broomstick — and §6 explains why the other test is a
      change-detector rather than evidence. Corrected by 03f.)

---

# RESULT — 2026-08-16

## 1. The mount ids, reconciled. The original list was WRONG; the node-level list is right.

Three id sets were in play. They are **three different things**, not three readings of one thing.

| set | what it actually is | owner |
|---|---|---|
| `019320{06,07,08,09,11,18,19,20}` | the eight v84 **mount sprites** (`Character.wz/TamingMob/*.img`) | **05 — correct** |
| `830000{0..7}` | eight v84 **Crimson Sky mobs** you fight | 06 |
| `000102{5,7,8,9}` / `00010{30,37,38,39}` | the eight **skills** that grant them | 05 |

**`8300000`–`8300007` are not mounts.** `Mob.wz/8300000.img/info` (v84) is `level=110 maxHP=150000
exp=6000 bodyAttack=1 mobType=2` — a hostile mob. The ticket's original list came from a name
manifest and the names *are* mount-like ("Soaring Hawk", "Dragonica"), which is exactly how the
confusion started; 06 found the same ids in the Crimson Sky maps' `life` nodes, which is where mobs
belong. 06 has merged all eight (`docs/wz-baseline/merge-lists/06/Mob.paths.txt`). 05 takes a
dependency on that and adds nothing.

**The orchestrator's linking hypothesis — that `info/tamingMob` resolves to `Mob.wz/830000x` — is
false, measured not assumed.** `tamingMob` is `6` for `01932006` and `7` for the other seven: a
1-based index into `TamingMob.wz/000N.img`, the 797-byte seven-image speed/jump/fatigue table. v83's
own Yeti mount `01932003` already reads `tamingMob=6`, and `TamingMob.wz/0006.img/info` is
`speed=120 jump=120 fs=10 swim=100 fatigue=3`. It is a movement class, not an id. Locked down by
`V84MountNodeTest.tamingMobIndexesTheMovementTableNotAMob`.

The reconciled table. Skill ids shown for Explorer; each exists identically at `1000xxxx`
(Noblesse) and `2000xxxx` (Legend), and Evan's `2001xxxx` copies live in `Skill.wz/2001.img`,
which is **ticket 13's**, not merged here.

| skill | name (v84 `String.wz/Skill.img`) | sprite | movement class |
|---|---|---|---|
| `0001025` | Charge! Wooden Pony | `01932006` | 6 (speed 120) |
| `0001026` | **Soaring** — flight, *not* a mount | — | — |
| `0001027` | Croco | `01932007` | 7 (speed 140) |
| `0001028` | Black Scooter | `01932008` | 7 |
| `0001029` | Pink Scooter | `01932009` | 7 |
| `0001030` | Nimbus Cloud | `01932011` | 7 |
| `0001037` | Unicorn | `01932018` | 7 |
| `0001038` | Low Rider | `01932019` | 7 |
| `0001039` | Red Truck | `01932020` | 7 |

Nothing in the WZ states the skill→sprite pairing — it is hardcoded in the client. Corroborated
against two independent reimplementations of this client era, agreeing row for row:
`porting-resources/reference-sources/Rebirth95-csharp/src/Rebirth/Characters/Skill/Buff/BuffSkill.cs:310-323`
and `porting-resources/reference-sources/HaRepacker-src/HaCreator/MapSimulator/Character/Skills/SkillManager.cs:8762-8774`.

## 2. The server riding path — **configuration, not new code.** Confirmed.

The ticket's premise holds. Every mechanism is id-agnostic and already worked:

| step | where | id-specific? |
|---|---|---|
| mount object | `src/main/java/client/Character.java:7462-7467` — `mount(id, skillid)` just sets two fields | no |
| buff application | `src/main/java/server/StatEffect.java:1307-1329` | only the sprite lookup |
| buff stat | `src/main/java/client/BuffStat.java:119` `MONSTER_RIDING` | no |
| client packet | `src/main/java/tools/PacketCreator.java:2842`, `:2868` | no |
| hunger / fatigue tick | `Character.java:9365-9368` via `registerMountHunger` | no |
| dismount on death | `Character.java:1263-1264` | no |
| cancel in no-mount fields | `src/main/java/server/maps/MapleMap.java:2539-2541` | no |
| buff expiry | generic `cancelEffect` path, `Character.java:7529-7530` | no |
| is-this-a-buff classification | `src/main/java/client/SkillFactory.java:152` — `isBuff = effect != null && hit == null && ball == null`, read from WZ | **no — data-driven**, so the nine new skills classify themselves |

**One honest qualification.** "Configuration" here does not mean a config *file*: the enumeration of
which skill ids are mounts is a hardcoded Java list. Before this ticket it was two of them —
a `switch (sourceid)` at `StatEffect.java:510-530` (HEAD) and an if-chain at `:1299-1306` — and
adding eight mounts × four beginner jobs would have meant 32 new `case` labels in one and 8 new
`else if` legs in the other. So the change is *data in one place*, not new subsystems, and the
ticket is not larger than it looks.

## 3. `StatEffect.java` — kept in shape, REWRITTEN in substance. It shipped a regression.

Inherited uncommitted from the previous attempt. Its shape was right — collapse the `switch` at
`StatEffect.java:510-530` (HEAD) and the if-chain at `:1299-1306` into one lookup table, because
adding eight mounts × four beginner jobs to a `switch` is 32 new `case` labels. **Its key was
wrong, and code review caught it.**

It keyed the table on `sourceid % 10000`, on the stated premise that a beginner skill has the same
last four digits in every beginner job. **That premise is false for three of the four v83 mount
families**, in this repo's own constants:

| mount | Explorer | Cygnus | Aran |
|---|---|---|---|
| Yeti Mount 1 | `1017` | `10001019` | `20001019` |
| Yeti Mount 2 | `1018` | `10001022` | `20001022` |
| Witch's Broomstick | `1019` | `10001023` | `20001023` |
| Balrog | `1031` | `10001031` | `20001031` |

(`Beginner.java:41-44`, `Noblesse.java:41-44`, `Legend.java:47-50`.) Only Balrog is job-stable.
So `% 10000` produced two live regressions: Cygnus/Aran **Yeti Mount 1** (`x0001019`) collided with
Explorer's **Broomstick** base `1019` and drew a broomstick; and Cygnus/Aran **Yeti Mount 2** and
**Broomstick** (`x0001022` / `x0001023`) matched nothing, so `isMonsterRidingSkill` went false for
them and they stopped mounting at all — four skills silently dead.

Fixed by keying on the **whole skill id**: `SKILL_MOUNTS` lists the twelve v83 entries by their
`constants.skills` constants, then generates the v84 entries in a loop, because the v84 skills
genuinely *are* job-stable (`000102x` / `1000102x` / `2000102x` / `2001102x`, per
`add-list/Skill.txt`). `beginnerSkillBase` is gone — with whole-id keys there is no suffix to
mis-match, so the Hero-`1121017` trap it existed for cannot arise.

`V84MountNodeTest.v83MountsStillMapToTheSameSprites` is the guard, and it is written against the
constants rather than literal ids **because literals are how this got through the first time**: the
previous test asserted `20001017 -> 1932003` and `10001019 -> 1932005`, both of which are wrong —
`20001017` is not a skill at all and `10001019` is Cygnus's Yeti, not a broomstick — so it passed
on the buggy code.

Two things left alone on purpose:

- `Beginner.SPACESHIP` / `Noblesse.SPACESHIP` stay out of the table: their sprite is
  `1932000 + skillLevel`, not a constant. Same for `MONSTER_RIDER`, which rides whatever is in slot
  -18. All three are still recognised by `isMonsterRidingSkill` without a row, and the test asserts
  exactly that.
- `isMonsterRidingSkill`'s `sourceid % 10000000 == 1004` does not match `Evan.MONSTER_RIDER`
  (`20011004 % 10000000 = 11004`). **Pre-existing**, unchanged by this ticket — HEAD's switch listed
  Beginner/Noblesse/Legend `MONSTER_RIDER` and not Evan's either. It is Evan's, so ticket 13's.

**Process note worth keeping.** The regression was in the work I was told stood ("re-run
byte-identical under the fixed tool"). That statement was about the three *WZ merges*, and it was
true; it said nothing about `StatEffect.java`, which was handed over as "review it, decide if it is
right". Byte-identical output from a fixed tool is not evidence about a Java file that tool never
touched.

## 4. Path lists — `docs/wz-baseline/merge-lists/05/`

| file | rows | note |
|---|---:|---|
| `Character.paths.txt` | **8** | the eight mount sprites. 184 more TamingMob rows deliberately NOT taken — see below |
| `Skill.paths.txt` | **27** | 9 skills × 3 beginner jobs |
| `Morph.paths.txt` | **25** | `0050`–`0053.img` + `fly2`/`fly2Move`/`fly2Skill` on 7 morph images |
| `String.paths.txt` | **7** | all forced; useless without `--force` |

**Composition order: 04 before 05** for `Character.wz` and `String.wz`, as directed — though on
both files the two lists are provably **disjoint**, so the order is not load-bearing. 04's
`Character.paths.txt` excludes `TamingMob/**` outright; 04 forced 30 of `COLLISION-FORCE.txt`'s 37
rows and 05 forces the remaining 7, so together they consume the force list exactly once each.
`Skill.wz` and `Morph.wz` are 05's alone.

### The 184 Mir animation rows: merged clean, then deliberately dropped

`add-list/Character.txt` carries 184 more `TamingMob/**` rows, all inside four images the live
client already has — `01902040/41/42` (Mir) and `01912033` (its saddle). Every one is a single extra
animation *layer* inside an existing frame (`…/fly/0/2`). They merged with `added 192, refused 0,
0 drifted`, so this is not a tooling limit. Then this, dumped from the merged output:

```
pre  01902041.img/fly/0 :  0 = canvas 144x67 (2197 B),  delay = 90
v84  01902041.img/fly/0 :  0 = canvas 160x62 (1810 B),  1 = 156x101,  2 = 45x35,  delay = 180
post 01902041.img/fly/0 :  0 = canvas 144x67 (2197 B),  delay = 90,  1 = 156x101,  2 = 45x35
```

v84 **re-authored** frame 0 and doubled the delay. Those are *edits*, which additive-only cannot
take, so the merge grafts v84's two new layers onto the live client's layer 0 at the live delay.
That is precisely the half-v83/half-v84 shape ticket 03c ruled *worse than either whole version*
for `Npc.wz/9000021.img` — and it is a rendering question no agent here can check. Mir is ticket
13's flagship mount and 13 can swap the whole image properly. 05 only needed Mir's **names**, which
it takes via `String.paths.txt`. The 184 rows are preserved commented-out at the foot of
`Character.paths.txt` with this rationale, ready to uncomment.

## 5. Naming: what was forced, what was left, and whose it is

Seven force roots, 18 ids, every one of them reading the literal `MISSING NAME` locally.

- **Evan's Mir + saddles (6 ids) — 05's, forced.** `1902040/41/42` → "Stage 1/2/3 Dragon",
  `1912033/34/35` → their saddles. These are why the ticket said a `Character.wz` diff cannot see
  them: the sprites shipped in v83, only the `String.wz` entries are new.
- **`Eqp/Dragon` (12 ids, one collapsed root) — 05's. Not left ambiguous.** On subject matter these
  are Evan dragon *equips* (Silver/Gold/Reverse Mask, Pendant, Wings, Tail), so they read as ticket
  13's. Taken here anyway: ticket 04 has already shipped a `String.wz` path list that excludes them
  and names 05 as the owner; ticket 13 is hard-blocked behind ticket 01's human client launch;
  and the container is id-for-id identical live vs v84 (12 ids, dumped both sides), so forcing the
  whole root is lossless. **Ticket 13 must not re-add this row** — it is on the force list once and
  a second force would be an overlap.
- **Left, and not mine: the 27 `String.wz/Skill.img` mount-skill names.** They are pure additions,
  zero collisions, no `--force` needed. 05's ownership was scoped to `Eqp/Taming` + `Eqp/Dragon`, so
  they were listed ready-to-paste in the header of `05/String.paths.txt` rather than merged.
  **TAKEN BY TICKET 03f, 2026-08-16** — `docs/wz-baseline/merge-lists/03f/String.paths.txt`.

  > **Correction, made by 03f.** This section originally called the gap "cosmetic only (the server
  > reads skills from `Skill.wz`, never `String.wz`)". **That is wrong, and it under-rated a
  > blocker as a nicety.** The server does read `String.wz/Skill.img` — as the **enumeration
  > source it grants skills from**. `MaxSkillCommand.java:44` iterates
  > `getDataProvider(WZFiles.STRING).getData("Skill.img").getChildren()` and feeds each child name
  > to `SkillFactory.getSkill(Integer.parseInt(...))` → `changeSkillLevel(max)`; the same loop is
  > at `ResetSkillCommand.java:44` and `NPCConversationManager.java:395`. A skill absent from
  > `Skill.img` is never visited, so `!maxskill` silently skipped exactly these nine. Combined with
  > §7's note that nothing in this ticket grants them, the eight mounts were **unobtainable by any
  > route on this server** until 03f merged these rows.

## 6. Verification — real output

Staging `D:\games\MapleStory\Server\wz-merge\05-r2\` (fresh; the previous attempt's `05\` is
superseded and left in place untouched). `pre\` SHA-256s equal the live client's, which equal the
backup's, for all four files.

```
merge Morph      added  25 (forced 0), refused 0   verify: 46 images, 0 unparseable, 11 content-checked, 0 drifted   exit 0
merge Skill      added  27 (forced 0), refused 0   verify: 76 images, 0 unparseable,  3 content-checked, 0 drifted   exit 0
merge Character  added   8 (forced 0), refused 0   verify: 7215 images, 0 unparseable, 8 content-checked, 0 drifted  exit 0
                 (7215 = the live client's 7,207 images + these 8. Ticket 04's "7,241
                  Character.wz image digests" is a wc -l of a hash file, not an image
                  count - 7,207 + 17 subdir rollups + 17 TOTAL lines. Reconciled by 03f;
                  both tickets' coverage was complete, only 04's label was wrong.)
merge String     added   7 (forced 7), refused 0   verify: 20 images, 0 unparseable,  1 content-checked, 0 drifted   exit 0
```

`String.wz` without `--force` is `added 0, refused 7, exit 5` — all seven `already exists in
target`, which is the whole point of the force list.

**§6.1 content digest, pre vs post** (`WzMerge hash`, `Compare-Object`):

- `String.wz/Eqp.img/Eqp` — exactly **2** children differ (`Dragon`, `Taming`) + `TOTAL`. Every
  other equip category digest-identical. That is the forced overwrite proving it stayed in its lane.
- `Skill.wz/000.img/skill` — exactly the 9 new ids + `TOTAL`. Nothing else moved.
- `Morph.wz/1000.img` — exactly `fly2`, `fly2Move`, `fly2Skill` + `TOTAL`.

**§6.2 diff tool** (`<out> <pre> <post> <pre>`), 22,047 images parsed, **0 parse failures**:

| file | add | removed | modified |
|---|---:|---:|---|
| `Character.wz` | 8 | 0 | 0 |
| `Morph.wz` | 25 | 0 | the 7 images `fly2` went into |
| `Skill.wz` | 27 | 0 | `000`, `1000`, `2000.img` |
| `String.wz` | 0 | **0** | `Eqp.img` only |

`String.wz` shows `add 0 / removed 0 / modified 1` because a force is a replacement in place —
`removed 0` is the line that matters: the 18 placeholder nodes came back as the same 18 ids.

**§6.3 the gate fires** — re-merging each output against itself: Morph `refused 25`, Skill
`refused 27`, Character `refused 8`, each `added 0`, each **exit 5**.

**§6.4 output SHA-256:**

```
Character  FC50BE708A1BD561101CCDB9E7E4B011679E55204F2810C3DC7999DC82C0F5A4
Skill      69AE95DF8380EC2268665A1205CD35F42B6DCBEBC85E6049C12657518BF95B49
Morph      E8E3D94E19B6CC8B3ADA097152216423547B9A63ACB59569AE0C76E7BBE4852D
String     C00D003E4DFC5104AD26A87B8C1B39C3F9C86E02DC14B5A55588FA605BF0A499
```

The first three are **byte-identical to the previous attempt's** — the merge is deterministic and
re-derivable from these notes.

**Server XML.** `WzMerge xml` for `Morph.wz` (`added 25, refused 0`) and `String.wz`
(`added 7, forced 7, refused 0`); `Character.wz` and `Skill.wz` XML were already applied and match
these path lists exactly. `git diff wz/String.wz/Eqp.img.xml` is **18 insertions / 24 deletions in
two places** — an in-place replacement, no reformat noise. (The extra 6 deletions are the six
`desc="MISSING INFO"` lines: v84 ships `name` only for those ids.)

**Tests.** New sibling `src/test/java/server/V84MountNodeTest.java` (7 tests, **8 after 03f added
`v84MountSkillsAreNamedSoTheServerCanGrantThem`**) — a sibling of `V84TracerNodeTest` for the same
reason ticket 04's `V84CosmeticNodeTest` is. It reads the server's own `XMLWZFile`/`XMLDomMapleData`,
including a negative control (`Eqp/Taming/1932000` must still read `MISSING NAME`, so a blanket
rewrite would fail rather than pass everything). The previous attempt's edits to
`V84TracerNodeTest.java` were reverted; that file is back at HEAD. (03f also moved the two-line
`wz(String)` helper out to `V84Wz`, which five node-test classes each held a verbatim copy of.)

> **What `v84MountSkillsMapToTheirSprites` is, honestly (review finding F5, written up by 03f).**
> It **copies `StatEffect.buildSkillMounts()` verbatim and asserts itself**, so it is a
> **change-detector, not evidence**: it tells you the table changed, never that the table is right.
> That is not a defect to delete, because the pairing is **hardcoded in the client and provable
> from no WZ node** — no test in this repo can be evidence for it; §1's two reference
> implementations and the in-game check are the evidence. Read it as a tripwire and nothing more.
> The parts of the class that **do** earn their place on their own: the negative controls, and
> `v83MountsStillMapToTheSameSprites`, which is written against `constants.skills` rather than
> literals and is what caught the `% 10000` regression.

```
./mvnw -o test  ->  Tests run: 1928, Failures: 0, Errors: 0, Skipped: 0   BUILD SUCCESS
    server.V84MountNodeTest  Tests run: 7, Failures: 0, Errors: 0
```

(1928 rather than the 1,910 recorded after 04: tickets 06 and 07 landed their own node tests on
this branch while 05 was in flight.)

**Client untouched.** All **18** live `.wz` still SHA-256-match
`_backup\client-v83-EzorsiaV2-2026-08-15\`, checked after the work. No `.partial`, `.merged` or
`.TEMP` beside the client.

## 7. What I could not do

- Launch the game. Everything in §8 is staged, not performed.
- Prove the skill→sprite pairing from the WZ — it is not in the WZ. Two reference implementations
  agree; the in-game check is the real one.
- Say whether the eight mount skills are **obtainable** by a player. Nothing in this ticket grants
  them. Whether v84 intends a quest or cash-shop route is unresearched and out of scope here.
  (This line originally said "a GM `!skill` is how the human test starts". **There is no `!skill`
  command in this codebase.** The GM route is `!maxskill`, and it only reaches these nine once
  ticket 03f's 27 `String.wz/Skill.img` rows are installed — see §5.)
- Judge whether the 184 Mir animation rows render correctly when half-merged (§4). Deferred to 13.

## Human steps — staged, not performed

Client closed, including any HaRepacker window. Rollback for every step is the same:
`copy D:\games\MapleStory\Server\_backup\client-v83-EzorsiaV2-2026-08-15\<Name>.wz D:\games\MapleStory\<Name>.wz`
and, server-side, `git checkout -- wz/` — **but check `git status` first**, other tickets' XML is in
the same tree.

**Step 0 — install. ⚠ `Character.wz` and `String.wz` need a COMPOSED merge, not this ticket's file.**

`Server\wz-merge\05-r2\Character.wz` was merged onto the pristine live base and therefore contains
05's 8 rows and **none of ticket 04's 240**. Same for `String.wz` (05's 7 forced rows, not 04's
385). Installing 05's copies silently reverts 04. Two ways forward:

- *Correct:* use the composed install pass. **It exists now** —
  `docs/wz-baseline/merge-lists/composed/`, built and run end to end by ticket 03f, which consumes
  04, 05, 06, 07 and 03f's own rows against one live base. Read its `README.md` first: the
  composed `Character.wz` and `String.wz` merges exit **3**, and that is the correct result, not a
  failure.
- *If testing 05 alone right now:* install only `Morph.wz` and `Skill.wz` from `05-r2\` — neither is
  touched by any other ticket — and accept that the eight mount **sprites** and the Mir/Dragon
  **names** will be missing until the composed pass runs. The mounts will still be castable and
  rideable; the client will draw nothing for the vehicle. That is a partial test, and it is worth
  saying so before someone reads a blank mount as a failure.

```
copy D:\games\MapleStory\Server\wz-merge\05-r2\Morph.wz D:\games\MapleStory\Morph.wz
copy D:\games\MapleStory\Server\wz-merge\05-r2\Skill.wz D:\games\MapleStory\Skill.wz
dir D:\games\MapleStory\Morph.wz D:\games\MapleStory\Skill.wz
```

Then `launch.bat`, and `localhome.exe` — **not** `localhome.evan.exe`; mixing ticket 01's binary
patch in makes any failure ambiguous.

| # | do | pass | fail signature |
|---|---|---|---|
| 1 | `!maxskill` on a Beginner-tier character (and again on a Noblesse and on an Aran) | all nine mount skills appear in the skill window, **named** | **`!skill` does not exist** — the only skill commands `CommandsExecutor` registers anywhere are `maxskill` (`:417`), `resetskill` (`:418`) and `mobskill` (`:427`), so the original `!skill 1025 1 1` here was uncastable. `!maxskill` enumerates `String.wz/Skill.img`, which is why ticket 03f's 27 name rows are what makes this step work at all. Not learnable → check the composed `String.wz` **and** `Skill.wz` are installed |
| 2 | cast it | character mounts a wooden pony, speed/jump rise | mounts but renders as nothing → `Character.wz` not composed yet, §0 |
| 3 | walk, jump, climb a ladder, change map | mount persists across all four | mount vanishes on map change → regression in the shared riding path, **stop and report** |
| 4 | cast the skill again / take the buff to expiry | dismounts cleanly, stats return | stuck mounted → `cancelEffectFromBuffStat` path |
| 5 | repeat 2–4 for `1027`, `1028`, `1029`, `1030`, `1037`, `1038`, `1039` | each shows its **own** sprite: Croco, Black Scooter, Pink Scooter, Nimbus Cloud, Unicorn, Low Rider, Red Truck | **two skills showing the same sprite = the mapping in §1 is wrong**; record which pair and report |
| 6 | **regression:** cast `1017` — Yeti Rider (granted by step 1's `!maxskill`) | rides the Yeti as before | a pre-existing mount broken = `StatEffect` refactor regression, **stop and report** |
| 7 | **regression:** an equip mount — equip a saddle in slot -18 and cast `1004` | rides the equipped mount | as above |
| 8 | cast `1026` — "Soaring" | character flies; check flight animation is not visibly broken | wrong-looking flight → `Morph.wz` `fly2` states |
| 9 | after the composed install pass only: inspect Mir `1902040` and any `Eqp/Dragon` equip in the item window | real names, not "MISSING NAME" | still MISSING NAME → the force did not reach the binary side |

Steps 6 and 7 are the ones that matter most — they cover the fourth acceptance criterion, and they
are the only check on the `StatEffect` refactor that a human can perform.
