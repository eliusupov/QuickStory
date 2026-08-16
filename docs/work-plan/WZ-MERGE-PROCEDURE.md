# WZ merge procedure — v84 content into the live v83 client + Cosmic's server XML

**Tickets 04–09 execute this document. Do not invent a second way.** Established by ticket 03
(tracer: item `2001500`, "Red Potion"), hardened by ticket 03b after a safety review, collision
figures re-measured by ticket 02g, and hardened again by ticket **03e** after a second review found
the deny-list unenforced, the staging guard blind in the configuration this document itself
prescribes, `deps` wrong at the granularity ticket 06 needs, and a failure mode that exits 0.
Everything here was run end to end, not designed on paper; the verbatim output is under
`docs/wz-baseline/merge-lists/03-verification/`.

If you are about to run a merge and have no other context: read sections 0–4, then follow section 5
literally. Nothing before section 5 is optional background.

---

## 0. Before you touch anything

| | |
|---|---|
| **Working directory** | `D:\games\MapleStory\Server\Cosmic\.claude\worktrees\evan-dualblade` — run every command from here, never from `D:\games\MapleStory\`. MapleLib writes a multi-hundred-MB `.TEMP` scratch file relative to the process working directory (`WzFile.cs:664`). The tool now pins that to the staging directory during a save; do not make it your only defence. |
| **The client must be closed.** | Windows will not let you replace a `.wz` the client holds open, and a merge whose target is held open fails partway. Close MapleStory *and* any HaRepacker window before 5.7. |
| **Backup** | `D:\games\MapleStory\Server\_backup\client-v83-EzorsiaV2-2026-08-15\`. Confirm by hash that it covers every file you are about to change, **before** you change it (5.0). It is the only rollback for the binary side. |
| **`D:\games\MapleStory\` is read-only** except the single install copy in 5.7. Nothing else here writes there. |
| **Server-side undo** is `git checkout -- wz/`. The XML side is in git; the binary side is not. **Check `git status` before you use it** — another ticket's uncommitted XML lives in the same tree, and a blanket checkout takes theirs with yours. |
| **You are not alone.** More than one ticket runs at a time. Everything you create goes under `<stage>\<T>\`, including `pre\` (section 1). Never write into another ticket's directory, and never merge from a `pre\` you did not take. |
| **MapleLib revision** | `HaRepacker-src` at `a7c38edf7c58e8a8b272af490c51113db76bff08`. Every safety claim below is a property of that source. Check with `git -C D:\games\MapleStory\Server\porting-resources\reference-sources\HaRepacker-src rev-parse HEAD`; if it differs, the file/line references in `03-verification/` need re-reading before you trust them. |

## 1. Staging — the only supported way to run a merge

**A merge never writes to the live client.** It writes into a staging directory, is verified there,
and is then *copied* into place by hand as a separate, interruptible step.

```
D:\games\MapleStory\Server\wz-merge\
  <ticket>\pre\   byte-identical copies of the live .wz files THIS ticket merges into
  <ticket>\       the merged output ("post")
```

**`pre\` is per-ticket. This is not cosmetic.** It used to be one shared directory, and two tickets
that touch the same `.wz` cannot share it: if 06 installs its merged `Map.wz` and 07 then merges
onto the stale shared `pre\Map.wz`, **07's output silently reverts 06** — both runs exit 0, and
section 6.2's diff compares against that same stale snapshot and reports clean. Nothing downstream
catches it. So:

- take your own `pre\` under your own ticket directory (5.1), and
- every real merge must pass **`--live <the live .wz>`**. The tool SHA-256s the target and the live
  file and refuses with exit 2 if they differ. That is the check that makes a stale snapshot
  impossible to merge from rather than merely discouraged.

Why staging is a rule, not a preference — `WzFile.SaveToDisk`:

- **truncates the destination the instant it starts** (`WzFile.cs:675`), then spends the following
  minutes streaming unchanged images out of the *target's own open reader*
  (`WzDirectory.cs:353-357`). `Map.wz` is 629 MB.
- So an OOM (a `WzDirectory` manifest row `DeepClone`s an entire subtree into memory), a full disk
  or a Ctrl-C leaves a **truncated file that looks finished**.
- And its scratch file is relative to the working directory.

**The output-directory rule is absolute and does not depend on `<targetWz>`.** The tool refuses
with **exit 2**, before opening anything, when the directory `<outWz>` names:

- contains any `.exe` — that is a game install, never a staging directory; or
- already holds `.wz` files WzMerge did not put there. WzMerge drops a `.wz-merge-stage` marker in
  a directory the first time it writes one, so a multi-file ticket keeps working while the live
  client's 18 `.wz` are refused outright.

**If you are re-running a ticket that already has a staging directory from an earlier attempt,
start a new one** (`<stage>\<T>-r2\`). Output from before ticket 03e was produced by a tool with an
inert deny-list and a `deps` that under-reported, so it is not trustworthy. Those directories carry
no marker, so the guard refuses to write beside them — that refusal is the correct answer, not an
obstacle to work around. **The marker is not a judgement about the contents of a directory**, only
about who created it: once a directory has one, WzMerge will keep adding `.wz` to it, which is what
a multi-file ticket needs and is also why a fresh directory is the right call after any change to
the tool.

It still also refuses `<outWz>` equal to the target or to the v84 source. The three guards that
existed before were *all relational to `<targetWz>`* — and since 5.4 sets the target to a staging
snapshot rather than the live file, an `<outWz>` of `D:\games\MapleStory\Map.wz` passed all three
and the finished merge was `File.Move`d straight onto the live client. Ask the guard anything, in
advance, without writing a byte:

```
WzMerge guard D:\games\MapleStory\Map.wz          # -> REFUSED, exit 2
WzMerge guard <stage>\<T>\Map.wz                  # -> ALLOWED, exit 0
```

Once a merge has produced `<outWz>`, run it again with `--baseline <stage>\<T>\pre\<Name>.wz`
as the **last step before installing**: that form also asserts positional-array continuity and
exits 4 on a holed array. See 4.4.2 — this is the check that would have stopped the
`MapLogin.img/back` install.

Within staging it writes `<outWz>.partial`, verifies it, and only then moves it onto `<outWz>`.
The refusals and the corrupted-output test are demonstrated in `03-verification/safety-guards.md`.

## 2. Build

```
dotnet build -c Release docs/wz-baseline/tool-merge/WzMerge.csproj
# binary: docs/wz-baseline/tool-merge/bin/Release/net10.0-windows/WzMerge.exe
```

Sibling of ticket 02's diff tool (`docs/wz-baseline/tool/`), same `MapleLibProject` override.

## 3. Commands

```
WzMerge dump   <wz> <path/under/wz> [depth]
WzMerge merge  <sourceWz> <targetWz> <outWz|-> <pathsFile> <conflictsTxt> --deny <denyList> [--force <forceList>] [--live <liveWz>]
WzMerge xml    <sourceWz> <xmlRoot>            <pathsFile> <conflictsTxt> --deny <denyList> [--force <forceList>] [-]
WzMerge verify <wz> <pathsFile> [--baseline <targetWz>]
WzMerge hash   <wz> <path/under/wz>
WzMerge deps   <mapWz> <mapId|Map/MapN/<id>.img> <addListDir>
WzMerge guard  <outWz> [--baseline <targetWz>]     # --baseline REQUIRED if <outWz> exists (4.4.2)
WzMerge selftest                                   # no arguments, touches no disk; exit 0 / 4
```

`<pathsFile>` is a manifest: lines exactly as `docs/wz-baseline/add-list/*.txt` writes them
(`Item.wz/Consume/0200.img/02001500`); `#` and blanks ignored. Feed it an add-list directly, or a
hand-cut subset (see `docs/wz-baseline/merge-lists/`). **A paths file with zero rows is exit 2, not
a successful no-op** — see 5.3 for how you get one by accident.

**Path form.** `merge` / `xml` / `verify` take *manifest* form, rooted at `<Name>.wz`.
`dump` / `hash` / `deps` accept **either** manifest form or root-relative form
(`Item.wz/Consume/0200.img` and `Consume/0200.img` both work). That asymmetry used to be silent
and section 6.1 fed `hash` paths it could not resolve.

**Dry run:** `-` in the `<outWz>` slot for `merge`, `-` as a trailing argument for `xml`. Every
check runs; nothing is written. `--live` is not needed for a dry run; `--deny` still is.

**Manifest root:** both `merge` and `xml` match the leading `<Name>.wz` of each manifest line
against the **source** argument, so renaming a staging copy of the target cannot break lookups.
`merge` prints a note when the target is named differently. A row rooted at a *different* `.wz`
(which `deps` legitimately emits) is refused into `conflicts.txt` with that reason, not thrown.

### Exit codes — check them; do not eyeball the log

```
0  every requested path was added (or, on a dry run, would be)
1  unexpected failure
2  bad arguments, a 0-row paths file, a deny/force overlap, or a safety guard refused
3  completed, but >=1 row was REFUSED — read conflicts.txt
4  post-write verification failed — the output is NOT installable
5  refused rows AND ADDED NOTHING AT ALL — almost always a wrong argument or a stale manifest
```

`added 0, refused 21` is **exit 5**, not 0 and not 3. A scripted 04–09 loop that only checks for
zero stops on a file that imported nothing, which is the point; and 5 separates "imported 5,000 and
dropped one" from "imported nothing", which 3 used to conflate. For a dry run, exit 3 means
"collisions found" — that is the answer you asked for, not a fault; exit 5 on a dry run means the
target already has everything you asked for, or you are pointed at the wrong file.

## 4. The three rules the tool enforces

### 4.1 Additive-only, enforced in the write path

`WzMerge merge` resolves the target path **before** it writes. If anything already lives there the
write never happens — the row goes to `conflicts.txt` and the loop moves on. The only mutations are
`AddImage` / `AddDirectory` / `AddProperty` onto a parent that does not already hold that name
(the `ADDITIVE-ONLY GATE` comment in `Merge()`). Correctness comes from construction: a
presence-only re-diff after the merge cannot show that nothing pre-existing *changed*, since a
destructive overwrite preserves paths too. Section 6 is what to run instead.

Behind the gate: three of its four write branches append blindly (only `WzImage.AddProperty` throws
on a duplicate), so after every add the tool re-counts the parent's children with that name and
aborts the run — before saving — if it is not exactly 1.

**The XML side has the same intent and a different mechanism, and the difference matters.** It is a
line-text scan, so it only recognises elements written the way Cosmic's serializer writes them
(2 spaces per level, `name="…"` on the opening line). It walks the ancestor chain by indentation and
refuses a path whose name already exists **inside that parent** — so the gate holds at
`Eqp.img/Eqp/Hair/30000`, not just at the root — and refuses to overwrite an existing `.xml` file.
It refuses outright if the target file has a UTF-8 BOM or is not CRLF throughout (the splice
rewrites the whole file, and silently normalising someone else's line endings while adding one node
is not additive in any sense that matters). And it refuses if the container it is about to add to
shows **no children at the expected indent** — which is what a file indented any other way looks
like, and without that check the gate is blind and duplicates the node. (That last one also refuses
a genuinely empty container; that is the safe direction.) All of these are demonstrated in
`03-verification/safety-guards.md`.

### 4.2 conflicts.txt is a deliverable, not a log

It is the exhaustive list of v84 content the rule dropped. **A v84 change that is an *edit* rather
than an *addition* is indistinguishable from a collision, and additive-only silently discards it.**
Read the file before shipping. Two real examples from the tracer sweep, one per direction:

| path | live client | v84 | verdict |
|---|---|---|---|
| `String.wz/Npc.img/9901910` | "I am /name, who has reached Lv. 120." (Cosmic's injected NPC) | "I am /name, who has reached Lv. 200." | **rule was right** — importing would have destroyed server content |
| `String.wz/Cash.img/5530001` | `name="MISSING NAME"`, `desc="MISSING INFO"` | "DS Medal Basket" | **rule cost us something** — a human should take v84's value |

### 4.3 The deny-list and the force-list — the gate is not enough on its own

The additive-only gate only refuses paths that **already exist**. It is therefore structurally
blind to a harmful v84 *addition*, and `conflicts.txt` is silent on every one of them by
construction. Ticket 03c found three, all of which the tool used to write without comment:

| hazard | rows | why |
|---|---:|---|
| `Etc.wz/NpcLocation.img/9901910`–`9901919` | 10 | `PlayerNPC.java:66` — `9901910`–`9906599` is a range the **server allocates from at runtime**. Writing fixed world placements onto it puts a static NPC on ids the server hands out. |
| `String.wz/MonsterBook.img/<mob>/reward` | 17 roots | `reward/<n>` is the n-th **slot of a positional array**, not an identity. Of 689 v84 reward slots, 653 collide and 36 do not — those 36 append onto 17 Cosmic drop lists, giving Cosmic's entries at indices 0–22 and Nexon's at 23–28. A list neither vendor ever shipped. |
| `Npc.wz/9000021.img` | 1 | 24 of its nodes refuse as `unsupported shape: parent=WzUOLProperty`; the rest merge, leaving the NPC half-v83/half-v84 — worse than either whole version. |
| `Map.wz/Map/Map{1,2}/…/portal/<n>[/<field>]` | 12 | Ticket 08. Same positional-array hazard, on portals players use today: v84 reindexed four arrays, so index `n` names a different portal in each tree. Ten are the `<n>/<field>` shape the gate passes. One would attach a non-existent script to the working portal into Ludibrium Toy Factory. Since 03g the tool refuses this **shape** as well — see 4.4 — but the rows stay listed, because a deny row records the decision and the reason. |

**`--deny` is therefore mandatory on every `merge` and every `xml` run, dry runs included** (the
dry run is what you read before deciding, so a dry run without it produces the wrong decision).
The tool refuses to start without it.

```
--deny docs\wz-baseline\merge-lists\COLLISION-DENY.txt        40 roots
--force docs\wz-baseline\merge-lists\COLLISION-FORCE.txt      37 roots, OPTIONAL
```

Both files are `<path>\t# <reason>`, blanks and `#` ignored, one parser. **Every listed path is a
ROOT**: nothing at or beneath a deny root is written — which is how 17 `reward` parents cover all
36 at-risk slots with no wildcard syntax — and everything beneath a force root may be overwritten.
A manifest row that is a copy root *containing* a deny root is refused too, because there is no way
to write part of it. **Deny beats force, and a path in both lists is a hard exit 2**, not a silent
resolution: an overlap means two decisions contradict each other.

**`--force` is the only way past the additive-only gate.** Without it nothing existing is ever
overwritten. With it, the tool removes the existing node and writes v84's in its place, prints
`FORCE <path> (authorised overwrite …: <reason>)`, and counts it in the summary line. On the XML
side the replacement goes back at the **same line position**, so the git diff is N insertions /
N deletions in one place. The 37 committed force rows are exactly the ids whose live value is the
literal placeholder `MISSING NAME` / `MISSING INFO` — including the twelve `Eqp/Dragon` names and
Evan's Mir and saddles, which additive-only cannot repair and tickets 04 and 13 need.

**The 40 deny rows are not of equal confidence and the tool cannot tell you that.** The 10
`NpcLocation` rows are hard evidence; the 17 `reward` roots and ticket 08's 12 portal rows are a
mechanical consequence; `Npc.wz/9000021.img` is a judgement call made on the operator's behalf,
marked as such in the file, and is the one to revisit if the tool ever learns to write through a
UOL parent.

### 4.4 Positional arrays — the index is not an identity, and the tool now says so

**A WZ container whose children are all integers forming ONE CONSECUTIVE RUN is an array, and its
child names are POSITIONS, not ids.** `portal`, a map layer's `obj`, a map's `foothold` groups,
`Back/<set>.img/back`, a monster-book `reward`, an item box's `reward`, an equip animation's frames
— all of them. Two trees can hold the same entry at different indices, and then **index `n` means a
different thing in each tree**. Nothing in 4.1–4.3 can see that: the row does not collide, it lands
in the wrong place.

**The run does not have to start at 0** (03i). It said `0..c-1` until then, and that clause let two
rows through on `Character.wz/Glove/01082262.img` — `swingT2` is `{1,2}` in the live client and
`swingO3` is `{1}`, so the gate did not see an array at all while refusing six siblings of the same
shape on the same item. It also misses every `foothold` container in `Map.wz`, which v84 numbers
from 1: 14 such rows sit in `add-list/Map.txt` today, refused now, unrefused before.

**Two clauses did not change, and they are what stop it over-refusing:**

- **every** child must be an integer. A map `.img` has layers `0`–`7` *alongside* `info`, `portal`,
  `foothold` and `life`, so a map image is not an array — otherwise every row writing into a layer
  would be refused, including the six appends ticket 08 correctly merged.
- the run must be **consecutive**. Dropping this would make every id container in `String.wz` an
  array — `Consume.img`'s children are 2,290 integer item ids with enormous gaps — and refuse 501
  legitimate name rows. **The cost is a stated blind spot:** a genuinely sparse array such as
  `Glove/01082262.img/swingOF` = `{0,3}` or `Quest.wz/Check.img/4940` = `{0,1,4961}` reads as "not
  an array". Nothing structural separates those from an id table. The deny-list is what stands in
  front of them, as it does for `Exclusive.img` in 4.5.

Two shapes, and the second is the dangerous one:

| add-list row | what the additive gate sees | what actually happens |
|---|---|---|
| `<img>/portal/15` | new child, write it | appends — safe *only* if the arrays agree up to 15 |
| `<img>/portal/4/script` | parent `4` exists, leaf `script` does not, write it | **writes onto whichever portal sits at index 4 in the target** |
| `Check.img/28266/0/lvmax` | parent `0` exists, leaf `lvmax` does not, write it | **the indices line up perfectly and it is still wrong** — it adds a field to a record the target already has, changing what that record means (ticket 09: 108 working quests capped at Lv.40) |

**That third row is why the refusal message names two hazards and not one.** The interior-write
refusal covers "the source's slot `n` may be a different entry than this tree's" *and* "the slot is
the same entry and the row edits it". They need different investigations and only the first can be
cleared by comparing arrays. An operator who checks the indices, finds them identical and overrides
on that basis has disproved half the reason. Ticket 09's 108 `lvmax` rows are the worked example:
`Check.img/<id>` holds exactly `0` and `1`, so the gate refuses all 123 of 09's
`Check.img/<id>/<step>/<field>` rows structurally — measured, `added 9, refused 123`, exit 3 — but
every one of those refusals is about *what the field does to the record*, not about the index.

`WzMerge merge` and `WzMerge xml` both enforce this now, and report it as
`POSITIONAL ARRAY: …` in `conflicts.txt` — deliberately distinct from `already exists in target`,
so "this index would land on a different entry" is never read as "the target already had it". A row
is refused unless it is a **pure append**:

- the row must name the array's **last** segment (a row that writes a field *into* an existing slot
  is always refused — the slot exists, so it is one of the target's own entries);
- the index must be **greater than the array's highest index as it was before this run** (a
  `deny`/`force` decision, not the tool, is how you take one anyway). An index *below* the array's
  **lowest** index is refused as a **PREPEND** with its own message: a source that numbers the
  container from a different origin than the target is not aligned with it at all;
- every index between that highest one and this one must also be somewhere in the same manifest **and
  must actually land**, or the array would be left with a **hole**. `deps` only emits the assets a map
  *references*, so it will hand you `Obj/effect.img/quest/gate/7` without the `gate/6` that v84 added
  beside it. Add the missing sibling from `add-list/`; do not work around the refusal;
- and the appended entry must not be **content-identical to one the array already holds**. That is
  the case an index check alone cannot catch: if v84 *inserted* an entry earlier in the array, every
  later slot is the target's own content shifted one place and the last one looks like new
  material. `Map/Map2/220000300.img/portal/15` is exactly that — byte-identical to the live
  client's `portal/14`, because v84 inserted `scr00` at index 4.

#### 4.4.1 "and must actually land" — the partial-refusal hole (T23) `[FACT-measured]`

**A manifest row is a wish, not an outcome.** Until ticket 23 the gap clause above consulted the
*manifest*: if slot `k` was listed, slot `k+1` counted as continuous. Every index was therefore
judged against the **baseline** child count, independently of what the run had already refused.

`UI.wz/MapLogin.img/back` held exactly `0..47`. A merge asked for `48`–`54`. The
content-identical clause correctly refused `48`–`52` (v84 had inserted five entries earlier, so
those source slots are the target's own `0`–`4` shifted). `53` and `54` then read as clean
appends — `53 >= 48` against the baseline, and `48`–`52` were "in the manifest". Output:
`{0..47, 53, 54}`. `verified OK`, exit 3, `WzMerge guard` rc=0, **and the client died before the
login screen**. A partial fill is worse than no fill.

Two changes, and both are in the tool now:

- **The gate is evaluated against the RUNNING state.** Every refusal — deny-list, `MISSING IN
  SOURCE`, `already exists`, the array gate itself — records the refused slot, and any later slot
  of the same container is refused with `…slot k was REFUSED earlier in this run…`. Because
  add-list rows are sorted as *text* (`back/10` before `back/9`), a refusal can also arrive *after*
  an append that depended on it: a **continuity sweep** runs after the manifest and before
  `SaveToDisk`, finds any granted slot sitting above a hole, and **undoes** it (`UNDONE` in
  `conflicts.txt`). Either way the rule holds: *if any index of an array is refused, every later
  index of that array is refused too.*
- **`guard` asserts continuity on the finished file** — see 4.4.2.

Measured on the real trees (`wz-data\v84\UI.wz` -> the live `UI.wz`): the seven-row `back/48..54`
manifest is now `added 0 (forced 0), refused 7`, exit 5. Reversed into `54..48`, the sweep fires:
`HOLE in 'UI.wz/MapLogin.img/back' … slot(s) 53,54 sit ABOVE the hole and are being UNDONE`,
`added 0`, exit 5. The known-good 46-row 11h UI merge is unchanged: `added 46, refused 0`, exit 0.
`WzMerge selftest` reproduces both halves and fails if either regresses.

#### 4.4.2 `guard` asserts positional-array continuity

`WzMerge guard <outWz>` used to answer one question — *may I write here?* — and it still does, with
no flags, for a path that does not exist yet. **Once a file exists at that path it also asserts that
no positional array in it has a hole**, and exits **4** if one does. That is the net: a holed array
parses, resolves and content-digests perfectly, so neither `verify` nor the post-write check sees it.

`--baseline <the pre-merge target>` is **required** when the file exists, and it is not a
convenience. A holed array cannot be recognised on its own: the client tree itself is full of
integer containers with gaps that are perfectly legitimate id tables (UI.wz alone has nine —
`ChatBalloon.img/pet` is missing 16 of 52, `NameTag.img/medal` 43 of 125). So guard prefilters on
shape and then asks the baseline the *gate's own* question — **was this container a consecutive run
there?**

- not a consecutive run in the baseline -> never an array, it is an id table; adding an id widens
  its gaps legitimately. Discounted. (Without this, guard refuses the known-good 11h merge, which
  appends medal ids `96` and `124` to a container whose highest id was `87`.)
- a consecutive run in the baseline, holed now -> **this file broke a real array.** Refused.

```
WzMerge guard <stage>\<T>\UI.wz --baseline <stage>\<T>\pre\UI.wz
```

Measured against the reproduced broken file: `HOLED ARRAY MapLogin.img/back: missing 5 index(es) —
48,49,50,51,52 — the baseline held the consecutive run 0..47, and this file breaks it`, exit 4,
with all nine gapped id tables discounted. The same command on the known-good 11h output: exit 0.

**The refusal is structural, so it is not a substitute for reading the data.** When it fires,
dump both arrays and compare them **by name, not by index**:

```
WzMerge dump D:\games\MapleStory\Map.wz          Map/Map2/220000300.img/portal 2
WzMerge dump <v84>\Map.wz                        Map/Map2/220000300.img/portal 2
```

Then either re-author the row against *this* tree's index (a hand-authored node, not a merge — the
tool only ever copies from the source `.wz`) or put it on the deny-list with the reason. Ticket 08's
twelve are on `COLLISION-DENY.txt` (Hazard 2b) and 03i's two glove rows beside them (Hazard 2d); the
six 08 merged are pure appends and are in the composed `Map.paths.txt`. `merge-lists/08/ROUTE-ROWS.md`
is the worked example, including the cost of refusing: three staged areas have no route in.
`03-verification/positional-array-gate.md` is the measured proof, including the 68-parent
classification that shows the 03i widening changes exactly two rows and nothing else.

**One thing the refusal message says that is easy to misread as a contradiction.** Hazard (b) —
"this row adds a field to a record the target already has" — describes something the same run
*permits* elsewhere: `String.wz/Npc.img/1063018/d0` is also a field added to an existing node, and it
merges. The difference is not the shape. A named node can be checked by reading its name; an array
slot has only a position, so **you cannot tell which record you are editing without dumping it.**
That sentence is in the refusal text for the operator who spots the apparent contradiction and would
otherwise override on it.

**The XML side enforces the same rule with one gap**: it is a line-text scan, so it can see the
container's child names but cannot digest two nodes and compare them. It catches the interior write,
the occupied slot and the hole; it does **not** catch the content-identical append. The deny-list is
shared by both subcommands, so denying the row is what closes that gap on both sides.

### 4.5 An empty `conflicts.txt` is not evidence of safety

**Read this before writing "0 refused" in a ticket as though it meant anything.** The additive-only
gate refuses a row when the *leaf* already exists. Every hazard this project has found so far was a
row whose leaf did **not** exist, so the gate was silent by construction and `conflicts.txt` was
empty or header-only while the merge quietly changed content the live client already ships:

| found in | shape | `conflicts.txt` said |
|---|---|---|
| 03c, `String.wz/MonsterBook.img/<mob>/reward/<n>` | appends Nexon's drop slots onto Cosmic's rewritten lists | nothing |
| 08, `Map.wz/…/portal/<n>/<field>` | attaches a field to a portal players use today | nothing |
| 09, `Quest.wz/Check.img/<id>/0/lvmax` | adds a start requirement to 108 working quests | nothing |
| 09, `Quest.wz/Exclusive.img/{0,1,2}` | v84's re-partition merged *beside* the live one | nothing |

The rule that follows, and it is the through-line of all four: **a row that writes into a node the
live client already has is an EDIT, not an addition, and no clean run tells you which edit it is.**
Dump both sides of the row's parent before merging it. Depth is a usable tell inside one file — in
`Quest.wz` anything deeper than `<Img>.img/<id>` is by definition inside an existing quest — but it
is a tell, not a check.

**And one shape has no structural check at all, deliberately.** `Quest.wz/Exclusive.img` groups
medal ids; the live client uses one *named* group `medal`, v84 *replaced* it with numeric groups
`0`/`1`/`2` holding a different partition of the same ids. Those are genuine additions — no
collision, and the container is not an array (its only live child is named), so the positional gate
does not look at it — and merging them yields an image carrying both partitions with seven ids in
two mutually-exclusive groups. **No depth, index or subset rule sees a semantic replacement; only
dumping both sides does.** A heuristic that guessed at it — "refuse integer children added to a
container that has only named ones" — would refuse legitimate content and teach an operator to
override, which is worse than the gap. `Quest.wz/Exclusive.img` is therefore denied as a whole image
on `COLLISION-DENY.txt`, and **that list, not the tool, is what stands in front of this class.**

---

## 5. The run book

In order, per `.wz` file. `<stage>` = `D:\games\MapleStory\Server\wz-merge`, `<v84>` =
`D:\games\MapleStory\Server\porting-resources\wz-data\v84`, `<T>` = your ticket number,
`<lists>` = `docs\wz-baseline\merge-lists`, `<adds>` = `docs\wz-baseline\add-list`.

**Everything you write lives under `<stage>\<T>\`, including your `pre\` snapshots. You share no
directory with another ticket.** More than one ticket can be in flight at a time and two of them
touching the same `.wz` is normal; per-ticket `pre\` plus the `--live` hash check below is what
stops the second one from silently reverting the first.

### 5.0 Confirm the backup covers what you are about to change

```
certutil -hashfile D:\games\MapleStory\<Name>.wz SHA256
certutil -hashfile D:\games\MapleStory\Server\_backup\client-v83-EzorsiaV2-2026-08-15\<Name>.wz SHA256
```

Equal, for every file the ticket touches. **If a file is not in the backup, stop and back it up
before going further.** Record both hashes in your ticket.

### 5.1 Snapshot the pre-merge tree — into YOUR ticket directory

This copy is the target of the merge *and* the "before" side of every check in section 6. It must
exist before anything is written; nothing reconstructs it afterwards.

```
mkdir <stage>\<T>\pre
copy D:\games\MapleStory\<Name>.wz <stage>\<T>\pre\<Name>.wz
certutil -hashfile <stage>\<T>\pre\<Name>.wz SHA256      # must equal 5.0
```

**Take it as late as you can** — right before 5.2, not at the top of the ticket. It is a snapshot
of a file another ticket may install over, and the older it is the more likely 5.4 rejects it.
If 5.4 says `STALE SNAPSHOT`, another ticket installed in the meantime: re-take this copy, then
**re-read your dry run**, because the collisions may have changed.

### 5.2 Dry run, and read the conflicts

```
WzMerge merge <v84>\<Name>.wz <stage>\<T>\pre\<Name>.wz - <pathsFile> <stage>\<T>\<Name>.dry.conflicts.txt --deny <lists>\COLLISION-DENY.txt
```

(Every command in this document is one line. It is written for **PowerShell**, where the line
continuation is a backtick and `^` is not one — do not break these across lines with `^`.)

Add `--force <lists>\COLLISION-FORCE.txt` **only** if your ticket has decided to adopt v84 values
for listed ids (4.3). Without it nothing existing is overwritten, which is the safe default.

**Do this before every real merge.** It is the cheapest step in the pipeline and the only one that
tells you what you are about to lose. Exit 0 = nothing collides. Exit 3 = read the file; for every
row decide **drop it**, **re-id it**, or **take v84's value by hand**, and record the decision in
your ticket. Exit 5 = the target already has everything you asked for, or you are pointed at the
wrong file — do not read that as "nothing owed" without checking which. Do not proceed with unread
conflicts.

`conflicts.txt` now carries two kinds of row and they mean different things:
`already exists in target` is the additive-only gate (a v84 *edit* you are dropping), and
`DENIED by deny-list [...]` is a v84 *addition* that must not be written and needs no further
decision from you — 03c already made it, and the reason is quoted inline.

Seconds, for a property-level manifest — only the images a listed path walks through get parsed.
**A manifest row naming a whole `WzDirectory` is the exception:** that row `DeepClone`s the entire
subtree into memory during the dry run too (`Skill.wz/Dragon`), so it is neither instant nor cheap,
and it is memory-bound. If a larger directory ever OOMs, expand that row into per-image rows.

### 5.3 If you are merging maps: resolve asset references first

**The ordering rule (section 7) covers manifest parent/child only. It does not see asset
references, and a map is nothing but asset references.** A map `.img` names everything it needs by
*name*, with no textual connection to the path it lives at:

| in the map | resolves to |
|---|---|
| `back/<n>/bS` | `Map.wz/Back/<bS>.img` |
| `<layer>/obj/<n>/{oS,l0,l1,l2}` | `Map.wz/Obj/<oS>.img/<l0>/<l1>/<l2>` |
| `<layer>/info/tS` | `Map.wz/Tile/<tS>.img` |
| `info/bgm` = `Bgm14/DragonRider` | `Sound.wz/Bgm14.img/DragonRider` — **a different `.wz`** |
| `info/mapMark` = `Leafre` | `Map.wz/MapHelper.img/mark/Leafre` |
| `info/link` = `240080100` | another whole map image, with references of its own |

Merge a map without these and the client renders it broken, drops its background silently, shows a
blank world-map marker or plays no music. Real for ticket 06: `Map/Map2/240080000.img` references
`Obj/dungeon3.img/skyValley`, absent from v83 — and it also draws five new animation frames inside
`Back/dragonRoad.img`, an image v83 *does* have. **`info/link` matters just as much: 8 of ticket
06's 21 maps are pure link stubs whose entire layout lives in the target map.** Repo-wide, 2,266 of
5,262 maps use `link`.

Run this once per map image you intend to merge. **Pass the bare map id** — `deps` finds its bucket
itself, which is what "`Map/Map2/`" in the old version of this document got wrong for ticket 07's
`Map6` maps:

```
WzMerge deps <v84>\Map.wz <id> <adds>  > <stage>\<T>\<id>.deps.txt
```

The output **is a paths file**, already at manifest granularity, already including the map image
row(s) themselves. `deps` cross-references every reference against the add-lists, so what it prints
is what is actually owed, at the depth the manifests hold it — `Map.wz/Back/dragonRoad.img/ani/20`
and its nine siblings, not the whole-image row `Map.wz/Back/dragonRoad.img` that the merge would
just refuse. A reference the live client already satisfies is printed as a comment
(`# already in v83, nothing owed: …`), so the file is safe to feed straight in.

**Check the exit code.** `deps` exits 1 if the id has no `.img` under `Map/*` or if a link target
does not exist. When that happens it prints its rows **commented out**, so the file you just
redirected into holds no manifest rows at all and the merge that reads it exits 2 rather than
merging an incomplete list. Fix the cause and re-run; do not uncomment them. And note what that
failure used to do: the shell truncates the redirect target *before* `deps` runs, so a failed
`deps` left a zero-byte file and the merge that read it reported `added 0, refused 0`, exit 0 —
"nothing owed". A zero-row paths file is now exit 2 everywhere.

Then dry-run the deps file the same way as any other manifest:

```
WzMerge merge <v84>\Map.wz <stage>\<T>\pre\Map.wz - <stage>\<T>\<id>.deps.txt <stage>\<T>\<id>.deps.conflicts.txt --deny <lists>\COLLISION-DENY.txt
```

- `ADD …` → owed, and the row belongs in your real paths file.
- `SKIP … already exists in target` → the client already has it.
- `SKIP … row is rooted at Sound.wz, not Map.wz` → **a genuine cross-file dependency.** `deps`
  groups its rows under `# ==== <Name>.wz ====` banners; take that group to that file's own merge.

Worked example, verbatim, in `03-verification/safety-guards.md` §G9.

**`deps` output can be one row short of a whole array, and 4.4 is where you find out.** It emits the
assets a map *references*, so if v84 appended `Obj/effect.img/quest/gate/6` and `/7` and the map only
draws `7`, that is the only row you get — and merging it alone leaves a hole in the array. The
positional-array gate refuses the row and names the missing index; take the sibling from
`add-list/`, do not route around the refusal. Same section for what to do when a row targets a
`portal/<n>/…` or `<layer>/obj/<n>/…` slot on a map the live client **already has**: those are the
rows that quietly land on the wrong entry, and `merge-lists/08/ROUTE-ROWS.md` is the worked example.

`deps` does **not** resolve mob, npc, reactor or portal-script ids — still a deliberate scope cut,
but state it to yourself honestly rather than reading the banner as an all-clear: **a v84-only mob
id placed in a v84 map means the live client has no sprite for it.** Check the ids under the map's
`life/` against `add-list/{Mob,Npc,Reactor}.txt` by hand.

### 5.4 The real merge

```
WzMerge merge <v84>\<Name>.wz <stage>\<T>\pre\<Name>.wz <stage>\<T>\<Name>.wz <pathsFile> <stage>\<T>\<Name>.conflicts.txt --deny <lists>\COLLISION-DENY.txt --live D:\games\MapleStory\<Name>.wz
```

`--live` is **required** for a real merge and is checked before anything is opened: the tool
SHA-256s `<targetWz>` and the live file and refuses with exit 2 if they disagree. It must be the
**live client's** file — pointing it at the target is refused too, since comparing the target with
itself proves nothing. See 5.1 for what to do when it fires.

Expect `verified OK -> …` and exit 0 (or 3, if you accepted collisions at 5.2). **Exit 4 means the
output is bad:** it is left as `<Name>.wz.partial` and was not promoted. Do not install it, do not
rename it — find out why (disk, memory, an interrupted run, or a `CONTENT DRIFT` line) and re-run.
**Exit 5 means nothing was added at all** — read `conflicts.txt` before concluding anything.

Before promoting, the tool re-opens what it wrote and checks three things: every path you asked for
re-resolves; every image in the file parses; and **every image it inserted into digests the same on
disk as it did in memory** (`content OK <image> <sha>`). That last one is the check that would
catch a corrupted canvas payload, which path re-resolution and `ParseImage` both walk straight
past. A whole-`WzDirectory` manifest row (`Skill.wz/Dragon`) is not content-checked and the tool
says so on the line above the save.

### 5.5 Verify — section 6. Do not skip it.

### 5.6 Server XML

`wz/` in this repo is the server tree; `launch.bat` passes `-Dwz-path=wz`. Nothing needs
regenerating — `WzMerge xml` edits the `.img.xml` files in place.

```
WzMerge xml <v84>\<Name>.wz wz <pathsFile> <stage>\<T>\<Name>.xml.dry.conflicts.txt --deny <lists>\COLLISION-DENY.txt -
WzMerge xml <v84>\<Name>.wz wz <pathsFile> <stage>\<T>\<Name>.xml.conflicts.txt     --deny <lists>\COLLISION-DENY.txt
git diff --stat wz/
```

`--deny` is required here too, and `--force` behaves exactly as on the binary side. **If your
ticket forces a `String.wz` row, force it on BOTH sides** — the client reads the binary `.wz` and
the server reads this tree, and forcing only one leaves the two disagreeing.

The splice is a **text insert**, not an XML round-trip: the fragment goes in at sorted position
among its parent's children and every other byte of the file is untouched. Expect
`N insertions(+), 0 deletions(-)` and nothing else — the tracer's was `19 insertions(+), 0
deletions(-)` across two files. A forced row is the one exception: it replaces the existing element
**in place**, so it reads as N insertions / N deletions at one spot. Any *other* deletion, or a
reformat of lines you did not add, means stop and `git checkout -- wz/`.

**One caveat on that rule, because ticket 09 hit it and it is not a fault.** `git diff --stat` can
report hundreds of deletions on a large insert into a big, highly repetitive XML file — 09's
`Check.img.xml` showed `2076 insertions, 232 deletions` and `Say.img.xml` `1741, 583` — and **not
one line was actually removed**. Git re-anchors its hunks; every "deleted" line is re-added verbatim
in the same hunk. **The stat line does not discriminate; "is every old line still present" does.**
Before concluding either way, run the check that answers the question:

```
git show HEAD:wz/<Name>.wz/<Img>.img.xml > pre.xml       # CR-normalise both, then:
comm -23 <(sort -u pre.xml) <(sort -u wz/<Name>.wz/<Img>.img.xml) | head
```

Empty output means nothing was lost. Confirm it with the structural count too — the top-level
`<imgdir name>` set should have gained exactly your manifest's ids and lost none. A stat line with
deletions and an empty `comm` is a diff artefact; a non-empty `comm` is a real loss, stop.

Each written file is read back and compared with what was meant to be written; a mismatch is
**exit 4** and the message says the tree is half-applied. `xml` writes file by file, so an
exception mid-run (exit 1) leaves a partially-updated tree as well. Both recover the same way:
`git checkout -- wz/`, then re-run. That is why the XML side has no staging directory — git is the
staging directory.

**Dry-run limit:** the XML dry run re-reads each `.img.xml` from disk per row and never writes, so
two manifest rows adding the *same* name to the same file both report `ADD`. A real run catches the
second. Diff-tool-generated manifests never contain duplicate rows.

### 5.7 Install — the only write to `D:\games\MapleStory\`

Client closed. One file at a time, checking the size after each.

```
copy <stage>\<T>\<Name>.wz D:\games\MapleStory\<Name>.wz
dir D:\games\MapleStory\<Name>.wz
```

**If you are interrupted partway through a multi-file ticket** — ticket 06 touches roughly six `.wz`
files and six XML trees — the client is in a mixed state: some files v83+v84, some pure v83. That is
*usually* survivable, because additive-only means every old id still resolves — but a map installed
without its `Map.wz` scenery sets is not. Record which files you copied. To return to a known state,
restore **every** file the ticket touches from the backup:

```
copy D:\games\MapleStory\Server\_backup\client-v83-EzorsiaV2-2026-08-15\<Name>.wz D:\games\MapleStory\<Name>.wz
```

and on the server side `git checkout -- wz/`. Both undos are all-or-nothing; there is no
half-rollback, which is why 5.0 is mandatory.

### 5.8 Server-side check

`src/test/java/server/V84TracerNodeTest.java` reads the imported nodes through `XMLWZFile` /
`XMLDomMapleData` — the exact classes the running server uses. **Extend that class with your own
ids rather than building a second harness.**

Tickets 04–08 each landed a sibling class instead, to avoid four agents editing one file
mid-flight. That is a defensible call for the test *bodies*; it was not one for the two-line
`wz(String)` helper, which ended up copied verbatim five times. **It now lives once, in
`src/test/java/server/V84Wz.java`** — `import static server.V84Wz.wz;` and do not re-copy it.

```
./mvnw -o test -Dtest=V84TracerNodeTest
```

Two landmines it already works around, both worth knowing:

- **`WZFiles.DIRECTORY` is `static final`, resolved once per JVM**, and `MobSkillFactoryTest` points
  `wz-path` at a `@TempDir` before it. Whichever test class runs first wins for the entire surefire
  fork. Any test reading the real tree through `WZFiles`/`DataProviderFactory` is therefore
  order-dependent — construct `new XMLWZFile(Path.of("wz", "Item.wz"))` explicitly instead.
- **Do not put `ItemInformationProvider` in the committed suite.** Its constructor reads the
  `monstercarddata` table, and `DatabaseConnection.getConnection()` throws `IllegalStateException`
  — not `SQLException`, the only thing it catches — when the pool is uninitialised. Initialising it
  costs `INIT_CONNECTION_POOL_TIMEOUT` (90 s in `config.yaml`) on a machine without a database
  before it can even be skipped, and on a machine with one it leaves the static `dataSource` set for
  the rest of the fork. The provider-layer assertions cover the same nodes through the same reader.
  Ticket 03 ran the `ItemInformationProvider` path once by hand against a live MySQL and recorded
  the result; the rationale and the re-run recipe are in the test file's closing comment.

---

## 6. Verification — what actually proves something

Full reasoning and verbatim output: `docs/wz-baseline/merge-lists/03-verification/`
(`blocksize-invariance.md`, `safety-guards.md`, `gate-fires.md`).

### 6.1 The content digest — the check that can fail

```
WzMerge hash <stage>\<T>\pre\<Name>.wz <image/path>  >  pre.hashes.txt
WzMerge hash <stage>\<T>\<Name>.wz     <image/path>  >  post.hashes.txt
fc.exe /N pre.hashes.txt post.hashes.txt
```

**`fc.exe`, with the extension.** In PowerShell bare `fc` is the alias for `Format-Custom`, which
happily formats two file *names* and reports nothing — the check silently cannot fail. Use
`fc.exe`, or `Compare-Object (Get-Content pre.hashes.txt) (Get-Content post.hashes.txt)`.

`<image/path>` may be written either way — `String.wz/Consume.img` or `Consume.img`. `hash`,
`dump` and `deps` accept manifest form as well as root-relative form; only `merge`, `xml` and
`verify` require manifest form.

for each image a node was inserted into — i.e. each `modified-list` row from 6.2. One SHA-256 per
direct child of the image, over its **decoded** values: scalars by parsed value, canvases by the
SHA-256 of their compressed pixel bytes.

**Expected: the only differing lines are the ids you added, plus the `TOTAL` line.** Every other
child must be digest-identical. Tracer result: 55 of 56 children of `Item.wz/Consume/0200.img` and
2,290 of 2,291 children of `String.wz/Consume.img` identical.

**The merge now runs this same digest itself**, in memory before the save and again off the written
file, and refuses to promote the output on a mismatch (5.4). 6.1 remains worth running because it
compares against the *pre-merge* file and names which children moved — the tool's own check only
proves the write is faithful to what the merge built.

**Three things `hash` does not prove. State them where you use it, not in a footnote.**

1. **Sibling ORDER is normalised away.** `Canon()` walks children `OrderBy(Name, Ordinal)`, so a
   container whose children were reordered — same names, same values, different sequence — digests
   identically before and after. **Nothing in this project has ever proved that order was preserved
   inside a merged container**, and every "unchanged" in every ticket and in `REGRESSION.md` means
   "unchanged up to sibling order". For a keyed lookup (which is how both the client and
   `XMLDomMapleData` read these trees) that is fine; for a positional array it would not be, and
   4.4 is what stands in front of *that*. The one reorder actually observed — quest `28326` moving
   inside `Check.img.xml` when the sorted insert relocated it — was found in the server XML text,
   not by this tool. If order ever matters, diff `WzMerge dump` output, which prints in tree order.
2. **A `WzUOLProperty` is digested as its LINK STRING, not as what it points at** (03i). Following
   the link is what made `hash` stack-overflow on `Reactor.wz`: `1050000.img/0/hit/2` is a UOL back
   to its own ancestor `0`, and `Kids()` on a UOL returns the *resolved target's* children, so the
   walk ran `0 -> hit/2 -> 0` forever, branching twice per level, until `0xC00000FD`. Six images
   did it, symmetric pre and post, which an operator could easily read as merge damage. The link
   target is digested at its own path in the same pass, so nothing is lost — but a change *behind*
   the link does not move the UOL node's own digest.
3. **`Canon()` stops at depth 64** and writes a `DEPTH LIMIT` marker line into the digest if it ever
   gets there. That is a backstop for a cycle of some other shape, not the fix for the one above;
   the marker is inside the hashed text, so a truncated subtree can never digest equal to an
   untruncated one.

This is the important check. The merge sets `Changed = true` on exactly the image it inserted into,
so that image is the *only* one decoded, re-serialized and re-encrypted under the target's IV, and
therefore the only place a serializer bug can live. Also compare the added node against its v84
source (`WzMerge hash <v84>\<Name>.wz <image/path>`): for the tracer the digests match exactly,
PNG payloads included — the version trap proven on *content*, not just on headers.

### 6.2 The diff-tool run — additions, deletions, unexpected re-serialization

Ticket 02's diff tool doubles as the merge checker. It takes **four directories, positionally**, and
`SUMMARY.md` always labels its columns `v83-stock` / `v84` / `live client` regardless of what you
pass:

```
dotnet run -c Release --project docs/wz-baseline/tool -- <outDir> <v83Dir> <v84Dir> <liveDir>
#                                                        ^scratch  ^pre      ^post    ^pre again
dotnet run -c Release --project docs/wz-baseline/tool -- <stage>\<T>\diff <stage>\<T>\pre <stage>\<T> <stage>\<T>\pre
```

Note this compares against **your** `pre\`. That is only meaningful because 5.4 proved your `pre\`
still matched the live file at merge time; against a shared, stale snapshot this diff reports clean
on a merge that reverts another ticket.

Use an `<outDir>` **outside `docs/wz-baseline/`** or the run overwrites the committed manifests.
Then read exactly three things in `<outDir>`:

| file | expectation |
|---|---|
| `add-list/<Name>.txt` | **exactly** the paths you asked for — no more |
| `removed-list/<Name>.txt` | **empty** |
| `modified-list/<Name>.txt` | **only** the image(s) you inserted into |

A `modified-list` row for any other image means an image was re-serialized that should not have
been. That is a real failure this can catch.

**Ignore `modified-list/<Name>.live.txt` and the `protect` column.** With these roots the "live"
tree is the pre-merge tree compared with itself, so both are structurally empty/zero and carry no
information. They are kept only as a canary: non-empty means you passed the wrong roots.

**What the BlockSize numbers do *not* prove.** For every image except the one inserted into,
MapleLib copies the bytes verbatim out of the input and carries the recorded size across without
recomputing it (`WzDirectory.cs:353-357`), so pre-vs-post BlockSize compares a number with a copy of
itself. Verbatim memcpy is a *stronger* guarantee than equal size — but it is an argument from
MapleLib's source, not a measurement, and the older claim that every other image "survived a full
MapleLib repack byte-for-byte the same size" described something that never happened. 6.1 is the
measurement. The server XML has no BlockSize analogue at all; its guarantee is that the splice is a
text insert plus the refusals in 4.1.

### 6.3 The gate fires

Re-run the same merge against its **own output**. Every path now exists, so a working gate refuses
all of them:

```
  SKIP  Item.wz/Consume/0200.img/02001500  (already exists in target)
  added 0 (forced 0), refused 1                                         exit 5
```

(Exit **5**, not 3: "refused rows and added nothing" is its own code now. On this test it is the
expected result; anywhere else it means something is wrong with your arguments.)

Committed verbatim for both the binary and the XML side in `03-verification/gate-fires.md`.

### 6.4 Hash the output; re-check the client afterwards

```
certutil -hashfile <stage>\<T>\<Name>.wz SHA256          # record in the ticket
```

The merge is deterministic — twice from the same pre-merge copy gives byte-identical output
(`Item.wz` `115feac1…`, `String.wz` `d5721de2…`), and the XML splice is byte-identical across runs
too — so a merge can be re-run from the notes and checked by hash rather than by inspection. That is
what "repeatable by someone else" has to mean. After the ticket, every file you did **not** install
must still hash-match the backup; that is how "the live client was only changed where intended" is
*checked* rather than asserted.

---

## 7. Ordering rule, learned from the sweep

Import a parent before its children. `WzMerge` refuses `parent path absent in target` rather than
fabricating intermediate nodes — so a new `.img` inside a **new** directory needs the directory row
first. The add-list manifests are already collapsed to copy roots, which mostly makes this
automatic, but a hand-cut subset can break it.

**This rule is about manifest structure only.** It does not know that a map references a scenery
set, or anything else cross-file. For maps see 5.3 — that step is not optional and this rule is not
a substitute for it.

## 8. Trap 1 — the v84 version hash. Resolved structurally.

Measured, not folklore: `WzMerge dump` prints the patch version of any file.

```
D:/games/MapleStory/Item.wz                      iv=GMS  patchVersion=83
porting-resources/wz-data/v84/Item.wz            iv=GMS  patchVersion=84
```

The trap only bites if you open a **v84** file and save it. **We never do.** v84 is read-only
source; the file being written is the live client's own `.wz`, with nodes added into it. MapleLib's
`SaveToDisk(path)` with no override re-emits the IV and version hash it parsed off that file, so the
output is v83-encoded because its target always was. There is no conversion step to get wrong, and
therefore no menu option anyone can forget.

Verified: the merged `Item.wz` re-opens at `iv=GMS patchVersion=83` with the v84 node present and
its 358-byte / 283-byte icon payloads intact and byte-identical to the v84 source; 6.1 confirms the
same by content digest.

**Do not "re-save the v84 file at v83 and then copy from it."** That is the folk technique; it
converts 600 MB to convert two nodes and re-encodes content nobody diffed. Merge node-level into the
live file instead.

Corollary for anyone using HaRepacker by hand: its default write path is BMS, not GMS. Both tools
here try GMS → BMS → EMS on open, so a hand-saved file still loads, but it will no longer match the
client. Prefer the scripted path.

## 9. Trap 2 — `basedata`. Decision: re-export with base64 **off**. Never strip.

Cosmic's `wz/` XML was produced by `WzClassicXmlSerializer(indentation: 2, LineBreak.Windows,
exportbase64: false)`. Confirmed by inspecting `wz/Item.wz/Consume/0200.img.xml`: 2-space indent,
CRLF, no BOM, and `<canvas name="icon" width="27" height="30">` carrying dimensions and origin but
no pixel payload. The server never reads pixels — `XMLDomMapleData` only needs the node shape and
the scalar leaves.

So `WzMerge xml` constructs exactly that serializer and emits the fragment straight into the
existing `.img.xml`. **The 14 MB `2218.img.xml` problem never arises**, because base64 is never
generated in the first place. HaRepacker's GUI export with base64 on, followed by a stripping pass,
is the wrong shape: slower, lossy in a way nobody audits, and it rewrites files that should not
change.

## 10. The collision map for 04–09 — already measured

Full dry run of every add-list against the live client
(`docs/wz-baseline/merge-lists/addlist-dryrun-*.conflicts.txt`). **805 of the 16,177 add-list roots
are refused**, across all 16 `.wz` with an add-list — `UI.wz` included, which had no dry-run file at
all until ticket 03e produced one (it refuses **nothing**: 61 roots, 61 importable, which is what
"all 735 triaged" was quietly assuming without having measured it).

> **The figure moved 759 → 805 because the deny-list is now enforced** (4.3), and 46 of the
> difference is the point of it: 36 `MonsterBook` reward slots and 10 `NpcLocation` ids that
> previously sailed through as clean additions. `Etc.wz` 6 → 16 and `String.wz` 711 → 747 are those
> rows appearing for the first time. `Npc.wz` stays at 34 but 24 of them are reclassified from
> `unsupported shape` (a tool capability gap) to `DENIED` (a decision that has been made).

> **The 805 predates ticket 03g** and is a floor, not the current number: the deny-list gained 12
> rows and the positional-array gate (4.4) refuses a shape nothing used to count. Measured on the
> composed lists rather than the whole add-lists, it found 8 more — 6 in `Character.wz`, 2 in
> `Item.wz` — none of which appear in this table. Re-run the sweep before quoting the figure.

> Re-measured by ticket 02g after the diff tool's expansion went from 1 level to 3. The old figures
> — 41 collisions across 2,172 roots — were computed over manifests that could not see any node
> nested more than one level below a `.img`, which is where all of `String.wz/{Eqp,Etc,Map}.img`
> lives. Nothing about the merge rule changed; the manifests got bigger and truthful.

Read that precisely: it covers **manifest roots only**. A v84 change that lives *below* an existing
image — an edited property inside a `.img` that both trees have — is not an add-list row at all and
so cannot appear here. That class of change is what `modified-list/*.txt` is for, and it is a
separate read. This table says "of the things v84 adds, these are the ones whose id is already
taken"; it does not say "these are the only v84 changes additive-only will drop".

| file | roots | refused | what |
|---|---|---|---|
| `String.wz` | 1,579 | 747 | **689 are `MonsterBook.img/<id>/reward/<n>`: 653 already occupied, and the other 36 DENIED** — those 36 are the ones that would otherwise splice into 17 Cosmic drop lists (4.3). The rest: **30 in `Eqp.img`**, 11 `Npc.img` (the ten `990191x` names + 1), 10 `Cash.img`, 3 `Etc.img`, 2 `Ins.img`, 1 `Consume.img`. **`Map.img`: 125 new map names, 0 collisions — all importable.** |
| `Npc.wz` | 98 | 34 | `9901910.img`–`9901919.img` land **inside Cosmic's injected `99xxxxx` block** — ticket 08's biggest landmine; 03c's verdict is **drop**, because `9901910` is a server allocator base and live is a strict superset of v84 there. The other 24 are `9000021.img/{say,stand}/<n>/…`, now **DENIED** as one image: v84 rebuilt that NPC around UOL links, the merge cannot write through a UOL parent, and letting the remainder through leaves it half-v83/half-v84. |
| `Character.wz` | 438 | 6 | `Accessory/01142153.img`, `01142154.img`, plus `Dragon/019{4,5,6,7}2002.img/info/level` — the live client already ships Evan's dragon equips and v84 adds a `level` field to four of them. |
| `Etc.wz` | 10,634 | 16 | `Commodity.img/8941`–`8946`, cash-shop SNs already taken (keep local — see `COLLISION-TRIAGE.md`, "Settled from source"), plus the **10 DENIED `NpcLocation.img/990191x` rows**, which are additions and were being written silently. **10,459 of the roots are `Commodity.img/<sn>/Bonus`** — a field v84 adds to existing cash-shop entries. Real v84 data, but nobody should bulk-import it. |
| `Map.wz` | 601 | 2 | `WorldMap/WorldMap010.img/MapList/93`, `/94` — already present in the live world map. |
| `UI.wz` | 61 | 0 | clean. Still out of scope by ticket-03 decree (section 11), but now *measured* rather than assumed. |
| `Item.wz`, `Mob.wz`, `Quest.wz`, `Reactor.wz`, `Skill.wz` | 2,592 | 0 | clean |
| `Base.wz`, `Effect.wz`, `Morph.wz`, `Sound.wz`, `TamingMob.wz` | 110 | 0 | clean (02f's baselines; `Base`/`TamingMob` add nothing at all) |

**A refused row is not automatically a row you should force.** Ticket 02g compared every shared
`Eqp.img` id between the live client and v84: **589 names differ, and in all but 18 the live name is
the better one** (Ezorsia renamed all 507 faces; v84 calls them "Male Face 19"). The 18 that matter
are the ones where the live value is the literal placeholder `MISSING NAME`: all twelve
`Eqp/Dragon` ids (`1942000`–`1972002`, v84 "Silver/Gold/Reverse Mask/Pendant/Wings/Tail") and
`Accessory/1142143`–`1142151`. Same shape in `Eqp/Taming`: `1902040`–`1902042` and `1912033`–`1912035`
(Evan's Mir and its saddles) exist locally as `MISSING NAME` / `MISSING INFO`. **Those are ticket 04
and 13's blank labels, and additive-only will not fix them — they need a deliberate overwrite**,
which is what `--force <lists>\COLLISION-FORCE.txt` is (4.3). All of them are already on that list;
none of the 571 rows where the live name is better are.

The sweep also found the one shape the tool could not handle: `Skill.wz/Dragon`, a whole new
**directory** (Evan's dragon animations). Now supported via `WzDirectory.DeepClone()`, and the dry
run does exercise it — a dry run skips only the save, so the clone itself really runs. It is
memory-bound, since cloning a directory materialises every image beneath it; a dry run of `Skill.wz`
reports `added 14, refused 0`. What is **not** yet exercised is writing a cloned directory back out;
ticket 12 will be the first to do that, and it is the case most likely to hit the memory ceiling.

## 11. What is not covered

- **`UI.wz`.** Out of scope by ticket-03 decree and still is. Take `SkillEx` / `SkillMacroEx` only,
  never bulk. Its dry run exists now (`addlist-dryrun-UI.conflicts.txt`, 61 roots, 0 refused), so
  the decision is a scope decision rather than an unmeasured one.
- **Nodes nested 4+ levels below a `.img`.** The manifests expand 3 levels — deep enough for every
  id in this era, not deep enough for animation frames or foothold vertices. `WzMerge xml` itself
  has no depth limit any more (02g); the limit is what the manifests contain.
- **`Sound.wz/BgmGL.img` is unreadable by MapleLib in ALL THREE trees** (`WZ extended property
  exceeds its declared block`) — a library limitation, not a live-client defect, and symmetric, so
  it biases no manifest. Avoid; `deps` will name a `BgmGL` track it cannot help you with.
  **Since ticket 03f this no longer fails the merge**: post-write verification pre-scans the merge
  target and discounts, per image, only images that were *already* unparseable there, so a
  `Sound.wz` merge exits 0 instead of 4. An image that parses in the target and fails in the
  output still fails — that is the corruption the check exists for, and the discount is
  one-directional on purpose. `WzMerge verify <wz> <paths> --baseline <targetWz>` applies the same
  discount by hand. Before 03f, **every** `Sound.wz` merge stayed `.partial` and exited 4 no
  matter how correct the data was; ticket 06 hit it and correctly discarded the output.
  (`Base`/`Effect`/`Morph`/`Sound`/`TamingMob` **do** have stock baselines and add-lists now — 02f
  completed them. `Base.txt` and `TamingMob.txt` are 0-row files, which is why a 0-row manifest is
  a hard error rather than a silent success.)
- **Mob / npc / reactor ids referenced by a map.** `deps` resolves `Back`/`Obj`/`Tile`/`bgm`/
  `mapMark`/`link` but not these. **A v84-only mob id in a v84 map means the live client has no
  sprite for it** — check `life/` against `add-list/{Mob,Npc,Reactor}.txt` by hand.
- **Bit-rot inside a compressed pixel payload** — *partially* covered now. The merge digests the
  decoded content (canvases by the SHA-256 of their compressed pixel bytes) of every image it
  inserted into, before and after the save, and refuses to promote on a mismatch (5.4). What that
  does **not** cover: images the merge did not touch — MapleLib memcpy's those verbatim — and
  corruption that happens after the merge, on disk or during the copy in 5.7. The defence there is
  still the output hash (6.4).
- **A whole-`WzDirectory` manifest row is not content-checked** (`Skill.wz/Dragon`): there is no
  single image to digest. The merge prints which rows those were.
