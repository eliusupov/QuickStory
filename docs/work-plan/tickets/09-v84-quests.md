# 09 — v84 non-Evan quests accept and complete

**Blocked by:** 03, 06, 07, 08

**Status:** ready-for-agent

## What to build

The v84 quests that are not part of Evan's chain can be accepted, progressed and completed.

v84 added 198 quests. Most are pure WZ data — today Cosmic has only 253 scripts for 2,818 quests, so roughly 9% need JavaScript. Expect on the order of 18 scripted quests here, though the real number comes from the WZ merge.

Blocked by the area tickets because quests target the maps, mobs and NPCs those tickets deliver. A quest asking you to kill a Skelegon cannot be verified before Skelegons spawn.

The Evan quest chain (`22xxx`) is deliberately excluded — it belongs to ticket 13.

## Acceptance criteria

- [ ] Quest data merged into `Quest.wz` (QuestInfo, Check, Act, Say) and into the server XML tree
- [ ] Quests appear in the in-game quest list with correct text
- [ ] Quests requiring scripts have them written
- [ ] A representative sample across the new areas is accepted and completed end to end
- [ ] Existing quests still work — no regression from the `Quest.wz` merge
