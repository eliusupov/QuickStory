package constants.game;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GameConstantsExpRateTest {

    @ParameterizedTest
    @CsvSource({
            "1, 1.0", "9, 1.0", "10, 1.275", "15, 2.55", "20, 3.825", "25, 5.1",
            "30, 6.375", "35, 7.65", "40, 8.925", "50, 10.2", "100, 11.475", "120, 12.75"
    })
    void expRateCurveIsReducedByFifteenPercentAndNeverBelowOne(int level, float expectedRate) {
        assertEquals(expectedRate, GameConstants.getExpRateForLevel(level));
    }
}
