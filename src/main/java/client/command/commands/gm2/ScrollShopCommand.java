/*
    This file is part of the HeavenMS MapleStory Server, commands OdinMS-based
    Copyleft (L) 2016 - 2019 RonanLana

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation version 3 as published by
    the Free Software Foundation. You may not use, modify or distribute
    this program under any other version of the GNU Affero General Public
    License.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

/*

/*
   @Author: Arthur L - Refactored command content into modules
*/
package client.command.commands.gm2;

import client.Client;
import client.command.Command;
import server.Shop;
import server.ShopFactory;

import java.util.Locale;
import java.util.Map;

public class ScrollShopCommand extends Command {
    static final int TULCUS_SHOP_ID = 1052104;
    static final String EFFECT_CATEGORIES = "luk, str, dex, int, weapon-attack, magic-attack, hp, mp, "
            + "accuracy, avoidability, speed, jump, weapon-defense, magic-defense, spikes";
    static final String WEAPON_CATEGORIES = "one-handed-sword, one-handed-axe, one-handed-blunt, dagger, "
            + "wand, staff, two-handed-sword, two-handed-axe, two-handed-blunt, spear, polearm, bow, "
            + "crossbow, claw, knuckle, gun";
    static final String ALIASES = "watt/weapon-att, matt/magic-att, acc, avoid, wdef, mdef, traction, "
            + "1h-sword/1h-axe/1h-bw, 2h-sword/2h-axe/2h-bw, pole-arm, xbow, knuckler";
    private static final Map<String, String> EFFECT_STATS = Map.ofEntries(
            Map.entry("luk", "LUK"),
            Map.entry("str", "STR"),
            Map.entry("dex", "DEX"),
            Map.entry("int", "INT"),
            Map.entry("weapon-attack", "PAD"),
            Map.entry("watt", "PAD"),
            Map.entry("weapon-att", "PAD"),
            Map.entry("magic-attack", "MAD"),
            Map.entry("matt", "MAD"),
            Map.entry("magic-att", "MAD"),
            Map.entry("hp", "MHP"),
            Map.entry("mp", "MMP"),
            Map.entry("accuracy", "ACC"),
            Map.entry("acc", "ACC"),
            Map.entry("avoidability", "EVA"),
            Map.entry("avoid", "EVA"),
            Map.entry("speed", "Speed"),
            Map.entry("jump", "Jump"),
            Map.entry("weapon-defense", "PDD"),
            Map.entry("wdef", "PDD"),
            Map.entry("magic-defense", "MDD"),
            Map.entry("mdef", "MDD"),
            Map.entry("spikes", "preventslip"),
            Map.entry("traction", "preventslip"));
    private static final Map<String, Integer> WEAPON_TARGETS = Map.ofEntries(
            Map.entry("one-handed-sword", 20430), Map.entry("1h-sword", 20430),
            Map.entry("one-handed-axe", 20431), Map.entry("1h-axe", 20431),
            Map.entry("one-handed-blunt", 20432), Map.entry("one-handed-bw", 20432),
            Map.entry("1h-blunt", 20432), Map.entry("1h-bw", 20432),
            Map.entry("dagger", 20433),
            Map.entry("wand", 20437),
            Map.entry("staff", 20438),
            Map.entry("two-handed-sword", 20440), Map.entry("2h-sword", 20440),
            Map.entry("two-handed-axe", 20441), Map.entry("2h-axe", 20441),
            Map.entry("two-handed-blunt", 20442), Map.entry("two-handed-bw", 20442),
            Map.entry("2h-blunt", 20442), Map.entry("2h-bw", 20442),
            Map.entry("spear", 20443),
            Map.entry("polearm", 20444), Map.entry("pole-arm", 20444),
            Map.entry("bow", 20445),
            Map.entry("crossbow", 20446), Map.entry("xbow", 20446),
            Map.entry("claw", 20447),
            Map.entry("knuckle", 20448), Map.entry("knuckler", 20448),
            Map.entry("gun", 20449));

    {
        setDescription("Usage: !scrollshop [category] (blank opens all). Effects: " + EFFECT_CATEGORIES
                + ". Weapons: " + WEAPON_CATEGORIES + ".");
    }

    @Override
    public void execute(Client c, String[] params) {
        // Tulcus, the Scroll Seller (Kerning City). NPC 1052104 is his own shop id --
        // String.wz/Npc.img 1052104 "Tulcus"/"Scroll Seller", shops row (1052104, 1052104).
        if (params.length > 1) {
            sendUsage(c);
            return;
        }

        String stat = params.length == 0 ? null : effectStat(params[0]);
        Integer weaponTarget = params.length == 0 ? null : weaponTarget(params[0]);
        if (params.length == 1 && stat == null && weaponTarget == null) {
            sendUsage(c);
            return;
        }

        Shop shop = ShopFactory.getInstance().getShop(TULCUS_SHOP_ID);
        if (shop == null) {
            c.getPlayer().dropMessage(5, "Tulcus' shop is unavailable.");
        } else if (stat == null) {
            if (weaponTarget == null) {
                shop.sendShop(c);
            } else if (!shop.sendShopByItemCategory(c, weaponTarget)) {
                c.getPlayer().dropMessage(5, "Tulcus has no current stock in that category.");
            }
        } else if (!shop.sendShopByItemStat(c, stat)) {
            c.getPlayer().dropMessage(5, "Tulcus has no current stock in that category.");
        }
    }

    static String effectStat(String category) {
        return category == null ? null : EFFECT_STATS.get(normalize(category));
    }

    static Integer weaponTarget(String category) {
        return category == null ? null : WEAPON_TARGETS.get(normalize(category));
    }

    private static String normalize(String category) {
        return category.trim().toLowerCase(Locale.ROOT);
    }

    private static void sendUsage(Client c) {
        c.getPlayer().dropMessage(5, "Usage: !scrollshop [category]");
        c.getPlayer().dropMessage(5, "Effect categories: " + EFFECT_CATEGORIES);
        c.getPlayer().dropMessage(5, "Weapon categories: " + WEAPON_CATEGORIES);
        c.getPlayer().dropMessage(5, "Aliases: " + ALIASES);
    }
}
