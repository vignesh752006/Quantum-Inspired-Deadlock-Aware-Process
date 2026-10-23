package io.github.vignesh752006.quantumdeadlock.banker;

import io.github.vignesh752006.quantumdeadlock.model.SystemState;
import io.github.vignesh752006.quantumdeadlock.util.Matrices;
import io.github.vignesh752006.quantumdeadlock.util.Preconditions;

import java.util.ArrayList;
import java.util.List;

/**
 * Dijkstra's Banker's Algorithm: the classical safety check for a
 * resource-allocation state.
 *
 * <p>The state is <em>safe</em> when at least one ordering exists in which every
 * process can finish without deadlocking. The safety algorithm:</p>
 * <ol>
 *   <li>{@code Work = Available}; all {@code Finish[i] = false}</li>
 *   <li>Find an unfinished {@code i} with {@code Need[i][j] <= Work[j]} for every {@code j}</li>
 *   <li>If none exists, stop; otherwise simulate completion:
 *       {@code Work[j] += Allocation[i][j]}, {@code Finish[i] = true}, repeat from 2</li>
 *   <li>Safe iff every process finished</li>
 * </ol>
 *
 * <p>Complexity is {@code O(n^2 * m)}: the outer loop runs at most {@code n} times
 * and each full scan costs {@code O(n * m)}.</p>
 *
 * <p>The input state is never mutated — all working arrays are local copies.</p>
 */
public final class BankersAlgorithm {

    private BankersAlgorithm() {
        throw new AssertionError("No instances.");
    }

    /**
     * Runs the full safety check, recording a replayable trace.
     *
     * @param state the state to evaluate; must not be null
     * @return the verdict, safe sequence, timing and step trace
     */
    public static BankersResult checkSafety(SystemState state) {
        Preconditions.requireNonNull(state, "state must not be null.");

        long startNs = System.nanoTime();
        final int n = state.processCount();
        final int m = state.resourceCount();

        int[] work = state.availableVector();
        boolean[] finished = new boolean[n];
        int[] safeSequence = new int[n];
        int sequenceIndex = 0;
        List<String> trace = new ArrayList<>();
        int scanCount = 0;

        boolean progressed = true;
        while (progressed) {
            progressed = false;
            scanCount++;

            for (int i = 0; i < n; i++) {
                if (finished[i] || !canRun(state, i, work, m)) {
                    continue;
                }
                String before = Matrices.format(work);
                for (int j = 0; j < m; j++) {
                    work[j] += state.allocation(i, j);
                }
                trace.add("GRANT " + state.process(i).name() + ": Work " + before
                        + " -> " + Matrices.format(work));

                finished[i] = true;
                safeSequence[sequenceIndex++] = i;
                progressed = true;
            }
        }

        boolean allFinished = sequenceIndex == n;
        if (allFinished) {
            trace.add("SAFE: sequence " + Matrices.format(safeSequence));
        } else {
            StringBuilder blocked = new StringBuilder("BLOCKED:");
            for (int i = 0; i < n; i++) {
                if (!finished[i]) {
                    blocked.append(' ').append(state.process(i).name())
                            .append(" need=").append(Matrices.format(state.needRow(i)));
                }
            }
            trace.add(blocked.toString());
        }

        long elapsedNs = System.nanoTime() - startNs;
        return new BankersResult(allFinished,
                allFinished ? safeSequence : new int[0],
                elapsedNs, trace, scanCount);
    }

    /**
     * Fast boolean-only safety check that skips trace construction.
     *
     * <p>Used inside the hybrid fitness function's inner loop, where the only
     * thing that matters is the verdict.</p>
     *
     * @param state the state to evaluate; must not be null
     * @return true iff the state is safe
     */
    public static boolean isSafe(SystemState state) {
        Preconditions.requireNonNull(state, "state must not be null.");

        final int n = state.processCount();
        final int m = state.resourceCount();
        int[] work = state.availableVector();
        boolean[] finished = new boolean[n];
        int completed = 0;

        boolean progressed = true;
        while (progressed) {
            progressed = false;
            for (int i = 0; i < n; i++) {
                if (finished[i] || !canRun(state, i, work, m)) {
                    continue;
                }
                for (int j = 0; j < m; j++) {
                    work[j] += state.allocation(i, j);
                }
                finished[i] = true;
                completed++;
                progressed = true;
            }
        }
        return completed == n;
    }

    /**
     * Builds a human-readable report of a completed check.
     *
     * <p>Reporting lives here rather than printing straight to {@code System.out}
     * inside the algorithm, so callers decide what to do with the text.</p>
     *
     * @param state  the evaluated state, used for names
     * @param result the result of {@link #checkSafety}
     * @return a multi-line report
     */
    public static String formatReport(SystemState state, BankersResult result) {
        StringBuilder report = new StringBuilder();
        report.append("=== Banker's Algorithm Report ===\n");
        report.append(String.format("State    : %s%n", state.label()));
        report.append(String.format("Processes: %d   Resource types: %d%n",
                state.processCount(), state.resourceCount()));
        report.append(String.format("Verdict  : %s%n", result.safe() ? "SAFE" : "UNSAFE"));
        report.append(String.format("Sequence : %s%n", result.sequenceString(state)));
        report.append(String.format("Scans    : %d   Time: %d us%n",
                result.scanCount(), result.executionTimeNs() / 1_000));
        report.append("Trace:\n");
        for (String step : result.trace()) {
            report.append("  ").append(step).append('\n');
        }
        return report.toString();
    }

    /** True iff process {@code i}'s need fits entirely inside the work vector. */
    private static boolean canRun(SystemState state, int processIndex, int[] work, int resourceCount) {
        for (int j = 0; j < resourceCount; j++) {
            if (state.need(processIndex, j) > work[j]) {
                return false;
            }
        }
        return true;
    }
}
