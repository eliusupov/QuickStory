package tools.packetvalidator;

import net.packet.Packet;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Replays a {@link DecodeModel} over real bytes emitted by {@code PacketCreator}, the way the client
 * would, and reports the two failures that kill the client or desync it:
 *
 * <ul>
 *   <li><b>UNDER_SEND</b> - the model wanted more bytes than we sent. This is the exact condition
 *       behind {@code ZException (error code : 38 (Reached the end of the file.))}: the client's
 *       {@code CInPacket::Decode*} runs off the end and throws. It is what killed the owner's client
 *       on every monster drop (DROP_ITEM_FROM_MAPOBJECT was 38 bytes, v84 wanted 39).</li>
 *   <li><b>OVER_SEND</b> - we sent bytes the client never reads. Usually harmless on its own but it
 *       means the structures have diverged, and it is how a missing trailing field hides.</li>
 * </ul>
 */
public final class PacketStructureValidator {

    public enum Status {OK, UNDER_SEND, OVER_SEND}

    public record Result(Status status, String opcode, int consumed, int length, String detail) {
        public boolean ok() {
            return status == Status.OK;
        }

        @Override
        public String toString() {
            String where = status == Status.UNDER_SEND
                    ? "model ran out at offset " + consumed + " of " + length
                    : "model consumed " + consumed + " of " + length + " bytes";
            return opcode + ": " + status + " (" + where + ") " + detail;
        }
    }

    private PacketStructureValidator() {
    }

    public static Result validate(DecodeModel model, Packet packet) {
        return validate(model, packet.getBytes());
    }

    /** {@code bytes} is the full wire packet including its 2-byte opcode header. */
    public static Result validate(DecodeModel model, byte[] bytes) {
        if (bytes.length < 2) {
            return new Result(Status.UNDER_SEND, model.opcode(), 0, bytes.length,
                    "PACKET TOO SHORT: " + bytes.length + " bytes cannot even hold the 2-byte opcode.");
        }
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int pos = 2; // the client consumes the opcode before dispatching to the handler

        for (DecodeModel.Field f : model.fields()) {
            int need;
            switch (f.kind()) {
                case STR -> {
                    if (pos + 2 > bytes.length) {
                        return under(model, pos, bytes.length, f, 2);
                    }
                    need = 2 + (buf.getShort(pos) & 0xFFFF);
                }
                case BUF -> need = f.count();
                default -> need = f.kind().width;
            }
            // need is model data, not wire data, but a bad model must not silently wrap pos negative
            if (need < 0 || pos > bytes.length - need) {
                return under(model, pos, bytes.length, f, need);
            }
            pos += need;
        }

        if (pos < bytes.length) {
            return new Result(Status.OVER_SEND, model.opcode(), pos, bytes.length,
                    "we sent " + (bytes.length - pos) + " byte(s) the v84 client never reads; "
                            + "model source: " + model.source());
        }
        return new Result(Status.OK, model.opcode(), pos, bytes.length, "");
    }

    private static Result under(DecodeModel model, int pos, int length, DecodeModel.Field f, int need) {
        return new Result(Status.UNDER_SEND, model.opcode(), pos, length,
                "PACKET TOO SHORT: client field '" + f.name() + "' (" + f.kind() + ") wants " + need
                        + " byte(s) at offset " + pos + " but the packet is only " + length + " bytes."
                        + " The client throws ZException error 38 here. Model source: " + model.source());
    }
}
