package server;

import constants.id.MobId;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the real GMS v84 source of every Evan quest item that had none, and asserts each source
 * actually exists <em>and is reachable</em> in this tree. Sibling of
 * {@link EvanFarmChainSourcesRealLoad}, which did the same job for the three farm-chain items;
 * this one covers the eight quests changeSet 156 unblocks plus the two that are NOT drops.
 *
 * <pre>
 *   mvnw.cmd test -Dtest=EvanQuestSourcesRealLoad
 * </pre>
 *
 * <p><strong>Not a {@code *Test} class on purpose</strong>, same reason as its sibling:
 * {@link WZFiles#DIRECTORY} is a {@code static final} resolved once per JVM and
 * {@code MobSkillFactoryTest} points {@code wz-path} at a {@code @TempDir} with no {@code Quest.wz}.
 *
 * <p>The one thing the ticket-42 parity tool could not see, and the reason two of these were
 * mis-classified: a {@code drop_data} row carries a {@code questid}, and
 * {@code Character.needQuestItem} refuses the pickup for every quest but that one. "A row exists"
 * and "this quest can be finished" are different questions.
 *
 * <pre>
 *   22407 4032475 Lycanthrope Leather   mob 8140000 / 9500134   rows existed, gated to 28344
 *   22407 4032476 Captain Alpha's Buckle reactor 2302001        REACTOR drop, not a mob drop
 *   22410 4032504 / 4032505             same two sources, "the same materials as before"
 *   22524 4032459 Blue Mushroom Doll    mob 2220100 + 2220110   also needs a MobId merge-id fix
 *   22529 4032460 Refreshing Stump Sap  mob 130100 "Stump"      + npc 1022106 was unplaced
 *   22531 4032461 Zombie Mushroom Doll  mob 2230131
 *   22532 4032462 Wild Boar Doll        mob 2230112             also needed the spawn itself
 *   22548 4032463 Document with Clue    mob 3110100 "Ligator"
 *   22559 4032466 Golem Doll            mob 9300387 "Enraged Golem"
 *   22408 4032497 "Potter"              NPC 2092101 - a script, never a drop
 *   28351 4000566-71                    item 2022662's reward box; quest EXPIRED 2010-05-05
 * </pre>
 */
class EvanQuestSourcesRealLoad {

    /** Quests whose required item changeSet 156 sources. */
    private static final int[] SOURCED_QUESTS = {22407, 22410, 22524, 22529, 22531, 22532, 22548, 22559};

    private static final Path CHANGESET_156 =
            Path.of("src", "main", "resources", "db", "data", "156-evan-chain-drop-data.sql");

    @BeforeAll
    static void muteGraal() {
        System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
        assertTrue(Files.isRegularFile(Path.of(WZFiles.DIRECTORY, "Quest.wz", "Check.img.xml")),
                "wz-path resolved to '" + WZFiles.DIRECTORY + "', which holds no Quest.wz - another "
                        + "test class won the WZFiles.DIRECTORY race, so this says nothing about Evan");
    }

    /**
     * The load-bearing distinction, exactly as in the farm chain: a quest whose {@code Act.img/<id>/0}
     * start node is empty hands the player nothing, so its completion item must come from the world.
     * Every quest changeSet 156 touches is of that shape. If someone ever "fixes" one by adding an Act
     * grant instead, that is not v84 and this is where it gets caught.
     */
    @Test
    void noneOfTheseQuestsGrantsItsOwnCompletionItem() {
        for (int id : SOURCED_QUESTS) {
            assertNull(questAct(id + "/0/item"),
                    "quest " + id + " start Act.img now grants an item; in v84 it granted nothing and "
                            + "the player had to obtain it in the world - changeSet 156 would be wrong");
        }
        assertNull(questAct("22408/0/item"), "22408 start Act.img must still grant nothing");
    }

    /**
     * Every mob changeSet 156 hangs a drop on must (a) exist in Mob.wz and (b) be spawned on a map.
     * A drop row on a mob that spawns nowhere fixes nothing - that is the trap this whole ticket
     * exists to avoid.
     */
    @Test
    void everyMobDropperExistsAndIsSpawnedSomewhere() {
        assertMobSpawned(2220100, 106010000, "Blue Mushroom");
        assertMobSpawned(130100, 101030000, "Stump");
        assertMobSpawned(2230131, 105050300, "Annoyed Zombie Mushroom");
        assertMobSpawned(2230112, 101030001, "Terrified Wild Boar");
        assertMobSpawned(3110100, 107000000, "Ligator");
        assertMobSpawned(9300387, 910600010, "Enraged Golem");
        assertMobSpawned(8140000, 211040800, "Lycanthrope");
    }

    /**
     * 22532 was blocked twice over: no drop AND no spawn. v84 places 24 Terrified Wild Boars on
     * 101030001 "The Land of Wild Boar II"; this tree had none.
     *
     * <p><strong>This started as an append and is now a replacement.</strong> ce3895453 could only
     * add, so the map briefly carried 57 spawns - v84's 26 plus 31 held over from v83 - and this
     * test asserted that deviation. The owner then authorised the deletion explicitly ("do the map
     * replacment"), so f4bccbc0f replaced the whole life array with v84 stock and the expectations
     * below were inverted to match. The additive-only rule still governs everything else; map life
     * arrays are the one carve-out, and only because exact v84 parity is impossible without it.
     */
    @Test
    void theTerrifiedWildBoarSpawnMatchesV84Exactly() {
        assertEquals(24, lifeCount(101030001, "m", 2230112),
                "101030001 must carry v84's 24 Terrified Wild Boars");
        assertEquals(1, lifeCount(101030001, "m", 2230102),
                "v84 keeps exactly ONE Wild Boar here; the 30 v83 ones were removed by the "
                        + "owner-authorised replacement, so seeing 30 again means it regressed");
        assertEquals(1, lifeCount(101030001, "m", 2130100),
                "v84 keeps exactly ONE Fire Boar here");
    }

    /**
     * A life entry's {@code fh} is an INDEX into this map's own foothold table, and v84 renumbers
     * footholds on some maps - {@code 251000000} is one, where v84's fh 187 points at a platform
     * 1700px away in this tree. Every entry this ticket added therefore has to be checked against
     * THIS tree's footholds, not copied blind. This asserts the outcome for all of them.
     */
    @Test
    void everyPlacedLifeEntrySitsOnAFootholdThatExistsHere() {
        assertLifeOnValidFoothold(101030001, "m", 2230112);
        assertLifeOnValidFoothold(251000000, "n", 2092100);
        assertLifeOnValidFoothold(106000000, "n", 1022106);
        assertLifeOnValidFoothold(106000100, "n", 1022106);
        assertLifeOnValidFoothold(106000200, "n", 1022106);
        assertLifeOnValidFoothold(100000100, "n", 1011101);
        assertLifeOnValidFoothold(211040400, "n", 2030015);
        assertLifeOnValidFoothold(200010000, "n", 9010012);
        assertLifeOnValidFoothold(240010200, "n", 9010013);
        for (int map : new int[]{101030000, 101030100, 101030200, 101030300, 101030400}) {
            assertLifeOnValidFoothold(map, "n", 1022107);
        }
    }

    /**
     * 22529's start NPC was placed on no map at all, so the quest could not even be taken. v84 puts
     * Christopher on the three Warning Street maps the quest text names.
     */
    @Test
    void christopherIsPlacedOnAllThreeWarningStreetMaps() {
        for (int map : new int[]{106000000, 106000100, 106000200}) {
            assertEquals(1, lifeCount(map, "n", 1022106),
                    "npc 1022106 Christopher is not on map " + map + ", so quest 22529 cannot start");
        }
        String objective = questInfo(22529, "1");
        assertTrue(objective.contains("#o0130100#"),
                "QuestInfo 22529 no longer names mob 130100 as the source of 4032460; it read: " + objective);
    }

    /**
     * 22408 is a rescue, not a drop: the item is the man. NPC 2092101 was already placed and already
     * reachable (ticket 08's {@code enterPottery} portal), he simply had no script.
     */
    @Test
    void potterIsPlacedReachableAndScriptedRatherThanBeingADrop() throws IOException {
        assertEquals("Potter", stringName("Npc.img", "2092101"));
        assertEquals(1, lifeCount(925110000, "n", 2092101),
                "npc 2092101 is not on 925110000 'Pirate Treasure Vault', so 22408 has no source");

        Data pottery = portalScriptOf(251010403);
        assertNotNull(pottery, "map 251010403 has no scripted portal at all");
        assertEquals("enterPottery", DataTool.getString(pottery, ""),
                "map 251010403 lost the v84 portal whose script warps to the Pirate Treasure Vault");
        assertTrue(Files.readString(Path.of("scripts", "portal", "enterPottery.js"),
                        StandardCharsets.ISO_8859_1).contains("925110000"),
                "enterPottery.js no longer warps to 925110000");

        String potter = Files.readString(Path.of("scripts", "npc", "2092101.js"), StandardCharsets.ISO_8859_1);
        assertTrue(potter.contains("isQuestStarted(22408)") && potter.contains("gainItem(4032497"),
                "scripts/npc/2092101.js no longer grants 4032497 during quest 22408");

        // and the freed Potter, npc 2092100, must be in Herb Town for the follow-up 22409
        assertEquals(1, lifeCount(251000000, "n", 2092100),
                "npc 2092100 Potter is not in Herb Town, so quest 22409 cannot start");
        assertEquals(2092100, DataTool.getInt(questCheck("22409/0/npc"), -1),
                "22409 no longer starts at npc 2092100");
    }

    /**
     * The buckle is a REACTOR drop and the reactor names itself: 2302001's own info string is the
     * Korean for "deep sea treasure chest", and it is the one placed on "The Grave of a Wrecked Ship".
     * Its two siblings are the mid-water chest and a scallop, so this is identification, not
     * elimination.
     */
    @Test
    void theBuckleReactorIsTheDeepSeaTreasureChestOnTheWreckedShipMap() throws IOException {
        String info = DataTool.getString(DataProviderFactory.getDataProvider(WZFiles.REACTOR)
                .getData("2302001.img").getChildByPath("info/info"), "");
        assertTrue(info.startsWith("심해보물상자"),
                "reactor 2302001 no longer calls itself a deep-sea treasure chest; it read: " + info);

        assertEquals("The Grave of a Wrecked Ship", mapNameString(230040400));
        assertTrue(reactorCount(230040400, 2302001) > 0,
                "reactor 2302001 is not placed on 230040400, so the buckle has no source");

        assertTrue(Files.readString(Path.of("scripts", "reactor", "2302001.js"),
                        StandardCharsets.ISO_8859_1).contains("rm.dropItems("),
                "scripts/reactor/2302001.js no longer calls dropItems, so reactordrops rows are inert");
    }

    /**
     * The changeSet itself: every row, verbatim, plus registration. A row that drifts from the
     * evidence recorded beside it is exactly the silent corruption this ticket is guarding against.
     */
    @Test
    void changeSet156CarriesEveryRowAndIsRegistered() throws IOException {
        assertTrue(Files.isRegularFile(CHANGESET_156), "changeSet 156 seed file is missing");
        String sql = Files.readString(CHANGESET_156, StandardCharsets.UTF_8);

        for (String row : new String[]{
                "(2220100, 4032459, 1, 1, 22524, 40000)",
                "(2220110, 4032459, 1, 1, 22524, 40000)",
                "(130100, 4032460, 1, 1, 22529, 80000)",
                "(2230131, 4032461, 1, 1, 22531, 20000)",
                "(2230112, 4032462, 1, 1, 22532, 20000)",
                "(3110100, 4032463, 1, 1, 22548, 300000)",
                "(9300387, 4032466, 1, 1, 22559, 999999)",
                "(8140000, 4032475, 1, 1, 22407, 200000)",
                "(9500134, 4032475, 1, 1, 22407, 200000)",
                "(8140000, 4032504, 1, 1, 22410, 200000)",
                "(9500134, 4032504, 1, 1, 22410, 200000)",
                "(2302001, 4032476, 5, 22407)",
                "(2302001, 4032505, 5, 22410)"}) {
            assertTrue(sql.contains(row), "changeSet 156 no longer carries the row " + row);
        }

        String changelog = Files.readString(
                Path.of("src", "main", "resources", "db", "changelog-data.xml"), StandardCharsets.ISO_8859_1);
        assertTrue(changelog.contains("156-evan-chain-drop-data.sql"),
                "156 exists on disk but is not registered in changelog-data.xml, so it never runs");
        assertTrue(changelog.contains("DELETE FROM reactordrops WHERE reactorid = 2302001"),
                "changeSet 156 lost its reactordrops rollback");

        // 155 must not have been edited - it is APPLIED, so any change fails checksum validation
        assertTrue(changelog.contains("155-evan-tutorial-drop-data.sql"),
                "changeSet 155 vanished from the changelog");
    }

    /**
     * <strong>Closed.</strong> 22524 counts kills of mob 9101004, which has no Mob.wz image in either
     * tree - it is one of Nexon's "merge" ids that stands for two real mobs at once
     * ({@code Mob.wz/QuestCountGroup/9101004.img} = 2220100 + 2220110, both spawned on 106010000 /
     * 106010100). {@code Character.raiseQuestMobCount} carries the branch, so 9101004 must never be
     * placed as map life.
     */
    @Test
    void quest22524CountsAMergeIdThatHasNoMobImage() {
        assertEquals(9101004, MobId.BLUE_MUSHROOM_QUEST, "the merge id 22524 counts");
        assertEquals(2220100, MobId.BLUE_MUSHROOM);
        assertEquals(2220110, MobId.CRYING_BLUE_MUSHROOM);
        assertEquals(9101004, DataTool.getInt(questCheck("22524/1/mob/0/id"), -1),
                "22524 no longer counts mob 9101004");
        assertEquals(50, DataTool.getInt(questCheck("22524/1/mob/0/count"), -1),
                "v84 asks 100; this tree asks 50 under the owner's halving - QuestRequirementHalvingRealLoad");

        assertEquals("Blue Mushroom", stringName("Mob.img", "9101004"));
        assertFalse(Files.isRegularFile(Path.of(WZFiles.DIRECTORY, "Mob.wz", "9101004.img.xml")),
                "Mob.wz now HAS a 9101004 image - if that is genuine data, 9101004 can be spawned "
                        + "directly and the MobId merge-id route below is no longer the only fix");

        // the three merge ids the server already handles - the template for 9101004
        assertEquals("Green Mushroom", stringName("Mob.img", "9101000"));
        assertEquals("Zombie Mushroom", stringName("Mob.img", "9101001"));
        assertEquals("Ghost Stump", stringName("Mob.img", "9101002"));
    }

    /**
     * 28351's items are NOT a drop and must never be given one. The quest expired on 2010-05-05 and
     * its six symbols come out of item 2022662 "Evan's Paper Box", whose {@code reward} node already
     * lists them and which {@code ItemRewardHandler} already implements. The box itself was a GMS
     * server-side launch giveaway with no client-side record - that is the gap, and inventing a drop
     * source for it would be inventing content.
     */
    @Test
    void the28351SymbolsComeFromAnExpiredLaunchEventBoxAndNotFromDrops() throws IOException {
        assertEquals("201005050000", DataTool.getString(questCheck("28351/0/end"), ""),
                "28351 no longer carries its GMS expiry date");

        Data reward = DataProviderFactory.getDataProvider(WZFiles.ITEM)
                .getData("Consume/0202.img").getChildByPath("02022662/reward");
        assertNotNull(reward, "item 2022662 lost its reward node - 28351's symbols now have no source "
                + "at all, and the temptation to invent one gets worse");
        Set<Integer> inBox = new HashSet<>();
        for (Data entry : reward.getChildren()) {
            inBox.add(DataTool.getInt(entry.getChildByPath("item"), -1));
        }
        for (int id = 4000566; id <= 4000571; id++) {
            assertTrue(inBox.contains(id), "2022662's reward box no longer contains " + id);
        }

        String sql = Files.readString(CHANGESET_156, StandardCharsets.UTF_8);
        for (int id = 4000566; id <= 4000571; id++) {
            assertFalse(sql.contains(", " + id + ","),
                    "changeSet 156 has grown a drop row for " + id + " - these come out of a box, and a "
                            + "fabricated drop source is the exact failure this ticket forbids");
        }
    }

    /**
     * The deny-list gap ticket 42 named: the PlayerNPC band is 9900000-9906599 plus 9977777
     * ({@code NpcId.PLAYER_NPC_BASE} + {@code PlayerNPC.fetchAvailableScriptIdsFromDb}), but the deny
     * rows only covered 9901910-9901919. Everything the pinned v84 source offers below that floor is
     * now covered. Asserted mechanically off the add-list so a re-pin cannot silently reopen it.
     */
    @Test
    void everyReservedPlayerNpcRowTheAddListOffersIsDeniedOrStructurallyRefused() throws IOException {
        Path denyPath = Path.of("docs", "wz-baseline", "merge-lists", "COLLISION-DENY.txt");
        assertTrue(Files.isRegularFile(denyPath), "COLLISION-DENY.txt is missing");
        List<String> denyRoots = new ArrayList<>();
        for (String line : Files.readAllLines(denyPath, StandardCharsets.UTF_8)) {
            String s = line.strip();
            if (s.isEmpty() || s.startsWith("#")) {
                continue;
            }
            denyRoots.add(s.split("\t")[0].strip());
        }
        assertTrue(denyRoots.contains("Npc.wz/9900000.img"));
        assertTrue(denyRoots.contains("Npc.wz/9900001.img"));
        assertTrue(denyRoots.contains("Npc.wz/9901000.img"));
        assertTrue(denyRoots.contains("String.wz/Npc.img/9901000"));

        // and PlayerNPC.java still declares the range these roots were derived from
        String npcId = Files.readString(Path.of("src", "main", "java", "constants", "id", "NpcId.java"),
                StandardCharsets.ISO_8859_1);
        assertTrue(npcId.contains("PLAYER_NPC_BASE = 9900000"),
                "NpcId.PLAYER_NPC_BASE moved; the deny band 9900000-9906599 has to be re-derived");

        Path addList = Path.of("docs", "wz-baseline", "add-list");
        if (!Files.isDirectory(addList)) {
            return;     // add-list is a doc artefact, not a build input
        }
        List<String> uncovered = new ArrayList<>();
        List<Path> addFiles;
        try (var s = Files.list(addList)) {
            addFiles = s.toList();
        }
        for (Path f : addFiles) {
            for (String row : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                String s = row.strip();
                if (s.isEmpty() || s.startsWith("#") || !inPlayerNpcBand(s)) {
                    continue;
                }
                boolean covered = denyRoots.stream().anyMatch(
                        r -> s.equals(r) || s.startsWith(r + "/"));
                if (!covered && !alreadyInThisTree(s)) {
                    uncovered.add(f.getFileName() + ": " + s);
                }
            }
        }
        assertTrue(uncovered.isEmpty(),
                "these v84 rows land inside the PlayerNPC allocator band 9900000-9906599 and are "
                        + "neither deny-listed nor refused by the additive gate: " + uncovered);
    }

    /** Guards against the Quest cache being empty, which would make every lookup above vacuous. */
    @Test
    void theEvanQuestsActuallyLoad() {
        for (int id : SOURCED_QUESTS) {
            assertFalse(Quest.getInstance(id).getName().isBlank(), "quest " + id + " did not load");
        }
        assertFalse(Quest.getInstance(22408).getName().isBlank(), "quest 22408 did not load");
    }

    // ---------------------------------------------------------------- helpers

    /**
     * True when the add-list row names a node this tree ALREADY has, in which case WzMerge's
     * additive-only gate refuses it on its own and no deny row is needed. Two shapes occur in the
     * band: a whole {@code <id>.img} image, and a {@code String.wz/<img>/<id>} node.
     */
    private static boolean alreadyInThisTree(String row) {
        String[] seg = row.split("/");
        if (seg.length == 2 && seg[1].endsWith(".img")) {
            return Files.isRegularFile(Path.of(WZFiles.DIRECTORY, seg[0], seg[1] + ".xml"));
        }
        if (seg.length == 3 && seg[0].equals("String.wz") && seg[1].endsWith(".img")) {
            return DataProviderFactory.getDataProvider(WZFiles.STRING)
                    .getData(seg[1]).getChildByPath(seg[2]) != null;
        }
        return false;
    }

    /** True when the last path segment (or its id-shaped prefix) is inside 9900000-9906599 / 9977777. */
    private static boolean inPlayerNpcBand(String row) {
        for (String seg : row.split("/")) {
            String digits = seg.endsWith(".img") ? seg.substring(0, seg.length() - 4) : seg;
            if (digits.length() == 7 && digits.chars().allMatch(Character::isDigit)) {
                int id = Integer.parseInt(digits);
                if ((id >= 9900000 && id <= 9906599) || id == 9977777) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Data questAct(String path) {
        return DataProviderFactory.getDataProvider(WZFiles.QUEST).getData("Act.img").getChildByPath(path);
    }

    private static Data questCheck(String path) {
        return DataProviderFactory.getDataProvider(WZFiles.QUEST).getData("Check.img").getChildByPath(path);
    }

    private static String questInfo(int id, String node) {
        return DataTool.getString(DataProviderFactory.getDataProvider(WZFiles.QUEST)
                .getData("QuestInfo.img").getChildByPath(id + "/" + node), "");
    }

    private static String stringName(String img, String path) {
        Data d = DataProviderFactory.getDataProvider(WZFiles.STRING).getData(img).getChildByPath(path);
        assertNotNull(d, "String.wz/" + img + " has no node " + path);
        return DataTool.getString(d.getChildByPath("name"), "");
    }

    /** String.wz/Map.img groups maps by region ("maple", "victoria", "ossyria", ...), so walk them. */
    private static String mapNameString(int mapId) {
        Data root = DataProviderFactory.getDataProvider(WZFiles.STRING).getData("Map.img");
        for (Data region : root.getChildren()) {
            Data entry = region.getChildByPath(String.valueOf(mapId));
            if (entry != null) {
                return DataTool.getString(entry.getChildByPath("mapName"), "");
            }
        }
        return "";
    }

    private static Data mapData(int mapId) {
        DataProvider mapSource = DataProviderFactory.getDataProvider(WZFiles.MAP);
        Data mapData = mapSource.getData("Map/Map" + (mapId / 100000000) + "/"
                + String.format("%09d", mapId) + ".img");
        assertNotNull(mapData, "Map.wz has no image for map " + mapId);
        return mapData;
    }

    private static int lifeCount(int mapId, String type, int id) {
        Data life = mapData(mapId).getChildByPath("life");
        if (life == null) {
            return 0;
        }
        int n = 0;
        for (Data entry : life.getChildren()) {
            if (type.equals(DataTool.getString(entry.getChildByPath("type"), ""))
                    && Integer.parseInt(DataTool.getString(entry.getChildByPath("id")).trim()) == id) {
                n++;
            }
        }
        return n;
    }

    private static void assertMobSpawned(int mobId, int mapId, String expectedName) {
        assertEquals(expectedName, stringName("Mob.img", String.valueOf(mobId)));
        // Mob.wz image names are zero-padded to 7 chars: 130100 -> 0130100.img
        assertTrue(Files.isRegularFile(Path.of(WZFiles.DIRECTORY, "Mob.wz",
                        String.format("%07d", mobId) + ".img.xml")),
                "Mob.wz has no image for " + mobId);
        assertTrue(lifeCount(mapId, "m", mobId) > 0,
                "mob " + mobId + " (" + expectedName + ") is not spawned on map " + mapId + ", so a "
                        + "drop row on it fixes nothing");
    }

    /** Every {@code fh} on the named life entries must name a foothold that exists on that same map. */
    private static void assertLifeOnValidFoothold(int mapId, String type, int id) {
        Data map = mapData(mapId);
        Set<String> footholds = new HashSet<>();
        Data fhRoot = map.getChildByPath("foothold");
        assertNotNull(fhRoot, "map " + mapId + " has no foothold table");
        for (Data layer : fhRoot.getChildren()) {
            for (Data group : layer.getChildren()) {
                for (Data fh : group.getChildren()) {
                    footholds.add(fh.getName());
                }
            }
        }
        int seen = 0;
        for (Data entry : map.getChildByPath("life").getChildren()) {
            if (!type.equals(DataTool.getString(entry.getChildByPath("type"), ""))
                    || Integer.parseInt(DataTool.getString(entry.getChildByPath("id")).trim()) != id) {
                continue;
            }
            seen++;
            String fh = String.valueOf(DataTool.getInt(entry.getChildByPath("fh"), -1));
            assertTrue(footholds.contains(fh),
                    "life entry " + entry.getName() + " of " + id + " on map " + mapId + " names "
                            + "foothold " + fh + ", which does not exist on that map here - v84 "
                            + "renumbers footholds, so the index has to be re-authored per tree");
        }
        assertTrue(seen > 0, "no life entry for " + id + " on map " + mapId);
    }

    private static int reactorCount(int mapId, int reactorId) {
        Data reactors = mapData(mapId).getChildByPath("reactor");
        if (reactors == null) {
            return 0;
        }
        int n = 0;
        for (Data entry : reactors.getChildren()) {
            if (Integer.parseInt(DataTool.getString(entry.getChildByPath("id")).trim()) == reactorId) {
                n++;
            }
        }
        return n;
    }

    /** The {@code script} node of the one scripted portal on {@code mapId}, or null. */
    private static Data portalScriptOf(int mapId) {
        Data portals = mapData(mapId).getChildByPath("portal");
        assertNotNull(portals, "map " + mapId + " has no portal table");
        for (Data p : portals.getChildren()) {
            if (p.getChildByPath("script") != null) {
                return p.getChildByPath("script");
            }
        }
        return null;
    }
}
