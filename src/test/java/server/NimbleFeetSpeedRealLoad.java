package server;

import client.BuffStat;
import client.SkillFactory;
import config.YamlConfig;
import constants.skills.Beginner;
import constants.skills.Evan;
import constants.skills.Legend;
import constants.skills.Noblesse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks the actual skill loader and SPEED buff sent to the client for all beginner families. */
class NimbleFeetSpeedRealLoad {
    @Test
    void allBeginnerFamiliesGetTenSpeedPerLevelWithoutChangingOtherEffects() {
        assertTrue(YamlConfig.config.server.USE_ULTRA_NIMBLE_FEET);
        SkillFactory.loadAllSkills();
        int[] expectedMp = {4, 7, 10};
        for (int skillId : new int[]{Beginner.NIMBLE_FEET, Noblesse.NIMBLE_FEET,
                Legend.AGILE_BODY, Evan.NIMBLE_FEET}) {
            for (int level = 1; level <= 3; level++) {
                StatEffect effect = SkillFactory.getSkill(skillId).getEffect(level);
                int speed = effect.getStatups().stream()
                        .filter(stat -> stat.getLeft() == BuffStat.SPEED)
                        .findFirst().orElseThrow().getRight();
                assertEquals(10 * level, speed, skillId + " level " + level);
                assertEquals(expectedMp[level - 1], effect.getMpCon());
                assertEquals(340000, effect.getDuration());
                assertEquals(0, effect.getCooldown());
                assertEquals(1, effect.getStatups().size(), "Only SPEED is buffed");
            }
        }
    }
}
