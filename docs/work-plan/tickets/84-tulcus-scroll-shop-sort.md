# 84 - Sort Tulcus's scroll catalogue by advertised purpose

## Goal

Reassign only the positions of all **351 physical rows** in Tulcus shop **1052104** after
changeSet 178. Preserve all 351 rows, including the duplicate item ids, and preserve every item id,
price and pitch byte-for-byte.

## Evidence and precedent

- Ticket 83 and changeSet 178 establish the intended post-migration catalogue: 120 existing rows
  plus 231 additions, with 351 physical rows and 349 unique item ids.
- The two intentional-to-this-ticket physical duplicates are `2040205` at old positions 400 and
  620, and `2040206` at old positions 388 and 596. They have different prices and must remain four
  physical rows.
- The v84 `Consume` names and `0204` item nodes are the source of truth for advertised purpose,
  target slot and success. Actual secondary effects are not the primary classification: weapon ATT
  commonly adds STR/DEX/LUK, M.ATT commonly adds INT, and Accuracy commonly adds DEX and PAD.
- Copy the additive changeSet registration and exact rollback shape from changeSet 178, but create
  new **changeSet 179**. Do not edit an applied changeSet.
- The existing shop convention uses unique stride-4 positions. The sorted result must use exactly
  `104, 108, ... 1504`, one position per physical row.

## Exact sort contract

Sort ascending by this tuple:

1. Advertised purpose:
   `STR -> DEX -> INT -> LUK -> ATT -> M.ATT -> HP -> MP -> Accuracy -> Avoidability -> Speed -> Jump -> DEF -> Special`.
2. Advertised equipment target:
   `Helmet -> Face Accessory -> Eye Accessory -> Earring -> Topwear -> Overall -> Bottomwear -> Shoes -> Gloves -> Shield -> Cape -> Ring -> Belt -> Accessory -> One-Handed Sword -> One-Handed Axe -> One-Handed BW -> Dagger -> Wand -> Staff -> Two-Handed Sword -> Two-Handed Axe -> Two-Handed BW -> Spear -> Pole Arm -> Bow -> Crossbow -> Claw -> Knuckle -> Gun`.
3. Success: `100 -> 70 -> 60 -> 30 -> 10 -> all other rates descending`.
4. Item id ascending.
5. Price ascending.
6. Old position ascending as the final physical-row tie-breaker.

Canonical v84 advertised purpose wins over effect-node inspection. Pin these easy-to-misclassify
cases explicitly:

| id | purpose | slot | reason |
|---|---|---|---|
| `2040120` | ATT | Face Accessory | Shiny Nose Bandage advertises ATT despite its nonstandard name |
| `2040229` | Special | Eye Accessory | Dragon Glasses Special Scroll grants both PAD and MAD |
| `2040727` | Special | Shoes | Spikes scroll advertises neither a stat nor attack |
| `2041112` | STR | Ring | Canonical v84 name advertises Ring STR |
| `2049105`, `2049106` | ATT | Gloves | Anniversary glove ATT scrolls |
| `2049107` | STR | Gloves | Anniversary glove STR scroll |
| `2049108` | LUK | Gloves | Anniversary glove LUK scroll |
| `2049109` | INT | Gloves | Anniversary glove INT scroll |
| `2049110` | DEX | Gloves | Anniversary glove DEX scroll |
| `2049200`..`2049211` | name-advertised stat/HP/MP | Accessory | shared accessory family |

Name classification precedence must distinguish `Magic Att.` before the broader ATT/Attack match.
An ATT, M.ATT or Accuracy scroll stays in that advertised group even when it grants a main stat.

The expected physical-row purpose counts are:

| purpose | rows |
|---|---:|
| STR | 34 |
| DEX | 51 |
| INT | 41 |
| LUK | 41 |
| ATT | 80 |
| M.ATT | 20 |
| HP | 20 |
| MP | 5 |
| Accuracy | 26 |
| Avoidability | 3 |
| Speed | 3 |
| Jump | 6 |
| DEF | 19 |
| Special | 2 |

## Implementation

1. Register changeSet 179 immediately after 178.
2. Add one SQL migration containing the complete deterministic old-position to new-position map
   for all 351 physical rows. Scope every update to shop 1052104. Positions are the row identity so
   duplicate item ids remain distinguishable.
3. Avoid transient position collisions by using a collision-free temporary offset or one atomic
   CASE update, then assign the final stride-4 positions.
4. Rollback must contain the exact inverse map and restore every original position, including the
   historical 472..568 gap. It must not derive rollback order from current WZ data.
5. Add a focused real-load test which reads the v84 names/effects and the migration mapping, proves
   the exact classification pins and counts above, and verifies the complete sort tuple.

## Acceptance criteria

- [ ] ChangeSet 179 changes only `shopitems.position` for shop 1052104.
- [ ] The forward map covers exactly 351 old positions once and produces exactly the 351 unique
      positions 104..1504 in steps of four.
- [ ] The result follows the exact purpose, slot, success, item-id, price and old-position ordering.
- [ ] Purpose counts are exactly 34/51/41/41/80/20/20/5/26/3/3/6/19/2 in the declared order.
- [ ] The explicit special cases classify exactly as listed; M.ATT is checked before ATT.
- [ ] Both rows for each of 2040205 and 2040206 remain, at their unchanged prices and pitch.
- [ ] All 351 `(itemid, price, pitch)` tuples are unchanged as a physical-row multiset.
- [ ] Rollback restores all 351 original positions exactly and touches no other shop.
- [ ] No Java, WZ, shop-content, price, pitch, command or drop change is made.

## Blockers

Ticket 83/changeSet 178 must precede this migration. No owner decision is required.

## Agent

`gp-opus-medium` for implementation. Independent `/code-review`: `gp-opus-high`.
