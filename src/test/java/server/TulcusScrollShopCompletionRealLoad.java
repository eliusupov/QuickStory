package server;

import org.junit.jupiter.api.Test;
import provider.Data;
import provider.DataTool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * changeSet 178: the complete v84 30%/70% catalogue and Tulcus's ordinary main-stat gaps.
 *
 * <p><strong>Not a {@code *Test} class on purpose</strong>, per {@link V84Wz}.
 */
class TulcusScrollShopCompletionRealLoad {

    private static final int TULCUS = 1052104;
    private static final Path CHANGESET =
            Path.of("src", "main", "resources", "db", "data", "178-tulcus-scroll-shop-completion.sql");
    private static final Path CHANGELOG =
            Path.of("src", "main", "resources", "db", "changelog-data.xml");
    private static final Pattern ROW = Pattern.compile(
            "(?:SELECT|UNION ALL SELECT)\\s+(\\d+),\\s*(\\d+),\\s*(\\d+),\\s*(\\d+),\\s*(\\d+)" +
                    "\\s+WHERE NOT EXISTS \\(SELECT 1 FROM shopitems WHERE shopid = (\\d+) AND itemid = (\\d+)\\)");

    private static final Map<Integer, Integer> ORDINARY = Map.ofEntries(
            Map.entry(2040027, 100), Map.entry(2040029, 60), Map.entry(2040031, 10),
            Map.entry(2040303, 100), Map.entry(2040316, 100), Map.entry(2040319, 100),
            Map.entry(2040417, 100), Map.entry(2040418, 60), Map.entry(2040419, 10),
            Map.entry(2040423, 100), Map.entry(2040515, 100), Map.entry(2040530, 100),
            Map.entry(2040623, 100), Map.entry(2040801, 60), Map.entry(2040806, 100),
            Map.entry(2040923, 100), Map.entry(2040929, 100), Map.entry(2041012, 100),
            Map.entry(2041018, 100), Map.entry(2041021, 100),
            Map.entry(2041100, 100), Map.entry(2041101, 60), Map.entry(2041102, 10),
            Map.entry(2041103, 100), Map.entry(2041104, 60), Map.entry(2041105, 10),
            Map.entry(2041106, 100), Map.entry(2041107, 60), Map.entry(2041108, 10),
            Map.entry(2041109, 100), Map.entry(2041110, 60), Map.entry(2041111, 10));

    private static List<int[]> rows() throws IOException {
        Matcher matcher = ROW.matcher(Files.readString(CHANGESET, StandardCharsets.UTF_8));
        List<int[]> rows = new ArrayList<>();
        while (matcher.find()) {
            rows.add(IntStream.rangeClosed(1, 7).map(i -> Integer.parseInt(matcher.group(i))).toArray());
        }
        return rows;
    }

    private static Set<Integer> v84ScrollsAt(int success) {
        Data img = V84Wz.wz("Item.wz").getData("Consume/0204.img");
        assertNotNull(img, "Item.wz/Consume/0204.img did not parse");
        Set<Integer> ids = new HashSet<>();
        for (Data item : img.getChildren()) {
            if (DataTool.getInt("info/success", item, -1) == success) {
                ids.add(Integer.parseInt(item.getName()));
            }
        }
        return ids;
    }

    @Test
    void sqlMatchesTheCompleteV84SuccessSets() throws IOException {
        List<int[]> rows = rows();
        Set<Integer> sql30 = new HashSet<>();
        Set<Integer> sql70 = new HashSet<>();
        for (int[] row : rows) {
            if (row[2] == 1_000_000) sql30.add(row[1]);
            if (row[2] == 600_000) sql70.add(row[1]);
        }

        assertEquals(104, sql30.size());
        assertEquals(95, sql70.size());
        assertEquals(v84ScrollsAt(30), sql30);
        assertEquals(v84ScrollsAt(70), sql70);
    }

    @Test
    void rowsPinOrdinaryRatesPricesGuardsAndPositions() throws IOException {
        List<int[]> rows = rows();
        Map<Integer, int[]> byId = new TreeMap<>();
        for (int[] row : rows) {
            assertEquals(TULCUS, row[0]);
            assertEquals(0, row[3], "pitch for " + row[1]);
            assertEquals(row[0], row[5], "guard shop for " + row[1]);
            assertEquals(row[1], row[6], "guard item for " + row[1]);
            assertFalse(byId.containsKey(row[1]), "duplicate item " + row[1]);
            byId.put(row[1], row);
        }

        assertEquals(231, rows.size());
        assertEquals(231, byId.size());
        assertEquals(
                IntStream.range(0, 231).map(i -> 684 + 4 * i).boxed().toList(),
                rows.stream().map(row -> row[4]).toList());
        assertEquals(byId.keySet().stream().toList(), rows.stream().map(row -> row[1]).toList());

        Data img = V84Wz.wz("Item.wz").getData("Consume/0204.img");
        for (Map.Entry<Integer, Integer> entry : ORDINARY.entrySet()) {
            int id = entry.getKey();
            int success = entry.getValue();
            Data item = img.getChildByPath("0" + id);
            assertNotNull(item, "ordinary scroll absent from v84: " + id);
            assertEquals(success, DataTool.getInt("info/success", item, -1), "success for " + id);
            assertEquals(success == 10 ? 500_000 : 250_000, byId.get(id)[2], "price for " + id);
        }
        assertEquals(32, ORDINARY.size());
        assertTrue(ORDINARY.keySet().stream().noneMatch(v84ScrollsAt(30)::contains));
        assertTrue(ORDINARY.keySet().stream().noneMatch(v84ScrollsAt(70)::contains));
    }

    @Test
    void requestedRowsWereAbsentBefore178AndRollbackIsExact() throws IOException {
        Set<Integer> requested = rows().stream().map(row -> row[1]).collect(java.util.stream.Collectors.toSet());
        StringBuilder prior = new StringBuilder();
        try (Stream<Path> files = Files.list(CHANGESET.getParent())) {
            for (Path file : files.filter(p -> {
                Matcher m = Pattern.compile("^(\\d+)-").matcher(p.getFileName().toString());
                return m.find() && Integer.parseInt(m.group(1)) < 178;
            }).toList()) {
                Files.readAllLines(file, StandardCharsets.UTF_8).stream()
                        .filter(line -> !line.stripLeading().startsWith("--"))
                        .forEach(line -> prior.append(line).append('\n'));
            }
        }
        for (int id : requested) {
            assertFalse(Pattern.compile("(?:\\(|SELECT\\s+)" + TULCUS + "\\s*,\\s*" + id + "\\s*,")
                    .matcher(prior).find(), "already stocked before 178: " + id);
        }

        String xml = Files.readString(CHANGELOG, StandardCharsets.UTF_8);
        Matcher change = Pattern.compile("<changeSet id=\\\"178\\\"[\\s\\S]*?</changeSet>").matcher(xml);
        assertTrue(change.find(), "changeSet 178 is not registered");
        Matcher rollback = Pattern.compile(
                "DELETE FROM shopitems WHERE shopid = 1052104 AND itemid IN \\(([\\s\\S]*?)\\);")
                .matcher(change.group());
        assertTrue(rollback.find(), "changeSet 178 has no exact shop/item rollback");
        Set<Integer> rollbackIds = new HashSet<>();
        Matcher number = Pattern.compile("\\d+").matcher(rollback.group(1));
        int rollbackCount = 0;
        while (number.find()) {
            rollbackCount++;
            rollbackIds.add(Integer.parseInt(number.group()));
        }
        assertEquals(231, rollbackCount, "rollback must list every id exactly once");
        assertEquals(requested, rollbackIds);
    }
}
