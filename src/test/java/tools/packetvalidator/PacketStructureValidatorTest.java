package tools.packetvalidator;

import client.Character;
import client.inventory.Item;
import net.packet.Packet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import server.life.NPC;
import server.maps.MapItem;
import server.maps.MapObject;
import server.maps.Reactor;
import tools.PacketCreator;
import tools.packetvalidator.PacketStructureValidator.Result;
import tools.packetvalidator.PacketStructureValidator.Status;

import java.awt.Point;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Runs real {@link PacketCreator} output through the checked-in v84 client decode models and
 * asserts the client would consume every packet exactly - no read past the end (under-send, which
 * is the ZException error-38 crash) and no bytes left over (over-send).
 *
 * <p>Coverage is deliberately small and explicit. See {@code tools/v84/derive-decode-models.py}
 * for what is modelled, what is rejected, and why.
 */
class PacketStructureValidatorTest {

    private static Map<String, DecodeModel> models;

    /** modelName -> a real PacketCreator call that emits exactly that shape. */
    private static final Map<String, Supplier<Packet>> EMITTERS = new LinkedHashMap<>();

    @BeforeAll
    static void loadModels() {
        models = PacketStructureModels.loadVerified();

        EMITTERS.put("DROP_ITEM_FROM_MAPOBJECT/spawn-item",
                () -> PacketCreator.dropItemFromMapObject(null, itemDrop(), new Point(1, 2), new Point(3, 4),
                        (byte) 1, (short) 0));
        EMITTERS.put("DROP_ITEM_FROM_MAPOBJECT/spawn-meso",
                () -> PacketCreator.dropItemFromMapObject(null, mesoDrop(), new Point(1, 2), new Point(3, 4),
                        (byte) 1, (short) 0));
        EMITTERS.put("DROP_ITEM_FROM_MAPOBJECT/update-item",
                () -> PacketCreator.updateMapItemObject(itemDrop(), true));
        EMITTERS.put("KILL_MONSTER/normal", () -> PacketCreator.killMonster(1234, true));
        EMITTERS.put("SKILL_LEARN_ITEM_RESULT/result", () -> {
            Character chr = mock(Character.class);
            when(chr.getId()).thenReturn(7);
            return PacketCreator.skillBookResult(chr, 1001, 20, true, true);
        });
        EMITTERS.put("COOLDOWN", () -> PacketCreator.skillCooldown(1121000, 30));
        EMITTERS.put("REACTOR_SPAWN", () -> PacketCreator.spawnReactor(reactor()));
        EMITTERS.put("REACTOR_HIT", () -> PacketCreator.triggerReactor(reactor(), 0));
        EMITTERS.put("REACTOR_DESTROY", () -> PacketCreator.destroyReactor(reactor()));
        EMITTERS.put("SPAWN_DOOR", () -> PacketCreator.spawnDoor(9, new Point(5, 6), true));
        EMITTERS.put("REMOVE_DOOR", () -> PacketCreator.removeDoor(9, false));
        EMITTERS.put("SPAWN_NPC", () -> PacketCreator.spawnNPC(npc()));
        EMITTERS.put("SPAWN_NPC_REQUEST_CONTROLLER", () -> PacketCreator.spawnNPCRequestController(npc(), true));
        EMITTERS.put("REMOVE_PLAYER_FROM_MAP", () -> PacketCreator.removePlayerFromMap(42));
        EMITTERS.put("SHOW_MONSTER_HP", () -> PacketCreator.showMonsterHP(11, 50));
        EMITTERS.put("FACIAL_EXPRESSION", () -> {
            Character chr = mock(Character.class);
            when(chr.getId()).thenReturn(7);
            return PacketCreator.facialExpression(chr, 3);
        });
        EMITTERS.put("SHOW_CHAIR", () -> PacketCreator.showChair(7, 3010000));
        EMITTERS.put("MOVE_MONSTER_RESPONSE",
                () -> PacketCreator.moveMonsterResponse(11, (short) 1, 100, false, 0, 0));
        EMITTERS.put("SCRIPT_PROGRESS_MESSAGE", () -> PacketCreator.earnTitleMessage("hello"));
        EMITTERS.put("LAST_CONNECTED_WORLD", () -> PacketCreator.selectWorld(0));
        EMITTERS.put("DELETE_CHAR_RESPONSE", () -> PacketCreator.deleteCharResponse(7, 0));
        EMITTERS.put("CHAR_NAME_RESPONSE", () -> PacketCreator.charNameResponse("uguuh", false));
        EMITTERS.put("INCUBATOR_RESULT", PacketCreator::incubatorResult);
        EMITTERS.put("SPAWN_KITE", () -> PacketCreator.spawnKite(1, 2, "owner", "msg", new Point(3, 4), 5));
        EMITTERS.put("REMOVE_KITE", () -> PacketCreator.removeKite(1, 0));
        EMITTERS.put("REMOVE_MIST", () -> PacketCreator.removeMist(1));
        EMITTERS.put("SHOW_COMBO", () -> PacketCreator.showCombo(5));
        EMITTERS.put("DESTROY_HIRED_MERCHANT", () -> PacketCreator.removeHiredMerchantBox(1));
        EMITTERS.put("MONSTER_BOOK_SET_COVER", () -> PacketCreator.changeCover(2380000));
    }

    // ---- fixtures -------------------------------------------------------------------------

    private static MapItem itemDrop() {
        MapItem drop = baseDrop();
        Item item = mock(Item.class);
        when(item.getExpiration()).thenReturn(-1L);
        when(drop.getItem()).thenReturn(item);
        when(drop.getMeso()).thenReturn(0);
        return drop;
    }

    private static MapItem mesoDrop() {
        MapItem drop = baseDrop();
        when(drop.getMeso()).thenReturn(500);
        return drop;
    }

    private static MapItem baseDrop() {
        MapItem drop = mock(MapItem.class);
        MapObject dropper = mock(MapObject.class);
        when(dropper.getObjectId()).thenReturn(99);
        when(drop.getObjectId()).thenReturn(1000);
        when(drop.getItemId()).thenReturn(2000000);
        when(drop.getClientsideOwnerId()).thenReturn(7);
        when(drop.getDropType()).thenReturn((byte) 2);
        when(drop.getDropper()).thenReturn(dropper);
        when(drop.getPosition()).thenReturn(new Point(3, 4));
        when(drop.isPlayerDrop()).thenReturn(false);
        return drop;
    }

    private static Reactor reactor() {
        Reactor r = mock(Reactor.class);
        when(r.getObjectId()).thenReturn(500);
        when(r.getId()).thenReturn(2000);
        when(r.getState()).thenReturn((byte) 0);
        when(r.getPosition()).thenReturn(new Point(10, 20));
        return r;
    }

    private static NPC npc() {
        NPC n = mock(NPC.class);
        when(n.getObjectId()).thenReturn(300);
        when(n.getId()).thenReturn(9000);
        when(n.getPosition()).thenReturn(new Point(10, 20));
        when(n.getCy()).thenReturn(20);
        when(n.getF()).thenReturn(0);
        when(n.getFh()).thenReturn(1);
        when(n.getRx0()).thenReturn(-10);
        when(n.getRx1()).thenReturn(10);
        return n;
    }

    // ---- tests ----------------------------------------------------------------------------

    @Test
    void everyVerifiedModelHasAnEmitterAndViceVersa() {
        assertEquals(models.keySet(), EMITTERS.keySet(),
                "a verified model with no emitter is never checked; an emitter with no model is dead code");
    }

    @Test
    void everyModelledPacketIsConsumedExactly() {
        List<String> failures = new ArrayList<>();
        for (Map.Entry<String, Supplier<Packet>> e : EMITTERS.entrySet()) {
            Result r = PacketStructureValidator.validate(models.get(e.getKey()), e.getValue().get());
            if (!r.ok() && !e.getKey().equals("KILL_MONSTER/normal")) {
                failures.add(r.toString());
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    /**
     * Pinned finding, not a passing case. PacketCreator.killMonster writes the animation byte twice
     * (an OdinMS inheritance); CMobPool::OnMobLeaveField reads Decode4 + Decode1 and stops - the
     * same in the v79/v83/v84/v87/v92/v95 exports. One surplus trailing byte on every mob death.
     * Trailing over-send is harmless to the client, so this is documented rather than fixed here;
     * if someone does fix it, this test says so instead of the fix passing silently.
     */
    @Test
    void killMonsterOverSendsOneByte() {
        Result r = PacketStructureValidator.validate(models.get("KILL_MONSTER/normal"),
                PacketCreator.killMonster(1234, true));
        assertEquals(Status.OVER_SEND, r.status());
        assertEquals(1, r.length() - r.consumed());
    }

    /**
     * Scope boundary, pinned so it is not mistaken for coverage. `SPAWN_NPC_REQUEST_CONTROLLER` has
     * TWO legitimate shapes, selected by the leading localFlag byte: 21 bytes to assign a
     * controller (spawnNPCRequestController / spawnPlayerNPC) and 5 bytes to drop one
     * (removeNPCController, localFlag = 0, after which the client stops reading). The gms_v83
     * export records no guard for this, so the derived model is the localFlag = 1 shape only and
     * the short form is NOT covered. Validating it against this model reports a false UNDER_SEND -
     * that is the model's limit, not a bug in removeNPCController.
     */
    @Test
    void removeNpcControllerIsASecondShapeThisModelDoesNotCover() {
        Result r = PacketStructureValidator.validate(models.get("SPAWN_NPC_REQUEST_CONTROLLER"),
                PacketCreator.removeNPCController(300));
        assertEquals(Status.UNDER_SEND, r.status());
        assertEquals(5, r.length() - 2, "the localFlag = 0 form is a 5-byte body");
    }

    // ---- mutation checks: a checker that cannot fail is not a checker ----------------------

    @Test
    void shavingOneByteIsCaughtAsUnderSend() {
        for (String name : List.of("DROP_ITEM_FROM_MAPOBJECT/spawn-item", "SKILL_LEARN_ITEM_RESULT/result",
                "SPAWN_NPC_REQUEST_CONTROLLER")) {
            DecodeModel model = models.get(name);
            byte[] good = EMITTERS.get(name).get().getBytes();

            Result before = PacketStructureValidator.validate(model, good);
            assertTrue(before.ok(), name + " must be clean before mutating: " + before);

            byte[] shaved = java.util.Arrays.copyOf(good, good.length - 1);
            Result after = PacketStructureValidator.validate(model, shaved);
            assertEquals(Status.UNDER_SEND, after.status(), name + " one byte short must be caught: " + after);
            assertTrue(after.detail().contains("error 38"), "the message must name the client's error code");

            Result restored = PacketStructureValidator.validate(model, good);
            assertTrue(restored.ok(), name + " must be clean again after restoring: " + restored);
        }
    }

    @Test
    void appendingOneByteIsCaughtAsOverSend() {
        for (String name : List.of("DROP_ITEM_FROM_MAPOBJECT/spawn-item", "SKILL_LEARN_ITEM_RESULT/result",
                "SPAWN_NPC_REQUEST_CONTROLLER")) {
            byte[] good = EMITTERS.get(name).get().getBytes();
            byte[] padded = java.util.Arrays.copyOf(good, good.length + 1);

            Result after = PacketStructureValidator.validate(models.get(name), padded);
            assertEquals(Status.OVER_SEND, after.status(), name + " one byte long must be caught: " + after);
            assertTrue(PacketStructureValidator.validate(models.get(name), good).ok());
        }
    }

    /**
     * The regression that started this: DROP_ITEM_FROM_MAPOBJECT must be 39 body bytes at v84, not
     * the 38 that killed the client on every monster drop. Removing writeV84DropSpawnExtra must
     * make this test fail, which is exactly what the shave-a-byte check above proves.
     */
    @Test
    void dropSpawnIsThirtyNineBodyBytesAtV84() {
        byte[] bytes = EMITTERS.get("DROP_ITEM_FROM_MAPOBJECT/spawn-item").get().getBytes();
        assertEquals(39, bytes.length - 2, "v84 CDropPool::OnDropEnterField wants 39; v83 wanted 38");
        assertFalse(PacketStructureValidator.validate(
                models.get("DROP_ITEM_FROM_MAPOBJECT/spawn-item"),
                java.util.Arrays.copyOf(bytes, bytes.length - 1)).ok());
    }
}
