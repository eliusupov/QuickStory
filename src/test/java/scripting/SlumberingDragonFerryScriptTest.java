package scripting;

import constants.id.MapId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import scripting.npc.NPCConversationManager;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Olaf's ferry, Lith Harbour &lt;-&gt; Slumbering Dragon Island (work row R46, ticket 55).
 *
 * <p>The ride itself is two branches in {@code MapleMap.addPlayer}; these two NPC scripts are the
 * only way onto it, and both failure modes are silent - a missing {@code scripts/npc/<id>.js}
 * makes the click do nothing at all, and a wrong {@code warp()} target drops the passenger on a
 * ride map that never lands. So this drives both scripts against a stub {@code cm} and pins the
 * ride maps to the {@link MapId} constants the Java branches switch on.
 */
public class SlumberingDragonFerryScriptTest {
    private final AbstractScriptManager scriptManager = new AbstractScriptManager() {
    };

    @BeforeAll
    static void muteGraal() {
        System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
    }

    /** Stub of the NPCConversationManager surface the two ferry scripts touch. */
    public static class StubCm {
        private final int mapId;
        public final List<Integer> warps = new ArrayList<>();
        public final List<String> said = new ArrayList<>();
        public int disposes;

        StubCm(int mapId) {
            this.mapId = mapId;
        }

        public int getMapId() {
            return mapId;
        }

        public void warp(int mapid) {
            warps.add(mapid);
        }

        public void sendSimple(String text) {
            said.add(text);
        }

        public void sendOk(String text) {
            said.add(text);
        }

        public void dispose() {
            disposes++;
        }
    }

    /** start(), then {@code screens - 1} further action(1,0,0) calls, as the real handler does. */
    private StubCm run(int npcId, int fromMapId, int screens) throws Exception {
        ScriptEngine engine = scriptManager.getInvocableScriptEngine("npc/" + npcId + ".js");
        assertNotNull(engine, "npc/" + npcId + ".js did not evaluate");
        StubCm cm = new StubCm(fromMapId);
        engine.put("cm", cm);
        ((Invocable) engine).invokeFunction("start");
        for (int i = 1; i < screens; i++) {
            ((Invocable) engine).invokeFunction("action", 1, 0, 0);
        }
        return cm;
    }

    @Test
    void lithSideOlafBoardsTheOutboundRide() throws Exception {
        StubCm cm = run(1002101, MapId.LITH_HARBOUR, 2);

        assertEquals(List.of(MapId.FROM_LITH_TO_SDI), cm.warps);
        assertEquals(1, cm.said.size(), "1002101 should offer exactly one screen before boarding");
        assertEquals(1, cm.disposes);
    }

    @Test
    void islandSideOlafBoardsTheReturnRide() throws Exception {
        StubCm cm = run(1013207, MapId.SDI_TEMPORARY_HARBOR, 2);

        assertEquals(List.of(MapId.FROM_SDI_TO_LITH), cm.warps);
        assertEquals(1, cm.said.size());
        assertEquals(1, cm.disposes);
    }

    /**
     * 1013207 also stands on both ride maps. Boarding from there would be the ride-skip ticket 37
     * refuses - a passenger already at sea landing instantly.
     */
    @Test
    void olafOnDeckNeverReboards() throws Exception {
        for (int rideMap : new int[]{MapId.FROM_LITH_TO_SDI, MapId.FROM_SDI_TO_LITH}) {
            StubCm cm = run(1013207, rideMap, 1);

            assertTrue(cm.warps.isEmpty(), "1013207 warped from ride map " + rideMap);
            assertEquals(1, cm.disposes, "ride map " + rideMap + " should end the chat once");
        }
    }

    @Test
    void endingTheChatWarpsNobody() throws Exception {
        ScriptEngine engine = scriptManager.getInvocableScriptEngine("npc/1002101.js");
        assertNotNull(engine);
        StubCm cm = new StubCm(MapId.LITH_HARBOUR);
        engine.put("cm", cm);
        ((Invocable) engine).invokeFunction("action", -1, 0, 0);

        assertTrue(cm.warps.isEmpty());
        assertTrue(cm.said.isEmpty());
        assertEquals(1, cm.disposes);
    }

    /**
     * The stub above would keep passing if the ride maps in the scripts and the maps
     * {@code MapleMap.addPlayer} schedules on ever drifted apart. This pins them.
     */
    @Test
    void rideMapConstantsMatchTheMapWzIds() {
        assertEquals(200090080, MapId.FROM_LITH_TO_SDI);
        assertEquals(200090090, MapId.FROM_SDI_TO_LITH);
        assertEquals(914100000, MapId.SDI_TEMPORARY_HARBOR);
    }

    /**
     * Same reason as EvanJobAdvancementScriptTest's surface pin: the stub proves nothing about the
     * class the server actually binds as {@code cm}.
     */
    @Test
    void npcConversationManagerExposesTheScriptSurface() {
        for (String method : List.of("getMapId", "warp", "sendSimple", "sendOk", "dispose")) {
            boolean found = Stream.of(NPCConversationManager.class.getMethods())
                    .anyMatch(m -> m.getName().equals(method));
            assertTrue(found, "the ferry scripts call cm." + method + "(), which no longer exists");
        }
    }

    /**
     * move_RitSDI / move_SDIRit stay unwritten. Every working ferry in this tree has its
     * out00..out05 scripts missing too (move_RitRie, move_OrbEre, ...); writing them would skip the
     * scheduled ride this ticket just added, which is exactly ticket 37's refusal.
     */
    @Test
    void theOutPortalScriptsStayRefused() {
        assertNull(scriptManager.getInvocableScriptEngine("portal/move_RitSDI.js"));
        assertNull(scriptManager.getInvocableScriptEngine("portal/move_SDIRit.js"));
        assertNull(scriptManager.getInvocableScriptEngine("portal/move_RitRie.js"));
    }
}
