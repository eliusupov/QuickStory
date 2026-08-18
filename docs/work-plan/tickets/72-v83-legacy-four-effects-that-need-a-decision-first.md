# 72 - four effect rows that need a decision before any code (three v83 legacy, one v84 parity)

**Class:** MIXED - see the per-row table below. **R29 is v84 parity**; R25, R26, R27 are v83 legacy.
**Work rows:** R25, R26, R27, R29 - `docs/work-plan/V84-WORK-ROWS.tsv`
**Blocked by:** OWNER decision. R25/R26/R27 are outside the standing "v84 parity only" scope. **R29
is inside it** and does not need a scope waiver.

An earlier revision of this ticket asserted that "every one of them has zero `add-list` rows - v84
added nothing to any of these items or skills". **That was wrong on two of the four rows**, and the
correction is the most important thing in this file:

| Row | `add-list` rows | Class |
|---|---|---|
| R25 potions 2022359-2022365 | **zero** - no line for any of the seven ids in `add-list/Item.txt` | v83 legacy |
| R26 BFSkill items | **eight** - `add-list/Item.txt:145-152` | v83 legacy (see caveat in the row) |
| R27 Shadow Web DoT | **zero** (no `4111003`/`14111001` in `add-list/Skill.txt`) | v83 legacy |
| **R29 chairs** | **five** - `add-list/Item.txt:372-376`, four of them whole-new v84 chairs | **v84 PARITY** |

They are grouped because none of them is a mechanical read-and-apply: each needs either new plumbing
that does not exist, data this project does not have, or a balance call the owner has not made. Two
of the four also correct a wrong statement in the old tracker, and those corrections are the part
worth keeping even if no code is ever written.

## R25 - Seven potions grant only their flat half

Items **2022359** through **2022365** each carry **both** a flat node and a `*Rate` node - 2022359
has `pad=10` **and** `padRate=10`. `StatEffect.java:375-380` reads the six flat combat stats
(`pad`, `pdd`, `mad`, `mdd`, `acc`, `eva`), with `speed` at `:382` and `jump` at `:383`; **no line in
the method reads any `*Rate` key**, so the percentage half is dropped. (The old `375-382` citation
clipped `jump` and implied the range was the whole story.)

This corrects the old tracker, which said these "grant zero stats". They do not; the flat half is
read and applied. The gap is the percentage, and it is not a mechanical read-add: **no `*Rate` field
exists on `StatEffect`** and no percent-buff plumbing exists at all, so closing it means new
`BuffStat` percent handling in `Character.reapplyLocalStats`.

## R26 - Six BFSkill items are wholly unwired

Items **2022539**, **2022542**, **2022543**, **2022547**, **2022548**, **2022549**. There are zero
`BFSkill` references in `src/`, `tools/` or `scripts/`. The `spec/BFSkill` values are **0, 1, 2, 3,
4, 5**.

These items **do** carry `add-list` rows - `add-list/Item.txt:145-152`, eight lines, all of the form
`Item.wz/Consume/0202.img/020225NN/spec/consumeOnPickup`. Two caveats the old text elided:

* Those eight lines cover **2022539, 2022540, 2022541, 2022542, 2022543, 2022547, 2022548, 2022549**.
  **2022540 and 2022541 are not BFSkill items** and are not part of this row - do not carry them
  along.
* What is v84-new is only the `spec/consumeOnPickup` leaf, and it is **already present**. The
  `BFSkill` node itself, which is the whole of this defect, is v83. So the row stays v83 legacy - but
  say it that way, not as "zero `add-list` rows".

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

## R29 - All chairs heal identically - **and this one IS v84 parity**

**56 `recoveryHP`** and **35 `recoveryMP`** nodes, all in `wz/Item.wz/Install/0301.img.xml`, with
zero references in `src/main/java`. Chair regen is derived solely from the player's own max HP/MP.

**The old tracker's "several v84-new" was RIGHT, and an earlier revision of this ticket wrongly
"corrected" it to "zero `add-list` rows".** `add-list/Item.txt:372-376` carries five `Install/0301`
rows, and four of them are **whole-item adds carrying the very nodes this row is about**:

| add-list line | item | v84-new | recovery nodes |
|---|---|---|---|
| 372 | 03010069 | leaf only (`effect2`) | `recoveryHP=50`, `recoveryMP=30` (v83) |
| 373 | **03010097** | whole item | `recoveryHP=50` |
| 374 | **03010107** | whole item | `recoveryHP=40`, `recoveryMP=20` |
| 375 | **03010108** | whole item | `recoveryHP=40`, `recoveryMP=20` |
| 376 | **03010120** | whole item | `recoveryHP=40`, `recoveryMP=30` |

v84 shipped four new chairs whose only differentiating data is a heal rate this server never reads.
That makes R29 a **v84 parity gap**, in scope, and not something needing a scope waiver.

**`V84-WORK-ROWS.tsv` row R29 still reads "OUT - not v84" and is wrong.** That file is owned by the
orchestrator, not by this ticket, so the reclassification is recorded here and the owner must apply
it there.

### The reader, and why the id cannot reach it

`Character.getChairTaskIntervalRate(int maxhp, int maxmp)` is declared at `Character.java:2442` and
has **exactly one call site**, `:2490`, inside `updateChairHealStats()` (declared `:2477`), which is
itself called at `:2509` and `:2524`. **The item id is never a parameter** anywhere on that path, so
no chair can differ from any other without changing the signature at `:2442`, its one caller at
`:2490`, and threading the id down from whichever of `:2509` / `:2524` is on the sit path.

(The old "used at `:2477` and `:2490` and again at `:2500`, `:2509-2510` and `:2525-2526`" was wrong:
`:2477` is a declaration not a call, `:2500` is blank, and `:2525-2526` are the `healHP`/`healMP`
reads. The load-bearing claim - the id is never a parameter - survives intact.)

## Precedent

**R25.** UNKNOWN. No percent-buff precedent exists on `StatEffect`; the plumbing would be new.

**R26.** UNKNOWN on both halves - the client-side battlefield skill table is not in this tree, and
no temporary skill grant exists to copy.

**R27.** Not a missing precedent but a settled decision: commits **5f1abf3fb** and **94425ba61** are
the record that the removal was intentional. The number `getMaxHp()/50.0` is a hardcoded constant,
not recovered data.

**R29.** The reader signature at `Character.java:2442` is the precedent for what would have to
change: it takes max HP and max MP and nothing else. One declaration, one call site (`:2490`) - the
diff is small; the design question of where the item id enters is the whole cost.

## Acceptance criteria

None of these is implementable as written. The deliverable for this ticket is the decision plus the
corrections, and each of the following is objectively checkable:

- [ ] The owner records a yes/no per row, in writing, in `V84-OPEN-ITEMS.md`.
- [ ] The tracker prose no longer says potions 2022359-2022365 "grant zero stats"; it states that
      the flat node is read and the `*Rate` node is not.
- [ ] The tracker prose no longer attributes Shadow Web's DoT to Blaze Wizard; it names Hermit
      4111003 and Night Walker 14111001 and cites 5f1abf3fb as a deliberate removal.
- [ ] The tracker records R29 as **v84 parity, in scope**, citing the four whole-new v84 chairs at
      `add-list/Item.txt:373-376` (03010097, 03010107, 03010108, 03010120) and their `recoveryHP` /
      `recoveryMP` nodes. Any prose claiming `Install/0301` has zero `add-list` rows is deleted.
- [ ] The tracker no longer claims all four rows in this ticket have zero `add-list` rows. Only R25
      and R27 do; R26 has eight (`add-list/Item.txt:145-152`) and R29 has five (`:372-376`).
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
- Do not present R25, R26 or R27 as v84 parity work. **Do present R29 as v84 parity work** - it has
  five `add-list` rows and four v84-new chairs. Getting this backwards is the exact error this
  revision fixes.
- Do not write "zero `add-list` rows" about R26 or R29. Check `add-list/Item.txt` before repeating any
  add-list count from this ticket.
- Do not start R25 by adding a single `padRate` special case. The row needs percent handling in
  `reapplyLocalStats` or it needs nothing.
