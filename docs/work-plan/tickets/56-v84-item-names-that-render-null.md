# 56 - the v84 items that render with no name

**Class:** v84 parity
**Work rows:** R04, R45, R48 - `docs/work-plan/V84-WORK-ROWS.tsv`
**Blocked by:** None - can start immediately

Fourteen items have an `Item.wz` or `Character.wz` image in this tree - so they exist, they render,
their stats load - and no `String.wz` entry, so `ItemInformationProvider.getName` returns null and
the inventory, the quest window and every chat token draw an empty slot. One of them is handed over
by a live Evan quest and one by a live Evan quest script. All fourteen are expected in v84's own
`String.wz`, so the governing question answers itself: **is it in the v84 data? Yes.**

## R04 - item 4032526 has an `Item.wz` node and no `String.wz` name

`wz/Item.wz/Etc/0403.img.xml` carries `04032526`. `wz/String.wz/Etc.img.xml` carries 4032520,
4032521, 4032522, 4032523, 4032524, 4032525, 4032527, 4032528 and 4032529 - **and not 4032526**. The
item is awarded at `Quest.wz/Act.img.xml:4837` (quest **22572**) and consumed at
`Quest.wz/Act.img.xml:4846`, so an Evan on 22572 is handed a nameless item and told to give it back.

`add-list/Item.txt:304` makes 4032526 a v84-new item.

Pristine v84 `String.wz/Etc.img/Etc/4032526` states both leaves in full:

- `name` = **John's Map**
- `desc` = **John's map that contains information on some island. That island is said to be the
  place where the dragon lies asleep.**

## R45 - item 1003028 has a `Character.wz` image and no `String.wz` name

`wz/Character.wz/Cap/01003028.img.xml` exists, which is why the cap renders and its stats load.
`wz/String.wz/Eqp.img.xml` has no entry for it. The cap is granted by `scripts/quest/22002.js`, a
live Evan quest script, so this is reachable today by any Evan.

Recorded as ticket 45 blocker **B5**.

## R48 - twelve v84-new equips have an image and no name

Twelve ids, all with a `Character.wz` image in this tree and no `String.wz/Eqp.img` entry:

**1003029, 1003030, 1003043, 1042180, 1052226, 1060138, 1061160, 1072418, 1072425, 1082261,
1702248, 1702254.**

`ItemInformationProvider.getName` (`ItemInformationProvider.java:1396-1407`) returns null on a
missing `String` node, which is what every one of these produces. Found by the item-source sweep -
see `docs/work-plan/V84-ITEM-SOURCE-SWEEP.md`.

## Precedent

Same family and same fix in all three rows: recover the name (and `desc`, where the carve has one)
from the pristine carve at `porting-resources/wz-data/v84/String.wz` and merge the leaf additively.

- **Commit `8c24b6fa5`** did exactly this for the two medals. Copy its shape - it is the reference
  edit for a missing `String.wz` name.
- **Commit `df9e779a9`** took the 11 Evan NPC names from the same source and is the second example.
- The carve is `SOURCES.md` tier 1: `porting-resources/wz-data/v84/`, 18 `.wz` dated 2010-03-29,
  byte-identical to a fresh carve of `GMSSetupv84.exe`. Read it with `WzPeek`; it is packed binary,
  not a text tree, and it is **read-only**.
- R04's two leaf values are already recovered and quoted verbatim above; use them as written.
- For R45, if v84's `String.wz/Eqp.img` itself has no entry for 1003028, check
  `porting-resources/evan-xml` before concluding. If neither has it, record the id as **UNKNOWN**
  and leave it nameless rather than inventing a name.
- For R48, the same rule per id: where v84 itself has no entry, say **UNKNOWN** in this ticket's
  closing note. Do not fabricate a name to close a row.

## Acceptance criteria

- [ ] `wz/String.wz/Etc.img.xml` carries `4032526/name` = `John's Map` and `4032526/desc` = the
      full sentence quoted above, byte for byte against the carve.
- [ ] `wz/String.wz/Eqp.img.xml` carries a `name` entry for **1003028** whose value matches
      pristine v84 - or the id appears in this ticket's UNKNOWN list with the carve query that
      proved v84 has none.
- [ ] `wz/String.wz/Eqp.img.xml` carries a `name` entry for each of 1003029, 1003030, 1003043,
      1042180, 1052226, 1060138, 1061160, 1072418, 1072425, 1082261, 1702248, 1702254 - or that id
      appears in the UNKNOWN list with its carve query.
- [ ] `ItemInformationProvider.getName` returns a non-null, non-empty string for every id not on the
      UNKNOWN list - asserted by a `*RealLoad` test with one assertion per id, so a single missing
      entry names itself.
- [ ] The same test asserts 4032520-4032525 and 4032527-4032529 still resolve, i.e. the merge added
      a sibling and did not rewrite the block.
- [ ] Every edited XML file's diff is confined to the added `name`/`desc` leaves. `core.autocrlf` is
      true in this worktree; a writer that forces the wrong line ending shows every line as changed.
- [ ] The test class is named in this ticket and invoked as `mvnw.cmd -o test -Dtest=<ClassName>`.
      Surefire now includes `*RealLoad` (`pom.xml:239,272-274`), **but do not run maven while
      sibling agents are active** - they collide on `target/`. State the invocation and hand it to
      the orchestrator.

## Do not

- Do not invent a name, a description, or a translation for any id the carve does not carry. Say
  UNKNOWN.
- Do not write to `porting-resources/wz-data/v84/` or `D:\games\MSv84\client\`. Both are read-only.
- Do not rewrite the surrounding `String.wz` blocks. The merge is additive, leaf by leaf; the
  `Item.wz` and `Character.wz` images are untouched by this ticket.
- Do not drop an empty-valued leaf while copying a section. `SOURCES.md`: empty is not absent.
- Do not compare positionally. Match on node name, never on storage order.
