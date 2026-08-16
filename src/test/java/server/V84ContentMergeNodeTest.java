package server;

import org.junit.jupiter.api.Test;
import provider.Data;
import provider.DataProvider;
import provider.DataTool;
import provider.wz.WZFiles;
import provider.wz.XMLWZFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static server.V84Wz.wz;

/**
 * Ticket 28 - the v84 content merge into the server's own WZ tree.
 * <p>
 * Sibling of the other {@code V84*NodeTest} classes for the reason they are all siblings:
 * {@link WZFiles#DIRECTORY} is a {@code static final} resolved once per JVM and another test
 * class redirects {@code wz-path} at a {@code @TempDir}, so the real tree has to be opened
 * through an explicitly constructed {@link XMLWZFile} ({@link V84Wz}).
 * <p>
 * The manifests under {@code docs/wz-baseline/merge-lists/28/} are the authority for what was
 * merged; this class reads them rather than restating them, so the test cannot drift from the
 * merge. What it adds on top is the two things a manifest cannot say:
 * <ul>
 *   <li>{@link #noCommoditySnIsServedTwice()} - the guard that makes ticket 28's biggest
 *       refusal permanent. 87 of v84's 110 "absent" {@code Commodity.img} slots carry an SN a
 *       server row already serves, and {@code CashItemFactory.loadAllCashItems} keys its map by
 *       SN, so merging them would silently replace 87 live rows in HashMap order.</li>
 *   <li>{@link #forestHallAndItsNpcLocationsStayOut()} - the deliberate absences.</li>
 * </ul>
 */
class V84ContentMergeNodeTest {

    private static final Path MERGE_LISTS = Path.of("docs", "wz-baseline", "merge-lists", "28");

    /** The 11 cash packages this ticket added, and the 11 Commodity slots that sell them. */
    private static final int[] NEW_PACKAGES = {9101608, 9102282, 9102283, 9102287, 9102288,
            9102289, 9102290, 9102291, 9102292, 9102293, 9102294};

    private static List<String> manifestRows() {
        List<String> rows = new ArrayList<>();
        try (Stream<Path> files = Files.list(MERGE_LISTS)) {
            for (Path p : files.filter(f -> f.getFileName().toString().endsWith(".paths.txt"))
                    .toList()) {
                for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                    String s = line.strip();
                    if (!s.isEmpty() && !s.startsWith("#")) {
                        rows.add(s);
                    }
                }
            }
        } catch (IOException e) {
            throw new AssertionError("merge manifests unreadable: " + MERGE_LISTS.toAbsolutePath(), e);
        }
        assertFalse(rows.isEmpty(), "no manifest rows found under " + MERGE_LISTS.toAbsolutePath()
                + " - this test would otherwise pass by asserting nothing");
        return rows;
    }

    /** {@code "Etc.wz/Commodity.img/8947"} -> the node it names, or null. */
    private static Data resolve(String row) {
        String[] halves = row.split("\\.wz/", 2);
        DataProvider provider = wz(halves[0] + ".wz");
        int imgEnd = halves[1].indexOf(".img");
        assertTrue(imgEnd > 0, "manifest row names no .img: " + row);
        String img = halves[1].substring(0, imgEnd + ".img".length());
        String sub = halves[1].substring(Math.min(halves[1].length(), imgEnd + ".img".length() + 1));
        Data node = provider.getData(img);
        if (node == null || sub.isEmpty()) {
            return node;
        }
        return node.getChildByPath(sub);
    }

    // ---- every merged row is really there ------------------------------------------------

    @Test
    void everyMergedRowResolvesThroughTheProductionReader() {
        List<String> missing = new ArrayList<>();
        for (String row : manifestRows()) {
            if (resolve(row) == null) {
                missing.add(row);
            }
        }
        assertEquals(List.of(), missing, "manifest rows that do not resolve in wz/");
    }

    /**
     * 279 WzMerge rows across nine archives, plus the 32 {@code NpcLocation} ids that
     * survived the deny-list, plus the 105 re-slotted cash-shop rows
     * ({@code Etc-appended.paths.txt}). Pin the shape so a silent half-merge fails.
     */
    @Test
    void theManifestIsTheSizeTheTicketClaims() {
        List<String> rows = manifestRows();
        assertEquals(416, rows.size(), "manifest row count");
        assertEquals(rows.size(), new HashSet<>(rows).size(), "duplicate manifest rows");
    }

    // ---- cash shop ------------------------------------------------------------------------

    /**
     * The load-bearing guard. {@code CashItemFactory.loadAllCashItems}
     * ({@code src/main/java/server/CashShop.java}) does {@code loadedItems.put(SN, ...)} over
     * every child of {@code Commodity.img}, so two rows with the same SN mean one silently
     * replaces the other, in HashMap iteration order. That is why ticket 28 merged 11 of v84's
     * 110 absent slots and refused 87 of them: those 87 repeat SNs {@code 80000000}-{@code
     * 80000086}, which this tree already serves from slots 8854-8940 with the identical ItemId,
     * Price and OnSale. If this fails, someone took them.
     */
    @Test
    void noCommoditySnIsServedTwice() {
        Map<Integer, String> seen = new HashMap<>();
        List<String> dupes = new ArrayList<>();
        int rows = 0;
        for (Data row : wz("Etc.wz").getData("Commodity.img").getChildren()) {
            rows++;
            int sn = DataTool.getIntConvert("SN", row);
            String prior = seen.put(sn, row.getName());
            if (prior != null) {
                dupes.add("SN " + sn + " served by both slot " + prior + " and slot " + row.getName());
            }
        }
        assertTrue(rows > 8000, "Commodity.img looks empty: " + rows + " rows");
        assertEquals(List.of(), dupes, "duplicate cash-shop SNs");
    }

    /** The 11 packages and the 11 priced, on-sale rows that sell them, both merged by this ticket. */
    @Test
    void theElevenNewCashPackagesAreSellable() {
        Data commodity = wz("Etc.wz").getData("Commodity.img");
        Data packages = wz("Etc.wz").getData("CashPackage.img");

        Map<Integer, Data> byItemId = new HashMap<>();
        for (Data row : commodity.getChildren()) {
            byItemId.put(DataTool.getIntConvert("ItemId", row), row);
        }
        for (int pkg : NEW_PACKAGES) {
            assertNotNull(packages.getChildByPath(String.valueOf(pkg)),
                    "CashPackage.img/" + pkg);
            Data row = byItemId.get(pkg);
            assertNotNull(row, "no Commodity.img row sells package " + pkg);
            assertEquals(1, DataTool.getIntConvert("OnSale", row, 0),
                    "package " + pkg + " is in the catalogue but not on sale");
            assertTrue(DataTool.getIntConvert("Price", row, 0) > 0,
                    "package " + pkg + " has no price");
        }
    }

    /**
     * {@code CashItemFactory.getPackage} walks a package's {@code SN} list and calls
     * {@code getItem(sn)} on each; an SN with no Commodity row is a null in that list. Checked
     * over every package in the tree, not just the eleven added, because the merge could only
     * break it globally.
     */
    @Test
    void everyCashPackageSnResolvesToACommodityRow() {
        Set<Integer> known = new HashSet<>();
        for (Data row : wz("Etc.wz").getData("Commodity.img").getChildren()) {
            known.add(DataTool.getIntConvert("SN", row));
        }
        Set<String> unresolved = new TreeSet<>();
        List<Data> allPackages = wz("Etc.wz").getData("CashPackage.img").getChildren();
        assertTrue(allPackages.size() > 400, "CashPackage.img looks empty: " + allPackages.size()
                + " - the assertion below would pass by checking nothing");
        for (Data pkg : allPackages) {
            for (Data sn : pkg.getChildByPath("SN").getChildren()) {
                int value = DataTool.getIntConvert(sn);
                if (!known.contains(value)) {
                    unresolved.add(pkg.getName() + " -> SN " + value);
                }
            }
        }
        assertEquals(Set.of(), unresolved, "cash packages naming an SN no Commodity row serves");
    }

    /**
     * The 105 rows {@code append-commodity.py} re-slotted. v84's own slot indices are taken
     * here by different rows (this tree's {@code Commodity.img} diverges from v84's from slot
     * 2322 onward), so they were appended at fresh indices instead. Slot index is read by
     * nothing server-side; SN is. This asserts the SN half landed.
     */
    @Test
    void everyReSlottedCashShopSnIsSellable() {
        Set<Integer> known = new HashSet<>();
        for (Data row : wz("Etc.wz").getData("Commodity.img").getChildren()) {
            known.add(DataTool.getIntConvert("SN", row));
        }
        List<String> missing = new ArrayList<>();
        int checked = 0;
        try {
            for (String line : Files.readAllLines(MERGE_LISTS.resolve("Etc-Commodity.APPENDED.txt"),
                    StandardCharsets.UTF_8)) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] cols = line.split("\t");
                checked++;
                if (!known.contains(Integer.parseInt(cols[1]))) {
                    missing.add("SN " + cols[1] + " (slot " + cols[0] + ")");
                }
            }
        } catch (IOException e) {
            throw new AssertionError("Etc-Commodity.APPENDED.txt unreadable", e);
        }
        assertEquals(105, checked, "re-slotted row count");
        assertEquals(List.of(), missing, "re-slotted SNs that did not land");
    }

    // ---- the rest of the merge -------------------------------------------------------------

    /** GM skills: data only. Nothing implements the effects, so this asserts presence, not behaviour. */
    @Test
    void theGmSkillImageCarriesItsSevenSkills() {
        Data img = wz("Skill.wz").getData("9000.img");
        assertNotNull(img, "Skill.wz/9000.img");
        Data skills = img.getChildByPath("skill");
        assertNotNull(skills, "9000.img/skill");
        for (int id : new int[]{90000000, 90001001, 90001002, 90001003, 90001004, 90001005,
                90001006}) {
            assertNotNull(skills.getChildByPath(String.valueOf(id)), "9000.img/skill/" + id);
        }
        assertNotNull(wz("String.wz").getData("Skill.img").getChildByPath("90000000"),
                "String.wz/Skill.img/90000000 - the name for the icon");
    }

    /**
     * Monster Book is implemented in this server (nine files under {@code src/main/java}
     * reference it), so a missing entry is visibly blank in-game. 41 were merged; spot-check
     * that a merged one carries the fields the feature reads AND that a pre-existing one is
     * untouched beside it.
     */
    @Test
    void theMonsterBookEntriesLandedAndTheOldOnesSurvived() {
        Data book = wz("String.wz").getData("MonsterBook.img");
        Data added = book.getChildByPath("3400000");
        assertNotNull(added, "MonsterBook.img/3400000 (merged by this ticket)");
        assertNotNull(added.getChildByPath("map"), "3400000/map");
        assertNotNull(added.getChildByPath("reward"), "3400000/reward");
        // A pre-existing entry whose reward list is on COLLISION-DENY.txt (line 51) because
        // it is a positional array v84 would splice onto. assertNotNull would pass on a
        // wholesale rewrite, so pin the slots this tree actually ships: Cosmic's own list,
        // ascending item ids, with Nexon's v84 tail absent.
        Data denied = book.getChildByPath("3100101");
        assertNotNull(denied, "MonsterBook.img/3100101 (pre-existing)");
        Data reward = denied.getChildByPath("reward");
        assertNotNull(reward, "3100101/reward (pre-existing)");
        assertEquals(1002156, DataTool.getIntConvert("0", reward), "3100101/reward/0 moved");
        assertEquals(1002622, DataTool.getIntConvert("1", reward), "3100101/reward/1 moved");
        assertEquals(260020200, DataTool.getIntConvert("0", denied.getChildByPath("map")),
                "3100101/map/0 moved");
        assertEquals(3, denied.getChildByPath("map").getChildren().size(),
                "3100101/map grew or shrank");
    }

    /** Six mobs, three NPCs, three reactors - the plain-data half of the merge. */
    @Test
    void theNewMobsNpcsAndReactorsParse() {
        for (int id : new int[]{2220110, 2230112, 9300388, 9300391, 9300393, 9300394}) {
            Data mob = wz("Mob.wz").getData(id + ".img");
            assertNotNull(mob, "Mob.wz/" + id + ".img");
            assertNotNull(mob.getChildByPath("info"), id + " has no info");
        }
        for (int id : new int[]{1022106, 1022107, 2030015}) {
            assertNotNull(wz("Npc.wz").getData(id + ".img"), "Npc.wz/" + id + ".img");
        }
        for (int id : new int[]{1002008, 2302006, 2409000}) {
            Data reactor = wz("Reactor.wz").getData(id + ".img");
            assertNotNull(reactor, "Reactor.wz/" + id + ".img");
            assertNotNull(reactor.getChildByPath("0"), id + " has no state 0");
        }
        assertNotNull(wz("Mob.wz").getData("QuestCountGroup/9101004.img"),
                "Mob.wz/QuestCountGroup/9101004.img");
    }

    /**
     * {@code String.wz/Map.img/victoria/100030320} is the name the Evan world merge
     * ({@code 831e9d023}) left behind - the map image landed, the name did not. Ticket 27 found
     * it; this ticket merged it.
     */
    @Test
    void theEvanFarmMapNamesCatchUpWithTheEvanMapMerge() {
        Data maps = wz("String.wz").getData("Map.img");
        assertNotNull(maps.getChildByPath("victoria/100030320"), "name for map 100030320");
        assertNotNull(wz("Map.wz").getData("Map/Map1/100030320.img"),
                "map 100030320 itself, merged by 831e9d023");
        assertNotNull(wz("String.wz").getData("ToolTipHelp.img")
                .getChildByPath("Mapobject/100030320"), "ToolTipHelp for 100030320");
    }

    // ---- the deliberate absences ------------------------------------------------------------

    /**
     * Two absences this ticket kept, both for the same reason.
     * <p>
     * {@code PlayerNPC} allocates script ids from {@code 9900000 + branch*100}; branch 19 is
     * {@code THUNDERBREAKER1}, so {@code 9901900}-{@code 9901999} is handed out at runtime to
     * Thunder Breakers who max out on a Hall of Fame map, and the only things gating it are the
     * {@code playernpcs} table and the existence of {@code Npc.wz/<id>.img} - which this tree
     * has for all ten. Map {@code 100030301} ("Forest Hall") places fixed NPCs on
     * {@code 9901910}-{@code 9901919}, i.e. squarely inside that run, and
     * {@code Etc.wz/NpcLocation.img/990191x} does the same. Both stay out.
     * <p>
     * {@code V84EvanWorldNodeTest.forestHallIsDeliberatelyNotMerged} pins the map; this pins the
     * NpcLocation half and the reason, so neither can be taken without a test going red.
     */
    @Test
    void forestHallAndItsNpcLocationsStayOut() {
        // positive control first: a broken Map.wz provider returns null for everything, and
        // would otherwise satisfy the assertion below by failing to read anything at all
        assertNotNull(wz("Map.wz").getData("Map/Map1/100030300.img"),
                "Map.wz is not readable, so the absence below proves nothing");
        assertNull(wz("Map.wz").getData("Map/Map1/100030301.img"),
                "map 100030301 was merged - read this test's javadoc first");
        Data locations = wz("Etc.wz").getData("NpcLocation.img");
        for (int npc = 9901910; npc <= 9901919; npc++) {
            assertNull(locations.getChildByPath(String.valueOf(npc)),
                    "NpcLocation.img/" + npc + " must never be merged");
        }
        // the same image really does hold other entries, or the assertion above is vacuous
        assertTrue(locations.getChildren().size() > 1000,
                "NpcLocation.img looks empty: " + locations.getChildren().size());
    }

    /**
     * {@code Say.img} stays at ticket 33's count: {@code Quest.java} never opens it. Ticket 28
     * did not merge its 135 Evan entries either, and this records that as a decision rather
     * than an oversight.
     */
    @Test
    void sayImgIsStillNotMerged() {
        Data say = wz("Quest.wz").getData("Say.img");
        assertNotNull(say, "Quest.wz/Say.img");
        assertNull(say.getChildByPath("22000"), "Say.img/22000 - still deliberately absent");
        assertNotNull(wz("Quest.wz").getData("QuestInfo.img").getChildByPath("22000"),
                "QuestInfo.img/22000 - present, so the assertion above is about Say.img only");
    }
}
