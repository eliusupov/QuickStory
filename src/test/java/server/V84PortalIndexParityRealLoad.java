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
 * <p>Every section below was taken from the pristine v84 archive rather than hand-edited, for the
 * reason {@code 070e4f883} gives: a hand-authored move already lost a leaf once. "From v84" means
 * one specific thing here - <b>v84 owns the client-facing fields, we own {@code script}</b>.
 * {@code pn/pt/x/y/tm/tn} and the slot number are resolved by the client against its own copy, so
 * they are v84's; {@code script} is read only by {@code PortalScriptManager} and never reaches the
 * wire, so ours survives unless v84 names one we actually have on disk. That division is what lets
 * the PQ maps take v84's data without losing the scripts that drive their stages.
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

    /**
     * The division of authority, as data: {@code pn/pt/x/y/tm/tn} and the slot number are what the
     * client resolves against its own copy, so they are v84's; {@code script} is read only by
     * {@code PortalScriptManager} and never reaches the wire, so it stays ours unless v84 names a
     * script we actually have on disk.
     *
     * <p>Taking v84's {@code tm}/{@code tn} costs nothing where we keep a script:
     * {@code GenericPortal.enterPortal} runs the script and <em>returns</em> - it only falls through
     * to {@code tm}/{@code tn} when {@code getScriptName()} is null. {@code MapPortal} is a bare
     * subclass that overrides nothing, so a {@code pt} of 2 does not change that.
     *
     * <p>{@code mapId, slot, pn, pt, tm, tn, script} - script {@code null} means no leaf.
     */
    private static final Object[][] CLIENT_FIELDS_V84_SCRIPT_OURS = {
            {970030001, 1, "out00", 2, 970030000, "out00", "raid_rest"},
            {990000000, 4, "st00", 2, 101030104, "st00", "guildwaitingexit"},
            {990000000, 5, "join00", 5, 990000100, "st00", "guildwaitingenter"},
            // slot 2 was our `sp`, a name the client never sends, leaving this map's only named
            // exit unclickable. Same x/y, so nothing about arriving here changes.
            {921100300, 2, "out00", 2, 211000000, "in01", ""},
            {921100300, 3, "out01", 7, 999999999, "", "s4common1_exit"},   // v84 wants s4common1_clear
            {300000010, 3, "in01", 8, 999999999, "", "jail_in"},           // v84 wants cellar
            // v84's own script names, written here rather than inherited: both gates turned out to
            // be derivable from Quest.wz. See theFourSlotsWeRefusedStillCarryOurWorkingRouting's
            // javadoc for 220011000's derivation, and scripts/portal/evanGolemDoor.js for the other.
            {220011000, 4, "in00", 7, 999999999, "", "enterBlackBC"},
            {106010101, 5, "in00", 7, 999999999, "", "evanGolemDoor"},
    };

    @Test
    void v84OwnsTheClientFacingFieldsAndWeKeepTheScript() {
        Map<String, String> wrong = new TreeMap<>();
        for (Object[] row : CLIENT_FIELDS_V84_SCRIPT_OURS) {
            Data node = section((Integer) row[0], "portal").getChildByPath(String.valueOf(row[1]));
            String key = row[0] + "/" + row[1];
            if (node == null) {
                wrong.put(key, "<no such slot>");
                continue;
            }
            String actual = DataTool.getString("pn", node, "<none>") + " pt=" + DataTool.getInt("pt", node, -1)
                    + " tm=" + DataTool.getInt("tm", node, -1) + " tn=" + DataTool.getString("tn", node, "<none>")
                    + " script=" + DataTool.getString("script", node, "<none>");
            String expected = row[2] + " pt=" + row[3] + " tm=" + row[4] + " tn=" + row[5] + " script=" + row[6];
            if (!expected.equals(actual)) {
                wrong.put(key, actual + "  (want " + expected + ")");
            }
        }
        assertEquals(Map.of(), wrong, "a portal stopped following v84 on a client-facing field, "
                + "or lost the script that is the only thing making it work");
    }

    /**
     * The four slots deliberately NOT taken from v84, each for a measured reason: each names a
     * {@code tn} that does not exist in the destination map <em>in v84's own archive either</em> -
     * they are dangling in v84 and our values are repairs. Following v84 there would send the
     * player through {@code GenericPortal}'s {@code to.getPortal(0)} fallback and dump him at slot
     * 0.
     *
     * <p>{@code 220011000} used to be the fifth, on the reading that {@code enterBlackBC} appears
     * exactly once in all 4848 v84 images "so there is nothing to derive its gate from". That was
     * wrong, and {@code Quest.wz} is where the derivation lives:
     * {@code QuestInfo.img/22583/1} names {@code 922030010} and {@code QuestInfo.img/22584/1}
     * names {@code 922030020}, the two are mutually exclusive because
     * {@code Check.img/22584/0/quest/0} requires 22583 at state 2, and the mobs those quests want
     * ({@code Check.img} 9300389 and 9300390) are each placed in exactly one map in the tree -
     * 922030011 and 922030022, both hanging off those two. Everything matching neither falls
     * through to {@code 220011001}, which is what our v83 warp already pointed at, so nothing lost
     * a route. The slot now lives in {@link #CLIENT_FIELDS_V84_SCRIPT_OURS}.
     */
    @Test
    void theFourSlotsWeRefusedStillCarryOurWorkingRouting() {
        Map<String, String> wrong = new TreeMap<>();
        for (Object[] row : new Object[][]{
                {300000100, 1, "out00", 222020400, "in01"},     // v84 says in00; 222020400 has no in00
                {610010000, 6, "U1_3", 682000000, "right01"},   // v84 says right00; 682000000 has no right00
                {610030020, 3, "out00", 610030010, "in06"},     // v84 says in00; 610030010 has no in00
                {914000200, 1, "east00", 914000100, "out00"}}) {  // v84 says west00; 914000100 has no west00
            Data node = section((Integer) row[0], "portal").getChildByPath(String.valueOf(row[1]));
            String key = row[0] + "/" + row[1];
            if (node == null) {
                wrong.put(key, "<no such slot>");
                continue;
            }
            String actual = DataTool.getString("pn", node, "<none>")
                    + " -> " + DataTool.getInt("tm", node, -1) + "/" + DataTool.getString("tn", node, "<none>");
            String expected = row[2] + " -> " + row[3] + "/" + row[4];
            if (!expected.equals(actual)) {
                wrong.put(key, actual + "  (want " + expected + ")");
            }
        }
        assertEquals(Map.of(), wrong, "one of the four refused slots was 'corrected' to v84 - v84 is "
                + "wrong on all four, see this method's javadoc before changing it back");
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
