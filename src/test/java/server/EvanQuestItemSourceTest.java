package server;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The three Evan quests whose required item had no source in the tree.
 *
 * <p>Every one of these is the same shape: {@code Act.img} is empty, the item is named only by
 * v84's own {@code QuestInfo} text, and the quest that demands it is unstartable or uncompletable
 * until some script hands it over. Ticket 09 wrote all three as state-only, which left them
 * accepted and permanently stuck.
 *
 * <ul>
 *   <li>{@code 2344} "Mushking Empire in Danger" - the Evan copy of {@code 2300}-{@code 2310}.
 *       Completion at {@code 1300005} wants 1x {@code 4032375}; the eleven siblings hand it out
 *       from their own start scripts.</li>
 *   <li>{@code 22602}/{@code 22603} "After Shedding" - Mir gives a shed scale ({@code 4032502} at
 *       Lv.80, {@code 4032503} at Lv.120). The scale is the sole Maker ingredient for medals
 *       {@code 1142156}/{@code 1142157}, which are the START requirement of medal quests
 *       {@code 29938}/{@code 29939}. No scale, no medal, no medal quest.</li>
 * </ul>
 *
 * <p>Reads text, not WZ, so it is a plain {@code *Test}: no {@code WZFiles.DIRECTORY}.
 */
class EvanQuestItemSourceTest {

    private static String read(String... parts) throws IOException {
        return Files.readString(Path.of(parts[0], java.util.Arrays.copyOfRange(parts, 1, parts.length)),
                StandardCharsets.UTF_8);
    }

    @Test
    void quest2344HandsOutAndConsumesTheRecommendationLetter() throws IOException {
        String js = read("scripts", "quest", "2344.js");
        assertTrue(js.contains("qm.gainItem(4032375, 1)"), "2344 start must give the letter");
        assertTrue(js.contains("qm.gainItem(4032375, -1)"), "2344 end must consume the letter");
    }

    @Test
    void mirHandsOverBothShedScales() throws IOException {
        assertTrue(read("scripts", "quest", "22602.js").contains("qm.gainItem(4032502, 1)"),
                "22602 must give the Lv.80 Dragon Scale");
        assertTrue(read("scripts", "quest", "22603.js").contains("qm.gainItem(4032503, 1)"),
                "22603 must give the Lv.120 Shiny Dragon Scale");
    }

    /**
     * The chain those scales exist for. If the Maker rows move, the scale ids above are the wrong
     * ones and the medal quests go back to being unstartable.
     */
    @Test
    void theScalesAreTheMakerIngredientsForTheTwoDragonMasterMedals() throws IOException {
        String sql = read("src", "main", "resources", "db", "data", "158-maker-v84-data.sql");
        assertTrue(sql.contains("(1142156, 4032502, 1)"), "medal 1142156 is made from 4032502");
        assertTrue(sql.contains("(1142157, 4032503, 1)"), "medal 1142157 is made from 4032503");

        String check = read("wz", "Quest.wz", "Check.img.xml");
        assertTrue(check.contains("<int name=\"id\" value=\"1142156\"/>"),
                "29938 still requires the medal it cannot otherwise obtain");
        assertTrue(check.contains("<int name=\"id\" value=\"1142157\"/>"),
                "29939 still requires the medal it cannot otherwise obtain");
    }
}
