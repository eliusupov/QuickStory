package tools.packetvalidator;

import io.netty.buffer.Unpooled;
import net.packet.ByteBufInPacket;
import net.packet.InPacket;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Everything here is driven by the real wire bytes in {@code tools/v84/client-start-error-0x19.hex} -
 * a verbatim CLIENT_START_ERROR (recv 0x19) uploaded by the live v84 client on 2026-08-17 00:01:38.
 *
 * <p>The capture is committed rather than cited by line number, because the server log it came from
 * rotates while the server runs: the same file that held this packet was rotated to a timestamped
 * name minutes later, which would have left the citation pointing at a different capture.
 */
class ClientCrashReportTest {

    private static final Path CAPTURE = Path.of("tools", "v84", "client-start-error-0x19.hex");

    private static final String E84_UGUUH =
            "ver(84), CharacterName(uguuh), WorldID(0), ChID(0), FieldID(40000), "
                    + "ZException (error code : 38 (Reached the end of the file.)) source((null))";

    /** The captured packet, opcode header included. */
    private static byte[] capturedPacket() {
        StringBuilder hex = new StringBuilder();
        try {
            for (String line : Files.readAllLines(CAPTURE, StandardCharsets.US_ASCII)) {
                if (!line.startsWith("#")) {
                    hex.append(line.trim());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    /** The crash-log string as the handler would read it off that packet. */
    private static String realBlob() {
        byte[] packet = capturedPacket();
        InPacket p = new ByteBufInPacket(Unpooled.wrappedBuffer(packet));
        assertEquals(0x19, p.readShort(), "captured packet must be CLIENT_START_ERROR");
        return p.readString();
    }

    private static final String REAL_BLOB = realBlob();

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
    void theCaptureIsTheOneTheTicketDescribes() {
        byte[] packet = capturedPacket();

        assertEquals(1663, packet.length, "captured wire packet size");
        assertEquals(0x19, packet[0] & 0xFF);
        assertEquals(0x067B, ((packet[3] & 0xFF) << 8) | (packet[2] & 0xFF), "length prefix, little-endian");
        assertEquals(1659, REAL_BLOB.getBytes(StandardCharsets.US_ASCII).length);
        assertEquals(3, ClientCrashReport.parseAll(REAL_BLOB).stream()
                .filter(e -> e.mapId() == 40000).count());
    }

    /** Pins the exact wording quoted in docs/work-plan/tickets/40-packet-error-detection.md. */
    @Test
    void logLinesReadTheWayTheTicketSaysTheyDo() {
        List<ClientCrashReport> entries = ClientCrashReport.parseAll(REAL_BLOB);

        assertEquals(3, entries.stream().map(ClientCrashReport::raw).distinct().count(),
                "the 12 uploaded entries are only 3 distinct crashes; dedupe reports 3, not 12");

        assertEquals("client v83 crashed: no character @ no map (not in game yet) (world -1, ch -1)"
                        + " error 11001 (No such host is known.)",
                entries.getFirst().describe());
        assertEquals("client v84 crashed: no character @ no map (not in game yet) (world -1, ch -1)"
                        + " error 38 (Reached the end of the file.)"
                        + " -- THE SERVER SENT A PACKET THAT WAS TOO SHORT; the client read past the end of it",
                entries.get(1).describe());
        assertEquals("client v84 crashed: uguuh @ map 40000 (world 0, ch 0)"
                        + " error 38 (Reached the end of the file.)"
                        + " -- THE SERVER SENT A PACKET THAT WAS TOO SHORT; the client read past the end of it",
                entries.get(11).describe());
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

    /** This packet arrives before the account logs in, so the body is entirely attacker-controlled. */
    @Test
    void hostileInputDoesNotThrowOrOverflow() {
        String huge = "9".repeat(40);
        // digit runs longer than 9 must fail the pattern rather than blow up Integer.parseInt
        assertFalse(ClientCrashReport.parseAll(
                        "ver(" + huge + "), CharacterName(x), WorldID(0), ChID(0), FieldID(0), "
                                + "ZException (error code : 38 (x)) source((null))")
                .getFirst().parsed());

        // the field-id slot is the one we act on; an overflowing value must not become a real map id
        assertFalse(ClientCrashReport.parseAll(
                        "ver(84), CharacterName(x), WorldID(0), ChID(0), FieldID(" + huge + "), "
                                + "ZException (error code : 38 (x)) source((null))")
                .getFirst().parsed());

        // 32767 is the largest length readString() can produce from a POSITIVE signed short; a
        // negative one throws inside readString and is handled by the caller, not here.
        assertEquals(1, ClientCrashReport.parseAll("x".repeat(32767)).size());
    }

    /**
     * The entry pattern ends in two greedy {@code (.*)} groups separated by literals, which is
     * quadratic on input crafted to keep re-matching them. This probe carries a VALID prefix so it
     * actually reaches those groups - a probe that fails on an earlier literal returns instantly and
     * proves nothing. Unguarded, the 44 KB case measured 0.46 s per line, pre-login and repeatable.
     */
    @Test
    void longLinesNeverReachTheQuadraticPartOfThePattern() {
        String validPrefix = "ver(84), CharacterName(x), WorldID(0), ChID(0), FieldID(0), "
                + "ZException (error code : 38 (";
        String evil = validPrefix + ")) source((".repeat(4000);
        assertTrue(evil.length() > 40_000, "probe must be big enough to matter");

        long start = System.nanoTime();
        List<ClientCrashReport> entries = ClientCrashReport.parseAll(evil);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertEquals(1, entries.size());
        assertFalse(entries.getFirst().parsed(), "over-long lines are rejected without matching");
        assertTrue(elapsedMs < 50, "must be gated on length, not matched; took " + elapsedMs + "ms");
    }

    @Test
    void entryCountIsBounded() {
        String line = "ver(84), CharacterName(x), WorldID(0), ChID(0), FieldID(0), "
                + "ZException (error code : 38 (x)) source((null))";
        List<ClientCrashReport> entries = ClientCrashReport.parseAll((line + "\r\n").repeat(5000));
        assertEquals(64, entries.size(), "one packet cannot make us allocate unboundedly");
    }
}
