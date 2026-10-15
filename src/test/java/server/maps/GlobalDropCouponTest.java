package server.maps;

import client.Character;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.ItemInformationProvider;
import server.life.Monster;
import server.life.MonsterGlobalDropEntry;
import tools.Randomizer;
import tools.DatabaseConnection;

import java.awt.Point;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class GlobalDropCouponTest {
    @BeforeAll
    static void initializeProviderWithoutDatabase() {
        try (MockedStatic<DatabaseConnection> db = mockStatic(DatabaseConnection.class)) {
            db.when(DatabaseConnection::getConnection).thenThrow(new SQLException("no database in tests"));
            ItemInformationProvider.getInstance();
        }
    }

    @Test
    void doubleDropCouponAlsoAffectsGlobalMonsterDrops() throws Exception {
        Character player = mock(Character.class);
        when(player.getDropRate()).thenReturn(2);
        Monster monster = mock(Monster.class);
        Point position = new Point();
        MonsterGlobalDropEntry entry = new MonsterGlobalDropEntry(0, 4, -1, 1, 1, (short) 0);

        try (MockedStatic<Randomizer> random = mockStatic(Randomizer.class)) {
            random.when(() -> Randomizer.nextInt(999999)).thenReturn(5);

            Method drop = MapleMap.class.getDeclaredMethod("dropGlobalItemsFromMonsterOnMap",
                    List.class, Point.class, byte.class, int.class, byte.class, int.class,
                    Character.class, Monster.class, short.class);
            drop.setAccessible(true);
            drop.invoke(mock(MapleMap.class), List.of(entry), position, (byte) 2, 1, (byte) 0,
                    100, player, monster, (short) 0);
            assertEquals(0, position.x, "the same roll should fail at 1x");
            drop.invoke(mock(MapleMap.class), List.of(entry), position, (byte) 2, player.getDropRate(), (byte) 0,
                    100, player, monster, (short) 0);
        }

        assertEquals(137, position.x, "a chance of 4 should pass a roll of 5 at 2x drop rate");
    }
}
