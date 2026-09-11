package server;

import org.junit.jupiter.api.Test;
import provider.Data;
import provider.DataTool;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tulcus weapon filters follow exact v84 scroll families, not name substring guesses. */
class TulcusWeaponCategoriesRealLoad {
    private static final Path STOCK = Path.of(
            "src", "main", "resources", "db", "data", "183-tulcus-regular-scroll-resort.sql");
    private static final Pattern ROW = Pattern.compile(
            "WHEN\\s+\\d+\\s+THEN\\s+\\d+\\s+/\\*\\s*(204\\d+),(\\d+),(\\d+)\\s*\\*/");
    private static final Map<Integer, Integer> COUNTS = Map.ofEntries(
            Map.entry(20430, 11), Map.entry(20431, 8), Map.entry(20432, 8), Map.entry(20433, 5),
            Map.entry(20437, 5), Map.entry(20438, 5), Map.entry(20440, 8), Map.entry(20441, 8),
            Map.entry(20442, 8), Map.entry(20443, 8), Map.entry(20444, 8), Map.entry(20445, 5),
            Map.entry(20446, 5), Map.entry(20447, 5), Map.entry(20448, 8), Map.entry(20449, 5));
    private static final Map<Integer, String> TARGET_NAMES = new LinkedHashMap<>();

    static {
        TARGET_NAMES.put(20430, "one-handed sword");
        TARGET_NAMES.put(20431, "one-handed axe");
        TARGET_NAMES.put(20432, "one-handed bw");
        TARGET_NAMES.put(20433, "dagger");
        TARGET_NAMES.put(20437, "wand");
        TARGET_NAMES.put(20438, "staff");
        TARGET_NAMES.put(20440, "two-handed sword");
        TARGET_NAMES.put(20441, "two-handed axe");
        TARGET_NAMES.put(20442, "two-handed bw");
        TARGET_NAMES.put(20443, "spear");
        TARGET_NAMES.put(20444, "pole[- ]arm");
        TARGET_NAMES.put(20445, "bow");
        TARGET_NAMES.put(20446, "crossbow");
        TARGET_NAMES.put(20447, "claw");
        TARGET_NAMES.put(20448, "knuckle(?:r)?");
        TARGET_NAMES.put(20449, "gun");
    }

    private static List<ShopItem> stock() throws Exception {
        Matcher rows = ROW.matcher(Files.readString(STOCK));
        List<ShopItem> stock = new ArrayList<>();
        while (rows.find()) {
            stock.add(new ShopItem((short) 1000, Integer.parseInt(rows.group(1)),
                    Integer.parseInt(rows.group(2)), Integer.parseInt(rows.group(3))));
        }
        assertEquals(375, stock.size(), "changeSet 183 is the retained Tulcus row order");
        return stock;
    }

    @Test
    void everyAdvertisedWeaponFamilyIsNonEmptyAndPreservesRealRowsInOrder() throws Exception {
        List<ShopItem> stock = stock();
        Set<Integer> representedWeaponFamilies = stock.stream().map(item -> item.getItemId() / 100)
                .filter(family -> family >= 20430 && family <= 20449).collect(java.util.stream.Collectors.toSet());
        assertEquals(COUNTS.keySet(), representedWeaponFamilies, "a retained weapon family lacks a category");
        assertEquals(COUNTS.keySet(), TARGET_NAMES.keySet(), "every category needs v84 name evidence");
        COUNTS.forEach((family, count) -> {
            List<ShopItem> expected = stock.stream().filter(item -> item.getItemId() / 100 == family).toList();
            List<ShopItem> actual = Shop.filterItemsByItemCategory(stock, family);
            assertEquals(count, actual.size(), Integer.toString(family));
            assertEquals(expected, actual, family + " changed row identity, order, price, or pitch");
        });
    }

    @Test
    void exactFamiliesAgreeWithEveryV84ItemNameAndDoNotCrossCollide() throws Exception {
        List<ShopItem> stock = stock();
        Data names = V84Wz.wz("String.wz").getData("Consume.img");
        assertNotNull(names);

        TARGET_NAMES.forEach((family, target) -> {
            Pattern exactTarget = Pattern.compile("(?i)^(?:dark )?scroll for " + target + " for ");
            for (ShopItem item : Shop.filterItemsByItemCategory(stock, family)) {
                String name = DataTool.getString("name", names.getChildByPath(Integer.toString(item.getItemId())), null);
                assertTrue(name != null && exactTarget.matcher(name).find(), item.getItemId() + ": " + name);
            }
        });

        assertEquals(List.of(2044500, 2044504, 2044501, 2044505, 2044502),
                Shop.filterItemsByItemCategory(stock, 20445).stream().map(ShopItem::getItemId).toList(),
                "bow must not absorb crossbow rows");
        assertEquals(List.of(2044600, 2044604, 2044601, 2044605, 2044602),
                Shop.filterItemsByItemCategory(stock, 20446).stream().map(ShopItem::getItemId).toList());
    }
}
