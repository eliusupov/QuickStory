package server;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import tools.DatabaseConnection;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Ticket 56 (rows R04/R45/R48): items that had an {@code Item.wz}/{@code Character.wz} image and no
 * {@code String.wz} entry, so {@code ItemInformationProvider.getName} returned null and the slot
 * drew empty. Every value here is copied from the pristine v84 carve at
 * {@code D:\games\MapleStory\Server\porting-resources\wz-data\v84\String.wz}, read with
 * {@code WzPeek dump} under a forced UTF-8 console. Trailing spaces and the Korean value are the
 * carve's own; do not "clean" them.
 *
 * <p>1702248 and 1702254 are deliberately absent: the carve has no node for either id, not under
 * {@code Eqp.img/Eqp/Weapon} where {@code getStringData} routes 1300000-1799999, so by the parity
 * rule they stay nameless. Same shape as EvanMedalNameRealLoad (commit 8c24b6fa5).
 */
class V84MissingItemNameRealLoad {

    /** Same pattern as EvanMedalNameRealLoad: no database in a test JVM. */
    @BeforeAll
    static void bootTheProviderWithoutADatabase() {
        try (MockedStatic<DatabaseConnection> db = Mockito.mockStatic(DatabaseConnection.class)) {
            db.when(DatabaseConnection::getConnection).thenThrow(new SQLException("no database in tests"));
            ItemInformationProvider.getInstance();
        }
    }

    private static String name(int itemId) {
        return ItemInformationProvider.getInstance().getName(itemId);
    }

    /**
     * R04: quest 22572 hands this over and takes it back; it drew as an empty slot. The {@code desc}
     * leaf went in beside it - there is no provider accessor for desc, so the XML diff is its proof.
     */
    @Test
    void johnsMapResolves() {
        assertEquals("John's Map", name(4032526));
    }

    /** The merge added a sibling under Etc; the nine that were already there still resolve. */
    @Test
    void theNineEtcSiblingsStillResolve() {
        for (int itemId : new int[]{4032520, 4032521, 4032522, 4032523, 4032524, 4032525,
                4032527, 4032528, 4032529}) {
            assertNotNull(name(itemId), "Etc sibling " + itemId + " lost its name");
        }
    }

    /** R45: scripts/quest/22002.js:44 gains this cap on a live Evan quest. */
    @Test
    void strawHatResolves() {
        assertEquals("Straw Hat", name(1003028));
    }

    /** R48: the ten v84-new equips the carve names. Trailing spaces are the carve's. */
    @Test
    void theTenV84NewEquipsResolve() {
        assertEquals("Former Hero Female Face", name(1003029));
        assertEquals("Former Hero Male Face", name(1003030));
        assertEquals("\uc21c\ub85d\uc758 \ubfd4", name(1003043));
        assertEquals("Checkered Shirt ", name(1042180));
        assertEquals("Former Hero Robe", name(1052226));
        assertEquals("Denim Shorts ", name(1060138));
        assertEquals("Denim Skirt ", name(1061160));
        assertEquals("Black Boots ", name(1072418));
        assertEquals("Freud's Shoes", name(1072425));
        assertEquals("Freud's Gloves", name(1082261));
    }
}
