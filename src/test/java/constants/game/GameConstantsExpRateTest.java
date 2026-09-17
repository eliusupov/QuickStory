package constants.game;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GameConstantsExpRateTest {

    @ParameterizedTest
    @CsvSource({
            "1, 1.0", "14, 1.0", "15, 2.0", "19, 2.0", "20, 2.5", "24, 2.5",
            "25, 3.5", "39, 3.5", "40, 4.0", "49, 4.0", "50, 5.0", "69, 5.0",
            "70, 6.0", "99, 6.0", "100, 7.0", "119, 7.0", "120, 8.0", "149, 8.0",
            "150, 10.0", "200, 10.0"
    })
    void expRateCurveMatchesTheApprovedBands(int level, float expectedRate) {
        assertEquals(expectedRate, GameConstants.getExpRateForLevel(level));
    }
}
