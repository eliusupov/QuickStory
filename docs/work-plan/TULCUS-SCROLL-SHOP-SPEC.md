# Tulcus scroll-shop completion

## Problem

Tulcus's scroll shop is missing one member of the Topwear LUK set and other ordinary
STR/DEX/INT/LUK scrolls. It also stocks none of the 30% or 70% scrolls carried by the v84 item
data. The owner wants the shop to be the convenient scroll catalogue he uses while playing.

## Solution

Add the missing ordinary main-stat scrolls, every v84 scroll whose actual success value is 30%,
and every v84 scroll whose actual success value is 70%. Existing stock stays unchanged.

Make the six v84 anniversary glove scrolls in those sets usable on gloves. The server currently
classifies their 20491 family neither as glove scrolls nor as a special compatible family, so a
shop row alone would sell six items the player cannot apply.

Prices are owner-set:

- ordinary main-stat scrolls keep Tulcus's existing price convention: 100% and 60% cost 250,000
  mesos; 10% costs 500,000 mesos;
- every 30% scroll costs 1,000,000 mesos;
- every 70% scroll costs 600,000 mesos.

## User stories

- As a player, I can buy the complete ordinary Topwear LUK set from Tulcus.
- As a player, I can buy the other missing ordinary main-stat scrolls without searching for a
  second shop.
- As a player, I can buy every 30% and 70% scroll that v84 actually contains.
- As the owner, I can restart from a packaged jar and reproduce the additions through the normal
  database migration path.

## Decisions

- "All 30%" and "all 70%" mean the success value in the pristine v84 item data, not words in an
  item's display name.
- The 30% and 70% sets are complete data-derived sets. Event, Balrog, anniversary, accessory and
  dark scrolls remain included when their v84 success value matches.
- "Main-stat" means the missing ordinary 100%/60%/10% STR, DEX, INT and LUK equipment sets already
  represented by Tulcus's catalogue. It does not sweep every item that incidentally grants a stat.
- Additions are database-only, additive, idempotent and exactly reversible. No existing shop row
  is repriced or renumbered.
- The sole code compatibility addition is for anniversary glove scrolls 2049105 through 2049110.
  Their existing effects and all existing 20491 special-item behaviour remain unchanged.
- The server's scroll-drop range already includes every requested 30% and 70% item. No drop-rate
  code change belongs in this work.

## Out of scope

- Changing any existing shop price or position.
- Adding scrolls absent from v84.
- Changing drop tables, drop-rate multipliers, scroll effects, WZ data or the shop command.
- Generalizing or reclassifying the whole 20491 family.
- Filling unrelated success tiers such as 15%, 50% or 65%.
