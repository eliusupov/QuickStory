# Ticket 11a — why the composed v84 merge crashes the v83 client

**Status: no single file is convicted. Read "What the evidence rules out" before spending a
launch — six hypotheses are dead, which is what makes the bisect below cheap.**

Client verified byte-identical to `_backup/client-v83-EzorsiaV2-2026-08-15/` at the start and
at the end of this work (all 18 `.wz`, SHA-256). Nothing under `D:\games\MapleStory\`,
`porting-resources\wz-data\` or `wz-merge\03i\` was written.

Tool: `docs/wz-baseline/tool-census/` (C#, MapleLib). Two passes, both run against the exact
staged artifacts:

```
WzCensus     <backupDir> <mergedDir> <outDir> <Wz1,Wz2,...>   # image + node + kind diff
WzCensus uol <mergedDir> <outFile> <Wz1,...> <imgListDir>     # link resolution
```

---

## 1. What the merge actually did — measured, not claimed

Backup (= the working client) vs `wz-merge\03i\`, every image, full depth.

| file | Δ bytes | new .img | re-serialised .img | nodes added¹ | **nodes removed** | **values overwritten** |
|---|---:|---:|---:|---:|---:|---:|
| Character | +5,571,815 | 154 | 12 | 1,240 | 0 | 0 |
| Item | +689,003 | 2 | 26 | 5,958 | 0 | 0 |
| Map | +7,965,309 | 48 | 16 | 1,524 | 0 | 0 |
| Mob | +17,234,185 | 28 | 0 | 0 | 0 | 0 |
| Morph | +118,200 | 4 | 7 | 165 | 0 | 0 |
| Npc | +657,008 | 15 | 0 | 0 | 0 | 0 |
| Quest | +90,477 | 0 | 4 | 3,197 | 0 | 0 |
| Reactor | +528,332 | 3 | 0 | 0 | 0 | 0 |
| Skill | +3,708,552 | 0 | 3 | 2,838 | 0 | 0 |
| Sound | +2,379,312 | 0 | 2 | 138 | 0 | 0 |
| **String** | +50,954 | 0 | 10 | 1,296 | **18** | **80** |
| **total** | **+38,993,147** | 254 | 80 | | **18** | **80** |

¹ counted inside re-serialised images only; a wholly new `.img` counts as one row in "new .img".

`new .img + re-serialised .img` per file equals the "content digests taken for N inserted-into
image(s)" line in every one of the eleven `03i\*.log.txt` exactly. **334 images out of ~22,000
were rewritten; every other image was streamed verbatim and its directory-entry checksum is
unchanged.** The risk surface is those 334 images and nothing else.

Total `.wz` bytes the client must mount: **1.864 GiB → 1.900 GiB (+37.2 MiB)**.

---

## 2. What the evidence rules out

Each of these was a live hypothesis. Each is now dead, with the measurement that killed it.

**Container integrity — DEAD.** All eleven headers are byte-shape identical to the backup:
`PKG1`, `fstart=60`, copyright `Package file v1.0 Copyright 2002 Wizet, ZMS`, zero slack,
**version hash 172 in both**, and `fstart + fsize == filesize` exactly in all 22 files. The
re-save at `patchVersion=83` is correct.

**Novel data shapes — DEAD, and this is the strongest negative result here.** The census
inventories, for a whole `.wz`, every shape a client needs a parser branch for: property CLR
type, canvas pixel format, canvas `ListWzUsed`, canvas dimension bucket, zero-dimension
canvases, sound header length, any leading-underscore property name (`_inlink`/`_outlink`),
UOLs that walk above their own image, and C1-range characters in strings. For **all eleven
files the set of kinds present in the merged file is exactly equal to the set present in the
backup** — `onlyB-kinds = 0` and `onlyA-kinds = 0`, eleven times. Every shape the merge
delivers is one this exact client already parses somewhere in that same file today. There is
no DXT/BC7 canvas, no `_inlink`, no `Canvas#Video`, no `RawData`, no escaping UOL.

This is a stronger test than the brief's "not present in any v83 image anywhere", because the
baseline is the client's own working files rather than stock v83.

**Unparseable output — DEAD.** MapleLib parses every image in all eleven merged files. The only
failure is `Sound.wz/BgmGL.img`, which fails **identically in the backup** — a MapleLib
limitation, symmetric, pre-existing, untouched by the merge.

**Non-additive damage — DEAD for ten of eleven files.** Node-level diff of every re-serialised
image: `Character, Item, Map, Mob, Morph, Npc, Quest, Reactor, Skill, Sound` removed **zero**
nodes and overwrote **zero** values. They are strict supersets of what the client already had.
(Character reports 2 "value changes": `Glove/01082262.img/ladder` and `/rope` were *empty*
sub-properties that gained children. Additions, not overwrites — an artifact of how the tool
renders a childless node.)

**Dangling links — DEAD as a crash mechanism.** Resolving every UOL inside all 334 rewritten
images against the merged tree: `Sound.wz` has **39 unresolvable UOLs — and the backup has the
same 39**, in the client that works right now. `Character.wz` has 5, all inside newly added
v84 equips (`Glove/01082270`, `Glove/01082271`, `Weapon/01702264`), and **all 5 resolve
identically-unresolvable in `wz-data/v84/` itself** — Nexon shipped them broken; the merge
introduced none. Skill/String/Quest/Map/Mob/Item/Npc/Morph/Reactor: 0 unresolvable. So the
merge added no broken link, and this client demonstrably tolerates broken links.

**`Skill.wz` as prime suspect — DEMOTED, and the ticket premise should be corrected.** Ticket 11
exists because v84 skill data can crash a v83 client. Measured: the 27 mount skills introduced
**no new property type, no new canvas format, no new node shape, no dangling link, and removed
nothing**. `Skill.wz/{000,1000,2000}.img` each grew ~1.236 MB and each re-serialised cleanly.
There is nothing in `Skill.wz` that a v83 parser has no branch for. It stays on the list on
semantic grounds only (see rank 3) — the audit's structural half is now done, and it is clean.

---

## 3. Ranked suspects

The honest headline: **by every measurement available offline the eleven merged files are valid
v83 WZ containing only shapes this client already parses.** The ranking below therefore rests
on residual mechanism, not on a defect anyone can point at. Each rank states what it rests on.

### Rank 1 — `String.wz` — *the only file that destroys or overwrites live data*

Rests on: a measured, unique property. Every other file is a strict superset of the working
client; `String.wz` is not.

- **18 nodes deleted.** All are `.../desc` = `"MISSING INFO"` under item ids whose *parent* was
  a force root. v84's node for those ids carries only `name`, so forcing the id-level root
  replaced the whole subtree and took the live `desc` with it:
  ```
  Eqp.img/Eqp/Accessory/{1142143,1142144,1142145,1142149,1142150,1142151}/desc
  Eqp.img/Eqp/Glove/1082262/desc
  Eqp.img/Eqp/Longcoat/{1051176,1052217,1052224,1052228}/desc
  Eqp.img/Eqp/Shield/1092067/desc
  Eqp.img/Eqp/Taming/{1902040,1902041,1902042,1912033,1912034,1912035}/desc
  ```
- **80 values overwritten** (the 41 force roots expanded), including four `Npc.img/9201144/*`
  and six `Npc.img/1013203/*` — dialogue lines, not just names.
- Six of the eighteen are `Eqp/Taming/*` — **mounts**, i.e. exactly the feature ticket 05 added
  27 skills for. If the crash is mount-related, this is where a live node went missing.

Against it: a missing `desc` is common in stock data and is a tooltip-time read, not a startup
read. This rank is "only file that can subtract", not "known to be fatal".

### Rank 2 — `Character.wz` — *the widest rewrite of always-loaded art*

Rests on blast radius, not on a defect.

- 154 new images plus 12 re-serialised, including **`Character.wz/00002000.img` (+653 nodes)** —
  the base body image used by every character render, at character-select and in game — and
  `Afterimage/mace.img` (+462).
- Those 12 images went through MapleLib's writer. The merge's own M2 digest proves they
  round-trip **through MapleLib**; it cannot prove they round-trip through Wizet's `ResMan.dll`
  + `Canvas.dll`. If a serializer defect exists anywhere, `00002000.img` is where it hurts most.
- Carries 5 dangling UOLs — inherited from v84, not introduced, and provably survivable.

### Rank 3 — `Skill.wz` — *semantics, no longer structure*

Rests on the ticket premise alone, now that the structural half is clean. 27 v84 skills in
`000/1000/2000.img`; the client enumerates a job's skill list when the skill window opens.
A v83 client mis-reading a v84 skill *record* would not show up in any shape census.

### Rank 4 — whole-set effect: the client maps every `.wz` and never unmaps

Rests on client-binary evidence plus the symptom's shape. This is the only hypothesis that
explains "all eleven pass every static check and were verified byte-identical, yet the set
crashes", and it is the one a naive bisect will fail to find.

- `NameSpace.dll` (Wizet's WZ package layer) imports `CreateFileMappingA`, `OpenFileMappingA`,
  `MapViewOfFile`, `SetFilePointer` — and **no `UnmapViewOfFile`, no `CloseHandle`, no
  `ReadFile`**. Views are created and never released, so each `.wz` stays mapped for the
  process lifetime. Disassembly of the single `MapViewOfFile` call site (`NameSpace.dll+0xd10a`,
  IAT `0x50817020`) shows size and offset are both caller-supplied, and `CreateFileMappingA`
  (`+0xd099`) takes a caller-supplied max size — the file-sized mapping is made once per `.wz`.
- Mounted total goes 1.864 GiB → 1.900 GiB; `Map.wz` alone needs one contiguous 646 MiB run.
- **The dialog matches exactly.** `Unknown error 0x%0lX` is at file offset `0x73d8e8` in the
  unpacked dump `local.exe`, immediately beside `IDispatch error #%d` — it is
  `_com_error::ErrorMessage()`, the C++ COM fallback string, not a WZ-specific message. A failed
  `MapViewOfFile` (or any failed `IWzNameSpace`/`IWzResMan` call) surfaces as exactly this
  message box: a clean abort with the client's own dialog and **no Windows fault event**, which
  is what was reported.
- With `RemoveLogos=true` in `config.ini` there is nothing between the window appearing and the
  login screen, so "shortly after the window appears" is consistent with the mount/first-load
  stage.

Against it: `MapleStory.exe` **is** `LARGE_ADDRESS_AWARE` (`Characteristics=0x012f`), so on
64-bit Windows the process gets 4 GiB of user address space, not 2 GiB, and 1.900 GiB should
fit. Demoted to rank 4 for that reason. It is not dead: no ASLR (`DllCharacteristics=0x0000`,
fixed module bases), Themida's ~8 MiB unpacked image, D3D resources and MapleStory's decoded
canvas cache all compete for the same space, and the 646 MiB request must be contiguous.

### Rank 5 — `Map.wz` / `Sound.wz` / `Mob.wz` / the small five

`Map.wz` is the largest file and the only one whose changed set includes a *global* image
(`MapHelper.img`, gained `mark/SnowDragon`); the other 15 changed images are per-map or
per-tileset. `Sound.wz/Bgm14.img` is now a single 22.9 MB image. `Mob.wz` is 28 purely-new
images and **zero** rewrites — the largest byte delta (+16.4 MiB) with the smallest content
risk, which makes it the best single probe for rank 4. `Quest, Item, Npc, Morph, Reactor` are
small, strictly additive and read late.

---

## 4. Bisect plan

### Step 0 — three free datapoints. Do these before spending a launch.

These cost nothing and any one of them can collapse the search to zero or one launch.

1. **The hex code in the dialog.** The format string is `Unknown error 0x%0lX`, so the box
   showed a number. It is the HRESULT and it names the failure class outright:
   `0x8007000E` = out of memory → **rank 4, stop bisecting, reduce total size**.
   `0x80070002` / `0x80070003` = file or path not found → a missing resource → rank 1/2.
   `0x8004…` / `0x80040154` = COM class not registered → a DLL/registration problem, not the wz.
   `0x80004005` = generic, no information.
2. **Exactly when.** Black window only / login screen drawn / after pressing Login / at
   character select / in game. "Login screen never drew" points hard at rank 4 (mount), because
   the login screen reads `UI.wz`, `EzorsiaV2_UI.wz`, `Map.wz/Obj/login.img`,
   `Map.wz/Back/UI_login.img` and `Sound.wz/BgmUI.img` — **none of which the merge touched.**
   "Crashed at character select or later" moves rank 2 and rank 3 up and rank 4 down.
3. **Peak memory of the client as it works today** (Task Manager → Details → add "Commit size"
   *and* "Working set", watch through login into a map). If commit is already past ~2.5 GB, rank
   4 becomes rank 1 and the plan changes to "install the three biggest files and nothing else".

### Define "pass" before launch 1

A launch only carries a bit if the same reproduction is run every time. **Pass = reach the point
where it crashed, plus one step past it.** If the crash was at the login screen, pass = login
screen draws. If it was later, pass = log in, load a character, open the skill window, hover a
mount item. A subset that boots to a login screen proves nothing about `Skill.wz`.

### The tree — worst case 4 launches

Copy from `wz-merge\03i\`, restore from `_backup\client-v83-EzorsiaV2-2026-08-15\`. Only the
listed files are merged; everything else stays backup.

```
L1  install 6:  String, Skill, Character, Quest, Item, Npc          (+11.9 MiB)
    CRASH ─► L2  install 3:  String, Skill, Character
               CRASH ─► L3  install 1: String     → crash = String        (3 launches)
                                                    pass  ─► L4 install 1: Skill
                                                              → decides Skill vs Character  (4)
               PASS  ─► L3  install 2: Quest, Item
                             crash ─► L4 install 1: Quest → decides Quest vs Item  (4)
                             pass  ─► Npc                                          (3)
    PASS  ─► L2  install 5:  Map, Mob, Sound, Morph, Reactor         (+27.1 MiB)
               CRASH ─► L3  install 2: Map, Mob
                             crash ─► L4 install 1: Map → decides Map vs Mob       (4)
                             pass  ─► L3' install 1: Sound → decides Sound vs Morph/Reactor (4)
               PASS  ─► ***neither half crashes alone → it is not one file.*** Go to rank 4.
```

**Worst case 4 launches. 3 if `String.wz` is the culprit, which is the ranking's top call.**
The first split deliberately puts ranks 1–3 in the same arm and keeps that arm's byte growth
small (+11.9 MiB of the +37.2 MiB), so an L1 crash also rules out rank 4 in the same launch.

### The trap to watch for

If **L1 passes and L2 passes**, do not keep subdividing — you have proved no half crashes on its
own, so no single file is at fault and you are in rank 4. Next launch is then: install
**Mob + Map + Sound only** (+26.3 MiB = 71% of the growth, 76 new images, only 18 rewrites, the
lowest content risk in the set). Crash there = size/mapping, and the fix is to shrink the merge
(drop `Sound.wz/Bgm14.img/DragonRider`, +2.1 MiB, and the unused `Mob.wz` additions), not to
hunt content.

---

## 5. Rows to change

Only one concrete, evidence-backed change came out of this, and it is a **correctness** fix
rather than a proven crash fix — the eighteen `desc` nodes the merge deletes are the only live
data the whole composed set destroys.

The force roots below sit at item-id level, and v84's node for those ids has only `name`, so the
merge replaces the id node wholesale and takes the live `desc` stub with it. **Do not drop the
rows — narrow them to `/name`.** Same edit in both files, 18 rows each:

`docs/wz-baseline/merge-lists/composed/String.paths.txt` and
`docs/wz-baseline/merge-lists/composed/FORCE.txt`

```
String.wz/Eqp.img/Eqp/Accessory/1142143   ->  String.wz/Eqp.img/Eqp/Accessory/1142143/name
String.wz/Eqp.img/Eqp/Accessory/1142144   ->  .../1142144/name
String.wz/Eqp.img/Eqp/Accessory/1142145   ->  .../1142145/name
String.wz/Eqp.img/Eqp/Accessory/1142149   ->  .../1142149/name
String.wz/Eqp.img/Eqp/Accessory/1142150   ->  .../1142150/name
String.wz/Eqp.img/Eqp/Accessory/1142151   ->  .../1142151/name
String.wz/Eqp.img/Eqp/Glove/1082262       ->  .../1082262/name
String.wz/Eqp.img/Eqp/Longcoat/1051176    ->  .../1051176/name
String.wz/Eqp.img/Eqp/Longcoat/1052217    ->  .../1052217/name
String.wz/Eqp.img/Eqp/Longcoat/1052224    ->  .../1052224/name
String.wz/Eqp.img/Eqp/Longcoat/1052228    ->  .../1052228/name
String.wz/Eqp.img/Eqp/Shield/1092067      ->  .../1092067/name
String.wz/Eqp.img/Eqp/Taming/1902040      ->  .../1902040/name
String.wz/Eqp.img/Eqp/Taming/1902041      ->  .../1902041/name
String.wz/Eqp.img/Eqp/Taming/1902042      ->  .../1902042/name
String.wz/Eqp.img/Eqp/Taming/1912033      ->  .../1912033/name
String.wz/Eqp.img/Eqp/Taming/1912034      ->  .../1912034/name
String.wz/Eqp.img/Eqp/Taming/1912035      ->  .../1912035/name
```

The other 23 force roots delete nothing — v84 supplies both `name` and `desc` there — and are
correct as written. `String.wz` is owned by whoever owns 03f/04–09; this file only reports the
finding.

## 6. What was NOT checked

- **Semantic fitness of v84 records inside a v83 reader.** Everything here is structural. A v84
  skill record with a field the v83 `SKILLENTRY` reads differently is invisible to a shape
  census and is the residual risk behind rank 3.
- **Raw-byte equality of verbatim-streamed images.** Inferred from equal `BlockSize` *and* equal
  directory-entry `Checksum` on ~22,000 images, which is a byte-sum, not a hash. Sound but not
  complete.
- **Anything requiring the client to run.** No launch was spent.
