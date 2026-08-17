package client.processor.stat;

import client.Character;
import client.Client;
import client.Job;
import client.Skill;
import client.SkillFactory;
import constants.skills.Evan;
import constants.skills.Legend;
import constants.skills.Noblesse;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * AssignSPProcessor.SPAssignAction(): Three Snails / Recovery / Nimble Feet are free for every
 * beginner line, and everything else costs a point out of the job's skill book.
 *
 * <p>The free-skill window was {@code skillid % 10000000 > 999 && < 1003}, which assumes a beginner
 * job id is a round thousand. Evan's is 2001, so its three are 20011000-20011002 and
 * {@code % 10000000} gives 11000-11002 - outside the window. An Evan fell through to the paid path
 * and spent real job SP on them; a beginner Evan, who banks no SP, could not learn them at all. The
 * loop inside the window had the same defect from the other end: {@code getJobType() * 10000000} is
 * 20000000 for an Evan, so the spent-total it accumulates was Aran's Legend skills, never Evan's.
 *
 * <p>SkillFactory is a WZ-backed static map that is empty outside a running server, so it is
 * stubbed. Only getMaxLevel() - the number of level effects - matters to the code under test.
 */
class AssignSPProcessorTest {

    private final Map<Integer, Skill> skills = new HashMap<>();

    private Skill skill(int skillid) {
        return skills.computeIfAbsent(skillid, id -> {
            Skill s = new Skill(id);
            for (int level = 0; level < 3; level++) {
                s.addLevelEffect(null);     // getMaxLevel() only counts these
            }
            return s;
        });
    }

    private Client clientOf(Job job, int level, int sp) {
        Client c = Mockito.mock(Client.class);
        Character chr = Character.getDefault(c);
        chr.setGMLevel(0);      // getDefault() hands out GM 2, which waives the job-tree check
        chr.setJob(job);
        chr.setLevel(level);
        if (sp > 0) {
            chr.gainSp(sp, 0, true);
        }
        Mockito.when(c.getPlayer()).thenReturn(chr);
        return c;
    }

    private int assign(Client c, int skillid) {
        try (MockedStatic<SkillFactory> factory = Mockito.mockStatic(SkillFactory.class)) {
            factory.when(() -> SkillFactory.getSkill(Mockito.anyInt()))
                    .thenAnswer(invocation -> skill(invocation.getArgument(0)));
            AssignSPProcessor.SPAssignAction(c, skillid);
        }
        return c.getPlayer().getSkillLevel(skill(skillid));
    }

    @Test
    void everyBeginnerLineLearnsItsThreeSnailsWithNoSp() {
        Client explorer = clientOf(Job.BEGINNER, 10, 0);
        Client noblesse = clientOf(Job.NOBLESSE, 10, 0);
        Client legend = clientOf(Job.LEGEND, 10, 0);
        Client evan = clientOf(Job.EVAN, 10, 0);

        assertAll(
                () -> assertEquals(1, assign(explorer, 1000), "job 0, skill 1000"),
                () -> assertEquals(1, assign(noblesse, Noblesse.THREE_SNAILS), "job 1000, skill 10001000"),
                () -> assertEquals(1, assign(legend, Legend.THREE_SNAILS), "job 2000, skill 20001000"),
                () -> assertEquals(1, assign(evan, Evan.THREE_SNAILS), "job 2001, skill 20011000")
        );
    }

    @Test
    void beginnerSkillsDoNotSpendJobSp() {
        Client evan = clientOf(Job.EVAN1, 10, 3);   // 2200, holding the 3 SP changeJob() grants

        assertAll(
                () -> assertEquals(1, assign(evan, Evan.NIMBLE_FEET), "20011002 learned"),
                () -> assertEquals(3, evan.getPlayer().getRemainingSps()[0], "book 0 untouched")
        );
    }

    /**
     * The guard cannot be numeric alone: Evan's own Magic Missile is 22001001, so {@code % 10000}
     * lands it in the same 1000-1002 window as a beginner skill. Only the beginner job id keeps it out.
     */
    @Test
    void realJobSkillsStillSpendJobSp() {
        Client evan = clientOf(Job.EVAN1, 10, 3);

        assertAll(
                () -> assertEquals(1, assign(evan, Evan.MAGIC_MISSILE), "22001001 learned"),
                () -> assertEquals(2, evan.getPlayer().getRemainingSps()[0], "book 0 charged one point")
        );
    }
}
