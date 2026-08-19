package net.server.channel.handlers;

import client.BuffStat;
import client.Skill;
import client.SkillFactory;
import constants.skills.Evan;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import server.StatEffect;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins Elemental Reset's v84 WZ values to the client damage formula's server-side ceiling. */
class EvanElementalResetRealLoad {

    @BeforeAll
    static void loadSkills() {
        SkillFactory.loadAllSkills();
    }

    @Test
    void everyWzResetLevelScalesAnElementalWeaknessTowardNeutral() {
        Skill reset = SkillFactory.getSkill(Evan.ELEMENTAL_RESET);
        StatEffect levelOne = reset.getEffect(1);
        StatEffect levelTen = reset.getEffect(10);
        StatEffect levelTwenty = reset.getEffect(20);

        assertAll(
                () -> assertEquals(5, levelOne.getX()),
                () -> assertEquals(110_000, levelOne.getDuration()),
                () -> assertEquals(50, levelTen.getX()),
                () -> assertEquals(100, levelTwenty.getX()),
                () -> assertEquals(300_000, levelTwenty.getDuration()),
                () -> assertTrue(levelTwenty.getStatups().stream().anyMatch(s -> s.getLeft() == BuffStat.ELEMENTAL_RESET && s.getRight() == 100)),
                () -> assertEquals(1.5, AbstractDealDamageHandler.elementalWeaknessDamageCeilingMultiplier(null)),
                () -> assertEquals(1.475, AbstractDealDamageHandler.elementalWeaknessDamageCeilingMultiplier(levelOne.getX())),
                () -> assertEquals(1.25, AbstractDealDamageHandler.elementalWeaknessDamageCeilingMultiplier(levelTen.getX())),
                () -> assertEquals(1.0, AbstractDealDamageHandler.elementalWeaknessDamageCeilingMultiplier(levelTwenty.getX()))
        );
    }
}
