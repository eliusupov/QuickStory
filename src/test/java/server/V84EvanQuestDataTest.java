package server;

import org.junit.jupiter.api.Test;
import provider.Data;
import provider.DataProvider;
import provider.DataTool;
import provider.wz.WZFiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static server.V84Wz.wz;

/**
 * Ticket 33 — the v84 quest ids this tree was missing, in the server's own {@code wz/} XML.
 * <p>
 * Ticket 09 took the 63 non-Evan ids of v84's 198 new quests; the 135 left are all {@code 22xxx},
 * the Evan chain, and until they landed every Evan quest script in {@code scripts/quest/} was a
 * dead file: {@code Quest.hasScriptRequirement} reads {@code Check.img}, found no node, and
 * {@code QuestScriptManager.start} disposed at {@code :71} before ever asking for a script.
 * <p>
 * The ids come out of {@code docs/wz-baseline/merge-lists/33/Quest.paths.txt} rather than a
 * literal list, so this test and the merge deliverable cannot drift apart.
 * <p>
 * Everything here opens the tree through {@link V84Wz#wz}, never {@code DataProviderFactory},
 * because {@link WZFiles#DIRECTORY} is a {@code static final} resolved once per JVM and
 * {@code MobSkillFactoryTest} redirects {@code wz-path} at a {@code @TempDir}. The ticket's
 * verification gate needs the opposite — a real {@code Quest} load, which can only go through that
 * static — so it lives in {@link V84EvanQuestRealLoad}, outside surefire's default includes.
 */
class V84EvanQuestDataTest {

    private static final Path PATHS_FILE = Path.of("docs", "wz-baseline", "merge-lists", "33", "Quest.paths.txt");

    /** The three images {@code Quest.java:116-118} reads. It never opens {@code Say.img}. */
    private static final List<String> CATEGORIES = List.of("QuestInfo.img", "Check.img", "Act.img");

    /** Evan's ten job advancements, and the ticket's named acceptance criterion. */
    private static final int[] ADVANCEMENTS = {22100, 22101, 22102, 22103, 22104, 22105, 22106, 22107, 22108, 22109};

    /**
     * A quest id this tree has and stock v84 does not — the one such id, in all three images.
     * Asserted because "additive only" is meaningless if the merge could have dropped it, and an
     * id-set comparison that only ever grows would not notice.
     */
    private static final String CUSTOM_QUEST_NOT_IN_V84 = "7778";

    private static List<String> pathRows() throws IOException {
        return Files.readAllLines(PATHS_FILE, StandardCharsets.UTF_8).stream()
                .map(String::trim)
                .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                .toList();
    }

    /** The distinct quest ids the merge added, derived from the path list. */
    private static Set<String> addedIds() throws IOException {
        Set<String> ids = new TreeSet<>();
        for (String row : pathRows()) {
            String[] segs = row.split("/");
            assertEquals(3, segs.length, "unexpected path shape: " + row);
            assertEquals("Quest.wz", segs[0], row);
            assertTrue(CATEGORIES.contains(segs[1]), "unexpected image in path list: " + row);
            ids.add(segs[2]);
        }
        return ids;
    }

    @Test
    void thePathListIsTheThreeImagesTimesOneHundredAndThirtyFiveEvanIds() throws IOException {
        Set<String> ids = addedIds();
        assertEquals(135, ids.size(), "expected 135 added quest ids");
        assertEquals(CATEGORIES.size() * 135, pathRows().size(), "expected 3 rows per id");

        List<String> notEvan = ids.stream().filter(id -> !id.startsWith("22")).toList();
        assertEquals(List.of(), notEvan, "every id ticket 33 adds should be 22xxx; 09 took the rest");
    }

    @Test
    void everyAddedIdIsPresentInAllThreeImages() throws IOException {
        DataProvider quest = wz("Quest.wz");
        Set<String> ids = addedIds();
        for (String category : CATEGORIES) {
            Data img = quest.getData(category);
            assertNotNull(img, category + " missing");
            List<String> absent = ids.stream().filter(id -> img.getChildByPath(id) == null).toList();
            assertEquals(List.of(), absent, category + ": ids absent after the merge");
        }
    }

    @Test
    void theTreesOwnCustomQuestSurvivedTheMerge() {
        DataProvider quest = wz("Quest.wz");
        for (String category : CATEGORIES) {
            assertNotNull(quest.getData(category).getChildByPath(CUSTOM_QUEST_NOT_IN_V84),
                    category + ": custom quest " + CUSTOM_QUEST_NOT_IN_V84 + " (absent from stock v84) was lost");
        }
    }

    @Test
    void everyAdvancementCarriesAStartScriptAndHasAScriptFile() {
        Data check = wz("Quest.wz").getData("Check.img");
        List<String> problems = new ArrayList<>();
        for (int id : ADVANCEMENTS) {
            Data start = check.getChildByPath(id + "/0");
            if (start == null) {
                problems.add(id + ": no Check.img/" + id + "/0");
                continue;
            }
            String script = DataTool.getString("startscript", start, "");
            if (script.isEmpty()) {
                problems.add(id + ": no startscript");
            }
            // Cosmic resolves quest/<id>.js and ignores the WZ's startscript string
            // (QuestScriptManager:52), so the file that matters is named after the id.
            if (!Files.isRegularFile(Path.of("scripts", "quest", id + ".js"))) {
                problems.add(id + ": scripts/quest/" + id + ".js missing");
            }
        }
        assertEquals(List.of(), problems);
    }

    /**
     * Evan's advancement level gates, straight out of the merged nodes. Asserted as the exact
     * ladder rather than "non-zero" because a merge that wrote the same node 10 times would pass
     * the weaker check. Cross-checked against the pre-Big-Bang Hidden Street quest archive.
     */
    @Test
    void theAdvancementLadderIsTheLevelsEvanActuallyUses() {
        Data check = wz("Quest.wz").getData("Check.img");
        int[] expected = {10, 20, 30, 40, 50, 60, 80, 100, 120, 160};
        List<Integer> actual = new ArrayList<>();
        for (int id : ADVANCEMENTS) {
            actual.add(DataTool.getInt("lvmin", check.getChildByPath(id + "/0"), 0));
        }
        assertEquals(List.of(10, 20, 30, 40, 50, 60, 80, 100, 120, 160), actual);
        assertEquals(expected.length, ADVANCEMENTS.length);
    }

    /**
     * Every added id is present in all three images, so nothing here is one-sided. Kept as its own
     * test because v84 is not uniform in general and the script layer keys off {@code Check} alone
     * — if a later merge adds a QuestInfo-only id, this is what says so.
     */
    @Test
    void noAddedIdIsPresentInOnlySomeOfTheThreeImages() throws IOException {
        DataProvider quest = wz("Quest.wz");
        Set<String> ids = addedIds();
        Set<String> partial = new LinkedHashSet<>();
        for (String id : ids) {
            long present = CATEGORIES.stream().filter(c -> quest.getData(c).getChildByPath(id) != null).count();
            if (present != CATEGORIES.size()) {
                partial.add(id + " in " + present + "/" + CATEGORIES.size());
            }
        }
        assertEquals(Set.of(), partial);
    }

    /**
     * The same check {@code Quest.hasScriptRequirement} makes, one level below it: a
     * {@code startscript} on the start block is what {@code QuestRequirementType.SCRIPT} is built
     * from, and before this merge {@code Check.img} had no {@code 22100} node at all.
     * <p>
     * The real {@link server.quest.Quest} load that closes the ticket's verification gate lives in
     * {@link V84EvanQuestRealLoad}, which surefire does not auto-run — see that class for why.
     */
    @Test
    void everyAdvancementsStartBlockIsWhatMakesHasScriptRequirementTrue() {
        Data check = wz("Quest.wz").getData("Check.img");
        for (int id : ADVANCEMENTS) {
            assertFalse(DataTool.getString("startscript", check.getChildByPath(id + "/0"), "").isBlank(),
                    id + ": no startscript, so hasScriptRequirement(false) would be false");
        }
    }
}
