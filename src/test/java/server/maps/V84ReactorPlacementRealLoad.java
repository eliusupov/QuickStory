package server.maps;

import org.junit.jupiter.api.Test;
import provider.Data;
import provider.DataProviderFactory;
import provider.DataTool;
import provider.wz.WZFiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Ticket 61 / row R11 - the 30 server-read {@code reactor} entries merged from the v84 carve.
 *
 * <pre>
 *   mvnw.cmd test -Dtest=V84ReactorPlacementRealLoad
 * </pre>
 *
 * <p>{@code MapFactory:294-298} iterates the {@code reactor} array and {@code loadReactor:356-362}
 * reads exactly {@code id}, {@code x}, {@code y}, {@code reactorTime} and {@code f} off each entry,
 * so a missing array index is a reactor that does not exist on the running server. Every tuple
 * below was read out of {@code porting-resources/wz-data/v84/Map.wz} with {@code WzPeek dump} -
 * none of it is derived.
 *
 * <p><strong>Asserted off the wz {@link Data}, not off a loaded {@link MapleMap}</strong>, the same
 * way {@code ForestHallRealLoad} does. {@code loadReactor} calls
 * {@code Reactor.resetReactorActions} -> {@code refreshReactorTimeout}, which schedules on
 * {@code TimerManager} whenever the reactor's stats carry a {@code timeOut}; 1002002, 1002003 and
 * 1002006 all do, and {@code TimerManager.ses} is null in a JVM where nothing called
 * {@code start()}. Reading the five fields is what {@code loadReactor} does anyway.
 *
 * <p><strong>109090300 (Sheep Ranch) is deliberately not v84's array.</strong> Our indices 0-13
 * carry five reactors the carve does not have at all - four {@code 1002001} and one extra
 * {@code 1002002} - and our 10-13 are the carve's 5-8 at a shifted slot. Taking the carve for 5-13
 * would delete five live reactors (ticket 46 forbids it) for no server-visible gain, since the slot
 * number is iterated and never read. So 0-13 are ours, 14-31 are v84's, and the divergence is
 * pinned here rather than left to drift.
 *
 * <p><strong>Not a {@code *Test} class on purpose</strong>: {@link WZFiles#DIRECTORY} is a
 * {@code static final} resolved once per JVM and {@code MobSkillFactoryTest} repoints
 * {@code wz-path} at a {@code @TempDir}.
 */
class V84ReactorPlacementRealLoad {

    /** The five fields {@code loadReactor} reads, in array order. */
    private record Placement(int id, int x, int y, int reactorTime, int f) {
    }

    /**
     * {@code 2302006} - the shipwreck treasure chest - has no {@code reactordrops} row and no
     * {@code scripts/reactor/2302006.js}. The twelve Aquarium reactors merged by ticket 61 are
     * therefore inert BY DESIGN, not by accident: {@code 156-evan-chain-drop-data.sql:261-266} put
     * quest 22407's drop on {@code 2302001} instead, whose {@code Reactor.wz} info string says
     * "deep sea" where the quest text says "shipwreck". Ticket 61 records that question and does
     * not resolve it. When {@link #the2302006ReactorsAreInertByDesign()} starts failing, someone
     * gave 2302006 behaviour and these twelve placements became load-bearing.
     */
    private static final int SHIPWRECK_CHEST = 2302006;

    private static final Map<Integer, List<Placement>> EXPECTED = Map.of(
            // ours 0-13 (five of them v83-only), then v84's 14-31 appended verbatim
            109090300, List.of(
                    new Placement(1002006, 313, 5, 0, 0),
                    new Placement(1002002, -142, -724, 0, 0),
                    new Placement(1002006, -44, 5, 0, 0),
                    new Placement(1002002, 136, -306, 0, 0),
                    new Placement(1009000, 554, -938, 20, 0),
                    new Placement(1002001, 922, -221, 20, 0),      // v83-only
                    new Placement(1002001, -667, -220, 20, 0),     // v83-only
                    new Placement(1002001, 32, 138, 20, 0),        // v83-only
                    new Placement(1002001, 351, -462, 20, 0),      // v83-only
                    new Placement(1002002, 135, -544, 0, 0),       // v83-only
                    new Placement(1002003, -228, -243, 15, 0),
                    new Placement(1002003, 494, -243, 15, 0),
                    new Placement(1009000, -1984, -865, 20, 1),
                    new Placement(1009000, 1564, -815, 20, 0),
                    new Placement(1002002, -1212, -56, 0, 0),      // 14, and v84 from here down
                    new Placement(1002003, -863, -58, 0, 0),
                    new Placement(1002003, 71, 123, 0, 0),
                    new Placement(1002002, -615, -241, 0, 0),
                    new Placement(1002002, -380, 117, 0, 0),
                    new Placement(1002002, 784, 123, 0, 0),
                    new Placement(1002002, -756, -714, 0, 0),
                    new Placement(1002003, -787, -478, 0, 0),
                    new Placement(1002003, -205, -475, 0, 0),
                    new Placement(1002002, 749, -481, 0, 0),
                    new Placement(1002003, 1379, -481, 0, 0),
                    new Placement(1002003, 2101, -359, 0, 0),
                    new Placement(1002002, 1913, -599, 0, 0),
                    new Placement(1002002, 1569, -52, 0, 0),
                    new Placement(1002003, 1287, 120, 0, 0),
                    new Placement(1002002, 2467, -301, 0, 0),
                    new Placement(1002002, 1231, -486, 0, 0),
                    new Placement(1002002, 1568, -250, 0, 0)),
            // the six Aquarium maps ARE v84's arrays exactly, pre-existing entries included
            230010400, List.of(
                    new Placement(2302000, -1220, 459, 40, 0),
                    new Placement(SHIPWRECK_CHEST, -1813, 559, 20, 1)),
            230020000, List.of(
                    new Placement(2302000, -1030, 399, 200, 0),
                    new Placement(SHIPWRECK_CHEST, -256, 679, 15, 0),
                    new Placement(SHIPWRECK_CHEST, -480, 620, 9, 1)),
            230040000, List.of(
                    new Placement(2302001, -249, 643, 600, 0),
                    new Placement(2302001, -895, 814, 480, 0),
                    new Placement(2302001, -755, 1639, 650, 0),
                    new Placement(2302001, -181, 66, 120, 0),
                    new Placement(2302001, -583, 1283, 200, 0),
                    new Placement(SHIPWRECK_CHEST, -149, 943, 10, 0),
                    new Placement(SHIPWRECK_CHEST, -878, 308, 12, 1)),
            230040100, List.of(
                    new Placement(2302001, -563, 1128, 360, 0),
                    new Placement(2302001, -736, 537, 300, 0),
                    new Placement(2302001, -146, 91, 300, 0),
                    new Placement(2302001, -89, 1141, 120, 0),
                    new Placement(2302001, -569, 1807, 250, 0),
                    new Placement(SHIPWRECK_CHEST, 4, 612, 18, 0),
                    new Placement(SHIPWRECK_CHEST, -346, 1473, 12, 0)),
            230040200, List.of(
                    new Placement(2302001, -587, 103, 320, 0),
                    new Placement(2302001, -408, 635, 500, 0),
                    new Placement(2302001, -267, 1346, 200, 0),
                    new Placement(2302001, -803, 1780, 180, 0),
                    new Placement(2302001, -765, 1170, 210, 0),
                    new Placement(SHIPWRECK_CHEST, -4, 285, 9, 1),
                    new Placement(SHIPWRECK_CHEST, -665, 1146, 12, 1),
                    new Placement(SHIPWRECK_CHEST, 202, 1453, 21, 0)),
            230040400, List.of(
                    new Placement(2302001, -147, 815, 60, 0),
                    new Placement(2302001, -595, 244, 180, 0),
                    new Placement(2302001, -70, 284, 200, 0),
                    new Placement(2302001, -632, 958, 140, 0),
                    new Placement(2302001, -544, 1749, 70, 0),
                    new Placement(2302001, 621, 1689, 90, 0),
                    new Placement(2302001, 465, 1302, 220, 0),
                    new Placement(SHIPWRECK_CHEST, 361, 318, 6, 1),
                    new Placement(SHIPWRECK_CHEST, -341, -76, 9, 0)));

    /** Count, slot order and every field {@code loadReactor} reads, on all seven maps. */
    @Test
    void everyOneOfTheSevenMapsCarriesExactlyTheReactorArrayTicket61Merged() {
        for (Map.Entry<Integer, List<Placement>> e : EXPECTED.entrySet()) {
            assertEquals(e.getValue(), placementsOf(e.getKey()),
                    "map " + e.getKey() + "'s reactor array moved. Every entry here was copied out "
                            + "of the v84 carve at porting-resources/wz-data/v84/Map.wz; a changed "
                            + "count means an index was added, dropped or renumbered.");
        }
    }

    /** The 12 Aquarium chests are inert by design; this fails the moment that stops being true. */
    @Test
    void the2302006ReactorsAreInertByDesign() throws IOException {
        assertFalse(Files.exists(Path.of("scripts", "reactor", SHIPWRECK_CHEST + ".js")),
                "a script for " + SHIPWRECK_CHEST + " appeared - the 12 Aquarium chests ticket 61 "
                        + "merged are no longer inert, so re-read ticket 61's open question about "
                        + "changeSet 156 putting quest 22407's drop on 2302001 instead");
        try (Stream<Path> sql = Files.walk(Path.of("src", "main", "resources", "db"))) {
            for (Path p : sql.filter(f -> f.toString().endsWith(".sql")).toList()) {
                assertFalse(Files.readString(p).contains(String.valueOf(SHIPWRECK_CHEST)),
                        p + " now mentions " + SHIPWRECK_CHEST + " - same open question as above");
            }
        }
    }

    /** Walks the array by slot NAME 0..n-1, which is how a hole or a renumber shows up. */
    private static List<Placement> placementsOf(int mapid) {
        String path = "Map/Map" + (mapid / 100000000) + "/" + mapid + ".img";
        Data map = DataProviderFactory.getDataProvider(WZFiles.MAP).getData(path);
        assertNotNull(map, "Map.wz/" + path + " is missing");
        Data reactors = map.getChildByPath("reactor");
        assertNotNull(reactors, mapid + " has no reactor array at all");

        List<Placement> out = new ArrayList<>();
        for (int i = 0; i < reactors.getChildren().size(); i++) {
            Data r = reactors.getChildByPath(String.valueOf(i));
            assertNotNull(r, mapid + " reactor array has a hole at index " + i
                    + " - MapFactory iterates it, so a hole is a silently dropped reactor");
            out.add(new Placement(
                    Integer.parseInt(DataTool.getString(r.getChildByPath("id"))),
                    DataTool.getInt(r.getChildByPath("x")),
                    DataTool.getInt(r.getChildByPath("y")),
                    DataTool.getInt(r.getChildByPath("reactorTime")),
                    DataTool.getInt(r.getChildByPath("f"), 0)));
        }
        return out;
    }
}
