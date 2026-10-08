package constants.game;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameConstantsExpRateTest {

    @ParameterizedTest
    @CsvSource({
            "1, 1.5", "14, 1.5", "15, 1.875", "16, 2.25", "19, 3.375", "20, 3.75",
            "21, 4.05", "24, 4.95", "25, 5.25", "40, 6.0", "50, 7.5", "70, 9.0",
            "100, 10.5", "120, 12.0", "150, 15.0", "200, 15.0"
    })
    void expRateCurveMatchesTheApprovedMilestones(int level, float expectedRate) {
        assertEquals(expectedRate, GameConstants.getExpRateForLevel(level), 0.0001f);
    }

    @Test
    void effectiveExpRequirementNeverDropsBetweenLevels() {
        double previous = 0;
        for (int level = 1; level < 200; level++) {
            double effective = ExpTable.getExpNeededForLevel(level) / GameConstants.getExpRateForLevel(level);
            assertTrue(effective >= previous, "effective EXP dropped at level " + level);
            previous = effective;
        }
    }
}
