# Ticket 16 — regression pass over everything 04–09 changed

Run 2026-08-16 against composed install **`Server\wz-merge\03h\`** (ticket 03h, commit `f998d58f0`)
and server tree at `f998d58f0`. Evan (tickets 10–15) is **out of scope** — see §9.

**Client exposure: none.** All 18 live `.wz` SHA-256-match
`Server\_backup\client-v83-EzorsiaV2-2026-08-15\` **before and after** this run, and no
`.partial` / `.merged` / `.TEMP` exists beside them. Nothing was installed; nothing was launched.

Suite: **1,994 green** (1,991 at the start of the pass, plus this ticket's 3).

---

## 1. Method — why this is a digest pass, not a presence check

`protect-list/Character.txt` is 2,987 copy roots but `modified-list/Character.live.txt` is **5,114**,
and the latter is where Ezorsia's ~18.6 MB of HD art lives, under stock node paths. Presence
diffing cannot see it. So every claim below is one of:

- **`WzMerge hash`** — `Canon()` digests decoded leaf values recursively (canvases by their
  compressed pixel bytes), so a changed grandchild moves the ancestor digest. Run on the `pre\`
  snapshot and on the merged output, one line per child, diffed.
- **`WzMerge dump <img> 40`** — full recursive listing, pre vs post, parsed into `path -> value`
  and classified `added` / `removed` / `changed`. Used on every image whose digest moved.
- **`xml.etree` node-set diff** of the server `wz/*.img.xml` at `94e66d80c` (the last pre-v84 commit)
  vs `HEAD`, keyed by path. Serializer reformatting is invisible to it.

**Stated limits, not hidden:**

- `Canon()` normalises **sibling order** away, so a pure same-name reorder is invisible. Nothing
  here proves order was preserved. (A reorder is in fact visible in the server XML — see §2.)
- The `pre\` snapshots were hash-verified against the backup by this pass, so "unchanged" means
  unchanged relative to the real live client, not to a staging artefact.
- `WzMerge hash` **crashes** on some input — see §8. Six Reactor images are covered by a
  depth-12 dump instead of a full digest.

---

## 2. Protect list and `modified-list/*.live.txt` — met

Whole-file digest sweep, `pre\` vs `03h\`, all eleven merged files:

The sweep digests each file's root children, then drills into every child whose digest moved, so a
directory that does not move is proved identical to its leaves in one line. Counts are images:

| wz | images added | **images removed** | images digest-changed |
|---|---:|---:|---:|
| Character | 154 | **0** | **12** |
| Item | 2 | **0** | 26 |
| String | 0 | **0** | 10 |
| Map | 48 (47 maps + `Tile/blackTileFly.img`) | **0** | 19 |
| Mob | 28 | **0** | **0** |
| Npc | 15 | **0** | **0** |
| Morph | 4 | **0** | 7 |
| Skill | 0 | **0** | 3 |
| Sound | 0 | **0** | 2 |
| Reactor | 3 | **0** | **0** |
| Quest | 0 | **0** | 4 |

**Zero nodes removed anywhere except 18, and all 18 are placeholders.** All 80 changed
images were then dumped recursively. Result: `removed=0, changed=0` on **every** image outside
`String.wz`. The complete set of non-additive effects in the whole install is:

- **80 changed leaf values, all in `String.wz`**, spanning exactly **52 ids** — which is exactly what
  `composed/FORCE.txt`'s 41 roots reach (`Eqp/Dragon` is a container root covering 12 ids).
  Verified id by id.
- **18 removed nodes, all `String.wz/Eqp.img/**/desc`**, each dumped from the `pre\` snapshot before
  and after: **every one held the literal `MISSING INFO`** on an item whose `name` was
  `MISSING NAME`. The force path removes and re-adds the container; v84's node has no `desc`, so the
  placeholder goes with it. Zero real content lost. (This reproduces R4's "18 removed, every one a
  placeholder" by an independent route.)

**None of the 98 affected paths is on `protect-list/String.txt`** (7,604 roots) — checked
mechanically. Every one is a stock id present in v83-stock. No live-only custom content was
touched.

Two caveats worth stating rather than burying:

1. `modified-list/*.live.txt` is **image-granular** (BlockSize-based). At that granularity every
   `String.wz` image is "protected", so an ancestor test against it is trivially true and proves
   nothing. The leaf-level result above is what carries the weight.
2. Six of the 41 force roots take an **untranslated Korean** name over `MISSING NAME`
   (`Eqp/Accessory/1142143,45,49,50,51`, `Eqp/Longcoat/1051176`). `COLLISION-FORCE.txt` says so
   in its own comments, so this is disclosed, not a surprise — but 08's Korean NPC names were
   flagged to the owner as reversible and these were not. Same class; same choice available.

**Ezorsia's hair and face names specifically:** `String.wz/Eqp.img/Eqp/Hair` — **1,518 names pre,
1,558 post, 40 added, 0 changed, 0 removed.** The 9 refusals (`31660`–`31667`, `33101`) held. All
507 Ezorsia-renamed faces untouched.

---

## 3. Existing quests — met, and cross-checked two ways

Server XML, `94e66d80c` → `HEAD`, node-set diff:

| image | old paths | new | added | **removed** | **changed** |
|---|---:|---:|---:|---:|---:|
| `Act.img` | 38,432 | 38,970 | 538 | **0** | **0** |
| `Check.img` | 55,624 | 57,061 | 1,437 | **0** | **0** |
| `QuestInfo.img` | 20,023 | 20,512 | 489 | **0** | **0** |
| `Say.img` | 42,361 | 43,094 | 733 | **0** | **0** |

Additions land under **exactly 63 quest ids**, the same 63 in all four images. Top-level quest-id
counts move `2,818 → 2,881` (`QuestInfo`), `2,824 → 2,887` (`Act`), `2,807 → 2,870` (`Check`),
`2,801 → 2,864` (`Say`): **+63 and nothing else. The 2,818 existing quests are intact.**

The **binary** `Quest.wz` merge reproduces those four numbers exactly (`+538 / +1,437 / +489 / +733`,
`removed=0 changed=0`), so client and server agree about the same 63 quests — a cross-check nothing
had run before. `Exclusive.img` and `PQuest.img` are digest-identical, so 09's `Exclusive` refusal
held on the client side too.

**The 108 `lvmax` rows did not land**, and neither did the 15 date rows:

- `Check.img.xml` `lvmax` occurrences `325 → 326`; the one addition is on a *new* quest, and
  `changed=0 / removed=0` proves no live quest gained or lost one.
- `200801010000` / `200801020000` occur **zero** times in `Check.img.xml`.
- **All 108 `lvmax` rows and all 15 date rows are on `COLLISION-DENY.txt`** — so they are refused
  structurally, not merely omitted from a list. Pinned by
  `V84RegressionTest.everyHarmfulQuestRowIsOnTheDenyListNotMerelyOffTheMergeList`, with the 252
  merged rows as a negative control (a row that is both merged and denied fails).

**One observation, not a defect.** Quest `28326` moves position inside `Check.img.xml` between the
two versions — the sorted-insert relocates it. A duplicate-sibling scan of all four images returns
**0**, and the server reads these as a keyed map, so this is cosmetic. It is recorded because it is
exactly the class the digest tool is blind to.

---

## 4. Drops, shops and spawn rates — met

- **`152-drop-data.sql` is byte-untouched.** `git diff 94e66d80c HEAD -- src/main/resources/db/`
  is three files: `changelog-data.xml` (+6 lines, two `<changeSet>` blocks), and the two new SQL
  files. `151-global-drop-data.sql` and `009-drop.sql` likewise untouched.
- **153 and 154 are additive only** — zero `UPDATE` / `DELETE` / `REPLACE` / `ALTER` / `TRUNCATE`,
  one `INSERT` each.
- **No existing dropper gained rows.** 152 covers 1,004 dropperids; 153 adds 16 and 154 adds 4, and
  the intersection with 152 is **empty**, as is 153 ∩ 154. Pinned by
  `V84RegressionTest.theNewDropChangeSetsTouchNoDropperThatAlreadyHadRows`.
- **Shops:** no shop SQL changed. `Etc.wz` was declined wholesale by 04 and is still unmerged
  (10,634 add-list rows, 0 claimed), so `Commodity.img` — the cash-shop table — is untouched.
- **Spawn rates:** across the whole of `Map.wz`, exactly **three** pre-existing map images changed
  (`106010102`, `200080600`, `251010403`), and their additions are **only** `portal` and layer-`obj`
  entries — **zero `life` nodes were added to any map the live client already had**, so no existing
  spawn table moved. All other map changes are 47 whole new map images.

---

## 5. Hairstyles and equips — met as far as data can show

- **No pre-existing `Character.wz/Hair` image was touched at all.** Every hair addition is a whole
  new image. Exactly 12 `Character.wz` images changed and all 12 are additive
  (`removed=0, changed=0`): 4 `Weapon` (04's F1 stat rows), 4 `Dragon`, `00002000.img`,
  `Afterimage/mace.img`, `Cap/01002728.img/info`, `Glove/01082262.img`.
- The 2 `Accessory/0114215{3,4}` level-up-medal refusals and the 4 `Dragon/*/info/level` refusals
  held (`Character.conflicts.txt`, 12 refused).
- 04's hair-NPC edit (`scripts/npc/1012103.js`) **appends** to `mhair_v` / `fhair_v` and preserves
  every existing entry in order. `00034040`–`00034047` are correctly absent — they have no name in
  either tree, which is the honest gap 04 reported rather than inventing one.

### 5.1 — REGRESSION FOUND: two glove rows got past the positional-array gate

`Character.wz/Glove/01082262.img` ("Dragon Master's Proof"). 03g refused **6** rows here on the
stated rule that the live client ships this glove with different art and splicing one v84 layer into
an Ezorsia frame is the same hazard as 08's portal. **The composed list carries 11 rows on this
image. Two of the remaining five are the same shape and they landed.**

The gate fires only when a container's children are **exactly** `0..c-1`. These two arrays start at 1:

```
swingT2  live {1: rGlove 5x6, 2: lGlove 12x8}      v84 {1: rGlove 7x5, 2: rGlove 12x9}
         merged -> slot 2 now holds Ezorsia's lGlove 12x8 AND v84's rGlove 12x9
swingO3  live {1: rGlove 6x5}                      v84 {0: lGlove 7x5 + rGlove 47x9, 1: rGlove 7x5}
         merged -> {0: v84's pair, 1: Ezorsia's 6x5} — two frames from two art sets, and
                   v84's slot-0 rGlove is 47x9 against the live 6x5, so the arrays are very
                   unlikely to be the same animation at all
```

`swingT2/2/rGlove` is *literally* the case the six refusals describe, unrefused. Its sibling
`swingP2/2/rGlove` **was** refused, and the only difference between them is that `swingP2`'s live
children are `0..2` and `swingT2`'s are `{1,2}`.

**Scope, measured not guessed.** Every composed row that writes under a small-integer container was
enumerated (34 distinct parents) and each parent's children classified against the rule in the
`pre\` snapshot. **Exactly two parents are `GATE-MISSES`, both on this glove.** Everything else is
either covered by the rule (and was refused where it mattered — the two `Item.wz` `reward/43` rows,
the `MonsterBook` roots) or is not an array (`NOT-ALL-INT`).

**Impact:** cosmetic, one equip, four animation frames. **Not a blocker.** But the rule as written
in `WZ-MERGE-PROCEDURE.md` §4.4 does not do what §4.4 claims, and the fix is one clause — an array
whose children are *all* integers and whose maximum is within the child count is an array whether or
not it starts at 0. Owner-visible options: extend the rule and re-run, or force-list the two rows
deliberately. Recorded rather than fixed here: `tool-merge/Program.cs` and
`WZ-MERGE-PROCEDURE.md` belong to another agent in flight.

---

## 6. The owner's own changes — met

`git diff upstream/master 94e66d80c -- wz/` is **exactly 3 files**, as 02e recorded:
`Quest.wz/Act.img.xml`, `Quest.wz/Check.img.xml`, `String.wz/Cash.img.xml`.

- **The quest item-count rebalance survived.** `Act.img.xml` and `Check.img.xml` show
  `removed=0, changed=0` from `94e66d80c` to `HEAD` (§3), so no value the owner set was altered.
  The rebalance is still the only divergence from upstream in those files.
- **The three retimed cash coupons survived.** `5211048` "40 minutes 2 x EXP", `5360042`
  "40 minutes 2 x Drop", `5211060` "20 min 3 x EXP" are all present at `HEAD`; the only
  `Cash.img.xml` changes since the baseline are additions.
- **`config.yaml` is untouched** — zero diff since `94e66d80c`, so `exp_rate 1`, `meso_rate 5`,
  `drop_rate 1`, `boss_drop_rate 10` stand.
- **The boss-spawn work is untouched** — zero files matching `boss`/`spawn` differ from the merge
  commit `94e66d80c`.
- **`GameConstants.java` is untouched**, so commit `078b600db` ("Lower exp rates to 0.3x multiplier;
  cap drop rate scaling at 4x") is still in effect.

The only pre-existing files any ticket modified are `StatEffect.java` (R4-reviewed) and two NPC
scripts (`1012103.js`, `2083006.js`), both of which append to their arrays without disturbing an
existing index.

---

## 7. Content reconciliation — every row accounted for

**The composed install:** 1,662 rows offered → **1,639 merged + 23 refused**. Exact.

| wz | list rows | merged | refused |
|---|---:|---:|---:|
| Character | 254 | 242 | 12 |
| Item | 391 | 389 | 2 |
| String | 510 | 501 (41 forced) | 9 |
| Quest | 252 | 252 | 0 |
| Map | 133 | 133 | 0 |
| Mob | 28 | 28 | 0 |
| Skill | 27 | 27 | 0 |
| Morph | 25 | 25 | 0 |
| Sound | 24 | 24 | 0 |
| Npc | 15 | 15 | 0 |
| Reactor | 3 | 3 | 0 |
| **total** | **1,662** | **1,639** | **23** |

The 23 refusals are **15 `already exists in target` + 8 `POSITIONAL ARRAY`** — matching the
composed README exactly. **Zero `DENIED` refusals fired**, because the deny-list's 156 roots are not
on any composed list; the deny-list is the net under the lists, not part of this run's arithmetic.

**What v84 offered, and where the rest of it went:**

| wz | offered (add-list) | claimed | unclaimed | why unclaimed |
|---|---:|---:|---:|---|
| Etc | 10,634 | 0 | 10,634 | declined wholesale by 04 — bulk `Commodity/Bonus` rows plus 1,518 SNs pointing at out-of-scope items |
| String | 1,579 | 510 | 1,069 | names for content no ticket merged (Evan, unmerged mobs/maps) |
| Mob | 1,216 | 28 | 1,188 | mobs in areas no ticket claimed |
| Quest | 924 | 252 | 672 | **540 Evan (ticket 13) + 132 refused by design** (`09/DEEP-ROWS.md`) |
| Map | 601 | 133 | 468 | 29 whole map images (19 Evan `900xxxxxx`, 10 other) + **407 rows writing into maps the live client already has** + 32 assets |
| Character | 438 | 254 | 184 | **exactly** the 184 Mir animation rows 05 dropped as half-v83/half-v84 |
| Npc | 98 | 15 | 83 | includes the 10 `99019xx` dropped by decision and the 24 UOL-blocked `9000021` rows |
| Sound | 62 | 24 | 38 | SFX for unmerged mobs |
| UI | 61 | 0 | 61 | no ticket owns `UI.wz` |
| Skill | 55 | 27 | 28 | Evan's skills, tickets 12/13 |
| Effect | 23 | 0 | 23 | no ticket owns `Effect.wz` |
| Reactor | 6 | 3 | 3 | `1002008`, `2302006`, `2409000` — reactors for areas no ticket merged |
| Item | 391 | **391** | 0 | fully claimed |
| Morph | 25 | **25** | 0 | fully claimed |
| Base / TamingMob | 0 | 0 | 0 | genuinely empty |
| **total** | **16,113** | **1,662** | **14,451** | |

**The one thing this table exposes that no ticket stated:** of the 468 unclaimed `Map.wz` rows,
**407 write into maps the live client already has** — the exact hazard class 08 measured. 08
triaged **18** of them (the ones inside its own areas) and split them 6 merge / 12 deny. **The other
389 were never examined by anyone.** They are harmless today precisely because they are on no list,
but "unclaimed" and "refused" are not the same thing and this row of the table is the former.

---

## 8. `WzMerge hash` crashes on `Reactor.wz` — tool defect found by this pass

`WzMerge hash <Reactor.wz> /` exits **`0xC00000FD` (stack overflow)** after emitting its child
lines, on both the pre and post files. Per-image hashing isolates it to **6 images**:
`1050000`, `1102000`, `1102002`, `9201001`, `9202002`, `9708000`. `Sound.wz` at root fails the same
way; per-image it is clean apart from `BgmGL.img`, which is the known unparseable image (exit 1,
symmetric in all three trees).

`Canon()` recurses unbounded and these images evidently contain a cycle or a very deep chain. This
matters beyond convenience: **`hash` is the project's protect-verification instrument, and pointed
at a whole `Reactor.wz` it fails in a way an operator could read as a merge fault.** It should be
depth-bounded or cycle-guarded, and it should exit non-zero *before* printing partial results.

Coverage was completed by fallback: all six are **identical pre vs post at dump depth 12**
(structure, leaf values, canvas dimensions and compressed byte counts). Combined with the 415
digest-identical images, `Reactor.wz` is fully accounted for: **421 pre, 3 added, 0 removed, 0
changed.**

---

## 9. What this pass does NOT cover

- **Evan.** Tickets 10–15 are hard-blocked on ticket 01's human client-launch test. There is no Evan
  content in the composed install, so acceptance criterion 6 ("all four classes plus Evan playable
  from level 1") **cannot be met by this pass** and is not claimed. The four existing classes are
  covered only as far as data allows — nothing here demonstrates a character logging in.
- **Anything requiring the client to run.** Nothing was installed. Every rendering, spawn, quest-
  acceptance and playability claim in this document is a claim about *data*, not about the game.
- **Sibling order** inside a merged container — see §1.
- **The 389 unexamined `Map.wz` deep rows** — see §7. Not merged, so not a regression; not triaged
  either.

## 10. Human-verified steps, staged

Install `Server\wz-merge\03h\{Character,Item,Map,Mob,Morph,Npc,Quest,Reactor,Skill,Sound,String}.wz`
over the live client per `WZ-MERGE-PROCEDURE.md` §5 (client closed, backup verified first), then:

| # | check | pass signature | fail signature |
|---|---|---|---|
| 1 | Log in an existing character of each of the four classes | character loads, equips render | missing sprite / crash on load |
| 2 | Open the hair NPC (`1012103`, Henesys) on a male and a female character | the 5 new families appear and preview correctly; every pre-existing style still listed | a style vanished, or a preview renders as a blank |
| 3 | Equip "Dragon Master's Proof" (`01082262`) and swing a two-handed weapon | glove renders normally | left/right glove mismatch or a stray 47-px smear on one frame — **that is §5.1** |
| 4 | Accept and complete any quest in `28162`–`28325` on a character **above Lv.40** | quest is offered and completes | quest not offered → the `lvmax` refusal did not hold |
| 5 | Accept quests `2208`–`2211` (NPC `1092011`) and `3845` (NPC `2092001`) | offered | not offered → a date gate landed |
| 6 | Kill any Leafre mob and confirm drops look normal | unchanged drop rates | changed → 153/154 hit an existing dropper |

Rollback for all six: restore the 18 `.wz` from
`Server\_backup\client-v83-EzorsiaV2-2026-08-15\`.

## 11. Test added

`src/test/java/server/V84RegressionTest.java` — three checks, each negative-controlled:

| test | negative control run |
|---|---|
| every harmful quest row is on the deny-list, and no merged row is | deny list with `Check.img/28162` removed → **fails** with that row named |
| the new drop changeSets touch no dropper 152 already covers | `153` swapped for `152` → **fails** with 1,004 overlapping ids |
| the 2,818 pre-merge quests are all still present | an over-broad first draft asserting every `QuestInfo` id also has a `Check` row → **failed** on `9800`, which legitimately has no `Check` row in either version; corrected to per-image counts |
