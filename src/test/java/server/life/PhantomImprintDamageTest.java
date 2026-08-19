package server.life;

import client.Skill;
import client.SkillFactory;
import client.status.MonsterStatus;
import client.status.MonsterStatusEffect;
import constants.skills.Evan;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PhantomImprintDamageTest {
    @BeforeAll
    static void loadSkills() {
        SkillFactory.loadAllSkills();
    }

    @Test
    void imprintIncreasesAllIncomingDamageByItsWzXPercent() {
        Skill imprint = SkillFactory.getSkill(Evan.PHANTOM_IMPRINT);

        Monster levelOne = mob();
        buff(levelOne, imprint, 1);
        assertEquals(101, levelOne.modifyIncomingDamage(100), "level 1 x=1");

        Monster levelTwenty = mob();
        buff(levelTwenty, imprint, 20);
        assertEquals(105, levelTwenty.modifyIncomingDamage(100), "level 20 x=5");

        assertEquals(100, mob().modifyIncomingDamage(100), "no curse means no damage increase");
    }

    private static Monster mob() {
        return new Monster(9300018, new MonsterStats());
    }

    private static void buff(Monster mob, Skill skill, int level) {
        try {
            Field stati = Monster.class.getDeclaredField("stati");
            stati.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<MonsterStatus, MonsterStatusEffect> statuses = (Map<MonsterStatus, MonsterStatusEffect>) stati.get(mob);
            int x = skill.getEffect(level).getX();
            statuses.put(MonsterStatus.PHANTOM_IMPRINT,
                    new MonsterStatusEffect(Map.of(MonsterStatus.PHANTOM_IMPRINT, x), skill, null, false));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Monster status storage changed", e);
        }
    }
}
