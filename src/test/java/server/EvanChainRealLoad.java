package server;

import client.Character;
import client.Client;
import client.QuestStatus;
import net.packet.InPacket;
import net.packet.Packet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import provider.Data;
import provider.DataProvider;
import provider.DataProviderFactory;
import provider.DataTool;
import provider.wz.WZFiles;
import scripting.quest.QuestScriptManager;
import server.maps.Dragon;
import server.maps.MapleMap;
import server.quest.Quest;

import java.awt.Point;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Walks the Evan chain server-side, off the real {@code wz/} tree and the real
 * {@code scripts/quest/*.js} through the real Graal script manager, and pins down where it stops
 * being walkable. Companion to {@link V84EvanQuestRealLoad}, which proved the 135 ids <em>load</em>;
 * this one asks whether they can <em>fire</em>.
 *
 * <pre>
 *   mvnw.cmd test -Dtest=EvanChainRealLoad
 * </pre>
 *
 * <p><strong>Not a {@code *Test} class on purpose</strong>, same reason as {@link Quest1021RealLoad}
 * and {@link EarlyGameQuestScriptsRealLoad}: {@link WZFiles#DIRECTORY} is a {@code static final}
 * resolved once per JVM and {@code MobSkillFactoryTest} points {@code wz-path} at a {@code @TempDir}
 * with no {@code Quest.wz} in it.
 *
 * <p>The order the data actually declares, read out of {@code Check.img} and not off a wiki:
 * <pre>
 *   22000 -&gt; 22001 -&gt; ... -&gt; 22007            job 2001, npcs 1013100/1/2/3, the farm
 *   22007 completed + level 10                  gates 22100, the 1st job advancement (npc 1013000)
 *   22100 .. 22109                              job advancements, npc 1013000, autoStart
 *   22500 ..                                    job 2200+, npc 1013000, the Mir chain
 * </pre>
 */
class EvanChainRealLoad {

    /** The farm chain, in the order Check.img's {@code quest} prerequisites put them. */
    private static final int[] FARM_CHAIN = {22000, 22001, 22002, 22003, 22004, 22005, 22006, 22007};

    /** Mir. Every job advancement and the whole 22500 chain names him as their quest npc. */
    private static final int MIR = 1013000;

    @BeforeAll
    static void muteGraal() {
        System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
        assertTrue(Files.isRegularFile(Path.of(WZFiles.DIRECTORY, "Quest.wz", "Check.img.xml")),
                "wz-path resolved to '" + WZFiles.DIRECTORY + "', which holds no Quest.wz - another "
                        + "test class won the WZFiles.DIRECTORY race, so this says nothing about Evan");
    }

    /**
     * The farm chain's eight quest givers, checked against the {@code life} node of the map each is
     * meant to stand on, read through the same {@link DataProvider} {@code MapFactory} uses. If any
     * of these were absent, {@code QuestActionHandler.isNpcNearby} would refuse the QUEST_ACTION
     * before {@code canStart} ever ran, and the chain would be dead at its first step.
     */
    @Test
    void everyFarmChainQuestGiverIsSpawnedOnARealMap() {
        Map<Integer, Integer> giverToMap = new LinkedHashMap<>();
        giverToMap.put(1013100, 100030101);   // Mom, Evan's room
        giverToMap.put(1013101, 100030102);   // Utah, the living room
        giverToMap.put(1013102, 100030102);   // the Bull Dog
        giverToMap.put(1013103, 100030300);   // Gustav, the farm
        giverToMap.put(1013105, 100030310);   // Anna

        for (Map.Entry<Integer, Integer> e : giverToMap.entrySet()) {
            assertTrue(npcIsInLifeOf(e.getValue(), e.getKey()),
                    "npc " + e.getKey() + " is not in Map.wz life of map " + e.getValue()
                            + "; isNpcNearby would refuse every quest it gives");
        }

        // and every npc the farm chain names is one of those five
        for (int id : FARM_CHAIN) {
            Quest q = Quest.getInstance(id);
            for (boolean end : new boolean[]{false, true}) {
                int npc = q.getNpcRequirement(end);
                if (npc == -1) {
                    continue;
                }
                assertTrue(giverToMap.containsKey(npc),
                        "quest " + id + (end ? " end" : " start") + " names npc " + npc
                                + ", which is not one of the farm npcs this test placed");
            }
        }
    }

    /** Every {@code startscript} / {@code endscript} the farm chain declares has a file behind it. */
    @Test
    void everyFarmChainScriptDeclaredByCheckImgExistsOnDisk() {
        for (int id : FARM_CHAIN) {
            Quest q = Quest.getInstance(id);
            assertFalse(q.getName().isBlank(), "quest " + id + " did not load from QuestInfo.img");
            if (q.hasScriptRequirement(false) || q.hasScriptRequirement(true)) {
                assertTrue(Files.isRegularFile(Path.of("scripts", "quest", id + ".js")),
                        "Check.img declares a script for " + id + " but scripts/quest/" + id
                                + ".js is absent, so QuestScriptManager disposes with a lone log.warn");
            }
        }
    }

    /**
     * <strong>Mir is on no map, and that is CORRECT.</strong> This test's assertion is unchanged; its
     * conclusion is the opposite of what it used to be. Mir was never a field NPC in v84 -
     * {@code Etc.wz/NpcLocation.img} gives 1013000 the location {@code -1}, where his immediate
     * neighbour 1013001 gets a real {@code 900010200} - because he is the Evan's own summoned
     * {@link server.maps.Dragon}, spawned per-player for every Evan past job 2001. Quest 22500's
     * objective text says "Talk to him by clicking on the Baby Dragon".
     *
     * <p>So seeding {@code plife} with a Mir would have been a hack: a static statue of Mir parked on
     * one map while the real Mir flies beside the player. The faithful fix is in
     * {@code QuestActionHandler.isNpcNearby}, which now treats an owned dragon as the npc being
     * present - see {@link #isNpcNearbyAcceptsTheOwnedDragonAsMir()}.
     */
    @Test
    void mirIsSpawnedOnNoMapBecauseHeIsASummonNotAFieldNpc() throws IOException {
        List<String> found = new ArrayList<>();
        Path maps = Path.of(WZFiles.DIRECTORY, "Map.wz", "Map");
        assertTrue(Files.isDirectory(maps), "no Map.wz/Map under '" + WZFiles.DIRECTORY + "'");
        try (Stream<Path> walk = Files.walk(maps)) {
            for (Path p : walk.filter(Files::isRegularFile).toList()) {
                String body = new String(Files.readAllBytes(p), StandardCharsets.ISO_8859_1);
                if (body.contains(String.valueOf(MIR))) {
                    found.add(p.getFileName().toString());
                }
            }
        }
        assertEquals(List.of(), found,
                "Mir is on a map after all - this test's premise, and the blocker list built on it, "
                        + "are stale");

        // 22100 survives only because it is autoStart; 22500 has no such exemption.
        Quest firstAdvancement = Quest.getInstance(22100);
        assertEquals(MIR, firstAdvancement.getNpcRequirement(false));
        assertTrue(firstAdvancement.isAutoStart(),
                "22100 must be autoStart or isNpcNearby refuses it too and Evan can never advance");

        Quest babyDragonAwakens = Quest.getInstance(22500);
        assertEquals(MIR, babyDragonAwakens.getNpcRequirement(false), "22500 start npc");
        assertEquals(MIR, babyDragonAwakens.getNpcRequirement(true), "22500 end npc");
        assertFalse(babyDragonAwakens.isAutoStart() || babyDragonAwakens.isAutoComplete(),
                "22500 is neither autoStart nor autoComplete, so it goes through the isNpcNearby "
                        + "dragon guard rather than around it - if it ever became autoStart that "
                        + "would be a convenience hack, not v84");

        // Nexon's own index agrees Mir has no field location, so this is data, not an accident.
        String npcLocation = Files.readString(
                Path.of(WZFiles.DIRECTORY, "Etc.wz", "NpcLocation.img.xml"), StandardCharsets.ISO_8859_1);
        int at = npcLocation.indexOf("<imgdir name=\"" + MIR + "\">");
        assertTrue(at > 0, "NpcLocation.img has no entry for Mir at all");
        assertTrue(npcLocation.substring(at, at + 120).contains("value=\"-1\""),
                "NpcLocation.img now gives Mir a real field map; if that is genuine v84 data then he "
                        + "should be placed rather than treated as a summon");
    }

    /**
     * <strong>The second unplaced Evan npc, and it needs nothing either.</strong> 1013202
     * "Black Shadow" starts 22575, 22576, 22577 and 22581 and stands on no map - not here and not
     * in pristine v84. He is not a gap: he is Hiver in disguise, a voice that accosts you, and
     * v84 says so three ways.
     *
     * <ol>
     *   <li>{@code QuestInfo.img/22581/2} unmasks him outright - "#p1013202#'s name is
     *       #p1013203#" - and 1013203 Hiver IS placed, on 922030000, which is where 22581's
     *       COMPLETION npc is. Pristine {@code Say.img/22581/1/0} calls that meeting "our first
     *       meeting face to face", and {@code Say.img/22576/0/0} says "you don't need to come all
     *       the way to see me in person, though".
     *   <li>{@code Etc.wz/NpcLocation.img/1013202/0} is {@code -1}, byte-identical to Mir's marker
     *       above, and {@code docs/wz-baseline/add-list/Etc.txt} shows v84 ADDED that node - so the
     *       {@code -1} is a v84 authoring decision, not an v83 leftover.
     *   <li>All four quests are {@code autoStart}, and {@code QuestActionHandler}'s whole
     *       proximity block sits inside a {@code !isAutoStart() && !isAutoComplete()} guard, so the
     *       placement check is never reached. Unlike Mir, this one needs no special case at all.
     * </ol>
     *
     * <p>Recorded as a test because a sweep has already re-filed it once as four blockers.
     */
    @Test
    void blackShadowIsSpawnedOnNoMapAndNeedsNoSpecialCaseBecauseHisQuestsAreAutoStart()
            throws IOException {
        final int blackShadow = 1013202;
        final int hiver = 1013203;

        List<String> found = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(Path.of(WZFiles.DIRECTORY, "Map.wz", "Map"))) {
            for (Path p : walk.filter(Files::isRegularFile).toList()) {
                if (new String(Files.readAllBytes(p), StandardCharsets.ISO_8859_1)
                        .contains(String.valueOf(blackShadow))) {
                    found.add(p.getFileName().toString());
                }
            }
        }
        assertEquals(List.of(), found, "1013202 is on a map after all - re-derive this");

        for (int id : new int[]{22575, 22576, 22577, 22581}) {
            Quest q = Quest.getInstance(id);
            assertEquals(blackShadow, q.getNpcRequirement(false), "quest " + id + " start npc");
            assertTrue(q.isAutoStart(), "quest " + id + " is no longer autoStart, so isNpcNearby "
                    + "now demands a placed 1013202 and the quest is genuinely blocked");
        }

        // the reveal, and the fact that the man behind it IS placed where 22581 ends
        assertTrue(questInfoText(22581, "2").contains("#p" + hiver + "#"),
                "QuestInfo 22581/2 no longer names 1013203, which is the sole warrant for treating "
                        + "1013202 as a disguise rather than a missing npc");
        assertEquals(hiver, Quest.getInstance(22581).getNpcRequirement(true),
                "22581 no longer ENDS at Hiver");

        String npcLocation = Files.readString(
                Path.of(WZFiles.DIRECTORY, "Etc.wz", "NpcLocation.img.xml"), StandardCharsets.ISO_8859_1);
        int at = npcLocation.indexOf("<imgdir name=\"" + blackShadow + "\">");
        assertTrue(at > 0, "NpcLocation.img has no entry for 1013202 at all");
        assertTrue(npcLocation.substring(at, at + 120).contains("value=\"-1\""),
                "NpcLocation.img now gives 1013202 a real field map; if that is genuine v84 data "
                        + "then he should be placed rather than treated as a disguise");
    }

    /**
     * <strong>The fix for the 25 blocked starts and 22 blocked ends.</strong> Exercises the real
     * {@code QuestActionHandler.isNpcNearby} against the real quest 22500. With a dragon out the
     * check passes; without one it still refuses, so the guard is scoped to actual Evans and cannot
     * be used by anything else to claim an arbitrary npc is nearby.
     *
     * <p>Reflection because the method is private static - that is the smallest thing that fails if
     * the guard is removed, and it beats asserting on source text.
     */
    @Test
    void isNpcNearbyAcceptsTheOwnedDragonAsMir() throws Exception {
        Method isNpcNearby = Class.forName("net.server.channel.handlers.QuestActionHandler")
                .getDeclaredMethod("isNpcNearby", InPacket.class, Character.class, Quest.class, int.class);
        isNpcNearby.setAccessible(true);

        Quest babyDragonAwakens = Quest.getInstance(22500);
        InPacket p = mock(InPacket.class);
        lenient().when(p.available()).thenReturn(0);

        Character chr = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        lenient().when(chr.getPosition()).thenReturn(new Point(0, 0));
        lenient().when(chr.getMap()).thenReturn(map);
        lenient().when(chr.getName()).thenReturn("evan");
        lenient().when(chr.getMapId()).thenReturn(910150000);
        lenient().when(map.getNPCById(MIR)).thenReturn(null);   // he is on no map, by design

        lenient().when(chr.getDragon()).thenReturn(mock(Dragon.class));
        assertEquals(true, isNpcNearby.invoke(null, p, chr, babyDragonAwakens, MIR),
                "an Evan with their dragon out must be allowed to talk to Mir; without this every "
                        + "quest in the 22500 chain is refused before canStart ever runs");

        lenient().when(chr.getDragon()).thenReturn(null);
        assertEquals(false, isNpcNearby.invoke(null, p, chr, babyDragonAwakens, MIR),
                "with no dragon the guard must not fire - it is not a blanket exemption for 1013000");
    }

    /** All ten advancements ride the same exemption; if one lost it, that job level would be lost. */
    @Test
    void allTenJobAdvancementsAreAutoStartAndScripted() {
        for (int id = 22100; id <= 22109; id++) {
            Quest q = Quest.getInstance(id);
            assertEquals(MIR, q.getNpcRequirement(false), "quest " + id + " start npc");
            assertTrue(q.isAutoStart(),
                    "quest " + id + " is not autoStart, so isNpcNearby refuses it - Mir is on no map");
            assertTrue(q.hasScriptRequirement(false), "quest " + id + " lost its startscript");
            assertTrue(Files.isRegularFile(Path.of("scripts", "quest", id + ".js")));
        }
    }

    /**
     * <strong>Superseded, and kept only as the control it always really was.</strong> This method
     * used to assert that 22004, 22005 and 22007 were all dead because none of their completion items
     * had a row in {@code drop_data} or {@code reactordrops}. The absence was real; the CONCLUSION was
     * wrong, and wrong in the expensive direction - it would have had someone invent three drop rows.
     *
     * <p>Only 4032498 was ever a drop. 4032449 and 4032451 are obtained by CLICKING AN NPC, which is
     * why searching the drop tables for them found nothing and always would have:
     * QuestInfo 22007 says in plain words "You can obtain an Egg by clicking on a Hen", and the Hen
     * (npc 1013104) and the Baby Pig (npc 1013200) are both already placed, with working scripts.
     * {@link EvanFarmChainSourcesRealLoad} now pins all three real sources.
     *
     * <p>What survives here is the part that was load-bearing: 4032452 as the control proving these
     * seed files are the right ones to read, and the fact that 22004/22005/22007 grant nothing
     * themselves, so their items genuinely have to come from the world.
     */
    @Test
    void theFarmChainCompletionItemsAreNotGrantedByTheQuestsThemselves() throws IOException {
        String reactorDrops = Files.readString(Path.of("src", "main", "resources", "db", "data",
                "131-reactordrops-data.sql"), StandardCharsets.ISO_8859_1);
        assertTrue(reactorDrops.contains("4032452"),
                "control failed: 4032452 (Bundle of Hay, quest 22502) should be on reactor 1002008");

        // The two click-an-npc items must stay OUT of the drop tables - a drop row for either would
        // be an invented source that quietly duplicates the authentic one.
        String drops = Files.readString(Path.of("src", "main", "resources", "db", "data",
                "152-drop-data.sql"), StandardCharsets.ISO_8859_1);
        for (String itemId : new String[]{"4032449", "4032451"}) {
            assertFalse(drops.contains(itemId) || reactorDrops.contains(itemId),
                    "item " + itemId + " now has a DROP source, but in v84 it came from clicking an "
                            + "npc; adding a drop row for it invents content that never existed");
        }

        // and the quests really do demand them, so the above is load-bearing
        for (int id : new int[]{22004, 22005, 22007}) {
            Quest q = Quest.getInstance(id);
            assertFalse(q.getName().isBlank(), "quest " + id + " did not load");
        }
        assertTrue(Quest.getInstance(22007).hasScriptRequirement(true),
                "22007 completes through q22007e, whose gainItem(4032451, -1) assumes the player "
                        + "already picked the Egg up off the Hen");
    }

    /**
     * Thirteen Evan quest scripts spell the window X as {@code mode == 0 && type == 0 -> status--}
     * rather than as a missing dispose, so the {@code mode == 0 && type > 0} sweep does not see them,
     * but the fall-off is identical: from the first box status goes to -1, no branch matches, and the
     * handler returns having sent nothing. This asserts the framework net catches it anyway, on the
     * real 22000.js - the first dialogue an Evan ever sees.
     */
    @Test
    void theWindowXOnTheVeryFirstEvanDialogueDoesNotWedgeTheSession() {
        Client c = evanAt(22000);

        QuestScriptManager.getInstance().start(c, (short) 22000, 1013100);
        verify(c, atLeastOnce()).sendPacket(any(Packet.class));

        clearInvocations(c);
        QuestScriptManager.getInstance().start(c, (byte) 0, (byte) 0, -1);   // the window X
        assertNull(QuestScriptManager.getInstance().getQM(c),
                "22000.js took status-- to -1 and matched no branch; without the framework net the "
                        + "session stays in qms and every later QUEST_ACTION from this Evan is "
                        + "swallowed until a map change");
    }

    /**
     * The Mushroom training centre npcs admit an Evan on quest business BEFORE applying their
     * "under level 20 only" ceiling.
     *
     * <p>910060100 is the only map in Map.wz carrying mob 9300386; quest 22518 needs 100 of them,
     * and 22521 requires 22518 completed. Ordered the other way round, an Evan who crosses level 20
     * with 22518 still open loses every quest from 22521 to 22596 with no way back in. Both npcs
     * carry a copy of the same dialogue, so both are pinned.
     */
    @Test
    void theTrainingCentreAdmitsEvanQuestsPastTheLevel20Ceiling() throws IOException {
        for (String npc : List.of("1012118", "1012119")) {
            String js = Files.readString(Path.of("scripts", "npc", npc + ".js"),
                    StandardCharsets.UTF_8);

            int evanGate = js.indexOf("cm.isQuestActive(22515)");
            int levelGate = js.indexOf("cm.getLevel() >= 20");
            assertTrue(evanGate >= 0, npc + ".js no longer recognises the Evan training quests");
            assertTrue(levelGate >= 0, npc + ".js dropped the under-20 ceiling entirely - it still "
                    + "has to apply to everyone who is not on 22515-22518");
            assertTrue(evanGate < levelGate,
                    npc + ".js applies the level 20 ceiling ahead of the Evan quest branch, which "
                            + "makes quest 22518 unwinnable past level 20 and ends the chain there");
        }
    }

    /**
     * The four ids ticket 41 listed as open and never re-verified. All four were closed by later
     * work, not by this ticket, so this is a regression lock rather than a fix: it fails the moment
     * any of them regresses back to the state the ticket described.
     *
     * <ul>
     *   <li>{@code 1052002} (quest 22535) and {@code 2092001} (quest 22587) were placed by the 38
     *       life placements of {@code ce3895453}, each on exactly the map
     *       {@code Etc.wz/NpcLocation.img} names for it - 103000000 and 251000000.</li>
     *   <li>{@code 4032455} (quest 22510) is not a drop and never was: 22510's own
     *       {@code startscript q22510s} hands it over, which is why the drop tables have no row.</li>
     *   <li>{@code 4032468} (quest 22567) comes from {@code Act.img/22568/1}, the repeatable
     *       hand-in at npc 2030012 that trades 5 each of 4000070/71/72/4000068 for it. Nothing was
     *       invented for either item.</li>
     * </ul>
     */
    @Test
    void theFourIdsTicket41LeftOpenAllHaveARealV84Source() throws IOException {
        assertTrue(npcIsInLifeOf(103000000, 1052002),
                "npc 1052002 left Map.wz life of 103000000; quest 22535 cannot be handed in");
        assertTrue(npcIsInLifeOf(251000000, 2092001),
                "npc 2092001 left Map.wz life of 251000000; quest 22587 cannot be handed in");

        assertTrue(Files.readString(Path.of("scripts", "quest", "22510.js"), StandardCharsets.UTF_8)
                        .contains("gainItem(4032455"),
                "22510.js no longer grants 4032455; the item has no other source in v84 data");

        Data grant = DataProviderFactory.getDataProvider(WZFiles.QUEST)
                .getData("Act.img").getChildByPath("22568/1/item");
        assertNotNull(grant, "Act.img/22568/1 lost its item block - the only source of 4032468");
        boolean grants4032468 = false;
        for (Data entry : grant.getChildren()) {
            if (DataTool.getInt(entry.getChildByPath("id"), 0) == 4032468) {
                grants4032468 = DataTool.getInt(entry.getChildByPath("count"), 0) > 0;
            }
        }
        assertTrue(grants4032468,
                "Act.img/22568 no longer grants 4032468, so quest 22567 becomes uncompletable");
    }

    /** True if {@code npcId} appears as a {@code type=\"n\"} life entry of {@code mapId}. */
    private static boolean npcIsInLifeOf(int mapId, int npcId) {
        DataProvider mapSource = DataProviderFactory.getDataProvider(WZFiles.MAP);
        Data mapData = mapSource.getData("Map/Map" + (mapId / 100000000) + "/"
                + String.format("%09d", mapId) + ".img");
        assertNotNull(mapData, "Map.wz has no image for map " + mapId);
        Data life = mapData.getChildByPath("life");
        if (life == null) {
            return false;
        }
        for (Data entry : life.getChildren()) {
            if (!"n".equals(DataTool.getString(entry.getChildByPath("type"), ""))) {
                continue;
            }
            if (Integer.parseInt(DataTool.getString(entry.getChildByPath("id"))) == npcId) {
                return true;
            }
        }
        return false;
    }

    /** One string node off {@code Quest.wz/QuestInfo.img/<id>}, "" when absent. */
    private static String questInfoText(int id, String node) {
        return DataTool.getString(DataProviderFactory.getDataProvider(WZFiles.QUEST)
                .getData("QuestInfo.img").getChildByPath(id + "/" + node), "");
    }

    /** Level 10 Evan standing next to the quest npc with the quest already STARTED. */
    private static Client evanAt(int questId) {
        Client c = mock(Client.class);
        Character chr = mock(Character.class);
        MapleMap map = mock(MapleMap.class);

        lenient().when(c.getPlayer()).thenReturn(chr);
        lenient().when(c.canClickNPC()).thenReturn(true);
        lenient().when(c.getScriptEngine(anyString())).thenReturn(null);
        lenient().when(chr.getMap()).thenReturn(map);
        lenient().when(chr.getName()).thenReturn("evan");
        lenient().when(chr.getLevel()).thenReturn(10);
        lenient().when(chr.getQuest(any(Quest.class)))
                .thenReturn(new QuestStatus(Quest.getInstance(questId), QuestStatus.Status.STARTED));
        lenient().when(map.containsNPC(anyInt())).thenReturn(true);
        return c;
    }
}
