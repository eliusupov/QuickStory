package client;

import constants.game.GameConstants;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Character.levelUpGainSp(): who banks 3 SP per level and who banks none.
 *
 * <p>The guard used to be {@code GameConstants.getJobBranch(job) == 0}. Job 2001 is Evan's beginner
 * job and the only beginner id that is not a round thousand, so getJobBranch returns 3 for it and
 * every Evan beginner banked 3 SP per level - about 27 by the level-10 advancement, on top of the 3
 * the advancement itself grants. isBeginnerJob() already covers 2001 and agrees with getJobBranch
 * on every other job.
 *
 * <p>levelUp() itself cannot be driven here: it ends in getMap().broadcastMessage() and a party
 * update. Reflection reaches the one method under test without widening it in production code.
 */
class CharacterLevelUpSpTest {

    private static int spAfterOneLevel(Job job) throws Exception {
        Character chr = Character.getDefault(Mockito.mock(Client.class));
        chr.setJob(job);
        chr.setLevel(9);

        Method levelUpGainSp = Character.class.getDeclaredMethod("levelUpGainSp");
        levelUpGainSp.setAccessible(true);
        levelUpGainSp.invoke(chr);

        return chr.getRemainingSps()[GameConstants.getSkillBook(job.getId())];
    }

    @Test
    void beginnersBankNoSp() throws Exception {
        assertAll(
                () -> assertEquals(0, spAfterOneLevel(Job.BEGINNER), "job 0"),
                () -> assertEquals(0, spAfterOneLevel(Job.NOBLESSE), "job 1000"),
                () -> assertEquals(0, spAfterOneLevel(Job.LEGEND), "job 2000, Aran's beginner"),
                () -> assertEquals(0, spAfterOneLevel(Job.EVAN), "job 2001, Evan's beginner")
        );
    }

    /** The trap the guard sidesteps: getJobBranch still calls 2001 a 3rd-job character. */
    @Test
    void getJobBranchStillMisreadsEvansBeginnerJob() {
        assertAll(
                () -> assertEquals(0, GameConstants.getJobBranch(Job.LEGEND)),
                () -> assertEquals(3, GameConstants.getJobBranch(Job.EVAN))
        );
    }

    @Test
    void advancedJobsBankThreeSpPerLevel() throws Exception {
        assertAll(
                () -> assertEquals(3, spAfterOneLevel(Job.MAGICIAN), "job 200"),
                () -> assertEquals(3, spAfterOneLevel(Job.FP_MAGE), "job 211"),
                () -> assertEquals(3, spAfterOneLevel(Job.ARAN1), "job 2100"),
                () -> assertEquals(3, spAfterOneLevel(Job.EVAN1), "job 2200"),
                () -> assertEquals(3, spAfterOneLevel(Job.EVAN2), "job 2210, into skill book 1")
        );
    }
}
