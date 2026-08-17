package server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import provider.Data;
import provider.DataTool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * changeSet 168: Evan's books drop the way every other class's books drop.
 *
 * <p>The claim the changeSet makes is narrow and mechanical, so it can be checked exactly: every
 * row is a verbatim copy of a row that already exists for the corresponding ARAN book, with only
 * the itemid replaced. Aran is the analogue because its fourteen mastery books are the only ones
 * in the 3x chance group and because the two sets line up 1:1 - seven skills times two tiers each,
 * plus four skill books each.
 *
 * <p>No database is needed for any of it. Every Aran row the changeSet copies is itself declared
 * in {@code 152-drop-data.sql}, {@code 153-crimson-sky-drop-data.sql} or
 * {@code 154-neo-city-2227-drop-data.sql}, so the test diffs changeSet against changeSet and the
 * live table never enters into it.
 *
 * <p>{@link #theSeventeenItemsAreExactlyEvansBooks()} derives the item list from {@code Item.wz}
 * rather than restating it, so a book appearing or moving in the item data breaks this instead of
 * silently leaving Evan short.
 *
 * <p><strong>Not a {@code *Test} class on purpose</strong>, per {@link V84Wz}.
 */
class EvanBookDropRealLoad {

    private static final Path DATA = Path.of("src", "main", "resources", "db", "data");
    private static final Path CHANGESET = DATA.resolve("168-evan-book-drop-data.sql");

    /** The Aran rows live in these three - verified exhaustively against the live table. */
    private static final List<String> SOURCES = List.of(
            "152-drop-data.sql", "153-crimson-sky-drop-data.sql", "154-neo-city-2227-drop-data.sql");

    /**
     * Evan book -> the Aran book its rows were copied from. Pairing is by skill slot in id order,
     * then by master tier, so a 20 copies a 20. Soul Stone has no 30 book in v84, which is why
     * Combo Tempest 30 (2290137) is absent from the right-hand column.
     */
    private static final Map<Integer, Integer> ANALOGUE = new LinkedHashMap<>();

    static {
        ANALOGUE.put(2290140, 2290126);   // Illusion 20       <- Overswing 20
        ANALOGUE.put(2290141, 2290127);   // Illusion 30       <- Overswing 30
        ANALOGUE.put(2290142, 2290128);   // Flame Wheel 20    <- High Mastery 20
        ANALOGUE.put(2290143, 2290129);   // Flame Wheel 30    <- High Mastery 30
        ANALOGUE.put(2290144, 2290130);   // Magic Mastery 20  <- Freeze Standing 20
        ANALOGUE.put(2290145, 2290131);   // Magic Mastery 30  <- Freeze Standing 30
        ANALOGUE.put(2290146, 2290132);   // Blaze 20          <- Final Blow 20
        ANALOGUE.put(2290147, 2290133);   // Blaze 30          <- Final Blow 30
        ANALOGUE.put(2290148, 2290134);   // Dark Fog 20       <- High Defense 20
        ANALOGUE.put(2290149, 2290135);   // Dark Fog 30       <- High Defense 30
        ANALOGUE.put(2290150, 2290136);   // Soul Stone 20     <- Combo Tempest 20
        ANALOGUE.put(2290151, 2290138);   // Onyx 20           <- Combo Barrier 20
        ANALOGUE.put(2290152, 2290139);   // Onyx 30           <- Combo Barrier 30
        ANALOGUE.put(2280026, 2280013);   // Flame Wheel book  <- Final Blow book
        ANALOGUE.put(2280027, 2280014);   // Magic Mastery     <- High Defense
        ANALOGUE.put(2280028, 2280015);   // Dark Fog          <- Combo Tempest
        ANALOGUE.put(2280029, 2280016);   // Soul Stone        <- Combo Barrier
    }

    /** {@code (dropperid, itemid, min, max, questid, chance)}. */
    private static final Pattern ROW = Pattern.compile(
            "\\((\\d+),\\s*(\\d+),\\s*(\\d+),\\s*(\\d+),\\s*(\\d+),\\s*(\\d+)\\)");

    /** itemid -> the rows for it, each rendered without the itemid so two books can be compared. */
    private static Map<Integer, Set<String>> rowsByItem(Path file) throws IOException {
        String sql = Files.readAllLines(file, StandardCharsets.UTF_8).stream()
                .filter(l -> !l.stripLeading().startsWith("--"))
                .collect(Collectors.joining("\n"));
        Matcher m = ROW.matcher(sql);
        Map<Integer, Set<String>> ret = new LinkedHashMap<>();
        while (m.find()) {
            ret.computeIfAbsent(Integer.parseInt(m.group(2)), k -> new TreeSet<>())
                    .add(m.group(1) + "/" + m.group(3) + "/" + m.group(4) + "/" + m.group(5) + "/" + m.group(6));
        }
        return ret;
    }

    private static Map<Integer, Set<String>> sourceRows() throws IOException {
        Map<Integer, Set<String>> all = new LinkedHashMap<>();
        for (String f : SOURCES) {
            rowsByItem(DATA.resolve(f)).forEach((item, rows) ->
                    all.computeIfAbsent(item, k -> new TreeSet<>()).addAll(rows));
        }
        return all;
    }

    @Test
    void everyRowIsAVerbatimCopyOfItsAranAnalogue() throws IOException {
        Map<Integer, Set<String>> mine = rowsByItem(CHANGESET);
        Map<Integer, Set<String>> src = sourceRows();

        assertAll(ANALOGUE.entrySet().stream().<Executable>map(e -> () -> {
            Set<String> want = src.get(e.getValue());
            assertNotNull(want, "no source rows found for Aran book " + e.getValue());
            assertTrue(!want.isEmpty(), "Aran book " + e.getValue() + " has no rows to copy");
            assertEquals(want, mine.get(e.getKey()),
                    "item " + e.getKey() + " must carry exactly the rows of Aran book " + e.getValue());
        }));
    }

    @Test
    void theChangeSetAddsNothingBeyondTheMapping() throws IOException {
        Map<Integer, Set<String>> mine = rowsByItem(CHANGESET);

        assertAll(
                () -> assertEquals(ANALOGUE.keySet(), mine.keySet(), "unexpected itemid in changeSet 168"),
                () -> assertEquals(78, mine.values().stream().mapToInt(Set::size).sum(),
                        "63 mastery-book rows + 15 skill-book rows")
        );
    }

    /** These itemids must be Evan's books, derived from wz - not whatever the file happens to say. */
    @Test
    void theSeventeenItemsAreExactlyEvansBooks() {
        List<Integer> derived = new ArrayList<>();
        derived.addAll(evanBooks("Consume/0228.img"));
        derived.addAll(evanBooks("Consume/0229.img"));

        assertEquals(derived.stream().sorted().toList(),
                ANALOGUE.keySet().stream().sorted().toList(),
                "changeSet 168 and Item.wz disagree on which books are Evan's");
    }

    /** No book may gain a source twice - these ids must appear in no other changeSet at all. */
    @Test
    void noOtherChangeSetAlreadyDropsTheseBooks() throws IOException {
        Map<Integer, Set<String>> elsewhere = new LinkedHashMap<>();
        try (var files = Files.list(DATA)) {
            for (Path p : files.filter(p -> p.getFileName().toString().endsWith(".sql"))
                    .filter(p -> !p.getFileName().equals(CHANGESET.getFileName())).toList()) {
                rowsByItem(p).forEach((item, rows) ->
                        elsewhere.computeIfAbsent(item, k -> new TreeSet<>()).addAll(rows));
            }
        }

        assertEquals(List.of(),
                ANALOGUE.keySet().stream().filter(elsewhere::containsKey).sorted().toList(),
                "an Evan book already had drop rows elsewhere - 168 would double them");
    }

    private static List<Integer> evanBooks(String img) {
        Data node = V84Wz.wz("Item.wz").getData(img);
        assertNotNull(node, "Item.wz/" + img + " did not parse");
        List<Integer> ret = new ArrayList<>();
        for (Data book : node.getChildren()) {
            Data skills = book.getChildByPath("info/skill");
            if (skills == null) {
                continue;
            }
            List<Integer> list = new ArrayList<>();
            for (int i = 0; i < skills.getChildren().size(); i++) {
                int skillid = DataTool.getInt(Integer.toString(i), skills, 0);
                if (skillid == 0) {
                    break;
                }
                list.add(skillid);
            }
            if (!list.isEmpty() && list.stream().allMatch(s -> s / 10000 == 2217 || s / 10000 == 2218)) {
                ret.add(Integer.parseInt(book.getName()));
            }
        }
        return ret;
    }
}
