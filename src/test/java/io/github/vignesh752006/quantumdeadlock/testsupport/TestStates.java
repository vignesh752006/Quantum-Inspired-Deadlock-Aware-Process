package io.github.vignesh752006.quantumdeadlock.testsupport;

import io.github.vignesh752006.quantumdeadlock.model.Process;
import io.github.vignesh752006.quantumdeadlock.model.Resource;
import io.github.vignesh752006.quantumdeadlock.model.SystemState;

/**
 * Shared state builders for the test suite.
 *
 * <p>Hand-built states keep the Banker's Algorithm and cycle-detection tests
 * independent of the generator: if the generator were wrong, a test that built its
 * input with the generator would still pass. The fixtures below are the classic
 * textbook shapes.</p>
 */
public final class TestStates {

    private TestStates() {
        throw new AssertionError("No instances.");
    }

    /**
     * A state that is unambiguously safe: P0 needs nothing, so work grows monotonically
     * until every process completes.
     *
     * <pre>
     *        R0   R1   R2        R0   R1   R2
     *  P0: alloc 1,0,0   max 1,0,0   need 0,0,0   (already complete)
     *  P1: alloc 1,0,0   max 1,1,0   need 0,1,0
     *  P2: alloc 0,0,0   max 1,1,1   need 1,1,1
     *  total = 2,2,2   available = 0,2,2
     * </pre>
     *
     * <p>Safe sequence: {@code <P0, P1, P2>}.</p>
     */
    public static SystemState safeState() {
        int[][] allocation = {
                {1, 0, 0},
                {1, 0, 0},
                {0, 0, 0}
        };
        int[][] maximum = {
                {1, 0, 0},
                {1, 1, 0},
                {1, 1, 1}
        };
        int[] total = {2, 2, 2};
        int[] available = {0, 2, 2};
        return buildState(allocation, maximum, total, available, "safe-textbook");
    }

    /**
     * A state that is unambiguously unsafe: P0 holds R0 and needs R1, P1 holds R1
     * and needs R0, and nothing is free. Pure circular wait.
     *
     * <pre>
     *  P0: alloc 1,0   max 1,1   need 0,1
     *  P1: alloc 0,1   max 1,1   need 1,0
     *  total = 1,1   available = 0,0
     * </pre>
     */
    public static SystemState unsafeRing() {
        int[][] allocation = {
                {1, 0},
                {0, 1}
        };
        int[][] maximum = {
                {1, 1},
                {1, 1}
        };
        int[] total = {1, 1};
        int[] available = {0, 0};
        return buildState(allocation, maximum, total, available, "unsafe-ring");
    }

    /**
     * A one-resource state whose resource-allocation graph has no cycle: P0 holds the
     * only instance and needs nothing, P1 merely waits.
     *
     * <pre>
     *  P0: alloc 1   max 1   need 0
     *  P1: alloc 0   max 1   need 1
     *  total = 1   available = 0
     * </pre>
     */
    public static SystemState acyclicGraphState() {
        int[][] allocation = {{1}, {0}};
        int[][] maximum = {{1}, {1}};
        int[] total = {1};
        int[] available = {0};
        return buildState(allocation, maximum, total, available, "acyclic-graph");
    }

    /**
     * Builds a validated state from matrices.
     *
     * @param allocation allocation matrix
     * @param maximum    maximum-demand matrix
     * @param total      per-resource capacity
     * @param available  per-resource free count
     * @param label      descriptive label
     * @return the validated state
     */
    public static SystemState buildState(int[][] allocation, int[][] maximum,
                                         int[] total, int[] available, String label) {
        Process[] processes = new Process[allocation.length];
        for (int i = 0; i < allocation.length; i++) {
            processes[i] = new Process(i, "P" + i, allocation[i], maximum[i]);
        }

        Resource[] resources = new Resource[total.length];
        for (int j = 0; j < total.length; j++) {
            resources[j] = new Resource(j, "R" + j, total[j], available[j]);
        }

        return new SystemState(processes, resources, allocation, maximum, available, label);
    }
}
