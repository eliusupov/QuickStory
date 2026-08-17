package server.life.positioner;

import client.Job;
import constants.game.GameConstants;
import constants.id.NpcId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import provider.Data;
import provider.DataProvider;
import provider.DataProviderFactory;
import provider.DataTool;
import provider.wz.WZFiles;
import server.life.PlayerNPCFactory;
import server.maps.Foothold;
import server.maps.FootholdTree;
import server.maps.MapleMap;

import java.awt.Point;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins changeSet 163, the Hall of Fame seed, against the code that would otherwise have written it.
 * Every row in {@code 163-hall-of-fame-data.sql} claims to be what
 * {@code PlayerNPC#createPlayerNPCInternal} produces; this recomputes the claim from the real
 * {@code Map.wz} footholds and the real {@code GameConstants} mapping and fails on any drift.
 *
 * <pre>
 *   mvnw.cmd test -Dtest=HallOfFameSeedRealLoad
 * </pre>
 *
 * <p>The row that matters is 9901000. Quest 22402 "Meeting the Dragon Rider" names that npc, and it
 * is not an ordinary NPC id - it is {@code NpcId.PLAYER_NPC_BASE + 100 * branch} for the warrior
 * branch 10, so only a PlayerNPC can ever occupy it. A prior pass proved that placing a plain life
 * row in this band silently collides with the allocator, hence {@link #seededIdsStayInsideTheBand()}.
 *
 * <p><strong>Not a {@code *Test} class on purpose</strong>: {@link WZFiles#DIRECTORY} is a
 * {@code static final} resolved once per JVM and {@code MobSkillFactoryTest} points {@code wz-path}
 * at a {@code @TempDir}, so a real-wz class has to stay out of the default {@code *Test} run.
 */
class HallOfFameSeedRealLoad {

    private static final Path SEED =
            Path.of("src", "main", "resources", "db", "data", "163-hall-of-fame-data.sql");

    /** One seeded row: character job (for the hall mapping) and the values the SQL claims. */
    private record Row(String name, int job, int mapid, int scriptId, int podiumRank,
                       int x, int cy, int fh) {
    }

    /**
     * Creation order, highest level first - the same order the ranks in the SQL count up in. Job ids
     * are the live {@code characters} rows the seed selects by name; if one of them job-advances the
     * hall it belongs to can change, and {@link #eachCharacterIsInTheHallItsJobMapsTo()} says so.
     */
    private static final List<Row> ROWS = List.of(
            new Row("Shadow", 412, 103000008, 9901300, 0, 0, -8, 12),
            new Row("Robinhood", 312, 100000204, 9901200, 0, 0, -1, 12),
            new Row("Wall", 132, 102000004, 9901000, 0, 0, 33, 12),
            new Row("monkeyDluffy", 421, 103000008, 9901301, 1, -120, 71, 5),
            new Row("arikrab", 231, 101000004, 9901100, 0, 0, -8, 40),
            new Row("CaptianKid", 520, 120000105, 9901400, 0, 0, -41, 18));

    private static String seedSql;

    @BeforeAll
    static void readSeed() throws IOException {
        assertTrue(Files.isRegularFile(Path.of(WZFiles.DIRECTORY, "Map.wz", "Map", "Map1", "102000004.img.xml")),
                "wz-path resolved to '" + WZFiles.DIRECTORY + "', which holds no Hall of Warriors - "
                        + "another test class won the WZFiles.DIRECTORY race, so this says nothing");
        seedSql = Files.readString(SEED, StandardCharsets.UTF_8);
    }

    /**
     * The whole point of the changeSet: 9901000 is the warrior hall's first slot, and the character
     * the seed puts there is a warrior. {@code getHallOfFameBranch} decides the id, not the SQL.
     */
    @Test
    void questNpc9901000IsTheFirstWarriorSlotOnTheHallOfWarriors() {
        Row wall = ROWS.stream().filter(r -> r.scriptId() == 9901000).findFirst().orElseThrow();

        assertEquals(102000004, wall.mapid(), "quest 22402's npc has to be in the Hall of Warriors");
        assertTrue(Job.getById(wall.job()).isA(Job.WARRIOR), wall.name() + " is not a warrior");
        assertEquals(102000004, GameConstants.getHallOfFameMapid(Job.getById(wall.job())));
        assertEquals(NpcId.PLAYER_NPC_BASE + 100 * GameConstants.getHallOfFameBranch(Job.getById(wall.job()), 102000004),
                wall.scriptId(), "9901000 is warrior branch 10's base; the mapping moved");
        assertTrue(PlayerNPCFactory.isExistentScriptid(9901000),
                "Npc.wz/9901000.img is missing - the client would crash on the spawn");
    }

    /** Every seeded character lands in the hall its own job maps to, and in that hall's branch. */
    @Test
    void eachCharacterIsInTheHallItsJobMapsTo() {
        for (Row row : ROWS) {
            Job job = Job.getById(row.job());
            assertNotNull(job, row.name() + " has job id " + row.job() + ", unknown to Job");
            assertEquals(GameConstants.getHallOfFameMapid(job), row.mapid(), row.name() + " is in the wrong hall");
            assertTrue(GameConstants.isPodiumHallOfFameMap(row.mapid()),
                    row.name() + "'s hall is not a podium map, so the seeded position formula does not apply");
            assertTrue(GameConstants.canPnpcBranchUseScriptId(
                            GameConstants.getHallOfFameBranch(job, row.mapid()), row.scriptId()),
                    row.name() + "'s scriptid " + row.scriptId() + " is outside its own hall's branch");
            assertTrue(PlayerNPCFactory.isExistentScriptid(row.scriptId()),
                    "Npc.wz/" + row.scriptId() + ".img is missing");
        }
    }

    /**
     * The reserved PlayerNPC band. A row that drifts out of it merges into a live rank-NPC slot,
     * which is exactly the silent corruption ticket 42 found.
     */
    @Test
    void seededIdsStayInsideTheBand() {
        Matcher m = Pattern.compile("\\b(99\\d{5})\\b").matcher(seedSql);
        int seen = 0;
        while (m.find()) {
            int id = Integer.parseInt(m.group(1));
            assertTrue((id >= 9900000 && id <= 9906599) || id == 9977777,
                    id + " in " + SEED + " is outside the reserved PlayerNPC band 9900000-9906599 / 9977777");
            seen++;
        }
        assertTrue(seen >= ROWS.size(), "expected at least one id per seeded row, found " + seen);
    }

    /**
     * The position values. {@code createPlayerNPCInternal} writes
     * {@code getGroundBelow(PlayerNPCPodium.calcNextPos(rank, step))} plus {@code x +/- 50} and the
     * foothold under the result; recompute all of it off the real map and compare.
     */
    @Test
    void positionsMatchWhatThePodiumWouldHaveComputed() {
        for (Row row : ROWS) {
            MapleMap map = loadFootholds(row.mapid());
            Point ground = map.getGroundBelow(PlayerNPCPodium.calcNextPos(row.podiumRank(), 1));

            assertEquals(row.x(), ground.x, row.name() + " x");
            assertEquals(row.cy(), ground.y, row.name() + " cy");
            assertEquals(row.fh(), map.getFootholds().findBelow(ground).getId(), row.name() + " fh");

            // the derived table names its columns on the first UNION member only; drop those aliases
            String values = seedSql.replaceAll(" (?:cname|map|scriptid|x|cy|fh|wrank|jobrank)(?=[,)])", "");
            assertTrue(values.contains(row.x() + ", " + row.cy() + ", " + row.fh() + ","),
                    row.name() + ": " + SEED + " does not carry x/cy/fh " + row.x() + "/" + row.cy()
                            + "/" + row.fh());
        }
    }

    /**
     * The bug the seed depends on not being there any more: an empty podium map used to refuse its
     * first PlayerNPC outright and throw away the branch's lowest scriptid with it - 9901000.
     */
    @Test
    void anEmptyPodiumHallHandsOutItsFirstSlot() {
        assertEquals(new Point(0, -47), PlayerNPCPodium.calcNextPos(0, 1),
                "podium rank 0 moved; the seeded rank-0 rows no longer match the code");
        assertEquals(new Point(0, -47), PlayerNPCPodium.reorganizePlayerNpcs(loadFootholds(102000004), 1, List.of()),
                "an empty Hall of Warriors still refuses its first PlayerNPC, so a runtime deploy "
                        + "would burn scriptid 9901000 instead of using it");
    }

    /** Same foothold wiring {@code MapFactory} does, minus everything a real map load needs. */
    private static MapleMap loadFootholds(int mapid) {
        DataProvider mapSource = DataProviderFactory.getDataProvider(WZFiles.MAP);
        Data mapData = mapSource.getData("Map/Map" + (mapid / 100000000) + "/" + String.format("%09d", mapid) + ".img");
        assertNotNull(mapData, "no Map.wz entry for " + mapid);

        List<Foothold> all = new LinkedList<>();
        Point lBound = new Point();
        Point uBound = new Point();
        for (Data footRoot : mapData.getChildByPath("foothold")) {
            for (Data footCat : footRoot) {
                for (Data footHold : footCat) {
                    Foothold fh = new Foothold(
                            new Point(DataTool.getInt(footHold.getChildByPath("x1")), DataTool.getInt(footHold.getChildByPath("y1"))),
                            new Point(DataTool.getInt(footHold.getChildByPath("x2")), DataTool.getInt(footHold.getChildByPath("y2"))),
                            Integer.parseInt(footHold.getName()));
                    lBound.x = Math.min(lBound.x, fh.getX1());
                    uBound.x = Math.max(uBound.x, fh.getX2());
                    lBound.y = Math.min(lBound.y, fh.getY1());
                    uBound.y = Math.max(uBound.y, fh.getY2());
                    all.add(fh);
                }
            }
        }

        FootholdTree tree = new FootholdTree(lBound, uBound);
        all.forEach(tree::insert);

        MapleMap map = new MapleMap(mapid, 0, 0, 0, 1);
        map.setFootholds(tree);
        return map;
    }
}
