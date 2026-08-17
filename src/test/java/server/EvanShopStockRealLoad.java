package server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import provider.Data;
import provider.DataProvider;
import provider.DataTool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * changeSet 166: Evan stock for the two shops the owner built - Sly (2080001) in Leafre and
 * Tulcus (1052104) outside Kerning City.
 *
 * <p>Shop contents live in the {@code shopitems} table and there is no database in a test JVM, so
 * what is pinned here is the pair the changeSet actually asserts: its rows against the items
 * {@code Item.wz} really contains, and the two shop identities against {@code Map.wz} /
 * {@code String.wz}. It fails if an id list drifts from wz, if the price or position conventions
 * the owner's own 244 rows established are broken, or if someone re-points a row at a different
 * NPC.
 *
 * <p>Sly's set is DERIVED from wz rather than restated - every book in {@code Consume/0228.img}
 * and {@code Consume/0229.img} whose skills all sit in Evan's 2217/2218 job blocks - so a book
 * appearing or moving in the item data breaks this test instead of silently leaving the shop
 * short. It is 4 skill books and 13 mastery books today, thirteen and not fourteen because v84
 * ships no "Soul Stone 30".
 *
 * <p>Two things deliberately have no assertion here. That the four skill books are load-bearing
 * rests on {@code Skill.isFourthJob()} and {@code AssignSPProcessor:91}, but {@code SkillFactory}
 * is a WZ-backed static map that is empty outside a running server (see
 * {@code AssignSPProcessorTest}), so reaching it would mean mocking the very thing under test.
 * And nothing pins the absence of Evan scrolls, because the only way to test it would be to
 * restate {@code ScrollHandler}'s private matching rule, and a duplicated rule pins the copy.
 * Both are argued with file:line in the changeSet header instead.
 *
 * <p><strong>Not a {@code *Test} class on purpose</strong>, per {@link V84Wz}.
 */
class EvanShopStockRealLoad {

    private static final Path CHANGESET =
            Path.of("src", "main", "resources", "db", "data", "166-evan-shops-data.sql");

    private static final int SLY = 2080001;             // Leafre : Department Store
    private static final int SLY_MAP = 240000002;
    private static final int TULCUS = 1052104;          // Warning Street : The Swamp of Despair II
    private static final int TULCUS_MAP = 107000100;

    private static final int BOOK_PRICE = 5000000;      // flat across all 155 of Sly's rows
    private static final int SLY_FIRST_POSITION = 156;  // his positions run 1..155
    private static final int TULCUS_FIRST_POSITION = 624;   // his run 104..620, stride 4

    /** {@code (shopid, itemid, price, pitch, position)} tuples. */
    private static final Pattern ROW = Pattern.compile(
            "\\((\\d+),\\s*(\\d+),\\s*(\\d+),\\s*(\\d+),\\s*(\\d+)\\)");

    private static List<int[]> rowsFor(int shopId) throws IOException {
        // the header is prose about ids and prices; matching it would be matching the commentary
        String sql = Files.readAllLines(CHANGESET, StandardCharsets.UTF_8).stream()
                .filter(l -> !l.stripLeading().startsWith("--"))
                .reduce("", (a, b) -> a + "\n" + b);
        Matcher m = ROW.matcher(sql);
        List<int[]> rows = new ArrayList<>();
        while (m.find()) {
            int[] row = IntStream.rangeClosed(1, 5).map(g -> Integer.parseInt(m.group(g))).toArray();
            if (row[0] == shopId) {
                rows.add(row);
            }
        }
        return rows;
    }

    /** Books in the given img whose every skill belongs to Evan - job blocks 2217 and 2218. */
    private static Map<Integer, List<Integer>> evanBooks(String img) {
        Data node = V84Wz.wz("Item.wz").getData(img);
        assertNotNull(node, "Item.wz/" + img + " did not parse");
        Map<Integer, List<Integer>> ret = new LinkedHashMap<>();
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
                ret.put(Integer.parseInt(book.getName()), list);
            }
        }
        return ret;
    }

    @Test
    void slyStocksExactlyTheEvanBooksThatExist() throws IOException {
        List<Integer> skillBooks = evanBooks("Consume/0228.img").keySet().stream().sorted().toList();
        List<Integer> masteryBooks = evanBooks("Consume/0229.img").keySet().stream().sorted().toList();
        List<Integer> expected = new ArrayList<>(skillBooks);
        expected.addAll(masteryBooks);

        assertAll(
                () -> assertEquals(expected, rowsFor(SLY).stream().map(r -> r[1]).toList(),
                        "changeSet 166 and Item.wz disagree on Evan's books, or on their order"),
                () -> assertEquals(List.of(2280026, 2280027, 2280028, 2280029), skillBooks,
                        "the four Evan skill books - Flame Wheel, Magic Mastery, Dark Fog, Soul Stone"),
                () -> assertEquals(13, masteryBooks.size(),
                        "7 skills x 2 tiers less the absent Soul Stone 30"),
                () -> assertEquals(2290140, masteryBooks.get(0)),
                () -> assertEquals(2290152, masteryBooks.get(12),
                        "2290152 is the last mastery book in v84; 2290153 does not exist")
        );
    }

    /** Price, position and the skill-books-first grouping are the owner's, not invented here. */
    @Test
    void slyRowsFollowTheConventionHisShopAlreadyUses() throws IOException {
        List<int[]> rows = rowsFor(SLY);
        List<Integer> positions = rows.stream().map(r -> r[4]).toList();

        assertAll(
                () -> assertEquals(17, rows.size(), "4 skill books + 13 mastery books"),
                () -> assertTrue(rows.stream().allMatch(r -> r[2] == BOOK_PRICE),
                        "every book in this shop costs " + BOOK_PRICE),
                () -> assertTrue(rows.stream().allMatch(r -> r[3] == 0), "pitch is 0 on every row"),
                () -> assertEquals(
                        IntStream.range(0, rows.size()).map(i -> SLY_FIRST_POSITION + i).boxed().toList(),
                        positions,
                        "positions must continue the contiguous 1..N run without renumbering"),
                () -> assertTrue(rows.stream().takeWhile(r -> r[1] < 2290000).count() == 4,
                        "skill books come before mastery books, as they do at positions 1-16")
        );
    }

    /**
     * Tulcus: each row's price must match the success rate the item actually carries, under the
     * conventions his 89 rows set - 100% and 60% at 250000, 10% at 500000, except gloves and
     * shield magic-attack scrolls, which his own 2040919/2040920 pair prices at 500000/750000.
     */
    @Test
    void tulcusRowsArePricedOffTheirRealSuccessRate() throws IOException {
        Data img = V84Wz.wz("Item.wz").getData("Consume/0204.img");
        assertNotNull(img, "Item.wz/Consume/0204.img did not parse");
        Map<Integer, Integer> expected = Map.of(
                100, 250000,
                60, 250000,
                10, 500000);
        List<int[]> rows = rowsFor(TULCUS);

        assertAll(
                () -> assertEquals(7, rows.size(), "3 overall INT, 2 gloves magic att, 2 wand/staff 100%"),
                () -> assertEquals(
                        IntStream.range(0, rows.size())
                                .map(i -> TULCUS_FIRST_POSITION + 4 * i).boxed().toList(),
                        rows.stream().map(r -> r[4]).toList(),
                        "positions must continue the shop's stride-4 run from 620"),
                () -> assertAll(rows.stream().<Executable>map(r -> () -> {
                    Data item = img.getChildByPath("0" + r[1]);
                    assertNotNull(item, "scroll " + r[1] + " is not in Item.wz");
                    int success = DataTool.getInt("info/success", item, -1);
                    boolean glovesMagicAtt = r[1] == 2040816 || r[1] == 2040817;
                    int want = glovesMagicAtt
                            ? (success == 10 ? 750000 : 500000)     // the 2040919/2040920 precedent
                            : expected.get(success);
                    assertEquals(want, r[2], "scroll " + r[1] + " at " + success + "% success");
                }))
        );
    }

    /**
     * The omission that needs a reason: topwear and bottomwear INT scrolls were asked for and are
     * not here because v84 has none. If a merge ever adds them, this fails and the shop can be
     * topped up rather than staying quietly short.
     */
    @Test
    void v84HasNoTopwearOrBottomwearIntScroll() {
        Data img = V84Wz.wz("Item.wz").getData("Consume/0204.img");

        assertAll(
                () -> assertEquals(List.of(), intScrollsIn(img, 20404), "topwear"),
                () -> assertEquals(List.of(), intScrollsIn(img, 20406), "bottomwear"),
                () -> assertEquals(List.of(2040512, 2040513, 2040514), intScrollsIn(img, 20405).stream()
                                .filter(i -> i <= 2040514).toList(),
                        "overall INT is the one slot in the request that does exist, at all three tiers")
        );
    }

    private static List<Integer> intScrollsIn(Data img, int family) {
        List<Integer> ret = new ArrayList<>();
        for (Data item : img.getChildren()) {
            if (!item.getName().chars().allMatch(Character::isDigit)) {
                continue;
            }
            int id = Integer.parseInt(item.getName());
            if (id / 100 == family && item.getChildByPath("info/incINT") != null) {
                ret.add(id);
            }
        }
        return ret.stream().sorted().toList();
    }

    /** Both shop ids are the NPCs the owner meant - the identification, not a guess at it. */
    @Test
    void bothShopIdsAreTheNpcsTheOwnerMeant() {
        DataProvider string = V84Wz.wz("String.wz");

        assertAll(
                () -> assertEquals("Sly", DataTool.getString("name",
                        string.getData("Npc.img").getChildByPath(Integer.toString(SLY)), null)),
                () -> assertEquals("Leafre", DataTool.getString("streetName",
                        string.getData("Map.img").getChildByPath("ossyria/" + SLY_MAP), null)),
                () -> assertTrue(npcIsOnMap(SLY, "Map2/" + SLY_MAP),
                        "npc " + SLY + " is not placed on map " + SLY_MAP),
                () -> assertEquals("Tulcus", DataTool.getString("name",
                        string.getData("Npc.img").getChildByPath(Integer.toString(TULCUS)), null)),
                () -> assertTrue(npcIsOnMap(TULCUS, "Map1/" + TULCUS_MAP),
                        "npc " + TULCUS + " is not placed on map " + TULCUS_MAP)
        );
    }

    private static boolean npcIsOnMap(int npcId, String mapPath) {
        Data map = V84Wz.wz("Map.wz").getData("Map/" + mapPath + ".img");
        assertNotNull(map, "Map.wz/Map/" + mapPath + ".img did not parse");
        Data life = map.getChildByPath("life");
        assertNotNull(life, "map " + mapPath + " has no life node");
        return life.getChildren().stream().anyMatch(l ->
                "n".equals(DataTool.getString("type", l, null))
                        && String.valueOf(npcId).equals(DataTool.getString("id", l, null)));
    }
}
