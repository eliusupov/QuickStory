-- ============================================================================================
-- Monster Book: the 39 v84 cards that had no mob mapping and therefore could never drop.
--
-- Item.wz/Consume/0238.img ships 382 monster cards; monstercarddata mapped 343. The 39
-- unmapped ones are exactly the 39 that v84 adds (docs/wz-baseline/add-list/Item.txt lines
-- 226-264). Unmapped means no drop row, which means unobtainable, which means the card sets
-- covering Ludibrium, Showa, Leafre, the Temple of Time and Neo Tokyo can never complete.
--
-- WHERE THE MOB ID COMES FROM - it is not inferred.
--   Every card node carries its own mob: Item.wz/Consume/0238.img/<cardid>/info/mob.
--   All 382 cards have that leaf; the 343 already-mapped rows agree with it on 336 of 343
--   (the 7 disagreements are pre-existing and are NOT touched here - see the note at the
--   bottom). So the leaf is the same source the existing table was built from, and the 39
--   new rows below are read straight off it.
--   NOTE the ids are zero-padded to 8 digits in the XML (02380020); compare as int.
--
-- WHERE THE DROP CHANCE COMES FROM - it is copied, not invented.
--   drop_data holds 576 monster-card rows over the 343 mapped cards, and they use exactly
--   two values:
--     select chance, count(*) from drop_data where itemid between 2380000 and 2389999
--     group by chance;   ->   8000 x 409,  24000 x 167
--   The split is by card id with no exception: every 8000 row is cardid < 2388000 and every
--   24000 row is cardid >= 2388000 (min/max of each group: 2380000-2387013 and 2388000-
--   2388070). The 2388xxx block is the boss block - checked independently against Mob.wz,
--   all 6 new 2388xxx cards have info/boss=1 and all 33 others have boss=0, 39/39 agreement.
--   So each row below takes 8000 or 24000 from that existing population, unchanged.
--   (server/maps/MapleMap.java:763 then scales card drops by 3.5x, or 0.35x on a boss.)
--
-- One drop row per card, from the single mob the WZ names. Cards whose mob has extra
-- server-side clones (the 9300xxx/9400xxx event copies that some existing cards carry) get
-- no such rows here - that would be inference, and none of these 39 mobs has a known clone.
-- ============================================================================================

INSERT INTO monstercarddata (cardid, mobid)
VALUES (2380020, 1210111),  -- Strange Pig
       (2381084, 2220110),  -- Crying Blue Mushroom
       (2381085, 2230112),  -- Terrified Wild Boar
       (2382097, 3400000),  -- Cherry Bubble Tea
       (2382098, 3400002),  -- Melon Bubble Tea
       (2382099, 3400001),  -- Mango Bubble Tea
       (2382100, 3400003),  -- Yeti Doll Claw Game
       (2382101, 3400005),  -- Jr. Pepe Doll Claw Game
       (2382102, 3400008),  -- Transformed Doll Claw Game
       (2382103, 4300001),  -- Blue Perfume
       (2382104, 4300003),  -- Yellow Perfume
       (2382105, 4300005),  -- Pink Perfume
       (2382106, 4300006),  -- Kid Mannequin
       (2382107, 4300007),  -- Female Mannequin
       (2382108, 4300008),  -- Male Mannequin
       (2382109, 4300009),  -- Latest Hits Compilation
       (2382110, 4300010),  -- Greatest Oldies
       (2382111, 4300011),  -- Cheap Amplifier
       (2382112, 4300012),  -- Fancy Amplifier
       (2384058, 7120103),  -- Red Slime
       (2384059, 7120104),  -- Silver Slime
       (2384060, 7120105),  -- Gold Slime
       (2384061, 7120106),  -- Overlord A
       (2384062, 7120107),  -- Overlord B
       (2385038, 7120108),  -- Robby
       (2385039, 7120109),  -- Iruvata
       (2385040, 8120104),  -- Maverick Type A
       (2385041, 8120105),  -- Maverick Type S
       (2385042, 8120106),  -- Maverick Type D
       (2385043, 8120102),  -- Afterlord
       (2385044, 8120103),  -- Prototype Lord
       (2386033, 8140511),  -- Imperial Guard
       (2386034, 8140512),  -- Royal Guard
       (2388075, 4300013),  -- Spirit of Rock
       (2388076, 7220003),  -- Bergamot
       (2388077, 8220010),  -- Dunas
       (2388078, 8220011),  -- Aufheben
       (2388079, 8220012),  -- Oberon
       (2388080, 8220013);  -- Nibelung

INSERT INTO drop_data (dropperid, itemid, minimum_quantity, maximum_quantity, questid, chance)
VALUES (1210111, 2380020, 1, 1, 0, 8000),
       (2220110, 2381084, 1, 1, 0, 8000),
       (2230112, 2381085, 1, 1, 0, 8000),
       (3400000, 2382097, 1, 1, 0, 8000),
       (3400002, 2382098, 1, 1, 0, 8000),
       (3400001, 2382099, 1, 1, 0, 8000),
       (3400003, 2382100, 1, 1, 0, 8000),
       (3400005, 2382101, 1, 1, 0, 8000),
       (3400008, 2382102, 1, 1, 0, 8000),
       (4300001, 2382103, 1, 1, 0, 8000),
       (4300003, 2382104, 1, 1, 0, 8000),
       (4300005, 2382105, 1, 1, 0, 8000),
       (4300006, 2382106, 1, 1, 0, 8000),
       (4300007, 2382107, 1, 1, 0, 8000),
       (4300008, 2382108, 1, 1, 0, 8000),
       (4300009, 2382109, 1, 1, 0, 8000),
       (4300010, 2382110, 1, 1, 0, 8000),
       (4300011, 2382111, 1, 1, 0, 8000),
       (4300012, 2382112, 1, 1, 0, 8000),
       (7120103, 2384058, 1, 1, 0, 8000),
       (7120104, 2384059, 1, 1, 0, 8000),
       (7120105, 2384060, 1, 1, 0, 8000),
       (7120106, 2384061, 1, 1, 0, 8000),
       (7120107, 2384062, 1, 1, 0, 8000),
       (7120108, 2385038, 1, 1, 0, 8000),
       (7120109, 2385039, 1, 1, 0, 8000),
       (8120104, 2385040, 1, 1, 0, 8000),
       (8120105, 2385041, 1, 1, 0, 8000),
       (8120106, 2385042, 1, 1, 0, 8000),
       (8120102, 2385043, 1, 1, 0, 8000),
       (8120103, 2385044, 1, 1, 0, 8000),
       (8140511, 2386033, 1, 1, 0, 8000),
       (8140512, 2386034, 1, 1, 0, 8000),
       (4300013, 2388075, 1, 1, 0, 24000),
       (7220003, 2388076, 1, 1, 0, 24000),
       (8220010, 2388077, 1, 1, 0, 24000),
       (8220011, 2388078, 1, 1, 0, 24000),
       (8220012, 2388079, 1, 1, 0, 24000),
       (8220013, 2388080, 1, 1, 0, 24000);

-- --------------------------------------------------------------------------------------------
-- NOT CHANGED HERE, on purpose:
--   7 of the 343 pre-existing rows disagree with Item.wz .../info/mob. Listed as cardid
--   (db mobid -> wz mobid):
--     2383045 (6130102 -> 6130103), 2388011 (9300105 -> 9300119), 2388017 (6400006 ->
--     8150000), 2388026 (6400008 -> 8130100), 2388043 (8820001 -> 8820000),
--     2388068 (3300006 -> 3300007), 2388069 (3300007 -> 3300006)  <- that last pair is swapped.
--   Every one of them already has a drop row, so no card is unobtainable because of them and
--   nothing is blocked. Changing them would be an edit, not an addition, so it is left for
--   the owner to call. Re-derive with the mob leaf above.
