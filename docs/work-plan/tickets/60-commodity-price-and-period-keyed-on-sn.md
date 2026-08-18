# 60 - CLOSED: the 160 Commodity leaves are not a defect, and the merge has no source

**Class:** v84 parity
**Work rows:** R10 - `docs/work-plan/V84-WORK-ROWS.tsv`
**Status:** **REFUSED - do not implement. Do not re-file.**

This ticket used to ask for 160 `Price`/`Period`/`OnSale` leaves to be merged into
`Etc.wz/Commodity.img` from the v84 carve, keyed on `SN`. **Keyed on `SN`, the carve does not contain
those values.** There is nothing to merge. Our tree already matches v84 on every leaf `CashShop`
reads.

The row exists because a diff tool compared two differently-ordered arrays **by index**. That single
error produced the gap, the counts, and the "two independent measurements agree" corroboration. The
measurements below are the record, so this is never re-derived from the same bad key.

## The measurement that closes the row

Keyed on the `SN` leaf inside each node - the key this ticket itself always said was the only valid
one - against
`D:\games\MapleStory\Server\porting-resources\wz-data\v84\Etc.wz`:

| our gap | rows in our tree | SN-matched carve row **has** the leaf |
|---|---:|---:|
| missing `Price` | 94 | **0** |
| missing `Period` | 78 | **5** |
| missing `OnSale` | 2 | **0** |

The only v84-added leaf present on those rows is `Bonus`, which this ticket always classed benign and
which `CashShop` never reads.

Worked example, so the failure mode is unmistakable:

* Ours, `wz/Etc.wz/Commodity.img.xml` index **8858**: `SN 80000004, ItemId 3010015, Count 1,
  OnSale 0` - no `Price`, no `Period`.
* The carve row **for SN 80000004** is index **8962**: `SN 80000004, ItemId 3010015, Count 1,
  Bonus 0, OnSale 0` - **also no `Price`, no `Period`.**
* The carve's index **8858** is a different item entirely: `SN 70000282, ItemId 9101842, Price 5900,
  Period 90`.

An index-keyed merge would have written 5900 mesos and a 90-day period onto SN 80000004.

## The index-alignment claim is false, and two tracker files inherited it

`docs/work-plan/V84-COVERAGE.md:95-97` asserts that the diff tool matched v83 and v84 at the same
index, *"so the indices line up and the missing `Price`/`Period` are real."* Measured:

* Of the 88 affected indices that exist in the carve, **zero** have a matching `SN`.
* Across the whole image, **6,724 of 9,057** shared indices hold different `SN`s, and 6,719 hold
  different `ItemId`s.

This ticket used to cite the agreement between `v84coverage.py`'s 78 and a direct query's 78 as
"evidence the indices happen to line up today". It is not evidence of anything: both numbers derive
from the same index-keyed comparison, so they agree because they share the same broken key.

**`V84-COVERAGE.md` and `V84-WORK-ROWS.tsv:11` both carry the error and are owned by another agent.**
Whoever owns the trackers needs to fix the index-alignment paragraph and close R10; this ticket does
not edit them.

## The counts were wrong too

* **82 + 78 + 2 = 162**, not 160. The headline number never added up.
* Those are *leaves*, not rows. Distinct affected **rows** in our tree = **94** - every
  `Period`-missing and `OnSale`-missing row is also `Price`-missing, so the union is 94.
* Our tree has **94** rows missing `Price`, not 82. The 82 is the `add-list` line count, not a count
  of our file. (`docs/wz-baseline/add-list/Etc.txt` does carry exactly 82 `.../Price`, 78
  `.../Period` and 2 `.../OnSale` lines - the component numbers matched the add-list; they just did
  not match the tree.)
* The 12-row shortfall is indices 9051-9062: **9051-9056** lack `Price` in the carve as well, and
  **9057-9062 do not exist in the carve at all**.

## The arrays are not even the same length

Ours has **9,063** children (0..9062, contiguous). The carve has **9,057** (0..9056, contiguous). We
carry six rows v84 never had. This ticket described the work as a leaf-level merge into aligned
arrays; the arrays are neither aligned nor the same size.

## The acceptance criteria contradicted each other

The old AC #1 demanded the merged `OnSale` equal the carve's value for that SN. The old "Do not"
forbade flipping any `OnSale` from 0 to 1. Those cannot both hold under an index-keyed read: the
carve at indices **8932-8939** says `OnSale=1` - e.g. index 8933 `SN 92000013` at price 3600, index
8934 `SN 92000014` at price 3500 - and 8933/8934 are exactly the two rows this ticket listed as
"missing `OnSale`". A naive index merge puts eight untested rows on sale at real prices.

Keyed on SN the correct merge is a no-op, so the contradiction never fires - but the ticket
authorised both readings, and the wrong one is the one that looks like it worked.

## What was verified TRUE, and is worth keeping

- **`CashShop.java:243-248`** reads exactly six leaves - `SN`, `ItemId`, `Price` (**:245**), `Period`
  (**:246**), `Count`, `OnSale` (**:248**). No more, no fewer. Both line cites in the old ticket were
  right.
- **An absent `OnSale` defaults to off.** `:248` is
  `DataTool.getIntConvert("OnSale", item, 0) == 1` - the third argument is the default. The two rows
  with no `OnSale` leaf are not on sale.
- **Nothing is mispriced today.** Of the affected rows: 92 have `OnSale=0` explicitly, 2 have no
  `OnSale` leaf (so, off), and **zero** have `OnSale=1`. Conversely, none of our 2,070 `OnSale=1`
  rows is missing `Price` or `Period`. The safety claim was always correct - it just described a row
  that did not need doing.
- **`Commodity.img` children are numeric array indices on both sides**, contiguous from 0, never SNs.
- The eight leaves `Bonus`, `Class`, `Gender`, `Priority`, `Limit`, `PbPoint`, `PbGift`, `PbCash`
  appear **nowhere** in `src/` as string literals. Genuinely unread, correctly classed benign.
- The benign accounting is internally consistent: 10,181 Commodity + 1,165 `Mob.wz/*/info/category`
  = **11,346**, and the matrix total 11,475 = 11,346 + 5 `Mob info/default` + 9 `Npc` + 115 `String`.
  Ticket 59 and this ticket do **not** contradict each other on that figure.
- `docs/work-plan/SOURCES.md:103-104` does carry the governing rule: *"**Storage order is not name
  order.** ... Compare **keyed on node name**, never positionally."* Applying it is what closed this
  row.

One correction for the record, since ticket 59 cites it: `add-list/Etc.txt` holds **10,459**
Commodity lines, not 10,181 - 10,181 benign 8-leaf rows, plus the 162 read-leaf rows above, plus
**116 whole-node entries** (`Etc.wz/Commodity.img/8941` .. `/9056`) that neither ticket mentioned.

## Corrections to the old ticket's citations

- The carve is at the absolute path
  `D:\games\MapleStory\Server\porting-resources\wz-data\v84\Etc.wz` - a **sibling of this repo**, not
  a subdirectory of it. The old repo-relative `porting-resources/wz-data/v84/Etc.wz` does not
  resolve; `docs/work-plan/SOURCES.md:14` has it right.
- `WzPeek.exe` has exactly two subcommands: **`dump`** and **`scan`**.
- Commits `434c5cba5` and `32fa7879f` were cited as the additive leaf-merge precedent. **Neither is
  additive.** `434c5cba5` "Halve the v84-era quest requirements..." rewrites existing leaf values
  (`Act.img.xml` 104 lines, `Check.img.xml` 142 lines, 301 insertions / 137 deletions);
  `32fa7879f` touches no `wz/` file at all. The only genuinely additive precedent in this area is
  **`8c24b6fa5`** (`wz/String.wz/Eqp.img.xml | 6 ++++++`, zero deletions).
- Surefire's `default-test` execution **excludes** `*RealLoad` at `pom.xml:239`
  (`<exclude>**/*RealLoad.java</exclude>`); the **include** is in the separate `real-load-tests`
  execution at `pom.xml:272-274`. The old "Surefire includes `*RealLoad` (`pom.xml:239,272-274`)"
  read as if 239 were part of the include.
- `tools/playthrough/v84coverage.py` **writes** `docs/work-plan/V84-COVERAGE.tsv` in full
  (`OUT` at `:46`, `fh.write` at `:232`). It is not a read-only check. Its Commodity carve-out is
  `BENIGN[1]` at `:85-87`, with the `CashShop.java:243-248` reason attached.

## If someone wants to re-open this

The bar is a **new measurement keyed on `SN`** showing that the carve carries a `Price` or `Period`
our tree lacks for that same SN. The measurement above says it does not, for all 94 rows. An
index-keyed count - including anything derived from `add-list` or from `v84coverage.py`, both of
which are index-keyed here - is not evidence and does not re-open the row.

## Do not

- **Do not merge `Commodity.img` by array index.** This was always the ticket's one rule, and it is
  now also the reason the row is closed: the arrays do not correspond.
- Do not treat the `add-list` counts (82/78/2) as counts of our tree. They are not; the tree numbers
  are 94/78/2.
- Do not flip any `OnSale` from 0 to 1. Every affected row is off-sale today, and an index-keyed read
  of the carve would put eight of them on sale at real prices.
- Do not add the eight leaves `CashShop` never reads. They are closed as benign and stay that way.
- Do not run `v84coverage.py` to "re-check" this. It is index-keyed on this image and it writes a
  tracker file this ticket does not own.
