package server;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import tools.DatabaseConnection;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 1142152/1142155: the two Evan medals had no {@code String.wz/Eqp.img/1142xxx} entry at all, so
 * {@code Character.getMedalText()} built {@code "<" + getName(id) + "> "} over a null and chat
 * rendered {@code <null>}. Values confirmed against two independent sources:
 * {@code porting-resources/evan-xml/extracted/Evan WZ/String/Eqp.img.xml:1492-1503} and the
 * pristine v84 {@code Quest.wz/QuestInfo.img/29934,29937}, whose {@code viewMedalItem} and quest
 * name corroborate the same two strings.
 */
class EvanMedalNameRealLoad {

    /** Same pattern as MasteryBookJobMatchRealLoad: no database in a test JVM. */
    @BeforeAll
    static void bootTheProviderWithoutADatabase() {
        try (MockedStatic<DatabaseConnection> db = Mockito.mockStatic(DatabaseConnection.class)) {
            db.when(DatabaseConnection::getConnection).thenThrow(new SQLException("no database in tests"));
            ItemInformationProvider.getInstance();
        }
    }

    @Test
    void bothEvanMedalsResolveTheirName() {
        assertEquals("Well-Behaved Child", ItemInformationProvider.getInstance().getName(1142152));
        assertEquals("Secret Organization Temporary Member", ItemInformationProvider.getInstance().getName(1142155));
    }
}
