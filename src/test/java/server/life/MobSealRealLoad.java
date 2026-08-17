package server.life;

import client.SkillFactory;
import client.status.MonsterStatus;
import client.status.MonsterStatusEffect;
import constants.skills.BlazeWizard;
import constants.skills.Crusader;
import constants.skills.FPMage;
import constants.skills.ILMage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import provider.wz.WZFiles;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Seal has to actually stop the mob from casting.
 *
 * <p>{@code String.wz/Skill.img/2111004/desc}: "Once sealed, monsters can't use skills." All three
 * Seals ({@link FPMage#SEAL} 2211004, {@link ILMage#SEAL} 2111004, {@link BlazeWizard#SEAL}
 * 12111002) write {@link MonsterStatus#SEAL} (0x400, the mob debuff the client draws), but
 * {@code Monster.canUseSkill} only ever consulted {@link MonsterStatus#SEAL_SKILL} (0x4000000) -
 * which nothing but the three Crash skills and mob skill 157 writes. So the icon appeared on the
 * mob, the client played the animation, and the mob went on casting.
 *
 * <p>The two constants are NOT redundant and this does not merge them: SEAL is the client-facing
 * status the mage skills own, SEAL_SKILL stays the crash/mob-skill one. Only the gate learned to
 * read both. {@code sealDoesNotStickToBosses} is the pre-existing boss carve-out at
 * {@code StatEffect.applyMonsterBuff}, restated here so a future change to the gate cannot silently
 * start shutting bosses down.
 *
 * <p><strong>Not a {@code *Test} class on purpose</strong>: {@link WZFiles#DIRECTORY} is a
 * {@code static final} resolved once per JVM and {@code MobSkillFactoryTest} repoints
 * {@code wz-path} at a {@code @TempDir}.
 */
class MobSealRealLoad {

    @BeforeAll
    static void loadSkills() {
        SkillFactory.loadAllSkills();
    }

    /** What the three Seals put on the mob, straight out of Skill.wz through the real StatEffect. */
    @Test
    void allThreeSealsWriteTheSealStatus() {
        assertAll(
                () -> assertTrue(stati(FPMage.SEAL, 20).containsKey(MonsterStatus.SEAL), "2211004"),
                () -> assertTrue(stati(ILMage.SEAL, 20).containsKey(MonsterStatus.SEAL), "2111004"),
                () -> assertTrue(stati(BlazeWizard.SEAL, 20).containsKey(MonsterStatus.SEAL), "12111002"),
                () -> assertTrue(stati(Crusader.ARMOR_CRASH, 20).containsKey(MonsterStatus.SEAL_SKILL),
                        "crash keeps SEAL_SKILL - the gate reads both, it did not swap one for the other"));
    }

    /** Before the fix this returned true: the gate never looked at SEAL. */
    @Test
    void aSealedMobCannotCast() {
        Monster mob = mob();
        MobSkill toUse = MobSkillFactory.getMobSkillOrThrow(MobSkillType.SEAL, 1);

        assertTrue(mob.canUseSkill(toUse, false), "an untouched mob must still be able to cast");

        buff(mob, MonsterStatus.SEAL);
        assertFalse(mob.canUseSkill(toUse, false), "2111004's own help text: sealed monsters can't use skills");
    }

    /** The blast radius check: every other mob debuff must leave casting alone. */
    @Test
    void otherDebuffsStillLetTheMobCast() {
        MobSkill toUse = MobSkillFactory.getMobSkillOrThrow(MobSkillType.SEAL, 1);
        for (MonsterStatus status : MonsterStatus.values()) {
            if (status == MonsterStatus.SEAL || status == MonsterStatus.SEAL_SKILL) {
                continue;
            }
            Monster mob = mob();
            buff(mob, status);
            assertTrue(mob.canUseSkill(toUse, false), status + " must not gate skill use");
        }
    }

    private static Map<MonsterStatus, Integer> stati(int skillId, int level) {
        return SkillFactory.getSkill(skillId).getEffect(level).getMonsterStati();
    }

    /** A bare mob with enough mp for MobSkill 120's mpCon of 5. No map, no channel, no database. */
    private static Monster mob() {
        MonsterStats stats = new MonsterStats();
        stats.setMp(100);
        return new Monster(9300018, stats);
    }

    /**
     * {@code applyStatus} needs a MapleMap and a running channel to broadcast through, so the status
     * goes in the way {@code applyStatus} would leave it. {@code canUseSkill} reads it back through
     * the real {@link Monster#isBuffed}.
     */
    private static void buff(Monster mob, MonsterStatus status) {
        try {
            Field stati = Monster.class.getDeclaredField("stati");
            stati.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<MonsterStatus, MonsterStatusEffect> map = (Map<MonsterStatus, MonsterStatusEffect>) stati.get(mob);
            map.put(status, new MonsterStatusEffect(Map.of(status, 1), null, null, false));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Monster.stati is gone or changed shape - re-point this test", e);
        }
    }
}
