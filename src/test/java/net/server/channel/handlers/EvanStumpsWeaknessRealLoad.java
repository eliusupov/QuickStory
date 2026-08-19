package net.server.channel.handlers;

import client.Character;
import client.Client;
import client.Skill;
import client.SkillFactory;
import constants.skills.Evan;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import server.life.Monster;
import server.life.MonsterStats;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Pins Stump's Weakness to its v84 WZ mobCode and damage multiplier. */
class EvanStumpsWeaknessRealLoad {
    @BeforeAll
    static void loadSkills() {
        SkillFactory.loadAllSkills();
    }

    @Test
    void weaknessUsesTheWzStumpCodeAndDamageMultiplier() {
        Character evan = Character.getDefault(Mockito.mock(Client.class));
        Skill weakness = SkillFactory.getSkill(Evan.STUMPS_WEAKNESS);
        evan.changeSkillLevel(weakness, (byte) 1, 1, -1);

        assertAll(
                () -> assertEquals(130100, weakness.getMobCode()),
                () -> assertEquals(150, weakness.getEffect(1).getDamage()),
                () -> assertEquals(1.5, AbstractDealDamageHandler.evanStumpsWeaknessDamageCeilingMultiplier(evan, mob(130100))),
                () -> assertEquals(1.0, AbstractDealDamageHandler.evanStumpsWeaknessDamageCeilingMultiplier(evan, mob(130101)))
        );
    }

    private static Monster mob(int id) {
        return new Monster(id, new MonsterStats());
    }
}
