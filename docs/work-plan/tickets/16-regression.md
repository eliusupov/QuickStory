# 16 — Regression — custom content and existing systems intact

**Blocked by:** 04, 05, 06, 07, 08, 09, 15

**Status:** ready-for-agent

## What to build

Proof that the upgrade added everything intended and broke nothing that already worked.

The specific risk this ticket exists to catch: your client carries roughly 24.6 MB of content present in neither stock v83 nor stock v84 — Ezorsia's HD work and your own. Every WZ merge along the way was an opportunity to silently delete some of it. The protect-list from ticket 02 is the checklist.

Beyond custom content, the systems most exposed to a WZ merge are the ones whose files were touched wholesale: `String.wz` (name tables for every category), `Quest.wz` (2,818 existing quests) and `Character.wz` (every equip and hairstyle in the game).

Also confirm your own server changes survived: exp and drop rates in `config.yaml`, boss spawn work, and the balance changes in the recent commit history.

## Acceptance criteria

- [ ] Every node on the ticket-02 protect list still present
- [ ] Existing quests still accept and complete
- [ ] Existing drops, shops and spawn rates unchanged
- [ ] Existing hairstyles and equips still render
- [ ] Your own server changes (rates, boss spawns, balance) still in effect
- [ ] All four classes plus Evan playable from level 1
- [ ] Final content count reconciled against the add-list from ticket 02
