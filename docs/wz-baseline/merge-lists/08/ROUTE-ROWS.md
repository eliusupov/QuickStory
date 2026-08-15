# Ticket 08 — the six route rows, and the three that were refused

Every map this ticket ships is a *new* `.img`, so 89 of `Map.paths.txt`'s 95 rows are additions the
gate cannot get wrong. The remaining six write **into a positional array on a map the live client
already has**, which is the hazard class 03c named (`String.wz/MonsterBook.img/*/reward`) and which
`conflicts.txt` is structurally blind to: the row does not collide, it lands at an index whose
meaning differs between the two trees.

`add-list/Map.txt` offers **eighteen** such rows onto the five live maps this ticket's areas hang
off. **Six are safe and were merged; twelve are not and were refused.** The difference was measured,
not assumed — for every one, the live array was dumped and compared with v84's index by index.

*(An earlier draft of this file listed nine rows and gave a wrong reason for one of them. The
enumeration below is the corrected one: `grep -n "<mapid>.img/portal" docs/wz-baseline/add-list/Map.txt`
for each of the five maps is what produces it, and that is the check to re-run rather than trust
this table.)*

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

Twelve rows, across four maps. In every case v84 **reordered or inserted into** the array, so the
index that carries the change in v84 carries something else in the live client.

| row(s) | live `portal[N]` | v84 `portal[N]` | what merging it would do |
|---|---|---|---|
| `Map/Map1/106010101.img/portal/5/{horizontalImpact,script}` | `out00` → 106010100 | `in00` `pt=7 script=evanGolemDoor` | attach `evanGolemDoor` to the **exit portal back to Golem's Temple 1** |
| `Map/Map1/106010102.img/portal/{4,5,6,7}/horizontalImpact` | `in04`, `in05`, `in06`, `out00` | `in03`, `in04`, `in05`, `in06` | v84 moved `out00` to index 3, shifting `in03`–`in06` down one. Every row lands one portal off — **including on the very array this ticket appends to at index 8** |
| `Map/Map2/220000300.img/portal/4/{horizontalImpact,script}` | `h000` `pt=10` (a hidden town point) | `scr00` `pt=7 script=enterBlackFrog` | attach `enterBlackFrog` to a **hidden town portal**. This IS the row that carries v84's Frog House entrance — it is simply unusable at this index |
| `Map/Map2/220000300.img/portal/6/image` | `west00` → 220000400 | `h001` | write an `image` node onto the **wrong portal** |
| `Map/Map2/220000300.img/portal/15` | *(does not exist; live has 15 portals, `0`–`14`)* | `in06` → 220000307 | append a **duplicate `in06`** — v84 has 16 portals only because it inserted `scr00` at index 4 |
| `Map/Map2/220011000.img/portal/4/{horizontalImpact,script}` | `in00` `pt=2` → 220011001 | `in00` `pt=7 script=enterBlackBC` | attach `enterBlackBC` to the **working portal into Ludibrium Toy Factory**, whose script does not exist, i.e. break it |

`V84MiscAreasNodeTest.theUnsafeRoutePortalRowsWereNotMerged` asserts these refusals, so a later
ticket cannot merge them by accident and read it as an improvement.

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

**And a row of the form `<img>/<array>/<n>/<field>` — where `n` already exists in the target — is
the more dangerous shape**, because it passes the parent check and the additive gate happily writes
a new leaf onto the wrong sibling. Ten of the twelve refusals above are that shape. The only check
that catches them is comparing the two arrays element by element by `pn`, not by index. Do that
before merging any `portal/<n>/…` or `obj/<n>/…` row onto a pre-existing image.
