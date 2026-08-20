package constants.game;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GameConstantsExpRateTest {

    @ParameterizedTest
    @CsvSource({
            "1, 1.0", "9, 1.0", "10, 1.2", "15, 2.4", "20, 3.6", "25, 4.8",
            "30, 6.0", "35, 7.2", "40, 8.4", "50, 9.6", "100, 10.8", "120, 12.0"
    })
    void expRateCurveIsReducedByTwentyPercentAndNeverBelowOne(int level, float expectedRate) {
        assertEquals(expectedRate, GameConstants.getExpRateForLevel(level));
    }
}
