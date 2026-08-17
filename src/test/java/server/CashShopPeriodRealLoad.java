package server;

import org.junit.jupiter.api.Test;
import provider.Data;
import provider.DataProviderFactory;
import provider.DataTool;
import provider.wz.WZFiles;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins that nothing on sale in the cash shop expires in a day by accident.
 *
 * <p>{@code CashItemFactory.loadAllCashItems} reads {@code Period} with a default of <em>1</em>
 * (CashShop:246) and {@code CashItem}'s constructor then maps {@code period == 0 ? 90 : period}
 * (CashShop:142). A row that states {@code Period 0} therefore means 90 days, but a row with no
 * {@code Period} node at all falls to the default of 1 and, since
 * {@code ItemConstants.EXPIRING_ITEMS} is true, {@code toItem()} stamps it
 * {@code now + DAYS.toMillis(1)}.
 *
 * <p>79 of the 9,063 Commodity rows have no {@code Period} node. Exactly one of them is
 * {@code OnSale 1}: SN 60001005, item 5000060, 20,000 NX - by far the most expensive pet in the
 * catalogue, sold as a 24-hour item. The other 78 are not purchasable, which is why the fix is the
 * row rather than the default: changing the default would silently move all 79.
 *
 * <p>The value is not invented. SN 60001005 is one of six locally added rows, 60001000-60001005 at
 * Commodity nodes 8941-8946; the other five are identical in shape and all state
 * {@code Period 90}. There is no "permanent" pet row to copy - of the 211 pet rows in the tree, 207
 * state {@code Period 0} (which is 90 days) and the remaining 4 state 90 outright, so 90 days is
 * what every pet in this catalogue already is. {@link #everyPetOnSaleLastsNinetyDays()} pins that.
 *
 * <pre>
 *   mvnw.cmd test -Dtest=CashShopPeriodRealLoad
 * </pre>
 *
 * <p><strong>Not a {@code *Test} class on purpose</strong>, same reason as its siblings:
 * {@link WZFiles#DIRECTORY} is {@code static final} and resolved once per JVM, and
 * {@code MobSkillFactoryTest} points {@code wz-path} at an empty {@code @TempDir}.
 */
class CashShopPeriodRealLoad {

    /** The hand-added Pink Bean pet row that was being sold as a 24-hour item. */
    private static final int PINK_BEAN_PET_SN = 60001005;

    /** What {@code CashItem} turns a stated {@code Period} of 0 into. */
    private static final int DEFAULT_PERIOD_DAYS = 90;

    private static List<Data> commodityRows() {
        Data commodity = DataProviderFactory.getDataProvider(WZFiles.ETC).getData("Commodity.img");
        assertNotNull(commodity, "Etc.wz/Commodity.img did not load");
        List<Data> rows = commodity.getChildren();
        assertTrue(rows.size() > 8000, "Commodity.img looks empty: " + rows.size() + " rows");
        return rows;
    }

    /** Days an item bought from this row actually lasts, mirroring CashShop:246 and :142. */
    private static int effectivePeriod(Data row) {
        int stated = DataTool.getIntConvert("Period", row, 1);
        return stated == 0 ? DEFAULT_PERIOD_DAYS : stated;
    }

    /**
     * The regression. Before the fix SN 60001005 was the one on-sale row with no {@code Period}
     * node, so it sold a 20,000 NX pet that died in 24 hours.
     */
    @Test
    void noOnSaleRowIsMissingItsPeriod() {
        List<String> missing = new ArrayList<>();
        int onSale = 0;
        for (Data row : commodityRows()) {
            if (DataTool.getIntConvert("OnSale", row, 0) != 1) {
                continue;
            }
            onSale++;
            if (row.getChildByPath("Period") == null) {
                missing.add("SN " + DataTool.getIntConvert("SN", row)
                        + " (item " + DataTool.getIntConvert("ItemId", row)
                        + ", " + DataTool.getIntConvert("Price", row, 0) + " NX)"
                        + " has no Period node, so it is sold as a 1-day item");
            }
        }
        assertTrue(onSale > 0, "no on-sale rows found - the loop asserted nothing");
        assertEquals(List.of(), missing, "on-sale cash shop rows with no Period");
    }

    /** The row itself, and that it now matches the five siblings it was added alongside. */
    @Test
    void thePinkBeanPetLastsAsLongAsItsSiblingRows() {
        Data pinkBean = null;
        List<Integer> siblingPeriods = new ArrayList<>();
        for (Data row : commodityRows()) {
            int sn = DataTool.getIntConvert("SN", row);
            if (sn == PINK_BEAN_PET_SN) {
                pinkBean = row;
            } else if (sn >= 60001000 && sn < PINK_BEAN_PET_SN) {
                siblingPeriods.add(effectivePeriod(row));
            }
        }

        assertNotNull(pinkBean, "SN " + PINK_BEAN_PET_SN + " is gone from the catalogue");
        assertEquals(5, siblingPeriods.size(), "expected rows 60001000-60001004 alongside it");
        for (int sibling : siblingPeriods) {
            assertEquals(DEFAULT_PERIOD_DAYS, sibling,
                    "a sibling row changed, so " + PINK_BEAN_PET_SN + " is no longer copying it");
        }
        assertEquals(DEFAULT_PERIOD_DAYS, effectivePeriod(pinkBean),
                "SN " + PINK_BEAN_PET_SN + " does not last as long as the rows it was added with");
    }

    /**
     * No pet in this catalogue is permanent, so 90 days is not a downgrade of anything - it is
     * what every other purchasable pet already is.
     */
    @Test
    void everyPetOnSaleLastsNinetyDays() {
        List<String> odd = new ArrayList<>();
        int pets = 0;
        for (Data row : commodityRows()) {
            int itemId = DataTool.getIntConvert("ItemId", row);
            if (itemId < 5000000 || itemId >= 5001000 || DataTool.getIntConvert("OnSale", row, 0) != 1) {
                continue;
            }
            pets++;
            if (effectivePeriod(row) != DEFAULT_PERIOD_DAYS) {
                odd.add("SN " + DataTool.getIntConvert("SN", row) + " lasts " + effectivePeriod(row) + " days");
            }
        }
        assertTrue(pets > 20, "only " + pets + " pets on sale - the loop asserted nothing");
        assertEquals(List.of(), odd, "on-sale pets that do not last 90 days");
    }
}
