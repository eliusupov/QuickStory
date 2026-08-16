# The positional-array gate fires — 6 pass, 12 refuse

Ticket **03g**. The rule and what it costs: `WZ-MERGE-PROCEDURE.md` §4.4. The data it was measured
against: `merge-lists/08/ROUTE-ROWS.md`.

`add-list/Map.txt` offers **18 rows that write into a `portal`/`obj` array on a map the live client
already has**. Ticket 08 dumped every one of those arrays index by index and split them by hand:
**6 pure appends, 12 not**. That split is the oracle. The gate has to reproduce it from structure
alone, with no knowledge of portals, without being told which map is which.

The eighteen are re-derived, not copied from 08's table (which says itself that an earlier draft
listed nine rows and gave a wrong reason for one):

```
grep -n "106010101.img/portal\|106010102.img/portal\|200080600.img/\|220000300.img/portal\|220011000.img/portal\|251010403.img/" docs\wz-baseline\add-list\Map.txt
```

→ `Map.txt:241-242, 293-297, 371-373, 392-397, 492-493`. Eighteen rows, matching 08's enumeration
exactly.

## Reproducing

The twelve are on `COLLISION-DENY.txt` now, and **deny is checked before the gate**, so a run with
the committed deny-list proves nothing about the gate — it reports `DENIED` twelve times. Use a
deny-list that does not name them. Two throwaway files:

`positional.paths.txt` — the eighteen rows, 08's six safe ones first:

```
Map.wz/Map/Map2/200080600.img/1/obj/25
Map.wz/Map/Map2/200080600.img/1/obj/26
Map.wz/Map/Map2/200080600.img/portal/6
Map.wz/Map/Map2/251010403.img/4/obj/33
Map.wz/Map/Map2/251010403.img/portal/4
Map.wz/Map/Map1/106010102.img/portal/8
Map.wz/Map/Map1/106010101.img/portal/5/horizontalImpact
Map.wz/Map/Map1/106010101.img/portal/5/script
Map.wz/Map/Map1/106010102.img/portal/4/horizontalImpact
Map.wz/Map/Map1/106010102.img/portal/5/horizontalImpact
Map.wz/Map/Map1/106010102.img/portal/6/horizontalImpact
Map.wz/Map/Map1/106010102.img/portal/7/horizontalImpact
Map.wz/Map/Map2/220000300.img/portal/4/horizontalImpact
Map.wz/Map/Map2/220000300.img/portal/4/script
Map.wz/Map/Map2/220000300.img/portal/6/image
Map.wz/Map/Map2/220000300.img/portal/15
Map.wz/Map/Map2/220011000.img/portal/4/horizontalImpact
Map.wz/Map/Map2/220011000.img/portal/4/script
```

`positional.deny.txt` — one unrelated row, because `--deny` is mandatory and a 0-row list is exit 2:

```
Npc.wz/9000021.img	# irrelevant to Map.wz; present only to satisfy --deny
```

Dry run (`-` in the `<outWz>` slot), so the live client is read and nothing is written:

```
WzMerge merge <v84>\Map.wz D:\games\MapleStory\Map.wz - positional.paths.txt positional.conflicts.txt --deny positional.deny.txt
```

## Result — `added 6 (forced 0), refused 12`, exit 3

Re-run at ticket **03i** against the widened rule (see below); the split is unchanged.

```
18 paths requested
  ADD   Map.wz/Map/Map2/200080600.img/1/obj/25
  ADD   Map.wz/Map/Map2/200080600.img/1/obj/26
  ADD   Map.wz/Map/Map2/200080600.img/portal/6
  ADD   Map.wz/Map/Map2/251010403.img/4/obj/33
  ADD   Map.wz/Map/Map2/251010403.img/portal/4
  ADD   Map.wz/Map/Map1/106010102.img/portal/8
  SKIP  Map.wz/Map/Map1/106010101.img/portal/5/horizontalImpact  (… 0..5 … INTO slot 5 …)
  SKIP  Map.wz/Map/Map1/106010101.img/portal/5/script             (… INTO slot 5 …)
  SKIP  Map.wz/Map/Map1/106010102.img/portal/4/horizontalImpact   (… 0..7 … INTO slot 4 …)
  SKIP  Map.wz/Map/Map1/106010102.img/portal/5/horizontalImpact   (… INTO slot 5 …)
  SKIP  Map.wz/Map/Map1/106010102.img/portal/6/horizontalImpact   (… INTO slot 6 …)
  SKIP  Map.wz/Map/Map1/106010102.img/portal/7/horizontalImpact   (… INTO slot 7 …)
  SKIP  Map.wz/Map/Map2/220000300.img/portal/4/horizontalImpact   (… 0..14 … INTO slot 4 …)
  SKIP  Map.wz/Map/Map2/220000300.img/portal/4/script             (… INTO slot 4 …)
  SKIP  Map.wz/Map/Map2/220000300.img/portal/6/image              (… INTO slot 6 …)
  SKIP  Map.wz/Map/Map2/220000300.img/portal/15  (POSITIONAL ARRAY: … Index 15 IS a pure append onto 15 entries, but the source entry is content-identical to one the array already holds — the two arrays have diverged (the source inserted earlier and every later slot is shifted), so this appends a DUPLICATE rather than new content.)
  SKIP  Map.wz/Map/Map2/220011000.img/portal/4/horizontalImpact   (… 0..4 … INTO slot 4 …)
  SKIP  Map.wz/Map/Map2/220011000.img/portal/4/script             (… INTO slot 4 …)
dry run: not saving
added 6 (forced 0), refused 12
```

The interior-write refusal in full, since it is the one an operator will argue with. **This text
changed at 03h and again at 03i; what follows is what the tool prints today**, copied out of
`positional.conflicts.txt`, not paraphrased:

> POSITIONAL ARRAY: `'Map.wz/Map/Map1/106010101.img/portal'` holds exactly the consecutive
> integers 0..5, so its children are SLOTS OF A POSITIONAL ARRAY, not identities. This row writes
> `'script'` INTO slot 5, which already EXISTS. TWO hazards, and this refusal covers both —
> checking one and finding it harmless does not clear the row: (a) the source's slot 5 need not be
> the same ENTRY as this tree's, so the field lands on whichever entry sits at that index HERE (v84
> reindexes arrays, and the two trees need not even hold the same NUMBER of entries); (b) even when
> it is the same entry, the row EDITS a record the target already has by adding a field to it, and
> the additive gate cannot see an edit that adds. What makes (b) different from adding a field to
> any other existing node — which this same run does permit — is that the record here has NO NAME,
> only a position, so there is nothing to check the edit against: you cannot tell WHICH record you
> are editing without dumping it. Do that: dump BOTH slots in full, decide what the added field does
> to the record that is already there, and either re-author the row against THIS tree or deny it.
> (Worked example of (b) landing with the indices lining up perfectly: ticket 09's
> `Quest.wz/Check.img/<id>/0/lvmax`, which caps 108 working quests at Lv.40.)

Clause (b)'s second sentence is there because the tool would otherwise contradict itself: the same
composed run **permits** `String.wz/Npc.img/1063018/d0`, which is also "a field added to a node the
target already has". The difference is not the shape, it is that `1063018` is a name and `5` is a
position — and that is the sentence, so an operator who spots the apparent contradiction finds the
answer in the message instead of overriding.

Exactly 08's split, and each refusal names the array, its length and the slot — not
`already exists in target`, which is the whole point: an operator reading `conflicts.txt` can tell
"this index would land on a different entry" from "the target already had it".

## Where the rule as first stated was wrong, and 08 was right

03c's rule is *"the tell is a parent whose children are consecutive integers; refuse unless the
index equals the child count."* Eleven of the twelve fall out of it. **`220000300.img/portal/15`
does not** — the live client has fifteen portals (`0`–`14`), so index 15 **is** a pure append by
that rule, and a merge obeying it exactly would have written the row.

08's manual read refused it, and 08 is right. Measured:

```
WzMerge dump D:\games\MapleStory\Map.wz Map/Map2/220000300.img/portal/14
  pn = in06   pt = 2   x = 2008   y = 103   tm = 220000307   tn = out00
WzMerge dump <v84>\Map.wz               Map/Map2/220000300.img/portal/15
  pn = in06   pt = 2   x = 2008   y = 103   tm = 220000307   tn = out00
```

Byte-identical. v84 has sixteen portals only because it **inserted** `scr00` at index 4; every slot
after it is the same entry one place further along, and the last one falls off the end and looks
like new material. An index check cannot see that. So the implemented rule carries a fourth clause
the written one did not: **a pure append whose content already exists in the array is refused.**
That is what fires here, and the message says so.

The rule is therefore stronger than 03c's, not a restatement of it, and it caught two rows in the
composed install that nobody had enumerated — `Item.wz/Consume/0202.img/020225{03,14}/reward/43`,
where v84's slot 43 is content-identical to the live client's slot 16. Same mechanism, different
file. See `composed/README.md`.

---

# Ticket 03i — the rule missed every array that does not start at 0

Ticket 16's regression pass found two rows on `Character.wz/Glove/01082262.img` that landed while
**six siblings of identical shape on the same item** were refused. The cause was one clause:
`ArrayCount` demanded the children be *exactly* `0..c-1`, and these two containers are not
zero-based, so the gate did not see an array at all and never looked.

```
swingT2  live {1: rGlove 5x6, 2: lGlove 12x8}    v84 {1: rGlove 7x5, 2: rGlove 12x9}
swingO3  live {1: rGlove 6x5}                    v84 {0: lGlove 7x5 + rGlove 47x9, 1: rGlove 7x5}
```

`swingT2/2/rGlove` is *literally* what the six refusals describe. Its sibling `swingP2/2/rGlove`
**was** refused, and the only difference is that `swingP2`'s live children are `0..2`.

## The corrected rule

`ArrayCount` → **`ArrayRange`**, returning `(Min, Count)` instead of a count:

> A container is a positional array iff **every** child name is a non-negative integer and those
> integers form **one consecutive run**. The run does **not** have to start at 0.

Two clauses did *not* change, and both are what keep it from over-refusing:

- **every** child must be an integer — a map `.img` has layers `0`–`7` *alongside* `info`, `portal`,
  `foothold` and `life`, so it is still not an array, and the six appends ticket 08 correctly
  merged still merge;
- the run must be **consecutive** — 03h declined to drop this and 03i declines again. Allowing
  holes would make every id container in `String.wz` an array (`Consume.img`'s children are 2,290
  integer item ids with enormous gaps) and refuse 501 legitimate name rows.

Three index checks follow from `Min`: an index inside `Min..Max` is **occupied**, an index **below
`Min` is a prepend** (new, and the refusal says so — a source numbering the container from a
different origin is not aligned with the target at all), and the gap scan runs from `Max + 1`.

### The blind spot this leaves, stated rather than papered over

A genuinely sparse array reads as "not an array". Two real examples in this tree:
`Glove/01082262.img/swingOF` = `{0,3}`, and `Quest.wz/Check.img/4940` = `{0,1,4961}` — the second
found by the code review, and the reason 03h's "the gate refuses all 123 `Check.img/<id>/<step>`
rows structurally" is a statement about *those* rows and not a general guarantee. Nothing structural
separates `{0,1,4961}` from an id table, and **no row in any list targets either container today**.
`COLLISION-DENY.txt` is what stands in front of this class, exactly as it does for
`Quest.wz/Exclusive.img`.

## Proof — every indexed parent in the composed install, both directions

Every composed row that writes under an integer segment was enumerated (**68 distinct parents**,
a superset of the 34 small-integer ones ticket 16 classified) and each parent's children were read
out of the `pre\` snapshot — i.e. out of the live client — and classified:

| class | parents | what the rule does |
|---|---:|---|
| `ARRAY-0` — consecutive run from 0 | 29 | array under both the old rule and the new one |
| `ARRAY-N` — consecutive run, base ≠ 0 | **2** | **array only under 03i's rule** |
| `RUN-WITH-HOLES` — all integers, gaps | 30 | not an array under either (`String.wz`/`Quest.wz` id tables) |
| `NOT-ALL-INT` — a named child present, or empty | 7 | not an array under either |

The two `ARRAY-N` parents are `swingT2` and `swingO3`. **Nothing else in the composed install
changes class**, which is the claim that matters: the widening cannot over-refuse a row it was not
already looking at.

Run against the live client, all eleven composed lists, before/after the fix:

| file | 03h added / refused | 03i added / refused |
|---|---|---|
| `Character` | 242 / 12 | **240 / 14** |
| every other file | unchanged | **unchanged** |

`Compare-Object` on the two `Character.conflicts.txt` refusal sets returns exactly two rows —
`swingT2/2/rGlove` and `swingO3/0` — and nothing else, in either direction. **No new refusal beyond
the two.** 08's eighteen-row oracle at the top of this file was re-run against the same binary and
still splits `added 6, refused 12`.

The two rows are also on `COLLISION-DENY.txt` (Hazard 2d) — the gate records a shape, a deny row
records the decision, and the deny-list is shared with `WzMerge xml`, whose text scan cannot make
the content-identical comparison.

## Re-running it

The check is the merge itself. From the repo root, with a `pre\` snapshot that hash-matches the
live client:

```
WzMerge merge <v84>\Character.wz <stage>\pre\Character.wz - `
  docs\wz-baseline\merge-lists\composed\Character.paths.txt <stage>\Character.dry.conflicts.txt `
  --deny docs\wz-baseline\merge-lists\COLLISION-DENY.txt
```

`added 240 (forced 0), refused 14`, exit 3. Any other pair of numbers means the rule moved.

## What it caught elsewhere — bigger than the glove

The widened rule was then run over the **395 `add-list/Map.txt` rows that write into maps the live
client already has** and that nobody had ever triaged (`COLLISION-DENY.txt` Hazard 5). It refuses
**216** of them, and **14 of those 216 are refused only because of this ticket** — every one a
`foothold/<n>` container, which v84 numbers from **1**, not 0. Footholds are collision geometry and
the server reads them (`MapFactory.java:197`). None of those rows is on a list today; ticket 13
works in `Map.wz` next.
