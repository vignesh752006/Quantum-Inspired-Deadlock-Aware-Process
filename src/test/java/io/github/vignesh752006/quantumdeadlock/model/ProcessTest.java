package io.github.vignesh752006.quantumdeadlock.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers Step 1 of the original harness: process construction, derived need and
 * the allocation feasibility probe.
 */
class ProcessTest {

    @Test
    @DisplayName("need is derived as maximum minus allocation")
    void needIsDerived() {
        Process process = new Process(0, "P0", new int[]{2, 1, 0}, new int[]{5, 3, 4});

        assertArrayEquals(new int[]{3, 2, 4}, process.need());
        assertEquals(3, process.need(0));
        assertEquals(2, process.need(1));
        assertEquals(4, process.need(2));
    }

    @Test
    @DisplayName("accessors return copies, not internal arrays")
    void accessorsReturnDefensiveCopies() {
        Process process = new Process(0, "P0", new int[]{1, 2}, new int[]{4, 5});

        int[] allocation = process.allocation();
        allocation[0] = 999;
        int[] need = process.need();
        need[1] = 999;

        assertEquals(1, process.allocation(0), "internal allocation must be unchanged");
        assertEquals(3, process.need(1), "internal need must be unchanged");
    }

    @Test
    @DisplayName("canAllocate is true only when the whole need fits")
    void canAllocateRequiresFullNeed() {
        Process process = new Process(0, "P0", new int[]{1, 1}, new int[]{4, 3});
        // need = [3, 2]

        assertTrue(process.canAllocate(new int[]{3, 2}), "exact fit must be allowed");
        assertTrue(process.canAllocate(new int[]{10, 10}));
        assertFalse(process.canAllocate(new int[]{2, 10}), "short on resource 0");
        assertFalse(process.canAllocate(new int[]{10, 1}), "short on resource 1");
    }

    @Test
    @DisplayName("blank name falls back to P<id>")
    void blankNameFallsBackToId() {
        assertEquals("P7", new Process(7, "   ", new int[]{0}, new int[]{1}).name());
        assertEquals("P7", new Process(7, null, new int[]{0}, new int[]{1}).name());
    }

    @Test
    @DisplayName("allocation exceeding maximum is rejected")
    void allocationAboveMaximumRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new Process(0, "P0", new int[]{5}, new int[]{4}));
        assertTrue(error.getMessage().contains("exceeds maximum"), error.getMessage());
    }

    @Test
    @DisplayName("mismatched vector lengths are rejected")
    void mismatchedLengthsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new Process(0, "P0", new int[]{1, 2}, new int[]{1}));
    }

    @Test
    @DisplayName("negative values are rejected")
    void negativeValuesRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new Process(0, "P0", new int[]{-1}, new int[]{5}));
        assertThrows(IllegalArgumentException.class,
                () -> new Process(-1, "P0", new int[]{1}, new int[]{2}));
    }

    @Test
    @DisplayName("deepCopy is independent of the original")
    void deepCopyIsIndependent() {
        Process original = new Process(3, "P3", new int[]{1, 2}, new int[]{4, 6});
        Process copy = original.deepCopy();

        assertEquals(original.id(), copy.id());
        assertArrayEquals(original.need(), copy.need());
        assertEquals(original.toString(), copy.toString());
    }
}
