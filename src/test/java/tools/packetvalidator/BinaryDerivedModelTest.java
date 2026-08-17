package tools.packetvalidator;

import client.Character;
import net.packet.Packet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.PacketCreator;
import tools.packetvalidator.PacketStructureValidator.Result;
import tools.packetvalidator.PacketStructureValidator.Status;

import java.util.ArrayList;
import java.util.Arrays;
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
 * The same replay check as {@link PacketStructureValidatorTest}, but against models derived from the
 * v84 CLIENT BINARY rather than from the atlas export - see {@code tools/v84/derive-binary-models.py}.
 *
 * <p>Why a second table rather than more rows in the first: the atlas-derived models encode deltas
 * somebody already proved, so they can only ever agree with {@code PacketCreator}. These are walked
 * out of the client itself with no reference to server code, so a disagreement is a finding. The
 * two tables overlapping is therefore worth an assertion of its own -
 * {@link #theTwoIndependentDerivationsAgreeWhereTheyOverlap()}.
 *
 * <p>Only {@code verified} rows load. A row is promoted only after a human has read BOTH the
 * handler's disassembly and the emitting PacketCreator method; the addresses are in the script.
 * The reason that bar is high is in the script too: the dispatch resolver can stop on an early-out
 * arm and report the pool dispatcher's prefix as if it were the whole packet.
 */
class BinaryDerivedModelTest {

    private static Map<String, DecodeModel> models;

    /** modelName -> a real PacketCreator call. Every verified model needs exactly one. */
    private static final Map<String, Supplier<Packet>> EMITTERS = new LinkedHashMap<>();

    @BeforeAll
    static void load() {
        models = PacketStructureModels.loadVerified(PacketStructureModels.BINARY_PATH,
                "v84 client binary, ");

        EMITTERS.put("AUTO_HP_POT", () -> PacketCreator.sendAutoHpPot(2000000));
        EMITTERS.put("AUTO_MP_POT", () -> PacketCreator.sendAutoMpPot(2000003));
        EMITTERS.put("CLAIM_STATUS_CHANGED", PacketCreator::enableReport);
        EMITTERS.put("SET_EXTRA_PENDANT_SLOT", () -> PacketCreator.setExtraPendantSlot(true));
        EMITTERS.put("REMOVE_NPC", () -> PacketCreator.removeNPC(300));
        EMITTERS.put("SET_GENDER", () -> {
            Character chr = mock(Character.class);
            when(chr.getGender()).thenReturn(0);
            return PacketCreator.updateGender(chr);
        });
    }

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
            if (!r.ok()) {
                failures.add(r.toString());
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    /**
     * The corroboration that makes the binary table worth having. Where an opcode is modelled by
     * BOTH the atlas-derived table and the independent binary walk, the two must describe the same
     * number of bytes. They were built from different sources by different methods; if they ever
     * disagree, one of them is wrong and this says so instead of both quietly passing.
     */
    @Test
    void theTwoIndependentDerivationsAgreeWhereTheyOverlap() {
        Map<String, DecodeModel> atlas = PacketStructureModels.loadVerified();
        // candidate rows included on purpose: the corroboration is between two DERIVATIONS, and
        // whether a human has signed off on a row has no bearing on whether the client agrees.
        Map<String, DecodeModel> binary = PacketStructureModels.loadAll(
                PacketStructureModels.BINARY_PATH, "v84 client binary, ");
        List<String> both = new ArrayList<>();
        List<String> disagreements = new ArrayList<>();
        for (Map.Entry<String, DecodeModel> e : binary.entrySet()) {
            DecodeModel other = atlas.get(e.getKey());
            if (other == null) {
                continue;
            }
            both.add(e.getKey());
            int a = fixedWidth(other);
            int b = fixedWidth(e.getValue());
            if (a != b) {
                disagreements.add(e.getKey() + ": atlas-derived says " + a
                        + " bytes, v84-binary-derived says " + b
                        + " - one of the two derivations is wrong, adjudicate at the binary");
            }
        }
        assertTrue(disagreements.isEmpty(), String.join("\n", disagreements));
        assertFalse(both.isEmpty(), "the two tables no longer overlap, so nothing is cross-checked");
    }

    /** -1 for a model with a variable-length field; those are compared by shape, not by width. */
    private static int fixedWidth(DecodeModel m) {
        int n = 0;
        for (DecodeModel.Field f : m.fields()) {
            switch (f.kind()) {
                case STR -> {
                    return -1;
                }
                case BUF -> n += f.count();
                default -> n += f.kind().width;
            }
        }
        return n;
    }

    // ---- mutation checks: a checker that cannot fail is not a checker ----------------------

    @Test
    void shavingOneByteIsCaughtAsUnderSend() {
        for (String name : EMITTERS.keySet()) {
            DecodeModel model = models.get(name);
            byte[] good = EMITTERS.get(name).get().getBytes();

            assertTrue(PacketStructureValidator.validate(model, good).ok(),
                    name + " must be clean before mutating");

            Result after = PacketStructureValidator.validate(model,
                    Arrays.copyOf(good, good.length - 1));
            assertEquals(Status.UNDER_SEND, after.status(),
                    name + " one byte short must be caught: " + after);
            assertTrue(after.detail().contains("error 38"),
                    "the message must name the client's error code");

            assertTrue(PacketStructureValidator.validate(model, good).ok(),
                    name + " must be clean again after restoring");
        }
    }

    @Test
    void appendingOneByteIsCaughtAsOverSend() {
        for (String name : EMITTERS.keySet()) {
            byte[] good = EMITTERS.get(name).get().getBytes();
            Result after = PacketStructureValidator.validate(models.get(name),
                    Arrays.copyOf(good, good.length + 1));
            assertEquals(Status.OVER_SEND, after.status(),
                    name + " one byte long must be caught: " + after);
            assertTrue(PacketStructureValidator.validate(models.get(name), good).ok());
        }
    }

    /**
     * Provenance, pinned. These models say they came from the client image; if someone regenerates
     * the table from the atlas export instead, the cross-check above stops being independent and
     * silently becomes a tautology.
     */
    @Test
    void modelsSayTheyCameFromTheBinary() {
        for (DecodeModel m : models.values()) {
            assertTrue(m.source().startsWith("v84 client binary, "),
                    m.opcode() + " has the wrong provenance: " + m.source());
        }
    }
}
