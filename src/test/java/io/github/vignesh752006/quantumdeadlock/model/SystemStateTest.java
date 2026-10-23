package io.github.vignesh752006.quantumdeadlock.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers Step 2 of the original harness: state validation, the two invariants,
 * and immutability guarantees.
 */
class SystemStateTest {

    private static Process[] processes(int[][] allocation, int[][] maximum) {
        Process[] result = new Process[allocation.length];
        for (int i = 0; i < allocation.length; i++) {
            result[i] = new Process(i, "P" + i, allocation[i], maximum[i]);
        }
        return result;
    }

    private static Resource[] resources(int[] total, int[] available) {
        Resource[] result = new Resource[total.length];
        for (int j = 0; j < total.length; j++) {
            result[j] = new Resource(j, "R" + j, total[j], available[j]);
        }
        return result;
    }

    private static SystemState sampleState() {
        int[][] allocation = {{1, 0}, {0, 1}};
        int[][] maximum = {{2, 1}, {1, 2}};
        int[] total = {2, 2};
        int[] available = {1, 1};
        return new SystemState(processes(allocation, maximum), resources(total, available),
                allocation, maximum, available, "sample");
    }

    @Test
    @DisplayName("dimensions and derived need are correct")
    void dimensionsAndNeed() {
        SystemState state = sampleState();

        assertEquals(2, state.processCount());
        assertEquals(2, state.resourceCount());
        assertEquals("sample", state.label());
        assertArrayEquals(new int[]{1, 1}, state.needRow(0));
        assertArrayEquals(new int[]{1, 1}, state.needRow(1));
        assertArrayEquals(new int[]{1, 1}, state.availableVector());
    }

    @Test
    @DisplayName("conservation invariant is enforced")
    void conservationEnforced() {
        int[][] allocation = {{1, 0}, {0, 1}};
        int[][] maximum = {{2, 1}, {1, 2}};
        int[] total = {2, 2};
        int[] consistentAvailable = {1, 1};
        // The Resource objects stay self-consistent; only the SystemState sees the
        // mismatched vector, so the failure must come from the conservation check.
        int[] mismatchedAvailable = {5, 5};

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new SystemState(processes(allocation, maximum),
                        resources(total, consistentAvailable),
                        allocation, maximum, mismatchedAvailable, "broken"));
        assertTrue(error.getMessage().contains("conservation"), error.getMessage());
    }

    @Test
    @DisplayName("allocation above maximum is rejected")
    void allocationAboveMaximumRejected() {
        int[][] allocation = {{5, 0}};
        int[][] maximum = {{1, 0}};
        int[] total = {5};
        int[] available = {0};

        assertThrows(IllegalArgumentException.class,
                () -> new SystemState(processes(allocation, maximum), resources(total, available),
                        allocation, maximum, available, "broken"));
    }

    @Test
    @DisplayName("matrix shape must match the declared sizes")
    void shapeMismatchRejected() {
        int[][] allocation = {{1, 0}};
        int[][] maximum = {{2, 1}, {1, 2}};
        int[] total = {2, 2};
        int[] available = {1, 1};

        assertThrows(IllegalArgumentException.class,
                () -> new SystemState(processes(allocation, maximum), resources(total, available),
                        allocation, maximum, available, "broken"));
    }

    @Test
    @DisplayName("accessors return copies, so the state cannot be mutated")
    void accessorsReturnCopies() {
        SystemState state = sampleState();

        int[][] allocation = state.allocationMatrix();
        allocation[0][0] = 999;
        int[] available = state.availableVector();
        available[0] = 999;

        assertEquals(1, state.allocation(0, 0));
        assertEquals(1, state.available(0));
    }

    @Test
    @DisplayName("constructor copies input arrays rather than aliasing them")
    void constructorDoesNotAliasInput() {
        int[][] allocation = {{1, 0}, {0, 1}};
        int[][] maximum = {{2, 1}, {1, 2}};
        int[] total = {2, 2};
        int[] available = {1, 1};

        SystemState state = new SystemState(processes(allocation, maximum),
                resources(total, available), allocation, maximum, available, "sample");

        allocation[0][0] = 99;
        available[0] = 99;

        assertEquals(1, state.allocation(0, 0), "state must not alias the caller's array");
        assertEquals(1, state.available(0));
    }

    @Test
    @DisplayName("deepCopy is a distinct object with equal content")
    void deepCopyWithEqualContent() {
        SystemState state = sampleState();
        SystemState copy = state.deepCopy();

        assertNotSame(state, copy);
        assertEquals(state, copy);
        assertEquals(state.hashCode(), copy.hashCode());
    }

    @Test
    @DisplayName("out-of-range indices fail loudly")
    void outOfRangeIndicesFail() {
        SystemState state = sampleState();
        assertThrows(IndexOutOfBoundsException.class, () -> state.process(5));
        assertThrows(IndexOutOfBoundsException.class, () -> state.resource(-1));
    }

    @Test
    @DisplayName("totalResourceWaste sums the available vector")
    void totalResourceWasteSumsAvailable() {
        assertEquals(2, sampleState().totalResourceWaste());
    }
}
