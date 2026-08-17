package net.server.channel.handlers;

import client.Character;
import client.Client;
import client.Job;
import client.Skill;
import client.SkillFactory;
import constants.skills.Evan;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AbstractDealDamageHandler.canCrit(): who is allowed to land a critical hit.
 *
 * <p>It matters twice - the autoban damage ceiling is doubled for a character that can crit, and
 * parseDamage() inverts the damage line so the client draws the hit as a crit. Evan was in neither:
 * the list is jobs, and Critical Magic 22140000 arrives at Evan7, which no Job.isA() can name
 * without widening its id/100 rule across all ten Evan jobs. So an Evan's crits rendered as plain
 * hits and a legitimate 150% hit could trip DAMAGE_HACK.
 *
 * <p>SkillFactory is a WZ-backed static map that is empty outside a running server, so it is
 * stubbed.
 */
class CritEligibilityTest {

    private final Skill criticalMagic = new Skill(Evan.CRITICAL_MAGIC);

    private Character evan(Job job, int criticalMagicLevel) {
        Character chr = Character.getDefault(Mockito.mock(Client.class));
        chr.setJob(job);
        if (criticalMagicLevel > 0) {
            chr.changeSkillLevel(criticalMagic, (byte) criticalMagicLevel, 5, -1);
        }
        return chr;
    }

    private boolean canCrit(Character chr) {
        try (MockedStatic<SkillFactory> factory = Mockito.mockStatic(SkillFactory.class)) {
            factory.when(() -> SkillFactory.getSkill(Evan.CRITICAL_MAGIC)).thenReturn(criticalMagic);
            return AbstractDealDamageHandler.canCrit(chr);
        }
    }

    @Test
    void anEvanCritsOnceItHasCriticalMagic() {
        assertAll(
                () -> assertTrue(canCrit(evan(Job.EVAN7, 15)), "Evan7 with Critical Magic"),
                () -> assertTrue(canCrit(evan(Job.EVAN10, 1)), "Evan10 with one point in it"),
                () -> assertFalse(canCrit(evan(Job.EVAN6, 0)), "Evan6 cannot have it yet")
        );
    }

    @Test
    void everyOtherClassIsUnchanged() {
        assertAll(
                () -> assertTrue(canCrit(evan(Job.NIGHTLORD, 0)), "thief"),
                () -> assertTrue(canCrit(evan(Job.BOWMASTER, 0)), "bowman"),
                () -> assertTrue(canCrit(evan(Job.ARAN4, 0)), "Aran4"),
                () -> assertTrue(canCrit(evan(Job.BUCCANEER, 0)), "Buccaneer"),
                () -> assertFalse(canCrit(evan(Job.BISHOP, 0)), "Bishop"),
                () -> assertFalse(canCrit(evan(Job.HERO, 0)), "Hero"),
                () -> assertFalse(canCrit(evan(Job.ARAN2, 0)), "Aran2")
        );
    }
}
