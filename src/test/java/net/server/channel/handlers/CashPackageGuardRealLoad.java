package net.server.channel.handlers;

import constants.id.ItemId;
import net.packet.InPacket;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import provider.Data;
import provider.DataProviderFactory;
import provider.DataTool;
import provider.wz.WZFiles;
import server.CashShop;
import server.CashShop.CashItem;
import server.CashShop.CashItemFactory;
import server.ItemInformationProvider;
import service.NoteService;
import testutil.HandlerTest;
import testutil.Packets;
import tools.DatabaseConnection;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the guard on cash-shop action {@code 0x1E} ("buy package").
 *
 * <p>Both buy actions share one handler and one {@code canBuy} check, but only {@code 0x03}
 * ("buy item") validated what it had been handed. {@code 0x1E} ran {@code cs.gainCash(...)} and
 * only then {@code CashItemFactory.getPackage(itemId)}, which walks {@code packages.get(itemId)} -
 * null for any SN that is not a package - so the for-each threw NPE with the NX already deducted.
 * Nothing was delivered and nothing rolled the deduction back: {@code gainCash} mutates the
 * in-memory {@code nxPrepaid} and {@code CashShop.save()} writes that field to {@code accounts}.
 * A client sending 0x1E with an ordinary item's SN therefore burned real NX for nothing.
 *
 * <p>{@code CashShop.loadGifts} already guarded the same call with {@code isPackage}, so the fix is
 * the sibling caller's own pattern applied to the one caller that skipped it.
 *
 * <pre>
 *   mvnw.cmd test -Dtest=CashPackageGuardRealLoad
 * </pre>
 *
 * <p><strong>Not a {@code *Test} class on purpose</strong>, same reason as its siblings:
 * {@link WZFiles#DIRECTORY} is {@code static final} and resolved once per JVM, and
 * {@code MobSkillFactoryTest} points {@code wz-path} at an empty {@code @TempDir}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CashPackageGuardRealLoad extends HandlerTest {

    private static final int ACTION_BUY_ITEM = 0x03;
    private static final int ACTION_BUY_PACKAGE = 0x1E;
    private static final int NX_PREPAID = 4;

    /**
     * An on-sale, priced, non-package row: SN 10002333 / item 5050005. Re-asserted to be all three
     * by {@link #theSnThisTestFiresIsOnSaleAndNotAPackage()} so the test cannot decay into
     * checking nothing if the catalogue moves. Deliberately not a pet or an equip - {@code toItem}
     * calls {@code Pet.createPet} / {@code getEquipById} for those, which want a database.
     */
    private static final int NON_PACKAGE_SN = 10002333;

    @Mock
    private CashShop cashShop;

    @Mock
    private NoteService noteService;

    private CashOperationHandler handler;

    /**
     * Both the catalogue load and the provider {@code canBuy} logs through read the database, and
     * both catch SQLException - it is the IllegalStateException from an uninitialised pool that
     * escapes. Same shim {@code MasteryBookJobMatchRealLoad} uses.
     */
    @BeforeAll
    static void loadTheCatalogueWithoutADatabase() {
        try (MockedStatic<DatabaseConnection> db = Mockito.mockStatic(DatabaseConnection.class)) {
            db.when(DatabaseConnection::getConnection).thenThrow(new SQLException("no database in tests"));
            CashItemFactory.loadAllCashItems();
            ItemInformationProvider.getInstance();
        }
        assertNotNull(CashItemFactory.getItem(NON_PACKAGE_SN), "Commodity.img did not load");
    }

    @BeforeEach
    void prepareHandler() {
        handler = new CashOperationHandler(noteService);
        when(client.tryacquireClient()).thenReturn(true);
        when(chr.getCashShop()).thenReturn(cashShop);
        when(cashShop.isOpened()).thenReturn(true);
        when(cashShop.getCash(anyInt())).thenReturn(Integer.MAX_VALUE);  // afford anything, so only the guard can refuse
    }

    private InPacket buyPacket(int action, int sn) {
        return Packets.buildInPacket(out -> {
            out.writeByte(action);
            out.writeByte(0);
            out.writeInt(NX_PREPAID);
            out.writeInt(sn);
        });
    }

    @Test
    void theSnThisTestFiresIsOnSaleAndNotAPackage() {
        CashItem cItem = CashItemFactory.getItem(NON_PACKAGE_SN);
        assertNotNull(cItem, "SN " + NON_PACKAGE_SN + " has no Commodity.img row");
        assertTrue(cItem.isOnSale(), "SN " + NON_PACKAGE_SN + " is not on sale, so canBuy would stop "
                + "0x1E first and this test would prove nothing");
        assertTrue(cItem.getPrice() > 0, "SN " + NON_PACKAGE_SN + " is free, so no NX could be lost");
        assertFalse(CashItemFactory.isPackage(cItem.getItemId()), "SN " + NON_PACKAGE_SN + " is a package after all");
    }

    /**
     * The regression. Before the guard this threw NPE out of {@code getPackage} <em>after</em>
     * {@code gainCash} had run; now the sale is refused and no cash is touched.
     */
    @Test
    void buyingANonPackageSnAsAPackageCostsNoCash() {
        handler.handlePacket(buyPacket(ACTION_BUY_PACKAGE, NON_PACKAGE_SN), client);

        verify(cashShop, never()).gainCash(anyInt(), any(CashItem.class), anyInt());
        verify(cashShop, never()).addToInventory(any());
        verify(client).enableCSActions();
        verify(client).releaseClient();
    }

    /** The guard is on the package action only - the same SN still sells the ordinary way. */
    @Test
    void theSameSnStillSellsThroughTheItemAction() {
        when(chr.getLevel()).thenReturn(200);

        handler.handlePacket(buyPacket(ACTION_BUY_ITEM, NON_PACKAGE_SN), client);

        verify(cashShop).gainCash(anyInt(), any(CashItem.class), anyInt());
        verify(cashShop).addToInventory(any());
    }

    /** Every package a real 0x1E can name still resolves, so the guard refuses nothing genuine. */
    @Test
    void everyOnSalePackageRowStillPassesTheGuard() {
        Data commodity = DataProviderFactory.getDataProvider(WZFiles.ETC).getData("Commodity.img");
        int checked = 0;
        for (Data row : commodity.getChildren()) {
            int itemId = DataTool.getIntConvert("ItemId", row);
            if (DataTool.getIntConvert("OnSale", row, 0) != 1 || !ItemId.isCashPackage(itemId)) {
                continue;
            }
            assertTrue(CashItemFactory.isPackage(itemId), "on-sale package item " + itemId
                    + " has no CashPackage.img entry, so the guard would refuse a sale the shop offers");
            checked++;
        }
        assertTrue(checked > 0, "no on-sale cash packages found - the loop asserted nothing");
    }
}
