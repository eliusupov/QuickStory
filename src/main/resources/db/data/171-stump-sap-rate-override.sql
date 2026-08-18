-- ============================================================================================
-- Item 4032460 "Refreshing Stump Sap", quest 22529: raise all four droppers 80000 -> 150000
-- (8% -> 15%). Rate only. dropperid / itemid / questid / min / max are untouched.
--
-- THIS IS AN OWNER-DIRECTED TUNING OVERRIDE, NOT A CORRECTION AND NOT RECOVERED v84 DATA.
-- Read that literally. Nothing below upgrades the evidence behind these rows; it only records
-- who asked for the new number and on what basis.
--
-- WHERE THE FOUR ROWS CAME FROM (unchanged by this changeSet):
--   * (130100,  4032460, 1, 1, 22529, 80000)  - changeSet 156, read off QuestInfo.img/22529/1's
--     "#o0130100#" token.
--   * (1130100 / 1140100 / 2130100, ...)      - changeSet 170, the Deep Valley variants, added
--     because QuestInfo.img/22529/0 stages the quest on 106000000/100/200 where zero plain
--     Stumps spawn. See 170's header for the full argument; it still stands.
--
-- WHERE 80000 CAME FROM: it was never measured for 22529. It was borrowed from this same stump
-- family's other quest item, 4031773 (quest 2145), which sits at 80000 on three of these four
-- mobs (130100, 1130100, 1140100) and 200000 on the fourth (2130100). That is an in-tree
-- precedent, not a source - and this changeSet does NOT claim it was wrong. 150000 simply
-- overrides it by owner direction.
--
-- WHERE 150000 CAME FROM: the owner cites https://dreamms.gg/items/4032460/refreshing-stump-sap,
-- which lists 15%. Weigh that source honestly:
--   * DreamMS is a v92 private server. 15% is THEIR tuning decision, not GMS v84 data. No client
--     WZ ever contained drop tables (Nexon kept drops server-side), so no archive can confirm or
--     refute it for v84.
--   * DreamMS is not an independent authority for this project: an earlier session here traced
--     DreamMS data back to our own tree, so a match can be our own numbers echoed back.
--   * The owner has endorsed this method explicitly and repeatedly ("we can even derive drop
--     chance by type of other monsters and drop we already have"; "if the client has what drops,
--     we can adjust ratios based on dreamms and monsters that we already have drop ratios for").
-- So: an owner-chosen rate, informed by a v92 server's tuning, replacing an in-tree family
-- precedent. Anyone revisiting this should re-derive from the family precedent (80000 / 200000
-- on 4031773) rather than treating 150000 as a recovered value.
--
-- 150000 is applied uniformly to all four rows, including 2130100 - 4031773's upward outlier on
-- Dark Axe Stump is a property of THAT item and was never carried onto 4032460.
--
-- GATING UNCHANGED: questid 22529 stays on every row and stays load-bearing, because
-- Item.wz/Etc/0403.img/04032460 carries info/quest = 1 - MapleMap.sortDropEntries only consults
-- needQuestItem for items flagged that way. A higher rate makes that gate matter more, not less:
-- ungated, 15% sap would flood every player killing stumps.
--
-- Scoped by itemid AND questid so no other item's rows can be caught by it.
-- ============================================================================================
UPDATE drop_data
SET chance = 150000
WHERE itemid = 4032460
  AND questid = 22529
  AND dropperid IN (130100, 1130100, 1140100, 2130100);
