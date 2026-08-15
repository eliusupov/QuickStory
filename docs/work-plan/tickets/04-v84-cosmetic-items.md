# 04 — v84 cosmetic items usable in game

**Blocked by:** 03

**Status:** ready-for-agent

**Merge procedure: [`../WZ-MERGE-PROCEDURE.md`](../WZ-MERGE-PROCEDURE.md)** — established by ticket 03 and proven end to end. Use its tool (`docs/wz-baseline/tool-merge/`); do not invent a second way. Start with a dry run (`WzMerge merge <v84>/X.wz <live>/X.wz - <add-list> <conflicts>`) and read the conflicts before merging anything.

## What to build

The ~412 items v84 added — predominantly hairstyles (Evan Hair, Tighty Bun, Babish, Spiky Shag families in all eight colours) plus Evan equipment and Crimson Sky drops — exist, render on a character, and can be obtained.

Deliberately first among the content tickets: it is the lowest-risk, most visible slice and it exercises the full `Item.wz` + `Character.wz` + `String.wz` + server-XML path on content nobody will miss if a retry is needed.

Obtainability matters — an item that exists but has no source is not delivered. Route the cosmetics through whatever your server already uses (beauty salon NPC, NX shop, GM command) rather than inventing a new one.

Guard the custom-content protect list from ticket 02 throughout: `Character.wz` is where your client's 18.6 MB of custom data lives, so this is the single most dangerous file to merge carelessly.

## Acceptance criteria

- [x] Added items present in client WZ and server XML
- [x] Names resolve correctly from `String.wz` — no blank or placeholder labels *(two stated exceptions below)*
- [ ] Hairstyles render correctly on both genders — **human step, staged not performed**
- [x] Items are obtainable through an existing in-game route — wiring done; in-game confirmation is a human step
- [x] Protect-list nodes in `Character.wz` verified still present after the merge

---

# Result — 2026-08-16

Re-run from scratch after ticket 03e fixed the merge tool. The earlier `Server\wz-merge\04\`
output and the earlier server-XML edits were discarded (`git checkout` of ticket 04's files plus
deletion of its 148 untracked `.img.xml`), then everything below was produced fresh.

## The deliverable: path lists

**Staged binaries do not compose** — ticket 05 also writes `Character.wz`, so installing two full
staged files from the same base loses one set. The authoritative artefacts are the path lists;
the staged `.wz` exist only to prove the lists are correct.

| list | rows | binary merge | server XML |
|---|---:|---|---|
| `docs/wz-baseline/merge-lists/04/Character.paths.txt` | 246 | added **240**, refused 6 | added **237**, refused 9 |
| `docs/wz-baseline/merge-lists/04/Item.paths.txt` | 391 | added **391**, refused 0 | added **390**, refused 1 |
| `docs/wz-baseline/merge-lists/04/String.paths.txt` | 394 | added **385** (forced 30), refused 9 | added **384** (forced 30), refused 10 |

Composition order: **04 before 05** for `Character.wz` (05 adds `TamingMob/**`, which this list
excludes); `String.wz` is shared with 05 and 06 and the three lists are disjoint by `.img`.
`String.paths.txt` also carries the single `String.wz/Pet.img/5000067` row: `Pet.img` is outside
the file grant this ticket was given, but it is the name for `Item.wz/Pet/5000067.img`, which this
ticket adds, and shipping an item without its name row makes it unobtainable — every route filters
on `getName != null`. Nobody else claimed `Pet.img`.

`Etc.wz` — **deliberately not merged**, though ticket 04 owns it. Its 10,638 add-list rows are
~8,900 `Commodity.img/<sn>/Bonus` (a field nobody should bulk-import), 1,518 new cash-shop SNs
that mostly reference v84 items this ticket does not import (a `Commodity` entry pointing at a
missing item is a dead shop button), 94 `NpcLocation` rows of which 10 are deny-listed and the
rest place NPCs owned by tickets 06/08, and `MakeCharInfo.img/EvanChar{Male,Female}`, which
belongs to the Evan job ticket. Nothing in ticket 04's scope needs any of it: obtainability is
served by routes that already exist.

## Force decisions — 30, all one rule

`--force docs/wz-baseline/merge-lists/COLLISION-FORCE.txt`, applied to **both** the binary and the
XML side. 30 of its 37 rows fall inside ticket 04's path lists; every one is an id whose live value
is the literal `MISSING NAME` / `MISSING INFO` stub and whose v84 value is real text (03c's triage,
verified again here by the tool printing the old and new value per row). Breakdown:
`Cash.img` 10, `Consume.img` 1, `Ins.img` 2, `Etc.img/Etc` 3, `Eqp.img` 14.

The remaining **7 force rows were kept out of reach**, not merely unused: `Eqp/Dragon` (a collapsed
root covering 12 names) and `Eqp/Taming/{1902040,1902041,1902042,1912033,1912034,1912035}` are
excluded from `String.paths.txt`, so `--force` cannot touch them. They are ticket 05's.

## Refusals — every one decided

**`String.wz`, 9 refused.** `Eqp/Hair/{31660..31667, 33101}`. Measured with `WzMerge dump` against
both trees: the live value and v84's are **identical strings** ("Black Tighty Bun" … "Red The
Coco"). No-op refusals; keeping local costs nothing and forcing them would be pointless risk.

**`Character.wz`, 6 refused.**
- `Accessory/01142153.img`, `01142154.img` — live is a strict superset (Cosmic added an
  `info/level` subtree turning them into level-up medals). Keep local, zero cost.
- `Dragon/019{4,5,6,7}2002.img/info/level` — `COLLISION-TRIAGE.md` "Ambiguous". Kept local.
  Adopting v84's curve is not a data fix: `ItemInformationProvider.java:315-322` blind-scans
  `Character.wz` subdirectories, reads these as generic **equip** levelling, and v84's multi-level
  rows would switch on a path that is dormant today. That is an owner decision, not a cosmetics one.

**Server XML, 4 extra refusals over the binary side.**
- `Item.wz/Consume/0200.img/02001500` and `String.wz/Consume.img/2001500` — ticket 03's tracer,
  already committed in the server tree. The live client binary still lacks it, which is exactly why
  the two sides differ by these two rows.
- `Character.wz/Glove/01082262.img/{ladder/0, ladder/1, rope/0}` — refused with *"no child element
  at indent 4 … the additive gate is an indentation scan and would be blind here"*. These are
  client animation frames; the server reads no sprite frames from `Character.wz`, so the divergence
  is inert server-side. The three frames **are** present in the staged client `.wz`.

## Proof that nothing pre-existing moved

`WzMerge hash` over **every image in the file**, pre vs post — not a presence check, a SHA-256 of
each image's decoded content (canvases by the digest of their compressed pixel bytes).

**`Character.wz`: 7,241 image digests compared. Exactly 12 changed, and all 12 are the 12 images
this ticket's path list names sub-image rows for.** Nothing else in a 206 MB file moved.

| image | my rows | what changed |
|---|---:|---|
| `00002000.img` | 20 | 20 new children (`fly2`, `Awakening`, …), 158 → 178, none lost |
| `Afterimage/mace.img` | 6 | 6 new frames `11`–`16`, 12 → 18, none lost |
| `Cap/01002728.img` | 1 | new child inside `info`; child count unchanged, nothing lost |
| `Dragon/019{4,5,6,7}2002.img` | 2 each | new child inside `info` (the `info/level` row was refused) |
| `Glove/01082262.img` | 11 | new frames inside 8 existing action nodes; 34 → 34 children, none lost |
| `Weapon/013820 58, 01452058, 01472069, 01492024` | 16/16/5/15 | new child inside `info` |

Against the two manifests that define custom content: `protect-list/Character.txt` (2,987 rows) ∪
`modified-list/Character.live.txt` (5,114 rows) cover **5,120 distinct images**. Of those, **10
changed** — `Cap/01002728`, the 4 `Dragon`, `Glove/01082262`, the 4 `Weapon` — and each changed by
gaining a child, with **zero children lost or altered** (child-level digest diff, above). The other
**5,110 are digest-identical**. Ezorsia's 18.6 MB of HD art is untouched.

`String.wz`: of 20 root images, exactly the 5 ticket 04 owns changed. `Map.img`, `Mob.img`,
`Npc.img` (ticket 06), `MonsterBook.img`, `Skill.img`, `Pet.img`, `ToolTipHelp.img` — **byte-identical**.
Inside `Eqp.img`: `Eqp/Hair` 1,518 → 1,558 children with **0 overwritten**; `Eqp/Taming` 47 → 47 and
`Eqp/Dragon` 12 → 12, **0 changed, 0 added** — ticket 05's territory provably untouched. The 30
forced ids are the only overwrites anywhere, and they are exactly the intended ids.

`Item.wz`: 26 images gained children, 2 are brand new (`Cash/0562.img`, `Pet/5000067.img`) = the 28
images the tool content-checked. No other image changed.

**Gate fires (6.3):** re-running each merge against its own output, without `--force`:
`Item added 0 refused 391`, `String added 0 refused 393`, `Character added 0 refused 246` — exit 5
on all three.

**Server XML, byte level:** across the 43 `.img.xml` ticket 04 wrote, a sorted line-multiset diff
against `HEAD` finds **60 lines lost, and all 60 are `MISSING NAME` / `MISSING INFO` placeholders** —
the 30 forced rows × 2 lines. Every other line is an insertion.
Two files (`Item.wz/Cash/0501.img.xml`, `Item.wz/Consume/0238.img.xml`) show large `git diff`
deletion counts; those are **re-sorts**, not losses — the splice inserts at sorted position and
Cosmic's XML preserves WZ insertion order, so existing siblings relocate. Verified 0 lines lost in
both. (Do not compare these files through a PowerShell 5.1 pipeline without forcing
`[Console]::OutputEncoding = UTF8` — the console codepage mangles the Korean and typographic
characters and manufactures hundreds of phantom losses.)

## Hashes

| file | live == backup (before) | pre snapshot | staged output |
|---|---|---|---|
| `Character.wz` | `ED787285951C1388F3CF2A515999AB1C45307265BA9B9D01F2BA3F75F81C371C` | same | `F4ABA39499BD31797C40D84DE117CD6B59F4707FDD9E432732885D78BCE6A75F` (211,078,085 B) |
| `Item.wz` | `33D7E2D8416A6523935E9FC933107CA3B66F6DDE869667FFB0551746A36C5E44` | same | `5F407FABFF677FB321996278AD104CC7D05B7F2A165A352D85B9F3A628850EC6` (19,086,553 B) |
| `String.wz` | `9437DEB8CE481DAE4909097EBFB366D24BACCD73D55D3ED00FA3198603CAE499` | same | `19B472A598118A8DABE536E0EA801454E33B49F676A1529C65F248B2E379508E` (3,604,797 B) |

**After the ticket: all 18 live `.wz` still SHA-256-match the backup**, and no `.partial` / `.TEMP` /
`.merged` exists beside the client. Nothing was installed.

## Obtainability — existing routes only, no new mechanism

Two routes already exist and both now work for this content:

- **`!item <id>`** (`client/command/commands/gm2/ItemCommand.java:37`, GM 2) and **`!hair <id>`**
  (`gm3/HairCommand.java:33`, GM 3). Both gate on `ItemInformationProvider.getName(id) != null`,
  which reads `wz/String.wz/{Eqp,Etc,Consume,Ins,Cash}.img.xml` — the files this ticket wrote. Every
  named v84 cosmetic is reachable the moment the XML is in the tree. Zero code change.
- **Beauty salon.** Hair ids are hardcoded per NPC script and filtered through
  `pushIfItemExists → NPCConversationManager.getCosmeticItem` (`:539-556`), which is the same
  `getName` check. `scripts/npc/1012103.js` (Natalie, Henesys VIP) gained the **five named v84 hair
  families' base ids** — male `33030` Babish, `33050` Spiky Shag, `33150` Evan Hair (M); female
  `31990` Evan Hair (F), `34060` Bow Hair. Two lines. The existing loop adds the player's *current*
  colour digit, so the haircut menu gains one entry per family, in the colour the character already
  wears; the eight colours come from the salon's separate dye branch. `pushIfItemExists` drops the
  ids entirely while the WZ merge is not installed, so the edit is safe to land ahead of the client
  copy.

Not done, on purpose: no `shops`/`shopitems` rows and no `Etc.wz/Commodity.img` cash-shop entries
for the ~150 new equips. Pricing ~150 v84 cosmetics is a balance decision this ticket was not asked
to make, and `!item` already satisfies "obtainable through an existing route".

The other VIP salons (`1012117.js`, `1052100.js`, `2100005.js`, …) still offer only their original
arrays. Extending them is a one-line-each copy of the same edit if wanted.

## Two gaps, stated rather than papered over

1. **`Hair/00034040`–`00034047` have no name in v84 at all.** Eight sprites, `cash=1`, present in
   v84's `Character.wz` and absent from `String.wz/Eqp.img` in **both** v84 and the live client.
   Every server-side route to a hair filters on `getName != null`, so these eight render but cannot
   be selected. The art is merged (it costs nothing and a later ticket may want it); names were
   **not invented**. `V84TracerNodeTest.unnamedV84HairFamilyIsPresentButUnnamed` asserts the current
   state and says what to do if someone names them.
2. ~~`String.wz/Pet.img/5000067` left uncommitted.~~ **Closed.** It was going to ship an item
   whose name only existed in someone's working tree — unobtainable on a clean checkout. The row is
   now on `String.paths.txt` and both sides were re-run. `Pet.img` still has no formal owner.

## Server-load verification — real output

`src/test/java/server/V84CosmeticNodeTest.java` — six tests reading through `XMLWZFile` /
`XMLDomMapleData`, the classes the running server uses, with the explicit `Path.of("wz", …)`
construction `V84TracerNodeTest`'s class comment requires. It is a **sibling** of that class rather
than more methods inside it, because tickets 04 and 05 were both in flight and 05's edits to
`V84TracerNodeTest` (and to `StatEffect.java`, which they need to compile) are still uncommitted;
committing that file would have dragged their half-finished work in. Same harness, not a second one.

```
./mvnw -o test -Dtest=V84CosmeticNodeTest -DfailIfNoTests=true
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0 -- in server.V84CosmeticNodeTest
[INFO] BUILD SUCCESS

./mvnw -o test
[INFO] Tests run: 1910, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

The assertions are reading real data, not tautologies: three of them failed first and had to be
corrected against the tree — `1142154`'s `medalTag` is 83 (not 82 as the triage note implied), and
v84 pads `"Dragon Master's Proof "`, `"DS Medal Basket "`, `"Dragon Types and Characteristics
(Vol.I) "` with a trailing space.

What they cover: all 40 named v84 hair ids have **both** a parsing sprite (`info/islot == "Hr"`) and
a non-blank name; none of the 30 forced ids still reads `MISSING NAME`, with four spot-checks on the
actual text; the 9 Ezorsia hair names still read Ezorsia's value; the two Cosmic level-up medals
kept their `info/level` subtree; a sample of new equips and the two brand-new `Item.wz` images parse.

---

## Human steps — staged, not performed

I cannot launch the game. Everything below is exact; nothing here has been run.

### H0. Install the client files — **only after ticket 05's `Character.wz` is composed with 04's**

**Do not copy `04-r2\Character.wz` on its own if ticket 05 has already installed its own
`Character.wz`, and vice versa — the second copy silently reverts the first.** Both were merged from
the same live base. The composed install is: merge `04/Character.paths.txt` **then**
`05`'s path list into one output, or run 05's merge against 04's staged output. `Item.wz` and
`String.wz` from this ticket are safe to install as they stand only if no other ticket has installed
those two files since 2026-08-16; check by hash first.

```
:: 1. close MapleStory AND any HaRepacker window
:: 2. confirm the live files are still the ones this ticket merged from
certutil -hashfile D:\games\MapleStory\Item.wz SHA256
    expect 33D7E2D8416A6523935E9FC933107CA3B66F6DDE869667FFB0551746A36C5E44
certutil -hashfile D:\games\MapleStory\String.wz SHA256
    expect 9437DEB8CE481DAE4909097EBFB366D24BACCD73D55D3ED00FA3198603CAE499
certutil -hashfile D:\games\MapleStory\Character.wz SHA256
    expect ED787285951C1388F3CF2A515999AB1C45307265BA9B9D01F2BA3F75F81C371C

:: 3. copy, one at a time, checking the size after each
copy D:\games\MapleStory\Server\wz-merge\04-r2\Item.wz      D:\games\MapleStory\Item.wz
dir  D:\games\MapleStory\Item.wz            :: expect 19,086,553
copy D:\games\MapleStory\Server\wz-merge\04-r2\String.wz    D:\games\MapleStory\String.wz
dir  D:\games\MapleStory\String.wz          :: expect 3,604,797
copy D:\games\MapleStory\Server\wz-merge\04-r2\Character.wz D:\games\MapleStory\Character.wz
dir  D:\games\MapleStory\Character.wz       :: expect 211,078,085
```

**FAIL** if any expected hash in step 2 does not match — another ticket installed in the meantime;
stop and re-run the merge from a fresh `pre\` rather than copying.

### H1. Names — no placeholder labels

Start `launch.bat`, log in with `localhome.exe` (**not** `localhome.evan.exe` — 01's binary patch
would make a failure ambiguous), on a GM-3 account.

```
!item 1082262      -> "Dragon Master's Proof"   (was MISSING NAME)
!item 1092067      -> "Transparent Shield"
!item 4161049      -> "Dragon Types and Characteristics (Vol.I)"
!item 5530001      -> "DS Medal Basket"
```

**PASS**: each lands in the inventory with the name above (a trailing space is normal), and the
tooltip description is real text rather than `MISSING INFO`.
**FAIL**: any of them shows `MISSING NAME`, an empty label, or fails to spawn ("item does not
exist" means the server XML did not load — check `wz/String.wz/*.img.xml` in git).

### H2. Hairstyles render on both genders — *the criterion I could not tick*

```
:: male character
!hair 33030   -> Black Babish Hair
!hair 33053   -> Blonde Spiky Shag
!hair 33150   -> Black Evan Hair (M)

:: female character
!hair 31990   -> Black Evan Hair (F)
!hair 34064   -> Green Bow Hair
```

**PASS**: the hair changes immediately, is drawn in the character window, in the field, and behind
the body when the character faces away (`hairBelowBody`), and the name in the equip window matches.
**FAIL**: an invisible head, a garbled sprite, the old hair persisting, or the client dropping the
connection on the appearance packet. Male ids on a female character (and vice versa) are *expected*
to look wrong — that is not a failure, MapleStory hair is gender-specific.

Known: `!hair 34040`–`34047` will be **rejected** ("not a valid hair"). That is the documented
gap — v84 ships those sprites with no name — not a merge fault.

### H3. Obtainability through the salon

Give a character `5150001` (VIP Hair Coupon) — `!item 5150001` — go to **Henesys, NPC 1012103
(Natalie)**, choose "Haircut".

**PASS**: the style preview list now includes the five new families in the character's own colour,
and picking one consumes the coupon and applies the hair.
**FAIL**: the list is unchanged (script not reloaded — `!reloadscripts` or restart), or picking a
new style throws in the script log.

### H4. Rollback — verified path, all-or-nothing

Client side (client closed):

```
copy D:\games\MapleStory\Server\_backup\client-v83-EzorsiaV2-2026-08-15\Character.wz D:\games\MapleStory\Character.wz
copy D:\games\MapleStory\Server\_backup\client-v83-EzorsiaV2-2026-08-15\Item.wz      D:\games\MapleStory\Item.wz
copy D:\games\MapleStory\Server\_backup\client-v83-EzorsiaV2-2026-08-15\String.wz    D:\games\MapleStory\String.wz
```

Server side: `git revert` this ticket's commit. **Do not use a blanket `git checkout -- wz/`** —
tickets 05 and 06 have uncommitted XML in the same tree and it would take theirs with yours.
