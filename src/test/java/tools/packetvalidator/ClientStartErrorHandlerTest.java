package tools.packetvalidator;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CLIENT_START_ERROR arrives before the account logs in, so the dedupe set is fed entirely by
 * unauthenticated input. It has to stay bounded no matter what a sender does.
 */
class ClientStartErrorHandlerTest {

    @Test
    void seenSetStaysBoundedUnderUnauthenticatedInput() {
        Set<String> seen = ClientStartErrorHandler.newSeenSet();

        for (int i = 0; i < ClientStartErrorHandler.SEEN_LIMIT * 20; i++) {
            seen.add("crash-" + i);
        }

        assertEquals(ClientStartErrorHandler.SEEN_LIMIT, seen.size(),
                "an attacker sending unique entries must not grow this without limit");
    }

    @Test
    void seenSetSuppressesRepeatsAndReportsFirstSighting() {
        Set<String> seen = ClientStartErrorHandler.newSeenSet();
        String entry = "ver(84), CharacterName(uguuh), WorldID(0), ChID(0), FieldID(40000), "
                + "ZException (error code : 38 (Reached the end of the file.)) source((null))";

        assertTrue(seen.add(entry), "first sighting must be reported");
        assertFalse(seen.add(entry), "a reconnect re-uploads the whole history; do not re-alarm");
    }

    @Test
    void recentEntriesSurviveEvictionOfOlderOnes() {
        Set<String> seen = ClientStartErrorHandler.newSeenSet();
        seen.add("oldest");
        for (int i = 0; i < ClientStartErrorHandler.SEEN_LIMIT - 1; i++) {
            seen.add("filler-" + i);
        }
        seen.add("oldest");  // access-ordered: re-adding moves it to the young end

        for (int i = 0; i < 10; i++) {
            seen.add("newer-" + i);
        }

        assertFalse(seen.add("newer-9"), "the most recent entries must still be deduped");
        assertFalse(seen.add("oldest"), "a re-touched entry must not be the first one evicted");
    }
}
