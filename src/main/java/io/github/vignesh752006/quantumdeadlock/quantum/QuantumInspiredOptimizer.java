package io.github.vignesh752006.quantumdeadlock.quantum;

import io.github.vignesh752006.quantumdeadlock.model.SystemState;
import io.github.vignesh752006.quantumdeadlock.util.Preconditions;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Quantum-Inspired Evolutionary Algorithm searching for a low-fitness
 * process-execution order.
 *
 * <p>Per generation:</p>
 * <ol>
 *   <li>observe the population, collapsing qubits into candidate permutations</li>
 *   <li>score every candidate with the {@link FitnessFunction}</li>
 *   <li>update the global best and track stagnation</li>
 *   <li>rotate the qubits toward the global best's bitstring</li>
 *   <li>stop early once fitness has not improved for {@code convergenceLimit} generations</li>
 * </ol>
 *
 * <p>This is a classical algorithm that borrows the Q-bit formalism; it makes no
 * claim of quantum speedup. The search is stochastic, so every run takes an explicit
 * seed to make results reproducible.</p>
 *
 * <p>Instances are stateless with respect to a run: {@link #optimize(long)} builds a
 * fresh population each time, so the same instance can be run repeatedly.</p>
 */
public final class QuantumInspiredOptimizer {

    public static final int DEFAULT_POPULATION_SIZE = 20;
    public static final int DEFAULT_MAX_ITERATIONS = 100;
    public static final int DEFAULT_CONVERGENCE_LIMIT = 20;
    public static final double DEFAULT_DELTA_THETA = 0.05 * Math.PI;

    private final SystemState state;
    private final FitnessFunction fitnessFunction;
    private final int populationSize;
    private final int maxIterations;
    private final int convergenceLimit;
    private final double deltaTheta;
    private final IterationListener iterationListener;

    /**
     * Optional per-generation callback, for telemetry or streaming metrics.
     */
    @FunctionalInterface
    public interface IterationListener {
        /**
         * @param iteration      zero-based generation index
         * @param generationBest best candidate of this generation
         * @param globalBest     best candidate seen so far
         */
        void onIterationEnd(int iteration, CandidateSolution generationBest,
                            CandidateSolution globalBest);
    }

    /**
     * @param state            the state to optimise against
     * @param fitnessFunction  scorer to apply
     * @param populationSize   individuals per generation, positive
     * @param maxIterations    generation cap, positive
     * @param convergenceLimit generations without improvement before stopping, positive
     * @param deltaTheta       qubit rotation magnitude in radians, in {@code (0, pi/2]}
     */
    public QuantumInspiredOptimizer(SystemState state, FitnessFunction fitnessFunction,
                                    int populationSize, int maxIterations,
                                    int convergenceLimit, double deltaTheta) {
        this(state, fitnessFunction, populationSize, maxIterations, convergenceLimit,
                deltaTheta, null);
    }

    /**
     * Full constructor including the optional iteration listener.
     *
     * @param iterationListener callback invoked at the end of each generation; may be null
     */
    public QuantumInspiredOptimizer(SystemState state, FitnessFunction fitnessFunction,
                                    int populationSize, int maxIterations,
                                    int convergenceLimit, double deltaTheta,
                                    IterationListener iterationListener) {
        this.state = Preconditions.requireNonNull(state, "state must not be null.");
        this.fitnessFunction = Preconditions.requireNonNull(fitnessFunction,
                "fitnessFunction must not be null.");
        this.populationSize = Preconditions.requirePositive(populationSize, "populationSize");
        this.maxIterations = Preconditions.requirePositive(maxIterations, "maxIterations");
        this.convergenceLimit = Preconditions.requirePositive(convergenceLimit, "convergenceLimit");
        if (!Double.isFinite(deltaTheta) || deltaTheta <= 0.0) {
            throw new IllegalArgumentException(
                    "deltaTheta must be finite and positive, got " + deltaTheta + ".");
        }
        this.deltaTheta = deltaTheta;
        this.iterationListener = iterationListener;
    }

    /** Builds an optimizer with the documented default hyperparameters. */
    public QuantumInspiredOptimizer(SystemState state, FitnessFunction fitnessFunction) {
        this(state, fitnessFunction, DEFAULT_POPULATION_SIZE, DEFAULT_MAX_ITERATIONS,
                DEFAULT_CONVERGENCE_LIMIT, DEFAULT_DELTA_THETA);
    }

    /**
     * Runs the search with a reproducible seed.
     *
     * @param seed RNG seed
     * @return the run summary
     */
    public OptimizationResult optimize(long seed) {
        return optimize(new Random(seed));
    }

    /**
     * Runs the search.
     *
     * @param random source of randomness; must not be null
     * @return the run summary, including the best candidate found
     */
    public OptimizationResult optimize(Random random) {
        Preconditions.requireNonNull(random, "random must not be null.");

        long startNs = System.nanoTime();
        int processCount = state.processCount();
        QuantumPopulation population = new QuantumPopulation(populationSize, processCount);

        List<Double> bestHistory = new ArrayList<>(maxIterations);
        List<Double> averageHistory = new ArrayList<>(maxIterations);

        CandidateSolution globalBest = null;
        int iterationsRun = 0;
        int stagnation = 0;
        boolean converged = false;

        for (int iteration = 0; iteration < maxIterations; iteration++) {
            iterationsRun++;

            CandidateSolution[] generation = population.observe(random);
            CandidateSolution generationBest = null;
            double fitnessSum = 0.0;

            for (CandidateSolution candidate : generation) {
                fitnessFunction.evaluate(candidate, state);
                fitnessSum += candidate.fitness();
                if (generationBest == null || candidate.isBetterThan(generationBest)) {
                    generationBest = candidate;
                }
            }

            averageHistory.add(fitnessSum / populationSize);

            if (globalBest == null || generationBest.isBetterThan(globalBest)) {
                globalBest = generationBest.deepCopy();
                stagnation = 0;
            } else {
                stagnation++;
            }

            bestHistory.add(globalBest.fitness());
            if (iterationListener != null) {
                iterationListener.onIterationEnd(iteration, generationBest, globalBest);
            }

            // Rotate against the incumbent's bitstring. In the first generation the
            // incumbent IS this generation's best, so its bits are the honest target.
            population.rotateToward(globalBest.observedBits(), generation, deltaTheta);

            if (stagnation >= convergenceLimit) {
                converged = true;
                break;
            }
        }

        long elapsedNs = System.nanoTime() - startNs;
        double bestFitness = (globalBest == null) ? Double.POSITIVE_INFINITY : globalBest.fitness();
        return new OptimizationResult(globalBest, bestFitness, bestHistory, averageHistory,
                iterationsRun, converged, elapsedNs);
    }

    public SystemState state() {
        return state;
    }

    public FitnessFunction fitnessFunction() {
        return fitnessFunction;
    }
}
