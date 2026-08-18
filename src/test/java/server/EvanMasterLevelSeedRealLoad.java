package server;

import constants.skills.Evan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import provider.Data;
import provider.DataProvider;
import provider.DataTool;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static server.V84Wz.wz;

/**
 * Parity guard for the Evan skills whose SP cap comes from a {@code masterLevel} node in
 * {@code Skill.wz/22*.img}. {@code Character.setMasteries} must seed each of these at exactly its
 * wz masterLevel on job advance, or the master level stays 0 and the player can never spend SP.
 *
 * <p>The reported bug was Magic Guard (22111001, job 2211, masterLevel 5) sitting at master level 0
 * because {@code setMasteries} had no 2211 branch. This test pins the wz masterLevel of every Evan
 * skill that carries the node, so the hardcoded seed values in {@code setMasteries} (5 for the
 * 2211/2214 branches, 10 for the pre-existing 2217/2218 ones) can never silently drift from the
 * data. Each value is non-zero — a masterLevel node that read 0 would itself be the bug.
 *
 * <p>Reads the real XML tree through {@link V84Wz#wz} for the reason documented there
 * ({@code WZFiles.DIRECTORY} is resolved once per JVM). Not a {@code *Test} class on purpose.
 */
class EvanMasterLevelSeedRealLoad {

    /** skillid -> the masterLevel Skill.wz carries for it, and the value setMasteries must seed. */
    private static final Map<Integer, Integer> EXPECTED_MASTER_LEVEL = new LinkedHashMap<>();

    static {
        // Missed before this fix — seeded by the new setMasteries 2211/2214 branches.
        EXPECTED_MASTER_LEVEL.put(Evan.MAGIC_GUARD, 5);      // 22111001, job 2211
        EXPECTED_MASTER_LEVEL.put(Evan.CRITICAL_MAGIC, 5);   // 22140000, job 2214
        EXPECTED_MASTER_LEVEL.put(Evan.MAGIC_BOOSTER, 5);    // 22141002, job 2214
        // Already seeded (via the min(10,max) loop, which equals 10 here) — pinned for regression.
        EXPECTED_MASTER_LEVEL.put(Evan.MAPLE_WARRIOR, 10);       // 22171000, job 2217
        EXPECTED_MASTER_LEVEL.put(Evan.ILLUSION, 10);            // 22171002, job 2217
        EXPECTED_MASTER_LEVEL.put(Evan.BLESSING_OF_THE_ONYX, 10);// 22181000, job 2218
        EXPECTED_MASTER_LEVEL.put(Evan.BLAZE, 10);               // 22181001, job 2218
    }

    private static int wzMasterLevel(DataProvider skills, int skillId) {
        String img = (skillId / 10000) + ".img"; // 22111001 -> 2211.img
        Data skillNode = skills.getData(img).getChildByPath("skill/" + skillId);
        assertNotNull(skillNode, img + "/skill/" + skillId);
        Data ml = skillNode.getChildByPath("masterLevel");
        assertNotNull(ml, skillId + " has no masterLevel node");
        return DataTool.getInt(ml);
    }

    @Test
    void everySeededEvanSkillMatchesItsWzMasterLevel() {
        DataProvider skills = wz("Skill.wz");
        assertAll(EXPECTED_MASTER_LEVEL.entrySet().stream().map(e -> (Executable) () -> {
            int actual = wzMasterLevel(skills, e.getKey());
            assertTrue(actual > 0, e.getKey() + " wz masterLevel must be non-zero");
            assertEquals(e.getValue().intValue(), actual,
                    e.getKey() + " wz masterLevel must equal the setMasteries seed value");
        }));
    }
}
