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
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * v83 legacy defect - not a v84 parity gap. {@code add-list/Item.txt} has no row for any
 * {@code 2049xxx} id, so v84 added nothing here; this is pre-existing v83 behaviour.
 *
 * <p>Eight items in {@code 0204.img} carry {@code info/randstat=1} and no {@code inc*} stats: the
 * chaos family. {@code scrollEquipWithId} used to route three of them - 2049100, 2049101, 2049102 -
 * to {@code scrollEquipWithChaos} by literal id. The other five, 2049103, 2049104, 2049112,
 * 2049113 and 2049114, fell to {@code default:} and ran {@code improveEquipStats} against a stat
 * map that holds nothing to apply, while the upgrade slot was consumed anyway. The branch now
 * selects on the harvested {@code randstat} flag, so all eight roll.
 *
 * <pre>
 *   mvnw.cmd test -Dtest=ChaosScrollRandstatRealLoad
 * </pre>
 *
 * <p><strong>Not a {@code *Test} class on purpose</strong>, same reason as its siblings:
 * {@link WZFiles#DIRECTORY} is {@code static final} and resolved once per JVM.
 */
class ChaosScrollRandstatRealLoad {

    /** The five that used to burn a slot and move nothing. */
    private static final List<Integer> UNHANDLED = List.of(2049103, 2049104, 2049112, 2049113, 2049114);

    /** The three that already worked, by id. */
    private static final List<Integer> ALREADY_HANDLED = List.of(2049100, 2049101, 2049102);

    private static final Path CONSUME_0204 = Path.of("wz", "Item.wz", "Consume", "0204.img.xml");

    private static final int TRIALS = 400;

    /** Same shim {@code TabletScrollRealLoad} uses; the provider constructor reads a table. */
    @BeforeAll
    static void bootTheProviderWithoutADatabase() {
        try (MockedStatic<DatabaseConnection> db = Mockito.mockStatic(DatabaseConnection.class)) {
            db.when(DatabaseConnection::getConnection).thenThrow(new SQLException("no database in tests"));
            ItemInformationProvider.getInstance();
        }
    }

    /** A fresh weapon with room to scroll and one stat above zero - chaos only moves stats it finds. */
    private static Equip weapon() {
        Equip equip = new Equip(1302000, (short) 0);
        equip.setUpgradeSlots((byte) 7);
        equip.setWatk((short) 10);
        return equip;
    }

    /** Every chaos scroll must be able to move a stat on the equip it is applied to. */
    @Test
    void everyChaosScrollMovesAStat() {
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        List<String> inert = new ArrayList<>();
        List<Integer> all = new ArrayList<>(ALREADY_HANDLED);
        all.addAll(UNHANDLED);

        for (int scrollId : all) {
            boolean moved = false;
            for (int i = 0; i < TRIALS && !moved; i++) {
                Equip equip = weapon();
                short before = equip.getWatk();
                Item result = ii.scrollEquipWithId(equip, scrollId, false, 0, false);
                if (result != null && ((Equip) result).getWatk() != before) {
                    moved = true;
                }
            }
            if (!moved) {
                inert.add(String.valueOf(scrollId));
            }
        }

        assertEquals(List.of(), inert, "chaos scrolls that never moved a stat in " + TRIALS
                + " applications each");
    }

    /** The flag has to be harvested at all, or the branch below it can never fire. */
    @Test
    void randstatIsHarvestedForAllEightChaosScrolls() {
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        for (int scrollId : randstatIds()) {
            Map<String, Integer> stats = ii.getEquipStats(scrollId);
            assertEquals(Integer.valueOf(1), stats.get("randstat"),
                    "getEquipStats(" + scrollId + ") did not harvest info/randstat");
        }
    }

    /** The eight ids the branch now covers are exactly the eight the data flags. */
    @Test
    void theDataFlagsExactlyTheEightScrollsWeHandle() {
        Set<Integer> expected = new TreeSet<>(ALREADY_HANDLED);
        expected.addAll(UNHANDLED);
        assertEquals(expected, randstatIds(), "items carrying info/randstat in Item.wz");
    }

    /** Acceptance criterion 3: the slot cost is unchanged - exactly one per application. */
    @Test
    void oneApplicationCostsExactlyOneUpgradeSlot() {
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        for (int scrollId : UNHANDLED) {
            int checked = 0;
            for (int i = 0; i < TRIALS; i++) {
                Equip equip = weapon();
                Item result = ii.scrollEquipWithId(equip, scrollId, false, 0, false);
                if (result == null) {
                    continue;   // cursed: the equip is destroyed, there is no slot left to count
                }
                assertEquals(6, ((Equip) result).getUpgradeSlots(),
                        "scroll " + scrollId + " did not consume exactly one of seven slots");
                checked++;
            }
            assertTrue(checked > 0, "scroll " + scrollId + " was cursed on all " + TRIALS + " trials");
        }
    }

    /** Read from the tree rather than listed by hand. */
    private static Set<Integer> randstatIds() {
        Set<Integer> ids = new TreeSet<>();
        try {
            Matcher m = Pattern.compile("<imgdir name=\"0(\\d{7})\">|<int name=\"randstat\" value=\"1\"/>")
                    .matcher(Files.readString(CONSUME_0204, StandardCharsets.UTF_8));
            int current = 0;
            while (m.find()) {
                if (m.group(1) != null) {
                    current = Integer.parseInt(m.group(1));
                } else if (current != 0) {
                    ids.add(current);
                }
            }
        } catch (IOException e) {
            throw new AssertionError("could not read 0204.img.xml", e);
        }
        return ids;
    }
}
