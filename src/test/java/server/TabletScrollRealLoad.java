package server;

import client.inventory.Equip;
import client.inventory.Item;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import provider.wz.WZFiles;
import tools.DatabaseConnection;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the 25 "Tablet" scrolls, 2047000-2047309, which could never succeed.
 *
 * <p>Every other scroll in the tree states one flat {@code info/success}. The tablets instead carry
 * a table - {@code info/successRates} and {@code info/cursedRates}, eight entries each - whose
 * index is how many scrolls the equip has already taken: 70% success / 10% cursed on an untouched
 * item, decaying to 7% / 100% on the eighth. {@code getEquipStats} has no reader for a table and
 * defaults {@code success} to 0, and {@code rollSuccessChance(0)} is
 * {@code Math.random() >= Math.pow(1 - 0, n)}, i.e. {@code >= 1.0}, which is false for every value
 * {@code Math.random()} can return. All 25 consumed a slot and failed, forever.
 *
 * <p>Two things the data forced, neither of them a guess:
 * <ul>
 *   <li>02047101 names its table {@code successes}, not {@code successRates} - Nexon's own typo,
 *       its eight values are identical to the other 24. Reading only the documented spelling would
 *       have left exactly one tablet still broken.</li>
 *   <li>{@code cursedRates} is read through the engine's existing meaning for {@code cursed} - a
 *       roll made only on failure, destroying the equip - rather than a new mechanic. That is the
 *       conservative reading: the fields already exist and already mean that.</li>
 * </ul>
 *
 * <p>{@link #onlyTheTabletsCarryARateTable()} is the blast-radius proof: nothing else in Item.wz
 * has such a table, and the new lookup is reached only when {@code success} is 0, which no working
 * scroll has.
 *
 * <pre>
 *   mvnw.cmd test -Dtest=TabletScrollRealLoad
 * </pre>
 *
 * <p><strong>Not a {@code *Test} class on purpose</strong>, same reason as its siblings:
 * {@link WZFiles#DIRECTORY} is {@code static final} and resolved once per JVM, and
 * {@code MobSkillFactoryTest} points {@code wz-path} at an empty {@code @TempDir}.
 */
class TabletScrollRealLoad {

    /** Tablet for One-Handed Weapon for ATT, +2 PAD. */
    private static final int TABLET = 2047000;

    /** The one that spells its table "successes". */
    private static final int MISSPELLED_TABLET = 2047101;

    private static final Path ITEM_WZ = Path.of("wz", "Item.wz");

    private static final Pattern RATE_TABLE =
            Pattern.compile("<imgdir name=\"0(\\d{7})\">|name=\"(successRates|successes|cursedRates)\"");

    private static final int TRIALS = 400;

    /** Same shim {@code MasteryBookJobMatchRealLoad} uses; the provider constructor reads a table. */
    @BeforeAll
    static void bootTheProviderWithoutADatabase() {
        try (MockedStatic<DatabaseConnection> db = Mockito.mockStatic(DatabaseConnection.class)) {
            db.when(DatabaseConnection::getConnection).thenThrow(new SQLException("no database in tests"));
            ItemInformationProvider.getInstance();
        }
    }

    /** A fresh weapon with room to scroll, already carrying {@code applied} successful scrolls. */
    private static Equip weaponAt(int applied) {
        Equip equip = new Equip(1302000, (short) 0);
        equip.setUpgradeSlots((byte) 7);
        equip.setLevel((byte) applied);
        return equip;
    }

    /** Successful applications of {@code scrollId} to a fresh equip, out of {@link #TRIALS}. */
    private static int successesAt(int scrollId, int applied) {
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        int successes = 0;
        for (int i = 0; i < TRIALS; i++) {
            Equip equip = weaponAt(applied);
            Item result = ii.scrollEquipWithId(equip, scrollId, false, 0, false);
            if (result != null && ((Equip) result).getLevel() > applied) {
                successes++;
            }
        }
        return successes;
    }

    /**
     * The regression. Unfixed this is 0 out of 400 for every tablet, at every scroll count,
     * because rollSuccessChance(0) can never return true.
     */
    @Test
    void aTabletOnAnUntouchedItemUsuallySucceeds() {
        int successes = successesAt(TABLET, 0);
        assertTrue(successes > 0, "tablet " + TABLET + " succeeded " + successes + " times out of "
                + TRIALS + " on an unscrolled item, where its table states 70%");
    }

    /** The typo'd one must work too, or the fix covers 24 of 25. */
    @Test
    void theTabletThatMisspellsItsTableAlsoWorks() {
        int successes = successesAt(MISSPELLED_TABLET, 0);
        assertTrue(successes > 0, "tablet " + MISSPELLED_TABLET + " (spells its table \"successes\") "
                + "succeeded " + successes + " times out of " + TRIALS);
    }

    /**
     * The table is indexed by scroll count, so it must actually be read at the index - a fix that
     * always took entry 0 would pass the test above and still be wrong. 70% against 7%.
     */
    @Test
    void aWellScrolledItemIsHarderToScrollThanAFreshOne() {
        int fresh = successesAt(TABLET, 0);
        int worn = successesAt(TABLET, 7);
        assertTrue(fresh > worn, "tablet " + TABLET + " succeeded " + fresh + "/" + TRIALS
                + " on an unscrolled item and " + worn + "/" + TRIALS + " on one that already took "
                + "seven, but its table states 70% against 7% - the scroll count is not being read");
    }

    /** Every tablet, not just the two named above. */
    @Test
    void allTwentyFiveTabletsCanSucceed()  {
        List<String> dead = new ArrayList<>();
        int checked = 0;
        for (int scrollId : tabletIds()) {
            if (successesAt(scrollId, 0) == 0) {
                dead.add(String.valueOf(scrollId));
            }
            checked++;
        }
        assertEquals(25, checked, "expected 25 tablets in 0204.img");
        assertEquals(List.of(), dead, "tablets that still never succeed");
    }

    /**
     * Blast radius. The rate-table lookup is the only new behaviour, and it can only fire for an
     * item that owns such a table; this asserts the 25 tablets are the only ones in Item.wz that
     * do, so no other scroll's odds moved.
     */
    @Test
    void onlyTheTabletsCarryARateTable() throws IOException {
        Set<Integer> withTable = new TreeSet<>();
        try (Stream<Path> files = Files.walk(ITEM_WZ)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".xml")).toList()) {
                Matcher m = RATE_TABLE.matcher(Files.readString(file, StandardCharsets.UTF_8));
                int current = 0;
                while (m.find()) {
                    if (m.group(1) != null) {
                        current = Integer.parseInt(m.group(1));
                    } else if (current != 0) {
                        withTable.add(current);
                    }
                }
            }
        }
        assertEquals(tabletIds(), withTable,
                "items carrying successRates/successes/cursedRates outside the tablet block");
    }

    /** 2047000-2047309, read from the tree rather than listed by hand. */
    private static Set<Integer> tabletIds() {
        Set<Integer> ids = new TreeSet<>();
        try {
            Matcher m = Pattern.compile("<imgdir name=\"(02047\\d{3})\">")
                    .matcher(Files.readString(ITEM_WZ.resolve(Path.of("Consume", "0204.img.xml")),
                            StandardCharsets.UTF_8));
            while (m.find()) {
                ids.add(Integer.parseInt(m.group(1)));
            }
        } catch (IOException e) {
            throw new AssertionError("could not read 0204.img.xml", e);
        }
        assertEquals(25, ids.size(), "expected 25 tablet items, found " + ids.size());
        return ids;
    }
}
