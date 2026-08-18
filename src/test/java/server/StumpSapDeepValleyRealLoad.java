package server;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import provider.Data;
import provider.DataProviderFactory;
import provider.DataTool;
import provider.wz.WZFiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins changeSet 170: quest 22529 "Helping Beginner Adventurer Christopher" takes 4032460
 * "Refreshing Stump Sap" from all four stumps, not just plain Stump.
 *
 * <pre>
 *   mvnw.cmd test -Dtest=StumpSapDeepValleyRealLoad
 * </pre>
 *
 * <p><strong>This deviates from a literal reading of v84 on purpose</strong>, owner-approved.
 * {@code QuestInfo.img/22529/1} names {@code #o0130100#} - plain Stump, a Perion mob - but
 * {@code QuestInfo.img/22529/0} stages the quest in Deep Valley I/II/III, where zero plain Stumps
 * spawn and only the three variants do, and the quest is level 22 against Stump's level 8. Client
 * WZ never held drop tables, so nothing in the archives can settle it; changeSet 156's single row
 * was itself authored from that same token and is not independent evidence. The point of this
 * class is that a later "cleanup" sweep, seeing the token, does not collapse this back to one row.
 *
 * <p><strong>Not a {@code *Test} class on purpose</strong>, same reason as its siblings
 * ({@link EvanPorkSourceRealLoad}, {@link V84QuestItemSourcesRealLoad}): {@link WZFiles#DIRECTORY}
 * is resolved once per JVM and {@code MobSkillFactoryTest} points {@code wz-path} at a
 * {@code @TempDir} with no {@code Quest.wz}.
 */
class StumpSapDeepValleyRealLoad {

    private static final Path CHANGESET_156 =
            Path.of("src", "main", "resources", "db", "data", "156-evan-chain-drop-data.sql");
    private static final Path CHANGESET_170 =
            Path.of("src", "main", "resources", "db", "data", "170-stump-sap-deep-valley-drop-data.sql");

    /** The three Deep Valley maps the quest stages itself on, and what actually stands in them. */
    private static final int[] DEEP_VALLEY = {106000000, 106000100, 106000200};

    @BeforeAll
    static void muteGraal() {
        System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
        assertTrue(Files.isRegularFile(Path.of(WZFiles.DIRECTORY, "Quest.wz", "Check.img.xml")),
                "wz-path resolved to '" + WZFiles.DIRECTORY + "', which holds no Quest.wz - another "
                        + "test class won the WZFiles.DIRECTORY race, so this says nothing about 22529");
    }

    /** All four droppers carry the item, all four quest-gated to 22529. The whole point. */
    @Test
    void allFourStumpsDropTheSapGatedToQuest22529() throws IOException {
        assertTrue(Files.isRegularFile(CHANGESET_170), "changeSet 170 seed file is missing");
        String sql156 = Files.readString(CHANGESET_156, StandardCharsets.UTF_8);
        String sql170 = Files.readString(CHANGESET_170, StandardCharsets.UTF_8);

        assertTrue(sql156.contains("(130100, 4032460, 1, 1, 22529, 80000)"),
                "changeSet 156's original plain-Stump row is gone - 170 is additive, it does not "
                        + "replace it, and 156 is APPLIED so it must not be edited");
        for (int dropper : new int[]{1130100, 1140100, 2130100}) {
            assertTrue(sql170.contains("(" + dropper + ", 4032460, 1, 1, 22529, 80000)"),
                    "changeSet 170 no longer carries the row for dropper " + dropper + ". If a sweep "
                            + "'corrected' this back to the literal #o0130100# token, read the header "
                            + "of 170 first - the deviation is deliberate and owner-approved");
        }

        String changelog = Files.readString(
                Path.of("src", "main", "resources", "db", "changelog-data.xml"), StandardCharsets.ISO_8859_1);
        assertTrue(changelog.contains("170-stump-sap-deep-valley-drop-data.sql"),
                "170 exists on disk but is not registered in changelog-data.xml, so it never runs");
        assertTrue(changelog.contains("156-evan-chain-drop-data.sql"),
                "the APPLIED changeSet that owns the plain-Stump row vanished from the changelog");
    }

    /**
     * The evidence for the deviation, straight out of the WZ. If any of this stops being true the
     * rows lose their justification and someone should revisit them rather than trust the header.
     */
    @Test
    void theQuestStagesItselfWhereOnlyTheVariantsSpawn() {
        assertEquals("Stump", stringName("Mob.img", "130100"));
        assertEquals("Axe Stump", stringName("Mob.img", "1130100"));
        assertEquals("Ghost Stump", stringName("Mob.img", "1140100"));
        assertEquals("Dark Axe Stump", stringName("Mob.img", "2130100"));
        assertEquals("Refreshing Stump Sap", stringName("Etc.img", "Etc/4032460"));

        assertTrue(questInfo(22529, "0").contains("#m106000000#"),
                "QuestInfo 22529/0 no longer sends the player to Deep Valley, which is the whole "
                        + "basis for putting the sap on the Deep Valley stumps");
        assertTrue(questInfo(22529, "1").contains("#o0130100#"),
                "QuestInfo 22529/1 no longer names plain Stump - the inconsistency changeSet 170 "
                        + "resolves has changed shape, so re-read it");

        int variants = 0;
        for (int map : DEEP_VALLEY) {
            assertEquals(0, lifeCount(map, "m", 130100),
                    "map " + map + " now spawns plain Stumps, which removes the reason changeSet 170 "
                            + "exists - the quest could then be done where it sends you with the 156 row alone");
            variants += lifeCount(map, "m", 1130100) + lifeCount(map, "m", 1140100)
                    + lifeCount(map, "m", 2130100);
        }
        assertEquals(109, variants,
                "the Deep Valley maps no longer carry their 109 stump variants (32+10, 20+22, 12+13)");

        assertEquals(22, DataTool.getInt(questCheck("22529/0/lvmin"), -1),
                "quest 22529 is no longer level 22, so the level-band half of the argument moved");
        assertEquals(4, DataTool.getInt(mobInfo(130100, "level"), -1),
                "plain Stump is no longer level 4");
        assertEquals(17, DataTool.getInt(mobInfo(1130100, "level"), -1));
        assertEquals(19, DataTool.getInt(mobInfo(1140100, "level"), -1));
        assertEquals(22, DataTool.getInt(mobInfo(2130100, "level"), -1),
                "Dark Axe Stump is no longer level 22, the quest's own lvmin - that exact match is "
                        + "the strongest single piece of evidence changeSet 170 rests on");
    }

    /**
     * The questid column only gates anything for items Item.wz flags {@code quest=1} -
     * {@code MapleMap.sortDropEntries} consults {@code needQuestItem} for those only. Without the
     * flag all four rows would leak sap into the world as ordinary loot.
     */
    @Test
    void theSapIsFlaggedAQuestItemSoTheQuestidGateApplies() {
        Data info = DataProviderFactory.getDataProvider(WZFiles.ITEM)
                .getData("Etc/0403.img").getChildByPath("04032460/info");
        assertNotNull(info, "Item.wz has no info node for 4032460");
        assertEquals(1, DataTool.getInt(info.getChildByPath("quest"), 0),
                "4032460 is no longer quest=1, so the questid gate on all four rows is inert and "
                        + "Refreshing Stump Sap drops for every player killing stumps");
    }

    // ---------------------------------------------------------------- helpers

    private static Data questCheck(String path) {
        return DataProviderFactory.getDataProvider(WZFiles.QUEST).getData("Check.img").getChildByPath(path);
    }

    private static String questInfo(int id, String node) {
        return DataTool.getString(DataProviderFactory.getDataProvider(WZFiles.QUEST)
                .getData("QuestInfo.img").getChildByPath(id + "/" + node), "");
    }

    private static Data mobInfo(int mobId, String field) {
        return DataProviderFactory.getDataProvider(WZFiles.MOB)
                .getData(String.format("%07d", mobId) + ".img").getChildByPath("info/" + field);
    }

    private static String stringName(String img, String path) {
        Data d = DataProviderFactory.getDataProvider(WZFiles.STRING).getData(img).getChildByPath(path);
        assertNotNull(d, "String.wz/" + img + " has no node " + path);
        return DataTool.getString(d.getChildByPath("name"), "");
    }

    private static int lifeCount(int mapId, String type, int id) {
        Data map = DataProviderFactory.getDataProvider(WZFiles.MAP).getData("Map/Map"
                + (mapId / 100000000) + "/" + String.format("%09d", mapId) + ".img");
        assertNotNull(map, "Map.wz has no image for map " + mapId);
        Data life = map.getChildByPath("life");
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
}
