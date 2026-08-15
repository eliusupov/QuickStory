-- Ticket 07 - Neo City 2227 playable. Drop tables for the four v84 mobs the ticket adds.
--
-- Additive only. This file does NOT touch 152-drop-data.sql or 153-crimson-sky-drop-data.sql:
-- both are Liquibase changeSets that have already run, so editing either in place would fail
-- checksum validation at startup. A new changeSet is the only form that both deploys and
-- leaves the applied history intact.
--
-- No rate, quantity or questid below was invented. Every row is a verbatim copy of a row that
-- already exists in 152-drop-data.sql for the live-client analogue, with only the dropperid
-- replaced. All four analogues are EXACT NAME MATCHES - v84 named these mobs after live ones
-- and appended a variant suffix - so no level/HP fallback was needed:
--
--   9400658 Imperial Guard Type A <- 8140511 Imperial Guard  24 rows (0 quest-gated)
--   9400659 Dunas Type D          <- 8220010 Dunas            1 rows (1 quest-gated)
--   9400660 Royal Guard Type S    <- 8140512 Royal Guard     40 rows (0 quest-gated)
--   9400661 Afterlord Type A      <- 8120102 Afterlord       15 rows (0 quest-gated)
--
-- QUEST ROWS. Ticket 06 established the rule: a `questid != 0` row may be copied only where
-- the analogue is a NAME match, otherwise an existing quest silently becomes completable on
-- a new mob. Here all four analogues are name matches, so the rule permits every row, and
-- exactly one row is quest-gated: 8220010 -> 9400659, item 4032516 "Time Sand", questid 3735
-- ("The Wreckage of the Missile" - defeat Dunas, deliver the sand). Copying it onto a Dunas
-- variant is what the rule is for, and it is inert in practice: 683070401 sits behind the
-- 2503 gate, which requires 3748 -> ... -> 3736, which requires 3735 already complete.
--
-- WHY 9400659 GETS ONE ROW. Not a truncated copy - it is the shape this server already uses for
-- Neo City bosses. 8220010/8220011/8220012 each carry exactly one row, the quest item at
-- chance 400000, and 8220013 (the final boss) carries none. Their real rewards come from the
-- TD_Battle event manager, not drop_data. Copying the analogue verbatim reproduces that.
-- 80 rows, 4 dropperids.

INSERT INTO drop_data (dropperid, itemid, minimum_quantity, maximum_quantity, questid, chance)
VALUES (9400658, 4000557, 1, 1, 0, 200000),
       (9400658, 2000006, 1, 1, 0, 40000),
       (9400658, 2022003, 1, 1, 0, 3000),
       (9400658, 2043301, 1, 1, 0, 750),
       (9400658, 2040322, 1, 1, 0, 750),
       (9400658, 2040030, 1, 1, 0, 750),
       (9400658, 2290050, 1, 1, 0, 500),
       (9400658, 2049000, 1, 1, 0, 400),
       (9400658, 2049001, 1, 1, 0, 400),
       (9400658, 4010001, 1, 1, 0, 7000),
       (9400658, 4020001, 1, 1, 0, 7000),
       (9400658, 4004003, 1, 1, 0, 3000),
       (9400658, 1002532, 1, 1, 0, 700),
       (9400658, 1402005, 1, 1, 0, 700),
       (9400658, 1312015, 1, 1, 0, 700),
       (9400658, 1050095, 1, 1, 0, 700),
       (9400658, 1382035, 1, 1, 0, 700),
       (9400658, 1072205, 1, 1, 0, 700),
       (9400658, 1082144, 1, 1, 0, 700),
       (9400658, 1052128, 1, 1, 0, 700),
       (9400658, 2290083, 1, 1, 0, 500),
       (9400658, 2290009, 1, 1, 0, 500),
       (9400658, 2290134, 1, 1, 0, 1500),
       (9400658, 0, 581, 871, 0, 400000),
       (9400659, 4032516, 1, 1, 3735, 400000),
       (9400660, 4000558, 1, 1, 0, 200000),
       (9400660, 2000006, 1, 1, 0, 40000),
       (9400660, 2000002, 1, 1, 0, 40000),
       (9400660, 2044602, 1, 1, 0, 750),
       (9400660, 2070006, 1, 1, 0, 400),
       (9400660, 2290082, 1, 1, 0, 500),
       (9400660, 2044804, 1, 1, 0, 750),
       (9400660, 2290097, 1, 1, 0, 500),
       (9400660, 2040318, 1, 1, 0, 750),
       (9400660, 2040929, 1, 1, 0, 750),
       (9400660, 2049000, 1, 1, 0, 400),
       (9400660, 2049001, 1, 1, 0, 400),
       (9400660, 4010001, 1, 1, 0, 7000),
       (9400660, 4020008, 1, 1, 0, 7000),
       (9400660, 4004000, 1, 1, 0, 3000),
       (9400660, 1422010, 1, 1, 0, 700),
       (9400660, 1082115, 1, 1, 0, 700),
       (9400660, 1051079, 1, 1, 0, 700),
       (9400660, 1040112, 1, 1, 0, 700),
       (9400660, 1060101, 1, 1, 0, 700),
       (9400660, 1002254, 1, 1, 0, 700),
       (9400660, 1050074, 1, 1, 0, 700),
       (9400660, 1050072, 1, 1, 0, 700),
       (9400660, 1082132, 1, 1, 0, 700),
       (9400660, 1051063, 1, 1, 0, 700),
       (9400660, 1002278, 1, 1, 0, 700),
       (9400660, 1050076, 1, 1, 0, 700),
       (9400660, 1051068, 1, 1, 0, 700),
       (9400660, 1002285, 1, 1, 0, 700),
       (9400660, 1002327, 1, 1, 0, 700),
       (9400660, 1041106, 1, 1, 0, 700),
       (9400660, 1061105, 1, 1, 0, 700),
       (9400660, 1040117, 1, 1, 0, 700),
       (9400660, 1060106, 1, 1, 0, 700),
       (9400660, 1072315, 1, 1, 0, 700),
       (9400660, 2290013, 1, 1, 0, 500),
       (9400660, 2290131, 1, 1, 0, 1500),
       (9400660, 2290067, 1, 1, 0, 500),
       (9400660, 2290116, 1, 1, 0, 500),
       (9400660, 0, 608, 908, 0, 400000),
       (9400661, 4000552, 1, 1, 0, 200000),
       (9400661, 2330004, 1, 1, 0, 400),
       (9400661, 2044113, 1, 1, 0, 750),
       (9400661, 2049000, 1, 1, 0, 400),
       (9400661, 4010000, 1, 1, 0, 7000),
       (9400661, 4020000, 1, 1, 0, 7000),
       (9400661, 4004002, 1, 1, 0, 3000),
       (9400661, 1102028, 1, 1, 0, 700),
       (9400661, 1072179, 1, 1, 0, 700),
       (9400661, 1082112, 1, 1, 0, 700),
       (9400661, 1452011, 1, 1, 0, 700),
       (9400661, 1041107, 1, 1, 0, 700),
       (9400661, 1040110, 1, 1, 0, 700),
       (9400661, 1002640, 1, 1, 0, 700),
       (9400661, 0, 446, 659, 0, 400000);
