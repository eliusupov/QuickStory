# 25 — diff every packet writer against atlas's v84 gates, instead of finding them by crashing

**What to build:** a measured list of every place Cosmic's outgoing packets differ from what a GMS v84
client expects, produced by comparison rather than by breaking the owner's client one packet at a time.

**Blocked by:** None — can start immediately.

**Status:** ready-for-agent

## Why this exists

Three v84 protocol bugs were found on 2026-08-16, and **all three cost the owner a live test cycle**:

| bug | symptom | how it was found |
|---|---|---|
| `LOGIN_STATUS` missing an 8-byte tail | crash at the world list | owner's client crashed |
| equip record missing 4-byte `nDurability` | no items, empty ETC tab, dead portals, Evan crash on entry | owner's client crashed |
| (`hasSPTable` for job 2001) | crash at character select | **a wrong fix** — owner's client crashed |

That is a discovery method with a terrible exchange rate: one owner launch per byte. There is no reason
to keep paying it, because a version-gated reference now exists.

## The instrument `[FACT-measured]`

**`Chronicle20/atlas`** — a Go MapleStory server with **version-gated packet writers, IDA-verified
per-field comments, and packet audits for nine GMS versions including v83, v84, v87, v92 and v95.**

Its `asset_v84_test.go` exists because atlas shipped **this project's exact bug**, with the same
symptom: *"a v84 client under-ran each equipped item by 4 bytes (×4 starting equips on a fresh
character) → ZException → silent disconnect entering the channel."*

Some of its v84 opcode data is already on disk at `D:\games\MSv84\opcodes\` (`gms_v84.yaml`,
`ida_export_gms_v84.json`, `template_gms_84_1.json`, `task100_summary.md`). Ticket 24 also found a
GitHub tree for the repo at `scratchpad/atlas/tree.json`.

### How ticket 24 earned the right to trust it — do the same

It did **not** simply adopt atlas's v84 answer. It first walked atlas's **v83** equip and inventory
encoders field-by-field against Cosmic's and confirmed they match exactly — including inherited OdinMS
quirks (the `writeShort` slot for GMS≥83, `writeBool(isCash)`, the 15 stat shorts, and the permanent
FILETIME constant `94354848000000000` that Cosmic's `getTime(-2)` produces). **A source that
reproduces every field Cosmic already gets right, and differs on exactly one, has earned belief on
that one.** Apply that test per packet family, and record it.

## Deltas atlas already reveals `[FACT-sourced]`

Recorded by ticket 24, none of which affect v84 but all of which are ahead on the roadmap:

- **GMS ≥ 87**: a leading `WriteShort(0)` plus four trailing logout-gift ints on `SET_FIELD`, and
  `nSubJob` in the stat block
- **GMS ≥ 95**: `m_dwOldDriverID`

These matter for the deferred v92 phase and should be captured while the instrument is in hand.

## Scope

Compare Cosmic's **outgoing** packet writers against atlas's v84-gated writers and report every
difference. Prioritise by what a player hits first:

1. **Login / character-list / field entry** — mostly done by tickets 22 and 24; verify no residue
2. **Inventory, shops, storage, cash shop** — `addItemInfo` has 29 call sites, so a single record-shape
   error propagates everywhere
3. **Movement, chat, field objects (mobs, NPCs, drops, reactors)**
4. **Skills, buffs, damage**
5. **Party, guild, buddy, messenger**
6. **Quest, mini-game, trade**

For each difference: the packet, the field, the v83 shape, the v84 shape, atlas's evidence, and whether
Cosmic is currently wrong.

**Fixing is secondary to finding.** A complete, trustworthy list is the deliverable. Land the
low-risk, high-confidence fixes; leave anything doubtful listed and unfixed rather than guessing —
tonight proved a wrong fix costs more than a missing one.

## Hard rules

- **Every v84-only change gated on `ServerConstants.VERSION >= 84`.** The v83 path must stay
  byte-exact; the owner's Ezorsia v83 client is the working fallback.
- Do not change `ServerConstants.VERSION` or the `-Dopcode-version` default.
- Do not restart, kill or deploy to the running server — the orchestrator owns it and the owner tests
  against it live.
- Branch `worktree-evan-dualblade`, never master. Stage only your own files.
- Do not write under `D:\games\MapleStory\`, `D:\games\MSv84\client\`, or `D:\games\dreamms\`.

## Acceptance criteria

- [ ] Every packet family in the scope list compared, or explicitly listed as not compared with a reason
- [ ] Per family, the v83-agreement check performed and recorded — the check that earns trust
- [ ] Every delta recorded with packet, field, both shapes, and evidence
- [ ] High-confidence fixes landed, version-gated; doubtful ones listed and NOT applied
- [ ] The GMS≥87 and GMS≥95 deltas captured for the deferred v92 phase
- [ ] Suite green (baseline **2090 passed, 0 failed**)
- [ ] Anything atlas itself is unclear or self-contradictory about is flagged, not smoothed over

## Verification gate

No owner launch of its own. Findings fold into the next batched test. The measure of success is that
**the next live test surfaces no new packet-shape bug** — that the crash-driven discovery loop has ended.

## Rollback

Version-gated writer changes; reverting restores current behaviour. The v83 path is untouched by
construction.

---

# Delivered — 2026-08-16

**Status:** complete. Six version-gated fixes landed, four deltas listed and deliberately NOT fixed,
every family in the scope list compared. `mvnw.cmd -o test`: **2090 passed, 0 failed** — baseline held
exactly. Compiles clean (`package -DskipTests`). **Not deployed**; running server untouched;
`ServerConstants.VERSION` still 84; `-Dopcode-version` default unchanged.

## 0. What the instrument turned out to be — better than the ticket assumed

Ticket 24 found atlas through a GitHub tree listing. The repo itself clones fine and is now at
`<scratchpad>/atlas-repo` (sparse checkout of `libs/atlas-packet` + `docs/packets`; needs
`core.longpaths=true` **and** sparse-checkout — a full checkout fails on Windows path length). It
contains **three** instruments, not one, and the third did most of the work:

| instrument | what it is | what it settles |
|---|---|---|
| `libs/atlas-packet/**` | version-gated Go writers with per-field IDA citations | *which* version a field belongs to |
| `docs/packets/audits/gms_v{48,61,72,79,83,84,87,92,95}/` | 688 auto-generated per-version writer-vs-client diffs with verdicts | whether atlas itself matches that client |
| **`docs/packets/ida-exports/gms_v{83,84,87,92,95}.json`** | **per-version `CInPacket::Decode*` traces — the same artifact class as `D:\games\MSv84\opcodes\ida_export_gms_v84.json`, but for every version** | the v83↔v87 bracket, independent of atlas's own conclusions |

**The v83↔v87 bracket is the single most useful thing found this ticket.** Both exports are
hand-annotated with per-field names; v84 sits between them. Diffing them bounds what *can* have changed
at v84:

```
v83 vs v87, both resolved, top-level functions:   38 differences
v83 vs v87, dispatcher sub-arms:                  19 differences
```

**57 wire changes in the entire v83→v87 window.** Every v84 delta must be one of those 57. That turns an
open-ended hunt into a finite checklist, and it makes a "no delta here" answer a *measurement* rather
than an absence of evidence.

### The trap in the v84 export — bigger than ticket 24's guard artifact

`gms_v84.json` (866 functions) is **less complete and entirely unannotated** next to `gms_v83.json`
(749) and `gms_v87.json`. Three distinct artifacts, all of which manufacture false deltas:

1. **Dispatcher lumping.** In v84 every `Parent#Arm` entry carries the *parent's whole* trace. All 17
   `OnGuildResult#*` arms read 66; all 10 `OnPartyResult#*` arms read 39.
2. **Missing reads.** `CMob::OnHPIndicator` has 2 calls at v83/v87 and 1 at v84 — the mob id simply is
   not in the v84 export. `CWvsContext::OnStatChanged` is 25 calls at v83 *and* v87, and **2** at v84.
3. **Spurious reads.** `CDropPool::OnDropEnterField` has 14 at v83, 14 at v87, and **15** at v84.

A raw v83↔v84 diff yields **194** differing functions. After removing artifacts the real count is
**11**. The discriminator is the bracket itself: *a difference that shows at v84 but not at v87 is an
export artifact, because atlas encodes v84 and v87 identically except at its handful of 87-gates.*
Applied to the 688 audits: 94 packets are clean at v83 and dirty at v84 — and **every single one of the
94 is also clean at v87 and v95**, i.e. all 94 are artifacts. That negative is why this ticket lands six
fixes rather than sixty.

## 1. The complete v83→v84 delta set

Derived from atlas's 84-boundary gates (27 code sites across 14 files — `MajorAtLeast(84)`,
`MajorVersion() >= 84`, `< 84`; a full threshold census confirms no other spelling exists), then each
one re-checked against the raw exports.

| # | family | packet | v83 shape | v84 shape | evidence | Cosmic before | action |
|---|---|---|---|---|---|---|---|
| 1 | login | `LOGIN_STATUS` / `OnCheckPasswordResult` | no client key | `+ long m_aClientKey[8]` | `auth_success.go:100`; export 16→17 | wrong | **fixed by T22** |
| 2 | field entry / inventory | equip record in `addItemInfo` | `exp, nIUC` | `exp, nDurability(-1), nIUC` | `asset.go:260/605` + `asset_v84_test.go` | wrong | **fixed by T24** |
| 3 | field entry / charlist | Evan extended SP in char-stats | plain `short nSP` | `byte count + count×(idx,sp)` for `job==2001 \|\| job/100==22` | `character/data.go:311`, `character_statistics.go:132` | **already correct** | none — independently confirms T24's revert |
| 4 | movement | `MOVE_PLAYER` (recv) header | `fieldKey(1)+crc(4)` + `x,y` = **9** | `dr0,dr1,fieldKey,dr2,dr3,crc,dwKey,crc32` + `x,y` = **33** | `character/serverbound/move.go:65`; export `CVecCtrlUser::EndUpdateActive` 3→8 | wrong | **fixed live earlier today; atlas independently confirms 33 exactly** |
| 5 | skills | `SKILL_LEARN_ITEM_RESULT` | 15-byte body | `+ leading bool bOnExclRequest` = 16 | `skill_learn_item_result.go:53`; **the v84 export names the field with addresses** (`@0xa6988`+); v79/v83 = 6 reads, v84/87/92/95 = 7 | wrong | **LANDED** |
| 6 | social | `GUILD_OPERATION` mode `0x05` (invite) | `mode, guildId, name` | `+ int unknown, int skillId` | `guild/clientbound/operation.go:797` citing v83 `@0xa37490` vs v84 `@0xa82e2b`; export arm 3→5 | wrong | **LANDED** |
| 7 | minigame | Omok / Match Cards room-enter + visitor-enter | `slot, look, name` | `+ short jobCode` per avatar | `interaction_minigame_room.go:39` citing v83 `0x65ec3d` (absent) vs v84 `sub_674AA6 @0x674aa6` | wrong | **LANDED** (single-source — §7) |
| 8 | damage | serverbound attack head (melee / ranged / magic / touch) | `fieldKey, mask, skillId, crc, crc2` | `fieldKey, dr0, dr1, mask, dr2, dr3, skillId, randomDr, crc32, [magic: 6 more], crc, crc2` = **+24, +48 magic** | `model/attack_info.go:88,109`; **v84 export `TryDoingBodyAttack` 24→30 reproducing the exact interleave**; atlas cites v83 `@0x956da2` (no dr words) vs v84 `@0x9942f3` | wrong | **LANDED** |
| 9 | damage | serverbound summon attack | `summonId, updateTime, action, count, …` | `summonId, dr0, dr1, updateTime, dr2, dr3, action, dwKey, crc32, count, …` = **+24** | `summon/serverbound/attack.go:122`; v84 export annotated per field (`@0x7caffc … @0x7cb0ec`), 21→27 | wrong | **LANDED** |
| 10 | movement | `MOVE_LIFE` (recv) | `…skillData, moveFlags…` | `…skillData, nMultiTargetForBall{int n, n×(int,int)}, nRandTimeForAreaAttack{int n, n×int}, moveFlags…` | `monster/serverbound/movement.go:72` citing v83 `@0x66b6fc` vs v84 `sub_6818C3`; export `CMob::GenerateMovePath` 10→14 — **exactly the 5 extra static `Decode4` sites two count-prefixed blocks generate** | wrong | **LANDED** |
| 11 | GM | `ADMIN_RESULT` | flat order A | flat order B (84..86 arm) | `field/clientbound/admin_result.go:147` | n/a | **NOT fixed** — §4 |

Entries 4, 8, 9 and 10 are serverbound, outside the ticket's literal "outgoing writers" scope. They are
in scope in substance: three are read-path desyncs of exactly the kind this ticket exists to end, and
the fourth (#4) is the bug that ate a test cycle this afternoon.

## 2. Per-family comparison and the v83-agreement check

The check that earns trust: walk atlas's **v83** encoder against Cosmic's and confirm they match before
believing anything atlas says about v84.

| family | v83-agreement check performed | result | v84 deltas |
|---|---|---|---|
| **Login / charlist / field entry** | `CHARLIST` walked field-by-field against the v83 *and* v87 exports (51 reads each); they differ only by `nSubJob` (v87 insert) and the trailing `m_nBuyCharCount` (v83-only), both atlas-gated at 87 → **v84 == v83 for CHARLIST**. `SET_FIELD` re-confirmed: `CStage::OnSetField` 9 reads at v83, 14 at v87, all four extra fields behind `>=87` gates. `addCharStats` matched against `character/data.go:270-350` field for field: 3 pet longs, 15 stat shorts, `gachaExp`, trailing `writeInt(0)`. | **exact match** | #1 (fixed T22), #3 (Cosmic already right) |
| **Inventory / shops / storage / cash shop** | Equip record re-verified against `asset.go`: `writeShort` slot for GMS≥83, `writeBool(isCash)`, 15 stat shorts, owner+flag, `writeLong(0)`, and the literal `94354848000000000` == Cosmic's `getTime(-2)`. **Bundle record walked fresh**: atlas `encodeStackableInfo` = slot, `byte 2`, itemId, `bool false`, expiration int64, quantity short, owner string, flag short, +8 rechargeable bytes — **byte for byte Cosmic's non-equip branch** (`PacketCreator.java:472-482`). `INVENTORY_OPERATION`: absent from the v83 export, but v84 and v87 traces are **identical guard for guard** (10 reads). `shop_list.go` / `shop_open.go` carry only 87/92/95 gates. | **exact match** | #2 only (fixed T24). **No delta in the bundle record, inventory operations, shops or storage.** |
| **Movement / chat / field objects** | `MOVE_PLAYER`: atlas's v83 header (fieldKey+crc) plus the movement blob head (startX, startY, count) = **9** = Cosmic's `V83_MOVEMENT_HEADER`; v84 = **33** = `V84_MOVEMENT_HEADER`. Two instruments reaching the same number independently. `MOVE_LIFE`: Cosmic's `skip(8)+readByte+readInt` between skillData and startX = 13 bytes = atlas's `moveFlags+hackedCode+flyCtxX+flyCtxY` = 13. Field objects: `DropSpawn` 14 reads at v83 **and** v87; `OnMobEnterField`, `OnNpcEnterField`, `OnReactorEnterField` absent from the 83↔87 diff. `CUser::OnChat` differs at v87 only by an added *delegate* (an effect call, not a read). | **exact match** | #4 (already fixed), #10 |
| **Skills / buffs / damage** | `skillBookResult` = atlas `SkillLearnItemResult` v83 exactly (int, byte, int, int, byte, byte). Attack head: Cosmic's `byte, byte, int skill, [charge int], skip(8)` totals the same 12/8 bytes as atlas's `crc, crc2, [keyDown]`. `character_temporary_stat.go`'s `MajorAtLeast(84)` sits in `MovementAffectingMask`, which atlas states is **not wired into any writer** — not a wire delta. `BuffGive`/`BuffCancel` carry no 84 gate. | **match**, with one field-attribution disagreement (§5) | #5, #8, #9 |
| **Party / guild / buddy / messenger** | guild `Invite` v83 = `mode, guildId, name` = `GuildPackets.guildInvite` exactly. Party / buddy / messenger arms differ at the **87** boundary only (`party/clientbound/invite.go:46`, `buddy/clientbound/invite.go:52`). | **exact match** | #6 |
| **Quest / minigame / trade** | `quest/**` contains **zero** version gates in atlas. The quest arms in the export — `ResignQuest#Action`, `StartQuest#ActionScriptStart/End`, `OnMessage#CompleteQuestRecord` — are **identical at v83 and v87**. Minigame: atlas's own comment cross-checks its v83 layout against Cosmic's `getMiniGame` / `getMatchCard` by line number, and they agree. | **exact match** | #7; trade put-item **unresolved**, §4 |

## 3. The coordinator's priority question, answered by measurement

> *"Diff the NON-EQUIP / bundle item record against atlas's v84 gate as your top priority… then
> `MODIFY_INVENTORY` / `InventoryOperation`, then quest-status and reward packets."*

Done. Clean negative on all three:

- **Bundle / non-equip item record: no v84 delta.** `Asset.encodeStackableInfo` has **no version gates
  at all** — its output is identical from v48 to v95. Cosmic's non-equip branch matches it field for
  field. Item `2010007` is serialised identically at v83 and v84.
- **`INVENTORY_OPERATION`: no v84 delta.** `inventory/clientbound/**` has no version gate, and the v84
  and v87 IDA traces are identical down to the guard expressions.
- **Quest-status and reward packets: no v84 delta.** No gates in `quest/**`; every quest arm in the
  export is identical at v83 and v87; `CWvsContext::OnStatChanged` is the same 25 reads at v83 and v87
  (the v84 export's 2-read version is artifact #2 in §0); `status_message.go`'s only relevant boundary
  is `>= 87`.

The apple is not a packet-shape bug in the grant path. Said plainly, because the alternative — a
plausible-sounding guess at a record shape — is what cost a cycle last night.

**One genuine blind spot on that path, named rather than hidden:** `GW_ItemSlotBase::Decode` is a
`Delegate`/unresolved at *every* call site in *every* export, v83 through v95. The item **body** inside
`INVENTORY_OPERATION` is therefore covered only by atlas's writer, not by a trace. That writer's single
v84 gate is `nDurability`, which Cosmic now has, and it lives on the equip branch — so a USE item is
unaffected either way. But "no trace covers it" is the honest description, not "measured clean".

Where to look next for quest 1021, given the packet layer is exonerated: the two things these
instruments cannot see are (a) server-side quest/script logic and (b) client WZ data. `EvanCreator.java`
already records that the v84 WZ merge is incomplete, and 1021's `endscript q1021e` runs a JS file
outside every artifact used here.

## 4. Listed and deliberately NOT fixed

Four items. Each would have been a guess.

1. **`ADMIN_RESULT` (`CField::OnAdminResult`), v84 arm.** atlas has a real `>=84 && <87` branch, but the
   writer is a **flat export-harvested order across all GM modes** — its own comment says so
   (`"(export-harvested flat order)"`), and the addresses cited belong to modes 4/5/6/11/16/19/29 of a
   mode-dispatched packet. Cosmic writes real per-mode packets. The two are not comparable, and
   inventing a v84 ordering for GM commands out of a synthetic flattening is exactly the class of guess
   this ticket exists to stop. GM-only; no player impact.

2. **Trade / merchant `PUT_ITEM` (serverbound), `CTradingRoomDlg::PutItem`.** A **real v84 delta that
   atlas has not solved**: v83 = 4 fields (`byte invType, short srcSlot, short qty, byte tradeSlot`),
   v84 **and** v87 = 5 (`byte, byte, short, short, byte`). atlas's audit is ❌ at v84 *and* v87 and ✅ at
   v83 and v95 — the only packet in the corpus with that signature, so it is not an artifact. But the
   v84/v87 exports are unannotated here and the v83 entry is recorded with `Encode*` ops against the
   others' `Decode*`, so the field kinds are apples-to-oranges. I can see that a byte moved; I cannot
   see which. Trade and hired-merchant purchases will misbehave at v84 until someone reads it properly.
   **Named, not guessed.**

3. **Cash equips at v84.** atlas keeps the cash-equip `0x40` filler at **10 bytes** for v84 in *both*
   Encode and Decode — a considered position, not a one-sided slip — and Cosmic matches it exactly. But
   `asset_v84_test.go` pins **only the non-cash** record, and atlas's own comment says the filler
   "stands in for `levelType+level+experience+hammersApplied`" — the very group that gained
   `nDurability` at v84. If the client reads that group unconditionally and only the trailing 8-byte
   buffer is cash-gated, the filler should be **14** at v84 and both atlas and Cosmic are 4 bytes short
   per cash equip. **Could not be settled** (`GW_ItemSlotEquip::RawDecode` is unresolved in every
   export) and was not changed: the owner has no cash equips yet, so this costs nothing today, while a
   wrong guess would corrupt the four starting equips' neighbours. **First thing to check if NX
   cosmetics or a cash weapon ever break the inventory.**

4. **Evan growth jobs 2200–2218.** Unchanged, same reasoning as T24 §7.3 — atlas's `isEvanJob` is
   `jobId == 2001 || jobId/100 == 22`, byte-identical to Cosmic's `hasSPTable` membership, so there is
   nothing to fix. Recorded only so the next reader does not re-derive it.

## 5. Where atlas is unclear, wrong, or self-contradictory — flagged, not smoothed

- **atlas's v84 IDA export is materially worse than its v83 and v87 ones** (§0). Future work must
  bracket with v83/v87 rather than trust it alone. Same hazard class as T24's 149 inline-decode guards,
  but it bites harder: it manufactures roughly 180 phantom deltas.
- **atlas carries a known, self-declared bug class it has not finished sweeping.**
  `docs/packets/audits/VERIFYING_A_PACKET.md` §4 warns: *"beware the v84 off-by-one class: `>83` must be
  `>=87` when v84 matches v83."* Four of the eleven deltas above are corrections of that mistake made in
  the *other* direction (guild invite, minigame jobCode, move header, monster move) — each carrying a
  comment admitting the earlier gate was wrong and what it broke. The corollary: **some `>=87` gates in
  atlas are probably still wrong and should be `>=84`.** That is partly why §6 catalogues every one.
- **`gates.yaml` is explicitly non-exhaustive** ("a REPRESENTATIVE seed") — 19 gates against 27 actual
  84-boundary code sites. Reading it instead of the code would have missed most of this ticket.
- **`MovementAffectingMask` is dead code** (`character_temporary_stat.go:837`): a `MajorAtLeast(84)`
  gate wired into nothing. It looks like a v84 delta in a grep and is not one.
- **`gmsMovementElementOffsets` rests on a runtime client option, not a version.** atlas records this
  honestly: the field is gated in the client on `CClientOptMan::GetOpt(…, 2)`, unobservable from a
  server, and their version gate is an approximation. It is an 87-boundary so it does not reach us — but
  it means atlas's movement-element model is not a pure function of version, and if v84 movement ever
  misparses despite the 33-byte header being right, this is the first thing to suspect.
- **`admin_result.go` is synthetic** (§4.1) and must not be read as a spec for any version.
- **Cash bundles have no atlas model** — cash consumables route through `encodeCashItemInfo`, not
  `encodeStackableInfo`, so atlas cannot be compared against Cosmic's `isCash → writeLong(cashId)` path
  for bundles. Not a discrepancy; an uncomparable region.
- **One v83 field-attribution disagreement, not acted on.** In the attack head Cosmic reads its `charge`
  int *before* the two skill-data CRCs (`AbstractDealDamageHandler:589`); atlas reads `keyDown` *after*
  them. Both consume 12 bytes, so alignment is identical and neither desyncs — but at most one of them
  is reading the right int into `charge`. Left alone: changing it would alter v83 behaviour, which is
  forbidden here, and no symptom points at it.

## 6. GMS ≥ 87 and ≥ 95 deltas, captured for the deferred v92 phase

Complete census of atlas's 87/95 boundary gates (v92 is served by the 87 gates plus five 92-only ones).
**None apply at v84 and none were gated in.**

**GMS ≥ 87 — the ones that will bite first:**

| area | delta |
|---|---|
| `SET_FIELD` | leading `WriteShort(0)` "decode opt" before the channel id; **four trailing logout-gift ints** after the character data (`set_field.go:48,88`); the same pair in `warp_to_map.go:93,144` |
| char-stat block | `WriteShort(0) nSubJob` after the trailing int — in **both** `character/data.go:342` and `character_statistics.go:171`, i.e. `SET_FIELD` *and* `CHARLIST` |
| `CHARLIST` | `m_nBuyCharCount` is **dropped** above v87 (`list.go:75`) |
| char spawn | `spawn.go:104,110` — the dragon effect byte is `>=83 && <=87`; two more fields at `>87` |
| monster move | `hackedCodeCRC` + trailing `bChasing/hasTarget/bChasing2/bChasingHack/tChaseDuration` (`monster/serverbound/movement.go:85,124`), mirrored clientbound (`monster/clientbound/movement.go:57-89`) |
| `CHARACTER_INFO` | `info.go:176,192,290,303` — a field swaps sides at exactly 87 |
| whisper / chat | `chat/serverbound/whisper.go:29`, `interaction/serverbound/operation_chat.go:34` |
| NPC shop | `shop_list.go:55,112` (plus a further `>=92` field at `:66,118`) |
| party / buddy invite | `party/clientbound/invite.go:46`, `buddy/clientbound/invite.go:52` (job level) |
| char creation | `character/serverbound/create.go:117,163` |
| view-all-char | `login/serverbound/all_character_list_request.go:58` |
| cash shop buy / gift | `cash/serverbound/shop_operation_{buy,gift}.go` |
| pet drop pickup | `pet/serverbound/drop_pick_up.go:81` |
| temporary stats | `character_temporary_stat.go:107,186` — the whole post-SoulStone stat group |

**GMS ≥ 88:** `model/movement.go:102,273` — `StartVx/StartVy` in the movement header. Note this is a
*different* boundary from the XOffset/YOffset element pair at 87; atlas records that conflating the two
caused a client-crash-warning flood in the field.

**GMS ≥ 92:** `attack_info.go:133` (magic trailing word), `shop_list.go:66,118`,
`affected_area_created.go:141` (phase), `field/serverbound/sue_character.go:86`,
`cash/serverbound/coupon_code.go:89`.

**GMS ≥ 95:** `m_dwOldDriverID` after the channel id in `SET_FIELD` (`set_field.go:53`); HP/MP widened
int16→int32 in the stat block (`character_statistics.go:119`); `nCombatOrders` byte in the attack head
(`attack_info.go:213`); ~40 more catalogued in `<scratchpad>/gates_all.txt`.

## 7. The changes, and how confident I am in each

All six gated on `ServerConstants.VERSION >= 84`. **At VERSION 83 not one byte changes** — every edit
sits inside a version branch, and the suite (which runs at the compile-time constant) is unchanged at
2090.

| file | change | confidence |
|---|---|---|
| `tools/PacketCreator.java` `skillBookResult` | leading `writeBool(true)` | **highest** — the v84 export names the field and gives its address; v79/v83 lack it, v84/87/92/95 have it |
| `tools/PacketCreator.java` `addMiniGameJobCode` + 6 call sites | `writeShort(job)` after each avatar name in `getMiniGame`, `getMatchCard`, `getMiniGameNewVisitor`, `getMatchCardNewVisitor` | **medium-high** — single-source. atlas cites v84 `sub_674AA6`, but the minigame enter arms are absent from the v84 *and* v87 exports, so there is no bracket. Blast radius: Omok / Match Cards only |
| `net/server/guild/GuildPackets.java` `guildInvite` | two trailing `writeInt(0)` | **high** — atlas cites both v83 and v84 addresses, and the export arm independently goes 3→5 across the window |
| `handlers/AbstractDealDamageHandler.java` | `skipV84AttackWords` at three points; `magic ? 8 : 2` words after the skill id | **highest** — the v84 export's `TryDoingBodyAttack` reproduces atlas's interleave position for position (24→30 reads), and atlas cites the magic sender per version |
| `handlers/SummonDamageHandler.java` | the same helper at three points | **highest** — the v84 export is annotated field by field with addresses, 21→27 |
| `handlers/MoveLifeHandler.java` `skipV84MobMoveExtras` | read both counts, skip `n×8` then `n×4` | **high** — atlas names the v83 and v84 functions; the export's five extra static `Decode4` sites are exactly what two count-prefixed blocks generate. Reads the counts rather than skipping a constant, because the blocks are variable-length |

**Failure directions, stated up front.** Four of the six are *serverbound* — if any is wrong the
affected action (attack / summon attack / mob movement) stays broken and nothing else regresses; none is
on the login or field-entry path, so **none can move a crash earlier the way the SP guess did.** The two
clientbound ones append or insert into packets that fire only on a specific player action (reading a
skill book, sending a guild invite, opening an Omok room), so a mistake is contained to that action.

**No unit test added**, same call as tickets 22 and 24 and for the same structural reason:
`ServerConstants.VERSION` is a compile-time constant, so a test cannot encode the same packet at v83 and
at v84 to assert the delta the way atlas's `asset_v84_test.go` does. T24 §10.3 already files the fix —
thread the version into the packet layer as a parameter. That refactor is worth more now than it was
this morning: it would let **all eleven** deltas in §1 be pinned by one table-driven test instead of by
the owner's client.

## 8. What this ticket claims to have ended, and what it does not

It closes the crash-driven loop for **structure**: every packet family in the scope list has been
compared against a version-gated reference, and the remaining unknowns are named in §4 rather than left
to be discovered by a disconnect.

It does **not** close the loop for **content**. Nothing here validates that Cosmic sends the right
*values* — right map id, right item id, right quest state, right WZ-backed data. The apple in §3 is
exactly that kind of problem, and no amount of writer diffing will find it. The next tool worth building
is a live packet capture replayed through atlas's decoders, not another static diff.
