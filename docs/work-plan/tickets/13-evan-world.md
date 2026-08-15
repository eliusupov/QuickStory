# 13 — Evan world and quest chain playable

**Blocked by:** 10, 09

**Status:** ready-for-agent

## What to build

Evan's starting area and story quests are playable start to finish.

Maps `900010000`–`900020220` (Dream Forest Entrance/Trail/Forest, Lush Forest, Lost Forest Entrance/Trail/Forest), `900090100`–`900090103` (Tutorial 0–2, Job Advancement), `900030000` Behind the Stronghold, plus `100030301` Forest Hall and `100030320` Large Forest Trail 2. NPCs Afrien, Hiver, Olaf, Glowing Stele.

Three gaps found during the audit that this ticket closes, none of which the Evan XML pack covers — it contains no Quest and no Map data:

1. **No Evan quest data in v83.** `QuestInfo.img.xml` has Aran's `21000/21001/21010…` and zero `22xxx`. Confirmed both ways: GMS/84 serves quest 22000, GMS/83 returns 404.
2. **Six referenced maps missing.** Cosmic's Evan scripts already reference `100030102, 100030103, 100030200, 100030300, 100030310, 100030400`; v83 has only `100030000` and `100030001`.
3. **Evan NPCs missing.** `1013101`, the giver of quest 22000, does not exist in v83 — that range holds only `1013000`.

Cosmic already ships 13 Evan quest scripts (`22000-22008`, `22500-22507`) that are inert until the WZ data lands.

## ⚠ Inherited hazard — read before merging `Skill.wz/2001.img`

*Recorded by ticket 03f, 2026-08-16, from the 04/05/06 code review (F4). Nothing is broken today;
this is a trap laid for whoever merges Evan's skill image.*

`StatEffect.java:147-181` (`buildSkillMounts`) generates the eight v84 mount rows for **four** job
prefixes — `0`, `1000`, `2000` and **`2001` (Evan)** — so the table already claims
`20011025`, `20011027`–`20011030` and `20011037`–`20011039` are mounts. Ticket 05 wrote those rows
from `add-list/Skill.txt`'s naming, and **nothing corroborates them at node level**:
`Skill.wz/2001.img` is this ticket's and is unmerged, so `SkillFactory` never resolves those ids
and the rows are inert.

**If 13 merges a `2001.img` in which `20011025` (or any of the other eight) is a real Evan skill,
it silently becomes a mount** — casting it applies `MONSTER_RIDING` and draws a wooden pony.
There is no error and no test failure; the map lookup just starts hitting.

**So: before merging `2001.img`, dump those nine ids from it.** If any is a genuine Evan skill,
delete `2001` from the job-prefix loop in `buildSkillMounts` in the same commit. If v84 really does
ship Evan copies of the mount skills at those ids, the rows are correct and this note can be
deleted. Related and separate: `isMonsterRidingSkill`'s `sourceid % 10000000 == 1004` does not
match `Evan.MONSTER_RIDER` (`20011004 % 10000000 = 11004`) — pre-existing, also 13's.

## Acceptance criteria

- [ ] Evan maps merged into client WZ and server XML, and reachable
- [ ] Evan NPCs present and interactive
- [ ] Evan quest data merged; the 13 existing scripts now fire
- [ ] The intro chain can be played from the first quest through job advancement
- [ ] The six previously-missing maps referenced by existing scripts now resolve
