package net.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CouponWindowTest {
    @Test
    void scheduledWindowsLastOneQuarterAsLong() {
        assertTrue(Server.isCouponActiveAtMinute(7, 11, 7 * 60 + 59));
        assertFalse(Server.isCouponActiveAtMinute(7, 11, 8 * 60));
        assertTrue(Server.isCouponActiveAtMinute(18, 20, 18 * 60 + 29));
        assertFalse(Server.isCouponActiveAtMinute(18, 20, 18 * 60 + 30));
    }

    @Test
    void allDayPurchaseCouponsRemainActiveUntilTheItemExpires() {
        assertTrue(Server.isCouponActiveAtMinute(0, 24, 9 * 60 + 41));
        assertTrue(Server.isCouponActiveAtMinute(0, 24, 23 * 60 + 59));
    }
}
