# The remaining v84-parity work

**Scope:** the open work rows R01-R52, decomposed into 20 tickets numbered 54-73. This is the
specification; the tickets carry the file-level detail, the exact ids and the acceptance criteria.
Nothing here restates a file path or a code fragment, because both go stale and the tickets are the
place they belong.

This revision is written **after** the ticket audit that corrected all twenty tickets. Where this
document and a ticket disagree, the ticket is right.

The standard, unchanged: **is it in the v84 data?** If yes, the server should support it. If no, we
do not build it, however broken it looks.

---

## Problem Statement

The v84 cutover is done and an Evan plays on a real GMS v84 client. What remains is the difference
between what the v84 archives ship and what this server actually serves - a few hundred leaf-level
data gaps and a short list of code paths written before Evan existed. The remainder is small in
bytes and large in consequence, because the gaps cluster on exactly the content a player walks
through.

Five problems, in the order a player meets them.

**1. The Evan chain stops at level 68.** Two quests want mobs that live on maps behind portals our
tree routes past. In v84 those portals are scripted branch portals; in our tree they are plain warps
with no script, so the branch never runs. One map over, the same defect makes Golem's Temple
unreachable. Slumbering Dragon Island - five more Evan quests - is shut for the same reason on both
of its doors, the Frog House route and the ferry.

**2. Items and NPCs render as nothing.** Fourteen items have an image and stats but no name string,
so they display as null or as the item id. Forty NPCs have no default line and answer with the
server's placeholder.

**3. Quest requirements are looser than v84 wrote them.** 123 requirement leaves are missing: 108
quests have no level cap and stay startable past it, and fifteen date and repeat fields are absent,
so repeatable quests have no cooldown and date-retired quests stay live.

**4. Server-read data nodes are simply absent.** Reactor arrays on seven maps are short by 31
entries. Fourteen mob-skill levels, six Maker recipes and two reward-box entries are not in our tree
at all. Four whole-new v84 chairs carry their own heal rates and the server reads none of them, so
every chair heals identically.

**5. Java paths written before Evan existed drop Evan out.** His mounts render nothing and his
statue lands in the wrong Hall of Fame branch. Separately, the cash gachapon sends v83 mode bytes to
a v84 client and fails silently, three v84-new cash items have no handler at all, and the v84 client
crashes outright when a player attacks on the first Maple Road hunting map. One database table also
disagrees with the item data it was built from, on seven monster-card rows.

Underneath all five sits a discipline problem this spec exists to enforce as much as the work
itself: **several of these gaps are tempting to fill with a value that looks right and is not.** v84
never shipped drop tables. A quest's mob token is not a drop table. An item named after a boss is
not dropped by that boss. And a gap measured with the wrong key is not a gap at all - the largest
single number this spec used to carry, 162 cash-shop price and period leaves, was an artifact of
comparing two differently-ordered arrays by index, and it closed as a non-defect once it was keyed
on the shop number. Every value in this work either comes from the pristine v84 carve, is copied
verbatim from an analogue row this database already holds, or does not get written.

---

## Solution

Twenty tickets. Each is a vertical slice that one agent can land in one context window, and each
declares what it covers, what gates it, and how it is checked.

**Four classes of ticket, handled differently.**

*v84 parity* (13 live tickets, plus one that the audit closed as a non-defect and which now exists
only as the record of its own refusal) is the real work: a gap between v84's data and ours, closed
by copying v84.

*v83 legacy* (3 tickets) is real defects that are **not** v84 parity gaps - every one has zero
add-list rows for the thing that is broken, meaning v84 added nothing there. The owner asked for
them to be ticketed anyway so the known fixes stop being rediscovered. Each states its class in its
first line so the distinction survives the ticket being read alone.

*research* (2 tickets) is work whose scope is undecided or unknowable. These state a question and
the evidence that would settle it. They contain no implementation plan, because planning
implementation for undecided scope is how a guess gets shipped.

*mixed* (1 ticket) pairs three legacy rows with one parity row that shares their seam and their
owner decision; the class is declared per row. That parity row is the chairs, which the audit moved
back out of legacy after finding four whole-new v84 chairs in the add-list.

**Ordering.** Only one genuine dependency exists across the whole set, and it was resolved by
grouping rather than sequencing: the Golem's Temple portal is the same edit as the quest-branch
portal with a different destination table, so the two ride together and the second copies the first.
Everything else is independent and can start today. Three tickets carry an owner decision - not a
technical blocker, a question only the owner can answer - and each names which decision and which
half of the ticket it gates.

**Grouping principle.** Rows share a ticket when they share a seam: the same archive and the same
merge shape, the same source file, the same subsystem, or the same owner decision. Rows that share
only a theme do not share a ticket. One row was explicitly kept out of its neighbour's ticket because
its evidence says so - a timed cash-slot expansion has no precedent anywhere in this repo, while its
neighbour, an NX credit, has two, and folding them would let the easy one carry the hard one.

**Sequencing recommendation.** Work the Evan route first (tickets 54 and 55): it is the only cluster
that currently stops a player mid-playthrough, and every other row is either invisible today or
latent. The one exception is the map-crash row, which stops *every* class on the fourth map of a
fresh character and should be worked as early as the owner can supply a client observation. Then the
string and quest data, which is mechanical and unblocks nothing but costs nothing. Then the Java
rows. The owner-gated tickets can be prepared - changeSet written, header reasoned - and held for
the decision.

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

### Quest rules

14. As a player, I want the 108 quests v84 capped to refuse to start once I am past their level cap,
    so that quest availability matches the game being matched.
15. As a player part-way through a repeatable request chain, I want its cooldown honoured, so that a
    repeatable quest behaves like one instead of being infinitely farmable.
16. As a player, I want date-retired quests to stay retired, so that dead content does not clutter
    live content.

### Data the server reads

17. As a player on the six Aquarium maps and the Sheep Ranch event map, I want every reactor v84
    places to be there, so that each map's reactor array is as long as v84's and the map plays as its
    own data describes.
18. As a player fighting mobs that use higher-level skills, I want those fourteen skill levels to
    exist, so that combat matches v84 rather than falling back on a lower tier.
19. As a player using either of two reward boxes, I want the full v84 reward table rolled, so that the
    44th entry is not silently unreachable.
20. As a player using Maker, I want the six recipes v84 added to be craftable, so that the Maker data
    is the v84 data.
21. As a player sitting on one of the four chairs v84 added, I want its own heal rate to apply, so
    that data v84 shipped to differentiate those chairs is not thrown away.

### Playing an Evan

22. As an Evan player, I want my three mount skills to render their mounts, so that riding shows a
    mount rather than nothing.
23. As a max-level Evan, I want my statue to appear in the Evan branch of the Hall of Fame, so that I
    am not filed under a default that belongs to nobody.

### Cash and items

24. As a player buying from the Cash Shop Surprise, I want the result to arrive, so that a gachapon
    pull is not a silent failure caused by two stale bytes.
25. As a player using either NX coupon, I want the stated points credited, so that a v84-new item does
    something when consumed.
26. As a player using the cash slot-expansion item, I want the seven-day expansion granted, so that a
    v84-new cash item is not inert.
27. As a player collecting monster cards, I want each card to name the mob its own item data names, so
    that the book and the drops agree.

### Playing at all

28. As any player on any class, I want to attack a monster on the first Maple Road hunting map without
    the client crashing, so that a fresh character can complete the first hunting quest in the game.

### Operating the server

29. As the server operator, I want every derived value to name the row it was copied from, so that
    recovered data, derived data and owner-directed overrides stay distinguishable a year from now.
30. As the server operator, I want the rows that cannot be answered from v84, and the rows that turned
    out not to be defects, recorded as closed rather than left open, so that they stop being re-filed
    as work.
31. As the server operator, I want the tempting-but-wrong rows written down by name, so that the next
    agent who spots the name link does not write the row.
32. As the server operator, I want defects that are real but not v84 parity ticketed and labelled as
    such, so that they are neither lost nor mistaken for parity work.
33. As the server operator, I want the two dead skill rows left behind by an old Evan login path
    removed, so that the database stops carrying rows nothing writes and nothing reads.
34. As the server operator, I want the unsourced-item list treated as a standing queue that is re-run
    rather than re-derived by hand, so that it shrinks by evidence instead of growing by invention.

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

**Array nodes are compared by name, never by position - and this rule has already caught a false
gap.** Storage order is not name order. The cash-shop commodity table is the case that proves it: its
children are array indices, not the shop numbers that identify a row. Compared by index it showed a
162-leaf gap; keyed on the shop number the values are not in the carve at all and our tree already
matches v84 on every leaf the cash shop reads. The row closed as a non-defect. Keying on the
identifying leaf is a stated acceptance criterion, not advice, and it is as much a guard against
inventing work as against writing the right value onto the wrong product.

**A merged node with no reader is still parity, and must be labelled as such.** Several leaves in
this work change no behaviour today: five of the fifteen quest date leaves have no enforcement path,
the Maker recipes are inert until the fetcher runs, and every one of the 31 reactor entries is on a
map that either routes nobody or holds a reactor with no script and no drops. They are merged for
parity and no ticket may claim a behaviour change it cannot demonstrate.

**A row is scoped out when merging it cannot work, not when it is inconvenient.** One mob skill is
excluded from its merge on exactly that basis: it is referenced by a live mob, but the server's skill
type enumeration has no constant for it, so the node would load into nothing.

**An empty leaf is data.** Dropping a present-but-empty node while copying a section is a deletion,
and the checks in this work distinguish "absent" from "present and empty". The converse also holds:
where the carve's value for a leaf is the same as the code's fallback for the leaf being absent,
merging it is a no-op and the row is dropped rather than counted.

**A present array index is not proof of equal content.** The coverage tool can see that our array is
shorter than v84's; it cannot see that index N holds the same thing on both sides. Where that
question matters, the precedent for resolving it is the whole-image terrain work already landed.

**Job branches name Evan; the job predicates stay untouched.** Evan falls out of magician branches
because the predicate compares a truncated job id. The predicate is load-bearing across the tree and
is left alone; Evan is named in each individual chain instead, which is the pattern a dozen sites
already follow. The AP-gain and AP-reset-floor sites have already been fixed this way and are the
worked precedent for the remaining ones.

**Version-gated protocol changes.** Mode bytes and enum values that moved between v83 and v84 go
behind a version guard using the existing helper, with the disassembly evidence recorded in the
javadoc beside the value - the same shape the already-landed mode fixes use.

**Database changes are additive by default, and the exception is argued from precedent rather than
waived.** Agents read the database and never write it. Corrections ship as new changeSets rather than
edits to frozen ones. One correction in this work is genuinely a set of UPDATEs against existing
data, and it ships because the table has no additive way to express "this row is wrong" - an INSERT
would leave two mobs named for one card - and because three earlier changeSets have already made
exactly this call. The other proposed UPDATE was withdrawn outright: its target quest does not exist,
and applying it would have made a live quest item permanently unlootable.

**No value gets invented to close a row.** Where a needed value cannot be derived, the row is a
research ticket, not an implementation ticket. Two rows are blocked on a client binary disassembly
that does not exist yet, and four close as permanent unknowns. Neither set gets a plausible
placeholder. The same rule closed a database repair that had no measurable damage to repair: no
correction ships against damage that cannot be shown to exist.

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

**Tests that pin the broken state must be found before the fix is written.** The portal work has
assertions across four test classes that currently assert today's wrong destination, and three of
those classes are outside the obvious invocation. A run that misses them looks green and breaks the
suite, so each ticket lists the tests its change invalidates alongside the tests it adds.

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
confined to the nodes the ticket claims. Ids our tree holds and the carve does not are protected
rather than reconciled away, because a wholesale reconciliation to the carve is a deletion of v83
content nobody asked to remove. Line endings matter - the map data in this tree uses a different
convention from the blobs, and a writer that forces the wrong one shows every line as changed and
buries the real edit.

**Acceptance criteria are objective or they are not criteria.** Every checkbox in every ticket is a
named test that passes, an exact node value present in the tree, a specific database row, an exact
byte, or an observable in-game behaviour. "Works correctly" appears in none of them.

**Negative assertions are first-class.** Several tickets are partly or wholly about what must *not*
appear: no drop row for a particular item, no shop stock for a particular NPC, no script file for a
particular hook, no static life row in the PlayerNPC band, no rows copied onto an unplaced dropper.
Those are asserted by count queries and by tests that pin absence, because a refusal nobody checks
quietly stops being a refusal.

**Regression surface to re-check on the data tickets.** Quest acceptance for the affected ids, map
load for the affected maps, and the existing terrain, portal-index and map-life parity suites, which
already cover the maps most of this work touches.

**Two rows cannot be closed by testing.** The map-crash row needs a client kill on the affected map;
the ferry needs a client click on the boat NPC. Both tickets state what an agent can prove offline and
stop there.

---

## Out of Scope

### Already landed - not remaining work

* **Evan's AP gains.** An Evan gained the wrong HP and MP per AP point because a job predicate
  compares a truncated id. Fixed, with the Evan job named in all four sites, and pinned by tests.
* **Evan's AP-reset floor.** Evan and the two beginner jobs had no HP/MP floor, so the illegal-swap
  guard could never fire. Fixed and corrected by a follow-up commit.
* **The persisted monster-card stat damage those fixes were expected to leave behind.** Measured
  against the live database, the character the row named has no card rows at all, and the one Evan
  that does shows no recoverable discrepancy. No correction ships; the row reopens only on a real
  observation.

### Closed by the audit - investigated, refused, and not to be re-filed

* **Cash-shop price and period, the largest number this spec used to carry.** Keyed on the shop
  number - the only valid key, which the ticket itself always said - the carve carries none of those
  values. The gap was produced by comparing two differently-ordered arrays by index. Our tree already
  matches v84 on every leaf the cash shop reads. There is nothing to merge.
* **The one NPC said to render MISSINGNO.** That id is inside the PlayerNPC band, takes its name from
  the database rather than the string archive, and is never rendered as a plain NPC. Merging the
  carve's static name would pin a fake name onto a slot the allocator hands to a real player.
* **The static life row on a Hall of Fame map.** Same id, same reason, and already refused
  permanently by an earlier ticket for the whole class.
* **The drop-row quest gate.** One of the two proposed UPDATEs targets a quest that does not exist;
  applying it would make a live quest item permanently unlootable. Withdrawn. Its sibling half is
  unshipped and needs its own row before it is worked.
* **One mob skill, excluded from the mob-skill merge.** It is referenced by a live mob, but the
  server's skill type enumeration has no constant for it, so merging the node would load into
  nothing.
* **Six map nodes dropped from the reactor ticket.** A rope array and three map flags are read by
  nothing anywhere in the tree; two entry hooks are present in the carve as the empty string, which
  the loader turns into the identical result it produces when the node is absent. Two further nodes
  were client-read rather than gaps and were never in scope.
* **The sourceless v84 pet.** v84 ships it with no drop, no shop, no reactor, no cash-shop and no
  quest grant. That is an answer, not a gap, and no row is added.
* **The contradiction over one unplaced mob's drop table.** The older changeSet's refusal was scoped
  to a different body of work, so the two changeSets never actually disagreed. The rows stay - all
  seventeen of them, not the six the row named - and the older note is annotated rather than
  reverted.

### Rows that are already v84-correct - no ticket, nothing to build

Six rows were investigated and need no work. They are listed here so they stop being re-filed.

* **The unread pickup-only item flag on 83 items.** Every affected item is already covered by
  something else: 63 carry a consume-on-pickup flag and are destroyed before they reach inventory, so
  enforcement would be a no-op; the other 20 are trade-blocked. The five v84-new ones work already.
* **The claimed unhandled equip-stat fields.** Two of the five are Maker reagents and are
  handled after a rename the Maker path already performs; one is skipped deliberately; one is a cash
  item that never routes through that code. Only a craft field on two tablet scrolls genuinely falls
  through, and no craft field exists on equipment because the profession system is post-v83 content.
* **"328 quests with no script".** 327 of the 328 carry an end date in the past - the
  latest is mid-2010, all dead before the v84 client shipped - and the date requirement refuses them
  before anything else is consulted. The one survivor's start NPC is placed on none of the 5,338 map
  images. None is an Evan quest, and a missing quest script fails softly with a warning. Two wording
  corrections belong to the tracker, not to code: the discriminator is "non-medal", not "non-Evan",
  and the medal-quest count is 39.
* **The dead-portal and silent-map figures.** The numbers do not reproduce and the corrected
  ones are benign: none of the dead portals carries a real destination and none is a warp type, so the
  fallback branch a prior ticket worried about has no instance in this map data. The town cases are
  cosmetic unity portals on six major towns. The Evan cases are already inside two other rows.
* **A map's unimplemented entry hook.** Our node is byte-identical to v84's, hook included. The
  name is shared with a v83 cave family whose maps have no scripts either and which works. The claim
  that the map is unreachable is wrong - a live portal warps there for anyone on the relevant quest,
  and the quests around it work today.
* **A map with no inbound portal.** Deliberate and already handled: it is a client-side cutscene
  map entered by a client-side warp, like its whole band, and both halves are already explained in the
  code that handles them.

### Not v84 parity - ticketed as v83 legacy, deliberately not built as parity work

Ten rows are real defects with zero add-list rows for the thing that is broken, meaning v84 added
nothing to them. They are ticketed (71, 73, the legacy rows of 72, and the surviving row of 66) so
their known fixes are not rediscovered, and every one of those tickets says in its first line that it
is v83 legacy. They are **not** part of v84 parity and must not be scheduled as if they were: five
chaos scrolls that burn the upgrade slot; seven potions whose percentage half is dropped; six
battlefield-skill items wired to nothing; a damage-over-time effect that was commented out on purpose
upstream in the same commit as the health overhaul that would have made it abusive; a hero-buff bonus
that never reaches local stats; seven stale monster-card rows; packet-validator coverage; an
in-memory de-duplication that re-warns once per restart; an unimplemented v83 event NPC; and one
tracker claim about endgame weapons that is refuted on all three of its assertions and closes as a
restatement.

**The chairs are no longer on this list.** An earlier revision put them here on the claim that they
had zero add-list rows. They have five, and four of those are whole-new v84 chairs carrying the very
heal-rate nodes the row is about. The chairs are v84 parity, in scope, and need no scope waiver - the
old tracker was right and the correction is one of the more important things the audit produced.

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

### Evan skill semantics - one established server correction

The Evan skill audit distinguishes a missing server effect from a client-computed calculation. This
matters because forcing a server implementation onto the latter creates a second, guessed combat
model instead of parity.

**Soul Stone is the one established server correction.** Its v84 skill data defines a timed, limited
party resurrection safeguard. The existing implementation instead revives every eligible party member
who is already dead at cast time. A cast must protect eligible living party members for its duration;
a qualifying later death consumes one of the skill's stated uses and revives that member at the stated
amount. The player must not receive an immediate resurrection simply because they were dead when the
skill was cast.

**Out of scope until evidence changes:** Magic Resistance needs the v84 incoming-element-byte mapping,
which the WZ archives do not contain; Dragon Fury, Magic Mastery's mastery half, Critical Magic's
chance/damage values, and Meteo Shower are client-computed damage mechanics with no independent
server calculation to add; Recovery Aura's exact HP/MP formula is not expressed by its node; and the
three invisible Weakness skills expose only their `mobCode`, not enforceable server semantics. These
are not placeholders for guessed behaviour. Existing generic effects remain in place.

---

## Further Notes

**Three owner decisions gate parts of this work,** down from six: three of the original six were
answered from the data during the audit and two were closed by the fixes that landed. None of the
three blocks the majority of any ticket, and each is named in the ticket that needs it: which room
the island's shared cave door uses as its fallback; whether the v83-legacy effect rows may be worked
at all, which is a scope waiver rather than a technical question; and the balance calls inside those
rows, where the plumbing does not exist and no precedent says what the number should be. The tickets
can be written, reasoned and prepared without the answers; they cannot be applied without them.

**Two artifacts are missing and no amount of effort in this repo produces them.** A v84 client
disassembly would settle the element mapping behind a buff that is written and never read, and the
failure-reason table that is currently v83's values carried over untested. Both are recorded as
research with the question stated and the artifact named, so neither is mistaken for a small Java
fix.

**One ticket in this set exists only to hold its own refusal.** The cash-shop price row is now a
document explaining why there is nothing to merge and recording the measurements, so the same wrong
key never produces the same phantom gap again. A closed ticket that is kept is cheaper than a row
that gets re-filed every quarter.

**The reactor merge raises a question it deliberately does not answer.** The reactor those twelve
Aquarium entries would add matches the quest text for an Evan quest whose drop row currently sits on
a differently-named reactor. That is a question for whoever owns the changeSet in question. The
reactor ticket adds the nodes and nothing else; if the changeSet is corrected later, those rows stop
being inert.

**Four sweeps are complete and should be cited rather than repeated.** The v84 coverage matrix, the
quest sweep, the quest-dropper sweep and the item-source sweep between them have already answered
"what did v84 add", "does every added quest work", "does the dropper live where the quest sends you"
and "does every added item have a source". Each is regenerable by script. Re-deriving any of them by
hand has happened before and produced numbers that disagreed with the tooling; the tooling was right.

**Counts are the thing this document has been wrong about most often.** The audit corrected numbers
in every one of the twenty tickets, and this revision carries the corrected ones: 123 quest leaves,
not 122; fourteen mob-skill levels, not fifteen; 31 reactor entries across seven maps, not forty
nodes; seventeen rows on the unplaced mob, not six; four v84-new chairs, not zero. Where a ticket and
a tracker disagree on a count, the ticket has been measured and the tracker has not.

**One row is a queue, not a task.** 127 v84-new items have no source of any kind. It has zero live
blockers, no new drop row is proposed by the sweep, and none should be added from it. It shrinks as
individual entries are established and is re-run rather than re-derived.

**Restatements land in the narrative tracker only.** Where a ticket corrects a claim rather than
shipping code, the correction is the deliverable - and the coverage files, the work rows, the status
page and the ticket ledger are owned by other agents and are not edited by implementing agents.

**The server is live and the owner plays on it.** Never restart it, never kill it, never launch a
client. Packet visibility toggles live through an in-game command, so no restart is needed for it -
check that before anyone schedules one.
