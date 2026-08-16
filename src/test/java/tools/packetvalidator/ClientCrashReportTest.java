package tools.packetvalidator;

import io.netty.buffer.Unpooled;
import net.packet.ByteBufInPacket;
import net.packet.InPacket;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fixture is the real CLIENT_START_ERROR (recv 0x19) blob captured from the v84 client on
 * 2026-xx-xx, verbatim from tools/v84/cutover-server.log line 16 (opcode 0x19, string length
 * 0x067B = 1659, 12 CRLF-separated entries + a trailing empty line).
 */
class ClientCrashReportTest {

    private static final String E83 =
            "ver(83), CharacterName(), WorldID(-1), ChID(-1), FieldID(-1), "
                    + "ZException (error code : 11001 (No such host is known.)) source((null))";
    private static final String E84_NOCHR =
            "ver(84), CharacterName(), WorldID(-1), ChID(-1), FieldID(-1), "
                    + "ZException (error code : 38 (Reached the end of the file.)) source((null))";
    private static final String E84_UGUUH =
            "ver(84), CharacterName(uguuh), WorldID(0), ChID(0), FieldID(40000), "
                    + "ZException (error code : 38 (Reached the end of the file.)) source((null))";

    /** Exactly the 12 entries + trailing CRLF the client actually uploaded. */
    private static final String REAL_BLOB = String.join("\r\n",
            E83, E84_NOCHR, E83, E83, E83, E83, E84_NOCHR, E84_NOCHR, E84_NOCHR,
            E84_UGUUH, E84_UGUUH, E84_UGUUH) + "\r\n";

    @Test
    void parsesTheRealCapturedBlob() {
        List<ClientCrashReport> entries = ClientCrashReport.parseAll(REAL_BLOB);

        assertEquals(12, entries.size());
        assertTrue(entries.stream().allMatch(ClientCrashReport::parsed), "every real entry must parse");

        ClientCrashReport last = entries.get(11);
        assertEquals(84, last.version());
        assertEquals("uguuh", last.character());
        assertEquals(0, last.worldId());
        assertEquals(0, last.channelId());
        assertEquals(40000, last.mapId());
        assertEquals(38, last.errorCode());
        assertEquals("Reached the end of the file.", last.errorText());
        assertEquals("(null)", last.source());
        assertTrue(last.isBufferUnderrun());
        assertTrue(last.describe().contains("map 40000"));
        assertTrue(last.describe().contains("TOO SHORT"));

        ClientCrashReport first = entries.getFirst();
        assertEquals(83, first.version());
        assertEquals(11001, first.errorCode());
        assertFalse(first.isBufferUnderrun(), "11001 is a DNS failure, not an under-read");
        assertFalse(first.describe().contains("TOO SHORT"));
        assertEquals(-1, first.mapId());
    }

    @Test
    void countsPerVersionSoV83NoiseIsSeparable() {
        List<ClientCrashReport> entries = ClientCrashReport.parseAll(REAL_BLOB);

        assertEquals(5, entries.stream().filter(e -> e.version() == 83).count());
        assertEquals(7, entries.stream().filter(e -> e.version() == 84).count());
        assertEquals(7, entries.stream().filter(ClientCrashReport::isBufferUnderrun).count());
        assertEquals(3, entries.stream().filter(e -> e.mapId() == 40000).count());
    }

    @Test
    void readsOffTheWireExactlyAsTheClientFramesIt() {
        // opcode 0x19 then a length-prefixed ASCII string - the real framing.
        byte[] body = REAL_BLOB.getBytes(StandardCharsets.US_ASCII);
        InPacket p = new ByteBufInPacket(Unpooled.buffer()
                .writeShortLE(0x19)
                .writeShortLE(body.length)
                .writeBytes(body));

        assertEquals(0x19, p.readShort());
        assertEquals(1659, body.length, "matches the captured 0x067B length prefix");
        assertEquals(12, ClientCrashReport.parseAll(p.readString()).size());
    }

    @Test
    void garbageIsKeptNotDropped() {
        List<ClientCrashReport> entries = ClientCrashReport.parseAll("total garbage\r\n" + E84_UGUUH);

        assertEquals(2, entries.size());
        assertFalse(entries.getFirst().parsed());
        assertEquals("total garbage", entries.getFirst().raw());
        assertTrue(entries.get(1).parsed());
    }

    @Test
    void emptyAndNullAreSafe() {
        assertTrue(ClientCrashReport.parseAll(null).isEmpty());
        assertTrue(ClientCrashReport.parseAll("").isEmpty());
        assertTrue(ClientCrashReport.parseAll("\r\n\r\n").isEmpty());
    }
}
