# The composed install — one merge per `.wz`, all content tickets at once

Built by **ticket 03f**. Tickets 04, 05, 06 and 07 each staged their own `.wz` **from the same
pristine v83 base**, so their outputs do not compose: installing two of them loses one set
(05's ticket says so at its "Human steps → Step 0", 06's at its step 1). The fix is to merge
**once per file from the ticket path lists**, which is what this directory is.

`compose.ps1` regenerates every `*.paths.txt` here from `..\{04,05,06,07,03f}\`. Those are the
source of truth; no `*.paths.txt` here is hand-edited. **`FORCE.txt` and this README are
hand-maintained** — `compose.ps1` does not touch either, so if you add a ticket, update `FORCE.txt`
yourself (see the ticket-08 note below).

## What it is

| file | rows | from |
|---|---:|---|
| `Character.paths.txt` | 254 | 04 (246) + 05 (8) |
| `Item.paths.txt` | 391 | 04 |
| `String.paths.txt` | **471** | 04 (394) + 05 (7) + 06 (35) + 07 (7) + 03f (28) |
| `Morph.paths.txt` | 25 | 05 |
| `Skill.paths.txt` | 27 | 05 |
| `Map.paths.txt` | 37 | 06 (34) + 07 (3) |
| `Mob.paths.txt` | 21 | 06 (17) + 07 (4) |
| `Npc.paths.txt` | 6 | 06 |
| `Reactor.paths.txt` | 2 | 06 |
| `Sound.paths.txt` | 1 | 06 |
| `FORCE.txt` | 38 roots | `COLLISION-FORCE.txt` (37) + 03f's `Npc.img/9201144` |

**Composability was checked, not assumed.** Across all ten files every row is unique and no row
is an ancestor of another, so nothing can be written twice or shadow another ticket's row. The
one order-sensitive mechanism is the force path — the `existing?.Remove()` branches of the
`switch (parent, srcObj)` in `Program.cs`'s `Merge()`, at `:955-977` as of this commit: a force
row removes and re-adds, and `Eqp/Dragon` is a **container-level** force root — but 04
has zero rows beneath it, so 04→05 and 05→04 give identical trees. Ordering inside each ticket
block **is** load-bearing for `Map.wz` (06's 12 dependency rows must precede the 22 map images)
and is preserved verbatim.

**Ticket 08 was still in flight when this was built, and is NOT composed in here.** It owns
`Map.wz`, `Mob.wz`, `Npc.wz`, `Reactor.wz`, `Sound.wz` and the `String.wz/{Map,Mob,Npc}.img` rows.
Its lists appeared in `..\08\` uncommitted and still changing while 03f's merge was running, so
folding them in would have produced an untested list; they were deliberately left out.

To compose 08 in: add `"08"` to the relevant entries of `compose.ps1`'s `$files` table and re-run.
Every file here ends with a `# ==== ticket 08 appends here ====` marker and nothing above it moves.
**Two things to get right when you do:**

- **08 has its own `String.force.txt` (3 forced rows).** `FORCE.txt` here is 37 + 03f's 1 = 38 and
  does **not** include them. The composed force list becomes 41 roots — regenerate it, do not just
  reuse this one, or 08's three forced names silently revert to their live values.
- **08's XML splices into `wz/` were already in the tree when 03f ran**, so the server side is
  ahead of this directory for `Map.img`/`Mob.img`/`Npc.img`. Re-read the counts below rather than
  assuming they still hold.

## Running it

Procedure: `..\..\..\work-plan\WZ-MERGE-PROCEDURE.md` §5, one file at a time, from the repo root.

```
WzMerge merge <v84>\<Name>.wz <stage>\<T>\pre\<Name>.wz <stage>\<T>\<Name>.wz `
  docs\wz-baseline\merge-lists\composed\<Name>.paths.txt <stage>\<T>\<Name>.conflicts.txt `
  --deny docs\wz-baseline\merge-lists\COLLISION-DENY.txt `
  --live D:\games\MapleStory\<Name>.wz
```

`--force docs\wz-baseline\merge-lists\composed\FORCE.txt` on **`String.wz` only** — all 38 force
roots are `String.wz` paths. Add it to the `xml` run for `String.wz` too, or the client and the
server disagree about the same ids.

## ⚠ Exit 3 on `Character` and `String` is the CORRECT result, not a failure

A scripted install that aborts on non-zero will stop on these two. It should not.

| file | exit | added | forced | refused |
|---|---:|---:|---:|---:|
| `Morph` | 0 | 25 | 0 | 0 |
| `Skill` | 0 | 27 | 0 | 0 |
| `Sound` | 0 | 1 | 0 | 0 |
| `Reactor` | 0 | 2 | 0 | 0 |
| `Npc` | 0 | 6 | 0 | 0 |
| **`String`** | **3** | 462 | 38 | **9** |
| `Item` | 0 | 391 | 0 | 0 |
| **`Character`** | **3** | 248 | 0 | **6** |
| `Mob` | 0 | 21 | 0 | 0 |
| `Map` | 0 | 37 | 0 | 0 |

The 15 refusals are **04's deliberate decisions**, each triaged before 04 shipped:

- **`String.wz`, 9 rows** — `Eqp/Hair/31660`–`31667` and `33101`. Ezorsia already names these and
  v84's string is byte-identical, so the refusal is a no-op. `V84CosmeticNodeTest
  .ezorsiaHairNamesWereNotOverwritten` pins them.
- **`Character.wz`, 2 rows** — `Accessory/01142153.img`, `01142154.img`. Cosmic turned both into
  level-up medals, so the live node is a strict **superset** of v84's; taking v84's would delete
  Cosmic content. `V84CosmeticNodeTest.cosmicLevelUpMedalsSurvivedTheRefusal` pins it.
- **`Character.wz`, 4 rows** — `Dragon/019{4,5,6,7}2002.img/info/level`. The one genuinely open
  question, recorded as an owner call in `COLLISION-FORCE.txt` (commented out, with the reason)
  and in ticket 04. Not a fault; an undecided decision.

`Sound.wz` exits **0** only on a `WzMerge` built after ticket 03f. Before that, the post-write
verifier counted `Sound.wz/BgmGL.img` — which MapleLib cannot parse in **any** of the three trees
— as damage, so every `Sound.wz` merge failed verification, stayed `.partial` and exited 4 no
matter how correct the data was. 06 hit exactly that and correctly discarded its output. The
verifier now discounts images that were **already** unparseable in the merge target, per image,
and only in that direction: an image that parses in the target and fails in the output still
fails (proven both ways in ticket 03f's report).

## Verified end to end, 2026-08-16

Staging `D:\games\MapleStory\Server\wz-merge\03f\`. All 18 live `.wz` SHA-256-matched
`_backup\client-v83-EzorsiaV2-2026-08-15\` before and after; every `pre\` snapshot hash-matched
its live file (`--live` on every run). All ten outputs passed the tool's own post-write
verification — path re-resolution, full-file parse, and per-image content digest — and were
promoted. Gate re-fire on the composed `String.wz` output: `added 0, refused 471`, **exit 5**.

§6.1 content digest, pre vs post, on the two images 03f itself touched:

- `String.wz/Skill.img` — **29** lines differ out of 613 children: the 27 new ids plus the two
  `TOTAL` lines. Nothing pre-existing moved.
- `String.wz/Npc.img` — 7,076 → 7,081 children, **9** lines differ: 06's 5 additions, the old and
  new `9201144` (the forced replacement, in and out), and the two `TOTAL` lines. The force stayed
  in its lane.

Output SHA-256:

```
Character  0C3DEFD3A73B4C36BABECDF0A9B47CE8C5F5154CDE3B8FF7B40844F821939EC0   211,840,293
Item       5F407FABFF677FB321996278AD104CC7D05B7F2A165A352D85B9F3A628850EC6    19,086,553
Map        BD285D383D3A0343FA6BC19FF76308B37D3706140CF547F2635E8FB325B08474   640,993,100
Mob        A7648418E3F27249CF96877C483CBCF93DAA890269313EB3CC59A16B0B590ACC   495,872,051
Morph      E8E3D94E19B6CC8B3ADA097152216423547B9A63ACB59569AE0C76E7BBE4852D     6,322,806
Npc        A2109FCB8AB53C34D21C2F781BE62C185AF6AD872ECCF7626C403C4EBF00C098    53,917,831
Reactor    ABBA71C6B881F18AF520C5929BC58F711CAAFD9E02388E4E51C3FF97F51B99AC    54,827,370
Skill      69AE95DF8380EC2268665A1205CD35F42B6DCBEBC85E6049C12657518BF95B49    80,213,925
Sound      6FF56D43138DDDADAB0FEE24E1DF718EA913134B827E1FBD2CB3CB9A0F41B678   365,388,238
String     974CFF98CA5D427F11C5F480A54B87151DC8122E5D9A4CA4FB652BCA6576A369     3,609,952
```

`Morph`, `Skill`, `Npc` and `Reactor` are **byte-identical to the single ticket that owns them**
(05's and 06's recorded hashes) — the composition is deterministic and the four single-owner
files re-derive exactly.

**These outputs are superseded the moment ticket 08 lands** — 08 merges `Map`, `Mob`, `Npc`,
`Reactor` and `Sound` from the same base. Re-run this composition after 08 rather than installing
these; the run is cheap (minutes) and the point of it is that it is repeatable.

## Server XML

The XML tree in `wz/` is **already composed** — every ticket spliced its own rows in place, so
there is no XML equivalent of this directory to run. What ticket 03f applied on top:
`WzMerge xml <v84>\String.wz wz ..\03f\String.paths.txt <conflicts> --deny … --force .\FORCE.txt`
→ `added 28 (forced 1), refused 0`, exit 0.
