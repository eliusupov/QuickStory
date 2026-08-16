package tools;

import client.inventory.Item;
import constants.net.ServerConstants;
import net.packet.Packet;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import server.maps.MapItem;
import server.maps.MapObject;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Pins the wire length of DROP_ITEM_FROM_MAPOBJECT ({@code CDropPool::OnDropEnterField}).
 *
 * <p>v84 ends that decoder with two unconditional {@code Decode1} where v83 ends with one
 * (v83 localhome.exe @0x506385 vs v84 @0x50F20C + @0x50F21D, read off the live process image),
 * so a v83-shaped drop packet under-runs the v84 client by exactly one byte and it throws
 * {@code ZException (error code : 38)} - the 0x26 that {@code CInPacket::Decode1} stores at
 * v84 0x4066C9. Every monster kill that drops something hits this. See
 * {@code PacketCreator.writeV84DropSpawnExtra} and ticket 32.
 */
class DropSpawnPacketTest {

    @Test
    void itemDropCarriesTheV84TrailingByte() {
        byte[] body = withoutOpcode(PacketCreator.dropItemFromMapObject(
                null, itemDrop(), new Point(0x0102, 0x0304), new Point(0x0506, 0x0708),
                (byte) 1, (short) 0x090A).getBytes());

        byte[] v83 = new byte[]{
                1,                              // mod
                0x44, 0x33, 0x22, 0x11,         // drop object id
                0,                              // isMeso
                (byte) 0xBA, 0x7A, 0x3D, 0x00,  // item id 4029114
                0x0D, 0x00, 0x00, 0x00,         // clientside owner id
                2,                              // drop type
                0x06, 0x05, 0x08, 0x07,         // dropTo
                (byte) 0x99, (byte) 0x88, 0x77, 0x66,   // dropper object id
                0x02, 0x01, 0x04, 0x03,         // dropFrom (mod != 2)
                0x0A, 0x09,                     // delay
                0x00, (byte) 0x80, 0x05, (byte) 0xBB, 0x46, (byte) 0xE6, 0x17, 0x02, // DEFAULT_TIME
                1,                              // pet EQP pickup (!playerDrop)
        };

        if (ServerConstants.VERSION >= 84) {
            assertArrayEquals(concat(v83, new byte[]{0}), body,
                    "v84 CDropPool::OnDropEnterField reads one more trailing byte than v83");
        } else {
            assertArrayEquals(v83, body, "v83 must stay byte-exact");
        }
    }

    @Test
    void mapItemUpdateCarriesTheV84TrailingByte() {
        int body = PacketCreator.updateMapItemObject(itemDrop(), true).getBytes().length - 2;
        // mod, oid, isMeso, itemId, owner, dropType, pos, dropperId, expiration, playerDrop
        assertEquals(1 + 4 + 1 + 4 + 4 + 1 + 4 + 4 + 8 + 1 + (ServerConstants.VERSION >= 84 ? 1 : 0),
                body, "same client function, same missing byte");
    }

    private static MapItem itemDrop() {
        MapObject dropper = Mockito.mock(MapObject.class);
        when(dropper.getObjectId()).thenReturn(0x66778899);

        Item item = Mockito.mock(Item.class);
        when(item.getExpiration()).thenReturn(-1L);

        MapItem drop = Mockito.mock(MapItem.class);
        when(drop.getObjectId()).thenReturn(0x11223344);
        when(drop.getMeso()).thenReturn(0);
        when(drop.getItemId()).thenReturn(4029114);
        when(drop.getClientsideOwnerId()).thenReturn(13);
        when(drop.getDropType()).thenReturn((byte) 2);
        when(drop.getDropper()).thenReturn(dropper);
        when(drop.getItem()).thenReturn(item);
        when(drop.getPosition()).thenReturn(new Point(0x0506, 0x0708));
        when(drop.isPlayerDrop()).thenReturn(false);
        return drop;
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
