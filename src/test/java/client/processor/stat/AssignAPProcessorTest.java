package client.processor.stat;

import client.Character;
import client.Client;
import client.Job;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

class AssignAPProcessorTest {

    @Test
    void getMinHp() {
        int max_level = 200;
        int cygnus_max_level = 120;

        BiFunction<Job,Integer,Integer> f = AssignAPProcessor::getMinHp;

        assertAll(
                // Beginners
                () -> assertEquals(2438, f.apply(Job.BEGINNER, max_level)),
                () -> assertEquals(1478, f.apply(Job.NOBLESSE, cygnus_max_level)),

                // Warrior (Explorer)
                () -> assertEquals(4918, f.apply(Job.WARRIOR, max_level)),

                () -> assertEquals(5218, f.apply(Job.FIGHTER, max_level)),
                () -> assertEquals(5218, f.apply(Job.CRUSADER, max_level)),
                () -> assertEquals(5218, f.apply(Job.HERO, max_level)),

                () -> assertEquals(4918, f.apply(Job.PAGE, max_level)),
                () -> assertEquals(4918, f.apply(Job.WHITEKNIGHT, max_level)),
                () -> assertEquals(4918, f.apply(Job.PALADIN, max_level)),

                () -> assertEquals(4918, f.apply(Job.SPEARMAN, max_level)),
                () -> assertEquals(4918, f.apply(Job.DRAGONKNIGHT, max_level)),
                () -> assertEquals(4918, f.apply(Job.DARKKNIGHT, max_level)),

                // Warrior (Cygnus)
                () -> assertEquals(2998, f.apply(Job.DAWNWARRIOR1, cygnus_max_level)),
                () -> assertEquals(3298, f.apply(Job.DAWNWARRIOR2, cygnus_max_level)),
                () -> assertEquals(3298, f.apply(Job.DAWNWARRIOR3, cygnus_max_level)),
                () -> assertEquals(3298, f.apply(Job.DAWNWARRIOR4, cygnus_max_level)),

                // Warrior (Aran)
                () -> assertEquals(4918, f.apply(Job.ARAN1, max_level)),
                () -> assertEquals(5218, f.apply(Job.ARAN2, max_level)),
                () -> assertEquals(5218, f.apply(Job.ARAN3, max_level)),
                () -> assertEquals(5218, f.apply(Job.ARAN4, max_level)),

                // Magician (Explorer)
                () -> assertEquals(2054, f.apply(Job.MAGICIAN, max_level)),

                () -> assertEquals(2054, f.apply(Job.FP_WIZARD, max_level)),
                () -> assertEquals(2054, f.apply(Job.FP_MAGE, max_level)),
                () -> assertEquals(2054, f.apply(Job.FP_ARCHMAGE, max_level)),

                () -> assertEquals(2054, f.apply(Job.IL_WIZARD, max_level)),
                () -> assertEquals(2054, f.apply(Job.IL_MAGE, max_level)),
                () -> assertEquals(2054, f.apply(Job.IL_ARCHMAGE, max_level)),

                () -> assertEquals(2054, f.apply(Job.CLERIC, max_level)),
                () -> assertEquals(2054, f.apply(Job.PRIEST, max_level)),
                () -> assertEquals(2054, f.apply(Job.BISHOP, max_level)),

                // Magician (Cygnus)
                () -> assertEquals(1254, f.apply(Job.BLAZEWIZARD1, cygnus_max_level)),
                () -> assertEquals(1254, f.apply(Job.BLAZEWIZARD2, cygnus_max_level)),
                () -> assertEquals(1254, f.apply(Job.BLAZEWIZARD3, cygnus_max_level)),
                () -> assertEquals(1254, f.apply(Job.BLAZEWIZARD4, cygnus_max_level)),

                // Bowman (Explorer)
                () -> assertEquals(4058, f.apply(Job.BOWMAN, max_level)),

                () -> assertEquals(4358, f.apply(Job.HUNTER, max_level)),
                () -> assertEquals(4358, f.apply(Job.RANGER, max_level)),
                () -> assertEquals(4358, f.apply(Job.BOWMASTER, max_level)),

                () -> assertEquals(4358, f.apply(Job.CROSSBOWMAN, max_level)),
                () -> assertEquals(4358, f.apply(Job.SNIPER, max_level)),
                () -> assertEquals(4358, f.apply(Job.MARKSMAN, max_level)),

                // Bowman (Cygnus)
                () -> assertEquals(2458, f.apply(Job.WINDARCHER1, cygnus_max_level)),
                () -> assertEquals(2758, f.apply(Job.WINDARCHER2, cygnus_max_level)),
                () -> assertEquals(2758, f.apply(Job.WINDARCHER3, cygnus_max_level)),
                () -> assertEquals(2758, f.apply(Job.WINDARCHER4, cygnus_max_level)),

                // Thief (Explorer)
                () -> assertEquals(4058, f.apply(Job.THIEF, max_level)),

                () -> assertEquals(4358, f.apply(Job.ASSASSIN, max_level)),
                () -> assertEquals(4358, f.apply(Job.HERMIT, max_level)),
                () -> assertEquals(4358, f.apply(Job.NIGHTLORD, max_level)),

                () -> assertEquals(4358, f.apply(Job.BANDIT, max_level)),
                () -> assertEquals(4358, f.apply(Job.CHIEFBANDIT, max_level)),
                () -> assertEquals(4358, f.apply(Job.SHADOWER, max_level)),

                // Thief (Cygnus)
                () -> assertEquals(2458, f.apply(Job.NIGHTWALKER1, cygnus_max_level)),
                () -> assertEquals(2758, f.apply(Job.NIGHTWALKER2, cygnus_max_level)),
                () -> assertEquals(2758, f.apply(Job.NIGHTWALKER3, cygnus_max_level)),
                () -> assertEquals(2758, f.apply(Job.NIGHTWALKER4, cygnus_max_level)),

                // Pirate (Explorer)
                () -> assertEquals(4438, f.apply(Job.PIRATE, max_level)),

                () -> assertEquals(4738, f.apply(Job.BRAWLER, max_level)),
                () -> assertEquals(4738, f.apply(Job.MARAUDER, max_level)),
                () -> assertEquals(4738, f.apply(Job.BUCCANEER, max_level)),

                () -> assertEquals(4738, f.apply(Job.GUNSLINGER, max_level)),
                () -> assertEquals(4738, f.apply(Job.OUTLAW, max_level)),
                () -> assertEquals(4738, f.apply(Job.CORSAIR, max_level)),

                // Pirate (Cygnus)
                () -> assertEquals(2678, f.apply(Job.THUNDERBREAKER1, cygnus_max_level)),
                () -> assertEquals(2978, f.apply(Job.THUNDERBREAKER2, cygnus_max_level)),
                () -> assertEquals(2978, f.apply(Job.THUNDERBREAKER3, cygnus_max_level)),
                () -> assertEquals(2978, f.apply(Job.THUNDERBREAKER4, cygnus_max_level))
        );
    }

    @Test
    void getMinMp() {
        int max_level = 200;
        int cygnus_max_level = 120;

        BiFunction<Job,Integer,Integer> f = AssignAPProcessor::getMinMp;

        assertAll(
                // Beginners
                () -> assertEquals(1995, f.apply(Job.BEGINNER, max_level)),
                () -> assertEquals(1195, f.apply(Job.NOBLESSE, cygnus_max_level)),

                // Warrior (Explorer)
                () -> assertEquals(855, f.apply(Job.WARRIOR, max_level)),

                () -> assertEquals(855, f.apply(Job.FIGHTER, max_level)),
                () -> assertEquals(855, f.apply(Job.CRUSADER, max_level)),
                () -> assertEquals(855, f.apply(Job.HERO, max_level)),

                () -> assertEquals(955, f.apply(Job.PAGE, max_level)),
                () -> assertEquals(955, f.apply(Job.WHITEKNIGHT, max_level)),
                () -> assertEquals(955, f.apply(Job.PALADIN, max_level)),

                () -> assertEquals(955, f.apply(Job.SPEARMAN, max_level)),
                () -> assertEquals(955, f.apply(Job.DRAGONKNIGHT, max_level)),
                () -> assertEquals(955, f.apply(Job.DARKKNIGHT, max_level)),

                // Warrior (Cygnus)
                () -> assertEquals(535, f.apply(Job.DAWNWARRIOR1, cygnus_max_level)),
                () -> assertEquals(535, f.apply(Job.DAWNWARRIOR2, cygnus_max_level)),
                () -> assertEquals(535, f.apply(Job.DAWNWARRIOR3, cygnus_max_level)),
                () -> assertEquals(535, f.apply(Job.DAWNWARRIOR4, cygnus_max_level)),

                // Warrior (Aran)
                () -> assertEquals(855, f.apply(Job.ARAN1, max_level)),
                () -> assertEquals(855, f.apply(Job.ARAN2, max_level)),
                () -> assertEquals(855, f.apply(Job.ARAN3, max_level)),
                () -> assertEquals(855, f.apply(Job.ARAN4, max_level)),

                // Magician (Explorer)
                () -> assertEquals(4399, f.apply(Job.MAGICIAN, max_level)),

                () -> assertEquals(4849, f.apply(Job.FP_WIZARD, max_level)),
                () -> assertEquals(4849, f.apply(Job.FP_MAGE, max_level)),
                () -> assertEquals(4849, f.apply(Job.FP_ARCHMAGE, max_level)),

                () -> assertEquals(4849, f.apply(Job.IL_WIZARD, max_level)),
                () -> assertEquals(4849, f.apply(Job.IL_MAGE, max_level)),
                () -> assertEquals(4849, f.apply(Job.IL_ARCHMAGE, max_level)),

                () -> assertEquals(4849, f.apply(Job.CLERIC, max_level)),
                () -> assertEquals(4849, f.apply(Job.PRIEST, max_level)),
                () -> assertEquals(4849, f.apply(Job.BISHOP, max_level)),

                // Magician (Cygnus)
                () -> assertEquals(2639, f.apply(Job.BLAZEWIZARD1, cygnus_max_level)),
                () -> assertEquals(3089, f.apply(Job.BLAZEWIZARD2, cygnus_max_level)),
                () -> assertEquals(3089, f.apply(Job.BLAZEWIZARD3, cygnus_max_level)),
                () -> assertEquals(3089, f.apply(Job.BLAZEWIZARD4, cygnus_max_level)),

                // Bowman (Explorer)
                () -> assertEquals(2785, f.apply(Job.BOWMAN, max_level)),

                () -> assertEquals(2935, f.apply(Job.HUNTER, max_level)),
                () -> assertEquals(2935, f.apply(Job.RANGER, max_level)),
                () -> assertEquals(2935, f.apply(Job.BOWMASTER, max_level)),

                () -> assertEquals(2935, f.apply(Job.CROSSBOWMAN, max_level)),
                () -> assertEquals(2935, f.apply(Job.SNIPER, max_level)),
                () -> assertEquals(2935, f.apply(Job.MARKSMAN, max_level)),

                // Bowman (Cygnus)
                () -> assertEquals(1665, f.apply(Job.WINDARCHER1, cygnus_max_level)),
                () -> assertEquals(1815, f.apply(Job.WINDARCHER2, cygnus_max_level)),
                () -> assertEquals(1815, f.apply(Job.WINDARCHER3, cygnus_max_level)),
                () -> assertEquals(1815, f.apply(Job.WINDARCHER4, cygnus_max_level)),

                // Thief (Explorer)
                () -> assertEquals(2785, f.apply(Job.THIEF, max_level)),

                () -> assertEquals(2935, f.apply(Job.ASSASSIN, max_level)),
                () -> assertEquals(2935, f.apply(Job.HERMIT, max_level)),
                () -> assertEquals(2935, f.apply(Job.NIGHTLORD, max_level)),

                () -> assertEquals(2935, f.apply(Job.BANDIT, max_level)),
                () -> assertEquals(2935, f.apply(Job.CHIEFBANDIT, max_level)),
                () -> assertEquals(2935, f.apply(Job.SHADOWER, max_level)),

                // Thief (Cygnus)
                () -> assertEquals(1665, f.apply(Job.NIGHTWALKER1, cygnus_max_level)),
                () -> assertEquals(1815, f.apply(Job.NIGHTWALKER2, cygnus_max_level)),
                () -> assertEquals(1815, f.apply(Job.NIGHTWALKER3, cygnus_max_level)),
                () -> assertEquals(1815, f.apply(Job.NIGHTWALKER4, cygnus_max_level)),

                // Pirate (Explorer)
                () -> assertEquals(3545, f.apply(Job.PIRATE, max_level)),

                () -> assertEquals(3695, f.apply(Job.BRAWLER, max_level)),
                () -> assertEquals(3695, f.apply(Job.MARAUDER, max_level)),
                () -> assertEquals(3695, f.apply(Job.BUCCANEER, max_level)),

                () -> assertEquals(3695, f.apply(Job.GUNSLINGER, max_level)),
                () -> assertEquals(3695, f.apply(Job.OUTLAW, max_level)),
                () -> assertEquals(3695, f.apply(Job.CORSAIR, max_level)),

                // Pirate (Cygnus)
                () -> assertEquals(2105, f.apply(Job.THUNDERBREAKER1, cygnus_max_level)),
                () -> assertEquals(2255, f.apply(Job.THUNDERBREAKER2, cygnus_max_level)),
                () -> assertEquals(2255, f.apply(Job.THUNDERBREAKER3, cygnus_max_level)),
                () -> assertEquals(2255, f.apply(Job.THUNDERBREAKER4, cygnus_max_level))
        );
    }

    /**
     * Every branch of getMinHp/getMinMp is an {@code isA} chain rooted at an Explorer or Cygnus job,
     * plus a {@code job == BEGINNER || job == NOBLESSE} case. Evan (2001, 2200-2218) matches none of
     * them - {@code EVAN1.isA(MAGICIAN)} is false because 2200/100 is 22, not 2 - and neither does
     * Aran's LEGEND (2000), so both fall out with multiplier 0 and offset 0. That is the AP-reset
     * floor: {@code getMaxHp() + hplose < getMinHp(...)} can never fire, so an Evan can drain max HP
     * or MP with AP Reset past the point every other class is stopped at. Pinning the wrong values.
     */
    @Test
    void evanAndLegendHaveNoMinimumHpMpFloor() {
        assertAll(
                () -> assertEquals(0, AssignAPProcessor.getMinHp(Job.LEGEND, 200), "job 2000 HP"),
                () -> assertEquals(0, AssignAPProcessor.getMinHp(Job.EVAN, 200), "job 2001 HP"),
                () -> assertEquals(0, AssignAPProcessor.getMinHp(Job.EVAN1, 200), "job 2200 HP"),
                () -> assertEquals(0, AssignAPProcessor.getMinHp(Job.EVAN10, 200), "job 2218 HP"),
                () -> assertEquals(0, AssignAPProcessor.getMinMp(Job.LEGEND, 200), "job 2000 MP"),
                () -> assertEquals(0, AssignAPProcessor.getMinMp(Job.EVAN, 200), "job 2001 MP"),
                () -> assertEquals(0, AssignAPProcessor.getMinMp(Job.EVAN1, 200), "job 2200 MP"),
                () -> assertEquals(0, AssignAPProcessor.getMinMp(Job.EVAN10, 200), "job 2218 MP")
        );
    }

    private static int apChange(String method, Job job) throws Exception {
        Character chr = Character.getDefault(Mockito.mock(Client.class));
        chr.setJob(job);

        Method calc = AssignAPProcessor.class.getDeclaredMethod(method, Character.class, boolean.class);
        calc.setAccessible(true);
        return (int) calc.invoke(null, chr, true);    // the AP-reset path, the one that is not random
    }

    /**
     * Evan is an INT class and must gain HP/MP like a magician: 6 HP and 18 MP per AP point.
     *
     * <p>He did not, until this was fixed. {@code Job.isA} compares {@code id/100} - 22 for Evan
     * against 2 for MAGICIAN - so {@code EVAN1.isA(MAGICIAN)} is <b>false</b> and every Evan fell
     * into the trailing "everything else" case at 8 HP and 6 MP. Every AP point an Evan spent was
     * worth a third of a magician's MP, and the loss is permanent because MP is banked per point.
     * The owner hit it in play at job 2200. Fixed by naming {@code Job.EVAN1} alongside MAGICIAN and
     * BLAZEWIZARD1 in the five {@code isA} chains of this class; {@code isA} itself was left alone,
     * because its {@code id/100} rule is load-bearing everywhere else.
     */
    @Test
    void evanGainsHpAndMpAsAMagician() throws Exception {
        assertAll(
                () -> assertEquals(6, apChange("calcHpChange", Job.MAGICIAN), "magician HP per AP"),
                () -> assertEquals(18, apChange("calcMpChange", Job.MAGICIAN), "magician MP per AP"),
                () -> assertEquals(6, apChange("calcHpChange", Job.EVAN1), "job 2200 HP per AP"),
                () -> assertEquals(18, apChange("calcMpChange", Job.EVAN1), "job 2200 MP per AP"),
                () -> assertEquals(6, apChange("calcHpChange", Job.EVAN10), "job 2218 HP per AP"),
                () -> assertEquals(18, apChange("calcMpChange", Job.EVAN10), "job 2218 MP per AP")
        );
    }
}