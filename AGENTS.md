# QuickStory — the owner's own MapleStory server

A private MapleStory server, descended from HeavenMS/OdinMS. Java, ~2,900 tests, a MySQL database
under Liquibase, and the game's own `.wz` data tree. **The owner plays on it.** That is the point of
the project and the constraint on everything in it.

**Current state:** the GMS v84 port is complete. Normal work happens on `master`; the workflow below
applies to every new task.

## How work gets done — read `docs/work-plan/WORKFLOW.md`

By default, every piece of work, however small: **spec → tickets → ledger → `/implement` → `/code-review`.**
Implement agents commit their own work; review agents commit their own fixes; **the orchestrator
commits only the ledger and never reviews or writes code.** Each ticket names the agent tier its
effort deserves. Opus only.

This full workflow is the default. The owner may explicitly waive named planning steps (spec,
tickets and/or ledger) and/or independent review for a named task. A waiver is task-scoped, does
not carry forward, and leaves every unnamed step in force. A workflow waiver never waives evidence
rules or hard safety constraints; changing one requires an explicit, separate instruction.

## The rule for v84-era behavior

For v84 behavior, ask: **is it in the v84 data?** If yes, the server should support it. If no, do
not call it a v84 parity gap. The owner: *"i want feature parity to v84 only."*

Exception, and it is labelled as one: a handful of v83-legacy defects are ticketed at the owner's
request. They are marked `v83 legacy` and must never be relabelled as parity gaps.

## Where the state lives — read these, do not re-derive them

| file | what it answers |
|---|---|
| `docs/work-plan/WORKFLOW.md` | **how any work gets done here.** Spec, tickets, agent tiers, who commits |
| `docs/work-plan/TICKET-LEDGER.tsv` | **the work queue.** What is done, what is next, what is blocked, and which agent runs each row |
| `docs/work-plan/tickets/NN-*.md` | one ticket each, with ids, precedent and acceptance criteria |
| `docs/work-plan/V84-REMAINING-SPEC.md` | why the remaining work exists |
| `docs/work-plan/SOURCES.md` | **what may be cited as evidence, and the traps this project has hit** |
| `docs/work-plan/V84-COVERAGE.md` | what v84 added vs what we carry, computed from `add-list/` |
| `docs/work-plan/ORCHESTRATOR-PROMPT.md` | how to run this project as an orchestrator |
| `docs/work-plan/AUTONOMY.md` | how to run the ticket queue unattended |

Everything above is regenerable or hand-maintained on purpose. **Do not create a new tracker.** Four
disagreeing status files were consolidated once already.

## Evidence rules

- **Never invent** a rate, a dropper, a coordinate, a script's behaviour, or a value. Copy a real
  analogue row and name it in the changeSet header.
- **Read the whole quest** — pre-accept text, objective, dialogue. A mob token is not a drop table.
- **Check `git log` before calling anything a defect.** Several "gaps" were deliberate removals.
- **A claim in a tracker is not evidence.** Verify against the tree. Tickets have been wrong,
  including one that proposed a fix already applied and cited its own fix commit as precedent.
- Per-AP HP/MP gain is **server-side and provably absent** from all client data. Settled; do not
  re-ask.

## Hard constraints

- **Layout.** `D:\games\MapleStory\` holds the owner's **v83 HD client** at its root (`*.wz`,
  `MapleStory.exe`) *and* the server repo at `D:\games\MapleStory\Server\Cosmic`. `D:\games\MSv84\`
  holds the v84 side: `client\`, `client-hd\`, `opcodes\`, `bypass\`, and the Ezorsia fork.
- **Read-only, never write:** the client files at the ROOT of `D:\games\MapleStory\` (not
  `Server\`, which is the repo), `D:\games\MSv84\client\`, `D:\games\dreamms\`, and
  `D:\games\MapleStory\Server\porting-resources\wz-data\v84\` — the pristine v84 carve.
- **The repo is `D:\games\MapleStory\Server\Cosmic`**, branch `master`. The completed v84 migration
  worktree is historical; do not use it for new work.
- **Work on `master`.** Never merge, rebase, push, or reset it without the owner asking in those words.
- **Never launch a game client** — it rewrites a shared registry key.
- **Changesets and resources ship INSIDE the jar.** A restart without `./mvnw -o package` applies
  nothing. Verify a change landed in the **database**, not in the log.
- **SELECT-only** on MySQL from agents (`cosmic`, root/root). Changes go through a new Liquibase
  changeSet. Never edit an applied changeSet.
- Never `git add -A`, never a bare `git stash` or `git reset`. Explicit pathspecs only.
- **Parallel agents collide on `target/`.** Either forbid maven in the fan-out, or let exactly one
  agent hold it.
- **Commit early.** Untracked files have no safety net — two agents once shared one file and 47 rows
  of work were nearly lost.
- Every character in the database is GM 2 or 6 **by design**. Never benchmark against another one.

## Working style

- **Orchestrate; do not implement.** Dispatch subagents and verify their claims before relaying.
- **The orchestrator does not open files.** It reads the ledger and dispatches. Facts arrive as
  short structured reports. This is what keeps its context small.
- **Decide from the data.** The owner: *"None of them need my judgment."* Exhaust the v84 carve, the
  client binary, our tree, the database, `git log` and dated sources before asking anything.
- **Agents are Opus only** — `gp-opus-low` / `-medium` / `-high`. Never Sonnet. Pick the tier; do not
  default to high.
- **Answer the owner very short.** Bullets, no preamble, `**tldr:**` at the end. *"i cant read so
  much."* Decisions go as a short list with your pick in bold. Detail only if he asks.
- **Write short too.** Ledger cells and docs get the finding, not the transcript. One or two lines.
