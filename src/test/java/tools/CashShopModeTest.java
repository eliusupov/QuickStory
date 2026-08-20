package tools;

import client.Character;
import client.inventory.Item;
import constants.net.ServerConstants;
import net.packet.ByteBufOutPacket;
import net.packet.Packet;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import server.CashShop;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Pins the CASHSHOP_OPERATION mode byte ({@code CCashShop::OnCashItemResult}) at both versions.
 *
 * <p>v84 inserted three arms ahead of the locker family, so every mode this server sends is +3
 * there. The live symptom that found it: entering the Cash Shop drew the shop, then immediately
 * showed "Due to an unknown error, the request for Cash Shop has failed" with nothing at all in the
 * server log. {@code showWishList(false)} writes 0x4F, which at v84 is {@code LOAD_LOCKER_FAILED}
 * (@0x47c71a, body = one NoticeFailReason byte) - the client read the first wish-list int's low
 * byte, 0 for an empty wish list, as reason 0x00 = "Due to an unknown error, failed".
 *
 * <p>Evidence: the v84 client's own dispatcher arms in
 * {@code D:\games\MSv84\opcodes\ida_export_gms_v84.json}. All 21 modes this server writes match a
 * v84 arm by name AND by decode shape at +3. See {@code PacketCreator.cashShopMode}.
 */
class CashShopModeTest {

    @Test
    void cashShopEntryPacketsCarryTheVersionsOwnMode() {
        // Sent by EnterCashShopHandler right after openCashShop. The 0x4F one is the reported bug.
        assertMode(0x50, PacketCreator.showGifts(List.of()), "showGifts");
        assertMode(0x52, PacketCreator.showWishList(wishlistChr(), false), "showWishList load");
        assertMode(0x58, PacketCreator.showWishList(wishlistChr(), true), "showWishList update");
    }

    @Test
    void cashShopResultPacketsCarryTheVersionsOwnMode() {
        assertMode(0x5F, PacketCreator.showCashShopMessage((byte) 0), "notice");
        assertMode(0x5C, PacketCreator.showCouponRedeemedItems(1, 0, 0, List.of(), List.of()), "coupon");
        assertMode(0x63, PacketCreator.showBoughtInventorySlots(1, (short) 24), "inventory slots");
        assertMode(0x65, PacketCreator.showBoughtStorageSlots((short) 4), "storage slots");
        assertMode(0x67, PacketCreator.showBoughtCharacterSlot((short) 4), "character slots");
        assertMode(0x6F, PacketCreator.deleteCashItem(item()), "destroy");
        assertMode(0x88, PacketCreator.refundCashItem(item(), 0), "rebate");
        assertMode(0x8C, PacketCreator.showBoughtCashPackage(List.of(), 1), "buy package");
        assertMode(0x90, PacketCreator.showBoughtQuestItem(4001126), "buy normal");
    }

    @Test
    void cashEquipWithdrawalCarriesV84DurabilityField() {
        ByteBufOutPacket trailer = new ByteBufOutPacket();
        PacketCreator.writeCashEquipExtendedTrailer(trailer);
        byte[] expectedTrailer = {
                0x40, 0x40, 0x40, 0x40, 0x40, 0x40,
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
                0x40, 0x40, 0x40, 0x40
        };

        assertArrayEquals(expectedTrailer, trailer.getBytes());
    }

    /**
     * CASHSHOP_CASH_ITEM_GACHAPON_RESULT is a different handler -
     * {@code CCashShop::OnCashItemGachaponResult} @0x47f8fc, whose v84 export names SUCCESS = 238
     * (0xEE) and FAILED = 237 (0xED). That is +9, not cashShopMode's +3, so these two must never
     * route through cashShopMode (it would emit 0xE7/0xE8 and the client would silently ignore the
     * packet - Cash Shop Surprise doing nothing, with no error at all).
     */
    @Test
    void gachaponResultPacketsCarryTheVersionsOwnMode() {
        assertMode(0xED, PacketCreator.onCashItemGachaponOpenFailed(), "gachapon failed");
        assertMode(0xEE,
                PacketCreator.onCashGachaponOpenSuccess(1, 0L, 1, item(), 4001126, 1, false),
                "gachapon success");
    }

    // ------------------------------------------------------------------

    private static void assertMode(int expected, Packet p, String what) {
        assertEquals(expected, p.getBytes()[2] & 0xFF,
                what + ": wrong CASHSHOP_OPERATION mode at VERSION " + ServerConstants.VERSION);
    }

    private static Character wishlistChr() {
        CashShop cs = Mockito.mock(CashShop.class);
        when(cs.getWishList()).thenReturn(List.of());
        Character chr = Mockito.mock(Character.class);
        when(chr.getCashShop()).thenReturn(cs);
        return chr;
    }

    private static Item item() {
        return new Item(5000000, (short) 0, (short) 1);
    }
}
