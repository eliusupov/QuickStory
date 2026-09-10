# Tulcus v84 100% scroll completion

## Problem

Tulcus's scroll shop still lacks 82 ordinary or event scrolls whose v84 data gives them a 100%
success rate. Four event scrolls in that missing set also fail the server's ordinary
equipment-family gate, despite carrying exact equipment requirements and a working random-stat
effect.

Twenty-nine other 100% items are GM-strength scrolls: they carry 10%-scroll stats and resell for
750,000 mesos. They do not belong in this player shop. Two were previously queued and must be
removed before the shop work reaches the live database.

## Solution

Add all 82 missing non-GM v84 100% scrolls to Tulcus at the shop's established 250,000-meso 100%
tier. Remove the two GM-strength rows queued by the earlier migration. Keep every equipment
restriction from the v84 data.

Restore the owner's catalogue order after adding the stock: STR, DEX, INT, LUK, ATT, M.ATT, then
the remaining purposes. Preserve every existing physical row, including intentional duplicates.

## User stories

- As the owner, I can buy every non-GM scroll that v84 identifies as 100% from Tulcus.
- As a player, I cannot buy GM-strength scrolls from a normal shop.
- As a player, restricted event scrolls work only on the equipment listed in their v84 data.
- As a shopper, I see the expanded catalogue in the same purpose-first order as before.

## Decisions

- The pristine v84 data is authoritative: 156 total 100% scrolls, including exactly 29
  GM-strength scrolls.
- Exclude all 29 GM-strength ids. Add the 82 missing non-GM ids, including restricted and event
  scrolls, at 250,000 mesos.
- Remove the two GM-strength ids queued by the earlier shop migration.
- Preserve exact v84 equipment requirement lists. Only four newly sold random-stat event scrolls
  need a compatibility correction.
- Use new additive migrations. Do not edit the already committed shop migrations.

## Out of scope

- Scroll drop rates, MapleMap drop multipliers, WZ edits, new scroll effects, or new shop commands.
- Adding any scroll whose v84 success value is not 100.
- Adding or repricing any of the 29 GM-strength scrolls.
