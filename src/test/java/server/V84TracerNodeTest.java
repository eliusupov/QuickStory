package server;

import org.junit.jupiter.api.Test;
import provider.Data;
import provider.DataProvider;
import provider.DataTool;
import provider.wz.WZFiles;
import provider.wz.XMLWZFile;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static server.V84Wz.wz;

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

    // wz(String) lives in V84Wz - one copy for all the v84 node tests (ticket 03f, F8).

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

}

/*
 * Deliberately NOT a test here: the ItemInformationProvider path. It was run once during
 * ticket 03 against a live MySQL and passed —
 *
 *     ii.getName(2001500)                  -> "Red Potion"
 *     ii.getItemEffect(2001500).getHp()    -> 50
 *     ii.isUntradeableRestricted(2001500)  -> true
 *
 * — but it does not belong in the committed suite. Its constructor reads the monstercarddata
 * table, so it needs DatabaseConnection.initializeConnectionPool(), which on a machine without
 * a database burns INIT_CONNECTION_POOL_TIMEOUT (90 s in config.yaml) before it can be skipped,
 * and on a machine with one leaves the static dataSource set for the rest of the surefire fork.
 * A 90-second CI stall and cross-test global state are too high a price for an assertion the
 * provider-layer tests above already cover: ItemInformationProvider reads these same nodes
 * through the same XMLWZFile.
 *
 * To re-run it by hand, add a test that calls initializeConnectionPool() first and invoke it
 * with -Dtest=... so it gets its own fork.
 */
