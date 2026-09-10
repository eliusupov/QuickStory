# Tulcus scroll-shop sorting

## Problem

Tulcus carries a broad scroll catalogue, but its order follows historical additions rather than
what a player is shopping for. Related STR, DEX, INT, LUK, attack and other scrolls are scattered.

## Solution

Reorder the existing catalogue by the scroll's advertised purpose, then equipment slot, success
rate, item id and price. Change positions only: every physical row, duplicate, price and pitch stays
exactly as it is.

## User stories

- As a player, I can scan one contiguous section for each main stat and attack type.
- As a player, I find variants for the same equipment slot and success rate together.
- As the owner, I keep the exact catalogue and pricing already chosen.

## Decisions

- Purpose order is STR, DEX, INT, LUK, ATT, M.ATT, HP, MP, Accuracy, Avoidability, Speed, Jump,
  DEF, Special.
- The canonical v84 item name decides advertised purpose. A secondary stat granted by a scroll
  does not move it away from the purpose in its name.
- Ties sort by equipment slot, success rate in descending order (100, 70, 60, 30, 10, then any
  other rate), item id, then price.
- Existing duplicate rows remain separate.
- The reorder is fully reversible.

## Out of scope

- Adding or removing scrolls.
- Deduplicating rows or changing prices, pitch, success values or item behaviour.
- Changing the shop command, NPC, drop rates or WZ data.

