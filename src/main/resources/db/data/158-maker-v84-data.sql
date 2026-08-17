-- ============================================================================================
-- Maker: the 6 recipes v84 adds to Etc.wz/ItemMake.img and the server had none of.
--
--   1142156, 1142157  - the two medals            (ItemMake.img/0)
--   1942002, 1952002, 1962002, 1972002 - the four EVAN DRAGON EQUIPMENT slots (ItemMake.img/2)
--
-- Nothing here is invented. Every number is read out of the v84 archive, or produced by this
-- repo's own generator formula from a number read out of it.
--
-- --------------------------------------------------------------------------------------------
-- WHERE THE RECIPE COMES FROM
-- --------------------------------------------------------------------------------------------
-- docs/wz-baseline/add-list/Etc.txt:10475-10480 lists the six as v84 additions. The worktree's
-- own wz/Etc.wz/ItemMake.img.xml is still the v83 file and does NOT contain them, so they were
-- read straight out of the pristine v84 archive with the repo's existing peek tool:
--
--   docs/wz-baseline/tool-peek/bin/Debug/net10.0-windows/WzPeek.exe dump \
--     D:/games/MapleStory/Server/porting-resources/wz-data/v84/Etc.wz ItemMake.img/2/01942002 6
--
-- (ids are zero-padded to 8 digits inside ItemMake.img - 01942002, not 1942002.)
-- Re-running that for each of the six reproduces every value below verbatim.
--
-- KNOWN GAP, stated rather than papered over: wz/Etc.wz/ItemMake.img.xml is NOT updated by this
-- changeSet. It is only read at runtime for the `catalyst` leaf
-- (server/ItemInformationProvider.java:2191, getMakerStimulant), and an absent node and a node
-- with no catalyst both yield -1, so behaviour is identical either way. But it does mean a fresh
-- tools/mapletools/SkillMakerFetcher run against the worktree XML would emit a maker-data.sql
-- without these six. Merging the six nodes into Etc.wz is an Etc.wz-merge job, not a data job.
--
-- --------------------------------------------------------------------------------------------
-- WHERE req_meso COMES FROM - it is not a copied rate and it is not invented either; it is
-- computed by the formula this repo already used to build the other 834 rows.
-- --------------------------------------------------------------------------------------------
-- makercreatedata.req_meso is NOT the raw ItemMake `meso` leaf. tools/mapletools/
-- SkillMakerFetcher.java:generateUpdatedItemFee() marks it up before writing:
--   equip, reqLevel >= 108 : 1000 * ceil((meso/10  + meso) / 1000)
--   equip, reqLevel <  108 : 1000 * ceil((meso/11  + meso) / 1000)
--   weapon (itemid/100000 in 13,14) : 1000 * floor((meso/10 + meso) / 1000)
--   non-equip (itemid >= 2000000)   : 1000 * ceil((meso/10 + meso) / 1000)
-- where reqLevel is the EQUIP's own Character.wz reqLevel, not ItemMake's.
--
-- Verified before use: replaying that formula over the v83 ItemMake `meso` leaf reproduces the
-- stored req_meso for all 145 existing id=2 rows, 145/145 exact, 0 mismatches.
--
-- The four dragon equips: wz/Character.wz/Dragon/0194-0197 2002.img.xml all carry reqLevel 120,
-- so the >=108 branch applies:  1000 * ceil((300000/10 + 300000)/1000) = 330000.
-- The two medals carry meso 0, so req_meso is 0 on every branch.
--
-- --------------------------------------------------------------------------------------------
-- SHAPE - checked against how the existing 1,926 recipe rows relate to their siblings
-- --------------------------------------------------------------------------------------------
--   makercreatedata  header: one row per craftable. `id` mirrors the ItemMake.img group and is
--                    provenance only - the server never selects on it
--                    (ItemInformationProvider.java:2091 keys on itemid alone).
--   makerrecipedata  the `recipe` list, one row per ingredient (ItemInformationProvider:2106).
--   makerrewarddata  the `randomReward` list. NONE of the six has a randomReward node, so this
--                    changeSet writes no reward rows.
--   makerreagentdata unrelated - it is the 4130xxx reagent stat table (ItemInformationProvider
--                    :2029), keyed on the reagent, not on the crafted item. Untouched.
--
-- Both header and recipe rows are required: getMakerItemEntry reads makercreatedata for cost and
-- level gate and makerrecipedata for ingredients, so either alone leaves the recipe unusable.
--
-- The dragon equips list 4260007 x8 and 4260008 x4, which lands them in the 4260000-4269999
-- window getMakerDisassembledItems() selects on, so disassembly returns half the crystals with
-- no extra data. That is the stock behaviour, not something added here.
-- ============================================================================================

-- ItemMake.img/0/01142156 - reqLevel 80, reqSkillLevel 1, itemNum 1, reqItem 4032502, tuc 0, meso 0
-- ItemMake.img/0/01142157 - reqLevel 120, reqSkillLevel 1, itemNum 1, reqItem 4032503, tuc 0, meso 0
-- ItemMake.img/2/019x2002 - reqLevel 115, reqSkillLevel 3, itemNum 1, tuc 3, meso 300000 (-> 330000)
INSERT INTO makercreatedata (id, itemid, req_level, req_maker_level, req_meso, req_item, req_equip, catalyst, quantity, tuc)
VALUES (0, 1142156, 80, 1, 0, 4032502, 0, 0, 1, 0),
       (0, 1142157, 120, 1, 0, 4032503, 0, 0, 1, 0),
       (2, 1942002, 115, 3, 330000, 0, 0, 0, 1, 3),
       (2, 1952002, 115, 3, 330000, 0, 0, 0, 1, 3),
       (2, 1962002, 115, 3, 330000, 0, 0, 0, 1, 3),
       (2, 1972002, 115, 3, 330000, 0, 0, 0, 1, 3);

INSERT INTO makerrecipedata (itemid, req_item, count)
VALUES (1142156, 4032502, 1),
       (1142156, 4011006, 2),
       (1142156, 4000021, 1),
       (1142156, 4007001, 10),
       (1142156, 4007005, 10),
       (1142157, 4032503, 1),
       (1142157, 4011006, 4),
       (1142157, 4000021, 1),
       (1142157, 4007006, 10),
       (1142157, 4007002, 10),
       (1942002, 4007002, 12),
       (1942002, 4260007, 8),
       (1942002, 4260008, 4),
       (1942002, 4020009, 15),
       (1952002, 4007001, 12),
       (1952002, 4260007, 8),
       (1952002, 4260008, 4),
       (1952002, 4020009, 15),
       (1962002, 4007005, 12),
       (1962002, 4260007, 8),
       (1962002, 4260008, 4),
       (1962002, 4020009, 15),
       (1972002, 4007006, 12),
       (1972002, 4260007, 8),
       (1972002, 4260008, 4),
       (1972002, 4020009, 15);
