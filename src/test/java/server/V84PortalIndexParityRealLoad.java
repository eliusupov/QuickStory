package server;

import org.junit.jupiter.api.Test;
import provider.Data;
import provider.DataTool;
import provider.wz.WZFiles;
import provider.wz.XMLWZFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static server.V84Wz.wz;

/**
 * The rest of the index-addressed portal drift, outside the 17 towns
 * {@link V84TownIndexParityRealLoad} covers. Same defect, same three mechanisms, different maps.
 *
 * <ol>
 *   <li><b>Arrival is a slot number.</b> {@code PacketCreator.getWarpToMap} writes
 *       {@code portal.getId()} and {@code PortalFactory.loadPortal} takes that id from the node's
 *       <em>name</em>, so if our slot n holds a different portal than the client's slot n the
 *       player lands somewhere else. Four Omega Sector maps had two slots transposed.
 *   <li><b>Clicking is a name off the wire.</b> {@code ChangeMapHandler} reads the portal name the
 *       client sends and looks it up with {@code MapleMap.getPortal(String)}, which is
 *       {@code String.equals} - case included. Two maps carried a name the v84 client never sends,
 *       so those portals answered nothing at all.
 *   <li><b>Door portals are re-ided.</b> {@code pt} 6 nodes get a synthetic {@code 0x80 + n}
 *       instead of their node name, so their order only decides which mystic-door slot is which -
 *       never an arrival. El Nath's block of six was rotated by one against v84 anyway.
 * </ol>
 *
 * <p>Every section below was taken from the pristine v84 archive verbatim rather than hand-edited,
 * for the reason {@code 070e4f883} gives: a hand-authored move already lost a leaf once.
 *
 * <p><strong>Not a {@code *Test} class on purpose</strong>: {@link WZFiles#DIRECTORY} is a
 * {@code static final} resolved once per JVM and {@code MobSkillFactoryTest} repoints
 * {@code wz-path} at a {@code @TempDir}, hence the explicit {@link XMLWZFile} via {@link V84Wz}.
 */
class V84PortalIndexParityRealLoad {

    /**
     * The four Omega Sector maps whose slots were genuinely transposed - the only maps in this set
     * where a player actually arrived at the wrong portal. Each is a pure permutation of v84's own
     * array: the multiset of {@code (pn, pt, x, y, tm, tn, script)} was already equal, only the
     * order was not. Spelled out in full because a count check cannot see a transposition.
     */
    private static final Map<Integer, String[]> OMEGA_SECTOR = new LinkedHashMap<>(Map.of(
            221030200, new String[]{"sp", "sp", "sp", "sp", "east00", "west00"},
            221030300, new String[]{"sp", "sp", "sp", "hiden00", "east00", "west00"},
            221030500, new String[]{"sp", "sp", "sp", "sp", "hiden00", "east00", "west00"},
            221030501, new String[]{"sp", "sp", "sp", "sp", "west00"}));

    /** El Nath's door block in v84's order: our tree had the same six x's rotated by one. */
    private static final int[] EL_NATH_DOOR_X = {-464, -863, -782, -705, -621, -545};

    /**
     * map id -&gt; slot -&gt; the name the v84 client sends when that portal is clicked. Our tree
     * answered a different string, and {@code getPortal(String)} compares with {@code equals}, so
     * the lookup returned null and the click did nothing.
     */
    private static final Map<Integer, Map<Integer, String>> CLIENT_SENT_NAME = Map.of(
            105100100, Map.of(6, "OutPerrion"),            // was outPerrion - case only
            106021400, Map.of(2, "TD_MC_enterboss1"));     // was right00

    /** The {@code pt} 6/0 nodes v84 names {@code tp} and our tree named {@code np}. */
    private static final Map<Integer, int[]> TOWN_PORTAL_SLOTS = new LinkedHashMap<>(Map.of(
            682000000, new int[]{8, 9, 10},
            801000100, new int[]{1},
            801000200, new int[]{1},
            801010000, new int[]{1, 2},
            801030000, new int[]{1}));

    @Test
    void omegaSectorSlotsHoldWhatTheV84ClientHoldsThere() {
        Map<Integer, List<String>> actual = new TreeMap<>();
        Map<Integer, List<String>> expected = new TreeMap<>();
        for (Map.Entry<Integer, String[]> e : OMEGA_SECTOR.entrySet()) {
            expected.put(e.getKey(), List.of(e.getValue()));
            actual.put(e.getKey(), portalOrder(e.getKey(), e.getValue().length));
        }
        assertEquals(expected, actual,
                "an Omega Sector slot holds a different portal than the v84 client's slot of the "
                        + "same number, so arriving on this map lands at the wrong portal");
    }

    /**
     * The two transposed slots that a stored {@code characters.spawnpoint} could name. Unlike the
     * maps in {@link V84TownIndexParityRealLoad}, these two do move an {@code sp}, so this pins
     * exactly which - a stale stored id still resolves to a real portal on the same small map and
     * is rewritten on the next save, but if the set grows, that is worth knowing.
     */
    @Test
    void onlyTwoTransposedSlotsAreOnesSpawnpointCouldHold() {
        Map<Integer, List<String>> eligible = new TreeMap<>();
        for (Map.Entry<Integer, int[]> e : Map.of(221030500, new int[]{3, 4},
                221030501, new int[]{2, 4}).entrySet()) {
            Data portals = section(e.getKey(), "portal");
            for (int slot : e.getValue()) {
                Data node = portals.getChildByPath(String.valueOf(slot));
                assertNotNull(node, "map " + e.getKey() + " slot " + slot);
                int pt = DataTool.getInt("pt", node, -1);
                if ((pt == 0 || pt == 1) && DataTool.getInt("tm", node, -1) == 999999999) {
                    eligible.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(String.valueOf(slot));
                }
            }
        }
        assertEquals(Map.of(221030500, List.of("3"), 221030501, List.of("2")), eligible,
                "the set of transposed spawnpoint-eligible slots changed");
    }

    @Test
    void elNathsDoorBlockIsInV84sOrder() {
        Data portals = section(211000000, "portal");
        List<Integer> actual = new ArrayList<>();
        for (int slot = 18; slot < 18 + EL_NATH_DOOR_X.length; slot++) {
            Data node = portals.getChildByPath(String.valueOf(slot));
            assertNotNull(node, "El Nath slot " + slot);
            assertEquals("tp", DataTool.getString("pn", node, null), "slot " + slot + " is not a door");
            actual.add(DataTool.getInt("x", node, Integer.MIN_VALUE));
        }
        List<Integer> expected = new ArrayList<>();
        for (int x : EL_NATH_DOOR_X) {
            expected.add(x);
        }
        assertEquals(expected, actual,
                "El Nath's door portals are rotated against v84 - PortalFactory hands these "
                        + "0x80 + n, so the rotation decides which mystic-door slot is which");
    }

    @Test
    void everyPortalAnswersTheNameTheV84ClientSends() {
        Map<String, String> wrong = new TreeMap<>();
        for (Map.Entry<Integer, Map<Integer, String>> e : CLIENT_SENT_NAME.entrySet()) {
            Data portals = section(e.getKey(), "portal");
            for (Map.Entry<Integer, String> slot : e.getValue().entrySet()) {
                Data node = portals.getChildByPath(String.valueOf(slot.getKey()));
                String name = node == null ? "<no such slot>" : DataTool.getString("pn", node, "<none>");
                if (!slot.getValue().equals(name)) {
                    wrong.put(e.getKey() + "/" + slot.getKey(), name);
                }
            }
        }
        assertEquals(Map.of(), wrong,
                "ChangeMapHandler looks the client's string up with String.equals, so a portal "
                        + "named anything else - case included - is unclickable");
    }

    /**
     * {@code PortalScriptManager} resolves {@code "portal/" + scriptName + ".js"} as a plain path,
     * so the {@code OutPerrion_*} rename that came with the {@code pn} fix has to be on disk under
     * v84's casing or the portal is silently dead everywhere the filesystem is case-sensitive.
     */
    @Test
    void perionExitScriptsExistUnderV84sCasing() {
        Data node = section(105100100, "portal").getChildByPath("6");
        assertNotNull(node, "105100100 portal slot 6");
        assertEquals("OutPerrion_1", DataTool.getString("script", node, null));
        for (String script : List.of("OutPerrion_1", "OutPerrion_2")) {
            Path file = Path.of("scripts", "portal", script + ".js");
            assertTrue(Files.isRegularFile(file), file + " is missing, so the portal does nothing");
        }
    }

    @Test
    void theDoorSlotsV84NamesTpAreNamedTp() {
        Map<String, String> wrong = new TreeMap<>();
        for (Map.Entry<Integer, int[]> e : TOWN_PORTAL_SLOTS.entrySet()) {
            Data portals = section(e.getKey(), "portal");
            for (int slot : e.getValue()) {
                Data node = portals.getChildByPath(String.valueOf(slot));
                String name = node == null ? "<no such slot>" : DataTool.getString("pn", node, "<none>");
                if (!"tp".equals(name)) {
                    wrong.put(e.getKey() + "/" + slot, name);
                }
            }
        }
        assertEquals(Map.of(), wrong, "these slots drifted back off v84's names");
    }

    @Test
    void kerningSquareComePortalSitsWhereV84PutsIt() {
        Data node = section(101000400, "portal").getChildByPath("1");
        assertNotNull(node, "101000400 portal slot 1");
        assertEquals("come00", DataTool.getString("pn", node, null));
        assertEquals(1442, DataTool.getInt("x", node, Integer.MIN_VALUE), "v84 x");
        assertEquals(-120, DataTool.getInt("y", node, Integer.MIN_VALUE), "v84 y");
    }

    @Test
    void portalSlotsAreConsecutiveFromZero() {
        Map<Integer, String> broken = new TreeMap<>();
        for (int mapId : new int[]{101000400, 105100100, 106021400, 211000000, 221000000,
                221030200, 221030300, 221030500, 221030501, 222000000, 240000000,
                682000000, 801000100, 801000200, 801010000, 801030000}) {
            Data portals = section(mapId, "portal");
            int size = portals.getChildren().size();
            for (int i = 0; i < size; i++) {
                if (portals.getChildByPath(String.valueOf(i)) == null) {
                    broken.put(mapId, "missing slot " + i);
                    break;
                }
            }
        }
        assertEquals(Map.of(), broken,
                "a gap in the portal array shifts every later index against the client");
    }

    private static List<String> portalOrder(int mapId, int size) {
        Data portals = section(mapId, "portal");
        assertEquals(size, portals.getChildren().size(), "map " + mapId + " portal count");
        List<String> actual = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            Data node = portals.getChildByPath(String.valueOf(i));
            actual.add(node == null ? "<missing>" : DataTool.getString("pn", node, "<none>"));
        }
        return actual;
    }

    private static Data section(int mapId, String name) {
        String path = String.format("Map/Map%d/%09d.img", mapId / 100000000, mapId);
        Data image = wz("Map.wz").getData(path);
        assertNotNull(image, "Map.wz has no image for map " + mapId);
        Data node = image.getChildByPath(name);
        assertNotNull(node, "map " + mapId + " has no " + name + " node");
        return node;
    }
}
