package net.server.channel.handlers;

import client.inventory.Pet;
import net.packet.InPacket;
import net.packet.OutPacket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import server.maps.Dragon;
import server.maps.MapleMap;
import server.maps.Summon;
import server.movement.LifeMovementFragment;
import testutil.HandlerTest;
import testutil.Packets;
import tools.HexTool;

import java.awt.Point;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the serverbound movement header - the bytes each handler must consume before the
 * movement-command count - at both versions.
 *
 * <p>MOVE_PLAYER needs a v84 gate (9 -> 33, commit {@code 404ec864d}); MOVE_DRAGON, MOVE_SUMMON and
 * MOVE_PET must NOT get one. That asymmetry is measured, not assumed: the v84 anti-cheat "dr words"
 * pass rewrote - and virtualised - {@code CVecCtrlUser::EndUpdateActive} alone. It left the shared
 * {@code CMovePath::Encode} (v83 {@code 0x0068A563}, v84 {@code 0x006A121A}) instruction-for-
 * instruction identical, and left the dragon / summon / pet encoders alone too. Addresses are
 * recorded in each handler; the full read-out is docs/work-plan/tickets/38-v84-movement-headers.md.
 *
 * <p>These failures are silent by construction: a wrong header makes the command count read as
 * garbage, {@code updatePosition} throws {@code EmptyMovementException}, every one of these handlers
 * catches and ignores it, the object never moves and nothing is logged. So each test asserts the
 * target actually arrived at the encoded destination - not merely that no exception escaped.
 */
@ExtendWith(MockitoExtension.class)
class MovementHeaderTest extends HandlerTest {
    /** Packet-level bytes the client writes before CMovePath's own 4-byte origin. */
    private static final int PLAYER_PROLOGUE = 29;
    private static final int DRAGON_PROLOGUE = 0;   // the dragon writes none, both versions

    private static final int ORIGIN_X = 0x0007;
    private static final int ORIGIN_Y = 0x00D7;
    private static final int DEST_X = 0x0047;
    private static final int DEST_Y = 0x00F7;
    private static final int SUMMON_OID = 0x11223344;
    private static final int PET_ID = 0x55667788;

    @Mock
    private MapleMap map;

    @Test
    void movePlayerHeaderIsThirtyThreeAtV84() {
        when(chr.getMap()).thenReturn(map);

        new MovePlayerHandler().handlePacket(move(filler(PLAYER_PROLOGUE)), client);

        verify(chr).setPosition(new Point(DEST_X, DEST_Y));
    }

    /**
     * The control for the other three: a byte-for-byte MOVE_PLAYER the v84 client actually sent
     * (tools/v84/cutover-server.prev.log, 22:44:51.787), opcode stripped. Reading it at 9 yields a
     * command count of 0xFF; only 33 lands on the real count. Four more captures in that log agree.
     */
    @Test
    void movePlayerParsesACapturedV84Packet() {
        byte[] captured = HexTool.toBytes(
                "FF FF FF FF FF FF FF FF 01 FF FF FF FF FF FF FF FF AE 12 4A BB 0F F6 50 FA 83 84 F8"
                        + " 6A 07 00 D7 00 01 00 47 00 D7 00 7D 00 00 00 04 00 02 FE 01 11 44 44 44 44"
                        + " 44 44 44 44 04 07 00 D7 00 47 00 D7 00");
        when(chr.getMap()).thenReturn(map);

        new MovePlayerHandler().handlePacket(Packets.buildInPacket(p -> p.writeBytes(captured)), client);

        verify(chr).setPosition(new Point(0x47, 0xD7));
    }

    @Test
    void moveDragonHeaderIsFourAtBothVersions() {
        Dragon dragon = mock(Dragon.class);
        when(dragon.getOwner()).thenReturn(chr);
        when(chr.getDragon()).thenReturn(dragon);
        when(chr.getMap()).thenReturn(map);

        new MoveDragonHandler().handlePacket(move(filler(DRAGON_PROLOGUE)), client);

        verify(dragon).setPosition(new Point(DEST_X, DEST_Y));
    }

    @Test
    void moveSummonHeaderIsEightAtBothVersions() {
        Summon summon = mock(Summon.class);
        when(summon.getObjectId()).thenReturn(SUMMON_OID);
        when(chr.getSummonsValues()).thenReturn(List.of(summon));
        when(chr.getMap()).thenReturn(map);

        // Encode4 owner cid - the whole packet-level prologue, 4 bytes at both versions
        new MoveSummonHandler().handlePacket(move(p -> p.writeInt(SUMMON_OID)), client);

        verify(summon).setPosition(new Point(DEST_X, DEST_Y));
    }

    @Test
    void movePetHeaderIsTwelveAtBothVersions() {
        Pet pet = mock(Pet.class);
        when(chr.getPetIndex(PET_ID)).thenReturn((byte) 0);
        when(chr.getPet(0)).thenReturn(pet);
        when(chr.getMap()).thenReturn(map);

        // the 8-byte locker SN, of which the handler reads the first 4 as the pet id; its readLong
        // then swallows the SN's tail plus CMovePath's origin - 12 bytes, both versions
        new MovePetHandler().handlePacket(move(p -> {
            p.writeInt(PET_ID);
            p.writeInt(-1);
        }), client);

        ArgumentCaptor<List<LifeMovementFragment>> moves = ArgumentCaptor.forClass(List.class);
        verify(pet).updatePosition(moves.capture());
        assertEquals(new Point(DEST_X, DEST_Y), moves.getValue().getFirst().getPosition());
    }

    /**
     * One movement command behind {@code prologue}, laid out exactly as the client writes it:
     * the packet-level prologue, then CMovePath::Encode's Encode2 startX / Encode2 startY /
     * Encode1 count, then a single absolute-move element.
     */
    private static InPacket move(Consumer<OutPacket> prologue) {
        return Packets.buildInPacket(p -> {
            prologue.accept(p);
            p.writeShort(ORIGIN_X);     // CMovePath::Encode Encode2 - v83 0x0068A57C, v84 0x006A1233
            p.writeShort(ORIGIN_Y);     //                  Encode2 - v83 0x0068A592, v84 0x006A1249
            p.writeByte(1);             //                  Encode1 - v83 0x0068A5C3, v84 0x006A127A
            p.writeByte(0);             // MOVE_ABSOLUTE
            p.writeShort(DEST_X);
            p.writeShort(DEST_Y);
            p.writeShort(0);            // x wobble
            p.writeShort(0);            // y wobble
            p.writeShort(4);            // foothold
            p.writeByte(2);             // new stance
            p.writeShort(0x1FE);        // duration
        });
    }

    /**
     * 0xFF, the value the v84 client's obfuscated "dr words" actually carry on the wire - so a
     * header that is short by any amount reads a negative command count and throws, rather than
     * accidentally parsing.
     */
    private static Consumer<OutPacket> filler(int length) {
        byte[] bytes = new byte[length];
        Arrays.fill(bytes, (byte) 0xFF);
        return p -> p.writeBytes(bytes);
    }
}
