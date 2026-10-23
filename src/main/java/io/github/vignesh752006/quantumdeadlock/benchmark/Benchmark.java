package io.github.vignesh752006.quantumdeadlock.benchmark;

import io.github.vignesh752006.quantumdeadlock.banker.BankersAlgorithm;
import io.github.vignesh752006.quantumdeadlock.banker.BankersResult;
import io.github.vignesh752006.quantumdeadlock.generator.TestCaseGenerator;
import io.github.vignesh752006.quantumdeadlock.model.SystemState;
import io.github.vignesh752006.quantumdeadlock.quantum.CandidateSolution;
import io.github.vignesh752006.quantumdeadlock.quantum.FitnessFunction;
import io.github.vignesh752006.quantumdeadlock.quantum.OptimizationResult;
import io.github.vignesh752006.quantumdeadlock.quantum.QuantumInspiredOptimizer;
import io.github.vignesh752006.quantumdeadlock.util.Preconditions;
import io.github.vignesh752006.quantumdeadlock.util.Statistics;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs the three-way comparison across the standard benchmark sizes.
 *
 * <p>For every size one canonical state is generated, then each algorithm receives
 * its own {@link SystemState#deepCopy()} so no run can influence another. Every
 * repetition is recorded — nothing is discarded or cherry-picked — which is what
 * lets the exported CSV/JSON be re-analysed independently.</p>
 *
 * <p>Reporting goes to a supplied {@link PrintStream} rather than straight to
 * {@code System.out}, so a test or embedder can capture or silence it.</p>
 */
public final class Benchmark {

    public static final int BANKER_REPETITIONS = 5;
    public static final int QEA_REPETITIONS = 30;
    public static final int HYBRID_REPETITIONS = 30;

    private static final long BASE_STATE_SEED = 42L;
    private static final long BASE_QEA_SEED = 1000L;

    private final PrintStream out;
    private final List<Result> results = new ArrayList<>();

    /** Creates a benchmark that reports to {@code System.out}. */
    public Benchmark() {
        this(System.out);
    }

    /**
     * @param out destination for progress and summary output; must not be null
     */
    public Benchmark(PrintStream out) {
        this.out = Preconditions.requireNonNull(out, "out must not be null.");
    }

    /** Executes every configuration, accumulating results. */
    public void runAll() {
        results.clear();
        int[][] sizes = TestCaseGenerator.BENCHMARK_SIZES;

        out.println("=== Deadlock-Aware Scheduling Benchmark ===");
        out.printf("Banker repetitions: %d   QEA: %d   Hybrid: %d   Configurations: %d%n",
                BANKER_REPETITIONS, QEA_REPETITIONS, HYBRID_REPETITIONS, sizes.length);
        out.println();

        for (int index = 0; index < sizes.length; index++) {
            int processCount = sizes[index][0];
            int resourceCount = sizes[index][1];
            long stateSeed = BASE_STATE_SEED + index;

            out.printf("-- [%d/%d] n=%d m=%d seed=%d --%n",
                    index + 1, sizes.length, processCount, resourceCount, stateSeed);

            SystemState canonical = TestCaseGenerator.generateSafe(processCount, resourceCount, stateSeed);

            List<Result> bankerResults = runBanker(canonical, stateSeed);
            results.addAll(bankerResults);
            printProgress("Banker", bankerResults);

            List<Result> qeaResults = runStochastic(canonical, stateSeed, false);
            results.addAll(qeaResults);
            printProgress("QEA", qeaResults);

            List<Result> hybridResults = runStochastic(canonical, stateSeed, true);
            results.addAll(hybridResults);
            printProgress("Hybrid", hybridResults);

            out.println();
        }

        printSummaryTable();
    }

    private List<Result> runBanker(SystemState canonical, long stateSeed) {
        List<Result> runs = new ArrayList<>(BANKER_REPETITIONS);
        FitnessFunction scorer = new FitnessFunction(false);

        for (int repetition = 0; repetition < BANKER_REPETITIONS; repetition++) {
            SystemState copy = canonical.deepCopy();

            long startNs = System.nanoTime();
            BankersResult bankerResult = BankersAlgorithm.checkSafety(copy);
            long elapsedNs = System.nanoTime() - startNs;

            int processCount = canonical.processCount();
            double fitness;
            int completed;
            double waitingTime;
            double waste;
            boolean safe = bankerResult.safe();

            if (safe) {
                // Score the Banker-produced order with the same function the QEA uses,
                // so the comparison is like-for-like rather than timing-only.
                CandidateSolution scored = new CandidateSolution(
                        bankerResult.safeSequence(), new int[processCount]);
                scorer.evaluate(scored, canonical);
                fitness = scored.fitness();
                completed = scored.processesCompleted();
                waitingTime = scored.totalWaitingTime();
                waste = scored.resourceWaste();
            } else {
                fitness = FitnessFunction.PENALTY_FITNESS;
                completed = 0;
                waitingTime = processCount * 100.0;
                waste = canonical.totalResourceWaste();
            }

            runs.add(new Result("Banker", processCount, canonical.resourceCount(), repetition,
                    stateSeed, "safe", elapsedNs, fitness, completed, waitingTime, waste,
                    safe, bankerResult.scanCount()));
        }
        return runs;
    }

    private List<Result> runStochastic(SystemState canonical, long stateSeed, boolean hybrid) {
        String algorithm = hybrid ? "Hybrid" : "QEA";
        int repetitions = hybrid ? HYBRID_REPETITIONS : QEA_REPETITIONS;
        List<Result> runs = new ArrayList<>(repetitions);

        for (int repetition = 0; repetition < repetitions; repetition++) {
            SystemState copy = canonical.deepCopy();
            FitnessFunction scorer = new FitnessFunction(hybrid);
            QuantumInspiredOptimizer optimizer = new QuantumInspiredOptimizer(copy, scorer);

            OptimizationResult optimization = optimizer.optimize(BASE_QEA_SEED + repetition);
            CandidateSolution best = optimization.bestSolution();

            runs.add(new Result(algorithm, canonical.processCount(), canonical.resourceCount(),
                    repetition, stateSeed, "safe",
                    optimization.executionTimeNs(),
                    optimization.bestFitness(),
                    best == null ? 0 : best.processesCompleted(),
                    best == null ? 0.0 : best.totalWaitingTime(),
                    best == null ? 0.0 : best.resourceWaste(),
                    best != null && best.isSafe(),
                    optimization.iterationsRun()));
        }
        return runs;
    }

    private void printProgress(String algorithm, List<Result> runs) {
        Statistics times = Statistics.of(runs, Result::executionTimeMs);
        Statistics fitness = Statistics.of(runs, Result::fitnessValue);
        long safeCount = runs.stream().filter(Result::isSafe).count();
        out.printf("   %-6s mean_time=%7.2f ms  mean_fitness=%9.1f  safe=%d/%d%n",
                algorithm, times.mean(), fitness.mean(), safeCount, runs.size());
    }

    /** Prints the per-size, per-algorithm aggregate table. */
    public void printSummaryTable() {
        out.println();
        out.println("=== Summary ===");
        out.printf("%-8s %5s %5s %10s %10s %9s %7s%n",
                "Algorithm", "n", "m", "mean ms", "mean fit", "safe", "iters");

        for (int[] size : TestCaseGenerator.BENCHMARK_SIZES) {
            int processCount = size[0];
            for (String algorithm : new String[]{"Banker", "QEA", "Hybrid"}) {
                List<Result> subset = filter(algorithm, processCount);
                if (subset.isEmpty()) {
                    continue;
                }
                Statistics times = Statistics.of(subset, Result::executionTimeMs);
                Statistics fitness = Statistics.of(subset, Result::fitnessValue);
                Statistics iterations = Statistics.of(subset, r -> r.iterations());
                long safeCount = subset.stream().filter(Result::isSafe).count();

                out.printf("%-8s %5d %5d %10.2f %10.1f %4d/%-4d %7.1f%n",
                        algorithm, processCount, size[1], times.mean(), fitness.mean(),
                        safeCount, subset.size(), iterations.mean());
            }
        }
        out.println();
        printFairnessFlags();
    }

    /**
     * Reports the two properties the comparison is supposed to demonstrate:
     * that the deterministic baseline wins on time in at least one configuration,
     * and that the optimiser does not automatically beat it on quality.
     */
    private void printFairnessFlags() {
        boolean bankerEverFaster = false;
        boolean qeaEverNoBetter = false;

        for (int[] size : TestCaseGenerator.BENCHMARK_SIZES) {
            List<Result> bankerRuns = filter("Banker", size[0]);
            List<Result> qeaRuns = filter("QEA", size[0]);
            if (bankerRuns.isEmpty() || qeaRuns.isEmpty()) {
                continue;
            }

            double bankerNanos = Statistics.of(bankerRuns,
                    result -> (double) result.executionTimeNs()).mean();
            double qeaNanos = Statistics.of(qeaRuns,
                    result -> (double) result.executionTimeNs()).mean();
            if (bankerNanos < qeaNanos) {
                bankerEverFaster = true;
            }

            double bankerFitness = Statistics.of(bankerRuns, Result::fitnessValue).mean();
            double qeaFitness = Statistics.of(qeaRuns, Result::fitnessValue).mean();
            if (qeaFitness >= bankerFitness) {
                qeaEverNoBetter = true;
            }
        }

        out.println("Fairness checks");
        out.printf("  [%s] Banker is faster than QEA in at least one size%n",
                bankerEverFaster ? "PASS" : "FAIL");
        out.printf("  [%s] QEA fails to improve on Banker in at least one size%n",
                qeaEverNoBetter ? "PASS" : "FAIL");
        if (!bankerEverFaster) {
            out.println("  WARNING: Banker was never faster - check for JIT warm-up artefacts.");
        }
    }

    /** @return the runs matching one algorithm and process count */
    public List<Result> filter(String algorithm, int processCount) {
        List<Result> subset = new ArrayList<>();
        for (Result result : results) {
            if (result.algorithm().equals(algorithm) && result.processCount() == processCount) {
                subset.add(result);
            }
        }
        return subset;
    }

    /** @return a copy of every recorded run */
    public List<Result> allResults() {
        return new ArrayList<>(results);
    }
}
