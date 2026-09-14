# How work gets done here — permanently, for anything

This is the default flow for **any** work in QuickStory: a bug, a feature, a migration, a sweep. Not
just the v84 port.

---

## The shape

```
owner's task  ->  ledger  ->  /implement  ->  done
                    ^                        |
                    +------------------------+
```

**Spec, tickets and independent code review are opt-in:** perform each only when the owner asks
for it. This standing default applies to later work too. Existing specs and tickets remain useful
records and briefs; they do not require new planning artifacts or review for every change.

### Explicit owner waiver

The ledger remains required unless explicitly waived by the owner for a named task. An explicit
task waiver may also omit a previously requested step; it does not carry forward and leaves
unnamed steps in force. Workflow preferences never waive evidence rules or hard safety constraints;
changing one requires an explicit, separate instruction. Urgency and task size never imply a ledger waiver.

---

## 1. Ledger by default; spec and tickets when requested

- When requested, `/to-spec` synthesises what has been discussed into `docs/work-plan/<feature>-SPEC.md`.
  Problem, solution, user stories, decisions, out of scope. **No file paths, no code** — those rot.
- When requested, `/to-tickets` breaks the task or requested spec into vertical slices, one file each under
  `docs/work-plan/tickets/NN-<slug>.md`, numbered from the current maximum. Each ticket carries its
  ids, its precedent, its acceptance criteria, and what blocks it.
- Append a row per task or requested ticket to `docs/work-plan/TICKET-LEDGER.tsv`, unless the
  ledger was explicitly waived. **That file is the queue.** A direct task needs no ticket file.

A ticket that does not name exact ids and a precedent row to copy is not finished. The test: could
a `gp-opus-low` agent get it wrong? If yes, the brief is underspecified — fix the ticket, do not
raise the tier.

---

### Reading the ledger

`TICKET-LEDGER.tsv` is plain TSV — header on line 1, data after, **no comment convention**. Do not
add one; parsers here do not skip comments.

- **Queue filter:** `state != REFUSED && startable_now == YES`
- **Dispatch target:** the `agent` column
- Rows are **ragged by design** — early tickets carry fewer fields than later ones. Any reader must
  tolerate short rows rather than assume a fixed column count.

---

## 2. Every ledger row names its own agent

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

Dispatched with the **`/implement`** skill, pointed at the owner's named task or an existing or
requested ticket file. No ticket waiver is needed for direct tasks.

- It reads the task brief or ticket, does the work, runs appropriate checks.
- **It commits its own work.** The orchestrator does not commit code.
- It reports in **at most 15 lines**: verdict, files changed, test counts, and anything that
  contradicts the ticket.

If it finds the ticket is wrong — already done, stale line numbers, a claim that does not hold —
it says so and stops rather than forcing the change. That has happened and it matters.

---

## 4. The code-review agent

Only when the owner asks for independent review, the orchestrator spins up an agent with the
**`/code-review`** skill over the requested work. Implementation checks still run by default.

- It reviews **and fixes** what it finds.
- **It commits its own fixes.**
- It is adversarial by construction: its job is to refute, not to confirm.

The orchestrator never reviews the code itself and never commits it. Reviewing your own dispatch is
how a wrong claim survives.

---

## 5. The orchestrator's job, and its limits

Under the default workflow, it does exactly four things:

1. Read `TICKET-LEDGER.tsv`
2. Dispatch the agent the row names
3. Write the verdict back to the ledger, and commit **the ledger**
4. Move to the next row

It **does not**: open source files, run tests, review code, commit code, or re-derive facts that a
document already holds. Every fact it knows arrived in a 15-line report.

For direct work it dispatches the owner's brief; add planning or review agents only for requested
steps. With a task-scoped waiver, omit only the named steps.

That restriction is not tidiness — it is why its context stays small enough to finish the queue
without compacting. See `AUTONOMY.md`.

---

## 6. When the work is done

When implementation and its checks are complete (and requested review is complete), the ledger
row closes. Existing or requested tickets and specs remain the record; new ones are not required.

**Do not create a new tracker.** Four disagreeing status files were consolidated once already, and
the consolidation had to be computed from `docs/wz-baseline/add-list/` because none of them agreed.
