package io.github.vignesh752006.quantumdeadlock.quantum;

import io.github.vignesh752006.quantumdeadlock.banker.BankersAlgorithm;
import io.github.vignesh752006.quantumdeadlock.model.SystemState;
import io.github.vignesh752006.quantumdeadlock.util.Preconditions;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Scores a candidate execution order against a state. Lower is better:
 *
 * <pre>
 *   fitness = 1000 * deadlockPenalty
 *           +  100 * unfinishedProcesses
 *           +        totalWaitingTime
 *           +        resourceWaste
 * </pre>
 *
 * <p>The weights encode a lexicographic preference: never deadlock, then finish as
 * many processes as possible, then minimise waiting, then minimise idle resources.
 * A state where any process remains permanently blocked is penalised at 1000, which
 * dominates any combination of the smaller terms.</p>
 *
 * <h2>Hybrid mode</h2>
 * <p>With {@code hybrid} enabled the Banker's Algorithm becomes a <em>hard</em>
 * constraint rather than another soft term: an unsafe state, or an order that
 * deadlocks, receives {@link #PENALTY_FITNESS}. That confines the search to the
 * provably safe region instead of merely discouraging unsafe candidates.</p>
 */
public final class FitnessFunction {

    public static final double DEADLOCK_WEIGHT = 1000.0;
    public static final double UNFINISHED_WEIGHT = 100.0;
    public static final double WAIT_WEIGHT = 1.0;
    public static final double WASTE_WEIGHT = 1.0;

    /**
     * Fitness assigned to candidates rejected by the hybrid hard constraint.
     *
     * <p>Deliberately finite: {@code Double.MAX_VALUE} would overflow to
     * {@link Double#POSITIVE_INFINITY} as soon as any averaging or summation touched
     * it, poisoning the generation mean.</p>
     */
    public static final double PENALTY_FITNESS = Double.MAX_VALUE / 2.0;

    private final boolean hybrid;

    /** Standard QEA fitness: deadlock treated as a soft penalty. */
    public FitnessFunction() {
        this(false);
    }

    /**
     * @param hybrid true to enforce the Banker safety check as a hard constraint
     */
    public FitnessFunction(boolean hybrid) {
        this.hybrid = hybrid;
    }

    public boolean isHybrid() {
        return hybrid;
    }

    /**
     * Evaluates a candidate, recording all metrics on it.
     *
     * @param candidate the order to score
     * @param state     the state to score it against
     * @return the fitness value, lower being better
     */
    public double evaluate(CandidateSolution candidate, SystemState state) {
        Preconditions.requireNonNull(candidate, "candidate must not be null.");
        Preconditions.requireNonNull(state, "state must not be null.");
        if (candidate.processCount() != state.processCount()) {
            throw new IllegalArgumentException("Candidate process count "
                    + candidate.processCount() + " does not match state process count "
                    + state.processCount() + ".");
        }

        final int processCount = state.processCount();
        final int resourceCount = state.resourceCount();

        if (hybrid && !BankersAlgorithm.isSafe(state)) {
            candidate.applyRejection(PENALTY_FITNESS, state.totalResourceWaste(), processCount);
            return PENALTY_FITNESS;
        }

        int[] work = state.availableVector();
        int completed = 0;
        double totalWaitingTime = 0.0;

        Deque<Integer> queue = new ArrayDeque<>(processCount);
        for (int processIndex : candidate.order()) {
            queue.addLast(processIndex);
        }

        boolean progressed = true;
        while (!queue.isEmpty() && progressed) {
            progressed = false;
            int thisRound = queue.size();

            for (int k = 0; k < thisRound; k++) {
                int processIndex = queue.removeFirst();
                if (canAllocate(state, processIndex, work, resourceCount)) {
                    for (int j = 0; j < resourceCount; j++) {
                        work[j] += state.allocation(processIndex, j);
                    }
                    completed++;
                    progressed = true;
                } else {
                    totalWaitingTime += 1.0;
                    queue.addLast(processIndex);
                }
            }
        }

        int unfinished = processCount - completed;
        boolean safe = unfinished == 0;
        double deadlockPenalty = safe ? 0.0 : 1.0;

        double resourceWaste = 0.0;
        for (int j = 0; j < resourceCount; j++) {
            resourceWaste += work[j];
        }

        if (hybrid && !safe) {
            candidate.applyRejection(PENALTY_FITNESS, resourceWaste, processCount);
            return PENALTY_FITNESS;
        }

        double fitness = (DEADLOCK_WEIGHT * deadlockPenalty)
                + (UNFINISHED_WEIGHT * unfinished)
                + (WAIT_WEIGHT * totalWaitingTime)
                + (WASTE_WEIGHT * resourceWaste);

        candidate.applyEvaluation(fitness, safe, completed, totalWaitingTime, resourceWaste);
        return fitness;
    }

    /** True iff the process's remaining need fits entirely inside the work vector. */
    private static boolean canAllocate(SystemState state, int processIndex, int[] work, int resourceCount) {
        for (int j = 0; j < resourceCount; j++) {
            if (state.need(processIndex, j) > work[j]) {
                return false;
            }
        }
        return true;
    }
}
