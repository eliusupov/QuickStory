package server;

import org.junit.jupiter.api.Test;
import provider.Data;
import provider.DataProvider;
import provider.DataTool;
import provider.wz.WZFiles;
import provider.wz.XMLWZFile;
import tools.DatabaseConnection;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Ticket 03 tracer bullet: item 2001500 ("Red Potion", the untradeable v84 variant) was
 * imported from the GMS v84 WZ into this repo's server XML tree by
 * {@code docs/wz-baseline/tool-merge}. Procedure: {@code docs/work-plan/WZ-MERGE-PROCEDURE.md}.
 * <p>
 * This is the agent-verifiable half of the ticket: the server's own XML reader
 * ({@link XMLWZFile} / {@code XMLDomMapleData}, the exact classes the running server uses)
 * parses the imported nodes and reads their values back.
 * <p>
 * Note the {@link XMLWZFile} constructed by hand instead of {@code DataProviderFactory}:
 * {@link WZFiles#DIRECTORY} is a {@code static final} resolved once per JVM, and
 * {@code MobSkillFactoryTest} points the {@code wz-path} property at a {@code @TempDir}
 * before it. Whichever test class runs first wins for the whole fork, so anything reading
 * the real tree through {@code WZFiles} is order-dependent. Tickets 04-09: extend this class
 * with your own ids, and keep using the explicit path.
 */
class V84TracerNodeTest {

    private static final int TRACER_ITEM_ID = 2001500;

    private static DataProvider wz(String wzFile) {
        return new XMLWZFile(Path.of("wz", wzFile));
    }

    @Test
    void itemWzTracerNodeParses() {
        Data bucket = wz("Item.wz").getData("Consume/0200.img");
        assertNotNull(bucket, "Item.wz/Consume/0200.img.xml did not parse");

        Data node = bucket.getChildByPath("0" + TRACER_ITEM_ID);
        assertNotNull(node, "Item.wz/Consume/0200.img/02001500 missing from the server XML tree");
        assertEquals(50, DataTool.getInt("spec/hp", node, -1), "spec/hp");
        assertEquals(1, DataTool.getInt("info/tradeBlock", node, -1), "info/tradeBlock");
        assertEquals(0, DataTool.getInt("info/price", node, -1), "info/price");
    }

    @Test
    void stringWzTracerNodeParses() {
        Data bucket = wz("String.wz").getData("Consume.img");
        assertNotNull(bucket, "String.wz/Consume.img.xml did not parse");

        Data node = bucket.getChildByPath(String.valueOf(TRACER_ITEM_ID));
        assertNotNull(node, "String.wz/Consume.img/2001500 missing from the server XML tree");
        assertEquals("Red Potion", DataTool.getString("name", node, null), "name");
    }

    /**
     * The merge must not have disturbed the images it inserted into. One neighbour on each
     * side of the insertion point in each file.
     */
    @Test
    void neighbouringNodesSurvivedTheMerge() {
        Data bucket = wz("Item.wz").getData("Consume/0200.img");
        assertEquals(50, DataTool.getInt("spec/hp", bucket.getChildByPath("02000000"), -1),
                "stock Red Potion 2000000 changed");
        assertEquals(180000, DataTool.getInt("spec/time", bucket.getChildByPath("02002000"), -1),
                "stock 2002000 changed");

        assertNotNull(wz("String.wz").getData("Consume.img").getChildByPath("2023000"),
                "String.wz/Consume.img/2023000 disappeared");
    }

    /**
     * The real consumer, end to end: name from String.wz, usable effect from Item.wz spec.
     * Needs the real {@code wz-path} (see the class comment) and a database, because
     * {@link ItemInformationProvider}'s constructor reads the monster-card table. Skipped,
     * not failed, when either is missing.
     */
    @Test
    void itemInformationProviderResolvesTracer() {
        assumeTrue(Files.exists(WZFiles.ITEM.getFile().resolve("Consume/0200.img.xml")),
                "wz-path was redirected by another test class in this fork - skipping");
        assumeTrue(DatabaseConnection.initializeConnectionPool(), "no database reachable - skipping");

        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        assertEquals("Red Potion", ii.getName(TRACER_ITEM_ID));
        assertEquals(50, ii.getItemEffect(TRACER_ITEM_ID).getHp(), "the item must actually heal 50 HP");
        assertTrue(ii.isUntradeableRestricted(TRACER_ITEM_ID), "v84 marks 2001500 tradeBlock=1");
    }
}
