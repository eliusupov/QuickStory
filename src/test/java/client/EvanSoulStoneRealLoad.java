package client;

import constants.skills.Evan;
import net.server.world.Party;
import net.server.world.PartyCharacter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import server.StatEffect;
import server.TimerManager;
import server.maps.MapObject;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;

/** Soul Stone's selected, living recipients revive once only when they later die. */
class EvanSoulStoneRealLoad {

    @BeforeAll
    static void loadSkills() {
        SkillFactory.loadAllSkills();
    }

    private static Character character() {
        return Mockito.spy(Character.getDefault(Mockito.mock(Client.class)));
    }

    private static StatEffect soulStone() {
        return SkillFactory.getSkill(Evan.SOUL_STONE).getEffect(20);
    }

    private static void putInParty(Character... members) {
        Party party = new Party(1, new PartyCharacter(members[0]));
        for (Character member : members) {
            party.addMember(new PartyCharacter(member));
            member.setParty(party);
        }
    }

    @Test
    void castSelectsOnlyLivingPartyMembersAndUsesTheWzRecipientCount() {
        StatEffect effect = soulStone();
        Character caster = character();
        Character first = character();
        Character second = character();
        Character third = character();
        Character alreadyDead = character();
        Character outOfRange = character();
        MapleMap map = Mockito.mock(MapleMap.class);
        alreadyDead.updateHp(0);
        putInParty(caster, first, second, third, alreadyDead, outOfRange);
        Mockito.doReturn(map).when(caster).getMap();
        Mockito.doReturn(map).when(first).getMap();
        Mockito.doReturn(map).when(second).getMap();
        Mockito.doReturn(map).when(third).getMap();
        Mockito.doReturn(map).when(alreadyDead).getMap();
        List<MapObject> mapPlayers = List.of(caster, first, second, third, alreadyDead);
        Mockito.when(map.getMapObjectsInRect(any(), anyList())).thenReturn(mapPlayers);

        try (MockedStatic<TimerManager> timers = Mockito.mockStatic(TimerManager.class)) {
            timers.when(TimerManager::getInstance).thenReturn(Mockito.mock(TimerManager.class));
            effect.applyTo(caster);
        }

        int revived = 0;
        for (Character member : List.of(first, second, third)) {
            member.updateHp(0);
            if (member.reviveFromSoulStone(1_000L)) {
                revived++;
                assertEquals(member.getCurrentMaxHp() * effect.getX() / 100, member.getHp(), "x percent HP");
            }
        }
        outOfRange.updateHp(0);
        int revivedCount = revived;
        var bounds = org.mockito.ArgumentCaptor.forClass(java.awt.Rectangle.class);
        verify(map).getMapObjectsInRect(bounds.capture(), anyList());

        assertAll(
                () -> assertEquals(effect.getY(), revivedCount, "exactly WZ y random living party members are protected"),
                () -> assertEquals(0, alreadyDead.getHp(), "cast never revives an existing corpse"),
                () -> assertFalse(alreadyDead.reviveFromSoulStone(1_000L), "existing corpse was never protected"),
                () -> assertFalse(outOfRange.reviveFromSoulStone(1_000L), "out-of-range party member was never protected"),
                () -> assertTrue(bounds.getValue().contains(new Point(0, 0)), "WZ range includes the caster position"),
                () -> assertFalse(bounds.getValue().contains(new Point(401, 0)), "WZ range excludes a party member beyond rb")
        );
    }

    @Test
    void expiredSoulStoneCannotRevive() {
        StatEffect effect = soulStone();
        Character target = character();
        long start = 1_000L;

        target.protectFromSoulStone(effect, start);
        target.updateHp(0);

        assertAll(
                () -> assertFalse(target.reviveFromSoulStone(start + effect.getDuration()), "expires at WZ time"),
                () -> assertEquals(0, target.getHp(), "expired protection leaves the later death dead")
        );
    }
}
