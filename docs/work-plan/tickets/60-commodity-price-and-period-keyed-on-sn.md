# 60 - the 160 Commodity leaves, merged on SN and not on array index

**Class:** v84 parity
**Work rows:** R10 - `docs/work-plan/V84-WORK-ROWS.tsv`
**Blocked by:** None - can start immediately

160 rows in `Etc.wz/Commodity.img` are missing the `Price` and `Period` v84 gives them. **Nothing is
mispriced today** - every affected row is `OnSale=0`, so no player can buy any of them. This is
latent data debt, and it is on this list because the leaves are v84's and the server reads them, not
because anything is broken in game. The one thing that can go wrong here is the merge itself, and it
has a named trap.

## R10 - 160 Commodity leaves are missing

`Etc.wz/Commodity.img`, by leaf:

- **82** indices missing `Price` - reader `CashShop.java:245`
- **78** indices missing `Period` - reader `CashShop.java:246`
- **2** indices missing `OnSale`

`add-list/Etc.txt` carries the paths; values come from the pristine carve at
`porting-resources/wz-data/v84/Etc.wz`.

**The trap, and it is the whole ticket.** `Commodity.img`'s children are **array indices, not SNs**.
`add-list` reports these as field-level additions under an existing index, which means the diff tool
matched v83 and v84 at the same index. `SOURCES.md` states the rule directly - *"storage order is not
name order"* - so the merge must be **keyed on the `SN` leaf inside each node**, and never on the
node's own name.

Two independent measurements agree on the `Period` count: `tools/playthrough/v84coverage.py` finds 78
indices with no `Period`, and a direct query of our tree finds 78 rows with no `Period`. That
agreement is evidence the indices happen to line up today; it is not licence to merge by index.

`CashShop.java:243-248` reads exactly `SN`, `ItemId`, `Price`, `Period`, `Count` and `OnSale`. The
other eight v84-added leaves per node - `Bonus`, `Class`, `Gender`, `Priority`, `Limit`, `PbPoint`,
`PbGift`, `PbCash` - are part of the 11,346 benign rows carved out in `V84-COVERAGE.md` and are
**not** in scope.

## Precedent

- Values from `porting-resources/wz-data/v84/Etc.wz` via `WzPeek` - `SOURCES.md` tier 1,
  **read-only**.
- `V84-COVERAGE.md`, section "Rows 2 and 3", states the caveat and the two independent counts. Read
  it before touching the file.
- The additive leaf-merge shape is the same one commits `434c5cba5` and `32fa7879f` used on
  `Quest.wz`.
- There is no precedent in this repo for an SN-keyed WZ merge, which is why the acceptance criteria
  below demand the key be proved rather than assumed.

## Acceptance criteria

- [ ] For every `SN` in our `Commodity.img` that the carve also carries, the merged node's `Price`,
      `Period` and `OnSale` equal the carve's values **for that same SN** - asserted by a
      `*RealLoad` test that looks the carve row up by `SN`, not by index.
- [ ] The 82 `Price`, 78 `Period` and 2 `OnSale` leaves all exist after the merge, and re-running
      `python tools/playthrough/v84coverage.py` drops the `Etc` GAP count by 160.
- [ ] Every node's `SN` and `ItemId` are unchanged by the merge - proved by comparing the pre- and
      post-edit `(index, SN, ItemId)` triples and asserting the set is identical.
- [ ] No node's array index moves. The child names in the edited file are the same names in the same
      positions as before.
- [ ] A test asserts that every row this ticket touched still has `OnSale=0`, i.e. the merge did not
      put anything on sale that was not on sale before. Any row the carve says should be `OnSale=1`
      is listed explicitly in this ticket instead of being merged silently.
- [ ] The eight benign leaves (`Bonus`, `Class`, `Gender`, `Priority`, `Limit`, `PbPoint`, `PbGift`,
      `PbCash`) are still absent - the merge did not widen.
- [ ] Test class named here, invoked as `mvnw.cmd -o test -Dtest=<ClassName>`. Surefire includes
      `*RealLoad` (`pom.xml:239,272-274`), **but do not run maven while sibling agents are active**.

## Do not

- **Do not merge by array index.** This is the one rule. An index-keyed merge writes a v84 price onto
  whatever SN happens to sit at that slot in our file, and the resulting file looks correct to every
  tool we have.
- Do not flip any `OnSale` from 0 to 1 as a side effect. If the carve disagrees with us on `OnSale`,
  say so in the ticket and stop - putting 160 untested rows on sale is not a leaf merge.
- Do not add the eight leaves `CashShop` never reads. They are already closed as benign.
- Do not reorder, compact or renumber the array.
- Do not let this row outrank anything a player can hit. It is `OnSale=0` across the board.
