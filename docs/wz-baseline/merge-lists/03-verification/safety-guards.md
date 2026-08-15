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
