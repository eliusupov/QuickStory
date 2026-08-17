package server;

import client.Job;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import provider.Data;
import provider.DataProvider;
import provider.DataProviderFactory;
import provider.DataTool;
import provider.wz.WZFiles;
import tools.DatabaseConnection;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ItemInformationProvider.getSkillStats(): which of a mastery book's skills belongs to the reader.
 *
 * <p>The test was {@code curskill / 10000 == playerJob}. Every class but Evan is immune to that,
 * because its 4th-job id is also the prefix of its 4th-job skills - a Bishop is job 232 and its
 * books name 232xxxx. Evan is the exception: the job advances to 2218 while Illusion, Flame Wheel
 * and Magic Mastery stay in the 2217 block, so books 2290140-2290145 worked at Evan9 and stopped
 * working the moment the character became Evan10. No later book teaches them, so the three skills
 * were simply lost. Books also run to 2290152 in v84, past the 2290139 that
 * {@code usableMasteryBooks()} stopped at.
 *
 * <p>{@link #onlyEvanSeesADifference()} is the load-bearing one: it replays the old predicate over
 * every mastery book in {@code Item.wz} against every job id in {@link Job} and asserts the
 * provider still picks the identical skill everywhere outside the Evan block.
 *
 * <p><strong>Not a {@code *Test} class on purpose</strong>: {@link WZFiles#DIRECTORY} is a
 * {@code static final} resolved once per JVM and {@code MobSkillFactoryTest} points {@code wz-path}
 * at a {@code @TempDir}.
 */
class MasteryBookJobMatchRealLoad {

    private static final int FIRST_BOOK = 2290000;

    /** Highest mastery book in v84, and the bound usableMasteryBooks() now scans to. */
    private static final int LAST_BOOK = 2290152;

    /** itemid -> the book's skill list, in WZ order. */
    private static Map<Integer, List<Integer>> masteryBooks() {
        DataProvider items = DataProviderFactory.getDataProvider(WZFiles.ITEM);
        Data img = items.getData("Consume/0229.img");
        Map<Integer, List<Integer>> ret = new LinkedHashMap<>();
        for (int itemId = FIRST_BOOK; itemId <= LAST_BOOK + 10; itemId++) {
            Data skills = img.getChildByPath("0" + itemId + "/info/skill");
            if (skills == null) {
                continue;
            }
            List<Integer> list = new ArrayList<>();
            for (int i = 0; i < skills.getChildren().size(); i++) {
                int skillid = DataTool.getInt(Integer.toString(i), skills, 0);
                if (skillid == 0) {
                    break;
                }
                list.add(skillid);
            }
            ret.put(itemId, list);
        }
        return ret;
    }

    /** The predicate as it stood: first entry whose job prefix equals the reader's job exactly. */
    private static int byExactJob(List<Integer> bookSkills, int job) {
        for (int skillid : bookSkills) {
            if (skillid / 10000 == job) {
                return skillid;
            }
        }
        return 0;
    }

    private static int resolved(int itemId, int job) {
        return ItemInformationProvider.getInstance().getSkillStats(itemId, job).get("skillid");
    }

    /**
     * The provider is a singleton whose constructor reads the {@code monstercarddata} table, and
     * there is no database in a test JVM - it throws IllegalStateException out of the class
     * initializer. An SQLException instead lands in the catch loadCardIdData() already has, so the
     * rest of the constructor (all of it WZ) runs.
     */
    @BeforeAll
    static void bootTheProviderWithoutADatabase() {
        try (MockedStatic<DatabaseConnection> db = Mockito.mockStatic(DatabaseConnection.class)) {
            db.when(DatabaseConnection::getConnection).thenThrow(new SQLException("no database in tests"));
            ItemInformationProvider.getInstance();
        }
    }

    @Test
    void onlyEvanSeesADifference() {
        List<String> differences = new ArrayList<>();
        for (Map.Entry<Integer, List<Integer>> book : masteryBooks().entrySet()) {
            for (Job job : Job.values()) {
                int was = byExactJob(book.getValue(), job.getId());
                int now = resolved(book.getKey(), job.getId());
                if (was != now) {
                    differences.add(book.getKey() + " job " + job.getId() + ": " + was + " -> " + now);
                }
            }
        }

        assertAll(
                () -> assertTrue(differences.stream().allMatch(d -> d.contains("job 2218")),
                        "a job outside the Evan block resolved differently: " + differences),
                () -> assertEquals(6, differences.size(), "Evan10 should gain exactly the six 2217 books: " + differences)
        );
    }

    @Test
    void evan10ResolvesItsOwn2217BlockBooks() {
        int evan10 = Job.EVAN10.getId();

        assertAll(
                () -> assertEquals(22171002, resolved(2290140, evan10), "Illusion 20"),
                () -> assertEquals(22171002, resolved(2290141, evan10), "Illusion 30"),
                () -> assertEquals(22171003, resolved(2290142, evan10), "Flame Wheel 20"),
                () -> assertEquals(22171003, resolved(2290143, evan10), "Flame Wheel 30"),
                () -> assertEquals(22170001, resolved(2290144, evan10), "Magic Mastery 20"),
                () -> assertEquals(22170001, resolved(2290145, evan10), "Magic Mastery 30"),
                // the 2218-block books were never broken, and must stay resolved
                () -> assertEquals(22181000, resolved(2290152, evan10), "Onyx 30"),
                // and an Evan9 keeps what it already had
                () -> assertEquals(22171002, resolved(2290140, Job.EVAN9.getId()), "Illusion 20 at Evan9")
        );
    }

    /** usableMasteryBooks() stopped at 2290139, which is short of every Evan book. */
    @Test
    void everyEvanBookIsInsideTheScannedRange() {
        Map<Integer, List<Integer>> books = masteryBooks();

        assertAll(
                () -> assertTrue(books.containsKey(2290140), "first Evan book exists"),
                () -> assertTrue(books.containsKey(LAST_BOOK), "last Evan book exists"),
                () -> assertEquals(List.of(), books.keySet().stream().filter(i -> i > LAST_BOOK).toList(),
                        "a book past the scanned range appeared")
        );
    }
}
