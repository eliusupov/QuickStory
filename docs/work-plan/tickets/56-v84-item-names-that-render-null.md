# 56 - the v84 items that render with no name

**Class:** v84 parity
**Work rows:** R04, R45, R48 - `docs/work-plan/V84-WORK-ROWS.tsv`
**Blocked by:** None - can start immediately

Fourteen items have an `Item.wz` or `Character.wz` image in this tree - so they exist, they render,
their stats load - and no `String.wz` entry, so `ItemInformationProvider.getName` returns null and
the inventory, the quest window and every chat token draw an empty slot. One of them is handed over
by a live Evan quest and one by a live Evan quest script.

**Twelve of the fourteen are in v84's `String.wz`. Two are not.** `1702248` and `1702254` have **no
node at all** in the pristine carve - not under `Eqp/Weapon`, which is where
`ItemInformationProvider.getStringData` (`ItemInformationProvider.java:266-268`) routes ids
1300000-1799999, and not anywhere else under `Eqp.img/Eqp` (a full depth-2 dump is 7,287 lines and
contains neither id). Those two have a different source; see R48.

## R04 - item 4032526 has an `Item.wz` node and no `String.wz` name

`wz/Item.wz/Etc/0403.img.xml:14006` carries `04032526`. `wz/String.wz/Etc.img.xml` carries 4032520
(:9497), 4032521 (:9501), 4032522 (:8656), 4032523 (:8660), 4032524 (:8664), 4032525 (:8668), 4032527
(:95), 4032528 (:98) and 4032529 (:102) - **and not 4032526**. The item is awarded at
`wz/Quest.wz/Act.img.xml:4837` (quest **22572**, `22572/0/item/0`, count 1) and consumed at
`wz/Quest.wz/Act.img.xml:4846` (`22572/1/item/0`, count -1), so an Evan on 22572 is handed a nameless
item and told to give it back.

`docs/wz-baseline/add-list/Item.txt:304` makes 4032526 a v84-new item.

Pristine v84 `String.wz/Etc.img/Etc/4032526` states both leaves in full, verified character for
character against the carve:

- `name` = **John's Map**
- `desc` = **John's map that contains information on some island. That island is said to be the
  place where the dragon lies asleep.**

Both are pure ASCII with no trailing whitespace, so these two values are safe to use verbatim.

**There is no "4032526 block" to preserve.** The nine siblings sit in three widely separated regions
of the file (lines 95-102, 8656-8668, 9497-9501); 4032526 has no adjacent sibling anywhere. Pick an
insertion point, state it in the Delivered note, and match the surrounding indentation - the file has
no ordering the merge can honour.

## R45 - item 1003028 has a `Character.wz` image and no `String.wz` name

`wz/Character.wz/Cap/01003028.img.xml` exists, which is why the cap renders and its stats load.
`wz/String.wz/Eqp.img.xml` has no entry for it (zero hits for the id). The cap is granted by
`scripts/quest/22002.js:44` (`qm.gainItem(1003028, 1, true);`, rendered at `:41` as
`#i1003028# 1 #t1003028#`), a live Evan quest script, so this is reachable today by any Evan.

**The carve has it:** `Eqp.img/Eqp/Cap/1003028/name` = **Straw Hat**. This row needs no fallback
source and no UNKNOWN escape - just read the carve.

Recorded as ticket 45 blocker **B5**.

## R48 - twelve v84-new equips have an image and no name

Twelve ids, all with a `Character.wz` image in this tree and no `String.wz/Eqp.img` entry - both
halves verified individually for all twelve:

**1003029, 1003030, 1003043, 1042180, 1052226, 1060138, 1061160, 1072418, 1072425, 1082261,
1702248, 1702254.**

`ItemInformationProvider.getName` (`ItemInformationProvider.java:1396-1407`, returning null at
`:1402` when `strings == null`) returns null on a missing `String` node, which is what every one of
these produces. Found by the item-source sweep - see `docs/work-plan/V84-ITEM-SOURCE-SWEEP.md:276-281`,
which lists the same twelve.

### Ten come straight from the carve

`1003029` Former Hero Female Face, `1003030` Former Hero Male Face, `1003043` Korean value
`순록의 뿔`, `1042180` `Checkered Shirt ` (**trailing space**), `1052226` Former Hero Robe,
`1060138` `Denim Shorts ` (trailing space), `1061160` `Denim Skirt ` (trailing space), `1072418`
`Black Boots ` (trailing space), `1072425` Freud's Shoes, `1082261` Freud's Gloves.

### Two are absent from the carve and come from `evan-xml`

**`1702248` and `1702254` have no node in v84's `String.wz` at all.** They are in
`porting-resources/evan-xml` - absolute path
`D:\games\MapleStory\Server\porting-resources\evan-xml\extracted\Evan WZ\String\Eqp.img.xml:17413-17420` -
carrying `name="루돌프"` and `desc="#c건, 너클을 제외한 모든 무기#에 착용이 가능한 무기이다."`.

Use that source for these two ids and state in the Delivered note that it is a tier-2 source, not the
carve. **If the owner refuses a non-carve source, these two close as UNKNOWN and stay nameless** - but
do not record them UNKNOWN without first noting that a source exists on disk.

## Precedent

Same family and same fix across all three rows: recover the name (and `desc`, where the source has
one) and merge the leaf additively.

- **Commit `8c24b6fa5`** is the reference edit and **the only genuinely additive precedent in this
  area**: `wz/String.wz/Eqp.img.xml | 6 ++++++`, zero deletions, for the two Evan medals
  1142152/1142155. Copy its shape.
- **Commit `df9e779a9`** is often cited alongside it but is **not** an additive merge: it is
  `wz/String.wz/Npc.img.xml | 70 ++++---`, **35 insertions and 35 deletions**, replacing existing
  English name values with Korean ones ("force-merge the 11 remaining Korean names"). Useful as
  evidence the carve is the accepted source; not the shape to copy here.
- The carve is `SOURCES.md` tier 1 and lives at the absolute path
  `D:\games\MapleStory\Server\porting-resources\wz-data\v84\` - a **sibling of this repo**, not a
  subdirectory of it. `docs/work-plan/SOURCES.md:14` is the authority. It holds **17** `.wz` files
  dated 2010-03-29 (`SOURCES.md` says 18; the directory listing says 17), byte-identical to a fresh
  carve of `GMSSetupv84.exe`, and it is **read-only**.
- Read it with `docs/wz-baseline/tool-peek/bin/Release/net10.0-windows/WzPeek.exe`, which has exactly
  two subcommands: **`dump`** and **`scan`**.

### Encoding hazard - this row cannot be done with a naive WzPeek capture

`WzPeek.exe` writes to the console in the ANSI codepage. `Eqp.img/Eqp/Cap/1003043/name` prints as
`??? ?`; the real value is `순록의 뿔`, recovered only by forcing
`[Console]::OutputEncoding = [System.Text.Encoding]::UTF8` before the call. The same hazard silently
eats the Korean values for 1702248/1702254 and can eat the **trailing spaces** on 1042180, 1060138,
1061160 and 1072418. Force UTF-8 output and verify the trailing spaces survive, or the "byte for
byte" criterion below passes against corrupted data.

## Acceptance criteria

- [ ] `wz/String.wz/Etc.img.xml` carries `4032526/name` = `John's Map` and `4032526/desc` = the
      full sentence quoted above, byte for byte against the carve. The Delivered note states where
      in the file it was inserted and why.
- [ ] `wz/String.wz/Eqp.img.xml` carries `1003028/name` = `Straw Hat`, matching the carve.
- [ ] `wz/String.wz/Eqp.img.xml` carries a `name` entry for each of 1003029, 1003030, 1003043,
      1042180, 1052226, 1060138, 1061160, 1072418, 1072425, 1082261 from the carve, with trailing
      spaces preserved where the carve has them and Korean preserved where the carve has it.
- [ ] 1702248 and 1702254 carry the `evan-xml` values, **or** appear in this ticket's UNKNOWN list
      with the owner's explicit refusal of the tier-2 source recorded. The carve query proving v84
      has no node for either id is recorded either way.
- [ ] `ItemInformationProvider.getName` returns a non-null, non-empty string for every id not on the
      UNKNOWN list - asserted by a `*RealLoad` test with one assertion per id, so a single missing
      entry names itself.
- [ ] The same test asserts 4032520-4032525 and 4032527-4032529 still resolve, i.e. the merge added
      a sibling and did not rewrite anything.
- [ ] Every edited XML file's diff is confined to the added `name`/`desc` leaves. `core.autocrlf` is
      true in this worktree; a writer that forces the wrong line ending shows every line as changed.
- [ ] The test class is named in this ticket and invoked as `mvnw.cmd -o test -Dtest=<ClassName>`.
      Surefire's `default-test` execution **excludes** `*RealLoad` at `pom.xml:239`
      (`<exclude>**/*RealLoad.java</exclude>`); the **include** is in the separate `real-load-tests`
      execution at `pom.xml:272-274`. **Do not run maven while sibling agents are active** - they
      collide on `target/`. State the invocation and hand it to the orchestrator.

## Do not

- Do not invent a name, a description, or a translation for any id. For 1702248/1702254 the choice is
  `evan-xml` or UNKNOWN, nothing else.
- Do not translate the Korean values into English. `df9e779a9` established Korean-from-carve as
  acceptable; a translation is invention.
- Do not strip the trailing spaces from 1042180, 1060138, 1061160 or 1072418. They are in the carve.
- Do not write to `D:\games\MapleStory\Server\porting-resources\` or `D:\games\MSv84\client\`. Both
  are read-only.
- Do not rewrite the surrounding `String.wz` entries. The merge is additive, leaf by leaf; the
  `Item.wz` and `Character.wz` images are untouched by this ticket.
- Do not drop an empty-valued leaf while copying a section. `SOURCES.md`: empty is not absent.
- Do not compare positionally. Match on node name, never on storage order.

## Closure for 1702248 and 1702254 - UNUSED, no name merged (2026-08-18)

The owner authorised one tier-2 lookup on <https://dreamms.gg/items> for these two ids. Result, and
it is **not v84 data** - it is a modern private server's own database, cited here only as context:

| id | dreamms.gg name | dreamms.gg source |
|---|---|---|
| 1702248 | `Rudolph` | Christmas event Surprise Style Box, 2022 and 2023 - a dreamms event, not v84 |
| 1702254 | `루돌프` (untranslated) | *"No known source for this item."* |

Neither name may be merged, because **neither id is obtainable on this server**. Verified:

- `wz/Etc.wz/Commodity.img.xml` - **no row** for either id, so the cash shop cannot sell them.
  (`V84-ITEM-SOURCE-SWEEP.tsv:122,124` reached the same conclusion independently: `NO_SOURCE_IN_V84`.)
- `wz/` - the only files mentioning either id are the art images
  `wz/Character.wz/Weapon/01702248.img.xml` and `01702254.img.xml`. No quest act, no reactor, no NPC.
- `scripts/` and `src/` - zero references; the sole hit is the explanatory comment at
  `src/test/java/server/V84MissingItemNameRealLoad.java:22`.
- Database (SELECT-only) - zero rows in `inventoryitems`, `drop_data`, `drop_data_global`,
  `shopitems`, `reactordrops`, `mts_items`, `nxcode_items`, `makercreatedata`, `makerrewarddata`.

Nothing in the game can hand a player either item, so nothing renders their missing name. v84 ships
the art and no name and no way to obtain them; that is consistent, not a defect. **UNKNOWN is the
final answer for both ids** - the acceptance box "appear in this ticket's UNKNOWN list" is satisfied
here. If a future ticket ever makes one obtainable, the name must come from a v84 source, not from
this table.
