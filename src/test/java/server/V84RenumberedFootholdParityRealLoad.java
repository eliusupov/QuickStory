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
 * The second batch of the bug {@link V84TownIndexParityRealLoad} documents: twenty more maps where
 * v84 renumbered the foothold table without moving a single platform, and our tree kept v83's
 * numbering. {@code life/*&#47;fh} and every runtime {@code findBelow(pos).getId()} travel to the
 * client as bare ids, so the client drew every npc, mob, pet and PlayerNPC on whatever platform
 * that id happens to name in ITS table.
 *
 * <p>Proved before it was applied, per map: the multiset of {@code (layer, group, x1, y1, x2, y2)}
 * is identical between the two trees, so an id-to-id bijection exists. v84's table was then taken
 * verbatim and every {@code life} row re-pointed through it - 384 rows, all of which still stand on
 * the same physical platform they stood on before. Nothing was deleted; the life rows we carry that
 * v84 does not are kept and re-pointed with the rest. Fifteen mob rows that match v84 on
 * {@code (type, id, x, y)} but cited a different overlapping platform than v84 does were then given
 * v84's own {@code fh}, so no row on these twenty maps disagrees with v84 where the two describe the
 * same spawn.
 *
 * <p>The expectation below is the same digest {@code docs/wz-baseline/tool-peek} prints
 * ({@code WzPeek digest D:\games\MSv84\client\Map.wz}): SHA-1 of {@code id|layer|group|x1,y1,x2,y2}
 * per foothold, ordered by id, first twelve hex characters. Hashing rather than transcribing keeps
 * 2477 footholds pinned in twenty lines; to see WHICH id moved, run
 * {@code WzPeek fh <Map.wz> <mapId>} and diff it against the image.
 *
 * <p><strong>Not a {@code *Test} class on purpose</strong>: {@link WZFiles#DIRECTORY} is a
 * {@code static final} resolved once per JVM and {@code MobSkillFactoryTest} repoints
 * {@code wz-path} at a {@code @TempDir}, hence the explicit {@link XMLWZFile} via {@link V84Wz}.
 */
class V84RenumberedFootholdParityRealLoad {

    /** map id -&gt; v84's foothold-table digest, and the number of footholds it covers. */
    private static final Map<Integer, String> V84_FOOTHOLD_DIGEST = new LinkedHashMap<>(Map.ofEntries(
            Map.entry(130000120, "18cdc57331de"),   // Ereve, Queen's garden          20 footholds
            Map.entry(190000000, "1e01adbc0315"),   // Rien                          179
            Map.entry(191000000, "a9cb15766816"),   // Rien, Snow Hill               190
            Map.entry(192000000, "bbbc38f75363"),   // Rien, Penguin Port            187
            Map.entry(196000000, "620acb4c9ce8"),   // Rien, Hidden Street           107
            Map.entry(197000000, "4d317af55e7c"),   // Rien, Danger Zone              87
            Map.entry(209000000, "70d460c303a1"),   // Happyville                    153
            Map.entry(220000305, "800462b7d949"),   // Ludibrium, Toy Factory          13
            Map.entry(251010403, "4a6a667666bb"),   // Sharp Cliff IV                105
            Map.entry(540000000, "710fae0f31d4"),   // Singapore, CBD                459
            Map.entry(550000000, "ead473af3f7c"),   // Kampung Village               277
            Map.entry(550000400, "483d50b2ca23"),   // Kampung, Fantasy Theme Park   157
            Map.entry(610030100, "85afd7ddbd77"),   // Guild PQ                      119
            Map.entry(677000011, "4372318870aa"),   // Ariant Coliseum               125
            Map.entry(680000110, "869eee5b2f27"),   // Amoria, Chapel                 18
            Map.entry(680000210, "6a8563183531"),   // Amoria, Cathedral              18
            Map.entry(801000000, "deaa72eb2d63"),   // Showa Town                     95
            Map.entry(914030000, "a93be1503b68"),   // Aran tutorial                 117
            Map.entry(925010300, "70637207dd70"),   // Zakum altar                    33
            Map.entry(926010001, "6732f602656a")    // Guild HQ                       18
    ));

    /**
     * The ten of the twenty whose {@code portal} array diverged from v84, in the order they now
     * carry. One rule produced all ten: <b>v84's rows sit at v84's indices, and anything of ours
     * that v84 does not have is appended past v84's last index.</b> That satisfies parity and the
     * additive policy at once - every index the client resolves means what v84 means, and no row of
     * ours was dropped to get there.
     *
     * <p>The number of leading slots v84 owns is in the trailing comment. On seven maps that is the
     * whole array; on {@code 130000120}, {@code 209000000} and {@code 610030100} the tail is ours:
     * the {@code rankDeveloperRoom} door, six {@code tp} doors, and the Guild PQ
     * {@code glpqPortalDummy} portal (custom content from {@code a7beff1bb}, and the one extra row
     * {@code protect-list/Map.txt} already records at {@code 610030100.img/portal/6}).
     *
     * <p>Why the index matters at all: {@code getWarpToMap} sends {@code portal.getId()} and
     * {@code PortalFactory} takes that id from the slot NAME, so a portal in the wrong slot makes
     * the client land the player at its neighbour. Clicking was never affected - that resolves by
     * name. {@code pt} 6 doors are exempt either way ({@code PortalFactory} re-ids them to 0x80+),
     * which is why {@code 209000000}'s six extra doors are harmless wherever they sit.
     */
    private static final Map<Integer, String[]> V84_PORTAL_ORDER = new LinkedHashMap<>(Map.ofEntries(
            Map.entry(130000120, new String[]{"sp", "sp", "st00", "st01", "down00", "down01",
                    "dn00", "dn01", "dn02", "dn03", "dn04", "dn05", "dn06", "dn07", "dn08", "dn09",
                    "dn10", "dn11", "dn12", "dn13", "dn14", "dn15", "in00"}),          // 22 v84's
            Map.entry(190000000, new String[]{"sp", "sp", "in00", "in01", "out00"}),   //  5 v84's
            Map.entry(191000000, new String[]{"sp", "sp", "sp", "sp", "sp", "sp", "sp", "up00",
                    "up01", "up02", "up10", "up11", "up12", "in00", "in01", "out00"}), // 16 v84's
            Map.entry(192000000, new String[]{"sp", "sp", "sp", "sp", "in00", "out00"}),
            Map.entry(196000000, new String[]{"sp", "sp", "sp", "sp", "east00", "out00"}),
            Map.entry(197000000, new String[]{"sp", "sp", "sp", "west00", "east00"}),
            Map.entry(209000000, new String[]{"sp", "sp", "sp", "sp", "sp", "sp", "sp", "h001",
                    "out00", "chimney01", "chimney00", "h002", "h003", "h001_1", "h002_1",
                    "h003_1", "st00", "chimney00_1", "chimney00_2",
                    "tp", "tp", "tp", "tp", "tp", "tp"}),                              // 19 v84's
            Map.entry(550000000, new String[]{"sp", "sp", "sp", "st00", "WP00", "east00", "west00",
                    "market00", "hide01", "hide02", "hide03", "hide04", "hide05", "hide06",
                    "hide07", "tp", "tp", "tp", "tp", "tp", "tp"}),                    // 21 v84's
            Map.entry(610030100, new String[]{"sp", "sp", "sp", "sp", "sp", "next00", "next01"}),
                                                                                       //  6 v84's
            Map.entry(801000000, new String[]{"sp", "sp", "sp", "sp", "in00", "h00_0", "h00_1",
                    "h00_2", "h00_3", "west00", "center00", "in01", "in02",
                    "tp", "tp", "tp", "tp", "tp", "tp"})                               // 19 v84's
    ));

    @Test
    void everyRenumberedMapCarriesV84sFootholdTable() {
        Map<Integer, String> actual = new TreeMap<>();
        for (int mapId : V84_FOOTHOLD_DIGEST.keySet()) {
            actual.put(mapId, footholdDigest(mapId));
        }
        assertEquals(new TreeMap<>(V84_FOOTHOLD_DIGEST), actual,
                "a foothold table drifted from v84 - every npc and mob on that map is now drawn on "
                        + "whatever platform its id names in the client's own table");
    }

    @Test
    void everyLifeRowStandsOnAFootholdThisMapActuallyHas() {
        Map<Integer, String> orphans = new TreeMap<>();
        for (int mapId : V84_FOOTHOLD_DIGEST.keySet()) {
            Set<Integer> ids = footholdIds(mapId);
            for (Data entry : section(mapId, "life").getChildren()) {
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

    /**
     * Kampung Village and Showa, sampled: these are the npcs a player walks into first, and their
     * v83 ids named platforms hundreds of units away in the v84 client (9010000 stood on 11 and 76).
     */
    @Test
    void kampungAndShowaNpcsStandOnV84sFootholdIds() {
        assertEquals(Map.of(9010000, 186, 9010009, 56, 9010010, 184, 1022101, 51),
                fhByNpc(550000000, 9010000, 9010009, 9010010, 1022101),
                "Kampung Village npc foothold ids drifted from v84");
        assertEquals(Map.of(9010000, 3, 9120014, 79, 9120011, 72),
                fhByNpc(801000000, 9010000, 9120014, 9120011),
                "Showa Town npc foothold ids drifted from v84");
    }

    /**
     * The fifteen mob rows that survived the renumber correctly - same platform as before - but had
     * always cited a different overlapping platform than v84 does. They match v84 on
     * {@code (type, id, x, y)}, that key is unique on both sides, so v84's {@code fh} was adopted.
     * Slot -&gt; fh, because the ids repeat within a map and the slot does not.
     */
    @Test
    void theMobRowsThatDisagreedWithV84NowCarryV84sFh() {
        assertEquals(Map.of(190000000, Map.of(20, 109, 22, 134, 26, 69),
                        677000011, Map.of(1, 123, 11, 96),
                        192000000, Map.of(6, 71, 9, 69, 10, 62, 30, 54, 31, 62,
                                39, 69, 40, 66, 41, 59, 42, 62, 43, 55)),
                Map.of(190000000, fhBySlot(190000000, 20, 22, 26),
                        677000011, fhBySlot(677000011, 1, 11),
                        192000000, fhBySlot(192000000, 6, 9, 10, 30, 31, 39, 40, 41, 42, 43)),
                "a mob row drifted back off v84's foothold id");
    }

    @Test
    void everyPortalArrayCarriesV84sRowsAtV84sIndices() {
        Map<Integer, List<String>> expected = new TreeMap<>();
        Map<Integer, List<String>> actual = new TreeMap<>();
        for (Map.Entry<Integer, String[]> e : V84_PORTAL_ORDER.entrySet()) {
            expected.put(e.getKey(), List.of(e.getValue()));
            actual.put(e.getKey(), portalOrder(e.getKey()));
        }
        assertEquals(expected, actual,
                "a portal slot moved - arrivals on that map land at the neighbouring portal");
    }

    private static Map<Integer, Integer> fhBySlot(int mapId, int... slots) {
        Data life = section(mapId, "life");
        Map<Integer, Integer> found = new TreeMap<>();
        for (int slot : slots) {
            Data node = life.getChildByPath(String.valueOf(slot));
            assertNotNull(node, "map " + mapId + " has no life slot " + slot);
            found.put(slot, DataTool.getInt("fh", node, -1));
        }
        return found;
    }

    /**
     * {@code characters.spawnpoint} stores a portal index, so a reorder can leave one pointing at a
     * different portal. {@code findClosestPlayerSpawnpoint} only ever returns a {@code pt} 0 or 1
     * portal whose {@code tm} is {@code MapId.NONE}, so only those slots can ever have been stored.
     *
     * <p>Three of the four reorders move no such slot - {@code 197000000} moved a {@code pt} 2,
     * {@code 550000000} {@code pt} 7/10/6, {@code 610030100} {@code pt} 8. {@code 191000000} does:
     * {@code up12} is {@code pt} 1 with {@code tm} NONE and v84 puts it at 12 where we had it at 14.
     * That one was measured rather than argued - {@code SELECT map, spawnpoint FROM characters}
     * returned zero rows on all seven touched maps (37 characters exist), so no correction changeset
     * ships. This test pins the shape so a later reorder cannot make it stale silently.
     */
    @Test
    void theOnlySpawnpointEligibleSlotThatMovedIsUp12OnRien() {
        Map<Integer, List<String>> spawnable = new TreeMap<>();
        for (int[] mapAndFirstMovedSlot : new int[][]{
                {191000000, 12}, {197000000, 3}, {550000000, 6}, {610030100, 5}}) {
            List<String> hits = new ArrayList<>();
            for (Data node : section(mapAndFirstMovedSlot[0], "portal").getChildren()) {
                if (Integer.parseInt(node.getName()) < mapAndFirstMovedSlot[1]) {
                    continue;
                }
                int pt = DataTool.getInt("pt", node, -1);
                if ((pt == 0 || pt == 1) && DataTool.getInt("tm", node, -1) == 999999999) {
                    hits.add(node.getName() + ":" + DataTool.getString("pn", node, "?"));
                }
            }
            spawnable.put(mapAndFirstMovedSlot[0], hits);
        }
        assertEquals(Map.of(191000000, List.of("12:up12"), 197000000, List.of(),
                        550000000, List.of(), 610030100, List.of()), spawnable,
                "the set of spawnpoint-eligible slots at or after an insertion changed, so "
                        + "characters.spawnpoint has to be re-measured for that map");
    }

    @Test
    void portalSlotsAreConsecutiveFromZero() {
        Map<Integer, String> broken = new TreeMap<>();
        for (int mapId : V84_FOOTHOLD_DIGEST.keySet()) {
            Data portals = section(mapId, "portal");
            for (int i = 0; i < portals.getChildren().size(); i++) {
                if (portals.getChildByPath(String.valueOf(i)) == null) {
                    broken.put(mapId, "portal array has no slot " + i);
                    break;
                }
            }
        }
        assertEquals(Map.of(), broken,
                "a gap in the portal array shifts every later index against the client");
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

    private static List<String> portalOrder(int mapId) {
        Data portals = section(mapId, "portal");
        List<String> actual = new ArrayList<>();
        for (int i = 0; i < portals.getChildren().size(); i++) {
            Data node = portals.getChildByPath(String.valueOf(i));
            actual.add(node == null ? "<missing>" : DataTool.getString("pn", node, "<none>"));
        }
        return actual;
    }

    private static Map<Integer, Integer> fhByNpc(int mapId, int... npcIds) {
        Set<Integer> wanted = new HashSet<>();
        for (int id : npcIds) {
            wanted.add(id);
        }
        Map<Integer, Integer> found = new HashMap<>();
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
