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
     * A DELIBERATE ABSENCE, pinned so that merging it is a decision and not an accident.
     * <p>
     * {@code Map/Map1/100030301.img} ("Forest Hall") has ten {@code life} entries on NPC ids
     * {@code 9901910}–{@code 9901919}. That is inside the range {@code PlayerNPC} allocates from at
     * runtime ({@code PlayerNPC.java:66}: {@code 9901910}–{@code 9906599} are HeavenMS's own
     * injected player-NPC ids), which is the same reason {@code Etc.wz/NpcLocation.img/990191x} is
     * on {@code COLLISION-DENY.txt}. Merging the map would put fixed Nexon NPCs on ids this server
     * hands out. If it is ever wanted, the ten {@code life} slots have to go first — and that is a
     * hand-authored node, not a merge.
     */
    @Test
    void forestHallIsDeliberatelyNotMerged() {
        assertNull(wz("Map.wz").getData(mapNode(100030301)),
                "100030301 was merged — read this test's javadoc before keeping it");
    }

    private static int[] concat(int[] a, int[] b) {
        int[] out = new int[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
