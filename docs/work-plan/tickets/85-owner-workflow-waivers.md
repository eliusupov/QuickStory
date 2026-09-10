# 85 - Recognize explicit owner workflow waivers

**Class:** owner-requested
**Slice:** `docs/work-plan/OWNER-WORKFLOW-WAIVER-SPEC.md`
**Blocked by:** None.
**Startable now:** YES.
**Implementation agent:** `gp-opus-low`.
**Review agent:** `gp-opus-high`, unless explicitly waived by the owner for this task.

Update `AGENTS.md` and `docs/work-plan/WORKFLOW.md` so the existing
spec -> tickets -> ledger -> implementation -> independent review flow remains the default, while
an explicit owner instruction may waive spec, tickets, ledger and/or independent review for the
task it names.

## Acceptance criteria

- [ ] Both documents state that the full workflow remains the default.
- [ ] Both recognize an explicit, task-scoped owner waiver of any named planning step and/or
      independent review.
- [ ] A waiver does not carry to later tasks and does not waive unnamed workflow steps.
- [ ] Safety, evidence and hard constraints remain in force unless the owner explicitly and
      separately changes the relevant constraint.
- [ ] No ledger, source, database, client or WZ change is made.

## Do not

- Do not weaken any safety, evidence, Git, database, client-launch or read-only-path rule.
- Do not make workflow waivers implicit or infer them from urgency or task size.
