package server.quest;

import client.Character;
import client.QuestStatus;
import org.junit.jupiter.api.Test;
import provider.wz.WZFiles;
import server.quest.requirements.AbstractQuestRequirement;
import server.quest.requirements.IntervalRequirement;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The runtime half of ticket 09's refusal, re-established after ticket 58 briefly overturned it.
 *
 * <pre>
 *   mvnw.cmd -o test -Dtest=QuestCheckDateAndLevelCapRealLoad
 * </pre>
 *
 * <p><strong>History, so nobody merges these leaves a third time.</strong> Commit {@code 8e740646b}
 * (ticket 09) measured all 132 {@code Quest.wz} add-list rows that write <em>into</em> quests the
 * live client already ships and refused them - {@code docs/wz-baseline/merge-lists/09/DEEP-ROWS.md}.
 * Commit {@code dcba0f8e0} (ticket 58) merged 123 of them without citing that refusal. It was
 * reverted: 108 {@code lvmax = 40} caps on 28162..28266/28282/28283/28325, the 12
 * {@code start}/{@code end}/{@code interval} leaves on 2208-2211, and {@code 3845/0/end}. Only the
 * two provably inert leaves were kept ({@code 10109/0/interval}, {@code 9260/0/dayByDay}).
 *
 * <p><strong>Why.</strong> {@code MaxLevelRequirement.check} is {@code maxLevel >= chr.getLevel()}
 * and {@code Quest.canStart} returns on the first unmet start requirement, so a merged
 * {@code lvmax 40} makes 108 startable quests permanently unstartable above Lv.40 - and 102 of the
 * 108 carry no {@code lvmin} at all, i.e. they were open to every level. {@code EndDateRequirement}
 * compares a single past timestamp against the wall clock, so the {@code end} leaves retire five
 * more. The database has 21 characters above Lv.40 and 15 rows on quest 3845 alone. The v84-parity
 * rule does not reach content the owner can play today.
 *
 * <p>Lives in package {@code server.quest} to read {@link Quest#startReqs} (protected) and assert on
 * the requirement objects the loader actually built. Every "absent" assertion is paired with a
 * live control on the same requirement type, so a loader that silently built nothing at all would
 * fail rather than pass.
 *
 * <p><strong>Not a {@code *Test} class on purpose</strong>: {@link WZFiles#DIRECTORY} is
 * {@code static final}, resolved once per JVM.
 */
class QuestCheckDateAndLevelCapRealLoad {

    /** The 108 ids ticket 58 capped at 40: 28162..28266 inclusive, plus three outliers. */
    private static final List<Integer> LVMAX_IDS = lvmaxIds();

    /** The five live quests a merged {@code end} would have retired. */
    private static final List<Integer> NOT_RETIRED = List.of(2208, 2209, 2210, 2211, 3845);

    private static List<Integer> lvmaxIds() {
        List<Integer> ids = new ArrayList<>();
        for (int id = 28162; id <= 28266; id++) {
            ids.add(id);
        }
        ids.add(28282);
        ids.add(28283);
        ids.add(28325);
        return ids;
    }

    private static void assertTreeIsLoaded() {
        assertTrue(Files.isRegularFile(Path.of(WZFiles.DIRECTORY, "Quest.wz", "Check.img.xml")),
                "wz-path resolved to '" + WZFiles.DIRECTORY + "', which holds no Quest.wz - another "
                        + "test class won the WZFiles.DIRECTORY race, so this says nothing about the data");
    }

    private static AbstractQuestRequirement startReq(int questId, QuestRequirementType type) {
        return Quest.getInstance(questId).startReqs.get(type);
    }

    private static Character charAtLevel(int level) {
        Character chr = mock(Character.class);
        lenient().when(chr.getLevel()).thenReturn(level);
        lenient().when(chr.getName()).thenReturn("test");
        return chr;
    }

    /**
     * Per id, not an aggregate: none of the 108 builds a {@code MAX_LEVEL} requirement, so a
     * character of any level is still admitted by the level gate. The control is quest 28002, whose
     * v83-only {@code lvmax 51} <em>does</em> build one - without it this test would pass on a
     * loader that had stopped reading {@code lvmax} entirely.
     */
    @Test
    void noneOfThe108BeginnerQuestsIsCappedAtLevel40() {
        assertTreeIsLoaded();
        assertNotNull(startReq(28002, QuestRequirementType.MAX_LEVEL),
                "control: 28002's v83-only lvmax must still load, or this test proves nothing");
        for (int questId : LVMAX_IDS) {
            assertNull(startReq(questId, QuestRequirementType.MAX_LEVEL),
                    "quest " + questId + " gained a MAX_LEVEL requirement - v84's lvmax 40 was merged "
                            + "and every character above Lv.40 just lost the quest");
        }
    }

    /**
     * The two v83-only caps are real and enforced. They are also the reason a merge here has to be
     * additive rather than a reconcile-to-carve: the carve does not have them.
     */
    @Test
    void theTwoV83OnlyLevelCapsStillLoadAndStillEnforce() {
        assertTreeIsLoaded();
        for (int questId : List.of(28002, 28004)) {
            AbstractQuestRequirement req = startReq(questId, QuestRequirementType.MAX_LEVEL);
            assertNotNull(req, "quest " + questId + " lost its v83-only lvmax");
            assertTrue(req.check(charAtLevel(51), null), "quest " + questId + " at its cap of 51");
            assertFalse(req.check(charAtLevel(52), null), "quest " + questId + " above its cap of 51");
        }
    }

    /**
     * 2208-2211 and 3845 keep no {@code END_DATE} start requirement, so they stay startable. The
     * control is 10109, which carried {@code end = 2008121900} long before either ticket and does
     * refuse - proving {@code EndDateRequirement} is wired and the five nulls above mean something.
     */
    @Test
    void theFiveLiveQuestsWereNotRetiredByAPastEndDate() {
        assertTreeIsLoaded();
        AbstractQuestRequirement control = startReq(10109, QuestRequirementType.END_DATE);
        assertNotNull(control, "control: 10109's pre-existing end date must load");
        assertFalse(control.check(charAtLevel(30), null), "control: 10109 ended in 2008, must refuse");

        for (int questId : NOT_RETIRED) {
            assertNull(startReq(questId, QuestRequirementType.END_DATE),
                    "quest " + questId + " gained an END_DATE requirement - v84's 2008/2010 end date "
                            + "was merged and the quest is now permanently unstartable");
        }
        for (int questId : List.of(2208, 2209, 2210, 2211)) {
            assertNull(startReq(questId, QuestRequirementType.INTERVAL),
                    "quest " + questId + " gained an INTERVAL requirement - that also flips "
                            + "Quest.repeatable, a shape neither vendor shipped without the end date");
        }
    }

    /**
     * {@code 10109/0/interval} is one of the two ticket-58 leaves that was kept: the quest is
     * already date-dead, so making it repeatable enforces nothing new, and it is the only merged
     * {@code interval} whose reader is reachable at all.
     */
    @Test
    void intervalIsHonouredForQuest10109() {
        assertTreeIsLoaded();
        IntervalRequirement req = (IntervalRequirement) startReq(10109, QuestRequirementType.INTERVAL);
        assertNotNull(req, "10109 has no INTERVAL requirement - interval did not merge");
        assertEquals(MINUTES.toMillis(1440), req.getInterval(), "carve gives 10109 interval 1440 minutes");

        Character chr = charAtLevel(30);
        QuestStatus status = mock(QuestStatus.class);
        when(chr.getQuest(any(Quest.class))).thenReturn(status);

        when(status.getStatus()).thenReturn(QuestStatus.Status.COMPLETED);
        when(status.getCompletionTime()).thenReturn(System.currentTimeMillis());
        assertFalse(req.check(chr, null), "completed a minute ago, the 1440-minute wait has not passed");

        when(status.getCompletionTime()).thenReturn(System.currentTimeMillis() - MINUTES.toMillis(1441));
        assertTrue(req.check(chr, null), "completed more than 1440 minutes ago");
    }

    /**
     * The other kept leaf. {@code QuestRequirementType.java:108} is {@code case "daybyday":},
     * lowercase, and Java's string switch is case-sensitive, so the WZ name {@code dayByDay} falls
     * to {@code default:} -> {@code UNDEFINED}. Even matched it would build nothing:
     * {@code Quest.getRequirement} has no {@code DAY_BY_DAY} case. Asserted so the next person does
     * not re-derive it, and so that fixing the case is a deliberate act with this test to update.
     */
    @Test
    void dayByDayMergedAsInertData() {
        assertTreeIsLoaded();
        assertEquals(QuestRequirementType.UNDEFINED, QuestRequirementType.getByWZName("dayByDay"),
                "QuestRequirementType.java:108 is case \"daybyday\" - a case-sensitive switch misses dayByDay");
        assertNull(startReq(9260, QuestRequirementType.DAY_BY_DAY), "nothing reads dayByDay");
    }
}
