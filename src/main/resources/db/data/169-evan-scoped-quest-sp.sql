-- ============================================================================================
-- Restores the Evan growth-quest SP that scoped rewards failed to award.
--
-- Until SpAction was fixed, a quest's `sp` reward paid only while the player still held the job
-- its `job` scope names. Every Evan growth quest stays completable past that point (Check.img
-- admits 2200-2218 on all 28), so handing one in after the advancement that ended its growth
-- forfeited the point for good: level-up SP moves to the next skill book at the same instant, so
-- the book left behind has no further income and can never be refilled.
--
-- The window is as narrow as the player makes it. Char 51 lost three points to an advancement
-- taken 10ms after the hand-in before it.
--
-- WHAT THIS CREDITS, AND WHY IT CANNOT DOUBLE-PAY
--
-- Nothing stored says what job a character held at the moment they completed a quest, so "was
-- this reward paid?" is not directly answerable. What IS knowable is the ceiling. A growth's
-- designed SP income is
--
--     3 (the advancement itself, Character.changeJob: spGain = 1, +2 for hasSPTable)
--   + 3 x levels spent in that growth  (Character.levelUpGainSp)
--   + the scoped quest rewards the character has actually completed
--
-- and what they hold is (unspent SP in that book) + (levels of skills in that growth's tree).
-- The credit is the gap, capped at the quest SP that could have gone missing:
--
--     credit = GREATEST(0, LEAST(quest_sp, designed - held - spent))
--
-- A character who did every growth quest on time is already at `designed`, so the gap is 0 and
-- they get nothing. Re-running this after it has applied also yields 0 for everyone - it is
-- idempotent by construction, which is also why it has no meaningful rollback.
--
-- Bounded side effect, deliberate: a character who advanced a level or more late legitimately
-- earned less than `designed`, and this tops that up too, because the two causes are
-- indistinguishable in stored data. The cap keeps it bounded by their own completed quest SP and
-- never lets a growth exceed the income v84 designed for it.
--
-- Measured on this database at the time of writing: 1 character, 3 points (char 51, book 0).
-- The Evan chain is the only source of `sp` quest rewards in v84 - all 28 nodes in Act.img are
-- Evan's - so no other class can be affected.
-- ============================================================================================

WITH RECURSIVE
    -- Evan's ten growths: skill book index, the level each advancement becomes legal
    -- (Check.img/22100-22109 lvmin), and where the next one takes over.
    growth (job, book, adv_level, next_adv_level) AS (
        SELECT 2200, 0, 10, 20 UNION ALL SELECT 2210, 1, 20, 30 UNION ALL SELECT 2211, 2, 30, 40
        UNION ALL SELECT 2212, 3, 40, 50 UNION ALL SELECT 2213, 4, 50, 60
        UNION ALL SELECT 2214, 5, 60, 80 UNION ALL SELECT 2215, 6, 80, 100
        UNION ALL SELECT 2216, 7, 100, 120 UNION ALL SELECT 2217, 8, 120, 160
        UNION ALL SELECT 2218, 9, 160, 200
    ),
    -- The 28 `sp` rewards in Act.img, each with the growth its own scope names.
    scoped (quest, sp_value, job) AS (
        SELECT 22500, 1, 2200 UNION ALL SELECT 22506, 1, 2200 UNION ALL SELECT 22509, 1, 2200
        UNION ALL SELECT 22510, 1, 2200 UNION ALL SELECT 22511, 1, 2200
        UNION ALL SELECT 22518, 1, 2200 UNION ALL SELECT 22521, 1, 2200
        UNION ALL SELECT 22524, 1, 2210 UNION ALL SELECT 22527, 1, 2210
        UNION ALL SELECT 22528, 1, 2210 UNION ALL SELECT 22530, 1, 2210
        UNION ALL SELECT 22531, 1, 2210 UNION ALL SELECT 22532, 1, 2210
        UNION ALL SELECT 22533, 1, 2210
        UNION ALL SELECT 22539, 1, 2211 UNION ALL SELECT 22547, 1, 2211
        UNION ALL SELECT 22552, 1, 2211 UNION ALL SELECT 22553, 1, 2211
        UNION ALL SELECT 22557, 1, 2211 UNION ALL SELECT 22559, 1, 2211
        UNION ALL SELECT 22561, 1, 2211
        UNION ALL SELECT 22562, 1, 2212 UNION ALL SELECT 22566, 1, 2212
        UNION ALL SELECT 22569, 1, 2213 UNION ALL SELECT 22574, 2, 2213
        UNION ALL SELECT 22575, 1, 2213 UNION ALL SELECT 22576, 1, 2213
        UNION ALL SELECT 22580, 1, 2214
    ),
    slot (i) AS (
        SELECT 0 UNION ALL SELECT i + 1 FROM slot WHERE i < 9
    ),
    -- Scoped reward SP each character has actually completed, per growth.
    earned AS (
        SELECT q.characterid, g.job, g.book, g.adv_level, g.next_adv_level, SUM(s.sp_value) AS quest_sp
        FROM queststatus q
                 JOIN scoped s ON s.quest = q.quest
                 JOIN growth g ON g.job = s.job
        WHERE q.status = 2
        GROUP BY q.characterid, g.job, g.book, g.adv_level, g.next_adv_level
    ),
    -- Designed income vs what the book actually holds. `sp` is the ten-slot CSV column;
    -- rows that are not ten slots wide are left alone rather than guessed at.
    tally AS (
        SELECT e.characterid,
               e.book,
               e.quest_sp,
               CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(c.sp, ',', e.book + 1), ',', -1) AS SIGNED) AS held,
               COALESCE((SELECT SUM(k.skilllevel)
                         FROM skills k
                         WHERE k.characterid = e.characterid
                           AND k.skillid DIV 10000 = e.job), 0)                                 AS spent,
               3 + 3 * GREATEST(0, LEAST(c.level, e.next_adv_level) - e.adv_level) + e.quest_sp AS designed
        FROM earned e
                 JOIN characters c ON c.id = e.characterid
        WHERE LENGTH(c.sp) - LENGTH(REPLACE(c.sp, ',', '')) = 9
    ),
    credit AS (
        SELECT characterid, book, GREATEST(0, LEAST(quest_sp, designed - held - spent)) AS sp
        FROM tally
    ),
    -- Only characters that are actually owed something get their `sp` column rewritten.
    rebuilt AS (
        SELECT c.id,
               GROUP_CONCAT(
                       CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(c.sp, ',', slot.i + 1), ',', -1) AS SIGNED)
                           + COALESCE(cr.sp, 0)
                       ORDER BY slot.i SEPARATOR ',') AS sp
        FROM characters c
                 JOIN slot
                 LEFT JOIN credit cr ON cr.characterid = c.id AND cr.book = slot.i
        WHERE c.id IN (SELECT characterid FROM credit WHERE sp > 0)
        GROUP BY c.id
    )
UPDATE characters c
    JOIN rebuilt r ON r.id = c.id
SET c.sp = r.sp;
