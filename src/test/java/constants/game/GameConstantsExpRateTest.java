package constants.game;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GameConstantsExpRateTest {

    @ParameterizedTest
    @CsvSource({
            "1, 1.0", "14, 1.0", "15, 2.0", "20, 3.0", "25, 4.0", "34, 4.0",
            "35, 5.0", "40, 6.0", "50, 7.0", "100, 8.0", "120, 10.0"
    })
    void expRateCurveMatchesTheApprovedRoundedBands(int level, float expectedRate) {
        assertEquals(expectedRate, GameConstants.getExpRateForLevel(level));
    }
}
