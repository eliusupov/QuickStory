# 03 — WZ merge pipeline proven end to end

**Blocked by:** 02

**Status:** ready-for-agent

## What to build

One v84 node imported into a copy of your client WZ, re-saved at v83 version encoding, loading correctly in game — and the same node exported to Cosmic's XML tree and read by the server without error.

This is the tracer bullet for every content ticket that follows. Pick the smallest, most boring node available (a single cosmetic item is ideal) so the ticket is about the **pipeline**, not the content.

Two output trees are required and they are not interchangeable: binary `.wz` at `D:\games\MapleStory\` for the client, and "Private Server" XML at `Cosmic\wz\` for the server. A node that works in one and not the other is a half-finished import.

Two known mechanical traps to resolve here, once, so no later ticket rediscovers them: v84 WZ carries a different version hash and must be re-saved at v83 encoding; and the XML that HaRepacker exports for the client carries `basedata` base64 image attributes which the server does not need (they inflate `2218.img.xml` from a few hundred KB to 14 MB).

Do not touch `UI.wz` in this ticket. It is the one file that must never be bulk-copied.

## Acceptance criteria

- [ ] One v84 node imported into a copy of the client WZ and visible/usable in game
- [ ] Same node present in `Cosmic\wz\` XML and loaded by the server with no parse error
- [ ] Version re-save procedure documented in the work-plan folder
- [ ] Decision recorded on how server-side XML is produced (re-export vs stripping `basedata`)
- [ ] Procedure is repeatable by someone else from the notes alone
