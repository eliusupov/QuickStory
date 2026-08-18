package server;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import provider.wz.WZFiles;
import server.life.MobSkill;
import server.life.MobSkillFactory;
import server.life.MobSkillType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins ticket 59 R09/R12: the 14 mob-skill levels v84 adds to {@code Skill.wz/MobSkill.img}.
 *
 * <pre>
 *   mvnw.cmd -o test -Dtest=MobSkillV84RealLoad
 * </pre>
 *
 * <p><strong>Not a {@code *Test} class on purpose</strong>, same reason as its siblings:
 * {@link WZFiles#DIRECTORY} is a {@code static final} resolved once per JVM.
 *
 * <p><strong>Mob skill 137 is deliberately not here.</strong> v84 adds it as a whole node and the
 * carve has a single {@code level/1}, but {@link MobSkillType} jumps {@code FEAR(136)} straight to
 * {@code PHYSICAL_IMMUNE(140)}, so {@code MobSkillType.from(137)} is empty and
 * {@code MobSkillFactory} is keyed on the enum - the node would be unreachable whether merged or
 * not. Its real referrer is mob 8300003 ("Soaring Blue Wyvern") via
 * {@code attack<N>/info/disease}, whose disease therefore does nothing today. Adding the enum
 * constant and its {@code Disease} mapping is separate work; ticket 59 scopes 137 out and does not
 * merge it.
 */
class MobSkillV84RealLoad {

    /** The 14 levels merged by R12, id -> the new level. */
    private static final List<int[]> V84_ADDED = List.of(
            new int[]{110, 10},
            new int[]{114, 35}, new int[]{114, 36},
            new int[]{115, 2},
            new int[]{123, 24}, new int[]{123, 25}, new int[]{123, 26},
            new int[]{125, 11},
            new int[]{127, 15},
            new int[]{128, 15},
            new int[]{133, 6},
            new int[]{145, 6},
            new int[]{200, 177}, new int[]{200, 178});

    /** The v83 top level of each touched skill: every one of them must still resolve. */
    private static final Map<Integer, Integer> V83_TOP_LEVEL = Map.of(
            110, 9, 114, 34, 115, 1, 123, 23, 125, 10, 127, 14, 128, 14, 133, 5, 145, 5, 200, 176);

    @BeforeAll
    static void muteGraal() {
        System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
        assertTrue(Files.isRegularFile(Path.of(WZFiles.DIRECTORY, "Skill.wz", "MobSkill.img.xml")),
                "wz-path resolved to '" + WZFiles.DIRECTORY + "', which holds no Skill.wz/MobSkill.img "
                        + "- another test class won the WZFiles.DIRECTORY race, so this says nothing "
                        + "about mob skills");
    }

    @Test
    void everyV84LevelResolves() {
        for (int[] pair : V84_ADDED) {
            MobSkillType type = MobSkillType.from(pair[0]).orElseThrow();
            MobSkill skill = MobSkillFactory.getMobSkill(type, pair[1])
                    .orElseThrow(() -> new AssertionError("MobSkill.img has no " + pair[0] + "/level/"
                            + pair[1] + " - the v84 merge is missing or was reverted"));
            assertEquals(type, skill.getType());
        }
    }

    /** Additive merge: the v83 levels must still be there, and one past the new top must not. */
    @Test
    void theV83LevelsSurvivedAndNothingBeyondTheV84TopWasInvented() {
        for (Map.Entry<Integer, Integer> e : V83_TOP_LEVEL.entrySet()) {
            MobSkillType type = MobSkillType.from(e.getKey()).orElseThrow();
            for (int level = 1; level <= e.getValue(); level++) {
                int lv = level;
                MobSkillFactory.getMobSkill(type, lv).orElseThrow(
                        () -> new AssertionError("v83 level " + e.getKey() + "/" + lv + " no longer resolves"));
            }
        }

        // one past the merged top of each skill must still be absent
        for (int[] pair : List.of(new int[]{110, 11}, new int[]{114, 37}, new int[]{115, 3},
                new int[]{123, 27}, new int[]{125, 12}, new int[]{127, 16}, new int[]{128, 16},
                new int[]{133, 7}, new int[]{145, 7}, new int[]{200, 179})) {
            assertTrue(MobSkillFactory.getMobSkill(MobSkillType.from(pair[0]).orElseThrow(), pair[1]).isEmpty(),
                    "MobSkill.img grew a level " + pair[0] + "/" + pair[1] + " that v84 does not have");
        }
    }

    /** The scoped-out node, asserted so the day someone adds the enum constant, this tells them. */
    @Test
    void mobSkill137IsStillUnreachable() {
        assertTrue(MobSkillType.from(137).isEmpty(),
                "MobSkillType now has a 137 constant - merge Skill.wz/MobSkill.img/137 and wire its "
                        + "Disease mapping, then delete this test");
    }
}
