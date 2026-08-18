# 71 - v83 legacy: the two effect defects whose fix is four lines

**Class:** v83 legacy - NOT a v84 parity gap
**Work rows:** R24, R28 - `docs/work-plan/V84-WORK-ROWS.tsv`
**Blocked by:** None - can start immediately

Both rows in this ticket are **v83 legacy defects, not v84 parity gaps**: neither item nor buff has
a single `add-list` row, so v84 added nothing to either and closing them is outside the standing
"v84 parity only" scope. They are ticketed anyway because the owner asked for them to be, and
because in both cases the fix is known, small and already written down. One touches chaos scrolls,
the other touches Echo of Hero; they are grouped because both are stat-application defects in the
same two files.

## R24 - Five chaos scrolls burn the upgrade slot and apply nothing

Items **2049103**, **2049104**, **2049112**, **2049113**, **2049114** all carry `info/randstat=1`.
`ItemInformationProvider.java:1170-1172` special-cases only **2049100** (`CHAOS_SCROll_60`),
**2049101** (`LIAR_TREE_SAP`) and **2049102** (`MAPLE_SYRUP`) by id, routing them to
`scrollEquipWithChaos`. The other five fall through to `default:` at `:1176` and run
`improveEquipStats` against an empty stat map - no stat moves - while the upgrade slot is consumed at
**`:1182`** (`:1183` is that block's closing brace).

There is a **second** decrement at `:1188`, on the scroll-failure path in the `else` arm. Any change
here must leave both alone; between them they are what acceptance criterion 3 pins.

The known fix is two lines:

1. Put `"randstat"` beside `"fs"` in `getEquipStats` (`ItemInformationProvider.java:580`) so the
   flag is harvested at all.
2. Replace the three hardcoded case labels with `if (stats.get("randstat") > 0)` inside `default:`,
   which then covers all eight scrolls by data instead of three by id.

`add-list/Item.txt` has zero rows for any `2049xxx` id. Pre-existing v83 behaviour throughout.

## R28 - Echo of Hero's +4% watk never reaches local stats

The buff is written at `StatEffect.java:581-585` and delivered map-wide by `applyEchoOfHero` at
`:972`, called from `SpecialMoveHandler.java:143`. It arrives, it is held, and then nothing consults
it: `Character.reapplyLocalStats()` at `Character.java:7699` has **zero** `ECHO_OF_HERO` references,
so `localwatk` never sees the 4%.

The fix is four lines inserted after the WATK block at `Character.java:7784-7787`:

    Integer echobuff = getBuffedValue(BuffStat.ECHO_OF_HERO);
    if (echobuff != null) {
        localwatk += (localwatk * echobuff.intValue()) / 100;
    }

This is a v83 bug with no `add-list` row, but it is live for the current playthrough: Evan has
ECHO_OF_HERO as skill **20011005**.

## Precedent

**R24.** The three ids already special-cased at `ItemInformationProvider.java:1170-1172` are the
behaviour to generalise - the correct outcome for the five is exactly what 2049100/101/102 already
do. `"fs"` at `:580` is the analogue harvest line - the last `put` in `getEquipStats` - same map, same
shape, one more key.

**R28.** The WATK block immediately above the insertion point at `Character.java:7784-7787` is the
precedent - same method, same local, same percentage-application idiom. Nothing is derived; the 4%
is already in the buff value.

## Acceptance criteria

- [ ] Applying **2049103**, **2049104**, **2049112**, **2049113** or **2049114** to an equip changes
      at least one stat on that equip, asserted per id by a test that reads the stat map before and
      after.
- [ ] `getEquipStats` returns a `randstat` key for all eight `randstat=1` scrolls, and the
      `default:` branch selects on that key rather than on the three literal ids.
- [ ] The upgrade slot is consumed exactly once per scroll application, unchanged from today.
- [ ] `ItemInformationProvider.java:1146` is **untouched** in the diff.
- [ ] A character holding `BuffStat.ECHO_OF_HERO` at value 4 has `localwatk` exactly 4% higher than
      the same character without the buff, asserted by a test that calls `reapplyLocalStats` on both.
- [ ] A character with no ECHO_OF_HERO buff has an unchanged `localwatk` before and after the patch -
      the null branch is exercised.

Run the named classes with `-Dtest=<Name>`. **Do not run maven while sibling agents are active** -
they collide on `target/`.

## Do not

- Do not touch `ItemInformationProvider.java:1146`. `CHAOS_SCROll_60` is also a Vegas modifier there
  and changing that line changes a second, unrelated behaviour.
- Do not present either row as v84 parity work. Both have zero `add-list` rows and the ticket must
  keep saying so.
- Do not extend R28 to the other unread buffs in the same family. `ELEMENTAL_RESISTANCE` is declared
  three times and read nowhere; that is a separate, much larger row and it is blocked on data.
