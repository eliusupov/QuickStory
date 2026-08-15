# WZ merge procedure — v84 content into the live v83 client + Cosmic's server XML

Established by ticket 03 (tracer: item `2001500`, "Red Potion"). **Tickets 04–09 follow this
document; do not invent a second way.** Everything here was run end to end, not designed on paper.

Tool: `docs/wz-baseline/tool-merge/` (C# console, MapleLib) — sibling of ticket 02's
`docs/wz-baseline/tool/` diff tool, same `MapleLibProject` override.

```
dotnet build -c Release docs/wz-baseline/tool-merge/WzMerge.csproj
# binary: docs/wz-baseline/tool-merge/bin/Release/net10.0-windows/WzMerge.exe

WzMerge dump  <wz> <path/under/wz> [depth]
WzMerge merge <sourceWz> <targetWz> <outWz|-> <pathsFile> <conflictsTxt>
WzMerge xml   <sourceWz> <xmlRoot>            <pathsFile> <conflictsTxt>
```

`<pathsFile>` is a manifest: lines exactly as `docs/wz-baseline/add-list/*.txt` writes them
(`Item.wz/Consume/0200.img/02001500`), `#` and blanks ignored. Feed it an add-list directly, or a
hand-cut subset (see `docs/wz-baseline/merge-lists/`).

---

## The rule: additive-only, enforced in the write path

`WzMerge merge` resolves the target path **before** it writes. If anything already lives there the
write never happens — the row goes to `conflicts.txt` and the loop moves on. The only mutations in
the tool are `AddImage` / `AddDirectory` / `AddProperty` onto a parent that does not already hold
that name (`Program.cs`, the `ADDITIVE-ONLY GATE` comment in `Merge()`). Correctness comes from
construction.

This matters because the proof people reach for does not work: a presence-only re-diff after the
merge cannot show that nothing pre-existing *changed*, since a destructive overwrite preserves
paths too. See "Verification" below for what to run instead.

The same gate guards the XML side: `WzMerge xml` refuses a path whose `name="…"` already appears
in the target `.img.xml`, and refuses to overwrite an existing `.xml` file.

### conflicts.txt is a deliverable, not a log

It is the exhaustive list of v84 content the rule dropped. **A v84 change that is an *edit* rather
than an *addition* is indistinguishable from a collision, and additive-only silently discards it.**
Read the file before shipping. Two real examples from the tracer sweep, one per direction:

| path | live client | v84 | verdict |
|---|---|---|---|
| `String.wz/Npc.img/9901910` | "I am /name, who has reached Lv. 120." (Cosmic's injected NPC) | "I am /name, who has reached Lv. 200." | **rule was right** — importing would have destroyed server content |
| `String.wz/Cash.img/5530001` | `name="MISSING NAME"`, `desc="MISSING INFO"` | "DS Medal Basket" | **rule cost us something** — a human should take v84's value |

### The collision map for 04–09 — already measured

Full dry run of every add-list against the live client (`docs/wz-baseline/merge-lists/addlist-dryrun-*.conflicts.txt`).
**41 of the 2,172 add-list roots collide** (every `.wz` with an add-list except `UI.wz`, which is
out of scope). Nothing else in the v84 add surface does.

| file | refused | what |
|---|---|---|
| `Npc.wz` | 10 | `9901910.img`–`9901919.img` — v84's new NPCs land **inside Cosmic's injected `99xxxxx` block**. Ticket 08's biggest landmine: these cannot be imported at their native ids without destroying server NPCs. Re-id or drop. |
| `String.wz` | 23 | the same ten `Npc.img/990191x` names, plus `Cash.img/5500005–6`, `Cash.img/5530001–8`, `Consume.img/2100166`, `Ins.img/3994179–80` |
| `Etc.wz` | 6 | `Commodity.img/8941`–`8946` — cash-shop SNs already taken |
| `Character.wz` | 2 | `Accessory/01142153.img`, `01142154.img` |
| `Item.wz`, `Map.wz`, `Mob.wz`, `Quest.wz`, `Reactor.wz`, `Skill.wz` | 0 | clean |
| `Base.wz`, `Effect.wz`, `Morph.wz`, `Sound.wz`, `TamingMob.wz` | 0 | clean (02f's new baselines; `Base`/`TamingMob` add nothing at all) |

The sweep also found the one shape the tool could not handle: `Skill.wz/Dragon`, a whole new
**directory** (Evan's dragon animations). Now supported via `WzDirectory.DeepClone()`; it is
memory-bound, since cloning a directory materialises every image beneath it.

Re-run any of these in seconds — pass `-` as `<outWz>` for a dry run (nothing is repacked, and
only the images a listed path actually touches get parsed).

```
WzMerge merge <v84>/Npc.wz D:/games/MapleStory/Npc.wz - docs/wz-baseline/add-list/Npc.txt out.txt
```

**Do this before every real merge.** It is the cheapest step in the pipeline and the only one that
tells you what you are about to lose.

---

## Trap 1 — the v84 version hash. Resolved structurally.

Measured, not folklore: `WzMerge dump` prints the patch version of any file.

```
D:/games/MapleStory/Item.wz                      iv=GMS  patchVersion=83
porting-resources/wz-data/v84/Item.wz            iv=GMS  patchVersion=84
```

The trap only bites if you open a **v84** file and save it. **We never do.** v84 is read-only
source; the file being written is the live client's own `.wz`, with nodes added into it. MapleLib's
`SaveToDisk(path)` with no override re-emits the IV and version hash it parsed off that file, so
the output is v83-encoded because its target always was. There is no conversion step to get wrong,
and therefore no menu option anyone can forget.

Verified: the merged `Item.wz` re-opens at `iv=GMS patchVersion=83` with the v84 node present and
its 358-byte / 283-byte icon payloads intact and byte-identical to the v84 source.

**Do not "re-save the v84 file at v83 and then copy from it."** That is the folk technique, it
converts 600 MB to convert two nodes, and it re-encodes content nobody diffed. Merge node-level
into the live file instead.

Corollary for anyone using HaRepacker by hand: its default write path is BMS, not GMS. Both tools
here try GMS → BMS → EMS on open, so a hand-saved file still loads, but it will no longer match the
client. Prefer the scripted path.

## Trap 2 — `basedata`. Decision: re-export with base64 **off**. Never strip.

Cosmic's `wz/` XML was produced by `WzClassicXmlSerializer(indentation: 2, LineBreak.Windows,
exportbase64: false)`. Confirmed by inspecting `wz/Item.wz/Consume/0200.img.xml`: 2-space indent,
CRLF, no BOM, and `<canvas name="icon" width="27" height="30">` carrying dimensions and origin but
no pixel payload. The server never reads pixels — `XMLDomMapleData` only needs the node shape and
the scalar leaves.

So `WzMerge xml` constructs exactly that serializer and emits the fragment straight into the
existing `.img.xml`. **The 14 MB `2218.img.xml` problem never arises**, because base64 is never
generated in the first place. HaRepacker's GUI export with base64 on, followed by a stripping pass,
is the wrong shape: it is slower, it is lossy in a way nobody audits, and it rewrites files that
should not change.

The splice is a **text insert**, not an XML round-trip: the fragment goes in at sorted position
among the root's children and every other byte of the file is untouched. The tracer's diff is
`19 insertions(+), 0 deletions(-)` across two files — no serializer reformat noise, and the merge
is additive in git as well as in content.

---

## Verification — BlockSize invariance, not a presence diff

Ticket 02's diff tool doubles as the merge checker; its roots are positional args:

```
dotnet run -c Release --project docs/wz-baseline/tool -- <outDir> <pre-merge-tree> <post-merge-tree> <post-merge-tree>
```

Then read `<outDir>/modified-list/*.txt`: every row is a path present in **both** trees whose
`WzImage.BlockSize` changed. A destructive overwrite shows up here; a presence diff would miss it.
`add-list` must equal exactly what you asked for, and `removed-list` must be empty.

Tracer result — the whole merge, measured:

| | pre | post | add-list | removed-list | images with changed BlockSize |
|---|---|---|---|---|---|
| `Item.wz` | 7,361 paths | 7,362 | `Consume/0200.img/02001500` | empty | `Consume/0200.img` 53,447 → 54,270 |
| `String.wz` | 12,859 paths | 12,860 | `Consume.img/2001500` | empty | `Consume.img` 329,281 → 329,327 |

One image changed per file — the one the node was inserted into — and it grew by exactly the added
content. Every other image in both files came through a **full MapleLib repack** byte-for-byte the
same size. Nothing pre-existing was disturbed.

**Known limit, stated rather than papered over:** `BlockSize` is a change detector, not a hash. A
replacement that happens to compress to the identical length is a false negative. Escalation if a
specific merge is ever disputed: hash each image's canonical re-serialization (not its raw block
bytes — those embed absolute offsets). Not done, deliberately.

Second check, cheaper and independent — **re-run the same merge against its own output.** Every
path now exists, so a working gate refuses all of them and `conflicts.txt` fills up:

```
  SKIP  Item.wz/Consume/0200.img/02001500  (already exists in target)
  added 0, refused 1
```

Third: **the merge is deterministic.** Running it twice from the same pre-merge copy produced
byte-identical output (`Item.wz` `115feac1…`, `String.wz` `d5721de2…`), and the XML splice is
byte-identical across runs too. So a merge can be re-run from the notes and checked by hash rather
than by inspection — which is what "repeatable by someone else" has to mean.

---

## Server side

`wz/` in this repo is the server tree; `launch.bat` passes `-Dwz-path=wz`. Nothing needs
regenerating — `WzMerge xml` edits the `.img.xml` files in place.

Verify with `src/test/java/server/V84TracerNodeTest.java`, which reads the imported nodes through
`XMLWZFile` / `XMLDomMapleData` — the exact classes the running server uses. **Tickets 04–09:
extend that class with your own ids rather than building a second harness.**

```
./mvnw -o test -Dtest=V84TracerNodeTest
```

Two landmines it already works around, both worth knowing:

- **`WZFiles.DIRECTORY` is `static final`, resolved once per JVM**, and `MobSkillFactoryTest` points
  `wz-path` at a `@TempDir` before it. Whichever test class runs first wins for the entire surefire
  fork. Any test reading the real tree through `WZFiles`/`DataProviderFactory` is therefore
  order-dependent — construct `new XMLWZFile(Path.of("wz", "Item.wz"))` explicitly instead.
- **`ItemInformationProvider`'s constructor reads the `monstercarddata` table** and
  `DatabaseConnection.getConnection()` throws `IllegalStateException` (not `SQLException`, which is
  the only thing it catches) when the pool is uninitialised. Guard that test with
  `assumeTrue(DatabaseConnection.initializeConnectionPool())`.

---

## Ordering rule, learned from the sweep

Import a parent before its children. `WzMerge` refuses `parent path absent in target` rather than
fabricating intermediate nodes — so a new `.img` inside a **new** directory needs the directory
row first. The add-list manifests are already collapsed to copy roots, which mostly makes this
automatic, but a hand-cut subset can break it.

## What is not covered

- **`UI.wz`.** Out of scope by ticket-03 decree and still is. Take `SkillEx` / `SkillMacroEx` only,
  never bulk.
- **XML more than one level below a `.img`.** The add-list manifests expand images exactly one
  level, so no manifest row needs it. `WzMerge xml` refuses loudly instead of guessing; add a real
  XML walker if a hand-written list ever needs it.
- **Sound / TamingMob / Effect / Morph / Base.** No stock baseline exists for these, so there is no
  add-list to feed the tool (ticket 05's mounts need `TamingMob.wz` extracted first).
- **`live Sound.wz/BgmGL.img` is unreadable by MapleLib** (`WZ extended property exceeds its
  declared block`). Pre-existing; avoid.
