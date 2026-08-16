package server;

import org.junit.jupiter.api.Test;
import provider.wz.WZFiles;
import provider.wz.XMLWZFile;
import server.quest.Quest;
import server.quest.QuestActionType;
import server.quest.actions.SpAction;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ticket 33's verification gate: {@code Quest.hasScriptRequirement(22100)} is true on a
 * <strong>real</strong> {@link Quest} load — the static {@link WZFiles} provider, not a hand-built
 * {@link XMLWZFile} and not a stubbed {@code qm}.
 *
 * <pre>
 *   mvnw.cmd test -Dtest=V84EvanQuestRealLoad
 * </pre>
 *
 * <p><strong>Why this is not a {@code *Test} class, and so does not run in the default suite.</strong>
 * {@link WZFiles#DIRECTORY} is a {@code static final} resolved once per JVM, surefire runs the whole
 * suite in one fork, and {@code MobSkillFactoryTest} points {@code wz-path} at a {@code @TempDir}
 * holding nothing but {@code Skill.wz/MobSkill.img.xml}. Whichever class touches {@code WZFiles}
 * first wins for every other class, and in the full suite {@code MobSkillFactoryTest} wins — so
 * {@link Quest}'s static provider resolves to a tree with no {@code Quest.wz} in it and this gate
 * fails for a reason that has nothing to do with the quest data. The ten sibling
 * {@code V84*NodeTest} classes all avoid {@code DataProviderFactory} for exactly this reason; this
 * one cannot, because the thing under test IS that static. Dropping out of the default includes is
 * the whole fix: no pom change, no fork configuration, no ordering assumption, and the gate is one
 * command away instead of hidden behind an {@code assumeTrue} that would report green by skipping.
 *
 * <p>Before ticket 33 merged the Evan ids, {@code Check.img} had no {@code 22100} node at all, so
 * {@code Quest}'s constructor returned at {@code Quest.java:126} with empty requirement maps,
 * {@code hasScriptRequirement} was false, and {@code QuestScriptManager.start} disposed at
 * {@code :71} — which is why every Evan quest script in {@code scripts/quest/} was a dead file.
 */
class V84EvanQuestRealLoad {

    private static final int[] ADVANCEMENTS = {22100, 22101, 22102, 22103, 22104, 22105, 22106, 22107, 22108, 22109};

    @Test
    void evanAdvancementQuestsLoadAndCarryTheirScriptRequirement() {
        // A JVM-wide wz-path redirect would surface below as "22100 has no script requirement",
        // which is indistinguishable from the merge having failed. Say which it is.
        assertTrue(Files.isRegularFile(Path.of(WZFiles.DIRECTORY, "Quest.wz", "QuestInfo.img.xml")),
                "wz-path resolved to '" + WZFiles.DIRECTORY + "', which holds no Quest.wz - another "
                        + "test class won the WZFiles.DIRECTORY race, so this says nothing about the merge");

        for (int id : ADVANCEMENTS) {
            Quest quest = Quest.getInstance(id);
            assertNotNull(quest, "Quest.getInstance(" + id + ")");
            assertTrue(quest.hasScriptRequirement(false),
                    "Quest " + id + " should carry a start script requirement");
            assertFalse(quest.getName().isBlank(), "Quest " + id + " should have a name from QuestInfo.img");
        }

        // The name comes from QuestInfo.img, a different image than the one that made
        // hasScriptRequirement true, so this also proves both images resolved through the static.
        assertEquals("Dragon Master 1st Job Advancement", Quest.getInstance(22100).getName());
        assertEquals("Dragon Master 10th Job Advancement", Quest.getInstance(22109).getName());
    }

    /**
     * The negative control for the class above it. {@code 22110} is not a quest v84 ships, so it
     * must load as an empty {@link Quest} with no script requirement. Without this, "every id I
     * asked about said true" would also be the result of a {@code hasScriptRequirement} that
     * always says true.
     */
    /**
     * Ticket 34 added {@code SpAction} and unit-tested it against hand-written XML, but nothing ever
     * proved {@link Quest}'s own {@code getAction} switch wires {@code sp} up on a real load - and a
     * quest whose {@code completeActs} lacks the entry pays nothing however correct the action is.
     * 22500 is the first of the 28 {@code sp}-carrying Evan quests.
     *
     * <p>{@code completeActs} is private with no accessor; adding one to production for a test is
     * not worth it, hence the reflection.
     */
    @SuppressWarnings("unchecked")
    @Test
    void anSpRewardQuestLoadsAnSpActionIntoItsCompleteActs() throws Exception {
        Field completeActs = Quest.class.getDeclaredField("completeActs");
        completeActs.setAccessible(true);

        Map<QuestActionType, ?> acts = (Map<QuestActionType, ?>) completeActs.get(Quest.getInstance(22500));
        assertTrue(acts.containsKey(QuestActionType.SP),
                "Act.img/22500/1/sp did not become a SP action - " + acts.keySet());
        assertInstanceOf(SpAction.class, acts.get(QuestActionType.SP));
    }

    /**
     * The gate the whole Evan chain hangs off. 22100 is not reachable from a fresh character: it
     * wants job 2001, level 10, and quest 22007 <em>completed</em>. If 22007 is not in the merged
     * data with a way to finish it, every advancement above it is dead no matter how good the
     * scripts are. This asserts the link, not the whole chain.
     */
    @Test
    void the1stAdvancementsPrerequisiteQuestExistsAndIsFinishable() {
        Quest first = Quest.getInstance(22100);
        assertEquals(1013000, first.getNpcRequirement(false), "22100 is Mir's");

        Quest prereq = Quest.getInstance(22007);
        assertFalse(prereq.getName().isBlank(), "22100 requires 22007 completed, but 22007 did not load");
        assertTrue(prereq.hasScriptRequirement(true),
                "22007 has no endscript, so nothing can complete it and 22100 stays unreachable");
        assertTrue(Files.isRegularFile(Path.of("scripts", "quest", "22007.js")),
                "Check.img declares endscript q22007e but scripts/quest/22007.js is absent");
    }

    @Test
    void anIdV84DoesNotShipHasNoScriptRequirement() {
        Quest absent = Quest.getInstance(22110);
        assertNotNull(absent);
        assertFalse(absent.hasScriptRequirement(false),
                "22110 is not a v84 quest id; it must not report a script requirement");
    }
}
