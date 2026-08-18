package server.quest.actions;

import client.Character;
import client.Job;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import provider.Data;
import provider.wz.XMLDomMapleData;
import provider.wz.XMLWZFile;
import server.quest.Quest;
import server.quest.QuestActionType;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Ticket 34. {@code sp} was not in {@link QuestActionType#getByWZName}, so 28 Evan quests silently
 * dropped their SP reward.
 * <p>
 * The node is a <em>list</em> of awards, each with its own value and its own job filter - copying
 * {@code ExpAction}'s flat-int shape would pay every job the first entry. Everything below drives
 * the real {@code XMLDomMapleData} reader over that nested shape, not a hand-built stub, so a
 * regression in either the parse or the filter fails here.
 */
class SpActionTest {

    @TempDir
    Path tmp;

    /**
     * The shape as it appears in the v84 {@code Act.img}: {@code sp/<i>/{sp_value, job/<j>}}.
     * Two branches of one quest id paying different amounts is the whole reason for the nesting.
     */
    private static final String TWO_BRANCHES = """
            <imgdir name="sp">
              <imgdir name="0">
                <int name="sp_value" value="1"/>
                <imgdir name="job">
                  <int name="0" value="2200"/>
                </imgdir>
              </imgdir>
              <imgdir name="1">
                <int name="sp_value" value="3"/>
                <imgdir name="job">
                  <int name="0" value="2210"/>
                  <int name="1" value="2211"/>
                </imgdir>
              </imgdir>
            </imgdir>
            """;

    private Data node(String xml) throws IOException {
        Path file = Files.writeString(tmp.resolve("sp.xml"), xml, StandardCharsets.UTF_8);
        try (FileInputStream fis = new FileInputStream(file.toFile())) {
            return new XMLDomMapleData(fis, tmp);
        }
    }

    private SpAction action(String xml) throws IOException {
        return new SpAction(mock(Quest.class), node(xml));
    }

    private Character playerOf(Job job) {
        Character chr = mock(Character.class);
        when(chr.getJob()).thenReturn(job);
        return chr;
    }

    @Test
    void theWzNameIsMappedAtAll() {
        assertEquals(QuestActionType.SP, QuestActionType.getByWZName("sp"));
    }

    /** Entry 0's job list is the match, so entry 0's value is paid - not entry 1's. */
    @Test
    void theEntryMatchingThePlayersJobIsTheOnePaid() throws IOException {
        Character chr = playerOf(Job.EVAN1); // 2200 -> skillbook 0
        action(TWO_BRANCHES).run(chr, null);
        verify(chr).gainSp(1, 0, false);
    }

    /**
     * The second entry, reached only by walking past the first - this is the assertion that fails
     * if the parser reads {@code sp/0} as a flat value and pays everyone the same.
     */
    @Test
    void aLaterEntryIsReachedAndItsOwnValueIsUsed() throws IOException {
        Character chr = playerOf(Job.EVAN2); // 2210 -> skillbook 1
        action(TWO_BRANCHES).run(chr, null);
        verify(chr).gainSp(3, 1, false);
    }

    /** Job 2211 shares entry 1 with 2210 but has its own skill book - both halves must move. */
    @Test
    void everyJobInAnEntrysListMatchesIt() throws IOException {
        Character chr = playerOf(Job.EVAN3); // 2211 -> skillbook 2
        action(TWO_BRANCHES).run(chr, null);
        verify(chr).gainSp(3, 2, false);
    }

    /**
     * No entry lists this job, so the first entry is paid - into the book <em>its</em> scope names,
     * 2200's book 0, not the player's current one.
     * <p>
     * This used to award nothing, and that was the live defect: an Evan who advanced with a growth
     * quest still open forfeited its point for good, because the book he had just left has no other
     * income. A reward is now attached to the growth that wrote it, not to where the player is
     * standing. Who may complete an Evan quest at all is still {@code Check.img}'s job, and it
     * admits only 2200-2218 on every quest that carries an {@code sp} node.
     */
    @Test
    void aJobOnNoListIsPaidTheFirstEntryIntoThatEntrysBook() throws IOException {
        Character chr = playerOf(Job.MAGICIAN);
        action(TWO_BRANCHES).run(chr, null);
        verify(chr).gainSp(1, 0, false);
    }

    /** An award carrying no {@code job} child is unfiltered and applies to whoever completes it. */
    @Test
    void anAwardWithNoJobFilterAppliesToAnyone() throws IOException {
        String xml = """
                <imgdir name="sp">
                  <imgdir name="0">
                    <int name="sp_value" value="2"/>
                  </imgdir>
                </imgdir>
                """;
        Character chr = playerOf(Job.BOWMAN); // 300 -> skillbook 0
        action(xml).run(chr, null);
        verify(chr).gainSp(2, 0, false);
    }

    /**
     * The award goes through {@code gainSp(delta, skillbook, silent)} - the extended ten-slot
     * {@code sp} column Evan needs - and never through a single-value setter. Asserted by leaving
     * {@code gainSp} as the only interaction beyond the job read.
     */
    @Test
    void theAwardGoesThroughTheExtendedSpPathAndNothingElse() throws IOException {
        Character chr = playerOf(Job.EVAN2);
        action(TWO_BRANCHES).run(chr, null);
        verify(chr).getJob();
        verify(chr).gainSp(3, 1, false);
        verifyNoMoreInteractions(chr);
    }

    /**
     * The ticket's open question: was anything <em>other</em> than Evan losing SP all along? No.
     * Every {@code sp} node in the server's {@code Act.img} belongs to a {@code 22xxx} quest, so
     * before the v84 Evan merge the count was zero and this bug cost the live server nothing.
     * <p>
     * The {@code exp} tally is the control: with no walk at all the {@code sp} assertion passes
     * vacuously, so the test would be unable to fail. {@link server.V84Wz} is package-private in
     * {@code server}, hence the explicit {@link XMLWZFile} here - same reason it gives, namely that
     * {@code WZFiles#DIRECTORY} is a per-JVM {@code static final} another test class redirects.
     */
    @Test
    void noQuestOutsideTheEvanChainCarriesAnSpNode() {
        Data act = new XMLWZFile(Path.of("wz", "Quest.wz")).getData("Act.img");
        int expNodes = 0;
        for (Data quest : act.getChildren()) {
            for (Data phase : quest.getChildren()) {
                if (phase.getChildByPath("exp") != null) {
                    expNodes++;
                }
                if (phase.getChildByPath("sp") != null) {
                    int id = Integer.parseInt(quest.getName());
                    assertTrue(id >= 22000 && id <= 22999,
                            "Act.img/" + id + "/" + phase.getName() + "/sp - a non-Evan quest has been "
                                    + "silently dropping SP too; ticket 34 measured this as zero");
                }
            }
        }
        assertTrue(expNodes > 1000, "walked the tree and found only " + expNodes
                + " exp rewards - the walk is broken, so the sp finding above means nothing");
    }
}
