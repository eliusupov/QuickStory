package net.server.channel.handlers;

import client.BuffStat;
import client.Job;
import client.autoban.AutobanManager;
import net.packet.InPacket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import server.life.Monster;
import server.life.MonsterStats;
import server.maps.MapleMap;
import testutil.HandlerTest;
import testutil.Packets;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TakeDamageHandler: BuffStat.MAGIC_SHIELD was written and never read.
 *
 * <p>StatEffect.loadSkillEffectFromData() has had an arm for Magic Shield 22131001 all along, and
 * nothing in the codebase ever asked for the stat back, so the skill spent 20 seconds and a 40
 * second cooldown doing nothing. It now sits with Achilles and High Defense, the other two
 * incoming-damage reductions.
 */
@ExtendWith(MockitoExtension.class)
class TakeDamageMagicShieldTest extends HandlerTest {
    private static final int MOB_ID = 100100;
    private static final int MOB_OID = 7;

    private final TakeDamageHandler handler = new TakeDamageHandler();

    @Mock
    private MapleMap map;

    @Mock
    private Monster attacker;

    @Mock
    private MonsterStats stats;

    @Mock
    private AutobanManager autoban;

    @BeforeEach
    void prepareMap() {
        when(chr.getMap()).thenReturn(map);
        when(chr.getJob()).thenReturn(Job.EVAN10);
        when(map.getMapObject(MOB_OID)).thenReturn(attacker);
        when(attacker.getId()).thenReturn(MOB_ID);
        when(attacker.getStats()).thenReturn(stats);
        when(chr.getAutobanManager()).thenReturn(autoban);
        lenient().when(stats.loseItem()).thenReturn(null);
    }

    /** A melee hit (damagefrom -1) from a mob that is on the map. */
    private InPacket meleeHit(int damage) {
        return Packets.buildInPacket(out -> {
            out.writeInt(0);
            out.writeByte(-1);
            out.writeByte(0);   // element, read and discarded
            out.writeInt(damage);
            out.writeInt(MOB_ID);
            out.writeInt(MOB_OID);
            out.writeByte(0);   // direction
        });
    }

    @Test
    void magicShieldTakesItsPercentageOffTheHit() {
        lenient().when(chr.getBuffedValue(BuffStat.MAGIC_SHIELD)).thenReturn(30);

        handler.handlePacket(meleeHit(1000), client);

        verify(chr).addMPHP(-700, 0);
    }

    @Test
    void withoutTheBuffTheFullHitLands() {
        handler.handlePacket(meleeHit(1000), client);

        verify(chr).addMPHP(-1000, 0);
    }
}
