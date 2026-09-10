# 87 - Keep only regular scrolls in Tulcus's shop

## Goal

Remove exactly 56 screenshot-identified non-regular rows from shop 1052104, then re-sort the 375
remaining physical rows. Use additive changeSets 182 and 183; never edit 178-181.

## Exact removal set

### Balrog - 11 stocked rows

`2040728` STR 30%; `2040729` INT 30%; `2040730` LUK 30%; `2040731` DEX 30%; `2040732` HP 30%;
`2040733` MP 30%; `2040734` Speed 30%; `2040735` Jump 30%; `2040736` Accuracy 30%; `2040737`
Avoidability 30%; `2040738` Defense 30%.

Pristine v84 also has `2040739` Balrog's Twilight Scroll 5%, but it is absent from the post-181
shop and must not be inserted or counted as a deletion.

### White-icon 100% - 31 rows

| target | ids and v84 names |
|---|---|
| Helmet | `2040041` DEF; `2040042` HP |
| Earring | `2040334` INT |
| Topwear | `2040430` DEF |
| Overall | `2040538` DEX; `2040539` DEF |
| Bottomwear | `2040630` DEF |
| Shoes | `2040740` DEX; `2040741` Jump; `2040742` Speed |
| Gloves | `2040829` DEX; `2040830` ATT |
| Shield | `2040936` DEF |
| Cape | `2041066` Magic DEF; `2041067` Weapon DEF |
| Weapons | `2043023` One-Handed Sword ATT; `2043117` One-Handed Axe ATT; `2043217` One-Handed BW ATT; `2043312` Dagger ATT; `2043712` Wand Magic ATT; `2043812` Staff Magic ATT; `2044025` Two-handed Sword ATT; `2044117` Two-handed Axe ATT; `2044217` Two-handed BW ATT; `2044317` Spear ATT; `2044417` Pole Arm ATT; `2044512` Bow ATT; `2044612` Crossbow ATT; `2044712` Claw ATT; `2044815` Knuckler Attack; `2044908` Gun Attack |

Each canonical name above is `Scroll for <target> for <purpose> 100%`. Pristine v84 `Item.wz`
proves the exact class by identical image data, not by name: SHA-256 prefixes are
`icon=7C3A3FE118A80B62`, `iconRaw=FE6794DD34F17BC7`.

### 6th Anniversary - 6 rows

- `2049105` [6th Anniversary] Dark Scroll for Gloves for ATT 70%
- `2049106` [6th Anniversary] Dark Scroll for Gloves for ATT 30%
- `2049107` [6th Anniversary] Dark Scroll for Gloves for STR 70%
- `2049108` [6th Anniversary] Dark Scroll for Gloves for LUK 70%
- `2049109` [6th Anniversary] Dark Scroll for Gloves for INT 70%
- `2049110` [6th Anniversary] Dark Scroll for Gloves for DEX 70%

These six reuse the same white `icon` / `iconRaw` hash pair, but are pinned separately by exact id.

### Pictured specials - 8 rows

- `2041200` Dragon Stone
- `2049101` Liar Tree Sap 100%
- `2049102` Maple Syrup 100%
- `2049103` Beach Sandals Scroll 100%
- `2049104` Agent Equipment Scroll 100%
- `2049112` King Pepe's 100% Scroll for Weapons
- `2049113` Normal Witch Scroll
- `2049114` Witch's Belt Scroll

The pictured Cape Magic DEF/Weapon DEF rows are `2041066/2041067` above; the pictured Balrog
Defense row is `2040738` above. Do not count either twice.

## Evidence

- Owner screenshots show the eight specials, `2041066/2041067`, and `2040738`; owner then expanded
  scope to every Balrog, white-icon 100%, and 6th Anniversary scroll.
- ChangeSet 178 inserts the stocked Balrog rows and anniversary rows at
  `178-tulcus-scroll-shop-completion.sql:86-96,220-225`.
- ChangeSet 180 inserts the eight specials at
  `180-tulcus-v84-100-percent-scrolls.sql:47,80-86`. Their screenshot presence proves 180 applied;
  their purpose-sorted display strongly indicates 181 applied.
- ChangeSet 181 is the authoritative source for each complete current
  `(shopid,itemid,price,pitch,position)` tuple. `Shop.java:268` displays by `position DESC`.

## Implementation

1. Register changeSet **182** after 181. Delete only each removal id's exact post-181 tuple,
   including shop id, item id, price, pitch, and position. Its rollback uses guarded inserts to
   restore those exact 56 tuples and nothing else.
2. Register changeSet **183** after 182. Atomically map all 375 remaining positions to unique
   stride-4 positions `104..1600`, retaining the ticket-84 purpose, target, success, item-id, price,
   and old-position ordering contract. Its rollback is the exact inverse map to post-182 positions.
3. Add focused tests deriving the icon equivalence from pristine v84 data and pinning the exact
   removal union, tuples, counts, sort map, rollback maps, and changelog order.
4. Do not change Java compatibility/effects, WZ data, other shops, or any prior changeSet.

## Acceptance criteria

- [ ] Exactly 56 physical rows and 56 unique ids are removed; no removal id remains.
- [ ] `2040739` was not present and remains absent; no row is added.
- [ ] Every non-removal row retains its item id, price, and pitch.
- [ ] Post-state is exactly 375 physical rows and 373 unique ids.
- [ ] Positions are exactly `104..1600` by four and display correctly under `ORDER BY position DESC`.
- [ ] Rollback 183 exactly restores the post-182 positions; rollback 182 exactly restores the 56
      deleted post-181 tuples without duplicating an unexpected owner row.
- [ ] Changelog order is 181 -> 182 -> 183; 178-181 are unchanged.
- [ ] Ordinary gray 100% scrolls remain stocked.

## Agent

`gp-opus-medium` for implementation.
