# 32 — Externalise the opcode table

**What to build:** the server's packet opcodes come from a properties file chosen at startup
instead of being compiled into two Java enums. Switching the server between v83 and v84 becomes
selecting a file, not editing 485 constants and rebuilding.

**Blocked by:** None — can start immediately.

**Status:** ready-for-agent

**Why this one first:** it is the thing that makes the whole protocol phase tractable. Without it,
every opcode experiment against a v84 client is an edit-recompile-relaunch cycle across 485
constants. With it, iterating is a file swap. ~60 lines of mechanism.

## The mechanism `[FACT-sourced]`

`Chronicle20/Vertisy` replaced the hardcoded opcode enum with a **valueless enum** plus an
`ExternalCodeTableGetter` that loads `sendops-NN.properties` / `recvops-NN.properties` at startup.
Same OdinMS AGPL lineage as Cosmic, so the licence is compatible and the idiom already matches.

**Correction to an earlier claim — do not repeat it:** Vertisy's `sendops-92.properties` is
**byte-identical** to its `sendops-90.properties` (same for recv). It is a **v90** table that
merely intended to reach v92. Take Vertisy's *mechanism*, never its data as "v92".

## Scope

- `net/opcodes/SendOpcode.java` (366 lines) and `RecvOpcode.java` (216 lines) — 485 entries total
- A `v83` properties pair generated **from Cosmic's current values**, so v83 behaviour is bit-identical
- Startup selection of the table, defaulting to v83

## Acceptance criteria

- [ ] Opcode values load from properties at startup; the enums no longer carry literals
- [ ] A generated `v83` table reproduces **exactly** today's values — diff the generated file
      against the current constants, entry by entry, and show zero differences
- [ ] Server starts, a character logs in, plays, and logs out with no behavioural change
- [ ] Full test suite green (baseline: **2,008** tests)
- [ ] Missing or unparseable entries fail **loudly at startup**, never silently as opcode 0
- [ ] Selecting a table is one setting; adding a `v84` table later needs no Java change

## Verification gate

**Zero-diff against current behaviour.** This ticket must be invisible in game — if anything
changes, it failed. No owner launch needed; the existing suite plus a local login proves it.

## Rollback

Single commit, self-contained; revert restores compiled constants.
