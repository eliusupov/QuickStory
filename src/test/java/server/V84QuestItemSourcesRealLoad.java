package server;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import provider.Data;
import provider.DataProviderFactory;
import provider.DataTool;
import provider.wz.WZFiles;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the conclusion of the whole-set sweep in {@code docs/work-plan/V84-QUEST-SWEEP.md}: every
 * item that a still-live v84-added quest demands resolves to a source this server actually seeds.
 * Sibling of {@link EvanPorkSourceRealLoad} and {@link EvanQuestSourcesRealLoad}, which pin one
 * quest each; this one pins the class, so the next WZ merge cannot reintroduce a Stump-Sap-shaped
 * hole silently.
 *
 * <pre>
 *   mvnw.cmd -o test -Dtest=V84QuestItemSourcesRealLoad
 * </pre>
 *
 * <p><strong>Not a {@code *Test} class on purpose</strong>, same reason as its siblings:
 * {@link WZFiles#DIRECTORY} is a {@code static final} resolved once per JVM and
 * {@code MobSkillFactoryTest} points {@code wz-path} at a {@code @TempDir} with no {@code Quest.wz}.
 *
 * <p>Sources are read from the <em>seed SQL</em> rather than the live database, deliberately: the
 * seeds are what a fresh install gets and what review can see, and the test then needs no server.
 */
class V84QuestItemSourcesRealLoad {

    private static final Path DATA = Path.of("src", "main", "resources", "db", "data");
    private static final Path MERGE_09 = Path.of("docs", "wz-baseline", "merge-lists", "09", "Quest.paths.txt");
    private static final Path MERGE_33 = Path.of("docs", "wz-baseline", "merge-lists", "33", "Quest.paths.txt");

    /** The tables whose second column is an item id. {@code drop_data_global} keys on its first. */
    private static final Set<String> ITEM_TABLES =
            Set.of("drop_data", "shopitems", "reactordrops", "makercreatedata", "makerrewarddata");

    /**
     * The two item requirements that legitimately have no source, both P2 and both stated by v84's
     * own data - see V84-QUEST-SWEEP.md. Anything else appearing here is a real blocker.
     *
     * <ul>
     *   <li>3994184 / 3994185 - the Korean 1049x winter-event block. 10487, 10490 and 10496 in that
     *       same block carry {@code end 200912280000}; 10491-10494 and 10497 demand the same items.
     *   <li>1142170 - "Honorable Mesoranger", quest 19011's own medal. The quest is
     *       {@code autoAccept} with {@code medalCategory 3} and requires possession of the medal to
     *       start: a GM handed it out, nothing in the world grants it.
     * </ul>
     */
    private static final Set<Integer> EXEMPT_ITEMS = Set.of(3994184, 3994185, 1142170);
    private static final Set<Integer> EXEMPT_QUESTS = Set.of(10491, 10492, 10493, 10494, 10497, 19011);

    @BeforeAll
    static void muteGraal() {
        System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
        assertTrue(Files.isRegularFile(Path.of(WZFiles.DIRECTORY, "Quest.wz", "Check.img.xml")),
                "wz-path resolved to '" + WZFiles.DIRECTORY + "', which holds no Quest.wz - another "
                        + "test class won the WZFiles.DIRECTORY race, so this says nothing about v84");
    }

    /**
     * The boundary of the sweep, and the one number a future merge is most likely to move. 63 + 135
     * is the split tickets 09 and 33 both state.
     */
    @Test
    void theV84AddedQuestSetIsStillSixtyThreePlusOneHundredThirtyFive() {
        assertEquals(63, questIds(MERGE_09).size(), "ticket 09's merge list no longer holds 63 quest ids");
        assertEquals(135, questIds(MERGE_33).size(), "ticket 33's merge list no longer holds 135 quest ids");
        assertEquals(198, v84AddedQuestIds().size(), "the two lists now overlap; they must be disjoint");
    }

    /**
     * The class-wide claim. For every v84-added quest that is not behind an expired {@code end}
     * date, every item its {@code Check.img} demands must be seeded somewhere: a drop row, a shop,
     * a reactor, a Maker recipe, another quest's reward, or a script's {@code gainItem}.
     */
    @Test
    void everyLiveV84QuestItemRequirementResolvesToASource() {
        Set<Integer> sourced = seedSourcedItems();
        sourced.addAll(questRewardItems());
        sourced.addAll(scriptGrantedItems());
        assertTrue(sourced.size() > 5000,
                "only " + sourced.size() + " sourced items were found - the seed scan is broken, so "
                        + "a pass below would mean nothing");

        List<String> unsourced = new ArrayList<>();
        for (int quest : v84AddedQuestIds()) {
            if (isExpired(quest)) {
                continue;
            }
            for (int item : requiredItems(quest)) {
                if (!sourced.contains(item) && !EXEMPT_ITEMS.contains(item)) {
                    unsourced.add(quest + " needs " + item);
                }
            }
        }
        assertEquals(List.of(), unsourced,
                "a live v84-added quest demands an item nothing on this server produces - this is the "
                        + "Refreshing Stump Sap failure mode, and it makes the quest uncompletable");
    }

    /**
     * The exemptions have to stay non-vacuous: if someone later gives 3994185 or 1142170 a source,
     * or the Korean block acquires an English replacement, the list above must shrink rather than
     * quietly cover a new hole.
     */
    @Test
    void theExemptionsAreStillExactlyTheSixQuestsTheyWereWrittenFor() {
        Set<Integer> sourced = seedSourcedItems();
        sourced.addAll(questRewardItems());
        sourced.addAll(scriptGrantedItems());

        Set<Integer> hit = new TreeSet<>();
        for (int quest : v84AddedQuestIds()) {
            if (isExpired(quest)) {
                continue;
            }
            for (int item : requiredItems(quest)) {
                if (EXEMPT_ITEMS.contains(item) && !sourced.contains(item)) {
                    hit.add(quest);
                }
            }
        }
        assertEquals(new TreeSet<>(EXEMPT_QUESTS), hit,
                "the P2 exemption list no longer matches the quests it was justified for");
    }

    /**
     * Quest 22529 "Helping Beginner Adventurer Christopher", the report that triggered the sweep.
     * The row exists; what makes it look broken is that v84 names <em>plain</em> Stump 130100, and
     * the Deep Valleys the quest sends you to carry Axe Stump and Ghost Stump instead. Both halves
     * are pinned, because "fixing" this by adding a row on 1130100 would be inventing content.
     */
    @Test
    void quest22529AsksForStumpSapFromPlainStumpAndTheRowExists() throws IOException {
        assertEquals(4032460, DataTool.getInt(questCheck("22529/1/item/0/id"), -1));
        assertEquals("Stump", stringName("Mob.img", "130100"));
        assertEquals("Refreshing Stump Sap", stringName("Etc.img", "Etc/4032460"));

        String objective = DataTool.getString(DataProviderFactory.getDataProvider(WZFiles.QUEST)
                .getData("QuestInfo.img").getChildByPath("22529/1"), "");
        assertTrue(objective.contains("#o0130100#"),
                "QuestInfo 22529 no longer names mob 130100 as the source of 4032460; it read: " + objective);

        String sql = Files.readString(DATA.resolve("156-evan-chain-drop-data.sql"), StandardCharsets.UTF_8);
        assertTrue(sql.contains("(130100, 4032460, 1, 1, 22529, 80000)"),
                "changeSet 156 no longer carries the Refreshing Stump Sap row");
        assertFalse(sql.contains("1130100") || sql.contains("1140100"),
                "a row has been added on Axe Stump or Ghost Stump - v84 names only mob 130100, so "
                        + "that is invented content, not parity");
    }

    /**
     * The other half of the same finding, and the reason the report looked like a missing drop: the
     * three maps quest 22529 sends the player to carry no plain Stumps at all. Pristine v84 agrees
     * (Deep Valley I is 32x 1130100 + 10x 1140100 + npc 1022106), so this is parity, not a gap.
     */
    @Test
    void theDeepValleysCarryAxeAndGhostStumpsAndNoPlainStump() {
        assertEquals(0, lifeCount(106000000, "m", 130100), "Deep Valley I now spawns plain Stump");
        assertEquals(32, lifeCount(106000000, "m", 1130100), "Deep Valley I's Axe Stump count changed");
        assertEquals(10, lifeCount(106000000, "m", 1140100), "Deep Valley I's Ghost Stump count changed");
        assertEquals(1, lifeCount(106000000, "n", 1022106), "Christopher is no longer in Deep Valley I");

        assertEquals(0, lifeCount(106000100, "m", 130100), "Deep Valley II now spawns plain Stump");
        assertEquals(0, lifeCount(106000200, "m", 130100), "Deep Valley III now spawns plain Stump");

        // and the mob the quest does name is reachable, one map out of Perion
        assertTrue(lifeCount(101030000, "m", 130100) > 0,
                "East Domain of Perion no longer carries plain Stump, so 22529 has no live source");
    }

    // ---------------------------------------------------------------- helpers

    private static Set<Integer> v84AddedQuestIds() {
        Set<Integer> all = new TreeSet<>(questIds(MERGE_09));
        all.addAll(questIds(MERGE_33));
        return all;
    }

    /** A merge list row is {@code Quest.wz/<Image>.img/<questid>}; only whole-node roots count. */
    private static Set<Integer> questIds(Path list) {
        Set<Integer> ids = new TreeSet<>();
        try {
            for (String line : Files.readAllLines(list, StandardCharsets.UTF_8)) {
                String[] p = line.trim().split("/");
                if (p.length == 3 && "Quest.wz".equals(p[0]) && p[2].matches("\\d+")) {
                    ids.add(Integer.parseInt(p[2]));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertFalse(ids.isEmpty(), "merge list " + list + " yielded no quest ids");
        return ids;
    }

    private static boolean isExpired(int quest) {
        String end = DataTool.getString(questCheck(quest + "/0/end"), "");
        return end.length() >= 8
                && end.substring(0, 8).compareTo(LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)) < 0;
    }

    private static List<Integer> requiredItems(int quest) {
        List<Integer> items = new ArrayList<>();
        for (String phase : new String[]{"0", "1"}) {
            Data node = questCheck(quest + "/" + phase + "/item");
            if (node == null) {
                continue;
            }
            for (Data entry : node.getChildren()) {
                int id = DataTool.getInt(entry.getChildByPath("id"), 0);
                if (id > 0) {
                    items.add(id);
                }
            }
        }
        return items;
    }

    /** Every id that a seed INSERT puts in an item column. */
    private static Set<Integer> seedSourcedItems() {
        Pattern insert = Pattern.compile("INSERT INTO (\\w+)");
        Pattern tuple = Pattern.compile("\\(\\s*(-?\\d+)\\s*,\\s*(-?\\d+)");
        Set<Integer> found = new HashSet<>();
        try (Stream<Path> files = Files.list(DATA)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".sql")).toList()) {
                String table = "";
                for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                    Matcher m = insert.matcher(line);
                    if (m.find()) {
                        table = m.group(1);
                    }
                    boolean second = ITEM_TABLES.contains(table);
                    if (!second && !"drop_data_global".equals(table)) {
                        continue;
                    }
                    Matcher t = tuple.matcher(line);
                    while (t.find()) {
                        found.add(Integer.parseInt(t.group(second ? 2 : 1)));
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return found;
    }

    /** Any quest that hands an item out is a source for it. Positive counts only. */
    private static Set<Integer> questRewardItems() {
        Set<Integer> found = new HashSet<>();
        Data act = DataProviderFactory.getDataProvider(WZFiles.QUEST).getData("Act.img");
        for (Data quest : act.getChildren()) {
            for (String phase : new String[]{"0", "1"}) {
                Data items = quest.getChildByPath(phase + "/item");
                if (items == null) {
                    continue;
                }
                for (Data entry : items.getChildren()) {
                    if (DataTool.getInt(entry.getChildByPath("count"), 0) > 0) {
                        found.add(DataTool.getInt(entry.getChildByPath("id"), 0));
                    }
                }
            }
        }
        return found;
    }

    private static Set<Integer> scriptGrantedItems() {
        Pattern gain = Pattern.compile("gainItem\\s*\\(\\s*(\\d{4,8})");
        Set<Integer> found = new HashSet<>();
        try (Stream<Path> files = Files.walk(Path.of("scripts"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".js")).toList()) {
                Matcher m = gain.matcher(Files.readString(f, StandardCharsets.ISO_8859_1));
                while (m.find()) {
                    found.add(Integer.parseInt(m.group(1)));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return found;
    }

    private static Data questCheck(String path) {
        return DataProviderFactory.getDataProvider(WZFiles.QUEST).getData("Check.img").getChildByPath(path);
    }

    private static String stringName(String img, String path) {
        Data d = DataProviderFactory.getDataProvider(WZFiles.STRING).getData(img).getChildByPath(path);
        assertNotNull(d, "String.wz/" + img + " has no node " + path);
        return DataTool.getString(d.getChildByPath("name"), "");
    }

    private static int lifeCount(int mapId, String type, int id) {
        Data map = DataProviderFactory.getDataProvider(WZFiles.MAP).getData("Map/Map"
                + (mapId / 100000000) + "/" + String.format("%09d", mapId) + ".img");
        assertNotNull(map, "Map.wz has no image for map " + mapId);
        Data life = map.getChildByPath("life");
        if (life == null) {
            return 0;
        }
        int n = 0;
        for (Data entry : life.getChildren()) {
            if (type.equals(DataTool.getString(entry.getChildByPath("type"), ""))
                    && Integer.parseInt(DataTool.getString(entry.getChildByPath("id")).trim()) == id) {
                n++;
            }
        }
        return n;
    }
}
