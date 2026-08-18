package server;

import org.junit.jupiter.api.Test;
import provider.Data;
import provider.DataProviderFactory;
import provider.DataTool;
import provider.wz.WZFiles;
import server.quest.Quest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the owner's "quest requirements are halved" rule, which is <strong>data, not code</strong>:
 * every {@code count} under {@code Quest.wz/Check.img} — {@code mob} (kill) and {@code item}
 * (collect / pick up) alike — is stored at {@code ceil(n / 2)}, never below 1, and the matching
 * negative {@code count} in {@code Act.img} (what the hand-in takes) is halved with it. Rewards
 * (positive {@code Act.img} counts) and every non-counting requirement — level, job, meso,
 * completed-quest, item-equipped — are untouched.
 *
 * <pre>
 *   mvnw.cmd test -Dtest=QuestRequirementHalvingRealLoad
 * </pre>
 *
 * <p><strong>Not a {@code *Test} class on purpose</strong>, same reason as its siblings:
 * {@link WZFiles#DIRECTORY} is a {@code static final} resolved once per JVM.
 *
 * <p><strong>Why this class exists.</strong> The rule has no server-side enforcement to unit-test —
 * {@link Quest#getMobAmountNeeded(int)} and {@code ItemRequirement} just read the number the WZ
 * gives them, and both the progress cap ({@code QuestStatus.progress}, {@code QuestStatus.java:164})
 * and the completion predicate ({@code MobRequirement.check}, {@code MobRequirement.java:75}) read
 * that same number, so they cannot disagree with each other. What they <em>can</em> disagree with is
 * the client, which holds its own copy of these counts and is what actually gates completion
 * ({@code QuestInfo.img/&lt;id&gt;/autoComplete}). A WZ merge that reintroduces a pristine count on
 * either side soft-locks the quest: the server stops counting at its number and the client never
 * reaches its own. That is exactly how quest 2132 broke. This class fails the moment the server
 * side drifts back.
 *
 * <p>{@code docs/wz-baseline/tool-questsync} pushes these same numbers into a client {@code Quest.wz}
 * so the two stay in step.
 */
class QuestRequirementHalvingRealLoad {

    /**
     * {questId, mobId, pristine count, halved count}. Pristine values read out of the untouched
     * v84 archive with {@code docs/wz-baseline/tool-peek}.
     */
    private static final int[][] MOB_REQUIREMENTS = {
            {2132, 210100, 8, 4},       // "Beginner Magician's First Training Session" - the soft-locked one
            {2108, 2230100, 99, 50},    // odd: rounds UP
            {3202, 3230303, 25, 13},    // odd: rounds UP
            {1023, 1210100, 2, 1},      // even, lands on the floor
            {1018, 9300018, 1, 1},      // already 1 - the floor holds, a kill quest never becomes unkillable
    };

    /** {questId, itemId, pristine count, halved count} - the "collect"/"pick up" half of the rule. */
    private static final int[][] ITEM_REQUIREMENTS = {
            {10005, 4031701, 5, 3},     // odd: rounds UP
            {10010, 4031999, 20, 10},
            {1001, 4031003, 1, 1},      // already 1 - a hand-in of exactly one stays exactly one
    };

    private static void assertTreeIsLoaded() {
        assertTrue(Files.isRegularFile(Path.of(WZFiles.DIRECTORY, "Quest.wz", "Check.img.xml")),
                "wz-path resolved to '" + WZFiles.DIRECTORY + "', which holds no Quest.wz - another "
                        + "test class won the WZFiles.DIRECTORY race, so this says nothing about the data");
    }

    @Test
    void killRequirementsAreHalvedRoundingUpAndNeverBelowOne() {
        assertTreeIsLoaded();
        for (int[] row : MOB_REQUIREMENTS) {
            int questId = row[0], mobId = row[1], pristine = row[2], halved = row[3];
            assertEquals(Math.max(1, (pristine + 1) / 2), halved,
                    "fixture for quest " + questId + " does not follow ceil(n/2) min 1");
            assertEquals(halved, Quest.getInstance(questId).getMobAmountNeeded(mobId),
                    "quest " + questId + " mob " + mobId + " (pristine " + pristine + ")");
        }
    }

    @Test
    void collectRequirementsAreHalvedTheSameWay() {
        assertTreeIsLoaded();
        for (int[] row : ITEM_REQUIREMENTS) {
            int questId = row[0], itemId = row[1], pristine = row[2], halved = row[3];
            assertEquals(Math.max(1, (pristine + 1) / 2), halved,
                    "fixture for quest " + questId + " does not follow ceil(n/2) min 1");
            assertEquals(halved, Quest.getInstance(questId).getCompleteItemAmountNeeded(itemId),
                    "quest " + questId + " item " + itemId + " (pristine " + pristine + ")");
        }
    }

    /**
     * Every {@code count 0} requirement in the tree, and there are exactly these. A zero is not a
     * quantity: {@code ItemRequirement.check} inverts it into "you must NOT hold this item"
     * ({@code ItemRequirement.java:93}). All thirteen are zero in the pristine v84 archive too, so
     * none of them came from the halving - which is the point of the list. {@code ceil(n/2)} with a
     * floor of 1 cannot produce a new one, and if a future edit ever does, that requirement silently
     * inverts and the quest becomes uncompletable in a way no log will mention.
     */
    private static final List<String> PRISTINE_ZERO_COUNTS = List.of(
            "10230/0/item/0", "10230/0/item/1", "28103/0/item/0", "8220/1/item/0", "8850/0/item/0",
            "8851/0/item/0", "8852/0/item/0", "8853/0/item/0", "8854/0/item/0", "8871/0/item/0",
            "9951/0/item/0", "9951/0/item/1");

    /**
     * The floor, across the whole tree rather than the eight ids above. Nothing here says the counts
     * <em>are</em> halved; that is the job of the two tests above. This says the halving never
     * overshot into a zero.
     */
    @Test
    void noRequirementCountWasHalvedToZero() {
        assertTreeIsLoaded();
        Data check = DataProviderFactory.getDataProvider(WZFiles.QUEST).getData("Check.img");
        List<String> zeroes = new ArrayList<>();
        for (Data quest : check.getChildren()) {
            for (Data state : quest.getChildren()) {
                for (String type : new String[]{"mob", "item"}) {
                    Data reqs = state.getChildByPath(type);
                    if (reqs == null) {
                        continue;
                    }
                    for (Data entry : reqs.getChildren()) {
                        // "item" with no count at all is a hold-this-item check, not a quantity
                        Data count = entry.getChildByPath("count");
                        if (count != null && DataTool.getInt(count) == 0) {
                            zeroes.add(quest.getName() + "/" + state.getName() + "/" + type + "/" + entry.getName());
                        }
                    }
                }
            }
        }
        zeroes.sort(null);
        assertEquals(PRISTINE_ZERO_COUNTS.stream().sorted().toList(), zeroes,
                "requirement counts that are 0 - anything new here was halved to zero");
    }
}
