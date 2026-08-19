package client;

import constants.skills.Evan;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import server.StatEffect;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Soul Stone's selected, living recipients revive once only when they later die. */
class EvanSoulStoneRealLoad {

    @BeforeAll
    static void loadSkills() {
        SkillFactory.loadAllSkills();
    }

    private static Character character() {
        return Character.getDefault(Mockito.mock(Client.class));
    }

    private static StatEffect soulStone() {
        return SkillFactory.getSkill(Evan.SOUL_STONE).getEffect(20);
    }

    @Test
    void twoProtectedMembersReviveOnceAtTheWzPercentage() {
        StatEffect effect = soulStone();
        Character first = character();
        Character second = character();
        long start = 1_000L;

        first.protectFromSoulStone(effect, start);
        second.protectFromSoulStone(effect, start);
        first.updateHp(0);
        second.updateHp(0);

        assertAll(
                () -> assertTrue(first.reviveFromSoulStone(start + 1), "first later death is revived"),
                () -> assertEquals(first.getCurrentMaxHp() * effect.getX() / 100, first.getHp(), "x percent HP"),
                () -> assertTrue(second.reviveFromSoulStone(start + 1), "second later death is revived"),
                () -> assertEquals(second.getCurrentMaxHp() * effect.getX() / 100, second.getHp(), "x percent HP"),
                () -> assertFalse(first.reviveFromSoulStone(start + 1), "a consumed Soul Stone cannot revive a third death")
        );
    }

    @Test
    void expiredSoulStoneCannotRevive() {
        StatEffect effect = soulStone();
        Character target = character();
        long start = 1_000L;

        target.protectFromSoulStone(effect, start);
        target.updateHp(0);

        assertAll(
                () -> assertFalse(target.reviveFromSoulStone(start + effect.getDuration()), "expires at WZ time"),
                () -> assertEquals(0, target.getHp(), "expired protection leaves the later death dead")
        );
    }
}
