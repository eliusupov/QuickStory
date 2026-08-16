package server;

import org.junit.jupiter.api.Test;
import provider.Data;
import provider.DataProvider;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static server.V84Wz.wz;

/**
 * Ticket 16 - the three regression facts that nothing else pins.
 * <p>
 * Deliberately small. {@link V84QuestNodeTest} already asserts the refused {@code lvmax} and date
 * rows are absent from the <em>server</em> XML, on a five-id sample; {@link V84CosmeticNodeTest}
 * already pins the Ezorsia hair names and the level-up medals. What no test covered before this
 * one:
 * <ol>
 *   <li>the same refusals hold on the <em>client</em> side, i.e. every one of those rows is on
 *       {@code COLLISION-DENY.txt}, so a future composed merge that consumed the raw add-list
 *       would still refuse them - all 123, not a sample;</li>
 *   <li>no dropper that already had a drop table gained rows from changeSet 153 or 154;</li>
 *   <li>the 2,818 quests the live client shipped before the v84 merge are all still there.</li>
 * </ol>
 * Opens the tree through {@link V84Wz} for the JVM-wide {@code wz-path} reason documented there.
 */
class V84RegressionTest {

    private static final Path ADD_LIST = Path.of("docs", "wz-baseline", "add-list", "Quest.txt");
    private static final Path DENY_LIST = Path.of("docs", "wz-baseline", "merge-lists", "COLLISION-DENY.txt");
    private static final Path NINE_PATHS = Path.of("docs", "wz-baseline", "merge-lists", "09", "Quest.paths.txt");
    /**
     * Ticket 33's 135 Evan ids. Subtracted alongside ticket 09's for the same reason: this test
     * counts what PREDATES the v84 merges, so every id a merge added has to come off the total or
     * the next additive merge turns a deletion check into a "the count moved" check.
     */
    private static final Path THIRTY_THREE_PATHS = Path.of("docs", "wz-baseline", "merge-lists", "33", "Quest.paths.txt");
    private static final Path SQL = Path.of("src", "main", "resources", "db", "data");

    /**
     * Quest ids per category image before the v84 merge, measured at {@code 94e66d80c} (ticket 16).
     * The four counts differ because not every quest has a row in every image - {@code 9800} is in
     * {@code QuestInfo.img} and in no other - so "2,818 quests" is the {@code QuestInfo} figure and
     * the other three are their own numbers, not a discrepancy.
     */
    private static final Map<String, Integer> QUESTS_BEFORE_THE_MERGE = Map.of(
            "QuestInfo.img", 2818, "Act.img", 2824, "Check.img", 2807, "Say.img", 2801);

    /** {@code lvmax} rows onto live beginner quests 28162-28325, all uniformly {@code 40}. */
    private static final int LVMAX_ROWS = 108;

    /** {@code start}/{@code end}/{@code interval}/{@code dayByDay} rows onto live quests. */
    private static final int DATE_ROWS = 15;

    private static List<String> rows(Path p) throws IOException {
        return Files.readAllLines(p, StandardCharsets.UTF_8).stream()
                .map(l -> l.replace("\r", "").trim())
                .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                .toList();
    }

    /** The quest ids a merge path list adds, taken off its {@code QuestInfo.img} rows. */
    private static List<Integer> questInfoIds(Path pathList) throws IOException {
        return rows(pathList).stream()
                .filter(r -> r.startsWith("Quest.wz/QuestInfo.img/"))
                .map(r -> Integer.parseInt(r.substring(r.lastIndexOf('/') + 1)))
                .toList();
    }

    /** A deny row is a ROOT: it reaches everything beneath it, which is why no wildcard exists. */
    private static boolean denied(List<String> deny, String path) {
        return deny.stream().anyMatch(d -> path.equals(d) || path.startsWith(d + "/"));
    }

    // ------------------------------------------------------------------ 1. client-side refusals

    /**
     * Absence from the composed path list is why these never merged, but absence is a choice a
     * later ticket can undo by regenerating a list from {@code add-list/Quest.txt}. The deny-list
     * is the part that survives that, and {@code --deny} is required on every merge, so this is
     * the assertion that makes the refusal structural rather than incidental.
     */
    @Test
    void everyHarmfulQuestRowIsOnTheDenyListNotMerelyOffTheMergeList() throws IOException {
        List<String> deny = rows(DENY_LIST).stream()
                .map(l -> l.split("\t")[0].split("#")[0].trim())
                .filter(l -> !l.isEmpty())
                .toList();
        List<String> add = rows(ADD_LIST);

        List<String> lvmax = add.stream().filter(r -> r.endsWith("/0/lvmax")).toList();
        assertEquals(LVMAX_ROWS, lvmax.size(), "add-list/Quest.txt lvmax row count moved");
        for (String row : lvmax) {
            assertTrue(denied(deny, row), row + " caps a working v83 quest at Lv.40 and is not denied");
        }

        List<String> dates = add.stream()
                .filter(r -> r.endsWith("/0/start") || r.endsWith("/0/end")
                        || r.endsWith("/0/interval") || r.endsWith("/0/dayByDay"))
                .toList();
        assertEquals(DATE_ROWS, dates.size(), "add-list/Quest.txt date row count moved");
        for (String row : dates) {
            assertTrue(denied(deny, row), row + " puts a dead date window on a live quest and is not denied");
        }

        // negative control: the rows ticket 09 DID merge must not be denied, or the merge is inert.
        for (String row : rows(NINE_PATHS)) {
            assertFalse(denied(deny, row), row + " is both merged and denied");
        }
    }

    // ------------------------------------------------------------------ 2. drop tables

    /**
     * changeSets 153 and 154 are additive by construction only if no dropper they touch already
     * had rows in 152. An overlap would not fail Liquibase - it would silently double a live
     * mob's drop table.
     */
    @Test
    void theNewDropChangeSetsTouchNoDropperThatAlreadyHadRows() throws IOException {
        Set<Integer> existing = droppers("152-drop-data.sql");
        assertFalse(existing.isEmpty(), "152-drop-data.sql parsed to zero droppers - parser is wrong");
        for (String file : List.of("153-crimson-sky-drop-data.sql", "154-neo-city-2227-drop-data.sql")) {
            Set<Integer> added = droppers(file);
            assertFalse(added.isEmpty(), file + " parsed to zero droppers");
            Set<Integer> overlap = new TreeSet<>(added);
            overlap.retainAll(existing);
            assertEquals(0, overlap.size(),
                    file + " adds rows to " + overlap.size() + " dropper(s) 152 already covers, e.g. "
                            + overlap.stream().limit(5).toList());
        }
    }

    private static Set<Integer> droppers(String file) throws IOException {
        Pattern row = Pattern.compile("^\\s*\\((\\d+),", Pattern.MULTILINE);
        Matcher m = row.matcher(Files.readString(SQL.resolve(file), StandardCharsets.UTF_8));
        Set<Integer> ids = new TreeSet<>();
        while (m.find()) {
            ids.add(Integer.parseInt(m.group(1)));
        }
        return ids;
    }

    // ------------------------------------------------------------------ 3. existing quests

    /**
     * The merges added 63 (ticket 09) + 135 (ticket 33) quest ids and must have removed none.
     * Counting rather than listing keeps this honest: a merge that dropped one live quest and
     * added one of its own would still leave the total right, so the four category images are
     * checked to agree and the count of ids that are NEITHER ticket's is what is asserted.
     */
    @Test
    void the2818QuestsThatPredateTheMergeAreAllStillPresent() throws IOException {
        DataProvider quest = wz("Quest.wz");
        Set<Integer> merged = new TreeSet<>(questInfoIds(NINE_PATHS));
        assertEquals(63, merged.size(), "ticket 09 merged 63 quest ids");
        Set<Integer> evan = new TreeSet<>(questInfoIds(THIRTY_THREE_PATHS));
        assertEquals(135, evan.size(), "ticket 33 merged 135 Evan quest ids");
        assertEquals(Set.of(), evan.stream().filter(merged::contains).collect(java.util.stream.Collectors.toSet()),
                "the two merges must not claim the same id");
        merged.addAll(evan);

        Set<Integer> preexisting = new TreeSet<>();
        for (Map.Entry<String, Integer> e : QUESTS_BEFORE_THE_MERGE.entrySet()) {
            Data image = quest.getData(e.getKey());
            assertNotNull(image, e.getKey());
            Set<Integer> ids = new TreeSet<>();
            for (Data child : image.getChildren()) {
                ids.add(Integer.parseInt(child.getName()));
            }
            ids.removeAll(merged);
            assertEquals(e.getValue(), ids.size(),
                    e.getKey() + ": quest ids that predate the v84 merge - a drop here means the "
                            + "merge deleted live content");
            preexisting.addAll(ids);
        }

        Data check = quest.getData("Check.img");
        // none of the 108 v84 would have capped picked one up. 325 live quests carry a
        // legitimate lvmax of their own, so this asserts the 108 targeted ids, not "no lvmax".
        for (String row : rows(ADD_LIST)) {
            if (!row.endsWith("/0/lvmax")) {
                continue;
            }
            String id = row.split("/")[2];
            assertTrue(preexisting.contains(Integer.parseInt(id)),
                    "add-list targets Check.img/" + id + " which is not a pre-existing quest");
            assertNull(check.getChildByPath(id + "/0/lvmax"),
                    "Check.img/" + id + "/0/lvmax was merged - that caps a working v83 quest at Lv.40");
        }
    }
}
