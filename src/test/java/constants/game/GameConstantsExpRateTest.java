package constants.game;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameConstantsExpRateTest {

    @ParameterizedTest
    @CsvSource({
            "1, 1.3", "14, 1.3", "15, 1.625", "16, 1.95", "19, 2.925", "20, 3.25",
            "21, 3.51", "24, 4.29", "25, 4.55", "40, 5.2", "50, 6.5", "70, 7.8",
            "100, 9.1", "120, 10.4", "150, 13.0", "200, 13.0"
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
