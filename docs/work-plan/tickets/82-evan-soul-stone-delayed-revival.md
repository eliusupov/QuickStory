# 82 - Evan Soul Stone: delayed, limited party revival

**Class:** v84 parity
**Slice:** `docs/work-plan/V84-REMAINING-SPEC.md` — Evan skill semantics
**Blocked by:** None.
**Startable now:** YES.
**Implementation agent:** `gp-opus-high`.
**Review agent:** `gp-opus-high`.

## Problem

Evan Soul Stone (`22181003`) was made to use the immediate-resurrection path in commit
`c9d0ca824`. That fixed its original no-op, but the path revives already-dead party members as soon
as it is cast. The skill's v84 data describes a timed safeguard for later deaths, with a limited
number of uses. Immediate Resurrection is therefore the wrong effect shape.

## What to build

Implement Soul Stone as its own delayed party-revival effect.

1. On cast, target only eligible **living** party members inside the v84 `lt`/`rb` range, using the
   party-targeting shape already used by Blessing of the Onyx. Do not revive anybody at cast time.
2. Keep the protection for the duration in `2218.img/skill/22181003/level/<level>/time`.
3. When a protected member dies during that interval, revive them at the percentage in that level's
   `x`, consume one of the uses in that level's `y`, and remove the protection when its uses are
   exhausted or its timer expires. A member who was dead when the skill was cast is not retroactively
   eligible.
4. Before editing, re-read the authoritative v84 `Skill.wz` node and `String.wz/Skill.img/22181003`
   text. At level 20 the existing record reports `x=50`, `y=2`, and a 300-second duration. If either
   the data or client text contradicts the delayed-and-limited semantics above, stop and report the
   contradiction rather than choose a different meaning.

## Precedent

- **Commit `c9d0ca824`** is the mandatory negative precedent: it correctly identified Soul Stone as
  no longer a generic buff but incorrectly reused `isResurrection()`'s immediate-death target path.
  Replace only that Evan behaviour; Bishop/GM Resurrection remains an immediate cast.
- **Commit `09407afcc`** (Blessing of the Onyx) is the party-range precedent. Reuse its existing
  party eligibility/range traversal rather than introducing a second party query.
- The ordinary resurrection implementation is the revival/HP-restoration precedent after a Soul
  Stone protection has actually triggered. Do not route Soul Stone's cast through it.

## Acceptance criteria

- [ ] A level-20 cast of `22181003` does not revive a dead party member, even when that member is
      inside the cast range.
- [ ] A living, eligible party member protected by the cast and killed before expiry revives once at
      **50%** HP; an unprotected member and an out-of-range member do not.
- [ ] The test exercises the two-use level-20 value: exactly two eligible later deaths consume the
      effect; a third does not revive through Soul Stone. The effect also expires after **300 seconds**
      without reviving a later death.
- [ ] The implementation reads the selected level's `x`, `y`, and `time`; it does not hard-code
      50, 2, or 300 seconds.
- [ ] Existing Bishop and GM Resurrection still revives an already-dead target immediately, at its
      existing restoration amount. Soul Stone is the only changed skill id.
- [ ] A regression test loads the real `2218.img` data and proves the cast-time, delayed-death,
      exhausted-use, and expiry cases above. It must fail if `22181003` is put back in the generic
      immediate-resurrection condition.
- [ ] The implementation agent does **not** run Maven while the shared `target/` directory may be
      owned by another agent. The orchestrator assigns the isolated test run; the reviewer reports
      the exact command and result.

## Do not

- Do not alter WZ/client files, packet layouts, database rows, cooldown, MP cost, `x`, `y`, `time`,
  the party range, or any other Evan skill.
- Do not revive a player who was already dead at cast time, revive more than `y` members, make the
  protection permanent, or invent a random-target rule that the v84 data/text does not state.
- Do not implement Magic Resistance, Dragon Fury, Magic Mastery's mastery half, Critical Magic,
  Meteo Shower, Recovery Aura, or the three Weakness skills in this ticket. Their missing values or
  server responsibility are not established by authoritative v84 data.
