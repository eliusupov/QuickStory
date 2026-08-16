# STATUS — GMS v83 → v84 upgrade

Orchestrator log. State: `in-flight` / `done` / `partial` / `blocked-on-human` / `failed`.

**Roster constraint (owner directive, 2026-08-15): Opus only.** `gp-opus-high` /
`gp-opus-medium`. No Sonnet agents. The standing ceiling still applies — never `xhigh` or above.

| # | Ticket | Agent | State | Note | Updated |
|---|---|---|---|---|---|
| 01 | Evan client gate patched | `gp-opus-high` | **done** | **The runtime patch works.** `tools\evan-gate-patch.log` 12:03:49 — attached to the live `MapleStory.exe`, `read:` the exact 21-byte pattern at `0x00761714`, `GUARD PASS`, `RESULT: PATCHED and verified`. Themida did not re-encrypt and the client kept running. The static route is dead: `local.exe`/`localhome.exe` are **memory dumps**, not clients. **Patches memory — re-run every launch.** | 2026-08-16 |
| 02 | WZ baseline diff | `gp-sonnet-high` | partial | Manifests delivered (commit `550bf8580`) + reusable MapleLib diff tool. **Review found the tool has 5 high-severity defects — manifests must be regenerated.** See R1. | 2026-08-15 |
| 02b | Map.wz integrity probe | resumed | done | **Verdict: damaged copy.** 832 v83 map paths absent from v84 anywhere by leaf name; 815 resolve to real named content. Commit `62e0026d9`. Keroben `9000071` confirmed genuinely new in v84. | 2026-08-15 |
| 02c | v84 source integrity | `gp-opus-high` | done | Commit `613dba2e2`. **The dump was never damaged** — byte-identical to `GMSSetupv84.exe`. v84 genuinely deleted 832 maps. Supersedes 02b's inference. Side find: `v83-stock/` was missing `Reactor.wz`, now extracted. | 2026-08-15 |
| 02d | Fix the WZ diff tool | `gp-opus-high` | done | Commit `0a9036bb1`. **All 8 defects fixed, each proven by demonstration rather than inspection.** Manifests regenerated and now final. R1's H1/H2/H4/H5 closed → **04–09 unblocked.** | 2026-08-15 |
| 02f | Extract missing WZ baselines | `gp-opus-medium` | done | Commit `84a0a2dd7`. 13 new `.wz` added to the two baseline trees, zero overwrites, zero hash mismatches; diff re-run. **Both trees are now complete.** Found ticket 05's premise is wrong — see below. | 2026-08-15 |
| 03 | WZ merge pipeline | `gp-opus-high` | blocked-on-human | Tracer item `2001500` merged into both trees. **Criteria 2–5 ticked and verified; 1 needs a client launch.** New tool `docs/wz-baseline/tool-merge/`; procedure at `docs/work-plan/WZ-MERGE-PROCEDURE.md`. Additive-only enforced in the write path; BlockSize-invariance clean. **Swept every add-list — 41 collisions, see Frontier.** | 2026-08-15 |
| 02e | P0nk/Cosmic provenance | `gp-opus-medium` | done | Commit `aa80fb983`. Fork is 43 ahead / 2 behind upstream; **exactly 3 `wz/` files differ.** Owner's hypothesis disproven for the Map.wz finding but it exposed a bigger one — see below. | 2026-08-15 |
| 02g | Deep-nested name manifest | `gp-opus-high` | done | Commits `c4c3e77a0`, `22635f235`. **Expansion depth 1 → 3, uniformly, no allowlist.** Coupled `WzMerge xml` fix proven end to end. **Every manifest was materially incomplete** — see below. | 2026-08-15 |
| R1 | Code review — batch 01+02 | `gp-opus-high` | done | 5 high, 8 medium, 5 low. 01 sound; 02's tool is the weak link. Verdict below. | 2026-08-15 |
| R2 | Code review — batch 02c–02f + 03 | `gp-opus-high` | done | **4 blockers, 6 high, 6 medium.** Gate verified sound; the risk is `SaveToDisk`. **04–09 do not proceed as written.** Verdict below. | 2026-08-15 |
| 03b | Merge-tool safety fixes | `gp-opus-high` | in-flight | R2's B1/B3/B4 + H3–H6, M1–M4. Rewrites `WZ-MERGE-PROCEDURE.md` to be executable cold. | 2026-08-15 |
| 03c | Collision triage | `gp-opus-medium` | done | Commits `8fa84f31f`, `2da6583c0`. 735 collisions triaged: 35 no-op / 653 keep-local / 37 adopt-v84 / 10 ambiguous. **Found two silent-corruption hazards the gate cannot catch.** Deny-list seeded as data (28 rows). | 2026-08-16 |
| 03b | Merge-tool safety fixes | `gp-opus-high` | done | **B1/B3/B4 + H3–H6, M1–M4 all closed with real output.** Guard orchestrator-verified against the live client. **Tickets 04–09 unblocked.** | 2026-08-16 |
| 03d | Resolve the 2 ambiguous calls | `gp-opus-medium` | done | Commit `e4ccb0405`. **Cash-shop rows settled → keep local, no decision needed.** Dragon levelling still an owner call but the evidence is now conclusive. | 2026-08-16 |
| 04 | v84 cosmetics | `gp-opus-high` | in-flight | First content ticket. | 2026-08-16 |
| 05 | v84 mounts | `gp-opus-high` | in-flight | Corrected data pointers; `Morph.wz` + Mir naming. | 2026-08-16 |
| 06 | Crimson Sky | `gp-opus-high` | in-flight | Largest content win. `deps`-first merge ordering + SQL drop tables. | 2026-08-16 |
| R3 | Code review — 02g, 03b–03d | `gp-opus-high` | **partial — main report missing** | Only its *addendum* was delivered. That addendum ends "**Stop 04–06 still stands on B1/B2/B3**" — findings I have never seen. Main body requested. | 2026-08-16 |
| 10 | Evan exists, renders, has a dragon | `gp-opus-high` | done bar in-game | **The Evan tracer landed.** 88 path rows (`Skill` 12, `String` 70, `Etc` 4, `UI` 2), `added 88 / refused 0 / denied 0 / forced 0`. Composed re-run with 10 folded in → **1,750 rows across 13 files**, nine of eleven byte-identical to 03i. Suite **2,008**. **Two corrections that matter: the client was never installed, and the server side was NOT "only WZ data".** | 2026-08-16 |
| 03e | Merge-tool safety fixes, round 2 | `gp-opus-high` | done | **R3's B1–B4 + H1–H6, M1–M6 closed, each with before/after output from two real binaries** (`03-verification/safety-guards.md` §G10–G16). Deny/force lists enforced; output-directory guard made absolute; `deps` rewritten to add-list granularity + link/bgm/mapMark; 0-row manifest and "added nothing" are exit 2 / exit 5. **Ticket 05's three merges re-run byte-identical.** | 2026-08-16 |

## Content batch — 04 and 06 landed, orchestrator-verified

**04 cosmetics — done bar two human steps** (`e8b06939a`, `c3bc7d20d`). 1,045 path rows across
`Character`/`Item`/`String`. Its protect proof is the right kind: `WzMerge hash` over **every
`Character.wz` image digest**, pre vs post — **exactly 12 changed, all 12 named by its own list**,
and of the 5,120 images in `protect-list ∪ modified-list.live`, 5,110 are digest-identical and the
10 that moved only *gained* children. It forced 30 `String.wz` names, every one an id whose live
value was the literal `MISSING NAME`, and deliberately left the 7 `Eqp/{Dragon,Taming}` rows to 05.
It also declined `Etc.wz` entirely with reasons (bulk `Bonus` rows, 1,518 SNs pointing at
out-of-scope items = dead shop buttons). Two honest gaps: `Hair/00034040`–`00034047` ship art with
**no name in either tree** — it merged the art and refused to invent names — and hair rendering on
both genders is the one criterion needing a human.

**06 Crimson Sky — done bar the travel route** (`59cb85105`). 95 paths: **95 added, 0 refused, 0
denied, 0 forced.** The fixed `deps` earned itself here — the maps need 10 individual
`Back/dragonRoad.img` frames *inside an image v83 already has*, exactly what the old whole-image
version hid, and the 8 link stubs now walk 2 map images each instead of reporting "0 dependencies".

Two findings from 06 that matter beyond it:
- **The ticket's mob list was wrong.** `9500374`–`9500382` appear in **no `life` node anywhere** —
  they are `summonType=1` clones of live Leafre mobs at identical exp. The 22 maps actually spawn
  **`8300000`–`8300006`**, which is ticket 05's mount list appearing as *mob* ids. Both sets merged;
  nothing spawns the nine. Handed to 05 to reconcile — likely `193200x` is the worn equip and
  `830000x` the rendered mob, linked by `info/tamingMob`, but that is a hypothesis to verify.
- **Drops went into a new changeSet.** `152-drop-data.sql` has already run everywhere, so editing it
  fails Liquibase checksum validation — `153-crimson-sky-drop-data.sql`, 776 rows, each a **verbatim
  copy** of a name-matched Leafre analogue with only `dropperid` swapped. It copied `questid != 0`
  rows only from name-matched analogues, without which Manon's quests `7301`/`7303` would become
  completable on Dragonica and Leviathan at chance 1000000.

**Orchestrator-verified:** full suite `./mvnw -o test` re-run from a clean shell → **exit 0**
(1,910 tests); path lists and the 819-line drop file present on disk; all client `.wz` still
hash-match the backup.

**05 mounts — done** (`4e8c49594`). 67 path rows: Character 8, Skill 27, Morph 25, String 7 (all
forced). Suite green at **1,928 tests**, orchestrator-re-run → exit 0.

It settled the three-way id confusion by measurement, and **the linking hypothesis I gave it was
wrong**: `info/tamingMob` is `6` for `01932006` and `7` for the other seven — a 1-based index into
`TamingMob.wz`'s 797-byte speed/jump/fatigue table, i.e. a **movement class, not a mob id**. v83's
own Yeti `01932003` already reads `tamingMob=6`. So the three sets are three different things:
`019320xx` are the mount **sprites** (05), `830000x` are hostile Crimson Sky **mobs**
(`level=110 maxHP=150000 exp=6000 bodyAttack=1`, 06's, already merged), and `000102x`/`000103x` are
the **skills** that grant them (05). Skill→sprite pairing is not in the WZ at all — client-hardcoded,
corroborated against two independent v95 sources.

**It also found and fixed a live regression that had nothing to do with v84.** The inherited
`StatEffect.java` keyed its mount table on `sourceid % 10000`, a false premise: Explorer's Yeti
Mount 1 is `1017` but Cygnus/Aran's is `x0001019`, and their Yeti Mount 2 / Broomstick are
`x0001022`/`x0001023` — so **Cygnus and Aran characters riding Yeti Mount 1 drew a broomstick**, and
four skills stopped being mounts entirely. Now keyed on whole ids from `constants.skills`. The old
test passed only because it asserted literals (`20001017`, which is not a skill id).

Riding is **configuration, not new code**, confirmed at `file:line` — `Character.java:7462` sets two
fields and everything downstream (`BuffStat`, `PacketCreator`, hunger, dismount, field-limit cancel,
expiry) is id-agnostic; `SkillFactory:152` derives `isBuff` from the WZ so the new skills classify
themselves.

**It dropped 184 Mir animation rows that merged clean** — dumping them showed they graft v84's
layers 1–2 onto the live layer 0 at the live delay, because v84 re-authored frame 0 and additive-only
cannot take an edit. Half-v83/half-v84, which 03c already ruled worse than either whole. Preserved
commented-out for ticket 13.

**Force-list bookkeeping: 04 took 30 rows, 05 the remaining 7 — consumed exactly once.** 05 claims
`Eqp/Dragon`'s 12 names for itself rather than 13, and says 13 must not re-add them.

**Open gap it flagged rather than hid:** the 27 `String.wz/Skill.img` mount-skill names are
unmerged, so the skills will render nameless. Ready-to-paste in `05/String.paths.txt`. **No ticket
owns `String.wz/Skill.img` — folding it into the composed install pass.** Also: nothing in-game
*grants* these skills; GM `!skill` is the only entry point today.

**07 Neo City 2227 — done, and it is the first content ticket with no owner decision attached**
(`8fc0a4b0f`). 14 path rows, **18 added / 0 refused / 0 denied / 0 forced**, 80 drop rows, zero
overlap with 04–06, suite green at 1,928.

- **The ticket's mob list was right this time** — all four ids genuinely placed across 32 `life`
  entries. It checked rather than assumed, which is the point: 06's list was wrong. All four carry
  `summonType=1`, the flag that made 06's nine unspawnable, but here it is a red herring because
  these *are* placed. Zero `n`/`r` entries, so no `Npc.wz` or `Reactor.wz` work at all.
- **`deps` owed nothing** — 135 of 135 references already in v83; v84 reuses the Neo City tileset
  wholesale. (The old hardcoded-`Map2` version would have silently exited 0 here.)
- **The route works, and for the opposite reason to 06's**: `240070000`'s `TD_neo` portal is
  byte-identical in v83 and v84, so both halves already shipped — and Cosmic had the warp
  **pre-written and commented out**, waiting for these maps. One real call: `quests[i]` gates
  `array[i]`, so it added `3749` as the seventh gate.

**Its own review caught three of its mistakes and it reported them plainly**: a wrong spawn
breakdown in its report (6×/5× where the data says 9×/4×) that its test missed because the test
asserted per-map *totals* — now asserts the per-mob matrix, negative-controlled by patching a count
and confirming failure; `Sound.wz` merged when it belonged to 06 under the batch split — reverted
clean and handed off; and criterion 1 ticked as "present in client WZ" when nothing is installed —
reworded to staged-not-installed. That last one matters: it is exactly the class of claim this
project has to keep honest.

**Could not do:** prove quest `3749` is passable end to end — it needs the `TD_Battle5` two-player
boss instance, so if that is broken 2227 is unreachable through the front door. Fallback `3748`
recorded in the human steps.

### R4 — content-batch review (04/05/06): main report received. Verdicts, then the two edits.

**SQL is safe to apply to a live server**, verified mechanically rather than sampled: **776/776 rows
are verbatim copies** of a `152-drop-data.sql` row under the declared analogue modulo `dropperid`;
zero mismatches, zero orphan payloads, zero duplicate tuples, zero overlap with dropperids 152
already covers. **39/39 `questid != 0` rows sit on name-matched analogues**, and the four
non-name-matched droppers carry zero quest rows — so the Manon-`7301`/`7303`-on-Dragonica risk 06
guarded against **did not materialise**. The new-changeSet call was right for a reason 06 did not
state: `DatabaseMigrations.java:39` calls bare `liquibase.update()` with `ValidatingVisitor` and no
`validCheckSum`/`runOnChange` anywhere, so editing 152 would be a hard `ValidationFailedException`
**at server startup**. Applies cleanly: columns match `009-drop.sql`, `id` omitted so PK collision is
impossible, pure ASCII, exactly one semicolon.

**`StatEffect.java` is safe** — exact parity on every v83 mount (deleted `switch` cases map 1:1 onto
the new predicate ∪ `SKILL_MOUNTS`, same sprites), no id collisions with any advanced-job skill, and
`MONSTER_RIDER`/`SPACESHIP` behaviour preserved exactly. **But the premise was misstated:** the
`% 10000` bug 05 described was in *uncommitted inherited work*, not at `f1dbbd85d` — HEAD's code was
already correct. So this is a refactor of working code plus new mounts, not a fix, which raises the
parity bar rather than lowering it. It also correctly excluded skill `1026`: nine skills were added
per job, not eight, and `1026` is `플라잉` (Flying) — flight, not a mount.

**Merge integrity is stronger than the tickets claimed.** A structural diff over **all 291 modified
files** found: **18 nodes removed anywhere — every one a `MISSING INFO` placeholder** — and 66 value
changes, being exactly the 37 forced rows' name/desc. `Character`, `Item`, `Map`, `Mob`, `Morph`,
`Npc`, `Skill`, `Sound`, `Reactor`: **purely additive, 0 removed, 0 changed.** 04's "exactly 12
Character images" and "5,120 in the union, 10 changed" both reproduce exactly, derived two
independent ways.

### The two edits that gate the install

**Edit A — 27 rows, and without them the eight new mounts cannot be obtained by any route.**
Ticket 05 omitted `String.wz/Skill.img`'s 27 mount-skill rows, reasoning that "the server reads
skills from `Skill.wz`, never `String.wz`". **That is false.** `String.img` is the *enumeration
source for granting skills*: `MaxSkillCommand.java:44` iterates its children → `SkillFactory.getSkill`
→ `changeSkillLevel(max)`, and the same loop runs in `ResetSkillCommand` and
`NPCConversationManager:395`. A skill absent from `String.img` is never visited. Worse, **there is no
`!skill` command in this codebase** — only `maxskill`/`resetskill`/`mobskill` — so 05's human test
step names a command that does not exist, and the one real substitute skips exactly these nine
skills. The half-state is already committed: `Skill.wz/000.img.xml:2224` has `0001025`; the
`String.wz` side has zero. Pure additions, no `--force`.

**Edit B — the `Sound.wz` verifier is wrong, not the data.** `Program.cs` counted `bad` across the
whole output including `BgmGL.img`, which MapleLib cannot parse in **any** of the three trees — so
every `Sound.wz` merge fails verification and exits 4 regardless of correctness. A pre-existing
defect must not be reported as damage this merge caused. Fix must still fail an image that parses in
the target and not in the output.

**Composition is verified sound**: 04 ∩ 05 ∩ 06 = ∅ on every shared file, the 37-row force list is
consumed exactly once (30 + 7, none twice, none missed), and **order is not load-bearing** — the one
order-sensitive path is the force `Remove()`-then-re-add, and since 04 has zero rows under the
`Eqp/Dragon` container root, 04→05 and 05→04 give identical trees.
**Expected composed run: `Character` exit 3 (248 added, 6 refused), `String` exit 3 (427 added, 37
forced, 9 refused), everything else 0.** Those refusals are 04's deliberate ones — an install script
that aborts on non-zero will stop there, and it must not read exit 3 as failure.

### 03f install prep — both edits landed, and the composed merge has now actually been run

Commit `02a6be70c`. Suite **1,949 green**; all 18 client `.wz` still bit-identical to the backup
(orchestrator-verified independently).

- **Edit A done.** 27 mount-skill names merged; `!maxskill` now walks all 27 ids, so **the mounts are
  grantable**. 05's false "never reads `String.wz`" claim and its non-existent `!skill` human step
  are corrected in place. New test asserts all 27 plus "Soaring" ×3, with `20011025` held as a
  **negative** control since it is ticket 12/13's.
- **Edit B done, and proven in both directions** — the part that matters. The verifier now re-parses
  only the images that failed, against the *target*, and discounts pre-existing damage:
  `Sound.wz` → `1 pre-existing, discounted`, exit 0. And a deliberately corrupted `TamingMob.wz`
  (24 bytes overwritten in a file that parses clean in the target) → `UNPARSEABLE image 0007.img`,
  exit 4. Suppressing `bad` outright would have passed that; this does not.
- **The composed merge ran end to end**, which nothing had done before:
  `String` exit 3 (462 added, 38 forced, 9 refused) · `Character` exit 3 (248 added, 6 refused) ·
  `Item` 391 · `Map` 37 · `Mob` 21 · `Skill` 27 · `Morph` 25 · `Npc` 6 · `Reactor` 2 · `Sound` 1, all
  exit 0. It matched the predicted counts once two omissions in the prediction were accounted for —
  the predicted `String` total had left out 07's 7 rows. **`Morph`, `Skill`, `Npc` and `Reactor` came
  out byte-identical to 05's and 06's separately recorded hashes, so composition is deterministic.**
  Gate re-fires on the composed output: `added 0, refused 471`, exit 5.
- **F2 resolved, and the evidence inverted the premise**: `v83-stock/String.wz` carries the *identical*
  "Steward" node, so it was never Cosmic content at all — v84 renamed a stock GMS npc. Adopted.
- All four doc defects fixed; the test helper extracted to `V84Wz` with **zero** copies remaining.

**Carried forward from 03f:**
1. **08 is deliberately NOT composed in** — its lists were uncommitted and still changing during the
   run, and folding them in would have shipped an untested list. Every composed file ends with an
   append marker.
2. **08 has its own `String.force.txt` with 3 forced rows, so the composed force list becomes 41, not
   38.** Reusing the current `FORCE.txt` at install time would silently revert them.
3. Commit `2a89da169` (08's) swallowed two of 03f's files mid-flight. Content on disk is correct and
   tested; only the attribution is wrong. Third time this has happened — an artefact of concurrent
   agents sharing one working tree, not of anyone's work being lost.

### 08 misc areas — done, and it found the most dangerous merge shape yet

Commits `2a89da169` → `b7e5a4b47` → `8dd83b3ad`. 174 path rows (Map 95, Mob 7, Npc 9, Reactor 1,
Sound 23, String 39), **zero overlap with 04–07**, suite 1,949 green, client bit-identical.

**The headline: the additive gate is blind to positional-array writes, and this class corrupts
content players use today.** `add-list/Map.txt` offers **18 rows writing into `portal`/`obj` arrays
on maps the live client already has.** 08 dumped every array index by index: **6 are pure appends
(safe, merged), 12 are not** — v84 reordered or inserted, so the same index means different things
in the two trees. `220011000/portal/4/*` **would break the working portal into Ludibrium Toy
Factory.** And **10 of the 12 are the shape `<array>/<n>/<field>` where `<n>` already exists** — the
parent check passes, the leaf does not exist, so the merge writes onto the wrong sibling and reports
nothing. Same class as the MonsterBook splice, but on live portals. 03g is deny-listing the 12 and
adding a general tool check, with 08's 6/12 split as the oracle.

**It also drew the 13 boundary the brief never specified:** of 51 whole-image map rows outside
06/07's areas, **29 are Evan's world** (`900010000`–`900090104`, Utah's House, Farm Street) → ticket
13. This ticket took the other 22.

**19 of 22 maps are staged-but-unreachable, each with a reason** — that list is a deliverable, not a
caveat. 8 blocked on an uncopyable node (06's travel-route problem again), 8 on the Slumbering Dragon
Island flow, 1 gated on quests `22515`–`22518` that do not exist yet, 1 with no entry point in any
vendor's data. Three are reachable via merged appends plus a 3-line portal script each.

**It reported a mistake it made and reverted.** It overwrote `scripts/portal/enterDollcave.js`
believing an unreferenced-looking v83 script had no handler; it had one, warping to `105040201`
behind two quests. Restored byte-identical, kept out of the commit, guarded by a test. Root cause: a
glob whose brace syntax matched nothing, read as "no such file". `910050300` moved from reachable to
staged as a result. This is exactly the failure mode worth knowing about — absence of a grep hit is
not absence of a reference.

Smaller: six of the seven new mobs give **exp 0** — scripted obstacles, so no drop SQL and no
changeSet at all. Of 06's ten flagged `mapMark` entries, exactly **1** is owed. And the Christmas NPC
names are **shipped in Korean by v84 itself** — imported as-is and flagged as the one thing a human
may want reversed.

### 03g — the positional-array gate, and why a general rule beat an enumeration

Commit `3d7860a28`. Suite 1,949 green; all 18 client `.wz` SHA-256-matched before *and* after.

**It re-derived the 18 rows rather than trusting 08's table and reached the same 6/12 split — with
one disagreement, and 08 was right.** `Map/Map2/220000300.img/portal/15` **passes** 03c's written
rule (live has 15 portals `0`–`14`, so index 15 is an append), yet 08 refused it. Measured:

```
live  portal/14 : pn=in06 pt=2 x=2008 y=103 tm=220000307 tn=out00
v84   portal/15 : pn=in06 pt=2 x=2008 y=103 tm=220000307 tn=out00   <- byte-identical
```

v84 has 16 portals only because it inserted `scr00` at index 4; every later slot is the target's own
content shifted one place, and the last falls off the end **looking like new material**. So the
implemented rule needed a fourth clause the written rule lacked: *a pure append whose content is
already in the array is refused.* This is the value of making an agent investigate a disagreement
instead of adjusting the rule to match.

**The general rule then found 8 more hazardous rows that nobody had enumerated — in lists already
shipped by ticket 04:**
- `Item.wz/Consume/0202.img/020225{03,14}/reward/43` — v84's slot 43 is content-identical to the live
  box's **slot 16**. The MonsterBook hazard, in a different file.
- 6 × `Character.wz/Glove/01082262.img/…/{l,r}Glove` — the live client ships this glove with
  *different art* (live `stabTF/2/rGlove` 6×5 @(-7,-15); v84 5×5 @(2,2)). Splicing v84's layer into
  an Ezorsia frame is the same shape 08 refused on a portal. Cost of refusing: one glove renders
  without its left-hand layer on four frames. Cosmetic, force-listable if an owner disagrees.

The rule: a container whose children are **exactly** the consecutive integers `0..c-1` is an array —
"exactly" is load-bearing, since a map `.img` has layers `0`–`7` *alongside* `info`/`portal` and is
therefore not one. A slot write is refused unless it is a genuine append: last segment, index ≥ the
**baseline** child count (memoised, so a text-ordered manifest listing `back/10` before `back/9` is
judged as a set), no hole left, and content not already present. Refusals read `POSITIONAL ARRAY:`,
never `already exists` — an operator can tell the two apart. Now documented at **§4.4**, with the
enforced rules, plus a pointer from §5.3 where a map operator actually meets it.

**Composed install re-run with 08 folded in: 1,409 rows, `FORCE.txt` at 41 roots, all ten files
verified and promoted, no `.partial`.** `String` exit 3 (501 added, 41 forced, 9 refused), `Character`
exit 3 (242 added, 12 refused — 6 lost to the new gate), `Item` exit 3 (389, 2 refused), `Sound`
**exit 0** at last, everything else 0. `Morph` and `Skill` reproduce **byte-identical to 03f**; the
other eight differ for stated reasons. One composition fill was needed — `Obj/effect.img/quest/gate/6`,
because `deps` emits only what a map *references*, so 08 got `gate/7` alone and the array would have
been `0-5,7`.

### 09 quests — done, and the honest number is one

Commits `8e740646b`, `56591f8c5`. 252 rows, `added 252 / refused 0 / denied 0 / forced 0`, every
pre-existing child of all four `Quest.wz` images digest-identical, zero overlap with 04–08. Suite
**1,991 green**.

`add-list/Quest.txt` is **924 roots, not 198 quests**: 792 whole quest nodes = 198 × 4 category
images (`Act`/`Check`/`QuestInfo`/`Say` agree exactly on the id sets — the old "798 vs 198" is
arithmetic, not a defect), of which **135 quests are the `22xxx` Evan chain → ticket 13** and 63 are
09's, plus **132 rows writing into live content**. Scripts needed: **30, not the ~18 estimated** —
8 routed to the existing `medalQuest.js`, **22 written**, zero overwrites.

**Only 1 of the 63 quests can be accepted today.** 48 carry an `end` date **v84 itself shipped
already expired**, 9 name only Evan job ids, 4 sit behind an `infoex` counter, 1 behind a dead
upstream quest. `19011` is the residue. That is the ticket's real outcome and it is a property of
v84's own data, not of the merge.

**It reported a misread that review caught**: it had read `2200`–`2218` as Aran and counted three
acceptable quests; they are `Job.EVAN1`–`EVAN10` (`Job.java:62-63`), Aran is `2100`–`2112`. Chasing
it surfaced a second bad generalisation (`10480`'s job list *excludes* Evan). Both are now asserted
as sets derived from the tree rather than written as prose.

**It refused 132 rows, each measured on both sides** — and two of the shapes defeat the new gate:
- **108 × `Check.img/<id>/0/lvmax = 40`** onto live beginner quests `28162`–`28325`. The live node is
  v84's minus `lvmax`, so the gate writes every one silently. Effect: **108 currently-startable
  quests become unavailable above Lv.40.** These have **no numeric array index**, so 03g's
  index-keyed check misses all of them.
- **13 date rows** giving currently-ungated working quests a **24-hour window in January 2008**.
- **`Exclusive.img`** — live has one *named* group `medal` (14 ids); v84 **replaced** it with numeric
  groups `0`/`1`/`2` holding a different partition. Being additions they merge clean, producing an
  image with `medal` **and** `0`/`1`/`2` and seven ids in two mutually-exclusive groups.
  **Depth alone cannot catch this; only dumping both sides does.**

Two corrections it made to other tickets: **08's handoff was addressed to the wrong ticket** —
`22515`–`22518` are `22xxx`, i.e. 13's, so `910060100` stays unreachable and that win does not
exist. And quest **`3759` grants Soaring**, which 06's Crimson Sky maps gate on
(`needSkillForFly=1`) — merged and scripted, but behind one expired `end` node. Owner decision.

**The through-line of this project, now stated three times over:** `conflicts.txt` being empty is
**not** evidence of safety for writes into existing records. MonsterBook, the portals, and now these.

### 03h — 09 was wrong about the gate, and the real defect was subtler

Commit `f998d58f0`. Deny-list **40 → 156 roots**; composed set **1,662 rows**; suite 1,991 green;
client 18/18 intact. Orchestrator-verified on disk.

**It contradicted 09 and measured the contradiction rather than arguing it.** 09 said its 108
`lvmax` rows have no numeric index so the gate would miss all of them. In fact `Check.img/<id>`
holds exactly children `0` and `1`, which reads as an array, so **all 123 `Check.img` rows already
refuse structurally** — `added 9, refused 123`. 09 had refused them by omission and never ran them
past the gate at all.

**The actual defect it found is worse than the one it disproved.** The refusal *message* named one
hazard — "v84's slot `n` need not be the same entry as the target's" — and for these rows that
reason is **demonstrably false**: slot 0 is the start block in both trees and the indices line up.
An operator who does what the message says (compare the arrays by name) **disproves the stated
reason and overrides a correct refusal.** A right answer with a wrong justification is more dangerous
than a wrong answer, because it trains people to override the mechanism. Both messages now name both
hazards — the slot may differ, *and* even when it matches, the row edits an existing record by adding
a field to it — with `lvmax` as the worked example.

**What it deliberately did not build**, which is the right call: no general "write into an existing
record" refusal, because the composed install **contains legitimate rows of exactly that shape**
(08's `String.wz/Npc.img/{1063018,1205000,2012034}` gaining `d0`/`d1`, which are wanted). The shape
does not discriminate; only the field's meaning to the server does. A refusal there would be a false
refusal on shipped content. Likewise it left **six non-hazards off the deny-list** on the reasoning
that a deny-list carrying non-hazards is one operators learn to skim.

**`Exclusive.img` is genuinely uncatchable by any structural rule** — a semantic replacement where
v84 swapped a named group for numeric ones, and the container is not an array (its only live child
is `medal`). Denied as a whole image and documented as a gap at the point of use.

`WZ-MERGE-PROCEDURE.md` gained **§4.5 — "An empty `conflicts.txt` is not evidence of safety"** — the
through-line as a table across MonsterBook, the portals, `lvmax` and `Exclusive.img`, all four of
which `conflicts.txt` said nothing about.

**Composition is now proven stable:** all ten pre-existing outputs are byte-identical to the previous
run — not just `Morph` and `Skill` — and **`Quest.wz` reproduces ticket 09's own staged hash exactly**
(`5F37E5F5…`, 6,083,413 B), so 09's merge re-derives bit-for-bit from the composed manifest. It also
corrected 03g's row count (1,409 → 1,410) rather than carrying the error forward.

### 16 partial — regression over 04–09. Nothing was lost, and one real gap was found.

Commit `6fd528453`. Suite **1,994 green**; all 18 client `.wz` SHA-256-matched the backup at the
**start and at the end** of the run.

**The headline is a negative, and it is the one that matters: no content was lost.** A `WzMerge hash`
sweep across all 11 merged files found **0 images removed and 0 leaf values changed anywhere outside
`String.wz`**, with all 80 changed images then dumped recursively at depth 40. Ezorsia's ~24.6 MB
survives.

| criterion | verdict |
|---|---|
| protect list present **and unchanged** | met — digests, not presence |
| existing quests still work | met — server XML and the binary merge *independently* give `removed=0, changed=0`; 2,818 → 2,881 |
| drops / shops / spawn rates | met — `152-drop-data.sql` byte-untouched, 153/154 INSERT-only with dropperids **disjoint** from 152's 1,004, and **zero `life` nodes added to any pre-existing map** |
| hairstyles and equips | met **with one regression** — see below |
| owner's own changes | met — `config.yaml`, `GameConstants.java` and all boss/spawn files byte-identical to `94e66d80c`; upstream divergence still **exactly 3 `wz/` files**, with the quest rebalance and all three retimed coupons intact |
| four classes **plus Evan** from L1 | **not coverable** — Evan does not exist; nothing installed, client never launched, **no playability claim of any kind is made** |
| content reconciled | met — 1,662 rows = 1,639 merged + 23 refused, exact |

**Reconciliation with nothing unexplained:** of 16,113 add-list rows offered, 1,662 claimed. The
unclaimed remainder accounts cleanly — Etc 10,634 declined wholesale by 04, Quest 672 = **540 Evan +
132 refused by design** (exactly 09's split), Character 184 = **exactly** the Mir animation rows 05
dropped. Zero `DENIED` fired in the run: the deny-list's 156 roots are the net *under* the lists,
not part of this arithmetic.

**The regression it found — and it is exactly the class the gate exists to stop.** 03g refused 6
`Character.wz/Glove/01082262` rows to keep v84 layers out of Ezorsia's frames; the composed list
carries 11, and **two landed**. `swingT2/2` now holds Ezorsia's `lGlove 12x8` *and* v84's
`rGlove 12x9` in one frame. Cause: the gate fires only when children are **exactly `0..c-1`**, and
these arrays are `{1,2}` and `{1}` — not zero-based, so they are not seen as arrays at all. The agent
enumerated **all 34 indexed parents** in the composed lists and classified each: exactly these two
are misses, nothing else. **§4.4's rule does not do what it claims.** Routed to 03i with those 34 as
the oracle.

**Two more worth having:**
- **`WzMerge hash` stack-overflows (`0xC00000FD`) on `Reactor.wz`.** The project's own
  protect-verification instrument fails on a whole file, in a way an operator would read as merge
  damage. Data is fine (depth-12 dumps, all 6 identical); `Canon()` needs a depth bound.
- **389 `Map.wz` add-list rows write into maps the live client already has and were never triaged.**
  Harmless today because they are on no list — but **"unclaimed" is not "refused"**, and ticket 13
  works in `Map.wz`.

Each new test was negative-controlled (mutated deny-list → fails naming the row; `153`→`152` → fails
with 1,004 overlaps), and the quest test's over-broad first draft genuinely failed and was corrected.
Stated limit: `Canon()` normalises sibling order away, so **nothing in this project proves order was
preserved inside a merged container.**

### R5 + 03j — the client output is safe, and the server tree had drifted from it

**R5 verdict: the composed client `.wz` output is safe for a human to install.** The reviewer hashed
all ten `03g`/`03h` outputs itself rather than trusting the tables — byte-identical, and `Quest.wz`
bit-for-bit equal to ticket 09's own staged file. It also confirmed the deny-list has **no dead roots
and no over-coverage** (the 116 Quest roots cover exactly 126 add-list rows, every root with at least
one row beneath it), and endorsed both judgement calls: `Exclusive.img` denied whole, and the six
non-hazards deliberately left off.

**Two adjudications:**
- **03h right, 09 wrong** — independently re-derived: **2,869 of 2,870** `Check.img` quest nodes have
  children exactly `{0,1}`, so the gate does refuse all 123 rows. 09 asserted a negative it never
  measured. (The exception, quest `4940` with `{0,1,4961}`, is targeted by no row today.)
- **The gate does not over-refuse — it *under*-refuses.** All 8 refusals in 04's shipped lists are
  genuine. The sparse-array hole the regression pass found is real and was reproduced independently.

**The finding nobody had looked for: the server XML had diverged from the client merge.** The gate
landed in 03g *after* 04 had already spliced its rows into `wz/`, and nothing un-applied the server
side — so the tree carried rows the merge now refuses. `composed/README.md`'s "the XML tree is already
composed" covers what a new ticket *adds*, not what a later gate *refuses*.

**03j (commit `7507c71f6`) inventoried all of it** — `docs/wz-baseline/XML-RECONCILE.md`, derived
mechanically from the run logs and git rather than by eye, with per-file counts matching ticket 16's
independent enumeration exactly:

| | refused | in XML | divergent | server-readable | **both** |
|---|---:|---:|---:|---:|---:|
| Character | 12 | 12 | 6 (8 with 03i's) | 6 | **0** |
| Item | 2 | 2 | 2 | 2 | **2** |
| String | 9 | 9 | 0 | 9 | **0** |
| **total** | **23** | **23** | **8** | **17** | **2** |

**Exactly 2 of 23 could ever have changed behaviour, and both are fixed.** The headline is an
intersection, not a total — 15 readable rows are read constantly but identical on both sides, and the
6 divergent Glove rows are genuinely divergent but never read.

- **`Item.wz`**: both boxes carried `2020014` byte-identically at slots 43 *and* 16, so
  `getItemReward` summed its `prob` twice and rolled it at double weight. Reverted, and proven against
  the **real post-install binary** rather than against itself: 43 slots `0..42`, `2020014` once,
  totalprob 19864 / 18363, with every slot's full content compared, not just the totals.
- **`gate/6` added, not `gate/7` removed** — orchestrator-verified: the array now runs `0..7` on both
  sides. Removing `7` would have created a divergence to close a hole.
- **8 Glove rows left permanently**, recorded as art-only: no Java or script reads glove art, and
  `getData("Obj/` has zero hits repo-wide.

It also accepted a review correction worth noting: its "server-readable" column had been **false** for
the String and Dragon rows. They *are* read — they are safe because they are unchanged, which is a
different and more honest reason.

**One follow-up routed to 03i:** both `reward/43` rows are still on `composed/Item.paths.txt` and no
`Item.wz` row is on the deny-list, so the next `WzMerge xml` run would silently re-break the fix —
the XML gate is a line scan and cannot see a content-identical append. Two deny rows close it.

### Deferred deliberately, recorded not decided

- **F1 — owner call.** Four *live-customized* weapons (`01382058`, `01452058`, `01472069`,
  `01492024`) gained v84 combat stats (`incPAD 77`, `tuc 7`, `attack 6`, criticals) into `info` nodes
  that previously held only cosmetic fields. Three are "… for Transformation" morph props that become
  real weapons with 7 upgrade slots. The server reads these fields. Practical risk is low — none is
  in a drop table, shop row or script, all are `cash=1`, and `Etc.wz` was declined so nothing makes
  them purchasable — but 04 classified it as "new child inside `info`" and stopped, where the
  `Dragon/…/info/level` case got an explicit decision. This one deserves the same.
- ~~**F4 — deferred hazard for ticket 13.**~~ **CLOSED by ticket 10, and it inverted.** The code is
  an explicit **eight-id list**, not a `20011025`–`20011039` range (`StatEffect.java:174-187`), and
  dumping the now-merged `2001.img` shows **all eight are real Evan skills and each is exactly the
  mount the table pairs it with** — 8/8 by name. The failure mode cannot occur. `20011026` is
  "Soaring", flight, correctly outside the list and now the negative control. What it *did* surface:
  `2001.img` also ships `20011018`/`19`/`31` (Yeti Rider, Witch's Broomstick, Balrog) as real mounts
  with **no sprite mapping**, and the offsets do not transfer from any other job — recorded for
  **ticket 12** rather than guessed at, which would have been F4 again.
- **F2 — a defect 06 created**, now routed: it kept the live name "Steward" over v84's "Shadow Knight
  Rene" to protect custom content, but `9201144` is referenced by exactly one thing in the repo —
  the Dragon's Nest `life` node 06 itself just added. The reasoning protected nothing; the player
  sees a black knight labelled "Steward".

### R4 addendum — independent corroboration and a self-correction

Same delivery failure as R3: only the addendum arrived. It states "no verdict changes — SQL safe,
`StatEffect` safe, lists composable **after the two edits**", referencing findings I have not seen.
Main body requested. **Do not treat the batch as reviewed until it lands.**

What the addendum does establish, and it is worth having:

- **The protect claim reproduces by an independent route.** A recursive per-child descent over the
  12 changed `Character.wz` images found **0 subtrees removed, 0 leaf values changed, 68 added** —
  matching the digest result via server-XML node sets instead of binary digests.
- **It names the guarantee the tickets only implied, and it is stronger than what they argued.**
  All 37 `COLLISION-FORCE.txt` rows are rooted at `String.wz`, and `--force` is the only path past
  the additive gate — so **nothing in `Character.wz` or `Item.wz` was ever *capable* of being
  overwritten**. 04 argued from digests instead, and cited a child-level diff file that does not
  exist.
- **`WzMerge hash` is confirmed not to be a presence check** — `Canon()` hashes decoded leaf values
  recursively, so a changed child moves the ancestor digest. One blind spot: sibling order is
  normalised away, so a pure same-name reorder is invisible.

**Four documentation defects to fold in** — each is the kind that rots into a wrong decision three
tickets later:
1. `04-…:121` "`Pet.img` byte-identical" is **false** — `c3bc7d20d` added `String.wz/Pet.img/5000067`.
   The merge is right; the doc contradicts itself two sections apart.
2. "7,241 `Character.wz` image digests" is a **line count**; the real image count is **7,207**
   (7,207 + 17 subdir + 17 `TOTAL` rows). Coverage was genuinely complete; the label was wrong.
   Corrected in this file above. Ticket 05's "7,215 images" contradicts 04 on the same file.
3. **06's "95 added" is the XML total — the binary side installed 94**, because `Sound.wz` exited 4
   and was discarded. 06's own §Sound section says so; the headline number carries no qualifier.
4. 06's drop-row count: 790 → **776**.

**And it corrected itself, which is the most useful thing in it.**
`V84MountNodeTest.v84MountSkillsMapToTheirSprites` is a **verbatim copy of
`StatEffect.buildSkillMounts()`** — same 8-row table, same job list, same arithmetic — so it asserts
itself and cannot fail unless someone edits one file and not the other. That is structurally the
same sin ticket 05 accused the *old* test of, just larger. The parts that earn their place are the
**negative** controls (`assertNull(skillMountItem(1121017))` catches exactly the `% 10000`
regression class) and `v83MountsStillMapToTheSameSprites`, keyed on `constants.skills.*` symbols.
Since skill→sprite pairing is client-hardcoded and provable from no WZ node, **no test can be
evidence for it** — that assertion is a change-detector and should be labelled as one.

One claim is neither confirmable nor refutable: 05's "the old test asserted `20001017`" —
`git log --all -S'20001017'` is empty, so the string never existed in this repo and the critiqued
test lived in reverted uncommitted work. The *reasoning* holds (`Legend.YETI_MOUNT1 = 20001019`, and
`10001019 % 10000 == 1019 == Beginner.WITCH_BROOMSTICK`).

Also worth recording: 05's three output hashes verify byte-identically across **four** independent
runs, so determinism is real rather than asserted — but the staged binaries live outside the repo,
so "re-derivable from the committed tree" is overstated. The repo carries only path lists.

### Owner decision needed — Crimson Sky travel route

06 could not deliver criterion 5 and was right not to fake it. The only edge between Crimson Sky and
existing content is `240080000/left00 → 240030102`, pointing **outward**, and `240030102` has no
return portal in **either** v83 or v84 — so there is no node to merge and `conflicts.txt` is empty by
construction rather than by luck. The one Leafre map v84 did edit (`240000000`) gained a scripted
`tp` portal and lost an NPC; no route. Three options are written up in the ticket. A second gate is
`needSkillForFly=1`. **Crimson Sky is otherwise complete and unreachable until this is decided.**

## Run order to completion (owner directive 2026-08-16: run until the entire work is done)

**Design rule discovered while re-dispatching, and it changes what a content ticket delivers:**
**staged merges from the same base do not compose.** If two tickets each stage a full `Character.wz`
from the same live base, installing both silently loses one set of changes. So from now on a content
ticket's authoritative deliverable is its **path list** (`docs/wz-baseline/merge-lists/<NN>/<Wz>.paths.txt`)
plus its server XML and SQL. It still stages and verifies a `.wz` — that is how it proves the list is
correct — but the client files ship from **one composed merge at install time**, consuming every
ticket's path list together. A dedicated install pass does that before 16.

Tickets are serialized by **file ownership**, not by the dependency graph alone, because concurrent
agents editing the same `wz/*.img.xml` was a real source of damage this session.

| batch | tickets | owns | why not parallel with the others |
|---|---|---|---|
| A *(running)* | **04**, **06** | 04: `Character`/`Item`/`Etc` + `String.{Eqp,Etc,Consume,Ins,Cash}` · 06: `Map`/`Mob`/`Npc`/`Reactor`/`Sound` + `String.{Map,Mob,Npc}` + drop SQL | disjoint by construction |
| B | **05**, **07** | 05: `Character/TamingMob`, `Morph`, `Skill`, `Eqp/{Taming,Dragon}` names · 07: Neo City maps | 05 collides with 04 on `Character.wz`; 07 collides with 06 on `Map.wz` + `String/Map.img` |
| C | **08**, then **09** | 08: misc v84 areas · 09: quests | 08 collides with 06/07 on `Map.wz`; 09 is blocked by 06+07+08 |
| D | **install pass**, then **16** | composed merge of every path list; regression | needs all content lists |
| E | **10 → 11 → 12 → 13 → 14 → 15** | the Evan branch | **hard-blocked on ticket 01's human launch test** |

> **Superseded 2026-08-16.** 01's runtime patch ran and verified against the live process, so the
> Evan branch is unblocked and **10 is done bar the in-game checks**. The collision worry above did
> not materialise: 10 needed **zero** `Character.wz` rows (04 had already merged all twenty
> `00002000.img` Evan action rows) and re-added none of 05's `Eqp/Dragon` or `Eqp/Taming` force
> roots. **11 → 12 → 13 → 14 → 15 now all need the client running and the composed merge installed**
> — that copy is the highest-value thing the owner can do.

> ### ⛔ 04/05/06 STOPPED AND KILLED, 2026-08-16. Merges frozen again until 03e lands.
> R3's main report arrived and the stop call was right. **Two blockers I had believed closed were
> never actually implemented, and I had reported one of them to the owner as handled — my error;
> I did not verify it.**
>
> - **B1 — the deny-list is inert.** `COLLISION-DENY.txt` is 28 imperative rows and **`Program.cs`
>   contains zero occurrences of `deny`, `force`, or either filename** (orchestrator-verified by
>   grep). The procedure mentions neither list. Every deny hazard is a v84 *addition*, so the
>   additive gate structurally cannot catch it and `conflicts.txt` is empty for all three. The data
>   is correct; the enforcement does not exist.
> - **B2 — the staging guards do not fire in the configuration the procedure itself uses.** All
>   three guards at `Program.cs:386-391` are relational to `<targetWz>`, but §5.4 sets target to a
>   *staged copy*, so `<out> = D:\games\MapleStory\Map.wz` passes all three and `File.Move(...,
>   overwrite: true)` overwrites the live client. **My earlier verification was correct but narrower
>   than I claimed for it** — I tested target = the live file, which is not what the runbook does.
> - **B3 — `deps` under-reports at exactly the granularity ticket 06 needs**: `Back`/`Tile` at
>   whole-image level, which the merge then discards as "already exists", so 8 of 9 Crimson Sky maps
>   would ship with missing backgrounds; and it reports "0 dependencies" for the 8 link-stub maps.
> - **B4** §5.3 hardcodes `Map2` and fails to a **silent exit 0**; **H1** `<stage>\pre\` is shared
>   mutable state, so a later ticket can silently revert an earlier one with both exiting 0.
>
> **What survived:** ticket 05 got furthest — staged `Character.wz` (+8), `Skill.wz` (+27),
> `Morph.wz` (+25), all `refused 0`, none touched by any deny hazard (those live in `Etc`/`String`/
> `Npc`, which 05 never merged), plus server XML applied to the working tree and git-recoverable.
> 03e will re-run those three merges under the fixed tool and report byte-identity.
>
> **Client exposure: none.** All five client `.wz` — including `Character.wz` — hash-match the backup.

> ### ✅ 03e LANDED — all four blockers closed with before/after output, 2026-08-16
> Evidence: `docs/wz-baseline/merge-lists/03-verification/safety-guards.md` §G10–G16. Each
> comparison runs the **previous committed `Program.cs`**, rebuilt unchanged from `git show HEAD:`
> as a second binary, against the fixed one on the same arguments.
>
> - **B1 closed.** `--deny` is now REQUIRED on every `merge` and `xml` (dry runs included);
>   `--force` is optional and is the only way past the additive gate. Demonstrated: the old tool
>   wrote `Etc.wz/NpcLocation.img/9901910 = 100030301` into its output and exited 0; the new one
>   refuses all ten with the reason quoted from the file. Root semantics measured on the full
>   `String.txt` — refusals 711 → 747, and the difference is **exactly the 36 MonsterBook reward
>   slots across 17 mobs** that 03c predicted. Deny/force overlap is exit 2.
> - **B2 closed** with an absolute rule about the OUTPUT DIRECTORY, not a relational one: refuse
>   any directory holding an `.exe`, or holding `.wz` files WzMerge did not put there. Proven both
>   ways — the old tool overwrote a file in a client-shaped directory (0 → 6,322,806 bytes) on the
>   procedure's own target configuration; the new one exits 2. Testable against the real client
>   with zero write risk via the new read-only `WzMerge guard <outWz>`.
> - **B3 closed.** `deps` resolves against the add-list, so `Map/Map2/240080000.img` now yields the
>   ten `Back/dragonRoad.img/{ani,back}/*` rows instead of the whole-image row the merge discards,
>   and a link stub walks through to its target. `info/link`, `info/bgm` and `info/mapMark` added.
> - **B4 / H1 / H4 closed.** `deps` takes a bare id and finds its bucket (`683010000 → Map6`);
>   a 0-row paths file is exit 2; "refused rows and added nothing" is the new exit 5; `pre\` is
>   per-ticket and `--live` hashes the snapshot against the live file before every real merge.
> - **M2** wired the content digest into the merge path — inserted-into images are digested in
>   memory and re-digested off the written file, exit 4 on drift.
>
> **Discard 04's and 06's staged output.** `Server\wz-merge\04\{Character,Item,String}.wz` and
> `Server\wz-merge\06\{Map,Mob,Npc,Reactor,String}.wz` were produced with the deny-list inert; 04
> merged `String.wz` and 06 merged `String.wz`, `Npc.wz` and `Map.wz`, all of which the deny-list
> now touches, and 06's 22 `*.deps.txt` are at the old whole-image granularity that under-reports.
> Both tickets re-run into a fresh `<T>-r2\` directory — the output guard refuses to write beside
> unmarked `.wz`, which enforces that by itself. **05's `Server\wz-merge\05\` is verified good and
> can be installed as it stands.**
>
> **Ticket 05 stands.** Its three merges re-ran under the fixed tool and are **byte-identical**
> (`Morph E8E3D94E…`, `Skill 69AE95DF…`, `Character FC50BE70…`), all content-checks clean. Nothing
> it did was affected; it only gained checks it now passes. **04–06 can be re-dispatched against
> the procedure as written.**

> ### ⏸ Earlier hold, superseded by the above
> R3's addendum recommends stopping 04–06 on findings B1–B3 that were never delivered to me. I am
> not letting three agents commit merged WZ data on a recommendation I cannot read, and I am not
> ignoring it either. All three told to continue analysis/SQL/XML/dry-run work but **not to promote
> any `.partial` or commit merged WZ output** until cleared.
>
> **Exposure, checked rather than assumed:** the live client is fully untouched — `Item`, `String`,
> `Npc`, `Map` **and `Character.wz`** all SHA256-match the backup, and no `.partial`/`.merged`/`.TEMP`
> exists beside it. All merge output is confined to per-ticket staging dirs under
> `Server\wz-merge\{04,05,06}\`. The 262 working-tree changes are server-side XML plus two `src/`
> files, all git-tracked and reversible with `git checkout -- wz/ src/`. Nothing is committed.

**Orchestrator verification of 03** (independent, not taken on trust): live `Item.wz` and
`String.wz` still SHA256-match the backup, so the client is untouched. `./mvnw -Dtest=V84TracerNodeTest
-DfailIfNoTests=true test` re-run from a clean shell → **exit 0**, so the server-side criterion is
genuinely met rather than reported. Merge tool, procedure doc, and all 15 per-file dry-run conflict
lists are present on disk.

## Frontier

**03 is done bar the in-game check.** The pipeline exists, is scripted, and is proven on one node:
additive-only enforced in the write path, BlockSize-invariance verified (nothing pre-existing
changed), both mechanical traps resolved, the server side agent-verified against real output.
`docs/work-plan/WZ-MERGE-PROCEDURE.md` is what 04–09 execute — **do not invent a second way.**

**04–09 unblocked** by 02d closing R1's H1/H2/H4/H5 and by 03 landing the pipeline.
Each should open with a `WzMerge merge … -` dry run. Three findings they must not rediscover:

- **`Npc.wz/9901910`–`9901919` collide with Cosmic's injected `99xxxxx` NPC block** — 10 sprite
  nodes plus 10 `String.wz` names. v84's new NPCs cannot be imported at their native ids without
  destroying server content. **Ticket 08 must re-id or drop them.** Spot-checked: live
  `String.wz/Npc.img/9901910` is Cosmic's Lv.120 Thunder Breaker fame NPC, v84's is a Lv.200 line.
- **759 collisions across 16,052 add-list roots** (was 41/2,172 before the depth-3 rebuild — that
  figure is superseded): String 711, Npc 34, Character 6, Etc 6, Map 2; Item/Mob/Quest/Reactor/Skill/
  Base/Effect/Morph/Sound/TamingMob clean. **A large refusal count is normal now** — 711 of the 711
  String refusals are `MonsterBook.img/<id>/reward/<n>`. Per-file lists in
  `docs/wz-baseline/merge-lists/addlist-dryrun-*.conflicts.txt`; triage in flight (03c).
  Not all collisions favour the live client: `String.wz/Cash.img/5530001` is `MISSING NAME` /
  `MISSING INFO` locally and "DS Medal Basket" in v84. Additive-only drops that; a human shouldn't.
- **RESOLVED by 02g — expansion is now depth 3 uniformly, and every manifest was rebuilt.** The
  hole was far wider than "two images": `String.wz/Map.img` carried **zero** rows, so v84's 125 new
  map names were invisible to **06/07/08**, not just 04. All 125 import with zero collisions.
  See the 02g section for the full before/after table — protect-lists in particular were understating
  live-only content by orders of magnitude (`Character` 4 → 2,987).
  **Still true and unfixable by merging:** 18 live ids read the literal placeholder `MISSING NAME` —
  all twelve `Eqp/Dragon` equips, Evan's Mir + saddles, 6 medals. Additive-only cannot repair a name
  that already exists. Those need a deliberate overwrite via the `--force-list` path (03b/03c).
  Conversely **do not bulk-import `Eqp` names**: of 589 differing shared ids, the live name is better
  in 571 of them (Ezorsia renamed all 507 faces; v84 says "Male Face 19").

## R2 — code review of batch 02c–02f + 03

> ## ✅ LIFTED — 03b closed B1. The tool now refuses to endanger the client.
> Orchestrator-verified by running the exact footgun: `WzMerge merge <v84>/Npc.wz
> D:/games/MapleStory/Npc.wz D:/games/MapleStory/Npc.merged.wz …` → **exit 2, refused**, nothing
> created, and `Npc/Item/String/Map.wz` all still SHA256-match the backup. Merges stage to their own
> directory, write `.partial`, re-open and re-parse every added path before promoting, and pin CWD so
> MapleLib's `.TEMP` cannot land beside the client. Copying staged output onto the client remains a
> deliberate human step.
> **Known limit, stated not hidden:** verification catches truncation and structural damage — the
> shapes an interrupted or OOM write produces — but a 4 KB corruption inside a compressed canvas
> payload passed clean.

**Is the merge tool safe to point at the client? Reading from it, yes. Writing to it, no — and the
procedure's only worked example tells you to.** The danger is not the additive-only gate; the gate is
sound. It is `SaveToDisk`.

**The gate itself was traced into MapleLib and cleared.** All three `Resolve` legs
(`WzDirectory`/`WzImage`/`WzImageProperty`) are `OrdinalIgnoreCase` down through MapleLib's own
lookups, so **the `Ordinal` bug found earlier in the XML gate has no sibling in the binary path.**
`WzDirectory.DeepClone` sets `bIsImageChanged`, so added content is genuinely re-serialized under the
target's IV — no risk of v84 ciphertext being memcpy'd into a v83 file. Worth stating plainly: the
part most likely to silently corrupt content is correct.

### Blockers — 04–09 do not proceed until these close

- **B1 (critical) — `SaveToDisk` truncates the destination *first*, then streams for minutes, and the
  procedure's only concrete invocation targets the live client.** `File.Create` truncates before a
  single image byte is written; unchanged images are then streamed out of the *source* file over the
  following minutes (Item.wz ≈200 MB, Map.wz 629 MB). `outWz == targetWz` fails to corrupt the client
  **only because** `FileShare.Read` makes `File.Create` throw first — the OS saving us, not the tool;
  there is no `outPath != tgtPath` guard anywhere. Any *other* pre-existing output file is destroyed
  up front and rebuilt non-atomically, and **the tool never re-opens its own output**, so an OOM,
  full disk or Ctrl-C leaves a plausible-looking truncated `.wz` that nothing stops you copying onto
  the client. Also: MapleLib's temp file is a **relative** path, so running the tool from
  `D:\games\MapleStory\` drops a multi-hundred-MB scratch file into the live client directory.
- **B2 (critical) — the add-list is not exhaustive, and the hole is wider than I first briefed.**
  `add-list/String.txt` has zero rows for `Eqp.img`, `Etc.img` **and `Map.img`**. **v84's new map
  names are entirely absent from the manifests** — that is the "no blank labels" criterion in
  **06, 07 and 08**, not just 04. Same rule likely blinds `Skill.wz/<job>.img/skill/<newId>` and
  `Quest.wz/{Check,Act,Say}.img/<id>/<step>`. The fix in flight (02g) is the right one, but it is
  **coupled**: the XML writer refuses depth > 1, so deepening the diff tool alone produces manifests
  the writer rejects wholesale. 02g has been told.
- **B3 (high) — the BlockSize-invariance proof cannot fail.** Only the inserted-into image is marked
  `Changed`; every other image is raw-memcpy'd and its `size` is carried straight off the input
  directory entry, never recomputed. So pre-vs-post compares a number to a copy of itself for ~523
  images. Reality is *better* than the evidence (verbatim memcpy beats size-equality) but that comes
  from reading MapleLib's source, not from anything measured — and **the one image that was actually
  re-serialized has no content check at all.** The delta arithmetic is circular: the added bytes are
  defined as the delta.
- **B4 (high) — ticket 06 will ship broken maps.** The procedure's ordering rule covers manifest
  parent/child only, **not asset references**. Cutting a subset of `Map/Map2/2400800*.img` — the
  obvious read of ticket 06 — ships maps whose `Back`/`Obj` sets are absent.

Highs: the merge **exits 0 when it imports nothing** (a scripted 04–09 loop reports green); `WzMerge
xml` has no dry-run despite two documents instructing one; the procedure mentions the backup **zero**
times and never says to close the client; its verification section is unrunnable as written. Mediums:
the XML gate asserts BOM/CRLF but silently depends on a two-space indent nothing checks; MapleLib is
unvendored and unpinned though every safety property rests on that exact source.

**Verified clean, no finding:** the 41/2,172 collision counts (recounted exactly), 6 new reactors,
8 mounts, `TamingMob` 797 B in all three trees, 02f's 13 files with zero overwrites, 832 removed maps.
And `V84TracerNodeTest` is **not** a grep — it constructs the same `XMLWZFile` the running server
uses, and the negative control genuinely fails 2. Dropping the DB-backed assertion **did not** hollow
it out: that provider reads through the same class, so it bought no extra coverage. Its real limit is
different and was unstated — it only compares values against XML the tool itself wrote, so
serializer fidelity is untested. Same gap as B3, from the other end.

## 02g — the manifests were incomplete everywhere, not just in two images

Orchestrator-verified on disk: `add-list/String.txt` now carries **125 `Map.img`** rows and **154
`Eqp.img`** rows where it previously carried zero; `protect-list/Character.txt` is **2,987** entries,
up from 4.

The fix is one uniform constant — `EXPAND_DEPTH = 3` for every image in every file, no allowlist
(an allowlist is what caused this bug class twice). Depth 3 is where ids bottom out in this era;
below it you are into animation frames and foothold vertices, which `modified-list` already covers
via BlockSize and which should never be a copy root. Cost was **measured, not assumed**: ~9 min and
~2 GB peak for the 3-tree sweep, versus ~5 min at depth 1. Full expansion was rejected on those
measurements. The ceiling is named in the code: an id nested 4+ deep is invisible again.

**What was previously invisible — this is the important part:**

| manifest | was | now | who it was blinding |
|---|---|---|---|
| `add-list/String.txt` — `Map.img` | **0** | **125** | **06, 07, 08** — v84's new map names |
| `add-list/String.txt` — `Eqp.img` | 0 | 154 rows / 165 names | 04 |
| `add-list/String.txt` — `Etc.img` | 0 | 32 | 04 |
| `add-list/Skill.txt` | 18 | 55 | any skill ticket — 27 skills added to *existing* job images |
| `protect-list/Character.txt` | 4 | **2,987** | everything |
| `protect-list/{Npc,Map,Quest,Mob,Item,Reactor,UI}` | 5,333 / 10 / 4 / 0 / 4 / 7 / 0 | 5,981 / 399 / 225 / 168 / 106 / 49 / 41 | everything |
| `removed-list/{Mob,Etc,Map,Character,Quest,Item}` | 0 / 77 / 833 / 28 / 1 / 0 | 674 / 2,028 / 1,017 / 136 / 39 / 35 | 16 |

So the earlier "zero removals for Item/Mob/Skill/String/UI/Reactor" was true only at *image* level.
`modified-list` is unchanged, as it should be — it is BlockSize-based and depth-independent, which is
a useful cross-check that nothing else moved for the wrong reason.

**Collisions moved 41/2,172 → 759/16,052 roots.** A large refusal count is now normal, not a failure
signal: 711 of the 759 are `String.wz/MonsterBook.img/<id>/reward/<n>`, and 10,459 of `Etc.wz`'s
roots are `Commodity.img/<sn>/Bonus`, a field v84 adds to existing entries. `Map.img`'s 125 new map
names collide **zero** times — all importable.

**The blank-label problem is real and additive-only cannot fix it.** Comparing every shared `Eqp.img`
id: 589 names differ, and in all but 18 the *live* name is better (Ezorsia renamed all 507 faces;
v84 says "Male Face 19" — do not import those). The 18 that matter are where the live value is the
literal placeholder `MISSING NAME`: **all twelve `Eqp/Dragon` equips** (`1942000`–`1972002` — Silver/
Gold/Reverse Mask, Pendant, Wings, Tail), **Evan's Mir and its saddles** (`1902040`–`1902042`,
`1912033`–`1912035`), and 6 medals. These need a deliberate overwrite — ticket 04 and 13 both.

**Two new structural limits, now documented:** a *collapsed copy root hides its interior* (`Eqp/Dragon`
is one add-list row whose twelve `MISSING NAME` ids were never compared), and *depth 3 can cross a
UOL* — the 24 new `Npc.wz/9000021.img/{say,stand}/<n>` refusals are `parent=WzUOLProperty`, i.e.
structural refusals, not id collisions, which an operator would otherwise misread as conflicts.

## 03c — collision triage, and the hazard class the gate cannot see

`docs/wz-baseline/merge-lists/COLLISION-TRIAGE.md` + `COLLISION-FORCE.txt` (37 rows).
Buckets over 735 collisions: **35 no-op** (live == v84 byte-for-byte), **653 keep-local**,
**37 adopt-v84**, **10 ambiguous**. The adopt rule carries zero judgement — it is exactly the rows
where the live value is the literal placeholder `MISSING NAME` / `MISSING INFO`.

**The classification signal I pointed it at was wrong, and it was right to discard it.**
`protect-list` is defined as *live AND NOT v83 AND NOT v84*, so **by construction no collision can
ever appear in it** — using it would have mislabelled all six Cosmic `Commodity.img/894x` cash-shop
rows as "stock leftover". It did a three-way *value* compare instead and found 733 of 735 paths are
absent from v83-stock entirely: the real axis is **stub vs real content**, not custom vs stock.

### ⚠ Two hazards additive-only cannot catch, because they are *additions*, not collisions

`conflicts.txt` is silent on both. The gate stops overwrites; it does not stop writing something new
into a place the server owns. Both are now requirements on 03b (a **deny-list**: refuse listed paths
regardless of presence — a mechanism the tool does not have today).

1. **`Etc.wz/NpcLocation.img/9901910`–`9901919` would merge silently onto a server-owned id range.**
   On `add-list/Etc.txt:10569-10578`, absent from the live client, so nothing fires. v84's value is a
   fixed world placement (`9901910/0 = 100030301`) for Nexon's Lv.200 fame NPC. Orchestrator-verified
   in `src/main/java/server/life/PlayerNPC.java:66-67`: `9901910` is the **base of a ~4,700-id range
   the server allocates from at runtime** (`9901910`–`9906599`, plus `9977777`), not ten stray nodes.
   This is the real `99019xx` clash — refusing the ten `Npc.wz` images does nothing about it.
2. **Additive-only actively corrupts positional arrays.** Of 689 `String.wz/MonsterBook.img/<id>/reward/<n>`
   slots on the add-list, 654 are refused and **36 get written** — into 17 Cosmic drop lists. Cosmic's
   entries sit at indices 0–22, Nexon's land at 23–28, producing a list neither vendor ever shipped.
   No partial answer exists; exclude `MonsterBook.img/*/reward/*` wholesale. **General rule: any
   manifest row that is one slot of a positional array is unsafe to merge piecemeal.**
3. Lesser: the 24 `Npc.wz/9000021.img` UOL refusals leave that NPC **partially merged today** — worse
   than either whole version.

**Deny-list seeded as data:** `docs/wz-baseline/merge-lists/COLLISION-DENY.txt`, 28 rows — the 10
`NpcLocation` ids, **17** `MonsterBook/<mob>/reward` parents (root semantics reach all 36 at-risk
slots, so **no wildcard syntax is needed**), and `Npc.wz/9000021.img`. Format is deliberately
identical to `COLLISION-FORCE.txt` so one parser serves both; **deny wins over force, and an overlap
should be a hard exit rather than a silent resolution.** The `--deny-list` flag itself belongs to
03b — 03c correctly declined to edit another agent's files and delivered the data instead.

**The 28 deny rows are not of equal confidence, and the procedure must not flatten them:**
the 10 `NpcLocation` rows are hard evidence (`PlayerNPC.java:66`); the 17 `reward` roots are a
mechanical consequence of the 36-slot splice; **`Npc.wz/9000021.img` is a judgement call made on the
operator's behalf** and is marked as such in-file. That last row is the one that will be wrong later
if it is presented as settled evidence alongside the other 27.

### Drop the ten `99019xx` — confirmed, and for a stronger reason than the review gave

Not merely "re-id is unimplementable". `9901910` is a live server allocator base (verified above), so
re-iding means relocating server logic. And the live client is a strict *superset* of v84 on these
paths — v84's `9901910.img` lacks `info/speak/1`, `say/speak/1` and `stand/delay` — so a successful
re-id would import something **poorer** than what is already there. Ticket 08 should say drop.

### Ambiguous: 10 → 4. Only the Dragon rows are left.

**`Etc.wz/Commodity.img/894{1..6}` — settled, keep local, zero loss.** The cash shop is not DB-driven
(`CashShop.java:239-249` loads `Commodity.img` from WZ and keys the map by **`SN`, ignoring node
names**), but it does not matter, because **v84's payload already exists locally** as nodes
`8848`–`8853` (`SN=70000365`…`70000370`). The live client merely renumbered those six entries and
reused `894{1..6}` for pets. Adopting v84 would insert six **duplicate SNs** and delete the six pet
SNs `60001000`–`60001005`, which the client still renders — every click would hit `getItem(sn) → null`.

*Correction worth recording:* I made that argument from the **server's** `wz/` tree while the merge
targets the **client's** `D:\games\MapleStory\Etc.wz` — different files, different owners. The agent
caught it and re-verified against the client too. Same conclusion, but the argument was incomplete as
I gave it. The general lesson, kept in the triage as a worked example: **an id collision is not a
content collision — check whether the other side's content already exists elsewhere in the file
before weighing a trade-off.**

### The 4 remaining ambiguous — need an owner decision

- **`Character.wz/Dragon/019{4,5,6,7}2002.img/info/level` (4 rows).** Live is a flat `exp=10000` for
  every level with no stat increments; v84 has a real curve plus per-level `incSTR/DEX/INT/LUK/PDD/MDD`
  and `case/0/prob`. The Evan tickets probably want v84 — but Cosmic's flat table is a deliberate
  *shape*, not a stub, so it may be load-bearing tuning. **Decides on: does the server read
  `Character.wz/Dragon/*/info/level`, or is dragon levelling server-computed?** Display-only → adopt.
  Server-read → adopting changes progression for existing characters.
- **`Etc.wz/Commodity.img/894{1..6}` (6 rows).** Live: `SN=60001000..60001005`, six pets, all
  `OnSale=1`. v84: `SN=70000365..70000370`, different items, five of six `OnSale=0`. Adopting deletes
  six working cash-shop listings. Reads as keep-local and that is the force-list default — flagged
  only because **`SN` is a server-side key**. **Decides on: does `CashItemFactory` read
  `Commodity.img` from the client WZ at all, or is Cosmic DB-driven?** DB-driven → both sides inert,
  keep local.

## Manifests — final, and how to consume them

Commit `0a9036bb1`, in `docs/wz-baseline/`. Paths are collapsed to **copy roots**: no listed path is
an ancestor of another, so copy the listed path and never separately copy its children.

| List | Meaning |
|---|---|
| `add-list/` | nodes v84 adds over v83 — the import source |
| `protect-list/` | nodes only in the live client |
| `modified-list/<wz>.txt` | paths in both v83 and v84 whose content differs — **v84 edits, which additive-only merging will refuse; read `conflicts.txt`** |
| `modified-list/<wz>.live.txt` | paths where the live client differs from v83 — **the real custom-content list** |
| `removed-list/` | nodes v84 deleted |

**Operational rule for 04–09: every path in `modified-list/*.live.txt` is protected, exactly as if
it were in `protect-list/`.** `protect-list/Character.txt` has 4 entries; `modified-list/Character.live.txt`
has **5,114**, and not one is a trivial-sized delta — that is Ezorsia's 18.6 MB of HD art, sitting
under stock node paths where presence-only diffing cannot see it. Protecting only the protect-list
destroys the client's custom art.

## 02d — what the rebuilt tool found

Each fix was proven by demonstration, not inspection (e.g. H1 was verified by corrupting an image
header in a scratch copy and showing the old tool silently lost 6 real additions where the new one
reports `[PARSE-FAIL]`).

- **The old manifests were incomplete, not poisoned.** 55,112 images parsed across three trees with
  **zero** failures in the ten previously-diffed files. Exactly one real parse failure exists —
  `live Sound.wz/BgmGL.img`, which MapleLib cannot read at all (`InvalidDataException`). It sits in a
  file that was never baselined, so nothing shipped was corrupted. Anyone doing BGM work must know.
- **Expanding the walk found 33 previously-invisible additions**: `Map.wz` +7 (five new `Obj`
  sub-sets — `acc1.img/DragonDream`, `acc1.img/heneFarmFD`, `acc1.img/heneFarmTW`, `acc12.img/dragon`,
  `dungeon3.img/skyValley` — plus `Effect.img/evan` and a minimap added to an existing map),
  `Character.wz` +26 (20 Evan dragon animation states, 6 `Afterimage/mace.img/*`), `Npc.wz` +8.
  Those five `Obj` sub-sets are the concrete breakage case: a v84 map referencing
  `Obj/acc1.img/DragonDream` on a client lacking it is a broken map.
  **My "zero new Obj across 76 new maps is implausible" framing was directionally right but
  overstated** — the real answer is five, and there are genuinely zero new `Back`/`Tile` sub-nodes.
  v84 reuses existing object sets heavily. Recorded because the agent was right to report the real
  number rather than the expected one.
- **BlockSize is a conditional signal, and you must read the sizes rather than the row count.**
  `Mob.wz` v83→v84 shows 1,171 changed images but **671 differ by ≤4 bytes** — re-encodes, not
  content. `Character.live.txt` shows 5,114 with **no** trivial deltas — real substitution.
  False negatives (same-size replacement) remain possible; escalation is canonical-serialization
  hashing, deliberately not built.
- **Reactor is genuinely baselined now** using 02c's recovered `Reactor.wz`: **6 new v84 reactors**
  (`1002008`, `1409000`, `2302006`, `2408005`, `2408006`, `2409000` — new-area reactors, as R1
  predicted), and the old 2-entry protect-list was indeed suspect.
- **`removed-list/` now covers every file**, so removals are measured rather than assumed: Map 833,
  Etc 77, Character 28, Npc 7, Quest 1, and zero for Item/Mob/Skill/String/UI/Reactor.
- **Counts moved for a good reason.** `protect-list/Etc.txt` 5,267 → 4 and `add-list/Skill.txt`
  190 → 14, because sub-keys collapsed into their copy roots. No content was lost; raw path counts
  are in each manifest header.
- `List.wz` is not a WZ archive at all (`FStart` outside the file under every IV) — kept as a
  visible row rather than silently dropped.

## R1 — code review of batch 01+02

**Verdict: 01 is sound. 02's manifests are real but were produced by a tool with silent failure
modes; regenerate before anything consumes them.**

High severity (all routed to 02d unless noted):
- **H1 — parse failures are silently counted as node absences.** MapleLib reports image-parse
  failure by *return value*, not exception; the tool's `catch` is dead code for the real failure
  mode. A failed v84 image silently drops content from every downstream ticket; a failed live
  image leaves custom content unprotected. This is the one that most undermines trust in the
  existing manifests.
- **H2 — `Map.wz` is walked shallow, so v84's new `Obj`/`Back`/`Tile` sub-nodes are invisible.**
  The tell: zero new `Obj` entries across 76 new maps. Importing a v84 map whose object references
  are absent from the live client gives a broken map or a client crash — surfacing at ticket 06/07,
  not here.
- **H3 — the additive-only merge rule is right, but its stated proof is vacuous.** A presence-only
  re-diff cannot show that no pre-existing node *changed* — an overwrite preserves paths too.
  Fix: snapshot `BlockSize` per path pre/post-merge (already read by the tool, ~10 lines). The same
  mechanism run across v83/v84 also yields the missing **modified-nodes** list, without which
  additive-only silently drops every v84 change that is an edit rather than an addition — e.g. the
  16 mobs v84 only *renamed*, and portals added to existing maps that lead into Crimson Sky / Neo
  City. Routed to 02d (build it) and to ticket 03 (adopt it as the acceptance criterion, and log
  every skipped write to `conflicts.txt`).
- **H4 — `Reactor.wz` has no v83 baseline** and `SUMMARY.md` prints `0` where the truth is
  "unknown"; its protect-list was computed without the v83 term, so entries may be stock content
  mislabelled custom.
- **H5 — `TamingMob`, `Effect`, `Sound`, `Morph`, `Base`, `List`, `EzorsiaV2_UI` were never
  baselined and no `MISSING` row flags it. Ticket 05 (mounts) currently has no source data** —
  mount definitions live in `TamingMob.wz`.

Notable mediums: exception aborts the encryption-IV fallback loop, which will bite ticket 03 the
moment it re-saves a file under BMS (M1); protect-list is `live − (v83 ∪ v84)` rather than
`live − v83`, suppressing ~18 live custom nodes (M2); ticket 02's manifest cross-check is ticked
but its own Findings admit two of seven categories are unexplained (M5); and the human steps in
ticket 01 omit the most likely real failure mode — **antivirus/SmartScreen quarantining a NOP-sled
in a Themida-packed exe**, whose symptom would be misread as a Themida abort and send someone down
the `CUSTOM.dll` path for nothing (M8).

R1 confirmed independently: ticket 01's `0x361714` → VA `0x00761714` derivation is correct, the
`dinput8.dll` / `CUSTOM.dll` fallback assessment is sound, and "do not build `CUSTOM.dll`
speculatively" is the right call. It also endorsed swapping WzComparerR2 for MapleLib.

## 02e — what the upstream diff settled

Owner's lead ("our wz files have custom things in them — check P0nk Cosmic on git"). Verified by
the orchestrator: `upstream` = P0nk/Cosmic, merge base `cf5ba0923`, **43 ahead / 2 behind**, and
exactly three `wz/` files differ from upstream: `Quest.wz/Act.img.xml`, `Quest.wz/Check.img.xml`,
`String.wz/Cash.img.xml`. That is the entire server-side divergence — a quest item-count rebalance
(values roughly halved across 670 + 1,036 quest ids) and three cash coupons retimed 4h→40min. Zero
nodes added or deleted anywhere. The 14,000-line raw diff is almost all serializer reformatting.

Three consequences, in order of importance:

1. **The protect-list is mislabelled, and that is the real find.** All **16,037** protect-list
   entries exist in the upstream-identical `wz/` tree — Npc 5,332/5,332, String 5,417/5,417, Etc
   5,267/5,267, every small file 100%. **None of it originated in this fork.** It is upstream
   Cosmic's own injected content (`99xxxxx` NPC block, quest `7778`, `Map/Map7/777777777.img`,
   reactors `9400300`/`9400301`). The list is still operationally right — that content must survive
   the upgrade — but it is **regenerable from `upstream/master` on demand** rather than something
   to be reverse-engineered out of a client binary. Much cheaper than ticket 02 assumed.
   *Caveat:* this covers the node **list**, not the assets. `wz/` holds zero `.png`; the sprites for
   that content exist only in the live client, so the client-side merge still must not clobber them.
2. **The Map.wz damage finding survives, independently corroborated.** All **832/832** paths
   reported missing from our v84 dump exist in `wz/Map.wz/` — orchestrator-spot-checked: Monster
   Carnival `970030100`–`970042717` (845 files), Mu Lung Dojo `925*` (601), Sheep Ranch `9105*`.
   That tree is byte-identical to upstream and is v83-derived, so the content is unquestionably
   real v83 content and `wz-data/v84/` is genuinely damaged. Server-side customization does not
   explain it — the comparison never involved our files.
3. **`wz/` is a usable v83 baseline for structure and values, not for binaries.** It carries map
   geometry, portals, footholds, life, string tables and canvas dimensions as plain XML — grep- and
   ElementTree-scriptable, which is far cheaper than binary WZ tooling for id enumeration and stat
   lookups. It has no sprites or audio (`Sound.wz` is 44 empty shells). And it is **not** a stock-v83
   oracle: it is Cosmic-flavoured v83, so any ticket treating it as stock is wrong.

Also: the 2 upstream commits we lack include "Fix quest 21010 reward", touching the same two Quest
files we modified — a future upstream merge conflicts there. Not urgent, but it is a known landmine.

## 02f — baselines completed, and ticket 05's premise corrected

Both stock trees now hold every `.wz` the live client has. New rows: `Base` (add 0), `Effect`
(+20), `Morph` (+25), `Reactor` (+6), `Sound` (+62, −2 JP-only tracks), `TamingMob` (+0).
55,343 images parsed, 3 parse failures — all three are `Sound.wz/BgmGL.img`, one per tree, so the
failure is symmetric and biases nothing. `List.wz` is not a WZ archive in any tree (a flat 13 KB
list file, byte-identical between v83 and v84); kept as a visible row rather than silently dropped.

**Ticket 05 was pointed at the wrong file and has been corrected in place.** `TamingMob.wz` is a
**797-byte stub** in v83, v84 *and* the live client, with zero v84 additions — it has never held
mount definitions in this era, and only serves as an index target for an `info/tamingMob` integer.
Had the ticket been dispatched as written, an agent would have opened it, found nothing, and
plausibly concluded v84 added no mounts.

Mounts are in **`Character.wz/TamingMob/`** (47 → 55). The eight v84 mounts are
`019320{06,07,08,09,11,18,19,20}.img`, all absent from the live client, all real equips with full
animation sets and **no `String.wz` name in either stock tree**. Two things ticket 05 must also
touch that no one had identified: `Morph.wz`'s new `fly2`/`fly2Move`/`fly2Skill` states plus
`0050`–`0053.img`, and — the one a `Character.wz` diff structurally cannot find — **Evan's Mir**:
v84 *names* six ids whose sprites already shipped in v83 (`1902040/41/42` "Stage 1/2/3 Dragon",
`1912033/34/35` their saddles). Only the `String.wz` entries are new, so presence-only diffing of
`Character.wz` misses the patch's flagship mount entirely. That overlaps ticket 13.

The ticket's original id list (`8300000`–`8300007`) came from the naming manifest and is
unverified; the count matches but the id sets must be reconciled before anything is built. Recorded
in the ticket rather than silently rewritten.

## 03 — what the pipeline settled

Full procedure: **`docs/work-plan/WZ-MERGE-PROCEDURE.md`**. The four things worth carrying:

1. **The version trap is structural, not procedural.** `dump` measures it: live client `.wz` are
   `iv=GMS patchVersion=83`, `wz-data/v84/` are `patchVersion=84`. But the merge never opens a v84
   file for writing — v84 is read-only source and the file being saved is the live client's own,
   so MapleLib re-emits the IV and version hash it parsed off that file. There is no conversion
   step, therefore no menu option anyone can forget. **Do not "re-save v84 at v83 and copy from
   it"** — that is the folk technique and it re-encodes 600 MB to move two nodes.
2. **`basedata` never arises. Decision: re-export with base64 off.** Cosmic's `wz/` XML is exactly
   `WzClassicXmlSerializer(2, LineBreak.Windows, exportbase64: false)` — verified against
   `wz/Item.wz/Consume/0200.img.xml` (2-space, CRLF, no BOM, `<canvas>` with dimensions and no
   payload). The tool constructs that serializer and splices the fragment as **text** at sorted
   position, so the tracer's server-side diff is `19 insertions(+), 0 deletions(-)` with zero
   reformat noise. Exporting with base64 on and stripping afterwards is the wrong shape.
3. **Additive-only is a gate, not an audit.** `WzMerge` resolves the target path before writing and
   only ever calls `AddImage`/`AddDirectory`/`AddProperty` onto a parent that lacks that name.
   Verified two ways: BlockSize-invariance (one image changed per file — the one inserted into, by
   exactly the added bytes; `removed-list` empty; every other image survived a full repack the same
   size), and re-running the merge on its own output (`added 0, refused 1`). Known limit, stated
   not hidden: a same-size replacement would be a BlockSize false negative.
4. **A pre-existing test-isolation defect worth not rediscovering.** `WZFiles.DIRECTORY` is
   `static final`, resolved once per JVM, and `MobSkillFactoryTest` redirects `wz-path` to a
   `@TempDir`. Whichever test class runs first wins for the whole surefire fork, so any test
   reading the real tree through `DataProviderFactory` is order-dependent. Construct
   `new XMLWZFile(Path.of("wz", "Item.wz"))` explicitly instead.

## Open findings carried forward

1. **Character.wz protect-list blind spot.** Presence-only diffing cannot see ~18.6 MB of custom
   HD content substituted under stock node paths; only 4 nodes / 63 KB are listed. The manifest
   must not be read as "anything not listed is safe to overwrite."
   Mitigation: additive-only merge rule **plus** the BlockSize-invariance check from R1-H3 — the
   rule alone was not enough, and the proof it originally shipped with was not a proof.
2. **CLOSED — and the earlier conclusion was wrong. `wz-data/v84/` was never damaged.**
   02b observed correctly that 832 `Map.wz` paths vanish between v83 and v84; the *inference* that
   our copy was damaged was wrong, and I relayed it as established fact before it was. 02c settled
   it three ways: the dump is SHA256-identical to a fresh carve of `GMSSetupv84.exe` (`Map.wz`
   `38B9AEBA8E585F1E…`, and the CAB's own directory declares that same size, so there was never
   anything to truncate); `v83-stock` likewise matches `GMSSetupv83.exe`; and maplestory.io —
   which I checked myself rather than taking on report — serves `GMS/83/map/970030100`
   ("Stage 1 <Mano>") but 404s it under `GMS/84`, while Henesys resolves fine under 84.
   **GMS v84 genuinely deleted Monster Carnival, Mu Lung Dojo and Sheep Ranch**, and they were
   still gone in v92.
   Consequences: the README's "purely additive" premise is false and has been corrected in place.
   04–09 still proceed from `wz-data/v84/` unchanged, because we import additions and never delete.
   But **no ticket may wholesale-swap a WZ file** — replacing `Map.wz` would destroy ~832 working
   maps plus Ezorsia's 2.9 MB. Only `Map.wz` removals are characterised; the other ten files are
   unmeasured, not measured-clean.
3. **`add-list/Map.txt` still needs regenerating** — not because the source was bad, but because
   R1-H1/H2 mean the tool that produced it was. The 93-vs-79 manifest gap is now better explained
   by v84's removals than by any missing data.
4. **`v83-stock/` was missing `Reactor.wz`** (the v83 installer does ship it, 54,133,811 B).
   Extracted to `wz-data/v83-reactor/Reactor.wz` and left outside `v83-stock/` so it could not
   perturb 02d's in-flight run. This upgrades R1-H4 from a reporting fix to a real one: a genuine
   Reactor add-list is now producible. Handed to 02d.
5. NPC `9000071` (Keroben) — closed. Genuinely new in v84, not a naming artifact.

### 03i — gate widened, and the `hash` crash was not what it looked like

Commit `fa1093791`. Suite **1,996 green**; client **18/18** at start and end.

**The corrected rule:** a container is a positional array iff *every* child name is a non-negative
integer **and** those integers form **one consecutive run** — which need not start at 0. Both
discriminating clauses survive: *every* child an integer (so a map `.img` with layers `0`–`7` beside
`info`/`portal` is still not an array) and *consecutive* (so `String.wz/Consume.img`'s 2,290 gapped
item ids is still not an array — **dropping that clause would have refused 501 legitimate name rows**,
which is exactly why 03h declined the broader rule and 03i declined it again).

It classified **68** indexed parents, a superset of the regression pass's 34: 29 `ARRAY-0`,
**2 `ARRAY-N`**, 30 `RUN-WITH-HOLES`, 7 not-all-integer. **Exactly the two glove parents change class,
and the before/after dry run over all eleven lists differs in exactly those two rows** — `Character`
242/12 → 240/14, every other file's pair identical.

**It declined to cover `Check.img/4940`** (`{0,1,4961}`), which the review had flagged — covering it
means allowing holes, which turns every id table into an array. No row targets `4940`. Recorded as a
stated blind spot rather than quietly widened, and 03h's "the gate refuses all 123 rows structurally"
is now qualified as true of *those* rows, not a general guarantee.

**The `hash` crash was a UOL cycle, not depth.** `Reactor.wz/1050000.img/0/hit/2` is a `WzUOLProperty`
pointing at its own ancestor, and `Kids()` on a UOL returns the *resolved target's* children — so
`Canon()` walked `0 → hit/2 → 0` forever, branching twice per level. **A depth bound alone would still
have expanded 2^depth lines.** Fixed by digesting a UOL as its link string (the target is digested at
its own path anyway), plus depth 64 as a backstop that writes a `DEPTH LIMIT` marker *into* the hashed
text — so a truncated subtree can never digest equal to an untruncated one. `hash <Reactor.wz>` now
exits 0 in 0.33 s.

**`Map.wz` triage — 395 rows, not 389** (the regression pass subtracted 08's 6 merged rows twice):
216 refused structurally, 119 safe scenery appends left unclaimed, **55 denied as 28 roots**. Nothing
untriaged. **14 of the 216 are refused only because of this ticket, and every one is a
`foothold/<n>`** — v84 numbers footholds from 1, they are collision geometry, and the server reads
them (`MapFactory.java:197`). That is a bigger practical effect than the glove. Among the 55: eight
`portal` rows, five of them `pn=sp` spawn points that change **where a player lands**.

The two `Item.wz` `reward/43` deny rows are in, and an XML dry run confirms **both are refused on the
XML side too** — so 03j's revert cannot silently regress. All of R5's operator-facing text defects are
fixed, including the install doc's contradictory closing line.

One latent bug left deliberately, with a `ponytail:` comment naming the fix: `PositionalRefusal`
memoises the baseline before this run's appends, so a manifest containing both `…/obj/25` and
`…/obj/25/foo` would refuse the second. **It cannot fire** — no composed row is an ancestor of
another, re-checked — and speculatively fixing it is the thing this codebase is trying not to do.

---

### 10 — the Evan tracer. Small merge, two corrections that are bigger than the merge.

Suite **2,008 green** (1,996 + 12). All **18** client `.wz` SHA-256-match the backup at the start
**and** at the end. 88 path rows in `merge-lists/10/`, `added 88 / refused 0 / denied 0 / forced 0`
on all four files, exit 0. Composition re-run with 10 folded in: `compose.ps1` `$expect`
1,662 → **1,750**, thirteen files, staged at `Server\wz-merge\10c\`.

**Correction 1 — the client was never installed, and my dispatch said it was.** I briefed 10 that
the composed merge was live on the client and that the composed lists were therefore the live
contract. It is not: all 18 `.wz` are byte-identical to `_backup\client-v83-EzorsiaV2-2026-08-15\`
and **none** of the eleven matches its 03i staged output (`String.wz` live `9437DEB8…` 3,561,285 vs
03i `04ADEF71…` 3,612,239). The ticket's own first check agreed with my brief, was contradicted by
its second, and it chased the contradiction instead of choosing between them — which is the only
reason this was caught. **This file's closing line was right all along; my dispatch was the wrong
one.** The practical consequence is the install target: ticket 10's own `wz-merge\10\` was merged
from pristine v83, the same base as 03i, so those two sets **do not compose** and installing
`10\String.wz` would drop tickets 04–08. **Install `10c\`, thirteen files, and nothing else.**

**Correction 2 — "the gap is WZ data, not Java" is false, and it blocked two acceptance criteria.**

- **`JobCommand.java:41,53` rejected `jobid >= 2200`** — i.e. every one of Evan's ten job levels.
  `!job 2200`, which is literally criterion 2, printed "not available". Now asks `Job.getById`,
  which is both correct and wider: the old guard let any id under 2200 through to `changeJob(null)`
  and returned silently with no message at all.
- **`Character.createDragon():10246` only constructed the Dragon**, and the constructor sends the
  spawn to its *owner* only; map registration and the broadcast lived solely in
  `MapleMap.addPlayer:2676`, which **both** callers run after. So a job-changed Evan's dragon was
  invisible to every other player until the Evan changed map — criterion 4. Extracted to
  `MapleMap.spawnDragon(Dragon)` and called from both.
- **`Dragon` overrode `getObjectId()` to return the owner's character id**, while `addMapObject`
  files objects under a fresh map OID and `removeMapObject(obj)` looks that key up through
  `getObjectId()` — so **every dragon removal silently missed** and a departed Evan's dragon stayed
  in `mapobjects` to be spawned for the next arrival (DRAGON is a non-ranged type, `MapleMap:3067`)
  — criterion 5. Override deleted; its one consumer rewritten; every packet already read the owner
  id from `getOwner().getId()`. Demonstrated on a real `MapleMap` with the old shape as the negative
  control in the same test.
- **And a fourth, which 10 *introduced* and its own code review caught — worth reading as a pattern,
  not just a bug.** The fix for `createDragon()` registered into `getMap()`, but **`Character.map`
  is never cleared when a player leaves a map** — the Cash Shop / MTS path runs `removePlayer` and
  leaves the field set. So `!job <name> 2210` aimed at someone in the Cash Shop would file a dragon
  into a map its owner is not in, *after* the removal that would have cleaned it up: **the same
  ghost dragon, re-created by the fix for it.** Guarded inside `MapleMap.spawnDragon` on
  `characters.contains(owner)` so every caller inherits it, and negative-controlled — deleting the
  guard fails the test on its assertion, not on an incidental NPE.

Eight more defects were **measured and left alone**, each routed by owner rather than guessed at —
an Evan gains **0 HP/MP per level** (`levelUp():6327-6361` has no matching branch) and job 2001 is
awarded *warrior* HP (`changeJob():1190`, `2001 % 1000 == 1`), both **ticket 14**; `Evan.MONSTER_RIDER`
20011004 fails `sourceid % 10000000 == 1004` (it gives 11004), **ticket 12**; and a latent
`AIOOBE` in `getJobBranch` for EVAN5–EVAN10. Full table with `file:line` in the ticket. **The DB
schema claim is true — `002-character.sql:28` is already the ten-slot `sp` column and no SQL was
written.**

**Hazard F4 is closed, and it inverted.** The worry was that `StatEffect`'s speculative
`20011025`–`20011039` mapping would turn a real Evan skill into a mount. The code is an **explicit
eight-id list, not a range**, and dumping the merged `2001.img` shows **all eight are real Evan
skills and each is exactly the mount the table pairs it with** — Wooden Pony, Croco, Black Scooter,
Pink Scooter, Nimbus Cloud, Unicorn, Low Rider, Red Truck, 8/8 by name. So the failure mode cannot
occur. `20011026` is "Soaring" — flight, correctly excluded, and now the negative control that
fires if anyone widens the list back into a range. The one thing it *did* surface: `2001.img` also
ships `20011018`/`19`/`31` (Yeti Rider, Witch's Broomstick, Balrog) as real mounts with **no sprite
mapping**, and the id offsets do not transfer from any other job — so it recorded them for ticket 12
rather than inventing three sprite ids, which would have been F4 all over again.

**What it did not have to do, and this is the useful part for 11–15:** `Character.wz` needed
**nothing**. Ticket 04 already merged all twenty `00002000.img/<action>` rows — every Evan body
animation — and the four `Dragon/*/info/equipTradeBlock` rows. The Evan XML pack at
`porting-resources/evan-xml/` was **not opened**: v84 carries the lot. And the 2003/2004 dragon-equip
tiers the scope doc promises **are not in v84 at all**, so they are a later-version artefact of that
pack.

`Etc.wz` and `UI.wz` join the composition here, both narrowly: 4 and 2 rows. `Etc` is
`MakeCharInfo.img`'s Evan block, which 04 declined and which **the server genuinely reads**
(`MakeCharInfoValidator:17-23`) — creation *data*, creation *flow* is 15. `UI` is exactly §11's
stated `SkillEx`/`SkillMacroEx` exception and nothing else; **59 of 61 UI roots are left**,
including every `RaceSelect/BtEvan` row, which is 15's. It first took the two `Equip/DragonEquip`
rows as well and **gave them back to ticket 14 on review** — criterion 4 is "spawns, follows, and
moves", not "can be equipped", so nothing in 10 earned them, and "no other ticket has claimed it" is
not a reason to widen a scope rule.

Three smaller things worth carrying:

- **`Skill.wz/Dragon` is the first whole-`WzDirectory` row anyone has written out**, and §5.4 says
  those are not content-checked. It closed the gap the other way: `WzMerge hash` on the merged
  directory equals **v84's own digest exactly** (`d27e4899…`) across all ten images and their
  decoded canvas payloads. `WzMerge xml` refuses that row by design — no `.img` segment — so
  `Skill.wz`'s XML run is `added 38, refused 1`, **exit 3**, and an install script must not read it
  as failure. A second XML-only path list was written and then deleted: the tool already draws the
  line, and two lists that must agree is a divergence waiting to happen.
- **A `compose.ps1` defect the fold-in created and then closed.** A backtick used as a quote mark
  inside a double-quoted `$perFile` string is a **newline escape**, so a comment block split in two
  and shipped its own second half as a manifest row. Everything downstream stayed plausible.
  `compose.ps1` now asserts every emitted row starts with `<Name>.wz/`, proven both ways, and the
  re-merge without the stray row is byte-identical — it had cost nothing but an exit code.
- **09's handoff taken.** `scripts/quest/3759.js` (Soaring, which 06's Crimson Sky maps gate on) had
  a dropMessage guard for Evan and a test pinning `2001.img.xml`'s *absence* with the message "…can
  now be replaced by a teachSkill". It appeared; the guard is gone; the assertion is inverted rather
  than deleted, as was 03f's `assertNull(20011025)` negative control in `V84MountNodeTest`.


# WHERE THE PROJECT STANDS

**Evan works in game.** `!job 2001` succeeded, the character exists and renders. Tickets **01–10 and
16 (partial)** are complete; **11 is running**, **12–15 not started.** 01's runtime gate patch
reported `GUARD PASS` → `PATCHED and verified` against the live process. **It patches memory — it
must be re-run after every client launch.**

## ✅ SOLVED 2026-08-16 — the skill-window crash was `UI.wz/Basic.img/Tab8`

**One missing node.** It was sitting unapplied in our own `add-list/UI.txt:7` the entire time.
Merged additively from `porting-resources/wz-data/v84/UI.wz` (1 added, 0 forced, 0 refused,
0 conflicts, `patchVersion` 83 inherited, content digest verified, 0 drifted) and installed —
**the skill window opens, and with `!maxskill` the Evan skills are there.**

This is a **15-year-old unsolved community bug**. Three people reported this exact symptom on
RaGEZONE in 2011 (*"the client crashed when I opened the skill bar. No prompt from the client."*)
and nobody ever fixed it. The only proposed cause was an unconfirmed guess.

**Why every binary-level theory failed:** v83's skill window has 5 tab slots and Evan has 10
advancements. A 5-slot array indexed past its end is an out-of-bounds read — **no exception, no
packet, no `_com_error`** — which is exactly why the `_com_issue_error` trap never fired, why the
server logged nothing, and why Windows recorded no crash event. Both gate patches were necessary
and neither was sufficient.

**The generalisable lesson — this cost most of a day:** *when the client misbehaves around v84
content, check the add-list for an unapplied node before disassembling anything.* Two separate
"impossible" conclusions (this, and Evan character creation — see ticket 15 below) were both just
missing nodes we already knew about. `docs/wz-baseline/add-list/` is the first place to look.

### 🐛 WzMerge DEFECT — a partial array refusal LEAVES A HOLE. Found the hard way, still unfixed.

Merging the UI add-list broke the client **before the login screen**. Cause was the merge tool,
not the data:

`UI.wz/MapLogin.img/back` held exactly `0..47`. The array gate **refused `48..52`** (correctly —
v84 inserted earlier, so those slots are content-duplicates) and then **allowed `53` and `54`**,
producing `{0..47, 53, 54}` — a five-index **hole** in a positional array. `MapLogin.img` is the
login-screen background, so it died exactly where the symptom said.

**The bug:** each index is judged against the **baseline** child count (48), so `53 >= 48` reads as
a clean append. It is only an append if `48..52` land too. The gate evaluates rows **independently
instead of against the running state**, so any *partial* refusal within one array silently creates
a gap — the precise corruption the gate exists to prevent.

**`WzMerge guard` does NOT catch this** (verified: `rc=0` on the broken output). Guard checks
parse/verify, not array continuity.

**Until fixed, the rule is: if the gate SKIPs any index of an array, drop EVERY row of that array
from the merge.** Never accept a partial array result. Fix would be to re-evaluate appends against
post-refusal state, and to teach `guard` to assert array continuity.

Rebuilt excluding all of `MapLogin.img` (cosmetic login background): **46 added, 0 refused,
0 conflicts, 0 drifted** → `Server\wz-merge\11h\UI.wz`.

### Where the two ~~dead~~ gate patches actually stand

Both are still REQUIRED — without them the skills resolve to nothing even once granted. Our two
addresses match the published community frontier exactly
([RZ 1226050](https://forum.ragezone.com/threads/evan-class-for-v83.1226050/) publishes
`PatchNop(0x0075C783,4)` and `PatchNop(0x00761714,21)`). There is no published third patch, and
none is needed.

### Feasibility, settled (ticket 11e research)

- Evan shipped **GMS v84 (2010-03-31)**; v83 shipped 2010-02-22 — the binary predates Evan by 5 weeks.
- **There is no v87+ escape hatch.** No working Evan exists on *any* Cosmic/HeavenMS-class server.
  A client retarget would mean **488 hand-mapped opcodes** (`SendOpcode` 308 + `RecvOpcode` 180)
  plus packet structures, AES keys and NGS/CRC bypasses. Not warranted — the v83 path works.
- Server side was never the blocker: `client/Job.java:59-63` already has `EVAN(2001)`,
  `EVAN1(2200)`…`EVAN10(2218)`.
- **Dual Blade is v88 with zero prior art on v83.** Treat as unexplored, not as a known path.

---

**HISTORICAL — the investigation that got here.** Kept because the dead ends are expensive to
re-derive.

### The gate hypothesis is DEAD — tested and falsified 2026-08-16

Ticket 11 found there are **two** Evan gates, not one, and that patching only the first is *worse
than none* (`GetSkillLevel` reports level 1 for Evan's beginner-common ids while `GetSkill` still
returns NULL → unguarded deref at `0x008F2600`):

| VA | function | patch |
|---|---|---|
| `0x0075C776` | `CSkillInfo::GetSkill` (85 call sites) | surgical: `74 08`→`90 90`, `75 04`→`EB 04` |
| `0x00761714` | `CSkillInfo::GetSkillLevel` | 21× `0x90` |

**Both were patched and verified in a live `MapleStory.exe` (PID 40532, 14:26:41) — and the skill
window still crashed.** Ticket 11 pre-committed to the falsification: *"still crashes ⇒ diagnosis
wrong; do not delete WZ nodes, look for a third gate."* Honoured — **nothing is being stripped
from `Skill.wz`.**

Gate order in `$Gates` is now a **safety property**: `GetSkill` first, because the loop stops at
the first timeout and `GetSkillLevel`-alone is the crash state while `GetSkill`-alone is inert.

**Also ruled out by ticket 11, offline:** all 27 actions Evan's 58 skills reference exist in the
live `Character.wz/00002000.img`; all 48 `skill/` and `level/` child names are already used by v83
images; every skill has `name`+`desc`+`h1..hN`. `SkillEx`/`SkillMacroEx` are dead weight — the
string `SkillEx` appears nowhere in the client image in either encoding.

**Wasted-effort warning for future sessions:** `local.exe` / `localhome.exe` / `*.evan.exe` are
ImpREC memory dumps, **not runnable clients**. They self-relaunch with `÷ GameLaunching` forever,
each process dying in under a second with no window — a ~2/second process storm. Several crash
reports were collected against `localhome.evan.exe` before this was spotted and are **worthless**.
The patcher now watches `MapleStory` **only**. The server logs *nothing* during the crash (opening
the skill window sends no packet), so server logs will never help here.

### Live three-point bisect — the crash is a MISSING EVAN UI RESOURCE, not code and not skill data

Same character, same client, both gates patched; **only the job id changed**:

| Job | Class | v83 ships its UI resources | Skill window |
|---|---|---|---|
| `0` | Explorer beginner | yes | **opens**, skills listed |
| `2000` | Aran beginner (Legend) | **yes** | **opens**, zero skills listed |
| `2001` | Evan beginner (Legend) | **no** | **CRASHES** (5×) |

**Two conclusions, both load-bearing:**

1. **The special/Legend-class path works.** Aran is structurally the same shape as Evan — Legend
   beginner job, own skill root, own special-cased tab resource — and it opens. So "v83 can't
   handle a Legend job" is false. The difference is that v83 *ships Aran's UI resources and not
   Evan's*.
2. **The fault is drawn regardless of skill count.** Job 2000 opened while listing **zero** skills,
   so the per-skill icon draw path never executed — and it was fine. Job 2001 would also list zero
   skills (none learned) and still crashes. Therefore the crash is in **window/tab construction**,
   which runs before and independently of the skill list — **not** in per-skill icon canvases.
   This deprioritises the `0x008AA04D`→`0x008F2600` unguarded deref unless it can be shown to run
   on an empty skill list.

This is the signature of a **missing resource**, which means the fix is most likely **additive** —
adding the Evan tab node — exactly what the owner's rule wants, and better than patching code.

### 11c — THE HOLE IS FOUND. `Skill/Tab` serves 5 tabs; Evan needs 11.

| node | present indices | count |
|---|---|---|
| `UIWindow.img/Skill/Tab/enabled/<n>` | 0,1,2,3,4 | 5 |
| `UIWindow.img/Skill/Tab/disabled/<n>` | 0,1,2,3,4 | 5 |
| `UIWindow.img/Skill/Tab/AranButton/Bt<d>` | **Bt1..Bt4** — no `Bt0`, no `Bt5+` | 4 |
| `UIWindow.img/SkillEx/Tab/enabled\|disabled/<n>` | **0..10** | **11** |
| `UIWindow.img/SkillEx/**/AranButton` | **absent** | 0 |

Both live-working jobs have 5-long chains; Evan's is 11. `Bt%d` (`0x00B3B690`) formatted for an
11-entry chain reaches `Bt5`..`Bt10`, which do not resolve — at **tab construction**, matching
2000-opens-with-zero-skills vs 2001-crashes. v84 shipping a separate `SkillEx/Tab` sized exactly
0..10 is the corroboration: v84 built a wider window for Evan and never widened v83's `Skill/Tab`.

**Not merge damage — computed, not assumed.** 6-level dump with canvases hashed by stored
compressed bytes: LIVE vs v83 backup = **zero** differing lines; LIVE vs stock v84 = **zero**.
Stock v84 also has only `Bt1..Bt4`. `Evan` under `UIWindow.img`: **0 hits** in both.

**`Skill.wz/2001.img` is a CLEAN NEGATIVE — nothing to strip.** 27/27 `icon`, `iconMouseOver`,
`iconDisabled`, all `32x32 f1`, all inflate. `iconMouseOverDisabled` is 0/27 — *and absent from
every v83 image too* (000/1000/2000), so nothing relies on it. Child sets identical to `000.img`;
levels contiguous `1..N`; **0 UOL/_inlink/_outlink** in the Evan images; 2,273 + 254 + 156 canvases
decoded with **0 bad**; every skill id has a `String.wz` name row. Empty level sets exist in the
*working* `000.img` too.

**The fix is ADDITIVE — the owner's preferred shape, and permanent (unlike the per-launch memory
patch):**
- `Skill/Tab/enabled|disabled/5..10` ← `SkillEx/Tab/enabled|disabled/5..10` (real Evan glyphs,
  already present in the live file). `0..4` is a consecutive run, so this is a genuine append.
- `Skill/Tab/AranButton/Bt5..Bt10` ← clone `Bt4` (`normal/0 pressed/0 disabled/0 mouseOver/0`,
  each `34x18 f1` + origin). Tabs 5-10 read "4th job" until real art exists — cosmetic.

### ⛔ 11b OVERTURNS 11c — the tab hypothesis is DEAD. Do not implement the tab fix.

11c's *data* is correct (`Skill/Tab` has 5 slots, `SkillEx/Tab` has 11) but its *inference* was
wrong, and so was the orchestrator's "Aran is the control" reasoning. Disassembly, not assertion:

- **`CUISkill::CreateTabs` @`0x008AD2D1`** (from `UpdateSkills` @`0x008ACEB7`) loops on the tab
  **INDEX**, not the root value: `cmp edi,[eax-4]` uses `roots[]` only for its **count**, then
  `push edi` / `push "%d"` / `sprintf`. **`roots[edi]` is never read.**
- **A job-2001 character is a BEGINNER — its root list has ONE element.** The 11-job chain belongs
  to a job-2218 character. Jobs 0, 2000 and 2001 all build exactly one tab, named `"0"`.
  Job 0 works ⇒ the tab strip works. `Bt5..Bt10` would add nodes **nothing ever asks for.**
- **The `AranButton` block is gated on jobs 2100–2199 only** (`job/1000==2 && (job%1000)/100==1`).
  `2000 % 1000 / 100 = 0`, so **`!job 2000` never exercised the Aran path** — it was not the
  control the orchestrator claimed. No `"EvanButton"` string exists in the image in any encoding.
- **No third gate.** `CSkillInfo::LOAD` @`0x0075C060` enumerates every `Skill.wz` child; the only
  skips are `"MC"`/`"BF"`/`"Ite"` prefixes and one res-string. `LoadSkillRoot` @`0x0075C858`
  registers unconditionally, on the startup path, not lazily.
- **Icon/row path ruled out by dump:** `2000.img` and `2001.img` are structural twins — 28 vs 27
  skills, same shapes, **byte-identical icon canvases**, no `masterLevel`/`req`, zero UOLs.
  Job 2000 renders zero rows, therefore so does 2001.

**After both patched gates, nothing left in the skill path treats 2001 differently from 2000.**
A full-image scan for `0x000007D1` found 60 sites; every one reachable from the skill path behaves
identically for both jobs.

**One real defect found, not proven to fire:** `CSkillInfo::GetSkillsByRoot` @`0x0075C7C5` runs
*first* on window open, and at `0x0075C7E2` dereferences the result of `0x0075C70A` unguarded.
`0x0075C70A` null-checks its own result; its only caller does not. Fires iff the root has no
`<root>.img` registered — and `2001.img` **is** installed, so this is a real bug but probably not
this crash.

### ▶ ACTIVE: the `_com_error` tracer — get the address, stop guessing

The dialog is a **thrown** `_com_error` (`"Unknown error 0x%0lX"` @`0x00B3D8E8`), which is why
Windows logs no crash event. All 22,687 `FAILED(hr)` sites funnel through `0x00A5FDE4`.
`patch-evan-gate.ps1 -Trace` installs a 14-byte trap there:
`mov [0x00A5FDEE],esp` + `jmp $` — the client **freezes at the throw** instead of dying.
Then `-ReadTrace` reads it back: `[esp]` = **the exact VA of the failing check**, `[esp+4]` = the
HRESULT, code-range words = a poor-man's backtrace. `0x00A5FDE4 + 14 = 0x00A5FDF2` = the next
function's start, so nothing outside is touched. Self-test verifies the pattern is unique.
**If it does NOT freeze**, the throw came direct from `0x00A60BB7`, whose only two sites are
`0x0075C61E` / `0x0075CC89` — both `E_FAIL` out of the `Skill.wz` loader. Either way it is decisive.

Hint for whoever reads the trace: **`2001 % 10 == 1`** while every other beginner job ends in `0`.
Any v83 routine doing `job % 10` advancement arithmetic reads 2001 as "1st advancement of class
200". If the fault lands outside `CUISkill`, that is the shape to look for.

### `docs/wz-baseline/tool-uicopy/` — built, UNUSED, UNVALIDATED. Do not trust it as-is.

Built for the dead tab fix, cancelled mid-flight. Never run against anything that shipped. Its
additive-only and zero-removal machinery is sound, but review found:
- The canvas **dimension check is tautological** — it compares source metadata to a verbatim copy
  of itself. Only the `bmp == null` clause has teeth (that one does exercise zlib inflate).
- **No content digest anywhere.** Marking `Changed` re-serializes the *entire* `.img`, and nothing
  checks the re-encoded siblings. It proves zero *removals*, never zero *modifications* — the exact
  gap `tool-merge` closed with `Canon`/`Digest`, which was not carried over.
- **patchVersion 83 is never asserted**, only compared against a co-detected value — and the two
  detections are different searches (input finds `MapleStory.exe` and seeds at 83; staged output
  brute-forces from 0).
- An exception **during verification exits 1, not 4**, leaving the `.partial` behind.
- **Relative UOL/`_inlink` targets break on rename** — resolution is relative to the node's own
  position, and landing a subtree at a new name/depth silently repoints them. That is the failure
  mode a rename tool actually has, and verification skips UOLs by construction.

## Installed on the client right now — `evan-min`, not the composed set

`Server\wz-merge\evan-min\` — **5 files, 109 rows**, purely additive, zero forces, zero removals.
Orchestrator-verified by foreground SHA-256 on 2026-08-16: exactly `Character Etc Skill String UI`
differ from the backup; the other **13 `.wz` are byte-identical to it**. (Hash in the foreground —
a backgrounded `Get-FileHash` in this harness once produced a false 13-file DIFF that did not
reproduce.)

### The composed set crashes the client — bisect in progress

`Server\wz-merge\10c\` (**1,750 rows / 13 files**) was installed twice and **crashed the client both
times** — once with the `String.wz` deletion defect present, once after it was fixed and the set was
provably additive. So *removal was not the cause*. The dialog is `_com_error`'s
`Unknown error 0x%0lX` — an **HRESULT, not a WZ parser message** — and the owner has no code.
Stock v84 WZ was also installed once as a diagnostic but is **confounded** (`patchVersion=84`
against a v83 binary); every composed output is `83`, so headers were not the cause.

**Correction — `evan-min` did NOT exonerate its five files.** It is a strict subset of the composed
set (one `UI` row aside), so **771 composed rows remain untested in files that currently look
"proven"**: `String` 510, `Character` 234, `Skill` 27. The live crash space is therefore **1,642 rows
across 12 files**, not 871 across 8. An earlier note of mine claiming otherwise was wrong.

| Half | Install | Rows under test |
|---|---|---|
| **B** | current `evan-min` 5 + `10c`'s `Item Quest Map Mob Morph Npc Sound Reactor` | **871** |
| **A** | `evan-min` 5 upgraded to `10c`'s `Skill String Character UI Etc` | **771** |

Both halves are **already built** in `10c\` — each bisect step is a file copy, not a merge run.
Step B only touches files that currently match the backup, so the split is clean. One launch
per step; ~4 launches to isolate.

Server XML and SQL are applied to the repo and green at **2,008** tests, and the server runs from
**the worktree** — the owner's normal `launch.bat` runs the **main checkout**, which has none of
this work. **The branch must be merged for his own launcher to pick it up**, including the
`config.yaml` IP fix (a pre-existing bug that breaks his normal server too).

## What needs the owner — in priority order

1. ~~**Ticket 01's go/no-go.**~~ **DONE.** The runtime patcher ran against the live
   `MapleStory.exe` and logged `GUARD PASS` → `PATCHED and verified`. Themida did not re-encrypt
   the region and the client kept running. **It patches memory, so `tools\patch-evan-gate.ps1`
   must be re-run after every launch** — without it no Evan skill resolves, and the symptom looks
   exactly like bad WZ data.
2. ~~**Install the composed merge `10c\` (13 files).**~~ **Done twice — crashes the client both
   times.** Superseded by the bisect above. The orchestrator installs each half; the owner only
   launches. **Do not install `wz-merge\10\`** (four files, merged from pristine v83 to prove
   ticket 10's path lists); it does not compose with 03i's output and `10\String.wz` alone would
   drop tickets 04–08.
3. **Crimson Sky's travel route.** The area is complete and unreachable; neither v83 nor v84 ships
   the return node, so this is a design decision, not a defect. Three options in ticket 06. Related:
   quest **`3759` grants Soaring**, which those maps gate on, and it sits behind one expired date node.
4. **Four `Character.wz/Dragon/*/info/level` rows** — adopting v84 switches on a dormant
   equip-levelling path for four items. Kept local pending your call.
5. **Four customized weapons** (`01382058`, `01452058`, `01472069`, `01492024`) gained v84 combat
   stats into previously-cosmetic nodes. Low practical risk; never explicitly decided.
6. **Six forced `String.wz` names take untranslated Korean over `MISSING NAME`**, and v84's own
   Christmas NPC names are Korean too. Reversible in one place; listed in the composed README.

## Human-required queue

Batched; nothing here blocks other tickets.

| From | Step | Staged? |
|---|---|---|
| ~~01~~ | ~~Double-click `local.evan.exe`~~ — **superseded and done.** `local.exe`/`localhome.exe` turned out to be memory dumps, not clients; the client is `MapleStory.exe`, Themida-compressed, unpatchable on disk. `tools\patch-evan-gate.ps1` patches the live process and has run successfully. **Re-run it after every launch.** | **done** |
| ~~10~~ | ~~Install `Server\wz-merge\10c\` — 13 files.~~ **Done — crashes the client. Superseded by the bisect.** | done, failed |
| **11** | **Launch 1 — the gate test.** Launch `MapleStory.exe` and say so; orchestrator patches the live PID; log in as the Evan and open the skill window. Survives → the crash was the missing per-launch patch. Crashes → it is data-level and 11 has its reproduction. Nothing is reinstalled for this; current `evan-min` stands. | **ready** |
| **bisect** | **Launch 2 — same sitting.** Close the client; orchestrator copies half **B** in; relaunch. Crash localises to 871 rows, clean load localises to the other 771. Repeat ~4×, halving each time. Each pass also *ships content*, so a green step is never wasted. | **ready** |
| **merge** | **Merge the branch to `master`** so the owner's normal `launch.bat` (main checkout) picks up the server work and the `config.yaml` Hamachi-IP fix. | pending owner |
| ~~03~~ | ~~Copy `Server\wz-merge\post\{Item,String}.wz`~~ — **folded into 10's install.** Those two files predate the deny-list and the positional-array gate. The tracer item `2001500` is in the composed `Item.wz`, so `!item 2001500` remains the right smoke test — just run it after 10's install, not after a separate copy. | superseded |

**Orchestrator verification of 01** (independent): pattern occurs exactly once per binary at
`0x361714`; each patched copy differs from its original in exactly 21 contiguous bytes
`0x361714`–`0x361728`, all `0x90`, size unchanged; live binaries still hash-match the backups.
