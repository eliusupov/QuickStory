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

## Rollback

Two new resource files. Nothing reads them unless `-Dopcode-version=84` is passed. Deleting them
restores today's behaviour exactly. This is the safest ticket in the migration and it should stay
that way — resist any temptation to "just also" flip the version.
