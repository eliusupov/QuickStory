package server;

import client.Character;
import client.Job;
import constants.game.GameConstants;
import org.junit.jupiter.api.Test;
import provider.Data;
import provider.DataProvider;
import provider.DataTool;
import server.quest.Quest;
import server.quest.actions.SpAction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A quest's {@code sp} reward belongs to the growth its own {@code job} scope names, not to
 * whatever growth the player happens to be standing in when they hand it in.
 *
 * <p>Before this, {@link SpAction} paid only while the player still held the scoped job. Every
 * Evan growth quest stays completable past that point - {@code Check.img} admits 2200-2218 on all
 * 28 - so advancing with quests open silently forfeited their points, permanently: level-up SP
 * moves to the next book at the same instant, so the abandoned book has no further income. Char 51
 * lost three that way, to an advancement taken 10ms after the hand-in before it.
 *
 * <p>Everything below reads the server's own {@code Act.img} / {@code Check.img} / {@code
 * Skill.wz} rather than restating any of it, so the budget arithmetic in
 * {@link #theEarlyGrowthsBudgetToExactlyTheirSkillCeiling()} is the real v84 design and not a
 * transcription of it. That test is the one that would catch a fix which "restores" SP by paying
 * into the current book: it would push a later growth past the ceiling its skills can absorb.
 *
 * <p><strong>Not a {@code *Test} class on purpose</strong>, per {@link V84Wz}.
 */
class EvanScopedQuestSpRealLoad {

    /** Evan's ten growth jobs, in advancement order. */
    private static final List<Integer> GROWTHS =
            List.of(2200, 2210, 2211, 2212, 2213, 2214, 2215, 2216, 2217, 2218);

    /** SP granted by {@code Character.changeJob} on an advancement into a growth. */
    private static final int ADVANCEMENT_SP = 3;

    /** SP granted by {@code Character.levelUpGainSp} on every non-beginner level. */
    private static final int SP_PER_LEVEL = 3;

    private record Reward(int questId, int sp, int scopedJob, SpAction action) {
    }

    /** Quest.wz is opened once - Act.img is the biggest file in the tree. */
    private static final DataProvider QUEST_WZ = V84Wz.wz("Quest.wz");

    private static List<Reward> rewards;

    /** Every {@code sp} reward in the server's Act.img, with the job its scope names. */
    private static synchronized List<Reward> rewards() {
        if (rewards != null) {
            return rewards;
        }
        List<Reward> found = new ArrayList<>();
        for (Data quest : QUEST_WZ.getData("Act.img").getChildren()) {
            for (Data phase : quest.getChildren()) {
                Data sp = phase.getChildByPath("sp");
                if (sp == null) {
                    continue;
                }
                for (Data award : sp.getChildren()) {
                    Data jobs = award.getChildByPath("job");
                    assertEquals(1, jobs == null ? 0 : jobs.getChildren().size(),
                            "Act.img/" + quest.getName() + " award " + award.getName() + " - the "
                                    + "scope-names-the-book rule assumes exactly one job per award");
                    found.add(new Reward(Integer.parseInt(quest.getName()),
                            DataTool.getInt(award.getChildByPath("sp_value"), 0),
                            DataTool.getInt(jobs.getChildren().getFirst(), -1),
                            new SpAction(mock(Quest.class), sp)));
                }
            }
        }
        assertEquals(28, found.size(), "v84 carries 28 quest SP rewards, all Evan's");
        rewards = found;
        return found;
    }

    /** The {@code sp} node of one quest, ready to run. */
    private static SpAction actionFor(int questId) {
        return rewards().stream()
                .filter(r -> r.questId() == questId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Act.img/" + questId + " carries no sp node"))
                .action();
    }

    private static Character playerOf(Job job) {
        Character chr = mock(Character.class);
        when(chr.getJob()).thenReturn(job);
        return chr;
    }

    /**
     * The defect itself. Quest 22518 is scoped to 2200 and char 51 completed it as 2210; its point
     * belongs to the 1st growth's book 0, not to the 2nd growth's book 1.
     */
    @Test
    void aScopedRewardCreditsItsOwnGrowthAfterTheAdvancement() {
        Character chr = playerOf(Job.EVAN2);    // 2210, one growth past the scope
        actionFor(22518).run(chr, null);
        verify(chr).gainSp(1, 0, false);
    }

    /** And the on-time hand-in is unchanged - same book, same amount. */
    @Test
    void theSameRewardStillCreditsThatGrowthDuringIt() {
        Character chr = playerOf(Job.EVAN1);    // 2200, the scoped job
        actionFor(22518).run(chr, null);
        verify(chr).gainSp(1, 0, false);
    }

    /**
     * The general form, over all 28 rewards and all ten growths: the book is a function of the
     * reward's scope alone. Includes the quests char 51 has open right now - 22528 and 22530-22533,
     * all scoped to 2210 - handed in from every growth up to 2218.
     */
    @Test
    void everyScopedRewardLandsInItsOwnBookFromEveryLaterGrowth() {
        for (Reward reward : rewards()) {
            int expectedBook = GameConstants.getSkillBook(reward.scopedJob());
            for (int growth : GROWTHS) {
                Character chr = playerOf(Job.getById(growth));
                reward.action().run(chr, null);
                verify(chr).gainSp(reward.sp(), expectedBook, false);
            }
        }
    }

    /**
     * The design the fix has to leave intact. A growth's SP income is
     * {@code 3 (advancement) + 3 * (levels in the growth) + its quest rewards}, and for Evan's
     * first three growths that is exactly what its two skills can absorb at max level. Paying a
     * scoped reward into the current book instead would overshoot one budget and starve another.
     *
     * <p>Advancement levels come from {@code Check.img} 22100-22109, skill ceilings from
     * {@code Skill.wz} - both read, neither restated.
     */
    @Test
    void theEarlyGrowthsBudgetToExactlyTheirSkillCeiling() {
        Map<Integer, Integer> advancementLevel = advancementLevels();
        Map<Integer, Integer> questSp = new LinkedHashMap<>();
        for (Reward reward : rewards()) {
            questSp.merge(reward.scopedJob(), reward.sp(), Integer::sum);
        }

        for (int i = 0; i < 3; i++) {      // 2200, 2210, 2211 - the growths that close exactly
            int growth = GROWTHS.get(i);
            int span = advancementLevel.get(GROWTHS.get(i + 1)) - advancementLevel.get(growth);
            int budget = ADVANCEMENT_SP + SP_PER_LEVEL * span + questSp.getOrDefault(growth, 0);
            assertEquals(skillCeiling(growth), budget,
                    "growth " + growth + ": " + ADVANCEMENT_SP + " + " + SP_PER_LEVEL + "*" + span
                            + " + " + questSp.get(growth) + " quest SP must equal what its skills hold");
        }
    }

    /** {@code Check.img/22100-22109} lvmin - the level each advancement becomes legal. */
    private static Map<Integer, Integer> advancementLevels() {
        Map<Integer, Integer> levels = new LinkedHashMap<>();
        Data check = QUEST_WZ.getData("Check.img");
        for (int i = 0; i < GROWTHS.size(); i++) {
            Data phase = check.getChildByPath((22100 + i) + "/0");
            levels.put(GROWTHS.get(i), DataTool.getInt(phase.getChildByPath("lvmin"), -1));
        }
        assertEquals(10, levels.get(2200), "the 1st growth is a level 10 advancement in v84");
        return levels;
    }

    /** Total SP a growth's skills can absorb: the sum of their max levels. */
    private static int skillCeiling(int growth) {
        int ceiling = 0;
        for (Data skill : V84Wz.wz("Skill.wz").getData(growth + ".img").getChildByPath("skill").getChildren()) {
            ceiling += skill.getChildByPath("level").getChildren().size();
        }
        assertTrue(ceiling > 0, "Skill.wz/" + growth + ".img held no skills");
        return ceiling;
    }
}
