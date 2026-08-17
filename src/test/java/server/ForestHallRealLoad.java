package server;

import org.junit.jupiter.api.Test;
import provider.Data;
import provider.DataProvider;
import provider.DataProviderFactory;
import provider.DataTool;
import provider.wz.WZFiles;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Map 100030301 "Forest Hall" - the Evan Lv.200 hall of fame behind the dragon egg on the farm.
 *
 * <pre>
 *   mvnw.cmd test -Dtest=ForestHallRealLoad
 * </pre>
 *
 * <p>Imported from the pristine v84 archive (SHA256-identical to
 * {@code porting-resources/wz-data/v84/Map.wz}, itself confirmed byte-identical to a fresh carve of
 * {@code GMSSetupv84.exe}) via {@code WzMerge xml}, with its ten {@code life} rows on npc ids
 * 9901910-9901919 dropped. Those ids are inside the band {@code PlayerNPC} allocates at runtime,
 * and this server's hall of fame is DB-driven - {@code PlayerNPCPodium} computes every position and
 * never reads map {@code life} - so a static row there is a collision that buys nothing.
 *
 * <p>Everything asserted below is what {@code MapFactory.loadMapFromWz} actually dereferences, so
 * this fails on a map that parses but cannot load.
 *
 * <p><strong>Not a {@code *Test} class on purpose</strong>: {@link WZFiles#DIRECTORY} is a
 * {@code static final} resolved once per JVM and {@code MobSkillFactoryTest} repoints
 * {@code wz-path} at a {@code @TempDir}.
 */
class ForestHallRealLoad {

    private static final int FOREST_HALL = 100030301;
    private static final int FARM_CENTER = 100030300;
    private static final int STELE = 1013106;

    /** {@code NpcId.PLAYER_NPC_BASE} .. the top of the band PlayerNPC.java:66-72 documents. */
    private static final int ALLOCATOR_LO = 9900000;
    private static final int ALLOCATOR_HI = 9906599;

    @Test
    void forestHallHoldsTheSteleAndNothingFromThePlayerNpcBand() {
        Data life = mapData(FOREST_HALL).getChildByPath("life");
        Data stele = life.getChildByPath("0");

        assertEquals(1, life.getChildren().size(), "100030301 life should be exactly the Glowing Stele");
        assertNotNull(stele, "100030301 has no life/0");
        assertEquals(String.valueOf(STELE), DataTool.getString(stele.getChildByPath("id")),
                "100030301's one life row is no longer npc " + STELE);
        assertEquals("n", DataTool.getString(stele.getChildByPath("type")),
                "the stele must stay an npc, not a mob");

        for (Data row : life) {
            int id = Integer.parseInt(DataTool.getString(row.getChildByPath("id")));
            assertFalse(id >= ALLOCATOR_LO && id <= ALLOCATOR_HI,
                    "life row on npc " + id + " sits in the PlayerNPC allocator band - v84's ten "
                            + "9901910-9901919 anchors were dropped on purpose, see this class's javadoc");
        }
    }

    /**
     * {@code MapFactory} builds a {@code FootholdTree} and {@code loadLife} pins each life to a
     * foothold id. A row citing a foothold the map lacks puts the npc nowhere reachable.
     */
    @Test
    void everyLifeRowStandsOnAFootholdTheMapActuallyHas() {
        Data map = mapData(FOREST_HALL);

        Set<Integer> footholds = new HashSet<>();
        for (Data layer : map.getChildByPath("foothold")) {
            for (Data group : layer) {
                for (Data fh : group) {
                    footholds.add(Integer.parseInt(fh.getName()));
                }
            }
        }
        assertEquals(101, footholds.size(), "100030301 foothold count moved");

        for (Data row : map.getChildByPath("life")) {
            int fh = DataTool.getInt(row.getChildByPath("fh"));
            assertTrue(footholds.contains(fh),
                    "life/" + row.getName() + " (npc " + DataTool.getString(row.getChildByPath("id"))
                            + ") cites foothold " + fh + ", which this map does not have");
        }
    }

    /**
     * Slot names are positional arrays. {@code MapFactory} iterates children rather than indexing,
     * so a hole loads - but it is the signature of a botched hand-edit, and dropping ten rows from
     * the head of {@code life} is exactly the edit that leaves one.
     */
    @Test
    void lifeAndPortalSlotsRunConsecutivelyFromZero() {
        assertSlotsAreConsecutive(FOREST_HALL, "life", 1);
        assertSlotsAreConsecutive(FOREST_HALL, "portal", 2);
    }

    /**
     * The round trip {@code scripts/portal/inDragonEgg.js:8} depends on: the else-branch warps a
     * non-Evan into 100030301, and {@code out00} is the only way back out.
     */
    @Test
    void theOnlyExitResolvesToAPortalFarmCenterReallyHas() {
        Data out00 = null;
        for (Data portal : mapData(FOREST_HALL).getChildByPath("portal")) {
            if ("out00".equals(DataTool.getString(portal.getChildByPath("pn")))) {
                out00 = portal;
            }
        }
        assertNotNull(out00, "100030301 lost its out00 portal - the map becomes a one-way trip");
        assertEquals(FARM_CENTER, DataTool.getInt(out00.getChildByPath("tm")), "out00 target map");

        String target = DataTool.getString(out00.getChildByPath("tn"));
        assertEquals("in00", target, "out00 target portal");

        Set<String> farmCenterPortals = new HashSet<>();
        for (Data portal : mapData(FARM_CENTER).getChildByPath("portal")) {
            farmCenterPortals.add(DataTool.getString(portal.getChildByPath("pn")));
        }
        assertTrue(farmCenterPortals.contains(target),
                "100030300 has no portal named '" + target + "'; it has " + farmCenterPortals);
    }

    /** The {@code info} leaves {@code MapFactory} reads unconditionally, plus the map's name. */
    @Test
    void infoCarriesWhatMapFactoryReadsAndStringWzNamesIt() {
        Data info = mapData(FOREST_HALL).getChildByPath("info");

        assertEquals(FOREST_HALL, DataTool.getInt(info.getChildByPath("returnMap")), "returnMap");
        assertEquals("Bgm00/DragonDream", DataTool.getString(info.getChildByPath("bgm")), "bgm");
        assertEquals("Farm", DataTool.getString(info.getChildByPath("mapMark")), "mapMark");
        assertEquals(72, DataTool.getInt(info.getChildByPath("fieldLimit")), "fieldLimit");

        // VRTop == VRBottom would send MapFactory down the miniMap-bounds branch instead
        assertFalse(DataTool.getInt(info.getChildByPath("VRTop"))
                        == DataTool.getInt(info.getChildByPath("VRBottom")),
                "VRTop == VRBottom, so the map would be sized off miniMap rather than its VR box");

        // v84 has these present-and-EMPTY. Absent and empty are different states: MapFactory
        // substitutes the map id for "" but would NPE-guard differently on a missing node.
        assertEquals("", DataTool.getString(info.getChildByPath("onFirstUserEnter")),
                "onFirstUserEnter must stay present and empty, as v84 has it");
        assertEquals("", DataTool.getString(info.getChildByPath("onUserEnter")),
                "onUserEnter must stay present and empty, as v84 has it");

        Data name = wz(WZFiles.STRING).getData("Map.img").getChildByPath("victoria/" + FOREST_HALL);
        assertNotNull(name, "String.wz/Map.img/victoria/" + FOREST_HALL + " is gone");
        assertEquals("Forest Hall", DataTool.getString(name.getChildByPath("mapName")),
                "mapName drifted from v84's exact string (it carried a trailing space here once)");
        assertEquals("Farm Street", DataTool.getString(name.getChildByPath("streetName")), "streetName");
    }

    private static void assertSlotsAreConsecutive(int mapid, String section, int expected) {
        Data node = mapData(mapid).getChildByPath(section);
        StringBuilder missing = new StringBuilder();
        for (int i = 0; i < expected; i++) {
            if (node.getChildByPath(String.valueOf(i)) == null) {
                missing.append(' ').append(i);
            }
        }
        assertEquals("", missing.toString().trim(),
                mapid + " " + section + " is missing slot(s):" + missing
                        + " - a hole means a row was cut without renumbering");
        assertEquals(expected, node.getChildren().size(), mapid + " " + section + " entry count");
    }

    private static Data mapData(int mapid) {
        String path = "Map/Map" + (mapid / 100000000) + "/" + mapid + ".img";
        Data data = wz(WZFiles.MAP).getData(path);
        assertNotNull(data, "Map.wz/" + path + " is missing");
        return data;
    }

    private static DataProvider wz(WZFiles file) {
        return DataProviderFactory.getDataProvider(file);
    }
}
