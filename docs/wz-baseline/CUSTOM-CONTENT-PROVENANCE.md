# What the "custom content" actually is — and whether the hard path is necessary

Ticket 00. Read-only investigation. No game data changed.

**Backup verification** — `D:\games\MapleStory\*.wz` vs
`D:\games\MapleStory\Server\_backup\client-v83-EzorsiaV2-2026-08-15\`, SHA-256:

| | result |
|---|---|
| start of run | **18 / 18 MATCH** |
| end of run | **18 / 18 MATCH** |

(One backgrounded PowerShell hash pass mid-run reported 13 DIFFs. It did not reproduce: live
file sizes and mtimes were unchanged, per-file re-hashes came back equal, and a foreground
full pass returned 18/18 MATCH. Treat backgrounded `Get-FileHash` pipelines in this harness as
unreliable; hash in the foreground.)

---

## TL;DR

**The premise in `docs/V84-UPGRADE-SCOPE.md:91` is false as stated.**

> "Dropping v84's `Character.wz` over yours would delete 18.6 MB of custom content outright."

There is no 18.6 MB of custom content in `Character.wz`. The 18.6 MiB file-size surplus is a
**lossless re-encode of stock v83 art from BGRA4444 to BGRA8888**. 29.4 million pixels were
decoded and compared across both trees: **100.0000 % identical**, **0 canvases** changed
dimensions, and **0.0000 %** of the live client's pixels carry colour information that stock
v83's 16-bit format could not already express. Not one new sprite, not one upscale.

**But the conclusion "never swap WZ files wholesale" survives — for completely different
reasons.** What a stock-v84 install would actually destroy is not Ezorsia HD art; it is
**Cosmic server content** (5,357 injected NPCs, 7,604 String rows, 399 Map metadata rows) and
**832 maps v84 itself deleted** that the server implements in Java. Plus a version-header
mismatch: live WZ headers say `version=83`, stock v84's say `version=84`, against a v83 client
binary.

So: the machinery was built to protect the wrong thing, and it can be cut down hard — but
path (b) is worse than path (a), not better.

---

## 1. What the custom `Character.wz` content actually is

### 1.1 The protect-list is 2,983 integers and 4 face images

`protect-list/Character.txt` — 2,987 rows, live client minus (v83-stock ∪ v84). Leaf-name
histogram of every row:

```
   2876  info/level      (an imgdir: level/info/1..30/exp)
    104  info/cash       (int)
      1  info/incPAD     (int)
      1  info/incMAD     (int)
      1  info/incDEX     (int)
      4  <whole .img>    Face/00020816, 00020817, 00021817, 00021820
```

That is the entire list. `info/level` is the equip level-up ("growth item") table —
`level/info/<1..30>/exp`, values almost all `10000`. Example, live client
`Character.wz/Accessory/01012011.img/info`:

```xml
<int name="cash" value="0"/>
<imgdir name="level">
  <imgdir name="info">
    <imgdir name="1"><int name="exp" value="10000"/></imgdir>
    ... through 30 ...
```

No canvases. No equips. No hairstyles. 88,362 added `exp` leaves is the bulk of it.

### 1.2 The 5,114 "modified" images changed encoding, not art

`modified-list/Character.live.txt` lists 5,114 images whose `WzImage.BlockSize` differs between
stock v83 and the live client. Full recursive property diff of all 7,201 shared images
(tool: `CharProbe`, MapleLib-based, see §6):

```
images compared:                                    7,201
images with ANY difference:                         5,114
images with a canvas DIMENSION change:                  0
canvas nodes compared:                            586,264
```

**Zero dimension changes across 586,264 canvas pairs.** Every sprite in the live client is
exactly the pixel size stock v83 shipped.

Then decode-and-compare, sampling every 20th image (361 images, spanning all 17 categories):

```
canvases compared:                                 31,510
canvases pixel-IDENTICAL after decode:             31,510
canvases pixel-DIFFERENT after decode:                  0
pixels compared:                               29,351,524
pixels identical:                              29,351,524   (100.0000 %)
live pixels expressible losslessly in ARGB4444: 29,351,524   (100.0000 %)
live pixels carrying NEW colour information:            0   (  0.0000 %)

format-pair histogram (v83 -> live)
   21,438   1->2      (BGRA4444 16-bit -> BGRA8888 32-bit)
   10,072   1->1      (untouched)
```

`WzPngFormat.Format1` = BGRA4444, `Format2` = BGRA8888
(`porting-resources/reference-sources/HaRepacker-src/MapleLib/MapleLib/WzLib/WzProperties/WzPngFormat.cs`).
Widening 4444 → 8888 doubles the stored bytes and changes **nothing** the client renders,
because every stored colour was already a 4-bit value. This is the signature of a WZ archive
having been opened and re-saved by HaRepacker, which writes Format2 by default.

Sampled node paths, with dimensions, for the record — all identical on both sides:

```
Accessory/01010000.img/info/icon           26x28   fmt 1 -> 2
Accessory/01010000.img/info/iconRaw        24x23   fmt 1 -> 2
Accessory/01010000.img/default/default      9x10   fmt 1 -> 2
Accessory/01010000.img/angry/0/default     11x15   fmt 1 -> 2
Accessory/01010000.img/vomit/0/default      2x6    fmt 1 -> 2
Character.wz/Face/00020816.img/.../face    27x15   (live-only image, 36 canvases)
```

### 1.3 The byte arithmetic closes

```
live   Character.wz   206,267,331 B
v83    Character.wz   186,792,151 B
                      -----------
file delta             19,475,180 B   (18.6 MiB)   <- the number in V84-UPGRADE-SCOPE.md:91

sum of BlockSize over the 5,114 ALREADY-EXISTING modified images:
  v83   153,709,362 B
  live  173,116,608 B
                      -----------
  delta  19,407,246 B   = 99.65 % of the file delta
```

**99.65 % of the "18.6 MB of custom content" is images that already existed in stock v83
getting fatter in place.** Of that, roughly 1–2 MB is the `level/info/1..30/exp` metadata
(~88 k int leaves + ~91 k container imgdirs); the remaining ≈ 18 MB is the 4444 → 8888
re-encode. The residual 67,934 B is the 160 images the live client has that v83 does not, plus
directory/header overhead.

### 1.4 The live client is a superset of stock v84

```
images in stock v83 Character.wz:  7,201
images in stock v84 Character.wz:  7,357
images in live      Character.wz:  7,361

live \ v83  = 160 images
live \ v84  =   4 images   (Face/00020816, 00020817, 00021817, 00021820)
v84  \ live =   0 images
v83  \ live =   0 images
```

Every one of stock v84's 7,357 `Character.wz` images is **already in the live client**. The 160
images the live client has beyond v83 are 156 stock-v84 additions (Hair `00033xxx`/`00034xxx`
families, Weapon `013029xx`, TamingMob `019320xx`, the Evan-era hairs and equips) plus 4 Cosmic
faces. Somebody already did a v84 Character content import; it is baked into the client.

What v84 would still *change* (deep diff v84 → live): 12 images where v84 redrew sprites
(`Glove/01082262`, `TamingMob/01902040`, …), ~1,300 revised `origin`/`navel`/`lt`/`rb` anchors,
87 revised `delay` values, and ~30 stat rebalances (`reqLevel 200→180`, `reqJob 5→21`,
`incMAD 61→70`). Cosmetic-to-minor; the server enforces its own stats from `wz/`.

---

## 2. Provenance — tested per candidate

### 2.1 Upstream P0nk/Cosmic — **confirmed, 100 %**

The earlier check (`provenance-p0nk.md`) covered 16,037 rows when `protect-list/Character.txt`
held only 4. Re-ran it against the current depth-3 lists, all files, resolving each protected
path inside the repo's own `wz/**.img.xml` tree (byte-identical to `upstream/master` except
`Quest.wz/{Act,Check}.img` and `String.wz/Cash.img`):

| protect list | rows | present in upstream `wz/` | missing |
|---|---:|---:|---:|
| Character | 2,987 | **2,987** | 0 |
| Etc | 6 | 6 | 0 |
| Item | 106 | 106 | 0 |
| Map | 399 | 399 | 0 |
| Mob | 168 | 168 | 0 |
| Npc | 5,981 | 5,981 | 0 |
| Quest | 225 | 225 | 0 |
| Reactor | 49 | 49 | 0 |
| Skill | 3 | 3 | 0 |
| String | 7,604 | 7,604 | 0 |
| UI | 41 | 41 | 0 |
| **total** | **17,569** | **17,569 (100 %)** | **0** |

And separately: **160 / 160** of the live-only `Character.wz` images exist as
`wz/Character.wz/<Category>/<id>.img.xml`.

**What this proves:** every protected node path, and every scalar value under it, is content
that P0nk/Cosmic upstream ships and versions in git. Nothing in the protect set originated with
this fork or with Ezorsia.

**What it does not prove:** `wz/` contains zero `.png` — canvases appear as
`<canvas width= height=><vector name="origin"/></canvas>` metadata shells with no payload. So
`wz/` proves *structure and scalars*, never *pixels*. Anywhere the protected content is real
art, `wz/` can tell you it should exist and how big it is, but cannot reproduce it.

How much of the protect set is actually art (canvases larger than 2×2, counted from the
upstream XML under each protected path):

| file | protected rows | canvases | canvases > 2×2 | typical dims |
|---|---:|---:|---:|---|
| Npc | 5,981 | 10,668 | **4** | 10,664 are **1×1 blanks** (invisible script NPCs); 4 × 138×112 |
| Character | 2,987 | 144 | **144** | 4 face images, ~26×16 each |
| Reactor | 49 | 78 | **76** | 196×217, 100×121 (reactors 2618000/2618006/9208004) |
| UI | 41 | 41 | **41** | 25×25 boss HP-bar portrait icons |
| Map | 399 | 18 | **18** | 5 × 640×470, rest small |
| Skill | 3 | 9 | **9** | 32×32 icons |
| Item | 106 | 8 | **8** | 32×32 icons |
| Etc / Mob / Quest / String | 8,003 | 0 | **0** | pure scalar metadata |

**378 canvases** in the entire protect set carry real pixels. Everything else — 17,000-plus
paths — is integers and strings that can be regenerated from git.

Sample of what an "injected NPC" is (`wz/Npc.wz/9904608.img.xml`):

```xml
<imgdir name="info">
  <int name="imitate" value="1"/> <int name="hideName" value="1"/>
  <imgdir name="script"><imgdir name="0"><string name="script" value="rank_user"/></imgdir></imgdir>
</imgdir>
<imgdir name="stand"><canvas name="0" width="1" height="1"><vector name="origin" x="0" y="0"/></canvas></imgdir>
```

A 1×1 transparent pixel and a script name. That is the "custom content" in `Npc.wz`.

### 2.2 MapleEzorsia V2 — **confirmed: the HD is runtime, and it ships no character art**

The README (fetched) states the DLL "modifies addresses in a default, packed, v83 MapleStory
client … as well as changes the game window and canvas resolution to HD", and that there are
"no WZ/IMG conflicts; Ezorsia V2 will generate its only WZ/IMG file".

Corroborated directly against the binaries on disk. ASCII strings in
`D:\games\MapleStory\dinput8.dll` (2,840,064 B):

```
.detour  .detourc  .detourd            <- Microsoft Detours sections
dinput8 hook initialized
Applying resolution
\dinput8.dll
EzorsiaV2_UI
EzorsiaV2_UI.wz
Data/MapleEzorsiaV2wzfiles.img
MapleEzorsiaV2wzfiles.img/Common/frame1024   ...frame1280, frame1366, frame1600, frame1920
MapleEzorsiaV2wzfiles.img/Base/backgrnd  /backgrnd1  /backgrnd2
Base.wz
;what resolutions should the game use? (reccomended values: 1280x720, 1366x768; ...)
width=1280
;only true if you directly edited the original frame in UI.wz and want to use that
```

`D:\games\MapleStory\config.ini` is live and matches: `width=1280 / height=720`,
`WindowedMode=true`, `RemoveLogos=true`, `setDamageCap`, `speedMovementCap`,
`CustomLoginFrame=false`.

`EzorsiaV2_UI.wz` (1,370,002 B) contains exactly **4 images**:

```
MapleEzorsiaV2wzfiles.img     <- the HD login/UI frames at 5 resolutions + cash-shop backgrounds
smap.img  zmap.img  StandardPDD.img   <- Base.wz shims the hook needs
```

**Ezorsia's entire WZ footprint is one 1.3 MB self-contained file it generates itself.** It
never touches `Character.wz`, `Map.wz`, `Npc.wz` or any other archive. The HD is a Detours
proxy resizing the window and canvas at runtime. The owner is right.

### 2.3 The owner's own edits, or an unrelated WZ pack — **no evidence of either**

Nothing in any protect list, and no live-only `Character.wz` image, fails to resolve inside
upstream Cosmic's `wz/`. The one physical trace of local tooling is the presence of
`hacreator/` and `harepacker/` in the client directory and the wholesale Format1→Format2
re-encode, which is what HaRepacker does on save — i.e. the client's `Character.wz` has been
round-tripped through HaRepacker at some point, by whoever assembled the Cosmic client
distribution. That round-trip is the entire 18.6 MB.

---

## 3. Would stock v84 + the Ezorsia DLL preserve what the owner cares about?

The HD, yes — that is a runtime hook and is unaffected by which WZ files are on disk.
Everything else, no. Concretely, what path (b) destroys:

### 3.1 Server-required (losing it breaks the server)

| what | size | evidence |
|---|---|---|
| **832 maps v84 deleted** — Monster Carnival (`970030100`–`970042717`), Mu Lung Dojo (`925*`), Sheep Ranch / Happyville (`9105*`) | `removed-list/Map.txt`: **1,017 roots / 354,953 paths** | Server implements both: `src/main/java/server/partyquest/MonsterCarnival.java`, `MonsterCarnivalParty.java`, `net/server/channel/handlers/MonsterCarnivalHandler.java`, `tools/mapletools/DojoUpdate.java`, `constants/id/MapId.java`. Verified against maplestory.io by an earlier ticket. |
| **5,357 Cosmic injected NPCs** (`9901xxx`, `99065xx`, `9977777`) | live `Npc.wz` has 6,977 images vs stock v83's 1,620 | Scripts reference them (`rank_user`, etc.). Client cannot render an NPC id with no `.img`. |
| **7,604 `String.wz` rows** — `Cash.img` later-version items, `MonsterBook.img` entries, `Eqp.img/Eqp` | `protect-list/String.txt` | Server serves these ids; missing strings are a known crash/blank-window source. `CRASH-ANALYSIS.md` already fingers `String.wz` as the top suspect for the composed-install crash. |
| **399 `Map.wz` metadata rows** — `fieldLimit`, `swim`, `mobTime`, `onUserEnter`, `portal`, `foothold`, `life/*/f` | `protect-list/Map.txt` | Spawn tables and portal geometry the server drives. |
| **2,028 `Etc.wz` roots v84 deleted** — `Commodity.img` cash-shop rows | `removed-list/Etc.txt` | Cash shop inventory. |
| **674 `Mob.wz` roots v84 deleted** — mostly `info/mobType` | `removed-list/Mob.txt` | |
| **WZ header version mismatch** | live/composed headers read `version=83`; stock v84 reads `version=84` (measured with `CharProbe VER`) | The v83 `MapleStory.exe` validates a version hash against the header. Dropping v84-headered archives on a v83 binary is a first-order load failure — plausibly the same class of generic `_com_error` the owner saw. **Note the negative result: the composed merge outputs in `Server\wz-merge\10` are all `version=83`, so the earlier crash was *not* a version-header mismatch.** |

### 3.2 Cosmetic (annoying, not fatal)

| what | count |
|---|---|
| 4 Cosmic face images (`00020816`, `00020817`, `00021817`, `00021820`), 36 canvases each at ~27×15 | 144 canvases |
| 76 custom reactor canvases (`2618000`, `2618006`, `9208004`), 196×217 and smaller | 76 |
| 41 boss HP-bar portrait icons, `UI.wz/UIWindow.img/MobGage/Mob/<id>`, 25×25 | 41 |
| 18 `Map.wz` canvases, incl. 5 × 640×470 | 18 |
| 9 skill icons + 8 item icons, 32×32 | 17 |
| 4 NPC sprites at 138×112 | 4 |
| The 4444→8888 re-encode | **worth nothing — renders identically** |
| v84's 12 redrawn glove/mount sprites and ~1,300 revised anchors would be *gained*, not lost | — |

**Total genuinely-irreplaceable art at stake across all 17,569 protected paths: 378 canvases.**
Everything else is regenerable from `upstream/master`'s `wz/` XML tree at any time — which also
means the framing "salvage it from the live client binary before it's lost" is wrong for 98 % of
the protect set.

---

## 4. Recommendation

**Take (a) — continue the selective merge — but cut its scope by more than half, starting with
`Character.wz`.**

Reasoning, in order of weight:

1. **(b) loses server-required content that (a) does not.** 832 maps the server has Java
   handlers for, 5,357 NPCs its scripts summon, 7,604 String rows. The premise that justified
   (a) was wrong, but a different and stronger justification was sitting underneath it the
   whole time. v84 is a *subtractive* patch for this server.
2. **(b) additionally introduces a v84 header against a v83 binary**, a failure mode (a) has
   already been measured not to have.
3. **(a) can now be made much cheaper.** The evidence retires whole blocks of machinery:
   - **Delete `protect-list/Character.txt` from the merge entirely.** 2,987 rows protecting
     2,983 integers and 4 faces. `Character.wz` has no art worth defending — pixel-identical to
     stock v83, and its image set is already a superset of v84's. Anything you break there you
     can rebuild from `wz/`. That is the single largest and least-justified block of gating.
   - **Stop treating the live client as an irreplaceable artifact.** 17,569 / 17,569 protected
     paths are in git. Reduce "must preserve from the binary" to the **378 canvases** in
     §2.1 — a list small enough to export to PNG once, into
     `porting-resources/`, and stop guarding at merge time.
   - **The remaining risk is where `CRASH-ANALYSIS.md` already put it**: `String.wz`, the one
     file that subtracts. Concentrate there rather than across eleven files.
4. There is no third option that gets HD for free. The DLL already provides the HD and is
   orthogonal to all of this — it neither helps nor hinders either path.

### What would change my mind toward (b)

- **If the server does not actually run Monster Carnival or Mu Lung Dojo in practice** — the
  handlers compile, but if those maps are unreachable on this shard, the largest single argument
  for (a) evaporates and (b) becomes tempting.
- **If a v84-headered WZ set loads cleanly in this client.** Cheap to test in a throwaway copy
  of the client directory: drop stock v84's `Item.wz` (smallest at 18 MB) in alongside otherwise
  live files and launch. If it loads, the version-hash objection dies and (b) is worth costing
  out properly. If it does not, (b) is dead on arrival regardless of content.
- **If a scripted rebuild of the protect set from `wz/` proves easy** (it is XML → WZ, and 98 %
  of it is scalars). Then (b) plus a post-install "re-inject Cosmic content" script is
  genuinely simpler than the current merge apparatus — you would be trading a 1,662-row triage
  for one deterministic regeneration step. This is the strongest version of (b) and it is worth
  half a day to prototype before committing further to (a).

### What would not change my mind

More crash forensics on the composed set. Six hypotheses are already dead on measurement and
the owner has no error code. If (a) continues, the cheapest next move is to shrink the merge
until it works, not to keep interrogating the merge that did not.

---

## 5. Where the premise came from

`V84-UPGRADE-SCOPE.md:60-95` derived "~24.6 MB of custom content" from **file sizes**, and then
attributed it: "That is Ezorsia's HD work and whatever you have added." The same document warns,
two paragraphs later and correctly, "Do not use file size as a proxy for content when merging —
compare nodes, not bytes." That warning applies to its own table. The project's own machine-
generated `SUMMARY.md` already contradicted the 18.6 MB figure — it reports `Character.wz`
protect bytes as **62,921**, not 18,600,000 — and nobody reconciled the two numbers.

Suggested correction to `V84-UPGRADE-SCOPE.md:91`, for whoever owns that file:

> ~~Dropping v84's `Character.wz` over yours would delete 18.6 MB of custom content outright.~~
> The 18.6 MB `Character.wz` surplus is a lossless BGRA4444→BGRA8888 re-encode of stock v83 art
> (0 dimension changes, 29.4 M pixels compared, 100 % identical) plus ~1–2 MB of equip
> `level` metadata. It is not custom art. The real reason not to swap wholesale is that v84
> *deletes* 832 maps the server implements, and that the client's Cosmic-injected NPC/String
> content lives only in the live WZ set. See `CUSTOM-CONTENT-PROVENANCE.md`.

---

## 6. Reproducing this

Tool: `CharProbe`, a ~200-line MapleLib consumer, in the session scratchpad
(`…\153450ca-8c96-43b9-ab04-ac30f7fe175a\scratchpad\CharProbe\`). Same
`MapleLibProject` reference as `docs/wz-baseline/tool/WzDump.csproj`. Modes:

```
CharProbe <wz> <dirA> <dirB> <out>          full recursive property diff: added/removed leaf
                                            names, scalar changes, canvas dim + format +
                                            compressed-byte changes
CharProbe PIXELS <wz> <dirA> <dirB> <out> N decode every Nth image's canvases in both trees and
                                            compare pixel-by-pixel; reports how many B pixels
                                            are not expressible in ARGB4444
CharProbe ONLY   <wz> <dirA> <dirB>         image-set difference both directions
CharProbe VER    <wz,wz,...> <dir> [<dir>]  WZ header version + encryption per file
```

Protect-list-vs-upstream check and the canvas census are plain Python over `wz/**.img.xml`
(`scratchpad\canvascount.py`); no WZ tooling needed, because `wz/` is XML.

Every run above was read-only. Nothing under `D:\games\MapleStory\`,
`porting-resources\wz-data\**` or `Server\wz-merge\**` was written.
