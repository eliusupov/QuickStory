# 11 — Evan skill crash audit — final skill list locked

**Blocked by:** 10

**Status:** ready-for-agent

## What to build

A definitive list of which Evan skills work in the v83 client and which must be cut, with the cut ones stripped from the WZ import.

**This must happen before any skill Java is written.** Some Evan skills reference animation actions that do not exist in v83's client-side string pool, and those cannot be added — the release author states it directly: *"actions are client-sided StringPools, so you can't just add and implement an entire new set of v84 actions."* A skill that crashes the client is not fixable server-side at any effort. Implementing one before testing it is wasted work.

Method: give a test character every Evan skill, fire each one, record whether the client survives. Strip the crashers from your `Skill.wz` import so they never appear in the skill window at all — a skill the player can see but never use is worse than one that was never there.

Output is an input to ticket 12: the 14 skills identified as real work may shrink.

## Acceptance criteria

- [ ] Every Evan skill fired in-client and its result recorded
- [ ] Crashing skills identified and removed from the WZ import
- [ ] Surviving skill list written down as the authoritative scope for ticket 12
- [ ] Skill window shows only usable skills
- [ ] The permanent ceiling this imposes on Evan is documented for the player-facing notes
