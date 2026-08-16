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

```
18 paths requested
  ADD   Map.wz/Map/Map2/200080600.img/1/obj/25
  ADD   Map.wz/Map/Map2/200080600.img/1/obj/26
  ADD   Map.wz/Map/Map2/200080600.img/portal/6
  ADD   Map.wz/Map/Map2/251010403.img/4/obj/33
  ADD   Map.wz/Map/Map2/251010403.img/portal/4
  ADD   Map.wz/Map/Map1/106010102.img/portal/8
  SKIP  Map.wz/Map/Map1/106010101.img/portal/5/horizontalImpact  (POSITIONAL ARRAY: 'Map.wz/Map/Map1/106010101.img/portal' holds exactly the consecutive integers 0..5, so its children are SLOTS OF A POSITIONAL ARRAY, not identities. This row writes 'horizontalImpact' INTO slot 5, which already exists — and v84's slot 5 need not be the same entry as the target's. …)
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
