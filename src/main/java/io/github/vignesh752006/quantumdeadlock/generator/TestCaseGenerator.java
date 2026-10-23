package io.github.vignesh752006.quantumdeadlock.generator;

import io.github.vignesh752006.quantumdeadlock.model.Process;
import io.github.vignesh752006.quantumdeadlock.model.Resource;
import io.github.vignesh752006.quantumdeadlock.model.SystemState;

import java.util.Random;

/**
 * Builds reproducible {@link SystemState} instances from an explicit seed.
 *
 * <p>Every method takes a seed and a size pair, so a failing benchmark case can be
 * replayed exactly. Three construction modes are offered:</p>
 * <ul>
 *   <li>{@link #generateSafe} - safe by construction; the Banker's Algorithm will
 *       always find a complete sequence</li>
 *   <li>{@link #generateUnsafe} - contains a deliberate circular-wait ring; the
 *       Banker's Algorithm will always report unsafe</li>
 *   <li>{@link #generateRandom} - unconstrained; the verdict depends on the draw</li>
 * </ul>
 */
public final class TestCaseGenerator {

    /** The six standard benchmark shapes as {@code {processes, resourceTypes}}. */
    public static final int[][] BENCHMARK_SIZES = {
            {10, 5},
            {20, 5},
            {50, 10},
            {100, 15},
            {200, 20},
            {500, 30}
    };

    private static final int MIN_TOTAL_PER_RESOURCE = 3;
    private static final int SCALE_FACTOR = 3;
    private static final int MAX_RING_SIZE = 10;

    private TestCaseGenerator() {
        throw new AssertionError("No instances.");
    }

    /**
     * Builds a state that is safe by construction.
     *
     * <p>The generator walks a randomly chosen safe completion order. For each
     * process it allocates from the remaining capacity budget and then constrains
     * that process's need to the resources already freed by its predecessors, so the
     * walk itself is always feasible. Whatever budget is left becomes available.</p>
     *
     * @param processCount  processes, at least 2
     * @param resourceCount resource types, at least 1
     * @param seed          RNG seed for reproducibility
     * @return a safe state
     */
    public static SystemState generateSafe(int processCount, int resourceCount, long seed) {
        validate(processCount, resourceCount);
        Random random = new Random(seed);

        int[] total = randomTotals(processCount, resourceCount, random);

        int[] available = new int[resourceCount];
        int[] remainingBudget = new int[resourceCount];
        for (int j = 0; j < resourceCount; j++) {
            int maxAvailable = Math.max(1, total[j] / (processCount + 1));
            available[j] = random.nextInt(maxAvailable + 1);
            remainingBudget[j] = total[j] - available[j];
        }

        int[] safeOrder = randomPermutation(processCount, random);
        int[][] allocation = new int[processCount][resourceCount];
        int[][] maximum = new int[processCount][resourceCount];
        int[] work = available.clone();

        for (int step = 0; step < processCount; step++) {
            int processIndex = safeOrder[step];
            int processesLeft = processCount - step;

            for (int j = 0; j < resourceCount; j++) {
                int maxAllocation = (processesLeft == 1)
                        ? remainingBudget[j]
                        : remainingBudget[j] / processesLeft;

                allocation[processIndex][j] = (maxAllocation > 0)
                        ? random.nextInt(maxAllocation + 1)
                        : 0;
                remainingBudget[j] -= allocation[processIndex][j];

                int maxNeed = work[j];
                int need = (maxNeed > 0) ? random.nextInt(maxNeed + 1) : 0;
                maximum[processIndex][j] = allocation[processIndex][j] + need;
            }

            for (int j = 0; j < resourceCount; j++) {
                work[j] += allocation[processIndex][j];
            }
        }

        for (int j = 0; j < resourceCount; j++) {
            available[j] += remainingBudget[j];
        }

        return buildState(processCount, resourceCount, allocation, maximum, available, total,
                String.format("SAFE-n%d-m%d-seed%d", processCount, resourceCount, seed));
    }

    /**
     * Builds a state containing a deliberate circular-wait ring, so it is unsafe.
     *
     * <p>Ring process {@code i} holds every instance of resource {@code i} and waits
     * for one instance of resource {@code (i + 1) mod k}, with nothing free in the
     * ring, so no member can ever proceed. Non-ring processes and resources are
     * filled randomly to keep the state realistic.</p>
     *
     * @param processCount  processes, at least 2
     * @param resourceCount resource types, at least 2
     * @param seed          RNG seed for reproducibility
     * @return an unsafe state
     */
    public static SystemState generateUnsafe(int processCount, int resourceCount, long seed) {
        validate(processCount, resourceCount);
        if (resourceCount < 2) {
            throw new IllegalArgumentException(
                    "generateUnsafe requires at least 2 resource types to form a ring.");
        }

        Random random = new Random(seed);
        int ringSize = Math.min(Math.min(processCount, resourceCount), MAX_RING_SIZE);

        int[] total = randomTotals(processCount, resourceCount, random);
        int[][] allocation = new int[processCount][resourceCount];
        int[][] maximum = new int[processCount][resourceCount];
        int[] available = new int[resourceCount];

        for (int i = 0; i < ringSize; i++) {
            allocation[i][i] = total[i];
            maximum[i][i] = total[i];
            int nextResource = (i + 1) % ringSize;
            maximum[i][nextResource] = Math.max(maximum[i][nextResource], 1);
        }
        for (int j = 0; j < ringSize; j++) {
            available[j] = 0;
        }

        for (int j = ringSize; j < resourceCount; j++) {
            int remaining = total[j];
            for (int i = ringSize; i < processCount && remaining > 0; i++) {
                int maxAllocation = remaining / Math.max(1, processCount - i);
                int allocated = (maxAllocation > 0) ? random.nextInt(maxAllocation + 1) : 0;
                allocation[i][j] = allocated;
                remaining -= allocated;

                int maxNeed = Math.max(0, total[j] / Math.max(1, processCount - ringSize));
                maximum[i][j] = allocated + ((maxNeed > 0) ? random.nextInt(maxNeed + 1) : 0);
            }
            available[j] = remaining;
        }

        return buildState(processCount, resourceCount, allocation, maximum, available, total,
                String.format("UNSAFE-n%d-m%d-seed%d-ring%d",
                        processCount, resourceCount, seed, ringSize));
    }

    /**
     * Builds an unconstrained random state; the verdict is whatever the algorithms
     * decide.
     *
     * @param processCount  processes, at least 2
     * @param resourceCount resource types, at least 1
     * @param seed          RNG seed for reproducibility
     * @return a random state
     */
    public static SystemState generateRandom(int processCount, int resourceCount, long seed) {
        validate(processCount, resourceCount);
        Random random = new Random(seed);

        int[] total = randomTotals(processCount, resourceCount, random);
        int[][] allocation = new int[processCount][resourceCount];
        int[][] maximum = new int[processCount][resourceCount];
        int[] available = new int[resourceCount];
        int[] remaining = total.clone();

        for (int i = 0; i < processCount; i++) {
            for (int j = 0; j < resourceCount; j++) {
                int maxAllocation = remaining[j] / Math.max(1, processCount - i);
                int allocated = (maxAllocation > 0) ? random.nextInt(maxAllocation + 1) : 0;
                allocation[i][j] = allocated;
                remaining[j] -= allocated;

                int extra = random.nextInt(Math.max(1, total[j] / 2) + 1);
                maximum[i][j] = allocated + extra;
            }
        }
        System.arraycopy(remaining, 0, available, 0, resourceCount);

        return buildState(processCount, resourceCount, allocation, maximum, available, total,
                String.format("RANDOM-n%d-m%d-seed%d", processCount, resourceCount, seed));
    }

    /**
     * Generates one safe state per standard benchmark size.
     *
     * @param baseSeed base seed; size {@code k} uses {@code baseSeed + k}
     * @return one state per entry of {@link #BENCHMARK_SIZES}
     */
    public static SystemState[] generateAllSizesSafe(long baseSeed) {
        SystemState[] states = new SystemState[BENCHMARK_SIZES.length];
        for (int index = 0; index < BENCHMARK_SIZES.length; index++) {
            states[index] = generateSafe(BENCHMARK_SIZES[index][0], BENCHMARK_SIZES[index][1],
                    baseSeed + index);
        }
        return states;
    }

    /**
     * Generates one unsafe state per standard benchmark size.
     *
     * @param baseSeed base seed; size {@code k} uses {@code baseSeed + k}
     * @return one state per entry of {@link #BENCHMARK_SIZES}
     */
    public static SystemState[] generateAllSizesUnsafe(long baseSeed) {
        SystemState[] states = new SystemState[BENCHMARK_SIZES.length];
        for (int index = 0; index < BENCHMARK_SIZES.length; index++) {
            states[index] = generateUnsafe(BENCHMARK_SIZES[index][0], BENCHMARK_SIZES[index][1],
                    baseSeed + index);
        }
        return states;
    }

    private static void validate(int processCount, int resourceCount) {
        if (processCount < 2) {
            throw new IllegalArgumentException(
                    "processCount must be at least 2, got " + processCount + ".");
        }
        if (resourceCount < 1) {
            throw new IllegalArgumentException(
                    "resourceCount must be at least 1, got " + resourceCount + ".");
        }
    }

    private static int[] randomTotals(int processCount, int resourceCount, Random random) {
        int[] total = new int[resourceCount];
        int upperBound = Math.max(MIN_TOTAL_PER_RESOURCE + 1, processCount * SCALE_FACTOR);
        for (int j = 0; j < resourceCount; j++) {
            total[j] = MIN_TOTAL_PER_RESOURCE
                    + random.nextInt(upperBound - MIN_TOTAL_PER_RESOURCE + 1);
        }
        return total;
    }

    private static int[] randomPermutation(int size, Random random) {
        int[] permutation = new int[size];
        for (int i = 0; i < size; i++) {
            permutation[i] = i;
        }
        for (int i = size - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int swap = permutation[i];
            permutation[i] = permutation[j];
            permutation[j] = swap;
        }
        return permutation;
    }

    private static SystemState buildState(int processCount, int resourceCount,
                                          int[][] allocation, int[][] maximum,
                                          int[] available, int[] total, String label) {
        Process[] processes = new Process[processCount];
        for (int i = 0; i < processCount; i++) {
            processes[i] = new Process(i, "P" + i, allocation[i], maximum[i]);
        }

        Resource[] resources = new Resource[resourceCount];
        for (int j = 0; j < resourceCount; j++) {
            resources[j] = new Resource(j, "R" + j, total[j], available[j]);
        }

        return new SystemState(processes, resources, allocation, maximum, available, label);
    }
}
