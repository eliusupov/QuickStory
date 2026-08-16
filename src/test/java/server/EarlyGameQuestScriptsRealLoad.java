package server;

import client.Character;
import client.Client;
import client.QuestStatus;
import net.packet.Packet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import provider.wz.WZFiles;
import scripting.quest.QuestScriptManager;
import server.maps.MapleMap;
import server.quest.Quest;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Real {@link Quest} load off the real {@code wz/} tree driving the real {@code scripts/quest/*.js}
 * through the real Graal script manager, for the four early-game quests whose declared start/end
 * script did not exist on disk. Only {@link Client}, {@link Character} and {@link MapleMap} are
 * stubbed.
 *
 * <p>Before these files existed, {@code AbstractScriptManager} line 48-51 returned {@code null} for
 * a missing file and {@code QuestScriptManager} disposed with a single {@code log.warn} - the player
 * saw nothing at all. Each test here asserts the opposite: a packet leaves the server.
 *
 * <p>The second assertion in each test is the X-close. {@code scripts/quest/1021.js} disposes only
 * on {@code mode == -1} or {@code (mode == 0 && type > 0)}, so the window X (mode 0, type 0) falls
 * off its state machine and leaves the {@link QuestScriptManager} session held forever, killing
 * every later QUEST_ACTION at {@code qms.containsKey(c)}. The four scripts added here dispose on
 * every reachable path, and that is what {@code assertNull(getQM(c))} pins down.
 *
 * <pre>
 *   mvnw.cmd test -Dtest=EarlyGameQuestScriptsRealLoad
 * </pre>
 *
 * <p><strong>Not a {@code *Test} class on purpose</strong>, same reason as {@link Quest1021RealLoad}:
 * {@link WZFiles#DIRECTORY} is a {@code static final} resolved once per JVM and
 * {@code MobSkillFactoryTest} points {@code wz-path} at a {@code @TempDir} with no {@code Quest.wz}.
 */
class EarlyGameQuestScriptsRealLoad {

    @BeforeAll
    static void muteGraal() {
        System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
        assertTrue(Files.isRegularFile(Path.of(WZFiles.DIRECTORY, "Quest.wz", "Check.img.xml")),
                "wz-path resolved to '" + WZFiles.DIRECTORY + "', which holds no Quest.wz - another "
                        + "test class won the WZFiles.DIRECTORY race, so this says nothing about quests");
    }

    /** 20015 "Greetings From the Young Empress" - startscript only, closes the Noblesse tutorial. */
    @Test
    void scriptedStartOf20015Talks() {
        assertTrue(Quest.getInstance(20015).hasScriptRequirement(false),
                "Check.img/20015/0/startscript must survive the load, or the manager disposes silently");
        assertTrue(Files.isRegularFile(Path.of("scripts", "quest", "20015.js")));

        Client c = player(20015);
        QuestScriptManager.getInstance().start(c, (short) 20015, 1101000);
        verify(c, atLeastOnce()).sendPacket(any(Packet.class));

        clearInvocations(c);
        QuestScriptManager.getInstance().start(c, (byte) 0, (byte) 0, -1);   // the window X
        assertNull(QuestScriptManager.getInstance().getQM(c),
                "20015.js must dispose on mode 0 / type 0 or it wedges every later QUEST_ACTION");
    }

    /** 6700 "The Bowman's Road" - endscript only, Athena Pierce. */
    @Test
    void scriptedEndOf6700Talks() {
        assertTrue(Quest.getInstance(6700).hasScriptRequirement(true),
                "Check.img/6700/1/endscript must survive the load, or the manager disposes silently");

        Client c = player(6700);
        QuestScriptManager.getInstance().end(c, (short) 6700, 1012100);
        verify(c, atLeastOnce()).sendPacket(any(Packet.class));

        clearInvocations(c);
        QuestScriptManager.getInstance().end(c, (byte) 0, (byte) 0, -1);     // the window X
        assertNull(QuestScriptManager.getInstance().getQM(c),
                "6700.js must dispose on mode 0 / type 0 or it wedges every later QUEST_ACTION");
    }

    /**
     * 2233 / 2234, Captain Al's Family chain. Both read the family entry, which is null here, so
     * both take the "not there yet" branch: one packet, then an immediate dispose. That the session
     * is already gone on the very next line is the point - there is no state machine to fall off.
     */
    @Test
    void scriptedEndOf2233And2234TalkAndDisposeImmediately() {
        for (short questId : new short[]{2233, 2234}) {
            assertTrue(Quest.getInstance(questId).hasScriptRequirement(true),
                    "Check.img/" + questId + "/1/endscript must survive the load");

            Client c = player(questId);
            QuestScriptManager.getInstance().end(c, questId, 1002103);

            verify(c, atLeastOnce()).sendPacket(any(Packet.class));
            assertNull(QuestScriptManager.getInstance().getQM(c),
                    questId + ".js must dispose on every path");
        }
    }

    /**
     * Negative control. Without it, "a packet was sent" would also be the result of a manager that
     * answers regardless of whether a script file exists. 1054 is deliberately left uncoded, so it
     * must still take the silent path and leave no session behind.
     */
    @Test
    void aQuestWithNoScriptFileStillLeavesNoSession() {
        Quest quest = Quest.getInstance(1054);
        assertNotNull(quest);

        Client c = player(1054);
        // 1054 declares a startscript but has no file. It is also unreachable for a reason no
        // script could fix: Check.img/1054/0/end is "2009020200", already expired when v84 shipped,
        // so EndDateRequirement refuses it before its infoNumber 1055 is ever read. See
        // MedalQuestFallbackRealLoad#quests1048To1054AreRetiredEventContent. Missing file -> silence.
        QuestScriptManager.getInstance().start(c, (short) 1054, 1101002);
        verify(c, never()).sendPacket(any(Packet.class));
        assertNull(QuestScriptManager.getInstance().getQM(c),
                "an uncoded quest must not leave a QuestActionManager behind");
    }

    /** Level 10 character standing next to the quest NPC with the quest already STARTED. */
    private static Client player(int questId) {
        Client c = mock(Client.class);
        Character chr = mock(Character.class);
        MapleMap map = mock(MapleMap.class);

        lenient().when(c.getPlayer()).thenReturn(chr);
        lenient().when(c.canClickNPC()).thenReturn(true);
        lenient().when(c.getScriptEngine(anyString())).thenReturn(null);
        lenient().when(chr.getMap()).thenReturn(map);
        lenient().when(chr.getName()).thenReturn("Tester");
        lenient().when(chr.getLevel()).thenReturn(10);
        lenient().when(chr.getFamilyEntry()).thenReturn(null);
        lenient().when(chr.getQuest(any(Quest.class)))
                .thenReturn(new QuestStatus(Quest.getInstance(questId), QuestStatus.Status.STARTED));
        lenient().when(map.containsNPC(anyInt())).thenReturn(true);
        return c;
    }
}
