package server;

import client.Character;
import client.Client;
import client.Job;
import client.QuestStatus;
import io.netty.buffer.Unpooled;
import net.packet.ByteBufInPacket;
import net.packet.Packet;
import net.server.channel.handlers.QuestActionHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import provider.wz.WZFiles;
import scripting.quest.QuestScriptManager;
import server.life.NPC;
import server.maps.MapleMap;
import server.quest.Quest;
import tools.HexTool;

import java.awt.Point;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Ticket 26. Drives {@link QuestActionHandler} with the <em>exact</em> QUEST_ACTION bytes the owner's
 * v84 client sent for quest 1021 ("Roger's Apple") and asserts the server answers. Real {@link Quest}
 * load off the real {@code wz/} tree, real {@code scripts/quest/1021.js} through the real Graal script
 * manager - only {@link Client}, {@link Character} and {@link MapleMap} are stubbed.
 *
 * <pre>
 *   mvnw.cmd test -Dtest=Quest1021RealLoad
 * </pre>
 *
 * <p><strong>Not a {@code *Test} class on purpose</strong>, same reason as {@link V84EvanQuestRealLoad}:
 * {@link WZFiles#DIRECTORY} is a {@code static final} resolved once per JVM and
 * {@code MobSkillFactoryTest} points {@code wz-path} at a {@code @TempDir} with no {@code Quest.wz} in
 * it. Whichever class touches it first wins for the whole surefire fork.
 *
 * <p>Every gate crossed here is one that returned <em>silently</em> in production - no packet, no log -
 * which is exactly why 1021 looked like a black hole on the live server.
 */
class Quest1021RealLoad {

    /** action 4 (scripted start) | quest 1021 | npc 2000 | x/y - verbatim from the live capture. */
    private static final String QUEST_ACTION_1021 = "04 FD 03 D0 07 00 00 B5 FF 13 01";

    @BeforeAll
    static void muteGraal() {
        System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
    }

    @Test
    void scriptedStartOf1021AnswersWithNpcTalk() {
        assertTrue(Files.isRegularFile(Path.of(WZFiles.DIRECTORY, "Quest.wz", "Check.img.xml")),
                "wz-path resolved to '" + WZFiles.DIRECTORY + "', which holds no Quest.wz - another test "
                        + "class won the WZFiles.DIRECTORY race, so this says nothing about quest 1021");
        assertTrue(Files.isRegularFile(Path.of("scripts", "quest", "1021.js")),
                "scripts/ is resolved relative to the working directory; it must hold quest/1021.js");

        Quest quest = Quest.getInstance(1021);
        assertEquals(2000, quest.getNpcRequirement(false));
        assertTrue(quest.hasScriptRequirement(false),
                "Check.img/1021/0/startscript must survive the load, or QuestScriptManager disposes silently");

        Client c = beginnerAtRoger();
        handle(c);

        // sendNext() from 1021.js step 0. Nothing at all here is the live symptom.
        verify(c, atLeastOnce()).sendPacket(any(Packet.class));

        QuestScriptManager.getInstance().dispose(c);
    }

    /**
     * The black hole, now netted. Closing the very first Roger dialogue with the window X used to
     * leave the {@link QuestScriptManager} session open forever, and from then on every QUEST_ACTION
     * this character sent returned at {@code qms.containsKey(c)} - no packet, no log, no error.
     * Eight clicks, eight silences. Only a map change cleared it.
     *
     * <p>Mechanism: the X sends NPC_TALK_MORE with {@code lastMsg=0, action=0}, which
     * {@code NPCMoreTalkHandler} forwards as {@code mode=0, type=0}. 1021.js only disposes on
     * {@code mode == -1} or {@code mode == 0 && type > 0}; here it takes {@code status--}, lands on
     * -1, matches no branch and returns without disposing. mode 0 is the same byte for Prev and for
     * End chat, so the script cannot tell them apart - but {@code QuestScriptManager} can see that
     * the invocation pushed no dialogue and did not dispose, and drops the session on that.
     *
     * <p>Flip {@code disposeIfStalled} off in {@code QuestScriptManager.start(Client,byte,byte,int)}
     * and the {@code assertNull} below fails - that is the mutation check for this fix.
     */
    @Test
    void closingTheFirstDialogueDoesNotWedgeLaterQuestActions() {
        Client c = beginnerAtRoger();

        handle(c);
        verify(c, atLeastOnce()).sendPacket(any(Packet.class));
        clearInvocations(c);

        // The window X: NPCMoreTalkHandler's non-lastMsg-2 branch, action 0.
        QuestScriptManager.getInstance().start(c, (byte) 0, (byte) 0, -1);
        assertNull(QuestScriptManager.getInstance().getQM(c),
                "1021.js fell off its state machine without disposing; the framework safety net must "
                        + "drop the session or every later QUEST_ACTION from this client is swallowed");

        // The whole point: the character can talk to Roger again immediately, no map change needed.
        handle(c);
        verify(c, atLeastOnce()).sendPacket(any(Packet.class));

        QuestScriptManager.getInstance().dispose(c);   // don't leak the singleton entry
    }

    /**
     * The other half of the safety net: a Prev the script genuinely handles must still work. 1021.js
     * at status 1 is a {@code sendNextPrev}, and mode 0 / type 0 from there walks back to status 0
     * and pushes {@code sendNext} - identical bytes to the X-close above, opposite correct outcome.
     * The session must survive.
     */
    @Test
    void prevOnADialogueThatHandlesItKeepsTheSessionAndTalks() {
        Client c = beginnerAtRoger();

        handle(c);                                                          // status 0: sendNext
        QuestScriptManager.getInstance().start(c, (byte) 1, (byte) 0, -1);  // status 1: sendNextPrev
        clearInvocations(c);

        QuestScriptManager.getInstance().start(c, (byte) 0, (byte) 0, -1);  // Prev: back to status 0

        verify(c, atLeastOnce()).sendPacket(any(Packet.class));
        assertNotNull(QuestScriptManager.getInstance().getQM(c),
                "the safety net must not dispose a conversation the script kept alive with a dialogue");

        QuestScriptManager.getInstance().dispose(c);
    }

    /**
     * Ticket 31. Drives 1021.js to status 2 - the {@code qm.sendAcceptDecline} the owner never saw -
     * and reads the dialog-type byte straight off the wire.
     *
     * <p>This is the whole diagnosis in one assert: the packet IS built and sent (so the script did
     * not throw, the conversation was not disposed, and nothing gated the call), and the byte it
     * carries is the one a v84 client's {@code CScriptMan::OnScriptMessage} switch actually has a
     * case for. Before the fix this was 0x0C, and the v84 switch has no case 12 - the client dropped
     * the frame, drew nothing and replied nothing, exactly as observed.
     *
     * <p>NPC_TALK layout: opcode(2) speakerType(1) npcId(4) msgType(1) speaker(1) ...
     */
    @Test
    void acceptDeclineCarriesTheDialogTypeThisClientDispatchesOn() {
        Client c = beginnerAtRoger();

        handle(c);                                                          // status 0: sendNext
        QuestScriptManager.getInstance().start(c, (byte) 1, (byte) 0, -1);  // status 1: sendNextPrev
        clearInvocations(c);
        QuestScriptManager.getInstance().start(c, (byte) 1, (byte) 0, -1);  // status 2: sendAcceptDecline

        ArgumentCaptor<Packet> sent = ArgumentCaptor.forClass(Packet.class);
        verify(c, atLeastOnce()).sendPacket(sent.capture());
        byte[] talk = sent.getAllValues().get(sent.getAllValues().size() - 1).getBytes();

        assertEquals((byte) 0x0D, talk[7],
                "AskYesNoQuest is 12 in the v83 dialog-type enum and 13 from v84 on; sending the "
                        + "wrong one lands on a switch case the client does not have and the "
                        + "dialogue never renders");

        QuestScriptManager.getInstance().dispose(c);
    }

    /**
     * Negative control: the same packet from a non-beginner must NOT produce a talk. Without this,
     * "it answered" would also be the result of a handler that answers unconditionally.
     */
    @Test
    void wrongJobStartsNothing() {
        Client c = beginnerAtRoger();
        lenient().when(c.getPlayer().getJob()).thenReturn(Job.EVAN1);   // 2200

        handle(c);

        verify(c, never()).sendPacket(any(Packet.class));
    }

    private static void handle(Client c) {
        new QuestActionHandler().handlePacket(
                new ByteBufInPacket(Unpooled.wrappedBuffer(HexTool.toBytes(QUEST_ACTION_1021))), c);
    }

    /** Job 0, level 1, standing on map 20000 next to NPC 2000, with no queststatus row for 1021. */
    private static Client beginnerAtRoger() {
        Client c = mock(Client.class);
        Character chr = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        NPC roger = mock(NPC.class);

        lenient().when(c.getPlayer()).thenReturn(chr);
        lenient().when(c.canClickNPC()).thenReturn(true);
        lenient().when(c.getScriptEngine(anyString())).thenReturn(null);
        lenient().when(chr.getMap()).thenReturn(map);
        lenient().when(chr.getMapId()).thenReturn(20000);
        lenient().when(chr.getName()).thenReturn("Tester");
        lenient().when(chr.getPosition()).thenReturn(new Point(205, 215));
        lenient().when(chr.getJob()).thenReturn(Job.BEGINNER);
        lenient().when(chr.getGender()).thenReturn(0);
        lenient().when(chr.getQuest(any(Quest.class)))
                .thenReturn(new QuestStatus(Quest.getInstance(1021), QuestStatus.Status.NOT_STARTED));
        lenient().when(chr.isGM()).thenReturn(false);
        lenient().when(map.getNPCById(2000)).thenReturn(roger);
        lenient().when(roger.getPosition()).thenReturn(new Point(233, 58));
        return c;
    }
}
