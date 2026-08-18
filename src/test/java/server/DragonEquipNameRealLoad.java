package server;

import client.Character;
import client.Job;
import client.inventory.Equip;
import constants.inventory.ItemConstants;
import net.server.Server;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import tools.DatabaseConnection;

import java.sql.SQLException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * The dragon equips (1942000/1952000/1962000/1972000 and their 001/002 tiers): the names exist at
 * {@code Eqp/Dragon/<id>} but {@code getStringData()} routed all of 1900000-2000000 to
 * {@code Eqp/Taming}, so every one of the 12 resolved null. Fixed by carving out 1940000-1980000
 * before the Taming range in {@link ItemInformationProvider#getStringData}.
 *
 * <p>Wearing them was the second half. All twelve carry {@code info/islot = "Tm"} - the taming-mount
 * string - so {@code EquipSlot.getFromTextSlot} sent them to the mount slot -18 and
 * {@code canWearEquipment} refused the -1000 the client asked for. The four dragon body parts come
 * off the item id instead ({@link ItemConstants#getDragonSlot}).
 */
class DragonEquipNameRealLoad {

    /** id -> Eqp/Dragon name, per wz/String.wz/Eqp.img.xml and the pristine v84 archive. */
    private static final Map<Integer, String> DRAGON_NAMES = Map.ofEntries(
            Map.entry(1942000, "Silver Mask"), Map.entry(1942001, "Gold Mask"), Map.entry(1942002, "Reverse Mask"),
            Map.entry(1952000, "Silver Pendant"), Map.entry(1952001, "Gold Pendant"), Map.entry(1952002, "Reverse Pendant"),
            Map.entry(1962000, "Silver Wings"), Map.entry(1962001, "Gold Wings"), Map.entry(1962002, "Reverse Wings"),
            Map.entry(1972000, "Silver Tail"), Map.entry(1972001, "Gold Tail"), Map.entry(1972002, "Reverse Tail"));

    /** id -> equipped-inventory position, from the client's own itemid/10000 jump table. */
    private static final Map<Integer, Short> DRAGON_SLOTS = Map.ofEntries(
            Map.entry(1942000, (short) -1000), Map.entry(1942001, (short) -1000), Map.entry(1942002, (short) -1000),
            Map.entry(1952000, (short) -1001), Map.entry(1952001, (short) -1001), Map.entry(1952002, (short) -1001),
            Map.entry(1962000, (short) -1002), Map.entry(1962001, (short) -1002), Map.entry(1962002, (short) -1002),
            Map.entry(1972000, (short) -1003), Map.entry(1972001, (short) -1003), Map.entry(1972002, (short) -1003));

    /** Same pattern as MasteryBookJobMatchRealLoad: no database in a test JVM. */
    @BeforeAll
    static void bootTheProviderWithoutADatabase() {
        try (MockedStatic<DatabaseConnection> db = Mockito.mockStatic(DatabaseConnection.class)) {
            db.when(DatabaseConnection::getConnection).thenThrow(new SQLException("no database in tests"));
            ItemInformationProvider.getInstance();
        }
    }

    private static Character evan() {
        Character chr = mock(Character.class);
        when(chr.getJob()).thenReturn(Job.EVAN2);
        when(chr.getLevel()).thenReturn(120);   // the 002 tier is reqLevel 120
        when(chr.getTotalInt()).thenReturn(60);
        when(chr.getName()).thenReturn("evan2");
        return chr;
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

    /**
     * The premise of the whole fix: the wz says "Tm" for all twelve, so the slot cannot come from
     * the string. If a future wz merge ever gives them distinct islots, this fails and the id-keyed
     * mapping should be reconsidered.
     */
    @Test
    void allTwelveShareTheTamingMountIslotAndSoNeedTheIdKeyedSlot() {
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        for (Map.Entry<Integer, Short> e : DRAGON_SLOTS.entrySet()) {
            assertEquals("Tm", ii.getEquipmentSlot(e.getKey()), "islot of " + e.getKey());
            assertEquals(e.getValue().shortValue(), ItemConstants.getDragonSlot(e.getKey()),
                    "slot of " + e.getKey());
            assertTrue(ItemConstants.isDragonItem(e.getKey()));
        }
        assertFalse(ItemConstants.isDragonItem(1902000));   // Hog, a real "Tm" mount
    }

    @Test
    void anEvanMayWearEachDragonPieceInItsOwnSlotAndNoOther() {
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        try (MockedStatic<Server> server = mockStatic(Server.class)) {
            server.when(Server::getInstance).thenReturn(mock(Server.class));
            Character chr = evan();

            for (Map.Entry<Integer, Short> e : DRAGON_SLOTS.entrySet()) {
                short slot = e.getValue();
                assertTrue(ii.canWearEquipment(chr, (Equip) ii.getEquipById(e.getKey()), slot),
                        "evan into " + slot + " with " + e.getKey());

                short wrong = (short) (slot == -1000 ? -1001 : -1000);
                assertFalse(ii.canWearEquipment(chr, (Equip) ii.getEquipById(e.getKey()), wrong),
                        e.getKey() + " must not fit " + wrong);
                assertFalse(ii.canWearEquipment(chr, (Equip) ii.getEquipById(e.getKey()), (short) -18),
                        e.getKey() + " must not fit the mount slot its islot names");
            }
        }
    }

    @Test
    void aMagicianWhoIsNotAnEvanMayNotWearThem() {
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        try (MockedStatic<Server> server = mockStatic(Server.class)) {
            server.when(Server::getInstance).thenReturn(mock(Server.class));
            Character cleric = mock(Character.class);
            when(cleric.getJob()).thenReturn(Job.CLERIC);   // reqJob 2 is "magician", which fits a Cleric
            when(cleric.getLevel()).thenReturn(120);
            when(cleric.getTotalInt()).thenReturn(200);
            when(cleric.getName()).thenReturn("notevan");

            for (Map.Entry<Integer, Short> e : DRAGON_SLOTS.entrySet()) {
                assertFalse(ii.canWearEquipment(cleric, (Equip) ii.getEquipById(e.getKey()), e.getValue()),
                        "cleric wearing " + e.getKey());
            }
        }
    }

    /** The stats {@code recalcEquipStats} sums, on the piece the owner actually holds. */
    @Test
    void silverMaskCarriesItsWzStats() {
        Equip mask = (Equip) ItemInformationProvider.getInstance().getEquipById(1942000);
        assertEquals(2, mask.getInt());
        assertEquals(5, mask.getWdef());
        assertEquals(8, mask.getMdef());
        assertEquals(3, mask.getUpgradeSlots());
    }
}
