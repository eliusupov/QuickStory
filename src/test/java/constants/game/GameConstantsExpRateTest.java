package constants.game;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameConstantsExpRateTest {

    @ParameterizedTest
    @CsvSource({
            "1, 1.0", "14, 1.0", "15, 1.25", "16, 1.5", "19, 2.25", "20, 2.5",
            "21, 2.7", "24, 3.3", "25, 3.5", "40, 4.0", "50, 5.0", "70, 6.0",
            "100, 7.0", "120, 8.0", "150, 10.0", "200, 10.0"
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
