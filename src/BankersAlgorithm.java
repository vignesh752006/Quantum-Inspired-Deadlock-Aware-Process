import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * BankersAlgorithm.java
 *
 * Implements the classical Banker's Algorithm safety check as described in
 * Silberschatz, Galvin & Gagne, "Operating System Concepts" (10th ed.),
 * Section 8.6.3.
 *
 * The algorithm answers: "Is the current resource-allocation state SAFE?"
 * A state is safe iff there exists at least one ordering (safe sequence) in
 * which every process can eventually complete without causing deadlock.
 *
 * The 7-step safety algorithm:
 *   1.  Let Work  = Available  (copy)
 *   2.  Let Finish[i] = false  for all i
 *   3.  Find i such that:
 *         (a) Finish[i] == false
 *         (b) Need[i][j] <= Work[j]  for all j
 *       If no such i exists, go to step 6.
 *   4.  Work[j] += Allocation[i][j]  for all j      (simulate completion)
 *   5.  Finish[i] = true;  append i to safe sequence;  go to step 3.
 *   6.  If Finish[i] == true for all i → state is SAFE.
 *   7.  Else → state is UNSAFE (one or more processes cannot complete).
 *
 * Complexity:  O(n² × m)  — the outer "find next runnable" loop runs at most
 *              n times, and each scan of all n processes costs O(n × m).
 *
 * Design decisions:
 *   - The method NEVER mutates the passed {@link SystemState} — it works on
 *     copies of the available/allocation arrays.
 *   - An optional step trace is recorded so the dashboard and reports can
 *     replay the algorithm step-by-step.
 *   - The {@link BankersResult} inner class is a plain value object — easy to
 *     serialise to CSV/JSON in Step 8.
 *   - The static method {@link #isSafe(SystemState)} is a fast boolean helper
 *     for callers (like the hybrid QEA) that only need the verdict.
 *
 * Phase-2 note:
 *   No changes needed.  Phase 2 passes a {@code SystemState} built from live
 *   VM agent data; this class is completely data-agnostic.
 *
 * @author  OSDS-CP Group
 * @version 1.0  (Phase 1 – simulated data)
 */
public class BankersAlgorithm {

    // =========================================================================
    // Result value object
    // =========================================================================

    /**
     * Encapsulates the complete output of one Banker safety check.
     * All fields are immutable after construction.
     */
    public static class BankersResult {

        /** True iff the system state is safe. */
        public final boolean safe;

        /**
         * Indices of processes in the safe sequence, or an empty array if
         * the state is unsafe.  Index i in this array is the 0-based process
         * id; use {@code SystemState.getProcess(safeSequence[k]).getName()}
         * to get a human-readable label.
         */
        public final int[] safeSequence;

        /** Nanosecond wall-clock time for this one safety check. */
        public final long executionTimeNs;

        /**
         * Human-readable trace of every step the algorithm took.
         * One entry per "find runnable process" scan result.
         * Useful for the dashboard step-by-step view and for test assertions.
         */
        public final List<String> trace;

        /**
         * Number of process-scans performed (= steps in the outer while loop).
         * Useful as an additional benchmark metric.
         */
        public final int scanCount;

        BankersResult(boolean safe, int[] safeSequence,
                      long executionTimeNs, List<String> trace, int scanCount) {
            this.safe            = safe;
            this.safeSequence    = safeSequence.clone();
            this.executionTimeNs = executionTimeNs;
            this.trace           = Collections.unmodifiableList(new ArrayList<>(trace));
            this.scanCount       = scanCount;
        }

        /**
         * Formats the safe sequence as a human-readable string using process
         * names from the supplied state, e.g. "&lt;P1, P3, P4, P2, P0&gt;".
         *
         * @param state the state used to look up process names
         */
        public String sequenceString(SystemState state) {
            if (!safe || safeSequence.length == 0) return "<UNSAFE – no safe sequence>";
            StringBuilder sb = new StringBuilder("<");
            for (int k = 0; k < safeSequence.length; k++) {
                sb.append(state.getProcess(safeSequence[k]).getName());
                if (k < safeSequence.length - 1) sb.append(", ");
            }
            sb.append(">");
            return sb.toString();
        }

        @Override
        public String toString() {
            return String.format("BankersResult{safe=%b, scans=%d, timeNs=%d, seq=%s}",
                safe, scanCount, executionTimeNs, Arrays.toString(safeSequence));
        }
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Runs the Banker's Algorithm safety check on the given state.
     *
     * The {@link SystemState} is NOT modified.  All working arrays are local
     * copies.
     *
     * @param state the resource-allocation state to evaluate
     * @return a {@link BankersResult} with the verdict, safe sequence,
     *         execution time, and step trace
     */
    public static BankersResult checkSafety(SystemState state) {
        long startNs = System.nanoTime();

        final int n = state.getNumProcesses();
        final int m = state.getNumResources();

        // Step 1 & 2: initialise Work and Finish
        int[] work      = state.getAvailableVector();   // defensive copy
        boolean[] finish = new boolean[n];              // all false

        int[]        safeSeq   = new int[n];
        int          seqIdx    = 0;
        List<String> trace     = new ArrayList<>();
        int          scanCount = 0;

        // Step 3–5: repeat until no runnable process found
        boolean found = true;
        while (found) {
            found = false;
            scanCount++;

            for (int i = 0; i < n; i++) {
                if (!finish[i] && canRun(state, i, work, m)) {
                    // Step 4: simulate process i completing → release its allocation
                    StringBuilder sb = new StringBuilder();
                    sb.append("GRANT ").append(state.getProcess(i).getName())
                      .append(": Work ").append(Arrays.toString(work));
                    for (int j = 0; j < m; j++) work[j] += state.getAllocation(i, j);
                    sb.append(" → ").append(Arrays.toString(work));
                    trace.add(sb.toString());

                    // Step 5
                    finish[i]          = true;
                    safeSeq[seqIdx++]  = i;
                    found              = true;
                }
            }
        }

        // Step 6 & 7: check whether all processes finished
        boolean allFinished = (seqIdx == n);

        if (!allFinished) {
            // Record which processes are stuck
            StringBuilder sb = new StringBuilder("BLOCKED:");
            for (int i = 0; i < n; i++) {
                if (!finish[i]) {
                    sb.append(" ").append(state.getProcess(i).getName())
                      .append(" need=").append(Arrays.toString(state.getNeedRow(i)));
                }
            }
            trace.add(sb.toString());
        } else {
            trace.add("SAFE: sequence " + Arrays.toString(safeSeq));
        }

        long elapsedNs = System.nanoTime() - startNs;

        return new BankersResult(
            allFinished,
            allFinished ? safeSeq : new int[0],
            elapsedNs,
            trace,
            scanCount
        );
    }

    /**
     * Convenience boolean-only helper.  Marginally faster than
     * {@link #checkSafety} because it skips trace building.
     *
     * Used by the Hybrid QEA inner loop to filter candidates quickly.
     *
     * @param state the resource-allocation state to evaluate
     * @return true iff the state is safe
     */
    public static boolean isSafe(SystemState state) {
        final int n = state.getNumProcesses();
        final int m = state.getNumResources();
        int[] work      = state.getAvailableVector();
        boolean[] finish = new boolean[n];
        int count = 0;
        boolean found = true;
        while (found) {
            found = false;
            for (int i = 0; i < n; i++) {
                if (!finish[i] && canRun(state, i, work, m)) {
                    for (int j = 0; j < m; j++) work[j] += state.getAllocation(i, j);
                    finish[i] = true;
                    count++;
                    found = true;
                }
            }
        }
        return count == n;
    }

    /**
     * Prints a formatted report of the safety check result.
     *
     * @param state  the state that was checked (used for process names)
     * @param result the result from {@link #checkSafety}
     */
    public static void printReport(SystemState state, BankersResult result) {
        System.out.println("=== Banker's Algorithm Report ===");
        System.out.printf("State    : %s%n", state.getLabel());
        System.out.printf("Processes: %d   Resource types: %d%n",
            state.getNumProcesses(), state.getNumResources());
        System.out.printf("Verdict  : %s%n", result.safe ? "✓ SAFE" : "✗ UNSAFE");
        System.out.printf("Sequence : %s%n", result.sequenceString(state));
        System.out.printf("Scans    : %d   Time: %d µs%n",
            result.scanCount, result.executionTimeNs / 1_000);
        System.out.println("Trace:");
        for (String step : result.trace) System.out.println("  " + step);
        System.out.println();
    }

    // =========================================================================
    // Private helper
    // =========================================================================

    /**
     * Returns true iff process {@code i} can run given the current Work vector:
     * Need[i][j] <= work[j] for all j.
     */
    private static boolean canRun(SystemState state, int i, int[] work, int m) {
        for (int j = 0; j < m; j++) {
            if (state.getNeed(i, j) > work[j]) return false;
        }
        return true;
    }
}
