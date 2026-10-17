import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * QuantumInspiredOptimizer.java
 *
 * Implements the Quantum-Inspired Evolutionary Algorithm (QEA / QIEA)
 * for searching optimal process-execution schedules.
 *
 * Algorithm Workflow:
 *   1. Initialize population of Q-bit individuals at equal superposition (θ = π/4).
 *   2. For each iteration (up to maxIter):
 *        a. Observe population → collapse qubits to classical candidate permutations.
 *        b. Evaluate candidate solutions using the multi-objective {@link FitnessFunction}.
 *        c. Select generation best; update global best if improved.
 *        d. Record iteration statistics (best & average fitness).
 *        e. Rotate qubits toward global best via Q-gate rotation table (Δθ).
 *        f. Check convergence: terminate early if stagnant for convergenceLimit iterations.
 *   3. Return global best {@link CandidateSolution}.
 *
 * Phase-2 Extension:
 *   Can override {@link #onIterationEnd} for real-time VM telemetry or streaming metrics.
 *
 * @author  OSDS-CP Group
 * @version 1.0  (Phase 1)
 */
public class QuantumInspiredOptimizer {

    // ── Default Hyperparameters ───────────────────────────────────────────────
    public static final int    DEFAULT_POP_SIZE          = 20;
    public static final int    DEFAULT_MAX_ITER          = 100;
    public static final int    DEFAULT_CONVERGENCE_LIMIT = 20;
    public static final double DEFAULT_DELTA_THETA       = 0.05 * Math.PI;

    // ── Configuration ─────────────────────────────────────────────────────────
    private final SystemState     state;
    private final FitnessFunction fitnessFunction;
    private final int             popSize;
    private final int             maxIter;
    private final int             convergenceLimit;
    private final double          deltaTheta;

    // ── Execution State & Results ─────────────────────────────────────────────
    private final QuantumPopulation population;
    private CandidateSolution       globalBest;
    private final List<Double>      bestFitnessHistory = new ArrayList<>();
    private final List<Double>      avgFitnessHistory  = new ArrayList<>();
    private int                     iterationsRun      = 0;
    private long                    executionTimeNs    = 0;

    /**
     * Constructs a QEA optimizer with full parameter specification.
     */
    public QuantumInspiredOptimizer(SystemState state,
                                    FitnessFunction fitnessFunction,
                                    int popSize,
                                    int maxIter,
                                    int convergenceLimit,
                                    double deltaTheta) {
        if (state == null)           throw new IllegalArgumentException("state must not be null");
        if (fitnessFunction == null) throw new IllegalArgumentException("fitnessFunction must not be null");
        if (popSize <= 0)            throw new IllegalArgumentException("popSize must be positive: " + popSize);
        if (maxIter <= 0)            throw new IllegalArgumentException("maxIter must be positive: " + maxIter);
        if (convergenceLimit <= 0)   throw new IllegalArgumentException("convergenceLimit must be positive");

        this.state            = state;
        this.fitnessFunction  = fitnessFunction;
        this.popSize          = popSize;
        this.maxIter          = maxIter;
        this.convergenceLimit = convergenceLimit;
        this.deltaTheta       = deltaTheta;
        this.population       = new QuantumPopulation(popSize, state.getNumProcesses());
    }

    /**
     * Constructs a QEA optimizer using confirmed default parameters:
     * popSize=20, maxIter=100, convergenceLimit=20, deltaTheta=0.05π.
     */
    public QuantumInspiredOptimizer(SystemState state, FitnessFunction fitnessFunction) {
        this(state, fitnessFunction,
             DEFAULT_POP_SIZE, DEFAULT_MAX_ITER, DEFAULT_CONVERGENCE_LIMIT, DEFAULT_DELTA_THETA);
    }

    // ── Optimization Loop ─────────────────────────────────────────────────────

    /**
     * Executes the QEA optimization using a seeded PRNG for repeatability.
     */
    public CandidateSolution optimize(long seed) {
        return optimize(new Random(seed));
    }

    /**
     * Executes the QEA optimization loop.
     *
     * @param rng random number generator
     * @return the best {@link CandidateSolution} found
     */
    public CandidateSolution optimize(Random rng) {
        long startNs = System.nanoTime();

        population.reset();
        bestFitnessHistory.clear();
        avgFitnessHistory.clear();
        globalBest = null;
        iterationsRun = 0;

        int stagnation = 0;

        for (int iter = 0; iter < maxIter; iter++) {
            iterationsRun++;

            // 1. Observe population → generate candidate solutions
            CandidateSolution[] candidates = population.observe(rng);

            // 2. Evaluate each candidate
            CandidateSolution genBest = null;
            double sumFitness = 0.0;

            for (CandidateSolution cand : candidates) {
                fitnessFunction.evaluate(cand, state);
                sumFitness += cand.fitness;

                if (genBest == null || cand.isBetterThan(genBest)) {
                    genBest = cand;
                }
            }

            double avgFitness = sumFitness / popSize;

            // 3. Update global best and track stagnation
            if (globalBest == null || genBest.isBetterThan(globalBest)) {
                globalBest = genBest.deepCopy();
                stagnation = 0;
            } else {
                stagnation++;
            }

            bestFitnessHistory.add(globalBest.fitness);
            avgFitnessHistory.add(avgFitness);

            // Hook for external monitoring / Phase 2 telemetry
            onIterationEnd(iter, genBest, globalBest);

            // 4. Update Q-bit amplitudes toward global best
            population.updateAmplitudes(globalBest, candidates, deltaTheta);

            // 5. Early stopping on convergence stagnation
            if (stagnation >= convergenceLimit) {
                break;
            }
        }

        executionTimeNs = System.nanoTime() - startNs;
        return globalBest;
    }

    // ── Phase-2 Extension Hook ────────────────────────────────────────────────

    /**
     * Called at the conclusion of each generation iteration.
     * Subclasses or Phase 2 extensions can override to stream live metrics.
     */
    protected void onIterationEnd(int iter, CandidateSolution genBest, CandidateSolution globalBest) {
        // Default no-op
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public CandidateSolution getGlobalBest()           { return globalBest; }
    public List<Double>      getBestFitnessHistory()   { return new ArrayList<>(bestFitnessHistory); }
    public List<Double>      getAvgFitnessHistory()    { return new ArrayList<>(avgFitnessHistory); }
    public int               getIterationsRun()        { return iterationsRun; }
    public long              getExecutionTimeNs()      { return executionTimeNs; }
    public QuantumPopulation getPopulation()           { return population; }
    public SystemState       getState()                { return state; }
    public FitnessFunction   getFitnessFunction()      { return fitnessFunction; }

    // ── Display ───────────────────────────────────────────────────────────────

    public void printReport() {
        System.out.println("=== Quantum-Inspired Optimization Report ===");
        System.out.printf("State: %s (n=%d, m=%d)%n",
            state.getLabel(), state.getNumProcesses(), state.getNumResources());
        System.out.printf("Mode: %s%n", fitnessFunction.isHybrid() ? "Banker + QEA Hybrid" : "QEA Alone");
        System.out.printf("Iterations run: %d / %d (Stagnation limit: %d)%n",
            iterationsRun, maxIter, convergenceLimit);
        System.out.printf("Elapsed time:   %,d ns (%.3f ms)%n",
            executionTimeNs, executionTimeNs / 1_000_000.0);

        if (globalBest != null) {
            System.out.printf("Best Fitness:   %.2f (Safe: %b, Completed: %d/%d, Wait: %.1f, Waste: %.1f)%n",
                globalBest.fitness, globalBest.isSafe, globalBest.processesCompleted,
                state.getNumProcesses(), globalBest.totalWaitingTime, globalBest.resourceWaste);
        } else {
            System.out.println("No solution found.");
        }
    }
}
