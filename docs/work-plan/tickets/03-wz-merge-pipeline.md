# 03 — WZ merge pipeline proven end to end

**Blocked by:** 02

**Status:** blocked-on-human (agent work complete; criterion 1 needs a client launch)

## What to build

One v84 node imported into a copy of your client WZ, re-saved at v83 version encoding, loading correctly in game — and the same node exported to Cosmic's XML tree and read by the server without error.

This is the tracer bullet for every content ticket that follows. Pick the smallest, most boring node available (a single cosmetic item is ideal) so the ticket is about the **pipeline**, not the content.

Two output trees are required and they are not interchangeable: binary `.wz` at `D:\games\MapleStory\` for the client, and "Private Server" XML at `Cosmic\wz\` for the server. A node that works in one and not the other is a half-finished import.

Two known mechanical traps to resolve here, once, so no later ticket rediscovers them: v84 WZ carries a different version hash and must be re-saved at v83 encoding; and the XML that HaRepacker exports for the client carries `basedata` base64 image attributes which the server does not need (they inflate `2218.img.xml` from a few hundred KB to 14 MB).

Do not touch `UI.wz` in this ticket. It is the one file that must never be bulk-copied.

## Acceptance criteria

- [ ] One v84 node imported into a copy of the client WZ and visible/usable in game
      — **import done and verified in the file; "in game" is human-only.** See `## Human steps`.
- [x] Same node present in `Cosmic\wz\` XML and loaded by the server with no parse error
- [x] Version re-save procedure documented in the work-plan folder
- [x] Decision recorded on how server-side XML is produced (re-export vs stripping `basedata`)
- [x] Procedure is repeatable by someone else from the notes alone

## What was done

**Procedure: [`../WZ-MERGE-PROCEDURE.md`](../WZ-MERGE-PROCEDURE.md).** That document is the real
deliverable; this section is just the record for 03.

**Tracer node: item `2001500`, "Red Potion" — the untradeable v84 variant.**
Two nodes, one per file, both taken verbatim from ticket 02's committed manifests:

- `Item.wz/Consume/0200.img/02001500` — `info/{icon,iconRaw,price=0,tradeBlock=1}`, `spec/hp=50`
- `String.wz/Consume.img/2001500` — name "Red Potion", desc "…Recovers 50 HP."

Why this one: it is eight leaf properties and two 27×30 icons; it needs no `Character.wz`,
no `Map.wz`, no `UI.wz` — the three dangerous files; it exercises **both** halves of an item
(art in `Item.wz`, label in `String.wz`), which is the shape every ticket-04 cosmetic has; and it
is *usable*, not merely present, so the in-game check is unambiguous. Its icon is byte-identical to
stock v83's Red Potion `2000000`, which gives the human a side-by-side reference in one inventory
window. It was absent from the live client and from `wz/` before the merge — confirmed, not assumed.

**Tool: `docs/wz-baseline/tool-merge/`** (C# + MapleLib), sibling of ticket 02's diff tool.
`merge` writes the client `.wz`, `xml` writes the server tree, `dump` inspects a node.
Additive-only is a gate in `Merge()` before any mutation, not an audit afterwards.

**Outputs**

| artefact | where |
|---|---|
| merged client `Item.wz` + `String.wz` | `D:\games\MapleStory\Server\wz-merge\post\` (**not** installed) |
| pristine pre-merge copies | `D:\games\MapleStory\Server\wz-merge\pre\` (hash-identical to the live client and to the backup) |
| server XML | committed: `wz/Item.wz/Consume/0200.img.xml`, `wz/String.wz/Consume.img.xml` (+19 lines, −0) |
| merge input lists | `docs/wz-baseline/merge-lists/03-tracer-*.txt` |
| conflicts | `docs/wz-baseline/merge-lists/*.conflicts.txt` |
| server-side check | `src/test/java/server/V84TracerNodeTest.java` |

**conflicts.txt.** The tracer's own four conflict files are empty — both nodes were genuinely new,
which is what an empty conflicts file is supposed to mean. The gate was proven two ways instead:
re-running the merge against its own output refuses the path (`added 0, refused 1`), and a **dry run
of every add-list against the live client** produced the real thing — **41 collisions across
2,065 add-list roots**, tabulated in the procedure doc. The headline: `Npc.wz/9901910`–`9901919`,
where v84's new NPCs land inside Cosmic's injected `99xxxxx` block. Ticket 08 must re-id or drop
them. Conversely `String.wz/Cash.img/5530001` is a case where the rule *cost* us something — the
live client has the placeholder `MISSING NAME` and v84 has "DS Medal Basket".

**BlockSize invariance: no pre-existing node changed.** `Item.wz` 7,361 → 7,362 paths,
`String.wz` 12,859 → 12,860; exactly one image per file changed size (the one inserted into, by
exactly the added bytes); `removed-list` empty in both. Every other image survived a full MapleLib
repack byte-for-byte the same size. Limit: a same-size replacement would be a false negative.

**Server load, real output** (`./mvnw -o test -Dtest=V84TracerNodeTest`):

```
[INFO] Running server.V84TracerNodeTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.942 s
[INFO] BUILD SUCCESS
```

That includes the full `ItemInformationProvider` path against a live MySQL:
`getName(2001500)` → `"Red Potion"`, `getItemEffect(2001500).getHp()` → `50`,
`isUntradeableRestricted(2001500)` → `true`. Negative control: reverting just the two XML files
turns the same run into `Tests run: 3, Failures: 2` with
`Item.wz/Consume/0200.img/02001500 missing from the server XML tree` — the test is not vacuous.
Full suite: `Tests run: 1890, Failures: 0, Errors: 0, Skipped: 1` (the IIP case skips under
surefire because `MobSkillFactoryTest` redirects `wz-path` for the whole fork — see the procedure).

The server itself also boots clean on the modified tree:

```
23:16:53 Cosmic v83 starting up.
Database is up to date, no changesets to execute
23:16:58 Channel 1: Listening on port 7575
23:16:58 Listening on port 8484
23:16:58 Cosmic is now online after 5143 ms.
```

Note honestly what that does and does not prove: `ItemInformationProvider` is lazy — no handler
runs at boot — so the boot shows the modified tree does not break startup, and the test above is
what shows the tracer nodes themselves parse.

## Human steps — staged, not performed

An agent cannot launch MapleStory. Everything below is ready to run; nothing below has been run.
**The live client is untouched** — `D:\games\MapleStory\Item.wz` and `String.wz` still hash-match
`Server\_backup\client-v83-EzorsiaV2-2026-08-15\` (verified, SHA-256).

### 1. Install the merged files

Close the client first, then:

```
copy D:\games\MapleStory\Server\wz-merge\post\Item.wz    D:\games\MapleStory\Item.wz
copy D:\games\MapleStory\Server\wz-merge\post\String.wz  D:\games\MapleStory\String.wz
```

Expected sizes afterwards: `Item.wz` 18,398,263 bytes (was 18,397,440), `String.wz` 3,561,331
(was 3,561,285). Anything else means the wrong file was copied.

### 2. Start the server, then the client

```
cd D:\games\MapleStory\Server\Cosmic\.claude\worktrees\evan-dualblade
launch.bat
```

Wait for `Cosmic is now online`. Then launch the client you normally use —
`D:\games\MapleStory\localhome.exe` (**not** the `.evan.exe` copies; ticket 01 is a separate test
and mixing the two makes a failure ambiguous). Log a character in.

### 3. The check

As a GM, spawn the item:

```
!item 2001500
```

**Pass:** a red potion icon appears in the USE tab, identical to the ordinary Red Potion; hovering
it reads **"Red Potion"** with the description "A potion made out of red herbs. Recovers 50 HP.";
double-clicking it heals 50 HP; and it cannot be dropped or traded (`tradeBlock=1` — this is the
one visible difference from stock `2000000`, and it is the tell that you are looking at the
imported item rather than the old one).

**Fail signatures, and what each means:**

| symptom | meaning |
|---|---|
| client crashes or hangs at the login/world screen | the re-saved `.wz` is not readable by the client — version/IV problem. Roll back. |
| item appears with a **blank/black icon**, name correct | `Item.wz` merge lost the canvas payload; `String.wz` is fine |
| item appears with the correct icon but **no name / "null"** | `String.wz` did not take |
| `!item 2001500` reports an unknown item, server-side | the server XML is not being read — check `-Dwz-path` and that you launched from the worktree, not the main checkout |
| item exists but drinking it does nothing | `spec/hp` did not survive — but the test above already asserts it did, so suspect the client copy instead |
| **any other item or any UI element is now broken** | stop. That is the case the additive-only rule exists to prevent and it would mean the rule failed. Roll back and report. |

### 4. Rollback — always available, verified

```
copy D:\games\MapleStory\Server\_backup\client-v83-EzorsiaV2-2026-08-15\Item.wz    D:\games\MapleStory\Item.wz
copy D:\games\MapleStory\Server\_backup\client-v83-EzorsiaV2-2026-08-15\String.wz  D:\games\MapleStory\String.wz
```

Those two backup files were checked byte-for-byte against the live client during this ticket. The
server side rolls back with `git checkout -- wz/`.

### 5. What to report back

Just which row of the table above matched. A pass here unblocks 04–09; a crash at step 3 means the
node-level merge produces a file this specific client build will not read, which is a
pipeline-level finding, not a content one.
