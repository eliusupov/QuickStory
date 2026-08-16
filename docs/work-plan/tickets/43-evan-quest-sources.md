# 43 — Evan quest item sources: what the client names, what the server can serve

**Status:** applied. changeSet **156** (not yet run — needs a server restart), 14 `wz/` map files,
one new NPC script, four deny-list roots, one new `*RealLoad` test.

Follows ticket 42 (measurement) and changeSet 155 (the first fix, quest 22004). Same method:
**the v84 client data names the source in plain words; find it, prove it spawns, copy a rate that
already exists on that mob.** Nothing here is a reconstruction of a Nexon number.

---

## The correction that matters most

Ticket 42 asked *"does a `drop_data` row exist for this item?"*. That is not the same question as
*"can this quest be finished?"*, because a drop row carries a `questid` and
`Character.needQuestItem` (`Character.java:5810-5831`) refuses the pickup for **every quest but that
one**. Two consequences, both measured:

- **22407 was reported sourced and is not.** `4032475` Lycanthrope Leather has had two rows since
  the original seed — `(8140000, 4032475, 1,1, 28344, 200000)` and the same on `9500134` — but both
  are gated to quest **28344**, so a player on 22407 watches the item drop and cannot loot it.
- **22529 was not reported at all.** Re-walking `QuestInfo.img` area 7 against
  `drop_data`/`reactordrops`/`shopitems` *with the quest gate applied* turns up an eighth dead
  quest: 4032460 "Refreshing Stump Sap", no source anywhere, and its start NPC unplaced as well.

So the headline count is **8 quests fixed, not 7**, and two of the nine ticket 42 listed are not
drop problems at all (below).

---

## Per quest

| quest | item | source, and where the client says so | spawns? | rate | rate came from |
|---|---|---|---|---|---|
| 22407 | 4032475 ×10 | mobs **8140000 / 9500134** "Lycanthrope" | 8140000: 211040800/900, 211041000 ×3 each. **9500134 spawns nowhere — that row is inert** | 200000 | the same item's existing rows on the same mobs (only `questid` differs) |
| 22407 | 4032476 ×2 | reactor **2302001**, whose own `info` string is `심해보물상자` = "deep sea treasure chest" (`Reactor.wz/2302001.img.xml:4`); QuestInfo:7356 "Shipwreck Treasure Chests deep in the oceans of Aquaroad" | 230040400 "The Grave of a Wrecked Ship" ×7, +21 more across 230040xxx | 5 | quest 3083 "Kenta's Advice" — same NPC (2060005), same chest family, 5 count-1 items, all `chance 5` |
| 22410 | 4032504 ×10 / 4032505 ×2 | same two sources; QuestInfo:7389 says "the **same materials as before**", and the String.wz name+desc pairs are byte-identical to 4032475/4032476 | as above | 200000 / 5 | as above |
| 22524 | 4032459 ×1 | mobs **2220100 + 2220110**; QuestInfo:7697 "hunt 100 #o2220100#s and retrieve the #t4032459#" | 2220100 on 10 maps. **2220110 not placed — see "refused" below** | 40000 | Aran twin 21717/21718: same shape (merge-id counter, retrieve 1 puppet), rows on **both** base and variant, all 40000 |
| 22529 | 4032460 ×3 | mob **130100** "Stump"; QuestInfo:7732 "some #t4032460# **dropped by the #o0130100#s**" | 17 maps incl. all five the quest sends you to | 80000 | this mob's four authentic-GMS quest rows are all 80000 — the same number changeSet 155 chose |
| 22531 | 4032461 ×1 | mob **2230131** "Annoyed Zombie Mushroom"; Check:27456 counts 100 of it | 105050300 ×17 (the map the quest names) | **20000** | `(2230131, 4032321, 1,1, 21727, 20000)` — same mob, same item concept, and 21727 is literally *"your fourth assignment as an informant"* against Evan's *"A Guard's Fourth Assignment"* |
| 22532 | 4032462 ×1 | mob **2230112** "Terrified Wild Boar"; Check:27495 counts 100 of it | **was not placed at all — fixed here** | 20000 **DERIVED** | no row on this mob and no Aran twin; copied from 22531, the previous quest in the same chain, whose 20000 is measured. One hop from data, marked as such |
| 22548 | 4032463 ×1 | mob **3110100** "Ligator"; QuestInfo:7888 "recover the Clue ... from the #o3110100#s" | 107000000-107000300, 88 total | 300000 | this mob's only authentic-GMS quest row, `(3110100, 4031164, 1,1, 2084, 300000)`. **Caveat stated in the SQL:** 2084 wants 10 of its item and 22548 wants 1, so the single document comes fast |
| 22559 | 4032466 ×1 | mob **9300387 "Enraged Golem"** — ticket 42 could not find it and guessed 5130101/5130102; that guess is wrong and unused | 910600000 ×1, 910600010 ×3, `mobTime -1` (force-spawn once, `MapFactory.java:120`). 910600010 is reachable — ticket 08 already merged the `evanDollGR` portal | 999999 | Aran twin 21731 "Eliminate the Puppeteer!": `(9300344, 4032322, 1,1, 21731, 999999)` — quest-exclusive 93003xx mob in a hidden quest map, single culmination item |

**Rate ladder, stated once and applied per row** (each row in `156-evan-chain-drop-data.sql` names
the row it copied): R1 the same mob's own quest-gated row for a comparable GMS-era item — changeSet
155's rule; R2 the same quest re-skinned, i.e. the Aran informant chain, which is the Evan "Guard's
Assignment" chain with different names, same mobs, same ordinals; R3 the nearest sibling inside the
Evan chain, marked DERIVED.

**The true GMS v84 rates are not recoverable.** They were server-side and are in no WZ file, in any
version. Every number above is a copy of a number this server already uses, not a reconstruction.

## The two that are NOT drops

**22408 — item 4032497 "Potter".** Not a drop; the item *is* the man ("A master craftsman in Herb
Town. He is as light as a feather"). NPC **2092101 "Potter"** is already placed on 925110000 "Pirate
Treasure Vault" — in this tree *and* in stock v84, identical — and the way in already exists,
because ticket 08 merged `Map2/251010403.img/portal/4` (`script = enterPottery`) and wrote
`scripts/portal/enterPottery.js`. The only missing piece was that 2092101 had **no NPC script**, so
clicking him did nothing. Fixed as `scripts/npc/2092101.js`, modelled on the existing
`scripts/npc/1040000.js`. Dialogue provenance is in the file header: `Quest.wz/Say.img` has no node
for 22408 or any 224xx quest, so the lines are 2092100's own String.wz text with one written
connecting sentence, marked as written.

**28351 — items 4000566-4000571.** Not fixable, and must not be faked.
`Check.img.xml:40240` carries `end = "201005050000"`: this is a GMS launch-celebration quest that
**expired on 2010-05-05**. Its six symbols come out of item **2022662 "Evan's Paper Box"** ("a gift
celebrating Evan's launch"), whose `reward` node already lists all six
(`Item.wz/Consume/0202.img.xml:19842-19878`) and which the server already implements
(`ItemRewardHandler.java:56`). The box itself was handed out server-side by GMS and appears in no
client file, no shop, no drop, no reactor. **No rows were added.** The new test asserts changeSet
156 contains no row for those six ids, so a future "fix" that fabricates one fails.

## The gap changeSet 156 cannot close — 22524 needs three lines of Java

`Check.img.xml:27209-27213` makes 22524 require **100 kills of mob 9101004**. That mob has no
`Mob.wz` image in either tree: it is one of Nexon's "merge" ids that stands for two real mobs at
once, and `Character.java:7440-7448` already documents and implements exactly that mechanism for
three others:

```
GREEN_MUSHROOM (1110100) | DEJECTED_GREEN_MUSHROOM (1110130) -> 9101000
ZOMBIE_MUSHROOM (2230101) | ANNOYED_ZOMBIE_MUSHROOM (2230131) -> 9101001
GHOST_STUMP (1140100)    | SMIRKING_GHOST_STUMP (1140130)    -> 9101002
```

9101004 is named "Blue Mushroom" in `String.wz/Mob.img.xml:102-104` and its two members are
2220100 Blue Mushroom + 2220110 Crying Blue Mushroom — the same base+variant pairing. The fix is
one constant block in `constants/id/MobId.java` and one `else if` in `Character.java:7442-7448`.
**`src/main/java/**` is another worker's file set, so this is reported, not edited.** Until it
lands, 22524 has its item but its kill counter cannot move.

## Placements

All hand-authored as **pure appends** into `wz/Map.wz` life arrays. `WzMerge` refuses life merges
by design (life is a positional array, procedure §4.4) and that refusal is correct — the procedure's
own answer is to re-author against this tree, which is what happened. 486 insertions, **0 deletions**.

| what | maps | note |
|---|---|---|
| mob 2230112 ×24 | 101030001 | **deliberate deviation, read this** ↓ |
| npc 2092100 Potter | 251000000 | gates 22409. **v84's `fh=187` is wrong here** — see below |
| npc 1022106 Christopher | 106000000/100/200 | gates 22529 |
| npc 1011101 General Mau | 100000100 | |
| npc 1022107 Warning Post | 101030000/100/200/300/400 | signpost |
| npc 2030015 Hidden Rock | 211040400 | 22576's end NPC. **v84's `fh=102` is wrong here** |
| npc 9010012 Star Pixie | 200010000 | |
| npc 9010013 Hengki | 240010200 | |

**Footholds are a positional array too, and v84 renumbers them.** A life entry's `fh` is an index
into that map's own foothold table, and it is written straight into the spawn packet
(`PacketCreator.java:1402`). Copying v84's index blind puts the NPC on the wrong platform. Two of
the eight maps had moved:

- `251000000`: v84 `fh 187` is `(291,77)-(304,78)` in this tree — ~1,700px from where Potter
  stands. The platform he is actually on is **fh 21** `(-1426,238)-(-1108,238)`, a unique match.
- `211040400`: v84 `fh 102` is `(-96,-297)-(-79,-292)`; the right one here is **fh 100**
  `(-402,-267)-(-192,-281)`.

Every other index matched exactly. All 38 appended entries are asserted against this tree's own
foothold table by `EvanQuestSourcesRealLoad.everyPlacedLifeEntrySitsOnAFootholdThatExistsHere`.

### The 101030001 deviation — flag this to the owner

v84 did not *add* Terrified Wild Boars to "The Land of Wild Boar II"; it **replaced** the map's
life array. v84: 24×2230112 + 1×2230102 + 1×2130100 = 26 entries. This tree before: 30×2230102 +
3×2130100 = 33. The additive-only rule forbids deleting the 30 v83 Wild Boars, so the append leaves
the map at **57 entries** — a layout neither version shipped, and roughly double v84's density.

That was the only way to make quest 22532 possible at all without a deletion. **Exact v84 parity
needs the 30 v83 boars removed, which is a deletion and therefore the owner's call, not mine.**

## What was refused, and why

- **2220110 Crying Blue Mushroom (106010000, 106010100)** — not placed. Same replacement shape as
  above (v84 cut Horny Mushrooms 19→1 and 14→3 on those maps), so an append would take them to 57
  and 45 entries against v84's 44 and 32. And it buys nothing: 22524's doll now drops from 2220100,
  which spawns everywhere, and its kill counter needs the Java fix regardless. Density change to two
  live training maps for zero unblocking is not worth taking unilaterally.
- **9200018 Jr. Yetti / 9200019 White Fang (196000000)** — not placed. This is a straight **id
  substitution**: v84 has 22×9200018 + 1×9200019 and this tree has 22×5100000 + 1×5140000, same
  counts. Appending would double the map. It is a rename, not a gap, and doing it right means a
  replace.
- **Nothing for 28351**, per above.
- **NPC 1013000 (Mir) and 1013202 (Shammos)** — placed on no map in **either** tree, so no placement
  can fix them; they are script-summoned. Mir alone is the start NPC of ~30 area-7 quests, which
  makes him the largest single Evan blocker left. Out of this ticket's file set.
- **NPC 9901000**, quest 22402's start NPC — a PlayerNPC display slot. Placing it is exactly what
  the deny-list forbids. 22402 is unblockable by design here.
- Items **4032467** (22562, comes from Mir), **4032470** (22572, Florina Beach NPC), **4032471**
  (22576, granted by Shammos), **4032472** (22586, from Captain Hwang) — all NPC-sourced, none of
  them drops. No rows invented.

## Deny-list — Hazard 1b

`PlayerNPC.java:66-67` claims 9901910-9906599; the **code** says the band starts lower:
`NpcId.java:38  PLAYER_NPC_BASE = 9900000`, `PlayerNPC.java:321-323  branchSid = base + branch*100`,
`GameConstants.java:386  branch = 26 + 4*(mapid/100000000)` → max branch 62 → 9906600 exclusive.
So the reserved range is **9900000-9906599 plus 9977777** (`NpcId.CUSTOM_DEV`).

`grep -rn '99[0-9]\{5\}' docs/wz-baseline/add-list/` → 58 rows. 17 are out-of-band lookalikes,
10 are the existing `NpcLocation` deny rows, 20 name whole nodes this tree already has (the additive
gate refuses those on its own), and **11 sat below the 9901910 floor with nothing covering them** —
all of them *interior writes* into live PlayerNPC slots, which is precisely the §4.5 shape the gate
is blind to. Four new deny roots close them: `Npc.wz/9900000.img`, `Npc.wz/9900001.img`,
`Npc.wz/9901000.img`, `String.wz/Npc.img/9901000`. 188 → **192 roots**.

The 149 PlayerNPC rows ticket 42 found in 9901000-9901849 are `Map.wz` **life** entries; a life slot
has no id in its path, only an index, so no deny root can name one. They are refused structurally by
the positional-array gate, and that refusal must be left standing.

**Measured before and after**, same three-row probe, same dry run, only the deny file differs:

```
--deny <HEAD's 188-root list>   ADD   Npc.wz/9900000.img/info/reg/name1 -> wz\Npc.wz\9900000.img.xml:6
                                added 1 (forced 0), refused 2      exit 3
--deny <this ticket's 192>      SKIP  DENIED by deny-list [Npc.wz/9900000.img]
                                added 0 (forced 0), refused 3      exit 5
```

The gap was not theoretical: that row **merged**, silently, into a live PlayerNPC display slot. (The
other two rows happened to be refused anyway on an unrelated shape technicality — `parent '0' is not
an <imgdir>` — which is luck, not policy; they are now refused by decision.)

## Verification

- `WzMerge selftest` → **38 PASS / 0 FAIL / 7 intentional SKIP**, exit 0.
- changeSet 156 applied to a throwaway schema cloned from live `cosmic`: 13 rows land
  (23,014 → 23,025 `drop_data`, 1,116 → 1,118 `reactordrops`); the `<rollback>` restores both counts
  exactly. Schema dropped. **Live `cosmic` was not written to.**
- All 14 modified `wz/` files: XML well-formed, CRLF preserved, no BOM, life arrays consecutive
  `0..n-1` — 486 insertions, 0 deletions.
- `mvnw.cmd -o test -Dtest=EvanQuestSourcesRealLoad` → **12 tests, 0 failures**.

## Restart

changeSet 156 runs through Liquibase at server start. **Nothing in this ticket is live until the
server is restarted** — the DB rows, the map placements (Map.wz XML is read at map load, but the
running server has these maps cached) and the new NPC script all need it.
