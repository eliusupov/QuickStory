package server.life;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Pins the 40 {@code String.wz/Npc.img/<id>/d0} leaves merged from the pristine v84 carve.
 *
 * <pre>
 *   mvnw.cmd -o test -Dtest=NpcDefaultTalkRealLoad
 * </pre>
 *
 * <p>{@link LifeFactory#getNPCDefaultTalk(int)} is the only reader of that leaf and substitutes the
 * literal {@code "(...)"} when it is absent, so clicking any of these NPCs without a script showed
 * {@code (...)} rather than the line v84 ships. Six of the forty carve values are untranslated
 * Korean (2001000, 2001001, 9001001, 9001002, 9001005, 9001006); they are merged verbatim, the same
 * trade commit {@code df9e779a9} made for the 11 Evan NPC names.
 *
 * <p>{@code String.wz/Npc.img/9901000/name} is deliberately still absent - 9901000 is a PlayerNPC
 * slot whose name comes from the database, never from {@code String.wz}. See ticket 57.
 */
class NpcDefaultTalkRealLoad {

    @ParameterizedTest
    @ValueSource(ints = {
            1012119, 1013000, 1022008, 1032002, 1032102, 1052002, 1052006, 1061000, 1061006,
            1063003, 1063004, 1063005, 1063006, 1063007, 1063008, 1063009, 1063010,
            2001000, 2001001, 2002, 2003, 2020002, 2030015, 2032004, 2050009, 2060005,
            2112003, 2112004, 22000,
            9000007, 9000008, 9000009, 9001001, 9001002, 9001005, 9001006, 9020000,
            9250005, 9250010, 9250022})
    void mergedNpcHasV84DefaultTalk(int npcId) {
        String talk = LifeFactory.getNPCDefaultTalk(npcId);

        assertNotNull(talk, "no d0 for npc " + npcId);
        assertNotEquals("(...)", talk, "npc " + npcId + " still falls back to the (...) default");
    }
}
