package net.server.channel.handlers;

import client.Character;
import client.Client;
import constants.id.ItemId;
import net.packet.InPacket;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class CancelRateCouponTest {
    @Test
    void clickingCouponIconRestoresTheBuffMatchingTheActiveRate() {
        InPacket packet = mock(InPacket.class);
        when(packet.readInt()).thenReturn(-ItemId.EXP_COUPON_2X_4H);
        Client client = mock(Client.class);
        Character player = mock(Character.class);
        when(client.getPlayer()).thenReturn(player);

        new CancelItemEffectHandler().handlePacket(packet, client);

        verify(player).updateCouponRates();
        verify(player, never()).cancelEffect(ItemId.EXP_COUPON_2X_4H);
    }
}
