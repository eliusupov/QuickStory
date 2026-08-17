package server;

import org.junit.jupiter.api.Test;
import provider.Data;
import provider.DataTool;
import provider.wz.WZFiles;
import provider.wz.XMLWZFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
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
 * The third and hardest batch of the bug {@link V84TownIndexParityRealLoad} documents. On the twenty
 * maps in {@link V84RenumberedFootholdParityRealLoad} v84 had renumbered the foothold table without
 * moving a platform, so an id-to-id bijection existed. On the fifty-two here it <em>re-laid the
 * terrain</em>: 1115 platforms v84 has that we did not, 976 of ours that it does not.
 *
 * <h2>Why replacing the table is a correctness fix, not a physics risk</h2>
 *
 * Ticket 53 framed this as dangerous - "a wrong swap does not misdraw an NPC, it drops players
 * through the floor". It does not, and the reason is worth writing down, because it is what made
 * the work safe to do at all:
 *
 * <ul>
 *   <li>{@code AbstractMovementPacketHandler.updatePosition} - the only path a player's position
 *       takes - reads the client's absolute x/y and {@code p.skip}s the foothold id the client
 *       sends with it. The server runs no collision, no gravity and no validation of either.
 *       Movement is client-authoritative; the client uses <em>its own</em> terrain, which is v84's.
 *   <li>Every {@code findBelow(...).getId()} in the tree mints an id purely to send it: scripted
 *       npc spawns, pets, mobs, {@code PlayerNPC}. {@code PacketCreator} writes it raw.
 *   <li>{@code Character.getFh} is not a foothold id at all - it returns {@code getY1()}, a
 *       coordinate, and only magic-door placement reads it.
 *   <li>{@code calcPointBelow} yields a coordinate, for drop landing and mob spawn anchoring.
 * </ul>
 *
 * So a foothold our table had and the client did not was a phantom platform - the server anchored
 * npcs, mobs and item drops to ground the player could never see - and a platform the client drew
 * that we lacked was invisible ground. Both were already-broken states; taking v84's table verbatim
 * is what makes the two agree. No path exists by which it can move a player.
 *
 * <h2>What was done</h2>
 *
 * v84's {@code foothold} section verbatim - all 99,556 leaves of it, checked key-for-key and
 * value-for-value against the archive - then every {@code life} row re-pointed, 718 rows, by three
 * rules in order:
 *
 * <ol>
 *   <li>the platform the row's own {@code cy} names, where exactly one v84 platform spans its x at
 *       that y (84 rows landed here, {@link #CY_ANCHORED}: our v83 id named the neighbouring
 *       overlapping platform, and on every one of the 84 {@code cy} agrees with v84's own row);
 *   <li>otherwise v84's id for the same geometry, where that platform survives;
 *   <li>otherwise the nearest v84 ground at or below the row (3 rows, {@link #REANCHORED}).
 * </ol>
 *
 * Nothing in {@code life} was added or deleted - see
 * {@link #theRowsAVerbatimLifeTakeWouldHaveDeletedAreStillHere()} and
 * {@link #noHallOfFameMapCarriesAStaticNpcInThePlayerNpcBand()}.
 *
 * <p>Digest as in {@link V84RenumberedFootholdParityRealLoad}: SHA-1 of {@code id|layer|group|geom}
 * per foothold in id order, first twelve hex characters, the same value
 * {@code WzPeek digest D:\games\MSv84\client\Map.wz} prints.
 *
 * <p><strong>Not a {@code *Test} class on purpose</strong>: {@link WZFiles#DIRECTORY} is a
 * {@code static final} resolved once per JVM and {@code MobSkillFactoryTest} repoints
 * {@code wz-path} at a {@code @TempDir}, hence the explicit {@link XMLWZFile} via {@link V84Wz}.
 */
class V84TerrainFootholdParityRealLoad {

    /** map id -&gt; v84's foothold-table digest. Trailing number is the foothold count it covers. */
    private static final Map<Integer, String> V84_FOOTHOLD_DIGEST = new LinkedHashMap<>(Map.ofEntries(
            Map.entry(100000204, "10a6d88660cf"),   // Victoria Road - Hall of Bowmen                   2
            Map.entry(100030000, "c410f6b5fbd1"),   // Victoria Road - The Forest East of Henesys     511
            Map.entry(101000000, "64de27036023"),   // Victoria Road - Ellinia                       1238
            Map.entry(101000004, "196531f4bf85"),   // Victoria Road - Hall of Magicians               67
            Map.entry(101030101, "894a3e9410eb"),   // Victoria Road - Excavation Site I              119
            Map.entry(102000000, "f619d5c534d0"),   // Victoria Road - Perion                         521
            Map.entry(102000004, "3692428152b3"),   // Victoria Road - Hall of Warriors                 6
            Map.entry(103000000, "554354ae599a"),   // Victoria Road - Kerning City                   518
            Map.entry(103000008, "9e999b6a1e3d"),   // Victoria Road - Hall of Thieves                  3
            Map.entry(105090310, "9d1b5b5c151b"),   // Dungeon - Drake Area                           661
            Map.entry(106010101, "9256103d33d8"),   // Hidden Street - The Breathing Rock              93
            Map.entry(106010102, "f601087a9ebc"),   // Victoria Road - Entrance of Golem's Temple     141
            Map.entry(106020000, "4b5bb14908bf"),   // Mushroom Castle - Mushroom Forest Field         40
            Map.entry(109090000, "aaf2eee12fab"),   // Hidden Street - Sheep Ranch Lobby               60
            Map.entry(120000105, "a8bbaf029ddc"),   // The Nautilus - Training Room                    12
            Map.entry(130000101, "4adb0637df8c"),   // Empress's Road - Knights Chamber                62
            Map.entry(140010110, "d93176e78240"),   // Snow Island - Palace of the Master               46
            Map.entry(195000000, "a5b12389e182"),   // Premium Road - Dangerous Ant-Hole              758
            Map.entry(200080600, "af085d8f90f3"),   // Orbis - Orbis Tower <16th Floor>               206
            Map.entry(211000102, "f7cd629f916c"),   // El Nath - El Nath Department Store              25
            Map.entry(220011001, "f231fc3edf90"),   // Ludibrium - Sky Terrace                         11
            Map.entry(240070502, "0b1f1dc2c31c"),   // Neo City - Dangerous Tower Emergency Exit       49
            Map.entry(250000000, "b366fd8b0098"),   // Mu Lung - Mu Lung                              590
            Map.entry(251000000, "c8e970d3b470"),   // Herb Town - Herb Town                          333
            Map.entry(261000000, "1f04f6318de1"),   // Sunset Road - Magatia                          739
            Map.entry(270000100, "089eb2baee61"),   // Time Lane - Temple of Time                      24
            Map.entry(300000012, "bc609ad1051b"),   // Camp Conference Room - Cellar                    2
            Map.entry(541000000, "d307e2d09adf"),   // Singapore - Boat Quay Town                     275
            Map.entry(541000100, "d7e3f41c5247"),   // Singapore - Mysterious Path 1                  278
            Map.entry(541000300, "4a28bcf996b7"),   // Singapore - Mysterious Path 3                  316
            Map.entry(541010110, "797991be60a3"),   // Singapore - The Peaceful Ship                  154
            Map.entry(551000000, "8505e6ffcb50"),   // Malaysia - Kampung Village                     301
            Map.entry(551000200, "5d9a2655b879"),   // Malaysia - Hibiscus Road 2                     147
            Map.entry(600000000, "822cd52273dd"),   // New Leaf City - Town Center                    699
            Map.entry(610010002, "47ee2810a41d"),   // Phantom Forest - Swamp Bog                     292
            Map.entry(610030000, "7800ceab2023"),   // Crimsonwood Keep - Courtyard                    40
            Map.entry(610030010, "d06fc99a41db"),   // Crimsonwood Keep - Hall of Mastery              606
            Map.entry(610030300, "a046b6d2aa65"),   // Party Quest - The Test of Agility              1028
            Map.entry(610030510, "505fb67425ba"),   // Party Quest - Warrior Mastery Room              385
            Map.entry(610030530, "69564c4c96cf"),   // Party Quest - Thief Mastery Room                263
            Map.entry(610030700, "a99dd3ce7d71"),   // Party Quest - Grandmaster Secret Chamber        108
            Map.entry(670010100, "c5d3b445b44d"),   // Hidden Street - Entrance of Amorian Challenge    65
            Map.entry(670010200, "ab5d2806a3cc"),   // Hidden Street - Stage 1 - Magik Mirror          481
            Map.entry(670010400, "2cb58bfe00d9"),   // Hidden Street - Stage 3 - Twisted Switcher       84
            Map.entry(670010600, "eb5363c34211"),   // Hidden Street - Stage 5 - Fluttering Hearts     351
            Map.entry(670010750, "33bcd3d5a10d"),   // Hidden Street - Stage 7 - Amos' Vault           695
            Map.entry(674030000, "5a095381713f"),   // Treasure Dungeon - Initiation                   397
            Map.entry(910510100, "d6b0210a595e"),   // Hidden Street - Puppeteer's Secret Passage      948
            Map.entry(921110000, "5adf6788926d"),   // Hidden Street - Rider's Field                   457
            Map.entry(922010800, "fffb1396d42f"),   // Hidden Street - Abandoned Tower <Stage 8>       450
            Map.entry(926120200, "7305091bba75"),   // Hidden Street - Dran's Lab                       23
            Map.entry(930000300, "bc8259926f5e")    // Forest of Poison Haze - Forest of Haze          912
    ));

    /**
     * The three rows whose platform v84 deleted outright - no id maps to it and no v84 platform sits
     * at their {@code cy} - so they fall to the nearest v84 ground at or below their own position,
     * which is what {@code findBelow} computes and what the client draws there.
     * {@code map id -> npc/mob id -> v84 foothold id}.
     *
     * <p>{@code 195000000}'s Computer is the one worth knowing about: it drops 274px, because it
     * stood on foothold layer 6, four platforms our tree carries that neither stock v83 nor v84 has
     * ({@code docs/wz-baseline/protect-list/Map.txt} lists both the layer and the npc). Nothing in
     * the client draws that ledge, so there was never ground under it on the owner's screen.
     */
    private static final Map<Integer, Map<Integer, Integer>> REANCHORED = Map.of(
            106020000, Map.of(1300000, 15),     // Mushking, down 30px
            195000000, Map.of(1052013, 480),    // Computer, down 274px, off a custom ledge
            610030510, Map.of(9400582, 11));

    /**
     * Rows settled by their own {@code cy} rather than by mapping the old id. 84 of the 718 rows
     * landed here: our v83 {@code fh} named a platform that still exists in v84 but is <em>not</em>
     * the one the row stands on - the neighbouring, overlapping platform is. {@code cy} records the
     * y the row sits at and survives any renumber untouched, so it is what breaks the tie, and on
     * all 84 it agrees with the foothold v84's own life row cites. Sampled here.
     */
    private static final Map<Integer, Map<Integer, Integer>> CY_ANCHORED = Map.of(
            103000000, Map.of(2042002, 504),    // Spiegelmann
            250000000, Map.of(2091008, 47),
            541000000, Map.of(9000020, 202),    // Spinel
            910510100, Map.of(1063017, 179),
            930000300, Map.of(2133001, 700));

    /**
     * Ellinia, the largest delta of the fifty-two: 160 platforms added, 125 removed. Sampled at the
     * npcs a player actually walks into. Under our old v83 ids these were 1044, 621, 313 and 599 -
     * every one of them naming a different platform in the v84 client.
     */
    private static final Map<Integer, Integer> ELLINIA_NPC_FH =
            Map.of(1032000, 957,    // Regular Cab
                    1032100, 314,   // Arwen the Fairy
                    1032101, 112,   // Rowen the Fairy
                    9010003, 601,   // Ria
                    9010022, 70);   // Dimensional Mirror

    /**
     * The rows ticket 53 listed as casualties of a verbatim {@code life} take. None were taken: the
     * ticket-46 precedent ({@code 196000000}, where v84 parity would have deleted the Cafe PQ entry
     * npc and was refused) governs. They are kept and re-pointed with everything else.
     */
    private static final Map<Integer, int[]> KEPT_ROWS = Map.of(
            101000000, new int[]{1022101, 9250052},           // seasonal limitedname=xmasvillage
            102000000, new int[]{1022101, 9250052},
            103000000, new int[]{1022101, 1052012, 9000036},  // + Mong from Kong, + Agent E
            250000000, new int[]{1022101},
            251000000, new int[]{1022101, 9000036},
            600000000, new int[]{9000040, 9000041, 9010009},  // Dalair, Donation Box, Duey
            195000000, new int[]{1052013},                    // Computer, on a custom foothold layer
            140010110, new int[]{9010021},
            670010100, new int[]{9201047},
            211000102, new int[]{9100110});

    /**
     * The one life row whose {@code x} sits outside the platform it cites - by two pixels, on stock
     * data we inherited (the id changed, the geometry did not, so it was already like this). Listed
     * so {@link #everyLifeRowStandsOnAPlatformSpanningItsOwnX()} can be exact about the other 733.
     */
    private static final Set<String> OFF_PLATFORM_IN_STOCK_DATA = Set.of("674030000/9220020");

    @Test
    void everyTerrainMapCarriesV84sFootholdTable() {
        Map<Integer, String> actual = new TreeMap<>();
        for (int mapId : V84_FOOTHOLD_DIGEST.keySet()) {
            actual.put(mapId, footholdDigest(mapId));
        }
        assertEquals(new TreeMap<>(V84_FOOTHOLD_DIGEST), actual,
                "a foothold table drifted from v84 - the client resolves every id the server sends "
                        + "against ITS own table, so npcs, mobs, pets and item drops on that map are "
                        + "back on whatever platform the id happens to name there");
    }

    @Test
    void everyLifeRowStandsOnAFootholdThisMapActuallyHas() {
        Map<Integer, String> orphans = new TreeMap<>();
        for (int mapId : V84_FOOTHOLD_DIGEST.keySet()) {
            Set<Integer> ids = footholdIds(mapId);
            for (Data entry : lifeRows(mapId)) {
                int fh = DataTool.getInt("fh", entry, 0);
                if (fh != 0 && !ids.contains(fh)) {
                    orphans.put(mapId, "life slot " + entry.getName() + " cites foothold " + fh);
                    break;
                }
            }
        }
        assertEquals(Map.of(), orphans,
                "a life row names a foothold id that is not in its own map's table - the table was "
                        + "replaced and the row was not re-pointed with it");
    }

    /**
     * Id existence is not enough: a re-anchor that picked the wrong platform still yields a valid
     * id. This is the check that catches it - the cited foothold has to span the row's own x, and
     * lie at or below it, which is what {@code FootholdTree.findBelow} would have computed.
     */
    @Test
    void everyLifeRowStandsOnAPlatformSpanningItsOwnX() {
        List<String> wrong = new ArrayList<>();
        for (int mapId : V84_FOOTHOLD_DIGEST.keySet()) {
            Map<Integer, int[]> geom = footholdGeometry(mapId);
            for (Data entry : lifeRows(mapId)) {
                int fh = DataTool.getInt("fh", entry, 0);
                if (fh == 0) {
                    continue;   // our own "unanchored" sentinel; v84 emits it nowhere, and it predates this work
                }
                int[] g = geom.get(fh);
                assertNotNull(g, mapId + " life slot " + entry.getName() + " cites absent foothold " + fh);
                int x = DataTool.getInt("x", entry, 0);
                int y = DataTool.getInt("y", entry, 0);
                String key = mapId + "/" + DataTool.getString("id", entry, "?").trim();
                if (OFF_PLATFORM_IN_STOCK_DATA.contains(key)) {
                    continue;
                }
                if (x < Math.min(g[0], g[2]) || x > Math.max(g[0], g[2])) {
                    wrong.add(key + " x=" + x + " is off platform " + fh);
                } else if (footingAt(g, x) < y - 2) {
                    wrong.add(key + " y=" + y + " is below platform " + fh);
                }
            }
        }
        assertEquals(List.of(), wrong,
                "a life row was re-pointed at a foothold it does not actually stand on");
    }

    @Test
    void elliniaNpcsStandOnV84sFootholdIds() {
        assertEquals(new TreeMap<>(ELLINIA_NPC_FH),
                new TreeMap<>(fhByNpc(101000000, 1032000, 1032100, 1032101, 9010003, 9010022)),
                "Ellinia npc foothold ids drifted from v84 - it is the largest terrain delta of the "
                        + "fifty-two, so it is the first place a partial application shows up");
    }

    @Test
    void theReanchoredRowsLandOnV84Terrain() {
        assertEquals(pin(REANCHORED), read(REANCHORED),
                "a re-anchored row moved - these are the rows whose own v83 platform does not exist "
                        + "in v84 at all, so there is no mapping to fall back on");
    }

    @Test
    void theRowsSettledByTheirOwnCyStandWhereV84SaysTheyDo() {
        assertEquals(pin(CY_ANCHORED), read(CY_ANCHORED),
                "a cy-settled row moved - our v83 id named the neighbouring overlapping platform, "
                        + "and mapping that id forward would carry the wrong platform into v84");
    }

    private static Map<Integer, Map<Integer, Integer>> pin(Map<Integer, Map<Integer, Integer>> src) {
        Map<Integer, Map<Integer, Integer>> out = new TreeMap<>();
        src.forEach((k, v) -> out.put(k, new TreeMap<>(v)));
        return out;
    }

    private static Map<Integer, Map<Integer, Integer>> read(Map<Integer, Map<Integer, Integer>> src) {
        Map<Integer, Map<Integer, Integer>> out = new TreeMap<>();
        for (Map.Entry<Integer, Map<Integer, Integer>> e : src.entrySet()) {
            int[] wanted = e.getValue().keySet().stream().mapToInt(Integer::intValue).toArray();
            out.put(e.getKey(), new TreeMap<>(fhByLife(e.getKey(), wanted)));
        }
        return out;
    }

    /**
     * The refusal. A verbatim {@code life} take would have dropped these; every one is still here.
     * {@code 1052012} (Mong from Kong) and {@code 9000036} (Agent E) are the two ticket 53 singled
     * out as needing a decision - both are kept.
     */
    @Test
    void theRowsAVerbatimLifeTakeWouldHaveDeletedAreStillHere() {
        Map<String, String> missing = new TreeMap<>();
        for (Map.Entry<Integer, int[]> e : KEPT_ROWS.entrySet()) {
            Set<Integer> present = new HashSet<>();
            for (Data entry : lifeRows(e.getKey())) {
                present.add(Integer.parseInt(DataTool.getString("id", entry, "-1").trim()));
            }
            for (int id : e.getValue()) {
                if (!present.contains(id)) {
                    missing.put(e.getKey() + "/" + id, "gone");
                }
            }
        }
        assertEquals(Map.of(), missing,
                "a life row this tree carries and v84 does not was deleted - keeping them is the "
                        + "whole reason the life section was re-pointed row by row instead of replaced");
    }

    /**
     * The other refusal, and the reason it is not negotiable. v84 puts static {@code 9901xxx} npcs
     * on the seven Hall of Fame maps in this batch; we carry none of them and must not. Those ids
     * are not ordinary npcs - {@code PlayerNPC.fetchAvailableScriptIdsFromDb} allocates scriptids
     * from {@code NpcId.PLAYER_NPC_BASE + 100 * branch}, so every id v84 places there is inside a
     * live PlayerNPC branch and a static row in the band collides with the allocator. Cosmic fills
     * these halls from the {@code playernpcs} table instead.
     */
    @Test
    void noHallOfFameMapCarriesAStaticNpcInThePlayerNpcBand() {
        Map<String, String> intruders = new TreeMap<>();
        for (int mapId : new int[]{100000204, 101000004, 102000004, 103000008, 120000105,
                130000101, 140010110}) {
            for (Data entry : lifeRows(mapId)) {
                int id = Integer.parseInt(DataTool.getString("id", entry, "-1").trim());
                if (id >= 9900000 && id <= 9906599) {
                    intruders.put(mapId + "/" + id, "life slot " + entry.getName());
                }
            }
        }
        assertEquals(Map.of(), intruders,
                "a static life row sits in the reserved PlayerNPC scriptid band - it will collide "
                        + "with whatever PlayerNPC the allocator hands that id to");
    }

    /** y of the platform at x, linear between its endpoints, as {@code Foothold} treats it. */
    private static double footingAt(int[] g, int x) {
        if (g[1] == g[3] || g[0] == g[2]) {
            return g[1];
        }
        return g[1] + (double) (g[3] - g[1]) * (x - g[0]) / (g[2] - g[0]);
    }

    /** SHA-1 of {@code id|layer|group|x1,y1,x2,y2} per foothold in id order, first 12 hex chars. */
    private static String footholdDigest(int mapId) {
        Map<Integer, String> byId = new TreeMap<>();
        for (Data layer : section(mapId, "foothold").getChildren()) {
            for (Data group : layer.getChildren()) {
                for (Data foothold : group.getChildren()) {
                    byId.put(Integer.parseInt(foothold.getName()), String.join("|",
                            layer.getName(), group.getName(),
                            coord(foothold, "x1") + "," + coord(foothold, "y1") + ","
                                    + coord(foothold, "x2") + "," + coord(foothold, "y2")));
                }
            }
        }
        List<String> lines = new ArrayList<>();
        for (Map.Entry<Integer, String> e : byId.entrySet()) {
            lines.add(e.getKey() + "|" + e.getValue());
        }
        try {
            byte[] sha = MessageDigest.getInstance("SHA-1")
                    .digest(String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(sha).substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    private static String coord(Data foothold, String name) {
        Data leaf = foothold.getChildByPath(name);
        assertNotNull(leaf, "foothold " + foothold.getName() + " has no " + name);
        return String.valueOf(DataTool.getInt(leaf));
    }

    private static Map<Integer, int[]> footholdGeometry(int mapId) {
        Map<Integer, int[]> geom = new HashMap<>();
        for (Data layer : section(mapId, "foothold").getChildren()) {
            for (Data group : layer.getChildren()) {
                for (Data foothold : group.getChildren()) {
                    geom.put(Integer.parseInt(foothold.getName()), new int[]{
                            DataTool.getInt("x1", foothold, 0), DataTool.getInt("y1", foothold, 0),
                            DataTool.getInt("x2", foothold, 0), DataTool.getInt("y2", foothold, 0)});
                }
            }
        }
        return geom;
    }

    private static Set<Integer> footholdIds(int mapId) {
        Set<Integer> ids = footholdGeometry(mapId).keySet();
        assertTrue(ids.size() > 1, "map " + mapId + " has no footholds");
        return ids;
    }

    /** Every {@code life} child, or none - a few maps in this batch have no life section at all. */
    private static List<Data> lifeRows(int mapId) {
        Data image = image(mapId);
        Data life = image.getChildByPath("life");
        return life == null ? List.of() : new ArrayList<>(life.getChildren());
    }

    private static Map<Integer, Integer> fhByNpc(int mapId, int... npcIds) {
        return fhByLife(mapId, npcIds);
    }

    /** {@code life} id -&gt; its {@code fh}, for the ids asked for, npc or mob alike. */
    private static Map<Integer, Integer> fhByLife(int mapId, int... ids) {
        Set<Integer> wanted = new HashSet<>();
        for (int id : ids) {
            wanted.add(id);
        }
        Map<Integer, Integer> found = new HashMap<>();
        for (Data entry : lifeRows(mapId)) {
            int id = Integer.parseInt(DataTool.getString("id", entry, "-1").trim());
            if (wanted.contains(id)) {
                found.putIfAbsent(id, DataTool.getInt("fh", entry, -1));
            }
        }
        return found;
    }

    private static Data image(int mapId) {
        String path = String.format("Map/Map%d/%09d.img", mapId / 100000000, mapId);
        Data image = wz("Map.wz").getData(path);
        assertNotNull(image, "Map.wz has no image for map " + mapId);
        return image;
    }

    private static Data section(int mapId, String name) {
        Data node = image(mapId).getChildByPath(name);
        assertNotNull(node, "map " + mapId + " has no " + name + " node");
        return node;
    }
}
