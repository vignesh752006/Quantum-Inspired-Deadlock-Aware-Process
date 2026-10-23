package io.github.vignesh752006.quantumdeadlock.quantum;

import io.github.vignesh752006.quantumdeadlock.util.Preconditions;

import java.util.Arrays;
import java.util.Random;

/**
 * A population of Q-bit individuals for the Quantum-Inspired Evolutionary
 * Algorithm.
 *
 * <p>Each individual is an array of {@code n} {@link QuantumBit}s, one per process.
 * Observing an individual collapses its qubits into a bitstring, which is then
 * converted into a process permutation; rotating the qubits toward the best-known
 * bitstring is what performs the search.</p>
 */
public final class QuantumPopulation {

    public static final int DEFAULT_POP_SIZE = 20;

    private final int populationSize;
    private final int processCount;
    private final QuantumBit[][] qubits;

    /**
     * @param populationSize number of individuals, must be positive
     * @param processCount   qubits per individual, must be positive
     */
    public QuantumPopulation(int populationSize, int processCount) {
        Preconditions.requirePositive(populationSize, "populationSize");
        Preconditions.requirePositive(processCount, "processCount");

        this.populationSize = populationSize;
        this.processCount = processCount;
        this.qubits = new QuantumBit[populationSize][processCount];
        for (int individual = 0; individual < populationSize; individual++) {
            for (int process = 0; process < processCount; process++) {
                qubits[individual][process] = new QuantumBit();
            }
        }
    }

    /** Creates a population of {@link #DEFAULT_POP_SIZE} individuals. */
    public QuantumPopulation(int processCount) {
        this(DEFAULT_POP_SIZE, processCount);
    }

    public int populationSize() {
        return populationSize;
    }

    public int processCount() {
        return processCount;
    }

    public QuantumBit qubit(int individualIndex, int processIndex) {
        return qubits[individualIndex][processIndex];
    }

    /** Resets every qubit to the equal superposition. */
    public void reset() {
        for (QuantumBit[] individual : qubits) {
            for (QuantumBit qubit : individual) {
                qubit.reset();
            }
        }
    }

    /**
     * Observes every individual and converts it into a candidate ordering.
     *
     * <p>Each process gets a priority key built from three parts: the observed bit
     * (dominant, scaled by 100), the continuous {@code 1 - alpha^2} term that keeps
     * gradations finer than a single bit, and a small uniform jitter that breaks ties
     * in generation zero where every qubit is identical. Sorting by that key yields
     * a permutation.</p>
     *
     * @param random source of randomness; must not be null
     * @return one candidate per individual
     */
    public CandidateSolution[] observe(Random random) {
        Preconditions.requireNonNull(random, "random must not be null.");

        CandidateSolution[] candidates = new CandidateSolution[populationSize];
        Integer[] indices = new Integer[processCount];
        double[] priorityKeys = new double[processCount];

        for (int individual = 0; individual < populationSize; individual++) {
            int[] bits = new int[processCount];
            for (int process = 0; process < processCount; process++) {
                QuantumBit qubit = qubits[individual][process];
                bits[process] = qubit.observeBit(random.nextDouble());
                indices[process] = process;
                priorityKeys[process] = (bits[process] * 100.0)
                        + (1.0 - qubit.alphaSquared())
                        + (random.nextDouble() * 0.01);
            }

            Arrays.sort(indices, (a, b) -> Double.compare(priorityKeys[a], priorityKeys[b]));

            int[] order = new int[processCount];
            for (int i = 0; i < processCount; i++) {
                order[i] = indices[i];
            }
            candidates[individual] = new CandidateSolution(order, bits);
        }
        return candidates;
    }

    /**
     * Rotates every individual's qubits toward a target bitstring.
     *
     * <p>Each individual is rotated against its <em>own</em> observed bits, which is
     * the standard Q-gate rule: a qubit moves in the direction that increases the
     * amplitude of the target value. Rotating against a shared "current" candidate
     * instead would silently ignore every individual but that one.</p>
     *
     * @param targetBitstring the bitstring to steer toward
     * @param generation      this generation's observed candidates
     * @param delta           rotation magnitude in radians
     */
    public void rotateToward(int[] targetBitstring, CandidateSolution[] generation, double delta) {
        Preconditions.requireNonNull(targetBitstring, "targetBitstring must not be null.");
        Preconditions.requireNonNull(generation, "generation must not be null.");
        if (targetBitstring.length != processCount) {
            throw new IllegalArgumentException("targetBitstring length " + targetBitstring.length
                    + " != processCount " + processCount + ".");
        }
        if (generation.length < populationSize) {
            throw new IllegalArgumentException("generation length " + generation.length
                    + " < populationSize " + populationSize + ".");
        }

        for (int individual = 0; individual < populationSize; individual++) {
            int[] ownBits = generation[individual].observedBits();
            for (int process = 0; process < processCount; process++) {
                int target = targetBitstring[process];
                if (target == ownBits[process]) {
                    continue;
                }
                if (target == 0) {
                    qubits[individual][process].rotateToBit0(delta);
                } else {
                    qubits[individual][process].rotateToBit1(delta);
                }
            }
        }
    }
}
