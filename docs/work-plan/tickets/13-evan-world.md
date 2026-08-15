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

## Acceptance criteria

- [ ] Evan maps merged into client WZ and server XML, and reachable
- [ ] Evan NPCs present and interactive
- [ ] Evan quest data merged; the 13 existing scripts now fire
- [ ] The intro chain can be played from the first quest through job advancement
- [ ] The six previously-missing maps referenced by existing scripts now resolve
