package server;

import client.creator.novice.EvanCreator;
import constants.id.MapId;
import org.junit.jupiter.api.Test;
import provider.Data;
import provider.DataProvider;
import provider.DataTool;
import tools.StringUtil;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static server.V84Wz.wz;

/**
 * Ticket 13 — Evan's world, server side.
 * <p>
 * Path lists: {@code docs/wz-baseline/merge-lists/13/{Map,Npc,Mob,String}.paths.txt}
 * (+28 / +14 / +2 / +21, {@code added 65, refused 0, denied 0, forced 0}, exit 0 on all four).
 * Zero-change proof and its self-checks: {@code docs/wz-baseline/merge-lists/13/verify.ps1}.
 * <p>
 * A sibling of {@link V84EvanNodeTest} (ticket 10) for the reasons stated there, and it opens the
 * tree the same single way — {@link V84Wz#wz}, never {@code DataProviderFactory}.
 */
class V84EvanWorldNodeTest {

    /** The 9000xxxxx range, exactly as {@code docs/wz-baseline/add-list/Map.txt} holds it. */
    private static final int[] EVAN_MAPS = {
            900010000, 900010100, 900010200,
            900020100, 900020110, 900020200, 900020210, 900020220,
            900030000,
            900090000, 900090001, 900090002, 900090003, 900090004,
            900090100, 900090101, 900090102, 900090103, 900090104};

    /** The Henesys-farm story maps. 100030000/100030001 were already in this tree. */
    private static final int[] EVAN_FARM_MAPS = {
            100030100, 100030101, 100030102, 100030103,
            100030200, 100030300, 100030310, 100030320, 100030400};

    /** {@code MapFactory.getMapName} — the exact string the server asks {@code Map.wz} for. */
    private static String mapNode(int mapid) {
        return "Map/Map" + (mapid / 100000000) + "/" + mapid + ".img";
    }

    /**
     * The start map a newly created Evan is put on, wired end to end.
     * <p>
     * {@link EvanCreator} used {@code MapId.MUSHROOM_TOWN} as a placeholder because the Evan range
     * was absent server-side — {@code !warp 900010000} answered "map is invalid". The id itself was
     * read out of the v84 archive twice, from two independent sources: {@code
     * String.wz/Map.img/etc/900010000} is "Dream Forest Entrance" on street "Dream World", and
     * Edelstein's {@code UserOnPacketCreateNewCharacterPlug.cs:71} maps {@code RaceSelectType.Evan}
     * to {@code 900010000} in the same table where it maps Aran to {@code 914000000} — which is
     * Cosmic's own {@link MapId#ARAN_TUTORIAL_START}. It is <b>not</b> one of the {@code 9000901xx}
     * ids; those are "Video" street, i.e. cutscene maps.
     */
    @Test
    void evanStartsOnDreamForestEntranceAndTheMapIsThere() throws Exception {
        assertEquals(900010000, MapId.EVAN_TUTORIAL_START);

        Field startMap = EvanCreator.class.getDeclaredField("START_MAP");
        startMap.setAccessible(true);
        assertEquals(MapId.EVAN_TUTORIAL_START, startMap.get(null),
                "EvanCreator still starts Evan somewhere else (was MapId.MUSHROOM_TOWN)");

        Data map = wz("Map.wz").getData(mapNode(MapId.EVAN_TUTORIAL_START));
        assertNotNull(map, "Map.wz/" + mapNode(MapId.EVAN_TUTORIAL_START) + " missing");
        assertNotNull(map.getChildByPath("info"), "no info");
        assertNotNull(map.getChildByPath("portal/0"), "no spawn portal");
        assertNotNull(map.getChildByPath("foothold"), "no footholds — a player would fall forever");

        // MapFactory.getMapStringName sends every id >= 900000000 down its else branch, to "etc".
        Data name = wz("String.wz").getData("Map.img").getChildByPath("etc/900010000");
        assertNotNull(name, "String.wz/Map.img/etc/900010000 missing");
        assertEquals("Dream Forest Entrance", DataTool.getString("mapName", name, "").replaceAll("\\s+", " ").trim());
        assertEquals("Dream World", DataTool.getString("streetName", name, "").replaceAll("\\s+", " ").trim());
    }

    /**
     * Every merged map image parses and carries the three things {@code MapFactory.loadMapFromWz}
     * dereferences without a null check — {@code info}, {@code portal}, {@code foothold} — and none
     * of them uses {@code info/link}, so no further map image is owed. ({@code life} is optional
     * and is checked by {@link #everyLifeIdTheMergedMapsSpawnHasItsImage} instead.)
     */
    @Test
    void everyMergedEvanMapIsLoadable() {
        DataProvider maps = wz("Map.wz");
        for (int id : concat(EVAN_MAPS, EVAN_FARM_MAPS)) {
            Data map = maps.getData(mapNode(id));
            assertNotNull(map, "Map.wz/" + mapNode(id) + " missing");
            assertNotNull(map.getChildByPath("info"), id + ": no info");
            assertNotNull(map.getChildByPath("foothold"), id + ": no foothold");
            assertNotNull(map.getChildByPath("portal"), id + ": no portal");
            // None of the 28 uses info/link, so none of them pulls in a further map image.
            assertEquals("", DataTool.getString(map.getChildByPath("info/link"), ""),
                    id + ": has an info/link, and its target has to be merged too");
        }
    }

    /**
     * The six maps Cosmic's existing Evan scripts already warp to, named in ticket 13 as gap 2.
     * Before this merge {@code Map.wz} held only {@code 100030000} and {@code 100030001} of that
     * street, so every one of those warps hit "map is invalid".
     */
    @Test
    void theSixMapsExistingEvanScriptsReferenceNowResolve() {
        DataProvider maps = wz("Map.wz");
        for (int id : new int[]{100030102, 100030103, 100030200, 100030300, 100030310, 100030400}) {
            assertNotNull(maps.getData(mapNode(id)), "script-referenced map " + id + " still missing");
        }
    }

    /**
     * Derived, not restated: walk the {@code life} of all 28 merged maps and assert every id they
     * spawn has an image in the tree the server reads. This is the check that would have caught a
     * merged map whose NPCs or mobs were left behind — {@code MapFactory} logs and drops those, so
     * the map loads and is simply, silently, empty.
     */
    @Test
    void everyLifeIdTheMergedMapsSpawnHasItsImage() {
        DataProvider maps = wz("Map.wz");
        DataProvider npcs = wz("Npc.wz");
        DataProvider mobs = wz("Mob.wz");
        Data npcNames = wz("String.wz").getData("Npc.img");
        List<String> missing = new ArrayList<>();
        var seen = new TreeSet<String>();
        for (int id : concat(EVAN_MAPS, EVAN_FARM_MAPS)) {
            Data life = maps.getData(mapNode(id)).getChildByPath("life");
            if (life == null) {
                continue;
            }
            for (Data entry : life.getChildren()) {
                String type = DataTool.getString("type", entry, "");
                String lifeId = DataTool.getString("id", entry, "");
                if (lifeId.isEmpty() || !seen.add(type + lifeId)) {
                    continue;
                }
                // LifeFactory:100 left-pads a mob id to 11 chars ("0130100.img"); an NPC is
                // looked up by NAME out of String.wz/Npc.img (LifeFactory:294), and the server
                // never opens Npc.wz at all — the image is merged for completeness, the name row
                // is the one the server would print MISSINGNO without.
                int lifeIdInt = Integer.parseInt(lifeId);
                String what;
                if ("n".equals(type)) {
                    what = npcNames.getChildByPath(String.valueOf(lifeIdInt)) == null
                            ? "String.wz/Npc.img/" + lifeIdInt
                            : (npcs.getData(lifeIdInt + ".img") == null ? "Npc.wz/" + lifeIdInt + ".img" : null);
                } else {
                    String img = StringUtil.getLeftPaddedStr(lifeIdInt + ".img", '0', 11);
                    what = mobs.getData(img) == null ? "Mob.wz/" + img : null;
                }
                if (what != null) {
                    missing.add(id + " -> " + type + " " + what);
                }
            }
        }
        assertTrue(missing.isEmpty(), "life ids with no image: " + missing);
        assertFalse(seen.isEmpty(), "no life was walked at all — the loop is not exercising anything");
    }

    /**
     * NPC {@code 1013101} is the giver of quest {@code 22000}, ticket 13's gap 3, and the whole
     * Evan intro hangs off it.
     */
    @Test
    void theEvanIntroNpcsArePresentAndNamed() {
        DataProvider npcs = wz("Npc.wz");
        Data npcNames = wz("String.wz").getData("Npc.img");
        for (int id : new int[]{1013001, 1013002, 1013100, 1013101, 1013102, 1013103, 1013104,
                1013105, 1013200, 1013201, 1013202, 1013204, 1013205, 1013206}) {
            assertNotNull(npcs.getData(id + ".img"), "Npc.wz/" + id + ".img missing");
            assertNotNull(npcNames.getChildByPath(String.valueOf(id)), "String.wz/Npc.img/" + id + " missing");
        }
        assertNotNull(wz("Quest.wz").getData("QuestInfo.img").getChildByPath("22000"),
                "quest 22000 (ticket 33) is gone — 1013101 has nothing to give");
    }

    /**
     * A DELIBERATE PARTIAL MERGE, pinned so that the part left out stays left out.
     * <p>
     * {@code Map/Map1/100030301.img} ("Forest Hall") is genuine v84 — absent from v83-stock,
     * named in {@code String.wz/Map.img/victoria/100030301}, and its npc {@code 1013106}
     * ("Glowing Stele") carries Nexon's own script name {@code evan_lv200}. It is Nexon's Evan
     * Lv.200 hall of fame, so it belongs here and {@code scripts/portal/inDragonEgg.js:8} warps
     * to it.
     * <p>
     * What did NOT come with it: v84's ten {@code life} rows on npc ids
     * {@code 9901910}–{@code 9901919}. Those ids are inside the run {@code PlayerNPC} hands out
     * at runtime (branch 19, {@code 9901900}–{@code 9901999}), and this server's hall of fame is
     * DB-driven — {@code PlayerNPCPodium} computes every position and never reads map
     * {@code life}. Upstream {@code fca7b2ada} stripped 149 such static rows from exactly the ten
     * {@code GameConstants.isHallOfFameMap} ids for that reason. Re-adding them here would put
     * fixed NPCs on ids the allocator can hand out the same day.
     */
    @Test
    void forestHallIsMergedWithoutItsPlayerNpcAnchorRows() {
        Data forestHall = wz("Map.wz").getData(mapNode(100030301));
        assertNotNull(forestHall, "100030301 is gone — inDragonEgg.js:8 warps to it, so it must exist");

        List<Data> life = new ArrayList<>(forestHall.getChildByPath("life").getChildren());
        assertEquals(1, life.size(), "100030301 life should hold only the Glowing Stele");
        assertEquals("1013106", DataTool.getString(life.get(0).getChildByPath("id")),
                "the one life row is no longer npc 1013106");
        for (Data row : life) {
            int id = Integer.parseInt(DataTool.getString(row.getChildByPath("id")));
            assertFalse(id >= 9900000 && id <= 9906599,
                    "life row on npc " + id + " is inside the PlayerNPC allocator band — read "
                            + "this test's javadoc before keeping it");
        }
    }

    /**
     * The way back INTO the farm cluster, and the reason it had to be a replacement rather than an
     * append.
     *
     * <p>Until now this tree's 100030000 carried the v83 node at {@code portal/13}:
     * {@code pn=quest00 pt=7 x=-4428 y=-1286 tm=999999999 script=q2073}, the Camila's Gem door
     * into Utah's Pig Farm. Pristine v84 {@code Map.wz} replaces exactly that node, in place, at
     * three pixels' distance:
     *
     * <pre>
     *   v83-stock 100030000/portal/13  pn=quest00 pt=7 x=-4428 y=-1286 tm=999999999 script=q2073
     *   v84       100030000/portal/14  pn=in01    pt=2 x=-4431 y=-1287 tm=100030400 tn=out00
     * </pre>
     *
     * <p>v84 moved the Camila door one map outward: 100030400 ("Farm Entrance") carries
     * {@code quest00 pt=7 script=q2073} of its own, and this tree already has it. So the swap
     * loses no route - quest 2073 now runs 100030000 -> in01 -> 100030400 -> quest00 ->
     * 900000000, which is what {@code QuestInfo.img/2073/1} means by "I could get to
     * {@code #m900000000#} through {@code #m100030000#}".
     *
     * <p>What it fixes: {@code 100030400/out00} already pointed at {@code 100030000/in01}, a
     * portal that did not exist, so a player who left the Evan farm through it fell out of the
     * cluster with no way back - {@code 900020100 -> 100030300} (Evan quest 22005 only) was the
     * single inbound route. Appending in01 instead of replacing quest00 would have stacked two
     * portals three pixels apart, which is why the v83 node goes.
     */
    @Test
    void theFarmClusterHasItsV84WayBackIn() {
        DataProvider maps = wz("Map.wz");
        // Slot 14, not 13: this test was written when in01 was written over slot 13, which is
        // v84's west00. The array is now v84's own order (see ticket 53 and
        // V84TownIndexParityRealLoad) - the server sends the slot number to the client as the
        // arrival point, so the position is load-bearing, not cosmetic.
        Data in01 = maps.getData(mapNode(100030000)).getChildByPath("portal/14");
        assertNotNull(in01, "100030000/portal/14 vanished");
        assertEquals("in01", DataTool.getString("pn", in01, null),
                "100030000/portal/14 is not in01 - if the v83 quest00 is back, 100030400 is a "
                        + "one-way trip again");
        assertEquals(2, DataTool.getInt("pt", in01, -1));
        assertEquals(-4431, DataTool.getInt("x", in01, 0));
        assertEquals(-1287, DataTool.getInt("y", in01, 0));
        assertEquals(100030400, DataTool.getInt("tm", in01, -1));
        assertEquals("out00", DataTool.getString("tn", in01, null));
        assertNull(in01.getChildByPath("script"), "a pt=2 portal must carry no script");

        // The far side, and the Camila's Gem door that moved onto it.
        Data out00 = maps.getData(mapNode(100030400)).getChildByPath("portal/2");
        assertEquals("out00", DataTool.getString("pn", out00, null));
        assertEquals(100030000, DataTool.getInt("tm", out00, -1));
        assertEquals("in01", DataTool.getString("tn", out00, null),
                "100030400/out00 names a portal that must exist on 100030000");
        Data camila = maps.getData(mapNode(100030400)).getChildByPath("portal/3");
        assertEquals("quest00", DataTool.getString("pn", camila, null));
        assertEquals("q2073", DataTool.getString("script", camila, null),
                "quest 2073 lost its portal when 100030000/quest00 was replaced");
    }

    private static int[] concat(int[] a, int[] b) {
        int[] out = new int[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
