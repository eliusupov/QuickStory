package server.maps;

import client.Character;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import server.ItemInformationProvider;
import server.life.MonsterDropEntry;
import tools.DatabaseConnection;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A quest drop must not become a map object when nobody on the map needs it.
 *
 * <p>{@code spawnDrop} gates only VISIBILITY - its ranged lambda sends the drop packet to a client
 * only when {@code needQuestItem} holds for that client - while the {@link MapItem} itself was
 * spawned unconditionally. So a quest item the killer did not need lay on the ground invisible,
 * and every later ranged-object update re-evaluates {@code needQuestItem}
 * (MapleMap.java:1250/1260/1291, {@link MapItem#sendSpawnData}), which made the pile appear
 * retroactively the instant the quest was accepted. Reported from live play on a fresh Evan. The
 * intent was already written down in {@code db/data/155-evan-tutorial-drop-data.sql:54-57}:
 * "needQuestItem() only lets the item spawn while 22004 is in progress".
 *
 * <p>The gate cannot be the killer alone: the killer's kill is what drops a party member's quest
 * item for them (the {@code otherQuest} list, "thanks Articuno, Limit, Rohenn"), so both
 * directions are pinned below.
 *
 * <p>Fixture is the real drop_data row {@code (130100 Stump, 4031773, questid 2145)} cited in
 * {@code 155-evan-tutorial-drop-data.sql:44}; {@link #theFixtureIsAQuestItem()} fails loudly if
 * that stops being an {@code info/quest=1} item in wz.
 */
class QuestDropSpawnGateRealLoad {

    private static final int ITEM = 4031773;    // Stump Research collectible
    private static final short QUEST = 2145;

    /** Same pattern as EvanMedalNameRealLoad: no database in a test JVM. */
    @BeforeAll
    static void bootTheProviderWithoutADatabase() {
        try (MockedStatic<DatabaseConnection> db = Mockito.mockStatic(DatabaseConnection.class)) {
            db.when(DatabaseConnection::getConnection).thenThrow(new SQLException("no database in tests"));
            ItemInformationProvider.getInstance();
        }
    }

    @Test
    void theFixtureIsAQuestItem() {
        assertTrue(ItemInformationProvider.getInstance().isQuestItem(ITEM),
                "the whole sort only reaches the quest branch for an info/quest=1 item");
    }

    @Test
    void doesNotDropWhenNobodyOnTheMapNeedsIt() {
        assertEquals(0, otherQuestDrops().size(),
                "an item nobody needs is an invisible object that appears when the quest is accepted");
    }

    @Test
    void stillDropsForTheOnePartyMemberWhoNeedsIt() {
        assertEquals(1, otherQuestDrops(true).size(),
                "the killer's kill is what drops a party member's quest item for them");
    }

    /** Sorts one quest drop the KILLER does not need, past mapmates with the given needs. */
    private static List<MonsterDropEntry> otherQuestDrops(boolean... mapmateNeeds) {
        Character killer = mock(Character.class);   // needQuestItem defaults false: quest not accepted
        List<Character> players = new ArrayList<>(List.of(killer));
        for (boolean needs : mapmateNeeds) {
            Character mate = mock(Character.class);
            when(mate.needQuestItem(QUEST, ITEM)).thenReturn(needs);
            players.add(mate);
        }

        MapleMap map = mock(MapleMap.class);
        when(map.getAllPlayers()).thenReturn(players);
        when(killer.getMap()).thenReturn(map);

        List<MonsterDropEntry> plain = new ArrayList<>(), visibleQuest = new ArrayList<>(), otherQuest = new ArrayList<>();
        MapleMap.sortDropEntries(List.of(new MonsterDropEntry(ITEM, 80000, 1, 1, QUEST)),
                plain, visibleQuest, otherQuest, killer);

        assertEquals(0, plain.size() + visibleQuest.size(), "the killer needs it in neither case");
        return otherQuest;
    }
}
