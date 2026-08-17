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

    // ---- rows taken from v84 ---------------------------------------------------------------

    /** 926100203 "Yulete's Office" had an empty life node; v84's five investigation markers now fill it. */
    @Test
    void map926100203GainsTheAranInvestigationMarkers() {
        assertEquals(Map.of("n 2112007", 5), composition(926100203),
                "926100203 no longer carries v84's five 2112007 markers");
        assertSlotsAreConsecutive(926100203, 5);
    }

    /**
     * The one authorised deletion. v84 rebalances Ant Tunnel IV onto 2230131 by substitution:
     * 17 -> 37, against 42 -> 15 Horny and 6 -> 2 Zombie Mushrooms. Additive-only would have left
     * 86 spawn points against v84's 55, so this map was taken verbatim instead. Safe because the
     * 31 dropped rows are duplicate spawn slots of mobs that all remain on the map - unlike
     * 196000000 below, where the deletion would have removed the only instance of something.
     */
    @Test
    void map105050300MatchesV84Stock() {
        assertEquals(Map.of("n 1063007", 1, "m 2110200", 15, "m 2230101", 2, "m 2230131", 37),
                composition(105050300),
                "105050300 life no longer matches the v84 dump (55 entries)");
        assertSlotsAreConsecutive(105050300, 55);
    }

    /** v84 swaps 9010021 for 1202010 here; additive-only keeps both. */
    @Test
    void map140010111GainsPudinAndKeepsRyko() {
        Map<String, Integer> life = composition(140010111);
        assertEquals(1, life.getOrDefault("n 1202010", 0),
                "140010111 is missing v84's npc 1202010 (Pudin)");
        assertEquals(1, life.getOrDefault("n 9010021", 0),
                "140010111 dropped npc 9010021 (Wolf Spirit Ryko)");
        assertSlotsAreConsecutive(140010111, 12);
    }

    /** v84 puts a Master Dummy in the beginner practice field; ours had only the Practice Chart. */
    @Test
    void map250020000GainsTheMasterDummy() {
        assertEquals(Map.of("m 5090001", 1, "m 5120503", 12, "n 2096000", 2),
                composition(250020000),
                "250020000 is missing v84's mob 5090001 (Master Dummy)");
        assertSlotsAreConsecutive(250020000, 15);
    }

    /** The largest coordinate move taken from v84: Chico walks 624px east. */
    @Test
    void map220000300PlacesChicoWhereV84Does() {
        Data chico = mapData(220000300).getChildByPath("life/0");
        assertEquals("2040014", DataTool.getString(chico.getChildByPath("id"), "").trim());
        assertEquals(-1002, DataTool.getInt(chico.getChildByPath("x")), "220000300 npc 2040014 x");
        assertEquals(99, DataTool.getInt(chico.getChildByPath("y")), "220000300 npc 2040014 y");
        assertEquals(46, DataTool.getInt(chico.getChildByPath("fh")), "220000300 npc 2040014 fh");
    }

    // ---- rows deliberately NOT taken from v84 ------------------------------------------------

    /**
     * v84 statically places 9901517-9901537 here. Upstream deleted exactly those rows in
     * {@code fca7b2ada} when the Hall of Fame became the DB-driven {@link server.life.PlayerNPC}
     * system, so restoring them would double-place every Knights Chamber pnpc.
     */
    @Test
    void map130000110KeepsItsLifeEmptyForThePlayerNpcSystem() {
        Data life = mapData(130000110).getChildByPath("life");
        assertNotNull(life, "130000110 lost its life node");
        assertEquals(0, life.getChildren().size(),
                "130000110 got v84's static 9901517-9901537 back; those are deployed from the "
                        + "playernpcs table and would now appear twice");
    }

    /**
     * v84 gives the CWKPQ Pirate Mastery Room respawning mobs. {@code ca3838050} set every one of
     * them to {@code mobTime -1} (spawn once) so the stage can be cleared; parity here would make
     * it unclearable.
     */
    @Test
    void map610030550KeepsItsNonRespawningPqMobs() {
        Data life = mapData(610030550).getChildByPath("life");
        assertEquals(25, life.getChildren().size(), "610030550 life entry count");
        for (Data entry : life.getChildren()) {
            assertEquals(-1, DataTool.getInt(entry.getChildByPath("mobTime"), 0),
                    "610030550 life/" + entry.getName() + " respawns again; CWKPQ needs mobTime -1");
        }
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
