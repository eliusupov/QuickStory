# 72 - v83 legacy: four effect rows that need a decision before any code

**Class:** v83 legacy - NOT a v84 parity gap
**Work rows:** R25, R26, R27, R29 - `docs/work-plan/V84-WORK-ROWS.tsv`
**Blocked by:** OWNER decision - these are outside the standing "v84 parity only" scope

All four rows here are **v83 legacy defects, not v84 parity gaps**, and every one of them has zero
`add-list` rows - v84 added nothing to any of these items or skills. They are grouped because none
of them is a mechanical read-and-apply: each needs either new plumbing that does not exist, data
this project does not have, or a balance call the owner has not made. Three of the four also correct
a wrong statement in the old tracker, and those corrections are the part worth keeping even if no
code is ever written.

## R25 - Seven potions grant only their flat half

Items **2022359** through **2022365** each carry **both** a flat node and a `*Rate` node - 2022359
has `pad=10` **and** `padRate=10`. `StatEffect.java:375-382` reads the flat ones only, so the
percentage half is dropped.

This corrects the old tracker, which said these "grant zero stats". They do not; the flat half is
read and applied. The gap is the percentage, and it is not a mechanical read-add: **no `*Rate` field
exists on `StatEffect`** and no percent-buff plumbing exists at all, so closing it means new
`BuffStat` percent handling in `Character.reapplyLocalStats`.

## R26 - Six BFSkill items are wholly unwired

Items **2022539**, **2022542**, **2022543**, **2022547**, **2022548**, **2022549**. There are zero
`BFSkill` references in `src/`, `tools/` or `scripts/`. The `spec/BFSkill` values are **0, 1, 2, 3,
4, 5**.

Only `spec/consumeOnPickup` is v84-new on these items (`add-list/Item.txt:145-152`) and it is
already present. The items themselves are v83.

The blocking fact: the `BFSkill` value is an **index 0-5 into a client-side battlefield skill table
we do not have**, not a skill id. And there is no handler for a temporary skill grant anywhere in
this codebase - `SkillBookHandler.useSkillBook` grants permanently. Wiring these would be a large
invention on both halves.

## R27 - Shadow Web's damage-over-time is commented-out dead code, on purpose

`Monster.java:1328-1336`, routed by `Hermit.SHADOW_WEB` **4111003** and `NightWalker.SHADOW_WEB`
**14111001** at `:1329`. The old tracker's "Blaze Wizard" attribution is wrong - Blaze Wizard
appears at `StatEffect.java:1599-1600` and only for SLOW/SEAL. The bind itself works fine
(`StatEffect.java:896-898`); it is only the DoT that is dead.

It was removed deliberately upstream. `git log -L 1326,1336` shows commit **5f1abf3fb** ("HikariCP
config + MaxHP/MP & EXP overhaul") wrapping it in `/* */` in the same commit as the HP rework that
would have made a 2%-of-max-HP DoT abusive, and a later commit **94425ba61** migrated the dead code
to the new `overtimeAction` API without un-commenting it. The value is hardcoded `getMaxHp()/50.0`
and appears in no Skill.wz node, so there is no data to consult.

Reclassified: this is **not dead code to restore**, it is a **balance decision**. Do not un-comment
it.

## R29 - All chairs heal identically

**56 `recoveryHP`** and **35 `recoveryMP`** nodes, all in `wz/Item.wz/Install/0301.img.xml`, with
zero references in `src/main/java`. Chair regen is derived solely from the player's own max HP/MP:
`Character.getChairTaskIntervalRate(int maxhp, int maxmp)` at `Character.java:2442`, used at `:2477`
and `:2490` and again at `:2500`, `:2509-2510` and `:2525-2526`. **The item id is never a
parameter**, so no chair can differ from any other without changing that signature and all five call
sites.

This corrects the old tracker's "several v84-new": `Install/0301` has **zero** `add-list` rows.

## Precedent

**R25.** UNKNOWN. No percent-buff precedent exists on `StatEffect`; the plumbing would be new.

**R26.** UNKNOWN on both halves - the client-side battlefield skill table is not in this tree, and
no temporary skill grant exists to copy.

**R27.** Not a missing precedent but a settled decision: commits **5f1abf3fb** and **94425ba61** are
the record that the removal was intentional. The number `getMaxHp()/50.0` is a hardcoded constant,
not recovered data.

**R29.** The reader signature at `Character.java:2442` is the precedent for what would have to
change: it takes max HP and max MP and nothing else.

## Acceptance criteria

None of these is implementable as written. The deliverable for this ticket is the decision plus the
corrections, and each of the following is objectively checkable:

- [ ] The owner records a yes/no per row, in writing, in `V84-OPEN-ITEMS.md`.
- [ ] The tracker prose no longer says potions 2022359-2022365 "grant zero stats"; it states that
      the flat node is read and the `*Rate` node is not.
- [ ] The tracker prose no longer attributes Shadow Web's DoT to Blaze Wizard; it names Hermit
      4111003 and Night Walker 14111001 and cites 5f1abf3fb as a deliberate removal.
- [ ] The tracker prose no longer calls any `Install/0301` chair node v84-new; it states zero
      `add-list` rows.
- [ ] `Monster.java:1328-1336` is still commented out at the end of this ticket.
- [ ] If R25 is approved: a character drinking 2022359 has `localpad` raised by both `pad=10` and
      `padRate=10`, asserted by a test against `reapplyLocalStats`.
- [ ] If R29 is approved: two different chairs with different `recoveryHP` values produce measurably
      different heal amounts on the same character, asserted by a test.

Run any new test class with `-Dtest=<Name>`. **Do not run maven while sibling agents are active** -
they collide on `target/`.

## Do not

- Do not un-comment `Monster.java:1328-1336`. Its removal is upstream intent, in the same commit as
  the HP overhaul that made it abusive.
- Do not map `spec/BFSkill` values 0-5 onto skill ids. They are indices into a table this project
  does not have; any mapping would be invented.
- Do not present any of these four as v84 parity work. All four have zero `add-list` rows.
- Do not start R25 by adding a single `padRate` special case. The row needs percent handling in
  `reapplyLocalStats` or it needs nothing.
