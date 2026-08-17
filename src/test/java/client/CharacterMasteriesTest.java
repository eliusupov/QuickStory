package client;

import constants.skills.Evan;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import server.StatEffect;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Character.setMasteries(): the master level a job advancement hands out for free.
 *
 * <p>Evan's Hero's Will 22171004 was unlearnable. {@code Skill.isFourthJob()} names it explicitly,
 * so AssignSPProcessor caps its SP at {@code getMasterLevel()}, and nothing anywhere set that: the
 * 2217 arm granted only Maple Warrior and Illusion, {@code Skill.wz/2217.img} carries no
 * {@code masterLevel} node for it, and no mastery book in {@code Item.wz} lists it - for any class.
 * Zero cap, zero ways to raise it.
 *
 * <p>The grant is a flat 10 for every other skill in the list, all of which max at 30. Hero's Will
 * maxes at 5, and a master level over the max would let SP run past the last level effect into
 * {@code Skill.getEffect()}'s IndexOutOfBounds, hence the clamp.
 *
 * <p>SkillFactory is a WZ-backed static map that is empty outside a running server, so it is
 * stubbed; only getMaxLevel() - the number of level effects - matters here. The level counts below
 * are the real {@code Skill.wz} maxima.
 */
class CharacterMasteriesTest {

    private final Map<Integer, Skill> skills = new HashMap<>();

    private Skill skill(int skillid) {
        return skills.computeIfAbsent(skillid, id -> {
            Skill s = new Skill(id);
            int maxLevel = id == Evan.HEROS_WILL ? 5 : 30;
            for (int level = 0; level < maxLevel; level++) {
                s.addLevelEffect(null);
            }
            return s;
        });
    }

    private Character advancedTo(int jobId) {
        Character chr = Character.getDefault(Mockito.mock(Client.class));
        try (MockedStatic<SkillFactory> factory = Mockito.mockStatic(SkillFactory.class)) {
            factory.when(() -> SkillFactory.getSkill(Mockito.anyInt()))
                    .thenAnswer(invocation -> skill(invocation.getArgument(0)));
            chr.setMasteries(jobId);
        }
        return chr;
    }

    @Test
    void evan9GrantsHerosWillItsOwnMaxAsMasterLevel() {
        Character evan9 = advancedTo(2217);

        assertAll(
                () -> assertEquals(5, evan9.getMasterLevel(skill(Evan.HEROS_WILL)), "22171004 capped at its max"),
                () -> assertEquals(10, evan9.getMasterLevel(skill(Evan.MAPLE_WARRIOR)), "22171000 still 10"),
                () -> assertEquals(10, evan9.getMasterLevel(skill(Evan.ILLUSION)), "22171002 still 10")
        );
    }

    @Test
    void everyOtherGrantIsStillTen() {
        Character hero = advancedTo(112);
        Character evan10 = advancedTo(2218);

        assertAll(
                () -> assertEquals(10, hero.getMasterLevel(skill(constants.skills.Hero.BRANDISH)), "1121008"),
                () -> assertEquals(10, evan10.getMasterLevel(skill(Evan.BLESSING_OF_THE_ONYX)), "22181000"),
                () -> assertEquals(10, evan10.getMasterLevel(skill(Evan.BLAZE)), "22181001")
        );
    }

    /** Learning it is only half of it - it was absent from the dispel list too. */
    @Test
    void herosWillCuresAbnormalStatusForEvanToo() {
        assertTrue(StatEffect.isHerosWill(Evan.HEROS_WILL));
    }
}
