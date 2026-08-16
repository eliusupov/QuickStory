package tools.packetvalidator;

import java.util.ArrayList;
import java.util.List;

/**
 * What the client DECODES for one server-&gt;client opcode: an ordered list of
 * {@code CInPacket::Decode*} calls, one per field.
 *
 * <p>A model is only useful if it is <em>exact</em>. Nothing here guesses: models are hand-vetted
 * against the client binary / the IDA exports and each one records where it came from in
 * {@link #source()}. An opcode with a conditional or data-driven body has no model at all rather
 * than a wrong one - see {@link PacketStructureModels} for the covered / not-covered lists.
 */
public record DecodeModel(String opcode, String source, List<Field> fields) {

    /** One {@code CInPacket::Decode*} call. */
    public record Field(String name, Kind kind, int count) {
    }

    public enum Kind {
        /** Decode1 */
        U8(1),
        /** Decode2 */
        U16(2),
        /** Decode4 */
        U32(4),
        /** Decode8 */
        U64(8),
        /** two Decode2 (x, y) */
        POS(4),
        /** DecodeStr: a Decode2 length followed by that many bytes */
        STR(-1),
        /** DecodeBuf(n): n raw bytes, n known statically */
        BUF(-1);

        final int width;

        Kind(int width) {
            this.width = width;
        }
    }

    public static Builder of(String opcode, String source) {
        return new Builder(opcode, source);
    }

    public static final class Builder {
        private final String opcode;
        private final String source;
        private final List<Field> fields = new ArrayList<>();

        private Builder(String opcode, String source) {
            this.opcode = opcode;
            this.source = source;
        }

        public Builder u8(String name) {
            return add(name, Kind.U8, 1);
        }

        public Builder u16(String name) {
            return add(name, Kind.U16, 1);
        }

        public Builder u32(String name) {
            return add(name, Kind.U32, 1);
        }

        public Builder u64(String name) {
            return add(name, Kind.U64, 1);
        }

        public Builder pos(String name) {
            return add(name, Kind.POS, 1);
        }

        public Builder str(String name) {
            return add(name, Kind.STR, 1);
        }

        public Builder buf(String name, int bytes) {
            return add(name, Kind.BUF, bytes);
        }

        private Builder add(String name, Kind kind, int count) {
            fields.add(new Field(name, kind, count));
            return this;
        }

        public DecodeModel build() {
            return new DecodeModel(opcode, source, List.copyOf(fields));
        }
    }
}
