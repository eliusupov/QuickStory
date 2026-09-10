package net.server.channel.handlers;

import constants.inventory.ItemConstants;
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnniversaryGloveScrollTest {

    @Test
    void v84AnniversaryGloveScrollsMatchOnlyGloves() {
        IntStream.rangeClosed(2049105, 2049110).forEach(scrollId -> {
            assertTrue(ScrollHandler.canScroll(scrollId, 1082000), Integer.toString(scrollId));
            assertFalse(ScrollHandler.canScroll(scrollId, 1002000), Integer.toString(scrollId));
        });
    }

    @Test
    void adjacentSpecialScrollsAndOrdinaryGloveMatchingStayUnchanged() {
        IntStream.rangeClosed(2049100, 2049103)
                .forEach(scrollId -> assertTrue(ItemConstants.isChaosScroll(scrollId), Integer.toString(scrollId)));
        assertFalse(ItemConstants.isChaosScroll(2049104));
        assertFalse(ItemConstants.isChaosScroll(2049105));
        assertFalse(ItemConstants.isV84AnniversaryGloveScroll(2049104));
        assertFalse(ItemConstants.isV84AnniversaryGloveScroll(2049111));
        assertFalse(ScrollHandler.canScroll(2049104, 1082000));
        assertFalse(ScrollHandler.canScroll(2049111, 1082000));
        assertTrue(ScrollHandler.canScroll(2040801, 1082000));
        assertFalse(ScrollHandler.canScroll(2040801, 1002000));
    }
}
