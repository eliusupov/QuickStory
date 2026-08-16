# 28 — closing the measured v84 content gap in the server tree

**What this is:** the merge ticket 27 costed. It executes against 27's numbers, and it **corrects
three of them** — one of which was hiding a defect that would have taken the cash shop down.

**Status:** delivered. **416 rows / 7,219 nodes added** across **31 files** under `wz/`.
Zero pre-existing nodes changed, proven against `HEAD` with five self-checks that each break a
proof and require it to notice. Every added node compared node-for-node against the stock v84
archive: **0 divergences**. Suite **2123 passed, 0 failed**.

Baseline for every proof: `cdaecd678` (`git rev-parse HEAD` at merge time; `66d6c8e1a` + ticket 31,
which touched no `wz/`). `WzMerge selftest` was run first and passed all 26 checks including both
halves of the T23 positional-array regression.

Server tree only. Nothing under `D:\games\MapleStory\` was read for writing or written.

---

## What landed

| archive | rows | how | what |
|---|---:|---|---|
| `Etc.wz` | **105** | appended (see §Cash shop) | the cash-shop SNs v84 sells and this tree did not |
| `Etc.wz` | 75 | `WzMerge xml` | `Commodity` 11 · `CashPackage` 11 · `NPT_exception` 53 |
| `Etc.wz` | 32 | `WzMerge xml` | `NpcLocation` — **10 more were DENIED, correctly** |
| `String.wz` | 186 | `WzMerge xml` | `MonsterBook` 41 · `Consume` 33 · `Cash` 23 · `Map` 40 · `Mob` 18 · `ToolTipHelp` 16 · `Skill` 10 · `Pet` 3 · `Ins` 2 |
| `Mob.wz` | 7 | `WzMerge xml` | `2220110` `2230112` `9300388` `9300391` `9300393` `9300394` + `QuestCountGroup/9101004` |
| `Npc.wz` | 3 | `WzMerge xml` | `1022106` Christopher · `1022107` Perion Warning Post · `2030015` Hidden Rock |
| `Reactor.wz` | 3 | `WzMerge xml` | `1002008` `2302006` `2409000` |
| `Map.wz` | 2 | `WzMerge xml` | `Back/dragonDream.img` · `Tile/DeepgrassySoil.img` |
| `Skill.wz` | 1 | `WzMerge xml` | `9000.img` — GM skills `90000000`, `90001001`–`90001006` |
| `Effect.wz` | 1 | `WzMerge xml` | `Direction4.img` — Evan's cutscene direction |
| `Quest.wz` | 1 | `WzMerge xml` | `PQuestSearch.img` |
| | **416** | | |

Merge runs, verbatim: nine `WzMerge xml` invocations, `added 279 (forced 0), refused 0`, exit 0 —
plus the `NpcLocation` run, `added 32 (forced 0), refused 10`, exit 3, every refusal a deny-list
hit. `--deny COLLISION-DENY.txt` on every run including every dry run. Manifests and conflict
files: `docs/wz-baseline/merge-lists/28/`.

**The gate fires.** Re-running all nine manifests against their own output gives
`added 0, refused N`, **exit 5** on every archive — every row now resolves as already present.

**`git diff --numstat --diff-algorithm=minimal` over the 13 MODIFIED files under `wz/`: 3,613
insertions, 1 deletion.** (The other 18 files are new, so they have no diff.) Myers agrees with
minimal line for line here, so this is not ticket 33's realignment artefact. The single deletion is
documented below and is not a node.

---

## The one non-additive byte: a BOM

`wz/String.wz/MonsterBook.img.xml` is **the only BOM-bearing file in the entire `wz/` tree** —
measured, all 22,507 `.xml` files scanned, count 1. It predates every ticket here (`ad812de00`,
upstream Cosmic). `WzMerge xml` refuses to rewrite a BOM-bearing target on purpose, so the 41
Monster Book entries — the String gap ticket 27 called the one that matters, because Cosmic
implements Monster Book and the missing entries are visibly blank in-game — were refused with
`target xml has a UTF-8 BOM`.

The three BOM bytes were stripped, deliberately and as a separate step, before merging:

```
before  sha256 c9d759255929eba2c62a41392553f30f0df675e4ec3248fbae740ced4b5fa1ff  726747 bytes
after   sha256 069146b9973e97de86cb3d93c53949aba288b333799c313b809f069f5b4b1025  726744 bytes
identical after the 3 BOM bytes: True
```

It is the `-` line in the diff above, and it is the *only* one. Stated plainly because both proofs
below normalise a BOM away and therefore cannot see it: **this is the one byte-level change to
pre-existing content in the ticket, it is an encoding repair toward the tree's own convention, and
no node moved.** The file was also asserted CRLF-throughout before and after.

**Decided, not left as a side effect** — the three questions, answered:

- **Does the rest of the tree carry BOMs?** No. Census of every `.xml` under `wz/`:
  `String.wz` 20 files / **0** with a BOM; whole tree 22,507 files / **0** with a BOM. At the
  baseline commit the same census returned exactly one — this file. So the tree's convention is
  BOM-less, `MonsterBook.img.xml` was the lone straggler, and stripping it **removed** an
  inconsistency in a directory the server parses as a set rather than introducing one. Had the
  siblings been BOM-ful the right call would have been to preserve it and defer the 41 entries.
- **Does the read path care?** No, in either direction. `XMLWZFile.getData`
  (`src/main/java/provider/wz/XMLWZFile.java:74`) hands a raw `FileInputStream` to
  `XMLDomMapleData`, i.e. the DOM parser gets **bytes, not a `Reader`**, so it sniffs the encoding
  per the XML spec and skips a leading BOM itself. The ticket-33 fault was the opposite shape — a
  BOM written into a *path list* that Java read as text (`﻿Quest.wz/...`), where nothing
  sniffs anything. Proven live rather than argued: `theMonsterBookEntriesLandedAndTheOldOnesSurvived`
  reads `String.wz/MonsterBook.img` through that exact class and passes.
- **Was it necessary?** Yes. `WzMerge xml` refuses a BOM-bearing target outright, so the choice
  was strip it or drop the 41 Monster Book entries.

---

## Cash shop — ticket 27's biggest number was measuring the wrong thing

Ticket 27: *"`Commodity.img` index nodes `8947`–`9056` are absent — 110 rows, contiguous."*
That is true and it is not the gap, because **the slot index is not what anything reads.**

`CashItemFactory.loadAllCashItems` (`src/main/java/server/CashShop.java:241-250`) iterates the
children and does `loadedItems.put(SN, …)`. The node name is never read. So `Commodity.img` is an
**SN table server-side**, and the only questions that matter are which SNs are missing and whether
any row duplicates one.

Measured, with the comparator self-checked first (`array-align.py`, `sn_collide.py`):

- **The two arrays are not aligned.** Comparing `(SN, ItemId)` at every shared slot:
  **6,625 of 8,947 slots hold a different row in this tree than in v84**, diverging from slot
  **2322** onward. Self-check: comparing v84 slot *k* against server slot *k+1* differs on
  8,946 of 8,946, so the comparator is not inert.
- **87 of the "110 absent" slots are not new content at all.** They carry SNs
  `80000000`–`80000086`, which this tree already serves from slots `8854`–`8940` with the
  **identical ItemId, Price and OnSale** (0 of 87 differ). Merging them would have added 87
  duplicate SNs, and `put(SN, …)` would then have dropped one of each pair in HashMap iteration
  order — a silent, nondeterministic replacement of rows the server sells today. The additive
  gate is blind to it by construction: slot `8947` does not exist, so the gate says ADD. This is
  §4.5 of the procedure, found live.
- **At SN level the gap was 116**, not 110 — and **93 of the 116 sit in v84 slots this tree
  already uses for a different row**, so they are not merge-able by slot at all: the gate refuses
  them, and forcing would overwrite live rows.

### What was done instead

11 rows (`8947`–`8957`, SN `70000371`–`70000381`) were genuine appends above this tree's highest
slot and went in by `WzMerge xml`. The other **105 were appended at fresh slots `8958`–`9062`** by
`docs/wz-baseline/merge-lists/28/append-commodity.py`, which is a text append immediately before
the root close tag: no existing child is touched, the existing child order is preserved, and no SN
is duplicated. `Etc-Commodity.APPENDED.txt` records `newslot → SN → v84slot → ItemId` for all 105.

This is not a `WzMerge` operation and the ticket does not pretend it is — `WzMerge` copies a node
to the *same* path, which is exactly what cannot be done here. It is also the remedy the procedure
itself names for a diverged array (§4.4.2: *"re-author the row against this tree's index"*). It is
held to the same standard: the 105 rows are in `verify28.py`'s expected-new set like any other row,
and `fidelity28.py` compares each one node-for-node against **its v84 original**, found through the
recorded mapping rather than through its name.

### The defect this caught

The 11 `CashPackage` entries merged first were **broken on arrival**, and the Java test found it:

```
V84ContentMergeNodeTest.everyCashPackageSnResolvesToACommodityRow
  expected: <[]> but was: <[9101608 -> SN 20000659, … 40 entries …]>
```

`CashItemFactory.getPackage` is `for (int sn : packages.get(itemId)) cashPackage.add(getItem(sn).toItem());`
— an unknown SN is a **NullPointerException**, and the 11 rows selling those packages carry
`OnSale = 1`, so they were buyable. All 40 unresolved SNs are in the 93 that cannot be
slot-merged. Appending the 105 is what fixed it; the test is the pin.

**End state: this tree sells 9,063 distinct SNs, 0 duplicated. v84 sells 9,057. Every v84 SN is
now present**, and the 6 extra are Cosmic's own, untouched.

### `OnSale` — investigated, not touched

| | on sale | off |
|---|---:|---:|
| this tree, before | 2,010 | 6,937 |
| this tree, after | **2,070** | 6,993 |
| **stock v84, whole file** | **1,995** | 7,062 |

**"The shop looks empty" is not a data gap and not even a divergence from v84.** Stock v84 ships
78% of its own catalogue with `OnSale = 0` — that is Nexon rotating stock, not a Cosmic defect —
and this tree is already *more* generous than v84 was. Nothing was flipped: which items are on
sale is a game-design decision for the owner. If more stock is wanted, the lever is
`Commodity.img/<slot>/OnSale`, it needs no code, and it is a deliberate choice rather than a merge.

Two things the merge does **not** fix, both pre-existing and both out of scope here: gift,
name-change and world-transfer hardcode `NX_PREPAID` instead of the chosen currency
(`CashOperationHandler` 0x04 / 0x2E / 0x31), and the 105 appended rows carry v84's `Bonus` field
while the other 8,958 do not — nothing in `src/main/java` reads `Bonus` (`grep '"Bonus"'` → 0 hits).

---

## `100030301` "Forest Hall" — REFUSED, and the reason is stronger than the one on record

The previous refusal said `9901910`–`9906599` is *"a range the server allocates from"*. That is
right but loose enough to be argued with. Traced end to end, it is **specific**:

- `PlayerNPC.fetchAvailableScriptIdsFromDb` (`PlayerNPC.java:319-324`):
  `branchSid = NpcId.PLAYER_NPC_BASE + branch*100`, `branchLen = 100` for `branch < 26`.
- `GameConstants.getHallOfFameBranch` (`:384-420`): **branch 19 is `THUNDERBREAKER1`**.
- So the live allocation window is `9900000 + 1900 = ` **`9901900`–`9901999`**, which contains
  `9901910`–`9901919` **exactly**.
- The only gates on handing one out are the `playernpcs` table (`SELECT scriptid …`) and
  `PlayerNPCFactory.isExistentScriptid`, which is `npcData.getData(scriptid + ".img") != null` —
  and this tree ships all ten of `wz/Npc.wz/990191{0..9}.img.xml`. **Nothing blocks it.**
- Reachable in practice: any Thunder Breaker who maxes out on `NAUTILUS_TRAINING_ROOM`, which
  `isHallOfFameMap` returns true for.

So the dispatch's escape hatch — *prove the allocator range is not reachable for these ids* — is
closed in the negative: it **is** reachable, and I can name the job, the map and the line.

`Map/Map1/100030301.img` places ten fixed NPCs on exactly those ten ids (`life/0`–`life/9`,
`type=n`, `hide=1`), so merging it would put static world placements on ids the server hands out
at runtime. The map cannot be merged without them either: `WzMerge` copies whole subtrees, and a
deny root *inside* a copy root refuses the whole row (§4.3). Stripping `life` would be a
hand-authored map image, which is a different and much larger risk than the map is worth — Forest
Hall is a ranking hall on Farm Street, reachable only from `100030300`, and this tree has no
`1000303xx` farm cluster to reach it from.

**Verdict: stays out.** `V84EvanWorldNodeTest.forestHallIsDeliberatelyNotMerged` keeps the pin;
`V84ContentMergeNodeTest.forestHallAndItsNpcLocationsStayOut` adds the second half with the trace
above in its javadoc, so neither the map nor the NpcLocation ids can be taken without a red test.

### `Etc.wz/NpcLocation.img` — what was added, and the explicit confirmation

**Nothing in `9900000`–`9906599` was added. Confirmed from the diff, not from intent.**

All 42 absent `NpcLocation` ids were put in front of `WzMerge` deliberately, so the deny-list had
to make the call rather than being quiet because nothing reached it. Result: `added 32
(forced 0), refused 10`, exit 3, every refusal reading `DENIED by deny-list
[Etc.wz/NpcLocation.img/990191x]: server-allocated id range, PlayerNPC.java:66`
(`Etc-npcloc.xml.conflicts.txt`). That run is this ticket's live proof the deny-list is loaded and
firing.

The 32 that landed, in full — **highest id `9201145`, so not one is within 700,000 of the
allocator range**:

```
1011101 1013001 1013002 1013100 1013101 1013102 1013103 1013104 1013105 1013106
1013200 1013201 1013202 1013203 1013204 1013205 1013206 1013207 1022106 1022107
1063018 1205000 2012034 2030015 2085000 2085001 2085002 2085003 2092100 2092101
9201144 9201145
```

Checked mechanically against the working tree rather than against the manifest:

```
$ git diff --diff-algorithm=minimal -- wz/Etc.wz/NpcLocation.img.xml \
    | grep '^+' | grep -o 'imgdir name="[0-9]*"' | grep -o '[0-9]*' \
    | awk '$1>=9900000 && $1<=9906599' | wc -l
0
$ git diff --numstat --diff-algorithm=minimal -- wz/Etc.wz/NpcLocation.img.xml
106     0     wz/Etc.wz/NpcLocation.img.xml
```

**Why the 32 are safe.** They are world placements for NPCs that already exist in this tree —
`1013xxx` is the Evan farm/dragon cluster, `1022106`/`1022107`/`2030015` are the three NPC images
this same ticket merged, `2085xxx` and `92011xx` are ordinary town NPCs. None is in any
server-allocated range: `PLAYER_NPC_BASE` is `9900000` and the only other injected id is
`9977777`, both far above the maximum here. Nothing in `src/main/java` reads `NpcLocation.img` at
all (`grep -rn NpcLocation src/main/java` → 0 hits), so server-side the merge is inert; it is the
client's find-NPC UI data, merged for tree completeness.
`V84ContentMergeNodeTest.forestHallAndItsNpcLocationsStayOut` asserts all ten forbidden ids are
still `null`, and asserts the image holds >1,000 entries so that check cannot pass vacuously.

---

## Proof that nothing pre-existing changed

`docs/wz-baseline/merge-lists/28/verify28.py`, output committed as `verify28-report.txt`.
Two independent proofs per file, over **31 files**:

```
TOTAL over 31 files: changed=0 missing=0 unexpectedNew=0 expectedNew=7165 proof2LostLines=0
  PASS  control (unmutated)
  PASS  changed pre-existing value          -> proof1 changed>0
  PASS  deleted a pre-existing line         -> proof2 lost>0
  PASS  node nested inside a pre-existing record -> unexpectedNew>0
  PASS  two pre-existing siblings reordered -> proof1 changed>0
RESULT: PASS
```

**Proof 1 (parse).** A canonical record for *every* node: `path → (tag, all attributes, ordered
child-name list)`, with duplicate sibling names disambiguated by occurrence so a duplicated block
cannot hide behind its own key (ticket 33's Proof B hole). Assert every baseline path survives
with an identical record, and that the set of *new* paths is exactly the subtrees of the manifest
rows. A container whose only delta is manifest children is unchanged — checked as a
**subsequence**, so a reordered or replaced pre-existing child still fails.

**Proof 2 (text).** Every line of the baseline must survive with at least the same multiplicity.
Parser-independent; catches a deletion Proof 1 might normalise away.

**The self-checks are the point.** Ticket 33's lesson was that a check which can only print PASS
is not a check, and that a proof-of-zero can be vacuous. All four mutations are applied to a real
file (`String.wz/Consume.img.xml`) against a real baseline, the unmutated control is asserted to
pass in the same run, and the fourth deliberately reproduces the hole ticket 33 found in its own
Proof A — a node smuggled *inside* a pre-existing record at the same indent — and requires this
comparator to catch it.

Baseline is a `--rev` parameter, not a hardcoded `HEAD`, so re-running after the commit does not
silently self-diff — the trap that made ticket 33's first report vacuous. Reproduce with:

```
python docs\wz-baseline\merge-lists\28\verify28.py --rev cdaecd678
```

## Proof that the added nodes are what v84 holds

`fidelity28.py`, output committed as `fidelity28-report.txt`:

```
== 416 manifest rows, 7219 nodes compared against stock v84
   reader reconciliations: 6 float values compared numerically,
                           78 UOL nodes compared by type+presence only
== 0 divergences: every added node is what v84 holds (name, type, value, child order)
```

Ground truth is `WzMerge dump <v84>.wz <img> 30` — MapleLib's reader plus a six-line `Print`, a
different code path from the `XmlSerializer` that wrote the files. The comparator is **ticket 33's,
imported rather than re-implemented**, together with its four-mutation self-check, which runs
first and voids the result if any mutation escapes:

```
  CLEAN   control (unmutated clone) -> no findings
  CAUGHT  changed leaf value      CAUGHT  dropped child
  CAUGHT  extra child             CAUGHT  reordered children
```

Three reader-level reconciliations, each measured and stated rather than tolerated silently:

- **float.** The dump prints .NET's `ToString` (`10`); the serializer writes `10.0`. Six nodes,
  compared numerically.
- **UOL.** `dump` *follows* a `WzUOLProperty` and prints the resolved target's children with no
  link string (procedure §6.1 note 2), while the XML correctly holds `<uol value="../0/0"/>`.
  78 nodes: type and presence checked, link string not checkable from a dump. This is what the
  43 "divergences" in the first run turned out to be, all in `Reactor.wz`.
- **XML attribute normalisation.** `String.wz/Cash.img/5240019/name` genuinely contains eleven
  TAB characters. An XML parser normalises those to spaces on read, so the naive comparison
  reported a false difference. Tabs are escaped to `&#9;` before parsing, making the comparison
  exact instead of blind.

Two instrument faults were fixed before any result was believed, both inherited:

1. **Ticket 33's `LINE` regex cannot match a canvas line.** `0 [WzCanvasProperty] 1234x600, png
   341206 bytes` has no ` = value`, so it was swallowed as a value continuation **together with
   its whole subtree** — 971 phantom divergences, and `Effect.wz/Direction4.img` reported as 839
   nodes when it is 1,655. `Quest.wz` has no canvases, so ticket 33 never met it. Anyone reusing
   `merge-lists/33/fidelity.py` on an archive with canvases needs this fix.
2. **Console codepage.** `String.wz/Skill.img` failed to decode until the dump was taken under
   `chcp 65001`. Decoding is **strict** UTF-8, so a mangled codepage raises instead of comparing
   equal.

## The instrument was proved before the measurement

`gap.py --selftest` reproduces four answers that were known independently before it ran, plus two
controls:

```
  PASS  Quest.wz/QuestInfo.img  d=1 absent=0   expected=0    (ticket 33's containment)
  PASS  Quest.wz/Say.img        d=1 absent=135 expected=135  (ticket 33's deliberate decline)
  PASS  Etc.wz/Commodity.img    d=1 absent=110 expected=110  (ticket 27's count)
  PASS  String.wz/Map.img       d=2 absent=40  expected=40   (ticket 27's count, a DEPTH-2 case)
  PASS  negative control: 3015 v84 ids and 1620 server ids each diff to 0 against themselves
  PASS  '22000' occurs in Say.img.xml TEXT but is not one of its 2864 nodes, while it IS one
        of QuestInfo's 3016
```

That last one is ticket 27's fault #2 turned into a permanent assertion. Its first form was
**vacuous** — `"22000" not in s` is trivially true when `s` is empty, and `s` *was* empty on the
first run because the dump parser was wrong. It now asserts both halves and the set sizes.

---

## Corrections to ticket 27

1. **"Cash shop — pet item data — 2 absent: `5000022`, `5000054`" is wrong.** Both are present in
   `wz/Item.wz/Pet/`, and with **more** data than v84 has (1257 vs 987 lines, 243 vs 183). Nothing
   was owed and nothing was merged. The three pet ids genuinely absent were *names*, in
   `String.wz/Pet.img`: `5000040`, `5000043`, `5000046` — merged.
2. **"110 absent `Commodity` rows"** is a slot count. At SN level the gap was 116, of which only
   23 were new content and only 11 were merge-able by slot. See §Cash shop.
3. **`String.wz/ToolTipHelp.img` is 16 absent, not 10.** Ticket 27 diffed depth-2 names; two of
   the ids exist under one parent and not the other, so a name-only diff under-counts. This
   ticket diffs full paths.

Everything else in ticket 27 that this ticket touched reproduced **exactly**: `Commodity` 110,
`CashPackage` 11, `NPT_exception` 53, `NpcLocation` 42, `MonsterBook` 41, `Consume` 33, `Cash` 23,
`Mob` 18, `Skill` 10, `Pet` 3, `Ins` 2, `Map` 40, `Eqp` 37.

---

## Deferred, with reasons — no silent omissions

| deferred | rows | why |
|---|---:|---|
| `Quest.wz/Say.img` | 135 | `Quest.java:116-118` opens `QuestInfo`/`Act`/`Check` and nothing else; `grep -rn "Say.img" src/main/java` → 0. ~1.9 MB of dead weight. Ticket 33's decision, upheld. Pinned by `sayImgIsStillNotMerged`. |
| `Map/Map1/100030301.img` | 1 map | §Forest Hall. Refused on a traced allocator collision. |
| `Etc.wz/NpcLocation.img/990191x` | 10 | Same cause. Offered to the merge and refused by the deny-list, on the record. |
| `Commodity/*/Bonus` on pre-existing rows | 8,941 | A v84 schema field, not content. `grep '"Bonus"' src/main/java` → 0 hits. Every row is a write *into* an existing record — the §4.5 hazard class — for zero server-side effect. The 105 appended rows carry it because they came whole from v84. |
| `Mob info/category` | 1,168 | Same shape. `grep '"category"' src/main/java` → 0 hits. |
| `String.wz/Eqp.img` names | 37 | Unreleased-item stubs — the items have no data in v84 either. Ticket 27: "do not spend a ticket on them." |
| Map sub-nodes (`portal/<n>`, `life/<n>`) | 158 | Positional-array writes into maps players use today. Ticket 08 measured this class and put twelve rows on the deny-list; re-opening it needs the by-name array comparison of §4.4.2, per map, not a bulk merge. |
| `Skill.wz/Dragon/` | 1 dir | Another agent owns it. |
| GM skill *effects* for `9000.img` | — | Data merged (icons + names resolve); nothing implements the skills. Java, low value, not player-facing. |
| `Commodity` slots `8958`–`9044` at their **v84 indices** | 87 | They repeat SNs this tree already serves. Permanently refused, and `noCommoditySnIsServedTwice` fails if anyone takes them. |

**Not added to `COLLISION-DENY.txt` deliberately.** The 87 refused slots are already refused
structurally — the positional-array gate cannot append them now that the array runs to 9062, and
the Java test fails if they arrive by another route. Editing a file three other tickets share, to
record a decision two committed artefacts already record, is the wrong trade.

---

## Verification

- `WzMerge selftest` — 26 checks, all pass, run before anything was written.
- Nine `WzMerge xml` dry runs, `--deny` on every one, read before the real runs.
- Idempotence: all nine manifests re-run against their own output → **exit 5** each.
- `verify28.py` — 31 files, `changed=0 missing=0 unexpectedNew=0 lostLines=0`, 5/5 self-checks.
- `fidelity28.py` — 416 rows, 7,219 nodes, 0 divergences, 4/4 comparator mutations caught.
- `git diff --numstat --diff-algorithm=minimal` — 3,613 insertions, 1 deletion (the BOM).
- `src/test/java/server/V84ContentMergeNodeTest.java` — 12 tests through `XMLWZFile`, the class
  the running server uses. Manifest-driven (it reads `merge-lists/28/*.paths.txt` rather than
  restating the ids, so it cannot drift from the merge), plus the four things a manifest cannot
  say: no duplicate SN, every cash-package SN resolves, the re-slotted SNs all landed, and the
  deliberate absences are still absent.
- Full suite: **2123 passed, 0 failed** (baseline 2096; +12 mine, the rest concurrent agents').

**Not verified:** no server start and no client launch — server-side only, and this worktree is
shared with agents running against the live server. The client's own `.wz` archives were not
touched, so `Effect.wz/Direction4.img`, `Map.wz/Back|Tile`, `Quest.wz/PQuestSearch.img` and
`Skill.wz/9000.img` are inert here until a client build reads this tree; they were merged for
completeness, cost ~2,600 nodes in total, and are named here rather than left as a surprise.

## Rollback

`git checkout -- wz/` restores current behaviour, and `V84ContentMergeNodeTest` then fails, which
is the intended coupling. Everything is reproducible from
`docs/wz-baseline/merge-lists/28/` — `gap.py` measures, `mkpaths.py` regenerates the manifests,
`append-commodity.py` is idempotent, `verify28.py` and `fidelity28.py` prove the result.
