# Owner workflow waivers

## Problem

The workflow is written as absolute, so it conflicts with an explicit owner decision to skip
planning records or independent review for a particular task.

## Solution

Keep spec, tickets, ledger, implementation and independent review as the default workflow. Allow
the owner to explicitly waive spec, tickets, ledger and/or independent review for the task named in
that instruction.

## Decisions

- A waiver must be explicit and task-scoped; it is not a standing exception for later work.
- Only the named workflow steps are waived. All other workflow steps still apply.
- Safety, evidence and hard constraints remain non-waivable unless the owner explicitly and
  separately changes the relevant constraint.

## Out of scope

- Weakening evidence rules or hard constraints.
- Changing migration, database, Git, client-launch or read-only-path safeguards.
- Creating a second work tracker.
