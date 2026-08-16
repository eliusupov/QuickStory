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

    private static final int SEEN_LIMIT = 512;

    // ponytail: dedupe by exact entry text, LRU-bounded. Entries carry no timestamp, so two identical
    // crashes are indistinguishable and the second is suppressed - acceptable, the first already told
    // us where to look. Upgrade path if that ever matters: key by (client address, entry index).
    private static final Set<String> seen = Collections.synchronizedSet(
            Collections.newSetFromMap(new LinkedHashMap<>(SEEN_LIMIT, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > SEEN_LIMIT;
                }
            }));

    @Override
    public void handlePacket(InPacket p, Client c) {
        if (p.available() < 2) {
            return;
        }
        List<ClientCrashReport> entries = ClientCrashReport.parseAll(p.readString());

        int fresh = 0;
        for (ClientCrashReport entry : entries) {
            if (!seen.add(entry.raw())) {
                continue;
            }
            fresh++;
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
