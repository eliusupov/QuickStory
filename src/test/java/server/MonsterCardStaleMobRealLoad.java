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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins changeSet 174 - the seven {@code monstercarddata} rows that named a different mob than the
 * card's own {@code Item.wz/Consume/0238.img/<id>/info/mob} leaf. v83 legacy, not a v84 parity gap.
 *
 * <pre>
 *   mvnw.cmd test -Dtest=MonsterCardStaleMobRealLoad
 * </pre>
 *
 * <p><strong>Not a {@code *Test} class on purpose</strong>, same reason as
 * {@link MonsterCardV84RealLoad}: {@link WZFiles#DIRECTORY} is resolved once per JVM.
 */
class MonsterCardStaleMobRealLoad {

    private static final Path CHANGESET_174 = Path.of(
            "src", "main", "resources", "db", "data", "174-monstercarddata-wz-mob-corrections.sql");

    private static final Pattern UPDATE_ROW = Pattern.compile(
            "UPDATE monstercarddata SET mobid = (\\d+) WHERE cardid = (\\d{7});");

    @BeforeAll
    static void muteGraal() {
        System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
        assertTrue(Files.isRegularFile(Path.of(WZFiles.DIRECTORY, "Item.wz", "Consume", "0238.img.xml")),
                "wz-path resolved to '" + WZFiles.DIRECTORY + "', which holds no Item.wz/Consume/0238.img"
                        + " - another test class won the WZFiles.DIRECTORY race");
    }

    /** Every corrected value must be the one the card's own WZ leaf names. Nothing is invented. */
    @Test
    void allSevenCorrectionsAreTheValueTheCardItselfNames() throws IOException {
        Map<Integer, Integer> rows = updates();
        assertEquals(7, rows.size(), "changeSet 174 should carry exactly seven UPDATEs");

        for (Map.Entry<Integer, Integer> row : rows.entrySet()) {
            Integer wzMob = cardMob(row.getKey());
            assertNotNull(wzMob, "card " + row.getKey() + " has no info/mob leaf in Item.wz");
            assertEquals(wzMob, row.getValue(), "changeSet 174 sets card " + row.getKey() + " to mob "
                    + row.getValue() + " but its Item.wz info/mob leaf says " + wzMob);
        }
    }

    /** 2388068/2388069 are swapped with each other; a half-applied swap is worse than neither. */
    @Test
    void theSwappedPairIsCorrectedAsAPair() throws IOException {
        Map<Integer, Integer> rows = updates();
        assertEquals(Integer.valueOf(3300007), rows.get(2388068), "2388068 must take 2388069's old mob");
        assertEquals(Integer.valueOf(3300006), rows.get(2388069), "2388069 must take 2388068's old mob");
    }

    /** Rows are corrected in place: no INSERT, no DELETE, and drop_data is not touched. */
    @Test
    void changeSet174IsUpdatesOnlyAndIsRegistered() throws IOException {
        String sql = Files.readString(CHANGESET_174, StandardCharsets.UTF_8);
        String statements = sql.replaceAll("(?m)^--.*$", "");
        assertTrue(!statements.contains("INSERT") && !statements.contains("DELETE"),
                "changeSet 174 must be UPDATEs only - row count in monstercarddata is unchanged");
        assertTrue(!statements.contains("drop_data"),
                "changeSet 174 must not touch drop_data; those are live drop sources");
        assertTrue(sql.contains("4031405") && sql.contains("8732"),
                "changeSet 174 must record why R19 (4031405 / questid 8732) was withdrawn");

        String changelog = Files.readString(
                Path.of("src", "main", "resources", "db", "changelog-data.xml"), StandardCharsets.ISO_8859_1);
        assertTrue(changelog.contains("174-monstercarddata-wz-mob-corrections.sql"),
                "174 exists on disk but is not registered in changelog-data.xml, so it never runs");
    }

    /** The withdrawn R19 row's quest genuinely has no data anywhere; the refusal must stay true. */
    @Test
    void quest8732HasNoDataInAnyQuestArchive() throws IOException {
        for (String img : new String[]{"QuestInfo.img.xml", "Check.img.xml", "Act.img.xml"}) {
            Path p = Path.of("wz", "Quest.wz", img);
            assertTrue(Files.isRegularFile(p), "missing " + p);
            assertTrue(!Files.readString(p, StandardCharsets.ISO_8859_1).contains("name=\"8732\""),
                    img + " now defines quest 8732 - re-open R19 rather than leaving it withdrawn");
        }
    }

    // ---------------------------------------------------------------------------------------

    /** cardid -> corrected mobid, parsed out of the changeSet's UPDATE statements. */
    private static Map<Integer, Integer> updates() throws IOException {
        Map<Integer, Integer> rows = new LinkedHashMap<>();
        Matcher m = UPDATE_ROW.matcher(Files.readString(CHANGESET_174, StandardCharsets.UTF_8));
        while (m.find()) {
            rows.put(Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1)));
        }
        return rows;
    }

    /** Item.wz ids are 8-digit zero-padded; normalise through int or the lookup silently misses. */
    private static Integer cardMob(int cardId) {
        Data node = DataProviderFactory.getDataProvider(WZFiles.ITEM).getData("Consume/0238.img")
                .getChildByPath(String.format("%08d", cardId) + "/info/mob");
        return node == null ? null : DataTool.getInt(node, -1);
    }
}
