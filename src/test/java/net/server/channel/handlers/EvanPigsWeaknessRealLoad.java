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

/** Pins Pig's Weakness to its v84 WZ mobCode, including WZ-linked Pig visual variants. */
class EvanPigsWeaknessRealLoad {
    @BeforeAll
    static void loadSkills() {
        SkillFactory.loadAllSkills();
    }

    @Test
    void weaknessUsesTheWzPigFamilyAndItsWzDamageMultiplier() {
        Character evan = Character.getDefault(Mockito.mock(Client.class));
        Skill weakness = SkillFactory.getSkill(Evan.PIGS_WEAKNESS);
        evan.changeSkillLevel(weakness, (byte) 1, 1, -1);

        assertAll(
                () -> assertEquals(1210100, weakness.getMobCode()),
                () -> assertEquals(150, weakness.getEffect(1).getDamage()),
                () -> assertEquals(1.5, AbstractDealDamageHandler.evanPigsWeaknessDamageCeilingMultiplier(evan, mob(1210100))),
                () -> assertEquals(1.5, AbstractDealDamageHandler.evanPigsWeaknessDamageCeilingMultiplier(evan, mob(9300273))),
                () -> assertEquals(1.5, AbstractDealDamageHandler.evanPigsWeaknessDamageCeilingMultiplier(evan, mob(9300343))),
                () -> assertEquals(1.5, AbstractDealDamageHandler.evanPigsWeaknessDamageCeilingMultiplier(evan, mob(9500101))),
                () -> assertEquals(1.0, AbstractDealDamageHandler.evanPigsWeaknessDamageCeilingMultiplier(evan, mob(1210101)))
        );
    }

    private static Monster mob(int id) {
        return new Monster(id, new MonsterStats());
    }
}
