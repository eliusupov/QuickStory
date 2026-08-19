package net.server.channel.handlers;

import client.Skill;
import client.SkillFactory;
import constants.skills.Evan;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import server.StatEffect;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Pins Evan Slow's distinct WZ buff and monster-debuff durations to the server application path. */
class EvanSlowRealLoad {

    @BeforeAll
    static void loadSkills() {
        SkillFactory.loadAllSkills();
    }

    @Test
    void usesYSecondsForTheMonsterSlowAndTimeForTheEvanBuff() {
        Skill slow = SkillFactory.getSkill(Evan.SLOW);
        StatEffect levelOne = slow.getEffect(1);
        StatEffect levelFifteen = slow.getEffect(15);

        assertAll(
                () -> assertEquals(60_000, levelOne.getDuration(), "level 1 buff time"),
                () -> assertEquals(-12, levelOne.getX(), "level 1 speed"),
                () -> assertEquals(4_000, AbstractDealDamageHandler.evanSlowDebuffDuration(levelOne), "level 1 slow duration"),
                () -> assertEquals(150_000, levelFifteen.getDuration(), "level 15 buff time"),
                () -> assertEquals(-40, levelFifteen.getX(), "level 15 speed"),
                () -> assertEquals(12_000, AbstractDealDamageHandler.evanSlowDebuffDuration(levelFifteen), "level 15 slow duration")
        );
    }
}
