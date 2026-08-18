# 62 - Three Java constants v84 already settles

**Class:** v84 parity
**Work rows:** R14, R15, R21 - `docs/work-plan/V84-WORK-ROWS.tsv`
**Blocked by:** None - can start immediately

Three unrelated defects that share one property: the value each needs is already recovered and
written down, so none of them is a derivation. Two are single-line table entries and one is a pair
of mode bytes read out of the v84 disassembly export. They are grouped because they are all
constants in `src/main/java` and one agent can land all three in one context window.

## R14 - Evan's three mount skills are absent from `buildSkillMounts()`

Skills **20011018**, **20011019** and **20011031** have no entry in the `skillId -> TamingMob item
id` map built at `StatEffect.java:147-188`. The map is read back by `StatEffect.skillMountItem` at
`:190` and consumed at `:1365`; a skill with no entry yields no sprite, so the mount renders
nothing. The gap is pinned today by `V84EvanNodeTest.java:164-172`.

The sprite ids are derivable, contrary to the tracker's claim that they are not:

- **20011031 "Balrog"** shares offset `1031` with `Beginner.BALROG_MOUNT`, which maps to **1932010**.
- **20011019 "Witch's Broomstick"** is name-identical to `0001019`, which maps to **1932005**.
- **20011018 "Yeti Rider"** is ambiguous between **1932003** and **1932004**. It does not matter:
  `Character.wz/TamingMob/01932003.img:3-20` and `01932004.img:3-20` carry identical `info` and
  identical frame geometry, so both render the same yeti. Pick one and record which in the commit.

Reachable only via `!maxskill` today, so no live character is affected by the change.

## R15 - Cash gachapon result sends v83 mode bytes to a v84 client

`PacketCreator.java:6787` writes **0xE4** and `:6794` writes **0xE5** under
`CASHSHOP_CASH_ITEM_GACHAPON_RESULT`. v84 moved both: SUCCESS is **238 (0xEE)** and FAILED is
**237 (0xED)**. Cash Shop Surprise therefore fails silently against the v84 client - the packet is
sent, the client does not recognise the mode, nothing happens and nothing errors.

The path is broken today, so any change is an improvement.

## R21 - Evan's Hall of Fame map is not recognised as one

`constants/game/GameConstants.java:352-369`, `isHallOfFameMap()`, has no `case 100030301`. Because
that case is missing, `getHallOfFameBranch()`'s `EVAN1 -> 21` arm at `:411-412` is unreachable and
an Evan PlayerNPC falls through to the custom branch at `:387`, which computes
`26 + 4*(mapid/100000000)` = **30**. One case line fixes it.

Found while verifying the Forest Hall row; not previously tracked anywhere.

## Precedent

**R14.** Copy the v83 block at `StatEffect.java:149-161` verbatim, three more entries in the same
shape. The three sprite ids above are derived from ids already in the tree
(`Beginner.BALROG_MOUNT`, skill `0001019`) and from the two TamingMob images, not invented.

**R15.** `D:\games\MSv84\opcodes\ida_export_gms_v84.json` names
`CCashShop::OnCashItemGachaponResult` at **0x47f8fc** with SUCCESS mode=238 and FAILED mode=237.
That is the right handler and not a lookalike: the payload shape matches ours field for field -
`int64 sn`, `int32 remain`, 55-byte `GW_CashItemInfo`, then `itemId`/`count`/`jackpot` - which
proves only the mode byte moved. Put both behind a `VERSION >= 84` guard using the `cashShopMode()`
helper and copy its evidence javadoc style from `PacketCreator.java:3570-3604`.

**R21.** Aran's `PALACE_OF_THE_MASTER` case in the same switch is the analogue arm - same file, same
method, same one-line shape.

## Acceptance criteria

- [ ] `StatEffect.skillMountItem(20011031)` returns `1932010`, `skillMountItem(20011019)` returns
      `1932005`, and `skillMountItem(20011018)` returns `1932003` or `1932004` (whichever the commit
      names).
- [ ] `V84EvanNodeTest.evansEightMountIdsAreTheMountsStatEffectSaysTheyAre` has its assertion
      inverted - it asserted the absence, it now asserts the mapping - and passes.
- [ ] The gachapon SUCCESS byte on the wire is **0xEE** and FAILED is **0xED** when `VERSION >= 84`,
      and **0xE4** / **0xE5** below it, asserted by a test in the `CashShopModeTest` style that
      reads the built packet's mode byte at its exact offset.
- [ ] Both gachapon literals route through `cashShopMode()`; no bare `0xE4`/`0xE5` remains under
      `CASHSHOP_CASH_ITEM_GACHAPON_RESULT`.
- [ ] `GameConstants.isHallOfFameMap(100030301)` returns true and
      `GameConstants.getHallOfFameBranch(100030301)` returns **21**, not 30.
- [ ] Nothing else in the `isHallOfFameMap` switch changes branch: every other map id the switch
      already handled returns the same value before and after.

Run the named classes with `-Dtest=<Name>`. **Do not run maven while sibling agents are active** -
they collide on `target/`; use the JUnit platform launcher recipe recorded in ticket 53.

## Do not

- Do not treat 20011018's yeti as unknowable and skip it. The two candidate images are identical;
  the choice is free, only record which one was taken.
- Do not change the v83 mode bytes unconditionally. Both values must stay reachable behind the
  version guard.
- Do not touch `Job.isA` or the branch arithmetic at `GameConstants.java:387` to fix R21. The defect
  is the missing `case`, not the fallback formula, which is correct for genuinely custom maps.
