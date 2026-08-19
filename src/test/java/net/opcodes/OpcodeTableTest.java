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
    void loadsV84Values() {
        assertEquals(0x00, SendOpcode.LOGIN_STATUS.getValue());
        assertEquals(0x170, SendOpcode.VEGA_SCROLL.getValue());
        assertEquals(0x3713, RecvOpcode.CUSTOM_PACKET.getValue());
        assertEquals(0x10B, RecvOpcode.USE_HAMMER.getValue());
    }
}
