package server.quest.actions;

import client.Character;
import client.Job;
import config.YamlConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Quest exp is flat 1x while the character is still a beginner; past the 1st job advancement the
 * existing rate formula applies unchanged.
 * <p>
 * {@link Character#isBeginnerJob()} is exercised for real here - the {@code job} field is set by
 * reflection and only that one method is un-stubbed - so the 2001-vs-2200 boundary is asserted
 * against the real predicate, not against a {@code when(...)} that would agree with anything.
 */
class ExpActionTest {

    private static final int RAW = 100;
    private static final int EXP_RATE = 5;
    private static final int QUEST_EXP_RATE = 7;

    /** A mock whose {@code isBeginnerJob()} is the real one, driven by a real job id. */
    private Character playerOf(Job job) {
        Character chr = mock(Character.class);
        try {
            Field f = Character.class.getDeclaredField("job");
            f.setAccessible(true);
            f.set(chr, job);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Character.job moved - this test no longer drives the real predicate", e);
        }
        doCallRealMethod().when(chr).isBeginnerJob();
        when(chr.getExpRate()).thenReturn(EXP_RATE);
        when(chr.getQuestExpRate()).thenReturn(QUEST_EXP_RATE);
        return chr;
    }

    private void withQuestRate(boolean on, Runnable body) {
        boolean original = YamlConfig.config.server.USE_QUEST_RATE;
        try {
            YamlConfig.config.server.USE_QUEST_RATE = on;
            body.run();
        } finally {
            YamlConfig.config.server.USE_QUEST_RATE = original;
        }
    }

    /** The four beginner job ids. 2001 is the one an id-range check misses. */
    @Test
    void thePredicateHoldsForEveryBeginnerJobIncludingEvans() {
        for (Job job : new Job[]{Job.BEGINNER, Job.NOBLESSE, Job.LEGEND, Job.EVAN}) {
            assertTrue(playerOf(job).isBeginnerJob(), job + " (" + job.getId() + ") is a beginner job");
        }
    }

    /** Evan's 1st job is 2200, not 2100 - past the gate like any other 1st job. */
    @Test
    void thePredicateIsFalseOnceAdvancedIncludingEvansOwn2200() {
        for (Job job : new Job[]{Job.EVAN1, Job.WARRIOR, Job.MAGICIAN, Job.DAWNWARRIOR1, Job.ARAN1}) {
            assertFalse(playerOf(job).isBeginnerJob(), job + " (" + job.getId() + ") has advanced");
        }
    }

    @Test
    void aBeginnerIsPaidTheRawNumberWithQuestRateOff() {
        withQuestRate(false, () -> {
            Character chr = playerOf(Job.BEGINNER);
            ExpAction.runAction(chr, RAW);
            verify(chr).gainExp(RAW, true, true);
            verify(chr, never()).getExpRate();
        });
    }

    @Test
    void anEvanBeginnerIsPaidTheRawNumberWithQuestRateOn() {
        withQuestRate(true, () -> {
            Character chr = playerOf(Job.EVAN); // 2001
            ExpAction.runAction(chr, RAW);
            verify(chr).gainExp(RAW, true, true);
            verify(chr, never()).getQuestExpRate();
        });
    }

    /** Past the gate, byte-for-byte the old behaviour: the plain exp rate when USE_QUEST_RATE is off. */
    @Test
    void anAdvancedCharacterStillGetsTheExpRateWhenQuestRateIsOff() {
        withQuestRate(false, () -> {
            Character chr = playerOf(Job.EVAN1); // 2200
            ExpAction.runAction(chr, RAW);
            verify(chr).gainExp(RAW * EXP_RATE, true, true);
        });
    }

    /** ...and the quest exp rate when it is on. */
    @Test
    void anAdvancedCharacterStillGetsTheQuestExpRateWhenQuestRateIsOn() {
        withQuestRate(true, () -> {
            Character chr = playerOf(Job.EVAN1); // 2200
            ExpAction.runAction(chr, RAW);
            verify(chr).gainExp(RAW * QUEST_EXP_RATE, true, true);
        });
    }
}
