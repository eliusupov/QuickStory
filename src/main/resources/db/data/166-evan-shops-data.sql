-- ============================================================================================
-- Evan stock for the two shops the owner built himself: Sly's book shop in Leafre and Tulcus's
-- scroll shop outside Kerning City.
--
-- 17 shopitems rows into shop 2080001, 7 into shop 1052104. Additive only; touches no applied
-- changeSet and no row the owner added.
-- ============================================================================================
--
-- BOTH SHOPS WERE FOUND FROM THE DATA, not guessed:
--
--   SELECT s.shopid, s.npcid, COUNT(*) FROM shopitems si JOIN shops s ON s.shopid = si.shopid
--   WHERE si.itemid BETWEEN 2290000 AND 2290999 GROUP BY s.shopid, s.npcid;
--   -> 2080001 | 2080001 | 139        the only shop on the server selling any mastery book
--
--   2080001  "Sly"     Map2/240000002.img  = Leafre : Department Store
--   1052104  "Tulcus"  Map1/107000100.img  = Warning Street : The Swamp of Despair II
--
-- Tulcus is NOT literally in Kerning City, and the owner's "in kerning" should be read as the
-- region: 103000000 -> 107000000 -> 107000100 is two portals. Every NPC placed on each of the 48
-- maps whose String.wz entry names Kerning was checked against `shops`, and the only four with a
-- shop at all (1051000 Cutthroat Manny, 1051001 Don Hwang, 1051002 Dr. Faymus, 1052116 Thompson)
-- sell no 204xxxx scroll between them. Tulcus is the only scroll shop in the region and is also
-- the one the owner personally built out, which pins it independently of the map name.
--
-- Sly WAS the potion shop the owner half-remembered: 102-shopitems-data.sql seeds shop 2080001
-- with 30 potion/misc rows (2010000, 2020012-15, 2030000, 2060000/1, 2061000/1, 2070000, 2330000,
-- ...). The live table has none of them and 155 book rows instead; Tulcus likewise carries 89
-- scrolls the seed never had. Both rebuilds were done straight against the database -
-- DATABASECHANGELOG tops out at 163 and no changeSet has ever touched shopitems - so neither
-- shop's current contents are reproducible from this repo. Flagged to the owner, not fixed here:
-- capturing 244 hand-made rows into a changeSet is his call, not a side effect of this one.
--
-- ============================================================================================
-- SHOP 2080001 (Sly) - conventions these 17 rows continue, measured over his 155 rows, zero
-- variance: price 5000000 on every row; position 1..155 contiguous; ascending itemid; the
-- sixteen "[Skill Book]" 2280xxx items first, then mastery books 2290000-2290139. (2290109 is
-- absent because it does not exist in Item.wz/Consume/0229.img.xml.) Next free position is 156,
-- and the four skill books take 156-159 so the shop's skill-books-then-mastery-books grouping
-- survives; the mastery books follow at 160-172. Nothing is renumbered.
--
-- THE FOUR SKILL BOOKS, and why they are here rather than on a boss.
--
--   2280026  [Skill Book] Flame Wheel     -> 22171003
--   2280027  [Skill Book] Magic Mastery   -> 22170001
--   2280028  [Skill Book] Dark Fog        -> 22181002
--   2280029  [Skill Book] Soul Stone      -> 22181003
--
-- These four skills cannot be learned without them. AssignSPProcessor.java:91 caps SP at the
-- master level for a fourth-job skill:
--     curLevel + 1 <= (skill.isFourthJob() ? player.getMasterLevel(skill) : skill.getMaxLevel())
-- and Skill.isFourthJob() (Skill.java:55-62) names exactly 22170001, 22171003, 22171004, 22181002
-- and 22181003 - Evan's job ids are 2217 and 2218, so the usual `job % 10 == 2` never fires for
-- him and this hardcoded list is the whole of it. Character.java:1096-1107 hands out master level
-- 10 free on advancement for Maple Warrior, Illusion and Hero's Will at 2217 and Blessing of the
-- Onyx and Blaze at 2218 - and for nothing else. So the four skills above sit at master level 0,
-- `curLevel + 1 <= 0` is false, and not one SP can go into them. Each book carries masterLevel 10
-- / success 100 in Item.wz/Consume/0228.img.xml, which SkillBookHandler.useSkillBook applies.
--
-- Every other class gets its equivalent books by bossing. drop_data already carries, per book,
-- 3-6 rows: 2280013-2280016 (Aran, the closest comparable - the other Hero class) from Crimson
-- Balrog 8150000, Skelegon 8190003/9500380, Jr. Newtie 8190000/9500376, Zakum3 8800002, Qualm
-- Monk 8200005, Memory Monk 8200001, Chief Memory Guardian 8200004 and Ergoth 9300028;
-- 2280004-2280010 from Papulatus 8500002, Pianus 8510000/8520000, Grim Phantom Watch 8143000 and
-- Gigantic Spirit Viking 8141100. Two more come from quests instead (Quest.wz/Act.img.xml lists
-- 2280003 Maple Warrior and 2280012 Rush). So the answer to the owner's question is: no, Evan
-- does not get his skills free - four of them are gated exactly like everyone else's.
--
-- But nothing on this server produces Evan's four. Checked and empty in all of drop_data,
-- drop_data_global, reactordrops and shopitems, absent from Quest.wz/Act.img.xml, and referenced
-- by no script under scripts/. That is a parity gap, not a duplicated source: without these rows
-- Magic Mastery, Flame Wheel, Dark Fog and Soul Stone are unreachable for an Evan on this server,
-- permanently. THE SHOP IS COMPENSATING FOR A MISSING DROP SOURCE, which is the same thing the
-- owner already did with 2280017-2280019, the three Weakness books, which have no drop or quest
-- row either and which he stocked at positions 14-16.
--
-- THE THIRTEEN MASTERY BOOKS. All present in Item.wz/Consume/0229.img.xml, which holds 152
-- entries and ends at 02290152 - 02290153 does not exist. Each teaches an Evan skill:
--
--   2290140  Illusion 20                22171002   master 20
--   2290141  Illusion 30                22171002   master 30
--   2290142  Flame Wheel 20             22171003   master 20
--   2290143  Flame Wheel 30             22171003   master 30
--   2290144  Magic Mastery 20           22170001   master 20
--   2290145  Magic Mastery 30           22170001   master 30
--   2290146  Blaze 20                   22181001   master 20
--   2290147  Blaze 30                   22181001   master 30
--   2290148  Dark Fog 20                22181002   master 20
--   2290149  Dark Fog 30                22181002   master 30
--   2290150  Soul Stone 20              22181003   master 20
--   2290151  Blessing of the Onyx 20    22181000   master 20
--   2290152  Blessing of the Onyx 30    22181000   master 30
--
-- 7 skills x 2 tiers would be 14. There is no "Soul Stone 30" book in v84 - not an omission here.
-- 2217xxxx is Evan 9th Growth and 2218xxxx is Evan 10th Growth, his last two advancements, so all
-- 13 are the structural equivalent of 4th job. No Evan mastery book exists at any lower tier, so
-- "all of them" and "only the late-tier ones" are the same set; there was no line to draw.
--
-- The range is confirmed against pristine v84 SECOND-HAND, and that distinction is deliberate:
-- porting-resources/wz-data/v84/ holds packed .wz binaries, not an extracted XML tree, so no
-- direct XML diff is available. docs/wz-baseline/add-list/Item.txt - this repo's own census of
-- that archive, "nodes present in v84 and absent from v83-stock" - lists exactly
-- Consume/0229.img/02290140 through 02290152 (lines 213-225) and Consume/0228.img/02280026
-- through 02280029 (lines 209-212), and nothing beyond either.
--
-- The books work once bought: MasteryBookJobMatchRealLoad pins the getSkillStats fix that lets an
-- Evan10 still resolve the 2217-block books, and usableMasteryBooks() now scans to 2290152 rather
-- than stopping at 2290139.
--
-- ============================================================================================
-- SHOP 1052104 (Tulcus) - the INT scrolls, at the owner's stated prices.
--
-- His pricing is not one rule. Reading success off Item.wz/Consume/0204.img.xml for each of his
-- 89 rows gives three conventions, and the tier a row lands in decides which applies:
--   A. 65 rows, every weapon and every armor stat scroll:  60% -> 250000,  10% -> 500000
--   B. 21 rows, eye accessory and belts:   100% -> 250000, 60% -> 500000,  10% -> 1000000
--   C.  6 rows, ATT and Magic Att on gloves and shield:     60% -> 500000, 10% -> 750000
-- Convention A had no 100% tier at all until now; the owner supplied one - "including 100% at
-- 250,000" - which matches what B already charges for 100%, so the two agree.
--
-- The rows below take A's 60%/10% and the owner's new 100%, EXCEPT the two gloves rows, which
-- take C. C is the more specific precedent and it is already set by their own siblings: the
-- shield Magic Att pair 2040919/2040920 sits at 500000/750000 at positions 236/312. Pricing
-- gloves Magic Att at A's numbers would undercut the shield rows he set for the identical scroll
-- type one slot over. Flagged rather than assumed.
--
--   2040512  Scroll for Overall Armor for INT   100%  +1 INT
--   2040513  Scroll for Overall Armor for INT    60%  +2 INT
--   2040514  Scroll for Overall Armor for INT    10%  +5 INT
--   2040817  Scroll for Gloves for Magic Att.    60%  +1 INT   <- gloves' INT scroll is named
--   2040816  Scroll for Gloves for Magic Att.    10%  +3 INT      "Magic Att."; it grants incINT
--   2043700  Scroll for Wand for Magic Att.     100%  +1 MAD
--   2043800  Scroll for Staff for Magic Att.    100%  +1 MAD
--   2040024  Scroll for Helmet for INT 100%     100%  +1 INT   <- the one slot with no INT scroll
--   2040025  Scroll for Helmet for INT 60%       60%  +2 INT      in the shop at all
--   2040026  Scroll for Helmet for INT 10%       10%  +3 INT
--   2041015  Scroll for Cape for INT            100%  +1 INT   <- 60% and 10% already stocked
--
-- The helmet rows follow convention A + the owner's 100%, because his shop sets no helmet
-- precedent of its own: its only helmet rows are the two SEED ones, 2040003 at position 160 and
-- 2040000 at 164, both 100% at 35000. That 35000 is the seeded armor-100% price, not his - every
-- 100% row he added himself (2040202, 2040207, 2041300, 2041303, 2041306, 2041309) is 250000. So
-- helmet INT 100% lands at 250000 beside a seeded helmet DEF 100% at 35000. That split already
-- runs through the whole shop and is his, not something introduced here.
--
-- Positions continue the shop's stride-4 run; its highest is 620, so these take 624-664.
--
-- ============================================================================================
-- WHAT IS NOT IN HERE, AND WHY  (read this before assuming something was missed)
-- ============================================================================================
--   * NO Evan scrolls, for Tulcus or anyone. v84 ships no scroll that can target Evan-only
--     equipment, proven from the server's own matching rule rather than from a search that came
--     up empty:
--       ScrollHandler.java:189-198  return (scrollid / 100) % 100 == (itemid / 10000) % 100;
--     Evan's only exclusive gear is the dragon set (Character.wz/Dragon/: 1942000-02 Mask,
--     1952000-02 Pendant, 1962000-02 Wings, 1972000-02 Tail; all reqJob=2, tuc=3, so they do
--     carry 3 upgrade slots). Matching them needs scroll families 2049400/2049500/2049600/
--     2049700. Item.wz/Consume/0204.img.xml contains families 20400-20413, 20430-20433,
--     20437-20438, 20440-20449, 20470-20473, 20480 and 20490-20492 - 94/95/96/97 are simply not
--     among them. The 20492 Dark Scroll special case routes to ring/pendant/belt only, and
--     2041200 "Dragon Stone" is a pendant scroll gated on item 1122000: the name is a red
--     herring. reqJob in v84 is a five-bit class mask with no Evan bit, and Evan's weapons are
--     wands and staves shared with every magician, which Tulcus already stocks. Adding "Evan
--     scrolls" would mean inventing items. There are none.
--   * NO Topwear, Bottomwear or Shoes INT scrolls, though all three were asked for. They do not
--     exist in v84 at any success rate: scanning every entry in 0204.img.xml for an incINT node
--     gives Helmet 5, Overall 7, Shoes 2, Gloves 4, Shield 4, Cape 8, Topwear 0, Bottomwear 0.
--     The Shoes pair is 2040729 "Balrog's INT Scroll 30%" and 2040739 "Balrog's Twilight Scroll
--     5%" - event drops at success rates (30, 5) that exist nowhere in this shop's three
--     conventions, so there is no precedent to price them from. Left out rather than invented.
--
-- Liquibase's changelog table is the idempotency guard here, as it is for every neighbouring data
-- changeSet; the rollback in changelog-data.xml deletes exactly these 28 rows and nothing else.

INSERT INTO shopitems (shopid, itemid, price, pitch, position)
VALUES (2080001, 2280026, 5000000, 0, 156),
       (2080001, 2280027, 5000000, 0, 157),
       (2080001, 2280028, 5000000, 0, 158),
       (2080001, 2280029, 5000000, 0, 159),
       (2080001, 2290140, 5000000, 0, 160),
       (2080001, 2290141, 5000000, 0, 161),
       (2080001, 2290142, 5000000, 0, 162),
       (2080001, 2290143, 5000000, 0, 163),
       (2080001, 2290144, 5000000, 0, 164),
       (2080001, 2290145, 5000000, 0, 165),
       (2080001, 2290146, 5000000, 0, 166),
       (2080001, 2290147, 5000000, 0, 167),
       (2080001, 2290148, 5000000, 0, 168),
       (2080001, 2290149, 5000000, 0, 169),
       (2080001, 2290150, 5000000, 0, 170),
       (2080001, 2290151, 5000000, 0, 171),
       (2080001, 2290152, 5000000, 0, 172);

INSERT INTO shopitems (shopid, itemid, price, pitch, position)
VALUES (1052104, 2040512, 250000, 0, 624),
       (1052104, 2040513, 250000, 0, 628),
       (1052104, 2040514, 500000, 0, 632),
       (1052104, 2040817, 500000, 0, 636),
       (1052104, 2040816, 750000, 0, 640),
       (1052104, 2043700, 250000, 0, 644),
       (1052104, 2043800, 250000, 0, 648),
       (1052104, 2040024, 250000, 0, 652),
       (1052104, 2040025, 250000, 0, 656),
       (1052104, 2040026, 500000, 0, 660),
       (1052104, 2041015, 250000, 0, 664);
