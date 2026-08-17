package server;

import org.junit.jupiter.api.Test;
import provider.Data;
import provider.DataProvider;
import provider.DataProviderFactory;
import provider.DataTool;
import provider.wz.WZFiles;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Pins the life arrays of the maps taken to exact v84 parity, and pins the one that was deliberately
 * left alone.
 *
 * <pre>
 *   mvnw.cmd test -Dtest=V84MapLifeParityRealLoad
 * </pre>
 *
 * <p>The expected compositions are transcribed from {@code WzMerge dump} of the hash-verified v84
 * base tree at {@code D:\games\wz-stage\v84-base\Map.wz}, path {@code Map/Map1/<id>.img/life}. They
 * are exact counts, not floors, so an append puts the count over and a drop puts it under - either
 * way this fails.
 *
 * <p><strong>196000000 is the deliberate exception.</strong> v84 stock puts 22x 9200018 + 1x
 * 9200019 and <em>no npc</em> on that map; this tree runs it as Cafe PQ stage 5
 * ({@code scripts/event/CafePQ_5.js}, {@code entryMap = 196000000}) with npc 1052013 "Computer" as
 * the entry point. Replacing it with v84 stock would delete that npc and every PQ mob, so it was
 * not replaced. The assertion below is what makes a later blind replacement fail loudly.
 *
 * <p><strong>Not a {@code *Test} class on purpose</strong>: {@link WZFiles#DIRECTORY} is a
 * {@code static final} resolved once per JVM and {@code MobSkillFactoryTest} repoints {@code wz-path}
 * at a {@code @TempDir}.
 */
class V84MapLifeParityRealLoad {

    @Test
    void map106010000MatchesV84Stock() {
        assertEquals(Map.of("n 1072002", 1, "m 2110200", 1, "m 2220100", 31, "m 2220110", 11),
                composition(106010000),
                "106010000 life no longer matches the v84 dump (44 entries)");
        assertSlotsAreConsecutive(106010000, 44);
    }

    @Test
    void map106010100MatchesV84Stock() {
        assertEquals(Map.of("n 1040000", 1, "m 2110200", 3, "m 2220100", 12, "m 2220110", 13,
                        "m 2230101", 3),
                composition(106010100),
                "106010100 life no longer matches the v84 dump (32 entries)");
        assertSlotsAreConsecutive(106010100, 32);
    }

    /** See the class note: v84 parity here would delete the Cafe PQ stage-5 entry npc. */
    @Test
    void map196000000KeepsItsCafePqPopulationRatherThanV84Stock() {
        assertEquals(Map.of("n 1052013", 1, "m 5100000", 22, "m 5140000", 1),
                composition(196000000),
                "196000000 was replaced with v84 stock, which drops npc 1052013 (Computer) and "
                        + "breaks CafePQ_5's entry - or its Cafe PQ population moved");
        assertSlotsAreConsecutive(196000000, 24);
    }

    /** {@code "<type> <id>"} -> count, over every life entry of the map. */
    private static Map<String, Integer> composition(int mapId) {
        Data life = mapData(mapId).getChildByPath("life");
        assertNotNull(life, "map " + mapId + " has no life node");
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Data entry : life.getChildren()) {
            String key = DataTool.getString(entry.getChildByPath("type"), "?") + " "
                    + Integer.parseInt(DataTool.getString(entry.getChildByPath("id"), "-1").trim());
            counts.merge(key, 1, Integer::sum);
        }
        return new TreeMap<>(counts);
    }

    private static void assertSlotsAreConsecutive(int mapId, int expected) {
        Data life = mapData(mapId).getChildByPath("life");
        StringBuilder missing = new StringBuilder();
        for (int i = 0; i < expected; i++) {
            if (life.getChildByPath(String.valueOf(i)) == null) {
                missing.append(' ').append(i);
            }
        }
        assertEquals("", missing.toString().trim(),
                "map " + mapId + " life is missing slot(s):" + missing);
        assertEquals(expected, life.getChildren().size(),
                "map " + mapId + " life entry count");
    }

    private static Data mapData(int mapId) {
        DataProvider mapSource = DataProviderFactory.getDataProvider(WZFiles.MAP);
        Data mapData = mapSource.getData("Map/Map" + (mapId / 100000000) + "/"
                + String.format("%09d", mapId) + ".img");
        assertNotNull(mapData, "Map.wz has no image for map " + mapId);
        return mapData;
    }
}
