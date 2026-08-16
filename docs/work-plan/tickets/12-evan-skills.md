# 12 — Evan skills implemented

**Blocked by:** 11

**Status:** ready-for-agent

## What to build

Every Evan skill that survived the audit works: correct damage, correct buffs, correct duration.

Counted from the real v84 skill data, 58 Evan skills exist but only **14 are actual work**. 29 are pure data that `StatEffect.loadFromData` already handles — every nuke (Magic Missile, Fire Circle, Lightning Bolt, Magic Flare, Dragon Thrust, Killer Wings, Earthquake, Flame Wheel, Blaze, Dark Fog) and every passive. 12 are mount buffs that all reuse the existing `MONSTER_RIDING` stat. 3 are beginner-common skills already implemented for every class.

The 14:

| Work | Skills |
|---|---|
| Reuse existing stat, add a case | Magic Guard 22111001, Elemental Reset 22121001, Magic Booster 22141002, Maple Warrior 22171000, Hero's Will 22171004 |
| **New BuffStat mask** | Magic Shield 22131001, Magic Resistance 22151003, Soul Stone 22181003, Evan Slow 22141003 (distinct from existing `SLOW`), Phantom Imprint 22161002 (mob debuff) |
| Charge/keydown attack | Ice Breath 22121000, Fire Breath 22151001 — extra int in `AbstractDealDamageHandler`, same path as Big Bang |
| Bespoke | Recovery Aura 22161003 (party HP/MP aura), Blessing of the Onyx 22181000 (party buff) |

Also add the **16 missing skill constants**: `20011018 20011019 20011020 20011025 20011026 20011027 20011028 20011029 20011030 20011031 20011037 20011038 20011039 20019000 20019001 20019002`. Thirteen are named in the archived guide's `EvanJr` class; `20019000-2` are unidentified and carry damage values — identify before wiring.

Append the new BuffStat masks rather than inserting them, so existing mask ordering does not shift.

## Note from ticket 13 — the WZ half of this ticket is already done, 2026-08-16

Ticket 13's dispatch said `Skill.wz/2001.img` "was never merged server-side" and that this is why an
Evan NPEs on every attack. **Both halves of that are wrong, and 12 should not re-merge anything.**

- `wz/Skill.wz/2001.img.xml` (136 KB) and all ten Evan job images `2200`, `2210`–`2218` have been in
  this tree since commit `15f1e81fe`, **ticket 10**. `SkillFactory` resolves Evan's skills today, and
  `V84EvanNodeTest.everyEvanJobImageIsInTheServerTree` already asserts it. `2001.img/skill` carries
  `20011000`–`20011005`, `20011007`, `20011009`–`20011011`, `20011018`–`20011020`, `20011025`–`20011031`
  and `20011037`–`20011039`.
- The reported `Skill.getEffect` NPE therefore is **not** a WZ gap. `AbstractDealDamageHandler.java:656`
  dereferences `SkillFactory.getSkill(ret.skill)` with no null guard, so any id read out of the wrong
  offset of a v84 attack packet produces that exact stack. It is a packet-layout defect, same family
  as `404ec864d` (MOVE_PLAYER) and `cf9d9afa0` (mob-move counts). The one-line hardening at that call
  site belongs with the layout fix.
- **The F4 mount hazard is closed and the `2001` job prefix must stay in `buildSkillMounts`.**
  Re-verified from the v84 archive: `20011025` Charge! Wooden Pony, `20011027` Croco, `20011028` Black
  Scooter, `20011029` Pink Scooter, `20011030` Nimbus Cloud, `20011037` Unicorn, `20011038` Low Rider,
  `20011039` Red Truck — 8/8 the mounts the table pairs them with. `20011026` is Soaring (flight) and
  is correctly absent. Full table in `13-evan-world.md`.
- Still owed here, and unchanged by any of the above: the 16 missing constants (`20011018`/`19`/`31`
  are real mounts with **no sprite mapping** — `constants.skills.Evan` declares no
  `YETI_MOUNT`/`WITCH_BROOMSTICK`/`BALROG_MOUNT` and the id offsets do not transfer from Beginner or
  Legend, so it cannot be derived), the five BuffStat masks, and the two breath skills.

## Acceptance criteria

- [ ] 16 missing skill constants added
- [ ] Five new BuffStat masks defined and applied by their skills
- [ ] Both breath skills deal damage correctly through the charge path
- [ ] Recovery Aura and Blessing of the Onyx affect party members correctly
- [ ] The five reuse-existing-stat skills apply their buffs
- [ ] All 29 data-only skills verified working without code changes
- [ ] Existing classes unaffected — no BuffStat mask regression
