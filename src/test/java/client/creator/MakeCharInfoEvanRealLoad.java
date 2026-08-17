package client.creator;

import client.Character;
import client.Job;
import client.SkinColor;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * verifyCharacter only checks the starting equipment when the job is one whose creator takes those
 * ids off the creation packet. Job.EVAN (2001) is one - EvanCreator passes top/bottom/shoes/weapon
 * straight through, exactly like LegendCreator - but it was not on the list, so an Evan's starting
 * equipment was the one thing accepted unverified at the character-creation trust boundary.
 *
 * <p>Runs against the real Etc.wz MakeCharInfo.img, so it also proves EvanChar{Male,Female} actually
 * carries the four equip ids to check against: if it did not, adding the job to the list would have
 * rejected every legitimate Evan instead.
 */
class MakeCharInfoEvanRealLoad {

    /** The only ids MakeCharInfo.img/EvanCharMale offers for an Evan. */
    private static final int TOP = 1042180;
    private static final int BOTTOM = 1060138;
    private static final int SHOES = 1072418;
    private static final int WEAPON = 1302132;

    private static Character newEvan(int top, int bottom, int shoes, int weapon) {
        Character chr = Mockito.mock(Character.class);
        when(chr.getJob()).thenReturn(Job.EVAN);
        when(chr.isMale()).thenReturn(true);
        when(chr.getFace()).thenReturn(20100);
        when(chr.getHair()).thenReturn(30030);
        when(chr.getSkinColor()).thenReturn(SkinColor.getById(0));

        Item topItem = itemOf(top), bottomItem = itemOf(bottom);
        Item shoesItem = itemOf(shoes), weaponItem = itemOf(weapon);

        Inventory equipped = Mockito.mock(Inventory.class);
        when(chr.getInventory(InventoryType.EQUIPPED)).thenReturn(equipped);
        when(equipped.getItem((short) -5)).thenReturn(topItem);
        when(equipped.getItem((short) -6)).thenReturn(bottomItem);
        when(equipped.getItem((short) -7)).thenReturn(shoesItem);
        when(equipped.getItem((short) -11)).thenReturn(weaponItem);
        return chr;
    }

    private static Item itemOf(int itemId) {
        Item item = Mockito.mock(Item.class);
        when(item.getItemId()).thenReturn(itemId);
        return item;
    }

    @Test
    void anEvanStartingEquipmentIsVerified() {
        assertTrue(MakeCharInfoValidator.isNewCharacterValid(newEvan(TOP, BOTTOM, SHOES, WEAPON)),
                "the four ids the client actually offers must still create");

        assertFalse(MakeCharInfoValidator.isNewCharacterValid(newEvan(TOP, BOTTOM, SHOES, 1302000)),
                "a weapon that is not on Evan's list must be rejected");
        assertFalse(MakeCharInfoValidator.isNewCharacterValid(newEvan(1040002, BOTTOM, SHOES, WEAPON)),
                "a top that is not on Evan's list must be rejected");
    }
}
