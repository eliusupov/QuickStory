package net.server.channel.handlers;

import client.Character;
import client.Client;
import client.Job;
import client.Skill;
import client.SkillFactory;
import constants.skills.Evan;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvanLegendarySpiritRealLoad {

    @BeforeAll
    static void loadSkills() {
        SkillFactory.loadAllSkills();
    }

    @Test
    void evanUsesItsOwnLegendarySpiritSkill() {
        Character chr = Character.getDefault(Mockito.mock(Client.class));
        chr.setJob(Job.EVAN1);
        Skill legendarySpirit = SkillFactory.getSkill(Evan.LEGENDARY_SPIRIT);

        assertEquals(Evan.LEGENDARY_SPIRIT, chr.getBeginnerSkillBlock() + 1003);
        assertNotNull(legendarySpirit, "real Skill.wz must carry Evan's Legendary Spirit");
        chr.changeSkillLevel(legendarySpirit, (byte) 1, 1, -1);
        assertTrue(ScrollHandler.hasLegendarySpirit(chr));
    }
}
