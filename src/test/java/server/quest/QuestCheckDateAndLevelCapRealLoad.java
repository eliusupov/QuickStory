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
 * Ticket 58 - the 123 leaves under {@code Quest.wz/Check.img} that v84 has and we did not: 108
 * {@code lvmax} level caps (R07) and 15 date/repeat fields (R08). Values copied leaf by leaf from
 * the pristine carve at {@code porting-resources/wz-data/v84/Quest.wz}.
 *
 * <pre>
 *   mvnw.cmd -o test -Dtest=QuestCheckDateAndLevelCapRealLoad
 * </pre>
 *
 * <p>Lives in package {@code server.quest} so it can read {@link Quest#startReqs} (protected) and
 * assert on the requirement objects the loader actually built. That is the real reader path:
 * {@code QuestRequirementType.getByWZName} -> {@code Quest.getRequirement} -> a
 * {@code *Requirement}. {@link Quest#canStart} is not a usable seam for these ids - it also loops
 * over their {@code npc}, {@code job} and {@code quest} requirements, which no mock satisfies - but
 * it returns false on the <em>first</em> unmet one, so a requirement that refuses here refuses
 * there.
 *
 * <p><strong>Not a {@code *Test} class on purpose</strong>: {@link WZFiles#DIRECTORY} is
 * {@code static final}, resolved once per JVM.
 *
 * <p><strong>Two of the merged leaf names have no working reader</strong>, and this class pins that
 * rather than pretending otherwise:
 * <ul>
 *   <li>{@code dayByDay} - {@code QuestRequirementType.java:108} is {@code case "daybyday":},
 *       lowercase, and Java's string switch is case-sensitive, so the WZ name {@code dayByDay}
 *       falls to {@code default:} and resolves to {@code UNDEFINED}. Even matched it would build
 *       nothing: {@code Quest.getRequirement} has no {@code DAY_BY_DAY} case. Fixing the case is a
 *       behaviour change with its own blast radius and is deliberately not done here.</li>
 *   <li>{@code start} - resolves to {@code START}, which {@code Quest.getRequirement} lists beside
 *       {@code NORMAL_AUTO_START}/{@code END} under a bare {@code break}. No requirement is built.
 *       {@code EndDateRequirement} reads one bound only, the end.</li>
 * </ul>
 * So five of the fifteen R08 leaves - four {@code start} plus {@code 9260/0/dayByDay} - are merged
 * as parity data with no enforcement path.
 */
class QuestCheckDateAndLevelCapRealLoad {

    /** The 108 R07 ids: 28162..28266 inclusive, plus three outliers. All carry {@code lvmax 40}. */
    private static final List<Integer> LVMAX_IDS = lvmaxIds();

    private static final int CARVE_LVMAX = 40;

    /** The four quests the {@code end} merge permanently retires, and 3845 which it also retires. */
    private static final List<Integer> RETIRED_BY_END_DATE = List.of(2208, 2209, 2210, 2211, 3845);

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

    /** Per id, not an aggregate count: every one of the 108 built a real MaxLevelRequirement. */
    @Test
    void allHundredAndEightLevelCapsLoadedFromTheCarve() {
        assertTreeIsLoaded();
        Character justOver = charAtLevel(CARVE_LVMAX + 1);
        for (int questId : LVMAX_IDS) {
            AbstractQuestRequirement req = startReq(questId, QuestRequirementType.MAX_LEVEL);
            assertNotNull(req, "quest " + questId + " has no MAX_LEVEL requirement - lvmax did not merge");
            assertFalse(req.check(justOver, null),
                    "quest " + questId + " still startable at level " + (CARVE_LVMAX + 1));
        }
    }

    /** The cap is a cap, not a floor: at it and under it the quest is still startable. */
    @Test
    void aCharacterAtOrBelowTheCapIsAccepted() {
        assertTreeIsLoaded();
        for (int questId : List.of(28162, 28266, 28282, 28283, 28325)) {
            AbstractQuestRequirement req = startReq(questId, QuestRequirementType.MAX_LEVEL);
            assertTrue(req.check(charAtLevel(CARVE_LVMAX), null), "quest " + questId + " at the cap");
            assertTrue(req.check(charAtLevel(1), null), "quest " + questId + " well below the cap");
            assertFalse(req.check(charAtLevel(CARVE_LVMAX + 1), null), "quest " + questId + " over the cap");
        }
    }

    /**
     * The 28002/28004 caps are v83-only - the carve does not have them - so the merge had to be
     * additive rather than a reconcile-to-carve. If a later wholesale merge drops them, this fails.
     */
    @Test
    void theTwoV83OnlyLevelCapsSurvivedTheMerge() {
        assertTreeIsLoaded();
        for (int questId : List.of(28002, 28004)) {
            assertNotNull(startReq(questId, QuestRequirementType.MAX_LEVEL),
                    "quest " + questId + " lost its v83-only lvmax");
        }
    }

    /**
     * 10109 is the only merged {@code interval} that is reachable - 2208-2211 have an {@code end}
     * that refuses first, and {@code canStart} stops at the first refusal.
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
     * The retirement, pinned as intended behaviour. All five carve {@code end} values are in the
     * past (2008-01-02 for 2208-2211, 2010-01-01 for 3845) and {@code EndDateRequirement} refuses
     * anything past its end date, so these five are now permanently unstartable for everyone - the
     * same way ticket 44's 1048-1054 are retired. A {@code queststatus} row already in state 1 is
     * untouched: {@code end} is a <em>start</em> requirement, read out of {@code Check.img/&lt;id&gt;/0},
     * and {@link Quest#canComplete} never consults {@code startReqs}. So a character mid-quest can
     * still hand it in; nobody can start it again.
     */
    @Test
    void theMergedEndDatesRetireTheirQuests() {
        assertTreeIsLoaded();
        Character chr = charAtLevel(200);
        for (int questId : RETIRED_BY_END_DATE) {
            AbstractQuestRequirement req = startReq(questId, QuestRequirementType.END_DATE);
            assertNotNull(req, "quest " + questId + " has no END_DATE requirement - end did not merge");
            assertFalse(req.check(chr, null), "quest " + questId + " end date is in the past, must refuse");
        }
    }

    /**
     * The five inert leaves. Merged for parity, enforcing nothing. Asserted so the next person does
     * not re-derive the case mismatch - see the class javadoc.
     */
    @Test
    void startAndDayByDayMergeAsInertData() {
        assertTreeIsLoaded();
        assertEquals(QuestRequirementType.UNDEFINED, QuestRequirementType.getByWZName("dayByDay"),
                "QuestRequirementType.java:108 is case \"daybyday\" - a case-sensitive switch misses dayByDay");
        assertNull(startReq(9260, QuestRequirementType.DAY_BY_DAY), "nothing reads dayByDay");

        for (int questId : List.of(2208, 2209, 2210, 2211)) {
            assertEquals(QuestRequirementType.START, QuestRequirementType.getByWZName("start"),
                    "start still maps to START");
            assertNull(startReq(questId, QuestRequirementType.START),
                    "quest " + questId + ": Quest.getRequirement builds nothing for START");
        }
    }
}
