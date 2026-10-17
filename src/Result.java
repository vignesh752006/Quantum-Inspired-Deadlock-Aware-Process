import java.util.Arrays;

/**
 * Result.java
 *
 * Immutable value object holding the metrics from a single benchmark run
 * of one algorithm on one SystemState.
 *
 * Designed for easy CSV/JSON serialisation in Step 8 (PerformanceAnalyzer).
 *
 * @author  OSDS-CP Group
 * @version 1.0  (Phase 1)
 */
public class Result {

    // ── Identification ────────────────────────────────────────────────────────
    /** Algorithm label: "Banker", "QEA", or "Hybrid" */
    public final String algorithm;

    /** Number of processes in the test state. */
    public final int processCount;

    /** Number of resource types in the test state. */
    public final int resourceCount;

    /** Repetition index (0-based). */
    public final int repetition;

    /** Seed used for state generation. */
    public final long stateSeed;

    /** State type: "safe", "unsafe", or "random". */
    public final String stateType;

    // ── Timing ────────────────────────────────────────────────────────────────
    /** Execution time of this single run, in nanoseconds. */
    public final long executionTimeNs;

    // ── Quality Metrics ───────────────────────────────────────────────────────
    /** Fitness value (lower = better). For Banker, derived from safe-sequence simulation. */
    public final double fitnessValue;

    /** Number of processes that completed successfully. */
    public final int processesCompleted;

    /** Total waiting time accumulated during scheduling simulation. */
    public final double waitingTime;

    /** Total idle resource instances at end of simulation. */
    public final double resourceWaste;

    /** True iff the algorithm determined the schedule is safe / deadlock-free. */
    public final boolean isSafe;

    // ── QEA-specific (0 for Banker) ───────────────────────────────────────────
    /** Number of QEA iterations actually run (0 for Banker). */
    public final int iterations;

    /** The execution order found (process indices). */
    public final int[] executionOrder;

    // ── Constructor ───────────────────────────────────────────────────────────

    public Result(String algorithm, int processCount, int resourceCount,
                  int repetition, long stateSeed, String stateType,
                  long executionTimeNs, double fitnessValue,
                  int processesCompleted, double waitingTime,
                  double resourceWaste, boolean isSafe,
                  int iterations, int[] executionOrder) {
        this.algorithm         = algorithm;
        this.processCount      = processCount;
        this.resourceCount     = resourceCount;
        this.repetition        = repetition;
        this.stateSeed         = stateSeed;
        this.stateType         = stateType;
        this.executionTimeNs   = executionTimeNs;
        this.fitnessValue      = fitnessValue;
        this.processesCompleted = processesCompleted;
        this.waitingTime       = waitingTime;
        this.resourceWaste     = resourceWaste;
        this.isSafe            = isSafe;
        this.iterations        = iterations;
        this.executionOrder    = (executionOrder != null) ? executionOrder.clone() : new int[0];
    }

    // ── CSV ───────────────────────────────────────────────────────────────────

    /** CSV header row (matches toCsvRow column order). */
    public static String csvHeader() {
        return "algorithm,processCount,resourceCount,repetition,stateSeed,stateType,"
             + "executionTimeNs,fitnessValue,processesCompleted,waitingTime,"
             + "resourceWaste,isSafe,iterations";
    }

    /** Converts this result to a single CSV row. */
    public String toCsvRow() {
        return String.format("%s,%d,%d,%d,%d,%s,%d,%.4f,%d,%.4f,%.4f,%b,%d",
            algorithm, processCount, resourceCount, repetition, stateSeed, stateType,
            executionTimeNs, fitnessValue, processesCompleted, waitingTime,
            resourceWaste, isSafe, iterations);
    }

    @Override
    public String toString() {
        return String.format("Result{algo=%s, n=%d, m=%d, rep=%d, time=%dns, fitness=%.1f, safe=%b}",
            algorithm, processCount, resourceCount, repetition,
            executionTimeNs, fitnessValue, isSafe);
    }
}
