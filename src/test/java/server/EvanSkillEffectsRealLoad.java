package server;

import client.BuffStat;
import client.Skill;
import client.SkillFactory;
import constants.skills.Evan;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import provider.wz.WZFiles;
import tools.Pair;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What Evan's skills actually load as, straight out of {@code Skill.wz/2200-2218.img}.
 *
 * <p>SkillFactory decides "is this a buff" from the shape of the WZ node - an {@code effect} child
 * with no {@code hit}/{@code ball}, or an {@code action/0} of {@code alert2} - and falls back to a
 * hardcoded switch for everything those two miss. A skill the switch does not name loads with
 * {@code overTime = false}, which skips the block in
 * {@code StatEffect.loadSkillEffectFromData()} that turns {@code mad}/{@code pdd}/{@code mdd} into
 * statups, so applyBuffEffect() has nothing to apply and the skill is a no-op.
 *
 * <p><strong>Not a {@code *Test} class on purpose</strong>: {@link WZFiles#DIRECTORY} is a
 * {@code static final} resolved once per JVM and {@code MobSkillFactoryTest} points {@code wz-path}
 * at a {@code @TempDir}.
 */
class EvanSkillEffectsRealLoad {

    @BeforeAll
    static void loadSkills() {
        SkillFactory.loadAllSkills();
    }

    private static int statup(int skillid, int level, BuffStat stat) {
        Skill skill = SkillFactory.getSkill(skillid);
        for (Pair<BuffStat, Integer> statup : skill.getEffect(level).getStatups()) {
            if (statup.getLeft() == stat) {
                return statup.getRight();
            }
        }
        return 0;
    }

    /** 22181000, Evan's capstone. Level 30 is mad 50, pdd 100, mdd 100 for 60s on a 120s cooldown. */
    @Test
    void blessingOfTheOnyxRaisesAttackAndDefence() {
        StatEffect onyx = SkillFactory.getSkill(Evan.BLESSING_OF_THE_ONYX).getEffect(30);

        assertAll(
                () -> assertEquals(50, statup(Evan.BLESSING_OF_THE_ONYX, 30, BuffStat.MATK), "mad"),
                () -> assertEquals(100, statup(Evan.BLESSING_OF_THE_ONYX, 30, BuffStat.WDEF), "pdd"),
                () -> assertEquals(100, statup(Evan.BLESSING_OF_THE_ONYX, 30, BuffStat.MDEF), "mdd"),
                () -> assertEquals(60000, onyx.getDuration(), "time"),
                () -> assertEquals(120, onyx.getCooldown(), "cooltime")
        );
    }
}
