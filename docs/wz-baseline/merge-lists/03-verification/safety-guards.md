# Ticket 03b — the merge safety guards, demonstrated firing

Every block below is verbatim tool output, run 2026-08-16 from the worktree root. The live client
at `D:\games\MapleStory\` was read but never written; it still hash-matches
`Server\_backup\client-v83-EzorsiaV2-2026-08-15\`.

## Why the guards exist (MapleLib, read at revision `a7c38edf`)

`WzFile.SaveToDisk` is not atomic and not copy-on-write:

- `WzFile.cs:675` — `new WzBinaryWriter(File.Create(path), WzIv)` **truncates the destination
  immediately**, before one image byte is written.
- `WzDirectory.cs:353-357` — unchanged images are then streamed out of the **target's own open
  reader**, over the following minutes (`Item.wz` 18 MB here but 200 MB stock, `Map.wz` 629 MB).
- `WzFile.cs:664` — the scratch file is `Path.GetFileNameWithoutExtension(path) + ".TEMP"`, a
  **relative** path resolved against the process working directory. Run the tool from
  `D:\games\MapleStory\` and it drops a several-hundred-MB `.TEMP` into the live client folder.

So: aiming `<outWz>` at the client is a one-character mistake away at all times, an interrupted
write leaves a plausible-looking truncated `.wz`, and before 03b nothing ever re-read the tool's
own output — the first reader was the game client.

## G1 — `<outWz>` is the target itself

Refused before either file is opened. (It used to fail only by accident: `WzFile` opens with
`FileShare.Read` at `WzFile.cs:243`, so `File.Create` hits the lock and throws. That is the OS,
not a design.)

```
> WzMerge merge <v84>/Item.wz <stage>/pre/Item.wz <stage>/pre/Item.wz 03-tracer-Item.txt out.txt
REFUSED: <outWz> is the target itself (D:\games\MapleStory\Server\wz-merge\pre\Item.wz).
SaveToDisk truncates the destination before it reads the images it needs out of it.
Write to a staging directory and copy afterwards.
exit=2
```

## G2 — `<outWz>` in the same directory as the target

> **SUPERSEDED by G10 (ticket 03e).** This guard is *relational to `<targetWz>`*, and it only fired
> here because this example points the target at the live client. Section 5.4 of the procedure
> points the target at a staging snapshot instead, and in that configuration this guard — and the
> other two — pass while `<outWz>` is the live client. G10 replaces the reasoning with a rule about
> the output directory alone. The guard below is retained and still fires; it is simply not the
> guarantee.

This is the exact mistake the old procedure invited: its only concrete invocation targeted
`D:/games/MapleStory/Npc.wz`, so dropping the `-` and substituting an output name puts a
non-atomic 53 MB write, plus a `.TEMP` scratch file, inside the live client directory.

```
> WzMerge merge <v84>/Npc.wz D:/games/MapleStory/Npc.wz D:/games/MapleStory/Npc.merged.wz add-list/Npc.txt out.txt
REFUSED: <outWz> is in the same directory as the target (D:\games\MapleStory). Merges stage into
a directory of their own — a half-written .wz, or MapleLib's multi-hundred-MB .TEMP scratch file,
must never appear beside the file it was made from. See WZ-MERGE-PROCEDURE.md 'Staging'.
exit=2

> dir D:\games\MapleStory\Npc*
Npc.wz   53498512      (unchanged; nothing was created)
```

`<outWz>` equal to the v84 source is refused the same way.

## G3 — staged write: `.partial` → verify → move, with the scratch file pinned

```
> WzMerge merge <v84>/Item.wz <stage>/pre/Item.wz <stage>/03b/Item.wz 03-tracer-Item.txt Item.conflicts.txt
source ...\v84\Item.wz  iv=GMS patchVersion=84
target ...\wz-merge\pre\Item.wz  iv=GMS patchVersion=83
1 paths requested
  ADD   Item.wz/Consume/0200.img/02001500
saving ...\wz-merge\03b\Item.wz.partial at iv=GMS patchVersion=83 (inherited from target); scratch .TEMP in ...\wz-merge\03b
verify ...\wz-merge\03b\Item.wz.partial  iv=GMS patchVersion=83  re-resolving 1 paths
verify: 155 images parsed, 0 unparseable, 0 requested paths missing
verified OK -> ...\wz-merge\03b\Item.wz
conflicts: 0 -> ...\wz-merge\03b\Item.conflicts.txt
added 1, refused 0
exit=0
```

`<outWz>` is only ever touched by the final `File.Move`, so a crash mid-write leaves an obvious
`.partial` instead of a truncated file that looks finished. The run was launched from the worktree
root and left **no `.TEMP` file there** — the tool pins the working directory to the staging
directory for the duration of the save.

## G4 — the post-write verification actually catches a corrupted output

Deliberate corruption of a good output, then `WzMerge verify`, which runs the *same* routine the
merge runs before promoting `.partial`:

| corruption | result | exit |
|---|---|---|
| truncated 18,398,263 → 11,000,000 bytes (the disk-full / Ctrl-C shape) | `UNREADABLE: cannot open …: GMS/BMS/EMS parse status not Success` | **4** |
| 64 KB zeroed at offset 1,000,000 (interior structural damage) | `UNPARSEABLE image Item.wz\Pet\5000007.img: Exception ParseImage returned false` … `155 images parsed, 1 unparseable` | **4** |
| 4 KB zeroed at offset 12,000,000 (lands inside canvas pixel payload) | `155 images parsed, 0 unparseable, 0 requested paths missing` | 0 |
| untouched output | `155 images parsed, 0 unparseable, 0 requested paths missing` | 0 |

**Stated limit:** verification forces every image in the file to parse, so it catches truncation
and structural damage — the failure modes an interrupted or out-of-memory write actually produces.
It does **not** catch arbitrary bit-rot inside a compressed pixel payload, which parses fine and
is only decoded when something draws it. That is a storage-corruption failure mode, not a
merge-pipeline one; the defence against it is the SHA-256 of the promoted file.

When verification fails during a merge, the output is **left as `.partial` and never promoted**:

```
VERIFICATION FAILED. Output left at <out>.partial and NOT promoted to <out>. Do not install it.
```

## G5 — exit codes

```
0  every requested path added (or, on a dry run, would be)
1  unexpected failure (exception)
2  bad arguments, or a safety guard refused them  (G1/G2 above)
3  completed, but >=1 row refused — conflicts.txt is non-empty
4  post-write verification failed — DO NOT install the output
```

Before 03b, `added 0, refused 21` exited **0**, so a scripted 04–09 loop reported green having
imported nothing. Demonstrated:

```
> WzMerge merge <v84>/Item.wz <stage>/03b/Item.wz <stage>/03b-gate/Item.wz 03-tracer-Item.txt gate.conflicts.txt
  SKIP  Item.wz/Consume/0200.img/02001500  (already exists in target)
added 0, refused 1
exit=3
```

A dry run that finds collisions also exits 3 — for a dry run that is the *answer*, not a fault.

## G6 — internal assert behind the additive-only gate

Three of the gate's four write branches (`AddImage`, `AddDirectory`, `WzSubProperty.AddProperty`)
append blindly; only `WzImage.AddProperty` throws on a duplicate name. After every add the tool
now re-counts the parent's children with that name and throws if it is not exactly 1, before
anything is saved. Not triggerable from the CLI without breaking the gate on purpose, so it is
recorded as belt-and-braces rather than as a demonstrated refusal.

## G7 — XML side: the indentation assertion (M1)

The XML additive gate is an indentation-driven line scan. The BOM and CRLF refusals guard axes
the splice does not really depend on; the indentation, which it depends on entirely, was
unasserted — so a file indented any other way presents zero children at every level, the gate
refuses nothing, and the node is duplicated into a file that already had it.

Reproduced by re-indenting the shipped `wz/Item.wz/Consume/0200.img.xml` to 4 spaces and asking
for a node it already contains:

```
  SKIP  Item.wz/Consume/0200.img/02001500  (no child element at indent 2 in …\0200.img.xml —
        the additive gate is an indentation scan and would be blind here; refusing)
added 0, refused 1
exit=3
file unchanged: True
```

## G8 — XML dry run (H4)

```
> WzMerge xml <v84>/Item.wz wz 03-tracer-Item.txt out.txt -
source ...\v84\Item.wz iv=GMS; xml root wz  [DRY RUN — nothing will be written]
  SKIP  Item.wz/Consume/0200.img/02001500  (already exists in wz\Item.wz\Consume\0200.img.xml)
added 0, refused 1
exit=3
> git status --porcelain wz/       (empty)
```

New-file branch, dry, into an empty root — reports the add, creates nothing:

```
  ADD   Item.wz/Consume/0200.img -> new file <root>\Item.wz\Consume\0200.img.xml
added 1, refused 0
exit=0
files under <root>: 0
```

**Limit of the XML dry run:** it re-reads each `.img.xml` from disk per row and never writes, so
two manifest rows adding the *same* name to the same file both report `ADD`. A real run catches
the second (the first is on disk by then). Manifests generated by the diff tool never contain
duplicate rows.

## G9 — map asset dependencies (`WzMerge deps`, B4)

A map `.img` names its scenery by set name, not by path, and the parent-before-child ordering rule
does not see those references at all.

```
> WzMerge deps <v84>/Map.wz Map/Map2/240080000.img
# Map/Map2/240080000.img references 3 scenery sets
Map.wz/Back/dragonRoad.img
Map.wz/Obj/connect.img/rope
Map.wz/Obj/dungeon3.img/skyValley
```

Feeding that straight back in as a manifest, dry, against the live client answers "which of these
do I still owe?":

```
> WzMerge merge <v84>/Map.wz D:/games/MapleStory/Map.wz - 240080000.deps.txt deps.conflicts.txt
  SKIP  Map.wz/Back/dragonRoad.img        (already exists in target)
  SKIP  Map.wz/Obj/connect.img/rope       (already exists in target)
  ADD   Map.wz/Obj/dungeon3.img/skyValley
added 1, refused 2
exit=3
```

`SKIP … already exists` = the client already has that set. `ADD` = **the set is missing and must
be merged before the map**. Confirmed independently:

```
> WzMerge dump D:/games/MapleStory/Map.wz Obj/dungeon3.img/skyValley 0
NOT FOUND: Obj/dungeon3.img/skyValley     (exit 1)
```

So merging `Map/Map2/240080000.img` on its own — the obvious reading of ticket 06 — ships a map
whose `Obj` set does not exist in the client. `Map.wz/Obj/dungeon3.img/skyValley` is already a row
in `add-list/Map.txt`; the problem was never that it is unavailable, only that nothing connected
it to the map that needs it.

> **INCOMPLETE, corrected by G12 (ticket 03e).** `SKIP … already exists in target` on
> `Map.wz/Back/dragonRoad.img` above does **not** mean "nothing owed": that image exists in v83,
> but what v84 adds is ten nodes *inside* it, which this whole-image row can never surface. This
> worked example looked right only because `Obj/dungeon3.img/skyValley` happens to be the one shape
> where whole-node granularity is correct.

---

# Ticket 03e — the second round

Everything below was run against real data on 2026-08-16 with the tool at this commit. The "before"
column is the previous committed `Program.cs`, built unchanged from `git show HEAD:` into a scratch
project as `WzMergeOld.exe`, so the comparisons are of two real binaries rather than of a diff.

## G10 — the output-directory guard is absolute (B2)

The blocker: procedure §5.4 sets `<targetWz>` to `<stage>\pre\<Name>.wz`. An `<outWz>` of
`D:\games\MapleStory\<Name>.wz` is then **not the target, not the source, and not in the target's
directory** — G1/G2 and the source guard all pass, `File.Move(..., overwrite: true)` promotes the
merge onto the live client, and the pinned CWD drops MapleLib's `.TEMP` in the client folder.

Demonstrated on a **fake client directory** — 18 zero-byte `.wz` named exactly as the real ones —
so that a guard failure writes into scratch and touches nothing:

```
BEFORE (committed tool), target = <stage>\pre\Morph.wz, out = <fakeclient>\Morph.wz:
  ADD   Morph.wz/1103.img/fly2Skill                     ... (25 rows)
  saving <fakeclient>\Morph.wz.partial ... scratch .TEMP in <fakeclient>
  verify: 46 images parsed, 0 unparseable, 0 requested paths missing
  verified OK -> <fakeclient>\Morph.wz
  added 25, refused 0
  exit=0
> ls <fakeclient>\Morph.wz
  6322806        (was 0 bytes; the "client" file was replaced)

AFTER (this tool), byte-for-byte the same arguments plus --deny/--live:
  REFUSED: <fakeclient> already holds 17 .wz file(s) that WzMerge did not put there
  (e.g. Base.wz) and carries no .wz-merge-stage. ...
  exit=2                                                (nothing written)
```

And against the **real** client directory, read-only, via the new `guard` subcommand — which runs
exactly the same function and can never write:

```
> WzMerge guard D:\games\MapleStory\Map.wz
REFUSED: D:\games\MapleStory holds 7 executable(s) (e.g. local.evan.exe). That is a game install,
not a staging directory, and WzMerge never writes into one. ...
exit=2

> WzMerge guard D:\games\MapleStory\Server\wz-merge\03e\Map.wz
ALLOWED: ...\wz-merge\03e\Map.wz  (directory ...\wz-merge\03e is acceptable output staging)
exit=0
```

Two independent rules, either of which alone stops the client: an `.exe` in the directory, and
foreign `.wz` without the `.wz-merge-stage` marker WzMerge drops on first use. The marker is what
keeps a multi-file ticket (05 staged three `.wz` into one directory) working.

## G11 — the deny-list refuses a write the old tool performed (B1)

`Etc.wz/NpcLocation.img/9901910`–`9901919`, from `add-list/Etc.txt:10569-10578`. All ten are
**absent** from the live client, so the additive-only gate never sees a collision.

```
BEFORE (committed tool), 12-row cut, target = a copy of the live Etc.wz:
  ADD   Etc.wz/NpcLocation.img/9901910        ... all ten, plus 9201144/9201145
  verified OK -> <scratch>\b1-old\Etc.wz
  conflicts: 0
  added 12, refused 0
  exit=0
> WzMerge dump <scratch>\b1-old\Etc.wz Etc.wz/NpcLocation.img/9901910 2
  9901910 [WzSubProperty]
    0 [WzIntProperty] = 100030301          <- a fixed world placement, written onto a range
                                              PlayerNPC.java:66 allocates from at runtime

AFTER (this tool), same arguments + --deny COLLISION-DENY.txt:
  SKIP  Etc.wz/NpcLocation.img/9901910  (DENIED by deny-list [Etc.wz/NpcLocation.img/9901910]:
                                         server-allocated id range, PlayerNPC.java:66)
  ... x10
  ADD   Etc.wz/NpcLocation.img/9201144
  ADD   Etc.wz/NpcLocation.img/9201145
  conflicts: 10
  added 2 (forced 0), refused 10
  exit=3
> WzMerge dump <scratch>\b1-new\Etc.wz Etc.wz/NpcLocation.img/9901910 2
  NOT FOUND: Etc.wz/NpcLocation.img/9901910        exit=1
```

**Root semantics, measured rather than asserted.** Full `add-list/String.txt` dry run, before vs
after, on the same target:

```
before:  711 refused, all "already exists in target"
after:   747 refused = 676 "already exists" + 71 "DENIED by deny-list"
```

Set-differencing the two lists gives **exactly 36 rows the old tool wrote and this one refuses**,
across **17 distinct mobs** — matching `COLLISION-TRIAGE.md`'s 36-slot / 17-list finding, reached
by denying 17 `reward` parents with no wildcard syntax anywhere:

```
String.wz/MonsterBook.img/3100101/reward/23
String.wz/MonsterBook.img/3110301/reward/23
String.wz/MonsterBook.img/3110301/reward/24
...                                            (36 rows, 17 mobs)
```

Also re-measured while here: of the 689 `reward` slots on the add-list, **653** collide, not 654.
`653 + 36 = 689`. The stale figure is corrected in `COLLISION-DENY.txt` and in the procedure.

**Deny beats force, and an overlap is a hard exit:**

```
> WzMerge merge ... --deny COLLISION-DENY.txt --force <a force row above a deny root>
REFUSED: deny/force overlap: deny 'String.wz/MonsterBook.img/3100101/reward' and force
'String.wz/MonsterBook.img/3100101' cover the same node. Deny wins by rule, but an overlap means
two decisions contradict each other — fix the lists.
exit=2
```

**Force is the only way past the gate,** and it works on both sides:

```
> WzMerge dump <stage>\pre\String.wz String.wz/Cash.img/5530001 1
  name = MISSING NAME     desc = MISSING INFO
> WzMerge merge ... --deny ... --force COLLISION-FORCE.txt --live D:\games\MapleStory\String.wz
  FORCE String.wz/Cash.img/5530001  (authorised overwrite [...]: live "MISSING NAME" -> v84 "DS Medal Basket")
  ADD   String.wz/Cash.img/5530001
  FORCE String.wz/Eqp.img/Eqp/Taming/1902040  ... "Stage 1 Dragon"
  FORCE String.wz/Etc.img/Etc/4161049         ... "Dragon Types and Characteristics (Vol.I)"
  content OK  String.wz/Cash.img / Eqp.img / Etc.img
  added 3 (forced 3), refused 0        exit=0
> WzMerge dump <out>\String.wz String.wz/Cash.img/5530001 1
  name = DS Medal Basket
  desc = Double-click on it to exchange it for a DS Mania Medal.
```

The XML side replaces the element **in place** (line 2057 before and after), so a forced row reads
as N insertions / N deletions in one spot rather than as a move.

## G12 — `deps` at the granularity the manifests use (B3)

```
BEFORE:  # Map/Map2/240080000.img references 3 scenery sets
         Map.wz/Back/dragonRoad.img          <- exists in v83, merge refuses it, reader concludes
         Map.wz/Obj/connect.img/rope            "nothing owed" while missing 10 real rows
         Map.wz/Obj/dungeon3.img/skyValley

AFTER:   # 1 map image(s) walked, 14 references, 12 add-list rows owed, 6 already in v83.
         # already in v83, nothing owed: Map.wz/MapHelper.img/mark/Leafre
         # already in v83, nothing owed: Map.wz/Obj/connect.img/rope/24/0 .. /3
         # already in v83, nothing owed: Sound.wz/Bgm14.img/DragonLoad
         # ==== Map.wz ====
         Map.wz/Back/dragonRoad.img/ani/20 .. /ani/24
         Map.wz/Back/dragonRoad.img/back/42 .. /back/46
         Map.wz/Map/Map2/240080000.img
         Map.wz/Obj/dungeon3.img/skyValley
```

The ten `dragonRoad` rows are `add-list/Map.txt:6-15`. Eight of the nine back-bearing Crimson Sky
maps draw those frames; following the old §5.3 verbatim shipped all eight with missing backgrounds.

A **link stub** — 8 of ticket 06's 21 maps, and 2,266 of 5,262 maps repo-wide — used to print
"references 0 scenery sets" under a banner that reads as an all-clear:

```
> WzMerge deps <v84>\Map.wz 240080101 <add-list>
# id 240080101 resolved to Map/Map2/240080101.img
# 2 map image(s) walked, 12 references, 13 add-list rows owed, 4 already in v83.
Map.wz/Back/dragonRoad.img/ani/20 .. back/46
Map.wz/Map/Map2/240080100.img            <- the link target, followed
Map.wz/Map/Map2/240080101.img
Map.wz/Obj/dungeon3.img/skyValley
```

`info/bgm` and `info/mapMark` resolve too, including across files
(`Sound.wz/Bgm14.img/DragonRider`, `Map.wz/MapHelper.img/mark/<name>` — 10 new marks in the
add-list). Mob/npc/reactor ids remain a scope cut and the header now says what the cut costs.

## G13 — the bucket, and the silent exit 0 (B4 / H4)

```
> WzMerge deps <v84>\Map.wz 683010000 <add-list>
# id 683010000 resolved to Map/Map6/683010000.img          <- Map6, not the hardcoded Map2
```

The old §5.3 hardcoded `Map/Map2/<id>.img`. Substituting ticket 07's id but not the bucket gave
`NOT FOUND`, exit 1 — **after the shell had already truncated the redirect target**, so the merge
that read it found an empty manifest, ran its loop zero times and exited 0 with
"added 0, refused 0". Both halves are closed:

```
> WzMerge merge ... <a file of comments only> ...
REFUSED: empty.paths.txt holds 0 manifest rows (empty, or nothing but comments). A merge of
nothing must not report success — if the file was produced by a redirect, the producing command
failed after the shell had already truncated it.
exit=2

> WzMerge merge ... (every row refused)
added 0 (forced 0), refused 12
NOTHING WAS ADDED. Every requested row was refused — read conflicts.txt before assuming the
target already had this content.
exit=5
```

Exit 5 is new and separates "added 5,000, refused 1" from "added 0, refused 1" (M1). It also
covers `add-list/{Base,TamingMob}.txt`, which are genuinely 0-row files.

## G14 — the stale-snapshot check (H1)

`pre\` is per-ticket now, and `--live` is required for every real merge:

```
> WzMerge merge <v84>\Etc.wz <target> <out> <paths> <conflicts> --deny ...
REFUSED: a real merge requires --live <path to the live .wz that <targetWz> was copied from>. ...
exit=2

> ... --live D:\games\MapleStory\Etc.wz
snapshot check: hashing <target> and D:\games\MapleStory\Etc.wz …
snapshot check OK: 94c5bad3e4afa1a4797be9bacb916258d052d2112236f002bc913e57b2cdd2c9
```

A disagreement is exit 2 with `STALE SNAPSHOT` and both hashes. Without it, 06 installing its
`Map.wz` and 07 then merging onto the shared stale `pre\Map.wz` silently reverts 06 — both runs
exit 0, and §6.2's diff compares against the same stale snapshot and reports clean.

## G15 — content digests wired into the merge path (M2)

`Canon`/`Sha` existed for the `hash` subcommand and were never used by a merge. Every image a merge
inserts into is now digested from the in-memory tree before the save and re-digested off the
written file; a mismatch is exit 4 and the output is not promoted.

```
  content digests taken for 3 inserted-into image(s)
  content OK  String.wz/Cash.img  15eb44a2ece63b21…
  content OK  String.wz/Eqp.img   0845662e6d32c31a…
  content OK  String.wz/Etc.img   9ea375142d76a545…
verify: 20 images parsed, 0 unparseable, 0 requested paths missing, 3 images content-checked, 0 drifted
```

Canvases digest the SHA-256 of their compressed pixel bytes, which is the payload the documented
4 KB-corruption case damages and which path re-resolution and `ParseImage` both walk past. It does
not cover images the merge never touched (MapleLib memcpy's those) or a whole-`WzDirectory` row,
and the tool prints which rows fell in the latter class.

## G16 — ticket 05 re-run under the fixed tool

05's three merges, re-run from the same `pre\` snapshots with `--deny` and `--live`:

```
Morph      added 25, 11 images content-checked, 0 drifted   exit 0
Skill      added 27,  3 images content-checked, 0 drifted   exit 0
Character  added  8,  8 images content-checked, 0 drifted   exit 0

sha256, 05 staged output vs 03e re-run:
Morph      E8E3D94E19B6CC8B3ADA097152216423547B9A63ACB59569AE0C76E7BBE4852D   identical
Skill      69AE95DF8380EC2268665A1205CD35F42B6DCBEBC85E6049C12657518BF95B49   identical
Character  FC50BE708A1BD561101CCDB9E7E4B011679E55204F2810C3DC7999DC82C0F5A4   identical
```

Byte-identical, all three. None of 05's rows touches a deny root and it merged no `String.wz`, so
the fixes changed nothing about its output — they only added checks it now passes.

## G17 — what 03e's own code review caught, and the exit-code matrix

Two parallel reviews (standards + spec) were run against the diff before it was committed. Five
real defects came back and are fixed; recording them because four are the kind that only show up
under a force row, which nothing had exercised yet.

1. **The force path could DELETE without writing a replacement.** `existing.Remove()` ran before
   the `(parent, srcObj)` shape switch, whose `default:` arm does `Conflict(...); continue;` — so
   an unsupported shape deleted the live node and moved on, and the row was then *excluded from
   post-write verification precisely because it had landed in conflicts.txt*. An additive-only
   tool performing a silent deletion. Fixed by moving each `existing?.Remove()` **inside** its
   branch, immediately before the add that replaces it, and never onto the `default:` path.
2. **`--live` could be pointed at `<targetWz>` itself**, hashing equal trivially and turning H1's
   stale-snapshot check into a check of nothing. Now refused (exit 2).
3. **`<gameinstall>\brandnew\Map.wz` passed the output guard** — the directory did not exist yet,
   so there was nothing to inspect, and the stage marker written afterwards would have whitelisted
   it permanently. The `.exe` test now applies to the nearest **existing** directory on the way up.
   Only the nearest one: `D:\games\MapleStory` is itself a game install and staging lives beneath
   it, so a full ancestor walk would refuse the layout this procedure prescribes. Stated ceiling:
   an existing, `.exe`-free subdirectory of a game install still passes.
4. **The deny check compared an un-normalised row.** `Map.wz//Npc/2159.img` resolves and writes
   (`Split(RemoveEmptyEntries)` eats the empty segment) while matching no deny root. The row is now
   normalised the same way the write path normalises it, in a single `GateRefusal` used by both
   `merge` and `xml` so the two cannot drift apart.
5. **The XML force overwrite could delete a sibling.** `ElementEnd` decided "self-closing" by
   `EndsWith("/>")`, and this tree really does contain elements whose value carries an embedded
   newline — `wz/String.wz/Cash.img.xml:1984`, a `<string name="desc" …` spanning two lines. The
   walk now stops at a sibling **opening** at the same indent as well as at a closing tag, and
   returns "refuse" for anything it cannot establish:

```
> WzMerge xml <v84>\String.wz <xmlroot> <paths> <conflicts> --deny ... --force <String.wz/Cash.img/5010073/desc>
  SKIP  String.wz/Cash.img/5010073/desc  (force-list overwrite ABORTED: 'desc' is never closed
                                          at indent 4 in ...\Cash.img.xml)
  added 0 (forced 0), refused 1        exit=5
> file unchanged = True
```

Stated honestly: the *previous* version returned −1 here too, because the scan is bounded by the
parent's line range. The hole it left was narrower — a wrapped leaf sharing an indent with a
sibling container, whose `</…>` the scan would have adopted as its own. Refusing on a sibling
opening closes it without needing to know whether such a shape exists today.

Also fixed: a `deps` run with an unresolved reference now prints its rows **commented out**, so the
redirect target holds no manifest rows and the merge that reads it exits 2 rather than merging an
incomplete list; a touched image that cannot be re-resolved for the content digest is a hard
failure instead of a silently skipped row; and `xml`'s directory guard no longer runs on a dry run
(a dry run writes nothing, and the contract says its answer is its findings, not a refusal) while
gaining a per-directory check on the files it actually writes.

### Exit-code matrix, re-run after every fix above

```
guard: live client directory                                    exit 2   want 2
guard: a NEW subdirectory of the client directory               exit 2   want 2
guard: a new staging subdirectory                               exit 0   want 0
guard: client-shaped directory with 18 .wz and no .exe          exit 2   want 2
merge: --live is the target itself                              exit 2   want 2
merge: paths file with 0 manifest rows                          exit 2   want 2
merge: deny/force overlap                                       exit 2   want 2
merge: no --deny supplied                                       exit 2   want 2
merge: deny fires, 2 rows still added                           exit 3   want 3
merge: every row refused, nothing added                         exit 5   want 5
```

And ticket 05's three merges re-run **byte-identical again** after all of the above
(`E8E3D94E…`, `69AE95DF…`, `FC50BE70…`), which is what makes it safe to say the restructured write
path is the same write path.
