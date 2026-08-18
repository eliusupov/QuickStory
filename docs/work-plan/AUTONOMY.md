# Running the ticket queue unattended

The goal: one orchestrator works `TICKET-LEDGER.tsv` from top to bottom, overnight, without the
owner in the loop — and **without its own context filling up**.

---

## Why the orchestrator's context fills, and the fix

It is not the harness and it is not the model. It is that the orchestrator reads files.

A subagent's tool output — every `Read`, every `grep`, every test run — stays in **that agent's**
context and is discarded when it finishes. Only its **final message** reaches the orchestrator. So
the orchestrator's context grows by roughly *(number of agents) x (length of their reports)* and by
nothing else.

Two rules follow, and they are the whole design:

1. **The orchestrator never opens a project file.** Not source, not WZ, not logs, not tests. It reads
   `TICKET-LEDGER.tsv` and nothing else. Everything it knows arrives in a report.
2. **Reports are capped.** An uncapped agent returns 3-6k tokens of prose. Capped at 15 lines it
   returns ~400. Over 16 tickets that is the difference between 100k and 7k.

Budget per ticket: dispatch (~300) + implement report (~400) + review report (~400) + ledger write
(~200) ≈ **1.5k tokens**. Sixteen tickets ≈ 25k. The 200k ceiling is never approached.

---

## The report contract

Put this in every dispatch. It is the single highest-leverage line in the whole setup.

```
Report in AT MOST 15 lines: VERDICT (DONE / BLOCKED / FAILED), what changed (file, one line each),
the test command you ran and its pass/fail counts, and anything that contradicts the ticket.
No narration, no process description, no restating the brief. If you found nothing, say so in one line.
```

Agents that ignore the cap get their reports truncated by the next orchestrator turn anyway, so the
cap costs nothing and saves everything.

---

## The loop

Per ticket, the orchestrator does exactly this and nothing else:

1. Read `TICKET-LEDGER.tsv`. Pick the first row that is startable and unblocked.
2. Mark it `in-progress` in the ledger.
3. Dispatch an implement agent: *"Work `docs/work-plan/tickets/NN-*.md` to its acceptance criteria"*
   plus the report contract.
4. On DONE, dispatch a review agent against the same ticket — **adversarial**, told to refute.
5. Write the verdict to the ledger. Commit the ledger.
6. Next row.

**One agent at a time holds `target/`.** Either the implement agent runs maven and the reviewer does
not, or the fan-out forbids maven entirely and the orchestrator runs the suite between tickets.

---

## Waking itself up

Claude Code can do this three ways. They compose.

| mechanism | use it for |
|---|---|
| **background agents** | The default. A dispatched agent runs for hours and re-invokes the orchestrator when it finishes. No polling, no scheduling — this alone drives the queue. |
| **`/loop`** (dynamic mode) | The orchestrator picks its own next wake time via `ScheduleWakeup`. Good as a safety net so the loop survives an agent that hangs and never reports. Use a long delay — 1200s+ — not a short poll. |
| **`CronCreate`** | Fire on a schedule — "every night at 01:00, work the queue." Use when you want it to start without you present. |

**Never poll.** A background agent's completion re-invokes the orchestrator automatically; a
short-interval wakeup to "check on it" is pure waste. Schedule long, or not at all.

---

## Starting it

Ordinary run, owner present:

```
Work the ticket queue in docs/work-plan/TICKET-LEDGER.tsv. One ticket at a time:
dispatch an implement agent, then an adversarial review agent, then write the verdict
to the ledger and commit. Do not open project files yourself. Stop when the queue is
empty or three tickets in a row fail.
```

Unattended overnight — same instruction, wrapped:

```
/loop Work the ticket queue in docs/work-plan/TICKET-LEDGER.tsv ...
```

---

## Stop conditions — always set these

An autonomous loop with no brakes is how a night gets wasted.

- Stop after **three consecutive failures**. Something systemic is wrong.
- Stop on any ticket that needs a **client launch**, a **server restart**, or an owner decision.
  Ticket 68 is the known case. Mark it `blocked` and move on.
- Stop when the ledger has no startable row.
- **Never** let the loop restart the server or launch a client while the owner may be playing.

---

## What the owner reads in the morning

`git log --oneline` and the ledger. Both are on disk, both survive any compaction, and neither
requires the orchestrator to have remembered anything.
