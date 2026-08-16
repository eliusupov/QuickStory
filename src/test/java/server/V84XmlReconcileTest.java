package server;

import org.junit.jupiter.api.Test;
import provider.Data;
import provider.DataTool;

import java.util.Map;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static server.V84Wz.wz;

/**
 * Ticket 03j — the server XML tree reconciled against the composed binary merge.
 * <p>
 * The positional-array gate landed in 03g, <em>after</em> ticket 04 had already spliced its rows
 * into {@code wz/}. Nothing un-applied the server side, so the XML carried rows the composed
 * binary run refuses. The full inventory is {@code docs/wz-baseline/XML-RECONCILE.md}; the two
 * rows with a consequence are pinned here.
 * <p>
 * A sibling of {@link V84CosmeticNodeTest} for the same reason all the v84 node tests are
 * siblings, and it opens the tree the same one way, through {@link V84Wz#wz}.
 */
class V84XmlReconcileTest {

    /** golden pig egg boxes; {@code reward} is a positional array and 04 appended a 44th slot. */
    private static final Map<String, Integer> EGG_BOX_TOTALPROB = Map.of(
            "02022503", 19864,
            "02022514", 18363);

    /**
     * {@code ItemInformationProvider.getItemReward} iterates <em>every</em> child of {@code reward}
     * and sums {@code prob}, so a duplicated slot rolls its item at double weight and skews
     * {@code totalprob} against the client's table. 04 appended {@code reward/43}, a byte-identical
     * copy of the live {@code reward/16} ({@code 2020014} x10, prob 50), to both boxes; the
     * composed binary merge refuses it (03h/Item.conflicts.txt). Reverted here, so the two tables
     * are the 43 consecutive slots the client ships after install, and these totals are the ones
     * measured off {@code wz-merge/03h/Item.wz} itself.
     */
    @Test
    void goldenPigEggRewardTablesMatchTheComposedClientTable() {
        Data boxes = wz("Item.wz").getData("Consume/0202.img");
        assertNotNull(boxes, "Item.wz/Consume/0202.img.xml did not parse");

        EGG_BOX_TOTALPROB.forEach((boxId, expectedTotalProb) -> {
            Data reward = boxes.getChildByPath(boxId + "/reward");
            assertNotNull(reward, boxId + " has no reward table");

            int totalProb = 0;
            int rollsOf2020014 = 0;
            for (Data slot : reward.getChildren()) {   // same traversal as getItemReward
                if (DataTool.getInt("item", slot, 0) == 2020014) {
                    rollsOf2020014++;
                }
                totalProb += DataTool.getInt("prob", slot, 0);
            }

            TreeSet<Integer> slots = slotNames(reward);
            assertEquals(43, slots.size(), boxId + "/reward slot count");
            assertEquals(0, slots.first(), boxId + "/reward first slot");
            assertEquals(42, slots.last(), boxId + "/reward last slot — 43 is the refused row");
            assertEquals(1, rollsOf2020014, boxId + "/reward rolls 2020014 more than once");
            assertEquals(expectedTotalProb, totalProb, boxId + "/reward totalprob");
        });
    }

    /**
     * Ticket 08 spliced {@code gate/7} without {@code gate/6}, leaving the array {@code 0-5,7}.
     * The composed binary list carries {@code gate/6} as its one composition fill row precisely to
     * close that hole, so the XML has to carry it too or the two trees disagree on the array's
     * shape. Art only — nothing under {@code Map.wz/Obj} is read by the server.
     */
    @Test
    void questGateArrayHasNoHole() {
        Data effect = wz("Map.wz").getData("Obj/effect.img");
        assertNotNull(effect, "Map.wz/Obj/effect.img.xml did not parse");
        Data gate = effect.getChildByPath("quest/gate");
        assertNotNull(gate, "Map.wz/Obj/effect.img/quest/gate missing");

        // size 8 with first 0 and last 7 is exactly "0..7, no hole" — a missing 6 fails on size.
        TreeSet<Integer> slots = slotNames(gate);
        assertEquals(8, slots.size(), "quest/gate slot count, slots were " + slots);
        assertEquals(0, slots.first(), "quest/gate first slot");
        assertEquals(7, slots.last(), "quest/gate last slot");
    }

    /** child names of a positional array, as ints — every container here is one. */
    private static TreeSet<Integer> slotNames(Data container) {
        TreeSet<Integer> slots = new TreeSet<>();
        container.getChildren().forEach(slot -> slots.add(Integer.parseInt(slot.getName())));
        return slots;
    }
}
