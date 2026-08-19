# 77 - HD S4: first-launch bring-up walkthrough

**Class:** owner-requested-hd
**Slice:** S4 of `docs/work-plan/HD-CLIENT-V84-SPEC.md`
**Blocked by:** 74, 75, 76 - the patch set, the UI drift values and the UI-archive mount must all be
closed offline before the first launch is worth the owner's time.
**Startable now:** NO.
**Human gate:** a client launch. Agents may not launch a client (CLAUDE.md), so this ticket cannot be
an agent - it is the owner's to run, screen by screen.

Boot the assembled HD client on v84 and walk every screen end to end at 1280x720, fixing each screen
before moving to the next. This is the first time the whole stack - relocated patch table, loader
DLL, `EzorsiaV2_UI.wz`, the folded drift values - runs against a live v84 session. Everything before
this was proven offline; this is where it meets the client.

**Owner-requested HD row, not a v84-parity gap.** Do not relabel.

## The walkthrough, in the order a session hits them

1. **Login** - the login screen draws at 1280x720; `ccLoginDescriptorFix` (S1) behaves.
2. **Character select** - the character list renders, no clipped or misplaced elements.
3. **Character creation** - the creation UI is complete and correctly placed.
4. **In-game** - the field renders at widescreen with no leftover v83 offset.
5. **Status bar** - HP/MP/EXP bar correct (this is the `AdjustStatusBarBG`-class area S1 touched).
6. **Chat** - the chat window and input draw and scroll correctly.
7. **Inventory / equip** - the item windows open, draw, and are usable.
8. **Cash shop** - opens and renders.
   **FAILED 2026-08-19:** black screen, then client crash. Routed to ticket 74: unsafe shipped
   P194 `CashShopFixOnOff` cave (`call 0`), not a server error.
9. **Boss HP bar** - draws correctly when a boss is present.
10. **Super-megaphone (smega)** - the smega banner renders at widescreen.
11. **Mu Lung Dojo** - the Dojo UI draws correctly.

## New v84 UI screens to eyeball at 1280x720

These are v84-new UI/maps absent from the v83 HD client, so they were never viewport-tuned. Each is
a candidate for a hand-made `EzorsiaV2_UI.wz` nudge if it looks wrong at widescreen (see
`docs/work-plan/HD-ASSET-METHOD-FINDINGS.md`):

- **Evan RaceSelect (Login BtEvan)** - the class-select splash.
- **DragonEquip** - the Evan dragon-equip window.
- **New MobGage bars** - the v84 mob HP gauges.
- **Slumbering Dragon Island** maps (`914100xxx`) - new-map viewports.
- **Neo City** maps - new-map viewports.
- **dragonRoad / dragonDream** maps - Evan tutorial viewports.

For each screen: launch, observe, and if it is wrong, route the fix - a patch op or a drift value
back to S1/S2, a UI-archive issue back to S3. Fixes to the harness/loader are agent work dispatched
from here; the launch and the visual judgement are the owner's.

## Precedent

The bring-up shape is the one the spec lays out; tickets 30 / 30b assembled the client this walks.
There is no server precedent because HD touches no server path.

## Acceptance criteria

- [ ] Each of the eleven screens above renders correctly at 1280x720 on the v84 HD client, with no
      clipped, misplaced, or missing element and no leftover v83 patch relocating a wrong address.
- [ ] Any defect found is fixed at its real layer (patch op -> S1, drift value -> S2, UI archive
      -> S3) and re-launched, not worked around.
- [ ] No server change is made or needed at any screen (spec, no-op finding). If a screen looks like
      it needs one, the diagnosis is wrong - it is a client-stack issue.
- [ ] The owner records the walkthrough result per screen.

## Do not

- Do not dispatch this to an agent. It requires a client launch, which agents may not do.
- Do not launch against the shared root client - use the assembled/copied HD directory.
- Do not open a server ticket for any screen. HD is a no-op on the wire.
- Do not relabel as parity.
