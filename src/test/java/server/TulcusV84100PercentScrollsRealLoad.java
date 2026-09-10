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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/** changeSets 180/181: remaining non-GM v84 100% scrolls and complete Tulcus re-sort. */
class TulcusV84100PercentScrollsRealLoad {
    private static final Path ADD = Path.of("src/main/resources/db/data/180-tulcus-v84-100-percent-scrolls.sql");
    private static final Path SORT = Path.of("src/main/resources/db/data/181-tulcus-scroll-shop-resort.sql");
    private static final Path CHANGELOG = Path.of("src/main/resources/db/changelog-data.xml");
    private static final Pattern INSERT = Pattern.compile("(?:SELECT|UNION ALL SELECT)\\s+1052104,\\s*(\\d+),\\s*(\\d+),\\s*(\\d+),\\s*(\\d+)\\s+WHERE NOT EXISTS");
    private static final Pattern FORWARD = Pattern.compile("WHEN\\s+(\\d+)\\s+THEN\\s+(\\d+)\\s+/\\*\\s*(\\d+),(\\d+),(\\d+)\\s*\\*/");
    private static final Pattern CASE = Pattern.compile("WHEN\\s+(\\d+)\\s+THEN\\s+(\\d+)");
    private static final Set<Integer> GM = Set.of(
            2040006,2040007,2040303,2040403,2040506,2040507,2040603,2040709,2040710,2040711,
            2040806,2040807,2040903,2041024,2041025,2043003,2043103,2043203,2043303,2043703,
            2043803,2044003,2044103,2044203,2044303,2044403,2044503,2044603,2044703);

    private static List<int[]> matches(Path path, Pattern pattern, int groups) throws IOException {
        Matcher matcher = pattern.matcher(Files.readString(path, StandardCharsets.UTF_8));
        List<int[]> result = new ArrayList<>();
        while (matcher.find()) result.add(IntStream.rangeClosed(1, groups).map(i -> Integer.parseInt(matcher.group(i))).toArray());
        return result;
    }

    private static Set<Integer> v84Success100() {
        Data img = V84Wz.wz("Item.wz").getData("Consume/0204.img");
        assertNotNull(img);
        Set<Integer> ids = new HashSet<>();
        for (Data item : img.getChildren()) {
            if (DataTool.getInt("info/success", item, -1) == 100) ids.add(Integer.parseInt(item.getName()));
        }
        return ids;
    }

    @Test
    void addsExactlyTheMissingNonGmV84SetAtTheOwnerPrice() throws IOException {
        List<int[]> additions = matches(ADD, INSERT, 4);
        Set<Integer> ids = additions.stream().map(r -> r[0]).collect(java.util.stream.Collectors.toSet());
        assertEquals(82, additions.size());
        assertEquals(82, ids.size());
        assertTrue(v84Success100().containsAll(ids));
        assertTrue(java.util.Collections.disjoint(ids, GM));
        assertEquals(IntStream.range(0, 82).map(i -> 1508 + 4 * i).boxed().toList(), additions.stream().map(r -> r[3]).toList());
        additions.forEach(r -> { assertEquals(250000, r[1], Integer.toString(r[0])); assertEquals(0, r[2]); });

        String sql = Files.readString(ADD, StandardCharsets.UTF_8);
        assertTrue(sql.contains("itemid = 2040303 AND price = 250000 AND pitch = 0 AND position = 1116"));
        assertTrue(sql.contains("itemid = 2040806 AND price = 250000 AND pitch = 0 AND position = 1256"));
    }

    @Test
    void effectiveCatalogueHasExactCountsAndNoGmScrolls() throws IOException {
        List<int[]> rows = matches(SORT, FORWARD, 5);
        Set<Integer> ids = rows.stream().map(r -> r[2]).collect(java.util.stream.Collectors.toSet());
        Set<Integer> nonGm100 = new HashSet<>(v84Success100());
        assertEquals(156, nonGm100.size());
        nonGm100.removeAll(GM);

        assertEquals(431, rows.size());
        assertEquals(429, ids.size());
        assertEquals(127, ids.stream().filter(nonGm100::contains).count());
        assertTrue(java.util.Collections.disjoint(ids, GM));
        assertEquals(IntStream.range(0, 431).map(i -> 1824 - 4 * i).boxed().toList(), rows.stream().map(r -> r[1]).toList());
        assertEquals(431, rows.stream().map(r -> r[0]).distinct().count());
        assertEquals(Set.of(0), rows.stream().map(r -> r[4]).collect(java.util.stream.Collectors.toSet()));

        for (int id : List.of(2040205, 2040206)) assertEquals(2, rows.stream().filter(r -> r[2] == id).count());
        assertTrue(rows.stream().anyMatch(r -> r[2] == 2040205 && r[3] == 500000));
        assertTrue(rows.stream().anyMatch(r -> r[2] == 2040205 && r[3] == 1000000));
        assertTrue(rows.stream().anyMatch(r -> r[2] == 2040206 && r[3] == 250000));
        assertTrue(rows.stream().anyMatch(r -> r[2] == 2040206 && r[3] == 500000));
    }

    @Test
    void rollbacksAndChangelogOrderAreExact() throws IOException {
        String xml = Files.readString(CHANGELOG, StandardCharsets.UTF_8);
        assertTrue(xml.indexOf("<changeSet id=\"180\"") > xml.indexOf("<changeSet id=\"179\""));
        assertTrue(xml.indexOf("<changeSet id=\"181\"") > xml.indexOf("<changeSet id=\"180\""));
        Matcher c180 = Pattern.compile("<changeSet id=\"180\"[\\s\\S]*?</changeSet>").matcher(xml);
        assertTrue(c180.find());
        assertTrue(c180.group().contains("SELECT 1052104, 2040303, 250000, 0, 1116"));
        assertTrue(c180.group().contains("SELECT 1052104, 2040806, 250000, 0, 1256"));
        Set<Integer> additions = matches(ADD, INSERT, 4).stream().map(r -> r[0]).collect(java.util.stream.Collectors.toSet());
        Matcher deleted = Pattern.compile("\\((204\\d+),\\s*(1[5-8]\\d{2})\\)").matcher(c180.group());
        Map<Integer,Integer> rollbackRows = new LinkedHashMap<>();
        while (deleted.find()) rollbackRows.put(Integer.parseInt(deleted.group(1)), Integer.parseInt(deleted.group(2)));
        assertEquals(additions, rollbackRows.keySet());
        List<Integer> ordered = additions.stream().sorted().toList();
        for (int i = 0; i < ordered.size(); i++) assertEquals(1508 + 4 * i, rollbackRows.get(ordered.get(i)));

        Matcher c181 = Pattern.compile("<changeSet id=\"181\"[\\s\\S]*?</changeSet>").matcher(xml);
        assertTrue(c181.find());
        Map<Integer,Integer> inverse = new LinkedHashMap<>();
        Matcher cases = CASE.matcher(c181.group());
        while (cases.find()) inverse.put(Integer.parseInt(cases.group(1)), Integer.parseInt(cases.group(2)));
        Map<Integer,Integer> expected = new LinkedHashMap<>();
        for (int[] row : matches(SORT, FORWARD, 5)) expected.put(row[1], row[0]);
        assertEquals(expected, inverse);
    }
}
