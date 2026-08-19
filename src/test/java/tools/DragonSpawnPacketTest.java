package tools;

import client.Character;
import client.Job;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import server.maps.Dragon;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.when;

/**
 * Pins the wire length of SPAWN_DRAGON ({@code CUserPool::OnPacket} 0xB9 arm ->
 * {@code CDragon} decode at v84 0x00506F85).
 *
 * <p>v84 reads TWO shorts after the stance byte (13 bytes after the owner id); v83's writer emitted
 * byte+short (12). Measured in the live v84 client image, not inferred:
 * {@code Decode4 x @0x506FA5, Decode4 y @0x506FB2, Decode1 stance @0x506FD7, Decode2 @0x506FF3,
 * Decode2 @0x506FFA}. A v83-shaped dragon spawn under-runs the v84 client by one byte and it throws
 * {@code ZException (error code : 38)} - the crash-history entry
 * {@code evan @ map 100030102 error 38} the owner's client uploaded. Because Character respawns the
 * dragon on every map entry for job 2200+, that one short byte was a hard login loop, not a one-off.
 *
 * <p>The offline PacketStructureValidator models this packet wrong (its resolver stops on the mob
 * pool's early-out arm - see commit 393127dc6), so this hand-pinned length is the guard that catches
 * a regression. See {@code PacketCreator.spawnDragon} and ticket 24 / 32.
 */
class DragonSpawnPacketTest {

    @Test
    void dragonSpawnCarriesTheV84TrailingShort() {
        byte[] body = withoutOpcode(PacketCreator.spawnDragon(dragon()).getBytes());

        byte[] head = new byte[]{
                0x44, 0x33, 0x22, 0x11,   // owner id 0x11223344
                0x02, 0x01, 0x00, 0x00,   // x = 0x0102
                0x04, 0x03, 0x00, 0x00,   // y = 0x0304
                0x05,                     // stance
        };
        byte[] job = new byte[]{(byte) 0x98, 0x08}; // job 2200 = 0x0898, little-endian

        // stance byte + TWO shorts (the extra 0x00) + job short = 13 bytes after the owner id
        assertArrayEquals(concat(head, concat(new byte[]{0, 0}, job)), body,
                "v84 CDragon decode reads short+short after the stance byte, not byte+short");
    }

    private static Dragon dragon() {
        Job job = Mockito.mock(Job.class);
        when(job.getId()).thenReturn(2200);

        Character owner = Mockito.mock(Character.class);
        when(owner.getId()).thenReturn(0x11223344);
        when(owner.getJob()).thenReturn(job);

        Dragon d = Mockito.mock(Dragon.class);
        when(d.getOwner()).thenReturn(owner);
        when(d.getPosition()).thenReturn(new Point(0x0102, 0x0304));
        when(d.getStance()).thenReturn(5);
        return d;
    }

    private static byte[] withoutOpcode(byte[] packet) {
        byte[] body = new byte[packet.length - 2];
        System.arraycopy(packet, 2, body, 0, body.length);
        return body;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
