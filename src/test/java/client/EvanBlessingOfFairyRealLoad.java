package client;

import constants.skills.Evan;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Blessing's WZ x/y bonuses must enter server-side damage validation. */
class EvanBlessingOfFairyRealLoad {

    @BeforeAll
    static void loadSkills() {
        SkillFactory.loadAllSkills();
    }

    private static int[] totals(int blessingLevel) {
        Character chr = Character.getDefault(Mockito.mock(Client.class));
        chr.setJob(Job.EVAN10);
        if (blessingLevel > 0) {
            chr.changeSkillLevel(SkillFactory.getSkill(Evan.BLESSING_OF_THE_FAIRY), (byte) blessingLevel, 20, -1);
        }
        chr.updateStrDexIntLuk(4, 4, 100, 4, 0);
        return new int[]{chr.getTotalWatk(), chr.getTotalMagic()};
    }

    @Test
    void blessingUsesItsRealWzWeaponAndMagicAttackValues() {
        int[] bare = totals(0);

        assertAll(
                () -> assertEquals(bare[0] + 1, totals(1)[0], "level 1 x = weapon attack 1"),
                () -> assertEquals(bare[1] + 2, totals(1)[1], "level 1 y = magic attack 2"),
                () -> assertEquals(bare[0] + 20, totals(20)[0], "level 20 x = weapon attack 20"),
                () -> assertEquals(bare[1] + 40, totals(20)[1], "level 20 y = magic attack 40")
        );
    }
}
