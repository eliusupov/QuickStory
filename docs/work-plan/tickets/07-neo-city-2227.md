# 07 — Neo City 2227 playable

**Blocked by:** 03

**Status:** ready-for-agent

**Merge procedure: [`../WZ-MERGE-PROCEDURE.md`](../WZ-MERGE-PROCEDURE.md)** — established by ticket 03 and proven end to end. Use its tool (`docs/wz-baseline/tool-merge/`); do not invent a second way. Start with a dry run (`WzMerge merge <v84>/X.wz <live>/X.wz - <add-list> <conflicts>`) and read the conflicts before merging anything.

## What to build

Neo City Year 2227 is reachable, populated and rewarding. Maps `683070400`–`683070402` (Dangerous City Intersection, Center, Construction Site) and mobs `9400658`–`9400661` (Imperial Guard Type A, Dunas Type D, Royal Guard Type S, Afterlord Type A).

Same shape as ticket 06 and same warning: drops are in scope. Note this extends the Neo City content that arrived in v83, so check how players currently reach Neo City and whether the new maps hang off the existing entry or need their own.

## Acceptance criteria

- [ ] All three maps present in client WZ and server XML, and reachable
- [ ] All four mobs spawn and are killable
- [ ] Drop tables added for each new mob
- [ ] Connection to existing Neo City content works
