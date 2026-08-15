# 02 — WZ baseline diff and custom-content protect list

**Blocked by:** None — can start immediately

**Status:** ready-for-agent

## What to build

A written manifest of two things: every node in your client that is **custom** (present in neither stock v83 nor stock v84), and every node v84 **adds** over stock v83.

This is prefactoring — it makes every later import safe instead of guesswork. Your client is MapleEzorsia V2 HD and carries roughly 24.6 MB that exists in no stock build (`Character.wz` +18.6 MB, `Map.wz` +2.9 MB, `Npc.wz` +2.2 MB, `Etc.wz` +0.6 MB, `String.wz` +0.3 MB). Without knowing precisely what those nodes are, any merge risks silently destroying Ezorsia's work or yours.

Both stock references are already extracted: stock v83 at `porting-resources/wz-data/v83-stock/` and stock v84 at `porting-resources/wz-data/v84/`. WzComparerR2 is in `porting-resources/reference-sources/` and needs building.

The id-list manifest at `92-V83-V84-CONTENT-DELTA.md` (771 added nodes) is a good cross-check but is **not** authoritative — it was derived from `String.wz` naming tables, so a few entries may be named without carrying data. The node-level diff produced by this ticket supersedes it.

## Acceptance criteria

- [ ] ~~WzComparerR2 built and running~~ — not done, deliberately. See Findings.
- [x] Diff of your client against stock v83 produces a written protect-list of custom nodes, per WZ file
- [x] Diff of stock v83 against stock v84 produces the authoritative add-list, per WZ file
- [x] Add-list is cross-checked against the 771-node manifest and any discrepancy is explained
- [x] Both lists are committed as files, not left in a tool window

## Findings (2026-08-15)

Deliverables: `docs/wz-baseline/add-list/*.txt` (10 files), `docs/wz-baseline/protect-list/*.txt`
(11 files), `docs/wz-baseline/SUMMARY.md` (counts table), `docs/wz-baseline/tool/` (the diff tool
source, for reuse in tickets 04-09).

**Tooling deviation.** No .NET SDK was present; installed via winget
(`Microsoft.DotNet.SDK.9`/`.10`). `wz-data/v83-stock/` and `wz-data/v84/` turned out to still be
packed binary `.wz`, not extracted XML as the ticket text assumed — all three trees needed a real
WZ reader. WzComparerR2 is a GUI-only WinForms tool with no batch mode. Instead: checked out the
`MapleLib` git submodule under `reference-sources/HaRepacker-src/` (the same WZ-reading library
WzComparerR2 itself uses) and wrote a ~150-line console tool against it directly
(`docs/wz-baseline/tool/Program.cs`). Full 11-file × 3-tree sweep runs in under 2 minutes.

**Diff granularity.** Walk stops at the `.img` boundary (one entity = one image) for
Character/Map/Mob/Npc — confirmed correct by inspection. Six files needed one extra level because
entities live as sub-properties of a handful of shared category images: String, Quest, Skill,
Item (confirmed empirically — bucketed category images hold up to 10,000 item ids each; without
expansion Item.wz found only 2 new nodes against a 412-item manifest), Etc (the Evan
character-creation block is a new key inside the existing `MakeCharInfo.img`), UI
(`SkillEx`/`SkillMacroEx` already exist as containers in both stock trees; v84 enlarges their
contents, not the top-level image list).

**Verified vs claimed custom-content sizes.** Raw file-size deltas (live − v83) all confirmed
correct: Character +18.58 MB, Map +2.98 MB, Npc +2.25 MB, Etc +0.57 MB, String +0.30 MB — matches
the scope doc's ~24.6 MB claim. But composition differs sharply by file:
- Etc.wz and Npc.wz: ~92-100% of the size delta is genuinely new node paths (Etc:
  `MapNeighbors.img` + `DeveloperNpc.img`; Npc: a 5,332-entry sequential custom-NPC block
  `9901020`-`9906599` + `9977777`).
- Map.wz: ~68% new node paths (Crimson Sky, Evan forest, Neo City maps, etc — 79 confirmed new
  map images), remainder likely minor edits to shared/worldmap nodes.
- **Character.wz is the outlier: only 4 new node paths / ~63 KB out of an 18.6 MB delta (~0.3%).**
  Ezorsia's HD customization is overwhelmingly *content substituted under IDs that already exist
  in stock v83* (higher-res sprites resaved under the same paths), invisible to a
  presence-only diff by construction. **Consequence for tickets 04-09: importing v84's
  Character.wz must never overwrite a node path that already exists in the live client — the
  content there is very likely Ezorsia's HD replacement, not stock data, even though the path
  also exists in both stock trees.**

**Manifest cross-check (771 nodes).** Full category-by-category breakdown:
- **mob (41 manifest / 37 found):** 25 confirmed matching, 16 manifest entries
  (`9990000-9990015`, element/tag markers) are pre-existing v83 data that only got *named* in
  v84's String.wz — manifest false positives. 12 node-level adds not in the manifest at all
  (`1210111`, `2220110`, `2230112`, `9300385-9300393`) — real new mobs the naming-based manifest
  missed.
- **map (93 / 79):** same named areas covered (Crimson Sky, Dragon's Nest, Neo City, Evan's
  forests, tutorial/job-advancement, Golem's Temple, etc). Gap not fully bisected — plausibly
  Korean-only ids in the manifest (ghost ship, Premium Carnival PvP maps) plus the known
  Map.wz repack/recompress anomaly (node count fell 5,616→4,862 overall despite 79 real adds).
  Flagged as an open item, not force-explained.
- **item (412 / 267 in Item.wz):** roughly half the manifest's "item" entries are hairstyles
  (~49, old 5-digit ids) or equips (weapons, caps, accessories, dragon mask/pendant/wing/tail
  sets) that live in **Character.wz**, not Item.wz — confirmed present there in the 156-node
  Character.wz add-list. Item.wz's 267 correctly covers consumables/cash/install/pet/mastery
  books, inflated somewhat by sub-node expansion (one pet id alone contributes 26 behavior keys).
- **npc (27 / 42):** all 27 manifest npcs confirmed present. 15 extra found are unnamed companion
  npcs the manifest's naming-based diff missed. One manifest entry (`9000071` Keroben) was *not*
  found — likely pre-existing data only newly named in v84, same pattern as the mob markers;
  not independently verified.
- **quest (198 / 798):** not a real discrepancy — Quest.wz repeats each quest id across 4 category
  images (`Act`/`Check`/`QuestInfo`/`Say`). 197 unique ids × up to 4 + a few container extras
  (`Exclusive.img`, `PQuestSearch.img`) = 798.
- **skill (not in manifest):** 190 new nodes — all 10 Evan job trees + a `9000` beginner skill +
  a full `Skill.wz/Dragon/` animation set + one `MobSkill.img` entry. Corroborates
  `EVAN-DUALBLADE-SCOPE.md`'s skill count independently.
- **UI (not in manifest):** confirms `SkillEx`/`SkillMacroEx` both present as promised, plus 9
  other new UI keys (`NewCharEvan`, `tutorial.img/evan`, keybar slots, etc).
- **Etc (not in manifest):** confirms audit finding #5 directly —
  `MakeCharInfo.img/EvanCharFemale`/`EvanCharMale` are new in v84, closing the missing Evan
  character-creation gap.

**Not done:** did not bisect the Map.wz 93-vs-79 gap or independently verify the Keroben overcount
claim byte-for-byte against v83's String.wz — both plausible under the same
named-without-data/data-without-name pattern seen elsewhere, not proven id-by-id given time
budget. Sub-property byte sizes aren't tracked by the tool (only whole-`.img` size), so add/protect
byte totals read as 0 for String/Quest/parts of UI/Item/Etc even where real content exists —
cross-checked against raw file-size deltas instead where it mattered.

## Follow-up (2026-08-15): Map.wz 754-node drop — verdict [SUPERSEDED — see 02c below]

> **This section's conclusion is wrong.** Ticket 02c re-acquired v84 from the official installer
> and proved `wz-data/v84/Map.wz` is byte-identical to Nexon's own file. The archive is not
> damaged; v84 really did delete those maps. The reasoning below is preserved because the
> *evidence* (the 832-path list, the name resolution) is correct and still useful — only the
> "damaged/partial copy" inference from it is not.


Orchestrator flagged the Map.wz v83(5,616)→v84(4,862) net node drop as a possible source-integrity
problem, not a bookkeeping one, since it contradicts README.md's "v84 removed zero nodes" premise
and `wz-data/v84/` is the literal import source for tickets 04-09. Extended the same tool
(`docs/wz-baseline/tool/Program.cs`, `MapAudit`/`NpcSpotCheck`/`MapNameLookup`) rather than building
anything new.

**Verdict: option 3 — `porting-resources/wz-data/v84/Map.wz` is a damaged/partial copy. Not safe
to import from as-is.**

Evidence:
- 833 `Map.wz` image paths exist in v83-stock but not v84 (image-node count: v83 5,602, v84 4,848 —
  matches the 754 net drop). Cross-checked by leaf filename against the *entire* v84 tree
  (not just same-path): only 1 of 833 is found relocated elsewhere; **832 are genuinely absent
  from v84 anywhere.** Rules out option 2 (structural repack/renesting) — a renested file would
  still turn up somewhere in v84 by leaf name.
- Grouped by container: **811 of 832 are under `Map/Map9`**, 17 under `Map/Map0`, 4 under
  `Map/Map1`, 1 stray (`Obj/tutorial_jp.img`).
- Looked up all 832 ids by name in v83-stock's `String.wz/Map.img` (recursive search, not a
  region-layout guess — `Map.img` turned out to be organized by world name: `maple`, `victoria`,
  `ossyria`, `event`, `etc`, `jp`, `singapore`, etc, not by leading digit). **815 of 832 resolve
  to real, named GMS content**, e.g. `970030100` "Stage 1 &lt;Mano&gt;" through `970042717`
  "Stage 27 &lt;Pianus&gt;" (the full **Monster Carnival / Ola Ola battlefield stage series**, ~17
  instance rooms × ~28 boss stages), `925020610` "Mu Lung Dojo 6th Floor", and `109090001-4`
  "Sheep Ranch Lobby". These are long-lived, well-known pre-Big-Bang GMS minigame instance maps —
  not internal/test content, not plausible as a real single-patch removal. Only 17 (all tiny ids
  under `Map0`, e.g. `000000001`) have no String.wz entry at all, consistent with those being
  genuine internal/QA placeholder maps rather than evidence of damage.
- **Decisive cross-check against the project's own independent source:** `docs/work-plan/README.md`
  and the 771-node manifest's own headline table (built from maplestory.io's v83/v84 id lists, a
  source independent of our local WZ copies) states map removed=0 for the real v83→v84 patch —
  every v83 map id is claimed to still exist in real v84. Our local `wz-data/v84/Map.wz` is
  missing 815 *named, real* v83 map ids. Since an independent source says those ids exist in the
  real v84 and our local copy disagrees, **the local copy, not the historical record, is wrong.**
  This also revises the scope doc's earlier "Nexon repacked/recompressed the archive" explanation
  for Map.wz's size anomaly (606.0→599.8 MB) — recompression doesn't delete named nodes; the size
  drop is better explained by this same missing content.

Files: `docs/wz-baseline/map-v83-only-audit.txt` (full 832-path list, grouped counts),
`docs/wz-baseline/map-missing-names-v83.txt` (all 832 ids resolved against v83 String.wz).

This also closes the 93-vs-79 map add-list gap opened earlier: it does not fall out for free from
this audit (the gap is on the *add* side, v83 vs v84 union of new ids; this audit is about the
*missing* side, v84 vs v83). Left open as before — not force-explained.

**Npc `9000071` (Keroben) spot check:** confirmed **not present** in v83-stock's `Npc.wz` at all
(`Npc.wz/9000071.img` absent). This is a real "manifest-only, no v83 data" entry, not a
"named-but-no-data" case — Keroben is genuinely new in v84, not an overcount artifact.

**Action needed before ticket 03 touches Map.wz:** re-extract or re-download
`porting-resources/wz-data/v84/Map.wz` from `GMSSetupv84.exe` (already on disk at
`porting-resources/clients/`) and re-run this diff before treating it as the authoritative
Map.wz import source. Every other WZ file's v83/v84 copies were not re-verified against this same
leaf-name-anywhere test in this pass — only Map.wz was in scope per the orchestrator's ask — so a
quick repeat of the same `MapAudit` check against the other nine files before ticket 03 starts
would be cheap insurance, not yet done here.

## 02c (2026-08-15): source-integrity repair — verdict **(b)**

**`porting-resources/wz-data/v84/` is authentic and safe to import from. Do not re-acquire it.
But `docs/work-plan/README.md`'s founding premise — "v84 removed zero nodes, so every import is
purely additive" — is false, and must be corrected.**

### Provenance

`wz-data/v84/` was extracted from `clients/GMSSetupv84.exe` by carving its two spanned MSZip CAB
volumes (`EVAN-DUALBLADE-SCOPE.md` §8 says so; the byte layout confirms it). The installer is a
PE with a 1,839,733,200-byte appended blob at file offset 6,553,600; `MSCF` magic sits at
6,553,734 (vol 1, 1,048,576,000 B) and 1,055,129,734 (vol 2, 791,157,066 B). Carved back out with
both volumes present, `7z` reports **52 files, "Everything is Ok"** — no volume-span error, no
CRC error.

Neither hypothesis in the brief holds:

- **Not a partial/interrupted extraction.** All 11 `.wz` files in `wz-data/v84/` are
  **SHA256-identical** to a fresh extraction from the installer. Not just size — full hash.
  `Map.wz` = `38B9AEBA8E585F1EDDDD9C53BB4DC4713A83BF4F0010069505409774E6D5CF99`, 628,959,453 B in
  both. The CAB *directory itself* declares `Map.wz` as 628,959,453 B, so that is the size Nexon
  shipped; there is nothing to truncate.
- **Not a stripped private-server distribution.** It came out of the retail
  `GMSSetupv84.exe` (NGMSetup / "Nexon Game Manager", signed, `SECURITY` data dir present).

The **v83 baseline was verified the same way** and is also authentic: `v83-stock/Map.wz` is
SHA256-identical (`A0657907C42962D5B8A38E007DEFBCB9BA4F8A61BC32884E9F8C2BC1CB0EBEBE`) to
`GMSSetupv83.exe`'s copy, and all ten `v83-stock` file sizes match that installer's CAB directory.
`UI.wz`'s odd Feb 21 timestamp (vs Feb 17 for everything else) is genuine — Nexon rebuilt `UI.wz`
and `Patcher.exe` on Feb 21 and shipped both in the v83 installer.

Two incidental facts, both harmless: the v84 installer ships `TamingMob.wz` at **797 bytes**, and
so does the live Ezorsia client and the v83 installer — that is normal for this era, not a stub.
And `Base.wz` is 6,540 B in both builds.

### Verdict: (b) — the fresh copy lacks the 832 paths too

Since the fresh extraction is bit-for-bit the same file, it necessarily lacks them. Confirmed
independently of both our local copies **and** our WZ reader, via maplestory.io's own GMS dumps:

| id | name (v83) | GMS/83 | GMS/84 | GMS/92 |
|---|---|---|---|---|
| `970030100` | Stage 1 &lt;Mano&gt; | full map data | **404** | **404** |
| `925020610` | Mu Lung Dojo 6th Floor | — | **404** | — |
| `109090001` | Sheep Ranch Lobby | — | **404** | — |
| `100000000` | Henesys (control) | — | 200 OK | 200 OK |

The control proves the GMS/84 and GMS/92 datasets are live, so the 404s are absence, not an
outage. **GMS v0.84 genuinely deleted ~832 map images, and they were still gone in v0.92** — a
permanent removal, not a one-patch blip and not a repack.

This also settles the reader question: `map-v83-only-audit.txt` is *correct*. It was the inference
drawn from it that was wrong.

Byte accounting corroborates: v84's `Map.wz` is 6,485,442 B smaller than v83's while adding
2,772,832 B of new images — so ~9.3 MB removed across 832 images, ~11 KB each, exactly the weight
of small instance rooms.

### What this changes

1. **README.md's "removed zero nodes / purely additive" premise is false and should be edited.**
   It was derived from maplestory.io id-list *counts*, which evidently did not surface map
   removals. Everything built on "v84 ⊇ v83" needs re-reading with that in mind.
2. **Tickets 04–09 are not blocked.** They import v84's *additions* into the live client and never
   delete — so maps v84 dropped simply stay in Ezorsia's tree, which is the desired outcome. The
   operational rule was already "merge, never swap whole WZ files"; this is one more reason for it,
   not a new constraint. Wholesale-replacing `Map.wz` with v84's would now provably destroy ~832
   working maps (Mu Lung Dojo, the Stage/boss series, Sheep Ranch) on top of Ezorsia's 2.9 MB.
3. **`v83-stock/` is missing `Reactor.wz`** — the v83 installer ships it (54,133,811 B) but the
   baseline tree never got it, which is why `SUMMARY.md` reads `MISSING` / `add=0` for Reactor and
   why that row's add-list is unusable. Extracted to
   `porting-resources/wz-data/v83-reactor/Reactor.wz`; move it into `v83-stock/` and re-run the
   diff to get a real Reactor add-list. Left outside `v83-stock/` deliberately so it could not
   perturb another agent's in-flight run.

### Not done

- **Only `Map.wz` removals were characterised.** The other ten files each have a *net* node gain,
  but net gain does not rule out removals. The tool has no per-file "v83-only" list (`MapAudit` is
  `Map.wz`-only), so this is unmeasured, not measured-clean.
- **`ManualPatcherv84.exe` was not run.** It was the fallback route and became unnecessary once
  the full installer gave a byte-exact match; it is also GUI-driven, so per the destructive-action
  limits it was not clicked through. It would only re-confirm bytes already confirmed against the
  official full installer.

## 02f (2026-08-15): the seven unbaselined WZ files — acquired and baselined

`v83-stock/` and `v84/` held only 10 / 11 `.wz` files. Both retail installers actually ship **17**.
The seven missing ones are now carved out of the installers and added — **new files only, nothing
existing was touched or overwritten**.

### CAB offsets (v83 derived here; v84 confirms §02c)

| installer | vol 1 offset | vol 1 size | vol 2 offset | vol 2 size |
|---|---|---|---|---|
| `GMSSetupv83.exe` (1,763,606,856 B) | 3,371,141 | 1,048,576,000 | 1,051,947,141 | 711,654,917 |
| `GMSSetupv84.exe` (1,846,289,344 B) | 6,553,734 | 1,048,576,000 | 1,055,129,734 | 791,157,066 |

Both volumes must be carved as siblings named `MapleStory_1.cab` / `MapleStory_2.cab` — the name is
in vol 1's `szCabinetNext` field (flags=2 NEXT_CABINET; vol 2 has flags=1 PREV_CABINET, iCabinet=1).
`7z l` reports 51 files (v83) / 52 files (v84), "Everything is Ok", no span or CRC error. The exe was
**never executed**; only carved.

### Files added

| path | bytes | SHA256 |
|---|---|---|
| `wz-data/v83-stock/Base.wz` | 6,540 | `83FA0A8C2E11F8CBC4E92F46B5F9E8137FB1D0F7DD4B6CDC7EA337087C854CF4` |
| `wz-data/v83-stock/Effect.wz` | 63,334,965 | `3504500E6891895F747CBAC9BD707F8FAF0815C9D0DF79ECD468C6B15B33D660` |
| `wz-data/v83-stock/List.wz` | 13,336 | `7F9B67D010D8901F81B541B4C611F2FFA0B166001E4762D44FEDAB6AB7DFAD8B` |
| `wz-data/v83-stock/Morph.wz` | 6,204,606 | `9BF57995EFCD331F23E8FD0FF818E00EC159AB1995F8D9BCA7EF856E301F6782` |
| `wz-data/v83-stock/Reactor.wz` | 54,133,811 | `BE1573DC3461298906A35AAA8736C4097F0AB81055CFADA640DE72FA6774AD94` |
| `wz-data/v83-stock/Sound.wz` | 363,261,964 | `BC6570D39AE1C021AF433616ED4EC5F0C8917513B63D28536D116AAC74BDFD76` |
| `wz-data/v83-stock/TamingMob.wz` | 797 | `D23604F70C25CABD83E5C30A2ED9390BA1078C0966FD7D76A4ADFC03CB2CAE0D` |
| `wz-data/v84/Base.wz` | 6,540 | `08112B086BE4131756341B569D2059DA6D00B2F75D1BEEBDCBE3B8F73E7E661E` |
| `wz-data/v84/Effect.wz` | 78,068,632 | `4E42789E5C8E2224A49D1C79B97C8E7FF4C98A89DF9AB92672757766E3058C3A` |
| `wz-data/v84/List.wz` | 13,336 | `7F9B67D010D8901F81B541B4C611F2FFA0B166001E4762D44FEDAB6AB7DFAD8B` |
| `wz-data/v84/Morph.wz` | 6,322,806 | `F4E0CA1026153B08BB0A547B21DDC1C947B2E93EC20442156F37C23DD6C16C1E` |
| `wz-data/v84/Sound.wz` | 359,284,288 | `947139F27B85A6F9C0C680F59F974289AFA7BEC03C688F7C443B8A5DD4E9A2BD` |
| `wz-data/v84/TamingMob.wz` | 797 | `20F06862CF1E420EC8E0096BF58E7343EA538673BE5F0BEE653431AA671EE72C` |

Every size matches the installer's own CAB directory entry. **No hash mismatch against any
pre-existing file** — nothing pre-existing was compared-and-replaced, because nothing pre-existing
collided except `Reactor.wz`, handled below. `v84/Reactor.wz` (54,769,939 B,
`0FCBC377…A304C098FC`) was already present and was left alone.

`v83-reactor/Reactor.wz` (parked by 02c) is **SHA256-identical** to the installer's copy, so it was
moved into `v83-stock/` and the `v83-reactor/` directory removed. The diff tool's default v83 root
no longer needs the `;`-separated second directory.

### Re-run of the diff tool

Full 3-tree × 18-file sweep re-run in place; `SUMMARY.md` regenerated. 55,343 images parsed,
3 parse failures. New rows are all real, no `MISSING`:

| wz | v83-stock | v84 | live | add | removed | protect | mod v83→v84 |
|---|---|---|---|---|---|---|---|
| Base.wz | 322 | 322 | 322 | 0 | 0 | 0 | 0 |
| Effect.wz | 400 | 428 | 400 | 20 | 0 | 0 | 5 |
| Morph.wz | 508 | 565 | 508 | 25 | 0 | 0 | 7 |
| Reactor.wz | 2,560 | 2,594 | 2,565 | 6 | 0 | 7 | 0 |
| Sound.wz | 2,480 | 2,540 | 2,480 | 62 | 2 | 0 | 6 |
| TamingMob.wz | 14 | 14 | 14 | 0 | 0 | 0 | 0 |
| List.wz | OPEN-FAILED | OPEN-FAILED | OPEN-FAILED | — | — | — | — |

- **`List.wz` is not a WZ archive.** All three trees fail identically (`WZ header FStart is outside
  the file`) — it is a flat 13 KB plain-list file, and v83 and v84 ship byte-identical copies
  (same SHA256). Nothing to diff; the row is left visible rather than suppressed.
- **`Sound.wz/BgmGL.img` fails to parse in all three trees** (`InvalidDataException: WZ extended
  property exceeds its declared block`) — a MapleLib limitation, not a damaged file, and it fails
  symmetrically so it biases nothing. Left visible in `SUMMARY.md`.
- Sound.wz removals: `BgmJp.img/FirstStepMaster`, `BgmJp.img/Hana` — JP-only tracks, harmless.
- New-area content confirmed present: `Sound.wz/Bgm00.img/DragonDream`, `Bgm14.img/DragonRider`;
  `Effect.wz/BasicEff.img/DragonChanged`, `dragonFury`, `Direction4.img`, `SetEff.img/101`-`115`;
  `Morph.wz/0050`-`0053.img` plus a `fly2`/`fly2Move`/`fly2Skill` triple added to every existing
  morph image (Evan's flying-mount morph states).

### Mounts: `TamingMob.wz` is NOT where mounts live — ticket 05's premise is wrong

`TamingMob.wz` is **797 bytes in v83, v84 and the live client** — seven near-empty placeholder
images `0001.img`-`0007.img`. It carries no mount definitions in this era and diffs to `add=0`.
(v83's and live's copies are byte-identical; v84's differs only in header/offset bytes.)

Mounts actually live in **`Character.wz/TamingMob/`**, cross-referenced by an `info/tamingMob`
integer that indexes into `TamingMob.wz`'s placeholder images.

| tree | `Character.wz/TamingMob` images | `String.wz/Eqp.img/Eqp/Taming` names |
|---|---|---|
| v83-stock | 47 | 33 |
| v84 | **55** | **39** |
| live client | 47 | 47 (14 of them literal `MISSING NAME` placeholders) |

**v84 adds 8 mounts over v83**, all in the `1932xxx` block, all absent from the live client:
`01932006`, `01932007`, `01932008`, `01932009`, `01932011`, `01932018`, `01932019`, `01932020`.
Verified as real mount equips, not stubs — e.g. `01932006.img/info` = `islot=Tm, vslot=Tm,
reqLevel=13, tamingMob=6, tradeBlock=1, notSale=1, only=1`, with the full
`walk1/walk2/stand1/stand2/tired/jump/prone/ladder/rope/fly` animation set. They have **no
`String.wz` name entry in either stock tree** — unnamed/GM-side mounts.

Separately, v84 **names 6 mount ids whose sprites already existed in v83**: `1902040`/`1902041`/
`1902042` = "Stage 1/2/3 Dragon" and `1912033`/`1912034`/`1912035` = their saddles. That is
**Evan's Mir**, the player-facing new mount of this patch — the art shipped early in v83, only the
`String.wz` naming is new. A presence-only Character.wz diff cannot see it; the `String.wz` add-list
can.

**Where ticket 05 should look:**
1. `Character.wz/TamingMob/019320{06,07,08,09,11,18,19,20}.img` — the 8 genuinely new sprites
   (`docs/wz-baseline/add-list/Character.txt`, lines 140-147).
2. `String.wz/Eqp.img/Eqp/Taming` — import the 6 new Evan/Mir names; the sprites are already there.
3. `Morph.wz` `fly2`/`fly2Move`/`fly2Skill` on every morph image — the flying-mount morph states.
4. **Not** `TamingMob.wz`. Copying it would achieve nothing.

### Not done

- Did not diff `Sound.wz/BgmGL.img` (unparseable by MapleLib in every tree).
- Did not chase what the 8 unnamed `1932xxx` mounts *are* in-game — they have no `String.wz` entry
  in v83, v84, or the live client's stock rows, so there is no name to recover from WZ data alone.

### Reproducing this (recipe shared by 02c and 02f)

Carve both `MapleStory_1.cab` / `MapleStory_2.cab` from the offsets in the 02f table into one
directory (both volumes must be siblings and carry those exact names, or spanned files break),
then `7z x MapleStory_1.cab -oDEST <files...>`. 02f's scratch dir was deleted after use.

From 02c: `wz-data/v84-clean/` was created, hash-compared 11/11 against `wz-data/v84/`, and then **deleted**
— keeping 1.4 GB of provably identical bytes would only create ambiguity about which tree is
authoritative. `wz-data/v84/` is the one. The carved CAB scratch dirs were deleted too. To
regenerate any of it: carve `MapleStory_1.cab` / `MapleStory_2.cab` from the offsets above into
one directory (both volumes must be siblings and carry those exact names, or spanned files break),
then `7z x MapleStory_1.cab -oDEST <files...>`.
