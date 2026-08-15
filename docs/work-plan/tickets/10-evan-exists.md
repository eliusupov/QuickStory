# 10 — Evan exists, renders, and has a dragon

**Blocked by:** 01, 03

**Status:** ready-for-agent

## What to build

A character can become an Evan, renders correctly, and has a visible dragon that follows and moves with them.

This is the Evan tracer bullet — the narrowest complete path through the class. Job-change in with a GM command; creation flow is ticket 15.

Most of the server side already exists. Cosmic inherited MapleSolaxia's Evan work: `server/maps/Dragon.java`, the dragon spawn/move/remove packets, opcodes `0xB5`/`0xB6`/`0xB7`, `MoveDragonHandler` registered in `PacketProcessor`, extended-SP encoding, and the `sp VARCHAR(128)` column. **The database schema needs no changes.** The gap is WZ data and the client patch, not Java.

The WZ side is largely pre-extracted at `porting-resources/evan-xml/extracted/Evan WZ/` — Skill `2001` plus the ten job files, the dragon animation directory, 20 dragon equips, 15 body imgs, and String replacements. Take `SkillEx`/`SkillMacroEx` from the **v84 UI.wz**, not from that pack — the pack's `UIWindow.img` is a Big Bang dump and its own author says so.

Your v83 tree already has dragon equips `0194–0197 × 2000–2002` with full stats and all 12 names in `String.wz/Eqp.img`; the pack adds the 2003/2004 tiers.

## Acceptance criteria

- [ ] Evan skill and dragon WZ data merged into client WZ and server XML
- [ ] Job-change to 2001 and to 2200 succeeds
- [ ] Character renders correctly as an Evan
- [ ] Dragon spawns, follows, and moves; other players see it
- [ ] Dragon despawns correctly on job change, map change and logout
- [ ] Skill window opens without crashing
