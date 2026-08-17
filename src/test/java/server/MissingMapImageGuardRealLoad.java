package server;

import org.junit.jupiter.api.Test;
import provider.wz.WZFiles;
import server.maps.MapFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The missing-map-image guard in {@code MapFactory.loadMapFromWz}, and the list of script warps it
 * currently catches.
 *
 * <pre>
 *   mvnw.cmd test -Dtest=MissingMapImageGuardRealLoad
 * </pre>
 *
 * <p>{@code XMLWZFile.getData} returns {@code null} for an image this tree does not ship
 * ({@code XMLWZFile:70-72}), and {@code MapFactory} used to dereference it one line later. The
 * player-visible effect was a bare {@code NullPointerException} logged by
 * {@code PortalScriptManager.executePortalScript} with no map id in it, so every one of the six
 * warps below looked identical in the log.
 *
 * <p><strong>Not a {@code *Test} class on purpose</strong>: {@link WZFiles#DIRECTORY} is a
 * {@code static final} resolved once per JVM and {@code MobSkillFactoryTest} repoints
 * {@code wz-path} at a {@code @TempDir}.
 */
class MissingMapImageGuardRealLoad {

    /**
     * Every script warp whose destination image this tree does not ship. Each one throws out of
     * {@code MapFactory} today; the guard is what makes the log line say which map. Shrinking this
     * set is progress - growing it is a new dead portal, so it is pinned exactly.
     */
    private static final Set<Integer> KNOWN_DEAD_WARP_TARGETS = new TreeSet<>(Set.of(
            3000000,        // scripts/quest/2573.js:20
            103000897,      // scripts/npc/1052007.js:67
            211060010,      // scripts/portal/lionCastle_enter.js:3
            912060200,      // scripts/quest/2568.js:19
            912060300));    // scripts/npc/1096005.js:27

    /** {@code name(<mapid>} for any warp-shaped call taking a numeric map literal. */
    private static final Pattern WARP_CALL =
            Pattern.compile("\\b\\w*(?:[Ww]arp|setMap|moveMap|setFieldMap)\\w*\\s*\\(\\s*(\\d{4,9})\\b");

    /** 999999999 is Nexon's "no map" sentinel, not a destination. */
    private static final int NO_MAP = 999999999;

    @Test
    void loadingAnAbsentMapImageNamesTheMapIdInsteadOfNpeing() {
        int absent = 999999998;
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> MapFactory.loadMapFromWz(absent, 0, 0, null),
                "an absent map image no longer throws - if the guard was dropped, the next line "
                        + "dereferences a null mapData and the caller sees a bare NPE");
        assertTrue(e.getMessage() != null && e.getMessage().contains(String.valueOf(absent)),
                "the guard threw without naming the map id, which is the whole point of it: "
                        + e.getMessage());
    }

    @Test
    void noScriptWarpsToAMapThisTreeLacksBeyondTheKnownDeadOnes() throws IOException {
        Set<Integer> present = mapIdsOnDisk();
        Set<Integer> dead = new TreeSet<>();

        try (Stream<Path> scripts = Files.walk(Path.of("scripts"))) {
            for (Path p : scripts.filter(f -> f.toString().endsWith(".js")).toList()) {
                Matcher m = WARP_CALL.matcher(Files.readString(p));
                while (m.find()) {
                    int mapid = Integer.parseInt(m.group(1));
                    if (mapid != NO_MAP && !present.contains(mapid)) {
                        dead.add(mapid);
                    }
                }
            }
        }

        assertEquals(KNOWN_DEAD_WARP_TARGETS, dead,
                "the set of script warps with no map image moved. A NEW id means a script points at "
                        + "a map this tree does not ship, and that warp throws out of MapFactory; a "
                        + "MISSING id means the map landed - drop it from the list.");
    }

    private static Set<Integer> mapIdsOnDisk() throws IOException {
        Set<Integer> ids = new TreeSet<>();
        try (Stream<Path> imgs = Files.walk(Path.of(WZFiles.DIRECTORY, "Map.wz", "Map"))) {
            imgs.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".img.xml"))
                    .map(n -> n.substring(0, n.length() - ".img.xml".length()))
                    // Map.wz/Map holds non-map images too - AreaCode.img.xml has been there since the
                    // original import, and Files.walk reaches it. Only numeric names are map ids.
                    .filter(n -> n.chars().allMatch(Character::isDigit))
                    .forEach(n -> ids.add(Integer.parseInt(n)));
        }
        assertTrue(ids.size() > 5000, "only " + ids.size() + " map images found under "
                + WZFiles.DIRECTORY + " - another test class won the WZFiles.DIRECTORY race, so "
                + "this says nothing");
        return ids;
    }
}
