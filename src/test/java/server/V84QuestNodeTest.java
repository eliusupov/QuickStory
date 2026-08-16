package server;

import org.junit.jupiter.api.Test;
import provider.Data;
import provider.DataProvider;
import provider.DataTool;
import provider.wz.WZFiles;
import provider.wz.XMLWZFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static server.V84Wz.wz;

/**
 * Ticket 09 - the v84 quests that are not the Evan chain. Sibling of {@link V84TracerNodeTest},
 * {@link V84CrimsonSkyNodeTest}, {@link V84NeoCity2227NodeTest} and {@link V84MiscAreasNodeTest},
 * for the same reason they are siblings of each other: {@link WZFiles#DIRECTORY} is a
 * {@code static final} resolved once per JVM and another test class redirects {@code wz-path} at a
 * {@code @TempDir}, so the real tree has to be opened through an explicitly constructed
 * {@link XMLWZFile}.
 * <p>
 * The ids asserted here are read out of {@code docs/wz-baseline/merge-lists/09/Quest.paths.txt}
 * itself rather than from a literal list, so the test and the deliverable cannot drift apart.
 */
class V84QuestNodeTest {

    private static final Path PATHS_FILE = Path.of("docs", "wz-baseline", "merge-lists", "09", "Quest.paths.txt");

    /** The four category images every quest id appears in. */
    private static final List<String> CATEGORIES = List.of("QuestInfo.img", "Check.img", "Act.img", "Say.img");

    /**
     * The 22 quests whose {@code Check.img} carries a {@code startscript} / {@code endscript} and
     * that are NOT medal quests. A medal quest with no file of its own falls back to
     * {@code scripts/quest/medalQuest.js} ({@code QuestScriptManager:53}); a non-medal one does
     * not, and {@code QuestScriptManager} logs "is uncoded" and disposes.
     */
    private static final int[] SCRIPTED = {2344, 3540, 3759, 10480, 10481, 10490, 10491, 10492,
            10493, 10494, 10497, 10500, 10510, 10514, 10516, 28353, 28354, 28361, 28362, 28363,
            28364, 28365};

    /**
     * The nine quests carrying {@code viewMedalItem}, i.e. {@code GameConstants.isMedalQuest}.
     * <strong>Only eight of them carry a script field</strong> — {@code Check.img/10487} has
     * neither {@code startscript} nor {@code endscript}, so {@code hasScriptRequirement} is false
     * and {@code QuestScriptManager} returns at {@code :70} / {@code :122} without ever reaching
     * the {@code medalQuest.js} fallback at {@code :53}. So 30 script-requiring quests split
     * 22 written + 8 declined, not 22 + 9.
     */
    private static final int[] MEDAL_QUESTS = {10487, 19011, 29934, 29935, 29936, 29937, 29938,
            29939, 29940};

    /** The medal quest that carries no script field at all — see {@link #MEDAL_QUESTS}. */
    private static final int MEDAL_QUEST_WITHOUT_A_SCRIPT_FIELD = 10487;

    /**
     * The one quest of the 63 with no unmet gate. Everything else is behind an expired date, an
     * Evan-only job requirement, an {@code infoex} event counter, or a dead upstream quest — see
     * {@link #exactlyOneOfTheSixtyThreeHasNoUnmetGate}. An earlier draft of the ticket read job ids
     * {@code 2210}-{@code 2218} as Aran and claimed three quests were playable today; those ids are
     * {@code Job.EVAN2}-{@code EVAN10} ({@code Job.java:62-63}) and Aran is {@code 2100}-{@code 2112}.
     */
    private static final int ONLY_UNGATED_QUEST = 19011;

    /**
     * The 48 of 63 quests whose start block carries an already-expired {@code end} date. Not a
     * defect in the merge - it is what v84 ships, and {@code EndDateRequirement.check} compares it
     * against the wall clock, so {@code Quest.canStart} refuses them. Asserted so the claim in the
     * ticket is measured rather than remembered.
     */
    private static final int EXPECTED_DATE_GATED = 48;

    /** Rows of {@code add-list/Quest.txt} that write into a quest the live client already has. */
    private static final int EXPECTED_REFUSED_DEEP_ROWS = 132;

    private static List<String> pathRows() throws IOException {
        return Files.readAllLines(PATHS_FILE, StandardCharsets.UTF_8).stream()
                .map(l -> l.replace("\r", "").trim())
                .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                .toList();
    }

    /** The 63 quest ids, derived from the path list rather than hard-coded beside it. */
    private static List<Integer> questIds() throws IOException {
        return pathRows().stream()
                .filter(r -> r.startsWith("Quest.wz/QuestInfo.img/"))
                .map(r -> Integer.parseInt(r.substring(r.lastIndexOf('/') + 1)))
                .toList();
    }

    private static Data quest(DataProvider quest, String category, int id) {
        Data node = quest.getData(category).getChildByPath(String.valueOf(id));
        assertNotNull(node, category + "/" + id + " is absent from the server XML tree");
        return node;
    }

    // ---------------------------------------------------------------- the path list itself

    @Test
    void thePathListIs252RowsOf63QuestIdsAcrossFourCategoryImages() throws IOException {
        List<String> rows = pathRows();
        assertEquals(252, rows.size(), "Quest.paths.txt row count");
        assertEquals(252, new LinkedHashSet<>(rows).size(), "duplicate row in Quest.paths.txt");

        List<Integer> ids = questIds();
        assertEquals(63, ids.size(), "63 non-Evan quest ids");
        assertEquals(63, new TreeSet<>(ids).size(), "duplicate quest id");

        for (String category : CATEGORIES) {
            List<Integer> forCategory = rows.stream()
                    .filter(r -> r.startsWith("Quest.wz/" + category + "/"))
                    .map(r -> Integer.parseInt(r.substring(r.lastIndexOf('/') + 1)))
                    .toList();
            assertEquals(new TreeSet<>(ids), new TreeSet<>(forCategory),
                    category + " does not carry exactly the same 63 ids as QuestInfo.img");
        }
    }

    @Test
    void noQuestIdOnThisListIsTheEvanChain() throws IOException {
        for (int id : questIds()) {
            assertFalse(id >= 22000 && id <= 22999,
                    "22xxx is ticket 13's Evan chain and must not be on ticket 09's list: " + id);
        }
    }

    @Test
    void everyRowIsRootedAtAQuestIdAndNeverDeeper() throws IOException {
        for (String row : pathRows()) {
            assertEquals(3, row.split("/").length,
                    "a row deeper than Quest.wz/<Img>.img/<id> writes inside an existing quest: " + row);
        }
    }

    /**
     * The 924 add-list roots split 252 / 540 / 132 and nothing is unaccounted for. 540 is the Evan
     * chain (135 ids x 4 images), 132 is DEEP-ROWS.md. Reads the add-list, so a regenerated
     * manifest that changes the split fails here rather than being discovered three tickets later.
     */
    @Test
    void theAddListSplitsExactlyThreeWays() throws IOException {
        List<String> addList = Files.readAllLines(
                        Path.of("docs", "wz-baseline", "add-list", "Quest.txt"), StandardCharsets.UTF_8).stream()
                .map(l -> l.replace("\r", "").trim())
                .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                .toList();
        assertEquals(924, addList.size(), "add-list/Quest.txt root count");

        List<String> mine = pathRows();
        List<String> evan = addList.stream()
                .filter(r -> r.matches("Quest\\.wz/(QuestInfo|Check|Act|Say)\\.img/22\\d{3}"))
                .toList();
        List<String> deep = addList.stream()
                .filter(r -> !mine.contains(r) && !evan.contains(r))
                .toList();

        assertEquals(540, evan.size(), "Evan chain rows (ticket 13)");
        assertEquals(EXPECTED_REFUSED_DEEP_ROWS, deep.size(), "rows refused by ticket 09");
        assertEquals(924, mine.size() + evan.size() + deep.size());

        // and no Evan row is on this ticket's list
        assertTrue(evan.stream().noneMatch(mine::contains), "an Evan row leaked onto 09's list");
    }

    // ---------------------------------------------------------------- the merged tree

    @Test
    void all63QuestsArePresentInAllFourCategoryImagesWithANonBlankName() throws IOException {
        DataProvider quest = wz("Quest.wz");
        for (int id : questIds()) {
            for (String category : CATEGORIES) {
                quest(quest, category, id);
            }
            String name = DataTool.getString("name", quest(quest, "QuestInfo.img", id), "");
            assertFalse(name.isBlank(), "QuestInfo.img/" + id + "/name is blank");
            assertNotEquals("MISSING NAME", name, "QuestInfo.img/" + id + "/name");
        }
    }

    private static void assertNotEquals(String unexpected, String actual, String message) {
        assertFalse(unexpected.equals(actual), message);
    }

    /**
     * The merge must not have disturbed the quests the live client already had. The pre-merge child
     * counts are 2,824 / 2,807 / 2,818 / 2,801 and each must have gained exactly the 63 new ids -
     * asserted as an exact count rather than a floor, because a floor of 2,800 is already satisfied
     * by the un-merged tree and would have made this test green on a merge that never happened.
     * <p>
     * Ticket 33 then added the 135 Evan ids on top, to {@code QuestInfo}/{@code Check}/{@code Act}
     * only - it does not merge {@code Say.img}, which {@code Quest.java:116-118} never opens - so
     * {@code Say.img} is the one image still sitting at +63.
     */
    @Test
    void thePreExistingQuestsSurvivedTheMerge() {
        DataProvider quest = wz("Quest.wz");
        Map<String, Integer> preMerge = Map.of(
                "Act.img", 2824, "Check.img", 2807, "QuestInfo.img", 2818, "Say.img", 2801);
        for (String category : CATEGORIES) {
            int evan = category.equals("Say.img") ? 0 : 135;
            assertEquals(preMerge.get(category) + 63 + evan, quest.getData(category).getChildren().size(),
                    category + " child count after the merge");
        }
        // 3749 is ticket 07's Neo City 2227 gate; 3507 is quest 3540's prerequisite.
        assertNotNull(quest.getData("QuestInfo.img").getChildByPath("3749"));
        assertNotNull(quest.getData("Check.img").getChildByPath("3749"));
        assertNotNull(quest.getData("QuestInfo.img").getChildByPath("3507"));
    }

    /**
     * The 108 {@code lvmax = 40} rows v84 adds to live beginner quests were REFUSED. Merging them
     * would make 108 currently-startable quests unavailable above Lv.40 - see DEEP-ROWS.md. A later
     * ticket that merges them by accident fails here rather than shipping the regression.
     */
    @Test
    void theLvmaxRowsOntoLiveBeginnerQuestsWereNotMerged() {
        DataProvider quest = wz("Quest.wz");
        for (int id : new int[]{28162, 28200, 28266, 28282, 28325}) {
            Data start = quest.getData("Check.img").getChildByPath(id + "/0");
            assertNotNull(start, "Check.img/" + id + "/0");
            assertNull(start.getChildByPath("lvmax"),
                    "Check.img/" + id + "/0/lvmax was merged - that caps a working v83 quest at Lv.40");
        }
    }

    /**
     * The date rows onto quests 2208-2211 and 3845 were REFUSED. v84's values are a 24-hour window
     * in January 2008 and 2010-01-01, both long past, so merging them turns five working quests off.
     */
    @Test
    void theDateRowsOntoFiveWorkingLiveQuestsWereNotMerged() {
        DataProvider quest = wz("Quest.wz");
        for (int id : new int[]{2208, 2209, 2210, 2211}) {
            Data start = quest.getData("Check.img").getChildByPath(id + "/0");
            assertNotNull(start, "Check.img/" + id + "/0");
            assertNull(start.getChildByPath("end"), "Check.img/" + id + "/0/end was merged");
            assertNull(start.getChildByPath("start"), "Check.img/" + id + "/0/start was merged");
            assertNull(start.getChildByPath("interval"), "Check.img/" + id + "/0/interval was merged");
        }
        assertNull(quest.getData("Check.img").getChildByPath("3845/0/end"),
                "Check.img/3845/0/end was merged - that disables a working Lv.60-80 quest");
    }

    /**
     * {@code Exclusive.img} still holds the live client's single {@code medal} group and none of
     * v84's three numeric groups. Merging them would put seven ids in two mutually-exclusive groups
     * at once - the {@code MonsterBook/reward} splice class, in a second file.
     */
    @Test
    void theExclusiveImgGroupingWasNotSpliced() {
        DataProvider quest = wz("Quest.wz");
        Data exclusive = quest.getData("Exclusive.img");
        assertNotNull(exclusive.getChildByPath("medal"), "Exclusive.img/medal disappeared");
        assertEquals(14, exclusive.getChildByPath("medal").getChildren().size(),
                "Exclusive.img/medal child count");
        for (String group : new String[]{"0", "1", "2"}) {
            assertNull(exclusive.getChildByPath(group),
                    "Exclusive.img/" + group + " was merged beside the live 'medal' group");
        }
    }

    // ---------------------------------------------------------------- what the quests need

    /**
     * Every NPC any of the 63 quests names is in the server tree. The one exception is asserted as
     * an exception: 2001006 is a Christmas NPC ticket 08 gave a String.wz name but no Npc.wz image,
     * and only the already-expired quest 10487 references it.
     */
    @Test
    void everyNpcTheseQuestsNameHasAnImageExceptTheOneKnownGap() throws IOException {
        DataProvider quest = wz("Quest.wz");
        Set<Integer> npcs = new TreeSet<>();
        for (int id : questIds()) {
            Data check = quest(quest, "Check.img", id);
            for (Data step : check.getChildren()) {
                Integer npc = DataTool.getInt("npc", step, 0);
                if (npc != null && npc != 0) {
                    npcs.add(npc);
                }
            }
        }
        assertFalse(npcs.isEmpty(), "no npc requirement found at all - the dump is wrong");

        List<Integer> missing = new ArrayList<>();
        for (int npc : npcs) {
            if (!Files.exists(Path.of("wz", "Npc.wz", npc + ".img.xml"))) {
                missing.add(npc);
            }
        }
        assertEquals(List.of(2001006), missing,
                "unexpected missing NPC image(s) for ticket 09's quests");
    }

    /**
     * No quest in this ticket has a mob-kill requirement - measured, not assumed. The ticket's
     * premise ("a quest asking you to kill a Skelegon cannot be verified before Skelegons spawn")
     * is fiction for the non-Evan set, and this is where that is recorded.
     */
    @Test
    void noneOfThese63QuestsRequiresKillingAnything() throws IOException {
        DataProvider quest = wz("Quest.wz");
        for (int id : questIds()) {
            for (Data step : quest(quest, "Check.img", id).getChildren()) {
                assertNull(step.getChildByPath("mob"),
                        "Check.img/" + id + "/" + step.getName() + "/mob - the mob list is not empty after all");
            }
        }
    }

    /**
     * 48 of the 63 carry a start-block {@code end} date that has already passed, so
     * {@code EndDateRequirement} refuses them. This is v84's own data, not a merge fault; the count
     * is asserted so the ticket's headline claim stays true against the tree.
     */
    @Test
    void fortyEightOfTheSixtyThreeAreGatedBehindADateThatHasPassed() throws IOException {
        DataProvider quest = wz("Quest.wz");
        List<Integer> gated = new ArrayList<>();
        for (int id : questIds()) {
            String end = DataTool.getString("0/end", quest(quest, "Check.img", id), null);
            if (end != null && Integer.parseInt(end.substring(0, 4)) < 2020) {
                gated.add(id);
            }
        }
        assertEquals(EXPECTED_DATE_GATED, gated.size(),
                "quests with an expired start-block end date: " + gated);

        // and the whole Crimson Sky chain is among them, which is the one that costs something
        assertTrue(gated.containsAll(List.of(3756, 3757, 3758, 3759, 3760, 3761)),
                "the 3756-3761 chain should be date-gated by v84's own 2000010100 sentinel");
    }

    // ---------------------------------------------------------------- the scripts

    /**
     * Every quest whose WZ carries a script requirement and that is NOT a medal quest has a file.
     * The set is re-derived from the merged tree, so a quest this ticket forgot fails here.
     */
    @Test
    void everyNonMedalScriptedQuestHasItsScriptFile() throws IOException {
        DataProvider quest = wz("Quest.wz");
        Set<Integer> medals = Arrays.stream(MEDAL_QUESTS).boxed().collect(Collectors.toSet());

        Set<Integer> scriptedInWz = new TreeSet<>();
        for (int id : questIds()) {
            for (Data step : quest(quest, "Check.img", id).getChildren()) {
                if (step.getChildByPath("startscript") != null || step.getChildByPath("endscript") != null) {
                    scriptedInWz.add(id);
                }
            }
        }

        Set<Integer> owed = new TreeSet<>(scriptedInWz);
        owed.removeAll(medals);
        assertEquals(Arrays.stream(SCRIPTED).boxed().collect(Collectors.toCollection(TreeSet::new)), owed,
                "the set of quests needing a hand-written script drifted from the WZ");

        for (int id : owed) {
            assertTrue(Files.exists(Path.of("scripts", "quest", id + ".js")),
                    "scripts/quest/" + id + ".js is missing - QuestScriptManager would log 'is uncoded'");
        }
    }

    /**
     * Each script defines exactly the half (or halves) the WZ asks for — and which halves those are
     * is read out of {@code Check.img} rather than from a literal set beside the assertion, so the
     * test cannot agree with a stale copy of itself. {@code Quest} builds start requirements from
     * block {@code 0} and complete requirements from block {@code 1}, and
     * {@code getByWZName} maps both {@code startscript} and {@code endscript} to {@code SCRIPT}, so
     * "block 0 names a script field" is exactly {@code hasScriptRequirement(false)}.
     */
    @Test
    void eachScriptDefinesTheFunctionTheWzRequires() throws IOException {
        DataProvider quest = wz("Quest.wz");
        for (int id : SCRIPTED) {
            boolean needsStart = hasScriptField(quest, id, "0");
            boolean needsEnd = hasScriptField(quest, id, "1");
            assertTrue(needsStart || needsEnd, id + " is on SCRIPTED but names no script field");

            String body = Files.readString(Path.of("scripts", "quest", id + ".js"), StandardCharsets.UTF_8);
            assertEquals(needsStart, body.contains("function start("),
                    "scripts/quest/" + id + ".js start() presence vs Check.img/" + id + "/0");
            assertEquals(needsEnd, body.contains("function end("),
                    "scripts/quest/" + id + ".js end() presence vs Check.img/" + id + "/1");

            // every function must dispose on every path: one dispose per branch that can be entered.
            for (String fn : body.split("function ")) {
                if (fn.startsWith("start(") || fn.startsWith("end(")) {
                    assertTrue(fn.contains("qm.dispose()"),
                            "scripts/quest/" + id + ".js: a function never disposes - the client hangs on the NPC");
                }
            }
        }
    }

    private static boolean hasScriptField(DataProvider quest, int id, String block) {
        Data step = quest.getData("Check.img").getChildByPath(id + "/" + block);
        return step != null
                && (step.getChildByPath("startscript") != null || step.getChildByPath("endscript") != null);
    }

    /**
     * The medal quests are deliberately NOT given a file. Eight of the nine reach
     * {@code QuestScriptManager:53}'s {@code medalQuest.js} fallback; {@code 10487} carries no
     * script field at all, so it never enters the script path in the first place. Both facts are
     * asserted, because the ticket originally reported the split as 22 + 9 and it is 22 + 8.
     */
    @Test
    void theMedalQuestsRelyOnTheGenericFallbackAndHaveNoFileOfTheirOwn() {
        assertTrue(Files.exists(Path.of("scripts", "quest", "medalQuest.js")),
                "the medal fallback script this ticket relies on is gone");
        DataProvider quest = wz("Quest.wz");
        int withScriptField = 0;
        for (int id : MEDAL_QUESTS) {
            assertNotNull(quest.getData("QuestInfo.img").getChildByPath(id + "/viewMedalItem"),
                    "QuestInfo.img/" + id + "/viewMedalItem - not a medal quest after all");
            assertFalse(Files.exists(Path.of("scripts", "quest", id + ".js")),
                    "scripts/quest/" + id + ".js exists; medalQuest.js already covers it");
            if (hasScriptField(quest, id, "0") || hasScriptField(quest, id, "1")) {
                withScriptField++;
            } else {
                assertEquals(MEDAL_QUEST_WITHOUT_A_SCRIPT_FIELD, id,
                        "a second medal quest turned out to carry no script field");
            }
        }
        assertEquals(8, withScriptField, "medal quests that actually reach the medalQuest.js fallback");
        assertEquals(30, SCRIPTED.length + withScriptField,
                "the 30 script-requiring quests split 22 written + 8 declined - 10487 is a medal quest "
                        + "with no script field, so it is in neither half");
    }

    /**
     * The ticket's most load-bearing claim, and the one it originally got wrong: how many of the 63
     * a real character can actually accept today. Each quest is classified by the first start
     * requirement that cannot be satisfied in this tree, and the residue must be exactly
     * {@link #ONLY_UNGATED_QUEST}.
     * <p>
     * The Evan rule is the one that moved: {@code JobRequirement} ({@code Quest.canStart}) demands
     * one of the listed job ids, and 31 of the 63 list only {@code 2001} / {@code 2200}-{@code 2218}
     * — {@code Job.EVAN} and {@code Job.EVAN1}-{@code EVAN10}. Evan is unimplemented here, so no
     * character can hold one. Note two quests are the other way round: {@code 10480} lists every job
     * <em>except</em> Evan, and {@code 10520} lists everything except the two beginner ids.
     */
    @Test
    void exactlyOneOfTheSixtyThreeHasNoUnmetGate() throws IOException {
        DataProvider quest = wz("Quest.wz");

        Set<Integer> dateGated = new TreeSet<>();
        Set<Integer> evanJobGated = new TreeSet<>();
        Set<Integer> infoExGated = new TreeSet<>();
        Set<Integer> upstreamGated = new TreeSet<>();

        for (int id : questIds()) {
            Data start = quest(quest, "Check.img", id).getChildByPath("0");
            if (start == null) {
                continue;
            }
            String end = DataTool.getString("end", start, null);
            if (end != null && Integer.parseInt(end.substring(0, 4)) < 2020) {
                dateGated.add(id);
                continue;
            }
            Data jobs = start.getChildByPath("job");
            if (jobs != null && jobs.getChildren().stream()
                    .allMatch(j -> DataTool.getInt(j, -1) == 2001
                            || (DataTool.getInt(j, -1) >= 2200 && DataTool.getInt(j, -1) <= 2218))) {
                evanJobGated.add(id);
                continue;
            }
            if (start.getChildByPath("infoex") != null) {
                infoExGated.add(id);
                continue;
            }
            Data prereqs = start.getChildByPath("quest");
            if (prereqs != null && prereqs.getChildren().stream()
                    .anyMatch(q -> dateGated.contains(DataTool.getInt("id", q, 0)))) {
                upstreamGated.add(id);
            }
        }

        assertEquals(EXPECTED_DATE_GATED, dateGated.size(), "date-gated: " + dateGated);
        assertEquals(Set.of(2344, 3540, 29934, 29935, 29936, 29937, 29938, 29939, 29940),
                new TreeSet<>(evanJobGated), "Evan-job-gated");
        assertEquals(Set.of(10491, 10492, 10493, 10494), new TreeSet<>(infoExGated), "infoex-gated");
        assertEquals(Set.of(10497), new TreeSet<>(upstreamGated), "gated on a date-dead upstream quest");

        Set<Integer> ungated = new TreeSet<>(questIds());
        ungated.removeAll(dateGated);
        ungated.removeAll(evanJobGated);
        ungated.removeAll(infoExGated);
        ungated.removeAll(upstreamGated);
        assertEquals(Set.of(ONLY_UNGATED_QUEST), ungated,
                "the set of quests a character can accept today");
    }

    /**
     * {@code 10480} is the one quest of the 63 whose job list <em>excludes</em> Evan entirely, and
     * {@code 10520} the one that includes both Evan and everyone else. Recorded because "all the
     * job requirements are Evan-only" is the wrong generalisation and was already made once.
     */
    @Test
    void twoOfTheJobRequirementsAreNotEvanOnly() throws IOException {
        DataProvider quest = wz("Quest.wz");
        Set<Integer> jobs10480 = jobIds(quest, 10480);
        assertTrue(jobs10480.contains(0) && jobs10480.contains(2112),
                "10480 should list beginners and Aran");
        assertTrue(jobs10480.stream().noneMatch(j -> j == 2001 || (j >= 2200 && j <= 2218)),
                "10480's job list is supposed to exclude Evan");

        Set<Integer> jobs10520 = jobIds(quest, 10520);
        assertTrue(jobs10520.contains(2218) && jobs10520.contains(100),
                "10520 should list Evan and explorers alike");
    }

    private static Set<Integer> jobIds(DataProvider quest, int id) {
        Data jobs = quest.getData("Check.img").getChildByPath(id + "/0/job");
        assertNotNull(jobs, "Check.img/" + id + "/0/job");
        return jobs.getChildren().stream().map(j -> DataTool.getInt(j, -1))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /**
     * Quest 3759 is the one that grants Soaring, the skill ticket 06's Crimson Sky maps gate on.
     * Its Act node must name all four job variants and its script must teach one of them.
     */
    @Test
    void quest3759GrantsSoaringAndItsScriptTeachesIt() throws IOException {
        DataProvider quest = wz("Quest.wz");
        Data skills = quest(quest, "Act.img", 3759).getChildByPath("1/skill");
        assertNotNull(skills, "Act.img/3759/1/skill");
        Set<Integer> ids = new TreeSet<>();
        for (Data s : skills.getChildren()) {
            ids.add(DataTool.getInt("id", s, 0));
        }
        assertEquals(new TreeSet<>(List.of(1026, 10001026, 20001026, 20011026)), ids,
                "Act.img/3759/1/skill ids");

        String body = Files.readString(Path.of("scripts", "quest", "3759.js"), StandardCharsets.UTF_8);
        assertTrue(body.contains("qm.teachSkill("), "3759.js must teach Soaring");
        assertTrue(body.contains("20011026"), "3759.js must handle the Evan variant");

        // All four variants must resolve, or the script teaches a skill that does not exist. When
        // 09 wrote this, Skill.wz/2001.img was unmerged and the Evan branch was a dropMessage
        // guard, pinned here by asserting the file's ABSENCE. Ticket 10 merged that image, so the
        // guard is gone and the assertion is now the positive one it was always waiting to become.
        assertTrue(Files.readString(Path.of("wz", "Skill.wz", "2001.img.xml"), StandardCharsets.UTF_8)
                .contains("name=\"20011026\""), "skill 20011026 is missing from Skill.wz/2001.img.xml");
        assertFalse(body.contains("dropMessage("), "3759.js still carries the pre-ticket-10 Evan guard");
        assertTrue(Files.readString(Path.of("wz", "Skill.wz", "000.img.xml"), StandardCharsets.UTF_8)
                .contains("name=\"0001026\""), "skill 1026 is missing from Skill.wz/000.img.xml");
        assertTrue(Files.readString(Path.of("wz", "Skill.wz", "1000.img.xml"), StandardCharsets.UTF_8)
                .contains("name=\"10001026\""), "skill 10001026 is missing from Skill.wz/1000.img.xml");
        assertTrue(Files.readString(Path.of("wz", "Skill.wz", "2000.img.xml"), StandardCharsets.UTF_8)
                .contains("name=\"20001026\""), "skill 20001026 is missing from Skill.wz/2000.img.xml");
    }

    /**
     * Ticket 08 handed over that {@code 910060100} becomes reachable once quests 22515-22518 exist,
     * and pointed at this ticket. The data says otherwise: all four are 22xxx, i.e. the Evan chain,
     * i.e. not this ticket's. Asserted so the handoff is corrected in the tree and not only in prose.
     * <p>
     * <strong>The gate is now met, by ticket 33, not by 09.</strong> All four ids exist in the tree
     * today. What this test still holds is the boundary it was written for: they are not on ticket
     * 09's path list, and the route that waits on them is untouched. The presence assertion is kept
     * rather than deleted - inverted, so it fails if the Evan chain is ever dropped again.
     */
    @Test
    void the22515To22518GateIsTicket13sAndIsStillUnmet() throws IOException {
        DataProvider quest = wz("Quest.wz");
        for (int id : new int[]{22515, 22516, 22517, 22518}) {
            assertNotNull(quest.getData("QuestInfo.img").getChildByPath(String.valueOf(id)),
                    "quest " + id + " was merged by ticket 33 and must stay in the tree");
            assertFalse(questIds().contains(id), "quest " + id + " leaked onto ticket 09's list");
        }
        // and the pre-written route that waits on them is untouched
        String npc = Files.readString(Path.of("scripts", "npc", "1012118.js"), StandardCharsets.UTF_8);
        assertTrue(npc.contains("22515"), "scripts/npc/1012118.js no longer gates on 22515");
    }

    /**
     * Every script is named after its quest id and carries its provenance. The second half is the
     * one that earns its place: {@code QuestScriptManager:52} resolves {@code quest/<questid>.js}
     * and ignores the WZ's {@code startscript} / {@code endscript} <em>string</em>, so a file named
     * after that string ({@code q2344s.js}) would look right and never load. Checked across the
     * whole directory rather than against one hardcoded name.
     */
    @Test
    void everyScriptIsNamedAfterItsQuestIdAndCarriesItsProvenance() throws IOException {
        for (int id : SCRIPTED) {
            Path p = Path.of("scripts", "quest", id + ".js");
            assertTrue(Files.exists(p), p + " missing");
            String body = Files.readString(p, StandardCharsets.UTF_8);
            assertTrue(body.contains("ticket 09") || body.contains("v84"),
                    p + " carries no provenance comment");
        }

        List<String> wzNamed;
        try (var files = Files.list(Path.of("scripts", "quest"))) {
            wzNamed = files.map(p -> p.getFileName().toString())
                    .filter(n -> n.matches("q\\d+[se]\\.js"))
                    .sorted()
                    .toList();
        }
        assertEquals(List.of(), wzNamed,
                "quest scripts are resolved by quest id, not by the WZ's startscript/endscript string");
    }
}
