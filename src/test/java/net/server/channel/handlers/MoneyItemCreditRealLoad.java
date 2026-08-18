package net.server.channel.handlers;

import client.Character;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import server.CashShop;
import server.ItemInformationProvider;
import tools.DatabaseConnection;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the 520xxxx branch of {@code UseCashItemHandler}.
 *
 * <p>v84 added two coupons of that type, 5200009 and 5200010, that carry {@code info/maplepoint}
 * and no {@code info/meso}. The branch called {@code gainMeso(ii.getMeso(itemId), ...)} on them,
 * and {@code getMeso} returns <strong>-1</strong> when the item has no {@code info/meso} node - so
 * using either coupon consumed it, charged the player one meso and credited nothing.
 *
 * <pre>
 *   mvnw.cmd test -Dtest=MoneyItemCreditRealLoad
 * </pre>
 *
 * <p><strong>Not a {@code *Test} class on purpose</strong>: it loads the real WZ tree, and
 * {@code MobSkillFactoryTest} points {@code wz-path} at an empty {@code @TempDir} for the JVM.
 */
class MoneyItemCreditRealLoad {

    private static final int COUPON_1M = 5200009;
    private static final int COUPON_10K = 5200010;
    private static final int SACK_1M = 5200000;
    private static final int SACK_5M = 5200001;
    private static final int SACK_10M = 5200002;
    /** No such item: a 520xxxx with neither node, routed through the same branch. */
    private static final int NOT_AN_ITEM = 5200003;

    @BeforeAll
    static void bootTheProviderWithoutADatabase() {
        try (MockedStatic<DatabaseConnection> db = Mockito.mockStatic(DatabaseConnection.class)) {
            db.when(DatabaseConnection::getConnection).thenThrow(new SQLException("no database in tests"));
            ItemInformationProvider.getInstance();
        }
    }

    private static ItemInformationProvider ii() {
        return ItemInformationProvider.getInstance();
    }

    /** The amounts come from the WZ leaves, not from a literal in Java. */
    @Test
    void theTwoCouponsCarryTheirMaplePointValueInTheWz() {
        assertEquals(1000000, ii().getMaplePoint(COUPON_1M), "Item.wz/Cash/0520.img 05200009 info/maplepoint");
        assertEquals(10000, ii().getMaplePoint(COUPON_10K), "Item.wz/Cash/0520.img 05200010 info/maplepoint");
    }

    /** The three meso sacks have no maplepoint node, and the coupons have no meso node. */
    @Test
    void theNodesDoNotOverlap() {
        assertEquals(0, ii().getMaplePoint(SACK_1M));
        assertEquals(0, ii().getMaplePoint(SACK_5M));
        assertEquals(0, ii().getMaplePoint(SACK_10M));
        assertEquals(-1, ii().getMeso(COUPON_1M), "getMeso still returns -1 - that is the -1 being guarded");
        assertEquals(-1, ii().getMeso(COUPON_10K));
        assertEquals(0, ii().getMaplePoint(NOT_AN_ITEM), "unknown item must not throw");
    }

    @Test
    void eachCouponCreditsItsMaplePointsToCurrencyTwoAndChargesNoMeso() {
        for (int[] each : new int[][]{{COUPON_1M, 1000000}, {COUPON_10K, 10000}}) {
            Character player = mock(Character.class);
            CashShop shop = mock(CashShop.class);
            when(player.getCashShop()).thenReturn(shop);

            UseCashItemHandler.creditMoneyItem(player, each[0]);

            verify(shop, times(1)).gainCash(CashShop.MAPLE_POINT, each[1]);
            verify(shop, never()).gainCash(CashShop.NX_CREDIT, each[1]);
            verify(player, never()).gainMeso(anyInt());
            verify(player, never()).gainMeso(anyInt(), anyBoolean());
            verify(player, never()).gainMeso(anyInt(), anyBoolean(), anyBoolean(), anyBoolean());
        }
    }

    @Test
    void theThreeMesoSacksAreUnaffected() {
        for (int[] each : new int[][]{{SACK_1M, 1000000}, {SACK_5M, 5000000}, {SACK_10M, 10000000}}) {
            Character player = mock(Character.class);
            CashShop shop = mock(CashShop.class);
            when(player.getCashShop()).thenReturn(shop);

            UseCashItemHandler.creditMoneyItem(player, each[0]);

            verify(player, times(1)).gainMeso(each[1], true, false, true);
            verify(shop, never()).gainCash(anyInt(), anyInt());
        }
    }

    @Test
    void anItemWithNeitherNodeCreditsNothingAndChargesNothing() {
        Character player = mock(Character.class);
        CashShop shop = mock(CashShop.class);
        when(player.getCashShop()).thenReturn(shop);

        UseCashItemHandler.creditMoneyItem(player, NOT_AN_ITEM);

        verify(player, never()).gainMeso(anyInt(), anyBoolean(), anyBoolean(), anyBoolean());
        verify(shop, never()).gainCash(anyInt(), anyInt());
    }
}
