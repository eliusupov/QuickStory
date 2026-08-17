package constants.game;

import client.Job;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * GameConstants.getJobBranch(): what it actually returns across the Evan ladder, and what that
 * reaches. Pinning the current, wrong values - this class documents a defect, it does not assert a
 * fix.
 *
 * <p>The function reads a job id as {@code 0} for a round thousand, {@code 1} for a round hundred
 * and {@code 2 + (jobid % 10)} otherwise. Evan breaks both halves of that: its beginner job 2001 is
 * the only beginner id that is not a round thousand, and it has ten advancements (2200-2218) where
 * every other class has four, so the trailing digit runs to 8 and the branch number to 10.
 *
 * <p>Both consequences are dormant on the shipped config: {@code USE_ENFORCE_JOB_LEVEL_RANGE} and
 * {@code USE_ENFORCE_JOB_SP_RANGE} are false, and the SP-range path is additionally behind
 * {@code !hasSPTable(job)}, which is false for every Evan job. getSkillBook() does not consult this
 * method, so the ten-slot SP table is not reached from here.
 */
class GameConstantsJobBranchTest {

    @Test
    void evansBeginnerJobReadsAsAThirdJobCharacter() {
        assertAll(
                () -> assertEquals(0, GameConstants.getJobBranch(Job.BEGINNER), "job 0"),
                () -> assertEquals(0, GameConstants.getJobBranch(Job.NOBLESSE), "job 1000"),
                () -> assertEquals(0, GameConstants.getJobBranch(Job.LEGEND), "job 2000, Aran's beginner"),
                () -> assertEquals(3, GameConstants.getJobBranch(Job.EVAN), "job 2001 - wrong, should be 0")
        );
    }

    /**
     * The only caller reachable for job 2001: an Evan beginner is capped at level 120 where Aran's
     * 2000 is capped at 10. Behind USE_ENFORCE_JOB_LEVEL_RANGE, which ships false.
     */
    @Test
    void evansBeginnerJobIsNotCappedAtTheBeginnerMaxLevel() {
        assertAll(
                () -> assertEquals(10, GameConstants.getJobMaxLevel(Job.LEGEND), "job 2000"),
                () -> assertEquals(120, GameConstants.getJobMaxLevel(Job.EVAN), "job 2001 - wrong, should be 10")
        );
    }

    /** Evan's ten advancements run the branch number to 10; no other class exceeds 4. */
    @Test
    void evansLadderRunsTheBranchNumberPastEveryOtherClass() {
        assertAll(
                () -> assertEquals(4, GameConstants.getJobBranch(Job.HERO), "job 112, the Explorer ceiling"),
                () -> assertEquals(4, GameConstants.getJobBranch(Job.ARAN4), "job 2112"),
                () -> assertEquals(4, GameConstants.getJobBranch(Job.DAWNWARRIOR4), "job 1112"),
                () -> assertEquals(1, GameConstants.getJobBranch(Job.EVAN1), "job 2200"),
                () -> assertEquals(2, GameConstants.getJobBranch(Job.EVAN2), "job 2210"),
                () -> assertEquals(5, GameConstants.getJobBranch(Job.EVAN5), "job 2213"),
                () -> assertEquals(10, GameConstants.getJobBranch(Job.EVAN10), "job 2218")
        );
    }

    /**
     * jobUpgradeBlob and jobUpgradeSpUp are five entries wide, indexed by the branch number, so
     * every Evan job past 2212 indexes off the end. Unreachable as shipped - both readers sit behind
     * USE_ENFORCE_JOB_SP_RANGE (false) and behind {@code !hasSPTable}, true for no Evan job - but it
     * is the trap any future caller of getJobBranch walks into.
     */
    @Test
    void theJobUpgradeTablesDoNotReachEvansLaterBranches() {
        assertAll(
                () -> assertThrows(ArrayIndexOutOfBoundsException.class,
                        () -> GameConstants.getJobUpgradeLevelRange(GameConstants.getJobBranch(Job.EVAN5))),
                () -> assertThrows(ArrayIndexOutOfBoundsException.class,
                        () -> GameConstants.getChangeJobSpUpgrade(GameConstants.getJobBranch(Job.EVAN10)))
        );
    }
}
