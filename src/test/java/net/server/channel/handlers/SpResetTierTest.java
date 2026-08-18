package net.server.channel.handlers;

import constants.game.GameConstants;
import constants.skills.Evan;
import constants.skills.FPArchMage;
import constants.skills.FPMage;
import constants.skills.FPWizard;
import constants.skills.Magician;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SP reset scrolls are sold per advancement. Before {@code spResetCovers} the handler read
 * SPTo/SPFrom and moved the point with no tier test at all, so a 1st-job scroll rebuilt a 4th-job
 * build and Evan's five scrolls were interchangeable.
 *
 * <p>The tiers asserted here are the ones the item's own String.wz/Cash.img entry states, read out
 * of the pristine v84 archive at
 * {@code D:\games\MapleStory\Server\porting-resources\wz-data\v84\String.wz}: 5050001-5050004 are
 * "SP Reset (1st..4th job)", 5050005-5050009 are "Evan SP Reset (1st/2nd .. 9th/10th Skill)", and
 * the family stops at 5050009. The vanilla asymmetry - target pinned to the scroll's tier, source
 * allowed at or below it - comes from those same descs; see spResetCovers' javadoc.
 */
class SpResetTierTest {

    @Test
    void evanTenAdvancementsMapToTenBranches() {
        // Evan's jobs are 2200 then 2210-2218, not the x00/x10/x11/x12 shape `job % 10` assumes.
        assertEquals(1, GameConstants.getSkillBranch(Evan.MAGIC_MISSILE));        // 2200
        assertEquals(2, GameConstants.getSkillBranch(Evan.FIRE_CIRCLE));          // 2210
        assertEquals(3, GameConstants.getSkillBranch(Evan.LIGHTNING_BOLT));       // 2211
        assertEquals(4, GameConstants.getSkillBranch(Evan.ICE_BREATH));           // 2212
        assertEquals(5, GameConstants.getSkillBranch(Evan.MAGIC_FLARE));          // 2213
        assertEquals(6, GameConstants.getSkillBranch(Evan.DRAGON_THRUST));        // 2214
        assertEquals(7, GameConstants.getSkillBranch(Evan.FIRE_BREATH));          // 2215
        assertEquals(8, GameConstants.getSkillBranch(Evan.EARTHQUAKE));           // 2216
        assertEquals(9, GameConstants.getSkillBranch(Evan.MAPLE_WARRIOR));        // 2217
        assertEquals(10, GameConstants.getSkillBranch(Evan.BLESSING_OF_THE_ONYX));// 2218

        // 20011000-20011002 are Evan's beginner book: job 2001, the one beginner id that is not a
        // round thousand and so reads as a 3rd job through plain getJobBranch arithmetic.
        assertEquals(0, GameConstants.getSkillBranch(20011000));
    }

    @Test
    void eachEvanScrollCoversItsOwnPairOfAdvancements() {
        assertCovers(5050005, Evan.MAGIC_MISSILE, Evan.FIRE_CIRCLE);           // 1st/2nd
        assertCovers(5050006, Evan.LIGHTNING_BOLT, Evan.ICE_BREATH);           // 3rd/4th
        assertCovers(5050007, Evan.MAGIC_FLARE, Evan.DRAGON_THRUST);           // 5th/6th
        assertCovers(5050008, Evan.FIRE_BREATH, Evan.EARTHQUAKE);              // 7th/8th
        assertCovers(5050009, Evan.MAPLE_WARRIOR, Evan.BLESSING_OF_THE_ONYX);  // 9th/10th

        // the defect: the 1st/2nd scroll moving a point between 9th and 10th job skills
        assertFalse(spResetCovers(5050005, Evan.MAPLE_WARRIOR, Evan.BLESSING_OF_THE_ONYX));
        // either end being out of the pair is refused, whichever end it is
        assertFalse(spResetCovers(5050006, Evan.LIGHTNING_BOLT, Evan.FIRE_CIRCLE));
        assertFalse(spResetCovers(5050006, Evan.FIRE_CIRCLE, Evan.ICE_BREATH));
    }

    @Test
    void evanScrollsDoNotReachBelowTheirPair() {
        // Unlike the vanilla scrolls, whose descs grant a lower-tier source outright, Evan's say
        // only which advancement they apply to. Both ends stay inside the pair.
        assertFalse(spResetCovers(5050009, Evan.MAPLE_WARRIOR, Evan.EARTHQUAKE));
        assertFalse(spResetCovers(5050007, Evan.MAGIC_FLARE, Evan.ICE_BREATH));
    }

    @Test
    void eachVanillaScrollCoversItsOwnJob() {
        assertCovers(5050001, Magician.MAGIC_CLAW, Magician.IMPROVED_MP_RECOVERY);
        assertCovers(5050002, FPWizard.FIRE_ARROW, FPWizard.MP_EATER);
        assertCovers(5050003, FPMage.EXPLOSION, FPMage.PARTIAL_RESISTANCE);
        assertCovers(5050004, FPArchMage.BIG_BANG, FPArchMage.MAPLE_WARRIOR);

        // a target outside the scroll's own tier is the original defect, in both directions
        assertFalse(spResetCovers(5050001, FPArchMage.BIG_BANG, Magician.MAGIC_CLAW));
        assertFalse(spResetCovers(5050004, Magician.MAGIC_CLAW, FPArchMage.BIG_BANG));

        // the two families do not overlap in either direction. 5050006 covering branches 3 and 4 is
        // why: on an explorer it would be a 3rd<->4th job reset, the exact defect being fixed.
        assertFalse(spResetCovers(5050005, Magician.MAGIC_CLAW, Magician.IMPROVED_MP_RECOVERY));
        assertFalse(spResetCovers(5050006, FPMage.EXPLOSION, FPArchMage.BIG_BANG));
        assertFalse(spResetCovers(5050002, Evan.FIRE_CIRCLE, Evan.FIRE_CIRCLE));
        assertFalse(spResetCovers(5050004, Evan.ICE_BREATH, Evan.ICE_BREATH));
    }

    @Test
    void vanillaScrollsTakeTheirSourceFromAnyLowerJob() {
        // v84's own desc: "The 1st job SP raised AFTER the 2nd job adv. can also be reset and
        // applied to a 2nd job skill." The target stays pinned; the source may sit below it.
        assertTrue(spResetCovers(5050002, FPWizard.FIRE_ARROW, Magician.MAGIC_CLAW));
        assertTrue(spResetCovers(5050003, FPMage.EXPLOSION, FPWizard.FIRE_ARROW));
        assertTrue(spResetCovers(5050003, FPMage.EXPLOSION, Magician.MAGIC_CLAW));
        assertTrue(spResetCovers(5050004, FPArchMage.BIG_BANG, Magician.MAGIC_CLAW));

        // downward only - a higher-tier source with a lower-tier target is still the defect
        assertFalse(spResetCovers(5050002, FPWizard.FIRE_ARROW, FPMage.EXPLOSION));
        assertFalse(spResetCovers(5050003, FPMage.EXPLOSION, FPArchMage.BIG_BANG));

        // and the beginner book is not a source: branch 0 is below every scroll's tier
        assertFalse(spResetCovers(5050001, Magician.MAGIC_CLAW, 1000));
    }

    @Test
    void nothingOutsideTheFamilyIsCovered() {
        assertFalse(spResetCovers(5050010, Evan.BLESSING_OF_THE_ONYX, Evan.BLESSING_OF_THE_ONYX));
        assertFalse(spResetCovers(5050004, 9101004, 9101004));   // GM skills, no job
    }

    private static boolean spResetCovers(int itemId, int skillTo, int skillFrom) {
        return UseCashItemHandler.spResetCovers(itemId, skillTo, skillFrom);
    }

    /** Both orderings, for a pair the scroll is supposed to accept either way round. */
    private static void assertCovers(int itemId, int skillA, int skillB) {
        assertTrue(spResetCovers(itemId, skillA, skillB), itemId + ": " + skillA + " <- " + skillB);
        assertTrue(spResetCovers(itemId, skillB, skillA), itemId + ": " + skillB + " <- " + skillA);
    }
}
