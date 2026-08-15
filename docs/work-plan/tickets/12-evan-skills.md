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

## Acceptance criteria

- [ ] 16 missing skill constants added
- [ ] Five new BuffStat masks defined and applied by their skills
- [ ] Both breath skills deal damage correctly through the charge path
- [ ] Recovery Aura and Blessing of the Onyx affect party members correctly
- [ ] The five reuse-existing-stat skills apply their buffs
- [ ] All 29 data-only skills verified working without code changes
- [ ] Existing classes unaffected — no BuffStat mask regression
