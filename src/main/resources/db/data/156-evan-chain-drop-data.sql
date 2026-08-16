-- ============================================================================================
-- Evan quest chain - the drop/reactor sources for 8 quests that are dead on a required item:
-- 22407, 22410, 22524, 22529, 22531, 22532, 22548, 22559.
-- Companion to changeSet 155 (quest 22004 / Thick Branch), same method, same discipline.
--
-- 11 drop_data rows + 2 reactordrops rows. Additive only; touches no applied changeSet.
--
-- Quest 22529 is an EIGHTH quest, not in the parity ticket's list of nine. The ticket's tool asks
-- "does a drop row exist for this item", which cannot see a row gated to the WRONG quest, and it
-- did not walk area 7 exhaustively. Re-walking Quest.wz area 7 against drop_data/reactordrops/
-- shopitems WITH the questid gate applied found 22529 (4032460, no source at all) and confirmed
-- 22407's 4032475 as blocked-despite-having-rows. Both are fixed below.
--
-- ============================================================================================
-- WHAT IS NOT IN HERE, AND WHY  (read this before assuming something was missed)
-- ============================================================================================
--   * quest 22408 (item 4032497 "Potter") is NOT a drop. NPC 2092101 "Potter" is already placed
--     on map 925110000 "Pirate Treasure Vault" in BOTH trees (wz/Map.wz/Map/Map9/925110000.img
--     .xml life, and the same slot exists in stock v84), and the map is reachable - ticket 08
--     already merged Map2/251010403.img/portal/4 (script "enterPottery") and wrote
--     scripts/portal/enterPottery.js. The gap is that NPC 2092101 has NO scripts/npc entry, so
--     talking to him does nothing. Fixed as scripts/npc/2092101.js, not as a drop row.
--   * quest 28351 (items 4000566-4000571) is NOT a drop either, and is NOT fixable at all.
--     Quest.wz/Check.img.xml:40240 carries `end = "201005050000"` - it is a GMS launch-event
--     quest that EXPIRED 2010-05-05. Its items come out of item 2022662 "Evan's Paper Box"
--     ("A paper box containing a gift celebrating Evan's launch"), whose `reward` node already
--     lists all six (wz/Item.wz/Consume/0202.img.xml:19842-19878) and which the server already
--     implements (ItemRewardHandler.java:56 -> ItemInformationProvider.getItemReward). The box
--     itself was handed out server-side by GMS and appears in NO client file, no shop, no drop
--     and no reactor. Inventing a source for it would be inventing content. Left as a gap.
--   * quest 22524 gets its drop rows here, but ALSO needs a one-line server change that is out
--     of this changeSet's scope - see the 22524 block below.
--
-- ============================================================================================
-- THE EVIDENCE, PER QUEST. Every dropper below was checked to actually SPAWN.
-- ============================================================================================
-- Spawn proof is wz/Map.wz life entries in THIS repo's tree (the tree the server loads):
--     2220100 Blue Mushroom          106010000 x26, 106010100 x9, +8 more maps
--     2220110 Crying Blue Mushroom   NOT PLACED on this server yet - see the note under 22524
--     2230131 Annoyed Zombie Mushroom 105050300 x17 (the map quest 22531 names), +3 more
--     2230112 Terrified Wild Boar    NOT PLACED on this server yet - see the note under 22532
--     3110100 Ligator                107000000 x32, 107000100 x24, 107000200 x24, 107000300 x8
--     9300387 Enraged Golem          910600000 x1, 910600010 x3   (mobTime -1 = force-spawn once,
--                                    MapFactory.java:120-121; both maps identical in v84)
--     8140000 Lycanthrope            211040800 x3, 211040900 x3, 211041000 x3
--     9500134 Lycanthrope            spawns on NO map - see the note under 22407
--     2302001 Deep Sea Treasure Chest (reactor) 230040400 "The Grave of a Wrecked Ship" x7,
--                                    plus 230040000/100/200/300 x5 each and 230040410 x1
--
-- ============================================================================================
-- RATES. The true GMS v84 numbers are NOT RECOVERABLE.
-- ============================================================================================
-- GMS drop rates were server-side and appear in no WZ file, in any version. So no number below
-- is a reconstruction of Nexon's value and none is presented as one. Each is COPIED from a row
-- that already exists in this server's drop_data, chosen by this ladder, and each row names the
-- row it copied:
--
--   R1  the same MOB's own quest-gated row for a comparable GMS-era quest item.   (155's rule)
--   R2  the same QUEST re-skinned. The Aran "informant" chain and the Evan "A Guard's Nth
--       Assignment" chain are the same content with different names: same mobs, same "eliminate
--       the monsters and retrieve the puppet" shape, same ordinals (Aran's 4th assignment 21727
--       and Evan's 4th assignment 22531 are both the Annoyed Zombie Mushrooms), same era.
--   R3  the nearest sibling quest inside the Evan chain itself, when neither R1 nor R2 exists.
--       Marked DERIVED where used - it is one hop further from measured data than R1/R2.
--
-- Reminder on what `chance` means, so the numbers can be sanity-checked:
--   drop_data.chance   -> MapleMap.java:783  Randomizer.nextInt(999999) < chance*multipliers,
--                         and ETC items get x2.0 (MapleMap.java:722-728).
--   reactordrops.chance-> ReactorActionManager.java:230  Math.random() < dropRate/chance,
--                         i.e. a 1-in-N DENOMINATOR. Lower is more common. Opposite direction.
--
-- questid on every row makes the drop quest-gated: Character.java:5810-5831 needQuestItem() only
-- lets the item be picked up while that quest is in progress AND the player holds fewer than the
-- required count, so none of this can leak into the economy.

-- --------------------------------------------------------------------------------------------
-- 22524 "Strange Puppet"  ->  4032459 Blue Mushroom Doll x1
-- --------------------------------------------------------------------------------------------
-- Named source: QuestInfo.img.xml:7697 - "hunt #r100#k #r#o2220100#s#k and retrieve the
-- #b#t4032459##k". #o2220100# = mob 2220100 "Blue Mushroom" (String.wz/Mob.img.xml:195-197).
-- Act.img.xml:4143-4145 - node "0" (start) is EMPTY, so the quest grants nothing; node "1"
-- removes 4032459 (count -1). It has to come from the world.
--
-- Two droppers, not one, and that is copied not guessed. Check.img.xml:27209-27213 counts kills
-- of mob 9101004, which has NO Mob.wz image in either tree - it is one of Nexon's "merge" ids
-- that stands for two real mobs at once, exactly as Character.java:7440-7448 already documents
-- for 9101000/9101001/9101002. 9101004 is named "Blue Mushroom" (String.wz/Mob.img.xml:102-104)
-- and its two members are 2220100 Blue Mushroom + 2220110 Crying Blue Mushroom - the same
-- base+variant pairing as 9101001 = 2230101 + 2230131. The Aran twin of this exact shape,
-- quests 21717/21718 (counter 9101000, retrieve 1 puppet), carries FOUR rows in drop_data:
--   (1110100, 4032317, 1,1, 21717, 40000)   (1110130, 4032317, 1,1, 21717, 40000)
--   (1110100, 4032318, 1,1, 21718, 40000)   (1110130, 4032318, 1,1, 21718, 40000)
-- i.e. base AND variant, same chance. R2 -> 40000 on both 2220100 and 2220110.
-- (R1 was rejected on purpose: 2220100's own quest rows are 4001351/q28237/200000 and
--  2022016/q8164/100000, and q28237 "The Legendary Revival" is a later non-v84 event chain, so
--  it is a worse match than the Aran row despite being on the same mob.)
--
-- *** 22524 IS STILL BLOCKED AFTER THIS CHANGESET, and not by data. ***
-- The kill counter needs mob 9101004 to be raised, and nothing raises it. The fix is the same
-- three lines the server already has for the other three merge ids:
--     constants/id/MobId.java  + BLUE_MUSHROOM=2220100, CRYING_BLUE_MUSHROOM=2220110,
--                                BLUE_MUSHROOM_QUEST=9101004
--     client/Character.java:7442-7448  + one `else if` raising BLUE_MUSHROOM_QUEST
-- That file is owned by another worker; it is reported, not edited here.
INSERT INTO drop_data (dropperid, itemid, minimum_quantity, maximum_quantity, questid, chance)
VALUES (2220100, 4032459, 1, 1, 22524, 40000),
       (2220110, 4032459, 1, 1, 22524, 40000);

-- --------------------------------------------------------------------------------------------
-- 22529 "Helping Beginner Adventurer Christopher"  ->  4032460 Refreshing Stump Sap x3
-- --------------------------------------------------------------------------------------------
-- Named source, in the plainest words of any quest in this file: QuestInfo.img.xml:7732 - "He
-- asks that you bring him some #t4032460# dropped by the #o0130100#s. Defeat the #rStumps#k and
-- get some #b#t4032460##k for him." #o0130100# is mob 130100 "Stump" (map life nodes zero-pad
-- ids to 7 chars). String.wz/Etc.img.xml:9345-9348 - 4032460 is "Refreshing Stump Sap", "Tree
-- sap from a Stump." Check.img.xml:27361-27366 wants 3 of them and no mob kills.
--
-- This is the SAME MOB and the SAME SHAPE as changeSet 155's quest 22004 (3x 4032498 "Thick
-- Branch" from 130100), so the rate is the one 155 already established and the one all four of
-- 130100's authentic-GMS quest rows carry:
--   (130100, 4032498, 1,1, 22004, 80000)  (130100, 4031773, 1,1, 2145, 80000)
--   (130100, 4032374, 1,1, 2405, 80000)   (130100, 4032378, 1,1, 2408, 80000)
-- (its fifth quest row, 4001358/q28248/20000, belongs to a later non-v84 event chain and is not
-- used as the precedent.) R1, and consistent with the changeSet that is already applied.
--
-- Stump is everywhere: 100050000 x31, 101040000 x32, 101030000 x26, 102010000 x22 and 13 more
-- maps, including 101030000-101030400 where quest 22529 sends you. Reachability is not in doubt.
--
-- 22529 was ALSO blocked by its start NPC: 1022106 Christopher was placed on no map. That half
-- is fixed in the same commit, in wz/Map.wz/Map/Map1/106000{0,1,2}00.img.xml - the three maps
-- the quest text names ("Patrol the #m102000000# Warning Street #b#m106000000#, 2, and 3#k").
INSERT INTO drop_data (dropperid, itemid, minimum_quantity, maximum_quantity, questid, chance)
VALUES (130100, 4032460, 1, 1, 22529, 80000);

-- --------------------------------------------------------------------------------------------
-- 22531 "A Guard's Fourth Assignment"  ->  4032461 Zombie Mushroom Doll x1
-- --------------------------------------------------------------------------------------------
-- Named source: QuestInfo.img.xml:7755 - the objective line ends "#o2230131##r #a225311##k",
-- and Check.img.xml:27456-27460 requires 100 kills of mob 2230131 "Annoyed Zombie Mushroom"
-- (String.wz/Mob.img.xml:51-53). Act.img.xml:4255-4257 node "0" is EMPTY; node "1" removes it.
--
-- Rate: R1 AND R2 agree, which is as strong as this gets. drop_data already holds
--   (2230131, 4032321, 1, 1, 21727, 20000)
-- - same mob, same item concept (4032321 is "Annoyed Zombie Mushroom Doll"), same count (1),
-- and quest 21727 is literally the Aran fourth assignment: QuestInfo.img.xml:6285 "This is your
-- fourth assignment as an informant ... the erratic behavior of the #o2230101#s in #m105040300#".
-- Evan's 22531 is the same sentence with Evan's names. 20000 is therefore this mob's own
-- established rate for exactly this item, in exactly this quest slot.
--
-- Dropper is the VARIANT (2230131), not the base (2230101), because that is what the Aran row
-- does and because Check.img requires kills of 2230131 specifically. No row is added for
-- 2230101 - that would be an invented second source.
INSERT INTO drop_data (dropperid, itemid, minimum_quantity, maximum_quantity, questid, chance)
VALUES (2230131, 4032461, 1, 1, 22531, 20000);

-- --------------------------------------------------------------------------------------------
-- 22532 "A Guard's Fifth Assignment"  ->  4032462 Wild Boar Doll x1
-- --------------------------------------------------------------------------------------------
-- Named source: QuestInfo.img.xml:7762 - "defeat the #o2230112#s and retrieve the #bPuppet#k",
-- and Check.img.xml:27495-27499 requires 100 kills of 2230112 "Terrified Wild Boar"
-- (String.wz/Mob.img.xml:4686-4688). Act.img.xml:4280-4282 node "0" is EMPTY.
--
-- THIS QUEST WAS BLOCKED TWICE. 2230112 was placed on no map in this tree; stock v84 places 24
-- of them on 101030001 "Wild Boar Land". The placement half is fixed in the same commit as this
-- file, in wz/Map.wz/Map/Map1/101030001.img.xml.
--
-- Rate: DERIVED (R3). 2230112 carries no quest-gated row of its own, and the Aran chain has no
-- Wild Boar assignment, so there is no R1 and no R2. The value is copied from this chain's own
-- immediately preceding quest, 22531 above, whose 20000 IS measured (R1+R2). One hop from data,
-- and it is marked as such rather than dressed up: the two quests are consecutive, identical in
-- shape (1 doll + 100 kills of a "strange" variant mob) and identical in reward scale
-- (Act.img.xml exp 5100 vs 5750).
INSERT INTO drop_data (dropperid, itemid, minimum_quantity, maximum_quantity, questid, chance)
VALUES (2230112, 4032462, 1, 1, 22532, 20000);

-- --------------------------------------------------------------------------------------------
-- 22548 "Clue about the Thief"  ->  4032463 Document with Clue x1
-- --------------------------------------------------------------------------------------------
-- Named source: QuestInfo.img.xml:7888 - "the clue was lost on it's way to #m103000000# because
-- a #o3110100# attacked. She asks that you recover the #bClue#k about the thief from the
-- #r#o3110100#s#k". #o3110100# = "Ligator" (String.wz/Mob.img.xml:267-269). No kill counter -
-- Check.img.xml:28017-28024 asks for the item only. Act.img.xml:4463-4465 node "0" is EMPTY.
--
-- Rate: R1. 3110100's own quest-gated rows are
--   (3110100, 4031164, 1, 1, 2084, 300000)   quest 2084 "Icarus and the Balloon", authentic GMS
--   (3110100, 4031405, 1, 1, 8732, 500000)   quest 8732 has NO QuestInfo entry at all -> a
--                                            non-GMS addition, rejected as a precedent
-- so 300000 is this mob's only authentic-GMS quest-item rate and is what is copied.
-- Stated caveat, because it is a real difference and hiding it would be dishonest: 2084 asks for
-- TEN 4031164 and this quest asks for ONE 4032463, so at the same chance the single document
-- comes fast (~x2 ETC multiplier -> ~60% per Ligator). That is the correct direction for a "the
-- courier dropped it, go find it" step with no kill requirement, but it is a judgement, not a
-- measurement. If a slower feel is wanted, the defensible alternative is the Aran informant
-- chain's band (20000-100000); one number changes.
INSERT INTO drop_data (dropperid, itemid, minimum_quantity, maximum_quantity, questid, chance)
VALUES (3110100, 4032463, 1, 1, 22548, 300000);

-- --------------------------------------------------------------------------------------------
-- 22559 "Eliminate the Golems"  ->  4032466 Golem Doll x1
-- --------------------------------------------------------------------------------------------
-- The parity audit could not name this dropper and guessed 5130101/5130102 (Stone Golem / Dark
-- Stone Golem). That guess is WRONG and is not used. The mob exists and is named exactly what
-- the quest calls it: String.wz/Mob.img.xml:4869-4871 - mob 9300387 "Enraged Golem".
-- QuestInfo.img.xml:7982 - "enter that door, defeat the Enraged Golems, and bring back the
-- culprit ... a #bdoll or puppet#k". Act.img.xml:4625-4627 node "0" is EMPTY.
--
-- 9300387 spawns ONLY on 910600000 "Golem's Temple Entrance" (x1) and 910600010 "Abandoned
-- Hideout" (x3) - nowhere else, in either tree. Both carry mobTime -1, which MapFactory.java:
-- 120-121 force-spawns once at map load. 910600010 is "that door": ticket 08 already merged
-- Map1/106010102.img/portal/8 (script "evanDollGR", present at wz/Map.wz/Map/Map1/106010102
-- .img.xml:2785) and wrote scripts/portal/evanDollGR.js, which warps Golem's Temple 2 ->
-- 910600010. So the room, the door and the mob are all live already; only the drop was missing.
--
-- Rate: R2. The Aran twin is quest 21731 "Eliminate the Puppeteer!" - same construction, a
-- quest-exclusive 93003xx mob standing in a hidden quest-only map, dropping the chain's single
-- culmination item. Its row is
--   (9300344, 4032322, 1, 1, 21731, 999999)
-- and 999999 is copied unchanged. It is effectively guaranteed, which is the only rate that
-- works here: there are 4 kills available per visit and the mobs do not respawn, so anything
-- lower turns a story beat into a re-entry grind.
INSERT INTO drop_data (dropperid, itemid, minimum_quantity, maximum_quantity, questid, chance)
VALUES (9300387, 4032466, 1, 1, 22559, 999999);

-- --------------------------------------------------------------------------------------------
-- 22407 "Making a Bigger Saddle"   ->  4032475 Lycanthrope Leather x10 + 4032476 Buckle x2
-- 22410 "The Lost Big Saddle"      ->  4032504 Lycanthrope Leather x10 + 4032505 Buckle x2
-- --------------------------------------------------------------------------------------------
-- These two quests want the same two materials under different item ids, and the quest text
-- says so outright: QuestInfo.img.xml:7389 - 22410 asks for "#b60 million mesos#k in addition
-- to the #bsame materials as before#k". The pairs are byte-identical in String.wz/Etc.img.xml:
--   4032475 / 4032504  "Lycanthrope Leather"      "A tough piece of Lycanthrope leather."
--   4032476 / 4032505  "Captain Alpha's Buckle"   "...discovered deep inside the ocean in a
--                                                  treasure chest near the shipwreck."
-- Check.img.xml:25904-25918 (22407) and 26059-26073 (22410) confirm the 10/2/2 counts.
--
-- (a) LEATHER. drop_data ALREADY has the right droppers for 4032475 - mobs 8140000 and 9500134,
-- both named "Lycanthrope" (String.wz/Mob.img.xml:930-932, 3582-3584):
--   (8140000, 4032475, 1, 1, 28344, 200000)   (9500134, 4032475, 1, 1, 28344, 200000)
-- but BOTH are gated to quest 28344, and needQuestItem() (Character.java:5810-5822) returns
-- false for any quest other than the one on the row. So a player on 22407 cannot pick these up:
-- the parity report counted 4032475 as "sourced" because a row exists, which is the one thing
-- that report cannot see. Four rows are added - the SAME mobs and the SAME chance 200000 as the
-- rows already there, only the questid and (for 22410) the item id differ. R1, exact.
--
-- Note on 9500134: it spawns on no map today, so its two rows are inert. They are added anyway
-- so the 22407/22410 pair mirrors the existing 28344 pair exactly; 8140000 (211040800/900 and
-- 211041000, x3 each) is the dropper that actually works. Flagged rather than quietly omitted.
--
-- Note on shape: this makes (8140000, 4032475) the first (dropperid, itemid) pair in the table
-- to carry two rows with different questids. That is correct and is how the schema models one
-- item wanted by two quests - sortDropEntries (MapleMap.java:680-693) evaluates each row against
-- its own questid independently. The only visible artifact is that a 22407 player occasionally
-- sees the 28344-gated copy drop and cannot loot it, which is already how every quest drop
-- behaves for a player without the quest.
INSERT INTO drop_data (dropperid, itemid, minimum_quantity, maximum_quantity, questid, chance)
VALUES (8140000, 4032475, 1, 1, 22407, 200000),
       (9500134, 4032475, 1, 1, 22407, 200000),
       (8140000, 4032504, 1, 1, 22410, 200000),
       (9500134, 4032504, 1, 1, 22410, 200000);

-- (b) BUCKLE - a REACTOR drop, not a mob drop. QuestInfo.img.xml:7356 - "a #b#t4032476#s#k from
-- Shipwreck Treasure Chests deep in the oceans of Aquaroad". The reactor is 2302001, whose own
-- info string is the Korean for exactly that: wz/Reactor.wz/2302001.img.xml:4
--   <string name="info" value="심해보물상자:심해먼지"/>   = "DEEP SEA TREASURE CHEST : deep sea dust"
-- (its two siblings are 2302000 "mid-water-area treasure chest" and 2302002 "scallop", so the
-- identification is not by elimination - "deep sea" is written on the one the quest describes).
-- It is placed 7 times on 230040400, whose mapName is "The Grave of a Wrecked Ship"
-- (String.wz/Map.img.xml:4292-4295) - the shipwreck the item description names - plus 5 each on
-- 230040000/100/200/300 and 1 on 230040410. scripts/reactor/2302001.js already exists and calls
-- rm.dropItems(), which reads this table, so no script change is needed.
--
-- Rate: R1 on the reactor family. The one quest-gated precedent on these chests is quest 3083
-- "Kenta's Advice", which is authentic GMS, is given by NPC 2060005 - THE SAME NPC that gives
-- 22407 and 22410 - and asks for five count-1 items out of "a #bbox#k at the bottom of the
-- ocean" (QuestInfo.img.xml:13419). Its five rows are all chance 5:
--   (2302000, 4031274..4031278, 5, 3083)
-- so 5 is copied. Remember this column is a 1-in-N denominator, not a per-million weight.
INSERT INTO reactordrops (reactorid, itemid, chance, questid)
VALUES (2302001, 4032476, 5, 22407),
       (2302001, 4032505, 5, 22410);
