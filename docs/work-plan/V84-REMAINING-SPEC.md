# The remaining v84-parity work

**Scope:** the 52 open work rows, decomposed into 20 tickets numbered 54-73. This is the
specification; the tickets carry the file-level detail, the exact ids and the acceptance criteria.
Nothing here restates a file path or a code fragment, because both go stale and the tickets are the
place they belong.

The standard, unchanged: **is it in the v84 data?** If yes, the server should support it. If no, we
do not build it, however broken it looks.

---

## Problem Statement

The v84 cutover is done and an Evan plays on a real GMS v84 client. What remains is the difference
between what the v84 archives ship and what this server actually serves - 395 leaf-level data gaps
and a short list of code paths written before Evan existed. The remainder is small in bytes and
large in consequence, because the gaps cluster on exactly the content a player walks through.

Six problems, in the order a player meets them.

**1. The Evan chain stops at level 68.** Two quests want mobs that live on maps behind portals our
tree routes past. In v84 those portals are scripted branch portals; in our tree they are plain warps
with no script, so the branch never runs. One map over, the same defect makes Golem's Temple
unreachable. Slumbering Dragon Island - five more Evan quests - is shut for the same reason on both
of its doors, the Frog House route and the ferry.

**2. Items and NPCs render as nothing.** Fourteen items have an image and stats but no name string,
so they display as null or as the item id. Forty NPCs have no default line and answer with the
server's `(...)` placeholder. One NPC has no name at all and renders MISSINGNO.

**3. Quest requirements are looser than v84 wrote them.** 108 quests have no level cap and stay
startable past it. Fourteen date and repeat fields are missing, so repeatable quests have no cooldown
and date-retired quests stay live.

**4. Server-read data nodes are simply absent.** Reactor arrays on seven maps are short. One map's
entire info block is missing. Fifteen mob-skill levels, six Maker recipes, two reward-box entries and
160 cash-shop price and period values are not in our tree at all.

**5. Java paths written before Evan existed drop Evan out.** An Evan gains 6 MP per AP point where a
magician gains 18, and 8 HP where a magician gains 6 - a two-way error on every point ever spent. He
has no AP-reset floor, so the guard that refuses an illegal swap can never fire. His mounts render
nothing. His statue lands in the wrong Hall of Fame branch. Separately, the cash gachapon sends v83
mode bytes to a v84 client and fails silently, and three v84-new cash items have no handler at all.

**6. Two databases disagree with the data they were built from.** Two drop rows are missing the quest
gate their own sibling rows carry, so two items are lootable by players who are not on the quest.
Seven monster-card rows name a different mob than the item data does.

Underneath all six sits a discipline problem this spec exists to enforce as much as the work itself:
**several of these gaps are tempting to fill with a value that looks right and is not.** v84 never
shipped drop tables. A quest's mob token is not a drop table. An item named after a boss is not
dropped by that boss. Every value in this work either comes from the pristine v84 carve, is copied
verbatim from an analogue row this database already holds, or does not get written.

---

## Solution

Twenty tickets. Each is a vertical slice that one agent can land in one context window, and each
declares what it covers, what gates it, and how it is checked.

**Four classes of ticket, handled differently.**

*v84 parity* (13 tickets) is the real work: a gap between v84's data and ours, closed by copying v84.

*v83 legacy* (3 tickets) is real defects that are **not** v84 parity gaps - every one has zero
add-list rows, meaning v84 added nothing there. The owner asked for them to be ticketed anyway so the
known fixes stop being rediscovered. Each states its class in its first line so the distinction
survives the ticket being read alone.

*research* (2 tickets) is work whose scope is undecided or unknowable. These state a question and the
evidence that would settle it. They contain no implementation plan, because planning implementation
for undecided scope is how a guess gets shipped.

*mixed* (2 tickets) pairs a parity row with a legacy row that shares its seam and its owner decision;
the class is declared per row.

**Ordering.** Only one genuine dependency exists across the whole set, and it was resolved by
grouping rather than sequencing: the Golem's Temple portal is the same edit as the quest-branch
portal with a different destination table, so the two ride together and the second copies the first.
Everything else is independent and can start today. Six tickets carry an owner decision - not a
technical blocker, a question only the owner can answer - and each names which decision and which
half of the ticket it gates.

**Grouping principle.** Rows share a ticket when they share a seam: the same archive and the same
merge shape, the same source file, the same subsystem, or the same owner decision. Rows that share
only a theme do not share a ticket. One row was explicitly kept out of its neighbour's ticket because
its evidence says so - a timed cash-slot expansion has no precedent anywhere in this repo, while its
neighbour, an NX credit, has two, and folding them would let the easy one carry the hard one.

**Sequencing recommendation.** Work the Evan route first (tickets 54 and 55): it is the only cluster
that currently stops a player mid-playthrough, and every other row is either invisible today or
latent. Then the string and quest data, which is mechanical and unblocks nothing but costs nothing.
Then the Java rows. The owner-gated tickets can be prepared - changeSet written, header reasoned - and
held for the decision.

---

## User Stories

### Walking the world

1. As an Evan player at level 68 with the previous quest complete, I want to accept the next two
   quests in my chain and reach the maps their mobs live on, so that my chain does not dead-end four
   quests short of the island.
2. As a player on neither of those quests, I want the same portal to keep taking me where it takes me
   today, so that a fix for the Evan chain does not strand everyone else.
3. As a player standing at the Breathing Rock, I want the door to Golem's Temple Entrance to open, so
   that a map v84 ships and routes to is somewhere I can actually stand.
4. As a player who is not on the Golem's Temple quest, I want that same door to keep leading where it
   leads today, so that the branch is additive rather than a swap.
5. As an Evan player, I want the Frog House route to land me on Slumbering Dragon Island, so that the
   five island quests become playable at all.
6. As an Evan player inside the island's cave, I want the front door to put me in the room my current
   quest is about, so that four quests can share one entrance the way v84 built it.
7. As a player who owns none of those quests, I want that entrance to still put me somewhere sane, so
   that the shared door is not a dead end for everybody else.
8. As an Evan player at the harbour, I want Olaf's ferry to carry me to the island and back, so that
   the route v84 draws in its own map data is a route I can ride.
9. As a player, I want a ferry that cannot be short-circuited, so that opening the ride does not
   create a way to skip it.

### Reading the game

10. As a player who is handed a quest item, I want it to have a name in my inventory and quest window,
    so that I know what I am carrying and why.
11. As a player who receives a cap from an early Evan quest, I want it to show its name rather than
    nothing, so that an item that renders and equips is not anonymous.
12. As a player who obtains any of twelve v84-new equips, I want each to display its name, so that
    gear v84 shipped is legible.
13. As a player who clicks an NPC with no script, I want the line v84 wrote for that NPC, so that
    forty NPCs stop answering with a placeholder.
14. As a player, I want every NPC to have a name, so that none renders as MISSINGNO.

### Quest rules

15. As a player, I want the 108 instructor training quests to refuse to start once I am past their
    level cap, so that quest availability matches the game being matched.
16. As a player part-way through a repeatable request chain, I want its cooldown and its date window
    honoured, so that a repeatable quest behaves like one instead of being infinitely farmable.
17. As a player, I want date-retired quests to stay retired, so that dead content does not clutter
    live content.

### Data the server reads

18. As a player on the Aquarium maps, I want every reactor v84 places to be there, so that the map
    plays as its own data describes.
19. As a player entering Ludibrium's sky terrace, I want its map flags and entry hooks to exist, so
    that the map behaves rather than silently doing nothing.
20. As a player, I want the NPC and the rope v84 added to two maps to be present, so that the maps
    are not one object short of themselves.
21. As a player fighting mobs that use higher-level skills, I want those skill levels to exist, so
    that combat matches v84 rather than falling back on a lower tier.
22. As a player using either of two reward boxes, I want the full v84 reward table rolled, so that the
    44th entry is not silently unreachable.
23. As a player using Maker, I want the six recipes v84 added to be craftable, so that the Maker data
    is the v84 data.
24. As a server operator, I want every cash-shop row to carry its v84 price and period, so that when a
    row is put on sale it is not mispriced by default.

### Playing an Evan

25. As an Evan player, I want each AP point to give me the magician spread v84 gives me, so that my
    character is not quietly weaker than the class it is.
26. As an Evan player who has already spent AP under the wrong spread, I want a decision recorded
    about those points, so that the fix does not leave half my character mis-valued forever.
27. As an Evan player using AP reset, I want the illegal-swap guard to actually apply to me, so that I
    cannot reset myself into an invalid pool.
28. As an Evan player whose completed card sets wrote the wrong stat, I want that repaired on my
    saved character, so that the code fix reaches the damage it was written for.
29. As an Evan player, I want my three mount skills to render their mounts, so that riding shows a
    mount rather than nothing.
30. As a max-level Evan, I want my statue to appear in the Evan branch of the Hall of Fame, so that I
    am not filed under a default that belongs to nobody.

### Cash and items

31. As a player buying from the Cash Shop Surprise, I want the result to arrive, so that a gachapon
    pull is not a silent failure caused by two stale bytes.
32. As a player using either NX coupon, I want the stated points credited, so that a v84-new item does
    something when consumed.
33. As a player using the cash slot-expansion item, I want the seven-day expansion granted, so that a
    v84-new cash item is not inert.
34. As a player, I want an item I cannot use for a quest I am not on to stay invisible, so that the
    quest gate on a drop works the way its sibling rows already do.
35. As a player collecting monster cards, I want each card to name the mob its own item data names, so
    that the book and the drops agree.

### Playing at all

36. As any player on any class, I want to attack a monster on the first Maple Road hunting map without
    the client crashing, so that a fresh character can complete the first hunting quest in the game.

### Operating the server

37. As the server operator, I want every derived value to name the row it was copied from, so that
    recovered data, derived data and owner-directed overrides stay distinguishable a year from now.
38. As the server operator, I want the rows that cannot be answered from v84 recorded as closed rather
    than left open, so that they stop being re-filed as work.
39. As the server operator, I want the tempting-but-wrong rows written down by name, so that the next
    agent who spots the name link does not write the row.
40. As the server operator, I want defects that are real but not v84 parity ticketed and labelled as
    such, so that they are neither lost nor mistaken for parity work.

---

## Implementation Decisions

**Evidence hierarchy is fixed and non-negotiable.** The pristine v84 carve and the owner's installed
v84 client are the only things that settle "did v84 have X". Our own tree and database come second;
external references come third and are useful only for rates and dropper lists, never for existence.
A lower tier never overrides a higher one, and where they disagree the disagreement gets written down
rather than silently resolved.

**Drop tables are not recoverable, in any version.** v84's own archives contain no server reward data;
Nexon never shipped it. Any claim of the form "v84 dropped X from Y at Z%" is out of reach by
construction. Where a source is needed, the rule is: copy a real analogue row verbatim replacing only
the id, preferring the same mob family and then the same level band; take an external rate only where
the item exists in both versions and record it as such; otherwise write nothing.

**A row derived from a token cannot be corroborated by that token.** This project has already shipped
one circular-provenance row and it is the reason the rule is written down.

**Portal parity means the whole node, not the script leaf.** Both blocked Evan portals are plain warps
in our tree and scripted branch portals in v84. Adding the script name alone changes nothing, because
a warp portal never consults it. The type and the destination move too, and the destination our
portal has today becomes the script's fallback branch - the branch is never dropped, because it is
the only way through for a character not on the quest.

**Every branch a portal script takes must be named by data.** Each destination in this work is stated
by a quest's own info or check node, or by the contents of the room plus the quest that gates it.
Where a branch is not named by data - notably which room a character on none of the relevant quests
lands in - it is an owner decision, not an implementation detail.

**No portal or map script may play a client cutscene that the client cannot resolve.** That is how
every female Evan was crashing before the cutover, and none of the scripts in this work appears among
the client's scene nodes.

**Array nodes are compared by name, never by position.** Storage order is not name order. The
cash-shop commodity table is the sharp case: its children are array indices, not the shop numbers that
identify a row, and a positional merge there writes the right value onto the wrong product. Keying on
the shop number is a stated acceptance criterion, not advice.

**An empty leaf is data.** Dropping a present-but-empty node while copying a section is a deletion,
and the checks in this work distinguish "absent" from "present and empty".

**A present array index is not proof of equal content.** The coverage tool can see that our array is
shorter than v84's; it cannot see that index N holds the same thing on both sides. Where that
question matters, the precedent for resolving it is the whole-image terrain work already landed.

**Job branches name Evan; the job predicates stay untouched.** Evan falls out of magician branches
because the predicate compares a truncated job id. The predicate is load-bearing across the tree and
is left alone; Evan is named in each individual chain instead, which is the pattern a dozen sites
already follow.

**Version-gated protocol changes.** Mode bytes and enum values that moved between v83 and v84 go
behind a version guard using the existing helper, with the disassembly evidence recorded in the
javadoc beside the value - the same shape the already-landed mode fixes use.

**Database changes are additive by default and applied by the owner.** Agents read the database and
never write it. Corrections ship as new changeSets rather than edits to frozen ones. Two rows in this
work are genuinely UPDATEs against existing data, and they are gated on an explicit owner decision
precisely because they break the additive rule.

**No value gets invented to close a row.** Where a needed value cannot be derived, the row is a
research ticket, not an implementation ticket. Two rows are blocked on a client binary disassembly
that does not exist yet, and four close as permanent unknowns. Neither set gets a plausible
placeholder.

**Two rows require a client and no agent may launch one.** The map-crash row and the ferry-click
confirmation both bottom out in something only the owner can observe. Those tickets split their
acceptance criteria into what an agent can verify offline and what only a launch can confirm, and say
so.

---

## Testing Decisions

**The load-bearing tests run against real data, not fixtures.** This project's convention is a
separate test-class suffix for classes that load the real archives, because a static path is resolved
once per JVM and one fixture-based class repoints it. Those classes were invisible to the default
test runner until recently; they are included now, but a ticket that adds one still names it
explicitly so a reviewer can run it directly.

**Maven is not run by implementing agents.** Sibling agents share a build directory and collide.
Every ticket states its test invocation and hands the run to the orchestrator. A ticket is not done
because its tests were written; it is done when someone has run them.

**Script tests instantiate the real engine and assert exact interactions.** A portal or map script is
loaded under the real script engine, invoked against a mock interaction object, and asserted to make
exactly the calls it should and no others. Scripts are additionally asserted not to name a client
scene - a guard that has already caught a real regression, on a comment, which is the evidence it is
not decorative.

**Data merges are verified by measurement, not inspection.** Node-for-node and value-for-value against
the pristine carve, keyed on name; array lengths compared; empty-valued leaves preserved; the diff
confined to the nodes the ticket claims. Line endings matter - the map data in this tree uses a
different convention from the blobs, and a writer that forces the wrong one shows every line as
changed and buries the real edit.

**Acceptance criteria are objective or they are not criteria.** Every checkbox in every ticket is a
named test that passes, an exact node value present in the tree, a specific database row, an exact
byte, or an observable in-game behaviour. "Works correctly" appears in none of them.

**Negative assertions are first-class.** Several tickets are partly or wholly about what must *not*
appear: no drop row for a particular item, no shop stock for a particular NPC, no script file for a
particular hook, no rows copied onto an unplaced dropper. Those are asserted by count queries and by
tests that pin absence, because a refusal nobody checks quietly stops being a refusal.

**Regression surface to re-check on the data tickets.** Quest acceptance for the affected ids, map
load for the affected maps, and the existing terrain, portal-index and map-life parity suites, which
already cover the maps most of this work touches.

**Two rows cannot be closed by testing.** The map-crash row needs a client kill on the affected map;
the ferry needs a client click on the boat NPC. Both tickets state what an agent can prove offline and
stop there.

---

## Out of Scope

### Rows that are already v84-correct - no ticket, nothing to build

Six rows were investigated and need no work. They are listed here so they stop being re-filed.

* **R30 - the unread pickup-only item flag on 83 items.** Every affected item is already covered by
  something else: 63 carry a consume-on-pickup flag and are destroyed before they reach inventory, so
  enforcement would be a no-op; the other 20 are trade-blocked. The five v84-new ones work already.
* **R31 - the claimed unhandled equip-stat fields.** Two of the five are Maker reagents and are
  handled after a rename the Maker path already performs; one is skipped deliberately; one is a cash
  item that never routes through that code. Only a craft field on two tablet scrolls genuinely falls
  through, and no craft field exists on equipment because the profession system is post-v83 content.
* **R37 - "328 non-Evan quests with no script".** 327 of the 328 carry an end date in the past - the
  latest is mid-2010, all dead before the v84 client shipped - and the date requirement refuses them
  before anything else is consulted. The one survivor's start NPC is placed on none of the 5,338 map
  images. None is an Evan quest, and a missing quest script fails softly with a warning. Two wording
  corrections belong to the tracker, not to code: the discriminator is "non-medal", not "non-Evan",
  and the medal-quest count is 39.
* **R38 - the dead-portal and silent-map figures.** The numbers do not reproduce and the corrected
  ones are benign: none of the dead portals carries a real destination and none is a warp type, so the
  fallback branch a prior ticket worried about has no instance in this map data. The town cases are
  cosmetic unity portals on six major towns. The Evan cases are already inside two other rows.
* **R39 - a map's unimplemented entry hook.** Our node is byte-identical to v84's, hook included. The
  name is shared with a v83 cave family whose maps have no scripts either and which works. The claim
  that the map is unreachable is wrong - a live portal warps there for anyone on the relevant quest,
  and the quests around it work today.
* **R40 - a map with no inbound portal.** Deliberate and already handled: it is a client-side cutscene
  map entered by a client-side warp, like its whole band, and both halves are already explained in the
  code that handles them.

### Not v84 parity - ticketed as v83 legacy, deliberately not built as parity work

Eleven rows are real defects with zero add-list rows, meaning v84 added nothing to them. They are
ticketed (71, 72, 73 and the legacy half of 66) so their known fixes are not rediscovered, and every
one of those tickets says in its first line that it is v83 legacy. They are **not** part of v84
parity and must not be scheduled as if they were: five chaos scrolls that burn the upgrade slot;
seven potions whose percentage half is dropped; six battlefield-skill items wired to nothing; a
damage-over-time effect that was commented out on purpose upstream in the same commit as the health
overhaul that would have made it abusive; a hero-buff bonus that never reaches local stats; chairs
that all heal identically; seven stale monster-card rows; packet-validator coverage; an in-memory
de-duplication that re-warns once per restart; an unimplemented v83 event NPC; and one tracker claim
about endgame weapons that is refuted on all three of its assertions and closes as a restatement.

### Excluded from this spec entirely

* **The HD client.** A sibling agent owns it and the owner has put it on hold.
* **Dual Blade.** Not started, and deliberately not sized. It is a whole job - client route, skills,
  quest chain, creation - and belongs in its own project.
* **The staged phase-B archive tree.** Built and not installed; thousands of refused rows and dozens
  of conflict images remain. That is an owner call, not a work row.
* **Dragon equipment slots.** A sibling agent owns it.

### Standing refusals this spec does not reopen

Crimson Sky, whose assets v84 ships with no entrance anywhere in its own map data. The ferry portals
whose "stranded passenger" trap was refuted and whose addition would create a ride-skip exploit. One
map whose verbatim data take would delete a party-quest stage. The static Hall of Fame rows, which
collide with a live allocator and would undo a feature the owner asked for by name. The ice-wall
mechanic, whose mob is placed in none of v84's 4,848 map images. And one mob's drop table, which is
permanently unknowable because no client version ever carried drop tables.

---

## Further Notes

**Six owner decisions gate parts of this work.** None blocks the majority of any ticket, and each is
named in the ticket that needs it: which room the island's shared cave door uses as its fallback;
whether a v84-new pet gets a purchase source or closes as v84-faithful with no source; whether two
database corrections may be applied as updates against the additive-only rule; how already-spent AP
points are treated once the spread is fixed; what an Evan's AP-reset floor numbers actually are; and
whether the persisted stat repair may run against the owner's own character. The tickets can be
written, reasoned and prepared without the answers; they cannot be applied without them.

**Two artifacts are missing and no amount of effort in this repo produces them.** A v84 client
disassembly would settle the element mapping behind a buff that is written and never read, and the
failure-reason table that is currently v83's values carried over untested. Both are recorded as
research with the question stated and the artifact named, so neither is mistaken for a small Java
fix.

**The largest single number in the gap is also the least urgent.** 160 cash-shop price and period
values are missing, and every affected row is off sale, so nothing is mispriced today. It is latent
debt. It must not outrank a row a player can hit.

**Four sweeps are complete and should be cited rather than repeated.** The v84 coverage matrix, the
quest sweep, the quest-dropper sweep and the item-source sweep between them have already answered
"what did v84 add", "does every added quest work", "does the dropper live where the quest sends you"
and "does every added item have a source". Each is regenerable by script. Re-deriving any of them by
hand has happened before and produced numbers that disagreed with the tooling; the tooling was right.

**Counts in the old narrative tracker are unreliable and several are corrected by these rows.** The
item-source figure, the missing-script discriminator, the dead-portal counts, the "several v84-new"
chair claim, the endgame-weapon count, and a skill class attributed to the wrong job are all wrong in
the tracker and right in the work rows. Where a ticket restates one, the restatement is the
deliverable and it lands in the narrative tracker only - the coverage files, the work rows, the status
page and the ticket ledger are owned by other agents and are not edited by implementing agents.

**One row is a queue, not a task.** 127 v84-new items have no source of any kind. It has zero live
blockers, no new drop row is proposed by the sweep, and none should be added from it. It shrinks as
individual entries are established and is re-run rather than re-derived.

**The server is live and the owner plays on it.** Never restart it, never kill it, never launch a
client. Packet visibility toggles live through an in-game command, so no restart is needed for it -
check that before anyone schedules one.
