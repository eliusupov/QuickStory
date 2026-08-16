package server.partyquest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The room index handed to Channel#initMonsterCarnival must be the same 0-based field index that
 * NPCConversationManager#fieldTaken and the CPQ NPC scripts use. It was one too high, so field 0
 * was never reserved and field N+1 reported "full" whenever field N was in progress.
 */
class MonsterCarnivalRoomTest {

    @Test
    void cpq1ArenaMapsMapToZeroBasedFields() {
        // scripts/npc/2042000.js offers selections 0..5; cpqLobby(f) uses lobby 980000100 + f * 100
        // and startCPQ is handed that lobby id + 1.
        for (int field = 0; field <= 5; field++) {
            int arenaMapId = 980000100 + field * 100 + 1;
            assertEquals(field, MonsterCarnival.roomOf(arenaMapId, true),
                    "CPQ1 arena " + arenaMapId + " belongs to field " + field);
        }
    }

    @Test
    void cpq2ArenaMapsMapToZeroBasedFields() {
        // scripts/npc/2042005.js offers selections 0..2; cpqLobby2(f) uses 980031000 + f * 1000.
        for (int field = 0; field <= 2; field++) {
            int arenaMapId = 980031000 + field * 1000 + 1;
            assertEquals(field, MonsterCarnival.roomOf(arenaMapId, false),
                    "CPQ2 arena " + arenaMapId + " belongs to field " + field);
        }
    }

}
