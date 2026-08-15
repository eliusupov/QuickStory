package server;

import org.junit.jupiter.api.Test;
import provider.Data;
import provider.DataProvider;
import provider.DataTool;
import provider.wz.WZFiles;
import provider.wz.XMLWZFile;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Ticket 04 — the v84 cosmetics merged into this repo's server XML tree by
 * {@code docs/wz-baseline/tool-merge}. Path lists:
 * {@code docs/wz-baseline/merge-lists/04/{Character,Item,String}.paths.txt}.
 * This class reads the server XML tree, so the counts that matter here are the XML ones:
 * {@code Character.wz} +237, {@code Item.wz} +390, {@code String.wz} +384, of which 30 are
 * authorised overwrites of the literal placeholder {@code MISSING NAME}/{@code MISSING INFO}
 * (see {@code COLLISION-FORCE.txt}). The client-side binary merge added a few more; the
 * path-list headers record both figures and why they differ.
 * <p>
 * A sibling of {@link V84TracerNodeTest} rather than more methods inside it, purely so that
 * ticket 04 and ticket 05 could land without sharing one file mid-flight. It is the same harness,
 * not a second one: the same {@link XMLWZFile} constructed by hand over the same explicit
 * {@code Path.of("wz", …)}, for the same reason — {@link WZFiles#DIRECTORY} is a
 * {@code static final} resolved once per JVM and {@code MobSkillFactoryTest} points
 * {@code wz-path} at a {@code @TempDir} before it, so whichever test class runs first wins for
 * the whole surefire fork. Never reach the real tree through {@code DataProviderFactory} here.
 */
class V84CosmeticNodeTest {

    private static DataProvider wz(String wzFile) {
        return new XMLWZFile(Path.of("wz", wzFile));
    }

    /** base id of each named v84 hair family; all eight colour digits 0-7 exist for each. */
    private static final int[] V84_HAIR_FAMILIES = {31990, 33030, 33050, 33150, 34060};

    /**
     * Sprite and name have to arrive together: {@code NPCConversationManager.getCosmeticItem}
     * and {@code HairCommand} both gate on {@code ItemInformationProvider.getName != null},
     * which reads String.wz/Eqp.img. A hair with art and no name is unreachable.
     */
    @Test
    void v84HairFamiliesHaveBothSpriteAndName() {
        DataProvider chr = wz("Character.wz");
        Data eqp = wz("String.wz").getData("Eqp.img");
        assertNotNull(eqp, "String.wz/Eqp.img.xml did not parse");

        for (int base : V84_HAIR_FAMILIES) {
            for (int colour = 0; colour < 8; colour++) {
                int id = base + colour;
                Data sprite = chr.getData(String.format("Hair/000%d.img", id));
                assertNotNull(sprite, "Character.wz/Hair/000" + id + ".img.xml did not parse");
                assertEquals("Hr", DataTool.getString("info/islot", sprite, null), id + " info/islot");

                Data name = eqp.getChildByPath("Eqp/Hair/" + id);
                assertNotNull(name, "String.wz/Eqp.img/Eqp/Hair/" + id + " missing");
                String label = DataTool.getString("name", name, null);
                assertNotNull(label, id + " has no name");
                assertFalse(label.isBlank() || label.equals("MISSING NAME"), id + " name is a placeholder: " + label);
            }
        }
    }

    /**
     * The nine hair ids v84 also names are Ezorsia's already — v84's value is byte-identical
     * there, so the additive gate refusing them costs nothing. Asserted so that a later ticket
     * pointing --force at Eqp/Hair breaks this test rather than the client's labels.
     */
    @Test
    void ezorsiaHairNamesWereNotOverwritten() {
        Data eqp = wz("String.wz").getData("Eqp.img");
        assertEquals("Black Tighty Bun", DataTool.getString("name", eqp.getChildByPath("Eqp/Hair/31660"), null));
        assertEquals("Brown Tighty Bun", DataTool.getString("name", eqp.getChildByPath("Eqp/Hair/31667"), null));
        assertEquals("Red The Coco", DataTool.getString("name", eqp.getChildByPath("Eqp/Hair/33101"), null));
    }

    /** {@code <img>.img -> ids} that COLLISION-FORCE.txt authorised overwriting. */
    private static final Object[][] FORCED_NAMES = {
            {"Cash.img", "", new int[]{5500005, 5500006, 5530001, 5530002, 5530003, 5530004, 5530005, 5530006, 5530007, 5530008}},
            {"Consume.img", "", new int[]{2100166}},
            {"Ins.img", "", new int[]{3994179, 3994180}},
            {"Etc.img", "Etc/", new int[]{4161049, 4161050, 4161051}},
            {"Eqp.img", "Eqp/Accessory/", new int[]{1142143, 1142144, 1142145, 1142149, 1142150, 1142151}},
            {"Eqp.img", "Eqp/Glove/", new int[]{1082262}},
            {"Eqp.img", "Eqp/Longcoat/", new int[]{1051176, 1052217, 1052224, 1052228}},
            {"Eqp.img", "Eqp/PetEquip/", new int[]{1802039}},
            {"Eqp.img", "Eqp/Shield/", new int[]{1092067}},
            {"Eqp.img", "Eqp/Weapon/", new int[]{1452058}}};

    /** none of the 30 forced ids may still read the Cosmic name-table stub. */
    @Test
    void forcedNamesNoLongerReadMissingName() {
        DataProvider str = wz("String.wz");
        for (Object[] group : FORCED_NAMES) {
            Data bucket = str.getData((String) group[0]);
            assertNotNull(bucket, "String.wz/" + group[0] + ".xml did not parse");
            String prefix = (String) group[1];
            for (int id : (int[]) group[2]) {
                Data node = bucket.getChildByPath(prefix + id);
                assertNotNull(node, group[0] + "/" + prefix + id + " missing");
                String label = DataTool.getString("name", node, null);
                assertNotNull(label, id + " has no name");
                assertFalse(label.equals("MISSING NAME"), id + " is still MISSING NAME");
            }
        }

        // spot-check the actual v84 text, so "not MISSING NAME" cannot pass on a blank.
        // several v84 name strings carry a trailing space; stripped here so the assertion is
        // about the text having arrived, not about Nexon's padding.
        Data eqp = str.getData("Eqp.img");
        assertEquals("Dragon Master's Proof",
                DataTool.getString("name", eqp.getChildByPath("Eqp/Glove/1082262"), "").strip());
        assertEquals("Transparent Shield",
                DataTool.getString("name", eqp.getChildByPath("Eqp/Shield/1092067"), "").strip());
        assertEquals("DS Medal Basket",
                DataTool.getString("name", str.getData("Cash.img").getChildByPath("5530001"), "").strip());
        assertEquals("Dragon Types and Characteristics (Vol.I)",
                DataTool.getString("name", str.getData("Etc.img").getChildByPath("Etc/4161049"), "").strip());
    }

    /**
     * The two medals the gate refused: Cosmic turned them into level-up medals, so the live
     * node is a strict superset of v84's and keeping it is right. Proves the refusal preserved
     * Cosmic's extra subtree rather than half-writing v84's.
     */
    @Test
    void cosmicLevelUpMedalsSurvivedTheRefusal() {
        DataProvider chr = wz("Character.wz");
        int[][] medals = {{1142153, 82}, {1142154, 83}};
        for (int[] medal : medals) {
            Data node = chr.getData(String.format("Accessory/0%d.img", medal[0]));
            assertNotNull(node, "Accessory/0" + medal[0] + ".img.xml did not parse");
            assertNotNull(node.getChildByPath("info/level"), medal[0] + " lost Cosmic's info/level subtree");
            assertEquals(medal[1], DataTool.getInt("info/medalTag", node, -1), medal[0] + " info/medalTag");
        }
    }

    /** a sample of the new v84 equips and items, one per .wz the ticket wrote to. */
    @Test
    void v84CosmeticEquipsAndItemsParse() {
        Data cap = wz("Character.wz").getData("Cap/01003010.img");
        assertNotNull(cap, "Character.wz/Cap/01003010.img.xml did not parse");
        assertEquals("Cp", DataTool.getString("info/islot", cap, null), "01003010 info/islot");

        // v84 added a whole new image to Item.wz/Pet and to Item.wz/Cash. The pet's name lives
        // in String.wz/Pet.img, so it is on this ticket's path list too — an item added without
        // its name row is unobtainable, since every route filters on getName != null.
        DataProvider item = wz("Item.wz");
        assertNotNull(item.getData("Pet/5000067.img"), "Item.wz/Pet/5000067.img.xml missing");
        assertNotNull(item.getData("Cash/0562.img"), "Item.wz/Cash/0562.img.xml missing");
        assertEquals("Weird Alien",
                DataTool.getString("name", wz("String.wz").getData("Pet.img").getChildByPath("5000067"), null),
                "String.wz/Pet.img/5000067 name");

        // and new nodes inside images that already existed
        Data mace = wz("Character.wz").getData("Afterimage/mace.img");
        assertNotNull(mace.getChildByPath("16"), "Afterimage/mace.img/16 missing");
    }

    /**
     * Known gap, recorded rather than papered over: v84 ships Hair/00034040-00034047 as art
     * with NO String.wz/Eqp.img entry in either v84 or the live client. The sprites merge, but
     * every server-side route to a hair filters on getName != null, so these eight are
     * unreachable until someone authors names for them. Deliberately not invented here.
     */
    @Test
    void unnamedV84HairFamilyIsPresentButUnnamed() {
        DataProvider chr = wz("Character.wz");
        Data eqp = wz("String.wz").getData("Eqp.img");
        for (int id = 34040; id <= 34047; id++) {
            assertNotNull(chr.getData(String.format("Hair/000%d.img", id)),
                    "Character.wz/Hair/000" + id + ".img.xml did not parse");
            assertNull(eqp.getChildByPath("Eqp/Hair/" + id),
                    id + " now has a name - delete this test and add it to V84_HAIR_FAMILIES");
        }
    }
}
