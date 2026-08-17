package net.server.channel.handlers;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import provider.Data;
import provider.DataProvider;
import provider.DataProviderFactory;
import provider.DataTool;
import provider.wz.WZFiles;
import server.ItemInformationProvider;
import server.ItemInformationProvider.RewardItem;
import tools.DatabaseConnection;
import tools.Pair;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the two cash item types the six mage starter packages are made of.
 *
 * <p>{@code UseCashItemHandler} dispatches on {@code itemId / 10000}. Neither 553 (trade coupons)
 * nor 562 (mastery books) had a branch, so both fell through to
 * {@code log.warn("NEW CASH ITEM TYPE: {}")}: enableActions, item not consumed, nothing given.
 * Every child of packages 9102289-9102294 - 2,000 to 4,700 NX each, all OnSale=1 - is one of those
 * two types, so all six were inert on purchase.
 *
 * <p>The 553 branch had in fact been written and was dead: {@code itemType == 552} appeared twice,
 * the second commented {@code //DS EGG THING} and unreachable behind the Scissors of Karma branch
 * above it. "DS Egg" is item 4170012, which is exactly what the reward node of 5530009-12 hands
 * out, so the branch was a 553 that had been typed as 552.
 * {@link #noItemTypeIsDispatchedTwice()} is the general guard against that class of mistake.
 *
 * <pre>
 *   mvnw.cmd test -Dtest=CashItemTypeDispatchRealLoad
 * </pre>
 *
 * <p><strong>Not a {@code *Test} class on purpose</strong>, same reason as its siblings:
 * {@link WZFiles#DIRECTORY} is {@code static final} and resolved once per JVM, and
 * {@code MobSkillFactoryTest} points {@code wz-path} at an empty {@code @TempDir}.
 */
class CashItemTypeDispatchRealLoad {

    private static final Path HANDLER =
            Path.of("src", "main", "java", "net", "server", "channel", "handlers", "UseCashItemHandler.java");

    /** {@code } else if (itemType == 553) {} and friends. */
    private static final Pattern DISPATCHED_TYPE = Pattern.compile("itemType == (\\d+)");

    /** The six packages the audit found inert, each OnSale=1. */
    private static final List<Integer> MAGE_PACKAGES =
            List.of(9102289, 9102290, 9102291, 9102292, 9102293, 9102294);

    private static final int TRADE_COUPON = 553;
    private static final int MASTERY_BOOK = 562;

    /** Same shim {@code MasteryBookJobMatchRealLoad} uses; the provider constructor reads a table. */
    @BeforeAll
    static void bootTheProviderWithoutADatabase() {
        try (MockedStatic<DatabaseConnection> db = Mockito.mockStatic(DatabaseConnection.class)) {
            db.when(DatabaseConnection::getConnection).thenThrow(new SQLException("no database in tests"));
            ItemInformationProvider.getInstance();
        }
    }

    private static DataProvider etc() {
        return DataProviderFactory.getDataProvider(WZFiles.ETC);
    }

    private static Set<Integer> dispatchedTypes() throws IOException {
        String source = Files.readString(HANDLER, StandardCharsets.UTF_8);
        Set<Integer> types = new HashSet<>();
        Matcher m = DISPATCHED_TYPE.matcher(source);
        while (m.find()) {
            types.add(Integer.parseInt(m.group(1)));
        }
        assertTrue(types.size() > 15, "only " + types.size() + " dispatch branches parsed out of "
                + HANDLER + " - the regex stopped matching, so every assertion here is vacuous");
        return types;
    }

    /** SN -> ItemId over the whole catalogue. */
    private static Map<Integer, Integer> itemIdBySn() {
        Map<Integer, Integer> bySn = new HashMap<>();
        for (Data row : etc().getData("Commodity.img").getChildren()) {
            bySn.put(DataTool.getIntConvert("SN", row), DataTool.getIntConvert("ItemId", row));
        }
        return bySn;
    }

    /**
     * The regression. Before the fix this failed on 553 and 562, the only two types the six
     * packages are built from.
     */
    @Test
    void everyChildOfTheSixMagePackagesHasADispatchBranch() throws IOException {
        Set<Integer> dispatched = dispatchedTypes();
        Map<Integer, Integer> bySn = itemIdBySn();
        Data packages = etc().getData("CashPackage.img");

        Set<String> unhandled = new TreeSet<>();
        int children = 0;
        for (int pkg : MAGE_PACKAGES) {
            Data node = packages.getChildByPath(String.valueOf(pkg));
            assertNotNull(node, "CashPackage.img/" + pkg + " is gone");
            for (Data sn : node.getChildByPath("SN").getChildren()) {
                Integer itemId = bySn.get(DataTool.getIntConvert(sn));
                assertNotNull(itemId, "package " + pkg + " names SN " + DataTool.getIntConvert(sn)
                        + ", which no Commodity row serves");
                if (!dispatched.contains(itemId / 10000)) {
                    unhandled.add(pkg + " -> item " + itemId + " (type " + itemId / 10000 + ")");
                }
                children++;
            }
        }
        assertTrue(children > 0, "no package children read - the loop asserted nothing");
        assertEquals(Set.of(), unhandled,
                "package children whose item type falls through to \"NEW CASH ITEM TYPE\"");
    }

    /**
     * The chain is a single if/else-if on one variable, so a type named twice makes the second
     * branch unreachable. That is precisely how the 553 branch sat dead as a duplicate 552.
     */
    @Test
    void noItemTypeIsDispatchedTwice() throws IOException {
        String source = Files.readString(HANDLER, StandardCharsets.UTF_8);
        List<Integer> seen = new ArrayList<>();
        List<String> duplicates = new ArrayList<>();
        Matcher m = DISPATCHED_TYPE.matcher(source);
        while (m.find()) {
            int type = Integer.parseInt(m.group(1));
            if (seen.contains(type)) {
                duplicates.add(String.valueOf(type));
            }
            seen.add(type);
        }
        assertEquals(List.of(), duplicates,
                "cash item types dispatched more than once - every branch after the first is dead code");
    }

    /** 553 is routed straight at getItemReward, so each coupon must actually carry a reward node. */
    @Test
    void everyTradeCouponNamesExactlyWhatItTradesFor() {
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        List<String> broken = new ArrayList<>();
        int checked = 0;
        for (Data item : DataProviderFactory.getDataProvider(WZFiles.ITEM)
                .getData("Cash/0553.img").getChildren()) {
            int itemId = Integer.parseInt(item.getName());
            Pair<Integer, List<RewardItem>> reward = ii.getItemReward(itemId);
            if (reward.getRight().isEmpty()) {
                broken.add(itemId + ": no reward entries");
            } else if (reward.getLeft() <= 0) {
                broken.add(itemId + ": total probability " + reward.getLeft()
                        + ", so Randomizer.nextInt would never pick a reward");
            } else {
                for (RewardItem r : reward.getRight()) {
                    if (r.itemid == 0 || r.quantity <= 0) {
                        broken.add(itemId + ": reward item " + r.itemid + " x" + r.quantity);
                    }
                }
            }
            checked++;
        }
        assertTrue(checked >= 17, "only " + checked + " type 553 items read");
        assertEquals(List.of(), broken, "trade coupons the 553 branch could not pay out");
    }

    /** 562 is routed at the skill-book path, which needs these four nodes off the book itself. */
    @Test
    void everyMasteryBookCarriesTheSkillNodesTheBookPathReads() {
        List<String> broken = new ArrayList<>();
        int checked = 0;
        for (Data item : DataProviderFactory.getDataProvider(WZFiles.ITEM)
                .getData("Cash/0562.img").getChildren()) {
            String id = item.getName();
            Data info = item.getChildByPath("info");
            if (info == null || info.getChildByPath("skill") == null) {
                broken.add(id + ": no info/skill, so getSkillStats returns skillid 0");
            } else if (DataTool.getInt("masterLevel", info, 0) <= 0) {
                broken.add(id + ": masterLevel " + DataTool.getInt("masterLevel", info, 0));
            } else if (DataTool.getInt("success", info, 0) <= 0) {
                broken.add(id + ": success " + DataTool.getInt("success", info, 0)
                        + ", which rollSuccessChance can never pass");
            }
            checked++;
        }
        assertTrue(checked > 0, "no type 562 items read");
        assertEquals(List.of(), broken, "mastery books the skill-book path could not apply");
    }

    /** Both new branches are reached only if the catalogue still sells the packages. */
    @Test
    void allSixPackagesAreStillOnSale() {
        Map<Integer, Data> byItemId = new HashMap<>();
        for (Data row : etc().getData("Commodity.img").getChildren()) {
            byItemId.put(DataTool.getIntConvert("ItemId", row), row);
        }
        for (int pkg : MAGE_PACKAGES) {
            Data row = byItemId.get(pkg);
            assertNotNull(row, "no Commodity row sells package " + pkg);
            assertEquals(1, DataTool.getIntConvert("OnSale", row, 0), "package " + pkg + " is not on sale");
        }
    }

    /** Guards the two constants above against drifting out of the handler. */
    @Test
    void bothNewTypesAreDispatched() throws IOException {
        Set<Integer> dispatched = dispatchedTypes();
        assertTrue(dispatched.contains(TRADE_COUPON), "no branch for trade coupons (type 553)");
        assertTrue(dispatched.contains(MASTERY_BOOK), "no branch for mastery books (type 562)");
    }
}
