# Work plan — GMS v83 → v84 upgrade

16 tickets. Each is a vertical slice: reachable, populated and rewarding on its own, not a layer.

Scope: `../V84-UPGRADE-SCOPE.md` · Evidence: `../../../../porting-resources/docs/99-AUDIT-FINDINGS.md`
Content manifest: `../../../../porting-resources/docs/92-V83-V84-CONTENT-DELTA.md`

## Why these slices

The temptation is to slice by layer — "import all maps", "then all mobs", "then all drops". That
produces a long stretch where nothing is playable and the new areas are decorative. Every content
ticket here instead carries its **maps + mobs + NPCs + spawns + drops** together, so finishing one
means you can walk into the area and play it.

**No expand–contract sequencing is needed** — but not for the reason originally stated here.

> ~~v84 removed zero nodes, so every import is purely additive and nothing breaks call sites.~~
> **False. Corrected 2026-08-15 (ticket 02c).** GMS v84 **deleted 832 `Map.wz` nodes** — the entire
> Monster Carnival battlefield series (`970030100`–`970042717`), Mu Lung Dojo floors, Sheep Ranch.
> They were still gone in v92. Verified three ways: our `wz-data/v84/` is SHA256-identical to a
> fresh carve of `GMSSetupv84.exe`, so nothing is damaged; maplestory.io serves `GMS/83/map/970030100`
> ("Stage 1 <Mano>") and 404s the same id under `GMS/84` while Henesys still resolves; and the byte
> accounting agrees (−6.49 MB net while adding 2.77 MB ⇒ ~9.3 MB over 832 images ≈ 11 KB each).

The conclusion survives because **we import additions and never delete.** The dropped maps simply
stay in the live client's tree, where they still work. But the reasoning now has a hard constraint
attached:

**Never wholesale-swap a WZ file.** Replacing `Map.wz` with v84's copy would provably destroy ~832
working maps on top of Ezorsia's 2.9 MB of custom content. Every import is node-level and additive,
by construction, not by assumption.

Only `Map.wz` removals have been characterised. The other ten files show net node *gains*, but a net
gain does not rule out removals — they are **unmeasured, not measured-clean.**

## Dependency graph

```
01 client patch (go/no-go) ─────────────┐
                                        │
02 baseline diff ──► 03 WZ pipeline ────┼──► 04 cosmetics
                     (tracer bullet)    │    05 mounts
                                        │    06 Crimson Sky
                                        │    07 Neo City
                                        │    08 misc areas
                                        │    09 quests
                                        │
                                        └──► 10 Evan data ──► 11 crash audit ──► 12 skills ──┐
                                                   │                                          │
                                                   └──► 13 Evan world ────────────────────────┤
                                                                                              │
                                                                        14 progression ◄───────┘
                                                                              │
                                                                        15 creation path
                                                                              │
        04,05,06,07,08,09,15 ─────────────────────────────► 16 regression
```

## Frontier

Startable immediately: **01** and **02**. Everything else waits.

**Do 01 first regardless of anything else.** It is one hour and it decides whether Evan is
possible at all. If Themida rejects the patched binary, tickets 10–15 change shape entirely and
you want to know that before spending a week on WZ work.

## Ticket index

| # | Ticket | Blocked by |
|---|---|---|
| 01 | Evan client gate patched and client boots | — |
| 02 | WZ baseline diff and custom-content protect list | — |
| 03 | WZ merge pipeline proven end to end | 02 |
| 04 | v84 cosmetic items usable in game | 03 |
| 05 | v84 mounts rideable | 03 |
| 06 | Crimson Sky playable | 03 |
| 07 | Neo City 2227 playable | 03 |
| 08 | Misc v84 areas reachable | 03 |
| 09 | v84 non-Evan quests accept and complete | 03, 06, 07, 08 |
| 10 | Evan exists, renders, and has a dragon | 01, 03 |
| 11 | Evan skill crash audit — final skill list locked | 10 |
| 12 | Evan skills implemented | 11 |
| 13 | Evan world and quest chain playable | 10, 09 |
| 14 | Evan progression — SP, HP, dragon evolution | 12, 13 |
| 15 | Evan creation path | 14 |
| 16 | Regression — custom content and existing systems intact | 04–09, 15 |
