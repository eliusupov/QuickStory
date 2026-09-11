package server;

import client.Character;
import client.Client;
import client.inventory.manipulator.InventoryManipulator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import tools.PacketCreator;
import tools.DatabaseConnection;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopPurchaseTest {

    @BeforeAll
    static void bootItemDataWithoutTheUnrelatedMonsterCardDatabaseCache() {
        try (MockedStatic<DatabaseConnection> db = Mockito.mockStatic(DatabaseConnection.class)) {
            db.when(DatabaseConnection::getConnection).thenThrow(new SQLException("no database in tests"));
            ItemInformationProvider.getInstance();
        }
    }

    private static Shop shopWith(List<ShopItem> items) throws Exception {
        Constructor<Shop> constructor = Shop.class.getDeclaredConstructor(int.class, int.class);
        constructor.setAccessible(true);
        Shop shop = constructor.newInstance(1052104, 1052104);
        Method addItem = Shop.class.getDeclaredMethod("addItem", ShopItem.class);
        addItem.setAccessible(true);
        for (ShopItem item : items) {
            addItem.invoke(shop, item);
        }
        return shop;
    }

    @Test
    void filteredRowsRetainNativeQuantityAndPricePurchase() throws Exception {
        ShopItem bow = new ShopItem((short) 1000, 2044500, 250_000, 0);
        ShopItem crossbow = new ShopItem((short) 1000, 2044600, 600_000, 0);
        Shop shop = shopWith(Shop.filterItemsByItemCategory(List.of(bow, crossbow), 20445));
        Client client = mock(Client.class);
        Character player = mock(Character.class);
        when(client.getPlayer()).thenReturn(player);
        when(player.getMeso()).thenReturn(1_000_000);

        try (MockedStatic<InventoryManipulator> inventory = Mockito.mockStatic(InventoryManipulator.class);
             MockedStatic<PacketCreator> packets = Mockito.mockStatic(PacketCreator.class)) {
            inventory.when(() -> InventoryManipulator.checkSpace(client, 2044500, (short) 3, ""))
                    .thenReturn(true);

            shop.buy(client, (short) 0, 2044500, (short) 3);

            inventory.verify(() -> InventoryManipulator.addById(client, 2044500, (short) 3, "", -1));
            verify(player).gainMeso(-750_000, false);
            packets.verify(() -> PacketCreator.shopTransaction((byte) 0));
        }
    }

    @Test
    void weaponFilterBindsARealFilteredShopToThePlayer() throws Exception {
        ShopItem bow = new ShopItem((short) 1000, 2044500, 250_000, 0);
        ShopItem crossbow = new ShopItem((short) 1000, 2044600, 600_000, 0);
        Shop original = shopWith(List.of(bow, crossbow));
        Client client = mock(Client.class);
        Character player = mock(Character.class);
        when(client.getPlayer()).thenReturn(player);

        try (MockedStatic<PacketCreator> packets = Mockito.mockStatic(PacketCreator.class)) {
            assertTrue(original.sendShopByItemCategory(client, 20445));
            packets.verify(() -> PacketCreator.getNPCShop(client, 1052104, List.of(bow)));
        }

        ArgumentCaptor<Shop> activeShop = ArgumentCaptor.forClass(Shop.class);
        verify(player).setShop(activeShop.capture());
        assertNotSame(original, activeShop.getValue());
    }

    @Test
    void insufficientMesosAndInventorySpaceNeverChargeOrAdd() throws Exception {
        Shop shop = shopWith(List.of(new ShopItem((short) 1000, 2044500, 250_000, 0)));
        Client client = mock(Client.class);
        Character player = mock(Character.class);
        when(client.getPlayer()).thenReturn(player);

        try (MockedStatic<InventoryManipulator> inventory = Mockito.mockStatic(InventoryManipulator.class);
             MockedStatic<PacketCreator> packets = Mockito.mockStatic(PacketCreator.class)) {
            when(player.getMeso()).thenReturn(249_999);
            shop.buy(client, (short) 0, 2044500, (short) 1);
            packets.verify(() -> PacketCreator.shopTransaction((byte) 2));
            inventory.verifyNoInteractions();

            when(player.getMeso()).thenReturn(250_000);
            inventory.when(() -> InventoryManipulator.checkSpace(client, 2044500, (short) 1, ""))
                    .thenReturn(false);
            shop.buy(client, (short) 0, 2044500, (short) 1);
            packets.verify(() -> PacketCreator.shopTransaction((byte) 3));
            inventory.verify(() -> InventoryManipulator.addById(client, 2044500, (short) 1, "", -1), never());
            verify(player, never()).gainMeso(Mockito.anyInt(), Mockito.anyBoolean());
        }
    }

    @Test
    void invalidFilteredSlotCannotReachPurchaseState() throws Exception {
        Shop shop = shopWith(List.of(new ShopItem((short) 1000, 2044500, 250_000, 0)));
        Client client = mock(Client.class);

        try (MockedStatic<InventoryManipulator> inventory = Mockito.mockStatic(InventoryManipulator.class);
             MockedStatic<PacketCreator> packets = Mockito.mockStatic(PacketCreator.class)) {
            shop.buy(client, (short) -1, 2044500, (short) 1);
            shop.buy(client, (short) 1, 2044500, (short) 1);
            inventory.verifyNoInteractions();
            packets.verifyNoInteractions();
            verify(client, never()).getPlayer();
        }
    }
}
