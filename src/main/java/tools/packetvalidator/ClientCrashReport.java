package tools.packetvalidator;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One line of the client's own crash log, as uploaded in {@code CLIENT_START_ERROR} (recv {@code 0x19}).
 *
 * <p>The client keeps a cumulative, plain-text crash history and re-uploads the <em>whole thing</em> on
 * every connect. Each entry looks exactly like this (one per CRLF-separated line):
 *
 * <pre>
 * ver(84), CharacterName(uguuh), WorldID(0), ChID(0), FieldID(40000), ZException (error code : 38 (Reached the end of the file.)) source((null))
 * </pre>
 *
 * <p><b>Error code 38 is the one that matters.</b> {@code CInPacket::Decode1} at v84 {@code 0x4066C9}
 * does {@code mov dword ptr [ebp-4], 0x26} (0x26 = 38) and throws when the read runs past the end of
 * the buffer. So error 38 means: <em>the server sent a packet that was too short and the client ran
 * off the end of it</em>. {@code FieldID} is the map the character was on when it happened, which is
 * the single most useful field for narrowing down which packet was malformed.
 */
public record ClientCrashReport(int version, String character, int worldId, int channelId, int mapId,
                                int errorCode, String errorText, String source, String raw) {

    /** Under-read: the client hit the end of the buffer mid-decode. See class javadoc. */
    public static final int ERROR_BUFFER_UNDERRUN = 38;

    // Digit runs are capped at 9 so Integer.parseInt below cannot overflow on hostile input - this
    // packet arrives before the account logs in and is entirely attacker-controlled.
    private static final Pattern ENTRY = Pattern.compile(
            "ver\\((-?\\d{1,9})\\), CharacterName\\(([^)]*)\\), WorldID\\((-?\\d{1,9})\\), "
                    + "ChID\\((-?\\d{1,9})\\), FieldID\\((-?\\d{1,9})\\), "
                    + "ZException \\(error code : (-?\\d{1,9}) \\((.*)\\)\\) source\\((.*)\\)");

    /**
     * Splits the uploaded blob into entries and parses each one. Lines that do not match the known
     * shape are returned with {@code version == -1} and everything else zeroed, so nothing is silently
     * dropped - {@link #parsed()} tells the two apart.
     */
    public static List<ClientCrashReport> parseAll(String blob) {
        List<ClientCrashReport> out = new ArrayList<>();
        if (blob == null) {
            return out;
        }
        for (String line : blob.split("\r\n|\n")) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            Matcher m = ENTRY.matcher(line);
            if (m.matches()) {
                out.add(new ClientCrashReport(
                        Integer.parseInt(m.group(1)), m.group(2),
                        Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5)),
                        Integer.parseInt(m.group(6)), m.group(7), m.group(8), line));
            } else {
                out.add(new ClientCrashReport(-1, "", 0, 0, 0, 0, "", "", line));
            }
        }
        return out;
    }

    public boolean parsed() {
        return version != -1;
    }

    public boolean isBufferUnderrun() {
        return errorCode == ERROR_BUFFER_UNDERRUN;
    }

    /** Human-readable one-liner for the server log. */
    public String describe() {
        if (!parsed()) {
            return "unparsed crash entry: " + raw;
        }
        String where = mapId >= 0 ? ("map " + mapId) : "no map (not in game yet)";
        String who = character.isEmpty() ? "no character" : character;
        String meaning = isBufferUnderrun()
                ? " -- THE SERVER SENT A PACKET THAT WAS TOO SHORT; the client read past the end of it"
                : "";
        return "client v" + version + " crashed: " + who + " @ " + where
                + " (world " + worldId + ", ch " + channelId + ")"
                + " error " + errorCode + " (" + errorText + ")" + meaning;
    }
}
