-- ============================================================================================
-- Remove the stale 20000012 ("Blessing of the Fairy", Legend's own skill) rows that Evan logins
-- used to write. Evan's own constant is 20010012 (constants/skills/Evan.java:5), so 20000012 is
-- neither written nor read for an Evan any more: the rows grant nothing.
--
-- Scoped to Evan jobs only (2001 and 2200-2218). Measured on this database before applying:
--   skillid 20000012 exists on charids 4 (job 230), 8 (1100), 17 (2110), 19 (421), 21 (300),
--   27 (2110), 48 (2200), 50 (2200).
--   * charids 17 and 27 are job 2110 - Aran third job - where 20000012 IS Legend's own skill and
--     the row is legitimate. They are deliberately spared.
--   * charids 4, 8, 19, 21 are outside the scope entirely.
-- Expected effect: exactly 2 rows removed (charids 48 and 50), leaving 6 in the table.
--
-- Precedent for correcting an applied row with a new changeSet rather than editing a frozen one:
-- changeSets 164, 165 and 167.
-- ============================================================================================

DELETE FROM skills WHERE skillid = 20000012
  AND characterid IN (SELECT id FROM characters WHERE job BETWEEN 2200 AND 2218 OR job = 2001);
