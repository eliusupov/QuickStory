package client;

import constants.skills.Evan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@link Skill#isFourthJob()} - the single flag that decides whether the char-entry skill
 * record carries the extra masterLevel int ({@code PacketCreator.addSkillInfo}) and whether SP is
 * capped at masterLevel instead of maxLevel ({@code AssignSPProcessor}).
 *
 * <p>The v84 client crashed evan2 on map entry because {@code isFourthJob()} returned false for
 * Magic Guard (22111001): the client's {@code SkillConstants.IsSkillNeedMasterLevel} (Edelstein
 * v95 reference) reads a masterLevel int for it, the server wrote none, and the char-data packet
 * desynced. The client rule for Evan is: Magic Guard / Critical Magic / Magic Booster always carry
 * the node, plus every skill of the last two growths (jobs 2217 and 2218). The server MUST agree,
 * so a masterLevel-seeded level-0 skill (skilllevel 0, masterlevel 5) serializes with its int
 * present and shows a usable cap.
 */
class SkillMasterLevelFieldTest {

    private static boolean needsMasterLevel(int id) {
        return new Skill(id).isFourthJob();
    }

    @Test
    void evanMasterLevelSkillsCarryTheField() {
        // The three that crashed / would crash: seeded at masterLevel below their top growth.
        assertTrue(needsMasterLevel(Evan.MAGIC_GUARD), "22111001 Magic Guard - the evan2 crash");
        assertTrue(needsMasterLevel(Evan.CRITICAL_MAGIC), "22140000 Critical Magic");
        assertTrue(needsMasterLevel(Evan.MAGIC_BOOSTER), "22141002 Magic Booster");
        // Every skill of the last two growths carries the node (jobLevel 9 and 10).
        assertTrue(needsMasterLevel(Evan.MAPLE_WARRIOR), "22171000 - job 2217");
        assertTrue(needsMasterLevel(Evan.ILLUSION), "22171002 - job 2217");
        assertTrue(needsMasterLevel(Evan.MAGIC_MASTERY), "22170001 - job 2217");
        assertTrue(needsMasterLevel(Evan.HEROS_WILL), "22171004 - job 2217");
        assertTrue(needsMasterLevel(Evan.BLESSING_OF_THE_ONYX), "22181000 - job 2218");
        assertTrue(needsMasterLevel(Evan.BLAZE), "22181001 - job 2218");
    }

    @Test
    void evanSkillsWithoutTheNodeDoNotCarryTheField() {
        // Lower-growth Evan skills without a masterLevel node - must NOT write the int, or the
        // packet desyncs the other way. 22111000 is Magic Guard's growth sibling; 22101001 a
        // growth-2 attack; 20010012 an Evan beginner skill (job race 20, not 22).
        assertFalse(needsMasterLevel(22111000), "22111000 - no masterLevel node");
        assertFalse(needsMasterLevel(22101001), "22101001 - no masterLevel node");
        assertFalse(needsMasterLevel(20010012), "20010012 - EvanJr beginner skill");
    }

    @Test
    void nonEvanRuleUnchanged() {
        assertTrue(needsMasterLevel(1121000), "Hero 4th-job skill - job%10==2");
        assertFalse(needsMasterLevel(1111002), "Hero 3rd-job skill");
    }
}
