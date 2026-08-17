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
 * v84 does not are kept and re-pointed with the rest.
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
     * The five of the twenty whose {@code portal} array was ALSO taken from v84 verbatim. On each
     * of them v84's array is ours plus exactly one portal, so nothing is invented and nothing is
     * lost - and on {@code 197000000} and {@code 550000000} the new portal lands mid-array, which is
     * the {@code unityPortal2} defect again: {@code getWarpToMap} sends {@code portal.getId()} and
     * {@code PortalFactory} takes that id from the slot name, so every later slot answered one early.
     *
     * <p>The other five whose portal arrays still differ from v84 are deliberately untouched, see
     * the ticket: {@code 130000120} and {@code 610030100} carry a portal v84 lacks, {@code 209000000}
     * six {@code tp} doors v84 lacks (and {@code PortalFactory} re-ids {@code pt} 6 to 0x80+, so
     * those never address by position), {@code 801000000} differs only in one door's x, and
     * {@code 191000000} would need {@code up12} moved five pixels to take v84's array whole.
     */
    private static final Map<Integer, String[]> V84_PORTAL_ORDER = new LinkedHashMap<>(Map.of(
            190000000, new String[]{"sp", "sp", "in00", "in01", "out00"},
            192000000, new String[]{"sp", "sp", "sp", "sp", "in00", "out00"},
            196000000, new String[]{"sp", "sp", "sp", "sp", "east00", "out00"},
            197000000, new String[]{"sp", "sp", "sp", "west00", "east00"},
            550000000, new String[]{"sp", "sp", "sp", "st00", "WP00", "east00", "west00",
                    "market00", "hide01", "hide02", "hide03", "hide04", "hide05", "hide06",
                    "hide07", "tp", "tp", "tp", "tp", "tp", "tp"}));

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

    @Test
    void thePortalArraysTakenFromV84AreInV84sOrder() {
        Map<Integer, List<String>> expected = new TreeMap<>();
        Map<Integer, List<String>> actual = new TreeMap<>();
        for (Map.Entry<Integer, String[]> e : V84_PORTAL_ORDER.entrySet()) {
            expected.put(e.getKey(), List.of(e.getValue()));
            actual.put(e.getKey(), portalOrder(e.getKey()));
        }
        assertEquals(expected, actual,
                "a portal slot moved - arrivals on that map land at the neighbouring portal");
    }

    /**
     * The two mid-array insertions move no slot that {@code characters.spawnpoint} can hold, which
     * is why they ship without a correction changeset. {@code findClosestPlayerSpawnpoint} only ever
     * returns a {@code pt} 0 or 1 portal whose {@code tm} is {@code MapId.NONE}; on both maps every
     * such portal sits in the untouched head of the array.
     */
    @Test
    void noInsertedSlotIsOneThatSpawnpointCouldHold() {
        Map<Integer, String> spawnable = new TreeMap<>();
        for (int[] mapAndFirstMovedSlot : new int[][]{{197000000, 3}, {550000000, 6}}) {
            for (Data node : section(mapAndFirstMovedSlot[0], "portal").getChildren()) {
                if (Integer.parseInt(node.getName()) < mapAndFirstMovedSlot[1]) {
                    continue;
                }
                int pt = DataTool.getInt("pt", node, -1);
                if ((pt == 0 || pt == 1) && DataTool.getInt("tm", node, -1) == 999999999) {
                    spawnable.put(mapAndFirstMovedSlot[0], "slot " + node.getName()
                            + " (" + DataTool.getString("pn", node, "?") + ")");
                }
            }
        }
        assertEquals(Map.of(), spawnable,
                "a moved slot is now spawnpoint-eligible, so characters.spawnpoint needs a "
                        + "correction changeset for that map");
    }

    @Test
    void portalAndLifeSlotsAreStillWhereTheImageSaysTheyAre() {
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
