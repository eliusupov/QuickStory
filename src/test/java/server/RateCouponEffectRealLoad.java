package server;

import client.BuffStat;
import constants.id.ItemId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import tools.DatabaseConnection;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

class RateCouponEffectRealLoad {
    @BeforeAll
    static void loadItemDataWithoutDatabase() {
        try (MockedStatic<DatabaseConnection> db = mockStatic(DatabaseConnection.class)) {
            db.when(DatabaseConnection::getConnection).thenThrow(new SQLException("no database in tests"));
            ItemInformationProvider.getInstance();
        }
    }

    @Test
    void purchasedCouponsProvideVisibleBuffStats() {
        ItemInformationProvider items = ItemInformationProvider.getInstance();
        assertTrue(items.getItemEffect(ItemId.EXP_COUPON_2X_4H).getStatups().stream()
                .anyMatch(stat -> stat.getLeft() == BuffStat.COUPON_EXP3));
        assertTrue(items.getItemEffect(ItemId.DROP_COUPON_2X_4H).getStatups().stream()
                .anyMatch(stat -> stat.getLeft() == BuffStat.COUPON_DRP3));
    }
}
