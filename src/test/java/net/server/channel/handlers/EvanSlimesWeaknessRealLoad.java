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

/** Pins Slime's Weakness to its v84 WZ mobCode, including WZ-linked Slime visual variants. */
class EvanSlimesWeaknessRealLoad {
    @BeforeAll
    static void loadSkills() {
        SkillFactory.loadAllSkills();
    }

    @Test
    void weaknessUsesTheWzSlimeFamilyAndItsWzDamageMultiplier() {
        Character evan = Character.getDefault(Mockito.mock(Client.class));
        Skill weakness = SkillFactory.getSkill(Evan.SLIMES_WEAKNESS);
        evan.changeSkillLevel(weakness, (byte) 1, 1, -1);

        assertAll(
                () -> assertEquals(210100, weakness.getMobCode()),
                () -> assertEquals(150, weakness.getEffect(1).getDamage()),
                () -> assertEquals(1.5, AbstractDealDamageHandler.evanSlimesWeaknessDamageCeilingMultiplier(evan, mob(210100))),
                () -> assertEquals(1.5, AbstractDealDamageHandler.evanSlimesWeaknessDamageCeilingMultiplier(evan, mob(9300223))),
                () -> assertEquals(1.5, AbstractDealDamageHandler.evanSlimesWeaknessDamageCeilingMultiplier(evan, mob(9100000))),
                () -> assertEquals(1.5, AbstractDealDamageHandler.evanSlimesWeaknessDamageCeilingMultiplier(evan, mob(9500100))),
                () -> assertEquals(1.5, AbstractDealDamageHandler.evanSlimesWeaknessDamageCeilingMultiplier(evan, mob(9200005))),
                () -> assertEquals(1.5, AbstractDealDamageHandler.evanSlimesWeaknessDamageCeilingMultiplier(evan, mob(9300341))),
                () -> assertEquals(1.5, AbstractDealDamageHandler.evanSlimesWeaknessDamageCeilingMultiplier(evan, mob(9300271))),
                () -> assertEquals(1.0, AbstractDealDamageHandler.evanSlimesWeaknessDamageCeilingMultiplier(evan, mob(210101)))
        );
    }

    private static Monster mob(int id) {
        return new Monster(id, new MonsterStats());
    }
}
