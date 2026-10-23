package io.github.vignesh752006.quantumdeadlock.benchmark;

/**
 * Metrics from a single run of one algorithm on one state.
 *
 * <p>Immutable, and deliberately free of CSV/JSON formatting so the export format
 * lives in one place ({@link PerformanceAnalyzer}) rather than being duplicated
 * between a value object and a writer.</p>
 *
 * @param algorithm          algorithm label: {@code "Banker"}, {@code "QEA"} or {@code "Hybrid"}
 * @param processCount       number of processes in the test state
 * @param resourceCount      number of resource types in the test state
 * @param repetition         zero-based repetition index
 * @param stateSeed          seed used to generate the state
 * @param stateType          {@code "safe"}, {@code "unsafe"} or {@code "random"}
 * @param executionTimeNs    duration of this run in nanoseconds
 * @param fitnessValue       fitness score, lower being better
 * @param processesCompleted processes that completed successfully
 * @param waitingTime        accumulated blocked time
 * @param resourceWaste      idle resource instances at the end
 * @param isSafe             whether the schedule was found to be deadlock-free
 * @param iterations         generations run; 0 for the deterministic Banker baseline
 */
public record Result(String algorithm,
                     int processCount,
                     int resourceCount,
                     int repetition,
                     long stateSeed,
                     String stateType,
                     long executionTimeNs,
                     double fitnessValue,
                     int processesCompleted,
                     double waitingTime,
                     double resourceWaste,
                     boolean isSafe,
                     int iterations) {

    /** @return this run's duration in milliseconds */
    public double executionTimeMs() {
        return executionTimeNs / 1_000_000.0;
    }

    @Override
    public String toString() {
        return String.format("Result{algorithm=%s, n=%d, m=%d, rep=%d, time=%.3fms, fitness=%.1f, safe=%b}",
                algorithm, processCount, resourceCount, repetition,
                executionTimeMs(), fitnessValue, isSafe);
    }
}
