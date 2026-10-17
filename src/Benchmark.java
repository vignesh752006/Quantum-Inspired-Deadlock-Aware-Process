import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Benchmark.java
 *
 * Orchestrates the full benchmark suite comparing three scheduling algorithms:
 *   1. Banker's Algorithm alone (deterministic — 5 reps)
 *   2. QEA alone (stochastic — 30 reps)
 *   3. Banker + QEA Hybrid (stochastic — 30 reps)
 *
 * For each benchmark size in {@link TestCaseGenerator#BENCHMARK_SIZES}:
 *   - Generates ONE SystemState (safe) with a fixed seed
 *   - Passes deepCopy() to each algorithm for fairness
 *   - Records per-run results as {@link Result} objects
 *   - Computes and prints aggregate statistics (mean, std, min, max)
 *
 * Fairness guarantees:
 *   - Same SystemState across all three algorithms (verified by deepCopy)
 *   - Banker deterministic runs should produce identical timings (within JIT variance)
 *   - QEA/Hybrid seeds are sequential per repetition for reproducibility
 *   - Every single run is logged (no cherry-picking)
 *
 * Phase-2 Extension:
 *   Replace {@code TestCaseGenerator.generateSafe()} with
 *   {@code DataSource.fromAgentReport()} via the DataSource interface.
 *
 * @author  OSDS-CP Group
 * @version 1.0  (Phase 1)
 */
public class Benchmark {

    // ── Configuration ─────────────────────────────────────────────────────────
    public static final int BANKER_REPS = 5;
    public static final int QEA_REPS    = 30;
    public static final int HYBRID_REPS = 30;

    /** Base seed for state generation (each size gets baseSeed + sizeIndex). */
    private static final long BASE_STATE_SEED = 42L;

    /** Base seed for QEA/Hybrid RNG (each rep gets baseSeed + rep). */
    private static final long BASE_QEA_SEED = 1000L;

    // ── Results ───────────────────────────────────────────────────────────────
    private final List<Result> allResults = new ArrayList<>();

    // ── Run ───────────────────────────────────────────────────────────────────

    /**
     * Executes the complete benchmark suite across all standard sizes.
     * Results are accumulated in {@link #getAllResults()}.
     */
    public void runAll() {
        allResults.clear();
        int[][] sizes = TestCaseGenerator.BENCHMARK_SIZES;

        System.out.println("╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║        BENCHMARK SUITE — Deadlock-Aware Scheduling           ║");
        System.out.println("╠═══════════════════════════════════════════════════════════════╣");
        System.out.printf("║  Banker reps: %d   QEA reps: %d   Hybrid reps: %d            ║%n",
            BANKER_REPS, QEA_REPS, HYBRID_REPS);
        System.out.printf("║  Sizes: %d configurations                                     ║%n", sizes.length);
        System.out.println("╚═══════════════════════════════════════════════════════════════╝");
        System.out.println();

        for (int idx = 0; idx < sizes.length; idx++) {
            int n = sizes[idx][0];
            int m = sizes[idx][1];
            long stateSeed = BASE_STATE_SEED + idx;

            System.out.printf("── Benchmark [%d/%d]: n=%d  m=%d  seed=%d ──%n",
                idx + 1, sizes.length, n, m, stateSeed);

            // Generate ONE state for this size
            SystemState masterState = TestCaseGenerator.generateSafe(n, m, stateSeed);

            // ── 1. Banker's Algorithm (deterministic) ─────────────────────
            System.out.printf("   Banker (%d reps)...", BANKER_REPS);
            List<Result> bankerResults = runBanker(masterState, n, m, stateSeed);
            allResults.addAll(bankerResults);
            printQuickStats("Banker", bankerResults);

            // ── 2. QEA Alone (stochastic) ─────────────────────────────────
            System.out.printf("   QEA    (%d reps)...", QEA_REPS);
            List<Result> qeaResults = runQEA(masterState, n, m, stateSeed, false);
            allResults.addAll(qeaResults);
            printQuickStats("QEA", qeaResults);

            // ── 3. Hybrid (stochastic) ────────────────────────────────────
            System.out.printf("   Hybrid (%d reps)...", HYBRID_REPS);
            List<Result> hybridResults = runQEA(masterState, n, m, stateSeed, true);
            allResults.addAll(hybridResults);
            printQuickStats("Hybrid", hybridResults);

            System.out.println();
        }

        printSummaryTable();
    }

    // ── Banker Benchmark ──────────────────────────────────────────────────────

    private List<Result> runBanker(SystemState masterState, int n, int m, long stateSeed) {
        List<Result> results = new ArrayList<>();
        FitnessFunction fitnessEval = new FitnessFunction(false);

        for (int rep = 0; rep < BANKER_REPS; rep++) {
            SystemState copy = masterState.deepCopy();

            long startNs = System.nanoTime();
            BankersAlgorithm.BankersResult bResult = BankersAlgorithm.checkSafety(copy);
            long elapsed = System.nanoTime() - startNs;

            // Evaluate Banker's safe sequence through the FitnessFunction for fair comparison
            double fitness;
            int completed;
            double waitTime;
            double waste;
            boolean safe = bResult.safe;
            int[] order;

            if (safe) {
                order = bResult.safeSequence;
                CandidateSolution sol = new CandidateSolution(order, new int[n]);
                fitnessEval.evaluate(sol, masterState);
                fitness   = sol.fitness;
                completed = sol.processesCompleted;
                waitTime  = sol.totalWaitingTime;
                waste     = sol.resourceWaste;
            } else {
                order     = new int[0];
                fitness   = Double.MAX_VALUE / 2.0;
                completed = 0;
                waitTime  = n * 100.0;
                waste     = masterState.getTotalResourceWaste();
            }

            results.add(new Result(
                "Banker", n, m, rep, stateSeed, "safe",
                elapsed, fitness, completed, waitTime, waste, safe,
                bResult.scanCount, order
            ));
        }
        return results;
    }

    // ── QEA / Hybrid Benchmark ────────────────────────────────────────────────

    private List<Result> runQEA(SystemState masterState, int n, int m,
                                long stateSeed, boolean hybrid) {
        List<Result> results = new ArrayList<>();
        String algoName = hybrid ? "Hybrid" : "QEA";
        int reps = hybrid ? HYBRID_REPS : QEA_REPS;

        for (int rep = 0; rep < reps; rep++) {
            SystemState copy = masterState.deepCopy();
            FitnessFunction fitness = new FitnessFunction(hybrid);
            QuantumInspiredOptimizer optimizer = new QuantumInspiredOptimizer(
                copy, fitness,
                QuantumInspiredOptimizer.DEFAULT_POP_SIZE,
                QuantumInspiredOptimizer.DEFAULT_MAX_ITER,
                QuantumInspiredOptimizer.DEFAULT_CONVERGENCE_LIMIT,
                QuantumInspiredOptimizer.DEFAULT_DELTA_THETA
            );

            long qeaSeed = BASE_QEA_SEED + rep;
            CandidateSolution best = optimizer.optimize(qeaSeed);

            results.add(new Result(
                algoName, n, m, rep, stateSeed, "safe",
                optimizer.getExecutionTimeNs(),
                best.fitness,
                best.processesCompleted,
                best.totalWaitingTime,
                best.resourceWaste,
                best.isSafe,
                optimizer.getIterationsRun(),
                best.order
            ));
        }
        return results;
    }

    // ── Statistics & Display ──────────────────────────────────────────────────

    private void printQuickStats(String label, List<Result> results) {
        double[] times = results.stream().mapToDouble(r -> r.executionTimeNs / 1_000_000.0).toArray();
        double[] fitnesses = results.stream().mapToDouble(r -> r.fitnessValue).toArray();
        int safeCount = (int) results.stream().filter(r -> r.isSafe).count();

        System.out.printf(" mean_time=%.2f ms  mean_fitness=%.1f  safe=%d/%d%n",
            mean(times), mean(fitnesses), safeCount, results.size());
    }

    /**
     * Prints a comprehensive summary table of all benchmark results.
     */
    public void printSummaryTable() {
        System.out.println("╔═══════════════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                        BENCHMARK SUMMARY TABLE                                      ║");
        System.out.println("╠══════════╦═══════╦═══════╦══════════════════════╦═══════════════════╦════════╦════════╣");
        System.out.println("║ Algorithm║   n   ║   m   ║   Time (ms)          ║   Fitness         ║ Safe   ║ Iters  ║");
        System.out.println("║          ║       ║       ║ mean ± std [min,max] ║ mean ± std        ║ count  ║ mean   ║");
        System.out.println("╠══════════╬═══════╬═══════╬══════════════════════╬═══════════════════╬════════╬════════╣");

        for (int[] cfg : TestCaseGenerator.BENCHMARK_SIZES) {
            int n = cfg[0], m = cfg[1];
            for (String algo : new String[]{"Banker", "QEA", "Hybrid"}) {
                List<Result> subset = filterResults(algo, n);
                if (subset.isEmpty()) continue;

                double[] times = subset.stream().mapToDouble(r -> r.executionTimeNs / 1_000_000.0).toArray();
                double[] fits  = subset.stream().mapToDouble(r -> r.fitnessValue).toArray();
                double[] iters = subset.stream().mapToDouble(r -> r.iterations).toArray();
                int safeCount  = (int) subset.stream().filter(r -> r.isSafe).count();

                System.out.printf("║ %-8s ║ %5d ║ %5d ║ %6.2f ± %5.2f [%5.2f,%6.2f] ║ %7.1f ± %6.1f ║ %3d/%-3d║ %5.1f  ║%n",
                    algo, n, m,
                    mean(times), stdDev(times), min(times), max(times),
                    mean(fits), stdDev(fits),
                    safeCount, subset.size(),
                    mean(iters));
            }
        }

        System.out.println("╚══════════╩═══════╩═══════╩══════════════════════╩═══════════════════╩════════╩════════╝");

        // ── Fairness flags ────────────────────────────────────────────────
        System.out.println();
        checkFairnessFlags();
    }

    /**
     * Checks and reports fairness flags as required by the project spec.
     */
    private void checkFairnessFlags() {
        boolean bankerFasterThanQEA = false;
        boolean qeaNoImprovement = false;

        for (int[] cfg : TestCaseGenerator.BENCHMARK_SIZES) {
            int n = cfg[0];
            List<Result> bankerRuns = filterResults("Banker", n);
            List<Result> qeaRuns   = filterResults("QEA", n);

            if (!bankerRuns.isEmpty() && !qeaRuns.isEmpty()) {
                double bankerMeanTime = mean(bankerRuns.stream()
                    .mapToDouble(r -> r.executionTimeNs).toArray());
                double qeaMeanTime = mean(qeaRuns.stream()
                    .mapToDouble(r -> r.executionTimeNs).toArray());

                if (bankerMeanTime < qeaMeanTime) {
                    bankerFasterThanQEA = true;
                }

                // Check if QEA improved over Banker's fitness
                double bankerFit = mean(bankerRuns.stream()
                    .mapToDouble(r -> r.fitnessValue).toArray());
                double qeaFit = mean(qeaRuns.stream()
                    .mapToDouble(r -> r.fitnessValue).toArray());

                if (qeaFit >= bankerFit) {
                    qeaNoImprovement = true;
                }
            }
        }

        System.out.println("── Fairness Flags ──────────────────────────────────────────────");
        System.out.printf("  [%s] At least one size where Banker is faster than QEA%n",
            bankerFasterThanQEA ? "✓" : "✗");
        System.out.printf("  [%s] At least one size where QEA does not improve over Banker%n",
            qeaNoImprovement ? "✓" : "✗");

        if (!bankerFasterThanQEA) {
            System.out.println("  ⚠ WARNING: Banker was never faster than QEA — check for JIT/warmup artefacts.");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<Result> filterResults(String algorithm, int processCount) {
        List<Result> filtered = new ArrayList<>();
        for (Result r : allResults) {
            if (r.algorithm.equals(algorithm) && r.processCount == processCount) {
                filtered.add(r);
            }
        }
        return filtered;
    }

    public List<Result> getAllResults() {
        return new ArrayList<>(allResults);
    }

    private static double mean(double[] arr) {
        if (arr.length == 0) return 0.0;
        double sum = 0;
        for (double v : arr) sum += v;
        return sum / arr.length;
    }

    private static double stdDev(double[] arr) {
        if (arr.length <= 1) return 0.0;
        double m = mean(arr);
        double sumSq = 0;
        for (double v : arr) sumSq += (v - m) * (v - m);
        return Math.sqrt(sumSq / (arr.length - 1));
    }

    private static double min(double[] arr) {
        double m = Double.MAX_VALUE;
        for (double v : arr) if (v < m) m = v;
        return m;
    }

    private static double max(double[] arr) {
        double m = Double.MIN_VALUE;
        for (double v : arr) if (v > m) m = v;
        return m;
    }

    // ── Main entry point ──────────────────────────────────────────────────────

    /**
     * Standalone runner: execute the full benchmark suite.
     * Usage: java Benchmark
     */
    public static void main(String[] args) {
        Benchmark bench = new Benchmark();
        bench.runAll();

        System.out.printf("%nTotal results collected: %d%n", bench.getAllResults().size());
        System.out.println("Use PerformanceAnalyzer to export CSV/JSON for Python plotting.");
    }
}
