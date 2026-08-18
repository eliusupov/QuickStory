package tools;

import client.Character;
import constants.net.ServerConstants;
import net.packet.Packet;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Pins the mode/type discriminator of SHOW_STATUS_INFO and SERVERMESSAGE at both versions.
 *
 * <p>Both enums gained members mid-list in v84, so every mode at or above the insertion point means
 * a different thing on the wire. Read out of the client binaries, not inferred:
 *
 * <ul>
 * <li><b>SHOW_STATUS_INFO</b> - {@code CWvsContext::OnMessage}, dispatcher case 0x27 in both.
 *     v83 {@code localhome.exe} @0xA209D4 has {@code cmp eax,0xD} + jump table @0xA20A88 (14 arms);
 *     v84 @0xA6BDD9 has {@code cmp eax,0xE} + table @0xA6BE9A (15 arms). The extra arm is v84 mode 4
 *     @0xA6CEFA, which has no v83 counterpart and whose body does {@code idiv 100} /
 *     {@code cmp eax,0x16} / {@code cmp esi,0x7D1} - the Evan SP window. 13 of the 14 v83 arm bodies
 *     are instruction-identical to the v84 arm this mapping targets. <b>Shift point 4, +1.</b></li>
 * <li><b>SERVERMESSAGE</b> - {@code CWvsContext::OnBroadcastMsg}, dispatcher case 0x44 at v83 and
 *     0x46 at v84. v83 @0xA22785 switch @0xA229B0 {@code cmp esi,0xD} + table @0xA236D3 (14 arms,
 *     12 and 13 sharing body @0xA22AA4); v84 @0xA6DC97 switch @0xA6DF39 {@code cmp esi,0xF} + table
 *     @0xA6ED68 (16 arms, 14 and 15 sharing body @0xA6E039). All 14 v83 bodies match under this
 *     mapping; v84's new 12/13 match nothing at v83. <b>Shift point 12, +2.</b></li>
 * </ul>
 *
 * <p>See docs/work-plan/tickets/36-v84-status-and-broadcast-enums.md.
 */
class StatusInfoModeTest {

    private static final boolean V84 = ServerConstants.VERSION >= 84;

    // ------------------------------------------------------------------ SHOW_STATUS_INFO

    @Test
    void statusInfoModesBelowTheShiftPointAreUntouched() {
        assertMode(0, PacketCreator.getShowInventoryStatus(0xfe), "drop pickup");
        assertMode(0, PacketCreator.getShowMesoGain(1, false), "meso, not in chat, is mode 0");
        assertMode(1, PacketCreator.forfeitQuest((short) 1021), "quest record");
        assertMode(1, PacketCreator.completeQuest((short) 1021, 0L), "quest record");
        assertMode(2, PacketCreator.itemExpired(2010007), "cash item expire");
        assertMode(3, PacketCreator.getShowExpGain(1, 0, 0, true, false), "EXP - the attack path");
    }

    @Test
    void statusInfoModesAtOrAboveFourShiftByOneOnV84() {
        assertMode(V84 ? 5 : 4, PacketCreator.getShowFameGain(1), "fame");
        assertMode(V84 ? 6 : 5, PacketCreator.getShowMesoGain(1, true), "meso in chat");
        assertMode(V84 ? 7 : 6, PacketCreator.getGPMessage(1), "guild point");
        assertMode(V84 ? 8 : 7, PacketCreator.getItemMessage(2010007), "give item");
        assertMode(V84 ? 10 : 9, PacketCreator.showInfoText("x"), "system message");
        assertMode(V84 ? 10 : 9, PacketCreator.getDojoInfoMessage("x"), "system message");
        assertMode(V84 ? 10 : 9, PacketCreator.bunnyPacket(), "system message");
        assertMode(V84 ? 11 : 10, PacketCreator.updateAreaInfo(1, "x"), "quest record ex");
        assertMode(V84 ? 11 : 10, PacketCreator.getDojoInfo("x"), "quest record ex");
        assertMode(V84 ? 11 : 10, PacketCreator.updateDojoStats(dojoChr(), 1), "quest record ex");
    }

    // ------------------------------------------------------------------ SERVERMESSAGE

    @Test
    void broadcastModesBelowTwelveAreUntouched() {
        for (int type : new int[]{0, 1, 2, 5, 6, 7, 8, 9, 10, 11}) {
            assertMode(type, PacketCreator.serverNotice(type, "x"), "serverNotice " + type);
        }
        assertMode(3, PacketCreator.serverNotice(3, 1, "x"), "super megaphone");
        assertMode(4, PacketCreator.serverMessage("x"), "scrolling header");
        assertMode(8, PacketCreator.itemMegaphone("x", false, 1, null), "item megaphone");
        assertMode(10, PacketCreator.getMultiMegaphone(new String[]{"x"}, 1, false), "multi megaphone");
    }

    @Test
    void broadcastModesAtOrAboveTwelveShiftByTwoOnV84() {
        assertMode(V84 ? 14 : 12, PacketCreator.serverNotice(12, "x"), "v83 mode 12");
        assertMode(V84 ? 15 : 13, PacketCreator.serverNotice(13, "x"), "v83 mode 13");
    }

    // ------------------------------------------------------------------ PARTY_OPERATION (OnPartyResult)

    /**
     * v84 inserted 3 modes into OnPartyResult's door cluster: v83 localhome.exe modes 0x23/0x24/0x25
     * (@0xa3ec92 Decode4x3, @0xa3f19d Decode1+Str, @0xa3ecd5 Decode1+Decode4+Decode4+Decode2+Decode2)
     * map instruction-for-instruction to v84 ida_export {@code CWvsContext::OnPartyResult} @0xa89cf3
     * modes 0x26/0x27/0x28. The town-portal send uses 0x23, so it is 0x26 on v84. See ticket 36 s6.
     */
    @Test
    void partyPortalDoorModeShiftsByThreeOnV84() {
        assertMode(V84 ? 0x26 : 0x23,
                PacketCreator.partyPortal(100000000, 100000001, new java.awt.Point(0, 0)),
                "party town-portal door update");
    }

    // ------------------------------------------------------------------

    private static void assertMode(int expected, Packet p, String what) {
        assertEquals(expected, p.getBytes()[2] & 0xFF,
                what + ": wrong mode byte at VERSION " + ServerConstants.VERSION);
    }

    private static Character dojoChr() {
        Character chr = Mockito.mock(Character.class);
        when(chr.getDojoPoints()).thenReturn(0);
        when(chr.getFinishedDojoTutorial()).thenReturn(true);
        return chr;
    }
}
