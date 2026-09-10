package server;

import org.junit.jupiter.api.Test;
import provider.Data;
import provider.DataTool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** changeSet 179: position-only purpose sorting for Tulcus's 351 physical scroll rows. */
class TulcusScrollShopSortRealLoad {

    private static final Path SQL = Path.of("src", "main", "resources", "db", "data",
            "179-tulcus-scroll-shop-sort.sql");
    private static final Path CHANGELOG = Path.of("src", "main", "resources", "db", "changelog-data.xml");
    private static final Path SHOP = Path.of("src", "main", "java", "server", "Shop.java");
    private static final Pattern FORWARD = Pattern.compile(
            "WHEN\\s+(\\d+)\\s+THEN\\s+(\\d+)\\s+/\\*\\s*(\\d+),(\\d+),(\\d+)\\s*\\*/");
    private static final Pattern CASE = Pattern.compile("WHEN\\s+(\\d+)\\s+THEN\\s+(\\d+)");
    private static final Pattern ATT = Pattern.compile("(?i)\\batt\\b|attack|weapon att");
    private static final String[] PURPOSE_NAMES = {
            "STR", "DEX", "INT", "LUK", "ATT", "M.ATT", "HP", "MP", "Accuracy",
            "Avoidability", "Speed", "Jump", "DEF", "Special"
    };
    private static final int[] EXPECTED_COUNTS = {34, 51, 41, 41, 80, 20, 20, 5, 26, 3, 3, 6, 19, 2};

    private enum Purpose { STR, DEX, INT, LUK, ATT, MATT, HP, MP, ACCURACY, AVOIDABILITY, SPEED, JUMP, DEF, SPECIAL }

    private record Row(int oldPosition, int newPosition, int itemId, int price, int pitch,
                       Purpose purpose, int slot, int success) {}

    private static List<Row> rows() throws IOException {
        Matcher matcher = FORWARD.matcher(Files.readString(SQL, StandardCharsets.UTF_8));
        Data names = V84Wz.wz("String.wz").getData("Consume.img");
        Data items = V84Wz.wz("Item.wz").getData("Consume/0204.img");
        List<Row> rows = new ArrayList<>();
        while (matcher.find()) {
            int oldPosition = Integer.parseInt(matcher.group(1));
            int newPosition = Integer.parseInt(matcher.group(2));
            int itemId = Integer.parseInt(matcher.group(3));
            String name = DataTool.getString("name", names.getChildByPath(Integer.toString(itemId)), null);
            int success = DataTool.getInt("info/success", items.getChildByPath("0" + itemId), -1);
            rows.add(new Row(oldPosition, newPosition, itemId,
                    Integer.parseInt(matcher.group(4)), Integer.parseInt(matcher.group(5)),
                    purpose(itemId, name), slot(itemId), success));
        }
        return rows;
    }

    private static Purpose purpose(int id, String name) {
        if (id == 2040120) return Purpose.ATT;
        if (id == 2040229 || id == 2040727) return Purpose.SPECIAL;
        if (Pattern.compile("(?i)magic att").matcher(name).find()) return Purpose.MATT;
        if (ATT.matcher(name).find()) return Purpose.ATT;
        for (Purpose purpose : List.of(Purpose.ACCURACY, Purpose.AVOIDABILITY, Purpose.SPEED,
                Purpose.JUMP, Purpose.STR, Purpose.DEX, Purpose.INT, Purpose.LUK, Purpose.HP, Purpose.MP)) {
            if (Pattern.compile("(?i)\\b" + purpose.name() + "\\b").matcher(name).find()) return purpose;
        }
        if (Pattern.compile("(?i)def|defense").matcher(name).find()) return Purpose.DEF;
        throw new AssertionError("unclassified " + id + ": " + name);
    }

    private static int slot(int id) {
        if (id >= 2049105 && id <= 2049110) return 8; // anniversary gloves
        return switch (id / 100) {
            case 20400 -> 0; case 20401 -> 1; case 20402 -> 2; case 20403 -> 3;
            case 20404 -> 4; case 20405 -> 5; case 20406 -> 6; case 20407 -> 7;
            case 20408 -> 8; case 20409 -> 9; case 20410 -> 10; case 20411 -> 11;
            case 20413 -> 12; case 20492 -> 13; case 20430 -> 14; case 20431 -> 15;
            case 20432 -> 16; case 20433 -> 17; case 20437 -> 18; case 20438 -> 19;
            case 20440 -> 20; case 20441 -> 21; case 20442 -> 22; case 20443 -> 23;
            case 20444 -> 24; case 20445 -> 25; case 20446 -> 26; case 20447 -> 27;
            case 20448 -> 28; case 20449 -> 29;
            default -> throw new AssertionError("unclassified slot for " + id);
        };
    }

    private static int rateRank(int success) {
        return switch (success) { case 100 -> 0; case 70 -> 1; case 60 -> 2; case 30 -> 3; case 10 -> 4; default -> 1000 - success; };
    }

    @Test
    void mapIsCompleteAndFollowsTheFullSortContract() throws IOException {
        List<Row> actual = rows();
        Comparator<Row> order = Comparator.comparing(Row::purpose)
                .thenComparingInt(Row::slot).thenComparingInt(r -> rateRank(r.success()))
                .thenComparingInt(Row::itemId).thenComparingInt(Row::price).thenComparingInt(Row::oldPosition);

        assertEquals(351, actual.size());
        assertEquals(351, actual.stream().map(Row::oldPosition).distinct().count());
        assertEquals(349, actual.stream().map(Row::itemId).distinct().count());
        assertEquals(IntStream.range(0, 351).map(i -> 1504 - 4 * i).boxed().toList(),
                actual.stream().map(Row::newPosition).toList());
        assertEquals(actual.stream().sorted(order).toList(), actual);
        assertEquals(actual, actual.stream().sorted(Comparator.comparingInt(Row::newPosition).reversed()).toList());
        assertTrue(Files.readString(SHOP, StandardCharsets.UTF_8).contains("ORDER BY position DESC"));
        assertEquals(Set.of(0), actual.stream().map(Row::pitch).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void purposePinsAndCountsComeFromCanonicalV84Names() throws IOException {
        List<Row> rows = rows();
        Map<Purpose, Long> counts = new LinkedHashMap<>();
        for (Purpose purpose : Purpose.values()) counts.put(purpose, rows.stream().filter(r -> r.purpose == purpose).count());
        assertEquals(IntStream.range(0, EXPECTED_COUNTS.length).boxed()
                        .collect(java.util.stream.Collectors.toMap(i -> Purpose.values()[i], i -> (long) EXPECTED_COUNTS[i],
                                (a, b) -> a, LinkedHashMap::new)), counts);

        Map<Integer, Purpose> pins = Map.ofEntries(
                Map.entry(2040120, Purpose.ATT), Map.entry(2040229, Purpose.SPECIAL),
                Map.entry(2040727, Purpose.SPECIAL), Map.entry(2041112, Purpose.STR),
                Map.entry(2049105, Purpose.ATT), Map.entry(2049106, Purpose.ATT),
                Map.entry(2049107, Purpose.STR), Map.entry(2049108, Purpose.LUK),
                Map.entry(2049109, Purpose.INT), Map.entry(2049110, Purpose.DEX),
                Map.entry(2049200, Purpose.STR), Map.entry(2049201, Purpose.STR),
                Map.entry(2049202, Purpose.DEX), Map.entry(2049203, Purpose.DEX),
                Map.entry(2049204, Purpose.INT), Map.entry(2049205, Purpose.INT),
                Map.entry(2049206, Purpose.LUK), Map.entry(2049207, Purpose.LUK),
                Map.entry(2049208, Purpose.HP), Map.entry(2049209, Purpose.HP),
                Map.entry(2049210, Purpose.MP), Map.entry(2049211, Purpose.MP));
        for (Map.Entry<Integer, Purpose> pin : pins.entrySet()) {
            assertTrue(rows.stream().anyMatch(r -> r.itemId == pin.getKey() && r.purpose == pin.getValue()), pin.toString());
        }
        assertEquals(Purpose.MATT, purpose(2040817, "Scroll for Gloves for Magic Att."), "M.ATT precedes ATT");
    }

    @Test
    void sqlChangesOnlyPositionsAndRollbackIsTheExactInverse() throws IOException {
        String sql = Files.readString(SQL, StandardCharsets.UTF_8);
        String executable = sql.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)^--.*$", "");
        assertEquals(1, Pattern.compile("(?i)UPDATE\\s+shopitems").matcher(executable).results().count());
        assertTrue(Pattern.compile("(?i)SET\\s+position\\s*=\\s*CASE\\s+position").matcher(executable).find());
        assertTrue(Pattern.compile("(?i)WHERE\\s+shopid\\s*=\\s*1052104").matcher(executable).find());
        assertFalse(Pattern.compile("(?i)(itemid|price|pitch)\\s*=").matcher(executable).find());

        String changelog = Files.readString(CHANGELOG, StandardCharsets.UTF_8);
        assertTrue(changelog.indexOf("<changeSet id=\"179\"") > changelog.indexOf("<changeSet id=\"178\""));
        Matcher change = Pattern.compile("<changeSet id=\"179\"[\\s\\S]*?</changeSet>").matcher(changelog);
        assertTrue(change.find());
        Matcher inverse = CASE.matcher(change.group());
        Map<Integer, Integer> rollback = new LinkedHashMap<>();
        while (inverse.find()) rollback.put(Integer.parseInt(inverse.group(1)), Integer.parseInt(inverse.group(2)));
        assertEquals(rows().stream().collect(java.util.stream.Collectors.toMap(Row::newPosition, Row::oldPosition,
                (a, b) -> a, LinkedHashMap::new)), rollback);
    }

    @Test
    void physicalDuplicateRowsKeepTheirDistinctValues() throws IOException {
        Map<Integer, List<Row>> duplicates = rows().stream().filter(r -> r.itemId == 2040205 || r.itemId == 2040206)
                .collect(java.util.stream.Collectors.groupingBy(Row::itemId));
        Function<Row, List<Integer>> tuple = r -> List.of(r.oldPosition, r.price, r.pitch);
        assertEquals(List.of(List.of(400, 500000, 0), List.of(620, 1000000, 0)),
                duplicates.get(2040205).stream().map(tuple).toList());
        assertEquals(List.of(List.of(388, 250000, 0), List.of(596, 500000, 0)),
                duplicates.get(2040206).stream().map(tuple).toList());
    }
}
