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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins changeSet 158 - the six Maker recipes v84 adds to {@code Etc.wz/ItemMake.img} that the
 * server had no row for at all: the two medals 1142156/1142157 and, on the owner's critical path,
 * the four Evan dragon equipment slots 1942002/1952002/1962002/1972002.
 *
 * <pre>
 *   mvnw.cmd test -Dtest=MakerV84RealLoad
 * </pre>
 *
 * <p><strong>Not a {@code *Test} class on purpose</strong>, same reason as its siblings:
 * {@link WZFiles#DIRECTORY} is a {@code static final} resolved once per JVM.
 *
 * <p><strong>What this checks.</strong> Ticket 59 R09 merged the six nodes into
 * {@code wz/Etc.wz/ItemMake.img.xml}, so the ingredient lists are no longer literals this test has
 * to take on trust: {@link #theRecipeIsInThisTreesItemMakeAndAgreesWithChangeSet158()} re-reads
 * every scalar and every recipe pair out of the archive and compares them to the SQL. On top of
 * that it checks the derived values, which are the ones a human could have got wrong:
 *
 * <ul>
 *   <li>{@code req_meso} is not the raw {@code meso} leaf. It is marked up by
 *       {@code tools/mapletools/SkillMakerFetcher#generateUpdatedItemFee()}, and the markup depends
 *       on the equip's own {@code Character.wz} {@code reqLevel} straddling 108.
 *       {@link #reqMesoIsWhatThisReposOwnGeneratorFormulaProduces()} recomputes it from the WZ.</li>
 *   <li>Every ingredient and every crafted item must actually exist in the tree.</li>
 *   <li>Both {@code makercreatedata} and {@code makerrecipedata} rows must be present - either
 *       alone leaves the recipe unusable, see {@code ItemInformationProvider#getMakerItemEntry}.</li>
 * </ul>
 */
class MakerV84RealLoad {

    private static final Path CHANGESET_158 =
            Path.of("src", "main", "resources", "db", "data", "158-maker-v84-data.sql");

    /** The two medals, ItemMake.img group 0. */
    private static final int[] MEDALS = {1142156, 1142157};

    /** The four Evan dragon equipment slots, ItemMake.img group 2. */
    private static final int[] DRAGON_EQUIPS = {1942002, 1952002, 1962002, 1972002};

    /** Raw {@code meso} leaf from ItemMake.img/2/019x2002, before the generator's markup. */
    private static final int DRAGON_RAW_MESO = 300000;

    private static final Pattern CREATE_ROW = Pattern.compile(
            "\\((\\d+), (\\d+), (\\d+), (\\d+), (\\d+), (\\d+), (\\d+), (\\d+), (\\d+), (\\d+)\\)");
    private static final Pattern RECIPE_ROW = Pattern.compile("\\((\\d+), (\\d+), (\\d+)\\)");

    @BeforeAll
    static void muteGraal() {
        System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
        assertTrue(Files.isRegularFile(Path.of(WZFiles.DIRECTORY, "Etc.wz", "ItemMake.img.xml")),
                "wz-path resolved to '" + WZFiles.DIRECTORY + "', which holds no Etc.wz/ItemMake.img "
                        + "- another test class won the WZFiles.DIRECTORY race, so this says nothing "
                        + "about Maker");
    }

    /**
     * The derived number, recomputed rather than trusted. {@code makercreatedata.req_meso} is
     * {@code SkillMakerFetcher#generateUpdatedItemFee()} applied to the ItemMake {@code meso} leaf:
     * for a non-weapon equip it is {@code 1000 * ceil((meso/d + meso)/1000)} where {@code d} is 10
     * when the equip's own reqLevel is at least 108 and 11 below that. All four dragon equips are
     * reqLevel 120, so 300000 becomes 330000 - and if anyone ever edits those equips down past 108,
     * this fails instead of leaving a stale fee behind.
     *
     * <p>The same formula was checked against all 145 pre-existing group-2 rows before use and
     * reproduced every one of them exactly.
     */
    @Test
    void reqMesoIsWhatThisReposOwnGeneratorFormulaProduces() throws IOException {
        Map<Integer, int[]> create = createRows();
        for (int itemId : DRAGON_EQUIPS) {
            int reqLevel = equipReqLevel(itemId);
            assertEquals(120, reqLevel,
                    "dragon equip " + itemId + " is no longer reqLevel 120, so its Maker fee changed");
            assertEquals(makerFee(DRAGON_RAW_MESO, reqLevel), create.get(itemId)[4],
                    "changeSet 158's req_meso for " + itemId + " is not what generateUpdatedItemFee() "
                            + "produces from the v84 meso leaf " + DRAGON_RAW_MESO);
        }
        for (int itemId : MEDALS) {
            assertEquals(0, create.get(itemId)[4],
                    "medal " + itemId + " has meso 0 in ItemMake.img, so its fee must be 0");
        }
    }

    /**
     * Both halves are present for all six. {@code getMakerItemEntry} reads the header from
     * makercreatedata and the ingredients from makerrecipedata, so a recipe with only one of them
     * is either free or uncraftable.
     */
    @Test
    void allSixHaveBothAHeaderRowAndIngredients() throws IOException {
        Map<Integer, int[]> create = createRows();
        Map<Integer, List<int[]>> recipe = recipeRows();

        assertEquals(6, create.size(), "changeSet 158 should add exactly 6 makercreatedata rows");
        assertEquals(6, recipe.size(), "changeSet 158 should cover exactly 6 items in makerrecipedata");

        for (int itemId : allSix()) {
            assertNotNull(create.get(itemId), "no makercreatedata row for " + itemId);
            assertTrue(recipe.getOrDefault(itemId, List.of()).size() >= 4,
                    "item " + itemId + " has fewer ingredients than the v84 recipe lists");
            assertEquals(1, create.get(itemId)[8], "every one of these recipes yields itemNum 1");
        }

        // group 0 for the medals, group 2 for the equips, mirroring ItemMake.img
        for (int itemId : MEDALS) {
            assertEquals(0, create.get(itemId)[0], "medal " + itemId + " belongs to ItemMake.img/0");
        }
        for (int itemId : DRAGON_EQUIPS) {
            assertEquals(2, create.get(itemId)[0], "equip " + itemId + " belongs to ItemMake.img/2");
            assertEquals(3, create.get(itemId)[9], "v84 gives the dragon equips tuc 3");
            assertEquals(3, create.get(itemId)[3], "v84 gates the dragon equips at Maker skill 3");
            assertEquals(115, create.get(itemId)[2], "v84 gates the dragon equips at character level 115");
        }
    }

    /** A recipe naming an item that does not exist is a typo that would fail only in play. */
    @Test
    void everyCraftedItemAndEveryIngredientExists() throws IOException {
        for (int itemId : DRAGON_EQUIPS) {
            assertTrue(Files.isRegularFile(Path.of(WZFiles.DIRECTORY, "Character.wz", "Dragon",
                            String.format("%08d", itemId) + ".img.xml")),
                    "Character.wz/Dragon has no image for dragon equip " + itemId);
        }
        for (int itemId : MEDALS) {
            assertTrue(Files.isRegularFile(Path.of(WZFiles.DIRECTORY, "Character.wz", "Accessory",
                            String.format("%08d", itemId) + ".img.xml")),
                    "Character.wz/Accessory has no image for medal " + itemId);
        }
        for (Map.Entry<Integer, List<int[]>> row : recipeRows().entrySet()) {
            for (int[] ingredient : row.getValue()) {
                assertNotNull(etcItem(ingredient[0]),
                        "recipe for " + row.getKey() + " needs item " + ingredient[0]
                                + ", which is in no Item.wz/Etc image");
                assertTrue(ingredient[1] > 0,
                        "recipe for " + row.getKey() + " asks for a non-positive count of "
                                + ingredient[0]);
            }
        }
    }

    /**
     * The dragon equips must stay disassemblable for free. {@code getMakerDisassembledItems} selects
     * ingredients in the 4260000-4269999 crystal window and hands half of them back; the v84 recipes
     * carry 4260007 and 4260008, so this works with no extra data - as long as nobody edits those
     * ingredients out.
     */
    @Test
    void theDragonEquipsCarryCrystalsSoDisassemblyReturnsSomething() throws IOException {
        Map<Integer, List<int[]>> recipe = recipeRows();
        for (int itemId : DRAGON_EQUIPS) {
            long crystals = recipe.get(itemId).stream()
                    .filter(r -> r[0] >= 4260000 && r[0] < 4270000)
                    .count();
            assertEquals(2, crystals,
                    "dragon equip " + itemId + " no longer lists the two 4260xxx crystals, so "
                            + "getMakerDisassembledItems would return nothing for it");
        }
    }

    /**
     * The merge landed (ticket 59, R09): all six nodes now sit in this tree's ItemMake.img, under
     * the group the changeSet records and with <strong>zero-padded</strong> 8-digit names, which is
     * how {@code ItemInformationProvider#getMakerStimulant} looks them up. Since the tree now has
     * them, changeSet 158's literals are no longer unverifiable: every scalar and every
     * {@code recipe/<n>/{item,count}} pair is re-read from the archive and compared against the SQL,
     * which is what a {@code SkillMakerFetcher} run would derive. Row set, not row count.
     */
    @Test
    void theRecipeIsInThisTreesItemMakeAndAgreesWithChangeSet158() throws IOException {
        Data itemMake = DataProviderFactory.getDataProvider(WZFiles.ETC).getData("ItemMake.img");
        assertNotNull(itemMake, "Etc.wz has no ItemMake.img at all");

        Map<Integer, int[]> create = createRows();
        Map<Integer, List<int[]>> recipe = recipeRows();

        for (int itemId : allSix()) {
            String padded = String.format("%08d", itemId);
            int group = create.get(itemId)[0];
            Data node = itemMake.getChildByPath(group + "/" + padded);
            assertNotNull(node, "ItemMake.img/" + group + " has no node named " + padded
                    + " - an unpadded name would be invisible to getMakerStimulant");

            assertEquals(create.get(itemId)[2], DataTool.getInt("reqLevel", node, -1),
                    "reqLevel leaf of " + padded + " disagrees with changeSet 158");
            assertEquals(create.get(itemId)[3], DataTool.getInt("reqSkillLevel", node, -1),
                    "reqSkillLevel leaf of " + padded + " disagrees with changeSet 158");
            assertEquals(create.get(itemId)[8], DataTool.getInt("itemNum", node, -1),
                    "itemNum leaf of " + padded + " disagrees with changeSet 158");
            assertEquals(create.get(itemId)[9], DataTool.getInt("tuc", node, -1),
                    "tuc leaf of " + padded + " disagrees with changeSet 158");
            assertEquals(create.get(itemId)[5], DataTool.getInt("reqItem", node, 0),
                    "reqItem leaf of " + padded + " disagrees with changeSet 158");

            // The WZ meso leaf is the RAW figure; makercreatedata.req_meso is it marked up.
            int rawMeso = DataTool.getInt("meso", node, -1);
            assertEquals(makerFee(rawMeso, rawMeso == 0 ? 0 : equipReqLevel(itemId)),
                    create.get(itemId)[4],
                    "req_meso for " + itemId + " is not generateUpdatedItemFee() of the tree's meso "
                            + "leaf " + rawMeso);

            List<String> fromTree = new ArrayList<>();
            Data recipeNode = node.getChildByPath("recipe");
            assertNotNull(recipeNode, "ItemMake.img/" + group + "/" + padded + " has no recipe array");
            for (int i = 0; recipeNode.getChildByPath(String.valueOf(i)) != null; i++) {
                Data entry = recipeNode.getChildByPath(String.valueOf(i));
                fromTree.add(DataTool.getInt("item", entry, -1) + "x" + DataTool.getInt("count", entry, -1));
            }
            List<String> fromSql = new ArrayList<>();
            for (int[] row : recipe.getOrDefault(itemId, List.of())) {
                fromSql.add(row[0] + "x" + row[1]);
            }
            assertEquals(fromSql, fromTree,
                    "changeSet 158's makerrecipedata rows for " + itemId + " are not the recipe array "
                            + "a SkillMakerFetcher run would read out of ItemMake.img");
        }
    }

    /** The changeSet must exist, be registered, and not have disturbed the applied ones. */
    @Test
    void changeSet158IsRegisteredAndTheAppliedOnesAreUntouched() throws IOException {
        assertTrue(Files.isRegularFile(CHANGESET_158), "changeSet 158 seed file is missing");

        String changelog = Files.readString(
                Path.of("src", "main", "resources", "db", "changelog-data.xml"), StandardCharsets.ISO_8859_1);
        assertTrue(changelog.contains("158-maker-v84-data.sql"),
                "158 exists on disk but is not registered in changelog-data.xml, so it never runs");
        assertTrue(changelog.contains("DELETE FROM makerrecipedata WHERE itemid IN ("),
                "changeSet 158 lost its makerrecipedata rollback");
        assertTrue(changelog.contains("156-evan-chain-drop-data.sql"),
                "applied changeSet 156 vanished from the changelog");
    }

    // ---------------------------------------------------------------------------------------

    /** {@code SkillMakerFetcher#generateUpdatedItemFee()}, non-weapon equip branch. */
    private static int makerFee(int meso, int equipReqLevel) {
        float adjusted = meso;
        adjusted /= (equipReqLevel >= 108) ? 10 : 11;
        adjusted += meso;
        adjusted /= 1000;
        return 1000 * (int) Math.ceil(adjusted);
    }

    private static int[] allSix() {
        int[] all = new int[MEDALS.length + DRAGON_EQUIPS.length];
        System.arraycopy(MEDALS, 0, all, 0, MEDALS.length);
        System.arraycopy(DRAGON_EQUIPS, 0, all, MEDALS.length, DRAGON_EQUIPS.length);
        return all;
    }

    /** itemid -> the ten makercreatedata columns, in declaration order. */
    private static Map<Integer, int[]> createRows() throws IOException {
        Map<Integer, int[]> rows = new LinkedHashMap<>();
        Matcher m = CREATE_ROW.matcher(section("INSERT INTO makercreatedata"));
        while (m.find()) {
            int[] row = new int[10];
            for (int i = 0; i < 10; i++) {
                row[i] = Integer.parseInt(m.group(i + 1));
            }
            rows.put(row[1], row);
        }
        return rows;
    }

    /** itemid -> list of {req_item, count}. */
    private static Map<Integer, List<int[]>> recipeRows() throws IOException {
        Map<Integer, List<int[]>> rows = new LinkedHashMap<>();
        Matcher m = RECIPE_ROW.matcher(section("INSERT INTO makerrecipedata"));
        while (m.find()) {
            rows.computeIfAbsent(Integer.parseInt(m.group(1)), k -> new ArrayList<>())
                    .add(new int[]{Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))});
        }
        return rows;
    }

    private static String section(String insert) throws IOException {
        String sql = Files.readString(CHANGESET_158, StandardCharsets.UTF_8);
        int from = sql.indexOf(insert);
        assertTrue(from >= 0, "changeSet 158 no longer contains '" + insert + "'");
        int to = sql.indexOf(';', from);
        assertTrue(to > from, "'" + insert + "' in changeSet 158 is unterminated");
        return sql.substring(from, to);
    }

    /** Equip reqLevel straight off Character.wz - the input the fee markup branches on. */
    private static int equipReqLevel(int itemId) throws IOException {
        Path img = Path.of(WZFiles.DIRECTORY, "Character.wz", "Dragon",
                String.format("%08d", itemId) + ".img.xml");
        assertTrue(Files.isRegularFile(img), "Character.wz/Dragon has no image for " + itemId);
        Matcher m = Pattern.compile("<int name=\"reqLevel\" value=\"(\\d+)\"/>")
                .matcher(Files.readString(img, StandardCharsets.ISO_8859_1));
        assertTrue(m.find(), "no reqLevel leaf in " + img);
        return Integer.parseInt(m.group(1));
    }

    /** Item.wz/Etc images bundle items in blocks of 100, so derive the image from the id. */
    private static Data etcItem(int itemId) {
        String image = String.format("Etc/%04d.img", itemId / 10000);
        Data block = DataProviderFactory.getDataProvider(WZFiles.ITEM).getData(image);
        return block == null ? null : block.getChildByPath(String.format("%08d", itemId));
    }
}
