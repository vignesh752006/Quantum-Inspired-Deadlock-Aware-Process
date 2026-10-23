package io.github.vignesh752006.quantumdeadlock.quantum;

import io.github.vignesh752006.quantumdeadlock.util.Matrices;
import io.github.vignesh752006.quantumdeadlock.util.Preconditions;

/**
 * One candidate process-execution order produced by the QEA.
 *
 * <p>A candidate is a permutation of process indices: {@code order[0]} is
 * attempted first, {@code order[n-1]} last. The evolutionary search looks for the
 * permutation that minimises the fitness value.</p>
 *
 * <p>Evaluation metrics are set by the fitness function after construction, which
 * is why they are mutable while the permutation itself is final. The previous
 * version exposed them as public fields; they are now written through
 * {@link #applyEvaluation} so an unevaluated candidate is distinguishable from one
 * that scored badly.</p>
 */
public final class CandidateSolution implements Comparable<CandidateSolution> {

    private final int[] order;
    private final int[] observedBits;
    private final int processCount;

    private double fitness = Double.POSITIVE_INFINITY;
    private boolean safe;
    private int processesCompleted;
    private double totalWaitingTime;
    private double resourceWaste;
    private boolean evaluated;

    /**
     * @param order        execution-order permutation, all distinct indices in {@code [0, n)}
     * @param observedBits the qubit observations that produced this order; may be null
     * @throws IllegalArgumentException if the order is null, empty, or not a permutation
     */
    public CandidateSolution(int[] order, int[] observedBits) {
        Preconditions.requireNonNull(order, "CandidateSolution: order must not be null.");
        if (order.length == 0) {
            throw new IllegalArgumentException("CandidateSolution: order must not be empty.");
        }

        boolean[] seen = new boolean[order.length];
        for (int processIndex : order) {
            if (processIndex < 0 || processIndex >= order.length) {
                throw new IllegalArgumentException("CandidateSolution: order entry " + processIndex
                        + " is outside [0, " + (order.length - 1) + "].");
            }
            if (seen[processIndex]) {
                throw new IllegalArgumentException("CandidateSolution: duplicate process index "
                        + processIndex + " in order " + Matrices.format(order) + ".");
            }
            seen[processIndex] = true;
        }

        this.processCount = order.length;
        this.order = order.clone();
        this.observedBits = (observedBits != null && observedBits.length == order.length)
                ? observedBits.clone()
                : new int[order.length];
    }

    /** @return a copy of the execution-order permutation */
    public int[] order() {
        return order.clone();
    }

    /** @return a copy of the raw qubit observations behind this order */
    public int[] observedBits() {
        return observedBits.clone();
    }

    public int processCount() {
        return processCount;
    }

    public double fitness() {
        return fitness;
    }

    public boolean isSafe() {
        return safe;
    }

    public int processesCompleted() {
        return processesCompleted;
    }

    public double totalWaitingTime() {
        return totalWaitingTime;
    }

    public double resourceWaste() {
        return resourceWaste;
    }

    /** @return true once {@link #applyEvaluation} has been called */
    public boolean isEvaluated() {
        return evaluated;
    }

    /** Records a successful evaluation. */
    public void applyEvaluation(double fitness, boolean safe, int processesCompleted,
                                double totalWaitingTime, double resourceWaste) {
        this.fitness = fitness;
        this.safe = safe;
        this.processesCompleted = processesCompleted;
        this.totalWaitingTime = totalWaitingTime;
        this.resourceWaste = resourceWaste;
        this.evaluated = true;
    }

    /**
     * Records a rejection by the hybrid hard constraint.
     *
     * @param penaltyFitness the penalty value assigned to unsafe candidates
     * @param fallbackWaste  resource waste measured for the rejected candidate
     * @param processCount   number of processes in the state
     */
    public void applyRejection(double penaltyFitness, double fallbackWaste, int processCount) {
        applyEvaluation(penaltyFitness, false, 0, processCount * 100.0, fallbackWaste);
    }

    @Override
    public int compareTo(CandidateSolution other) {
        return Double.compare(this.fitness, other.fitness);
    }

    /** @return true iff this candidate's fitness is strictly lower */
    public boolean isBetterThan(CandidateSolution other) {
        return other != null && this.fitness < other.fitness;
    }

    /** @return a deep copy including evaluation metrics */
    public CandidateSolution deepCopy() {
        CandidateSolution copy = new CandidateSolution(order, observedBits);
        copy.fitness = this.fitness;
        copy.safe = this.safe;
        copy.processesCompleted = this.processesCompleted;
        copy.totalWaitingTime = this.totalWaitingTime;
        copy.resourceWaste = this.resourceWaste;
        copy.evaluated = this.evaluated;
        return copy;
    }

    @Override
    public String toString() {
        return String.format("CandidateSolution{order=%s, fitness=%.2f, safe=%b, completed=%d}",
                Matrices.format(order), fitness, safe, processesCompleted);
    }
}
