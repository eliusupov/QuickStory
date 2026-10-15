package client;

import constants.id.ItemId;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class CharacterRateCouponTest {
    @Test
    @SuppressWarnings("unchecked")
    void doubleCouponsMultiplyExistingExpAndDropRates() throws Exception {
        Character player = Character.getDefault(mock(Client.class));

        Field expRate = Character.class.getDeclaredField("expRate");
        expRate.setAccessible(true);
        expRate.setFloat(player, 8f);
        Field dropRate = Character.class.getDeclaredField("dropRate");
        dropRate.setAccessible(true);
        dropRate.setInt(player, 8);

        Field activeCoupons = Character.class.getDeclaredField("activeCoupons");
        activeCoupons.setAccessible(true);
        Map<Integer, Integer> owned = (Map<Integer, Integer>) activeCoupons.get(player);
        owned.put(ItemId.EXP_COUPON_2X_4H, 1);
        owned.put(ItemId.DROP_COUPON_2X_4H, 1);

        Field activeCouponRates = Character.class.getDeclaredField("activeCouponRates");
        activeCouponRates.setAccessible(true);
        Map<Integer, Integer> multipliers = (Map<Integer, Integer>) activeCouponRates.get(player);
        multipliers.put(ItemId.EXP_COUPON_2X_4H, 2);
        multipliers.put(ItemId.DROP_COUPON_2X_4H, 2);

        Method activate = Character.class.getDeclaredMethod("activateCouponsEffects");
        activate.setAccessible(true);
        activate.invoke(player);

        assertEquals(16f, player.getExpRate());
        assertEquals(16, player.getDropRate());
    }
}
