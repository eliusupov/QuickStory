package server;

import client.QuestStatus;
import client.QuestStatus.Status;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import provider.Data;
import provider.DataProviderFactory;
import provider.DataTool;
import provider.wz.WZFiles;
import server.quest.Quest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Evan quest-record gate. {@code Quest.canStart} and {@code Quest.canComplete} BOTH end in
 * {@code canQuestByInfoProgress}, which string-compares the record named by
 * {@code Check.img/<quest>/<n>/infoNumber} against that node's {@code infoex} value. Nine Evan
 * quest states gate on records 22597-22605, and nothing on this server ever wrote one of them, so
 * those states were unreachable regardless of {@code autoStart}/{@code autoComplete} -
 * {@code QuestActionHandler} cases 1/2/4/5 all route through the same two methods.
 *
 * <pre>
 *   mvnw.cmd test -Dtest=EvanQuestRecordGatesRealLoad
 * </pre>
 *
 * <p><strong>Not a {@code *Test} class on purpose</strong>, same reason as its siblings:
 * {@link WZFiles#DIRECTORY} is {@code static final} and resolved once per JVM, and
 * {@code MobSkillFactoryTest} points {@code wz-path} at a {@code @TempDir} holding no
 * {@code Quest.wz}.
 *
 * <p>This class pins the nine gates as data, pins the fact that the WZ only ever READS these
 * records, and asserts the one writer the v84 data actually settles - quest 22530's. The other six
 * quests deliberately have no writer asserted here; inventing one would be inventing content.
 */
class EvanQuestRecordGatesRealLoad {

    /**
     * Every gate, straight off {@code Check.img}: quest, the {@code Check.img} child index, the
     * record it reads, and the string it demands. Index 0 is the START requirement block and index 1
     * the COMPLETE block, which is why 22580 and 22589 appear twice.
     */
    private static final Object[][] GATES = {
            {22530, 1, 22597, "5"},
            {22556, 1, 22598, "1"},
            {22557, 1, 22598, "2"},
            {22580, 0, 22599, "1"},
            {22580, 1, 22599, "2"},
            {22588, 1, 22605, "1"},
            {22589, 0, 22600, "1"},
            {22589, 1, 22604, "1"},
            {22591, 1, 22601, "1"},
    };

    /**
     * The record slots themselves. Deliberately an explicit list and NOT the range 22597-22605:
     * 22602 "After Shedding 1" and 22603 "After Shedding 2" are real Evan quests that happen to sit
     * inside the same numeric band, and asserting the range instead of the list fails on them.
     */
    private static final int[] RECORD_SLOTS = {22597, 22598, 22599, 22600, 22601, 22604, 22605};

    /** The five maps QuestInfo 22530 names, in the order it names them. */
    private static final int[] WARNING_SIGN_MAPS =
            {101030000, 101030100, 101030200, 101030300, 101030400};

    private static final Path WARNING_POST_SCRIPT = Path.of("scripts", "npc", "1022107.js");

    @BeforeAll
    static void muteGraal() {
        System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
        assertTrue(Files.isRegularFile(Path.of(WZFiles.DIRECTORY, "Quest.wz", "Check.img.xml")),
                "wz-path resolved to '" + WZFiles.DIRECTORY + "', which holds no Quest.wz - another "
                        + "test class won the WZFiles.DIRECTORY race, so this says nothing about Evan");
    }

    /**
     * The defect, as data. Each row must still read exactly this way; a gate that moves silently
     * invalidates every writer built against it.
     */
    @Test
    void everyEvanRecordGateReadsExactlyWhatItDidWhenTheWritersWereBuilt() {
        for (Object[] gate : GATES) {
            String at = gate[0] + "/" + gate[1];
            assertEquals((int) (Integer) gate[2], DataTool.getInt(questCheck(at + "/infoNumber"), -1),
                    "Check.img/" + at + " no longer reads record " + gate[2]);
            assertEquals(gate[3], DataTool.getString(questCheck(at + "/infoex/0/value"), ""),
                    "Check.img/" + at + " no longer demands \"" + gate[3] + "\"");
        }
    }

    /**
     * <strong>Why a writer had to be built at all.</strong> 22597-22605 are quest ids that exist
     * only as record slots: no {@code Check.img}, no {@code Act.img}, no {@code QuestInfo.img}
     * entry anywhere in v84. The WZ reads them and never writes them, so the write is server-side
     * by construction and no amount of re-pinning the archive will supply it.
     */
    @Test
    void theRecordSlotsAreNotQuestsAndTheWzNeverWritesThem() {
        for (int record : RECORD_SLOTS) {
            assertNull(questCheck(String.valueOf(record)),
                    "record slot " + record + " has grown a Check.img entry - it is a real quest now "
                            + "and the hand-built writer for it needs re-deriving");
            assertNull(questAct(String.valueOf(record)),
                    "record slot " + record + " has grown an Act.img entry, which would mean the WZ "
                            + "writes it after all");
            assertNull(DataProviderFactory.getDataProvider(WZFiles.QUEST)
                            .getData("QuestInfo.img").getChildByPath(String.valueOf(record)),
                    "record slot " + record + " has grown a QuestInfo.img entry");
        }
    }

    /**
     * <strong>The trigger, stated by the data rather than inferred.</strong> The decisive sentence is
     * Mike's, in the PRISTINE v84 archive at {@code Quest.wz/Say.img/22530/0/yes/0}:
     *
     * <blockquote>"Simply go find the five Warning Signs located in the section I mentioned and
     * <em>click on them to read them</em>. All you have to do is to fix the mistakes."</blockquote>
     *
     * <p>That is the whole warrant for putting the writer on npc 1022107's talk script rather than
     * on a map entry, a mob kill or a reactor. It cannot be asserted here: this tree's
     * {@code Say.img} carries 22932 entries and not one Evan quest among them, and nothing under
     * {@code src/main/java} reads {@code Say.img} at all, so it is a human reference rather than a
     * server input. What this tree CAN check is the objective text that names the same five signs,
     * plus the absence itself - if the Evan Say lines are ever merged in, this stops being a
     * documentation-only citation and the assertion below should be tightened to the real thing.
     */
    @Test
    void theObjectiveNamesFiveSignsAndTheEvanSayLinesAreStillAbsentFromThisTree() {
        String objective = questInfo(22530, "1");
        assertTrue(objective.contains("Check on all 5 warning signs"),
                "QuestInfo 22530/1 no longer asks for all 5 signs; it read: " + objective);

        // the objective names its five maps in prose, not as #m<id># tokens, so the id list is only
        // justified by the names lining up - Nexon writes "Rocky Road 2" where String.wz says
        // "Rocky Road II", hence matching on the distinctive stem rather than the whole name
        String[] named = {"East Domain of Perion", "Rocky Road I", "Rocky Road 2", "Rocky Road 3",
                "East Rocky Mountain 1"};
        for (String name : named) {
            assertTrue(objective.contains(name),
                    "QuestInfo 22530/1 no longer names \"" + name + "\"; it read: " + objective);
        }
        assertEquals("East Domain of Perion", mapNameString(101030000));
        assertEquals("Rocky Road III", mapNameString(101030100));
        assertEquals("Rocky Road II", mapNameString(101030200));
        assertEquals("Rocky Road I", mapNameString(101030300));
        assertEquals("East Rocky Mountain I", mapNameString(101030400));

        assertNull(DataProviderFactory.getDataProvider(WZFiles.QUEST)
                        .getData("Say.img").getChildByPath("22530"),
                "this tree's Say.img has grown Evan's 22530 lines - the click-on-them instruction is "
                        + "now assertable here and this test should pin it directly");
    }

    /**
     * The signs themselves. v84 places npc 1022107 on exactly the five maps the objective names, one
     * per map - which is what makes "one distinct map per sign" a faithful count rather than a
     * convenient one.
     */
    @Test
    void oneWarningPostStandsOnEachOfTheFiveMapsTheObjectiveNames() {
        assertEquals("Perion Warning Post", stringName("Npc.img", "1022107"));
        for (int map : WARNING_SIGN_MAPS) {
            assertEquals(1, lifeCount(map, "n", 1022107),
                    "npc 1022107 is not on map " + map + ", so that sign can never be checked off");
        }
    }

    /**
     * <strong>The fix, exercised.</strong> The gate is a STRING equality against "5", so counting one
     * sign twice overshoots to "6" and locks the quest out permanently - a plain counter is not good
     * enough. {@link QuestStatus#addMedalMap} is the dedupe this codebase already uses for the same
     * shape ({@code MapScriptMethods.explorerQuest}), it refuses a map it has already seen, and it is
     * persisted in the {@code medalmaps} table so it survives a relog mid-quest.
     *
     * <p>The visits below deliberately include repeats and an out-of-order revisit. The assertion is
     * not "the count is 5", it is "the count renders as the exact string the quest demands".
     */
    @Test
    void revisitingWarningSignsStillLandsRecord22597OnExactlyFive() {
        Quest quest = Quest.getInstance(22530);
        QuestStatus qs = new QuestStatus(quest, Status.STARTED);

        assertEquals(22597, qs.getInfoNumber(),
                "a STARTED 22530 no longer resolves its record to 22597, so setQuestProgress would "
                        + "write the progress onto quest 22530 itself and the gate would never open");
        assertEquals("5", qs.getInfoEx(0), "22530 no longer demands \"5\"");

        int[] visits = {101030000, 101030000, 101030200, 101030100, 101030200,
                101030300, 101030000, 101030400, 101030400};
        for (int map : visits) {
            qs.addMedalMap(map);
        }

        assertEquals(5, qs.getMedalProgress(),
                "nine visits to five signs counted as " + qs.getMedalProgress() + " - the dedupe is "
                        + "gone, and anything but 5 leaves 22530 uncompletable forever");
        assertEquals(qs.getInfoEx(0), Integer.toString(qs.getMedalProgress()),
                "the value the writer would store does not string-match the value canQuestByInfoProgress "
                        + "compares it against");

        // and the dedupe is a real refusal, not a silent no-op the caller cannot see
        assertFalse(qs.addMedalMap(101030000), "addMedalMap accepted a sign that was already checked");
        assertTrue(qs.addMedalMap(101030001), "addMedalMap refused a map it had never seen");
    }

    /**
     * The script that performs the write. It must gate on 22530 being STARTED: while the quest is
     * NOT_STARTED, {@code Quest.getInfoNumber} looks at the START requirements instead, where 22530
     * has no {@code infoNumber} at all, and {@code Character.setQuestProgress} would then park the
     * value under quest 22530 rather than under record 22597.
     */
    @Test
    void theWarningPostScriptWritesRecord22597ThroughTheDedupe() throws IOException {
        assertTrue(Files.isRegularFile(WARNING_POST_SCRIPT),
                "scripts/npc/1022107.js is missing, so the five Warning Signs are inert scenery and "
                        + "quest 22530 cannot be completed");
        String script = Files.readString(WARNING_POST_SCRIPT, StandardCharsets.ISO_8859_1);

        assertTrue(script.contains("isQuestStarted(22530)"),
                "1022107.js no longer gates on 22530 being STARTED, so the record would be written to "
                        + "the wrong slot for anyone who has not taken the quest");
        assertTrue(script.contains("addMedalMap"),
                "1022107.js no longer dedupes by map, so re-clicking a sign overshoots past \"5\"");
        assertTrue(script.contains("setQuestProgress(22530, 22597"),
                "1022107.js no longer writes record 22597");
        assertEquals(1040001, DataTool.getInt(questCheck("22530/0/npc"), -1),
                "22530 no longer starts at npc 1040001 Mike, whose Say.img lines are the sole warrant "
                        + "for what the Warning Posts do");
    }

    /** Guards against the Quest cache being empty, which would make every lookup above vacuous. */
    @Test
    void theGatedQuestsActuallyLoad() {
        for (Object[] gate : GATES) {
            int id = (Integer) gate[0];
            assertFalse(Quest.getInstance(id).getName().isBlank(), "quest " + id + " did not load");
        }
    }

    // ---------------------------------------------------------------- helpers

    private static Data questAct(String path) {
        return DataProviderFactory.getDataProvider(WZFiles.QUEST).getData("Act.img").getChildByPath(path);
    }

    private static Data questCheck(String path) {
        return DataProviderFactory.getDataProvider(WZFiles.QUEST).getData("Check.img").getChildByPath(path);
    }

    private static String questInfo(int id, String node) {
        return DataTool.getString(DataProviderFactory.getDataProvider(WZFiles.QUEST)
                .getData("QuestInfo.img").getChildByPath(id + "/" + node), "");
    }

    /** String.wz/Map.img groups maps by region ("maple", "victoria", "ossyria", ...), so walk them. */
    private static String mapNameString(int mapId) {
        Data root = DataProviderFactory.getDataProvider(WZFiles.STRING).getData("Map.img");
        for (Data region : root.getChildren()) {
            Data entry = region.getChildByPath(String.valueOf(mapId));
            if (entry != null) {
                return DataTool.getString(entry.getChildByPath("mapName"), "");
            }
        }
        return "";
    }

    private static String stringName(String img, String path) {
        Data d = DataProviderFactory.getDataProvider(WZFiles.STRING).getData(img).getChildByPath(path);
        assertNotNull(d, "String.wz/" + img + " has no node " + path);
        return DataTool.getString(d.getChildByPath("name"), "");
    }

    private static int lifeCount(int mapId, String type, int id) {
        Data map = DataProviderFactory.getDataProvider(WZFiles.MAP).getData("Map/Map"
                + (mapId / 100000000) + "/" + String.format("%09d", mapId) + ".img");
        assertNotNull(map, "Map.wz has no image for map " + mapId);
        Data life = map.getChildByPath("life");
        if (life == null) {
            return 0;
        }
        int n = 0;
        for (Data entry : life.getChildren()) {
            if (type.equals(DataTool.getString(entry.getChildByPath("type"), ""))
                    && Integer.parseInt(DataTool.getString(entry.getChildByPath("id")).trim()) == id) {
                n++;
            }
        }
        return n;
    }
}
