# 04 — v84 cosmetic items usable in game

**Blocked by:** 03

**Status:** ready-for-agent

**Merge procedure: [`../WZ-MERGE-PROCEDURE.md`](../WZ-MERGE-PROCEDURE.md)** — established by ticket 03 and proven end to end. Use its tool (`docs/wz-baseline/tool-merge/`); do not invent a second way. Start with a dry run (`WzMerge merge <v84>/X.wz <live>/X.wz - <add-list> <conflicts>`) and read the conflicts before merging anything.

## What to build

The ~412 items v84 added — predominantly hairstyles (Evan Hair, Tighty Bun, Babish, Spiky Shag families in all eight colours) plus Evan equipment and Crimson Sky drops — exist, render on a character, and can be obtained.

Deliberately first among the content tickets: it is the lowest-risk, most visible slice and it exercises the full `Item.wz` + `Character.wz` + `String.wz` + server-XML path on content nobody will miss if a retry is needed.

Obtainability matters — an item that exists but has no source is not delivered. Route the cosmetics through whatever your server already uses (beauty salon NPC, NX shop, GM command) rather than inventing a new one.

Guard the custom-content protect list from ticket 02 throughout: `Character.wz` is where your client's 18.6 MB of custom data lives, so this is the single most dangerous file to merge carelessly.

## Acceptance criteria

- [ ] Added items present in client WZ and server XML
- [ ] Names resolve correctly from `String.wz` — no blank or placeholder labels
- [ ] Hairstyles render correctly on both genders
- [ ] Items are obtainable through an existing in-game route
- [ ] Protect-list nodes in `Character.wz` verified still present after the merge
