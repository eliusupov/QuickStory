-- ============================================================================================
-- monstercarddata: seven rows name a different mob than the WZ leaf the card was built from.
-- Authority is wz/Item.wz/Consume/0238.img.xml -> <id>/info/mob, the leaf the client itself reads.
-- Every value below was read out of that file at the line named; none is inferred.
--
--   card     table has   corrected to   0238.img.xml line
--   2383045  6130102     6130103        :5265
--   2388011  9300105     9300119        :9180
--   2388017  6400006     8150000        :9370
--   2388026  6400008     8130100        :9653
--   2388043  8820001     8820000        :9996
--   2388068  3300006     3300007        :10202   <- 2388068/2388069 are swapped with each other
--   2388069  3300007     3300006        :10231   <- and must be corrected as a pair
--
-- CLASSIFICATION: v83 legacy, NOT a v84 parity gap. All seven items have zero add-list rows;
-- the wrong attribution predates the v84 cutover. Do not relabel this as a parity fix.
--
-- Effect is limited to which mob a card is attributed to in the monster book. No loot visibility
-- and no player-facing gate changes. Row count in monstercarddata is unchanged (382 before, 382
-- after) - these are UPDATEs only, no INSERT and no DELETE.
--
-- Measured on this database immediately before applying: all seven rows still carried the stale
-- mobid listed in the "table has" column above.
--
-- drop_data IS STILL INCONSISTENT AFTER THIS CHANGESET. Do not read it as having made the two
-- tables agree. 152-drop-data.sql carries both pairings for five of the cards, and only the
-- swapped pairing for the other two:
--   2383045  correct :5032 (6130103)   stale also present :5000  (6130102)
--   2388011  correct :10481 (9300119)  stale also present :17523 (9300105)
--   2388017  correct :10486 (8150000)  stale also present :19186 (6400006)
--   2388026  correct :10493 (8130100)  stale also present :17957 (6400008)
--   2388043  correct :19975 (8820000)  stale also present :11817 (8820001)
--   2388068  no correct row            only :9482 (3300006) - the swapped pairing
--   2388069  no correct row            only :9556 (3300007) - the swapped pairing
-- Whether to correct drop_data is a separate, larger decision: those rows are live drop sources
-- and removing one changes what a mob drops. This changeSet does not touch drop_data.
--
-- R19 WAS WITHDRAWN and ships nothing here. It proposed adding questid 8732 to the
-- 4031405 / dropper 9500108 drop_data row. Quest 8732 has no entry in ANY wz/Quest.wz archive
-- (QuestInfo.img.xml, Check.img.xml, Act.img.xml all return zero matches). Character.needQuestItem
-- (Character.java:5825) returns false when Quest.getStartItemAmountNeeded yields Integer.MIN_VALUE,
-- which is what a quest with no data yields - so that UPDATE would make 4031405 permanently
-- unlootable from 9500108 for every character. This repo already rejected the same row as evidence
-- in 156-evan-chain-drop-data.sql:186-187. The other R19 row (4031568 / 2110301 / questid 3911) is
-- not a regression but is not attached here either; re-file it on its own if wanted.
--
-- Precedent for correcting applied rows in place with a new changeSet rather than editing a frozen
-- one: changeSets 164, 165, 167 and 173.
-- ============================================================================================

UPDATE monstercarddata SET mobid = 6130103 WHERE cardid = 2383045;
UPDATE monstercarddata SET mobid = 9300119 WHERE cardid = 2388011;
UPDATE monstercarddata SET mobid = 8150000 WHERE cardid = 2388017;
UPDATE monstercarddata SET mobid = 8130100 WHERE cardid = 2388026;
UPDATE monstercarddata SET mobid = 8820000 WHERE cardid = 2388043;
UPDATE monstercarddata SET mobid = 3300007 WHERE cardid = 2388068;
UPDATE monstercarddata SET mobid = 3300006 WHERE cardid = 2388069;
