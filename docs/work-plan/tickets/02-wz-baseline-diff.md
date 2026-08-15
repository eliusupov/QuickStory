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
