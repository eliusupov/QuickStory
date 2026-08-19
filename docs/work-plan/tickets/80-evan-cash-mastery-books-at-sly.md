# 80 - Evan cash mastery books at Sly

**Class:** owner-requested
**Slice:** `docs/work-plan/EVAN-CASH-MASTERY-BOOKS-SPEC.md`
**Blocked by:** None.
**Startable now:** YES.

Add the three v84 Evan cash mastery books to Sly (NPC/shop **2080001**) at the owner-set meso
prices. This is an owner-requested availability and pricing override, **not** a v84-parity gap.

| Item ID | Item | Evan skill | Available at | Price |
| ---: | --- | ---: | ---: | ---: |
| 5620006 | Magic Guard Mastery Book | 22111001 Magic Guard | 30 | 250000 |
| 5620007 | Magic Booster Mastery Book | 22141002 Magic Booster | 60 | 500000 |
| 5620008 | Critical Magic Mastery Book | 22140000 Critical Magic | 60 | 500000 |

## Evidence

Pristine, read-only GMS v84 data establishes all three identities:

- `Item.wz/Cash/0562.img` contains exactly `05620006`, `05620007`, and `05620008`; each is a
  cash item with success 100 and targets, respectively, skills `22111001`, `22141002`, and
  `22140000`.
- `String.wz/Cash.img` names them Magic Guard, Magic Booster, and Critical Magic Mastery Book and
  describes each as `Job: Evan`.
- `Etc.wz/Commodity.img` has v84 cash-catalog rows `8118`/SN `50200173`, `8120`/SN `50200175`,
  and `8123`/SN `50200178` for the three IDs. All carry `OnSale=0`: the client ships the items,
  but those catalog entries do not set the owner's meso-shop price or availability.

The server's computed v84 source sweep independently records only these three as Evan cash-only
mastery books. `175-evan-masterlevel-skill-cap-backfill.sql` identifies Magic Guard as Evan III
and Magic Booster/Critical Magic as Evan VI; the owner supplied the level-30/level-60 price split.

Current SELECT-only database evidence: shop 2080001 ends at position 172, has no row for any of
the three IDs, and all existing rows use `pitch=0`.

## What to do

1. Add a new Liquibase data changeSet numbered **176**. Its header must name changeSet 166 as the
   row-shape/order precedent and identify the three prices as the owner-directed override.
2. Insert exactly these three rows, appended after the current position 172:

   | shopid | itemid | price | pitch | position |
   | ---: | ---: | ---: | ---: | ---: |
   | 2080001 | 5620006 | 250000 | 0 | 173 |
   | 2080001 | 5620007 | 500000 | 0 | 174 |
   | 2080001 | 5620008 | 500000 | 0 | 175 |

3. Register the new changeSet and an exact rollback that deletes only those three `shopitems`
   rows for shop 2080001.

## Precedent

Copy the additive `shopitems (shopid, itemid, price, pitch, position)` row shape, position
continuation, Liquibase registration, and exact rollback from **changeSet 166,
`166-evan-shops-data.sql`**. Its Sly rows end at position 172; no existing row is changed or
renumbered.

## Acceptance criteria

- [ ] New changeSet 176 contains exactly the three tabled rows and no other stock mutation.
- [ ] The changelog registers the changeSet and its rollback deletes exactly those rows, scoped to
      shop 2080001.
- [ ] SELECT after application returns item IDs 5620006/5620007/5620008 at positions 173/174/175,
      prices 250000/500000/500000, and pitch 0; all pre-existing Sly rows remain unchanged.
- [ ] A freshly packaged jar applies the changeSet on restart; confirm the database result, not
      just the Liquibase log.

## Do not

- Do not alter `Commodity.img`, its `OnSale` values, Cash Shop packages, or any WZ data.
- Do not add the 13 ordinary `2290140`-`2290152` Evan mastery books; they are already stocked by
  changeSet 166 and are outside this request.
- Do not update, delete, reorder, or reprice any existing Sly row.
- Do not write directly to MySQL or edit an applied changeSet.
- Do not relabel this owner price/availability override as v84 parity work.
