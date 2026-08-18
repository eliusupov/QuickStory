# How work gets done here — permanently, for anything

This is the flow for **any** work in QuickStory: a bug, a feature, a migration, a sweep. Not just
the v84 port. Nothing gets built outside it.

---

## The shape

```
idea  ->  /to-spec  ->  /to-tickets  ->  ledger  ->  /implement  ->  /code-review  ->  done
                                           ^                                            |
                                           +--------------------------------------------+
```

**Nothing is implemented without a spec and tickets.** Not "small" things either — the audit that
found sixty defects only worked because there were tickets to audit.

---

## 1. Spec and tickets, always

- `/to-spec` — synthesises what has been discussed into `docs/work-plan/<feature>-SPEC.md`.
  Problem, solution, user stories, decisions, out of scope. **No file paths, no code** — those rot.
- `/to-tickets` — breaks the spec into vertical slices, one file each under
  `docs/work-plan/tickets/NN-<slug>.md`, numbered from the current maximum. Each ticket carries its
  ids, its precedent, its acceptance criteria, and what blocks it.
- Append a row per ticket to `docs/work-plan/TICKET-LEDGER.tsv`. **That file is the queue.**

A ticket that does not name exact ids and a precedent row to copy is not finished. The test: could
a `gp-opus-low` agent get it wrong? If yes, the brief is underspecified — fix the ticket, do not
raise the tier.

---

## 2. Every ticket names its own agent

The ledger carries an `agent` column. Effort decides the tier; nobody defaults to high.

| effort | agent | what it looks like |
|---|---|---|
| trivial | `gp-opus-low` | one constant, one row, a rename. Exact brief, no judgment |
| small | `gp-opus-medium` | a few sites, a changeSet from a named precedent |
| medium | `gp-opus-medium` | multi-file but the shape is known |
| large | `gp-opus-high` | judgment about where the fix belongs |
| any code review | `gp-opus-high` | adversarial work is never cheap |
| research / decoding | `gp-opus-xhigh` | rare. Binary and jump-table work only |

**Opus only. Never Sonnet on this project.**

---

## 3. The implement agent

Dispatched with the **`/implement`** skill, pointed at one ticket file.

- It reads the ticket, does the work, runs the tests.
- **It commits its own work.** The orchestrator does not commit code.
- It reports in **at most 15 lines**: verdict, files changed, test counts, and anything that
  contradicts the ticket.

If it finds the ticket is wrong — already done, stale line numbers, a claim that does not hold —
it says so and stops rather than forcing the change. That has happened and it matters.

---

## 4. The code-review agent

After a chunk of tickets lands, the orchestrator spins up an agent with the **`/code-review`** skill
over that chunk.

- It reviews **and fixes** what it finds.
- **It commits its own fixes.**
- It is adversarial by construction: its job is to refute, not to confirm.

The orchestrator never reviews the code itself and never commits it. Reviewing your own dispatch is
how a wrong claim survives.

---

## 5. The orchestrator's job, and its limits

It does exactly four things:

1. Read `TICKET-LEDGER.tsv`
2. Dispatch the agent the row names
3. Write the verdict back to the ledger, and commit **the ledger**
4. Move to the next row

It **does not**: open source files, run tests, review code, commit code, or re-derive facts that a
document already holds. Every fact it knows arrived in a 15-line report.

That restriction is not tidiness — it is why its context stays small enough to finish the queue
without compacting. See `AUTONOMY.md`.

---

## 6. When the work is done

The ledger row closes. The ticket file stays as the record of why it was built that way. The spec
stays as the record of what the goal was.

**Do not create a new tracker.** Four disagreeing status files were consolidated once already, and
the consolidation had to be computed from `docs/wz-baseline/add-list/` because none of them agreed.
