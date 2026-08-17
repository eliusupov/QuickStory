-- v84 drop parity. Items the v84 client lists against a mob that drop_data never had.
-- Additive only. Does not touch changeSets 152-159, which are applied with fixed checksums.
--
-- WHAT DROPS. wz/String.wz/MonsterBook.img carries a per-mob `reward` list of item ids. It is
-- exact, not indicative. For 8800002 Zakum, 8810018 Horntail, 8180000 Manon and 0100100 Snail
-- it matches existing drop_data one-for-one, the only extra row being mesos. Across the 382
-- mobs carrying a list, 97.6%% of listed items are already in drop_data. Every row below is one
-- of the remaining 2.4%%, and MonsterBookDropV84RealLoad re-derives all of them from the client.
--
-- RATES ARE DERIVED FROM THIS SERVER, and nothing here is claimed to be a Nexon number.
-- Authentic GMS probabilities live in Etc.wz/Server/Reward.img, which our Etc.wz does not ship
-- (it has no Server/ directory), so no third-party figure can be authentic either.
--
-- Each chance is the MEDIAN of existing cosmic.drop_data rows in the bucket
--     (item class) x (mob level band) x (boss flag)
-- over the 22,461 non-quest-gated rows already present. Quest-gated rows are excluded because
-- their rates encode quest pacing, not drop value. A bucket under 20 rows falls back to
-- (class x boss flag) across all levels. Every row names its bucket and sample size.
--
-- HOLDOUT, because a model that cannot predict rates we already know has no business setting
-- rates we do not. 20%% of droppers hidden, model rebuilt on the remaining 80%%, hidden rows
-- predicted. 4,461 rows over 191 mobs:
--     median fold-error 1.14x, within 2x 73.2%%, within 5x 89.2%%, within 10x 94.6%%
--     scroll/etc_quest/masterybook/meso 1.00x, etc_ore 1.11x, equip 1.14x,
--     use 2.00x, etc_mobdrop 2.00x
-- use and etc_mobdrop are the weak classes: potion and mob-etc rates are idiosyncratic per mob
-- and neither level nor the boss flag predicts them. Those rows are the least trustworthy here.
--
-- dreamms.gg IS NOT USED. It is the same v83 lineage as this server rendered at 3x, measured on
-- 2,621 (mob,item) pairs we share: ours/(their%% x 10000) is 0.333 flat, p10 through p75, in
-- every level band and item class independently. Importing it would reimport our own numbers,
-- and dropping the /3 would silently triple them.
--
-- boss_drop_rate DOES NOT DOUBLE-COUNT. MapleMap.java:979 sets chRate = isBoss ?
-- getBossDropRate() else getDropRate(), and MapleMap.java:753-770 applies a per-class
-- multiplier (scroll 13x, mastery book 5x, card 3.5x, each reduced for bosses). Both act on the
-- stored chance at drop time. Every rate here is a median of rows from the SAME
-- (class x boss flag) bucket, so those multipliers hit these rows exactly as they hit the rows
-- they came from. config.yaml boss_drop_rate: 10 does not inflate these relative to live.
--
-- BOUNDS. Every chance is clamped to the observed min/max of its own (class x boss flag) group,
-- and any clamp is noted on the row. questid is 0 throughout, because a quest-gated drop is a
-- design decision and is never derived. No monster card (238xxxx) here, as changeSet 157 owns
-- those. Mobs whose only client-listed item is a card therefore get no row at all.
--
-- Comments carry no statement terminator and no apostrophe, because this changeSet runs with
-- stripComments off and splitStatements on, so the header reaches the driver as statement text.

INSERT INTO drop_data (dropperid, itemid, minimum_quantity, maximum_quantity, questid, chance)
VALUES
       -- 1210111 Strange Pig  Lv.10
       (1210111, 4000002, 1, 1, 0, 300000),   -- DERIVED etc_mobdrop/1-20N n197
       (1210111, 2000000, 1, 1, 0, 20000),   -- DERIVED use/1-20N n674
       (1210111, 4003004, 1, 1, 0, 9000),   -- DERIVED etc_ore/1-20N n472
       (1210111, 2040902, 1, 1, 0, 750),   -- DERIVED scroll/1-20N n552
       (1210111, 1040011, 1, 1, 0, 700),   -- DERIVED equip/1-20N n1801
       (1210111, 1040034, 1, 1, 0, 700),   -- DERIVED equip/1-20N n1801
       (1210111, 1060024, 1, 1, 0, 700),   -- DERIVED equip/1-20N n1801
       (1210111, 2060000, 1, 1, 0, 20000),   -- DERIVED use/1-20N n674
       (1210111, 4010002, 1, 1, 0, 9000),   -- DERIVED etc_ore/1-20N n472
       (1210111, 4020001, 1, 1, 0, 9000),   -- DERIVED etc_ore/1-20N n472
       (1210111, 4000021, 1, 1, 0, 300000),   -- DERIVED etc_mobdrop/1-20N n197
       (1210111, 2061000, 1, 1, 0, 20000),   -- DERIVED use/1-20N n674
       (1210111, 1402018, 1, 1, 0, 700),   -- DERIVED equip/1-20N n1801
       (1210111, 1032003, 1, 1, 0, 700),   -- DERIVED equip/1-20N n1801
       (1210111, 1041012, 1, 1, 0, 700),   -- DERIVED equip/1-20N n1801
       (1210111, 1040014, 1, 1, 0, 700),   -- DERIVED equip/1-20N n1801
       (1210111, 4030012, 1, 1, 0, 6000),   -- DERIVED etc_quest/1-20N n174
       (1210111, 1492000, 1, 1, 0, 700),   -- DERIVED equip/1-20N n1801
       (1210111, 1052098, 1, 1, 0, 700),   -- DERIVED equip/1-20N n1801
       (1210111, 1072285, 1, 1, 0, 700),   -- DERIVED equip/1-20N n1801
       -- 2110301 Scorpion  Lv.29
       (2110301, 4031568, 1, 1, 0, 6000),   -- DERIVED etc_quest/21-40N n280
       -- 2220110 Crying Blue Mushroom  Lv.20
       (2220110, 4000009, 1, 1, 0, 300000),   -- DERIVED etc_mobdrop/1-20N n197
       (2220110, 2000001, 1, 1, 0, 20000),   -- DERIVED use/1-20N n674
       (2220110, 2002002, 1, 1, 0, 20000),   -- DERIVED use/1-20N n674
       (2220110, 2044002, 1, 1, 0, 750),   -- DERIVED scroll/1-20N n552
       (2220110, 1332006, 1, 1, 0, 700),   -- DERIVED equip/1-20N n1801
       (2220110, 1051000, 1, 1, 0, 700),   -- DERIVED equip/1-20N n1801
       (2220110, 1002127, 1, 1, 0, 700),   -- DERIVED equip/1-20N n1801
       (2220110, 2060000, 1, 1, 0, 20000),   -- DERIVED use/1-20N n674
       (2220110, 4010006, 1, 1, 0, 9000),   -- DERIVED etc_ore/1-20N n472
       (2220110, 4020005, 1, 1, 0, 9000),   -- DERIVED etc_ore/1-20N n472
       (2220110, 2000003, 1, 1, 0, 20000),   -- DERIVED use/1-20N n674
       (2220110, 1442001, 1, 1, 0, 700),   -- DERIVED equip/1-20N n1801
       (2220110, 1041027, 1, 1, 0, 700),   -- DERIVED equip/1-20N n1801
       (2220110, 1061025, 1, 1, 0, 700),   -- DERIVED equip/1-20N n1801
       (2220110, 1040012, 1, 1, 0, 700),   -- DERIVED equip/1-20N n1801
       (2220110, 1060010, 1, 1, 0, 700),   -- DERIVED equip/1-20N n1801
       (2220110, 2061000, 1, 1, 0, 20000),   -- DERIVED use/1-20N n674
       (2220110, 1072020, 1, 1, 0, 700),   -- DERIVED equip/1-20N n1801
       (2220110, 1432001, 1, 1, 0, 700),   -- DERIVED equip/1-20N n1801
       (2220110, 4020006, 1, 1, 0, 9000),   -- DERIVED etc_ore/1-20N n472
       (2220110, 2000002, 1, 1, 0, 20000),   -- DERIVED use/1-20N n674
       (2220110, 1072011, 1, 1, 0, 700),   -- DERIVED equip/1-20N n1801
       (2220110, 1332001, 1, 1, 0, 700),   -- DERIVED equip/1-20N n1801
       (2220110, 1382002, 1, 1, 0, 700),   -- DERIVED equip/1-20N n1801
       (2220110, 1002178, 1, 1, 0, 700),   -- DERIVED equip/1-20N n1801
       (2220110, 1412012, 1, 1, 0, 700),   -- DERIVED equip/1-20N n1801
       (2220110, 4030012, 1, 1, 0, 6000),   -- DERIVED etc_quest/1-20N n174
       (2220110, 1002613, 1, 1, 0, 700),   -- DERIVED equip/1-20N n1801
       (2220110, 1002616, 1, 1, 0, 700),   -- DERIVED equip/1-20N n1801
       (2220110, 1002619, 1, 1, 0, 700),   -- DERIVED equip/1-20N n1801
       -- 3210200 Jr. Cellion  Lv.33
       (3210200, 2050099, 1, 1, 0, 20000),   -- DERIVED use/21-40N n914
       -- 3210201 Jr. Lioner  Lv.33
       (3210201, 2050099, 1, 1, 0, 20000),   -- DERIVED use/21-40N n914
       -- 4230118 Ultra Gray  Lv.45
       (4230118, 4240000, 1, 1, 0, 6000),   -- DERIVED etc_quest/41-60N n247
       -- 6130100 Red Drake  Lv.60
       (6130100, 2050099, 1, 1, 0, 20000),   -- DERIVED use/41-60N n576
       -- 6300005 Zombie Mushmom  Lv.65, boss
       (6300005, 2011000, 1, 1, 0, 200000),   -- DERIVED use/61-80B n217
       -- 7120103 Red Slime  Lv.70
       (7120103, 2040427, 1, 1, 0, 300),   -- DERIVED scroll/61-80N n245
       (7120103, 2040824, 1, 1, 0, 300),   -- DERIVED scroll/61-80N n245
       (7120103, 2049100, 1, 1, 0, 300),   -- DERIVED scroll/61-80N n245
       (7120103, 4007006, 1, 1, 0, 9000),   -- DERIVED etc_ore/61-80N n227
       (7120103, 1072165, 1, 1, 0, 700),   -- DERIVED equip/61-80N n1089
       (7120103, 2040618, 1, 1, 0, 300),   -- DERIVED scroll/61-80N n245
       (7120103, 4007004, 1, 1, 0, 9000),   -- DERIVED etc_ore/61-80N n227
       (7120103, 4007007, 1, 1, 0, 9000),   -- DERIVED etc_ore/61-80N n227
       -- 7120104 Silver Slime  Lv.71
       (7120104, 1072165, 1, 1, 0, 700),   -- DERIVED equip/61-80N n1089
       (7120104, 2040618, 1, 1, 0, 300),   -- DERIVED scroll/61-80N n245
       (7120104, 4007007, 1, 1, 0, 9000),   -- DERIVED etc_ore/61-80N n227
       (7120104, 4007001, 1, 1, 0, 9000),   -- DERIVED etc_ore/61-80N n227
       -- 7120105 Gold Slime  Lv.72
       (7120105, 4007000, 1, 1, 0, 9000),   -- DERIVED etc_ore/61-80N n227
       (7120105, 4007004, 1, 1, 0, 9000),   -- DERIVED etc_ore/61-80N n227
       -- 7120106 Overlord A  Lv.75
       (7120106, 2040418, 1, 1, 0, 300),   -- DERIVED scroll/61-80N n245
       (7120106, 4007004, 1, 1, 0, 9000),   -- DERIVED etc_ore/61-80N n227
       (7120106, 4007002, 1, 1, 0, 9000),   -- DERIVED etc_ore/61-80N n227
       -- 7120107 Overlord B  Lv.75
       (7120107, 1060095, 1, 1, 0, 700),   -- DERIVED equip/61-80N n1089
       (7120107, 1040107, 1, 1, 0, 700),   -- DERIVED equip/61-80N n1089
       (7120107, 2040622, 1, 1, 0, 300),   -- DERIVED scroll/61-80N n245
       (7120107, 2040623, 1, 1, 0, 300),   -- DERIVED scroll/61-80N n245
       (7120107, 4007004, 1, 1, 0, 9000),   -- DERIVED etc_ore/61-80N n227
       (7120107, 4007006, 1, 1, 0, 9000),   -- DERIVED etc_ore/61-80N n227
       -- 7120108 Robby  Lv.77
       (7120108, 4000550, 1, 1, 0, 400000),   -- DERIVED etc_mobdrop/61-80N n75
       (7120108, 1040106, 1, 1, 0, 700),   -- DERIVED equip/61-80N n1089
       (7120108, 1082116, 1, 1, 0, 700),   -- DERIVED equip/61-80N n1089
       (7120108, 2040321, 1, 1, 0, 300),   -- DERIVED scroll/61-80N n245
       (7120108, 2040534, 1, 1, 0, 300),   -- DERIVED scroll/61-80N n245
       (7120108, 2043114, 1, 1, 0, 300),   -- DERIVED scroll/61-80N n245
       (7120108, 4007000, 1, 1, 0, 9000),   -- DERIVED etc_ore/61-80N n227
       (7120108, 4007006, 1, 1, 0, 9000),   -- DERIVED etc_ore/61-80N n227
       (7120108, 4130000, 1, 1, 0, 6000),   -- DERIVED etc_quest/61-80N n120
       (7120108, 4130002, 1, 1, 0, 6000),   -- DERIVED etc_quest/61-80N n120
       (7120108, 4130013, 1, 1, 0, 6000),   -- DERIVED etc_quest/61-80N n120
       -- 7120109 Iruvata  Lv.79
       (7120109, 1322028, 1, 1, 0, 700),   -- DERIVED equip/61-80N n1089
       (7120109, 2040619, 1, 1, 0, 300),   -- DERIVED scroll/61-80N n245
       (7120109, 2044314, 1, 1, 0, 300),   -- DERIVED scroll/61-80N n245
       (7120109, 2049100, 1, 1, 0, 300),   -- DERIVED scroll/61-80N n245
       (7120109, 4007003, 1, 1, 0, 9000),   -- DERIVED etc_ore/61-80N n227
       (7120109, 4007005, 1, 1, 0, 9000),   -- DERIVED etc_ore/61-80N n227
       (7120109, 4130002, 1, 1, 0, 6000),   -- DERIVED etc_quest/61-80N n120
       (7120109, 4130009, 1, 1, 0, 6000),   -- DERIVED etc_quest/61-80N n120
       -- 8120102 Afterlord  Lv.82
       (8120102, 1402015, 1, 1, 0, 700),   -- DERIVED equip/81-100N n641
       (8120102, 4007001, 1, 1, 0, 9000),   -- DERIVED etc_ore/81-100N n171
       (8120102, 4007003, 1, 1, 0, 9000),   -- DERIVED etc_ore/81-100N n171
       (8120102, 4130000, 1, 1, 0, 6000),   -- DERIVED etc_quest/81-100N n85
       (8120102, 4130013, 1, 1, 0, 6000),   -- DERIVED etc_quest/81-100N n85
       (8120102, 2040532, 1, 1, 0, 300),   -- DERIVED scroll/81-100N n229
       (8120102, 2044112, 1, 1, 0, 300),   -- DERIVED scroll/81-100N n229
       (8120102, 2044807, 1, 1, 0, 300),   -- DERIVED scroll/81-100N n229
       -- 8120103 Prototype Lord  Lv.5
       (8120103, 2040026, 1, 1, 0, 750),   -- DERIVED scroll/1-20N n552
       (8120103, 4007000, 1, 1, 0, 9000),   -- DERIVED etc_ore/1-20N n472
       (8120103, 4007003, 1, 1, 0, 9000),   -- DERIVED etc_ore/1-20N n472
       (8120103, 4130000, 1, 1, 0, 6000),   -- DERIVED etc_quest/1-20N n174
       (8120103, 4130002, 1, 1, 0, 6000),   -- DERIVED etc_quest/1-20N n174
       (8120103, 4130010, 1, 1, 0, 6000),   -- DERIVED etc_quest/1-20N n174
       -- 8120104 Maverick Type A  Lv.86
       (8120104, 4131007, 1, 1, 0, 6000),   -- DERIVED etc_quest/81-100N n85
       (8120104, 1041115, 1, 1, 0, 700),   -- DERIVED equip/81-100N n641
       (8120104, 1061114, 1, 1, 0, 700),   -- DERIVED equip/81-100N n641
       (8120104, 1492010, 1, 1, 0, 700),   -- DERIVED equip/81-100N n641
       (8120104, 2040619, 1, 1, 0, 300),   -- DERIVED scroll/81-100N n229
       (8120104, 4007001, 1, 1, 0, 9000),   -- DERIVED etc_ore/81-100N n171
       (8120104, 4007006, 1, 1, 0, 9000),   -- DERIVED etc_ore/81-100N n171
       (8120104, 4130003, 1, 1, 0, 6000),   -- DERIVED etc_quest/81-100N n85
       (8120104, 4130007, 1, 1, 0, 6000),   -- DERIVED etc_quest/81-100N n85
       (8120104, 4130011, 1, 1, 0, 6000),   -- DERIVED etc_quest/81-100N n85
       -- 8120105 Maverick Type S  Lv.9
       (8120105, 2040323, 1, 1, 0, 750),   -- DERIVED scroll/1-20N n552
       (8120105, 2043214, 1, 1, 0, 750),   -- DERIVED scroll/1-20N n552
       (8120105, 4007000, 1, 1, 0, 9000),   -- DERIVED etc_ore/1-20N n472
       (8120105, 4007002, 1, 1, 0, 9000),   -- DERIVED etc_ore/1-20N n472
       (8120105, 4130001, 1, 1, 0, 6000),   -- DERIVED etc_quest/1-20N n174
       (8120105, 4130007, 1, 1, 0, 6000),   -- DERIVED etc_quest/1-20N n174
       (8120105, 4130012, 1, 1, 0, 6000),   -- DERIVED etc_quest/1-20N n174
       -- 8120106 Maverick Type D  Lv.9
       (8120106, 1050080, 1, 1, 0, 700),   -- DERIVED equip/1-20N n1801
       (8120106, 2040031, 1, 1, 0, 750),   -- DERIVED scroll/1-20N n552
       (8120106, 4130022, 1, 1, 0, 6000),   -- DERIVED etc_quest/1-20N n174
       (8120106, 4007003, 1, 1, 0, 9000),   -- DERIVED etc_ore/1-20N n472
       (8120106, 4007005, 1, 1, 0, 9000),   -- DERIVED etc_ore/1-20N n472
       (8120106, 4130000, 1, 1, 0, 6000),   -- DERIVED etc_quest/1-20N n174
       (8120106, 4130019, 1, 1, 0, 6000),   -- DERIVED etc_quest/1-20N n174
       (8120106, 4130021, 1, 1, 0, 6000),   -- DERIVED etc_quest/1-20N n174
       -- 8140511 Imperial Guard  Lv.91
       (8140511, 1051097, 1, 1, 0, 700),   -- DERIVED equip/81-100N n641
       (8140511, 4007000, 1, 1, 0, 9000),   -- DERIVED etc_ore/81-100N n171
       (8140511, 4007006, 1, 1, 0, 9000),   -- DERIVED etc_ore/81-100N n171
       (8140511, 4130001, 1, 1, 0, 6000),   -- DERIVED etc_quest/81-100N n85
       (8140511, 4130014, 1, 1, 0, 6000),   -- DERIVED etc_quest/81-100N n85
       (8140511, 2040321, 1, 1, 0, 300),   -- DERIVED scroll/81-100N n229
       (8140511, 2040029, 1, 1, 0, 300),   -- DERIVED scroll/81-100N n229
       -- 8140512 Royal Guard  Lv.34
       (8140512, 1322028, 1, 1, 0, 700),   -- DERIVED equip/21-40N n1972
       (8140512, 1050083, 1, 1, 0, 700),   -- DERIVED equip/21-40N n1972
       (8140512, 1041117, 1, 1, 0, 700),   -- DERIVED equip/21-40N n1972
       (8140512, 1061116, 1, 1, 0, 700),   -- DERIVED equip/21-40N n1972
       (8140512, 2044802, 1, 1, 0, 300),   -- DERIVED scroll/21-40N n387
       (8140512, 2040317, 1, 1, 0, 300),   -- DERIVED scroll/21-40N n387
       (8140512, 2044809, 1, 1, 0, 300),   -- DERIVED scroll/21-40N n387
       (8140512, 4007002, 1, 1, 0, 9000),   -- DERIVED etc_ore/21-40N n491
       (8140512, 4130013, 1, 1, 0, 6000),   -- DERIVED etc_quest/21-40N n280
       (8140512, 4130018, 1, 1, 0, 6000),   -- DERIVED etc_quest/21-40N n280
       (8140512, 4130019, 1, 1, 0, 6000),   -- DERIVED etc_quest/21-40N n280
       -- 8300006 Dragonica  Lv.120, boss
       (8300006, 4001401, 1, 1, 0, 500000),   -- DERIVED etc_mobdrop/B/ALL-LEVELS n161 (band n13 < 20)
       (8300006, 2040002, 1, 1, 0, 3000),   -- DERIVED scroll/101-120B n98
       (8300006, 2040005, 1, 1, 0, 3000),   -- DERIVED scroll/101-120B n98
       (8300006, 2040302, 1, 1, 0, 3000),   -- DERIVED scroll/101-120B n98
       (8300006, 2040402, 1, 1, 0, 3000),   -- DERIVED scroll/101-120B n98
       (8300006, 2040502, 1, 1, 0, 3000),   -- DERIVED scroll/101-120B n98
       (8300006, 2040505, 1, 1, 0, 3000),   -- DERIVED scroll/101-120B n98
       (8300006, 2040602, 1, 1, 0, 3000),   -- DERIVED scroll/101-120B n98
       (8300006, 2040702, 1, 1, 0, 3000),   -- DERIVED scroll/101-120B n98
       (8300006, 2040705, 1, 1, 0, 3000),   -- DERIVED scroll/101-120B n98
       (8300006, 2040708, 1, 1, 0, 3000),   -- DERIVED scroll/101-120B n98
       (8300006, 2040802, 1, 1, 0, 3000),   -- DERIVED scroll/101-120B n98
       (8300006, 2040902, 1, 1, 0, 3000),   -- DERIVED scroll/101-120B n98
       (8300006, 2043002, 1, 1, 0, 3000),   -- DERIVED scroll/101-120B n98
       (8300006, 2043102, 1, 1, 0, 3000),   -- DERIVED scroll/101-120B n98
       (8300006, 2043202, 1, 1, 0, 3000),   -- DERIVED scroll/101-120B n98
       (8300006, 2043302, 1, 1, 0, 3000),   -- DERIVED scroll/101-120B n98
       (8300006, 2043702, 1, 1, 0, 3000),   -- DERIVED scroll/101-120B n98
       (8300006, 2043802, 1, 1, 0, 3000),   -- DERIVED scroll/101-120B n98
       (8300006, 2044002, 1, 1, 0, 3000),   -- DERIVED scroll/101-120B n98
       (8300006, 2044102, 1, 1, 0, 3000),   -- DERIVED scroll/101-120B n98
       (8300006, 2044202, 1, 1, 0, 3000),   -- DERIVED scroll/101-120B n98
       (8300006, 2044302, 1, 1, 0, 3000),   -- DERIVED scroll/101-120B n98
       (8300006, 2044402, 1, 1, 0, 3000),   -- DERIVED scroll/101-120B n98
       (8300006, 2044502, 1, 1, 0, 3000),   -- DERIVED scroll/101-120B n98
       (8300006, 2044602, 1, 1, 0, 3000),   -- DERIVED scroll/101-120B n98
       (8300006, 2044702, 1, 1, 0, 3000),   -- DERIVED scroll/101-120B n98
       (8300006, 1051104, 1, 1, 0, 7000),   -- DERIVED equip/101-120B n76
       (8300006, 1052075, 1, 1, 0, 7000),   -- DERIVED equip/101-120B n76
       (8300006, 1052071, 1, 1, 0, 7000),   -- DERIVED equip/101-120B n76
       (8300006, 1052072, 1, 1, 0, 7000),   -- DERIVED equip/101-120B n76
       (8300006, 1052134, 1, 1, 0, 7000),   -- DERIVED equip/101-120B n76
       (8300006, 2047200, 1, 1, 0, 3000),   -- DERIVED scroll/101-120B n98
       (8300006, 2047201, 1, 1, 0, 3000),   -- DERIVED scroll/101-120B n98
       (8300006, 2047202, 1, 1, 0, 3000),   -- DERIVED scroll/101-120B n98
       (8300006, 2047203, 1, 1, 0, 3000),   -- DERIVED scroll/101-120B n98
       (8300006, 2047204, 1, 1, 0, 3000),   -- DERIVED scroll/101-120B n98
       (8300006, 2047205, 1, 1, 0, 3000),   -- DERIVED scroll/101-120B n98
       (8300006, 2047206, 1, 1, 0, 3000),   -- DERIVED scroll/101-120B n98
       (8300006, 2047207, 1, 1, 0, 3000),   -- DERIVED scroll/101-120B n98
       (8300006, 2047208, 1, 1, 0, 3000),   -- DERIVED scroll/101-120B n98
       -- 8300007 Dragon Rider  Lv.120, boss
       (8300007, 2000005, 1, 1, 0, 200000),   -- DERIVED use/101-120B n140
       (8300007, 2049100, 1, 1, 0, 3000),   -- DERIVED scroll/101-120B n98
       (8300007, 2049201, 1, 1, 0, 3000),   -- DERIVED scroll/101-120B n98
       (8300007, 2049203, 1, 1, 0, 3000),   -- DERIVED scroll/101-120B n98
       (8300007, 2049205, 1, 1, 0, 3000),   -- DERIVED scroll/101-120B n98
       (8300007, 2049207, 1, 1, 0, 3000),   -- DERIVED scroll/101-120B n98
       (8300007, 2040502, 1, 1, 0, 3000),   -- DERIVED scroll/101-120B n98
       (8300007, 2040505, 1, 1, 0, 3000),   -- DERIVED scroll/101-120B n98
       (8300007, 2040514, 1, 1, 0, 3000),   -- DERIVED scroll/101-120B n98
       (8300007, 2040517, 1, 1, 0, 3000),   -- DERIVED scroll/101-120B n98
       (8300007, 2040534, 1, 1, 0, 3000),   -- DERIVED scroll/101-120B n98
       (8300007, 2047000, 1, 1, 0, 3000),   -- DERIVED scroll/101-120B n98
       (8300007, 2047001, 1, 1, 0, 3000),   -- DERIVED scroll/101-120B n98
       (8300007, 2047002, 1, 1, 0, 3000),   -- DERIVED scroll/101-120B n98
       (8300007, 2047100, 1, 1, 0, 3000),   -- DERIVED scroll/101-120B n98
       (8300007, 2047101, 1, 1, 0, 3000),   -- DERIVED scroll/101-120B n98
       (8300007, 2047102, 1, 1, 0, 3000),   -- DERIVED scroll/101-120B n98
       -- 9300028 Ergoth  Lv.48, boss
       (9300028, 2290096, 1, 1, 0, 10000);  -- DERIVED masterybook/41-60B n66
