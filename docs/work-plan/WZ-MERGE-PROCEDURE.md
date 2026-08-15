# WZ merge procedure — v84 content into the live v83 client + Cosmic's server XML

**Tickets 04–09 execute this document. Do not invent a second way.** Established by ticket 03
(tracer: item `2001500`, "Red Potion"), hardened by ticket 03b after a safety review, collision
figures re-measured by ticket 02g. Everything here was run end to end, not designed on paper; the
verbatim output is under `docs/wz-baseline/merge-lists/03-verification/`.

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
| **Server-side undo** is `git checkout -- wz/`. The XML side is in git; the binary side is not. |
| **MapleLib revision** | `HaRepacker-src` at `a7c38edf7c58e8a8b272af490c51113db76bff08`. Every safety claim below is a property of that source. Check with `git -C D:\games\MapleStory\Server\porting-resources\reference-sources\HaRepacker-src rev-parse HEAD`; if it differs, the file/line references in `03-verification/` need re-reading before you trust them. |

## 1. Staging — the only supported way to run a merge

**A merge never writes to the live client.** It writes into a staging directory, is verified there,
and is then *copied* into place by hand as a separate, interruptible step.

```
D:\games\MapleStory\Server\wz-merge\
  pre\        byte-identical copies of the live .wz files you are merging into
  <ticket>\   the merged output ("post")
```

Why this is a rule, not a preference — `WzFile.SaveToDisk`:

- **truncates the destination the instant it starts** (`WzFile.cs:675`), then spends the following
  minutes streaming unchanged images out of the *target's own open reader*
  (`WzDirectory.cs:353-357`). `Map.wz` is 629 MB.
- So an OOM (a `WzDirectory` manifest row `DeepClone`s an entire subtree into memory), a full disk
  or a Ctrl-C leaves a **truncated file that looks finished**.
- And its scratch file is relative to the working directory.

The tool enforces the discipline rather than trusting you to remember it. It refuses with **exit 2**,
before opening anything, if `<outWz>` is the target file, or the v84 source, or **in the same
directory as the target** — which is what aiming an output at `D:\games\MapleStory\` amounts to.
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
WzMerge merge  <sourceWz> <targetWz> <outWz|-> <pathsFile> <conflictsTxt>
WzMerge xml    <sourceWz> <xmlRoot>            <pathsFile> <conflictsTxt> [-]
WzMerge verify <wz> <pathsFile>
WzMerge hash   <wz> <path/under/wz>
WzMerge deps   <mapWz> <Map/MapN/<id>.img>
```

`<pathsFile>` is a manifest: lines exactly as `docs/wz-baseline/add-list/*.txt` writes them
(`Item.wz/Consume/0200.img/02001500`); `#` and blanks ignored. Feed it an add-list directly, or a
hand-cut subset (see `docs/wz-baseline/merge-lists/`).

**Dry run:** `-` in the `<outWz>` slot for `merge`, `-` as a trailing argument for `xml`. Every
check runs; nothing is written.

**Manifest root:** both `merge` and `xml` match the leading `<Name>.wz` of each manifest line
against the **source** argument, so renaming a staging copy of the target cannot break lookups.
`merge` prints a note when the target is named differently.

### Exit codes — check them; do not eyeball the log

```
0  every requested path was added (or, on a dry run, would be)
1  unexpected failure
2  bad arguments, or a staging guard refused them
3  completed, but >=1 row was REFUSED — read conflicts.txt
4  post-write verification failed — the output is NOT installable
```

`added 0, refused 21` is **exit 3**, not 0. A scripted 04–09 loop that only checks for zero will
stop on a file that imported nothing, which is the point. For a dry run, exit 3 means "collisions
found" — that is the answer you asked for, not a fault.

## 4. The two rules the tool enforces

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

---

## 5. The run book

In order, per `.wz` file. `<stage>` = `D:\games\MapleStory\Server\wz-merge`, `<v84>` =
`D:\games\MapleStory\Server\porting-resources\wz-data\v84`, `<T>` = your ticket number.

### 5.0 Confirm the backup covers what you are about to change

```
certutil -hashfile D:\games\MapleStory\<Name>.wz SHA256
certutil -hashfile D:\games\MapleStory\Server\_backup\client-v83-EzorsiaV2-2026-08-15\<Name>.wz SHA256
```

Equal, for every file the ticket touches. **If a file is not in the backup, stop and back it up
before going further.** Record both hashes in your ticket.

### 5.1 Snapshot the pre-merge tree

This copy is the target of the merge *and* the "before" side of every check in section 6. It must
exist before anything is written; nothing reconstructs it afterwards.

```
mkdir <stage>\pre
copy D:\games\MapleStory\<Name>.wz <stage>\pre\<Name>.wz
certutil -hashfile <stage>\pre\<Name>.wz SHA256      # must equal 5.0
```

### 5.2 Dry run, and read the conflicts

```
WzMerge merge <v84>\<Name>.wz <stage>\pre\<Name>.wz - <pathsFile> <stage>\<T>\<Name>.dry.conflicts.txt
```

**Do this before every real merge.** It is the cheapest step in the pipeline and the only one that
tells you what you are about to lose. Exit 0 = nothing collides. Exit 3 = read the file; for every
row decide **drop it**, **re-id it**, or **take v84's value by hand**, and record the decision in
your ticket. Do not proceed with unread conflicts.

Seconds, for a property-level manifest — only the images a listed path walks through get parsed.
**A manifest row naming a whole `WzDirectory` is the exception:** that row `DeepClone`s the entire
subtree into memory during the dry run too (`Skill.wz/Dragon`), so it is neither instant nor cheap,
and it is memory-bound. If a larger directory ever OOMs, expand that row into per-image rows.

### 5.3 If you are merging maps: resolve asset references first

**The ordering rule (section 7) covers manifest parent/child only. It does not see asset
references, and a map is nothing but asset references.** A map `.img` names its scenery by *set
name* — `back/<n>/bS`, `<layer>/obj/<n>/{oS,l0}`, `<layer>/info/tS` — and those sets live in
`Map.wz/Back`, `Map.wz/Obj` and `Map.wz/Tile` as separate manifest rows with no textual connection
to the map at all. Merge a map without its sets and the client renders it broken or crashes.

Real for ticket 06, not hypothetical: `Map/Map2/240080000.img` references
`Obj/dungeon3.img/skyValley`, which the live v83 client does not have.

For **each** map image you intend to merge:

```
WzMerge deps <v84>\Map.wz Map/Map2/<id>.img  > <stage>\<T>\<id>.deps.txt
WzMerge merge <v84>\Map.wz D:\games\MapleStory\Map.wz - <stage>\<T>\<id>.deps.txt <stage>\<T>\<id>.deps.conflicts.txt
```

Read the second command's output:

- `SKIP … already exists in target` → the client already has that set. Nothing owed.
- `ADD …` → **the set is missing.** Put that row in your paths file *above* the map row and merge
  it in the same run.

Worked example, verbatim, in `03-verification/safety-guards.md` §G9.

`deps` reports `Back` / `Obj` / `Tile` — the sets whose absence breaks rendering. It does **not**
follow mob, npc, reactor, sound or portal-script ids; those are server-side or live in other `.wz`
files and are the content ticket's own problem.

### 5.4 The real merge

```
mkdir <stage>\<T>
WzMerge merge <v84>\<Name>.wz <stage>\pre\<Name>.wz <stage>\<T>\<Name>.wz <pathsFile> <stage>\<T>\<Name>.conflicts.txt
```

Expect `verified OK -> …` and exit 0 (or 3, if you accepted collisions at 5.2). **Exit 4 means the
output is bad:** it is left as `<Name>.wz.partial` and was not promoted. Do not install it, do not
rename it — find out why (disk, memory, an interrupted run) and re-run.

### 5.5 Verify — section 6. Do not skip it.

### 5.6 Server XML

`wz/` in this repo is the server tree; `launch.bat` passes `-Dwz-path=wz`. Nothing needs
regenerating — `WzMerge xml` edits the `.img.xml` files in place.

```
WzMerge xml <v84>\<Name>.wz wz <pathsFile> <stage>\<T>\<Name>.xml.dry.conflicts.txt -   # dry
WzMerge xml <v84>\<Name>.wz wz <pathsFile> <stage>\<T>\<Name>.xml.conflicts.txt         # real
git diff --stat wz/
```

The splice is a **text insert**, not an XML round-trip: the fragment goes in at sorted position
among its parent's children and every other byte of the file is untouched. Expect
`N insertions(+), 0 deletions(-)` and nothing else — the tracer's was `19 insertions(+), 0
deletions(-)` across two files. Any deletion, or a reformat of lines you did not add, means stop and
`git checkout -- wz/`.

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
WzMerge hash <stage>\pre\<Name>.wz <image/path>   >  pre.hashes.txt
WzMerge hash <stage>\<T>\<Name>.wz  <image/path>  >  post.hashes.txt
fc pre.hashes.txt post.hashes.txt
```

for each image a node was inserted into — i.e. each `modified-list` row from 6.2. One SHA-256 per
direct child of the image, over its **decoded** values: scalars by parsed value, canvases by the
SHA-256 of their compressed pixel bytes.

**Expected: the only differing lines are the ids you added, plus the `TOTAL` line.** Every other
child must be digest-identical. Tracer result: 55 of 56 children of `Item.wz/Consume/0200.img` and
2,290 of 2,291 children of `String.wz/Consume.img` identical.

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
dotnet run -c Release --project docs/wz-baseline/tool -- <stage>\<T>\diff <stage>\pre <stage>\<T> <stage>\pre
```

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
  added 0, refused 1                                                    exit 3
```

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
(`docs/wz-baseline/merge-lists/addlist-dryrun-*.conflicts.txt`). **759 of the 16,052 add-list roots
collide** (every `.wz` with an add-list except `UI.wz`, which is out of scope).

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
| `String.wz` | 1,579 | 711 | 654 are `MonsterBook.img/<id>/reward/<n>` — the live client's monster-book rewards differ from v84's, so nearly every new reward slot is already occupied. The rest: **30 in `Eqp.img`**, 11 `Npc.img` (the ten `990191x` names + 1), 10 `Cash.img`, 3 `Etc.img`, 2 `Ins.img`, 1 `Consume.img`. **`Map.img`: 125 new map names, 0 collisions — all importable.** |
| `Npc.wz` | 98 | 34 | `9901910.img`–`9901919.img` land **inside Cosmic's injected `99xxxxx` block** — ticket 08's biggest landmine, re-id or drop. The other 24 are `9000021.img/say|stand/<n>/{delay,origin,z}` refused as `unsupported shape: parent=WzUOLProperty`: the live client aliases those frames, v84 defines them. Not a collision in the ordinary sense — resolve the alias first. |
| `Character.wz` | 438 | 6 | `Accessory/01142153.img`, `01142154.img`, plus `Dragon/019{4,5,6,7}2002.img/info/level` — the live client already ships Evan's dragon equips and v84 adds a `level` field to four of them. |
| `Etc.wz` | 10,634 | 6 | `Commodity.img/8941`–`8946`, cash-shop SNs already taken. **10,459 of the roots are `Commodity.img/<sn>/Bonus`** — a field v84 adds to existing cash-shop entries. Real v84 data, but nobody should bulk-import it. |
| `Map.wz` | 601 | 2 | `WorldMap/WorldMap010.img/MapList/93`, `/94` — already present in the live world map. |
| `Item.wz`, `Mob.wz`, `Quest.wz`, `Reactor.wz`, `Skill.wz` | 2,592 | 0 | clean |
| `Base.wz`, `Effect.wz`, `Morph.wz`, `Sound.wz`, `TamingMob.wz` | 110 | 0 | clean (02f's baselines; `Base`/`TamingMob` add nothing at all) |

**A refused row is not automatically a row you should force.** Ticket 02g compared every shared
`Eqp.img` id between the live client and v84: **589 names differ, and in all but 18 the live name is
the better one** (Ezorsia renamed all 507 faces; v84 calls them "Male Face 19"). The 18 that matter
are the ones where the live value is the literal placeholder `MISSING NAME`: all twelve
`Eqp/Dragon` ids (`1942000`–`1972002`, v84 "Silver/Gold/Reverse Mask/Pendant/Wings/Tail") and
`Accessory/1142143`–`1142151`. Same shape in `Eqp/Taming`: `1902040`–`1902042` and `1912033`–`1912035`
(Evan's Mir and its saddles) exist locally as `MISSING NAME` / `MISSING INFO`. **Those are ticket 04
and 13's blank labels, and additive-only will not fix them — they need a deliberate overwrite.**

The sweep also found the one shape the tool could not handle: `Skill.wz/Dragon`, a whole new
**directory** (Evan's dragon animations). Now supported via `WzDirectory.DeepClone()`, and the dry
run does exercise it — a dry run skips only the save, so the clone itself really runs. It is
memory-bound, since cloning a directory materialises every image beneath it; a dry run of `Skill.wz`
reports `added 14, refused 0`. What is **not** yet exercised is writing a cloned directory back out;
ticket 12 will be the first to do that, and it is the case most likely to hit the memory ceiling.

## 11. What is not covered

- **`UI.wz`.** Out of scope by ticket-03 decree and still is. Take `SkillEx` / `SkillMacroEx` only,
  never bulk.
- **Nodes nested 4+ levels below a `.img`.** The manifests expand 3 levels — deep enough for every
  id in this era, not deep enough for animation frames or foothold vertices. `WzMerge xml` itself
  has no depth limit any more (02g); the limit is what the manifests contain.
- **Sound / TamingMob / Effect / Morph / Base.** No stock baseline exists for these, so there is no
  add-list to feed the tool (ticket 05's mounts need `TamingMob.wz` extracted first).
- **`live Sound.wz/BgmGL.img` is unreadable by MapleLib** (`WZ extended property exceeds its
  declared block`). Pre-existing; avoid.
- **Bit-rot inside a compressed pixel payload** is not caught by post-write verification: it parses
  fine and is only decoded when something draws it. Verification catches truncation and structural
  damage, which are the failure modes an interrupted or out-of-memory write actually produces. The
  defence against the rest is the output hash (6.4).
- **Cross-file references other than map scenery.** `deps` covers `Back`/`Obj`/`Tile` only.
