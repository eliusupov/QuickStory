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
import provider.wz.WZFiles;
import server.life.Monster;
import server.life.MonsterStats;
import server.maps.MapleMap;
import testutil.HandlerTest;
import testutil.Packets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TakeDamageHandler: {@code BuffStat.DIVINE_BODY} and {@code BuffStat.INVINCIBLE} were written and
 * never read - the same shape of hole {@code TakeDamageMagicShieldTest} pins for MAGIC_SHIELD.
 *
 * <p><strong>Invincible Barrier</strong> 1010 / 10001010 / 20001010 / 20011010 (DIVINE_BODY). All
 * four carry the same one-level node, {@code time=30, x=1}, and {@code String.wz} says only
 * "30 seconds of invincibility" - x is a flag, there is no percentage to scale, so the hit is
 * dropped whole. Nothing in this tree grants the skill, so its blast radius outside a GM hand-out
 * is nil; the skill simply now does what its data says.
 *
 * <p><strong>Cleric's Invincible</strong> 2301003 (INVINCIBLE), {@code x = level + 10}, so 11% at
 * level 1 and 30% at 20. Its desc is the only one in the mitigation chain that excludes a damage
 * type: "Temporarily decreases the weapon damage received. It has no effect, however, on the magic
 * attack." {@code Mob.wz} carries that distinction as {@code attackN/info/magic}, present-and-1 on
 * 480 of the 948 mob attack nodes and absent on the other 468, which is what
 * {@code MobAttackInfo.isMagicAttack()} now reads.
 *
 * <p><strong>Not a {@code *Test} class on purpose</strong>: the magic/weapon cases drive
 * {@code MobAttackInfoFactory}, whose static initialiser opens the real {@code Mob.wz}, and
 * {@link WZFiles#DIRECTORY} is a {@code static final} resolved once per JVM that
 * {@code MobSkillFactoryTest} repoints at a {@code @TempDir}.
 */
@ExtendWith(MockitoExtension.class)
class TakeDamageInvincibleRealLoad extends HandlerTest {
    /** Stone Golem. Its only attack node carries {@code magic=1}, elemAttr L. */
    private static final int MAGIC_MOB = 3000000;
    /** Deep Buffy. Its only attack node has {@code PADamage=100} and no {@code magic}. */
    private static final int WEAPON_MOB = 2230108;
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
        lenient().when(chr.getMap()).thenReturn(map);
        lenient().when(chr.getJob()).thenReturn(Job.EVAN10);
        lenient().when(map.getMapObject(MOB_OID)).thenReturn(attacker);
        lenient().when(attacker.getStats()).thenReturn(stats);
        lenient().when(chr.getAutobanManager()).thenReturn(autoban);
        lenient().when(stats.loseItem()).thenReturn(null);

        // Mockito hands back 0, not null, for an Integer-returning method it has not been told
        // about, and every buff in this handler is read as "getBuffedValue(...) != null". An
        // unstubbed mock therefore reads as PERMANENTLY BUFFED. Absent has to be said out loud.
        lenient().when(chr.getBuffedValue(any(BuffStat.class))).thenReturn(null);
    }

    /** {@code damagefrom} -1 is a bump: contact damage, no attack node, always weapon. */
    private InPacket bump(int mobId, int damage) {
        return hit(mobId, (byte) -1, damage);
    }

    /** {@code damagefrom} 0 is the mob's {@code attack1}, the node that does or does not say magic. */
    private InPacket attack1(int mobId, int damage) {
        return hit(mobId, (byte) 0, damage);
    }

    private InPacket hit(int mobId, byte damagefrom, int damage) {
        when(attacker.getId()).thenReturn(mobId);
        return Packets.buildInPacket(out -> {
            out.writeInt(0);
            out.writeByte(damagefrom);
            out.writeByte(0);   // element, read and discarded
            out.writeInt(damage);
            out.writeInt(mobId);
            out.writeInt(MOB_OID);
            out.writeByte(0);   // direction
        });
    }

    @Test
    void invincibleBarrierDropsTheHitEntirely() {
        lenient().when(chr.getBuffedValue(BuffStat.DIVINE_BODY)).thenReturn(1);

        handler.handlePacket(bump(WEAPON_MOB, 1000), client);

        verify(chr, never()).addMPHP(anyInt(), anyInt());
    }

    /** Invincibility is not "weapon only" - the magic attack has to bounce off it too. */
    @Test
    void invincibleBarrierDropsMagicAttacksToo() {
        lenient().when(chr.getBuffedValue(BuffStat.DIVINE_BODY)).thenReturn(1);

        handler.handlePacket(attack1(MAGIC_MOB, 1000), client);

        verify(chr, never()).addMPHP(anyInt(), anyInt());
    }

    @Test
    void clericInvincibleTakesItsPercentageOffABump() {
        lenient().when(chr.getBuffedValue(BuffStat.INVINCIBLE)).thenReturn(30);

        handler.handlePacket(bump(WEAPON_MOB, 1000), client);

        verify(chr).addMPHP(-700, 0);
    }

    @Test
    void clericInvincibleTakesItsPercentageOffAWeaponAttack() {
        lenient().when(chr.getBuffedValue(BuffStat.INVINCIBLE)).thenReturn(30);

        handler.handlePacket(attack1(WEAPON_MOB, 1000), client);

        verify(chr).addMPHP(-700, 0);
    }

    /** The line 2301003's own desc draws, and the reason MobAttackInfo learned to read magic. */
    @Test
    void clericInvincibleDoesNothingAgainstAMagicAttack() {
        lenient().when(chr.getBuffedValue(BuffStat.INVINCIBLE)).thenReturn(30);

        handler.handlePacket(attack1(MAGIC_MOB, 1000), client);

        verify(chr).addMPHP(-1000, 0);
    }

    @Test
    void withoutEitherBuffTheFullHitLands() {
        handler.handlePacket(bump(WEAPON_MOB, 1000), client);

        verify(chr).addMPHP(-1000, 0);
    }
}
