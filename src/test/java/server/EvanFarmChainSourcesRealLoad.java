package server;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import provider.Data;
import provider.DataProvider;
import provider.DataProviderFactory;
import provider.DataTool;
import provider.wz.WZFiles;
import server.quest.Quest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the <em>real</em> GMS v84 source of the three farm-chain completion items, and asserts each
 * is now actually satisfiable. Companion to {@link EvanChainRealLoad}, which found the blockage;
 * this one pins the fix so it cannot silently rot.
 *
 * <pre>
 *   mvnw.cmd test -Dtest=EvanFarmChainSourcesRealLoad
 * </pre>
 *
 * <p><strong>Not a {@code *Test} class on purpose</strong>, same reason as {@link EvanChainRealLoad}:
 * {@link WZFiles#DIRECTORY} is a {@code static final} resolved once per JVM and {@code
 * MobSkillFactoryTest} points {@code wz-path} at a {@code @TempDir} with no {@code Quest.wz} in it.
 *
 * <p>The headline correction this class encodes: only <em>one</em> of the three items was ever a
 * drop. The other two are obtained by CLICKING AN NPC, which is why no amount of searching
 * {@code drop_data} / {@code reactordrops} found them - they were never supposed to be there.
 *
 * <pre>
 *   4032498 Thick Branch  x3  22004  mob 130100 "Stump"     drop_data, quest-gated
 *   4032449 Piglet        x1  22005  npc 1013200 "Baby Pig" scripts/npc/1013200.js
 *   4032451 Egg           x1  22007  npc 1013104 "Hen"      scripts/npc/1013104.js
 * </pre>
 */
class EvanFarmChainSourcesRealLoad {

    private static final int THICK_BRANCH = 4032498;
    private static final int PIGLET = 4032449;
    private static final int EGG = 4032451;

    private static final int STUMP = 130100;
    private static final int BABY_PIG_NPC = 1013200;
    private static final int HEN_NPC = 1013104;

    /** The hidden marker quest that arms the Baby Pig NPC. Deliberately absent from Quest.wz. */
    private static final int PIGLET_MARKER_QUEST = 22015;

    @BeforeAll
    static void muteGraal() {
        System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
        assertTrue(Files.isRegularFile(Path.of(WZFiles.DIRECTORY, "Quest.wz", "Check.img.xml")),
                "wz-path resolved to '" + WZFiles.DIRECTORY + "', which holds no Quest.wz - another "
                        + "test class won the WZFiles.DIRECTORY race, so this says nothing about Evan");
    }

    /**
     * The load-bearing distinction behind every other assertion here. 22003 and 22006 hand the player
     * their fetch item on the quest's own {@code Act.img/0} start node, so they are self-contained.
     * 22004, 22005 and 22007 have EMPTY Act nodes at both ends, so their items must come from the
     * world. If someone ever "fixes" one of these by adding an Act grant, that is not v84 and this
     * test is where it gets caught.
     */
    @Test
    void onlyTheSelfContainedFetchQuestsGrantTheirOwnItem() {
        assertEquals(4032448, actStartItem(22003), "22003 must still grant Lunch Made with Love");
        assertEquals(4032450, actStartItem(22006), "22006 must still grant the Empty Lunch Box");

        for (int id : new int[]{22004, 22005, 22007}) {
            assertNull(questAct(id + "/0/item"),
                    "quest " + id + " start Act.img now grants an item; in v84 it granted nothing and "
                            + "the player had to obtain it in the world");
            assertNull(questAct(id + "/1/item/0/id") == null ? null : positiveGrant(id),
                    "quest " + id + " complete Act.img now GIVES an item rather than only taking one");
        }
    }

    /**
     * 4032498 is the only one of the three that really was a mob drop, and the data says which mob in
     * plain words: String.wz calls 4032498 "A tree branch from a Stump", String.wz calls 130100
     * "Stump", and QuestInfo 22004 says "Defeat some of the #o0130100#s nearby".
     */
    @Test
    void thickBranchIsADropFromStumpAndStumpIsSpawnedOnTheFarm() throws IOException {
        assertEquals("Thick Branch", stringName("Etc.img", "Etc/" + THICK_BRANCH));
        assertEquals("Stump", stringName("Mob.img", String.valueOf(STUMP)));

        String objective = DataTool.getString(
                DataProviderFactory.getDataProvider(WZFiles.QUEST).getData("QuestInfo.img")
                        .getChildByPath("22004/1"), "");
        assertTrue(objective.contains("#o0" + STUMP + "#"),
                "QuestInfo 22004 no longer names mob " + STUMP + " as the source; it read: " + objective);

        assertTrue(mobIsInLifeOf(100030300, STUMP),
                "mob " + STUMP + " is not spawned on the Evan farm map 100030300, so 22004 is "
                        + "uncompletable no matter what drop_data says");

        // the fix itself: an additive changeSet, not an edit to an applied one
        Path seed = Path.of("src", "main", "resources", "db", "data", "155-evan-tutorial-drop-data.sql");
        assertTrue(Files.isRegularFile(seed), "changeSet 155 seed file is missing");
        String sql = Files.readString(seed, StandardCharsets.ISO_8859_1);
        assertTrue(sql.contains("(" + STUMP + ", " + THICK_BRANCH + ", 1, 1, 22004, 80000)"),
                "155 no longer carries the Stump -> Thick Branch row, quest-gated to 22004");

        String changelog = Files.readString(
                Path.of("src", "main", "resources", "db", "changelog-data.xml"), StandardCharsets.ISO_8859_1);
        assertTrue(changelog.contains("155-evan-tutorial-drop-data.sql"),
                "155 exists on disk but is not registered in changelog-data.xml, so it never runs");
    }

    /**
     * 4032451 was never a drop. QuestInfo 22007 says it outright: "You can obtain an Egg by clicking
     * on a Hen." The Hen is npc 1013104 and it is already placed in the living room, 100030102.
     */
    @Test
    void eggComesFromClickingTheHenNpcWhichIsPlacedOnTheLivingRoomMap() throws IOException {
        assertEquals("Egg", stringName("Etc.img", "Etc/" + EGG));

        String objective = DataTool.getString(
                DataProviderFactory.getDataProvider(WZFiles.QUEST).getData("QuestInfo.img")
                        .getChildByPath("22007/1"), "");
        assertTrue(objective.contains("#p" + HEN_NPC + "#"),
                "QuestInfo 22007 no longer names npc " + HEN_NPC + " as the source; it read: " + objective);

        assertTrue(npcIsInLifeOf(100030102, HEN_NPC),
                "npc " + HEN_NPC + " (Hen) is not in Map.wz life of 100030102, so the Egg is "
                        + "unobtainable and 22007 - which gates 22100, the 1st job advancement - is dead");

        String hen = Files.readString(Path.of("scripts", "npc", HEN_NPC + ".js"), StandardCharsets.ISO_8859_1);
        assertTrue(hen.contains("isQuestStarted(22007)") && hen.contains("gainItem(" + EGG),
                "scripts/npc/" + HEN_NPC + ".js no longer grants " + EGG + " during quest 22007");
    }

    /**
     * 4032449 was never a drop either - you walk to the pig and click it. The Baby Pig npc is placed
     * on both 900020100 and 900020110, and is armed by a hidden marker quest, 22015, that
     * {@code babyPigMap.js} force-starts when the player enters 900020110.
     *
     * <p>22015 is deliberately absent from Quest.wz: {@code Quest.getInstance} returns an empty
     * requirement-free Quest for an unknown id ({@code Quest.java:121-127} bails when {@code reqData}
     * is null), which is exactly what a pure marker wants. The invariant that actually matters is that
     * the three files agree on the SAME id - if they ever drift, the piglet becomes unobtainable and
     * nothing else in the codebase would notice.
     */
    @Test
    void pigletComesFromClickingTheBabyPigNpcArmedByTheHiddenMarkerQuest() throws IOException {
        assertEquals("Piglet", stringName("Etc.img", "Etc/" + PIGLET));

        assertTrue(npcIsInLifeOf(900020110, BABY_PIG_NPC),
                "npc " + BABY_PIG_NPC + " (Baby Pig) is not in Map.wz life of 900020110");
        assertTrue(npcIsInLifeOf(900020100, BABY_PIG_NPC),
                "npc " + BABY_PIG_NPC + " (Baby Pig) is not in Map.wz life of 900020100");

        // QuestInfo 22005 sends the player to 900020100, and that map's onUserEnter is the one that
        // must arm the marker on the map the pig is actually grabbable on.
        String objective = DataTool.getString(
                DataProviderFactory.getDataProvider(WZFiles.QUEST).getData("QuestInfo.img")
                        .getChildByPath("22005/1"), "");
        assertTrue(objective.contains("#m900020100#"),
                "QuestInfo 22005 no longer sends the player to 900020100; it read: " + objective);
        assertEquals("babyPigMap", DataTool.getString(
                        mapData(900020110).getChildByPath("info/onUserEnter"), ""),
                "900020110 lost its babyPigMap onUserEnter, so the marker quest never starts");

        String marker = "22015";
        assertEquals(PIGLET_MARKER_QUEST, Integer.parseInt(marker));
        String mapScript = Files.readString(
                Path.of("scripts", "map", "onUserEnter", "babyPigMap.js"), StandardCharsets.ISO_8859_1);
        String pigScript = Files.readString(
                Path.of("scripts", "npc", BABY_PIG_NPC + ".js"), StandardCharsets.ISO_8859_1);
        String outPortal = Files.readString(
                Path.of("scripts", "portal", "babyPigOut.js"), StandardCharsets.ISO_8859_1);

        assertTrue(mapScript.contains("forceStartQuest(" + marker + ")"),
                "babyPigMap.js no longer force-starts the marker quest " + marker);
        assertTrue(pigScript.contains("isQuestStarted(" + marker + ")")
                        && pigScript.contains("gainItem(" + PIGLET),
                "scripts/npc/" + BABY_PIG_NPC + ".js no longer gates on " + marker + " / grants " + PIGLET);
        assertTrue(outPortal.contains("isQuestCompleted(" + marker + ")"),
                "babyPigOut.js no longer checks " + marker + ", so the player cannot leave with the pig");
    }

    /**
     * The female-Evan client crash. {@code Effect/Direction4.img/PromiseDragon} carries ONLY
     * {@code Scene0} - it is a gender-neutral "word" effect - while its three siblings each carry
     * {@code Scene0} and {@code Scene1}. {@code PromiseDragon.js} used to append
     * {@code getPlayer().getGender()}, so a female Evan got {@code .../Scene1}, a path that does not
     * exist, and {@code showIntro} on a missing path crashes the client. Map 900090101 has no portal,
     * only {@code sp}, so that scene's {@code type=2} warp is the map's only exit.
     */
    @Test
    void promiseDragonSceneIsGenderNeutralAndTheScriptNoLongerAppendsGender() throws IOException {
        Path direction4 = Path.of(WZFiles.DIRECTORY, "Effect.wz", "Direction4.img.xml");
        assertTrue(Files.isRegularFile(direction4), "no Effect.wz/Direction4.img.xml");
        String body = Files.readString(direction4, StandardCharsets.ISO_8859_1);

        String promiseNode = topLevelNode(body, "PromiseDragon");
        assertTrue(promiseNode.contains("<imgdir name=\"Scene0\">"), "PromiseDragon lost Scene0");
        assertFalse(promiseNode.contains("<imgdir name=\"Scene1\">"),
                "PromiseDragon now HAS a Scene1 - if that is genuine v84 data the script may append "
                        + "gender again, but until then appending it crashes every female Evan");

        // control: the siblings really do carry both, so the absence above is a property of this node
        for (String sibling : new String[]{"meetWithDragon", "getDragonEgg", "crash"}) {
            assertTrue(topLevelNode(body, sibling).contains("<imgdir name=\"Scene1\">"),
                    "control failed: " + sibling + " should have a gendered Scene1");
        }

        String script = Files.readString(
                Path.of("scripts", "map", "onUserEnter", "PromiseDragon.js"), StandardCharsets.ISO_8859_1);
        assertTrue(script.contains("PromiseDragon/Scene0\""),
                "PromiseDragon.js must showIntro Scene0 unconditionally");
        assertFalse(script.contains("PromiseDragon/Scene\" + "),
                "PromiseDragon.js is appending gender again - Scene1 does not exist, so this is the "
                        + "female-Evan client crash coming back");
    }

    /**
     * <strong>The blocker that actually killed 22005.</strong> The piglet lives on 900020110, and
     * that map has NO inbound portal anywhere in Map.wz - its only route is the client-side type-2
     * scene warp at the end of {@code Effect/Direction4.img/getDragonEgg}. Those scene warps arrive
     * at {@code ChangeMapHandler} with no portal, and it only honours such a request when the source
     * map is whitelisted. Cygnus (9130401) and Aran (9140900) are; Evan's 9000900xx / 9000901xx
     * were not, so every Evan cutscene warp was silently dropped and the player stranded on a map
     * that has no portal at all, only {@code sp}.
     *
     * <p>Reads the real scene targets out of Direction4 and requires each to be named in the
     * handler, so ADDING an Evan scene without whitelisting its target fails here.
     *
     * <p>ponytail: source-text scan rather than driving the handler with a real packet - the
     * predicate is inline in a switch chain, so there is nothing to call. If that branch is ever
     * extracted into a method, assert on the method instead.
     */
    @Test
    void everyEvanCutsceneWarpTargetIsWhitelistedInChangeMapHandler() throws IOException {
        String direction4 = Files.readString(
                Path.of(WZFiles.DIRECTORY, "Effect.wz", "Direction4.img.xml"), StandardCharsets.ISO_8859_1);

        // The Evan story scenes, and the map each one plays on (Map.wz info/onUserEnter).
        Map<String, Integer> sceneToSourceMap = new LinkedHashMap<>();
        sceneToSourceMap.put("meetWithDragon", 900090100);
        sceneToSourceMap.put("PromiseDragon", 900090101);
        sceneToSourceMap.put("crash", 900090102);
        sceneToSourceMap.put("getDragonEgg", 900090103);

        String handler = Files.readString(Path.of("src", "main", "java", "net", "server", "channel",
                "handlers", "ChangeMapHandler.java"), StandardCharsets.ISO_8859_1);

        for (Map.Entry<String, Integer> e : sceneToSourceMap.entrySet()) {
            String node = topLevelNode(direction4, e.getKey());
            Matcher m = Pattern.compile("<int name=\"field\" value=\"(\\d+)\"/>").matcher(node);
            assertTrue(m.find(), "scene " + e.getKey() + " no longer declares a type-2 field warp");
            String target = m.group(1);

            assertTrue(handler.contains(target),
                    "Direction4 scene " + e.getKey() + " (played on map " + e.getValue() + ") warps the "
                            + "client to " + target + ", but ChangeMapHandler does not whitelist that "
                            + "target - the warp is dropped and the player is stranded");

            // and the source map really is in the Evan branch's divi range
            int divi = e.getValue() / 100;
            assertTrue(divi == 9000900 || divi == 9000901,
                    "map " + e.getValue() + " is outside the divi range the Evan branch covers");
        }

        assertTrue(handler.contains("divi == 9000900 || divi == 9000901"),
                "ChangeMapHandler lost the Evan intro branch; every Evan cutscene warp is dropped "
                        + "again and 900020110 becomes unreachable, taking quest 22005 with it");
    }

    /** 900020110 really has no inbound portal, which is why the scene warp above is load-bearing. */
    @Test
    void thePigletMapHasNoInboundPortalSoTheSceneWarpIsTheOnlyRoute() throws IOException {
        Path maps = Path.of(WZFiles.DIRECTORY, "Map.wz", "Map");
        assertTrue(Files.isDirectory(maps), "no Map.wz/Map under '" + WZFiles.DIRECTORY + "'");
        try (Stream<Path> walk = Files.walk(maps)) {
            for (Path p : walk.filter(Files::isRegularFile).toList()) {
                if (p.getFileName().toString().startsWith("900020110")) {
                    continue;   // the map's own header names itself
                }
                String body = Files.readString(p, StandardCharsets.ISO_8859_1);
                assertFalse(body.contains("<int name=\"tm\" value=\"900020110\"/>"),
                        p.getFileName() + " now has a portal to 900020110; if that is genuine v84 "
                                + "data the ChangeMapHandler whitelist may no longer be the only route");
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    /**
     * The body of a TOP-LEVEL {@code <imgdir>} of an .img.xml, sliced off by indentation. Direction4
     * nests a second {@code PromiseDragon} (the sprite) under {@code effect/}, so a plain
     * {@code indexOf} finds the wrong one and reports the scene node as empty.
     */
    private static String topLevelNode(String body, String name) {
        String open = "\n  <imgdir name=\"" + name + "\">";
        int at = body.indexOf(open);
        assertTrue(at >= 0, "no top-level <imgdir name=\"" + name + "\"> in this .img.xml");
        int end = body.indexOf("\n  <imgdir name=\"", at + open.length());
        return body.substring(at, end < 0 ? body.length() : end);
    }

    private static Data questAct(String path) {
        return DataProviderFactory.getDataProvider(WZFiles.QUEST).getData("Act.img").getChildByPath(path);
    }

    /** The item id quest {@code id} hands out on its start node, or -1 if it hands out nothing. */
    private static int actStartItem(int id) {
        Data item = questAct(id + "/0/item/0/id");
        return item == null ? -1 : DataTool.getInt(item, -1);
    }

    /** Non-null only if the complete node hands out (rather than consumes) an item. */
    private static Object positiveGrant(int id) {
        Data items = questAct(id + "/1/item");
        if (items == null) {
            return null;
        }
        for (Data entry : items.getChildren()) {
            if (DataTool.getInt(entry.getChildByPath("count"), 0) > 0) {
                return entry;
            }
        }
        return null;
    }

    private static String stringName(String img, String path) {
        Data d = DataProviderFactory.getDataProvider(WZFiles.STRING).getData(img).getChildByPath(path);
        assertNotNull(d, "String.wz/" + img + " has no node " + path);
        return DataTool.getString(d.getChildByPath("name"), "");
    }

    private static Data mapData(int mapId) {
        DataProvider mapSource = DataProviderFactory.getDataProvider(WZFiles.MAP);
        Data mapData = mapSource.getData("Map/Map" + (mapId / 100000000) + "/"
                + String.format("%09d", mapId) + ".img");
        assertNotNull(mapData, "Map.wz has no image for map " + mapId);
        return mapData;
    }

    private static boolean npcIsInLifeOf(int mapId, int npcId) {
        return lifeOfTypeContains(mapId, "n", npcId);
    }

    private static boolean mobIsInLifeOf(int mapId, int mobId) {
        return lifeOfTypeContains(mapId, "m", mobId);
    }

    /**
     * Map life ids are strings and mobs are zero-padded to 7 chars ({@code "0130100"}) while npcs are
     * not - parsing as int is what makes the two comparable.
     */
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

    /** Guards against the Quest cache being empty, which would make every lookup above vacuous. */
    @Test
    void theFarmChainQuestsActuallyLoad() {
        for (int id : new int[]{22003, 22004, 22005, 22006, 22007}) {
            assertFalse(Quest.getInstance(id).getName().isBlank(), "quest " + id + " did not load");
        }
    }
}
