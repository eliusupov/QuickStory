# 35 — DreamMS v92 client mining: results

Read-only archaeology on `D:\games\dreamms` (7.65 GB, downloaded 2026-08-16, 55 files).
Nothing was executed. Nothing was installed. Nothing was written under `D:\games\dreamms` or
`D:\games\MapleStory\`. No DreamMS asset was copied anywhere — only measurements, header fields,
node names and sizes are reproduced below.

---

## Answer to Question 1 (the one that matters)

**This client is a TECHNIQUE reference. It is NOT a data reference. Its WZ content is heavily
customised and in places back-ported from clients far newer than v92.**

The client *binary* is genuinely v92 — that part checks out three independent ways:

| Evidence | Value |
|---|---|
| `Base.wz` PE-equivalent version field, read by MapleLib | `VERSION 92` (our v84 `Base.wz` reads `84` from the same tool) |
| Every DreamMS `.wz` archive parses | GMS IV, `VERSION 92` |
| PDB path left in `DreamMS.exe` | `c:\ACGame_GL\Release_0092\Bin\MapleStory.pdb` |
| PDB path left in `NameSpace.dll` | `c:\ACGame_GL\Release_0092\Bin\NameSpace.pdb` |

The *data* is not. Proof, strongest first:

1. **Custom NPC IDs that Nexon never issued.** `Npc.wz` holds **7,413** images against stock v84's
   **1,662**. The tail includes `9977777.img` (a hand-picked ID) and a contiguous bulk-allocated
   block `9906000.img … 9906599.img`, 600 entries all exactly 403 bytes — a scripted mass-create,
   not a Nexon shipment.
2. **A second, parallel skill archive that does not exist in any Nexon client.** `Skill01.wz`
   contains *exactly* the same image-name set as `Skill.wz` (verified by set-diff: zero name
   differences) but every image is larger — e.g. `232.img` 5,524,226 → 19,866,345 bytes,
   `434.img` 4,146,071 → 19,674,645 bytes. It is an alternate art set, selected by the registry
   key `use_skill01`.
3. **Post-Big-Bang UI files inside a pre-Big-Bang client.** `UI.wz` adds `StatusBar3.img`,
   `UIWindow2.img`, `MapLogin1.img`, `OneADay.img` on top of the stock set. `StatusBar3`/`UIWindow2`
   are v.100+ files; v92 predates them.
4. **Skin/body images beyond the stock range.** `Character.wz` ships 19 body images
   (`00002000`–`00002027`, including `2024`–`2027`) against v84's 9. `00002000.img` itself is
   140,231 → 170,708 bytes, i.e. the base body was edited.
5. **Weapon categories outside the v92 set.** `Character01.wz/Weapon` contains `01392000.img` and
   `01602000`–`01602007` — outside the stock 130–149 + 170 weapon categories for this era.
6. Bulk size deltas consistent with all of the above: `Mob.wz` 2,116 images vs v84's 1,600;
   `String.wz/Eqp.img` 383 KB → 1,404 KB; `String.wz/PetDialog.img` 436 KB → 2,715 KB.

**Consequence for the project:** do not cite DreamMS for "what v92 shipped" in any form — not item
IDs, not mob stats, not skill data, not string tables. `Server\porting-resources\clients\GMSSetupv92.exe`
remains the only authoritative v92 data source on disk. Everything below is about *mechanism*.

---

## Instruments — what was proved, and how

Three instruments were built for this ticket. Each was pointed at something with a known answer
before it was pointed at DreamMS.

### 1. `WzList` — WZ structure lister
`docs/work-plan/tools/wzlist/` (C#, net10.0-windows, references the project's existing MapleLib
checkout — same library the `docs/wz-baseline/tool*` programs use). Lists directory/image names to a
given depth without parsing image contents, so a 2 GB archive costs the directory table only.

**Proof:** run against `D:\games\MSv84\client\Base.wz` — a file whose contents this project already
knows. It returned `VERSION 84`, `IV GMS`, `ident=PKG1`, the Wizet copyright line, and exactly the
15 stock mount directories plus `smap.img`/`StandardPDD.img`/`zmap.img`. Cross-checked against
`Character.wz` (v84: 17 dirs, 18 images, the expected `00002000`–`00012011` set). Both matched
prior knowledge before any DreamMS archive was opened.

**Known limitation, stated rather than hidden:** `List.wz` fails to parse in *both* clients with
`WZ header FStart is outside the file`. That is correct behaviour — `List.wz` is not a PKG1 archive
in this era. The instrument gets the same answer for our own known-good v84 file, so this is a
property of the format, not a tool failure.

### 2. `pe.ps1` — PE header / export / import reader
`scratchpad/pe.ps1`. Hand-rolled DOS→NT header walk, RVA→file-offset mapping, export directory and
import descriptor enumeration, including forwarder detection.

**Proof:** run against `C:\Windows\SysWOW64\version.dll`, whose export table is publicly documented.
It returned all 17 exports with correct ordinals — `GetFileVersionInfoA` (1) through
`VerQueryValueW` (17) — and correctly identified `VerLanguageNameA`/`VerLanguageNameW` as
**forwarders to KERNEL32**, which requires the RVA-inside-export-directory check to be right. That
is a stronger test than a name dump.

**Trap caught by the proof:** `version.dll` reported `TIMEDATE 4015924288 → 2097-04-04`. Modern
Microsoft binaries put a reproducible-build hash in that field, not a timestamp. So the timestamp
field is only trusted below where it is corroborated (it is, for `ijl15.dll`, by section content
hashes).

### 3. `strings.ps1` — ASCII / UTF-16LE string extractor
`scratchpad/strings.ps1`.

**Proof:** run against `D:\games\dreamms\ijl15.dll`, then compared against the *independently*
parsed export table from `pe.ps1`. Both produced the identical six `ijl*` export names plus the
`DreamMS.dll` dependency, from different parsing paths. Also confirmed the `.NewIT` section name
that `pe.ps1` had reported.

### Not used
7-Zip / cabinet extraction was not needed — no archive was extracted, so the spanned-cabinet
truncation trap was never in play. `GMSSetupv92.exe` was not opened.

---

## Q2 — The split-WZ scheme

### How the client discovers archives (established, high confidence)

`DreamMS.exe` contains, in `.rdata`, the UTF-16 string `Base.wz` immediately followed by the format
string `%s.wz` (file offset `0x77EE34`–`0x77EEB0`, verified by hexdump). `NameSpace.dll` is stock
v92 (`Release_0092` PDB) and exposes `CWzFileSystem`, `CWzPackage`, `CWzArchive`, `CWzNameSpace`.

That is the stock Wizet mechanism: the client opens `Base.wz`, enumerates its top-level
**directories**, and for each directory named `X` opens `X.wz` via `%s.wz`. `Base.wz` itself is
6,583 bytes and its mount directories are empty — they are pure declarations.

**DreamMS's `Base.wz` declares 21 mounts where stock v84 declares 15:**

```
stock v84 :  Character Effect Etc Item Map Mob Morph Npc Quest Reactor Skill Sound String TamingMob UI
DreamMS   :  Cap_Canvas Character Character01 Character02 Effect Etc Item Map Map01 Map02
             Mob Morph Npc Quest Reactor Skill Skill01 Sound String TamingMob UI
```

**So: registering a new archive costs one empty directory entry in `Base.wz` and one new file on
disk. No client patch is involved in the discovery step.** That is the reusable part.

Notable robustness datum: **`Cap_Canvas` is declared but `Cap_Canvas.wz` does not exist on disk.**
The patcher log records `Checked 55 files, repaired 55 files, skipped 0` — a full, complete
download, so nothing is missing. The client therefore tolerates a declared-but-absent archive.

### What actually got split (established — this is a measurement, not a guess)

The split is **disjoint subtree relocation**, not overlay and not shadowing. Whole top-level
subtrees were *moved out* of the base archive; each subtree exists in exactly one archive:

| Archive | Size | Top-level contents |
|---|---:|---|
| `Character.wz` | 565 MB | Accessory, Afterimage, Cap, Cape, Coat, **Dragon**, Face, Glove, Hair, Longcoat, Pants, PetEquip, Ring, Shield, Shoes + 38 body/head images |
| `Character01.wz` | 1,135 MB | **`Weapon` only** (2,577 images) |
| `Character02.wz` | 498 MB | **`TamingMob` only** |
| `Map.wz` | 152 MB | Map, WorldMap, Effect.img, MapHelper.img, Physics.img |
| `Map01.wz` | 489 MB | **`Back` only** |
| `Map02.wz` | 1,080 MB | **`Obj` + `Tile` only** |

Compare stock v84 `Character.wz`, which holds `TamingMob` and `Weapon` inline, and stock v84
`Map.wz`, which holds `Back`, `Obj` and `Tile` inline. Split points were chosen at the largest
single subtree in each archive.

`Skill01.wz` is a **different scheme in the same client**: same image-name set as `Skill.wz`,
different (larger) content, selected at runtime by the `use_skill01` registry flag. That is a
parallel/alternate archive, not a size split.

### What is NOT established (unknown — do not assume)

**How a lookup for `Map/Obj/...` reaches `Map02.wz` is unknown.** `DreamMS.exe` still contains
literal stock-form resource paths such as `Map/Obj/etc.img/5th_Timer/pie` and
`Map/Obj/insideGL.img/inside1/aniN`, so game code still asks for the *original* path while the data
lives under a different mount. Something must re-root the moved subtrees under their original
parent. I could not determine where:

- Neither `DreamMS.exe` (ASCII or UTF-16) nor the visible strings of `DreamMS.dll` contain the
  literals `Character01`, `Character02`, `Map01`, `Map02`, `Skill01` or `Cap_Canvas`.
- No numbered-archive format string (`%s%02d.wz`, `%s01.wz`, …) exists in either binary — the only
  archive format string is `%s.wz`.
- `DreamMS.dll` is **Themida-packed** (see below), so its code and data strings are not statically
  readable.

The two candidate mechanisms are (a) a patched/aliasing mount in the client's `CWzFileSystem` that
grafts a numbered archive's children onto the base namespace node, or (b) a `DreamMS.dll` hook on
namespace resolution. **I did not discriminate between them and am not guessing.** Resolving it
means either disassembling the unpacked `DreamMS.exe` mount routine or unpacking Themida; the first
is the cheaper follow-up and is a bounded task if we ever actually need it.

### Recommendation if we hit a `.wz` size ceiling

The declaration half is free and proven: add the mount to `Base.wz`, ship `Foo.wz`. Before relying
on it, we must settle the unknown above, because our additive merges grow *existing* subtrees —
which is precisely the case that needs the re-rooting behaviour, not just the mount.

---

## Q3 — Resolution / widescreen, and the HD-phase recommendation

### Established

- `DreamMS.dll` imports **`d3d9.dll` and `d3dx9_43.dll`** directly, and the client ships both
  `d3dx9_31.dll` (stock, 2010) and `d3dx9_43.dll` (added). Our v84 client ships neither. So DreamMS
  does its own Direct3D 9 work against a much newer D3DX than the stock client used.
- `DreamMS.dll`'s only readable version resource is **MinHook 1.3.3 (Tsuda Kageyu)** — the whole mod
  is built on MinHook, **not** Microsoft Detours (which is what Ezorsia V2 uses).
- The settings surface separates `resolution`, `screen_mode`, `fullscreen`, `vsync`,
  `disable_alt_enter`, `centered`, `align_ui_left`, `align_left`, `auto_hide_sidebar`, `sidebar_mode`
  — resolution and window mode are *distinct* axes, and there are explicit UI-anchoring flags.
- **Measured minimum viewport from the shipped default window geometry** in `DefaultSettings.reg`:
  `Metrics` right edge = `0x575 + 0x7d` = **1522 px**; `Chat` bottom edge = `0x259 + 0xe7` =
  **832 px**; `WidgetManager` right edge = `0x41f + 0x161` = 1440 px; `ExpTracker` posx = 1313.
  With `resolution = 0` as the shipped default, the default enum entry is therefore **not** 1024×768
  — the shipped layout needs at least ~1522×832, i.e. a 1600×900-class default.

### Not established (unknown)

Whether the resolution change is a render-target resize, a UI re-layout, upscaled art, or a
combination **cannot be read statically**: `DreamMS.dll` has a `.themida` section (7,168,000 bytes
virtual, 0 raw) and a `.boot` section (4,699,136 bytes) — it is Themida-packed, and the only strings
that survive are the MinHook version resource and `@.themida`. Unpacking Themida requires *running*
it, which the hard boundaries forbid, and would be a multi-hour project regardless. **Marked unknown
and stopped**, per the priority rule.

The presence of per-UI-element anchoring flags (`align_ui_left`, `centered`, `align_left`,
`auto_hide_sidebar`) and of stored per-window `posx/posy/width/height` for 11 separate windows is
*consistent with* real UI re-layout rather than pure upscaling, but that is an inference from the
settings shape, not a measurement of the renderer.

### Recommendation for the HD phase

**Copy DreamMS's structural model, not Ezorsia's.** Concretely:

1. **Hook framework: MinHook, not Detours.** DreamMS proves MinHook 1.3.3 is sufficient for a
   full-scale client mod. MinHook is a single small MIT-licensed library with no Detours licensing
   or build friction, and our v84 `edits\` DLLs are already free-standing MSVC DLLs it drops into.
2. **Delivery: our existing `ijl15.dll` + `edits\` loader.** We already have `LoadDLLsFromDirectory`.
   An HD module becomes `edits\hd-1.0.0.dll` — no new injection mechanism, no second proxy.
   This is the cheapest model on the table because the delivery half already exists and is proven on
   v84.
3. **Settings: registry or ini, but keep resolution and window-mode as separate axes**, and store
   per-window geometry, because that is what a re-layout (as opposed to an upscale) requires.
4. **Do not try to reproduce DreamMS's renderer** — we cannot see it, and guessing at it is exactly
   the failure mode this ticket warns about.

Ranked cost, cheapest first: our `ijl15` + `edits` + MinHook (reuses everything we have) <
Kaizen's `settings.ini` model (new config plumbing, unknown injection) < Ezorsia's
`dinput8.dll` + Detours (v83-only, new proxy, heavier dependency).

---

## Q4 — `ijl15.dll`: corroboration of ticket 20's correction

**Result: same host DLL, byte-identical base, completely independent implementation.** Ticket 20's
correction is confirmed, and can stop being hedged.

### DreamMS's `ijl15.dll` (356,352 B, PE timestamp 2001-05-30)

It is **Nexon's own shipped `ijl15.dll`, unmodified, plus one appended section.** Proof — SHA-256 of
each PE section's raw bytes, DreamMS's copy vs our pristine `D:\games\MSv84\client\ijl15.dll.bak`:

| Section | SHA-256 (identical in both) |
|---|---|
| `.text` | `D85A306FF5BE88307643158435A3871E5CB263C19844668E8BEDFFD5186384D0` |
| `.rdata` | `099E54B5AF4004B26D60A44426A399BF0EAC41B1876959A9CBE30DBE0DBA3771` |
| `.data` | `3D32EFC9884001B1CABD78BAF5F259841032DDD306E35A2502D67AD665D710DD` |
| `.data1` | `E3851F7CBEADD8DCD75480B5EF00CA6F1B261B98D0CDF059215FEE8E682312A7` |
| `.rsrc` | `D9137406883BB87494BFF18080401F54FD8B8795180A8E02D0E9B5F497F1BBC0` |

Export RVAs match exactly too (`ijlGetLibVersion @0x0002B110`, `ijlInit @0x00028A60`, …). The only
difference is a 7th section, **`.NewIT`** at RVA `0x58000`, and the PE import data directory
repointed to it (`rva=0x58000 size=60`). Hexdump of `.NewIT`:

```
58000  CC D6 04 00 ... 78 D8 04 00   <- descriptor 1: original KERNEL32 thunks, back into .rdata
58010  00 D0 04 00 65 80 05 00 ...   <- descriptor 2: name rva 0x5803C, thunks 0x5805D
5803C  "DreamMS.dll"
5805D  hint/name "?MainThread@@YGHXZ"
```

**Mechanism, in one sentence:** take the game's own `ijl15.dll` byte-for-byte, append a section
holding a rebuilt import directory that adds a dependency on `DreamMS.dll!?MainThread@@YGHXZ`, and
point the PE import directory at it. Windows then loads `DreamMS.dll` as an ordinary dependency —
no proxy exports, no forwarders, no code patching at all. `DreamMS.dll` exports exactly one symbol,
`?MainThread@@YGHXZ` (`int __stdcall MainThread(void)`).

### Our `ijl15.dll` (394,752 B, `bypass\GMS-84.1\`, installed at `client\ijl15.dll`)

A from-scratch MSVC-2015+ DLL (imports `MSVCP140`, `VCRUNTIME140`, `api-ms-win-crt-*`), 17 KB of
code and 353 KB of `.data`. Exports the six `ijl*` functions as thunks **plus a 7th export
`LoadDLLsFromDirectory`** — the `edits\` folder loader.

### Verdict

| | DreamMS | Our v84 bypass |
|---|---|---|
| Host DLL chosen | `ijl15.dll` | `ijl15.dll` |
| Base file | Nexon's original, byte-identical | rewritten from scratch |
| Injection | appended import-table section | export thunks + `LoadDLLsFromDirectory` |
| Toolchain era | MSVC 6-era original + hand-patched PE | MSVC 2015+ |
| Payload | one monolithic Themida-packed DLL | a folder of small `edits\*.dll` |

**Two independent teams, no shared code, same choice of `ijl15.dll` as the load hook.** That is a
genuine second data point: `ijl15.dll` is the norm for this client family, and `dinput8.dll` is the
Ezorsia lineage, exactly as ticket 20 said. Incidental confirmation of *why* `dinput8` also works:
`DreamMS.exe`'s import table shows MapleStory imports `dinput8.dll` natively, so both are legitimate
load hooks — but `ijl15` is the one both bypass projects picked.

Housekeeping note found in passing: `bypass\GMS-83.1\ijl15.dll` and `bypass\GMS-84.1\ijl15.dll` are
the same size (394,752) but **different files** (SHA-256 `010773B5…` vs `82796ECC…`). The installed
`client\ijl15.dll` matches GMS-84.1, which is correct.

---

## Q5 — Server routing

**DreamMS byte-patches the client's hardcoded login-IP table. It does not use a Winsock hook for
that step.**

`DreamMS.exe` contains exactly **three consecutive 16-byte slots**, at file offsets `0x735960`,
`0x735970`, `0x735980`, each holding the ASCII string `127.0.0.1` zero-padded to 16 bytes. These are
the only IP literals in the entire binary.

Three slots is the corroborating detail: our own `edits\redirect.ini` names the stock GMS login
array as **exactly three IPs** —

```
OriginalIP1=63.251.217.2
OriginalIP2=63.251.217.3
OriginalIP3=63.251.217.4
RedirectIP=127.0.0.1
RedirectPort=8484
```

Same cardinality, same `char[16]` slot layout. So DreamMS overwrote in the file what our
`redirect-1.0.0.dll` rewrites at runtime through the `WSPPROC_TABLE` hook.

### Answer to the underlying question

**Our `WSPPROC_TABLE` hook is not the norm — it is the more sophisticated of the two.** The norm is
the byte patch. Ours is strictly better for our situation (the client binary stays pristine and
hash-stable, the target is a one-line ini edit, and nothing has to be re-patched when the client is
re-extracted), and it is already working. **No change recommended.**

### Unknown

How DreamMS's *public* server is actually reached at runtime is unknown. The on-disk binary says
`127.0.0.1`, which cannot be the live endpoint. `DreamMS.dll` imports `WS2_32.dll` and bundles
MinHook, so a runtime `connect`/`gethostbyname` hook (using `127.0.0.1` as a sentinel) or an
in-memory re-patch driven by `DreamLauncher.exe` are both plausible — but `DreamMS.dll` is
Themida-packed and I could not read it, and running it is forbidden. No hostname, URL or non-local
IP appears in `DreamMS.exe`. **Recorded as unknown.** It does not matter for us either way.

---

## Q6 — Full feature surface from `DefaultSettings.reg`

`DefaultSettings.reg` is UTF-16, `HKEY_CURRENT_USER\SOFTWARE\DreamMS`, one root key plus 13 subkeys.
Intel only — **not a mandate to build any of this.**

Legend: **C** = plausibly client-side only · **S** = needs server support · **C/S** = client-side
rendering but needs server if the value is persisted or authoritative.

### Root key — display / window
| Key | Default | Side | Note |
|---|---|---|---|
| `resolution` | 0 | C | enum; default index is ≥1600×900-class (see Q3 measurement) |
| `screen_mode` | 0 | C | distinct axis from `fullscreen` |
| `fullscreen` | 0 | C | |
| `vsync` | 1 | C | |
| `disable_alt_enter` | 0 | C | |
| `centered` | 1 | C | window centring |
| `align_ui_left` | 1 | C | UI anchoring — implies re-layout, not upscale |
| `align_left` | 0 | C | second anchoring axis |
| `dark_mode` | 0 | C | |
| `fast_startup` | 0 | C | skip logos/intro |
| `auto_hide_sidebar` | 0 | C | |
| `sidebar_mode` | 0 | C | |

### Root key — HUD / readability
| Key | Default | Side | Note |
|---|---|---|---|
| `remove_cd_text` | 1 | C | |
| `remove_hpmp_text` | 1 | C | |
| `tooltip_opacity` | 0x50 | C | |
| `item_indicator` | 1 | C | on-ground item highlight |
| `quest_lightbulb` | 0 | C | |
| `buff_duration` | 1 | C | v83 already sends remaining duration |
| `debuff_row` | 1 | C | own debuffs are client-known |
| `cooldown_row` | 1 | C | own cooldowns are client-known |
| `party_buff_row` | 1 | **S** | requires the server to broadcast party members' buffs |
| `chat_plus` | 1 | C | tabbed chat; filtering of already-received messages |
| `extended_quickslot` | 0 | **S** | extra slots must persist in the server's keymap table |
| `inventory_expanded` | 1 | **S** | slot count is server-authoritative |
| `cash_bag_sorted_alpha` | 1 | C | local sort |

### Root key — damage / effects
| Key | Default | Side | Note |
|---|---|---|---|
| `damage_skin` | 0 | **C/S** | rendering is client; ownership/purchase needs server |
| `unique_damage_skin` | 0 | **C/S** | same |
| `damage_opacity` | 0x64 | C | |
| `self_damage_opacity` | 0x64 | C | |
| `skill_opacity` | 0x64 | C | |
| `summon_opacity` | 0x64 | C | |
| `pet_opacity` | 0x3c | C | |
| `chair_opacity` | 0x64 | C | |
| `use_skill01` | 0 | C | selects `Skill01.wz` over `Skill.wz` — pure archive choice |
| `skill_sound_mute` / `skill_sound_volume` | 0 / 0x0b | C | |
| `mob_sound_mute` / `mob_sound_volume` | 0 / 0x03 | C | |

### Root key — class-specific
| Key | Default | Side | Note |
|---|---|---|---|
| `dragon_roar_autopot` | 1 | C | **Evan** — auto-pot triggered by Dragon Roar's HP cost |
| `dragon_opacity` | 0x64 | C | **Evan** — dragon render opacity |
| `lv300_dragon` | 1 | C | **Evan** — force the final dragon appearance |
| `double_jump_fj` | 1 | C | flash-jump/double-jump input handling |
| `shad_dark_sight` | 1 | C | render self while in Dark Sight |
| `shadower_dark_sight` | 0 | C | |
| `thief_shifter_emotion` | 1 | C | |
| `shoe_spikes` | 1 | C | |
| `sg_route` | 0 | **unknown** | meaning not determined |

### Root key — integration
| Key | Default | Side |
|---|---|---|
| `discord_rpc` | 1 | C (`discord_game_sdk.dll` is imported by `DreamMS.dll`) |
| `Discord\show_ign`, `show_job_level`, `show_field` | 1,1,1 | C |

### Subkeys (window geometry + per-widget options)
`Chat`, `CWnd`, `DamageTracker`, `Expedition`, `ExpTracker`, `FontManager`, `Jukebox`, `LootLog`,
`MesoTracker`, `Metrics`, `MonsterInfo`, `WidgetManager` — each stores `posx/posy/width/height`.
Feature-bearing entries:

| Key | Side | Note |
|---|---|---|
| `Chat\{buddy,party,guild,alliance,spouse,whisper,smega,general}_tab` | C | tab filters |
| `Chat\timestamp`, `locked`, `focused_opacity`, `inactive_opacity`, `vertical_spacing` | C | |
| `FontManager\{buddy,party,guild,alliance,whisper,spouse}_chat` | C | per-channel ARGB colours |
| `Jukebox\favourites_count`, `songfav0` | C | client-side BGM override |
| `LootLog\manual_pickup`, `hide_filtered`, `filter_{equip,use,etc}` | C | pickup is client-initiated |
| `MonsterInfo` | **C/S** | exact HP would need server; damage-tracked estimate is client-side |
| `ExpTracker` / `MesoTracker` / `DamageTracker` / `Metrics` | C | derived from packets the client already receives |
| `Expedition` | **S** | expedition system is server-side |
| `WidgetManager\{info,exped,family,merchant}_notifications` + timeouts | **S** for exped/family/merchant | |
| `WidgetManager\{share_mouse,share_keyboard,on_escape_close,theme,opacity}` | C | multi-client input sharing |
| `WidgetManager\viewport_0..5`, `fps_control`, `fps` | C | |

### The Evan point, for ticket 14
`dragon_roar_autopot`, `dragon_opacity` and `lv300_dragon` are shipped, defaulted-on client settings.
They are an existence proof that **Evan's dragon renders correctly on a v92 client, including at the
final evolution stage** — a *client* fact, independent of DreamMS's customised data. `Skill.wz`
carries a `Dragon` top-level directory and `Character.wz` carries a `Dragon` directory (the latter
is also present in stock v84, so that part is not new).

---

## Q7 — Dual Blade

Cheap observations only, no digging:

- `Skill.wz` contains `430.img`, `431.img`, `432.img`, `433.img`, `434.img` — the full Dual Blade
  job chain. `434.img` is 4,146,071 B in `Skill.wz` and 19,674,645 B in `Skill01.wz`.
- `Character01.wz/Weapon` contains **35 katara images**, `01342000.img`–`01342xxx`. The katara
  (secondary weapon, category 134) art set exists and is populated.
- `Etc.wz` contains `MakeCharInfo.img` (3,754 B) — the character-creation constraint table that
  would carry Dual Blade's creation rules. **Not opened** (would require parsing image contents;
  and its contents are DreamMS's customised data anyway, so of limited value).
- Nothing determined about how Dual Blade character creation is presented in the UI. **Unknown.**

---

## Incidental findings worth recording

1. **`DreamMS.exe` is an unpacked/dumped v92 `MapleStory.exe`.** Stock v84 `MapleStory.exe` is
   Themida-packed — random section names (`oyihhyms`, `fshekobw`, `qytrjskw`), a single `kernel32`
   import, `.text` raw 3,125,248 vs virtual 8,720,384, and zero readable strings. `DreamMS.exe` has
   named sections, `.text` raw == virtual (7,229,440), a full 16-entry import table, and readable
   strings including the original PDB path. Its `.export` section is 2,023,424 virtual against 4,096
   raw — the classic signature of a memory dump realigned and given a rebuilt import table. Its PE
   export name is still `MapleStory.exe`, exporting `ZtlTaskMemAllocImp/FreeImp/ReallocImp`.
   **This is why static analysis of their EXE was possible at all**, and it is a technique available
   to us if we ever need to read v84 client internals.
2. **`DreamMS.dll` is Themida-packed** (`.themida` 7,168,000 virtual / 0 raw, `.boot` 4,699,136).
   Static analysis of the mod itself is closed. This is the single biggest limiter on this report.
3. **The mod is built on MinHook 1.3.3**, per the surviving version resource.
4. **`DreamLauncher.exe` is a .NET single-file self-contained bundle** (x86, `singlefilehost.exe`
   host, ~189 MB, .NET 6+). Its payload was not extracted. Its log format is Serilog-style
   (`[2026-08-16 14:25:27Z] [INF]`), build `3.0.11`, and it performs a metadata check then a
   per-file repair pass — 55 files, 7,650,825,245 bytes.
5. `version.data` is 24 bytes of base64 (`XrAFdWkBeLqCnd/KaX3xIQ==` → 16 raw bytes, i.e. a build
   GUID/hash, not a version number). `release.sequence` is the ASCII text `13`.
6. The anti-cheat set (`eTracer.aes`, `v3hunt.dll`, `suipre.dll`, `nmcogame.dll`, `nmconew.dll`,
   `aossdk.dll`, `bz32ex.dll`) is **byte-for-byte the stock 2010 AhnLab/nProtect payload** — same
   sizes as our v84 client's copies (`suipre.dll` 417,937; `v3hunt.dll` 131,201; `nmcogame.dll`
   315,392; `nmconew.dll` 1,638,400). Shipped, not used — they are inert without the HShield
   directory, which DreamMS does not ship. Nothing was executed or installed.

---

## Everything left as unknown

| # | Unknown | Why it was stopped |
|---|---|---|
| 1 | How `Map/Obj/...`-style lookups resolve into `Map02.wz` (aliasing mount vs. runtime hook) | needs disassembly of `DreamMS.exe`'s mount routine or Themida unpacking |
| 2 | The resolution/widescreen implementation (render target vs. re-layout vs. upscale) | `DreamMS.dll` is Themida-packed; unpacking requires running it |
| 3 | How the live server endpoint is supplied at runtime | same |
| 4 | Meaning of the `sg_route` setting | no readable string context |
| 5 | Why `Cap_Canvas` is declared in `Base.wz` with no file on disk | could be a removed/planned archive; not determinable statically |
| 6 | Whether `01392xxx` / `01602xxx` weapon categories are custom or back-ported | would require opening the images (customised data anyway) |
| 7 | Dual Blade character-creation presentation | deliberately not pursued (ticket priority) |
| 8 | `DreamLauncher.exe` payload / patch-manifest format | .NET single-file bundle, not extracted; out of scope |

## Acceptance criteria status

- [x] Q1 answered unambiguously — **technique reference only, not a data reference** (six independent
      proofs)
- [x] Split-WZ mechanism explained — discovery half fully established and reusable; **re-rooting half
      explicitly unknown**, so the answer is *not* claimed as complete
- [x] Resolution approach characterised as far as static analysis permits, with a named and justified
      HD-phase recommendation (MinHook + our existing `ijl15`/`edits` loader)
- [x] `ijl15.dll` compared — same host DLL, byte-identical Nexon base, independent implementation;
      ticket 20's correction confirmed
- [x] Server routing identified — in-binary patch of the three-slot login IP table to `127.0.0.1`;
      runtime endpoint delivery unknown
- [x] Full `DefaultSettings.reg` catalogue, every key marked C / S / C/S / unknown
- [x] All three instruments named, and each proved against a known-answer target before use
- [x] Nothing executed, nothing installed, nothing copied out beyond measurements
- [x] Unknowns listed, no confident guesses
