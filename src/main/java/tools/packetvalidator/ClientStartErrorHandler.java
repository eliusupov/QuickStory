package tools.packetvalidator;

import client.Client;
import constants.net.ServerConstants;
import net.PacketHandler;
import net.packet.InPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handles {@code CLIENT_START_ERROR} (recv {@code 0x19}) - the client's own crash log, uploaded in
 * plain text on the connect <em>after</em> the crash. Previously unregistered, so it landed in the
 * debug hex dump and nowhere else.
 *
 * <p>Entries for the version we are currently serving ({@link ServerConstants#VERSION}) are logged at
 * WARN with a banner; historical entries from other versions are noise and go to DEBUG.
 *
 * <p>The upload is cumulative, so a plain log would re-scream the entire history on every reconnect.
 * Entries are therefore de-duplicated by exact text against a bounded LRU set.
 */
public class ClientStartErrorHandler implements PacketHandler {
    private static final Logger log = LoggerFactory.getLogger(ClientStartErrorHandler.class);

    static final int SEEN_LIMIT = 512;

    // ponytail: dedupe by exact entry text, LRU-bounded. Entries carry no timestamp, so two identical
    // crashes are indistinguishable and the second is suppressed - acceptable, the first already told
    // us where to look. Upgrade path if that ever matters: key by (client address, entry index).
    private static final Set<String> seen = newSeenSet();

    /** Package-private so {@code ClientStartErrorHandlerTest} can prove the bound actually holds. */
    static Set<String> newSeenSet() {
        return Collections.synchronizedSet(
                Collections.newSetFromMap(new LinkedHashMap<>(SEEN_LIMIT, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                        return size() > SEEN_LIMIT;
                    }
                }));
    }

    /** This packet is unauthenticated, so cap how much noise one sender can put in the log. */
    private static final int MAX_LOGGED_PER_PACKET = 20;

    /**
     * Dedupe keys are truncated: a real entry is ~135 chars, but an unparsed line can be the whole
     * 32 KB the length prefix allows, and 512 of those retained would be 16 MB of unauthenticated
     * garbage. 256 chars is well past where two real entries can still differ.
     */
    private static final int MAX_KEY_LENGTH = 256;

    @Override
    public void handlePacket(InPacket p, Client c) {
        // readString() takes a SIGNED short length; a hostile client can make it negative or
        // longer than the remaining buffer. Both would throw out of here - just ignore the packet.
        if (p.available() < 2) {
            return;
        }
        String blob;
        try {
            blob = p.readString();
        } catch (RuntimeException e) {
            log.debug("Malformed CLIENT_START_ERROR from {}", c.getRemoteAddress());
            return;
        }

        List<ClientCrashReport> entries = ClientCrashReport.parseAll(blob);

        int fresh = 0;
        for (ClientCrashReport entry : entries) {
            String key = entry.raw().length() > MAX_KEY_LENGTH
                    ? entry.raw().substring(0, MAX_KEY_LENGTH) : entry.raw();
            if (!seen.add(key)) {
                continue;
            }
            fresh++;
            if (fresh > MAX_LOGGED_PER_PACKET) {
                continue;
            }
            if (entry.version() == ServerConstants.VERSION) {
                log.warn("*** CLIENT CRASH REPORT (current version v{}) *** {}",
                        ServerConstants.VERSION, entry.describe());
            } else {
                log.debug("Older client crash report (not v{}): {}", ServerConstants.VERSION, entry.describe());
            }
        }

        if (fresh > 0) {
            log.info("Client crash log from {}: {} entries uploaded, {} new", c.getRemoteAddress(),
                    entries.size(), fresh);
        }
    }

    @Override
    public boolean validateState(Client c) {
        return true; // uploaded on the login server before the account logs in
    }
}
