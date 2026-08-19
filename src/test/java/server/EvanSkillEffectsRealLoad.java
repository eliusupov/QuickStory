package server;

import client.BuffStat;
import client.Skill;
import client.SkillFactory;
import constants.skills.Bishop;
import constants.skills.Evan;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import provider.wz.WZFiles;
import server.maps.Mist;
import tools.Pair;

import java.awt.Rectangle;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void soulStoneIsNotImmediateResurrection() {
        StatEffect soulStone = SkillFactory.getSkill(Evan.SOUL_STONE).getEffect(20);
        assertAll(
                () -> assertFalse(soulStone.isResurrection(), "22181003 must not use the immediate resurrection path"),
                () -> assertEquals(50, soulStone.getX(), "revives at 50%"),
                () -> assertEquals(2, soulStone.getY(), "two party members"),
                () -> assertEquals(300000, soulStone.getDuration(), "300 seconds"),
                () -> assertEquals(0, SkillFactory.getSkill(Bishop.RESURRECTION).getEffect(10).getX(),
                        "Resurrection has no x, so it still revives at full"),
                () -> assertTrue(SkillFactory.getSkill(Bishop.RESURRECTION).getEffect(10).isResurrection(),
                        "Resurrection unchanged")
        );
    }

    /**
     * 22161003. The recovery tick read the <em>recipient's</em> skill level, so a party member
     * standing in the aura without the skill produced getEffect(0) - and Skill.getEffect() is
     * {@code effects.get(level - 1)}, so that is effects.get(-1), thrown inside a scheduled task.
     * The Mist already holds the StatEffect it was cast with, which is the caster's.
     */
    @Test
    void recoveryAuraReadsTheCastersLevel() {
        StatEffect cast = SkillFactory.getSkill(Evan.RECOVERY_AURA).getEffect(15);
        Mist mist = new Mist(new Rectangle(), null, cast);

        assertAll(
                () -> assertTrue(mist.isRecoveryMist(), "22161003 spawns a recovery mist"),
                () -> assertEquals(80, mist.getSourceX(), "x at level 15"),
                () -> assertThrows(IndexOutOfBoundsException.class,
                        () -> SkillFactory.getSkill(Evan.RECOVERY_AURA).getEffect(0),
                        "what a party member without the skill used to ask for")
        );
    }
}
