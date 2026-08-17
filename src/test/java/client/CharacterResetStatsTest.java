package client;

import config.YamlConfig;
import constants.game.GameConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Character.resetStats() at 1st job advancement.
 *
 * <p>Evan (job 2200) had no case in that switch, so an Evan advancing out of job 2001 kept the
 * beginner's auto-assigned STR/DEX spread on an INT class. The Evan branch also has to leave SP
 * alone: changeJob grants an Evan 3 SP rather than 1, and getSkillBook(2200) is book 0, so every
 * tsp formula in that switch would write over it with a smaller number.
 */
class CharacterResetStatsTest {

    private boolean savedAutoassign;

    @BeforeEach
    void enableAutoassign() {
        savedAutoassign = YamlConfig.config.server.USE_AUTOASSIGN_STARTERS_AP;
        YamlConfig.config.server.USE_AUTOASSIGN_STARTERS_AP = true;
    }

    @AfterEach
    void restoreAutoassign() {
        YamlConfig.config.server.USE_AUTOASSIGN_STARTERS_AP = savedAutoassign;
    }

    private static Character chrAt(Job job, int level, int str, int dex, int int_, int luk, int ap) {
        Client c = Mockito.mock(Client.class);
        Character chr = Character.getDefault(c);
        chr.setJob(job);
        chr.setLevel(level);
        chr.updateStrDexIntLuk(str, dex, int_, luk, ap);
        return chr;
    }

    @Test
    void evanFirstJobGetsTheMagicianSpreadAndKeepsItsSp() {
        // A level-10 beginner Evan that just ran changeJob(2200): the auto-assign level-up code
        // has dumped every point into STR/DEX, and 3 SP per level plus changeJob's 3 sit in book 0.
        Character chr = chrAt(Job.EVAN1, 10, 48, 9, 4, 4, 4);
        chr.gainSp(30, 0, true);
        chr.gainSp(7, 3, true);     // a 2213 book value that must survive untouched

        int pool = chr.getStr() + chr.getDex() + chr.getInt() + chr.getLuk() + chr.getRemainingAp();
        chr.resetStats();

        assertAll(
                () -> assertEquals(4, chr.getStr(), "STR reset to base"),
                () -> assertEquals(4, chr.getDex(), "DEX reset to base"),
                () -> assertEquals(20, chr.getInt(), "INT gets the magician allotment"),
                () -> assertEquals(4, chr.getLuk(), "LUK reset to base"),
                () -> assertEquals(pool - 32, chr.getRemainingAp(), "every unallotted point refunded as AP"),
                () -> assertArrayEquals(new int[]{30, 0, 0, 7, 0, 0, 0, 0, 0, 0}, chr.getRemainingSps(),
                        "ten-slot SP table untouched, book 0 included")
        );
    }

    /**
     * The ten-slot table is indexed by getSkillBook, so resetStats' SP write for job 2200 addresses
     * book 0 and nothing else. Pinned because a wrong assumption about Evan's SP encoding once
     * crashed the live v84 client at character select.
     */
    @Test
    void evanFirstJobUsesSkillBookZero() {
        assertAll(
                () -> assertEquals(0, GameConstants.getSkillBook(2001)),
                () -> assertEquals(0, GameConstants.getSkillBook(2200)),
                () -> assertEquals(1, GameConstants.getSkillBook(2210)),
                () -> assertEquals(9, GameConstants.getSkillBook(2218))
        );
    }

    @Test
    void explorerMagicianIsUnchanged() {
        Character chr = chrAt(Job.MAGICIAN, 8, 25, 4, 4, 4, 30);
        chr.gainSp(9, 0, true);

        int pool = chr.getStr() + chr.getDex() + chr.getInt() + chr.getLuk() + chr.getRemainingAp();
        chr.resetStats();

        assertAll(
                () -> assertEquals(4, chr.getStr()),
                () -> assertEquals(4, chr.getDex()),
                () -> assertEquals(20, chr.getInt()),
                () -> assertEquals(4, chr.getLuk()),
                () -> assertEquals(pool - 32, chr.getRemainingAp()),
                // unchanged behaviour: explorers DO have their SP recomputed, 1 + (level-8)*3
                () -> assertEquals(1, chr.getRemainingSps()[0])
        );
    }

    @Test
    void explorerWarriorIsUnchanged() {
        Character chr = chrAt(Job.WARRIOR, 12, 25, 4, 4, 4, 30);

        int pool = chr.getStr() + chr.getDex() + chr.getInt() + chr.getLuk() + chr.getRemainingAp();
        chr.resetStats();

        assertAll(
                () -> assertEquals(35, chr.getStr()),
                () -> assertEquals(4, chr.getDex()),
                () -> assertEquals(4, chr.getInt()),
                () -> assertEquals(4, chr.getLuk()),
                () -> assertEquals(pool - 47, chr.getRemainingAp()),
                () -> assertEquals(1 + (12 - 10) * 3, chr.getRemainingSps()[0])
        );
    }
}
