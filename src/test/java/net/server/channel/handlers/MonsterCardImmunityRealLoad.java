package net.server.channel.handlers;

import client.BuffStat;
import client.Character;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import provider.wz.WZFiles;
import server.ItemInformationProvider;
import server.StatEffect;
import tools.DatabaseConnection;
import tools.Pair;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The five monster cards that pierce a mob's immunity, 02385013 / 02386000 / 02387000 / 02387001 /
 * 02387003.
 *
 * <p>Each carries {@code spec/respectPimmune} or {@code respectMimmune} beside a {@code prob} - the
 * Skelosaurus card carries both off one prob - and the String.wz text is explicit about what that
 * prob buys: "20% chance of breaking through monster's weapon/magic defense". StatEffect raised the
 * two BuffStats so the client drew the icon, and then two things were missing: the {@code prob}
 * was never kept (only the meso-up and item-up branches beside it bothered), and the immunity gate
 * in {@code AbstractDealDamageHandler.applyAttack} flattened every damage line to 1 without ever
 * asking whether the player was holding a card. A mob under PImmune took 1 either way.
 *
 * <p>{@link #anExpiredAreaNeverPierces()} is the case that makes {@code isActive} load-bearing
 * rather than decorative: {@code Character.updateActiveEffects} only greys the client icon when you
 * leave a card's {@code con} range, the StatEffect stays in the buff map, so a gate that trusted
 * {@code getBuffEffect} alone would let an Aqua Road card work in Ludibrium.
 *
 * <p><strong>Not a {@code *Test} class on purpose</strong>: {@link WZFiles#DIRECTORY} is a
 * {@code static final} resolved once per JVM and {@code MobSkillFactoryTest} repoints
 * {@code wz-path} at a {@code @TempDir}.
 */
class MonsterCardImmunityRealLoad {

    /** Bone Fish Card, respectPimmune, prob 30, Aqua Road (230000000-230999999). */
    private static final int BONE_FISH = 2386000;
    /** Skelosaurus Card, both respects off one prob of 20, Dragon Forest (240030000-240040999). */
    private static final int SKELOSAURUS = 2387003;

    private static final int AQUA_ROAD = 230000000;
    private static final int LUDIBRIUM = 220000000;

    private static final int TRIALS = 200;

    /** Same shim {@code TabletScrollRealLoad} uses; the provider constructor reads a table. */
    @BeforeAll
    static void bootTheProviderWithoutADatabase() {
        try (MockedStatic<DatabaseConnection> db = Mockito.mockStatic(DatabaseConnection.class)) {
            db.when(DatabaseConnection::getConnection).thenThrow(new SQLException("no database in tests"));
            ItemInformationProvider.getInstance();
        }
    }

    private static StatEffect card(int itemId) {
        return ItemInformationProvider.getInstance().getItemEffect(itemId);
    }

    /** A player standing on {@code mapid} holding {@code card} as {@code respect}. */
    private static Character holding(StatEffect card, BuffStat respect, int mapid) {
        Character player = mock(Character.class);
        when(player.getBuffEffect(respect)).thenReturn(card);
        when(player.getMapId()).thenReturn(mapid);
        when(player.getPartyMembersOnSameMap()).thenReturn(List.of());
        return player;
    }

    private static int piercesOutOf(Character player, BuffStat respect) {
        int pierced = 0;
        for (int i = 0; i < TRIALS; i++) {
            if (AbstractDealDamageHandler.piercesImmunity(player, respect)) {
                pierced++;
            }
        }
        return pierced;
    }

    /** The prob that was being dropped. Unfixed, every one of these reads 0. */
    @Test
    void everyRespectCardKeepsItsProb() {
        assertAll(
                () -> assertEquals(15, card(2385013).getCardProb(), "02385013 Goby"),
                () -> assertEquals(30, card(BONE_FISH).getCardProb(), "02386000 Bone Fish"),
                () -> assertEquals(25, card(2387000).getCardProb(), "02387000 Gatekeeper"),
                () -> assertEquals(25, card(2387001).getCardProb(), "02387001 Thanatos"),
                () -> assertEquals(20, card(SKELOSAURUS).getCardProb(), "02387003 Skelosaurus"));
    }

    /** Both stats off the one prob, which is the shape only 02387003 has. */
    @Test
    void skelosaurusRaisesBothStats() {
        List<Pair<BuffStat, Integer>> statups = card(SKELOSAURUS).getStatups();
        assertAll(
                () -> assertTrue(statups.stream().anyMatch(s -> s.getLeft() == BuffStat.RESPECT_PIMMUNE), "pimmune"),
                () -> assertTrue(statups.stream().anyMatch(s -> s.getLeft() == BuffStat.RESPECT_MIMMUNE), "mimmune"));
    }

    /**
     * The regression. Unfixed the gate never asked, and with the prob dropped it could not have
     * answered: 0 out of 200 where the card states 30%.
     */
    @Test
    void aCardInItsOwnAreaPierces() {
        int pierced = piercesOutOf(holding(card(BONE_FISH), BuffStat.RESPECT_PIMMUNE, AQUA_ROAD),
                BuffStat.RESPECT_PIMMUNE);

        assertTrue(pierced > 0, "02386000 pierced " + pierced + " times out of " + TRIALS
                + " in Aqua Road, where its spec states prob 30");
    }

    /** Leaving the con range must stop it dead, not merely grey the icon. */
    @Test
    void anExpiredAreaNeverPierces() {
        int pierced = piercesOutOf(holding(card(BONE_FISH), BuffStat.RESPECT_PIMMUNE, LUDIBRIUM),
                BuffStat.RESPECT_PIMMUNE);

        assertEquals(0, pierced, "02386000 is an Aqua Road card and must do nothing in Ludibrium");
    }

    /** The blast radius: no card, no change - immunity stays absolute for everyone else. */
    @Test
    void withoutACardNothingPierces() {
        Character player = mock(Character.class);
        when(player.getBuffEffect(BuffStat.RESPECT_PIMMUNE)).thenReturn(null);

        assertFalse(AbstractDealDamageHandler.piercesImmunity(player, BuffStat.RESPECT_PIMMUNE));
    }

    /** A physical card must not open the magic gate, and the handler asks per attack type. */
    @Test
    void aWeaponCardDoesNotPierceMagicImmunity() {
        Character player = holding(card(BONE_FISH), BuffStat.RESPECT_PIMMUNE, AQUA_ROAD);
        when(player.getBuffEffect(BuffStat.RESPECT_MIMMUNE)).thenReturn(null);

        assertFalse(AbstractDealDamageHandler.piercesImmunity(player, BuffStat.RESPECT_MIMMUNE),
                "02386000 carries respectPimmune only");
    }
}
