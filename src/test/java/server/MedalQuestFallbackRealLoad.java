package server;

import client.Character;
import client.Client;
import client.QuestStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import provider.Data;
import provider.DataProvider;
import provider.wz.WZFiles;
import scripting.quest.QuestScriptManager;
import server.maps.MapleMap;
import server.quest.Quest;
import server.quest.requirements.EndDateRequirement;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Real {@link Quest} load off the real {@code wz/} tree driving the real
 * {@code scripts/quest/medalQuest.js} through the real Graal script manager.
 *
 * <p>Two separate claims live here.
 *
 * <p><strong>The medal fallback.</strong> 39 quests carrying {@code viewMedalItem} declare a
 * script in {@code Check.img} and have no {@code .js} of their own, so
 * {@code QuestScriptManager.getQuestScriptEngine} routes them to {@code medalQuest.js}. Eight of
 * them declare a startscript <em>and</em> an endscript - two NPC visits - and the old fallback
 * completed inside {@code start()}, collapsing both into one click. These tests pin the split.
 *
 * <p><strong>Quests 1048-1054.</strong> They are quoted as blocked on quest-progress keys 7631
 * and 1055 that nothing writes. They are blocked on something earlier and unconditional: every
 * one of them carries an expired {@code end} date, so {@link EndDateRequirement} refuses them
 * before any progress key is read. See the test for the citations.
 *
 * <pre>
 *   mvnw.cmd test -Dtest=MedalQuestFallbackRealLoad
 * </pre>
 *
 * <p><strong>Not a {@code *Test} class on purpose</strong>, same reason as
 * {@code EarlyGameQuestScriptsRealLoad}: {@link WZFiles#DIRECTORY} is a {@code static final}
 * resolved once per JVM and {@code MobSkillFactoryTest} points {@code wz-path} at a
 * {@code @TempDir} with no {@code Quest.wz}.
 */
class MedalQuestFallbackRealLoad {

    /** 29400 "Title Challenge - Veteran Hunter": Check.img/29400/0 startscript + /1 endscript. */
    private static final short TWO_VISIT_MEDAL = 29400;

    /** 29910 "Gallant Warrior": Check.img/29910/0 startscript only, and /0 already requires the medal. */
    private static final short ONE_VISIT_MEDAL = 29910;

    @BeforeAll
    static void muteGraal() {
        System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
        assertTrue(Files.isRegularFile(Path.of(WZFiles.DIRECTORY, "Quest.wz", "Check.img.xml")),
                "wz-path resolved to '" + WZFiles.DIRECTORY + "', which holds no Quest.wz - another "
                        + "test class won the WZFiles.DIRECTORY race, so this says nothing about quests");
    }

    /**
     * The premise of the split. If either of these ever stops holding, the two tests below are
     * asserting nothing about the real data.
     */
    @Test
    void theTwoMedalQuestsUnderTestDifferOnlyInWhetherAnEndScriptExists() {
        assertTrue(Quest.getInstance(TWO_VISIT_MEDAL).hasScriptRequirement(false));
        assertTrue(Quest.getInstance(TWO_VISIT_MEDAL).hasScriptRequirement(true),
                "29400 must declare Check.img/29400/1/endscript - it is what makes it a two-visit challenge");

        assertTrue(Quest.getInstance(ONE_VISIT_MEDAL).hasScriptRequirement(false));
        assertFalse(Quest.getInstance(ONE_VISIT_MEDAL).hasScriptRequirement(true),
                "29910 must NOT declare an endscript - completing on the spot is its whole flow");

        assertFalse(Files.isRegularFile(Path.of("scripts", "quest", TWO_VISIT_MEDAL + ".js")),
                "if 29400 ever gets its own script this test stops exercising the fallback");
        assertFalse(Files.isRegularFile(Path.of("scripts", "quest", ONE_VISIT_MEDAL + ".js")));
    }

    /**
     * A Title Challenge is accepted, not won, on the first visit. The old fallback called
     * forceCompleteQuest() inside start(), so the medal landed on the same click that accepted
     * the challenge.
     */
    @Test
    void aMedalQuestWithItsOwnEndScriptIsOnlyStarted() {
        Character chr = mock(Character.class);
        Client c = player(chr, TWO_VISIT_MEDAL);

        QuestScriptManager.getInstance().start(c, TWO_VISIT_MEDAL, 9000066);

        List<QuestStatus.Status> written = writtenStatuses(chr);
        assertTrue(written.contains(QuestStatus.Status.STARTED),
                "accepting the challenge must still start the quest, got " + written);
        assertFalse(written.contains(QuestStatus.Status.COMPLETED),
                "29400 declares its own endscript - claiming it is a second visit, got " + written);
        assertNull(QuestScriptManager.getInstance().getQM(c), "medalQuest.js must dispose on every path");
    }

    /**
     * Control: without an endscript there is no second visit, so start() is the whole flow.
     *
     * <p>No dispose assertion here, unlike the test above. The completing path ends in
     * {@code qm.earnTitle(qm.getMedalName())} and {@code getMedalName} initialises
     * {@link server.ItemInformationProvider}, which reads the database - absent here. The script
     * dies after the completion and {@code QuestScriptManager}'s catch-all disposes for it, so a
     * {@code getQM(c) == null} would pass whatever the script did. The status capture is taken
     * before that point and is real.
     */
    @Test
    void aMedalQuestWithoutAnEndScriptStillCompletesOnTheSpot() {
        Character chr = mock(Character.class);
        Client c = player(chr, ONE_VISIT_MEDAL);

        QuestScriptManager.getInstance().start(c, ONE_VISIT_MEDAL, 9000066);

        List<QuestStatus.Status> written = writtenStatuses(chr);
        assertTrue(written.contains(QuestStatus.Status.COMPLETED),
                "29910 has no second visit to complete on, got " + written);
        verify(chr, atLeastOnce()).sendPacket(any());
    }

    /** The second visit is what awards the medal for a two-visit challenge. */
    @Test
    void theEndScriptOfATwoVisitMedalQuestCompletesIt() {
        Character chr = mock(Character.class);
        // QuestScriptManager.end disposes anything not already STARTED - the challenge was
        // accepted on the first visit, which is exactly what the test above pins down.
        Client c = player(chr, TWO_VISIT_MEDAL, QuestStatus.Status.STARTED);

        QuestScriptManager.getInstance().end(c, TWO_VISIT_MEDAL, 9000066);

        assertTrue(writtenStatuses(chr).contains(QuestStatus.Status.COMPLETED));
    }

    /**
     * The user-visible wart, and the one claim here that is <em>not</em> proved by execution.
     * {@code qm.message} routes to Character.message, so a {@code never()} on the mock looks like
     * the natural assertion - but it cannot fail. The line before it in the old fallback was
     * {@code qm.getMedalName()}, which initialises {@link server.ItemInformationProvider}, whose
     * constructor reads the database; with no database the class fails to initialise, the script
     * dies there and {@code QuestScriptManager}'s catch-all disposes. The mock therefore never
     * sees {@code message} either way, and a test that cannot fail is not evidence.
     *
     * <p>So this asserts the source instead, and says so. It is a regression guard on one string,
     * not a behavioural proof.
     */
    @Test
    void theFallbackSourceNoLongerTellsThePlayerTheQuestIsNotCoded() throws Exception {
        String source = Files.readString(Path.of("scripts", "quest", "medalQuest.js"));
        assertFalse(source.contains("is not coded"),
                "medalQuest.js runs only after Quest.canStart/canComplete have passed - there is "
                        + "nothing uncoded left to apologise for");
    }

    /**
     * Quests 1048-1054 (the "Job Recommendation" survey and "Cygnus Knights") are unreachable for
     * a reason that has nothing to do with quest-progress key 7631 or 1055: Check.img gives each of
     * them an {@code end} date in 2009, and GMS v84 shipped in late 2009 with them already retired.
     *
     * <p>{@code EndDateRequirement.check} compares that string against now and returns false;
     * {@code Quest.canStart} refuses on the first unmet start requirement. So no writer for 7631
     * could make these startable, and Act.img declares no actions for them either - there is
     * nothing on the far side to reach.
     */
    @Test
    void quests1048To1054AreRetiredEventContent() {
        DataProvider quest = V84Wz.wz("Quest.wz");
        Data check = quest.getData("Check.img");
        Data act = quest.getData("Act.img");

        for (int id = 1048; id <= 1054; id++) {
            Data startReq = check.getChildByPath(id + "/0");
            assertNotNull(startReq, "Check.img/" + id + "/0 must exist");

            Data end = startReq.getChildByPath("end");
            assertNotNull(end, "quest " + id + " must carry an end date - that is the whole finding");

            EndDateRequirement req = new EndDateRequirement(Quest.getInstance(id), end);
            assertFalse(req.check(null, null),
                    "quest " + id + " end date " + end.getData() + " must already have passed");

            for (String state : new String[]{"0", "1"}) {
                Data acts = act.getChildByPath(id + "/" + state);
                if (acts != null) {
                    assertEquals(0, acts.getChildren().size(),
                            "Act.img/" + id + "/" + state + " must be empty - these quests award nothing");
                }
            }
        }
    }

    private static List<QuestStatus.Status> writtenStatuses(Character chr) {
        ArgumentCaptor<QuestStatus> captor = ArgumentCaptor.forClass(QuestStatus.class);
        verify(chr, atLeastOnce()).updateQuestStatus(captor.capture());
        return captor.getAllValues().stream().map(QuestStatus::getStatus).toList();
    }

    /** Level 200 character standing next to the medal NPC with the quest not yet taken. */
    private static Client player(Character chr, int questId) {
        return player(chr, questId, QuestStatus.Status.NOT_STARTED);
    }

    private static Client player(Character chr, int questId, QuestStatus.Status status) {
        Client c = mock(Client.class);
        MapleMap map = mock(MapleMap.class);
        QuestStatus notStarted = new QuestStatus(Quest.getInstance(questId), status);

        lenient().when(c.getPlayer()).thenReturn(chr);
        lenient().when(c.canClickNPC()).thenReturn(true);
        lenient().when(c.getScriptEngine(anyString())).thenReturn(null);
        lenient().when(chr.getMap()).thenReturn(map);
        lenient().when(chr.getName()).thenReturn("Tester");
        lenient().when(chr.getLevel()).thenReturn(200);
        lenient().when(chr.getQuest(any(Quest.class))).thenReturn(notStarted);
        lenient().when(chr.getQuest(anyInt())).thenReturn(notStarted);
        lenient().when(map.containsNPC(anyInt())).thenReturn(true);
        return c;
    }
}
