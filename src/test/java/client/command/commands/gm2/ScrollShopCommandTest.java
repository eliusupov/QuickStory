package client.command.commands.gm2;

import client.Character;
import client.Client;
import client.command.CommandsExecutor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import server.ItemInformationProvider;
import server.Shop;
import server.ShopFactory;
import server.ShopItem;
import tools.DatabaseConnection;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScrollShopCommandTest {
    private static final Pattern RETAINED_ROW = Pattern.compile("/\\* (\\d+),");

    @BeforeAll
    static void bootItemDataWithoutTheUnrelatedMonsterCardDatabaseCache() {
        try (MockedStatic<DatabaseConnection> db = Mockito.mockStatic(DatabaseConnection.class)) {
            db.when(DatabaseConnection::getConnection).thenThrow(new SQLException("no database in tests"));
            ItemInformationProvider.getInstance();
        }
    }

    @Test
    void canonicalCategoriesAndAliasesResolveCaseInsensitively() {
        Map<String, String> expected = Map.ofEntries(
                Map.entry("LUK", "LUK"), Map.entry("str", "STR"), Map.entry("dex", "DEX"),
                Map.entry("int", "INT"), Map.entry("watt", "PAD"), Map.entry("weapon-att", "PAD"),
                Map.entry("weapon-attack", "PAD"), Map.entry("MATT", "MAD"),
                Map.entry("magic-att", "MAD"), Map.entry("magic-attack", "MAD"),
                Map.entry("hp", "MHP"), Map.entry("mp", "MMP"), Map.entry("acc", "ACC"),
                Map.entry("accuracy", "ACC"), Map.entry("avoid", "EVA"),
                Map.entry("avoidability", "EVA"), Map.entry("speed", "Speed"),
                Map.entry("jump", "Jump"), Map.entry("wdef", "PDD"),
                Map.entry("weapon-defense", "PDD"), Map.entry("mdef", "MDD"),
                Map.entry("magic-defense", "MDD"), Map.entry("spikes", "preventslip"),
                Map.entry("TRACTION", "preventslip"));

        expected.forEach((alias, stat) -> assertEquals(stat, ScrollShopCommand.effectStat(alias)));
        assertNull(ScrollShopCommand.effectStat("helmet"));
    }

    @Test
    void weaponTargetsAndPracticalAliasesResolveWithoutAmbiguousCollisions() {
        Map<String, Integer> expected = Map.ofEntries(
                Map.entry("one-handed-sword", 20430), Map.entry("1H-SWORD", 20430),
                Map.entry("one-handed-axe", 20431), Map.entry("1h-axe", 20431),
                Map.entry("one-handed-blunt", 20432), Map.entry("one-handed-bw", 20432),
                Map.entry("1h-blunt", 20432), Map.entry("1h-bw", 20432),
                Map.entry("dagger", 20433), Map.entry("wand", 20437), Map.entry("staff", 20438),
                Map.entry("two-handed-sword", 20440), Map.entry("2h-sword", 20440),
                Map.entry("two-handed-axe", 20441), Map.entry("2h-axe", 20441),
                Map.entry("two-handed-blunt", 20442), Map.entry("two-handed-bw", 20442),
                Map.entry("2h-blunt", 20442), Map.entry("2h-bw", 20442),
                Map.entry("spear", 20443), Map.entry("polearm", 20444), Map.entry("pole-arm", 20444),
                Map.entry("bow", 20445), Map.entry("crossbow", 20446), Map.entry("XBOW", 20446),
                Map.entry("claw", 20447), Map.entry("knuckle", 20448), Map.entry("knuckler", 20448),
                Map.entry("gun", 20449));

        expected.forEach((alias, family) -> assertEquals(family, ScrollShopCommand.weaponTarget(alias)));
        for (String ambiguous : new String[]{"sword", "axe", "blunt", "weapon", "cross"}) {
            assertNull(ScrollShopCommand.weaponTarget(ambiguous));
        }
    }

    @Test
    void invalidCategoryPrintsHelpWithoutOpeningAnyShop() {
        Client client = mock(Client.class);
        Character player = mock(Character.class);
        when(client.getPlayer()).thenReturn(player);

        try (MockedStatic<ShopFactory> shops = Mockito.mockStatic(ShopFactory.class)) {
            new ScrollShopCommand().execute(client, new String[]{"helmet"});

            shops.verifyNoInteractions();
        }
        verify(player).dropMessage(5, "Usage: !scrollshop [category]");
        verify(player).dropMessage(5, "Effect categories: " + ScrollShopCommand.EFFECT_CATEGORIES);
        verify(player).dropMessage(5, "Weapon categories: " + ScrollShopCommand.WEAPON_CATEGORIES);
        verify(player).dropMessage(5, "Aliases: " + ScrollShopCommand.ALIASES);
    }

    @Test
    void noCategoryOpensTheFullTulcusShopAndLukUsesTheNormalFilteredShop() {
        Client client = mock(Client.class);
        ShopFactory factory = mock(ShopFactory.class);
        Shop shop = mock(Shop.class);
        when(factory.getShop(ScrollShopCommand.TULCUS_SHOP_ID)).thenReturn(shop);
        when(shop.sendShopByItemStat(client, "LUK")).thenReturn(true);

        try (MockedStatic<ShopFactory> shops = Mockito.mockStatic(ShopFactory.class)) {
            shops.when(ShopFactory::getInstance).thenReturn(factory);

            ScrollShopCommand command = new ScrollShopCommand();
            command.execute(client, new String[0]);
            command.execute(client, new String[]{"LuK"});
        }

        verify(shop).sendShop(client);
        verify(shop).sendShopByItemStat(client, "LUK");
        verify(shop, never()).sendShopByItemStat(client, "PDD");
    }

    @Test
    void weaponCategoryUsesTheNormalFilteredShop() {
        Client client = mock(Client.class);
        ShopFactory factory = mock(ShopFactory.class);
        Shop shop = mock(Shop.class);
        when(factory.getShop(ScrollShopCommand.TULCUS_SHOP_ID)).thenReturn(shop);
        when(shop.sendShopByItemCategory(client, 20446)).thenReturn(true);

        try (MockedStatic<ShopFactory> shops = Mockito.mockStatic(ShopFactory.class)) {
            shops.when(ShopFactory::getInstance).thenReturn(factory);
            new ScrollShopCommand().execute(client, new String[]{"XBoW"});
        }

        verify(shop).sendShopByItemCategory(client, 20446);
        verify(shop, never()).sendShop(client);
    }

    @Test
    void realCommandParserRoutesBothPrefixesAndPreservesNoArgStrAndInvalidBehavior() throws Exception {
        Client client = mock(Client.class);
        Character player = mock(Character.class);
        ShopFactory factory = mock(ShopFactory.class);
        Shop shop = tulcusShop();
        when(client.getPlayer()).thenReturn(player);
        when(client.tryacquireClient()).thenReturn(true);
        when(player.isGM()).thenReturn(true);
        when(player.gmLevel()).thenReturn(2);
        when(factory.getShop(ScrollShopCommand.TULCUS_SHOP_ID)).thenReturn(shop);

        try (MockedStatic<ShopFactory> shops = Mockito.mockStatic(ShopFactory.class)) {
            shops.when(ShopFactory::getInstance).thenReturn(factory);
            CommandsExecutor executor = CommandsExecutor.getInstance();

            assertTrue(CommandsExecutor.isCommand(client, "@scrollshop"));
            executor.handle(client, "@scrollshop   ");
            verify(player).setShop(shop);
            assertEquals(375, items(shop).size());
            verify(client).releaseClient();

            Mockito.clearInvocations(client, player);
            assertTrue(CommandsExecutor.isCommand(client, "!scrollshop   STR"));
            executor.handle(client, "!scrollshop   STR");
            ArgumentCaptor<Shop> openedShop = ArgumentCaptor.forClass(Shop.class);
            verify(player).setShop(openedShop.capture());
            Shop filtered = openedShop.getValue();
            List<ShopItem> strItems = items(filtered);
            assertEquals(72, strItems.size());
            ItemInformationProvider ii = ItemInformationProvider.getInstance();
            assertTrue(strItems.stream().allMatch(item -> ii.getEquipStats(item.getItemId()).getOrDefault("STR", 0) > 0));
            verify(client).releaseClient();

            Mockito.clearInvocations(client, player);
            executor.handle(client, "@scrollshop weird");
            verify(player, never()).setShop(Mockito.any());
            verify(player).dropMessage(5, "Usage: !scrollshop [category]");
            shops.verify(ShopFactory::getInstance, times(2));
        }
        verify(client).releaseClient();
    }

    private static Shop tulcusShop() throws Exception {
        Matcher rows = RETAINED_ROW.matcher(Files.readString(Path.of(
                "src", "main", "resources", "db", "data", "183-tulcus-regular-scroll-resort.sql")));
        Constructor<Shop> constructor = Shop.class.getDeclaredConstructor(int.class, int.class);
        constructor.setAccessible(true);
        Method addItem = Shop.class.getDeclaredMethod("addItem", ShopItem.class);
        addItem.setAccessible(true);
        Shop shop = constructor.newInstance(ScrollShopCommand.TULCUS_SHOP_ID, ScrollShopCommand.TULCUS_SHOP_ID);
        while (rows.find()) {
            addItem.invoke(shop, new ShopItem((short) 1000, Integer.parseInt(rows.group(1)), 1, 0));
        }
        return shop;
    }

    @SuppressWarnings("unchecked")
    private static List<ShopItem> items(Shop shop) throws Exception {
        Field items = Shop.class.getDeclaredField("items");
        items.setAccessible(true);
        return new ArrayList<>((List<ShopItem>) items.get(shop));
    }
}
