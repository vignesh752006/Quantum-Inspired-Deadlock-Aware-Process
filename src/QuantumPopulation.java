import java.util.Arrays;
import java.util.Comparator;
import java.util.Random;

/**
 * QuantumPopulation.java
 *
 * Manages a population of Q-bit individuals for the Quantum-Inspired
 * Evolutionary Algorithm (QEA / QIEA).
 *
 * In QEA:
 *   - Each individual represents a candidate process-execution ordering.
 *   - An individual consists of an array of n {@link QuantumBit}s (one per process).
 *   - The population has size {@code popSize} (default = 20).
 *   - Observation collapses qubits into classical bitstrings and maps them
 *     to process permutations.
 *   - Rotation updates qubit amplitudes toward the global-best observed bitstring.
 *
 * @author  OSDS-CP Group
 * @version 1.0  (Phase 1)
 */
public class QuantumPopulation {

    public static final int DEFAULT_POP_SIZE = 20;

    private final int popSize;
    private final int n;
    private final QuantumBit[][] qubits;

    /**
     * Constructs a QuantumPopulation with specified population size and process count.
     * All qubits are initialized to equal superposition (θ = π/4).
     *
     * @param popSize number of individuals in the population (e.g. 20)
     * @param n       number of processes (qubits per individual)
     */
    public QuantumPopulation(int popSize, int n) {
        if (popSize <= 0) throw new IllegalArgumentException("popSize must be positive: " + popSize);
        if (n <= 0)       throw new IllegalArgumentException("n must be positive: " + n);

        this.popSize = popSize;
        this.n       = n;
        this.qubits  = new QuantumBit[popSize][n];

        for (int k = 0; k < popSize; k++) {
            for (int i = 0; i < n; i++) {
                qubits[k][i] = new QuantumBit();
            }
        }
    }

    /** Convenience constructor using default population size of 20. */
    public QuantumPopulation(int n) {
        this(DEFAULT_POP_SIZE, n);
    }

    public int getPopSize() { return popSize; }
    public int getN()       { return n; }

    public QuantumBit getQubit(int individualIndex, int processIndex) {
        return qubits[individualIndex][processIndex];
    }

    /**
     * Resets all qubits across all individuals to equal superposition (θ = π/4).
     */
    public void reset() {
        for (int k = 0; k < popSize; k++) {
            for (int i = 0; i < n; i++) {
                qubits[k][i].reset();
            }
        }
    }

    /**
     * Observes the quantum population and generates an array of {@link CandidateSolution}s.
     *
     * For each individual:
     *   1. Each qubit i is observed to produce a classical bit (0 or 1).
     *   2. A priority key is computed for each process based on the observed bit,
     *      the qubit's continuous amplitude (favours bit 0 / "left"), and random jitter.
     *   3. Sorting processes by priority yields a valid permutation of [0..n-1].
     *
     * @param rng uniform random number generator
     * @return array of CandidateSolution objects of length popSize
     */
    public CandidateSolution[] observe(Random rng) {
        CandidateSolution[] solutions = new CandidateSolution[popSize];

        for (int k = 0; k < popSize; k++) {
            int[] bits = new int[n];
            Integer[] indices = new Integer[n];
            double[] priorityKeys = new double[n];

            for (int i = 0; i < n; i++) {
                bits[i] = qubits[k][i].observeBit(rng.nextDouble());
                indices[i] = i;
                // Bit 0 favours earlier execution (lower key).
                // α² provides continuous fine-grained priority from qubit state.
                // rng jitter ensures full exploration and tie-breaking at generation 0.
                priorityKeys[i] = (bits[i] * 100.0)
                                + (1.0 - qubits[k][i].getAlphaSquared())
                                + (rng.nextDouble() * 0.01);
            }

            // Sort process indices by priorityKey in ascending order
            Arrays.sort(indices, new Comparator<Integer>() {
                @Override
                public int compare(Integer a, Integer b) {
                    return Double.compare(priorityKeys[a], priorityKeys[b]);
                }
            });

            int[] order = new int[n];
            for (int i = 0; i < n; i++) {
                order[i] = indices[i];
            }

            solutions[k] = new CandidateSolution(order, bits);
        }

        return solutions;
    }

    /**
     * Updates qubit amplitudes of an individual using the Q-gate rotation table
     * toward the best-known solution.
     *
     * Rule (Han & Kim 2002):
     *   - If best bit = 0 and current bit = 1: rotate toward 0 (increase α²)
     *   - If best bit = 1 and current bit = 0: rotate toward 1 (increase β²)
     *   - If best bit == current bit: no rotation
     *
     * @param individualIndex index of individual to update
     * @param best            the global or generation best solution
     * @param current         the individual's current candidate solution
     * @param delta           rotation angle magnitude (e.g. 0.05π)
     */
    public void updateAmplitudes(int individualIndex, CandidateSolution best,
                                 CandidateSolution current, double delta) {
        for (int i = 0; i < n; i++) {
            int bBest = best.observedBits[i];
            int bCurr = current.observedBits[i];

            if (bBest == 0 && bCurr == 1) {
                qubits[individualIndex][i].rotateToBit0(delta);
            } else if (bBest == 1 && bCurr == 0) {
                qubits[individualIndex][i].rotateToBit1(delta);
            }
        }
    }

    /**
     * Updates qubit amplitudes for the entire population toward the best solution.
     *
     * @param best              the best solution found
     * @param currentPopulation current generation's evaluated solutions
     * @param delta             rotation angle magnitude
     */
    public void updateAmplitudes(CandidateSolution best,
                                 CandidateSolution[] currentPopulation, double delta) {
        for (int k = 0; k < popSize; k++) {
            updateAmplitudes(k, best, currentPopulation[k], delta);
        }
    }
}
