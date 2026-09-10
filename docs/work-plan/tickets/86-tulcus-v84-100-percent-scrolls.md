# 86 - Complete Tulcus's v84 100% scroll stock

## Goal

Add exactly the 82 non-GM v84 scrolls with `info/success=100` that remain absent from Tulcus shop
**1052104** after changeSet 178. Price every addition at 250,000 mesos. Delete the two GM-strength
rows added by 178, `2040303` and `2040806`, so none of the 29 guaranteed 10%-strength scrolls is
sold in this player shop.

Make restricted random-stat scrolls `2049104` and `2049112..2049114` usable through their existing
v84 equipment requirement lists, then re-sort all 431 physical shop rows by the established
purpose-first contract.

## Evidence and precedent

- Pristine `D:\games\MapleStory\Server\porting-resources\wz-data\v84\Item.wz`, queried with
  `WzPeek scan ... success 100 Consume/0204.img`, returns exactly **156** unique ids.
- SELECT-only live evidence on 2026-09-10: `DATABASECHANGELOG` contains neither 178 nor 179; shop
  1052104 has 120 physical rows, 118 unique ids, positions 104..680. Twenty-eight v84 100% ids are
  live, and changeSet 178 adds 19 more. Exactly 29 of the 156 are GM-strength; excluding them leaves
  the exact 82 additions below.
- Pristine v84 `String.wz/Consume.img` supplies the names below. Item names are classification
  evidence, not data to insert.
- `src/main/resources/db/data/166-evan-shops-data.sql:141-142` records the owner's 250,000-meso
  precedent for newly added ordinary 100% scrolls.
- `src/main/java/constants/inventory/ItemConstants.java:512-530` derives the exact 29 guaranteed
  10%-strength scrolls from WZ data and pays 750,000 mesos when one is sold. Owner decision: none
  belongs in Tulcus.
- ChangeSet 178 currently prices two members of that class at 250,000:
  `2040303` (`178-tulcus-scroll-shop-completion.sql:30`) and `2040806` (`:98`). They must be
  deleted before the database applies the shop work.
- `ScrollHandler.java:85-89` checks a scroll's exact WZ `req` list before family compatibility.
  Its later gate bypasses `ItemConstants.isChaosScroll`; that predicate currently stops at
  2049103. The effect path already handles all eight `randstat=1` scrolls, proven by
  `ChaosScrollRandstatRealLoad` and commits `35e39b0bf` / `51ae2d9ff`.
- Copy guarded insert and exact rollback structure from changeSet 178. Copy the atomic position
  CASE and exact inverse rollback structure from changeSet 179. Do not edit 178 or 179.

## Exact missing non-GM set - 82 rows

All rows below carry `info/success=100` in pristine v84 data.

| target | ids and canonical advertised purpose/name |
|---|---|
| Helmet | 2040018 Accuracy; 2040041 DEF 100%; 2040042 HP 100% |
| Face Accessory | 2040102 HP; 2040107 Avoidability |
| Eye Accessory | 2040211 Dragon Glasses Scroll; 2040212 Dragon Glasses Special Scroll |
| Earring | 2040300 INT; 2040312 DEF; 2040324 HP 100%; 2040334 INT 100% |
| Topwear | 2040400 DEF; 2040414 LUK; 2040420 HP 100%; 2040430 DEF 100% |
| Overall | 2040503 DEF; 2040538 DEX 100%; 2040539 DEF 100% |
| Bottomwear | 2040614 DEX; 2040617 Jump 100%; 2040620 HP 100%; 2040630 DEF 100% |
| Shoes | 2040706 Speed; 2040740 DEX 100%; 2040741 Jump 100%; 2040742 Speed 100% |
| Gloves | 2040800 DEX; 2040803 ATT; 2040818 Magic Att.; 2040823 HP 100%; 2040829 DEX 100%; 2040830 ATT 100% |
| Shield | 2040900 DEF; 2040918 Magic Att.; 2040926 HP 100%; 2040936 DEF 100% |
| Cape | 2041000 Magic Def.; 2041003 Weapon Def.; 2041006 HP; 2041009 MP; 2041066 Magic DEF 100%; 2041067 Weapon DEF 100% |
| Pendant | 2041200 Dragon Stone |
| One-Handed Sword | 2043010 Magic Att.; 2043015 Accuracy 100%; 2043023 ATT 100% |
| One-Handed Axe | 2043110 Accuracy 100%; 2043117 ATT 100% |
| One-Handed BW | 2043210 Accuracy 100%; 2043217 ATT 100% |
| Dagger | 2043312 ATT 100% |
| Wand | 2043712 Magic ATT 100% |
| Staff | 2043812 Magic ATT 100% |
| Two-Handed Sword | 2044010 Accuracy 100%; 2044025 ATT 100% |
| Two-Handed Axe | 2044110 Accuracy 100%; 2044117 ATT 100% |
| Two-Handed BW | 2044210 Accuracy 100%; 2044217 ATT 100% |
| Spear | 2044310 Accuracy 100%; 2044317 ATT 100% |
| Pole Arm | 2044410 Accuracy 100%; 2044417 ATT 100% |
| Bow | 2044500 ATT; 2044512 ATT 100% |
| Crossbow | 2044600 ATT; 2044612 ATT 100% |
| Claw | 2044712 ATT 100% |
| Knuckle | 2044800 Attack 100%; 2044805 Accuracy 100%; 2044815 Attack 100% |
| Gun | 2044900 Attack 100%; 2044908 Attack 100% |
| Pet Equip | 2048000 Speed; 2048003 Jump |
| Special/event | 2049101 Liar Tree Sap 100%; 2049102 Maple Syrup 100%; 2049103 Beach Sandals Scroll 100%; 2049104 Agent Equipment Scroll 100%; 2049112 King Pepe's 100% Scroll for Weapons; 2049113 Normal Witch Scroll; 2049114 Witch's Belt Scroll |

### Excluded GM-strength set

Do not add these 27 missing ids, and delete 2040303/2040806 after 178, for exactly 29 exclusions:

```text
2040006,2040007,2040403,2040506,2040507,2040603,2040709,2040710,2040711,
2040807,2040903,2041024,2041025,2043003,2043103,2043203,2043303,2043703,
2043803,2044003,2044103,2044203,2044303,2044403,2044503,2044603,2044703
```

Every one of the 82 listed additions costs 250,000.

## Compatibility

The following newly stocked items have exact v84 `req` equipment lists:

- 2040211 and 2040212: Dragon Glasses 1022097.
- 2041200: Horntail Necklace 1122000.
- 2049101..2049104 and 2049112..2049114: their event equipment enumerated in Item.wz.

`2049101..2049103` already bypass the ordinary family check as chaos scrolls. Extend that narrow
predicate to exactly `2049100..2049104` and `2049112..2049114`, so the remaining four can proceed
only after the existing `req` check succeeds. Do not include anniversary ids `2049105..2049110`
or unrelated `20491xx` ids; their special glove matching remains unchanged.

## Sort contract

Retain ticket 84's ascending purpose order:

`STR, DEX, INT, LUK, ATT, M.ATT, HP, MP, Accuracy, Avoidability, Speed, Jump, DEF, Special`.

Retain its target, success, item-id, price, and old-position tie-breakers. Add Pendant and Pet Equip
target ranks, and one final Event-specific rank for 20491xx items that span or name special event
equipment. Pin 2040211, 2040212, 2041200, 2049101..2049104 and 2049112..2049114 as Special.

The resulting physical-row purpose counts are:

| purpose | rows |
|---|---:|
| STR | 34 |
| DEX | 55 |
| INT | 42 |
| LUK | 42 |
| ATT | 100 |
| M.ATT | 25 |
| HP | 28 |
| MP | 6 |
| Accuracy | 36 |
| Avoidability | 4 |
| Speed | 6 |
| Jump | 9 |
| DEF | 32 |
| Special | 12 |

## Implementation

1. Register changeSet **180** after 179. Add a guarded row per missing `(shopid,itemid)` with
   `price=250000` and `pitch=0`. Use temporary unique positions 1508..1832 in ascending item-id
   order. In the same changeSet, delete only the exact post-179 rows
   `(1052104,2040303,250000,0,1116)` and `(1052104,2040806,250000,0,1256)`.
2. Give 180 an exact rollback: delete only the 82 additions and restore those two exact rows with
   guarded inserts.
3. Register changeSet **181** after 180. Atomically map all 431 physical rows to the sorted unique
   stride-4 positions 104..1824. Its rollback must be the exact inverse map, restoring the
   post-180 positions.
4. Make the narrow compatibility change above. No MapleMap or effect change is required.
5. Add real-load tests which derive the 156-id source set from pristine v84 data, compare it to the
   effective shop catalogue, pin prices and exact ids, verify the compatibility boundary, and
   verify the complete forward/inverse sort maps.

## Acceptance criteria

- [ ] ChangeSet 180 adds exactly 82 unique rows, and every id has pristine v84 `success=100`.
- [ ] The post-180 shop has 431 physical rows, 429 unique ids, and exactly 127 non-GM v84 100% ids.
- [ ] Every addition costs 250,000 and has pitch 0.
- [ ] None of the exact 29 GM-strength ids remains in Tulcus; 2040303 and 2040806 are deleted only
      when their complete post-179 tuple matches.
- [ ] Inserts are idempotently guarded and cannot duplicate or reprice an unexpected owner row.
- [ ] Rollback 180 deletes only its 82 additions and restores the two exact removed rows.
- [ ] 2049104 and 2049112..2049114 work on every WZ-required equip and reject every non-required
      control through the real handler compatibility route.
- [ ] Existing behaviour for 2049100..2049103, anniversary 2049105..2049110, ordinary scrolls,
      requirement checks, upgrade slots, and effects is unchanged.
- [ ] ChangeSet 181 changes only positions for shop 1052104, maps exactly 431 physical rows to
      104..1824 in steps of four, follows the declared contract/counts, and has an exact inverse.
- [ ] Both physical rows for 2040205 and both for 2040206 retain their item id, price and pitch.
- [ ] Tests pin the exact 82 additions, 29 exclusions, two exact deletions, v84 source set,
      compatibility, complete sort, exact rollback, and changelog order 179 -> 180 -> 181.
- [ ] After offline package and restart, `DATABASECHANGELOG` contains 178..181 and SELECT verifies
      all counts, ids, prices, pitch values and unique positions.
- [ ] No WZ, MapleMap, drop-table, shop command, scroll-effect or unrelated shop-row change exists.

## Agent

`gp-opus-medium` for implementation. Independent review was waived by the owner for this task.
