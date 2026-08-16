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

    /** Which half of each scripted quest the WZ actually requires. */
    private static final Set<Integer> NEEDS_START = Set.of(2344, 3540, 10480, 10481, 10490, 10497,
            10500, 10510, 10514, 10516, 28353, 28354);
    private static final Set<Integer> NEEDS_END = Set.of(2344, 3759, 10491, 10492, 10493, 10494,
            10510, 28354, 28361, 28362, 28363, 28364, 28365);

    /** The nine quests carrying {@code viewMedalItem}, i.e. {@code GameConstants.isMedalQuest}. */
    private static final int[] MEDAL_QUESTS = {10487, 19011, 29934, 29935, 29936, 29937, 29938,
            29939, 29940};

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
     * The merge must not have disturbed the quests the live client already had. 2,818 is what v83
     * ships in each category image; 63 new ids makes 2,881, and every pre-v84 id must still be
     * there. Spot-checks the ids other tickets depend on by name.
     */
    @Test
    void thePreExistingQuestsSurvivedTheMerge() {
        DataProvider quest = wz("Quest.wz");
        for (String category : CATEGORIES) {
            long count = quest.getData(category).getChildren().size();
            assertTrue(count > 2800, category + " lost quests: only " + count + " children");
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

    /** Each script defines exactly the half (or halves) the WZ asks for. */
    @Test
    void eachScriptDefinesTheFunctionTheWzRequires() throws IOException {
        for (int id : SCRIPTED) {
            String body = Files.readString(Path.of("scripts", "quest", id + ".js"), StandardCharsets.UTF_8);
            assertEquals(NEEDS_START.contains(id), body.contains("function start("),
                    "scripts/quest/" + id + ".js start() presence");
            assertEquals(NEEDS_END.contains(id), body.contains("function end("),
                    "scripts/quest/" + id + ".js end() presence");
            assertTrue(body.contains("qm.dispose()"),
                    "scripts/quest/" + id + ".js never disposes - the client would hang on the NPC");
        }
    }

    /**
     * The nine medal quests are deliberately NOT given a file: {@code QuestScriptManager:53} falls
     * back to {@code medalQuest.js} for them. Asserting the absence keeps that a decision.
     */
    @Test
    void theMedalQuestsRelyOnTheGenericFallbackAndHaveNoFileOfTheirOwn() {
        assertTrue(Files.exists(Path.of("scripts", "quest", "medalQuest.js")),
                "the medal fallback script this ticket relies on is gone");
        DataProvider quest = wz("Quest.wz");
        for (int id : MEDAL_QUESTS) {
            assertNotNull(quest.getData("QuestInfo.img").getChildByPath(id + "/viewMedalItem"),
                    "QuestInfo.img/" + id + "/viewMedalItem - not a medal quest after all");
            assertFalse(Files.exists(Path.of("scripts", "quest", id + ".js")),
                    "scripts/quest/" + id + ".js exists; medalQuest.js already covers it");
        }
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

        // 20011026 lives in Skill.wz/2001.img, which ticket 12/13 owns and which is not here yet.
        assertFalse(Files.exists(Path.of("wz", "Skill.wz", "2001.img.xml")),
                "Skill.wz/2001.img.xml appeared - 3759.js's Evan guard can now be replaced by a teachSkill");
        assertTrue(Files.exists(Path.of("wz", "Skill.wz", "000.img.xml")));
    }

    /**
     * Ticket 08 handed over that {@code 910060100} becomes reachable once quests 22515-22518 exist,
     * and pointed at this ticket. The data says otherwise: all four are 22xxx, i.e. the Evan chain,
     * i.e. ticket 13's. Asserted so the handoff is corrected in the tree and not only in prose.
     */
    @Test
    void the22515To22518GateIsTicket13sAndIsStillUnmet() throws IOException {
        DataProvider quest = wz("Quest.wz");
        for (int id : new int[]{22515, 22516, 22517, 22518}) {
            assertNull(quest.getData("QuestInfo.img").getChildByPath(String.valueOf(id)),
                    "quest " + id + " is on ticket 13's Evan list and must not be merged here");
            assertFalse(questIds().contains(id), "quest " + id + " leaked onto ticket 09's list");
        }
        // and the pre-written route that waits on them is untouched
        String npc = Files.readString(Path.of("scripts", "npc", "1012118.js"), StandardCharsets.UTF_8);
        assertTrue(npc.contains("22515"), "scripts/npc/1012118.js no longer gates on 22515");
    }

    /** No quest script this ticket wrote overwrote an existing one - all 22 ids were free. */
    @Test
    void theTwentyTwoScriptsAreAllNewFilesNamedAfterTheirQuestId() throws IOException {
        for (int id : SCRIPTED) {
            Path p = Path.of("scripts", "quest", id + ".js");
            assertTrue(Files.exists(p), p + " missing");
            String body = Files.readString(p, StandardCharsets.UTF_8);
            assertTrue(body.contains("ticket 09") || body.contains("v84"),
                    p + " carries no provenance comment");
        }
        // the WZ script NAMES (q2344s, ...) are not how Cosmic resolves scripts - QuestScriptManager:52
        // uses "quest/<questid>.js". Guard against someone "fixing" that by adding the WZ names.
        assertFalse(Files.exists(Path.of("scripts", "quest", "q2344s.js")),
                "scripts are resolved by quest id, not by the WZ's startscript string");
    }
}
