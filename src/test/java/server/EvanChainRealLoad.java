package server;

import client.Character;
import client.Client;
import client.QuestStatus;
import net.packet.Packet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import provider.Data;
import provider.DataProvider;
import provider.DataProviderFactory;
import provider.DataTool;
import provider.wz.WZFiles;
import scripting.quest.QuestScriptManager;
import server.maps.MapleMap;
import server.quest.Quest;

import java.io.IOException;
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
     * <strong>The chain's top blocker.</strong> Mir is the quest npc of all ten job advancements and
     * of every quest in the 22500 chain, and he is spawned on no map in this WZ at all - the literal
     * does not occur anywhere under {@code Map.wz/Map}. {@code plife}, the only other source
     * {@code MapFactory} reads, cannot rescue him for the {@code 22500} chain either, because nothing
     * in the repo seeds it.
     *
     * <p>{@code QuestActionHandler.isNpcNearby} lets an {@code autoStart} or {@code autoComplete}
     * quest past without an npc on the map, which is the only reason 22100..22109 still work. 22500
     * is neither, so it is refused at {@code getNPCById(1013000) == null} before {@code canStart}.
     */
    @Test
    void mirIsSpawnedOnNoMapWhichKillsTheEntire22500Chain() throws IOException {
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
                "22500 is neither autoStart nor autoComplete, so isNpcNearby demands Mir on the map "
                        + "and he is nowhere - the first quest after the 1st job advancement is dead");
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
     * <strong>The blocker the owner hits first.</strong> 22004, 22005 and 22007 each complete on an
     * etc item that the player must find in the world - the quest's own {@code Act.img/0} hands out
     * nothing - and no mob drop and no reactor drop in this server grants any of the three. 22007 is
     * the prerequisite of 22100, so the chain cannot reach the job advancement at all.
     *
     * <p>4032452 is the control: the same kind of item, for 22502, wired to reactor 1002008. Without
     * it, "no seed file mentions these ids" would also be the result of reading the wrong files.
     */
    @Test
    void threeFarmChainCompletionItemsHaveNoDropSourceAtAll() throws IOException {
        String drops = Files.readString(Path.of("src", "main", "resources", "db", "data",
                "152-drop-data.sql"), StandardCharsets.ISO_8859_1);
        String reactorDrops = Files.readString(Path.of("src", "main", "resources", "db", "data",
                "131-reactordrops-data.sql"), StandardCharsets.ISO_8859_1);

        assertTrue(reactorDrops.contains("4032452"),
                "control failed: 4032452 (Bundle of Hay, quest 22502) should be on reactor 1002008");

        // 4032498 Thick Branch x3 (22004), 4032449 Piglet (22005), 4032451 Egg (22007)
        for (String itemId : new String[]{"4032498", "4032449", "4032451"}) {
            assertFalse(drops.contains(itemId) || reactorDrops.contains(itemId),
                    "item " + itemId + " now has a drop source; the farm-chain blocker may be fixed");
        }

        // and the quests really do demand them, so the absence above is load-bearing
        for (int id : new int[]{22004, 22005, 22007}) {
            Quest q = Quest.getInstance(id);
            assertFalse(q.getName().isBlank(), "quest " + id + " did not load");
        }
        assertTrue(Quest.getInstance(22007).hasScriptRequirement(true),
                "22007 completes through q22007e, which does a gainItem(4032451, -1) the player "
                        + "can never satisfy");
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
