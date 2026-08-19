package net.server.channel.handlers;

import client.Character;
import client.Client;
import client.Job;
import client.SkillFactory;
import constants.skills.Evan;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import provider.Data;
import provider.DataTool;
import provider.wz.XMLWZFile;
import server.StatEffect;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Pins Critical Magic's client-owned chance/damage data to the server's reported-hit ceiling. */
class EvanCriticalMagicRealLoad {

    @BeforeAll
    static void loadSkills() {
        SkillFactory.loadAllSkills();
    }

    private static Character evan(int level) {
        Character chr = Character.getDefault(Mockito.mock(Client.class));
        chr.setJob(Job.EVAN10);
        chr.changeSkillLevel(SkillFactory.getSkill(Evan.CRITICAL_MAGIC), (byte) level, 5, -1);
        return chr;
    }

    private static int wzValue(int level, String property) {
        Data node = new XMLWZFile(Path.of("wz", "Skill.wz"))
                .getData("2214.img").getChildByPath("skill/22140000/level/" + level + "/" + property);
        return DataTool.getInt(node);
    }

    @Test
    void usesEveryWzCriticalDamageValueAsTheServerCeilingWithoutRollingAnotherCrit() {
        StatEffect levelOne = SkillFactory.getSkill(Evan.CRITICAL_MAGIC).getEffect(1);
        StatEffect levelFive = SkillFactory.getSkill(Evan.CRITICAL_MAGIC).getEffect(5);
        StatEffect levelFifteen = SkillFactory.getSkill(Evan.CRITICAL_MAGIC).getEffect(15);

        assertAll(
                () -> assertEquals(108, levelOne.getDamage()),
                () -> assertEquals(120, levelFive.getDamage()),
                () -> assertEquals(150, levelFifteen.getDamage()),
                () -> assertEquals(16, wzValue(1, "prop")),
                () -> assertEquals(20, wzValue(5, "prop")),
                () -> assertEquals(30, wzValue(15, "prop")),
                () -> assertEquals(1.08, AbstractDealDamageHandler.criticalDamageCeilingMultiplier(evan(1))),
                () -> assertEquals(1.20, AbstractDealDamageHandler.criticalDamageCeilingMultiplier(evan(5))),
                () -> assertEquals(1.50, AbstractDealDamageHandler.criticalDamageCeilingMultiplier(evan(15)))
        );
    }
}
