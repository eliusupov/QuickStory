package client;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import server.maps.MapleMap;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

class MonsterBookTest {

    /**
     * Runs the real {@code MonsterBook.applyMainStatBuff} against a real job id and hands back the
     * character it mutated. Only the three side effects are stubbed - the stat packet, the DB write
     * and the chat line - so the branch under test is the shipping one.
     */
    private static Character completeACardSet(Job job) throws Exception {
        Client c = Mockito.mock(Client.class);
        Character chr = Mockito.spy(Character.getDefault(c));
        doNothing().when(chr).updateSingleStat(any(Stat.class), anyInt());
        doNothing().when(chr).saveCharToDB();
        doNothing().when(chr).dropMessage(anyInt(), anyString());
        when(c.getPlayer()).thenReturn(chr);
        chr.setJob(job);

        Method apply = MonsterBook.class.getDeclaredMethod("applyMainStatBuff", Client.class, int.class);
        apply.setAccessible(true);
        apply.invoke(new MonsterBook(), c, 2380000);
        return chr;
    }

    /**
     * The card-set bonus is the owner's own customisation and it persists: +1 to the class's main
     * stat, saved to the DB. Evan is an INT class, but the chain asked {@code job.isA(MAGICIAN)},
     * which is false for 2200-2218 because {@code isA} compares id/100 - 22 against 2 - so every
     * Evan fell through to the trailing "default to STR" case. Every set an Evan completed
     * permanently added STR to a magician.
     */
    @Test
    void evanGetsIntFromACardSet() throws Exception {
        int baseStr = Character.getDefault(Mockito.mock(Client.class)).getStr();
        int baseInt = Character.getDefault(Mockito.mock(Client.class)).getInt();

        assertAll(
                () -> assertEquals(baseInt + 1, completeACardSet(Job.MAGICIAN).getInt(), "job 200 INT"),
                () -> assertEquals(baseInt + 1, completeACardSet(Job.EVAN1).getInt(), "job 2200 INT"),
                () -> assertEquals(baseInt + 1, completeACardSet(Job.EVAN10).getInt(), "job 2218 INT"),
                () -> assertEquals(baseInt + 1, completeACardSet(Job.EVAN).getInt(), "job 2001 INT"),

                () -> assertEquals(baseStr, completeACardSet(Job.EVAN1).getStr(), "job 2200 keeps STR"),
                () -> assertEquals(baseStr, completeACardSet(Job.EVAN10).getStr(), "job 2218 keeps STR"),
                () -> assertEquals(baseStr, completeACardSet(Job.EVAN).getStr(), "job 2001 keeps STR"),

                // the neighbouring branches, so a change to either side of the chain shows up here
                () -> assertEquals(baseStr + 1, completeACardSet(Job.WARRIOR).getStr(), "job 100 STR"),
                () -> assertEquals(baseInt + 1, completeACardSet(Job.BLAZEWIZARD1).getInt(), "job 1200 INT"),
                () -> assertEquals(baseStr + 1, completeACardSet(Job.ARAN1).getStr(), "job 2100 STR"),
                () -> assertEquals(baseStr + 1, completeACardSet(Job.LEGEND).getStr(), "job 2000 STR")
        );
    }

    /**
     * The "you cannot complete a set until you get a job" gate asked {@code isA(Job.BEGINNER)},
     * which is only ever true for job 0 - {@code isA} needs id/100 == 0 - so the Noblesse (1000),
     * Legend (2000) and Evan (2001) beginner jobs walked straight through it and took the bonus at
     * level 1. The gate now asks {@code Character.isBeginnerJob()}, which names all four.
     */
    @Test
    void theBeginnerGateSeesEveryBeginnerJob() {
        assertAll(
                () -> assertFalse(Job.NOBLESSE.isA(Job.BEGINNER), "isA misses 1000"),
                () -> assertFalse(Job.LEGEND.isA(Job.BEGINNER), "isA misses 2000"),
                () -> assertFalse(Job.EVAN.isA(Job.BEGINNER), "isA misses 2001"),

                () -> assertEquals(4, fifthCardOf(Job.BEGINNER, 9), "job 0 blocked at level 9"),
                () -> assertEquals(4, fifthCardOf(Job.NOBLESSE, 9), "job 1000 blocked at level 9"),
                () -> assertEquals(4, fifthCardOf(Job.LEGEND, 9), "job 2000 blocked at level 9"),
                () -> assertEquals(4, fifthCardOf(Job.EVAN, 9), "job 2001 blocked at level 9"),

                () -> assertEquals(5, fifthCardOf(Job.EVAN, 10), "job 2001 allowed at level 10"),
                () -> assertEquals(5, fifthCardOf(Job.EVAN1, 9), "job 2200 has advanced"),
                () -> assertEquals(5, fifthCardOf(Job.MAGICIAN, 9), "job 200 has advanced")
        );
    }

    /** Feeds the real addCard() five copies of one card and reports the count it settled on. */
    private static int fifthCardOf(Job job, int level) throws Exception {
        Client c = Mockito.mock(Client.class);
        Character chr = Mockito.spy(Character.getDefault(c));
        doNothing().when(chr).updateSingleStat(any(Stat.class), anyInt());
        doNothing().when(chr).saveCharToDB();
        doNothing().when(chr).dropMessage(anyInt(), anyString());
        Mockito.doReturn(Mockito.mock(MapleMap.class)).when(chr).getMap();
        when(c.getPlayer()).thenReturn(chr);
        chr.setJob(job);
        chr.setLevel(level);

        MonsterBook book = new MonsterBook();
        for (int i = 0; i < 5; i++) {
            book.addCard(c, 2380000);
        }
        return book.getCards().get(2380000);
    }
}
