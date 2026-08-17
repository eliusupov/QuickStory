package server;

import org.junit.jupiter.api.Test;
import provider.Data;
import provider.DataTool;
import provider.wz.WZFiles;
import provider.wz.XMLWZFile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static server.V84Wz.wz;

/**
 * Two things in {@code Map.wz} are <em>indices into the client's own copy of the map</em>, and the
 * server hands both to the client as bare numbers. If our tree numbers them differently from the
 * v84 client, the client resolves the number against its own array and the player sees the wrong
 * thing - which is exactly the bug this class was written for ("henesys npcs are all over the
 * place... im getting ported to random places on map").
 *
 * <ol>
 *   <li><b>Portal array position.</b> {@code PacketCreator.getWarpToMap} writes
 *       {@code portal.getId()}, and {@code PortalFactory.loadPortal} sets that id from the node's
 *       <em>name</em> - the array index. v84 inserts a portal named {@code unityPortal2} mid-array
 *       into 17 town maps, so every portal at or after that index was off by one.
 *       Clicking a portal was never affected (that is resolved by name, both in
 *       {@code ChangeMapHandler} and in {@code GenericPortal.enterPortal}); <em>arriving</em> was.
 *   <li><b>Foothold id.</b> {@code life/*&#47;fh} and every runtime
 *       {@code map.getFootholds().findBelow(pos).getId()} are ids in the server's foothold table.
 *       v84 re-saved these images and renumbered that table without moving a single platform, so
 *       our v83 ids named different platforms in the v84 client.
 * </ol>
 *
 * <p>Values are transcribed from the pristine v84 archive
 * ({@code porting-resources/wz-data/v84/Map.wz}, read with {@code docs/wz-baseline/tool-peek}).
 * Nothing here is invented - see the ticket for why {@code unityPortal2} is deliberately left
 * without a {@code scripts/portal/} implementation.
 *
 * <p><strong>Not a {@code *Test} class on purpose</strong>: {@link WZFiles#DIRECTORY} is a
 * {@code static final} resolved once per JVM and {@code MobSkillFactoryTest} repoints
 * {@code wz-path} at a {@code @TempDir}, hence the explicit {@link XMLWZFile} via {@link V84Wz}.
 */
class V84TownIndexParityRealLoad {

    /** map id -&gt; the array index v84 gives {@code unityPortal2}, and the map's total portal count. */
    private static final Map<Integer, int[]> UNITY_PORTAL = new LinkedHashMap<>(Map.ofEntries(
            Map.entry(100000000, new int[]{12, 35}),   // Henesys
            Map.entry(101000000, new int[]{28, 56}),   // Ellinia
            Map.entry(102000000, new int[]{14, 37}),   // Perion
            Map.entry(103000000, new int[]{23, 52}),   // Kerning City
            Map.entry(105040300, new int[]{12, 25}),   // Sleepywood
            Map.entry(120000000, new int[]{2, 18}),    // Nautilus
            Map.entry(200000200, new int[]{1, 8}),     // Orbis
            Map.entry(211000000, new int[]{11, 24}),   // El Nath
            Map.entry(220000000, new int[]{9, 47}),    // Ludibrium
            Map.entry(221000000, new int[]{3, 19}),    // Omega Sector
            Map.entry(222000000, new int[]{16, 25}),   // Korean Folk Town
            Map.entry(230000000, new int[]{12, 19}),   // Aquarium
            Map.entry(240000000, new int[]{4, 24}),    // Leafre
            Map.entry(250000000, new int[]{11, 29}),   // Mu Lung
            Map.entry(251000000, new int[]{7, 16}),    // Herb Town
            Map.entry(260000000, new int[]{19, 35}),   // Ariant
            Map.entry(261000000, new int[]{15, 44})    // Magatia
    ));

    /** The maps whose foothold table was renumbered back to v84's ids (geometry untouched). */
    private static final int[] FOOTHOLD_RENUMBERED = {
            100000000, 105040300, 109090300, 130000000, 211040400, 222000000, 240000000, 240070501};

    /**
     * Henesys in full, in v84's order. Henesys is spelled out rather than sampled because it is the
     * map the bug was reported from: if any index here moves, arrivals in Henesys are wrong again.
     */
    private static final String[] HENESYS_PORTALS = {
            "sp", "sp", "sp", "sp", "sp", "sp", "sp", "sp", "sp", "sp", "sp",
            "in03", "unityPortal2", "hp00", "hp00_1", "up00",
            "gm00", "gm01", "gm02", "gm03", "gm04",
            "west00", "east00", "east10", "in00", "in01", "in02", "hp01", "hp01_1",
            "tp", "tp", "tp", "tp", "tp", "tp"};

    /**
     * The two maps that were index-corrected a second time. Both had the right portals in the wrong
     * slots: {@code dda2d5f5a} appended the Frog House door at 15 where v84 has it at 4, and
     * {@code c07ade7db} wrote the farm's {@code in01} over slot 13 where v84 has {@code west00}.
     * Both commits reasoned that portals resolve by name so position does not matter - it does, for
     * arrivals, because {@code getWarpToMap} sends the position. Spelled out in full for the same
     * reason as Henesys: these two are on the owner's active path.
     */
    private static final String[] FROG_HOUSE_PORTALS = {          // 220000300
            "sp", "sp", "sp", "sp", "scr00", "h000", "h001", "west00", "east00",
            "in00", "in01", "in02", "in03", "in04", "in05", "in06"};

    private static final String[] EVAN_FARM_PORTALS = {           // 100030000
            "sp", "sp", "sp", "sp", "sp", "sp", "sp", "sp", "sp", "sp", "sp", "sp",
            "ntgo01", "west00", "in01", "in00", "h_east00", "h_west00", "east00"};

    @Test
    void everyTownCarriesUnityPortal2AtV84sIndex() {
        Map<Integer, String> wrong = new TreeMap<>();
        for (Map.Entry<Integer, int[]> e : UNITY_PORTAL.entrySet()) {
            Data portals = section(e.getKey(), "portal");
            int index = e.getValue()[0];
            Data node = portals.getChildByPath(String.valueOf(index));
            String name = node == null ? "<no such slot>" : DataTool.getString("pn", node, "<none>");
            if (!"unityPortal2".equals(name)) {
                wrong.put(e.getKey(), "slot " + index + " is " + name);
            }
        }
        assertEquals(Map.of(), wrong,
                "v84 puts unityPortal2 at these indices; a mismatch means every portal at or after "
                        + "it is off by one against the client, and arrivals land at the wrong portal");
    }

    @Test
    void unityPortal2CarriesV84sOwnNodeInHenesys() {
        Data node = section(100000000, "portal").getChildByPath("12");
        assertNotNull(node, "Henesys portal slot 12");
        assertEquals("unityPortal2", DataTool.getString("pn", node, null));
        assertEquals(8, DataTool.getInt("pt", node, -1), "v84 pt");
        assertEquals(5538, DataTool.getInt("x", node, -1), "v84 x");
        assertEquals(99, DataTool.getInt("y", node, -1), "v84 y");
        assertEquals(999999999, DataTool.getInt("tm", node, -1), "v84 tm");
        // The script is named by v84 and intentionally not implemented: a portal that does nothing
        // is correct parity (the client draws it either way), inventing a destination is not.
        assertEquals("unityPortal2", DataTool.getString("script", node, null));
    }

    @Test
    void everyTownHasV84sPortalCount() {
        Map<Integer, Integer> counts = new TreeMap<>();
        Map<Integer, Integer> expected = new TreeMap<>();
        for (Map.Entry<Integer, int[]> e : UNITY_PORTAL.entrySet()) {
            counts.put(e.getKey(), section(e.getKey(), "portal").getChildren().size());
            expected.put(e.getKey(), e.getValue()[1]);
        }
        assertEquals(expected, counts, "portal counts no longer match v84");
    }

    @Test
    void henesysPortalOrderIsV84sExactly() {
        assertEquals(List.of(HENESYS_PORTALS), portalOrder(100000000, HENESYS_PORTALS.length),
                "Henesys portal order drifted from v84");
    }

    @Test
    void frogHouseDoorSitsAtV84sIndexFour() {
        assertEquals(List.of(FROG_HOUSE_PORTALS), portalOrder(220000300, FROG_HOUSE_PORTALS.length),
                "220000300 portal order drifted from v84 - scr00 belongs at 4, and appending it "
                        + "instead pushes eleven portals one slot off the client");
    }

    @Test
    void evanFarmEntranceSitsAtV84sIndexThirteen() {
        assertEquals(List.of(EVAN_FARM_PORTALS), portalOrder(100030000, EVAN_FARM_PORTALS.length),
                "100030000 portal order drifted from v84 - west00 belongs at 13 and in01 at 14");
    }

    /**
     * The reorders above move no slot that {@code characters.spawnpoint} can hold, which is why they
     * ship without a correction changeset (unlike the 17 towns, see changeSet 164).
     * {@code findClosestPlayerSpawnpoint} only ever returns a {@code pt} 0 or 1 portal whose
     * {@code tm} is {@code MapId.NONE}; on both maps every such portal is an {@code sp} in the
     * untouched head of the array. If that stops being true, a stored index can go stale silently.
     */
    @Test
    void noReorderedSlotIsOneThatSpawnpointCouldHold() {
        Map<Integer, String> spawnable = new TreeMap<>();
        for (int[] mapAndFirstMovedSlot : new int[][]{{220000300, 4}, {100030000, 13}}) {
            Data portals = section(mapAndFirstMovedSlot[0], "portal");
            for (Data node : portals.getChildren()) {
                if (Integer.parseInt(node.getName()) < mapAndFirstMovedSlot[1]) {
                    continue;
                }
                int pt = DataTool.getInt("pt", node, -1);
                if ((pt == 0 || pt == 1) && DataTool.getInt("tm", node, -1) == 999999999) {
                    spawnable.put(mapAndFirstMovedSlot[0],
                            "slot " + node.getName() + " (" + DataTool.getString("pn", node, "?") + ")");
                }
            }
        }
        assertEquals(Map.of(), spawnable,
                "a moved slot is now spawnpoint-eligible, so characters.spawnpoint needs a "
                        + "correction changeset for that map");
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

    @Test
    void portalSlotsAreConsecutiveFromZero() {
        Map<Integer, String> broken = new TreeMap<>();
        for (int mapId : UNITY_PORTAL.keySet()) {
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
     * The v84 foothold ids for the four Henesys npcs whose {@code fh} moved furthest. Transcribed
     * from v84 {@code life}; under our old v83 numbering these were 126, 110, 136 and 120.
     */
    @Test
    void henesysNpcsStandOnV84sFootholdIds() {
        assertEquals(Map.of(1012000, 157, 1010100, 141, 1012109, 167, 9010002, 151),
                fhByNpc(100000000, 1012000, 1010100, 1012109, 9010002),
                "Henesys npc foothold ids drifted from v84 - the client will draw them on whatever "
                        + "platform these ids name in ITS table");
    }

    @Test
    void everyLifeRowStandsOnAFootholdThisMapActuallyHas() {
        Map<Integer, String> orphans = new TreeMap<>();
        for (int mapId : FOOTHOLD_RENUMBERED) {
            Set<Integer> ids = footholdIds(mapId);
            Data life = section(mapId, "life");
            for (Data entry : life.getChildren()) {
                int fh = DataTool.getInt("fh", entry, 0);
                if (fh != 0 && !ids.contains(fh)) {
                    orphans.put(mapId, "life slot " + entry.getName() + " cites foothold " + fh);
                    break;
                }
            }
        }
        assertEquals(Map.of(), orphans,
                "a life row names a foothold id that is not in its own map's table - the renumber "
                        + "was applied to one of the two and not the other");
    }

    private static Map<Integer, Integer> fhByNpc(int mapId, int... npcIds) {
        Set<Integer> wanted = new HashSet<>();
        for (int id : npcIds) {
            wanted.add(id);
        }
        Map<Integer, Integer> found = new TreeMap<>();
        for (Data entry : section(mapId, "life").getChildren()) {
            if (!"n".equals(DataTool.getString("type", entry, ""))) {
                continue;
            }
            int id = Integer.parseInt(DataTool.getString("id", entry, "-1").trim());
            if (wanted.contains(id)) {
                found.put(id, DataTool.getInt("fh", entry, -1));
            }
        }
        return found;
    }

    private static Set<Integer> footholdIds(int mapId) {
        Set<Integer> ids = new HashSet<>();
        for (Data layer : section(mapId, "foothold").getChildren()) {
            for (Data group : layer.getChildren()) {
                for (Data foothold : group.getChildren()) {
                    ids.add(Integer.parseInt(foothold.getName()));
                }
            }
        }
        assertTrue(ids.size() > 1, "map " + mapId + " has no footholds");
        return ids;
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
