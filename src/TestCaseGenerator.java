import java.util.Arrays;
import java.util.Random;

/**
 * TestCaseGenerator.java
 *
 * Produces reproducible {@link SystemState} objects for benchmarking.
 * Every method accepts an explicit {@code seed} so that any run can be
 * replicated exactly, and the same {@code SystemState} can be passed to
 * all three algorithms (Banker, QEA, Hybrid) for a fair comparison.
 *
 * Three generation modes
 * ──────────────────────
 *  generateSafe()   – guaranteed-safe state; Banker WILL find a safe sequence.
 *  generateUnsafe() – guaranteed-deadlock state via a deliberate circular-wait
 *                     ring; Banker WILL declare UNSAFE.
 *  generateRandom() – unconstrained random state; outcome determined by the
 *                     algorithm under test.
 *
 * Standard size configurations (from the project spec):
 * ┌──────────┬────────────────┐
 * │ processes│ resource types │
 * ├──────────┼────────────────┤
 * │    10    │       5        │
 * │    20    │       5        │
 * │    50    │      10        │
 * │   100    │      15        │ (mid of 10–20 range)
 * │   200    │      20        │
 * │   500    │      30        │ (mid of 20–50 range)
 * └──────────┴────────────────┘
 *
 * Phase-2 note:
 *   In Phase 2 a {@code VMAgentDataReader} will implement the same factory
 *   pattern — returning a {@link SystemState} built from live agent reports —
 *   without any changes to this class.
 *
 * @author  OSDS-CP Group
 * @version 1.0  (Phase 1 – simulated data)
 */
public class TestCaseGenerator {

    // -------------------------------------------------------------------------
    // Standard benchmark size table
    // -------------------------------------------------------------------------

    /** Rows: { numProcesses, numResources } for the six standard benchmark sizes. */
    public static final int[][] BENCHMARK_SIZES = {
        { 10,   5 },
        { 20,   5 },
        { 50,  10 },
        { 100, 15 },
        { 200, 20 },
        { 500, 30 }
    };

    // Tuning constants (kept in one place for easy adjustment)
    private static final int  MIN_TOTAL_PER_RESOURCE = 3;   // floor for total instances
    private static final int  SCALE_FACTOR           = 3;   // total[j] ≈ n × SCALE_FACTOR
    private static final int  MAX_RING_SIZE          = 10;  // deadlock ring cap (multi-resource types)

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /**
     * Generates a GUARANTEED-SAFE {@link SystemState}.
     *
     * Algorithm (forward simulation of a randomly ordered safe sequence):
     *  1. Pick random total capacities for each resource type.
     *  2. Reserve a small available pool upfront.
     *  3. For each process in a random safe order:
     *       a. Randomly allocate from the remaining capacity budget.
     *       b. Constrain need[i][j] ≤ current work[j] so that the sequence
     *          is always feasible up to this point.
     *       c. maximum = allocation + need.
     *       d. Advance work by releasing the process's allocation (simulation).
     *  4. The resulting state is safe by construction; Banker will confirm it.
     *
     * @param n    number of processes (≥ 2)
     * @param m    number of resource types (≥ 1)
     * @param seed RNG seed for full reproducibility
     * @return a guaranteed-safe {@link SystemState}
     */
    public static SystemState generateSafe(int n, int m, long seed) {
        validate(n, m);
        Random rng = new Random(seed);

        // Step 1: total capacities (scaled to n so there is meaningful contention)
        int[] total = randomTotals(n, m, rng);

        // Step 2: reserve initial available pool  (0 … total[j]/n  per resource)
        int[] available = new int[m];
        int[] budget    = new int[m];      // remaining capacity for allocations
        for (int j = 0; j < m; j++) {
            int maxAvail   = Math.max(1, total[j] / (n + 1));
            available[j]   = rng.nextInt(maxAvail + 1);
            budget[j]      = total[j] - available[j];
        }

        // Step 3: safe sequence = random permutation of [0 .. n-1]
        int[] safeOrder = randomPermutation(n, rng);

        int[][] allocation = new int[n][m];
        int[][] maximum    = new int[n][m];

        // work tracks how many resources will be "available" as each process
        // completes in the safe order (starts at the available pool).
        int[] work = available.clone();

        // Remaining allocation budget after each step
        int[] remainingBudget = budget.clone();

        for (int step = 0; step < n; step++) {
            int i             = safeOrder[step];
            int processesLeft = n - step;

            for (int j = 0; j < m; j++) {
                // Allocation: take at most (remaining budget / processes left)
                // so later processes also get something.
                // On the LAST process, take whatever remains to avoid a gap.
                int maxAlloc;
                if (processesLeft == 1) {
                    maxAlloc = remainingBudget[j];   // drain all leftover budget
                } else {
                    maxAlloc = remainingBudget[j] / processesLeft;
                }
                allocation[i][j]   = (maxAlloc > 0) ? rng.nextInt(maxAlloc + 1) : 0;
                remainingBudget[j] -= allocation[i][j];

                // Need: constrained to ≤ work[j]  ← KEY safety constraint
                int maxNeed     = work[j];
                int need        = (maxNeed > 0) ? rng.nextInt(maxNeed + 1) : 0;
                maximum[i][j]   = allocation[i][j] + need;
            }

            // Simulate: process i completes → releases its allocation
            for (int j = 0; j < m; j++) {
                work[j] += allocation[i][j];
            }
        }

        // Any unconsumed budget drains into available to close the invariant.
        // This can happen when every random allocation was 0.
        for (int j = 0; j < m; j++) {
            available[j] += remainingBudget[j];
        }

        String label = String.format("SAFE-n%d-m%d-seed%d", n, m, seed);
        return buildState(n, m, allocation, maximum, available, total, label);
    }

    /**
     * Generates a GUARANTEED-UNSAFE (deadlocked) {@link SystemState}.
     *
     * Algorithm (circular-wait ring):
     *  1. Choose ring size k = min(n, m, MAX_RING_SIZE), k ≥ 2.
     *  2. For ring processes 0 … k-1:
     *       - Process i holds ALL instances of resource i.
     *       - Process i needs at least 1 instance of resource (i+1) % k.
     *       - available[j] = 0 for j in the ring  → no process can make progress.
     *  3. Remaining processes (k … n-1) have zero allocation/need — they can
     *     finish trivially, but the ring is permanently deadlocked.
     *  4. Remaining resources (k … m-1) get normal random allocations from the
     *     non-ring processes so that the state looks realistic.
     *
     * @param n    number of processes (≥ 2)
     * @param m    number of resource types (≥ 2)
     * @param seed RNG seed
     * @return a guaranteed-deadlocked {@link SystemState}
     */
    public static SystemState generateUnsafe(int n, int m, long seed) {
        validate(n, m);
        if (m < 2) throw new IllegalArgumentException(
            "generateUnsafe requires m ≥ 2 to form a circular-wait ring.");

        Random rng = new Random(seed);

        int k = Math.min(Math.min(n, m), MAX_RING_SIZE);
        // k ≥ 2 is guaranteed by m ≥ 2 and the min(n,m) above (since n ≥ 2)

        int[] total     = randomTotals(n, m, rng);
        int[][] allocation = new int[n][m];
        int[][] maximum    = new int[n][m];
        int[]   available  = new int[m];

        // --- Build the circular-wait ring -----------------------------------
        //
        // Process i (0 ≤ i < k):
        //   holds  resource i:         allocation[i][i] = total[i]
        //   needs  resource (i+1)%k:   need[i][(i+1)%k] = 1
        //   → maximum[i][i]         = total[i]
        //   → maximum[i][(i+1)%k]   = 1
        //   → available[j] = 0 for j in 0..k-1  (all held by ring processes)
        //
        for (int i = 0; i < k; i++) {
            allocation[i][i]          = total[i];         // holds all of resource i
            maximum[i][i]             = total[i];         // its max is what it holds (satisfied)
            int nextRes               = (i + 1) % k;
            // allocation of nextRes = 0 (doesn't hold it), need = 1 → max = 1
            maximum[i][nextRes]       = Math.max(maximum[i][nextRes], 1);
            // Note: allocation[i][nextRes] stays 0, so need = 1 after SystemState derives it
        }

        // Ring resources are fully consumed → available = 0
        for (int j = 0; j < k; j++) {
            available[j] = 0;
        }

        // --- Non-ring resources: small random allocations from non-ring processes ---
        for (int j = k; j < m; j++) {
            int remaining = total[j];
            // Give non-ring processes (k..n-1) small random allocations
            for (int i = k; i < n && remaining > 0; i++) {
                int maxAlloc    = remaining / Math.max(1, n - i);
                int alloc       = (maxAlloc > 0) ? rng.nextInt(maxAlloc + 1) : 0;
                allocation[i][j] = alloc;
                remaining        -= alloc;
                // max = alloc + small need; need constrained to remaining capacity
                int maxNeed     = Math.max(0, total[j] / Math.max(1, n - k));
                maximum[i][j]   = alloc + (maxNeed > 0 ? rng.nextInt(maxNeed + 1) : 0);
            }
            available[j] = remaining;
        }

        String label = String.format("UNSAFE-n%d-m%d-seed%d-ring%d", n, m, seed, k);
        return buildState(n, m, allocation, maximum, available, total, label);
    }

    /**
     * Generates a RANDOM {@link SystemState} with no safety guarantee.
     * The resulting state may be safe or unsafe; Banker and CycleDetector
     * will determine which during the benchmark.
     *
     * @param n    number of processes (≥ 2)
     * @param m    number of resource types (≥ 1)
     * @param seed RNG seed
     * @return a random {@link SystemState}
     */
    public static SystemState generateRandom(int n, int m, long seed) {
        validate(n, m);
        Random rng = new Random(seed);

        int[] total     = randomTotals(n, m, rng);
        int[][] allocation = new int[n][m];
        int[][] maximum    = new int[n][m];
        int[]   available  = new int[m];

        int[] remaining = total.clone();

        // Distribute allocations randomly among processes
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                int maxAlloc    = remaining[j] / Math.max(1, n - i);
                int alloc       = (maxAlloc > 0) ? rng.nextInt(maxAlloc + 1) : 0;
                allocation[i][j] = alloc;
                remaining[j]    -= alloc;

                // Maximum: anywhere between allocation and total
                int extra       = rng.nextInt(Math.max(1, total[j] / 2) + 1);
                maximum[i][j]   = alloc + extra;
            }
        }

        // Whatever is left over goes into available
        System.arraycopy(remaining, 0, available, 0, m);

        String label = String.format("RANDOM-n%d-m%d-seed%d", n, m, seed);
        return buildState(n, m, allocation, maximum, available, total, label);
    }

    // =========================================================================
    // Convenience: generate ALL standard benchmark sizes at once
    // =========================================================================

    /**
     * Generates one safe SystemState for each entry in {@link #BENCHMARK_SIZES}.
     *
     * @param baseSeed base seed; each size uses (baseSeed + sizeIndex) for independence
     * @return array of SystemState objects, one per benchmark size
     */
    public static SystemState[] generateAllSizeSafe(long baseSeed) {
        SystemState[] states = new SystemState[BENCHMARK_SIZES.length];
        for (int idx = 0; idx < BENCHMARK_SIZES.length; idx++) {
            int n = BENCHMARK_SIZES[idx][0];
            int m = BENCHMARK_SIZES[idx][1];
            states[idx] = generateSafe(n, m, baseSeed + idx);
        }
        return states;
    }

    /**
     * Generates one unsafe SystemState for each entry in {@link #BENCHMARK_SIZES}.
     */
    public static SystemState[] generateAllSizeUnsafe(long baseSeed) {
        SystemState[] states = new SystemState[BENCHMARK_SIZES.length];
        for (int idx = 0; idx < BENCHMARK_SIZES.length; idx++) {
            int n = BENCHMARK_SIZES[idx][0];
            int m = BENCHMARK_SIZES[idx][1];
            states[idx] = generateUnsafe(n, m, baseSeed + idx);
        }
        return states;
    }

    /**
     * Generates one random SystemState for each entry in {@link #BENCHMARK_SIZES}.
     */
    public static SystemState[] generateAllSizeRandom(long baseSeed) {
        SystemState[] states = new SystemState[BENCHMARK_SIZES.length];
        for (int idx = 0; idx < BENCHMARK_SIZES.length; idx++) {
            int n = BENCHMARK_SIZES[idx][0];
            int m = BENCHMARK_SIZES[idx][1];
            states[idx] = generateRandom(n, m, baseSeed + idx);
        }
        return states;
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Validates basic parameter constraints.
     */
    private static void validate(int n, int m) {
        if (n < 2) throw new IllegalArgumentException("n (processes) must be ≥ 2, got " + n);
        if (m < 1) throw new IllegalArgumentException("m (resources) must be ≥ 1, got " + m);
    }

    /**
     * Generates random total-capacity values for m resource types.
     * Scaled to n so there is meaningful contention: total[j] ∈ [MIN_TOTAL, n*SCALE].
     */
    private static int[] randomTotals(int n, int m, Random rng) {
        int[] total = new int[m];
        int max     = Math.max(MIN_TOTAL_PER_RESOURCE + 1, n * SCALE_FACTOR);
        for (int j = 0; j < m; j++) {
            total[j] = MIN_TOTAL_PER_RESOURCE + rng.nextInt(max - MIN_TOTAL_PER_RESOURCE + 1);
        }
        return total;
    }

    /**
     * Returns a randomly shuffled permutation of [0 .. n-1] (Fisher-Yates).
     */
    private static int[] randomPermutation(int n, Random rng) {
        int[] perm = new int[n];
        for (int i = 0; i < n; i++) perm[i] = i;
        for (int i = n - 1; i > 0; i--) {
            int j   = rng.nextInt(i + 1);
            int tmp = perm[i]; perm[i] = perm[j]; perm[j] = tmp;
        }
        return perm;
    }

    /**
     * Constructs a {@link SystemState} from raw matrices, creating the Process
     * and Resource wrapper objects automatically.
     *
     * The conservation invariant (sum(alloc[i][j]) + available[j] == total[j])
     * is verified inside {@link SystemState}'s constructor; any bug in the
     * generation logic will surface immediately as an {@link IllegalArgumentException}.
     */
    private static SystemState buildState(int n, int m,
                                          int[][] allocation, int[][] maximum,
                                          int[] available, int[] total,
                                          String label) {
        // Build Process objects
        Process[] procs = new Process[n];
        for (int i = 0; i < n; i++) {
            procs[i] = new Process(i, "P" + i, allocation[i], maximum[i]);
        }

        // Build Resource objects
        Resource[] res = new Resource[m];
        for (int j = 0; j < m; j++) {
            res[j] = new Resource(j, "R" + j, total[j], available[j]);
        }

        return new SystemState(procs, res, allocation, maximum, available, label);
    }
}
