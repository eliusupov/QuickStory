package server;

import org.junit.jupiter.api.Test;
import provider.Data;
import provider.DataTool;
import provider.wz.WZFiles;
import provider.wz.XMLWZFile;

import static server.V84Wz.wz;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ticket 08 - the tail of the v84 map delta. Sibling of {@link V84TracerNodeTest},
 * {@link V84CrimsonSkyNodeTest} and {@link V84NeoCity2227NodeTest}, for the same reason they are
 * siblings of each other: {@link WZFiles#DIRECTORY} is a {@code static final} resolved once per
 * JVM and another test class redirects {@code wz-path} at a {@code @TempDir}, so the real tree has
 * to be opened through an explicitly constructed {@link XMLWZFile}.
 * <p>
 * Merge inputs are the path lists under {@code docs/wz-baseline/merge-lists/08/}; the ids asserted
 * here are exactly those lists. This ticket ships no drop SQL, so it adds no third copy of the
 * drop-file assertions 06 and 07 share - see the ticket for why that extraction was declined.
 */
class V84MiscAreasNodeTest {

    /** The 22 maps this ticket merges. */
    private static final int[] MAPS = {
            200080601,                                                   // Orbis Tower <Secret Room>
            200090080, 200090090,                                        // Olaf's Voyage, both ships
            910050300,                                                   // Abandoned Cave
            910060100, 910060101,                                        // Power B. Fore's centers
            910600000, 910600010,                                        // Golem's Temple Entrance / Abandoned Hideout
            914100000, 914100010,                                        // Temporary Harbor / Snowy Forest
            914100020, 914100021, 914100022, 914100023,                  // Cave of Silence x4
            922030000, 922030001,                                        // Frog House x2
            922030010, 922030011, 922030020, 922030021, 922030022,       // Sky Terrace / Safe
            925110000};                                                  // Pirate Treasure Vault

    /** The seven mobs. Every one is genuinely placed by a {@code life} node - checked below. */
    private static final int[] MOBS = {9300386, 9300387, 9300389, 9300390, 9300392, 9300395, 9300396};

    /** The nine NPC images. Six are placed; three are named by the ticket and placed by nothing. */
    private static final int[] NPCS = {1011101, 1013106, 1013203, 1013207, 1063018, 1205000,
            2012034, 2092100, 2092101};

    /** Placed by no map in either tree - staged deliberately, so assert the gap rather than hide it. */
    private static final Set<Integer> UNPLACED_NPCS = Set.of(1011101, 1013106, 2092100);

    /** 1012118 Power B. Fore is placed by 910060101 but already ships in v83 - nothing merged it. */
    private static final int PRE_EXISTING_NPC = 1012118;

    // wz(String) lives in V84Wz - one copy for all the v84 node tests (ticket 03f, F8).

    /** Asserts rather than returns null, so a missing map fails with its id instead of an NPE. */
    private static Data map(int mapId) {
        String bucket = "Map" + String.valueOf(mapId).charAt(0);
        String path = String.format("Map/%s/%d.img", bucket, mapId);
        Data node = wz("Map.wz").getData(path);
        assertNotNull(node, "Map.wz/" + path + ".xml did not parse");
        return node;
    }

    private static Data portal(int mapId, String name) {
        for (Data p : map(mapId).getChildByPath("portal").getChildren()) {
            if (name.equals(DataTool.getString("pn", p, null))) {
                return p;
            }
        }
        return null;
    }

    private static int portalCount(int mapId) {
        return map(mapId).getChildByPath("portal").getChildren().size();
    }

    /** Korean is the tell for a v83 GMS placeholder string that v84 translated. */
    private static boolean hasHangul(String s) {
        return s.chars().anyMatch(c -> (c >= 0xAC00 && c <= 0xD7A3) || (c >= 0x3130 && c <= 0x318F));
    }

    // ---- maps ---------------------------------------------------------------------------

    @Test
    void everyMiscAreaMapParses() {
        for (int mapId : MAPS) {
            Data node = map(mapId);
            assertNotNull(node.getChildByPath("info"), mapId + " has no info");
            assertNotNull(node.getChildByPath("portal/0"), mapId + " has no portals");
            assertNotNull(node.getChildByPath("foothold"), mapId + " has no footholds");
            assertNotNull(DataTool.getString("info/bgm", node, null), mapId + " has no info/bgm");
            assertNotNull(DataTool.getString("info/mapMark", node, null), mapId + " has no mapMark");
            assertNotNull(portal(mapId, "sp"), mapId + " has no spawn point");
        }
    }

    /**
     * The Slumbering Dragon Island maps are the only ones here that need a world-map marker v83
     * lacks. Nine of the ten new {@code MapHelper.img/mark/*} entries are NOT merged by this
     * ticket, so this pins which one is.
     */
    @Test
    void snowDragonIsTheOnlyNewMapMarkThisTicketNeeds() {
        Data marks = wz("Map.wz").getData("MapHelper.img").getChildByPath("mark");
        assertNotNull(marks.getChildByPath("SnowDragon"), "MapHelper.img/mark/SnowDragon");
        for (int mapId : MAPS) {
            String mark = DataTool.getString("info/mapMark", map(mapId), "");
            assertTrue("None".equals(mark) || marks.getChildByPath(mark) != null,
                    mapId + " names mapMark '" + mark + "' which is not in MapHelper.img");
        }
        // pre-existing marks must survive; the merge inserted a sibling into this node
        assertNotNull(marks.getChildByPath("Henesys"), "MapHelper.img/mark/Henesys (v83) was lost");
        assertNotNull(marks.getChildByPath("Ludibrium"), "MapHelper.img/mark/Ludibrium (v83) was lost");
    }

    /**
     * The check {@code WzMerge deps} explicitly does not make: every mob and NPC id a merged map
     * places must itself have been merged, or the client has no sprite for it. The per-mob-per-map
     * matrix is asserted rather than the per-map total, which is the mistake ticket 07's review
     * caught in its own numbers.
     */
    @Test
    void everyLifeIdInEveryMapWasMergedAndTheMatrixIsTheStatedOne() {
        Map<Integer, Map<String, Integer>> perMap = new HashMap<>();
        Set<Integer> placedMobs = new TreeSet<>();
        Set<Integer> placedNpcs = new TreeSet<>();
        for (int mapId : MAPS) {
            Data life = map(mapId).getChildByPath("life");
            if (life == null) {
                continue;   // six of the 22 place nothing at all
            }
            for (Data entry : life.getChildren()) {
                String type = DataTool.getString("type", entry, "");
                int id = Integer.parseInt(DataTool.getString("id", entry, "-1"));
                assertTrue("m".equals(type) || "n".equals(type),
                        mapId + " life entry of unexpected type '" + type + "'");
                perMap.computeIfAbsent(mapId, k -> new HashMap<>()).merge(type + id, 1, Integer::sum);
                if ("m".equals(type)) {
                    placedMobs.add(id);
                } else {
                    placedNpcs.add(id);
                }
            }
        }

        assertEquals(Map.ofEntries(
                        Map.entry(200080601, Map.of("n2012034", 1)),
                        Map.entry(200090080, Map.of("n1013207", 1)),
                        Map.entry(200090090, Map.of("n1013207", 1)),
                        Map.entry(910050300, Map.of("n1063018", 1)),
                        Map.entry(910060100, Map.of("m9300386", 19)),
                        Map.entry(910060101, Map.of("m210100", 7, "m1210101", 13, "n1012118", 1)),
                        Map.entry(910600000, Map.of("m9300387", 1)),
                        Map.entry(910600010, Map.of("m9300387", 3)),
                        Map.entry(914100000, Map.of("n1013207", 1)),
                        Map.entry(914100021, Map.of("n1205000", 1)),
                        Map.entry(914100023, Map.of("m9300392", 10)),
                        Map.entry(922030000, Map.of("n1013203", 1)),
                        Map.entry(922030011, Map.of("m9300389", 1)),
                        Map.entry(922030022, Map.of("m9300390", 1)),
                        Map.entry(925110000, Map.of("m9300395", 10, "m9300396", 10, "n2092101", 1))),
                perMap, "life entries per id per map, read off the v84 data");

        // 210100 and 1210101 are v83 mobs the training center reuses; 1012118 likewise.
        assertEquals(new TreeSet<>(List.of(210100, 1210101, 9300386, 9300387, 9300389, 9300390,
                9300392, 9300395, 9300396)), placedMobs, "the set of mobs these maps place");
        assertEquals(new TreeSet<>(List.of(1012118, 1013203, 1013207, 1063018, 1205000, 2012034,
                2092101)), placedNpcs, "the set of NPCs these maps place");

        for (int id : placedMobs) {
            // %07d, because LifeFactory:100 resolves mob images through
            // StringUtil.getLeftPaddedStr(mid + ".img", '0', 11) - the training centre reuses
            // v83's 0210100, whose image really is stored with the leading zero.
            assertNotNull(wz("Mob.wz").getData(String.format("%07d.img", id)),
                    "map life spawns mob " + id + " but Mob.wz/" + id + ".img.xml is absent");
        }
        for (int id : placedNpcs) {
            assertNotNull(wz("Npc.wz").getData(String.format("%d.img", id)),
                    "map life places npc " + id + " but Npc.wz/" + id + ".img.xml is absent");
        }
        assertNotNull(wz("Npc.wz").getData(PRE_EXISTING_NPC + ".img"),
                PRE_EXISTING_NPC + " is placed by 910060101 and was expected to ship in v83 already");
    }

    /** Exactly one reactor across all 22 maps, and it is the only Reactor.wz row on the list. */
    @Test
    void theOnlyReactorIs1409000InTheCaveOfSilence() {
        Set<String> placed = new TreeSet<>();
        for (int mapId : MAPS) {
            Data reactors = map(mapId).getChildByPath("reactor");
            if (reactors == null) {
                continue;
            }
            for (Data r : reactors.getChildren()) {
                placed.add(mapId + ":" + DataTool.getString("id", r, "?"));
            }
        }
        assertEquals(Set.of("914100022:1409000"), placed, "reactors placed by maps in scope");
        assertNotNull(wz("Reactor.wz").getData("1409000.img"), "Reactor.wz/1409000.img.xml");
        // ticket 06's two rows must still be there and must not have been re-added by this ticket
        assertNotNull(wz("Reactor.wz").getData("2408005.img"), "ticket 06's Reactor row 2408005");
        assertNotNull(wz("Reactor.wz").getData("2408006.img"), "ticket 06's Reactor row 2408006");
    }

    /**
     * The 67 dependency rows {@code deps} said were owed, spot-checked one per source image, plus
     * the v83 siblings beside them. A missing dependency row is the failure mode that renders a map
     * with a black background, and it is invisible to every other check here.
     */
    @Test
    void mapAssetDependenciesAreInTheTreeAndTheV83SiblingsSurvived() {
        Data backGrassy = wz("Map.wz").getData("Back/grassySoil.img");
        assertNotNull(backGrassy.getChildByPath("ani/7"), "Back/grassySoil.img/ani/7 (merged)");
        assertNotNull(backGrassy.getChildByPath("back/28"), "Back/grassySoil.img/back/28 (merged)");
        assertNotNull(backGrassy.getChildByPath("back/0"), "Back/grassySoil.img/back/0 (v83) was lost");

        Data backRien = wz("Map.wz").getData("Back/Rien.img");
        assertNotNull(backRien.getChildByPath("back/48"), "Back/Rien.img/back/48 (merged)");
        assertNotNull(backRien.getChildByPath("back/0"), "Back/Rien.img/back/0 (v83) was lost");

        assertNotNull(wz("Map.wz").getData("Back/toyCastleB1.img").getChildByPath("back/10"),
                "Back/toyCastleB1.img/back/10 (merged)");
        assertNotNull(wz("Map.wz").getData("Tile/grassySoil.img").getChildByPath("edD/1"),
                "Tile/grassySoil.img/edD/1 (merged)");
        assertNotNull(wz("Map.wz").getData("Obj/insideTC.img").getChildByPath("inside0/blackroom"),
                "Obj/insideTC.img/inside0/blackroom - the Orbis secret room object set");
        assertNotNull(wz("Map.wz").getData("Obj/acc12.img").getChildByPath("dragon"),
                "Obj/acc12.img/dragon - Slumbering Dragon Island scenery");
        assertNotNull(wz("Map.wz").getData("Obj/dungeon.img").getChildByPath("darkCave/acc/43"),
                "Obj/dungeon.img/darkCave/acc/43");
        assertNotNull(wz("Map.wz").getData("Obj/effect.img").getChildByPath("quest/gate/7"),
                "Obj/effect.img/quest/gate/7");
        assertNotNull(wz("Map.wz").getData("Obj/tower.img").getChildByPath("marineTower/gate/12"),
                "Obj/tower.img/marineTower/gate/12");
        assertNotNull(wz("Map.wz").getData("Obj/acc1.img").getChildByPath("grassySoil/golem/21"),
                "Obj/acc1.img/grassySoil/golem/21");
        assertNotNull(wz("Map.wz").getData("Obj/acc1.img").getChildByPath("grassySoil/nature/0"),
                "Obj/acc1.img/grassySoil/nature/0 (v83) was lost");
    }

    // ---- the routes in ---------------------------------------------------------------------

    /**
     * The three route rows this ticket merged onto maps the live client already has. Each is a
     * write into a POSITIONAL array, which is the hazard class 03c named, so each is checked two
     * ways: the new portal is there AND every pre-existing portal still has its original target.
     */
    @Test
    void theThreeMergedRoutePortalsWereAppendedWithoutDisturbingTheV83Ones() {
        assertEquals(7, portalCount(200080600), "200080600 had 6 portals; the merge appends one");
        Data blackRoom = portal(200080600, "in00");
        assertNotNull(blackRoom, "200080600/in00 - the route into the Orbis Tower secret room");
        assertEquals(8, DataTool.getInt("pt", blackRoom, -1), "in00 is a script portal");
        assertEquals("enterBlackRoom", DataTool.getString("script", blackRoom, null));
        assertEquals(200080700, DataTool.getInt("tm", portal(200080600, "under00"), -1),
                "200080600/under00 (v83) was disturbed");
        assertEquals(200080500, DataTool.getInt("tm", portal(200080600, "top00"), -1),
                "200080600/top00 (v83) was disturbed");

        assertEquals(5, portalCount(251010403), "251010403 had 4 portals; the merge appends one");
        Data pottery = portal(251010403, "in00");
        assertNotNull(pottery, "251010403/in00 - the route into the Pirate Treasure Vault");
        assertEquals("enterPottery", DataTool.getString("script", pottery, null));
        assertEquals(251010402, DataTool.getInt("tm", portal(251010403, "west00"), -1),
                "251010403/west00 (v83) was disturbed");

        assertEquals(9, portalCount(106010102), "106010102 had 8 portals; the merge appends one");
        Data dollGR = portal(106010102, "scr00");
        assertNotNull(dollGR, "106010102/scr00 - the route into the Abandoned Hideout");
        assertEquals("evanDollGR", DataTool.getString("script", dollGR, null));
        assertEquals(106010101, DataTool.getInt("tm", portal(106010102, "out00"), -1),
                "106010102/out00 (v83) was disturbed");
        for (String pn : List.of("in03", "in04", "in05", "in06")) {
            assertNotNull(portal(106010102, pn), "106010102/" + pn + " (v83) was lost");
        }

        // The two obj rows are the door art. Without them the portal is invisible, which is the
        // "nothing happens on touch" symptom the human step warns about - so count them too.
        assertEquals(27, map(200080600).getChildByPath("1/obj").getChildren().size(),
                "200080600 layer 1 obj: 25 in v83 + the two merged door frames");
        assertEquals(34, map(251010403).getChildByPath("4/obj").getChildren().size(),
                "251010403 layer 4 obj: 33 in v83 + the merged vault-door frame");
    }

    /**
     * The twelve route rows this ticket deliberately REFUSED, because v84 reordered or inserted
     * into those portal arrays and the add-list index names a different portal in the live client
     * than it does in v84. Merging them would have attached a script to a working v83 portal,
     * written a field onto the wrong sibling, or duplicated one. Asserted so a later ticket cannot
     * merge them by accident and call it an improvement. Full table: 08/ROUTE-ROWS.md.
     */
    @Test
    void theUnsafeRoutePortalRowsWereNotMerged() {
        // 106010101 and 106010102 no longer belong in this list. Both arrays have since been taken
        // from v84 whole, at v84's indices (ticket 53), so the mismatch that made those rows unsafe
        // - our portal/5 being out00 where v84 has in00, our portal/{4,5,6,7} being in04/in05/in06/
        // out00 where v84 has in03/in04/in05/in06 - no longer exists. The leaves are not "merged by
        // index" any more; they arrive as part of v84's own node. What has to be asserted now is the
        // alignment itself, which is a stricter guard than their absence ever was: if the array
        // slipped by one, out00 would pick up a horizontalImpact and in06 would lose one.
        assertEquals(6, portalCount(106010101), "106010101 must still have exactly 6 portals");
        assertNoneOf(106010101, "out00", "script", "horizontalImpact");
        // in00 is now at v84 parity too: a pt 7 script portal on evanGolemDoor with tm=999999999,
        // the script picking 910600000 (quest 22555) or 106010102 (everyone else). The pair is what
        // matters - v84's node without the script file leaves Golem's Temple with no entrance.
        assertEquals(7, DataTool.getInt("pt", portal(106010101, "in00"), -1),
                "106010101/in00 must be v84's scripted warp");
        assertEquals(999999999, DataTool.getInt("tm", portal(106010101, "in00"), -1),
                "a real tm on a pt 7 portal bypasses evanGolemDoor and re-seals 910600000");
        assertEquals("evanGolemDoor",
                DataTool.getString("script", portal(106010101, "in00"), null),
                "106010101/in00 must name evanGolemDoor - with tm=999999999 it is inert without it");
        assertTrue(Files.isRegularFile(Path.of("scripts", "portal", "evanGolemDoor.js")),
                "evanGolemDoor.js is gone, so 106010101/in00 leads nowhere at all");

        // v84 puts horizontalImpact on in03..in06 and scr00, and on nothing else in this array.
        for (String pn : List.of("in03", "in04", "in05", "in06", "scr00")) {
            assertNotNull(portal(106010102, pn).getChildByPath("horizontalImpact"),
                    "106010102/" + pn + " lost its horizontalImpact - v84 carries it on exactly "
                            + "these five, so losing one means the array no longer matches v84");
        }
        assertNoneOf(106010102, "out00", "horizontalImpact");

        // 220000300: v84 INSERTED scr00 at index 4, shifting eleven portals down, so the add-list
        // rows still cannot be applied - portal/15 would be a duplicate in06,
        // portal/4/{horizontalImpact,script} would land on h000, portal/6/image on west00. Those
        // three refusals stand. The PORTAL itself no longer does: see
        // theFrogHouseDoorWasHandAuthoredFromPristineV84 below.
        long in06 = map(220000300).getChildByPath("portal").getChildren().stream()
                .filter(p -> "in06".equals(DataTool.getString("pn", p, null))).count();
        assertEquals(1, in06, "220000300 gained a duplicate in06 portal");
        assertNoneOf(220000300, "h000", "script", "horizontalImpact");
        assertNoneOf(220000300, "west00", "image");

        // 220011000/portal/4 is v84's node now as well. v84 replaces the v83 warp to 220011001
        // with a pt 7 enterBlackBC gate; that script exists here and keeps 220011001 as its
        // fallback branch, so nothing lost a route. The count is still 5 - v84 replaced the slot,
        // it did not insert one, which is what made this row safe to take by index.
        assertEquals(5, portalCount(220011000), "220011000 must still have exactly 5 portals");
        assertEquals(7, DataTool.getInt("pt", portal(220011000, "in00"), -1),
                "220011000/in00 must be v84's scripted warp");
        assertEquals(999999999, DataTool.getInt("tm", portal(220011000, "in00"), -1),
                "a real tm on a pt 7 portal bypasses enterBlackBC and re-strands 22583/22584");
        assertEquals("enterBlackBC", DataTool.getString("script", portal(220011000, "in00"), null),
                "220011000/in00 must name enterBlackBC - with tm=999999999 it is inert without it");
        assertTrue(Files.isRegularFile(Path.of("scripts", "portal", "enterBlackBC.js")),
                "enterBlackBC.js is gone, so 220011001 has no entrance in all of Map.wz");
    }

    /**
     * 220000300/scr00 - the door into the Frog House, read straight out of the pristine v84
     * archive and hand-authored rather than merged.
     *
     * <p>Ticket 08 refused this portal, correctly, for a reason about its MERGE TOOL and not about
     * the data: v84 inserts scr00 at {@code portal/4} and pushes eleven portals down, so no
     * index-addressed add-list row can reach it. Reading
     * {@code porting-resources/wz-data/v84/Map.wz} directly settles what the row actually holds:
     *
     * <pre>
     *   v84 Map/Map2/220000300.img/portal/4
     *     pn=scr00 pt=7 x=-1674 y=106 tm=999999999 tn="" horizontalImpact=0 script=enterBlackFrog
     *   v83-stock Map/Map2/220000300.img/portal   15 portals, no scr00 at any index
     * </pre>
     *
     * <p>Appending it at index 15 reaches the same portal - the server looks portals up by name and
     * by id, never by array position - without moving the fifteen v83 portals, which is what the
     * merge could not avoid. The two maps behind it, 922030000 and 922030001, are the only two in
     * all of v84 Map.wz whose {@code out00} carries {@code tm=220000300 / tn="scr00"}.
     */
    @Test
    void theFrogHouseDoorWasHandAuthoredFromPristineV84() throws IOException {
        assertEquals(16, portalCount(220000300),
                "220000300 must have the 15 v83 portals plus the hand-authored v84 scr00");

        Data scr00 = portal(220000300, "scr00");
        assertNotNull(scr00, "220000300/scr00 is the only client-side route to the Frog House");
        assertEquals(7, DataTool.getInt("pt", scr00, -1), "scr00 must be a pt=7 script portal");
        assertEquals(-1674, DataTool.getInt("x", scr00, 0));
        assertEquals(106, DataTool.getInt("y", scr00, 0));
        assertEquals(999999999, DataTool.getInt("tm", scr00, -1),
                "pt=7 means the server picks the destination; tm must stay the null marker");
        assertEquals("enterBlackFrog", DataTool.getString("script", scr00, null));

        // The fifteen v83 portals must be exactly where they were - the whole point of appending.
        assertEquals(220000301, DataTool.getInt("tm", portal(220000300, "in00"), -1));
        assertEquals(220000000, DataTool.getInt("tm", portal(220000300, "east00"), -1));
        assertEquals(220000400, DataTool.getInt("tm", portal(220000300, "west00"), -1));

        // Both Frog Houses come back through this portal, and nothing else does.
        for (int frogHouse : new int[]{922030000, 922030001}) {
            Data out00 = portal(frogHouse, "out00");
            assertNotNull(out00, frogHouse + "/out00 is missing");
            assertEquals(220000300, DataTool.getInt("tm", out00, -1));
            assertEquals("scr00", DataTool.getString("tn", out00, null),
                    frogHouse + " returns to a portal that is not the one enterBlackFrog serves");
        }

        String js = Files.readString(Path.of("scripts", "portal", "enterBlackFrog.js"),
                StandardCharsets.UTF_8);
        assertTrue(js.contains("function enter(pi)"),
                "enterBlackFrog.js does not implement the PortalScript interface");
        assertTrue(js.contains("pi.warp(922030000, 0)"),
                "enterBlackFrog.js no longer routes 922030000, where npc 1013203 stands - "
                        + "Check.img names him for every one of quests 22581-22588");
        assertTrue(js.contains("pi.warp(922030001, 0)"),
                "enterBlackFrog.js no longer routes 922030001 - QuestInfo.img/22596/1 names that "
                        + "map id in the quest text itself");

        // The 922030001 arm is quest 22596 only. Ungated it would strand every player taking a
        // Black Wings mission in the empty fight room instead of in front of Hiver.
        int fight = js.indexOf("pi.warp(922030001, 0)");
        int gate = js.lastIndexOf("22596", fight);
        assertTrue(gate >= 0 && fight - gate < 200,
                "the 922030001 warp is not gated on Evan quest 22596");
    }

    /**
     * Mob 9300393 ("Gentleman", quest 22596's single kill) is placed by NO map in pristine v84 -
     * a full scan of all 4505 map images under {@code Map/Map*} in
     * {@code porting-resources/wz-data/v84/Map.wz} for {@code life/*}{@code /id = 9300393} returns
     * zero hits. It is a scripted spawn: 922030001 carries {@code info/onUserEnter="enterBlackfrog"}
     * (lowercase f - a different name from the portal script) and an empty {@code life}.
     *
     * <p>The hook is now written. Its one free parameter, the spawn x, has a source after all:
     * 922030001 is 922030000 with the platforms stripped - footholds 1/2/3 are identical in both
     * (the shell, whose only walkable surface is one floor at y=31 running x=-310 to x=314) and
     * 922030001 keeps nothing else but the entry ledge. Hiver stands in the twin room at
     * {@code Map9/922030000.img/life/0} x=-221, so the hook mirrors that x and lets
     * {@code spawnMonsterOnGroundBelow} settle the y onto the single floor. The room's geometry is
     * asserted below, because that is what makes the x safe rather than lucky.
     */
    @Test
    void theRageMobIsAScriptedSpawnAndTheHookNowExists() {
        assertEquals("enterBlackfrog",
                DataTool.getString("onUserEnter", map(922030001).getChildByPath("info"), null),
                "922030001 no longer declares the hook that has to spawn mob 9300393");
        Data life = map(922030001).getChildByPath("life");
        assertTrue(life == null || life.getChildren().isEmpty(),
                "922030001 now places life - if that is mob 9300393 from a real v84 source, say "
                        + "where, because pristine v84 places it on no map at all");

        Path hook = Path.of("scripts", "map", "onUserEnter", "enterBlackfrog.js");
        assertTrue(Files.exists(hook), "missing map hook " + hook + " - 922030001 declares it by "
                + "name, and without it quest 22596's only kill never appears");

        // the geometry the spawn x rests on: one floor, spanning the x the twin room puts Hiver at
        Data floor = map(922030001).getChildByPath("foothold/3/0/2");
        assertNotNull(floor, "922030001's floor foothold is gone");
        assertEquals(-310, DataTool.getInt(floor.getChildByPath("x1"), 0));
        assertEquals(314, DataTool.getInt(floor.getChildByPath("x2"), 0));
        assertEquals(DataTool.getInt(floor.getChildByPath("y1"), 0),
                DataTool.getInt(floor.getChildByPath("y2"), -1), "the floor is no longer level");
        assertNull(map(922030001).getChildByPath("foothold/3/0/5"),
                "922030001 has grown the twin room's platforms back - the spawn is no longer "
                        + "guaranteed to land on the one floor and the hook's x wants re-checking");
        assertEquals(-221, DataTool.getInt(
                        map(922030000).getChildByPath("life/0/x"), 0),
                "922030000 no longer stands Hiver at x=-221, which is the sole source for the x "
                        + "the enterBlackfrog hook spawns him at");
    }

    /** Asserts a named portal has none of the given child fields - i.e. no refused row landed on it. */
    private static void assertNoneOf(int mapId, String portalName, String... fields) {
        Data p = portal(mapId, portalName);
        assertNotNull(p, mapId + "/" + portalName + " is missing");
        for (String field : fields) {
            assertNull(p.getChildByPath(field),
                    mapId + "/" + portalName + " gained a '" + field + "' node - a refused v84 "
                            + "positional-array row landed on the wrong portal");
        }
    }

    /**
     * A script portal with no server-side script is inert - {@code PortalScriptManager} returns
     * false and the portal simply does nothing. These four are the whole reachability story of
     * this ticket, so assert they exist and warp where the docs say.
     */
    @Test
    void thePortalScriptsExistAndWarpToTheMergedMaps() throws IOException {
        Map<String, Integer> expected = Map.of(
                "enterBlackRoom", 200080601,
                "enterPottery", 925110000,
                "evanDollGR", 910600010);
        for (Map.Entry<String, Integer> e : expected.entrySet()) {
            Path p = Path.of("scripts", "portal", e.getKey() + ".js");
            assertTrue(Files.exists(p), "missing portal script " + p);
            String js = Files.readString(p, StandardCharsets.UTF_8);
            assertTrue(js.contains("pi.warp(" + e.getValue() + ","),
                    e.getKey() + ".js does not warp to " + e.getValue());
            assertTrue(js.contains("function enter(pi)"),
                    e.getKey() + ".js does not implement the PortalScript interface");
        }
    }

    /**
     * 910050300 Abandoned Cave is reached through 105070300's {@code in00}, the {@code pt=8} script
     * portal named {@code enterDollcave} - the same portal that already serves the Sleepywood
     * puppeteer route.
     * <p>
     * An earlier revision of this test forbade the cave from being mentioned in that script at all,
     * on the grounds that the name was "taken" by working v83 content. That reasoning does not
     * hold: {@code pt=8} means the destination is decided SERVER side, so v84's client data looks
     * identical whether or not the cave hangs off this portal, and the script already branches
     * three ways. What actually needs protecting is not the script's exclusivity but the v83 route
     * through it, so that is what this asserts.
     * <p>
     * The cave belongs here on the evidence: its {@code returnMap} and {@code forcedReturn} are
     * both 105070300, it appears in no other map's {@code tm} in any of Map.wz, and npc 1063018
     * lives alone on it and gates eight quests (22549's hand-in through 22566). Without the route
     * the Evan chain dies at level ~32 and 22550-22596 are unreachable.
     */
    @Test
    void theAbandonedCaveHangsOffEnterDollcaveWithoutBreakingTheSleepywoodRoute() throws IOException {
        Data dollCave = portal(105070300, "in00");
        assertNotNull(dollCave, "105070300/in00 - the v83 script portal");
        assertEquals("enterDollcave", DataTool.getString("script", dollCave, null));
        assertEquals(4, portalCount(105070300), "105070300 was not supposed to be touched at all");

        String js = Files.readString(Path.of("scripts", "portal", "enterDollcave.js"),
                StandardCharsets.UTF_8);
        assertTrue(js.contains("pi.warp(105040201,"),
                "enterDollcave.js no longer warps to 105040201 - a ticket has repurposed a working "
                        + "v83 portal script");
        assertTrue(js.contains("pi.openNpc(1063011,"),
                "enterDollcave.js lost the puppeteer password fallback");
        assertTrue(js.contains("pi.warp(910050300,"),
                "enterDollcave.js no longer routes the Abandoned Cave - npc 1063018 is then "
                        + "unreachable and Evan quests 22549-22566 cannot be completed");

        // The cave arm must stay behind an Evan quest gate: ungated, it would swallow the v83
        // route for every player who steps on the portal.
        int cave = js.indexOf("pi.warp(910050300,");
        int gate = js.lastIndexOf("22549", cave);
        assertTrue(gate >= 0 && cave - gate < 200,
                "the Abandoned Cave warp is not gated on Evan quest 22549 - it would hijack the "
                        + "Sleepywood puppeteer route");
    }

    /** Each of the three routed maps has a return portal at the name its script warps to. */
    @Test
    void everyRoutedMapReturnsTheWayItCameIn() {
        assertEquals(200080600, DataTool.getInt("tm", portal(200080601, "out00"), -1));
        assertEquals(251010403, DataTool.getInt("tm", portal(925110000, "out00"), -1));
        assertEquals(106010102, DataTool.getInt("tm", portal(910600010, "out00"), -1));
        assertEquals("scr00", DataTool.getString("tn", portal(910600010, "out00"), null));
    }

    // ---- names ------------------------------------------------------------------------------

    @Test
    void mapNamesAreReadable() {
        Data etc = wz("String.wz").getData("Map.img").getChildByPath("etc");
        Map<Integer, String> expected = Map.ofEntries(
                Map.entry(910050300, "Abandoned Cave"),
                Map.entry(910060100, "Power B. Fore's Spore Training Center"),
                Map.entry(910060101, "Power B. Fore's Borrowed Training Center"),
                Map.entry(910600000, "Golem's Temple Entrance"),
                Map.entry(910600010, "Abandoned Hideout"),
                Map.entry(914100000, "Temporary Harbor"),
                Map.entry(914100010, "Snowy Forest"),
                Map.entry(914100020, "Cave of Silence"),
                Map.entry(914100023, "Cave of Silence"),
                Map.entry(922030000, "Frog House"),
                Map.entry(922030022, "Safe - 2nd Entrance"),
                Map.entry(925110000, "Pirate Treasure Vault"));
        for (Map.Entry<Integer, String> e : expected.entrySet()) {
            Data node = etc.getChildByPath(String.valueOf(e.getKey()));
            assertNotNull(node, "String.wz/Map.img/etc/" + e.getKey() + " missing");
            // .trim(): several v84 strings ship with a trailing space; the merge carries them
            // verbatim, which is correct - do not "fix" the data.
            assertEquals(e.getValue(), DataTool.getString("mapName", node, "").trim());
        }
    }

    /**
     * The three forced rows. Their live values were untranslated Korean placeholders that the
     * additive-only gate refused; {@code 08/String.force.txt} authorised the overwrite. Assert
     * the English is there AND that no Hangul survived, on both sides of each node.
     */
    @Test
    void theThreeForcedStringRowsAreNowEnglish() {
        Data ossyria = wz("String.wz").getData("Map.img").getChildByPath("ossyria");
        for (int mapId : new int[]{200090080, 200090090}) {
            Data node = ossyria.getChildByPath(String.valueOf(mapId));
            assertNotNull(node, "String.wz/Map.img/ossyria/" + mapId + " missing");
            String street = DataTool.getString("streetName", node, "").trim();
            String name = DataTool.getString("mapName", node, "").trim();
            assertEquals("Olaf's Voyage", street, mapId + " streetName");
            assertFalse(hasHangul(name), mapId + " mapName is still Korean: " + name);
        }
        assertEquals("To the Slumbering Dragon Island",
                DataTool.getString("mapName", ossyria.getChildByPath("200090080"), "").trim());
        assertEquals("To Lith Harbor",
                DataTool.getString("mapName", ossyria.getChildByPath("200090090"), "").trim());

        Data hiver = wz("String.wz").getData("Npc.img").getChildByPath("1013203");
        assertNotNull(hiver, "String.wz/Npc.img/1013203 missing");
        assertEquals("Hiver", DataTool.getString("name", hiver, "").trim());
        assertEquals("Black Wing Captain", DataTool.getString("func", hiver, "").trim());
        for (String field : List.of("n0", "n1", "d0", "d1")) {
            String v = DataTool.getString(field, hiver, "");
            assertFalse(v.isBlank(), "1013203/" + field + " was lost by the forced overwrite");
            assertFalse(hasHangul(v), "1013203/" + field + " is still Korean: " + v);
        }
    }

    @Test
    void mobsAndNpcsParseAndAreNamed() {
        Data mobNames = wz("String.wz").getData("Mob.img");
        for (int id : MOBS) {
            Data node = wz("Mob.wz").getData(id + ".img");
            assertNotNull(node, "Mob.wz/" + id + ".img.xml did not parse");
            assertTrue(DataTool.getInt("info/maxHP", node, -1) > 0, id + " info/maxHP");
            String name = DataTool.getString("name", mobNames.getChildByPath(String.valueOf(id)), "");
            assertFalse(name.isBlank(), id + " has a blank name");
            assertFalse("MISSING NAME".equals(name), id + " name is the placeholder");
        }
        assertEquals("Watchmen Captain",
                DataTool.getString("name", mobNames.getChildByPath("9300396"), "").trim());
        assertEquals("Trainee Spore",
                DataTool.getString("name", mobNames.getChildByPath("9300386"), "").trim());

        Data npcNames = wz("String.wz").getData("Npc.img");
        for (int id : NPCS) {
            assertNotNull(wz("Npc.wz").getData(id + ".img"), "Npc.wz/" + id + ".img.xml did not parse");
            String name = DataTool.getString("name", npcNames.getChildByPath(String.valueOf(id)), "");
            assertFalse(name.isBlank(), "npc " + id + " has a blank name");
        }
        assertEquals("Olaf", DataTool.getString("name", npcNames.getChildByPath("1013207"), "").trim());
        assertEquals("General Mau",
                DataTool.getString("name", npcNames.getChildByPath("1011101"), "").trim());
        assertEquals("Glowing Stele",
                DataTool.getString("name", npcNames.getChildByPath("1013106"), "").trim());
        // 9000071 Keroben: the sprite already shipped in v83, only the name was new in v84
        assertEquals("Keroben",
                DataTool.getString("name", npcNames.getChildByPath("9000071"), "").trim());
        assertEquals("General Mau",
                DataTool.getString("name", npcNames.getChildByPath("2080007"), "").trim());
    }

    /**
     * The three NPCs the ticket names that no map <em>this ticket ships</em> places. Merged
     * deliberately, so the gap is asserted rather than left to be rediscovered as a bug.
     * `1011101` and `2092100` are placed by nothing in either tree; `1013106` Glowing Stele is
     * placed only by 100030301, an Evan world map ticket 13 owns.
     */
    @Test
    void theUnplacedNpcsAreMergedAndKnownToBeUnplaced() {
        for (int id : UNPLACED_NPCS) {
            assertNotNull(wz("Npc.wz").getData(id + ".img"), "Npc.wz/" + id + ".img.xml");
        }
        for (int mapId : MAPS) {
            Data life = map(mapId).getChildByPath("life");
            if (life == null) {
                continue;
            }
            for (Data entry : life.getChildren()) {
                int id = Integer.parseInt(DataTool.getString("id", entry, "-1"));
                assertFalse(UNPLACED_NPCS.contains(id),
                        mapId + " places " + id + ", which this ticket documents as unplaced");
            }
        }
    }

    /**
     * The ten {@code 99019xx} NPCs are dropped, not merged: {@code 9901910} is the base of the
     * range {@code PlayerNPC.java} allocates from at runtime, and Cosmic already ships its own
     * images there (commit {@code fca7b2ada}, "Implemented Kites, PlayerNPCs..."). They are placed
     * by 100030301, an Evan world map ticket 13 owns, so this is the assertion that stops v84's
     * versions arriving by the back door. v84's {@code 9901910.img} is a strict SUBSET of the live
     * one - it has no {@code info/speak} - so the live node's presence is the discriminator.
     */
    @Test
    void theServerOwnedNpcIdRangeIsStillCosmicsOwn() {
        Data npc = wz("Npc.wz").getData("9901910.img");
        assertNotNull(npc, "Cosmic's own 9901910.img.xml disappeared");
        assertNotNull(npc.getChildByPath("info/speak"),
                "9901910 lost info/speak - that is v84's node, which must never be merged here");
        // the other nine: Cosmic ships them too, and none may gain v84's shape either
        for (int id = 9901911; id <= 9901919; id++) {
            Data sibling = wz("Npc.wz").getData(id + ".img");
            assertNotNull(sibling, "Cosmic's own " + id + ".img.xml disappeared");
            assertNotNull(sibling.getChildByPath("info/speak"),
                    id + " lost info/speak - v84's node was merged onto the allocator range");
        }
        assertNull(wz("Npc.wz").getData("9000021.img").getChildByPath("say/2/delay"),
                "Npc.wz/9000021.img is on the deny-list and must stay wholly v83");
    }

    // ---- sound, and the absence of drop SQL --------------------------------------------------

    /**
     * The {@code Sound.wz} binary merge cannot be promoted (BgmGL.img is unparseable by MapleLib in
     * all three trees), but the server XML half was applied and the path list is the deliverable.
     * This asserts the XML half landed - all 23 rows, including ticket 07's four-row handoff and
     * ticket 06's twelve unclaimed mob banks.
     */
    @Test
    void theSoundXmlRowsWereApplied() throws IOException {
        Data mob = wz("Sound.wz").getData("Mob.img");
        assertNotNull(mob, "wz/Sound.wz/Mob.img.xml did not parse");

        // Read the path list rather than a literal array, so the two cannot drift apart.
        List<String> rows = manifestRows(Path.of("docs", "wz-baseline", "merge-lists", "08",
                "Sound.paths.txt"));
        assertEquals(23, rows.size(), "08/Sound.paths.txt row count");
        for (String row : rows) {
            assertTrue(row.startsWith("Sound.wz/Mob.img/"), "unexpected Sound row: " + row);
            String id = row.substring("Sound.wz/Mob.img/".length());
            assertNotNull(mob.getChildByPath(id), "Sound.wz/Mob.img/" + id + " was not spliced");
        }
        // ticket 06's own Sound row must still be there and must not have been re-added
        assertNotNull(wz("Sound.wz").getData("Bgm14.img").getChildByPath("DragonRider"),
                "ticket 06's Sound.wz/Bgm14.img/DragonRider");
    }

    /**
     * This ticket ships no drop SQL, and the reason is in the data rather than in the prose: six of
     * the seven mobs give zero exp, i.e. they are scripted obstacles, not huntable content. If a
     * later edit gives one of them real exp this fails and the decision gets revisited.
     */
    @Test
    void theseMobsAreObstaclesNotHuntableContentSoNoDropTablesAreOwed() {
        int withExp = 0;
        for (int id : MOBS) {
            int exp = DataTool.getInt("info/exp", wz("Mob.wz").getData(id + ".img"), -1);
            assertTrue(exp >= 0, id + " has no info/exp");
            if (exp > 0) {
                withExp++;
                assertEquals(9300386, id, "an unexpected mob in this ticket gives exp: " + id);
            }
        }
        assertEquals(1, withExp, "only Trainee Spore gives exp");

        for (String file : List.of("152-drop-data.sql", "153-crimson-sky-drop-data.sql",
                "154-neo-city-2227-drop-data.sql")) {
            Path p = Path.of("src", "main", "resources", "db", "data", file);
            String sql = readOrFail(p);
            for (int id : MOBS) {
                assertFalse(sql.contains("(" + id + ", "),
                        "dropperid " + id + " appears in " + file + ", which this ticket never edits");
            }
        }
    }

    /** Manifest rows of a path list: everything that is not blank and not a comment. */
    private static List<String> manifestRows(Path p) throws IOException {
        return Files.readAllLines(p, StandardCharsets.UTF_8).stream()
                .map(l -> l.replace("﻿", "").trim())
                .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                .toList();
    }

    private static String readOrFail(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("cannot read " + p, e);
        }
    }
}
