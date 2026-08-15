# Ticket 08 — the six route rows, and the three that were refused

Every map this ticket ships is a *new* `.img`, so 89 of `Map.paths.txt`'s 95 rows are additions the
gate cannot get wrong. The remaining six write **into a positional array on a map the live client
already has**, which is the hazard class 03c named (`String.wz/MonsterBook.img/*/reward`) and which
`conflicts.txt` is structurally blind to: the row does not collide, it lands at an index whose
meaning differs between the two trees.

`add-list/Map.txt` offers nine such rows for the areas in scope. **Six are safe and were merged;
three are not and were refused.** The difference was measured, not assumed — for every one, the live
child count and the v84 index were dumped and compared.

## Merged — verified pure appends

| row | live children | v84 index | what it is |
|---|---:|---:|---|
| `Map/Map2/200080600.img/1/obj/25` | 25 (`0`–`24`) | 25 | the secret-door art |
| `Map/Map2/200080600.img/1/obj/26` | 25 | 26 | the secret-door art |
| `Map/Map2/200080600.img/portal/6` | 6 (`0`–`5`) | 6 | `pn=in00 pt=8 script=enterBlackRoom` → 200080601 |
| `Map/Map2/251010403.img/4/obj/33` | 33 (`0`–`32`) | 33 | the vault-door art |
| `Map/Map2/251010403.img/portal/4` | 4 (`0`–`3`) | 4 | `pn=in00 pt=8 script=enterPottery` → 925110000 |
| `Map/Map1/106010102.img/portal/8` | 8 (`0`–`7`) | 8 | `pn=scr00 pt=8 script=evanDollGR` → 910600010 |

An append at index N onto an array of exactly N children cannot displace anything. v84 *did*
reorder the earlier slots of two of these arrays (`200080600` swapped `top00`/`under00`;
`106010102` moved `out00` to the front and changed `in03`–`in06` from `pt=1` to `pt=2`) — those are
`modified-list` differences the additive gate correctly drops, and they are harmless because a
portal is looked up **by name**, not by index. The post-merge content digest confirms the outcome:
only the `portal` (and `1`/`4`) children of these three images differ from pre-merge, and
`V84MiscAreasNodeTest.theThreeMergedRoutePortalsWereAppendedWithoutDisturbingTheV83Ones` asserts
every pre-existing portal still has its original target.

## Refused — the index means a different portal in each tree

| row | live `portal[N]` | v84 `portal[N]` | what merging it would do |
|---|---|---|---|
| `Map/Map1/106010101.img/portal/5/{horizontalImpact,script}` | `out00` → 106010100 | `in00` `pt=7 script=evanGolemDoor` | attach `evanGolemDoor` to the **exit portal back to Golem's Temple 1** |
| `Map/Map2/220000300.img/portal/15` | *(does not exist; live has 15 portals, `0`–`14`)* | `in06` → 220000307 | append a **duplicate `in06`**. v84's real addition is `scr00` at index **4**, inserted, which shifted all eleven `in0x` down one — that node is on no add-list row at any reachable index |
| `Map/Map2/220011000.img/portal/4/{horizontalImpact,script}` | `in00` `pt=2` → 220011001 | `in00` `pt=7 script=enterBlackBC` | attach `enterBlackBC` to the **working portal into Ludibrium Toy Factory**, whose script does not exist, i.e. break it |

`V84MiscAreasNodeTest.theThreeUnsafeRoutePortalRowsWereNotMerged` asserts all three refusals, so a
later ticket cannot merge them by accident and read it as an improvement.

**Cost of refusing them**, stated rather than buried: `910600000` Golem's Temple Entrance,
`922030000`/`922030001` Frog House and `922030010`–`922030022` Sky Terrace / Safe have **no route
in**. They are staged-but-unreachable. Repairing that is not a merge — it needs a hand-authored
portal node in both trees, exactly the shape ticket 06's travel route needed and for the same
reason: the merge tool only ever copies from a source `.wz`, and here the source node exists but
only at an index that means something else.

## General rule this adds to the procedure

`WzMerge deps` resolves asset references; the ordering rule covers manifest parent/child. **Neither
sees positional-array semantics.** Any add-list row of the form `<img>/<array>/<n>` onto an image
the target already has must be checked by dumping the target's child count for `<array>` and
comparing it with `n`. Equal ⇒ append, safe. Less ⇒ the merge refuses anyway (parent gap). Greater
⇒ **the row is naming an existing slot's neighbour and the arrays have diverged; do not merge it.**
