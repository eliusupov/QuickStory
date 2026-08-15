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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static server.V84Wz.wz;

/**
 * Ticket 06 - Crimson Sky. Sibling of {@link V84TracerNodeTest}, same technique and same
 * reason for it: {@link WZFiles#DIRECTORY} is a {@code static final} resolved once per JVM
 * and another test class redirects {@code wz-path} at a {@code @TempDir}, so the real tree
 * has to be opened through an explicitly constructed {@link XMLWZFile}. Kept as its own
 * class rather than appended to the tracer because tickets 04 and 06 ran concurrently
 * against the same working tree.
 * <p>
 * Merge inputs are the path lists under {@code docs/wz-baseline/merge-lists/06/}; the ids
 * asserted here are exactly those lists.
 */
class V84CrimsonSkyNodeTest {

    /** The 13 laid-out maps. */
    private static final int[] LAID_OUT_MAPS = {
            240080000, 240080040, 240080041, 240080050, 240080051,
            240080100, 240080200, 240080300, 240080400, 240080500,
            240080600, 240080700, 240080800};

    /** The 8 pure {@code info/link} stubs, and the map each one borrows its layout from. */
    private static final int[][] LINK_STUBS = {
            {240080101, 240080100}, {240080201, 240080200}, {240080301, 240080300},
            {240080401, 240080400}, {240080501, 240080500}, {240080601, 240080600},
            {240080701, 240080700}, {240080801, 240080800}};

    /** Placed by a map {@code life} node; the client needs a sprite for every one. */
    private static final int[] FLYING_MOBS = {
            8300000, 8300001, 8300002, 8300003, 8300004, 8300005, 8300006};

    /** Merged but not placed by any map in scope. */
    private static final int UNPLACED_MOB = 8300007;

    /** The nine mobs the ticket names. */
    private static final int[] DRAGON_MOBS = {
            9500374, 9500375, 9500376, 9500377, 9500378, 9500379, 9500380, 9500381, 9500382};

    private static final int[] NPCS = {2085000, 2085001, 2085002, 2085003, 9201144, 9201145};

    private static final int[] REACTORS = {2408005, 2408006};

    // wz(String) lives in V84Wz - one copy for all the v84 node tests (ticket 03f, F8).

    /** Asserts rather than returns null, so a missing map fails with its id instead of an NPE. */
    private static Data map(int mapId) {
        String bucket = mapId == 683010000 ? "Map6" : "Map2";
        String path = String.format("Map/%s/%d.img", bucket, mapId);
        Data node = wz("Map.wz").getData(path);
        assertNotNull(node, "Map.wz/" + path + ".xml did not parse");
        return node;
    }

    // ---- maps ---------------------------------------------------------------------------

    @Test
    void everyCrimsonSkyMapParses() {
        for (int mapId : LAID_OUT_MAPS) {
            Data node = map(mapId);
            assertNotNull(node, "Map.wz/Map/Map2/" + mapId + ".img.xml did not parse");
            assertNotNull(node.getChildByPath("info"), mapId + " has no info");
            assertNotNull(node.getChildByPath("portal/0"), mapId + " has no portals");
            assertEquals("Leafre", DataTool.getString("info/mapMark", node, null),
                    mapId + " info/mapMark");
        }

        Data nest = map(683010000);
        assertNotNull(nest, "Map.wz/Map/Map6/683010000.img.xml did not parse");
        assertEquals(683010000, DataTool.getInt("info/returnMap", nest, -1), "683010000 returnMap");
        assertEquals("dragonLair_GL", DataTool.getString("info/onUserEnter", nest, null),
                "683010000 onUserEnter");
    }

    /**
     * The dock is the way in and the way out. {@code left00} is the only portal that leads to
     * an existing v83 map, which makes it the whole travel route on the WZ side.
     */
    @Test
    void dockLinksBackToLeafre() {
        Data dock = map(240080000);
        assertEquals(240000000, DataTool.getInt("info/returnMap", dock, -1), "240080000 returnMap");
        assertEquals("Bgm14/DragonLoad", DataTool.getString("info/bgm", dock, null), "240080000 bgm");
        assertEquals(1, DataTool.getInt("info/fly", dock, -1), "240080000 is a fly map");

        Data left00 = null;
        for (Data portal : dock.getChildByPath("portal").getChildren()) {
            if ("left00".equals(DataTool.getString("pn", portal, null))) {
                left00 = portal;
            }
        }
        assertNotNull(left00, "240080000 has no left00 portal");
        assertEquals(240030102, DataTool.getInt("tm", left00, -1), "left00 target map");
        assertEquals("right00", DataTool.getString("tn", left00, null), "left00 target portal");

        // ...and the far side of it exists in the tree at all, which is the half that has to
        // be true before a human can walk the route. It does NOT yet carry a right00 portal;
        // see the ticket report.
        assertNotNull(wz("Map.wz").getData("Map/Map2/240030102.img"),
                "240030102 (the Leafre side of the route) is missing from the server tree");
    }

    @Test
    void linkStubsPointAtMapsThatExist() {
        for (int[] pair : LINK_STUBS) {
            Data stub = map(pair[0]);
            assertNotNull(stub, pair[0] + ".img.xml did not parse");
            assertEquals(pair[1], DataTool.getInt("info/link", stub, -1), pair[0] + " info/link");
            assertNotNull(map(pair[1]), pair[0] + " links to " + pair[1] + ", which is absent");
        }
    }

    /**
     * The check {@code WzMerge deps} explicitly does not make: every mob and npc id a merged
     * map places must itself have been merged, or the client has no sprite for it.
     */
    @Test
    void everyLifeIdInEveryMapWasMerged() {
        Set<Integer> mobs = new TreeSet<>();
        Set<Integer> npcs = new TreeSet<>();
        List<Integer> allMaps = new ArrayList<>();
        for (int m : LAID_OUT_MAPS) {
            allMaps.add(m);
        }
        for (int[] pair : LINK_STUBS) {
            allMaps.add(pair[0]);
        }
        allMaps.add(683010000);

        for (int mapId : allMaps) {
            Data life = map(mapId).getChildByPath("life");
            if (life == null) {
                continue;
            }
            for (Data entry : life.getChildren()) {
                int id = Integer.parseInt(DataTool.getString("id", entry, "-1"));
                String type = DataTool.getString("type", entry, "");
                if ("m".equals(type)) {
                    mobs.add(id);
                } else if ("n".equals(type)) {
                    npcs.add(id);
                }
            }
        }

        assertFalse(mobs.isEmpty(), "no mob spawned anywhere in Crimson Sky - life nodes lost");
        for (int id : mobs) {
            assertNotNull(wz("Mob.wz").getData(String.format("%d.img", id)),
                    "map life spawns mob " + id + " but Mob.wz/" + id + ".img.xml is absent");
        }
        for (int id : npcs) {
            assertNotNull(wz("Npc.wz").getData(String.format("%d.img", id)),
                    "map life places npc " + id + " but Npc.wz/" + id + ".img.xml is absent");
        }

        // and the set is the one the merge lists claim
        Set<Integer> expectedMobs = new LinkedHashSet<>();
        for (int id : FLYING_MOBS) {
            expectedMobs.add(id);
        }
        assertEquals(expectedMobs, mobs, "mobs actually placed by the maps");
    }

    @Test
    void everyReactorIdInEveryMapWasMerged() {
        Set<Integer> seen = new TreeSet<>();
        List<Integer> allMaps = new ArrayList<>();
        for (int m : LAID_OUT_MAPS) {
            allMaps.add(m);
        }
        allMaps.add(683010000);
        for (int mapId : allMaps) {
            Data reactors = map(mapId).getChildByPath("reactor");
            if (reactors == null) {
                continue;
            }
            for (Data entry : reactors.getChildren()) {
                seen.add(Integer.parseInt(DataTool.getString("id", entry, "-1")));
            }
        }
        // without this the loop below is vacuous if every `reactor` node were lost in the merge
        assertEquals(new TreeSet<>(List.of(2408005, 2408006)), seen,
                "the reactor ids the maps actually place");
        for (int id : seen) {
            assertNotNull(wz("Reactor.wz").getData(String.format("%d.img", id)),
                    "a map places reactor " + id + " but Reactor.wz/" + id + ".img.xml is absent");
        }
        for (int id : REACTORS) {
            assertNotNull(wz("Reactor.wz").getData(String.format("%d.img", id)),
                    "Reactor.wz/" + id + ".img.xml did not parse");
        }
    }

    /**
     * The dependency rows, at the granularity {@code deps} reports them. Merging the maps
     * without these is what "ships with missing backgrounds" looks like.
     */
    @Test
    void mapAssetDependenciesAreInTheTree() {
        Data dragonRoad = wz("Map.wz").getData("Back/dragonRoad.img");
        assertNotNull(dragonRoad, "Map.wz/Back/dragonRoad.img.xml did not parse");
        for (int frame = 20; frame <= 24; frame++) {
            assertNotNull(dragonRoad.getChildByPath("ani/" + frame), "Back/dragonRoad ani/" + frame);
        }
        for (int frame = 42; frame <= 46; frame++) {
            assertNotNull(dragonRoad.getChildByPath("back/" + frame), "Back/dragonRoad back/" + frame);
        }
        // the frames v83 already had must still be there
        assertNotNull(dragonRoad.getChildByPath("back/0"), "Back/dragonRoad back/0 disappeared");

        Data dungeon3 = wz("Map.wz").getData("Obj/dungeon3.img");
        assertNotNull(dungeon3.getChildByPath("skyValley"), "Obj/dungeon3.img/skyValley");
        assertNotNull(dungeon3.getChildByPath("dragonValley"),
                "Obj/dungeon3.img/dragonValley disappeared");

        assertNotNull(wz("Map.wz").getData("Tile/blackTileFly.img"),
                "Map.wz/Tile/blackTileFly.img.xml did not parse");
    }

    // ---- mobs, npcs, names --------------------------------------------------------------

    @Test
    void everyMergedMobParses() {
        for (int id : FLYING_MOBS) {
            Data node = wz("Mob.wz").getData(String.format("%d.img", id));
            assertNotNull(node, "Mob.wz/" + id + ".img.xml did not parse");
            assertTrue(DataTool.getInt("info/level", node, -1) >= 110, id + " info/level");
            assertTrue(DataTool.getInt("info/maxHP", node, -1) > 0, id + " info/maxHP");
        }
        assertNotNull(wz("Mob.wz").getData(String.format("%d.img", UNPLACED_MOB)),
                "Mob.wz/" + UNPLACED_MOB + ".img.xml did not parse");
        for (int id : DRAGON_MOBS) {
            Data node = wz("Mob.wz").getData(String.format("%d.img", id));
            assertNotNull(node, "Mob.wz/" + id + ".img.xml did not parse");
            assertEquals(1, DataTool.getInt("info/summonType", node, -1), id + " info/summonType");
            assertTrue(DataTool.getInt("info/maxHP", node, -1) > 0, id + " info/maxHP");
        }
        assertEquals(1, DataTool.getInt("info/boss",
                wz("Mob.wz").getData("9500382.img"), -1), "Leviathan is a boss");
    }

    @Test
    void everyMergedNpcParses() {
        for (int id : NPCS) {
            assertNotNull(wz("Npc.wz").getData(String.format("%d.img", id)),
                    "Npc.wz/" + id + ".img.xml did not parse");
        }
    }

    @Test
    void namesAreReadable() {
        Data mapNames = wz("String.wz").getData("Map.img").getChildByPath("ossyria");
        for (int mapId : LAID_OUT_MAPS) {
            Data node = mapNames.getChildByPath(String.valueOf(mapId));
            assertNotNull(node, "String.wz/Map.img/ossyria/" + mapId + " missing");
            String name = DataTool.getString("mapName", node, "");
            assertFalse(name.isBlank(), mapId + " has a blank mapName");
            assertFalse("MISSING NAME".equals(name), mapId + " mapName is the placeholder");
        }

        Data mobNames = wz("String.wz").getData("Mob.img");
        assertEquals("Skelegon", DataTool.getString("name", mobNames.getChildByPath("9500380"), "").trim());
        assertEquals("Leviathan", DataTool.getString("name", mobNames.getChildByPath("9500382"), "").trim());
        assertEquals("Dragonica", DataTool.getString("name", mobNames.getChildByPath("8300006"), "").trim());

        // .trim(): several v84 strings ship with a trailing space ("Matada ", "Soaring Hawk ").
        // The merge carries them verbatim, which is correct - do not "fix" the data.
        Data npcNames = wz("String.wz").getData("Npc.img");
        assertEquals("Matada",
                DataTool.getString("name", npcNames.getChildByPath("2085000"), "").trim());
        assertEquals("Crimson Sky Doorway",
                DataTool.getString("name", npcNames.getChildByPath("2085001"), "").trim());
        assertEquals("Giant Twin Dragon's Egg",
                DataTool.getString("name", npcNames.getChildByPath("9201145"), "").trim());
        // Ticket 03f, F2: taken from v84 after all, by force. 06 kept the live "Steward" to
        // protect Cosmic custom content, but v83-stock ships the identical Steward node - so
        // v84 renamed a STOCK npc, and 9201144 is placed by exactly one thing in this repo,
        // the 683010000 life node 06 itself added. Keeping the old name left a black knight
        // (Npc.wz/9201144.img info/script = blackKnight_GL) labelled "Steward".
        assertEquals("Shadow Knight Rene", DataTool.getString("name", npcNames.getChildByPath("9201144"), "").trim(),
                "9201144 should carry v84's name; it is forced via merge-lists/composed/FORCE.txt");
    }

    // ---- the drop tables ----------------------------------------------------------------

    private static final Path DROPS_152 =
            Path.of("src", "main", "resources", "db", "data", "152-drop-data.sql");
    private static final Path DROPS_153 =
            Path.of("src", "main", "resources", "db", "data", "153-crimson-sky-drop-data.sql");

    /**
     * No database here, so this parses the file the way Liquibase's splitter will see it:
     * one statement, every row well-formed, no stray {@code ;} inside a comment to split on.
     */
    @Test
    void crimsonSkyDropFileIsWellFormed() throws IOException {
        List<String> lines = Files.readAllLines(DROPS_153, StandardCharsets.UTF_8);

        // stripComments defaults to false and splitStatements to true, so the header reaches the
        // driver as statement text and a ';' anywhere in it would split the INSERT in half.
        long semicolons = lines.stream()
                .mapToLong(l -> l.chars().filter(c -> c == ';').count()).sum();
        assertEquals(1, semicolons, "exactly one ';' in the whole file, and it terminates the INSERT");
        assertTrue(lines.get(lines.size() - 1).trim().endsWith(");"), "file must end the INSERT");
        assertTrue(lines.stream().filter(l -> l.startsWith("--")).noneMatch(l -> l.contains("'")),
                "an apostrophe in a comment is a quote the SQL splitter has to track - avoid it");

        assertEquals(1, lines.stream().filter(l -> l.startsWith("INSERT INTO drop_data")).count(),
                "one INSERT");
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("VALUES (")), "one VALUES");

        Pattern row = Pattern.compile("^\\s*(?:VALUES )?\\((\\d+), (-?\\d+), (-?\\d+), (-?\\d+), (-?\\d+), (-?\\d+)\\)[,;]$");
        Set<Integer> droppers = new TreeSet<>();
        int rows = 0;
        for (String line : lines) {
            if (line.startsWith("--") || line.isBlank() || line.startsWith("INSERT INTO")) {
                continue;
            }
            Matcher m = row.matcher(line);
            assertTrue(m.matches(), "malformed row: " + line);
            droppers.add(Integer.parseInt(m.group(1)));
            assertTrue(Integer.parseInt(m.group(3)) >= 1, "minimum_quantity: " + line);
            assertTrue(Integer.parseInt(m.group(4)) >= Integer.parseInt(m.group(3)),
                    "maximum_quantity below minimum: " + line);
            assertTrue(Integer.parseInt(m.group(6)) > 0, "a zero chance never drops: " + line);
            rows++;
        }
        assertEquals(776, rows, "row count");

        Set<Integer> expected = new TreeSet<>();
        for (int id : FLYING_MOBS) {
            expected.add(id);
        }
        for (int id : DRAGON_MOBS) {
            expected.add(id);
        }
        assertEquals(expected, droppers, "one drop table per killable new mob, and nothing else");
    }

    /**
     * new dropperid -> the live Leafre mob its table was copied from. Second column of the
     * table in the 153 file header; kept here so the assertion below has something to check
     * the file against rather than checking the file against itself.
     */
    private static final int[][] ANALOGUE = {
            {8300000, 8190003}, {8300001, 8190004}, {8300002, 8150300}, {8300003, 8150301},
            {8300004, 8150302}, {8300005, 8180001}, {8300006, 8180000},
            {9500374, 8150200}, {9500375, 8150201}, {9500376, 8190000}, {9500377, 8190002},
            {9500378, 8140700}, {9500379, 8140701}, {9500380, 8190003}, {9500381, 8190004},
            {9500382, 8180000}};

    /** The four whose analogue is not a name match; quest-gated rows are not copied for these. */
    private static final Set<Integer> NOT_NAME_MATCHED = Set.of(8300000, 8300001, 8300006, 9500382);

    private static final Pattern DROP_ROW = Pattern.compile(
            "^\\s*(?:VALUES )?\\((\\d+), (-?\\d+, -?\\d+, -?\\d+, (-?\\d+), -?\\d+)\\)[,;]$");

    /**
     * The rates are not invented, in both directions: every row in 153 must already exist
     * verbatim for its declared analogue in 152, AND the copy must be complete - a subset check
     * alone would pass a table that silently lost half its rows.
     */
    @Test
    void everyCrimsonSkyDropRowExistsVerbatimForItsAnalogue() throws IOException {
        Map<String, Integer> analogueRows = new HashMap<>();   // "dropper|rest" -> questid
        for (String line : Files.readAllLines(DROPS_152, StandardCharsets.UTF_8)) {
            Matcher m = DROP_ROW.matcher(line);
            if (m.matches()) {
                analogueRows.put(m.group(1) + "|" + m.group(2), Integer.parseInt(m.group(3)));
            }
        }
        assertFalse(analogueRows.isEmpty(), "152-drop-data.sql parsed to nothing - parser is wrong");

        Map<Integer, Integer> copied = new HashMap<>();
        for (String line : Files.readAllLines(DROPS_153, StandardCharsets.UTF_8)) {
            Matcher m = DROP_ROW.matcher(line);
            if (!m.matches()) {
                continue;
            }
            int dropper = Integer.parseInt(m.group(1));
            int source = analogueOf(dropper);
            assertTrue(analogueRows.containsKey(source + "|" + m.group(2)),
                    "invented rate: (" + dropper + ", " + m.group(2)
                            + ") has no counterpart under analogue " + source);
            if (NOT_NAME_MATCHED.contains(dropper)) {
                assertEquals(0, Integer.parseInt(m.group(3)),
                        "quest-gated row copied from a non-name-matched analogue: " + line);
            }
            copied.merge(dropper, 1, Integer::sum);
        }

        // completeness: the copy is the whole analogue table, minus only the quest-gated rows
        // the non-name-match rule deliberately drops.
        for (int[] pair : ANALOGUE) {
            long expected = analogueRows.entrySet().stream()
                    .filter(e -> e.getKey().startsWith(pair[1] + "|"))
                    .filter(e -> !NOT_NAME_MATCHED.contains(pair[0]) || e.getValue() == 0)
                    .count();
            assertTrue(expected > 0, "analogue " + pair[1] + " has no rows in 152 - wrong id?");
            assertEquals((int) expected, (int) copied.getOrDefault(pair[0], 0),
                    "dropper " + pair[0] + " is an incomplete copy of analogue " + pair[1]);
        }
    }

    private static int analogueOf(int dropper) {
        for (int[] pair : ANALOGUE) {
            if (pair[0] == dropper) {
                return pair[1];
            }
        }
        throw new AssertionError("no analogue declared for dropper " + dropper);
    }

    /**
     * 152 is a Liquibase changeSet that has already run everywhere. Editing it changes its
     * checksum and fails validation at startup, so ticket 06's rows must not be in it.
     */
    @Test
    void theExistingDropTableWasNotRewritten() throws IOException {
        List<String> lines = Files.readAllLines(DROPS_152, StandardCharsets.UTF_8);
        assertTrue(lines.get(0).startsWith("INSERT INTO drop_data (dropperid, itemid"), "152 header");
        assertTrue(lines.get(lines.size() - 1).trim().endsWith("400000);"), "152 tail");

        Set<Integer> newDroppers = new TreeSet<>();
        for (int[] pair : ANALOGUE) {
            newDroppers.add(pair[0]);
        }
        newDroppers.add(UNPLACED_MOB);
        for (String line : lines) {
            Matcher m = DROP_ROW.matcher(line);
            if (m.matches()) {
                assertFalse(newDroppers.contains(Integer.parseInt(m.group(1))),
                        "ticket 06 rows leaked into 152-drop-data.sql: " + line);
            }
        }
    }
}
