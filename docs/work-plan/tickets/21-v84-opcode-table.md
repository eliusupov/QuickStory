# 21 — a v84 opcode table Cosmic can load

**What to build:** `opcodes/sendops-84.properties` and `opcodes/recvops-84.properties`, so that
starting the server with `-Dopcode-version=84` makes it speak v84 instead of v83. Ticket 32 already
built the seam; this ticket fills it.

**Blocked by:** None — can start immediately. **Do not change `ServerConstants.VERSION`.**

**Status:** ready-for-agent

## The premise changed — read this before anything else

The project believed for weeks that **no public GMS v84 opcode table existed** and that the table
would have to be reverse-engineered from the owner's own client. **That is wrong.** Ticket 20 found
one and it is on disk now:

```
D:\games\MSv84\opcodes\gms_v84.yaml            553 rows, from Chronicle20/atlas
D:\games\MSv84\opcodes\ida_export_gms_v84.json 734 KB, the IDB export the yaml was derived from
D:\games\MSv84\opcodes\gms_v83.yaml            the same shape for v83 — use it to diff
D:\games\MSv84\opcodes\discover_gms_v84.md     atlas's own account of how it derived them
```

Verified by the orchestrator, not taken on report: `553` rows = `330` clientbound + `223`
serverbound. Every row carries `op`, `direction`, `opcode`, `fname` (the IDA function name) and a
`provenance`.

## The catch, which is this ticket's real work

Provenance splits **234 `ida-discovered` / 222 `csv-import` / 97 `manual`**. `csv-import` means
*"seeded from the v83 column because the CSVs have no v84 column"* — i.e. **assumed identical to
v83, not observed**.

Below `0x3F` that assumption is fine: ticket 20 established the `0x00–0x3E` band is genuinely
unchanged from v83, which is why the handshake works. **Above `0x3E` it is exactly the trap** — the
v84 shift is monotonic but non-uniform (cumulative +2/+3/+4/+6/+7/+10), so a v83-inherited value is
silently wrong.

Measured by the orchestrator, and **larger than ticket 20 reported**:

| direction | `csv-import` rows at opcode ≥ `0x3F` |
|---|---|
| clientbound | 30 |
| **serverbound** | **80** |
| **total suspect** | **110 of 553 (20%)** |

Ticket 20's write-up says 30 because it audited only the clientbound half. **All 110 must be
adjudicated**, and serverbound is the bigger and less examined pile.

Adjudicate against `ida_export_gms_v84.json` using the row's `fname` — that is what makes this
tractable, because the IDA function name (`CLogin::OnCheckPasswordResult`) identifies the handler
independent of the number. Where the IDB gives an opcode, it wins over the csv-seeded value. Where
it does not, mark the row unresolved — do not guess, and do not let a v83 value through unlabelled.

One row is already known bad and is your smoke test: **`SERVERMESSAGE` sits at `0x44` in the yaml,
the IDB evidence says `0x46`, and `0x46` is vacant.** If your adjudication does not independently
catch that one, your adjudication does not work.

## Mapping to Cosmic

Cosmic's live table is 485 entries (**307 send + 178 recv**) in
`src/main/resources/opcodes/{send,recv}ops-83.properties`. Cosmic's *send* = server→client =
atlas *clientbound*; *recv* = client→server = atlas *serverbound*. Atlas is a superset both ways
(330 ≥ 307, 223 ≥ 178), so every Cosmic opcode should find a home — but the **names will not all
match**, and name-matching by eye across 485 rows is how a wrong table gets shipped.

Do it mechanically: match Cosmic's key to an atlas `op` where possible, and produce an explicit
**unmatched list in both directions** — Cosmic keys with no atlas row, and atlas rows with no
Cosmic key. Both lists are deliverables. A Cosmic key with no v84 evidence must not silently keep
its v83 number without being on that list.

**Sanity anchor:** `SET_FIELD` is `125` in v83 and Cosmic's table matches. Ticket 17 measured its
progression as 129 at v85, 133 at v86, 136 at v88, 140 at v90 — so a plausible v84 value sits at
125–129. If your table produces something outside that, something is wrong.

## The other half: prove the table before trusting it

This project's standing lesson is that **three wrong conclusions in one day came from broken
instruments, not bad reasoning.** A 485-row table that is 95% right is worse than no table, because
the failures are silent and scattered. So build the check, not just the table:

- A test that loads the 84 table the same way the server does and asserts it is complete and
  well-formed (no missing keys, no duplicate opcode values within a direction, all in range).
- A **v83↔v84 diff report** as a committed artefact: for every Cosmic key, its v83 value, its v84
  value, the delta, and the provenance that justified it. That report is how a human reviews 485
  rows without reading 485 lines of properties.
- Assert the low band: everything at opcode ≤ `0x3E` must be **unchanged** from v83. If your table
  moves something down there, you have a bug — ticket 20 proved that band identical by reaching the
  login screen.

## Acceptance criteria

- [ ] `opcodes/sendops-84.properties` and `opcodes/recvops-84.properties` exist and load under
      `-Dopcode-version=84` with all 5 bad-table failure modes still failing loudly
- [ ] All 110 `csv-import` rows at opcode ≥ `0x3F` adjudicated against the IDB — each either
      corrected, confirmed, or explicitly listed as unresolved
- [ ] The `SERVERMESSAGE` `0x44`→`0x46` error is caught by your process, not copied from this ticket
- [ ] Unmatched lists produced in both directions, and neither is silently empty
- [ ] `0x00–0x3E` proven byte-identical to the v83 table
- [ ] The v83↔v84 diff report committed
- [ ] `ServerConstants.VERSION` **unchanged at 83**, `-Dopcode-version` default **unchanged at 83**
- [ ] Full suite green (baseline **2072 passed, 0 failed**) — with the default unchanged, adding
      these files must not alter a single existing behaviour

## Verification gate

Server starts clean with `-Dopcode-version=84` and its dumped runtime table matches the committed
properties exactly — the same computational zero-diff proof ticket 32 used, which is stronger than
a boot log. **No owner client launch.** The cutover (flipping `VERSION` to 84 and driving the real
client past login) is ticket 29's, deliberately not this one — the owner's v83 server stays live
and working throughout.

## Delivered

**Status:** done, with 5 of 485 Cosmic keys explicitly unresolved (listed below).

Files: `src/main/resources/opcodes/sendops-84.properties` (307), `recvops-84.properties` (178),
`src/test/java/net/opcodes/OpcodeTable84Test.java`, `docs/work-plan/v84-opcode-diff.md`,
`docs/work-plan/tools/v84-opcodes/` (the generator, so this is reproducible not hand-typed).
`ServerConstants.VERSION` still `83`; `-Dopcode-version` default still `"83"`; no production
code touched at all.

### The premise had to be corrected before any of it worked

Two things in the ticket brief are wrong, and both matter:

1. **`ida_export_gms_v84.json` contains no opcodes.** It is a packet *structure* export -
   866 functions, each with `address` / `direction` / `calls[]`. There is no opcode field and
   no `_recv_0xNN` suffix on the names (exactly one name in 866 has one). Opcode numbers appear
   only as free prose inside `note` and `calls[].comment`. Adjudicating "via each row's fname
   against the IDB export" as written is not possible.
2. **`provenance` does not identify the stale rows.** atlas's own `task100_summary.md` (in the
   same directory) records that 188 v84 rows were already reshifted against the IDB - *without*
   updating provenance. So `csv-import` is stale on rows that were fixed and on rows that were
   not, in both directions. The 110-row suspect set the brief computes from provenance is
   therefore neither sound nor complete.

What does identify a stale row: **its v84 opcode still equals its v83 opcode inside a range the
IDB says shifted** - a delta=0 island in an otherwise monotonically rising curve. 94 such rows
(11 clientbound, 83 serverbound). `SERVERMESSAGE` falls out of that test on its own, no
hand-patching - see the smoke test below.

The usable independent instrument turned out to be **`template_gms_84_1.json`**, the live v84
routing table (222 writers + 145 handlers, each with an opcode *and* an fname). Cross-checked
against the registry it agrees on **357 of 357** fname matches - the 6 apparent conflicts are
all fnames shared by several ops (`CUser::OnChat` covers both `CHATTEXT` and `CHATTEXT1`,
`CUIFadeYesNo::OnButtonClicked` covers half the confirm dialogs) and resolve to agreement when
compared opcode-by-opcode. That table is also the only evidence that the serverbound `0x3F-0x75`
band genuinely did not shift - it independently places ~40 of those ops at their v83 opcodes,
and puts the first serverbound shift at `0x76 -> 0x78`.

### Numbers

| | clientbound | serverbound | total |
|---|---|---|---|
| stale registry rows found | 11 | 83 | 94 |
| resolved by template as genuinely unshifted | 0 | 37 | 37 |
| adjudicated: corrected (opcode moved) | 11 | 9 | 20 |
| adjudicated: confirmed unchanged | 0 | 18 | 18 |
| **UNRESOLVED** | **0** | **19** | **19** |

Clientbound is fully resolved. All 19 unresolved rows are serverbound, and 16 of them are the
`0xE7-0xF8` window where the shift is provably +6 below and +7 above with no anchor inside -
one op is inserted somewhere in there and nothing on disk says where. 11 of the 19 are
`UNNAMED_R***` rows with no fname, which atlas itself could not identify either.

**Only 5 of Cosmic's 485 keys land on unresolved evidence:**

| table | key | v83 | why |
|---|---|---|---|
| sendops | `MESO_BAG_MESSAGE` | `0xD2` | no atlas row either version; task-100 deleted it as version-absent in v84. Shift curve is ambiguous here (+5 below, +4 above - a genuine non-monotonic spot around the summon ops). |
| recvops | `CLICK_GUIDE` | `0xA2` | no atlas row; task-100 deleted it as "mis-fnamed and absent in v84". Curve ambiguous (+4/+6). |
| recvops | `OPEN_ITEMUI` | `0xEC` | in the `0xE7-0xF8` +6/+7 window |
| recvops | `CLOSE_ITEMUI` | `0xED` | same window |
| recvops | `USE_ITEMUI` | `0xEE` | same window |

These five carry **`0xFFFF`** in the emitted tables, with a `# UNRESOLVED (...)` comment on the
line above giving the reason - not a v83 value that would be silently wrong. `0xFFFF` never
appears on the wire, so the op is inert rather than misrouted; this is the same idiom Cosmic
already uses for `MAPLETV = 0xFFFE`. `OPEN_ITEMUI` in particular *cannot* be left at its v83
`0xEC`, because `0xEC` is `COUPON_CODE` in v84 (IDB- and template-confirmed) - that is exactly
the silent collision the ticket warns about.

### Smoke test, unassisted

The adjudicator flagged `SERVERMESSAGE` without being told to:

```
SMOKE TEST SERVERMESSAGE: CORRECTED 0x44 -> 0x46 | shift +2 between IDB anchors; 0x46 vacant
```

It reaches it the same way for `ALLIANCE_OPERATION` (`0x42 -> 0x44`, which is what frees `0x44`),
and for the kite pair, where the *template itself* is stale: it places `SpawnKite` at its v83
`0x10F` while placing its sibling `DestroyKite` at the IDB-derived `0x117`. A template row
sitting at a row's v83 opcode inside a shifted region is treated as stale evidence, not evidence
- the same test applied to the registry, applied to the template.

### Sanity anchor

`SET_FIELD` = **`0x80` (128)**, i.e. v83 125 +3. Inside the 125-129 the ticket predicted, and
consistent with 129@v85. Also `SPAWN_PLAYER 0xA0->0xA3`, `SPAWN_MONSTER 0xEC->0xF2`,
`SPAWN_NPC 0x101->0x108`, all matching `discover_gms_v84.md`'s per-range map exactly.

### Unmatched lists (neither empty)

**Cosmic keys with no atlas row - 7.** `MESO_BAG_MESSAGE`, `CLICK_GUIDE` (unresolved, above);
`CASHSHOP_CASH_GACHAPON_OPEN_RESULT` `0x14E->0x155` (name-matched to a v84-only atlas row);
`PARTY_SEARCH_REGISTER` `0xDC->0xE2` and `USE_MAPLELIFE` `0x100->0x107` (shift curve, target
vacant); `CUSTOM_PACKET 0x3713` and `MAPLETV 0xFFFE` (Cosmic-internal sentinels, kept verbatim).

**Atlas rows with no Cosmic key - 75.** Full table in the diff report.

Note the join is by **v83 opcode position, not by name**: Cosmic's v83 table and atlas's v83
registry agree on the opcode for every one of the 303 name-matched send keys and 166 of 168 recv
keys, so position is the stronger join and it carries the aliases for free (Cosmic `SERVERLIST`
= atlas `WORLD_INFORMATION`, Cosmic `REPORT` = atlas `CLAIM_REQUEST`, Cosmic `USE_HAMMER` =
atlas `ITEM_UPGRADE_UPDATE`, and so on). The two recv name/value disagreements are Cosmic's
wedding trio, which is off by one against atlas's naming; position maps them correctly anyway.

### Proof, not assertion

`OpcodeTable84Test`, 5 tests:
- key set == `SendOpcode.values()` / `RecvOpcode.values()` exactly, both directions
- every value decodes and fits an unsigned short
- **no v84 collision that v83 did not already have** (Cosmic's `WEDDING_TALK` /
  `WEDDING_TALK_MORE` legitimately share `0x8B`; anything else colliding means the shift was
  misapplied)
- **`0x00-0x3E` byte-identical to the v83 table**, both directions
- **the ticket-32 zero-diff proof**: reloads `SendOpcode`/`RecvOpcode` in a child
  `URLClassLoader` with `-Dopcode-version=84` set, reads the runtime value off all 485
  constants, and compares to the committed files. This exercises the real
  `OpcodeTable.load` -> `Integer.decode` -> enum-init path, so it is the runtime table being
  asserted, not the file's own contents.

The test was itself checked by mutating the table: `SERVERMESSAGE` back to `0x44` and
`LOGIN_STATUS` to `0x30` produced `sendops-84 collides at 0x30: [LOGIN_STATUS,
SET_TAMING_MOB_INFO]` and `sendops-84 moved LOGIN_STATUS out of the proven-unchanged 0x00-0x3E
band`. Reverted after.

### Suite

`2062 passed, 0 failed` excluding three test classes owned by the concurrent quest-merge work in
this worktree (`V84EvanQuestDataTest`, `V84QuestNodeTest`, `V84RegressionTest` - all four
failures are Quest.wz merge-count assertions, no opcode involvement). Full run is
`2092 run, 4 failed`, those same four. Baseline was 2072 with the other agent's tests included;
this ticket adds 5 and touches no production code, so no existing behaviour can have moved.

### What could not be verified without a live client

- The 19 unresolved registry rows / 5 unresolved Cosmic keys. Nothing on disk distinguishes +6
  from +7 in the `0xE7-0xF8` serverbound window. Resolving them needs either a named v84 IDB
  handler set or a packet capture.
- That the resolved values are *correct*, as opposed to consistent with every artefact on disk.
  Three independent sources agree (registry, live routing table, the IDB-derived range map) and
  the tables are internally collision-free, but agreement among artefacts is not the wire.
- The registry carries one pre-existing duplicate, serverbound `0xEC` =
  `COUPON_CODE` + `OPEN_ITEMUI` (task-100 claimed zero dups at 552 rows; the file on disk has
  553 after a later merge). Resolved in Cosmic's favour by parking `OPEN_ITEMUI` at `0xFFFF`.
- Ticket 29 still owns the cutover. Nothing here flips `VERSION`.

## Rollback

Two new resource files. Nothing reads them unless `-Dopcode-version=84` is passed. Deleting them
restores today's behaviour exactly. This is the safest ticket in the migration and it should stay
that way — resist any temptation to "just also" flip the version.
