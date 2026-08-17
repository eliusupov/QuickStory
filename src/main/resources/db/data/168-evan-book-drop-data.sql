-- ============================================================================================
-- Evan books drop like every other book: 63 mastery-book rows + 15 skill-book rows.
--
-- 78 drop_data rows over 62 droppers. Additive only - touches no applied changeSet.
--
-- The owner reported seeing books drop while playing and asked why Evan had none. He was right
-- and an earlier pass of mine was wrong: it queried drop_data for 2280000-2280099 only and
-- concluded from the empty result that books "have no drop source on this server". The mastery
-- range was never queried. drop_data already holds 697 rows for 2290000-2290139 across 139 books
-- and 103 droppers. Evan was the only class missing from it.
-- ============================================================================================
--
-- THE RULE, measured over those 697 rows, not assumed:
--
--   * Droppers are ordinary monsters, not just bosses. Level 45 to 180, the mass of them 90-140.
--     Every row is minimum_quantity 1, maximum_quantity 1, questid 0. There are 0 rows in
--     drop_data_global and 54 in reactordrops, so drop_data is where this lives.
--   * Each dropper has ONE base chance, set by the mob: 500 for an ordinary monster, 750 for the
--     Oblivion-tier elites, and 10000 / 15000 / 20000 / 25000 for bosses by rank.
--   * Every dropper carries exactly two distinct chances and the ratio is exactly 3.0, on all 54
--     droppers that use both. 500/1500, 750/2250, 10000/30000, 15000/45000, 20000/60000,
--     25000/75000. No other ratio occurs.
--   * The two tiers are two DISJOINT groups of books, and the split is by class:
--       - the 116 Explorer books (jobs 112-522) take the base chance
--       - 14 books take 3x base, and they are exactly 2290126-2290139, every Aran book, job 2112
--     The intersection of the two groups is empty. Checked: no book appears at both chances.
--   * The 20 and 30 tiers of the same skill sit at the SAME chance when one mob drops both -
--     64 mob+skill pairs carry both tiers, 64 of them at equal chance, 0 differ. Tier changes
--     which mobs, never the number.
--   * Nothing is level-gated in code. dropItemsFromMonsterOnMap adjusts EQUIP, ETC, arrows and
--     throwing stars only, and mastery books are USE, so the data is the whole story. That is
--     why the owner sees them drop at all.
--
-- ARAN IS THE ANALOGUE, and it is not a judgement call. Aran is the other new class of this era,
-- its books are the only ones in the 3x group, and the two sets line up 1:1:
--     Aran   7 skills x 2 tiers = 14 books, 2290126-2290139
--     Evan   7 skills x 2 tiers = 13 books, 2290140-2290152 (no Soul Stone 30 exists in v84)
--     Aran   4 skill books, 2280013-2280016
--     Evan   4 skill books, 2280026-2280029
-- So every row below is a verbatim copy of a real row that is already in drop_data for the
-- corresponding Aran book, with ONLY the itemid replaced. Dropper, quantity, questid and chance
-- are untouched. This is the method changeSet 153 established and the owner accepted -- 153
-- copies a real row and replaces the dropperid, this one copies a real row and replaces the
-- itemid, which is the more conservative of the two because it invents no dropper.
--
-- Pairing is by skill slot in id order, then by master tier, so a 20 copies a 20 and a 30 copies
-- a 30. Soul Stone has no 30 book, so Combo Tempest 30 (2290137) is simply left unused.
--
--   Illusion       <- Overswing        Blaze        <- Final Blow
--   Flame Wheel    <- High Mastery     Dark Fog     <- High Defense
--   Magic Mastery  <- Freeze Standing  Soul Stone   <- Combo Tempest (20 only)
--   Onyx           <- Combo Barrier
--
-- Verified before writing: all 78 chances already occur on their own dropper for some other book,
-- and drop_data holds 0 existing rows for any of these 17 itemids, so nothing here is a duplicate
-- and nothing invents a rate.
--
-- WHAT THIS CORRECTS. An earlier changeSet header of mine (166) said the Sly shop "is
-- compensating for a missing drop source" for the four skill books, and that no analogue existed
-- so naming a dropper would be invention. The first half stands: nothing in the v84 CLIENT names
-- a dropper, because GMS drop tables were server-side and were never shipped, so no carve can
-- answer it. What was too strict was the conclusion. The analogue is not in the client, it is in
-- this project - Aran, whose four skill books already carry drop rows in 152-drop-data.sql and
-- 153-crimson-sky-drop-data.sql. Copying those is derivation, not invention. The shop rows in
-- 166 stay: a guaranteed 5000000 mesos purchase and a sub-1% drop are different things, and
-- every Explorer skill book is likewise both bought from Sly and dropped.
--
-- ============================================================================================
-- WEB CHECK, as the owner asked. Corroboration, not authority. No row below came from a source.
-- ============================================================================================
-- bbb.hidden-street.net is behind a Cloudflare challenge and returns 403 to every fetch, so this
-- was read through Wayback captures of it and of global.hidden-street.net. GMS Big Bang landed
-- 2010-12-14, so captures from Apr-Dec 2010 describe the pre-Big-Bang table this server models
-- and 2011+ captures do not. MapleSEA has a different regional table and was ignored except
-- where noted.
--
-- IT AGREES ON SHAPE, which is the part that mattered:
--   * There is no level threshold. Each book has a hand-authored dropper list, exactly like the
--     697 rows already in drop_data. MapleStory Wiki, verbatim: "Upon release of the 4th Job
--     Advancement prior to the Big Bang Update, Mastery Books were difficult to obtain, with
--     each book dropped by certain mobs and/or Bosses."
--   * Droppers are ordinary monsters and bosses from about Lv 65 to Lv 160, with the 30 books
--     skewing to the highest end. That is the same band this changeSet writes into.
--   * All four Evan SKILL books came off Zakum arm 3, with no quest and no shop, and Aran's four
--     behave identically on the same site. Every row below already puts all four on Zakum3 at
--     60000, inherited from the Aran copy, so canon and this changeSet agree on the one dropper
--     canon names.
--   * Independent confirmation of the item data: the wiki records 70 percent success for the 20
--     books and 50 percent for the 30 books, which is exactly what Consume/0229.img carries for
--     all 13 Evan books.
--   * Several copied droppers coincide with canon without being aimed at it - Flame Wheel 20 on
--     Griffey, Blaze 20 on Pianus, Onyx 20 on Qualm Monk Trainee, Dark Fog 30 on Lyka, and Onyx
--     30 on the Oblivion Guardian tier are all both canonical and in the Aran set.
--
-- IT WAS NOT ADOPTED AS THE DROPPER LIST, for four reasons, and the files win on all of them:
--   1. It carries no rates at all. Every row would still need a chance, so switching droppers
--      would replace a fully derived row with a canonical mob plus an invented number.
--   2. Two of the thirteen lists come only from 2011-01-01 captures, three weeks after Big Bang,
--      and one - Illusion 30 - has no populated pre-Big-Bang list anywhere. Canon is incomplete.
--   3. Canon reaches down to Lv 54-90 mobs such as Sr. Bellflower Root, Yeti, Beetle and
--      Overlord A, which this server does not use for mastery books at all.
--   4. The owner asked for these to drop like the other books IN THIS PROJECT. This project is
--      already more generous than canon - it gives Aran's skill books ordinary-mob rows that
--      canon does not have either - so matching the project is what was asked for.
--
-- One loose end it closes. Three ids carry a String.wz name and Job Evan but have no node in
-- Consume/0229.img, so they cannot exist as items: 2290162 Magic Guard 20, 2290163 Magic Booster
-- 20 and 2290164 Critical Magic 10. The wiki explains them - "With the release of Evan in March
-- 2010 in GlobalMS, certain Mastery Books were sold in the Cash Shop, but this was not the case
-- in any other server" - and the Cash Shop set was the pre-120 Magic Guard, Magic Booster and
-- Critical Magic books. They are correctly absent here and from the shop.
--
-- Each row is annotated with its dropper name and level. The analogue book is named per group.

INSERT INTO drop_data (dropperid, itemid, minimum_quantity, maximum_quantity, questid, chance)
VALUES
       -- 2290140 Illusion 20                <- 2290126 Overswing
       (8140700, 2290140, 1, 1, 0, 1500),    -- Blue Dragon Turtle lvl 90
       (9500378, 2290140, 1, 1, 0, 1500),    -- Blue Dragon Turtle lvl 100
       (9500180, 2290140, 1, 1, 0, 30000),   -- Papulatus lvl 90
       (9500181, 2290140, 1, 1, 0, 30000),   -- Papulatus lvl 90
       (9500331, 2290140, 1, 1, 0, 30000),   -- Papulatus lvl 55
       (8500002, 2290140, 1, 1, 0, 45000),   -- Papulatus lvl 125
       -- 2290141 Illusion 30                <- 2290127 Overswing
       (8150300, 2290141, 1, 1, 0, 1500),    -- Red Wyvern lvl 97
       (8200004, 2290141, 1, 1, 0, 1500),    -- Chief Memory Guardian lvl 101
       (8200010, 2290141, 1, 1, 0, 1500),    -- Oblivion Monk Trainee lvl 124
       (8300002, 2290141, 1, 1, 0, 1500),    -- Soaring Red Wyvern lvl 115
       (8220004, 2290141, 1, 1, 0, 30000),   -- Dodo lvl 121
       -- 2290142 Flame Wheel 20             <- 2290128 High Mastery
       (8150302, 2290142, 1, 1, 0, 1500),    -- Dark Wyvern lvl 103
       (8300004, 2290142, 1, 1, 0, 1500),    -- Soaring Black Wyvern lvl 115
       (8180001, 2290142, 1, 1, 0, 30000),   -- Griffey lvl 105
       (8300005, 2290142, 1, 1, 0, 30000),   -- Soaring Griffey lvl 120
       (9500173, 2290142, 1, 1, 0, 30000),   -- Griffey lvl 80
       -- 2290143 Flame Wheel 30             <- 2290129 High Mastery
       (8200001, 2290143, 1, 1, 0, 1500),    -- Memory Monk lvl 91
       (8200006, 2290143, 1, 1, 0, 1500),    -- Qualm Monk Trainee lvl 109
       (8220009, 2290143, 1, 1, 0, 30000),   -- Snack Bar lvl 85
       (8500002, 2290143, 1, 1, 0, 45000),   -- Papulatus lvl 125
       -- 2290144 Magic Mastery 20           <- 2290130 Freeze Standing
       (8190004, 2290144, 1, 1, 0, 1500),    -- Skelosaurus lvl 113
       (8300001, 2290144, 1, 1, 0, 1500),    -- Soaring Eagle lvl 110
       (9500381, 2290144, 1, 1, 0, 1500),    -- Skelosaurus lvl 100
       (8180000, 2290144, 1, 1, 0, 30000),   -- Manon lvl 105
       (8300006, 2290144, 1, 1, 0, 30000),   -- Dragonica lvl 120
       (9500174, 2290144, 1, 1, 0, 30000),   -- Manon lvl 80
       (9500382, 2290144, 1, 1, 0, 30000),   -- Leviathan lvl 120
       -- 2290145 Magic Mastery 30           <- 2290131 Freeze Standing
       (8140512, 2290145, 1, 1, 0, 1500),    -- Royal Guard lvl 93
       (8200002, 2290145, 1, 1, 0, 1500),    -- Memory Monk Trainee lvl 94
       (9400660, 2290145, 1, 1, 0, 1500),    -- Royal Guard Type S lvl 160
       (8220005, 2290145, 1, 1, 0, 45000),   -- Lilynouch lvl 131
       (9400592, 2290145, 1, 1, 0, 45000),   -- Rellik lvl 130
       -- 2290146 Blaze 20                   <- 2290132 Final Blow
       (8140600, 2290146, 1, 1, 0, 1500),    -- Bone Fish lvl 92
       (8510000, 2290146, 1, 1, 0, 30000),   -- Pianus lvl 110
       (8520000, 2290146, 1, 1, 0, 30000),   -- Pianus lvl 110
       (9500332, 2290146, 1, 1, 0, 30000),   -- Pianus lvl 45
       -- 2290147 Blaze 30                   <- 2290133 Final Blow
       (8190003, 2290147, 1, 1, 0, 1500),    -- Skelegon lvl 110
       (8200008, 2290147, 1, 1, 0, 1500),    -- Chief Qualm Guardian lvl 116
       (8300000, 2290147, 1, 1, 0, 1500),    -- Soaring Hawk lvl 110
       (9500380, 2290147, 1, 1, 0, 1500),    -- Skelegon lvl 100
       (8220002, 2290147, 1, 1, 0, 30000),   -- Chimera lvl 85
       (8810018, 2290147, 1, 1, 0, 75000),   -- Horntail lvl 160
       -- 2290148 Dark Fog 20                <- 2290134 High Defense
       (8140511, 2290148, 1, 1, 0, 1500),    -- Imperial Guard lvl 91
       (8170000, 2290148, 1, 1, 0, 1500),    -- Thanatos lvl 108
       (8200004, 2290148, 1, 1, 0, 1500),    -- Chief Memory Guardian lvl 101
       (9400658, 2290148, 1, 1, 0, 1500),    -- Imperial Guard Type A lvl 143
       (8220004, 2290148, 1, 1, 0, 30000),   -- Dodo lvl 121
       -- 2290149 Dark Fog 30                <- 2290135 High Defense
       (8150200, 2290149, 1, 1, 0, 1500),    -- Green Cornian lvl 100
       (9400582, 2290149, 1, 1, 0, 1500),    -- Crimson Guardian lvl 120
       (9500374, 2290149, 1, 1, 0, 1500),    -- Green Cornian lvl 100
       (9400590, 2290149, 1, 1, 0, 45000),   -- Margana lvl 130
       (8220006, 2290149, 1, 1, 0, 60000),   -- Lyka lvl 141
       -- 2290150 Soul Stone 20              <- 2290136 Combo Tempest
       (8200003, 2290150, 1, 1, 0, 1500),    -- Memory Guardian lvl 98
       (8200007, 2290150, 1, 1, 0, 1500),    -- Qualm Guardian lvl 113
       (8220005, 2290150, 1, 1, 0, 45000),   -- Lilynouch lvl 131
       -- 2290151 Blessing of the Onyx 20    <- 2290138 Combo Barrier
       (8200006, 2290151, 1, 1, 0, 1500),    -- Qualm Monk Trainee lvl 109
       (9420513, 2290151, 1, 1, 0, 30000),   -- Capt. Latanica lvl 100
       (9400593, 2290151, 1, 1, 0, 45000),   -- Hsalf lvl 130
       (8220006, 2290151, 1, 1, 0, 60000),   -- Lyka lvl 141
       -- 2290152 Blessing of the Onyx 30    <- 2290139 Combo Barrier
       (8200002, 2290152, 1, 1, 0, 1500),    -- Memory Monk Trainee lvl 94
       (8200012, 2290152, 1, 1, 0, 2250),    -- Chief Oblivion Guardian lvl 131
       (9400514, 2290152, 1, 1, 0, 30000),   -- Geist Balrog Phase 3 lvl 95
       (8810018, 2290152, 1, 1, 0, 75000),   -- Horntail lvl 160
       -- 2280026 Flame Wheel                <- 2280013 Final Blow
       (8190003, 2280026, 1, 1, 0, 1500),    -- Skelegon lvl 110
       (8300000, 2280026, 1, 1, 0, 1500),    -- Soaring Hawk lvl 110
       (9500380, 2280026, 1, 1, 0, 1500),    -- Skelegon lvl 100
       (8150000, 2280026, 1, 1, 0, 30000),   -- Crimson Balrog lvl 100
       (8800002, 2280026, 1, 1, 0, 60000),   -- Zakum3 lvl 140
       -- 2280027 Magic Mastery              <- 2280014 High Defense
       (8200005, 2280027, 1, 1, 0, 1500),    -- Qualm Monk lvl 106
       (9400121, 2280027, 1, 1, 0, 45000),   -- Female Boss lvl 130
       (8800002, 2280027, 1, 1, 0, 60000),   -- Zakum3 lvl 140
       -- 2280028 Dark Fog                   <- 2280015 Combo Tempest
       (8200001, 2280028, 1, 1, 0, 1500),    -- Memory Monk lvl 91
       (9300028, 2280028, 1, 1, 0, 30000),   -- Ergoth lvl 115
       (8800002, 2280028, 1, 1, 0, 60000),   -- Zakum3 lvl 140
       -- 2280029 Soul Stone                 <- 2280016 Combo Barrier
       (8190000, 2280029, 1, 1, 0, 1500),    -- Jr. Newtie lvl 105
       (8200004, 2280029, 1, 1, 0, 1500),    -- Chief Memory Guardian lvl 101
       (9500376, 2280029, 1, 1, 0, 1500),    -- Jr. Newtie lvl 100
       (8800002, 2280029, 1, 1, 0, 60000);   -- Zakum3 lvl 140
