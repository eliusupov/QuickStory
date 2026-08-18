# Sources - what we may cite, and for what

The governing rule for this project: **is it in the v84 data? If yes, the server should support
it. If no, we do not build it.** Everything below exists to answer that question, or to fill the
gaps v84's data provably cannot answer.

Sources are ranked. **A lower tier never overrides a higher one.** When they disagree, say so in
the commit message rather than silently picking one.

---

## Tier 1 - authoritative: pristine GMS v84

`D:\games\MapleStory\Server\porting-resources\wz-data\v84\` - 18 `.wz`, all dated 2010-03-29.
`docs/wz-baseline/TOOL-NOTES.md:4-5` records it as byte-identical to a fresh carve of
`GMSSetupv84.exe`, and `Map.wz` / `Npc.wz` were later re-confirmed by SHA256 against the owner's
installed client.

**This is the only thing that settles "did v84 have X".** Read-only. Packed binaries, not XML - use
the tooling below rather than expecting a text tree.

`D:\games\MSv84\client\` - the owner's installed v84 client. All 17 archives byte-size identical to
the pristine carve, so it is equally authoritative *and* it is what his client actually parses.
Prefer it when the question is "what will his client do". **Never write to it.**

### What Tier 1 cannot answer

**Drop tables.** Nexon kept drop rates server-side and never shipped them. No carve of any version
contains them. Any claim of the form "v84 dropped X from Y at Z%" is **not** recoverable from the
files, and a quest's mob token is not a drop table - see the trap below.

---

## Tier 2 - our own tree and database

* `wz/` - the server's map/quest/item data. Diverges from Tier 1 deliberately in places; every
  divergence should be traceable to a commit and a reason.
* `cosmic` database - `drop_data`, `shopitems`, `reactordrops`, `queststatus`, `characters`.
  MySQL at `C:\Program Files\MySQL\MySQL Server 9.4\bin\mysql.exe`, root/root. **SELECT only** from
  agents; changes go through a new Liquibase changeSet.
* `docs/wz-baseline/` - the migration's own manifests. `add-list/` = in v84, not in v83-stock.
  `removed-list/`, `modified-list/`, `protect-list/` (deliberate custom content),
  `CUSTOM-CONTENT-PROVENANCE.md`.
* `scripts/` - npc, portal, reactor, quest, map hooks. Frequently the real answer to "what writes
  this quest record".
* `git log` / `git blame` - **check before calling anything a defect.** Several apparent gaps in
  this project turned out to be deliberate upstream removals.

### Reference clients, read-only

* `D:\games\MapleStory\` - the working **v83 Ezorsia HD** client. Ground truth for "what does HD
  actually look like". Its `UI.wz` is stock; the HD look is exe patches, not art.
* `D:\games\dreamms\` - pristine v83, for v83-vs-v84 diffs.

---

## Tier 3 - external references

Useful precisely where Tier 1 is silent: **drop rates, dropper lists, and how content behaved in
practice.** Never sufficient on its own for "did v84 ship this".

| source | good for | caveats |
|---|---|---|
| [dreamms.gg](https://dreamms.gg/) | drop rates and dropper lists per item | **v92, and a private server** - some values are their tuning. A prior session traced some DreamMS data back to *our own* tree, so agreement is not always independent. |
| hidden-street (`global.` / `bbb.`) | GMS-era drop and quest data | `bbb.` is Cloudflare-locked and 403s - use **Wayback captures**. Anchor on **2010** captures. |
| MapleStory Wiki / Fandom | mechanics, item and skill descriptions | Era drift; check the revision date. |
| ayumilove (2009-2010 posts) | skill tables, builds | Often **KMS pre-release**, not GMS. Contradicted v84 on Evan advancement levels. |

### Dating rule

* **GMS Big Bang: 2010-12-14.** Captures from 2011+ describe a rebalanced game and do **not**
  describe what this server models.
* **Evan launched in GMS March 2010**, so Evan content is a 2010 addition; anything describing Evan
  from 2009 is almost certainly KMS.

---

## Deriving values we cannot look up

Established and owner-approved, in order of preference:

1. **Copy a real analogue row verbatim, replacing only the id.** `153-crimson-sky-drop-data.sql`
   states the rule in its header; `168-evan-book-drop-data.sql` and `170` applied it. Prefer the
   same mob family, then the same level band.
2. **Take a Tier 3 rate** when the item exists in both versions. Record it as such.
3. **Never invent** a rate, a dropper, a script's behaviour, or a coordinate.

Whatever you pick, **name the precedent in the changeSet header** so the next reader can tell
recovered data from a derived value from an owner-directed override.

---

## Traps this project has actually hit

* **Circular provenance.** Quest 22529's drop row was authored by reading the quest's own mob
  token, then cited as evidence the row was right. A row derived from a token cannot be
  corroborated by that token.
* **A mob token is not a drop table.** `#o0130100#` names a mob; it does not say that mob drops the
  item, and the quest's staging and level band may point elsewhere. **Read the whole quest** -
  pre-accept text, objective, `Say.img` dialogue - not one token.
* **`PlayerNPC.java:66-67` is wrong.** It calls ids 9901910-9901919 "custom additions to HeavenMS";
  v84 ships all ten as Nexon content with its own text.
* **Storage order is not name order.** `WzPeek` emits section rows in archive order; our XML walk is
  document order. Compare **keyed on node name**, never positionally.
* **Empty is not absent.** A `value=""` leaf is data; dropping it while copying a section is a
  deletion.
* **CRLF.** Map XML in this tree is CRLF; a Python round-trip will silently rewrite the whole file.
* **Every character in the database is GM 2 or 6 by design.** Never benchmark against another
  character.

---

## Tooling

Built binaries under `docs/wz-baseline/*/bin/Release/net10.0-windows/`.

* `WzPeek` - `dump` / `scan` / `portals` / `life` / `fh` / `digest`. The query tool; `scan` walks
  every image, which is how you prove a negative.
* `WzDump` (`tool/`) - archive diff. `WzMerge` (`tool-merge/`) - additive importer, `xml` mode
  writes XML for listed paths. **Read its exit-code contract before scripting it.**
* `WzVR` (`tool-vr/`) - per-map camera bounds. `WzQuestSync` (`tool-questsync/`) - projects server
  quest counts onto a client `Quest.wz`.
* `tools/hd/` - the HD loader, its patch resolver (`resolve.py`) and patch data.
