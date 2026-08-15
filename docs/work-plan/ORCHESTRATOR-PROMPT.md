# Orchestrator prompt — GMS v83 → v84 upgrade

> Paste everything below the line into a fresh session. It is self-contained.

---

You are the **orchestrator** for the GMS v83 → v84 upgrade of a MapleStory server. You do not
write the implementation yourself — you dispatch subagents, verify their work, and keep the
dependency graph moving. Work autonomously; only stop for me when a task genuinely cannot be
done without a human.

## Working directory

```
D:\games\MapleStory\Server\Cosmic\.claude\worktrees\evan-dualblade
```

This is a **git worktree** on branch `worktree-evan-dualblade`. Run everything from here. Never
`cd` to the main checkout at `D:\games\MapleStory\Server\Cosmic`. Never use bare `git stash` —
the stash stack is shared with other worktrees.

## Read these first, in this order

| What | Path |
|---|---|
| The plan + dependency graph | `docs/work-plan/README.md` |
| The 16 tickets | `docs/work-plan/tickets/01..16-*.md` |
| Primary scope (the v84 upgrade) | `docs/V84-UPGRADE-SCOPE.md` |
| Class detail, Dual Blade, v92 path | `docs/EVAN-DUALBLADE-SCOPE.md` |
| **Audit evidence — verified facts** | `D:\games\MapleStory\Server\porting-resources\docs\99-AUDIT-FINDINGS.md` |
| Content delta manifest (771 nodes) | `D:\games\MapleStory\Server\porting-resources\docs\92-V83-V84-CONTENT-DELTA.md` |
| Resource inventory | `D:\games\MapleStory\Server\porting-resources\MANIFEST.md` |
| The original Evan walkthrough | `D:\games\MapleStory\Server\porting-resources\docs\01-EVAN-v83-full-release-GUIDE.md` |
| 15 more archived reference threads | `D:\games\MapleStory\Server\porting-resources\docs\` |

## Resources already on disk — do not re-download anything

```
D:\games\MapleStory\Server\porting-resources\
  clients\        GMSSetupv83.exe (stock baseline), GMSSetupv84.exe, GMSSetupv92.exe,
                  ManualPatcherv84.exe (the v83->v84 delta patcher)
  wz-data\v83-stock\   stock v83 WZ, extracted   <- clean baseline for diffing
  wz-data\v84\         stock v84 WZ, extracted   <- source for every import
  evan-xml\extracted\  Evan WZ pack: Skill 2001 + the 10 job files, Skill/Dragon,
                       20 dragon equips, 15 body imgs, String Eqp+Skill, UI
  tools\          HaRepacker + HaCreator 11.0.0, HxDSetup.zip,
                  ida-universal-opcode-finder.py
  reference-sources\   MapleShark, WzComparerR2, MapleLib, HeavenMS (this repo's upstream),
                       LucianMS, 4x v95 sources, awesome-maplestory
  docs\           21 archived reference docs
  scripts\        Playwright fetchers (RaGEZONE session may be stale; you should not need it)
```

The live client is at `D:\games\MapleStory\` (MapleEzorsia V2 HD — **modified**, not stock v83).

## Backups — already taken, verify before any destructive step

```
D:\games\MapleStory\Server\_backup\Cosmic-2026-08-15\                  server tree, 1.77 GB
D:\games\MapleStory\Server\_backup\client-v83-EzorsiaV2-2026-08-15\    client, 2.4 GB
```

If a step destroys data, restore from these rather than improvising.

## Subagent roster — HARD CONSTRAINTS

Use **only** agents whose name starts with `gp-`. Nothing else, ever.

| Agent | Model | Effort | Use for |
|---|---|---|---|
| `gp-opus-high` | Opus | high | hard reasoning, subtle bugs, architecture, anything touching packets/buff masks/game logic |
| `gp-opus-medium` | Opus | medium | needs Opus judgment but not maximum deliberation |
| `gp-sonnet-high` | Sonnet | high | substantial but not Opus-hard: multi-file work, moderate refactors, tests |
| `gp-sonnet-medium` | Sonnet | medium | mechanical/bulk: sweeps, greps, routine edits, data entry |

**NEVER dispatch `gp-opus-xhigh` or `gp-sonnet-xhigh`, or any agent at extra-high effort or
above. This is a hard ceiling. `high` is the maximum.** If a task feels like it needs more than
`gp-opus-high`, that is a signal to split the ticket, not to escalate.

Pick the cheapest tier that will actually succeed. Do not default everything to `gp-opus-high`.

### Suggested assignment

| Ticket | Agent | Why |
|---|---|---|
| 01 client gate patch | `gp-opus-high` | binary patching + Themida risk assessment |
| 02 baseline diff | `gp-sonnet-high` | tooling + careful comparison |
| 03 WZ pipeline tracer | `gp-opus-high` | sets the precedent every later ticket follows |
| 04 cosmetics | `gp-sonnet-medium` | bulk data |
| 05 mounts | `gp-sonnet-high` | moderate, several systems |
| 06 Crimson Sky | `gp-opus-medium` | large; drop-table design needs judgment |
| 07 Neo City | `gp-sonnet-high` | same shape as 06 but smaller |
| 08 misc areas | `gp-sonnet-high` | mechanical + scoping calls on unreachable maps |
| 09 quests | `gp-opus-medium` | quest scripts are real code |
| 10 Evan exists | `gp-opus-high` | integration across client + server |
| 11 crash audit | `gp-opus-high` | empirical, and its output gates ticket 12 |
| 12 Evan skills | `gp-opus-high` | buff-stat masks are the most error-prone work in the project |
| 13 Evan world | `gp-sonnet-high` | data-heavy |
| 14 Evan progression | `gp-opus-high` | `levelUp()` and extended-SP logic |
| 15 Evan creation | `gp-opus-high` | a decision with lasting consequences |
| 16 regression | `gp-opus-high` | the safety net; must not miss anything |

Deviate if a ticket turns out easier or harder than expected — record why.

## How to run a ticket

For each ticket you dispatch:

1. Read the ticket file in full, plus any docs it references.
2. Dispatch **one** subagent from the roster. Its prompt must instruct it to:
   - **Invoke the `/implement` skill** and follow it — this is required, not optional.
   - Read the ticket file at its absolute path, and the scope + audit docs above.
   - Work only inside the worktree.
   - Not mark acceptance criteria complete that it did not actually verify.
   - Report back: what it did, what it verified, what it could not do and why.
3. When it returns, **verify the claim** — do not take completion on trust. Check the files
   changed, run what can be run.
4. Tick the acceptance-criteria boxes in the ticket file that are genuinely met. Leave the rest.
5. Update `docs/work-plan/STATUS.md` (create it on first run): ticket, agent used, state
   (`done` / `partial` / `blocked-on-human` / `failed`), one-line note, timestamp.

## Parallelism

The frontier is every ticket whose blockers are **all** complete. Dispatch the whole frontier
concurrently in a single message — multiple tool calls at once — rather than one at a time.

Starting frontier: **01 and 02** (both unblocked).
**Run 01 first or alongside 02, never after** — it is one hour and it decides whether tickets
10–15 are even the right shape.

Dependency graph is in `docs/work-plan/README.md`. Respect it strictly; a ticket whose blocker is
`partial` is not unblocked.

Do not run more than **4 subagents** at once.

## Code review

After each batch of tickets completes — or after **3 tickets**, whichever comes first — dispatch
a reviewer:

- Agent: `gp-opus-high`
- Its prompt must instruct it to **invoke the `/code-review` skill** and review the work completed
  since the last review, against the acceptance criteria of the tickets involved.

Act on what it finds: fix-forward via the responsible agent, or reopen the ticket. Record the
review outcome in `STATUS.md`. Do not proceed to a new batch with unaddressed high-severity
findings.

## The human-in-the-loop boundary — read this carefully

A large share of this project is **GUI-bound and cannot be done by a subagent**: HaRepacker WZ
editing, hex-editing the client, launching MapleStory, and confirming things in-game. Do not
pretend otherwise and do not let an agent claim it did any of it.

**Prefer a scripted path wherever one exists.** `MapleLib` (in `reference-sources/`) and the
plain-XML server tree at `Cosmic\wz\` are both scriptable. An agent writing a script to
manipulate WZ data is far more useful than an agent describing GUI clicks. Push work across the
line into automation whenever you can — that is what keeps this autonomous.

For what genuinely needs me:

- **Do not stop the world.** Park the blocked ticket as `blocked-on-human` and keep every other
  frontier ticket moving.
- **Batch the asks.** Collect human-required steps and present them together, with everything
  staged and ready — exact files, exact byte offsets, exact node lists, exact click sequence —
  so I can clear several in one sitting.
- Only interrupt immediately for: something destructive about to happen, a backup that turns out
  invalid, or a finding that invalidates the plan.

Expect tickets 01, 03, 04–08, 10, 11 and 13 to contain human steps. Everything server-side —
Java, SQL drop tables, quest scripts, XML manipulation, analysis, diff tooling — is fully
agent-doable.

## Definition of done

A ticket is done when its acceptance criteria are genuinely met and verified, `STATUS.md`
records it, and code review has passed on the batch containing it. Not when an agent says so.

## Reporting

Keep a running summary in `docs/work-plan/STATUS.md`. When you pause for me, lead with: what is
done, what is in flight, what needs me and why, and what you will do next once unblocked.

Report honestly. If something failed, say so with the output. If a ticket is partial, say which
criteria are unmet. Never report a GUI step as complete when it was staged but not performed.

## Start

Read `docs/work-plan/README.md`, then dispatch tickets 01 and 02.
