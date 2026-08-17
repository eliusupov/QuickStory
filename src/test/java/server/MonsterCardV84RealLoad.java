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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins changeSet 157 - the 39 v84 monster cards that had no {@code monstercarddata} mapping and
 * therefore no drop, which made them unobtainable and their sets uncompletable.
 *
 * <pre>
 *   mvnw.cmd test -Dtest=MonsterCardV84RealLoad
 * </pre>
 *
 * <p><strong>Not a {@code *Test} class on purpose</strong>, same reason as its siblings:
 * {@link WZFiles#DIRECTORY} is a {@code static final} resolved once per JVM and
 * {@code MobSkillFactoryTest} points {@code wz-path} at a {@code @TempDir}.
 *
 * <p>The whole point of this class is that <em>none of changeSet 157 is a judgement call</em>.
 * Every card node in {@code Item.wz/Consume/0238.img} carries its own {@code info/mob} leaf, so the
 * mapping is read, not inferred, and {@link #everyMappingIsTheOneTheCardItselfNames()} re-derives
 * all 39 from the WZ and fails if a single row drifts.
 *
 * <p>The other trap this guards is the zero-padding one that has already produced a wrong
 * measurement on this project: ids are 8-digit zero-padded inside {@code 0238.img}
 * ({@code 02380020}), so anything comparing them as text mis-matches. Everything here normalises
 * through {@code int}.
 */
class MonsterCardV84RealLoad {

    private static final Path CHANGESET_157 =
            Path.of("src", "main", "resources", "db", "data", "157-monstercard-v84-data.sql");

    private static final Path ADD_LIST_ITEM =
            Path.of("docs", "wz-baseline", "add-list", "Item.txt");

    /** The boss block. Card ids at or above this are the boss cards; below it, ordinary mobs. */
    private static final int BOSS_CARD_FLOOR = 2388000;

    /** Both values are copied from the 576 monster-card rows drop_data already had. */
    private static final int CHANCE_NORMAL = 8000;
    private static final int CHANCE_BOSS = 24000;

    /**
     * The four mobs that changeSet 157 maps correctly but that no map in this tree spawns, so the
     * card cannot actually drop yet. Recorded rather than hidden: the row is still the right row,
     * and it goes live the moment the mob is placed. Verified absent from the pristine v84
     * {@code Map.wz} too, so this is not a merge regression - v84 ships the mob and the card and
     * places the mob nowhere.
     */
    private static final Map<Integer, String> UNSPAWNED_ON_PURPOSE = Map.of(
            3400008, "Transformed Doll Claw Game",
            4300001, "Blue Perfume",
            4300003, "Yellow Perfume",
            4300005, "Pink Perfume");

    private static final Pattern CARD_ROW = Pattern.compile("\\((\\d{7}), (\\d+)\\)");
    private static final Pattern DROP_ROW =
            Pattern.compile("\\((\\d+), (\\d{7}), (\\d+), (\\d+), (\\d+), (\\d+)\\)");

    @BeforeAll
    static void muteGraal() {
        System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
        assertTrue(Files.isRegularFile(Path.of(WZFiles.DIRECTORY, "Item.wz", "Consume", "0238.img.xml")),
                "wz-path resolved to '" + WZFiles.DIRECTORY + "', which holds no Item.wz/Consume/0238.img "
                        + "- another test class won the WZFiles.DIRECTORY race, so this says nothing "
                        + "about monster cards");
    }

    /**
     * The load-bearing assertion. {@code Item.wz/Consume/0238.img/<cardid>/info/mob} is the client's
     * own statement of which mob a card belongs to; changeSet 157 does nothing but transcribe it.
     * If a row here ever stops matching the WZ, someone has started inventing mappings.
     */
    @Test
    void everyMappingIsTheOneTheCardItselfNames() throws IOException {
        Map<Integer, Integer> rows = cardRows();
        assertEquals(39, rows.size(), "changeSet 157 should map exactly 39 cards");

        for (Map.Entry<Integer, Integer> row : rows.entrySet()) {
            int cardId = row.getKey();
            Integer wzMob = cardMob(cardId);
            assertNotNull(wzMob, "card " + cardId + " has no info/mob leaf in Item.wz/Consume/0238.img, "
                    + "so changeSet 157 cannot be transcribing it");
            assertEquals(wzMob, row.getValue(),
                    "changeSet 157 maps card " + cardId + " to mob " + row.getValue()
                            + " but the card's own Item.wz info/mob leaf says " + wzMob);
        }
    }

    /**
     * The 39 are exactly the v84 additions, no more and no less. If this drifts, either the add-list
     * was regenerated or someone widened the changeSet past the gap it was written to close.
     */
    @Test
    void theThirtyNineAreExactlyTheCardsV84Adds() throws IOException {
        Set<Integer> added = new TreeSet<>();
        for (String line : Files.readAllLines(ADD_LIST_ITEM, StandardCharsets.UTF_8)) {
            String row = line.trim();
            if (row.startsWith("Item.wz/Consume/0238.img/")) {
                added.add(Integer.parseInt(row.substring(row.lastIndexOf('/') + 1)));
            }
        }
        assertEquals(39, added.size(), "add-list/Item.txt no longer lists 39 cards under 0238.img");
        assertEquals(added, new TreeSet<>(cardRows().keySet()),
                "changeSet 157's card set is not the set v84 adds");
    }

    /**
     * No card is mapped without also being made to drop, and the two halves of the changeSet agree
     * with each other. A mapping with no drop row is the exact defect this changeSet exists to fix,
     * so re-introducing it silently is the regression to catch.
     */
    @Test
    void everyMappedCardAlsoGetsADropRowFromThatSameMob() throws IOException {
        Map<Integer, Integer> cards = cardRows();
        List<int[]> drops = dropRows();
        assertEquals(39, drops.size(), "changeSet 157 should add exactly 39 drop rows");

        for (int[] drop : drops) {
            int dropperId = drop[0];
            int cardId = drop[1];
            assertTrue(cards.containsKey(cardId),
                    "drop row for card " + cardId + " has no matching monstercarddata row");
            assertEquals(cards.get(cardId).intValue(), dropperId,
                    "card " + cardId + " is mapped to mob " + cards.get(cardId)
                            + " but its drop row hangs on mob " + dropperId);
            assertEquals(1, drop[2], "card " + cardId + " drop row must be quantity 1..1");
            assertEquals(1, drop[3], "card " + cardId + " drop row must be quantity 1..1");
            assertEquals(0, drop[4], "card " + cardId + " drop row must not be quest-gated; a questid "
                    + "makes Character.needQuestItem refuse the pickup for every other quest");
        }
    }

    /**
     * The chance is copied, never invented. drop_data's 576 pre-existing monster-card rows use only
     * 8000 and 24000, split strictly on the 2388xxx boss block. This asserts the new rows obey that
     * split <em>and</em> that the split is really about boss-ness, by checking each mob's own
     * {@code Mob.wz info/boss} leaf - two independent sources agreeing 39 times.
     */
    @Test
    void everyChanceIsOneOfTheTwoTheServerAlreadyUsedAndMatchesTheMobsBossFlag() throws IOException {
        for (int[] drop : dropRows()) {
            int dropperId = drop[0];
            int cardId = drop[1];
            int chance = drop[5];
            boolean bossCard = cardId >= BOSS_CARD_FLOOR;

            assertEquals(bossCard ? CHANCE_BOSS : CHANCE_NORMAL, chance,
                    "card " + cardId + " uses chance " + chance + ", which is not the value the "
                            + "existing rows use for its block");
            assertEquals(bossCard, isBoss(dropperId),
                    "card " + cardId + " sits in the " + (bossCard ? "boss" : "normal") + " id block "
                            + "but Mob.wz says mob " + dropperId + " is "
                            + (isBoss(dropperId) ? "a boss" : "not a boss")
                            + " - the id block and the boss flag must agree or the chance is wrong");
        }
    }

    /**
     * A drop row on a mob that spawns nowhere fixes nothing. 35 of the 39 mobs are spawned; the four
     * that are not are pinned by name in {@link #UNSPAWNED_ON_PURPOSE} so that the honest count
     * cannot quietly rot in either direction - placing one of them should force this list to shrink.
     */
    @Test
    void everyDropperExistsInMobWzAndAllButTheFourKnownGapsAreSpawned() throws IOException {
        Map<Integer, Integer> cards = cardRows();
        for (Map.Entry<Integer, Integer> row : cards.entrySet()) {
            assertNotNull(mobData(row.getValue()), "Mob.wz has no image for mob " + row.getValue()
                    + ", named by card " + row.getKey());
        }
        Set<Integer> wanted = new TreeSet<>(cards.values());
        Set<Integer> unspawned = new TreeSet<>(wanted);
        unspawned.removeAll(spawnedAmong(wanted));

        assertEquals(new TreeSet<>(UNSPAWNED_ON_PURPOSE.keySet()), unspawned,
                "the set of card mobs that spawn nowhere changed; expected only the four known v84 "
                        + "gaps " + UNSPAWNED_ON_PURPOSE + " but found " + unspawned);
    }

    /** The changeSet must exist, be registered, and not have disturbed the applied ones. */
    @Test
    void changeSet157IsRegisteredAndTheAppliedOnesAreUntouched() throws IOException {
        assertTrue(Files.isRegularFile(CHANGESET_157), "changeSet 157 seed file is missing");

        String changelog = Files.readString(
                Path.of("src", "main", "resources", "db", "changelog-data.xml"), StandardCharsets.ISO_8859_1);
        assertTrue(changelog.contains("157-monstercard-v84-data.sql"),
                "157 exists on disk but is not registered in changelog-data.xml, so it never runs");
        assertTrue(changelog.contains("DELETE FROM monstercarddata WHERE cardid IN ("),
                "changeSet 157 lost its monstercarddata rollback");

        // 155 and 156 are APPLIED - any edit fails checksum validation on the live database
        assertTrue(changelog.contains("155-evan-tutorial-drop-data.sql")
                        && changelog.contains("156-evan-chain-drop-data.sql"),
                "an applied changeSet vanished from the changelog");
    }

    // ---------------------------------------------------------------------------------------

    /** cardid -> mobid, parsed out of the monstercarddata half of the changeSet. */
    private static Map<Integer, Integer> cardRows() throws IOException {
        Map<Integer, Integer> rows = new LinkedHashMap<>();
        Matcher m = CARD_ROW.matcher(section("INSERT INTO monstercarddata"));
        while (m.find()) {
            rows.put(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
        }
        return rows;
    }

    /** {dropperid, itemid, min, max, questid, chance} per drop_data row in the changeSet. */
    private static List<int[]> dropRows() throws IOException {
        List<int[]> rows = new ArrayList<>();
        Matcher m = DROP_ROW.matcher(section("INSERT INTO drop_data"));
        while (m.find()) {
            int[] row = new int[6];
            for (int i = 0; i < 6; i++) {
                row[i] = Integer.parseInt(m.group(i + 1));
            }
            rows.add(row);
        }
        return rows;
    }

    /** The text of one INSERT statement, so the two statements' rows never bleed into each other. */
    private static String section(String insert) throws IOException {
        String sql = Files.readString(CHANGESET_157, StandardCharsets.UTF_8);
        int from = sql.indexOf(insert);
        assertTrue(from >= 0, "changeSet 157 no longer contains '" + insert + "'");
        int to = sql.indexOf(';', from);
        assertTrue(to > from, "'" + insert + "' in changeSet 157 is unterminated");
        return sql.substring(from, to);
    }

    /** Item.wz ids are 8-digit zero-padded; normalise through int or the lookup silently misses. */
    private static Integer cardMob(int cardId) {
        Data node = DataProviderFactory.getDataProvider(WZFiles.ITEM).getData("Consume/0238.img")
                .getChildByPath(String.format("%08d", cardId) + "/info/mob");
        return node == null ? null : DataTool.getInt(node, -1);
    }

    /** Mob.wz images are 7-digit zero-padded, one digit narrower than Item.wz's. */
    private static Data mobData(int mobId) {
        return DataProviderFactory.getDataProvider(WZFiles.MOB).getData(String.format("%07d", mobId) + ".img");
    }

    private static boolean isBoss(int mobId) {
        Data mob = mobData(mobId);
        assertNotNull(mob, "Mob.wz has no image for mob " + mobId);
        return DataTool.getInt(mob.getChildByPath("info/boss"), 0) == 1;
    }

    /**
     * Which of {@code wanted} are spawned as mob life anywhere in Map.wz, in ONE sweep of the tree.
     * Deliberately exhaustive rather than a lookup against a guessed map list - the point is to
     * prove a negative for the four unspawned mobs, which only a full walk can do.
     *
     * <p>{@code id} and {@code type} are siblings in no fixed order inside a life entry, so this
     * reads the enclosing {@code <imgdir>} rather than assuming the two leaves are adjacent.
     * Assuming adjacency is what made the first pass of this measurement miss a real spawn of mob
     * 8120106 on map 240070502 and report five gaps where there are four.
     */
    private static Set<Integer> spawnedAmong(Set<Integer> wanted) throws IOException {
        Set<Integer> found = new TreeSet<>();
        Pattern entry = Pattern.compile("<imgdir name=\"\\d+\">(.*?)</imgdir>", Pattern.DOTALL);
        Pattern id = Pattern.compile("<string name=\"id\" value=\"(\\d+)\"/>");

        try (var walk = Files.walk(Path.of(WZFiles.DIRECTORY, "Map.wz", "Map"))) {
            for (Path img : walk.filter(p -> p.getFileName().toString().endsWith(".img.xml")).toList()) {
                String xml = Files.readString(img, StandardCharsets.ISO_8859_1);
                int life = xml.indexOf("<imgdir name=\"life\">");
                if (life < 0) {
                    continue;
                }
                Matcher entries = entry.matcher(xml.substring(life));
                while (entries.find()) {
                    String body = entries.group(1);
                    if (!body.contains("<string name=\"type\" value=\"m\"/>")) {
                        continue;
                    }
                    Matcher hit = id.matcher(body);
                    if (hit.find()) {
                        int mobId = Integer.parseInt(hit.group(1));
                        if (wanted.contains(mobId)) {
                            found.add(mobId);
                        }
                    }
                }
                if (found.size() == wanted.size()) {
                    return found;
                }
            }
        }
        return found;
    }
}
