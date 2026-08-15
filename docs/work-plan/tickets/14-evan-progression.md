# 14 — Evan progression — SP, HP, dragon evolution

**Blocked by:** 12, 13

**Status:** ready-for-agent

## What to build

An Evan levels up correctly and the dragon evolves through the job stages.

Evan does not use the normal progression path. SP is awarded at fixed job levels rather than every level, and is stored in the extended ten-slot array rather than a single value — the encoding and the `sp VARCHAR(128)` column already exist in Cosmic, but `Character.levelUp()` needs Evan's award rules and its HP/MP growth (same as Magician).

The dragon's appearance changes with job level, which is why the quest data from ticket 13 matters here: the growth stage is driven by quest state and is also what displays the dragon in the skill window.

## Acceptance criteria

- [ ] `levelUp()` awards Evan SP at the correct job levels
- [ ] HP/MP growth matches Magician values
- [ ] SP allocates and persists across relog through the extended-SP column
- [ ] Dragon appearance changes at each job advancement
- [ ] Dragon growth stage displays correctly in the skill window
- [ ] Job advancement 2200 → 2218 works at every step
