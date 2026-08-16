package tools;

import constants.net.ServerConstants;
import net.packet.InPacket;
import net.packet.Packet;
import org.junit.jupiter.api.Test;
import testutil.Packets;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the wire layout of the clientbound MOVE_MONSTER body ({@code CMob::OnMove}).
 *
 * <p>v84 inserts {@code nMultiTargetForBall} + {@code nRandTimeForAreaAttack} (two int counts,
 * 8 bytes when both are empty) between the packed skill data and the movement body. Missing them
 * under-runs the v84 client by 8 bytes, and {@code Monster.resetMobPosition} broadcasts this packet
 * to EVERY player in the map including the attacker, straight out of the attack handler's
 * distance check. See {@code PacketCreator.writeV84MobMoveExtras} and ticket 32.
 */
class MoveMonsterPacketTest {

    private static final int MOVEMENT_LENGTH = 8;

    @Test
    void bodyCarriesTheV84MobMoveExtras() {
        InPacket movement = Packets.buildInPacket(p -> p.writeBytes(new byte[MOVEMENT_LENGTH]));

        Packet packet = PacketCreator.moveMonster(0x11223344, false, -1, 7, 3, 0x0102,
                new Point(0x0A0B, 0x0C0D), movement, MOVEMENT_LENGTH);

        byte[] body = withoutOpcode(packet.getBytes());
        byte[] head = new byte[]{
                0x44, 0x33, 0x22, 0x11,     // uniqueId
                0,                          // bNotForceLandingWhenDiscard
                0,                          // bNextAttackPossible
                (byte) 0xFF,                // bLeft (raw activity)
                7, 3, 0x02, 0x01,           // sEffect.m_Data: skillId, skillLevel, pOption
        };

        if (ServerConstants.VERSION >= 84) {
            assertArrayEquals(concat(head, new byte[8]), firstBytes(body, head.length + 8),
                    "v84 must write both count-prefixed blocks before the movement body");
        } else {
            assertArrayEquals(head, firstBytes(body, head.length),
                    "v83 must go straight from the skill data to the movement body");
        }

        int extras = ServerConstants.VERSION >= 84 ? 8 : 0;
        assertEquals(head.length + extras + 4 + MOVEMENT_LENGTH, body.length,
                "head + v84 extras + start position + movement body");
    }

    private static byte[] withoutOpcode(byte[] packet) {
        byte[] body = new byte[packet.length - 2];
        System.arraycopy(packet, 2, body, 0, body.length);
        return body;
    }

    private static byte[] firstBytes(byte[] src, int length) {
        byte[] out = new byte[length];
        System.arraycopy(src, 0, out, 0, length);
        return out;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
