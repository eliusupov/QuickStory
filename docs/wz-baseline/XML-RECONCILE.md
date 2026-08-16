# XML-RECONCILE — every row the composed binary merge refuses, checked against `wz/`

Ticket 03j. **Scope: the 23 rows the composed run refuses, and whether the server XML tree carries
them anyway.** Written because otherwise nobody knows how far client and server diverge.

## Why a divergence exists at all

The positional-array gate landed in **03g**. Ticket **04** (`e8b06939a`) had already spliced its
rows into `wz/` before that, and **nothing un-applied the server side**. `merge-lists/composed/README.md`
says the XML tree "is already composed" — that covers *what a ticket adds*, not *what a later gate
refuses*. So for every row the composed run refuses, the question "is it in the XML?" has to be asked
one row at a time, and it was not until now.

The binary side does not have this problem: it always restarts from the pristine `pre\` snapshot, so
a refusal there really is an absence.

**Relation to `REGRESSION.md`.** Ticket 16 already counted the composed install exactly — "1,662 rows
offered → 1,639 merged + **23 refused**" — and this document inventories that same 23. It is not a
second count. 16 answers *what the binary refused*; the question left open, and the one answered
here, is **which of those 23 the server XML kept anyway**. That is the whole divergence.

## Method — derived, not eyeballed

Source of refusals: the 03h run logs, `D:\games\MapleStory\Server\wz-merge\03h\*.conflicts.txt`, one
per composed file. **All eleven were read; eight are header-only (0 refused).** Each refused path was
then resolved against `wz/` by walking the `.img.xml` from its root by `name` attribute. For the rows
that *are* present, the second question — divergence or not — was answered from git: a node present
in `wz/` that **no ticket commit wrote** is the tree's own pre-existing v83 content, and the binary
refusing to overwrite it means both sides keep the same thing. Only a node a ticket **added** is a
real divergence.

**Two separate questions, and conflating them is the trap.** "Does the server read this node?" and "does
the XML disagree with the client here?" are independent. A node can be read by the server every day and
still be perfectly safe, because both trees hold the same thing. **Only the intersection — divergent
*and* server-readable — can change behaviour.**

| composed file | refused | in XML | divergent | server-readable | **divergent AND readable** |
|---|---:|---:|---:|---:|---:|
| `Character.wz` | 12 | 12 | **6** (8 once 03i's two deny rows count) | 6 | **0** |
| `Item.wz` | 2 | 2 | **2** | 2 | **2** |
| `String.wz` | 9 | 9 | 0 | 9 | **0** |
| `Map/Mob/Morph/Npc/Quest/Reactor/Skill/Sound` | 0 | – | – | – | – |
| **total** | **23** | **23** | **8** | **17** | **2** |

Read the last column: **exactly 2 of the 23 could ever have changed server behaviour, and both are
fixed.** The other 15 readable rows are read constantly and are *identical on both sides* — the binary
refused precisely because the target already held that content. The 6 divergent Glove rows are the
mirror image: really divergent, never read.

Which reads are real, so the column can be checked rather than believed:

| rows | read by |
|---|---|
| `Character.wz/Accessory/*.img` (2) | `ItemInformationProvider.getEquipStats` via `equipData` (`:147`, `:544`) |
| `Character.wz/Dragon/*/info/level` (4) | `ItemInformationProvider` equip-levelling (`:1908-1911`) |
| `String.wz/Eqp.img/Eqp/Hair/*` (9) | `ItemInformationProvider` item names (`:152`, `:176`) |
| `Item.wz/…/reward/43` (2) | `ItemInformationProvider.getItemReward` (`:1664-1686`) |
| `Character.wz/Glove/01082262.img/*` (6, 8 w/ 03i) | **nothing** — no Java or script reads glove animation frames |
| `Map.wz/Obj/effect.img/*` | **nothing** — no Java reads `Map.wz/Obj` at all (`getData("Obj/` has zero hits) |

Plus one row that is **not** a refusal but a **hole** the composed list exists to fill — `Map.wz`,
below. It is the only row in this document that was *missing* from the XML rather than present.

## The inventory

### `Item.wz` — 2 refused, 2 divergent, **2 server-readable. Fixed.**

| row | in XML | readable | disposition |
|---|---|---|---|
| `Item.wz/Consume/0202.img/02022503/reward/43` | yes, added by `e8b06939a` | **yes** | **REVERTED** |
| `Item.wz/Consume/0202.img/02022514/reward/43` | yes, added by `e8b06939a` | **yes** | **REVERTED** |

`reward` is a positional array. The live client holds `0..42`; v84 inserted an entry earlier and every
later slot shifted, so v84's index 43 is content-identical to a slot the array already has. The binary
merge refuses it. On the server side it landed: both boxes carried **44** slots with item `2020014`
at **both** `43` and `16`, byte-identical (`x10`, `prob 50`, `Effect/BasicEff/Event1/Good`).

`ItemInformationProvider.getItemReward` (`src/main/java/server/ItemInformationProvider.java:1664-1686`)
iterates **all** children of `reward` and sums `prob` with no de-duplication, so this is live
behaviour, not cosmetics: `2020014` rolled at **double weight** and `totalprob` sat 50 above the
table the client ships after install.

Proven after the revert, against the actual post-install binary `wz-merge\03h\Item.wz`:

| | slots | names | `2020014` | totalprob |
|---|---|---|---|---|
| `02022503` client (03h binary) | 43 | `0..42` | – | **19864** |
| `02022503` server XML | 43 | `0..42` | once, slot `16` | **19864** |
| `02022514` client (03h binary) | 43 | `0..42` | – | **18363** |
| `02022514` server XML | 43 | `0..42` | once, slot `16` | **18363** |

Slot-name sets equal and **every slot's full content equal** on both boxes, not just the totals.
Guarded by `V84XmlReconcileTest.goldenPigEggRewardTablesMatchTheComposedClientTable`.

> **This revert is re-breakable and the guard for it is not in place.** Both rows are still on
> `merge-lists/composed/Item.paths.txt:155-156`, and **no `Item.wz` row is on `COLLISION-DENY.txt`**.
> Per `WZ-MERGE-PROCEDURE.md` §4.4 the XML-side gate is a line-text scan that catches the interior
> write, the occupied slot and the hole but **not the content-identical append** — which is exactly
> this shape. So a future `WzMerge xml … composed\Item.paths.txt` would put `reward/43` straight
> back, silently, with no refusal. The binary side is safe (its own gate digests the nodes and
> refuses). **The durable fix is two deny rows**, and the deny-list is closed against direct edits
> for the ticket that owns it; this is reported, not applied. The test above is the interim guard.

### `Map.wz` — 0 refused, but a hole. **Filled.**

`Map.wz/Obj/effect.img`'s `quest/gate` children were `0,1,2,3,4,5,`**`7`** — ticket 08 (`2a89da169`)
spliced `7` without `6`, because `WzMerge deps` resolves what a map *references* and its map
references only `7`. v84 appends **both** (`add-list/Map.txt:581-582`) and the live client has `0-5`.
Today's XML gate would refuse `gate/7` outright as "would leave a GAP".

**Decision: add `6`, do not remove `7`.** Three reasons, in order:

1. `gate/6` is **on the add-list** and is a pure addition — the composed list already carries it as
   its single `$fill` row (the `$fill` table in `compose.ps1`), for exactly this reason.
2. The post-install client **will have both**: `wz-merge\03h\Map.wz` dumps `quest/gate` as
   `0,1,2,3,4,5,7,6`. Removing `7` from the XML would have *created* a divergence to close a hole.
3. Removing `7` would revert content ticket 08 deliberately staged.

Content copied from `<v84>\Map.wz` via `WzMerge dump`, not invented: 10 canvas frames, each
`182x223`, `origin 93,112`, `z 0`, `delay 150`, plus `repeat 9`. Verified: the whole `quest/gate`
array in `wz/Map.wz/Obj/effect.img.xml` now matches `wz-merge\03h\Map.wz` slot-for-slot and
frame-for-frame, including width/height/origin/z/delay/repeat. **Inert on the server** — nothing under
`Map.wz/Obj` is read by any Java source. Guarded by `V84XmlReconcileTest.questGateArrayHasNoHole`.

### `Character.wz` — 12 refused, 6 divergent today, **8 after 03i. Left in place, deliberately.**

Six rows on one glove, all refused as positional-array writes into an **occupied slot**:

| row | array in XML | in XML | readable | disposition |
|---|---|---|---|---|
| `…/01082262.img/stabTF/0/lGlove` | `0..3` | yes, `e8b06939a` | no | **LEFT** |
| `…/01082262.img/stabTF/1/lGlove` | `0..3` | yes, `e8b06939a` | no | **LEFT** |
| `…/01082262.img/swingP2/2/rGlove` | `0..2` | yes, `e8b06939a` | no | **LEFT** |
| `…/01082262.img/swingPF/0/lGlove` | `0..3` | yes, `e8b06939a` | no | **LEFT** |
| `…/01082262.img/swingPF/1/lGlove` | `0..3` | yes, `e8b06939a` | no | **LEFT** |
| `…/01082262.img/swingT3/0/rGlove` | `0..1` | yes, `e8b06939a` | no | **LEFT** |

**Why left:** these are `lGlove`/`rGlove` **canvas and uol animation frames**. Every hazard the
refusal warns about — "the field lands on whichever entry sits at that index HERE", "the row EDITS an
existing record" — is a hazard about *rendering*, and **the server does not render**. Nothing in
`src/main/java` reads `Character.wz/Glove/*/swing*` or `stab*`. Removing them buys nothing, costs a
diff against art the client will not have, and the class is now documented, which is what the row was
worth. **This is a knowing, recorded divergence, not an oversight.**

**It is 8, not 6.** Ticket **03i** widened the gate to any consecutive run (`ArrayRange`) and has
**already deny-listed** two more rows on this same item, whose arrays are **not zero-based** in the
live tree (`COLLISION-DENY.txt:110-111`):

| row | live array before 04 | in XML now | disposition |
|---|---|---|---|
| `…/01082262.img/swingT2/2/rGlove` | `{1,2}` | yes, `e8b06939a` | **LEFT** |
| `…/01082262.img/swingO3/0` | `{1}` | yes, `e8b06939a` — whole slot `0` | **LEFT** |

Note the second is `swingO3/0`, the **whole slot**, as the composed list writes it
(`merge-lists/composed/Character.paths.txt:96`) — not `swingO3/0/rGlove`. Same disposition and same
reason: art, inert server-side. Because these two are now on the **deny-list** rather than only
refused by the gate, the client will never carry them, so the XML holding them is a **permanent**
divergence — knowingly, and only in art the server never reads.

Mechanically: 04 added exactly **8** top-level rows to this image (38 node paths, 0 removed), and
those 8 are precisely the 6 refused in the 03h run plus these 2. There is no ninth.

The other six `Character.wz` refusals are **server-readable but not divergent** — which is the safe
half of the matrix, and a different reason from the Glove six:

| row | reason refused | in XML | readable | disposition |
|---|---|---|---|---|
| `Character.wz/Accessory/01142153.img` | already exists in target | yes — last written by `a17c23369`, **not by any v84 ticket** | **yes**, `getEquipStats` | none needed |
| `Character.wz/Accessory/01142154.img` | already exists in target | yes — `a17c23369` | **yes**, `getEquipStats` | none needed |
| `Character.wz/Dragon/01942002.img/info/level` | already exists in target | yes — predates 04 (`3a8377c28`) | **yes**, equip levelling | none needed |
| `Character.wz/Dragon/01952002.img/info/level` | already exists in target | yes — predates 04 | **yes** | none needed |
| `Character.wz/Dragon/01962002.img/info/level` | already exists in target | yes — predates 04 | **yes** | none needed |
| `Character.wz/Dragon/01972002.img/info/level` | already exists in target | yes — predates 04 | **yes** | none needed |

04 *did* touch the four Dragon images — but it added `info/equipTradeBlock`, never `info/level`. The
`level` subtrees are the tree's own content, which is why the binary refused to overwrite them. Both
sides keep what they had; that is agreement, not divergence, and **nothing needs doing even though the
server reads these every time it prices an equip.**

One caution, because 04's note is easy to misread: its "adopting them switches on a dormant
equip-levelling path" describes the consequence of **merging v84's `info/level`**, which neither side
did. It is not a claim that the node is inert — `ItemInformationProvider:1908-1911` reads `info/level`
today. The row is safe because it is unchanged, full stop.

### `String.wz` — 9 refused, 0 divergent, all 9 server-readable

All nine are `already exists in target`, all present in `wz/String.wz/Eqp.img.xml`, all read by
`ItemInformationProvider` for item names (`:152`, `:176`), and **none divergent**:

| row | in XML | readable | divergent | disposition |
|---|---|---|---|---|
| `String.wz/Eqp.img/Eqp/Hair/31660` | yes | yes | no | none needed |
| `String.wz/Eqp.img/Eqp/Hair/31661` | yes | yes | no | none needed |
| `String.wz/Eqp.img/Eqp/Hair/31662` | yes | yes | no | none needed |
| `String.wz/Eqp.img/Eqp/Hair/31663` | yes | yes | no | none needed |
| `String.wz/Eqp.img/Eqp/Hair/31664` | yes | yes | no | none needed |
| `String.wz/Eqp.img/Eqp/Hair/31665` | yes | yes | no | none needed |
| `String.wz/Eqp.img/Eqp/Hair/31666` | yes | yes | no | none needed |
| `String.wz/Eqp.img/Eqp/Hair/31667` | yes | yes | no | none needed |
| `String.wz/Eqp.img/Eqp/Hair/33101` | yes | yes | no | none needed |

**Neither 04 (`e8b06939a`) nor 05 (`4e8c49594`) touched a single one of the nine ids**, though both
wrote to that file — checked per id, not per file. 04's note records why the binary refused them: the
live and v84 strings are *identical*, so the row is a no-op on either side. Nothing to do.

### The eight files with an empty `conflicts.txt`

`Map`, `Mob`, `Morph`, `Npc`, `Quest`, `Reactor`, `Skill`, `Sound` refused **0** rows in the 03h run.
Per `WZ-MERGE-PROCEDURE.md` §4.5 that is **not** evidence of safety and is not claimed as such here —
it only means this document has no rows to inventory for them. The hazards §4.5 lists are rows the
gate is silent on by construction; they are the deny-list's job, not this reconciliation's.

## Re-deriving the two constants, and the limit of the test

`V84XmlReconcileTest` hard-codes `19864` and `18363`. **Those numbers came from the composed binary,
not from the XML they assert** — but the test cannot re-derive them, because a JUnit test will not
parse a 19 MB `.wz`. So the test pins the XML against a figure measured out-of-band, and **if the
client's table ever changes, the test will not notice.** That is the honest limit of it. To re-measure:

```
WzMerge dump <stage>\03h\Item.wz Consume/0202.img/02022503/reward 2
WzMerge dump <stage>\03h\Item.wz Consume/0202.img/02022514/reward 2
```

then sum `prob` over the children. That is how `19864` / `18363` were obtained, and the comparison run
for this ticket checked more than the totals: **slot-name sets equal and every slot's full content
equal**, both boxes, XML against binary. Re-run it after any future `Item.wz` merge; the totals alone
would not catch two compensating changes.

## Verification performed

- Full suite **green at 1,996** — 1,994 before this ticket, plus the 2 tests added here.
- All 18 live client `.wz` hash-match `_backup\client-v83-EzorsiaV2-2026-08-15\` **before and after**;
  nothing under `D:\games\MapleStory\` was written.
- Both edited `.img.xml` parse through the server's own `XMLWZFile` reader, not just a generic XML
  parser — that is what the two new tests exercise.
- There is no server-startup smoke test in this repo, so "the server starts" rests on the suite, as it
  has for every ticket in this series.

## What this document is not

It reconciles **the rows the composed binary run refuses**. It does **not** verify that every row the
run *accepted* also reached the XML — that is the composed run's own `--live` hash check and the
per-ticket path lists. Nor does it re-litigate the deny-list. Refusals only.

## Standing rule

**Any gate added after a ticket has already spliced into `wz/` leaves this same gap.** The binary
side restarts from `pre\`; the XML side does not. When a gate lands, re-run this check rather than
assuming the composed README's "already composed" covers it.
