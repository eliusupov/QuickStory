package constants.game;

import client.Job;
import constants.id.MapId;
import constants.skills.Evan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameConstantsEvanTest {

    /**
     * getHallOfFameMapid asks isCygnus, then isAran, then the Explorer {@code isA} chain. Evan
     * answers no to all three - {@code EVAN1.isA(MAGICIAN)} is false on the id/100 rule - so a
     * level-200 Evan was deployed as a PlayerNPC into KNIGHTS_CHAMBER_2, the Cygnus hall, by the
     * trailing "beginner explorers get the Cygnus map" fallback. PLAYERNPC_AUTODEPLOY is on.
     */
    @Test
    void evanGetsAHallOfFameMapThatIsNotCygnus() {
        assertAll(
                () -> assertEquals(MapId.HALL_OF_MAGICIANS, GameConstants.getHallOfFameMapid(Job.EVAN1), "2200"),
                () -> assertEquals(MapId.HALL_OF_MAGICIANS, GameConstants.getHallOfFameMapid(Job.EVAN10), "2218"),

                // the neighbours it must not be confused with
                () -> assertEquals(MapId.HALL_OF_MAGICIANS, GameConstants.getHallOfFameMapid(Job.BISHOP), "232"),
                () -> assertEquals(MapId.KNIGHTS_CHAMBER, GameConstants.getHallOfFameMapid(Job.BLAZEWIZARD4), "1212"),
                () -> assertEquals(MapId.PALACE_OF_THE_MASTER, GameConstants.getHallOfFameMapid(Job.ARAN4), "2112")
        );
    }

    /**
     * isPqSkill gates key binding via bannedBindSkills. Its {@code skill % 10000000} terms leave
     * 11009-11011 for Evan, whose beginner block is 2001, so Evan's dojo secret skills were the only
     * ones bindable.
     */
    @Test
    void evanDojoSecretSkillsCountAsPqSkills() {
        assertAll(
                () -> assertTrue(GameConstants.isPqSkill(Evan.BAMBOO_THRUST), "20011009"),
                () -> assertTrue(GameConstants.isPqSkill(Evan.INVINCIBLE_BARRIER), "20011010"),
                () -> assertTrue(GameConstants.isPqSkill(Evan.POWER_EXPLOSION), "20011011"),

                // the Explorer and Legend rows this was derived from
                () -> assertTrue(GameConstants.isPqSkill(1009), "explorer bamboo"),
                () -> assertTrue(GameConstants.isPqSkill(20001011), "legend power explosion")
        );
    }
}
