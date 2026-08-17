package scripting;

import client.Job;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import scripting.quest.QuestActionManager;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The 22100-22109 scripts are the only thing in the tree that performs an Evan job advancement -
 * v84's Act.img for those quests is empty and there is no endscript, so nothing data-driven does
 * it. This drives each script's state machine against a stub {@code qm} and asserts the chain, the
 * level gate and the job gate. ScriptEvaluationTest only proves the files parse.
 */
public class EvanJobAdvancementScriptTest {
    private final AbstractScriptManager scriptManager = new AbstractScriptManager() {
    };

    @BeforeAll
    static void muteGraal() {
        System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
    }

    /**
     * Stub of the QuestActionManager surface the advancement scripts touch. Public methods only -
     * Graal reaches them through the host access the script manager enables.
     */
    public static class StubQm {
        private final Job job;
        private final int level;
        public Integer changedTo;
        public boolean completed;
        public String lastText;
        public int resetStatsCalls;

        StubQm(int jobId, int level) {
            this.job = Job.getById(jobId);
            this.level = level;
        }

        public Job getJob() {
            return job;
        }

        public int getLevel() {
            return level;
        }

        public void changeJobById(int id) {
            changedTo = id;
        }

        public boolean forceStartQuest() {
            return true;
        }

        public boolean forceCompleteQuest() {
            completed = true;
            return true;
        }

        public void sendNext(String text) {
            lastText = text;
        }

        public void sendNextPrev(String text) {
            lastText = text;
        }

        public void sendOk(String text) {
            lastText = text;
        }

        public void resetStats() {
            resetStatsCalls++;
        }

        public void dispose() {
        }
    }

    private StubQm run(int questId, int jobId, int level, int screens) throws Exception {
        ScriptEngine engine = scriptManager.getInvocableScriptEngine("quest/" + questId + ".js");
        assertNotNull(engine, "quest/" + questId + ".js did not evaluate");
        StubQm qm = new StubQm(jobId, level);
        engine.put("qm", qm);
        for (int i = 0; i < screens; i++) {
            ((Invocable) engine).invokeFunction("start", (byte) 1, (byte) 0, 0);
        }
        return qm;
    }

    @ParameterizedTest(name = "{0}: {1} -> {2} at level {3}")
    @CsvSource({
            "22100, 2001, 2200, 10",
            "22101, 2200, 2210, 20",
            "22102, 2210, 2211, 30",
            "22103, 2211, 2212, 40",
            "22104, 2212, 2213, 50",
            "22105, 2213, 2214, 60",
            "22106, 2214, 2215, 80",
            "22107, 2215, 2216, 100",
            "22108, 2216, 2217, 120",
            "22109, 2217, 2218, 160",
    })
    void advancesOnTheSecondScreen(int questId, int fromJob, int toJob, int minLevel) throws Exception {
        StubQm qm = run(questId, fromJob, minLevel, 2);

        assertEquals(toJob, qm.changedTo);
        assertTrue(qm.completed);
        // Only 22100 leaves the beginner spread behind, so only 22100 resets stats. 22101-22109
        // advance an already-built Evan; resetting there would wipe a real stat allocation.
        assertEquals(questId == 22100 ? 1 : 0, qm.resetStatsCalls,
                "quest/" + questId + ".js resetStats() calls");
    }

    @ParameterizedTest(name = "{0} refuses below level {2}")
    @CsvSource({
            "22100, 2001, 10",
            "22105, 2213, 60",
            "22109, 2217, 160",
    })
    void refusesUnderLevel(int questId, int fromJob, int minLevel) throws Exception {
        StubQm qm = run(questId, fromJob, minLevel - 1, 2);

        assertNull(qm.changedTo);
    }

    @ParameterizedTest(name = "{0} refuses the wrong job")
    @CsvSource({
            "22100, 2200, 200",     // already advanced past it
            "22105, 2001, 200",     // still a beginner Evan
            "22109, 2100, 200",     // an Aran, at a level that clears the gate
    })
    void refusesWrongJob(int questId, int jobId, int level) throws Exception {
        StubQm qm = run(questId, jobId, level, 2);

        assertNull(qm.changedTo);
    }

    /**
     * The tests above drive a stub, so on their own they would still pass if the real qm lost a
     * method - the scripts would then fail at runtime with nothing here going red. This pins the
     * names the Evan scripts call to the class the server actually binds as {@code qm}.
     */
    @ParameterizedTest(name = "QuestActionManager exposes {0}")
    @CsvSource({
            "getJob", "getLevel", "changeJobById", "forceStartQuest", "forceCompleteQuest",
            "sendNext", "sendOk", "canHold", "gainItem", "dispose", "resetStats",
    })
    void questActionManagerExposesTheScriptSurface(String method) {
        boolean found = Stream.of(QuestActionManager.class.getMethods())
                .anyMatch(m -> m.getName().equals(method));

        assertTrue(found, "scripts/quest/22*.js call qm." + method + "(), which no longer exists");
    }
}
