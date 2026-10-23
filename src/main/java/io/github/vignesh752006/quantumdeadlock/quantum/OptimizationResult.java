package io.github.vignesh752006.quantumdeadlock.quantum;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable summary of one QEA run.
 *
 * <p>Returning a result object instead of leaving the caller to read mutable
 * getters off the optimizer means a run's outcome cannot be invalidated by a
 * subsequent run on the same instance.</p>
 *
 * @param bestSolution    the best candidate found
 * @param bestFitness     convenience copy of {@code bestSolution.fitness()}
 * @param bestHistory     best-so-far fitness per generation
 * @param averageHistory  population mean fitness per generation
 * @param iterationsRun   generations actually executed (may be fewer than the cap)
 * @param converged       true when the stagnation limit stopped the search early
 * @param executionTimeNs wall-clock duration of the run
 */
public record OptimizationResult(CandidateSolution bestSolution,
                                 double bestFitness,
                                 List<Double> bestHistory,
                                 List<Double> averageHistory,
                                 int iterationsRun,
                                 boolean converged,
                                 long executionTimeNs) {

    public OptimizationResult {
        bestHistory = bestHistory == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(bestHistory));
        averageHistory = averageHistory == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(averageHistory));
    }
}
