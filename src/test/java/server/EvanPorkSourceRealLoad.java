package server;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import provider.Data;
import provider.DataProviderFactory;
import provider.DataTool;
import provider.wz.WZFiles;
import server.quest.Quest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins changeSet 159: quest 22503 "A Bite of Pork" needs 4032453 x10 and nothing on this server
 * produced it. Sibling of {@link EvanQuestSourcesRealLoad}; same method, same discipline.
 *
 * <pre>
 *   mvnw.cmd test -Dtest=EvanPorkSourceRealLoad
 * </pre>
 *
 * <p>This was the worst instance of the class of bug those tickets chase, because 22503 sits on the
 * trunk: Check.img/22504/0 requires it at state 2, so the single missing item left 107 of the 135
 * Evan quests unstartable from level 11 on.
 *
 * <p><strong>Not a {@code *Test} class on purpose</strong>, same reason as its siblings:
 * {@link WZFiles#DIRECTORY} is a {@code static final} resolved once per JVM and
 * {@code MobSkillFactoryTest} points {@code wz-path} at a {@code @TempDir} with no {@code Quest.wz}.
 */
class EvanPorkSourceRealLoad {

    private static final Path CHANGESET_159 =
            Path.of("src", "main", "resources", "db", "data", "159-evan-pork-drop-data.sql");

    @BeforeAll
    static void muteGraal() {
        System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
        assertTrue(Files.isRegularFile(Path.of(WZFiles.DIRECTORY, "Quest.wz", "Check.img.xml")),
                "wz-path resolved to '" + WZFiles.DIRECTORY + "', which holds no Quest.wz - another "
                        + "test class won the WZFiles.DIRECTORY race, so this says nothing about Evan");
    }

    /** The requirement the whole chain hangs on, and the gate that propagates it to 107 quests. */
    @Test
    void quest22503Wants10PorkAnd22504IsGatedBehindIt() {
        assertEquals(4032453, DataTool.getInt(questCheck("22503/1/item/0/id"), -1));
        assertEquals(10, DataTool.getInt(questCheck("22503/1/item/0/count"), -1));

        assertEquals(22503, DataTool.getInt(questCheck("22504/0/quest/0/id"), -1),
                "22504 no longer requires 22503, so the 107-quest blast radius has changed");
        assertEquals(2, DataTool.getInt(questCheck("22504/0/quest/0/state"), -1),
                "22504 now accepts 22503 at a state other than 2 (completed)");
    }

    /**
     * The load-bearing distinction: {@code Act.img/22503/0} is empty, so the quest hands the player
     * nothing and 4032453 has to come from the world. Its completion node only ever CONSUMES the
     * pork (count -10), which is the same statement from the other side. If someone ever "fixes"
     * this with an Act grant instead of the drop, that is not v84 and this is where it gets caught.
     */
    @Test
    void quest22503GrantsNothingSoTheItemMustComeFromTheWorld() {
        assertNull(questAct("22503/0/item"),
                "quest 22503 start Act.img now grants an item; in v84 it granted nothing");
        assertEquals(-10, DataTool.getInt(questAct("22503/1/item/0/count"), 0),
                "quest 22503's completion node no longer CONSUMES 10 Pork; a non-negative count "
                        + "here would mean the quest hands the item out and changeSet 159 is wrong");
        assertEquals(22504, DataTool.getInt(questAct("22503/1/nextQuest"), -1),
                "22503 no longer chains into 22504");
    }

    /** The dropper is named by the quest's own text, and it spawns where that text sends you. */
    @Test
    void thePigIsNamedByTheQuestAndSpawnsOnTheFarmMap() {
        assertEquals("Pig", stringName("Mob.img", "1210100"));
        assertEquals("Pork", stringName("Etc.img", "Etc/4032453"));    // String.wz/Etc.img nests under "Etc"

        String objective = questInfo(22503, "1");
        assertTrue(objective.contains("#o1210100#"),
                "QuestInfo 22503 no longer names mob 1210100 as the source of 4032453; it read: " + objective);

        assertTrue(Files.isRegularFile(Path.of(WZFiles.DIRECTORY, "Mob.wz", "1210100.img.xml")),
                "Mob.wz has no image for 1210100");
        assertEquals("Large Forest Trail", mapNameString(100030310));
        assertEquals(20, lifeCount(100030310, "m", 1210100),
                "the farm map the quest points at no longer carries its 20 Pigs, so ~25 kills for "
                        + "10 Pork is no longer the real cost of this quest");
    }

    /**
     * The gate that keeps a quest drop out of the economy only fires for items Item.wz marks
     * {@code quest=1} - {@code MapleMap.sortDropEntries} consults {@code needQuestItem} for those
     * only. Without this flag the row's questid would be inert and Pork would become world loot.
     */
    @Test
    void porkIsFlaggedAQuestItemSoTheQuestidGateActuallyApplies() {
        Data info = DataProviderFactory.getDataProvider(WZFiles.ITEM)
                .getData("Etc/0403.img").getChildByPath("04032453/info");
        assertNotNull(info, "Item.wz has no info node for 4032453");
        assertEquals(1, DataTool.getInt(info.getChildByPath("quest"), 0),
                "4032453 is no longer quest=1, so changeSet 159's questid gate is inert and Pork "
                        + "would leak into the world as ordinary loot");
    }

    /**
     * The row, verbatim, plus registration. The rate is not free-floating: 200000 is copied from
     * {@code (1210100, 4032340, 1, 1, 21710, 200000)}, the same mob's own bulk quest row. Both ends
     * of that copy are asserted, so the row cannot drift from the evidence recorded beside it.
     */
    @Test
    void changeSet159CarriesTheRowAndItsPrecedentStillHolds() throws IOException {
        assertTrue(Files.isRegularFile(CHANGESET_159), "changeSet 159 seed file is missing");
        String sql = Files.readString(CHANGESET_159, StandardCharsets.UTF_8);
        assertTrue(sql.contains("(1210100, 4032453, 1, 1, 22503, 200000)"),
                "changeSet 159 no longer carries its one row");
        assertTrue(sql.contains("(1210100, 4032340, 1, 1, 21710, 200000)"),
                "changeSet 159 no longer records the row its rate was copied from");

        // the precedent must still be the shape the comment claims: same mob, a BULK fetch
        assertEquals(4032340, DataTool.getInt(questCheck("21710/1/item/0/id"), -1));
        assertEquals(25, DataTool.getInt(questCheck("21710/1/item/0/count"), -1),
                "quest 21710 is no longer a 25-item fetch, so it is no longer the bulk comparable "
                        + "that justified 200000 over the count-1 rows' 10000-80000 band");

        String changelog = Files.readString(
                Path.of("src", "main", "resources", "db", "changelog-data.xml"), StandardCharsets.ISO_8859_1);
        assertTrue(changelog.contains("159-evan-pork-drop-data.sql"),
                "159 exists on disk but is not registered in changelog-data.xml, so it never runs");
        assertTrue(changelog.contains("155-evan-tutorial-drop-data.sql")
                        && changelog.contains("156-evan-chain-drop-data.sql"),
                "an APPLIED evan-chain changeSet vanished from the changelog");
    }

    /**
     * <strong>Why 4032474 "Seruf Pearl" got no row, despite looking dead.</strong> Its two drop rows
     * sit on mobs 4220000 and 9303014, and neither appears in any map's life list - which is why the
     * generated report {@code tools/parity/reports/drops-mob-never-spawned.txt} flags 4220000. That
     * report only reads Map.wz life, and Seruf is not placed that way: {@code AreaBossSeruf.js}
     * spawns 4220001 into 230020100 at boot, and 4220001's {@code revive} node turns it into 4220000
     * on death, which {@code Monster.killBy} implements. So the existing row is reachable and quests
     * 22404/22405 are fine. This pins the chain, because the tempting "fix" is a guessed drop row.
     */
    @Test
    void theSerufPearlIsAlreadyReachableViaTheAreaBossReviveChain() throws IOException {
        assertEquals("Seruf", stringName("Mob.img", "4220000"));
        assertEquals("Seruf", stringName("Mob.img", "4220001"));

        Data revive = DataProviderFactory.getDataProvider(WZFiles.MOB)
                .getData("4220001.img").getChildByPath("info/revive");
        assertNotNull(revive, "mob 4220001 lost its revive node, so killing the shell no longer "
                + "produces 4220000 and 4032474's drop row really would be dead");
        assertEquals(4220000, DataTool.getInt(revive.getChildByPath("0"), -1),
                "4220001 no longer revives into 4220000");

        String spawner = Files.readString(Path.of("scripts", "event", "AreaBossSeruf.js"),
                StandardCharsets.ISO_8859_1);
        assertTrue(spawner.contains("4220001") && spawner.contains("230020100"),
                "AreaBossSeruf.js no longer spawns 4220001 into 230020100, so nothing raises Seruf "
                        + "at all and 4032474 becomes a genuine gap");
        assertEquals("The Seaweed Tower", mapNameString(230020100));

        // and no one has since invented a dropper for it
        assertFalse(Files.readString(CHANGESET_159, StandardCharsets.UTF_8).contains("4032474"),
                "changeSet 159 has grown a row for 4032474 - it already has a working source, and a "
                        + "guessed dropper is the exact failure this ticket forbids");
    }

    /** Guards against the Quest cache being empty, which would make the lookups above vacuous. */
    @Test
    void theEvanQuestsActuallyLoad() {
        assertFalse(Quest.getInstance(22503).getName().isBlank(), "quest 22503 did not load");
        assertFalse(Quest.getInstance(22504).getName().isBlank(), "quest 22504 did not load");
    }

    // ---------------------------------------------------------------- helpers

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
