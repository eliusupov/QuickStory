# 16 — Regression — custom content and existing systems intact

**Blocked by:** 04, 05, 06, 07, 08, 09, 15

**Status:** partial — everything except the Evan criterion is done; that one is not coverable

## What to build

Proof that the upgrade added everything intended and broke nothing that already worked.

The specific risk this ticket exists to catch: your client carries roughly 24.6 MB of content present
in neither stock v83 nor stock v84 — Ezorsia's HD work and your own. Every WZ merge along the way was
an opportunity to silently delete some of it. The protect-list from ticket 02 is the checklist.

Beyond custom content, the systems most exposed to a WZ merge are the ones whose files were touched
wholesale: `String.wz` (name tables for every category), `Quest.wz` (2,818 existing quests) and
`Character.wz` (every equip and hairstyle in the game).

Also confirm your own server changes survived: exp and drop rates in `config.yaml`, boss spawn work,
and the balance changes in the recent commit history.

## Acceptance criteria

- [x] Every node on the ticket-02 protect list still present — **and unchanged**. Digest sweep over
      the composed install (`Server\wz-merge\03h\`) vs the hash-verified `pre\` snapshot: **0 nodes
      removed and 0 leaf values changed anywhere outside `String.wz`**; inside `String.wz`, 80
      changed leaves and 18 removed `desc` nodes, all on the 52 ids the 41-root `FORCE.txt` reaches,
      every removed node holding the literal `MISSING INFO`. **None of the 98 is on
      `protect-list/String.txt`.**
- [x] Existing quests still accept and complete *(as far as data shows)* — server XML and the binary
      `Quest.wz` merge independently agree: `removed=0, changed=0`, additions confined to 63 new
      ids, top-level counts `2,818 → 2,881`. The 108 `lvmax` rows and 15 date rows did not land and
      are all on `COLLISION-DENY.txt`.
- [x] Existing drops, shops and spawn rates unchanged — `152-drop-data.sql` byte-untouched; 153/154
      are `INSERT`-only and their 20 dropperids are disjoint from 152's 1,004; `Etc.wz` unmerged so
      the cash shop is untouched; **zero `life` nodes added to any pre-existing map**.
- [x] Existing hairstyles and equips still render *(as far as data shows)* — `Eqp.img/Eqp/Hair`
      1,518 → 1,558 names, **0 changed, 0 removed**; no pre-existing `Character.wz/Hair` image
      touched; the 9 hair and 6 medal/dragon refusals held. **One regression found: 2 of the 6
      `Glove/01082262` rows 03g meant to refuse got past the gate — see REGRESSION.md §5.1.**
- [x] Your own server changes (rates, boss spawns, balance) still in effect — `config.yaml`,
      `GameConstants.java` and every boss/spawn file are byte-identical to `94e66d80c`; the 3
      `wz/` files that diverge from `upstream/master` still diverge the same way, with the quest
      rebalance and the three retimed coupons intact.
- [ ] **All four classes plus Evan playable from level 1 — NOT COVERABLE.** Evan does not exist yet:
      tickets 10–15 are hard-blocked on ticket 01's human client-launch test, and no Evan content is
      in the composed install. Nothing was installed and the client was never launched, so no
      playability claim of any kind is made here. Staged as human steps 1–6 in REGRESSION.md §10.
- [x] Final content count reconciled against the add-list from ticket 02 — 1,662 composed rows =
      1,639 merged + 23 refused, exact; 16,113 offered accounted for file by file.

## Evidence

`docs/wz-baseline/REGRESSION.md` — method, per-criterion evidence, the reconciliation table, the
two findings, and the staged human steps.

`src/test/java/server/V84RegressionTest.java` — 3 checks, each negative-controlled. Suite **1,994**.

## Findings

1. **Two `Character.wz/Glove/01082262` rows got past the positional-array gate** (§5.1). The rule
   fires only on children that are exactly `0..c-1`; `swingT2` is `{1,2}` and `swingO3` is `{1}`, so
   both passed and v84 `rGlove` layers now sit alongside Ezorsia art in the same frames — the exact
   hazard the six sibling refusals exist to prevent. Cosmetic, one equip, four frames. The rule
   needs one more clause. Not fixed here: `tool-merge/Program.cs` and `WZ-MERGE-PROCEDURE.md` are
   owned by another agent in flight.
2. **`WzMerge hash` stack-overflows on `Reactor.wz`** (§8) — 6 images, symmetric across pre and post.
   The project's protect-verification instrument fails on a whole file in a way that reads as a
   merge fault. Covered here by a depth-12 dump fallback; `Canon()` should be depth-bounded.
3. **389 `Map.wz` add-list rows that write into maps the live client already has were never
   triaged** (§7). Harmless today because they are on no merge list, but "unclaimed" is not
   "refused", and 08 examined only the 18 inside its own areas.
4. Six of the 41 forced `String.wz` names take untranslated Korean over `MISSING NAME`.
   `COLLISION-FORCE.txt` discloses this, but unlike 08's Korean NPC names it was never surfaced to
   the owner as reversible.

## Human steps — staged, not performed

REGRESSION.md §10. Six checks, each with a pass and a fail signature, and one rollback for all six.
Check 3 is the one that would confirm or clear finding 1.
