package server;

import client.Client;
import net.packet.Packet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import provider.Data;
import provider.DataProvider;
import provider.DataProviderFactory;
import provider.DataTool;
import provider.wz.WZFiles;
import scripting.map.MapScriptManager;
import scripting.quest.QuestScriptManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Walks the early game in PLAY ORDER, hop by hop, for a fresh Explorer and a fresh Evan, and asks of
 * each hop the only question that matters: <em>can a character who has done only the previous steps
 * take this one?</em>
 *
 * <pre>
 *   mvnw.cmd test -Dtest=EarlyGamePlayOrderRealLoad
 * </pre>
 *
 * <p>This is deliberately a different question from the ones its neighbours answer.
 * {@link V84EvanQuestRealLoad} proves the ids <em>load</em>; {@link EvanChainRealLoad} proves the
 * givers are <em>placed</em> and the scripts <em>fire</em>; {@link EvanFarmChainSourcesRealLoad}
 * proves each fetch item has a <em>real source</em>; {@link MapAndPortalScriptsRealLoad} proves
 * thirteen individual hooks behave. None of them walks a route end to end, and the route is where
 * the composition bug turned out to be - see
 * {@link #theBabyPigMapScriptStartsItsMarkerQuestSoQuest22005IsFinishable()}.
 *
 * <p><strong>Not a {@code *Test} class on purpose</strong>, same reason as {@link Quest1021RealLoad}:
 * {@link WZFiles#DIRECTORY} is a {@code static final} resolved once per JVM and
 * {@code MobSkillFactoryTest} points {@code wz-path} at a {@code @TempDir} with no {@code Quest.wz}.
 *
 * <p><strong>Non-vacuity, measured rather than asserted.</strong> Three constants above were
 * mutated one at a time and the suite re-run; each produced exactly one failure and named the right
 * thing:
 * <pre>
 *   EXPLORER_ROUTE hop 1020000 -&gt; 2000001   walk:182  "the portal's tm no longer points there"
 *   KNOWN_UNSCRIPTED_EARLY_QUESTS minus 1028         "the early-game ... set moved"
 *   expected end level 8 -&gt; 9                        "the farm chain's total exp moved ... at 1830"
 * </pre>
 * The blocker test carries its own control inline: it requires {@code evanTogether} to return
 * {@code true} from the same {@code runMapScript} call that {@code babyPigMap} must fail, so
 * {@code assertFalse} there cannot be an artefact of the harness.
 */
class EarlyGamePlayOrderRealLoad {

    /** How the player gets from one map to the next. Each kind is checked differently. */
    private enum Kind {
        /** A plain {@code pt=2} portal. The server moves the player; nothing else is needed. */
        PORTAL,
        /** A {@code pt=7} portal whose {@code script} must exist and must warp to the target. */
        SCRIPT_PORTAL,
        /** An NPC that must be in the map's {@code life} and whose script must warp to the target. */
        NPC,
        /**
         * A cutscene map: the client plays {@code Effect/Direction4.img/<scene>} and then asks to
         * move itself, with no portal involved. The target must be the map's own {@code returnMap},
         * and {@code ChangeMapHandler} must whitelist it - which
         * {@link EvanFarmChainSourcesRealLoad#everyEvanCutsceneWarpTargetIsWhitelistedInChangeMapHandler()}
         * pins separately.
         */
        CUTSCENE
    }

    private record Hop(int from, Kind kind, String via, int to, String note) {
    }

    /**
     * Creation map to the farm, in the order the data puts them. Read off Map.wz portal/life nodes
     * and the scripts, not off a wiki: every {@code via} below is a name that appears verbatim in
     * the map image under {@code wz/Map.wz/Map}.
     */
    private static final List<Hop> EVAN_ROUTE = List.of(
            new Hop(900010000, Kind.PORTAL, "in00", 900010100, "EvanCreator START_MAP -> the dream"),
            new Hop(900010100, Kind.SCRIPT_PORTAL, "contactDragon", 900090100, "touch the dragon"),
            new Hop(900090100, Kind.CUTSCENE, "meetWithDragon", 900010200, "the dragon speaks"),
            new Hop(900010200, Kind.NPC, "1013001", 900090101, "the sleeping dragon npc"),
            new Hop(900090101, Kind.CUTSCENE, "PromiseDragon", 100030100, "wake up in Evan's room"),
            new Hop(100030100, Kind.PORTAL, "out00", 100030101, "Evan's room -> Mom (1013100)"),
            new Hop(100030101, Kind.SCRIPT_PORTAL, "evanlivingRoom", 100030102, "-> Utah (1013101)"),
            new Hop(100030102, Kind.SCRIPT_PORTAL, "evanGarden0", 100030200, "living room -> garden"),
            new Hop(100030200, Kind.PORTAL, "west00", 100030300, "garden -> the farm (Gustav)"));

    /**
     * A fresh Explorer: {@code BeginnerCreator} START_MAP {@code MapId.MUSHROOM_TOWN} straight down
     * Maple Road, across Maple Island and onto Shanks' boat to Lith Harbour, where the job
     * advancement content starts. Every hop but the last is an ordinary {@code pt=2} portal.
     */
    private static final List<Hop> EXPLORER_ROUTE = List.of(
            new Hop(10000, Kind.PORTAL, "out00", 20000, "BeginnerCreator START_MAP"),
            new Hop(20000, Kind.PORTAL, "out00", 30000, "Maple Road"),
            new Hop(30000, Kind.PORTAL, "out00", 40000, "Maple Road"),
            new Hop(40000, Kind.PORTAL, "out00", 50000, "the combat tutorial map, quest 1035"),
            new Hop(50000, Kind.PORTAL, "east00", 1000000, "-> Amherst"),
            new Hop(1000000, Kind.PORTAL, "east00", 1010000, "Amherst -> Tree Dungeon"),
            new Hop(1010000, Kind.PORTAL, "east00", 1020000, "-> Pig Beach road"),
            new Hop(1020000, Kind.PORTAL, "east00", 2000000, "-> Southperry"),
            new Hop(2000000, Kind.NPC, "22000", 104000000, "Shanks' boat -> Lith Harbour"));

    /** The five Explorer 1st job advancers, and the map each stands on. */
    private static final Map<Integer, Integer> FIRST_JOB_ADVANCERS = Map.of(
            1022000, 102000003,   // Dances with Balrog, Warriors' Sanctuary
            1032001, 101000003,   // Grendel the Really Old, Magic Library
            1012100, 100000201,   // Athena Pierce, Bowman Instructional School
            1052001, 103000003,   // Dark Lord, Kerning hideout
            1090000, 120000101);  // Kyrin, Nautilus

    /**
     * Early-game quests (id &lt; 3000) whose Check.img declares a start/end script that has no
     * {@code scripts/quest/<id>.js}. 1048-1054 are the retired 2009 event chain that
     * {@link MedalQuestFallbackRealLoad#quests1048To1054AreRetiredEventContent()} already pins;
     * 1028 "To Lith Harbor!" is redundant with {@code scripts/npc/22000.js}, which sails the boat
     * on its own; 2147 is Korean-only leftover content.
     */
    private static final List<Integer> KNOWN_UNSCRIPTED_EARLY_QUESTS =
            List.of(1028, 1048, 1049, 1050, 1051, 1052, 1053, 1054, 2147);

    /** The farm chain in Check.img prerequisite order. */
    private static final int[] FARM_CHAIN = {22000, 22001, 22002, 22003, 22004, 22005, 22006, 22007};

    /** {@code qm.gainExp(NNN)} - the exp that lives in the scripts rather than in Act.img. */
    private static final Pattern GAIN_EXP = Pattern.compile("\\bgainExp\\((\\d+)\\)");

    @BeforeAll
    static void muteGraal() {
        System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
        assertTrue(Files.isRegularFile(Path.of(WZFiles.DIRECTORY, "Quest.wz", "Check.img.xml")),
                "wz-path resolved to '" + WZFiles.DIRECTORY + "', which holds no Quest.wz - another "
                        + "test class won the WZFiles.DIRECTORY race, so this says nothing about Evan");
    }

    /**
     * The whole route, hop by hop. A missing portal, a renamed script, an NPC that fell out of a
     * map's {@code life}, or a cutscene map whose {@code returnMap} drifted all fail here, naming the
     * exact step a player would get stuck on.
     *
     * <p>Non-vacuity: {@link #aRouteHopThatDoesNotExistIsReportedAsMissing()} runs the same three
     * lookups against names that are deliberately wrong and requires each to come back empty.
     */
    @Test
    void everyHopFromEvanCharacterCreationToTheFarmExists() throws IOException {
        walk(EVAN_ROUTE);
    }

    /**
     * The Explorer half of the same walk. Nine hops from {@code BeginnerCreator}'s start map to Lith
     * Harbour; the first eight are plain portals, the ninth is Shanks.
     *
     * <p>Note what this can and cannot say. It proves the route <em>exists server-side</em>. It says
     * nothing about whether the v84 client survives the walk - and it does not, today: the crash
     * reports the client uploaded on {@code 2026-08-17T00:01} name {@code FieldID(40000)}, the map
     * hop 4 lands on. That is a client-side WZ read (ZException 38, "Reached the end of the file")
     * and no server-side assertion can see it.
     */
    @Test
    void everyHopFromExplorerCharacterCreationToLithHarbourExists() throws IOException {
        walk(EXPLORER_ROUTE);
    }

    private static void walk(List<Hop> route) throws IOException {
        for (Hop hop : route) {
            String at = "hop " + hop.from() + " -" + hop.via() + "-> " + hop.to() + " (" + hop.note() + ")";
            assertNotNull(mapData(hop.from()), "Map.wz has no image for " + hop.from());
            assertNotNull(mapData(hop.to()), at + ": target map is not in Map.wz");

            switch (hop.kind()) {
                case PORTAL -> {
                    Data portal = portal(hop.from(), hop.via());
                    assertNotNull(portal, at + ": that portal name is gone from Map.wz");
                    assertEquals(hop.to(), DataTool.getInt(portal.getChildByPath("tm"), -1),
                            at + ": the portal's tm no longer points there");
                }
                case SCRIPT_PORTAL -> {
                    assertTrue(portalWithScript(hop.from(), hop.via()),
                            at + ": no portal on " + hop.from() + " names script '" + hop.via() + "'");
                    Path js = Path.of("scripts", "portal", hop.via() + ".js");
                    assertTrue(Files.isRegularFile(js),
                            at + ": scripts/portal/" + hop.via() + ".js is absent, and "
                                    + "GenericPortal.enterPortal only falls back to tm/tn when the "
                                    + "script NAME is null - a declared-but-missing script is a dead "
                                    + "portal, silently");
                    assertTrue(body(js).contains("warp(" + hop.to()),
                            at + ": " + hop.via() + ".js no longer warps to " + hop.to());
                }
                case NPC -> {
                    int npcId = Integer.parseInt(hop.via());
                    assertTrue(npcIsInLifeOf(hop.from(), npcId),
                            at + ": npc " + npcId + " is not in Map.wz life of " + hop.from()
                                    + ", and that map has no ordinary portal at all");
                    Path js = Path.of("scripts", "npc", npcId + ".js");
                    assertTrue(Files.isRegularFile(js), at + ": scripts/npc/" + npcId + ".js is absent");
                    assertTrue(body(js).contains("warp(" + hop.to()),
                            at + ": " + npcId + ".js no longer warps to " + hop.to());
                }
                case CUTSCENE -> {
                    assertEquals(hop.via(), DataTool.getString(
                                    mapData(hop.from()).getChildByPath("info/onUserEnter"), ""),
                            at + ": that map's onUserEnter is no longer " + hop.via());
                    assertTrue(Files.isRegularFile(Path.of("scripts", "map", "onUserEnter", hop.via() + ".js")),
                            at + ": scripts/map/onUserEnter/" + hop.via() + ".js is absent, so "
                                    + "MapScriptManager.runMapScript returns false with no log line");
                    assertEquals(hop.to(), DataTool.getInt(
                                    mapData(hop.from()).getChildByPath("info/returnMap"), -1),
                            at + ": returnMap drifted; the client scene warp lands elsewhere");
                }
            }
        }
    }

    /**
     * Negative control for {@link #everyHopFromCharacterCreationToTheFarmExists()}. Without this, a
     * lookup helper that returned something truthy for any input would make the whole walk green and
     * meaningless.
     */
    @Test
    void aRouteHopThatDoesNotExistIsReportedAsMissing() {
        assertNull(portal(900010000, "out99"), "portal lookup invented a portal that is not there");
        assertFalse(portalWithScript(900010000, "notAScriptName"),
                "script-portal lookup matched a script name no portal declares");
        assertFalse(npcIsInLifeOf(900010000, 1013001),
                "npc lookup found 1013001 on 900010000, where Map.wz places no life at all");
    }

    /**
     * Every map either walk passes through, on both sides of every hop, must have a file behind the
     * {@code onUserEnter} it declares. A declared-but-absent hook is the quietest failure in the
     * codebase: {@code MapScriptManager.runMapScript} returns false with no log line at all
     * (MapScriptManager.java:71-73), which is what left 900010000 dead before f66872cc.
     */
    @Test
    void everyMapOnEitherWalkHasTheOnUserEnterScriptItDeclares() {
        List<Integer> maps = new ArrayList<>();
        for (Hop hop : EVAN_ROUTE) {
            maps.add(hop.from());
            maps.add(hop.to());
        }
        for (Hop hop : EXPLORER_ROUTE) {
            maps.add(hop.from());
            maps.add(hop.to());
        }

        int declared = 0;
        List<String> missing = new ArrayList<>();
        for (int mapId : maps) {
            String hook = DataTool.getString(mapData(mapId).getChildByPath("info/onUserEnter"), "");
            if (hook.isBlank()) {
                continue;
            }
            declared++;
            if (!Files.isRegularFile(Path.of("scripts", "map", "onUserEnter", hook + ".js"))) {
                missing.add(mapId + " declares onUserEnter '" + hook + "'");
            }
        }

        assertTrue(declared >= 12,
                "only " + declared + " of these maps declared an onUserEnter, so the scan found "
                        + "nothing to check and would pass whatever the scripts dir held");
        assertEquals(List.of(), missing, "these maps are on the walk and their entry hook is silent");
    }

    /**
     * Quest 1035 "Todd's Hunting Method" is the Explorer's first kill-and-collect, and the first
     * thing on Maple Road that needs the world to cooperate rather than an NPC to hand something
     * over. Three separate pieces have to line up, and the drop row is the one that historically
     * goes missing.
     */
    @Test
    void theExplorerCombatTutorialQuestHasARealMobAndARealDropRow() throws IOException {
        assertEquals(9300018, DataTool.getInt(check("1035/1/mob/0/id"), -1),
                "1035 no longer asks for mob 9300018");
        assertEquals(4031802, DataTool.getInt(check("1035/1/item/0/id"), -1),
                "1035 no longer asks for item 4031802");
        assertTrue(mobIsInLifeOf(40000, 9300018),
                "mob 9300018 is not spawned on map 40000, so 1035 cannot be completed and the "
                        + "Maple Road tutorial dead-ends at Todd");
        assertTrue(Files.isRegularFile(Path.of(WZFiles.DIRECTORY, "Mob.wz", "9300018.img.xml")),
                "Mob.wz has no image for 9300018, so MapFactory cannot spawn it at all");

        String drops = body(Path.of("src", "main", "resources", "db", "data", "152-drop-data.sql"));
        assertTrue(drops.contains("(9300018, 4031802, 1, 1, 1035,"),
                "the 9300018 -> 4031802 row is gone from the drop seed, so the Shellpiece never "
                        + "drops and 1035 is unfinishable");
    }

    /**
     * The Explorer's first job advancement. Each of the five advancers has to be standing on a real
     * map and has to have a script that really changes job - an advancer with no script falls through
     * {@code NPCTalkHandler} to {@code log.warn("NPC ... is not coded")} and the player is a Beginner
     * forever.
     */
    @Test
    void everyExplorerFirstJobAdvancerIsPlacedWithAScriptThatChangesJob() throws IOException {
        for (Map.Entry<Integer, Integer> e : FIRST_JOB_ADVANCERS.entrySet()) {
            int npcId = e.getKey();
            assertTrue(npcIsInLifeOf(e.getValue(), npcId),
                    "npc " + npcId + " is not in Map.wz life of " + e.getValue());
            Path js = Path.of("scripts", "npc", npcId + ".js");
            assertTrue(Files.isRegularFile(js), "scripts/npc/" + npcId + ".js is absent");
            assertTrue(body(js).contains("changeJob"),
                    "scripts/npc/" + npcId + ".js no longer changes job, so this class cannot advance");
        }
    }

    /**
     * The early-game band (id &lt; 3000) of the "Check.img declares a script that has no file" sweep.
     * A missing file is silent - {@code AbstractScriptManager} returns null and
     * {@code QuestScriptManager} disposes with one {@code log.warn} - so the player clicks and
     * nothing happens. d2c68b1 closed four of these; this pins the remainder so a fifth cannot
     * appear unnoticed, and so that closing one of the nine shows up as a failing expectation rather
     * than as silence.
     */
    @Test
    void theOnlyEarlyGameQuestsMissingADeclaredScriptAreTheKnownRetiredOnes() {
        Data checkImg = DataProviderFactory.getDataProvider(WZFiles.QUEST).getData("Check.img");
        List<Integer> missing = new ArrayList<>();
        int declared = 0;
        for (Data quest : checkImg.getChildren()) {
            int id = Integer.parseInt(quest.getName());
            if (id >= 3000) {
                continue;
            }
            boolean declaresScript = false;
            for (Data side : quest.getChildren()) {
                declaresScript |= side.getChildByPath("startscript") != null
                        || side.getChildByPath("endscript") != null;
            }
            if (!declaresScript) {
                continue;
            }
            declared++;
            if (!Files.isRegularFile(Path.of("scripts", "quest", id + ".js"))) {
                missing.add(id);
            }
        }

        assertTrue(declared >= 50,
                "only " + declared + " early-game quests were seen to declare a script; Check.img "
                        + "did not load properly and this sweep proves nothing");
        assertEquals(KNOWN_UNSCRIPTED_EARLY_QUESTS, missing.stream().sorted().toList(),
                "the early-game declared-but-unscripted set moved");
    }

    /**
     * <strong>BLOCKER, and it is new tonight.</strong> Map 900020110 - the piglet hollow, quest
     * 22005 - has no inbound portal anywhere in Map.wz, so until {@code ChangeMapHandler} learned the
     * Evan cutscene warps nobody had ever arrived on it. Now that they can, its {@code onUserEnter}
     * runs for the first time, and it cannot do its job:
     *
     * <pre>
     *   scripts/map/onUserEnter/babyPigMap.js:25
     *       ms.getClient().getQM().forceStartQuest(22015);
     * </pre>
     *
     * <p>{@code Client.getQM()} (Client.java:1210) delegates straight to
     * {@code QuestScriptManager.getQM(this)}, which is the map of <em>open quest-script sessions</em>.
     * Arriving on a map is not a quest script, so it is null, and the call throws inside Graal.
     * {@code MapScriptManager.runMapScript} swallows that into a log line and returns false, so the
     * player sees only the {@code unlockUI()} that ran on the line before.
     *
     * <p>Consequence, in play order: marker quest 22015 never starts, so
     * {@code scripts/npc/1013200.js} answers "You are too far from the Piglet" forever, so 4032449 is
     * unobtainable, so 22005 cannot complete - and 22005 gates 22006 -&gt; 22007 -&gt; 22100, the 1st
     * job advancement. A fresh Evan stops at level 6. {@code scripts/portal/babyPigOut.js} also
     * refuses to open until 22015 is COMPLETE, so the player is additionally stuck on the map until
     * they log out (Character.java:8328 saves them at the map's forcedReturn, 100030300).
     *
     * <p><strong>FIXED, and this test now guards the fix.</strong> {@code MapScriptMethods extends
     * AbstractPlayerInteraction}, which already carries {@code forceStartQuest(int)}, so
     * {@code ms.forceStartQuest(22015)} needs no manager at all. The assertions below were inverted
     * when that landed, exactly as this javadoc previously said they would be.
     *
     * <p>Worth remembering why it was invisible for so long: the script is old, but 900020110 has no
     * inbound portal anywhere in Map.wz, so the map was unreachable and the script had never once
     * run. Teaching {@code ChangeMapHandler} to honour Evan's client-side scene warps is what made
     * the map reachable - and immediately exposed a latent bug on arrival. A composition failure,
     * not a regression in either change.
     */
    @Test
    void theBabyPigMapScriptStartsItsMarkerQuestSoQuest22005IsFinishable() throws IOException {
        // 1. The production reason, exercised as production code and not stubbed: with no quest
        //    script session open, this is what Client.getQM() returns.
        assertNull(QuestScriptManager.getInstance().getQM(mock(Client.class)),
                "QuestScriptManager.getQM now answers for a client with no session; if that is real "
                        + "then babyPigMap.js may have started working and this test is stale");

        // 2. The script really is the one Map.wz names, and it really does route through getQM().
        assertEquals("babyPigMap", DataTool.getString(
                        mapData(900020110).getChildByPath("info/onUserEnter"), ""),
                "900020110 lost its babyPigMap onUserEnter");
        String script = body(Path.of("scripts", "map", "onUserEnter", "babyPigMap.js"));
        // Match the CALL, not the bare name: the script's comment explains why getQM() was wrong,
        // and this reads the whole file including comments.
        assertFalse(script.contains("getQM().forceStartQuest"),
                "babyPigMap.js is routing through getQM() again - that is null on map entry, so 22015 "
                        + "would never start and the Evan chain would hard-stop at 22005 once more");
        assertTrue(script.contains("ms.forceStartQuest(22015)"),
                "babyPigMap.js must start 22015 through MapScriptMethods' own inherited "
                        + "forceStartQuest; without it the Piglet is unobtainable");

        // 3. Positive control: an onUserEnter script that never went through getQM runs to
        //    completion under the exact same harness, so the result below is about babyPigMap and
        //    not about the harness.
        assertTrue(MapScriptManager.getInstance().runMapScript(
                        mock(Client.class), "onUserEnter/evanTogether", false),
                "control failed: evanTogether.js did not run, so this harness proves nothing");

        // 4. The fix itself. Before f4bccbc0f's successor this returned false: a packet left first
        //    (the unlockUI) and the script then died on the next line inside Graal, swallowed by
        //    MapScriptManager. It must now run to completion.
        Client c = mock(Client.class);
        boolean ran = MapScriptManager.getInstance().runMapScript(c, "onUserEnter/babyPigMap", false);
        verify(c, atLeastOnce()).sendPacket(any(Packet.class));
        assertTrue(ran,
                "babyPigMap.js did not run to completion, so marker quest 22015 never starts, the "
                        + "Baby Pig keeps answering \"you are too far from the Piglet\", and quest "
                        + "22005 - and with it 22006, 22007 and the 22100 job advancement - is unfinishable");
    }

    /**
     * The eight farm quests carry level gates 2,3,4,5,6,7,7 and the chain has to pay for them itself:
     * there is nothing else on those maps a level-1 Evan can do. This adds up the exp the chain
     * really grants - {@code Act.img/<id>/1/exp} plus the {@code qm.gainExp(n)} the scripts hold -
     * and walks it through the real {@link constants.game.ExpTable}.
     *
     * <p>Raw exp is the honest floor: the world rate is 1 and {@code GameConstants.getExpRateForLevel}
     * returns exactly {@code 1.0f} for every level below 10, so nothing multiplies these numbers up
     * before the 22100 gate.
     *
     * <p>The second half is the part the owner needs to know: the chain's own exp lands a fresh Evan
     * at level 8, and 22100 - the 1st job advancement - wants level 10. Those last two levels have to
     * be ground, and the only mobs an Evan can reach before advancing are the eight Stumps on the
     * farm ({@code 100030310}, with the Dragon Turtles, is gated behind {@code evanFarmCT.js} on
     * {@code job != 2001}). That is not a defect, but it is a wall, and if someone ever "fixes" it by
     * inflating a reward this test says so.
     */
    @Test
    void theChainsOwnExpClearsEveryGateUpTo22007ButLeavesTheJobAdvancementShort() throws IOException {
        int total = 0;
        int level = 1;
        for (int id : FARM_CHAIN) {
            int lvmin = DataTool.getInt(check(id + "/0/lvmin"), 0);
            assertTrue(level >= lvmin,
                    "quest " + id + " needs level " + lvmin + " but the chain has only paid out "
                            + total + " exp by then, which is level " + level
                            + " - the farm chain cannot fund its own level gates");
            total += questExp(id);
            level = levelFor(total);
        }

        assertEquals(8, level,
                "the farm chain's total exp moved; it used to land a fresh Evan on level 8 at " + total);
        assertEquals(10, DataTool.getInt(check("22100/0/lvmin"), 0),
                "22100 no longer wants level 10");
        assertTrue(level < 10,
                "the chain now funds the 1st job advancement outright, which v84 did not - check "
                        + "whether a reward was inflated");
    }

    /**
     * Every item id the chain hands the player has to be a real item. Covers both sources:
     * {@code Act.img} grants and the {@code qm.gainItem(id, ...)} calls the scripts make - 22002's
     * hat (1003028) and 22004's fence (3010097) exist only in the scripts, Act.img grants neither.
     *
     * <p><strong>One known gap, found by this test and left standing deliberately.</strong> 1003028
     * has a real {@code wz/Character.wz/Cap/01003028.img.xml} - so the hat renders and its stats
     * load - but <em>no</em> entry in {@code String.wz/Eqp.img}, where its neighbours 1003027 and
     * 1003031 both sit. {@code ItemInformationProvider.getName} returns null for it
     * (ItemInformationProvider.java:1338-1341), which is handled, so the player gets a working but
     * nameless hat. Cosmetic, not a blocker - it is listed rather than asserted-away so that a
     * <em>new</em> missing id still fails this test.
     */
    @Test
    void everyItemTheFarmChainGrantsExistsInStringWz() throws IOException {
        Map<Integer, String> granted = new LinkedHashMap<>();
        for (int id : FARM_CHAIN) {
            for (String node : new String[]{id + "/0/item", id + "/1/item"}) {
                Data items = act(node);
                if (items == null) {
                    continue;
                }
                for (Data entry : items.getChildren()) {
                    if (DataTool.getInt(entry.getChildByPath("count"), 0) > 0) {
                        granted.put(DataTool.getInt(entry.getChildByPath("id"), 0), "Act.img/" + node);
                    }
                }
            }
            Path js = Path.of("scripts", "quest", id + ".js");
            if (Files.isRegularFile(js)) {
                Matcher m = Pattern.compile("gainItem\\((\\d{7,8})\\s*,\\s*(-?\\d+|true)")
                        .matcher(body(js));
                while (m.find()) {
                    if (!m.group(2).equals("-1")) {   // -1 is a take, not a grant
                        granted.put(Integer.parseInt(m.group(1)), "scripts/quest/" + id + ".js");
                    }
                }
            }
        }

        assertTrue(granted.size() >= 8,
                "only " + granted.size() + " granted items were found across the whole chain; the "
                        + "scan stopped matching, so this test would pass no matter what");

        List<String> missing = new ArrayList<>();
        for (Map.Entry<Integer, String> e : granted.entrySet()) {
            if (itemName(e.getKey()) == null) {
                missing.add(e.getKey() + " (from " + e.getValue() + ")");
            }
        }
        assertEquals(List.of(), missing,
                "the set of granted-but-unnamed ids moved. Every id listed here is a granted item "
                        + "with no String.wz name - a new gap. 1003028 used to sit here and was "
                        + "named in Eqp.img.xml, which is why the expectation is now empty");
    }

    // ------------------------------------------------------------------ helpers

    private static int questExp(int questId) throws IOException {
        int exp = DataTool.getInt(act(questId + "/1/exp"), 0);
        Path js = Path.of("scripts", "quest", questId + ".js");
        if (Files.isRegularFile(js)) {
            Matcher m = GAIN_EXP.matcher(body(js));
            while (m.find()) {
                exp += Integer.parseInt(m.group(1));
            }
        }
        return exp;
    }

    /** Real {@link constants.game.ExpTable}: {@code exp[n]} is the cost of the step n -&gt; n+1. */
    private static int levelFor(int totalExp) {
        int level = 1;
        int left = totalExp;
        while (left >= constants.game.ExpTable.getExpNeededForLevel(level)) {
            left -= constants.game.ExpTable.getExpNeededForLevel(level);
            level++;
        }
        return level;
    }

    /** Null when no String.wz image names the id, which is what "the item does not exist" looks like. */
    private static String itemName(int itemId) {
        DataProvider strings = DataProviderFactory.getDataProvider(WZFiles.STRING);
        for (String[] img : new String[][]{
                {"Consume.img", ""}, {"Etc.img", "Etc/"}, {"Ins.img", ""}, {"Cash.img", ""}}) {
            Data d = strings.getData(img[0]).getChildByPath(img[1] + itemId);
            if (d != null) {
                return DataTool.getString(d.getChildByPath("name"), "");
            }
        }
        Data eqp = strings.getData("Eqp.img").getChildByPath("Eqp");
        if (eqp != null) {
            for (Data category : eqp.getChildren()) {
                Data d = category.getChildByPath(String.valueOf(itemId));
                if (d != null) {
                    return DataTool.getString(d.getChildByPath("name"), "");
                }
            }
        }
        return null;
    }

    private static Data check(String path) {
        return DataProviderFactory.getDataProvider(WZFiles.QUEST).getData("Check.img").getChildByPath(path);
    }

    private static Data act(String path) {
        return DataProviderFactory.getDataProvider(WZFiles.QUEST).getData("Act.img").getChildByPath(path);
    }

    private static Data mapData(int mapId) {
        return DataProviderFactory.getDataProvider(WZFiles.MAP).getData("Map/Map" + (mapId / 100000000)
                + "/" + String.format("%09d", mapId) + ".img");
    }

    /** The {@code portal} child whose {@code pn} is {@code name}, or null. */
    private static Data portal(int mapId, String name) {
        Data portals = mapData(mapId).getChildByPath("portal");
        if (portals == null) {
            return null;
        }
        for (Data p : portals.getChildren()) {
            if (name.equals(DataTool.getString(p.getChildByPath("pn"), ""))) {
                return p;
            }
        }
        return null;
    }

    private static boolean portalWithScript(int mapId, String scriptName) {
        Data portals = mapData(mapId).getChildByPath("portal");
        if (portals == null) {
            return false;
        }
        for (Data p : portals.getChildren()) {
            if (scriptName.equals(DataTool.getString(p.getChildByPath("script"), ""))) {
                return true;
            }
        }
        return false;
    }

    private static boolean npcIsInLifeOf(int mapId, int npcId) {
        return lifeOfTypeContains(mapId, "n", npcId);
    }

    private static boolean mobIsInLifeOf(int mapId, int mobId) {
        return lifeOfTypeContains(mapId, "m", mobId);
    }

    /** Mob life ids are zero-padded strings ({@code "0130100"}); parsing as int makes them comparable. */
    private static boolean lifeOfTypeContains(int mapId, String type, int id) {
        Data life = mapData(mapId).getChildByPath("life");
        if (life == null) {
            return false;
        }
        for (Data entry : life.getChildren()) {
            if (!type.equals(DataTool.getString(entry.getChildByPath("type"), ""))) {
                continue;
            }
            if (Integer.parseInt(DataTool.getString(entry.getChildByPath("id")).trim()) == id) {
                return true;
            }
        }
        return false;
    }

    private static String body(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.ISO_8859_1);
    }
}
