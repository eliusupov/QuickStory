# 33 — v84 quest data in the server's own WZ, additively

**What to build:** the server knows about every quest v84 shipped. Today it knows 2,881 and
**none of them is an Evan quest**, so all 49 Evan quest scripts in this tree are dead files —
`Quest.hasScriptRequirement` reads `Check.img`, finds no node, and `QuestScriptManager` disposes
without ever loading the script. This ticket is what makes quests exist at all.

**Blocked by:** None — can start immediately.

**Status:** delivered — see [Delivered](#delivered). 135 Evan ids x 3 images = 405 nodes added,
zero pre-existing ids changed (proven twice, with self-checks), suite 2092/0.

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

---

# Delivered

**Status: delivered.** 135 quest ids added to each of `QuestInfo.img.xml`, `Check.img.xml` and
`Act.img.xml` — 405 nodes, `added 405, refused 0, denied 0, forced 0`. Every pre-existing quest id
in all three files is proven unchanged, twice, by two independent methods, each with a self-check
that demonstrates it can fail. Full suite **2092 passed, 0 failed**.

**The headline is not the count, it is what the 135 turned out to be gated on.** Ticket 09's 63
quests were 48/63 dead on arrival behind an `end` date that had already expired when v84 shipped.
**Zero of these 135 carry an `end` date at all** (measured on the start block of all 135, not
sampled). The entire Evan chain has exactly one unmet start gate: the job requirement. So this
ticket does not hand the next one a data problem — it hands it ticket 15.

## What was built, and the tooling decision

**No new merge engine.** `docs/wz-baseline/tool-merge/` (`WzMerge`, C#/MapleLib) already has an
`xml` subcommand that does precisely this job — additive-gated text splice into the server's XML,
through a `FragmentSerializer : WzClassicXmlSerializer` already calibrated to this tree's format
(2-space indent, CRLF, no BOM), refusing to touch a file that is not CRLF-throughout and
verifying every file reads back as written. It is what ticket 09 used for the same three files.
Writing a second tool would have been writing a second thing to be wrong.

What was written is only what did not exist: **the id arithmetic and the proof.** Three small
PowerShell scripts in `docs/wz-baseline/merge-lists/33/`, each with its output committed beside it:

| file | what it is |
|---|---|
| `ids.ps1` | computes `v84_ids - ours` per image and emits the path list |
| `verify.ps1` | the two proofs below, plus two self-checks |
| `v84-crossimage.ps1` | the "present in one image but not the others" report |
| `Quest.paths.txt` | **405 rows** — the authoritative deliverable, 135 ids x 3 images |
| `{QuestInfo,Check,Act}.new-ids.txt` | the 135, per image |
| `{QuestInfo,Check,Act}.ours-not-in-v84.txt` | `7778` — this tree's own quest, in each |
| `Quest.postmerge-recheck.conflicts.txt` | 405 rows, the idempotence re-check |
| `CROSS-IMAGE.txt`, `SUMMARY.txt` | measured output |

Step 1 was extraction, as the ticket said. `WzMerge dump <wz> <Img>.img 1` over
`porting-resources/wz-data/v84/Quest.wz` — opened `iv=GMS, patchVersion=84`, confirming the archive
is the genuine v84 one ticket 20 hash-verified.

## The numbers

| | QuestInfo | Check | Act | (Say) |
|---|---:|---:|---:|---:|
| v84 source | 3015 | 3004 | 3021 | 2998 |
| this tree, before | 2881 | 2870 | 2887 | 2864 |
| **added** | **135** | **135** | **135** | **0, deliberately** |
| this tree, after | 3016 | 3005 | 3022 | 2864 |

- **All 135 are `22xxx`.** Distribution: `220xx` 11, `221xx` 10, `223xx` 1, `224xx` 14,
  `225xx` 97, `226xx` 2. Not one non-Evan id was left behind — ticket 09 took the other 63 of
  v84's 198, and 135 + 63 = 198 closes exactly.
- **Containment is exact.** v84 ids missing from this tree after the merge: **0, 0, 0**. Ids in
  this tree that v84 does not have: **`7778` only**, in all three — this tree's own custom quest,
  still present, asserted by a test rather than left to trust.
- **Merge run:** `added 405 (forced 0), refused 0`, exit 0. Deny-list loaded with 188 roots.
- **Idempotence re-check:** re-running the same path list post-merge gives
  `added 0 (forced 0), refused 405`, exit 5 — every row now resolves as already present.

## Proof that zero pre-existing quest ids changed

Run: `docs\wz-baseline\merge-lists\33\verify.ps1`. Verbatim output:

```
A  QuestInfo : stripped 135/135 added top-level blocks; remainder identical to HEAD = True  (chars after strip=2168433, HEAD=2168433)
B  QuestInfo : pre-existing ids 2881 -> changed 0, missing 0; new ids 135
A  Check : stripped 135/135 added top-level blocks; remainder identical to HEAD = True  (chars after strip=2371828, HEAD=2371828)
B  Check : pre-existing ids 2870 -> changed 0, missing 0; new ids 135
A  Act : stripped 135/135 added top-level blocks; remainder identical to HEAD = True  (chars after strip=1688932, HEAD=1688932)
B  Act : pre-existing ids 2887 -> changed 0, missing 0; new ids 135
SELF-CHECK 1: proof A rejects a one-character value edit to a pre-existing quest = True
SELF-CHECK 2: proof A rejects a 22xxx block nested inside a pre-existing quest = True
RESULT: PASS
```

**Proof A (bytes).** Strip exactly the top-level `<imgdir>` blocks whose name is in the added-id
list; the remainder must be byte-identical to `HEAD`'s blob. This is stronger than a presence diff
because it fails on any edit anywhere, including an added block nested inside an existing quest.

**Proof B (parse).** Load `HEAD` and the new file with an XML parser and compare a canonical,
whitespace-independent digest of every pre-existing id's entire subtree. Also proves the files
still parse.

**The self-checks exist because the project's rule is that a check which can only print PASS is not
a check.** Both mutate the real merged file and both must come out "different from HEAD":
one changes a single character of an `lvmin` inside a pre-existing quest; the other re-inserts the
whole `22100` block *nested inside* a pre-existing quest instead of at top level — the exact
failure a presence-based diff would not see.

**Third, independent confirmation — and a trap worth recording.** `git diff --stat` reports
**190 deletions** in `Check.img.xml`, which flatly contradicts the above. It is a Myers artefact,
not a data fact:

```
myers     : 1807 0 Act | 4493 190 Check | 1049 0 QuestInfo
minimal   : 1807 0 Act | 4303   0 Check | 1049 0 QuestInfo
patience  : 1807 0 Act | 4303   0 Check | 1049 0 QuestInfo
histogram : 1807 0 Act | 4303   0 Check | 1049 0 QuestInfo
deleted lines with no identical inserted counterpart: 0
```

Three other diff algorithms all report a **pure insertion, zero deletions**, and every line Myers
calls deleted has an identical inserted counterpart. The cause is that `22100`–`226xx` sort
lexicographically right next to the pre-existing `2211`/`2212`, so Myers realigns across the
insertion boundary. **Do not read `--stat` deletions on these files as evidence of a destructive
merge; ask `--diff-algorithm=minimal`.**

SHA-256, before → after:

| file | before | after |
|---|---|---|
| `QuestInfo.img.xml` | `33173B49…EA6847B` | `E9C3DD92…D5B917C` |
| `Check.img.xml` | `EDAD8889…DAB3980` | `0B8FEB91…54DD13B9` |
| `Act.img.xml` | `6F6DB173…645BF409` | `987AAF64…476A25A3` |

## Verification gate — a real `Quest` load, and where it lives

```
mvnw.cmd test -Dtest=V84EvanQuestRealLoad
[INFO] Tests run: 2, Failures: 0, Errors: 0 -- in server.V84EvanQuestRealLoad
[INFO] BUILD SUCCESS
```

`src/test/java/server/V84EvanQuestRealLoad.java` loads all ten advancements through
`Quest.getInstance` over the **static `WZFiles` provider** — not a hand-built `XMLWZFile`, not a
stubbed `qm` — and asserts `hasScriptRequirement(false)` on each, plus that the name resolves out
of `QuestInfo.img` (`Dragon Master 1st`/`10th Job Advancement`), which proves a second image
resolved through the same static. It carries its own negative control: `22110`, an id v84 does not
ship, must report **no** script requirement — without it, "all ten said true" would also be the
result of a predicate that always says true.

**Why that class is not named `*Test` and does not run in the default suite.** `WZFiles.DIRECTORY`
is a `static final` resolved once per JVM, surefire runs the suite in one fork, and
`MobSkillFactoryTest` points `wz-path` at a `@TempDir` containing nothing but
`Skill.wz/MobSkill.img.xml`. Whichever class touches `WZFiles` first wins for every other class,
and **in the full suite `MobSkillFactoryTest` wins** — measured, not assumed: the first full run
failed with

```
wz-path resolved to 'C:\Users\...\junit-4297784088248428350/wz', which holds no Quest.wz
  - another test class won the WZFiles.DIRECTORY race, so this says nothing about the merge
```

which is the guard doing its job: an order-dependent failure that reads as an order-dependent
failure rather than as missing quest data. The ten sibling `V84*NodeTest` classes all avoid
`DataProviderFactory` for this reason; this one cannot, because that static *is* the thing under
test. Falling out of surefire's default includes is the whole fix — no pom change, no fork
configuration, no ordering assumption, and no `assumeTrue` reporting green by skipping.

`src/test/java/server/V84EvanQuestDataTest.java` (7 tests, in the suite) covers everything that is
order-independent: the path list is 3 x 135 and all-`22xxx`, every added id present in all three
images, `7778` survived, all ten advancements carry a `startscript` **and** have a
`scripts/quest/<id>.js` file, the level ladder is exactly 10/20/30/40/50/60/80/100/120/160, and no
added id is one-sided.

## Three assertions in other tickets' tests that this data legitimately invalidated

Additive merges move counts that earlier tickets pinned exactly. Fixed in place, intent preserved,
not loosened to floors:

- `V84QuestNodeTest.thePreExistingQuestsSurvivedTheMerge` — pinned `pre + 63`. Now
  `pre + 63 + 135`, **except `Say.img`, which stays at +63** because this ticket does not merge it.
- `V84QuestNodeTest.the22515To22518GateIsTicket13sAndIsStillUnmet` — asserted `22515`–`22518`
  are **absent**. They are ticket 33's and are now present. The assertion is **inverted, not
  deleted**, so it now fails if the Evan chain is ever dropped; the half that still holds (they are
  not on ticket 09's path list, and `scripts/npc/1012118.js` still gates on `22515`) is untouched.
  **Ticket 08's `910060100` handoff is therefore now met** — 09 correctly said it could not meet it.
- `V84RegressionTest.the2818QuestsThatPredateTheMergeAreAllStillPresent` — subtracted only ticket
  09's ids before counting. Now subtracts ticket 33's too, from its committed path list, and
  asserts the two lists are disjoint so neither ticket can silently claim the other's ids.

## Declined, and not verified — no silent omissions

**Declined, deliberately:**

- **`Say.img` — 135 nodes, the only real decline.** `Quest.java:116-118` opens `QuestInfo.img`,
  `Act.img` and `Check.img` and nothing else; `Say.img` is never read by the server. Merging it
  would be ~1.9 MB of dead weight. (Ticket 09 *did* merge its 63 into `Say.img`, so that file is
  now at +63 and internally inconsistent with the other three by exactly these 135. Recorded here
  so it is a known state, not a discovery.)
- **`Exclusive.img`, `PQuest.img`, `PQuestSearch.img` — untouched.** Nothing in `src/` reads
  `Exclusive.img`, and ticket 09 measured that v84's numeric groups merged additively onto this
  tree's named `medal` group put seven ids in two mutually-exclusive groups at once. Not re-litigated.
- **Zero rows refused and zero deny-list hits** on this ticket's 405 — every row is a whole new
  quest id, so nothing reached into an existing record. The deny list was still loaded and the gate
  was still live; it was exercised deliberately (below) rather than trusted because it was quiet.

**Inconsistent in v84 itself — 19 ids, reported as the ticket asks, none of them added by me.**
Full list in `CROSS-IMAGE.txt`. v84 has 19 quest ids that are not in all three images:
`4960` (QuestInfo+Check), `9250`–`9254`, `9266`, `9433` (Act only), and `9800`, `9809`–`9817`,
`9820` (QuestInfo+Act). **This tree already had all 19 with the identical shape, and still does** —
the merged tree's asymmetry list is the same 19, unchanged, so the merge introduced none.
**Of the 135 added ids, 0 are one-sided** — all 135 are in all three v84 images. `28332` exists in
`Say.img` alone; irrelevant, `Say` is not merged.

**Measured, not verified:**

- **"The server starts and loads quests without error" is only half-proven.** The quest data path
  is proven by a real `Quest` load through the production static provider. A full server start was
  **not** performed — it needs a database and a listening port, and this worktree is shared with
  concurrent agents. Whoever runs the next batched launch should treat that as the outstanding half.
- **In-game acceptance of any Evan quest was not performed** and cannot be from here: all 135
  require an Evan job id on the start block (`2001` on 24 of them, `2200`/`2210`–`2218` on 118),
  and no character this tree can create holds one. Same shape as ticket 09's finding, different
  cause — **not a date problem this time.**
- **`22100`'s prerequisite is `22007`** (state 2), which is inside the merged `220xx` block, so the
  chain's own upstream is present.

## Independent corroboration — Hidden Street (pre-Big-Bang archive)

Used as cross-check only; the WZ is the authority. The live site is behind a Cloudflare JS
challenge, so this came from Wayback snapshots **of that same site**, no substitute source.

- **Advancement levels: AGREES, 10/10 exact** — 10/20/30/40/50/60/80/100/120/160, including the
  irregular 60→80→100→120→160 tail. This is the check that mattered and it is clean.
- **Prerequisite chain: AGREES** — linear, each Nth requiring the (N-1)th, with the 1st requiring
  "Collecting Eggs", consistent with the WZ's `22007`.
- **NPC: AGREES on identity, NOT COVERED on id.** Every advancement page names **Mir**, Evan's
  dragon, triggered by the lightbulb and with no map location — consistent with `1013000` being a
  mapless Evan-specific NPC. Hidden Street prints no numeric ids anywhere, so `1013000` itself is
  uncorroborated.
- **Chain size: roughly AGREES** — 113 archived pages under its Evan-only "New Hero" category
  against the WZ's 135; archive coverage is not guaranteed complete. The `225xx`=97 banding is
  **NOT COVERED** (no ids on the site), as are the ids `22100`–`22109` themselves.
- **No disagreements found.**

## Instrument faults caught before they became conclusions

Recorded because this project's standing lesson is that the instrument lies before the reasoning does.
Every one of these produced a confident, wrong number first:

1. **`core.autocrlf=true`.** The blob is LF, the working tree is CRLF. The first run of proof B
   reported **324 changed quests in `QuestInfo`** — pure encoding, zero data. `git cat-file blob`
   comes out 24,402 bytes short of the file it came from, exactly the CR count
   (2,287,619 + 24,402 = 2,312,021). The reference is now the blob with LF→CRLF and the sizes match
   to the byte.
2. **PowerShell 5.1 `Set-Content -Encoding utf8` writes a BOM,** and PowerShell strips it again on
   read — so nothing in the PowerShell layer noticed. Java did not: the first test run read row one
   of the path list as `\uFEFFQuest.wz/QuestInfo.img/22000`. All generated lists are now BOM-less
   via `[IO.File]::WriteAllLines`.
3. **A PowerShell function returning a `HashSet` gets unrolled by the pipeline.** The first
   cross-image report claimed `QuestInfo=1` and **3,021 inconsistent ids**. It was the script.
4. **Myers diff realignment** — the 190 phantom deletions above.
5. **The merge tool was proved before it was trusted**, on a case with a known answer, not on a
   self-test that can only pass: a 3-row control of one id that already exists (must be refused by
   the additive gate), one deny-listed row (must be refused by the deny list), and one genuinely
   new id (must be added). It returned exactly `SKIP / SKIP / ADD`, exit 3, with both refusals
   named in `conflicts.txt`, before the real 405 were run.

## Acceptance criteria

- [x] Every v84 quest id absent from this tree is present in `QuestInfo`, and in `Check`/`Act` —
      containment measured: **0 v84 ids missing from any of the three**
- [x] All 135 `22xxx` Evan ids present, including `22100`–`22109` with their `startscript`
      (`q22100s`…`q22109s`, all ten, plus a `scripts/quest/<id>.js` for each)
- [x] **Zero pre-existing quest ids changed** — two independent automated proofs with self-checks,
      output above, plus three git diff algorithms agreeing on a pure insertion
- [~] The three files still parse (proven three ways: `XMLWZFile`, `XmlDocument`, and a real
      `Quest` load) — **the server start itself was not performed**, see above
- [x] Full suite green — **2092 passed, 0 failed** (baseline 2072; the delta is this ticket's 7
      plus concurrent agents' tests in the same worktree)
- [x] Every declined or inconsistent quest listed with its reason

## Rollback

Unchanged: `git checkout wz/Quest.wz/` restores current behaviour. Note that the three test files
this ticket touched would then fail, which is the intended coupling.
