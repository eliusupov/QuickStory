-- ============================================================================================
-- Backfill Evan skills whose SP cap comes from a Skill.wz masterLevel node but that were never
-- seeded on job advance. Before the Character.setMasteries fix (added 2211/2214 branches), these
-- skills were granted with masterlevel 0, so the player saw master level 0 and could not spend SP.
--
-- Parity truth (Skill.wz, this repo's wz/Skill.wz/22*.img):
--   22111001 Magic Guard    - job 2211 (Evan III), masterLevel 5
--   22140000 Critical Magic - job 2214 (Evan VI),  masterLevel 5
--   22141002 Magic Booster  - job 2214 (Evan VI),  masterLevel 5
-- (2217/2218's masterLevel-node skills are already seeded by setMasteries and excluded here.)
--
-- Scoped to Evan jobs at/past the growth that grants each skill, and only where no row exists
-- (a row already carrying masterlevel >= the wz cap - e.g. from a mastery book or a GM grant - is
-- left untouched). skilllevel 0 is deliberate: SP spending is left to the player. expiration -1.
--
-- Measured on this database (root@cosmic) before applying:
--   Evan chars: 48 ghfgh (job 2200), 50 evan (job 2200), 51 evan2 (job 2211).
--   * 48 and 50 are job 2200 - below Evan III - so out of scope for every insert below.
--   * 48 already holds 22111001/22140000/22141002 at masterlevel 20/15/15 (manual GM grant); the
--     NOT EXISTS guard and the job filter both exclude it regardless.
--   * 51 evan2 is job 2211 and has NO 22111001 row - this is the reported bug.
--   No char is at job 2214+ , so the Critical Magic / Magic Booster inserts touch 0 rows today.
-- Expected effect: exactly 1 row inserted - (charid 51, skillid 22111001, 0, 5, -1).
--
-- Precedent for correcting stale/missing Evan skill rows with a new changeSet rather than editing
-- a frozen one: changeSet 173 (evan-stale-legend-blessing-skill).
-- ============================================================================================

INSERT INTO skills (characterid, skillid, skilllevel, masterlevel, expiration)
SELECT c.id, 22111001, 0, 5, -1
  FROM characters c
 WHERE c.job BETWEEN 2211 AND 2218
   AND NOT EXISTS (SELECT 1 FROM skills s WHERE s.characterid = c.id AND s.skillid = 22111001);

INSERT INTO skills (characterid, skillid, skilllevel, masterlevel, expiration)
SELECT c.id, 22140000, 0, 5, -1
  FROM characters c
 WHERE c.job BETWEEN 2214 AND 2218
   AND NOT EXISTS (SELECT 1 FROM skills s WHERE s.characterid = c.id AND s.skillid = 22140000);

INSERT INTO skills (characterid, skillid, skilllevel, masterlevel, expiration)
SELECT c.id, 22141002, 0, 5, -1
  FROM characters c
 WHERE c.job BETWEEN 2214 AND 2218
   AND NOT EXISTS (SELECT 1 FROM skills s WHERE s.characterid = c.id AND s.skillid = 22141002);
