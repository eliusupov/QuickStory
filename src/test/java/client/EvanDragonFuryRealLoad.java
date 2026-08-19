package client;

import constants.skills.Evan;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Dragon Fury is active strictly between its WZ x and y MP percentages. */
class EvanDragonFuryRealLoad {

    @BeforeAll
    static void loadSkills() {
        SkillFactory.loadAllSkills();
    }

    private static int totalMagicAt(int mp) {
        Character chr = Character.getDefault(Mockito.mock(Client.class));
        chr.setJob(Job.EVAN8);
        chr.changeSkillLevel(SkillFactory.getSkill(Evan.DRAGON_FURY), (byte) 10, 10, -1);
        chr.updateMaxMp(100);
        chr.updateMp(mp);
        chr.updateStrDexIntLuk(4, 4, 100, 4, 0);
        return chr.getTotalMagic();
    }

    @Test
    void levelTenRaisesMagicOnlyInsideItsWzMpWindow() {
        int bare = totalMagicAt(50);

        assertAll(
                () -> assertEquals(bare, totalMagicAt(50), "x = 50 is outside: over, not at, 50%"),
                () -> assertEquals(bare * 110 / 100, totalMagicAt(51), "inside x = 50, y = 80"),
                () -> assertEquals(bare * 110 / 100, totalMagicAt(79), "still below y = 80"),
                () -> assertEquals(bare, totalMagicAt(80), "y = 80 is outside: under, not at, 80%")
        );
    }
}
