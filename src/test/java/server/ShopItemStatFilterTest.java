package server;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ShopItemStatFilterTest {

    @Test
    void filterKeepsOrderAndTheOriginalRowsIncludingPriceAndPitch() {
        ShopItem lukOnly = new ShopItem((short) 1000, 2040319, 250_000, 0);
        ShopItem multiStat = new ShopItem((short) 1000, 2040211, 600_000, 7);
        ShopItem attackOnly = new ShopItem((short) 1000, 2044700, 70_000, 0);
        List<ShopItem> stock = List.of(lukOnly, multiStat, attackOnly);
        Map<Integer, Map<String, Integer>> stats = Map.of(
                2040319, Map.of("LUK", 1),
                2040211, Map.of("LUK", 1, "PAD", 1),
                2044700, Map.of("PAD", 1));

        List<ShopItem> filtered = Shop.filterItemsByStat(stock, "LUK", stats::get);

        assertEquals(2, filtered.size());
        assertSame(lukOnly, filtered.get(0));
        assertSame(multiStat, filtered.get(1));
        assertEquals(250_000, filtered.get(0).getPrice());
        assertEquals(7, filtered.get(1).getPitch());
    }

    @Test
    void shopSlotsRejectNegativeAndPastEndIndexes() {
        ShopItem item = new ShopItem((short) 1000, 2040319, 250_000, 0);
        List<ShopItem> stock = List.of(item);

        assertSame(item, Shop.itemAt(stock, (short) 0));
        assertNull(Shop.itemAt(stock, (short) -1));
        assertNull(Shop.itemAt(stock, (short) 1));
        assertNull(Shop.itemAt(stock, Short.MAX_VALUE));
    }

    @Test
    void exactWeaponFamilyFilterKeepsRowsAndDoesNotCollideWithSimilarNames() {
        ShopItem bow = new ShopItem((short) 1000, 2044500, 250_000, 0);
        ShopItem crossbow = new ShopItem((short) 1000, 2044600, 600_000, 7);
        ShopItem secondBow = new ShopItem((short) 1000, 2044501, 1_000_000, 0);
        List<ShopItem> stock = List.of(bow, crossbow, secondBow);

        List<ShopItem> filtered = Shop.filterItemsByItemCategory(stock, 20445);

        assertEquals(List.of(bow, secondBow), filtered);
        assertEquals(List.of(250_000, 1_000_000), filtered.stream().map(ShopItem::getPrice).toList());
        assertEquals(7, Shop.filterItemsByItemCategory(stock, 20446).getFirst().getPitch());
    }
}
