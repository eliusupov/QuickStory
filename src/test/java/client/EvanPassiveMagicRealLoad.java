package client;

import constants.skills.Evan;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import provider.wz.WZFiles;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Character.reapplyLocalStats(): Evan's two magic passives never entered localmagic.
 *
 * <p>Dragon Soul 22000000 is flat {@code mad} 1 to 20 and is the very first skill an Evan gets, at
 * job 2200 - so every Evan on the server has been short its magic attack from level 10 onward.
 * Magic Mastery 22170001 adds a further {@code x}, 15 at level 30. Neither id appeared anywhere in
 * the codebase; the recalc had a branch for the Bowman experts and nothing for Evan.
 *
 * <p><strong>Not a {@code *Test} class on purpose</strong>: {@link WZFiles#DIRECTORY} is a
 * {@code static final} resolved once per JVM and {@code MobSkillFactoryTest} points {@code wz-path}
 * at a {@code @TempDir}. The real effects are needed here - the values are WZ, not constants.
 */
class EvanPassiveMagicRealLoad {

    @BeforeAll
    static void loadSkills() {
        SkillFactory.loadAllSkills();
    }

    /** An Evan10 with the given levels in the two passives, recalculated. */
    private static int totalMagic(int dragonSoulLevel, int magicMasteryLevel) {
        Character chr = Character.getDefault(Mockito.mock(Client.class));
        chr.setJob(Job.EVAN10);
        if (dragonSoulLevel > 0) {
            chr.changeSkillLevel(SkillFactory.getSkill(Evan.DRAGON_SOUL), (byte) dragonSoulLevel, 20, -1);
        }
        if (magicMasteryLevel > 0) {
            chr.changeSkillLevel(SkillFactory.getSkill(Evan.MAGIC_MASTERY), (byte) magicMasteryLevel, 30, -1);
        }
        chr.updateStrDexIntLuk(4, 4, 100, 4, 0);    // triggers the recalc
        return chr.getTotalMagic();
    }

    @Test
    void bothPassivesReachLocalMagic() {
        int bare = totalMagic(0, 0);

        assertAll(
                () -> assertEquals(bare + 20, totalMagic(20, 0), "Dragon Soul 20 is mad 20"),
                () -> assertEquals(bare + 1, totalMagic(1, 0), "and mad 1 at level 1"),
                () -> assertEquals(bare + 15, totalMagic(0, 30), "Magic Mastery 30 is x 15"),
                () -> assertEquals(bare + 35, totalMagic(20, 30), "they stack")
        );
    }
}
