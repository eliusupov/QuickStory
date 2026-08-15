# 15 — Evan creation path

**Blocked by:** 14

**Status:** ready-for-agent

## What to build

A player can create an Evan from scratch, rather than being job-changed into one by a GM.

Two routes, and the cheap one is legitimate. `CreateCharHandler.java` currently handles only job 0 (Cygnus), 1 (Adventurer) and 2 (Aran); adding `case 3` plus an `EvanCreator` (modelled on `LegendCreator`, starting job 2001 at map 100030000) is the server half. But the creation **UI** also needs `Etc.wz/MakeCharInfo.img` — which in v83 has `CharMale/Female`, `OrientChar*` and `PremiumChar*` but no Evan block — and a client edit so the client sends job 3.

If the client-side work proves expensive, a job-change NPC is an acceptable shipped answer. Decide deliberately and record which was chosen; do not leave both half-built.

## Acceptance criteria

- [ ] A new character can become an Evan without GM intervention
- [ ] Starting job, map, inventory and skills are correct
- [ ] The character persists correctly across relog
- [ ] Existing creation flows for Adventurer, Cygnus and Aran still work
- [ ] The chosen route (creation UI vs job-change NPC) is recorded with its reasoning
