package server;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import provider.Data;
import provider.DataProviderFactory;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins changeSet 160 - drop rows for items the v84 client itself lists against a mob but that
 * {@code drop_data} never had.
 *
 * <pre>
 *   mvnw.cmd test -Dtest=MonsterBookDropV84RealLoad
 * </pre>
 *
 * <p><strong>Not a {@code *Test} class on purpose</strong>, same reason as its siblings:
 * {@link WZFiles#DIRECTORY} is a {@code static final} resolved once per JVM and
 * {@code MobSkillFactoryTest} points {@code wz-path} at a {@code @TempDir}.
 *
 * <p>The claim this class exists to defend is that <em>which items drop is not a judgement
 * call</em>. {@code String.wz/MonsterBook.img/<mobid>/reward} is the client's own per-mob drop
 * list, and {@link #everyRowIsAnItemTheClientListsForThatMob()} re-derives all of changeSet 160
 * from it and fails if a single pair drifts. The rates are a separate matter and are deliberately
 * <em>not</em> asserted row-by-row here: they are derived statistics, documented with their
 * sample sizes in the changeSet header, and pinning each one would only freeze an estimate.
 * What is asserted about them is that they are in range, unmodified by any quest gate, and never
 * applied to a monster card.
 *
 * <p>The zero-padding trap that has already produced a wrong measurement on this project applies
 * here too, in the opposite direction from its sibling: {@code MonsterBook.img} names mobs
 * <em>unpadded</em> ({@code 100100}) while {@code Mob.wz} images are 7-digit padded
 * ({@code 0100100.img}). Everything below normalises through {@code int}.
 */
class MonsterBookDropV84RealLoad {

    private static final Path CHANGESET_160 =
            Path.of("src", "main", "resources", "db", "data", "160-monsterbook-drop-data.sql");

    /** Monster cards are changeSet 157's lane; a card here would double-insert. */
    private static final int CARD_FLOOR = 2380000;
    private static final int CARD_CEILING = 2389999;

    /** drop_data chance is out of 1,000,000, and 999999 is the guaranteed-drop idiom. */
    private static final int MAX_CHANCE = 999999;

    private static final Pattern DROP_ROW =
            Pattern.compile("\\((\\d+), (\\d+), (\\d+), (\\d+), (\\d+), (\\d+)\\)");

    @BeforeAll
    static void muteGraal() {
        System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
        assertTrue(Files.isRegularFile(Path.of(WZFiles.DIRECTORY, "String.wz", "MonsterBook.img.xml")),
                "wz-path resolved to '" + WZFiles.DIRECTORY + "', which holds no String.wz/MonsterBook.img "
                        + "- another test class won the WZFiles.DIRECTORY race, so this says nothing "
                        + "about monster book drops");
    }

    /**
     * The load-bearing assertion. Every {@code (dropperid, itemid)} in changeSet 160 must appear in
     * that mob's own {@code MonsterBook.img reward} list. If a row here stops matching the client,
     * someone has started inventing drops rather than transcribing them.
     */
    @Test
    void everyRowIsAnItemTheClientListsForThatMob() throws IOException {
        List<int[]> rows = dropRows();
        assertTrue(rows.size() > 100,
                "changeSet 160 parsed to only " + rows.size() + " rows, which means the row regex "
                        + "stopped matching the file rather than that the changeSet shrank");

        for (int[] row : rows) {
            int dropperId = row[0];
            int itemId = row[1];
            Set<Integer> listed = rewardList(dropperId);
            assertNotNull(listed, "changeSet 160 adds a drop for mob " + dropperId
                    + ", which has no MonsterBook.img entry at all, so the client never listed it");
            assertTrue(listed.contains(itemId),
                    "changeSet 160 gives mob " + dropperId + " item " + itemId
                            + ", but that mob's MonsterBook reward list does not name it");
        }
    }

    /** Cards belong to changeSet 157. One here would insert the same card drop twice. */
    @Test
    void noRowIsAMonsterCard() throws IOException {
        for (int[] row : dropRows()) {
            int itemId = row[1];
            assertTrue(itemId < CARD_FLOOR || itemId > CARD_CEILING,
                    "changeSet 160 contains monster card " + itemId + " on mob " + row[0]
                            + ", which changeSet 157 already inserts");
        }
    }

    /**
     * Rates are estimates, so what is pinned is that they cannot misbehave: in range, never zero,
     * and never quest-gated. A non-zero questid would make {@code Character.needQuestItem} refuse
     * the pickup for every other quest, which is the trap changeSet 157 documents.
     */
    @Test
    void everyRowIsUngatedSanelyBoundedAndPositiveQuantity() throws IOException {
        for (int[] row : dropRows()) {
            int itemId = row[1];
            int min = row[2];
            int max = row[3];
            int questId = row[4];
            int chance = row[5];

            assertEquals(0, questId, "item " + itemId + " on mob " + row[0] + " is quest-gated; a "
                    + "derived rate must never carry a questid, which is a design decision");
            assertTrue(chance >= 1 && chance <= MAX_CHANCE,
                    "item " + itemId + " on mob " + row[0] + " has chance " + chance
                            + ", outside 1.." + MAX_CHANCE);
            assertTrue(min >= 1 && max >= min,
                    "item " + itemId + " on mob " + row[0] + " has quantity range " + min + ".." + max);
        }
    }

    /**
     * No mob may receive the same item twice, which a regenerated changeSet could easily do by
     * merging two sources of the same list.
     */
    @Test
    void noDuplicateMobItemPairs() throws IOException {
        Set<String> seen = new LinkedHashSet<>();
        for (int[] row : dropRows()) {
            String pair = row[0] + ":" + row[1];
            assertTrue(seen.add(pair), "changeSet 160 lists mob/item " + pair + " more than once");
        }
    }

    /** The changeSet must exist, be registered, carry its rollback, and not disturb the applied ones. */
    @Test
    void changeSet160IsRegisteredAndTheAppliedOnesAreUntouched() throws IOException {
        assertTrue(Files.isRegularFile(CHANGESET_160), "changeSet 160 seed file is missing");

        String changelog = Files.readString(
                Path.of("src", "main", "resources", "db", "changelog-data.xml"), StandardCharsets.ISO_8859_1);
        assertTrue(changelog.contains("160-monsterbook-drop-data.sql"),
                "160 exists on disk but is not registered in changelog-data.xml, so it never runs");
        assertTrue(changelog.contains("DELETE FROM drop_data WHERE questid = 0 AND (dropperid, itemid) IN ("),
                "changeSet 160 lost its exact-pair rollback; deleting by dropperid alone would take "
                        + "pre-existing rows with it");

        // 152-156 are APPLIED - any edit fails checksum validation on the live database
        for (String applied : List.of("152-drop-data.sql", "153-crimson-sky-drop-data.sql",
                "154-neo-city-2227-drop-data.sql", "155-evan-tutorial-drop-data.sql",
                "156-evan-chain-drop-data.sql")) {
            assertTrue(changelog.contains(applied), "an applied changeSet vanished from the changelog: " + applied);
        }
    }

    // ---------------------------------------------------------------------------------------

    /** {dropperid, itemid, min, max, questid, chance} per row in the changeSet. */
    private static List<int[]> dropRows() throws IOException {
        String sql = Files.readString(CHANGESET_160, StandardCharsets.UTF_8);
        int from = sql.indexOf("INSERT INTO drop_data");
        assertTrue(from >= 0, "changeSet 160 no longer contains 'INSERT INTO drop_data'");

        List<int[]> rows = new ArrayList<>();
        Matcher m = DROP_ROW.matcher(sql.substring(from));
        while (m.find()) {
            int[] row = new int[6];
            for (int i = 0; i < 6; i++) {
                row[i] = Integer.parseInt(m.group(i + 1));
            }
            rows.add(row);
        }
        return rows;
    }

    /**
     * The item ids {@code String.wz/MonsterBook.img} lists as that mob's drops, or null when the
     * mob has no entry. MonsterBook names mobs unpadded, so the lookup goes through the raw int.
     */
    private static Set<Integer> rewardList(int mobId) {
        Data book = DataProviderFactory.getDataProvider(WZFiles.STRING).getData("MonsterBook.img");
        assertNotNull(book, "String.wz/MonsterBook.img did not parse");

        Data entry = book.getChildByPath(String.valueOf(mobId));
        if (entry == null) {
            return null;
        }
        Data reward = entry.getChildByPath("reward");
        Set<Integer> items = new TreeSet<>();
        if (reward != null) {
            for (Data slot : reward.getChildren()) {
                items.add(DataTool.getInt(slot, -1));
            }
        }
        return items;
    }
}
