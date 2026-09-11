package client.command.commands.gm2;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import server.ItemInformationProvider;
import tools.DatabaseConnection;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TulcusShopCategoriesRealLoad {
    private static final Pattern RETAINED_ROW = Pattern.compile("/\\* (\\d+),");

    @BeforeAll
    static void bootItemDataWithoutTheUnrelatedMonsterCardDatabaseCache() {
        try (MockedStatic<DatabaseConnection> db = Mockito.mockStatic(DatabaseConnection.class)) {
            db.when(DatabaseConnection::getConnection).thenThrow(new SQLException("no database in tests"));
            ItemInformationProvider.getInstance();
        }
    }

    @Test
    void everyAdvertisedCategoryHasCurrentTulcusStockWithThatV84Effect() throws Exception {
        Matcher rows = RETAINED_ROW.matcher(Files.readString(Path.of(
                "src", "main", "resources", "db", "data", "183-tulcus-regular-scroll-resort.sql")));
        List<Integer> itemIds = new ArrayList<>();
        while (rows.find()) {
            itemIds.add(Integer.parseInt(rows.group(1)));
        }
        assertEquals(375, itemIds.size(), "changeSet 183 is the retained Tulcus stock manifest");

        Map<String, Integer> expectedCounts = new LinkedHashMap<>();
        expectedCounts.put("LUK", 47);
        expectedCounts.put("STR", 72);
        expectedCounts.put("DEX", 77);
        expectedCounts.put("INT", 57);
        expectedCounts.put("PAD", 103);
        expectedCounts.put("MAD", 30);
        expectedCounts.put("MHP", 34);
        expectedCounts.put("MMP", 8);
        expectedCounts.put("ACC", 72);
        expectedCounts.put("EVA", 18);
        expectedCounts.put("Speed", 13);
        expectedCounts.put("Jump", 7);
        expectedCounts.put("PDD", 47);
        expectedCounts.put("MDD", 36);
        expectedCounts.put("preventslip", 1);

        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        expectedCounts.forEach((stat, expected) -> {
            long actual = itemIds.stream()
                    .map(ii::getEquipStats)
                    .filter(stats -> stats != null && stats.getOrDefault(stat, 0) > 0)
                    .count();
            assertEquals(expected.longValue(), actual, stat + " category drifted from v84 Item.wz");
            assertTrue(actual > 0, stat + " must not be advertised as an empty category");
        });
    }
}
