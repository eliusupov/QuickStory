package scripting;

import client.Character;
import client.command.CommandsExecutor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import scripting.npc.NPCConversationManager;
import tools.Pair;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandsNpcScriptTest {
    private static final int BACK_TO_RANKS = 10;
    private static final int PREVIOUS_PAGE = 11;
    private static final int NEXT_PAGE = 12;
    private static final Pattern COMMAND_ROW = Pattern.compile("#L\\d+# ([!@])([^ ]+) - ");
    private final AbstractScriptManager scriptManager = new AbstractScriptManager() {
    };

    @BeforeAll
    static void muteGraal() {
        System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
    }

    /** Public surface supplied to the real NPC script. */
    public static class StubCm {
        private final StubPlayer player;
        public final List<String> said = new ArrayList<>();
        public int disposes;

        StubCm(int gmLevel) {
            player = new StubPlayer(gmLevel);
        }

        public StubPlayer getPlayer() {
            return player;
        }

        public void sendSimple(String text) {
            said.add(text);
        }

        public void dispose() {
            disposes++;
        }
    }

    public static class StubPlayer {
        private final int gmLevel;

        StubPlayer(int gmLevel) {
            this.gmLevel = gmLevel;
        }

        public int gmLevel() {
            return gmLevel;
        }
    }

    private static class Session {
        private final Invocable script;
        private final StubCm cm;

        Session(Invocable script, StubCm cm) {
            this.script = script;
            this.cm = cm;
        }

        void select(int selection) throws Exception {
            script.invokeFunction("action", 1, 0, selection);
        }

        String dialogue() {
            return cm.said.get(cm.said.size() - 1);
        }
    }

    private Session start(int gmLevel) throws Exception {
        ScriptEngine engine = scriptManager.getInvocableScriptEngine("npc/commands.js");
        assertNotNull(engine, "npc/commands.js did not evaluate");
        StubCm cm = new StubCm(gmLevel);
        engine.put("cm", cm);
        Invocable script = (Invocable) engine;
        script.invokeFunction("start");
        return new Session(script, cm);
    }

    @Test
    void everyGm6RankListsTheRealExecutorCommandsOnceInOrder() throws Exception {
        List<Pair<List<String>, List<String>>> expected = CommandsExecutor.getInstance().getGmCommands();

        for (int rank = 0; rank <= 6; rank++) {
            Session session = start(6);
            session.select(rank);
            List<String> actual = new ArrayList<>();
            List<String> pages = new ArrayList<>();
            while (true) {
                String page = session.dialogue();
                pages.add(page);
                assertTrue(commandRows(page).size() <= 10, "rank " + rank + " page exceeds ten commands");
                actual.addAll(commandNames(page));
                if (!page.contains("#L" + NEXT_PAGE + "#Next page#l")) {
                    break;
                }
                session.select(NEXT_PAGE);
            }

            assertEquals(expected.get(rank).getLeft(), actual, "rank " + rank + " changed command order or coverage");
            assertFalse(pages.get(0).contains("#L" + PREVIOUS_PAGE + "#Previous page#l"));
            assertFalse(pages.get(pages.size() - 1).contains("#L" + NEXT_PAGE + "#Next page#l"));
        }
    }

    @Test
    void pageNavigationReturnsToThePermissionBoundRankChooser() throws Exception {
        Session session = start(6);
        session.select(0);
        String firstPage = session.dialogue();
        assertTrue(firstPage.contains("#L" + NEXT_PAGE + "#Next page#l"));
        assertFalse(firstPage.contains("#L" + PREVIOUS_PAGE + "#Previous page#l"));

        session.select(NEXT_PAGE);
        assertTrue(session.dialogue().contains("#L" + PREVIOUS_PAGE + "#Previous page#l"));
        session.select(PREVIOUS_PAGE);
        assertEquals(firstPage, session.dialogue());

        session.select(BACK_TO_RANKS);
        assertRankChooser(session.dialogue(), 6);
        session.select(0);
        while (session.dialogue().contains("#L" + NEXT_PAGE + "#Next page#l")) {
            session.select(NEXT_PAGE);
        }
        assertFalse(session.dialogue().contains("#L" + NEXT_PAGE + "#Next page#l"));
    }

    @Test
    void gm2KeepsItsRankVisibilityHeadingsAndPrefixes() throws Exception {
        Session session = start(2);
        assertRankChooser(session.dialogue(), 2);

        for (int rank = 0; rank <= 2; rank++) {
            session.select(rank);
            String page = session.dialogue();
            assertTrue(page.contains("#b" + List.of("Common", "Donator", "JrGM").get(rank) + "#k"));
            assertTrue(commandRows(page).stream().allMatch(row -> row.charAt(0) == (rank < 2 ? '@' : '!')));
            session.select(BACK_TO_RANKS);
        }
    }

    @Test
    void npcConversationManagerExposesTheCommandsScriptSurface() {
        for (String method : List.of("getPlayer", "sendSimple", "dispose")) {
            assertTrue(Stream.of(NPCConversationManager.class.getMethods()).anyMatch(m -> m.getName().equals(method)),
                    "commands.js calls cm." + method + "(), which no longer exists");
        }
        assertTrue(Stream.of(Character.class.getMethods()).anyMatch(m -> m.getName().equals("gmLevel")),
                "commands.js calls cm.getPlayer().gmLevel(), which no longer exists");
    }

    private static void assertRankChooser(String dialogue, int maxRank) {
        for (int rank = 0; rank <= 6; rank++) {
            String entry = "#L" + rank + "#" + List.of("Common", "Donator", "JrGM", "GM", "SuperGM", "Developer", "Admin").get(rank) + "#l";
            assertEquals(rank <= maxRank, dialogue.contains(entry));
        }
    }

    private static List<String> commandRows(String dialogue) {
        Matcher matcher = COMMAND_ROW.matcher(dialogue);
        List<String> rows = new ArrayList<>();
        while (matcher.find()) {
            rows.add(matcher.group(1) + matcher.group(2));
        }
        return rows;
    }

    private static List<String> commandNames(String dialogue) {
        return commandRows(dialogue).stream().map(row -> row.substring(1)).toList();
    }
}
