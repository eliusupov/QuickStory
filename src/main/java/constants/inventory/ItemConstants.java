/*
	This file is part of the OdinMS Maple Story Server
    Copyright (C) 2008 Patrick Huy <patrick.huy@frz.cc>
		       Matthias Butz <matze@odinms.de>
		       Jan Christian Meyer <vimes@odinms.de>

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
package constants.inventory;

import client.inventory.InventoryType;
import config.YamlConfig;
import constants.id.ItemId;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;

/**
 * @author Jay Estrella
 * @author Ronan
 */
public final class ItemConstants {
    protected static Map<Integer, InventoryType> inventoryTypeCache = new HashMap<>();

    public final static short LOCK = 0x01;
    public final static short SPIKES = 0x02;
    public final static short KARMA_USE = 0x02;
    public final static short COLD = 0x04;
    public final static short UNTRADEABLE = 0x08;
    public final static short KARMA_EQP = 0x10;
    public final static short SANDBOX = 0x40;             // let 0x40 until it's proven something uses this
    public final static short PET_COME = 0x80;
    public final static short ACCOUNT_SHARING = 0x100;
    public final static short MERGE_UNTRADEABLE = 0x200;

    public final static boolean EXPIRING_ITEMS = true;
    public final static Set<Integer> permanentItemids = new HashSet<>();

    public final static Set<Integer> ITEMS_TO_FILTER_OUT = new HashSet<>(Arrays.asList(
        2040000, // Scroll for Helmet for DEF
        2040001, // Scroll for Helmet for DEF
        2040002, // Scroll for Helmet for DEF
        2040006, // Scroll for Helmet for DEF
        2040019, // Scroll for Helmet for DEF
        2040020, // Scroll for Helmet for DEF
        2040310, // Scroll for Earring for DEF
        2040311, // Scroll for Earring for DEF
        2040312, // Scroll for Earring for DEF
        2040400, // Scroll for Topwear for DEF
        2040401, // Scroll for Topwear for DEF
        2040402, // Scroll for Topwear for DEF
        2040403, // Scroll for Topwear for DEF
        2040415, // Scroll for Topwear for DEF
        2040416, // Scroll for Topwear for DEF
        2040503, // Scroll for Overall Armor for DEF
        2040504, // Scroll for Overall Armor for DEF
        2040505, // Scroll for Overall Armor for DEF
        2040507, // Scroll for Overall Armor for DEF
        2040600, // Scroll for Bottomwear for DEF
        2040601, // Scroll for Bottomwear for DEF
        2040602, // Scroll for Bottomwear for DEF
        2040603, // Scroll for Bottomwear for DEF
        2040615, // Scroll for Bottomwear for DEF
        2040616, // Scroll for Bottomwear for DEF
        2040900, // Scroll for Shield for DEF
        2040901, // Scroll for Shield for DEF
        2040902, // Scroll for Shield for DEF
        2040903, // Scroll for Shield for DEF
        2040910, // Scroll for Shield for DEF
        2040911, // Scroll for Shield for DEF
        2041042, // Scroll for Cape for Magic DEF
        2041043, // Scroll for Cape for Magic DEF
        2041044, // Scroll for Cape for Weapon DEF
        2041045, // Scroll for Cape for Weapon DEF
        2040041, // Scroll for Helmet for DEF 100%
        2040936, // Scroll for Shield for DEF 100%
        2040630, // Scroll for Bottomwear for DEF 100%
        2040539, // Scroll for Overall Armor for DEF 100%
        2040430, // Scroll for Topwear for DEF 100%
        2041066, // Scroll for Cape for Magic DEF 100%
        2041067, // Scroll for Cape for Weapon DEF 100%
        2040943, // Scroll for Shield for DEF 50%
        2040629, // Scroll for Bottomwear for DEF 50%
        2040543, // Scroll for Overall Armor for DEF 50%
        2040429, // Scroll for Topwear for DEF 50%
        2040045, // Scroll for Helmet for DEF 50%
        2040003, // Scroll for Helmet for HP
        2040004, // Scroll for Helmet for HP
        2040005, // Scroll for Helmet for HP
        2040007, // Scroll for Helmet for HP
        2040010, // Scroll for Helmet for HP
        2040021, // Scroll for Helmet for MaxHP
        2040022, // Scroll for Helmet for MaxHP
        2040100, // Scroll for Face Accessory for HP
        2040101, // Scroll for Face Accessory for HP
        2040102, // Scroll for Face Accessory for HP
        2040324, // Scroll for Earring for HP 100%
        2040325, // Scroll for Earring for HP 70%
        2040326, // Scroll for Earring for HP 60%
        2040327, // Scroll for Earring for HP 30%
        2040328, // Scroll for Earring for HP 10%
        2040420, // Scroll for Topwear for HP 100%
        2040421, // Scroll for Topwear for HP 60%
        2040422, // Scroll for Topwear for HP 10%
        2040620, // Scroll for Bottomwear for HP 100%
        2040621, // Scroll for Bottomwear for HP 60%
        2040622, // Scroll for Bottomwear for HP 10%
        2040823, // Scroll for Gloves for HP 100%
        2040824, // Scroll for Gloves for HP 60%
        2040825, // Scroll for Gloves for HP 10%
        2040926, // Scroll for Shield for HP 100%
        2040927, // Scroll for Shield for HP 60%
        2040928, // Scroll for Shield for HP 10%
        2041005, // Scroll for Cape for Weapon Def.
        2041006, // Scroll for Cape for HP
        2041007, // Scroll for Cape for HP
        2041008, // Scroll for Cape for HP
        2041025, // Scroll for Cape for Weapon Def.
        2041046, // Scroll for Cape for MaxHP
        2041047, // Scroll for Cape for MaxHP
        2040042, // Scroll for Helmet for HP 100%
        2040046, // Scroll for Helmet for HP 50%
        2040939, // Scroll for Shield for HP 65%
        2040940, // Scroll for Shield for HP 15%
        2040831, // Scroll for Gloves for HP 65%
        2040832, // Scroll for Gloves for HP 15%
        2040633, // Scroll for Bottomwear for HP 65%
        2040634, // Scroll for Bottomwear for HP 15%
        2040433, // Scroll for Topwear for HP 65%
        2040434, // Scroll for Topwear for HP 15%
        2040339, // Scroll for Earring for HP 65%
        2040340, // Scroll for Earring for HP 15%
        2041002, // Scroll for Cape for Magic Def.
        2041009, // Scroll for Cape for MP
        2041010, // Scroll for Cape for MP
        2041011, // Scroll for Cape for MP
        2041024, // Scroll for Cape for Magic Def.
        2041043, // Scroll for Cape for Magic DEF
        2041048, // Scroll for Cape for MP
        2041049  // Scroll for Cape for MP
        ,2040008 // Dark scroll for Helmet for DEF
        ,2040009 // Dark Scroll for Helmet for DEF
        ,2040011 // Dark Scroll for Helmet for HP
        ,2040308 // Dark Scroll for Earring for DEF
        ,2040309 // Dark Scroll for Earring for DEF
        ,2040103 // Dark Scroll for Face Accessory for HP
        ,2040104 // Dark Scroll for Face Accessory for HP
        ,2040404 // Dark Scroll for Topwear for DEF
        ,2040405 // Dark Scroll for Topwear for DEF
        ,2040407 // Dark Scroll for Topwear for HP
        ,2040408 // Dark Scroll for Topwear for HP
        ,2040508 // Dark Scroll for Overall Armor for DEF
        ,2040509 // Dark Scroll for Overall Armor for DEF
        ,2040510 // Dark Scroll for Overall Armor for HP
        ,2040511 // Dark Scroll for Overall Armor for HP
        ,2040604 // Dark Scroll for Bottomwear for DEF
        ,2040605 // Dark Scroll for Bottomwear for DEF
        ,2040607 // Dark Scroll for Bottomwear for HP
        ,2040608 // Dark Scroll for Bottomwear for HP
        ,2040804 // Dark Scroll for Gloves for DEF
        ,2040805 // Dark Scroll for Gloves for DEF
        ,2040807 // Dark Scroll for Gloves for HP
        ,2040808 // Dark Scroll for Gloves for HP
        ,2040904 // Dark Scroll for Shield for DEF
        ,2040905 // Dark Scroll for Shield for DEF
        ,2040907 // Dark Scroll for Shield for HP
        ,2040908 // Dark Scroll for Shield for HP
        ,2041000 // Dark Scroll for Cape for DEF
        ,2041001 // Dark Scroll for Cape for DEF
        ,2041003 // Dark Scroll for Cape for Magic Def.
        ,2041004 // Dark Scroll for Cape for Magic Def.
        ,2041012 // Dark Scroll for Cape for HP
        ,2041013 // Dark Scroll for Cape for HP
        ,2041014 // Dark Scroll for Cape for MP
        ,2041015  // Dark Scroll for Cape for MP
        ,2048000 // Scroll for Pet Equip. for Speed
        ,2048001 // Scroll for Pet Equip. for Speed
        ,2048002 // Scroll for Pet Equip. for Speed
        ,2048003 // Scroll for Pet Equip. for Jump
        ,2048004 // Scroll for Pet Equip. for Jump
        ,2048005 // Scroll for Pet Equip. for Jump
        ,2048006 // Scroll for Pet Equip. for Speed
        ,2048007 // Scroll for Pet Equip. for Speed
        ,2048008 // Scroll for Pet Equip. for Jump
        ,2048009 // Scroll for Pet Equip. for Jump
        ,2040018 // Scroll for Helmet for Accuracy 100%
        ,2040024 // Scroll for Helmet for INT 100%
        ,2040027 // Scroll for Helmet for DEX 100%
        ,2040107 // Scroll for Face Accessory for Avoidability 100%
        ,2040202 // Scroll for Eye Accessory for Accuracy 100%
        ,2040207 // Scroll for Eye Accessory for INT 100%
        ,2040300 // Scroll for Earring for INT 100%
        ,2040312 // Scroll for Earring for DEF 100%
        ,2040316 // Scroll for Earring for DEX 100%
        ,2040319 // Scroll for Earring for LUK 100%
        ,2040324 // Scroll for Earring for HP 100%
        ,2040414 // Scroll for Topwear for LUK 100%
        ,2040417 // Scroll for Topwear for STR 100%
        ,2040420 // Scroll for Topwear for HP 100%
        ,2040423 // Scroll for Topwear for LUK 100%
        ,2040500 // Scroll for Overall Armor for DEX 100%
        ,2040506 // Scroll for Overall Armor for DEX 100%
        ,2040512 // Scroll for Overall Armor for INT 100%
        ,2040515 // Scroll for Overall Armor for LUK 100%
        ,2040530 // Scroll for Overall for STR 100%
        ,2040614 // Scroll for Bottomwear for DEX 100%
        ,2040617 // Scroll for Bottomwear for Jump 100%
        ,2040623 // Scroll for Bottomwear for DEX 100%
        ,2040700 // Scroll for Shoes for DEX 100%
        ,2040703 // Scroll for Shoes for Jump 100%
        ,2040706 // Scroll for Shoes for Speed 100%
        ,2040709 // Scroll for Shoes for DEX 100%
        ,2040710 // Scroll for Shoes for Jump 100%
        ,2040711 // Scroll for Shoes for Speed 100%
        ,2040800 // Scroll for Gloves for DEX 100%
        ,2040803 // Scroll for Gloves for ATT 100%
        ,2040806 // Scroll for Gloves for DEX 100%
        ,2040807 // Scroll for Gloves for ATT 100%
        ,2040818 // Scroll for Gloves for Magic Att. 100%
        ,2040900 // Scroll for Shield for DEF 100%
        ,2040903 // Scroll for Shield for DEF 100%
        ,2040918 // Scroll for Shield for Magic Att. 100%
        ,2040923 // Scroll for Shield for LUK 100%
        ,2040929 // Scroll for Shield for STR 100%
        ,2041000 // Scroll for Cape for Magic Def. 100%
        ,2041003 // Scroll for Cape for Weapon Def. 100%
        ,2041006 // Scroll for Cape for HP 100%
        ,2041009 // Scroll for Cape for MP 100%
        ,2041012 // Scroll for Cape for STR 100%
        ,2041015 // Scroll for Cape for INT 100%
        ,2041018 // Scroll for Cape for DEX 100%
        ,2041021 // Scroll for Cape for LUK 100%
        ,2041024 // Scroll for Cape for Magic Def. 100%
        ,2041025 // Scroll for Cape for Weapon Def. 100%
        ,2043015 // Scroll for One-Handed Sword for Accuracy 100%
        ,2043110 // Scroll for One-Handed Axe for Accuracy 100%
        ,2043210 // Scroll for One-Handed BW for Accuracy 100%
        ,2044010 // Scroll for Two-Handed Sword for Accuracy 100%
        ,2044110 // Scroll for Two-Handed Axe for Accuracy 100%
    ));

    static {
        // i ain't going to open one gigantic itemid cache just for 4 perma itemids, no way!
        for (int petItemId : ItemId.getPermaPets()) {
            permanentItemids.add(petItemId);
        }
    }

    public static int getFlagByInt(int type) {
        if (type == 128) {
            return PET_COME;
        } else if (type == 256) {
            return ACCOUNT_SHARING;
        }
        return 0;
    }

    public static boolean isThrowingStar(int itemId) {
        return itemId / 10000 == 207;
    }

    public static boolean isBullet(int itemId) {
        return itemId / 10000 == 233;
    }

    public static boolean isPotion(int itemId) {
        return itemId / 1000 == 2000;
    }

    public static boolean isFood(int itemId) {
        int useType = itemId / 1000;
        return useType == 2022 || useType == 2010 || useType == 2020;
    }

    public static boolean isConsumable(int itemId) {
        return isPotion(itemId) || isFood(itemId);
    }

    public static boolean isRechargeable(int itemId) {
        return isThrowingStar(itemId) || isBullet(itemId);
    }

    public static boolean isArrowForCrossBow(int itemId) {
        return itemId / 1000 == 2061;
    }

    public static boolean isArrowForBow(int itemId) {
        return itemId / 1000 == 2060;
    }

    public static boolean isArrow(int itemId) {
        return isArrowForBow(itemId) || isArrowForCrossBow(itemId);
    }

    public static boolean isPet(int itemId) {
        return itemId / 1000 == 5000;
    }

    public static boolean isExpirablePet(int itemId) {
        return YamlConfig.config.server.USE_ERASE_PET_ON_EXPIRATION || itemId == ItemId.PET_SNAIL;
    }

    public static boolean isPermanentItem(int itemId) {
        return permanentItemids.contains(itemId);
    }

    public static boolean isNewYearCardEtc(int itemId) {
        return itemId / 10000 == 430;
    }

    public static boolean isNewYearCardUse(int itemId) {
        return itemId / 10000 == 216;
    }

    public static boolean isAccessory(int itemId) {
        return itemId >= 1110000 && itemId < 1140000;
    }

    public static boolean isTaming(int itemId) {
        int itemType = itemId / 1000;
        return itemType == 1902 || itemType == 1912;
    }

    /**
     * Evan's dragon equipment - 1942xxx mask, 1952xxx pendant, 1962xxx wings, 1972xxx tail. Same range
     * {@code ItemInformationProvider.getStringData} routes to {@code Eqp/Dragon}.
     *
     * <p>All twelve carry {@code info/islot = "Tm"}, the taming-mount string, so {@link EquipSlot}
     * cannot tell a dragon piece from a mount and the slot has to come off the item id - which is
     * exactly what the client does, see {@link #getDragonSlot}.
     */
    public static boolean isDragonItem(int itemId) {
        return itemId >= 1940000 && itemId < 1980000;
    }

    /**
     * The equipped-inventory position of a dragon equip: -1000 mask, -1001 pendant, -1002 wings,
     * -1003 tail.
     *
     * <p>Read out of the client, not guessed. `GetEquipPartFromItemID` switches on
     * {@code itemId / 10000}; its 180-197 jump table (v83 {@code localhome.exe} @0x460a38, dispatched
     * at @0x4607e3) sends 194/195/196/197 to the arms at 0x460823/31/3f/4d, which load 0x3e8/0x3e9/
     * 0x3ea/0x3eb = 1000/1001/1002/1003. The same table gives 190 -> 18 and 191 -> 19, i.e. the mount
     * and saddle slots {@link EquipSlot} already has, so it is the right table. Corroborated on the
     * wire: the owner's v84 client sent slot -1000 for 1942000 Silver Mask.
     */
    public static short getDragonSlot(int itemId) {
        return (short) -(1000 + (itemId / 10000 - 194));
    }

    public static boolean isTownScroll(int itemId) {
        return itemId >= 2030000;
    }

    public static boolean isCleanSlate(int scrollId) {
        return scrollId > 2048999 && scrollId < 2049004;
    }

    public static boolean isModifierScroll(int scrollId) {
        return scrollId == ItemId.SPIKES_SCROLL || scrollId == ItemId.COLD_PROTECTION_SCROLl;
    }

    public static boolean isFlagModifier(int scrollId, short flag) {
        if (scrollId == ItemId.COLD_PROTECTION_SCROLl && ((flag & ItemConstants.COLD) == ItemConstants.COLD)) {
            return true;
        }
        return scrollId == ItemId.SPIKES_SCROLL && ((flag & ItemConstants.SPIKES) == ItemConstants.SPIKES);
    }

    public static boolean isChaosScroll(int scrollId) {
        return scrollId >= 2049100 && scrollId <= 2049103;
    }

    public static boolean isRateCoupon(int itemId) {
        int itemType = itemId / 1000;
        return itemType == 5211 || itemType == 5360;
    }

    public static boolean isExpCoupon(int couponId) {
        return couponId / 1000 == 5211;
    }

    public static boolean isPartyItem(int itemId) {
        return itemId >= 2022430 && itemId <= 2022433 || itemId >= 2022160 && itemId <= 2022163;
    }

    public static boolean isHiredMerchant(int itemId) {
        return itemId / 10000 == 503;
    }

    public static boolean isPlayerShop(int itemId) {
        return itemId / 10000 == 514;
    }

    public static InventoryType getInventoryType(final int itemId) {
        if (inventoryTypeCache.containsKey(itemId)) {
            return inventoryTypeCache.get(itemId);
        }

        InventoryType ret = InventoryType.UNDEFINED;

        final byte type = (byte) (itemId / 1000000);
        if (type >= 1 && type <= 5) {
            ret = InventoryType.getByType(type);
        }

        inventoryTypeCache.put(itemId, ret);
        return ret;
    }

    public static boolean isMakerReagent(int itemId) {
        return itemId / 10000 == 425;
    }

    public static boolean isOverall(int itemId) {
        return itemId / 10000 == 105;
    }

    public static boolean isCashStore(int itemId) {
        int itemType = itemId / 10000;
        return itemType == 503 || itemType == 514;
    }

    public static boolean isMapleLife(int itemId) {
        int itemType = itemId / 10000;
        return itemType == 543 && itemId != 5430000;
    }

    public static boolean isWeapon(int itemId) {
        return itemId >= 1302000 && itemId < 1493000;
    }

    public static boolean isEquipment(int itemId) {
        return itemId < 2000000 && itemId != 0;
    }

    public static boolean isFishingChair(int itemId) {
        return itemId == ItemId.FISHING_CHAIR;
    }

    public static boolean isMedal(int itemId) {
        return itemId >= 1140000 && itemId < 1143000;
    }

    public static boolean isFace(int itemId) {
        return itemId >= 20000 && itemId < 22000;
    }

    public static boolean isHair(int itemId) {
        return itemId >= 30000 && itemId < 35000;
    }

    public static boolean isMasteryBook(int itemId) {
        return itemId >= 2290000 && itemId <= 2290152;   // 2290140-2290152 are Evan's, added in v84
    }

    public static boolean isMonsterCard(int itemId) {
        return ItemId.isMonsterCard(itemId);
    }
}
