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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static server.V84Wz.wz;

/**
 * Ticket 07 - Neo City Year 2227. Sibling of {@link V84TracerNodeTest} and
 * {@link V84CrimsonSkyNodeTest}, same technique and same reason for it: {@link WZFiles#DIRECTORY}
 * is a {@code static final} resolved once per JVM and another test class redirects
 * {@code wz-path} at a {@code @TempDir}, so the real tree has to be opened through an explicitly
 * constructed {@link XMLWZFile}. Kept as its own class rather than appended to either sibling
 * because ticket 05 ran concurrently against the same working tree.
 * <p>
 * Merge inputs are the path lists under {@code docs/wz-baseline/merge-lists/07/}; the ids
 * asserted here are exactly those lists.
 */
class V84NeoCity2227NodeTest {

    /** The three maps, in portal order: Intersection -> Center -> Construction Site. */
    private static final int[] MAPS = {683070400, 683070401, 683070402};

    /** The four mobs. Every one is genuinely placed by a {@code life} node - checked below. */
    private static final int[] MOBS = {9400658, 9400659, 9400660, 9400661};

    /** The existing v83 Neo City hub the area hangs off. Merged by nobody; it already exists. */
    private static final int NEO_CITY_HUB = 240070000;

    // wz(String) lives in V84Wz - one copy for all the v84 node tests (ticket 03f, F8).

    /** Asserts rather than returns null, so a missing map fails with its id instead of an NPE. */
    private static Data map(int mapId) {
        String bucket = mapId >= 683000000 ? "Map6" : "Map2";
        String path = String.format("Map/%s/%d.img", bucket, mapId);
        Data node = wz("Map.wz").getData(path);
        assertNotNull(node, "Map.wz/" + path + ".xml did not parse");
        return node;
    }

    private static Data portal(int mapId, String name) {
        for (Data p : map(mapId).getChildByPath("portal").getChildren()) {
            if (name.equals(DataTool.getString("pn", p, null))) {
                return p;
            }
        }
        return null;
    }

    // ---- maps ---------------------------------------------------------------------------

    @Test
    void everyNeoCity2227MapParses() {
        for (int mapId : MAPS) {
            Data node = map(mapId);
            assertNotNull(node.getChildByPath("info"), mapId + " has no info");
            assertNotNull(node.getChildByPath("portal/0"), mapId + " has no portals");
            assertNotNull(node.getChildByPath("foothold"), mapId + " has no footholds");
            assertEquals("TokyoK", DataTool.getString("info/mapMark", node, null),
                    mapId + " info/mapMark - the world-map marker the area shares with Neo City");
            assertEquals("Bgm21/2215year", DataTool.getString("info/bgm", node, null),
                    mapId + " info/bgm");
            assertEquals(NEO_CITY_HUB, DataTool.getInt("info/returnMap", node, -1),
                    mapId + " returns to the Neo City hub");
            assertEquals(120, DataTool.getInt("info/lvLimit", node, -1), mapId + " info/lvLimit");
            // 0 here, unlike Crimson Sky's fly maps - no flying skill gate on this area.
            assertEquals(0, DataTool.getInt("info/fly", node, 0), mapId + " is not a fly map");
        }
    }

    /**
     * The whole reason this area is deliverable and ticket 06's was not: the edge into existing
     * content already exists on BOTH sides, in the stock v83 client, and needed no merge.
     * {@code 683070400/left00} leads out to {@code 240070000}'s {@code TD_neo} portal, and that
     * portal is a v83 script portal ({@code pt=8, script=TD_chat_enter}) which is present and
     * byte-identical in v83 and v84. Nothing was authored and nothing was forced.
     */
    @Test
    void theRouteIntoExistingNeoCityExistsOnBothSides() {
        Data left00 = portal(683070400, "left00");
        assertNotNull(left00, "683070400 has no left00 portal");
        assertEquals(NEO_CITY_HUB, DataTool.getInt("tm", left00, -1), "left00 target map");
        assertEquals("TD_neo", DataTool.getString("tn", left00, null), "left00 target portal");

        // the far side - the half ticket 06 could not produce
        Data hub = map(NEO_CITY_HUB);
        assertNotNull(hub, "240070000 (the Neo City hub) is missing from the server tree");
        Data tdNeo = portal(NEO_CITY_HUB, "TD_neo");
        assertNotNull(tdNeo, "240070000 has no TD_neo portal - the inbound half of the route");
        assertEquals(8, DataTool.getInt("pt", tdNeo, -1), "TD_neo is a script portal");
        assertEquals("TD_chat_enter", DataTool.getString("script", tdNeo, null),
                "TD_neo runs the portal script that opens the teleporter NPC");
    }

    @Test
    void theThreeMapsFormOneChain() {
        assertEquals(683070401, DataTool.getInt("tm", portal(683070400, "right00"), -1));
        assertEquals(683070400, DataTool.getInt("tm", portal(683070401, "left00"), -1));
        assertEquals(683070402, DataTool.getInt("tm", portal(683070401, "right00"), -1));
        assertEquals(683070401, DataTool.getInt("tm", portal(683070402, "left00"), -1));
        // the chain is a dead end by design - no portal off the far side
        assertNull(portal(683070402, "right00"), "683070402 gained an unexpected right00");
        for (int mapId : MAPS) {
            assertNotNull(portal(mapId, "sp"), mapId + " has no spawn point");
        }
    }

    /**
     * The check {@code WzMerge deps} explicitly does not make: every mob id a merged map places
     * must itself have been merged, or the client has no sprite for it. This is also where the
     * ticket's stated mob list gets checked against the data rather than taken on trust - ticket
     * 06's turned out to be wrong.
     */
    @Test
    void everyLifeIdInEveryMapWasMergedAndTheSetIsTheStatedOne() {
        Set<Integer> mobs = new TreeSet<>();
        Map<Integer, Map<Integer, Integer>> perMap = new HashMap<>();   // map -> mob -> count
        for (int mapId : MAPS) {
            Data life = map(mapId).getChildByPath("life");
            assertNotNull(life, mapId + " has no life node - spawns were lost in the merge");
            for (Data entry : life.getChildren()) {
                String type = DataTool.getString("type", entry, "");
                assertEquals("m", type,
                        mapId + " life entry of type '" + type + "'; this ticket merged no Npc.wz "
                                + "and no Reactor.wz because every entry was type=m");
                int id = Integer.parseInt(DataTool.getString("id", entry, "-1"));
                mobs.add(id);
                perMap.computeIfAbsent(mapId, k -> new HashMap<>()).merge(id, 1, Integer::sum);
            }
        }

        // Per mob per map, not just per map. An earlier version of this assertion checked only
        // the per-map totals and so passed straight over a wrong per-mob breakdown in the
        // ticket's own table - the numbers below are the ones the docs quote, so a doc that
        // drifts from the data now fails here.
        assertEquals(Map.of(
                        683070400, Map.of(9400658, 4, 9400661, 2),
                        683070401, Map.of(9400658, 6, 9400659, 1, 9400661, 5),
                        683070402, Map.of(9400658, 9, 9400660, 1, 9400661, 4)),
                perMap, "spawn counts per mob per map, read off the v84 life nodes");

        Set<Integer> expected = new TreeSet<>();
        for (int id : MOBS) {
            expected.add(id);
        }
        assertEquals(expected, mobs,
                "mobs actually placed by the maps vs the ticket's stated list");

        for (int id : mobs) {
            assertNotNull(wz("Mob.wz").getData(String.format("%d.img", id)),
                    "map life spawns mob " + id + " but Mob.wz/" + id + ".img.xml is absent");
        }
    }

    @Test
    void theMapsPlaceNoReactors() {
        for (int mapId : MAPS) {
            Data reactors = map(mapId).getChildByPath("reactor");
            assertTrue(reactors == null || reactors.getChildren().isEmpty(),
                    mapId + " places a reactor; Reactor.wz was not merged for this ticket");
        }
    }

    /**
     * {@code deps} reported ZERO add-list rows owed for all three maps - v84 reuses the v83 Neo
     * City tileset wholesale. That is a claim about the live client, so assert the assets are
     * actually there rather than trusting the banner. If any of these is absent the maps render
     * broken, which is the exact failure mode ticket 06's dependency rows exist to prevent.
     */
    @Test
    void theMapAssetsTheseMapsReferenceAreAllPreExisting() {
        assertNotNull(wz("Map.wz").getData("Back/neoCity2.img"),
                "Map.wz/Back/neoCity2.img - every one of the three maps draws its background from it");
        assertNotNull(wz("Map.wz").getData("MapHelper.img").getChildByPath("mark/TokyoK"),
                "MapHelper.img/mark/TokyoK - the world-map marker");

        Data zone4 = wz("Map.wz").getData("Obj/Tdungeon2.img").getChildByPath("zone4");
        assertNotNull(zone4, "Obj/Tdungeon2.img/zone4 - the Neo City object set");
        for (String sub : List.of("acc", "buiding", "foot", "foot2")) {
            assertNotNull(zone4.getChildByPath(sub), "Obj/Tdungeon2.img/zone4/" + sub);
        }
        assertNotNull(wz("Map.wz").getData("Obj/connect.img").getChildByPath("rope/14/0"),
                "Obj/connect.img/rope/14 - the ropes in 683070401/402");
        assertNotNull(wz("Map.wz").getData("Obj/Tdungeon.img").getChildByPath("mushCatle/gate/6"),
                "Obj/Tdungeon.img/mushCatle/gate/6");
        assertNotNull(wz("Sound.wz").getData("Bgm21.img").getChildByPath("2215year"),
                "Sound.wz/Bgm21.img/2215year - info/bgm for all three maps");
    }

    // ---- mobs and names -------------------------------------------------------------------

    @Test
    void everyMergedMobParses() {
        for (int id : MOBS) {
            Data node = wz("Mob.wz").getData(String.format("%d.img", id));
            assertNotNull(node, "Mob.wz/" + id + ".img.xml did not parse");
            assertTrue(DataTool.getInt("info/level", node, -1) >= 135, id + " info/level");
            assertTrue(DataTool.getInt("info/maxHP", node, -1) > 0, id + " info/maxHP");
            assertTrue(DataTool.getInt("info/exp", node, -1) > 0, id + " info/exp");
            // all four carry it; on ticket 06 the same flag marked mobs no map placed, but these
            // are placed, so it only affects the spawn animation.
            assertEquals(1, DataTool.getInt("info/summonType", node, -1), id + " info/summonType");
        }
        // the two the maps spawn on a 12h timer are the two flagged bosses
        assertEquals(1, DataTool.getInt("info/boss", wz("Mob.wz").getData("9400659.img"), -1),
                "Dunas Type D is a boss");
        assertEquals(1, DataTool.getInt("info/boss", wz("Mob.wz").getData("9400660.img"), -1),
                "Royal Guard Type S is a boss");
        assertEquals(0, DataTool.getInt("info/boss", wz("Mob.wz").getData("9400658.img"), 0),
                "Imperial Guard Type A is not a boss");
    }

    @Test
    void namesAreReadable() {
        Data ossyria = wz("String.wz").getData("Map.img").getChildByPath("ossyria");
        for (int mapId : MAPS) {
            Data node = ossyria.getChildByPath(String.valueOf(mapId));
            assertNotNull(node, "String.wz/Map.img/ossyria/" + mapId + " missing");
            String name = DataTool.getString("mapName", node, "");
            assertFalse(name.isBlank(), mapId + " has a blank mapName");
            assertFalse("MISSING NAME".equals(name), mapId + " mapName is the placeholder");
            assertTrue(name.contains("2227"), mapId + " mapName should name the year: " + name);
            assertEquals("Neo City", DataTool.getString("streetName", node, "").trim(),
                    mapId + " streetName");
        }
        assertEquals("<Year 2227> Dangerous City Intersection",
                DataTool.getString("mapName", ossyria.getChildByPath("683070400"), "").trim());

        // .trim(): v84 strings sometimes ship with a trailing space and the merge carries them
        // verbatim, which is correct - do not "fix" the data.
        Data mobNames = wz("String.wz").getData("Mob.img");
        assertEquals("Imperial Guard Type A",
                DataTool.getString("name", mobNames.getChildByPath("9400658"), "").trim());
        assertEquals("Dunas Type D",
                DataTool.getString("name", mobNames.getChildByPath("9400659"), "").trim());
        assertEquals("Royal Guard Type S",
                DataTool.getString("name", mobNames.getChildByPath("9400660"), "").trim());
        assertEquals("Afterlord Type A",
                DataTool.getString("name", mobNames.getChildByPath("9400661"), "").trim());
    }

    /**
     * Each new mob is named after a live one plus a variant suffix, which is what makes every
     * drop analogue below a name match. Asserted so a later edit cannot quietly break that
     * premise and leave the quest-row rule resting on nothing.
     */
    @Test
    void everyAnalogueIsGenuinelyANameMatch() {
        Data mobNames = wz("String.wz").getData("Mob.img");
        for (int[] pair : ANALOGUE) {
            String variantName = DataTool.getString(
                    "name", mobNames.getChildByPath(String.valueOf(pair[0])), "").trim();
            String analogueName = DataTool.getString(
                    "name", mobNames.getChildByPath(String.valueOf(pair[1])), "").trim();
            assertFalse(analogueName.isBlank(),
                    "analogue " + pair[1] + " has no live name - wrong id?");
            assertTrue(variantName.startsWith(analogueName + " Type"),
                    "analogue " + pair[1] + " (" + analogueName + ") is not a name match for "
                            + pair[0] + " (" + variantName + ")");
        }
    }

    // ---- the route into existing content, server side ---------------------------------------

    /**
     * The client half of the route is stock v83; the server half is the Neo Tokyo teleporter,
     * which Cosmic shipped with the 2227 destination authored and commented out because the maps
     * did not exist. This asserts it is live, and that the gate array kept the same length as the
     * destination array - {@code generateSelectionMenu} takes {@code min(limit, array.length)},
     * so a seventh destination behind only six gates is silently unreachable.
     */
    @Test
    void theNeoTokyoTeleporterRoutesTo2227() throws IOException {
        String js = Files.readString(Path.of("scripts", "npc", "2083006.js"), StandardCharsets.UTF_8);

        // Count both arrays by counting their ELEMENTS, not by splitting on a delimiter whose
        // spelling differs between the two - a quoted name containing ", " or a reformat that
        // wraps the line would otherwise make the two numbers incomparable and the assert vacuous.
        int gates = countElements(js, "quests", "\\d+");
        int destinations = countElements(js, "array", "\"[^\"]*\"");

        assertEquals(destinations, gates,
                "every destination needs its own gate quest, or the last is unreachable: "
                        + "generateSelectionMenu builds min(limit, array.length) entries and limit "
                        + "cannot exceed quests.length");
        assertEquals(7, destinations, "the 2227 destination");
        assertTrue(js.contains("Year 2227"), "the 2227 destination is not listed");

        assertTrue(Pattern.compile("case 6:\\s*\\n\\s*mapid = 683070400;").matcher(js).find(),
                "selection 6 does not warp to 683070400 (still commented out?)");
    }

    /** Elements of {@code var <name> = [...]}, counted by matching each element in turn. */
    private static int countElements(String js, String varName, String elementRegex) {
        Matcher decl = Pattern.compile("var " + varName + " = \\[([^\\]]*)]").matcher(js);
        assertTrue(decl.find(), "2083006.js no longer declares a '" + varName + "' array");
        Matcher elements = Pattern.compile(elementRegex).matcher(decl.group(1));
        int n = 0;
        while (elements.find()) {
            n++;
        }
        assertTrue(n > 0, varName + " parsed to zero elements - the element regex is wrong");
        return n;
    }

    // ---- the drop tables ----------------------------------------------------------------

    private static final Path DROPS_152 =
            Path.of("src", "main", "resources", "db", "data", "152-drop-data.sql");
    private static final Path DROPS_153 =
            Path.of("src", "main", "resources", "db", "data", "153-crimson-sky-drop-data.sql");
    private static final Path DROPS_154 =
            Path.of("src", "main", "resources", "db", "data", "154-neo-city-2227-drop-data.sql");

    /**
     * new dropperid -> the live Neo City mob its table was copied from. Second column of the
     * table in the 154 file header; kept here so the assertion below has something to check the
     * file against rather than checking the file against itself.
     */
    private static final int[][] ANALOGUE = {
            {9400658, 8140511},   // Imperial Guard Type A <- Imperial Guard   (240070600)
            {9400659, 8220010},   // Dunas Type D          <- Dunas            (240070303, boss)
            {9400660, 8140512},   // Royal Guard Type S    <- Royal Guard
            {9400661, 8120102}};  // Afterlord Type A      <- Afterlord        (240070400)

    /**
     * One grammar for a {@code drop_data} row, used by all three assertions below. Group 1 is the
     * dropperid and group 2 the whole rest of the row, which is what the verbatim-copy check
     * compares; groups 3-7 are the same fields individually, for the well-formedness check.
     */
    private static final Pattern DROP_ROW = Pattern.compile(
            "^\\s*(?:VALUES )?\\((\\d+), ((-?\\d+), (-?\\d+), (-?\\d+), (-?\\d+), (-?\\d+))\\)[,;]$");
    private static final int G_DROPPER = 1;
    private static final int G_REST = 2;
    private static final int G_MIN_QTY = 4;
    private static final int G_MAX_QTY = 5;
    private static final int G_QUESTID = 6;
    private static final int G_CHANCE = 7;

    /**
     * No database here, so this parses the file the way Liquibase's splitter will see it: one
     * statement, every row well-formed, no stray {@code ;} inside a comment to split on.
     */
    @Test
    void neoCityDropFileIsWellFormed() throws IOException {
        List<String> lines = Files.readAllLines(DROPS_154, StandardCharsets.UTF_8);

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

        Set<Integer> droppers = new TreeSet<>();
        int rows = 0;
        for (String line : lines) {
            if (line.startsWith("--") || line.isBlank() || line.startsWith("INSERT INTO")) {
                continue;
            }
            Matcher m = DROP_ROW.matcher(line);
            assertTrue(m.matches(), "malformed row: " + line);
            droppers.add(Integer.parseInt(m.group(G_DROPPER)));
            assertTrue(Integer.parseInt(m.group(G_MIN_QTY)) >= 1, "minimum_quantity: " + line);
            assertTrue(Integer.parseInt(m.group(G_MAX_QTY)) >= Integer.parseInt(m.group(G_MIN_QTY)),
                    "maximum_quantity below minimum: " + line);
            assertTrue(Integer.parseInt(m.group(G_CHANCE)) > 0, "a zero chance never drops: " + line);
            rows++;
        }
        assertEquals(80, rows, "row count");

        Set<Integer> expected = new TreeSet<>();
        for (int id : MOBS) {
            expected.add(id);
        }
        assertEquals(expected, droppers, "one drop table per new mob, and nothing else");
    }

    /**
     * The rates are not invented, in both directions: every row in 154 must already exist
     * verbatim for its declared analogue in 152, AND the copy must be complete - a subset check
     * alone would pass a table that silently lost half its rows.
     */
    @Test
    void everyNeoCityDropRowExistsVerbatimForItsAnalogue() throws IOException {
        Set<String> analogueRows = new TreeSet<>();   // "dropper|rest of the row"
        for (String line : Files.readAllLines(DROPS_152, StandardCharsets.UTF_8)) {
            Matcher m = DROP_ROW.matcher(line);
            if (m.matches()) {
                analogueRows.add(m.group(G_DROPPER) + "|" + m.group(G_REST));
            }
        }
        assertFalse(analogueRows.isEmpty(), "152-drop-data.sql parsed to nothing - parser is wrong");

        Map<Integer, Integer> copied = new HashMap<>();
        for (String line : Files.readAllLines(DROPS_154, StandardCharsets.UTF_8)) {
            Matcher m = DROP_ROW.matcher(line);
            if (!m.matches()) {
                continue;
            }
            int dropper = Integer.parseInt(m.group(G_DROPPER));
            int source = analogueOf(dropper);
            assertTrue(analogueRows.contains(source + "|" + m.group(G_REST)),
                    "invented rate: (" + dropper + ", " + m.group(G_REST)
                            + ") has no counterpart under analogue " + source);
            copied.merge(dropper, 1, Integer::sum);
        }

        // completeness. Every analogue here is a name match, so ticket 06's rule copies the whole
        // table including quest-gated rows - nothing is dropped, so expected == the full count.
        for (int[] pair : ANALOGUE) {
            long expected = analogueRows.stream()
                    .filter(k -> k.startsWith(pair[1] + "|")).count();
            assertTrue(expected > 0, "analogue " + pair[1] + " has no rows in 152 - wrong id?");
            assertEquals((int) expected, (int) copied.getOrDefault(pair[0], 0),
                    "dropper " + pair[0] + " is an incomplete copy of analogue " + pair[1]);
        }
    }

    /**
     * Exactly one copied row is quest-gated, and it is the one ticket 06's rule permits: a name
     * match. If a future edit repoints an analogue at a non-name-matched mob, this is what says
     * so before an existing quest silently becomes completable on a new mob.
     */
    @Test
    void theOnlyQuestGatedRowIsTheNameMatchedDunasOne() throws IOException {
        int questRows = 0;
        for (String line : Files.readAllLines(DROPS_154, StandardCharsets.UTF_8)) {
            Matcher m = DROP_ROW.matcher(line);
            if (m.matches() && Integer.parseInt(m.group(G_QUESTID)) != 0) {
                assertEquals("(9400659, 4032516, 1, 1, 3735, 400000),", line.trim(),
                        "an unexpected quest-gated row");
                questRows++;
            }
        }
        assertEquals(1, questRows, "quest-gated rows copied");
    }

    /**
     * 152 and 153 are Liquibase changeSets that have already run. Editing either changes its
     * checksum and fails validation at startup, so this ticket's rows must be in neither.
     */
    @Test
    void theExistingDropFilesWereNotRewritten() throws IOException {
        List<String> l152 = Files.readAllLines(DROPS_152, StandardCharsets.UTF_8);
        assertTrue(l152.get(0).startsWith("INSERT INTO drop_data (dropperid, itemid"), "152 header");
        assertTrue(l152.get(l152.size() - 1).trim().endsWith("400000);"), "152 tail");

        List<String> l153 = Files.readAllLines(DROPS_153, StandardCharsets.UTF_8);
        assertTrue(l153.get(0).startsWith("-- Ticket 06"), "153 header");

        for (int id : MOBS) {
            assertTrue(l152.stream().noneMatch(l -> l.contains("(" + id + ", ")),
                    "dropperid " + id + " was written into 152-drop-data.sql");
            assertTrue(l153.stream().noneMatch(l -> l.contains("(" + id + ", ")),
                    "dropperid " + id + " was written into 153-crimson-sky-drop-data.sql");
        }
        assertTrue(Files.readString(Path.of("src", "main", "resources", "db", "changelog-data.xml"),
                        StandardCharsets.UTF_8).contains("154-neo-city-2227-drop-data.sql"),
                "changeSet 154 is not registered, so the rows never deploy");
    }

    private static int analogueOf(int dropper) {
        for (int[] pair : ANALOGUE) {
            if (pair[0] == dropper) {
                return pair[1];
            }
        }
        throw new AssertionError("no analogue declared for dropper " + dropper);
    }
}
