package client;

import constants.skills.Beginner;
import constants.skills.Evan;
import constants.skills.Legend;
import constants.skills.Noblesse;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code getJobType() * 10000000} was the codebase's way of saying "this character's beginner skill
 * block", and it is right for Explorers (0), Cygnus (1000) and Aran (2000). It is wrong for Evan,
 * whose block is <b>2001</b>, not 2000: getJobType divides the job id by 1000, so every Evan was
 * handed Aran's Legend skills. That expression drove the mount id, the mount tiredness dispel,
 * Blessing of the Fairy on login and in the stat recalc, the ultra Three Snails check and the Maker
 * skill level - which is why an Evan could not use Maker at all and kept riding a tired mount.
 *
 * <p>All of those now go through {@code getBeginnerSkillBlock()}. The constants below are the real
 * skill ids from {@code constants.skills}, so this pins the accessor to the data, not to arithmetic.
 */
class CharacterBeginnerSkillBlockTest {

    private static int block(Job job) {
        Character chr = Character.getDefault(Mockito.mock(Client.class));
        chr.setJob(job);
        return chr.getBeginnerSkillBlock();
    }

    @Test
    void everyClassResolvesItsOwnBeginnerSkills() {
        assertAll(
                // Evan: job 2001 and 2200-2218 alike
                () -> assertEquals(Evan.MONSTER_RIDER, block(Job.EVAN) + 1004, "2001 Monster Rider"),
                () -> assertEquals(Evan.MONSTER_RIDER, block(Job.EVAN1) + 1004, "2200 Monster Rider"),
                () -> assertEquals(Evan.MONSTER_RIDER, block(Job.EVAN10) + 1004, "2218 Monster Rider"),
                () -> assertEquals(Evan.THREE_SNAILS, block(Job.EVAN5) + 1000, "2213 Three Snails"),
                () -> assertEquals(Evan.MAKER, block(Job.EVAN1) + 1007, "2200 Maker"),
                () -> assertEquals(Evan.BLESSING_OF_THE_FAIRY, block(Job.EVAN1) + 12, "2200 Blessing"),
                () -> assertEquals(Evan.ECHO_OF_HERO, block(Job.EVAN1) + 1005, "2200 Echo of Hero"),
                () -> assertEquals(Evan.BAMBOO_THRUST, block(Job.EVAN1) + 1009, "2200 Bamboo Thrust"),
                () -> assertEquals(Evan.INVINCIBLE_BARRIER, block(Job.EVAN1) + 1010, "2200 Barrier"),
                () -> assertEquals(Evan.POWER_EXPLOSION, block(Job.EVAN1) + 1011, "2200 Power Explosion"),

                // the three blocks that already worked, unchanged
                () -> assertEquals(Beginner.MONSTER_RIDER, block(Job.BEGINNER) + 1004, "0 Monster Rider"),
                () -> assertEquals(Beginner.MONSTER_RIDER, block(Job.BISHOP) + 1004, "232 Monster Rider"),
                () -> assertEquals(Noblesse.MONSTER_RIDER, block(Job.NOBLESSE) + 1004, "1000 Monster Rider"),
                () -> assertEquals(Noblesse.MONSTER_RIDER, block(Job.BLAZEWIZARD4) + 1004, "1212 Monster Rider"),
                () -> assertEquals(Legend.MONSTER_RIDER, block(Job.LEGEND) + 1004, "2000 Monster Rider"),
                () -> assertEquals(Legend.MONSTER_RIDER, block(Job.ARAN4) + 1004, "2112 Monster Rider"),
                () -> assertEquals(Legend.BLESSING_OF_THE_FAIRY, block(Job.ARAN1) + 12, "2100 Blessing"),

                // and the raw bases, so a change is visible even where no constant exists
                () -> assertEquals(0, block(Job.MAGICIAN), "Explorer base"),
                () -> assertEquals(10000000, block(Job.NOBLESSE), "Cygnus base"),
                () -> assertEquals(20000000, block(Job.ARAN1), "Aran base"),
                () -> assertEquals(20010000, block(Job.EVAN1), "Evan base")
        );
    }
}
