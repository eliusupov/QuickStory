package server;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/** changeSets 182/183: Tulcus retains only the owner's regular-scroll catalogue. */
class TulcusRegularScrollOnlyRealLoad {
    private static final Path BEFORE = Path.of("src/main/resources/db/data/181-tulcus-scroll-shop-resort.sql");
    private static final Path DELETE = Path.of("src/main/resources/db/data/182-tulcus-regular-scroll-only.sql");
    private static final Path SORT = Path.of("src/main/resources/db/data/183-tulcus-regular-scroll-resort.sql");
    private static final Path CHANGELOG = Path.of("src/main/resources/db/changelog-data.xml");
    private static final Pattern ROW = Pattern.compile("WHEN\\s+(\\d+)\\s+THEN\\s+(\\d+)\\s+/\\*\\s*(\\d+),(\\d+),(\\d+)\\s*\\*/");
    private static final Pattern DELETION = Pattern.compile("DELETE FROM shopitems WHERE shopid = (\\d+) AND itemid = (\\d+) AND price = (\\d+) AND pitch = (\\d+) AND position = (\\d+);");
    private static final Pattern RESTORE = Pattern.compile("SELECT\\s+1052104,\\s*(\\d+),\\s*(\\d+),\\s*(\\d+),\\s*(\\d+)\\s+WHERE NOT EXISTS \\(SELECT 1 FROM shopitems WHERE shopid = 1052104 AND itemid = (\\d+)\\)");
    private static final Pattern CASE = Pattern.compile("WHEN\\s+(\\d+)\\s+THEN\\s+(\\d+)");
    private static final Set<Integer> REMOVED = Set.of(
            2040728,2040729,2040730,2040731,2040732,2040733,2040734,2040735,2040736,2040737,2040738,
            2040041,2040042,2040334,2040430,2040538,2040539,2040630,2040740,2040741,2040742,2040829,
            2040830,2040936,2041066,2041067,2043023,2043117,2043217,2043312,2043712,2043812,2044025,
            2044117,2044217,2044317,2044417,2044512,2044612,2044712,2044815,2044908,2049105,2049106,
            2049107,2049108,2049109,2049110,2041200,2049101,2049102,2049103,2049104,2049112,2049113,2049114);

    private record Row(int oldPosition, int newPosition, int itemId, int price, int pitch) {}

    private static List<Row> rows(Path path) throws IOException {
        Matcher matcher = ROW.matcher(Files.readString(path, StandardCharsets.UTF_8));
        List<Row> rows = new ArrayList<>();
        while (matcher.find()) rows.add(new Row(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3)), Integer.parseInt(matcher.group(4)), Integer.parseInt(matcher.group(5))));
        return rows;
    }

    @Test
    void deletesExactlyThe56Post181Tuples() throws IOException {
        Map<Integer, Row> before = new LinkedHashMap<>();
        rows(BEFORE).forEach(r -> before.put(r.itemId, r));
        Matcher matcher = DELETION.matcher(Files.readString(DELETE, StandardCharsets.UTF_8));
        Set<Integer> actual = new HashSet<>();
        while (matcher.find()) {
            assertEquals(1052104, Integer.parseInt(matcher.group(1)));
            int id = Integer.parseInt(matcher.group(2));
            Row row = before.get(id);
            assertNotNull(row, Integer.toString(id));
            assertEquals(List.of(row.price, row.pitch, row.newPosition), List.of(Integer.parseInt(matcher.group(3)),
                    Integer.parseInt(matcher.group(4)), Integer.parseInt(matcher.group(5))));
            assertTrue(actual.add(id));
        }
        assertEquals(56, REMOVED.size());
        assertEquals(REMOVED, actual);
        assertFalse(actual.contains(2040739));
    }

    @Test
    void postStateAndSortMapAreComplete() throws IOException {
        List<Row> before = rows(BEFORE);
        List<Row> expected = before.stream().filter(r -> !REMOVED.contains(r.itemId)).toList();
        List<Row> actual = rows(SORT);
        assertEquals(375, actual.size());
        assertEquals(373, actual.stream().map(Row::itemId).distinct().count());
        assertEquals(IntStream.range(0, 375).map(i -> 1600 - 4 * i).boxed().toList(), actual.stream().map(Row::newPosition).toList());
        assertEquals(expected.stream().map(r -> List.of(r.newPosition, r.itemId, r.price, r.pitch)).toList(),
                actual.stream().map(r -> List.of(r.oldPosition, r.itemId, r.price, r.pitch)).toList());
        assertTrue(actual.stream().noneMatch(r -> REMOVED.contains(r.itemId) || r.itemId == 2040739));
        assertTrue(actual.stream().anyMatch(r -> r.itemId == 2040000));
        assertTrue(actual.stream().anyMatch(r -> r.itemId == 2043000));
    }

    @Test
    void rollbackAndChangelogOrderAreExact() throws IOException {
        String xml = Files.readString(CHANGELOG, StandardCharsets.UTF_8);
        assertTrue(xml.indexOf("<changeSet id=\"182\"") > xml.indexOf("<changeSet id=\"181\""));
        assertTrue(xml.indexOf("<changeSet id=\"183\"") > xml.indexOf("<changeSet id=\"182\""));
        Matcher c182 = Pattern.compile("<changeSet id=\"182\"[\\s\\S]*?</changeSet>").matcher(xml);
        assertTrue(c182.find());
        Matcher restores = RESTORE.matcher(c182.group());
        Set<Integer> restored = new HashSet<>();
        while (restores.find()) {
            int id = Integer.parseInt(restores.group(1));
            assertEquals(id, Integer.parseInt(restores.group(5)));
            assertTrue(restored.add(id));
        }
        assertEquals(REMOVED, restored);

        Matcher c183 = Pattern.compile("<changeSet id=\"183\"[\\s\\S]*?</changeSet>").matcher(xml);
        assertTrue(c183.find());
        Map<Integer,Integer> inverse = new LinkedHashMap<>();
        Matcher cases = CASE.matcher(c183.group());
        while (cases.find()) inverse.put(Integer.parseInt(cases.group(1)), Integer.parseInt(cases.group(2)));
        Map<Integer,Integer> expected = new LinkedHashMap<>();
        rows(SORT).forEach(r -> expected.put(r.newPosition, r.oldPosition));
        assertEquals(expected, inverse);
    }
}
