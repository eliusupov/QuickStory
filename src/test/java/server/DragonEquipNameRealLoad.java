package server;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import tools.DatabaseConnection;

import java.sql.SQLException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The dragon equips (1942000/1952000/1962000/1972000 and their 001/002 tiers): the names exist at
 * {@code Eqp/Dragon/<id>} but {@code getStringData()} routed all of 1900000-2000000 to
 * {@code Eqp/Taming}, so every one of the 12 resolved null. Fixed by carving out 1940000-1980000
 * before the Taming range in {@link ItemInformationProvider#getStringData}.
 *
 * <p>Wearing them is a separate, harder problem ({@code EquipSlot.java} has no dragon-equip slot)
 * and is out of scope here - this only pins the name lookup.
 */
class DragonEquipNameRealLoad {

    /** id -> Eqp/Dragon name, per wz/String.wz/Eqp.img.xml and the pristine v84 archive. */
    private static final Map<Integer, String> DRAGON_NAMES = Map.ofEntries(
            Map.entry(1942000, "Silver Mask"), Map.entry(1942001, "Gold Mask"), Map.entry(1942002, "Reverse Mask"),
            Map.entry(1952000, "Silver Pendant"), Map.entry(1952001, "Gold Pendant"), Map.entry(1952002, "Reverse Pendant"),
            Map.entry(1962000, "Silver Wings"), Map.entry(1962001, "Gold Wings"), Map.entry(1962002, "Reverse Wings"),
            Map.entry(1972000, "Silver Tail"), Map.entry(1972001, "Gold Tail"), Map.entry(1972002, "Reverse Tail"));

    /** Same pattern as MasteryBookJobMatchRealLoad: no database in a test JVM. */
    @BeforeAll
    static void bootTheProviderWithoutADatabase() {
        try (MockedStatic<DatabaseConnection> db = Mockito.mockStatic(DatabaseConnection.class)) {
            db.when(DatabaseConnection::getConnection).thenThrow(new SQLException("no database in tests"));
            ItemInformationProvider.getInstance();
        }
    }

    @Test
    void allTwelveDragonEquipsResolveTheirEqpDragonName() {
        for (Map.Entry<Integer, String> e : DRAGON_NAMES.entrySet()) {
            assertEquals(e.getValue(), ItemInformationProvider.getInstance().getName(e.getKey()),
                    "item " + e.getKey());
        }
    }

    /** Blast-radius check: the new Dragon range must not steal any real Taming id. */
    @Test
    void tamingMountsAreUnaffectedByTheNewDragonRoute() {
        assertEquals("Hog", ItemInformationProvider.getInstance().getName(1902000));
        assertEquals("Silver Mane", ItemInformationProvider.getInstance().getName(1902001));
        assertEquals("Saddle", ItemInformationProvider.getInstance().getName(1912000));
    }
}
