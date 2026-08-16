package net.opcodes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OpcodeTableTest {

    @Test
    void defaultTableCoversEveryOpcodeAndNothingElse() {
        // initialising both enums throws if a constant has no entry (or a bad one);
        // verify() then throws if an entry matches no constant
        assertDoesNotThrow(OpcodeTable::verify);
    }

    @Test
    void loadsV83Values() {
        assertEquals(0x00, SendOpcode.LOGIN_STATUS.getValue());
        assertEquals(0x166, SendOpcode.VEGA_SCROLL.getValue());
        assertEquals(0x3713, RecvOpcode.CUSTOM_PACKET.getValue());
        assertEquals(0x104, RecvOpcode.USE_HAMMER.getValue());
    }
}
