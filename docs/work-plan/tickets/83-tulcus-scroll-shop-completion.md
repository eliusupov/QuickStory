# 83 - Complete Tulcus's main-stat, 30% and 70% scroll stock

## Goal

Add exactly 231 missing v84-valid scrolls to Tulcus, NPC/shop **1052104**: 32 ordinary
main-stat rows, all 104 scrolls whose v84 `success` is 30, and all 95 whose v84 `success` is 70.
Do not alter existing stock. Make the six anniversary glove scrolls among those 199 data-derived
rows usable on gloves; without that narrow compatibility fix, the shop would sell unusable items.

## Evidence and precedent

- `wz/String.wz/Npc.img.xml:755-758` identifies 1052104 as **Tulcus**, **Scroll Seller**.
- `src/main/resources/db/data/101-shops-data.sql:22` maps shop 1052104 to NPC 1052104.
- `src/main/java/client/command/commands/gm2/ScrollShopCommand.java:39-41` sends this exact shop.
- SELECT-only live evidence before this ticket: shop 1052104 has 120 rows, positions 104..680;
  none of the 231 ids below is stocked. Topwear LUK 60%/10% already exist as
  2040425@250000/position416 and 2040427@500000/position432; only its 100% member is missing.
- Pristine `D:\games\MapleStory\Server\porting-resources\wz-data\v84\Item.wz`, queried with
  `WzPeek scan ... success 30 Consume/0204.img` and the equivalent `70` query, returns exactly
  104 and 95 ids respectively. Those counts and ids are the source of truth.
- Copy the additive, guarded `shopitems (shopid,itemid,price,pitch,position)` row shape from
  `src/main/resources/db/data/177-inkwell-and-tulcus-shop-items.sql:13-16`.
- Copy the Tulcus price and stride-4 position precedent from
  `src/main/resources/db/data/166-evan-shops-data.sql:110-145,194-204`: ordinary main-stat 100%
  and 60% are 250,000; 10% is 500,000; positions continue in steps of four.
- Register new **changeSet 178** after 177 and give it an exact rollback, following
  `src/main/resources/db/changelog-data.xml:373-379`.
- `src/main/java/server/maps/MapleMap.java:763-770` covers every id greater than 2040000 and less
  than 2050000. All ids below are inside that range. Its sole exception is Chaos Scroll 2049100
  (`src/main/java/constants/id/ItemId.java:151`), which belongs to neither the 30% nor 70% set.
  Normal-mob scroll chance is multiplied by 10 and boss chance by 1 before channel/card rates at
  `MapleMap.java:802-806`; no Java change is required.
- `src/main/java/net/server/channel/handlers/ScrollHandler.java:97-101,191-201` rejects an ordinary
  scroll unless `(scrollid / 100) % 100` matches the equipment category. Anniversary ids
  2049105..2049110 therefore calculate family 91 instead of glove family 8 and are rejected.
- `src/main/java/constants/inventory/ItemConstants.java:377-389` is the existing home for narrow
  scroll-family predicates. Its chaos predicate covers only 2049100..2049103; preserve that range.
  Pristine v84 data identifies 2049105/2049106 as glove ATT 70%/30%, and 2049107..2049110 as
  glove STR/LUK/INT/DEX 70%. These six, and only these six, need glove compatibility.

## Exact ordinary main-stat set - 32 rows

Use 250,000 for 100%/60% and 500,000 for 10%:

| slot/stat | ids and canonical names |
|---|---|
| Helmet DEX | 2040027 `Scroll for Helmet for DEX 100%`; 2040029 `...60%`; 2040031 `...10%` |
| Earring | 2040303 `Scroll for Earring for INT` 100%; 2040316 `...DEX 100%`; 2040319 `...LUK 100%` |
| Topwear | 2040417/2040418/2040419 `Scroll for Topwear for STR` 100/60/10%; 2040423 `Scroll for Topwear for LUK 100%` |
| Overall | 2040530 `Scroll for Overall for STR 100%`; 2040515 `Scroll for Overall Armor for LUK` 100% |
| Bottomwear | 2040623 `Scroll for Bottomwear for DEX 100%` |
| Gloves | 2040801 `Scroll for Gloves for DEX` 60%; 2040806 `Scroll for Gloves for DEX` 100% |
| Shield | 2040923 `Scroll for Shield for LUK 100%`; 2040929 `Scroll for Shield for STR 100%` |
| Cape | 2041012 STR, 2041018 DEX, 2041021 LUK; all `Scroll for Cape` 100% |
| Rings | 2041100..2041111: STR, INT, DEX, LUK sets, each ordered 100/60/10% |

All 32 ids and success values were independently found in the pristine v84 archive. Preserve the
canonical names from `wz/String.wz/Consume.img.xml`; names are evidence, not data to insert.

## Exact 30% set - 104 rows at 1,000,000 mesos each

```text
2040009,2040011,2040013,2040015,2040030,2040103,2040108,2040203,2040208,2040305,
2040307,2040309,2040322,2040327,2040405,2040407,2040409,2040411,2040426,2040509,
2040511,2040519,2040521,2040533,2040605,2040607,2040609,2040611,2040626,2040713,
2040715,2040717,2040728,2040729,2040730,2040731,2040732,2040733,2040734,2040735,
2040736,2040737,2040738,2040809,2040811,2040813,2040815,2040905,2040907,2040909,
2040917,2040922,2040932,2041027,2041029,2041031,2041033,2041035,2041037,2041039,
2041041,2041113,2041115,2041117,2041119,2041313,2041315,2041317,2041319,2043005,
2043007,2043018,2043105,2043113,2043205,2043213,2043305,2049106,2049201,2049203,
2049205,2049207,2049209,2049211,2040120,2043705,2043805,2044005,2044013,2044105,
2044113,2044205,2044213,2044305,2044313,2044405,2044413,2044505,2044605,2044705,
2044804,2044808,2044904,2040229
```

The canonical names are the matching v84 `Consume.img` names. This includes every dark-scroll
pair plus the non-dark 30% entries such as Helmet DEX, Topwear LUK, Overall STR, Bottomwear DEX,
shield STR, weapon accuracy/attack, Balrog 2040728..2040738, anniversary 2049106, accessory
2049201..2049211, Shiny Nose Bandage 2040120 and Dragon Glasses Special Scroll 2040229.

## Exact 70% set - 95 rows at 600,000 mesos each

```text
2040008,2040010,2040012,2040014,2040028,2040104,2040109,2040204,2040209,2040304,
2040306,2040308,2040320,2040325,2040404,2040406,2040408,2040410,2040424,2040508,
2040510,2040518,2040520,2040531,2040604,2040606,2040608,2040610,2040624,2040712,
2040714,2040716,2040808,2040810,2040812,2040814,2040904,2040906,2040908,2040916,
2040921,2040930,2041026,2041028,2041030,2041032,2041034,2041036,2041038,2041040,
2041112,2041114,2041116,2041118,2041312,2041314,2041316,2041318,2043004,2043006,
2043016,2043104,2043111,2043204,2043211,2043304,2043704,2049105,2049107,2049108,
2049109,2049110,2049200,2049202,2049204,2049206,2049208,2049210,2043804,2044004,
2044011,2044104,2044111,2044204,2044211,2044304,2044311,2044404,2044411,2044504,
2044604,2044704,2044803,2044806,2044903
```

The canonical names are the matching v84 `Consume.img` names. This intentionally includes dark,
anniversary, accessory and ordinary 70% rows because the owner asked for every v84-valid 70%.

## Implementation

1. Add `src/main/resources/db/data/178-tulcus-scroll-shop-completion.sql`.
2. Insert the exact union above into shop 1052104. Use `pitch=0` and an idempotent `NOT EXISTS`
   guard per `(shopid,itemid)`.
3. Sort the 231-row union by ascending item id. Assign positions deterministically as
   `684 + 4*i`, where `i` is the zero-based index in that sorted union. Positions therefore span
   exactly 684..1604 without changing any existing row.
4. Register changeSet 178 in `changelog-data.xml`. Rollback deletes only these 231 item ids from
   shop 1052104; it must not use a position range or affect another shop.
5. Add one narrowly named `ItemConstants` predicate for v84 anniversary glove scrolls
   2049105..2049110. Use it in `ScrollHandler.canScroll` to route only those six through the
   existing glove-family compatibility rule. Do not classify them as chaos scrolls and do not
   bypass the existing scroll requirements or slot checks.
6. Add one real-load test in the existing `V84Wz` style. Derive the complete 30% and 70% sets from
   `Item.wz`, compare them to the SQL rows and assert counts 104/95. Pin the 32 ordinary main-stat
   ids explicitly, their actual v84 success values, all prices, pitch zero, unique ids and the
   sorted stride-4 positions. Also assert no requested id existed in the prior Tulcus SQL rows.
7. Add focused compatibility tests proving all six anniversary ids accept a glove and reject a
   non-glove. Preserve the existing behaviour of 2049100..2049103 and a normal glove-scroll
   control. Prefer testing the real `canScroll` route; make only the minimum visibility change the
   test requires.

## Acceptance criteria

- [ ] ChangeSet 178 adds exactly **231** unique rows to shop 1052104: 32 main-stat, 104 30%, 95 70%.
- [ ] The three sets are disjoint and match the exact ids above; no other item is inserted.
- [ ] Every added 30% item is verified as `success=30` in pristine v84 data and costs 1,000,000.
- [ ] Every added 70% item is verified as `success=70` in pristine v84 data and costs 600,000.
- [ ] Main-stat pricing is 250,000 for 100%/60% and 500,000 for 10%.
- [ ] Every row has `pitch=0`; positions are unique, sorted by item id and exactly 684..1604 in
      steps of four. Existing rows and positions are byte-untouched.
- [ ] SQL is safe when a requested row already exists; it does not create a duplicate or reprice it.
- [ ] Rollback deletes exactly the 231 additions from shop 1052104 and nothing else.
- [ ] Focused tests prove the data-derived sets, counts, prices, positions and v84 item existence.
- [ ] 2049105 and 2049106 (glove ATT 70/30) and 2049107..2049110 (glove STR/LUK/INT/DEX 70)
      apply to glove-category equipment and are rejected for non-gloves.
- [ ] Existing 2049100..2049103 chaos/special handling and ordinary glove-scroll matching are
      unchanged; no other 20491 id gains compatibility.
- [ ] After `./mvnw -o package` and server restart, `DATABASECHANGELOG` contains 178; SELECT shows
      shop 1052104 has 351 rows, all 231 requested ids exactly once at the specified values.
- [ ] No Java changes exist beyond the narrow anniversary-glove predicate/matching path; no WZ,
      drop-table, existing shop-row or command change is made.

## Agent

`gp-opus-medium` for implementation. Independent `/code-review`: `gp-opus-high`.
