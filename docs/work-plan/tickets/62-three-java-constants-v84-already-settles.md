# 62 - Three Java constants v84 already settles

**Class:** v84 parity
**Work rows:** R14, R15, R21 - `docs/work-plan/V84-WORK-ROWS.tsv`
**Blocked by:** None - can start immediately

Three unrelated defects that share one property: the value each needs is already recovered and
written down, so none of them is a derivation. Two are single-line table entries and one is a pair
of mode bytes read out of the v84 disassembly export. They are grouped because they are all
constants in `src/main/java` and one agent can land all three in one context window.

All three defects are still open in HEAD; all three were re-measured for this revision.

## R14 - Evan's three mount skills are absent from `buildSkillMounts()`

Skills **20011018**, **20011019** and **20011031** have no entry in the `skillId -> TamingMob item
id` map built at `StatEffect.java:147-187`. The map is read back by `StatEffect.skillMountItem` at
`:190` and consumed at `:1365`; a skill with no entry yields no sprite, so the mount renders
nothing.

The map's v84 block (`:173-181`) loops job prefixes `{0, 1000, 2000, 2001}` over offsets
`1025, 1027, 1028, 1029, 1030, 1037, 1038, 1039`. 1018, 1019 and 1031 are not in that list, so
Evan's three are genuinely absent. The gap is pinned today by
`V84EvanNodeTest.evansThreeV83EraMountsAreNamedButUnmapped`, **`:164-172`**.

### The codebase currently argues these are not derivable. Engage that before writing code.

`V84EvanNodeTest.java:154-161`, the javadoc on that same test, says:

> *"The id offsets do not transfer: Beginner numbers them 1017/1018/1019 and Legend/Noblesse
> 1019/1022/1023, and Evan's 1018 is named 'Yeti Rider' rather than 'Yeti Mount 2', so which sprite
> each one wants cannot be derived - it would be the same speculation F4 warned about."*

**That objection is right about offsets and wrong about names.** Measured:

| Evan skill | v84 `String.wz/Skill.img` name | resolved by | sprite |
|---|---|---|---|
| **20011031** "Balrog" | matches `Beginner.BALROG_MOUNT` (1031) **and** `Legend.BALROG_MOUNT` (20001031) | offset agrees across two independent job blocks, and the name matches both | **1932010** |
| **20011019** "Witch's Broomstick" | name-identical to `Beginner.WITCH_BROOMSTICK` (1019) | **name only** - the offset does *not* transfer, `Legend`'s 1019 is `YETI_MOUNT1` | **1932005** |
| **20011018** "Yeti Rider" | name matches nothing exactly | **neither** offset nor name resolves it | see below |

So the objection stands for **20011018** and is answered for the other two. Do not paper over it -
the commit must say which argument carried each id.

**20011018 is genuinely ambiguous, and it does not matter.** The two candidates are `1932003`
(`Beginner.YETI_MOUNT1`) and `1932004` (`Beginner.YETI_MOUNT2`). The offset rule would pick 1932004,
but the offset rule is exactly what the test javadoc disputes, so it is not evidence here. It is
moot: **`wz/Character.wz/TamingMob/01932003.img.xml` and `01932004.img.xml` are byte-identical apart
from the id string in their own node names** - both 418 lines, identical `info` and identical frame
geometry throughout. Either renders the same yeti. Pick one, record which in the commit, and say
that the choice was free because the two images are identical.

### Scope note: the constants do not exist yet

`constants/skills/Evan.java` declares **no** `YETI_MOUNT`, `WITCH_BROOMSTICK` or `BALROG_MOUNT` - the
test javadoc says so and it is correct. "Copy the v83 block at `StatEffect.java:149-160` verbatim"
is therefore not literally possible; the three constants have to be added to `Evan.java` first, or
the three entries written as bare ids with a comment. Either is fine; the ticket's effort estimate
must include it.

Reachable only via `!maxskill` today (`client/command/commands/gm2/MaxSkillCommand.java`), so no
live character is affected by the change.

## R15 - Cash gachapon result sends v83 mode bytes to a v84 client

`PacketCreator.java:6800` writes **0xE4** (`onCashItemGachaponOpenFailed`) and `:6807` writes
**0xE5** (`onCashGachaponOpenSuccess`) under `CASHSHOP_CASH_ITEM_GACHAPON_RESULT`. v84 moved both:
SUCCESS is **238 (0xEE)** and FAILED is **237 (0xED)**. Cash Shop Surprise therefore fails silently
against the v84 client - the packet is sent, the client does not recognise the mode, nothing happens
and nothing errors. Both are reachable from `CashShopSurpriseHandler.java:48` and `:54`.

### This path must NOT route through `cashShopMode()`

The earlier revision required both literals to go through that helper *and* to come out as
0xEE / 0xED. Those two requirements contradict each other and the second one is correct.

* `cashShopMode` is `ServerConstants.VERSION >= 84 && v83Mode >= 0x4B ? v83Mode + 3 : v83Mode`
  (`PacketCreator.java:3617-3619`). A uniform **+3**. It would produce **0xE7** and **0xE8**.
* It is also documented (`:3580-3583`) as the discriminator for
  **`CCashShop::OnCashItemResult`** - a different handler from
  **`CCashShop::OnCashItemGachaponResult`**. Wrong dispatcher, so its +3 has no reason to apply.
* The actual shift here is a uniform **+9**, not +3: FAILED `0xE4` -> `0xED` and SUCCESS `0xE5` ->
  `0xEE`. The two handlers moved by different amounts because v84 inserted a different number of
  arms ahead of each, which is precisely why one shared helper cannot serve both.

**Write a separate guard.** A local `ServerConstants.VERSION >= 84 ? 0xEE : 0xE5` at each of the two
sites is the whole change. If a helper is wanted, it must be a new one named for *this* handler, not
`cashShopMode`.

### Evidence

`D:\games\MSv84\opcodes\ida_export_gms_v84.json` names `CCashShop::OnCashItemGachaponResult` at
**0x47f8fc**, clientbound, with:

> *"mode(Decode1) unconditional. SUCCESS (**mode=238**): sn:DecodeBuffer(8, LARGE_INTEGER int64) +
> remain:Decode4 (int32 new quantity) + newItem:DecodeBuffer(0x37=55, GW_CashItemInfo,
> UNCONDITIONAL) + delegate to CUICashItemGachapon::OnCashItemGachaponResult for
> itemId/count/jackpot. FAILED (**mode=237**): no further reads, StringPool notice only."*

That is the right handler and not a lookalike: the payload shape matches ours field for field.
`onCashGachaponOpenSuccess` writes `long boxCashId`, `int remainingBoxes`,
`addCashItemInformation(...)`, then `int rewardItemId`, `byte rewardQuantity`, `bool bJackpot` -
and the delegate `CUICashItemGachapon::OnCashItemGachaponResult` at `0x9db918` reads exactly
`Decode4 itemId`, `Decode1 count`, `Decode1 jackpot`. Only the mode byte moved.

## R21 - Evan's Hall of Fame map is not recognised as one

`constants/game/GameConstants.java:352-369`, `isHallOfFameMap()`, has no case for **100030301**.
Because that case is missing, `getHallOfFameBranch()`'s `EVAN1 -> 21` arm at `:412-413` is
unreachable and an Evan PlayerNPC falls through to the custom branch at `:387`, which computes
`26 + 4*(mapid/100000000)` = **30**. One case line fixes it.

Two mechanical details the earlier revision got wrong:

* **`getHallOfFameBranch` takes two arguments**: `getHallOfFameBranch(Job job, int mapid)`
  (`:385`). Any test must call it as such.
* **There is no `MapId` constant for 100030301.** Every arm of the `isHallOfFameMap` switch is
  written as a `MapId.*` constant (`MapId.HALL_OF_WARRIORS` = 102000004, `MapId.PALACE_OF_THE_MASTER`
  = 140010110, and so on), so a `MapId.FOREST_HALL = 100030301` has to be added alongside the case.

100030301 is "Forest Hall", the Evan Lv.200 hall of fame - `PlayerNPC.java:69-71` records that v84's
`String.wz/Npc.img/9901910` and `Etc.wz/NpcLocation.img/9901910/0` both name it, and
`src/test/java/server/ForestHallRealLoad.java:41` already loads it.

Found while verifying the Forest Hall row; not previously tracked anywhere.

## Precedent

**R14.** The v83 block at `StatEffect.java:149-160` is the shape - three more entries alongside it.
The sprite ids are derived from ids already in the tree (`Beginner.BALROG_MOUNT`,
`Beginner.WITCH_BROOMSTICK`, `Legend.BALROG_MOUNT`) and from the two byte-identical TamingMob
images, not invented.

**R15.** The IDA export above. For the *javadoc style* on the new guard - what evidence to record
and how - copy `PacketCreator.java:3570-3616`, the block over `cashShopMode`. Copy its style, not
its helper.

**R21.** Aran's `MapId.PALACE_OF_THE_MASTER` case in the same switch is the analogue arm - same file,
same method, same one-line shape, and it likewise has a `MapId` constant behind it.

## Acceptance criteria

- [ ] `StatEffect.skillMountItem(20011031)` returns `1932010`, `skillMountItem(20011019)` returns
      `1932005`, and `skillMountItem(20011018)` returns `1932003` or `1932004` - whichever the commit
      names, with the "the two images are byte-identical" reason recorded.
- [ ] **`V84EvanNodeTest.evansThreeV83EraMountsAreNamedButUnmapped` (`:164`) is inverted** - it
      asserted `assertNull` on all three, it now asserts the mapping - and its javadoc's
      "cannot be derived" paragraph is rewritten to say which argument resolved each id.
- [ ] **`V84EvanNodeTest.evansEightMountIdsAreTheMountsStatEffectSaysTheyAre` (`:124`) is
      unchanged.** It asserts the eight mounts that already map correctly and has nothing to do with
      this row. A diff there means the wrong test was edited.
- [ ] `V84EvanNodeTest.evansSoaringIsFlightNotAMount` (`:143`) still passes - it is the negative
      control that fails if `buildSkillMounts` is widened to a range instead of three explicit
      entries.
- [ ] The gachapon SUCCESS byte on the wire is **0xEE** and FAILED is **0xED** when `VERSION >= 84`,
      and **0xE5** / **0xE4** below it, asserted by a test in the `CashShopModeTest` style that reads
      the built packet's mode byte at `p.getBytes()[2]`.
- [ ] **Neither gachapon literal routes through `cashShopMode()`.** That helper is +3 and belongs to
      `OnCashItemResult`; a grep for `cashShopMode` under `CASHSHOP_CASH_ITEM_GACHAPON_RESULT`
      returns nothing.
- [ ] `GameConstants.isHallOfFameMap(100030301)` returns true and
      `GameConstants.getHallOfFameBranch(Job.EVAN1, 100030301)` returns **21**, not 30.
- [ ] A `MapId` constant for 100030301 exists and the new `case` uses it, matching every other arm of
      the switch.
- [ ] Nothing else in the `isHallOfFameMap` switch changes branch: every other map id the switch
      already handled returns the same value before and after.

Run the named classes with `-Dtest=<Name>`. **Do not run maven while sibling agents are active** -
they collide on `target/`; use the JUnit platform launcher recipe recorded in ticket 53.

## Do not

- Do not treat 20011018's yeti as unknowable and skip it. The two candidate images are byte-identical
  apart from their own node name; the choice is free, only record which one was taken.
- Do not invert `evansEightMountIdsAreTheMountsStatEffectSaysTheyAre`. It is not the test that pins
  this gap.
- Do not assert the offset rule transfers as a general principle. It resolves 20011031, the *name*
  resolves 20011019, and neither resolves 20011018.
- Do not route the gachapon bytes through `cashShopMode()`. It yields 0xE7/0xE8 and documents a
  different handler.
- Do not change the v83 mode bytes unconditionally. Both values must stay reachable behind the
  version guard.
- Do not touch `Job.isA` or the branch arithmetic at `GameConstants.java:387` to fix R21. The defect
  is the missing `case`, not the fallback formula, which is correct for genuinely custom maps.
