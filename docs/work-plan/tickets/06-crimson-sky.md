# 06 — Crimson Sky playable

**Blocked by:** 03

**Status:** ready-for-agent

## What to build

The Crimson Sky area is reachable, populated, and rewarding: you can travel there, fight the dragons, and get drops.

This is the largest genuine content win in v84 — a Leafre/Dragon-Nest expansion. Maps `240080000`–`240080800` (Crimson Sky Dock, Crimson Sky 1–5, Crimson Sky Edge, Nest Entrance, Crimson Sky Nest, Cave of the Deceased, Resurrection Site) plus `683010000` Dragon's Nest. Mobs `9500374`–`9500382`: Green Cornian, Dark Cornian, Jr. Newtie, Nest Golem, Blue/Red Dragon Turtle, **Skelegon, Skelosaurus, Leviathan**. NPCs Matada, Crimson Sky Doorway, Dragon Rider, Giant Twin Dragon's Egg.

**Drops are part of this ticket, not a follow-up.** Mob spawns arrive free inside the map `life` nodes, but drop tables live in Cosmic's database (`db/data/152-drop-data.sql`, currently 22,161 rows) and are not in WZ at all. Skelegon and Leviathan will be decorative scenery until those rows exist. An area you can reach but not profit from is not a delivered slice.

## Acceptance criteria

- [ ] All Crimson Sky maps present in client WZ and server XML, and reachable in game
- [ ] All nine mobs spawn at correct rates and are killable
- [ ] NPCs present and interactive
- [ ] Drop tables added so every new mob drops something appropriate
- [ ] Travel route into the area works from existing content
