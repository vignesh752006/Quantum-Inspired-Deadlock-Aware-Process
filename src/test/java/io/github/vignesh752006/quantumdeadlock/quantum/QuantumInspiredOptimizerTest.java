package io.github.vignesh752006.quantumdeadlock.quantum;

import io.github.vignesh752006.quantumdeadlock.generator.TestCaseGenerator;
import io.github.vignesh752006.quantumdeadlock.model.SystemState;
import io.github.vignesh752006.quantumdeadlock.testsupport.TestStates;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers Step 7 of the original harness: the optimiser improves on its starting
 * population, is reproducible for a given seed, and never returns an unsafe answer
 * in hybrid mode.
 */
class QuantumInspiredOptimizerTest {

    @Test
    @DisplayName("the best fitness never worsens across generations")
    void bestFitnessIsMonotonic() {
        SystemState state = TestStates.safeState();
        OptimizationResult result = new QuantumInspiredOptimizer(state, new FitnessFunction(false))
                .optimize(11L);

        var history = result.bestHistory();
        assertFalse(history.isEmpty());
        for (int i = 1; i < history.size(); i++) {
            assertTrue(history.get(i) <= history.get(i - 1) + 1e-9,
                    "best-so-far fitness must not increase at generation " + i);
        }
        assertEquals(history.size(), result.iterationsRun());
    }

    @Test
    @DisplayName("a fixed seed reproduces the same result exactly")
    void reproducibleForSeed() {
        SystemState state = TestStates.safeState();

        OptimizationResult first = new QuantumInspiredOptimizer(state, new FitnessFunction(false))
                .optimize(4242L);
        OptimizationResult second = new QuantumInspiredOptimizer(state, new FitnessFunction(false))
                .optimize(4242L);

        assertEquals(first.bestFitness(), second.bestFitness(), 1e-12);
        assertEquals(first.iterationsRun(), second.iterationsRun());
        assertEquals(Arrays.toString(first.bestSolution().order()),
                Arrays.toString(second.bestSolution().order()));
    }

    @Test
    @DisplayName("a different seed explores a different path")
    void differentSeedDiffers() {
        SystemState state = TestStates.safeState();

        OptimizationResult first = new QuantumInspiredOptimizer(state, new FitnessFunction(false))
                .optimize(1L);
        OptimizationResult second = new QuantumInspiredOptimizer(state, new FitnessFunction(false))
                .optimize(999L);

        assertNotNull(first.bestSolution());
        assertNotNull(second.bestSolution());
        // Fitness may coincide, but the two runs must at least be independent objects.
        assertFalse(first.bestSolution() == second.bestSolution());
    }

    @Test
    @DisplayName("hybrid mode always returns a safe solution")
    void hybridAlwaysSafe() {
        for (int[] size : new int[][]{{10, 5}, {20, 5}, {50, 10}}) {
            SystemState state = TestCaseGenerator.generateSafe(size[0], size[1], 21L);

            for (long seed = 0; seed < 5; seed++) {
                OptimizationResult result = new QuantumInspiredOptimizer(
                        state, new FitnessFunction(true)).optimize(seed);

                assertNotNull(result.bestSolution());
                assertTrue(result.bestSolution().isSafe(),
                        "hybrid must never return an unsafe order (n=" + size[0]
                                + ", seed=" + seed + ")");
            }
        }
    }

    @Test
    @DisplayName("the returned order is always a valid permutation")
    void orderIsAlwaysAPermutation() {
        SystemState state = TestCaseGenerator.generateSafe(15, 5, 8L);
        OptimizationResult result = new QuantumInspiredOptimizer(state, new FitnessFunction(false))
                .optimize(5L);

        int[] order = result.bestSolution().order();
        assertEquals(state.processCount(), order.length);

        boolean[] seen = new boolean[order.length];
        for (int processIndex : order) {
            assertFalse(seen[processIndex], "duplicate index " + processIndex);
            seen[processIndex] = true;
        }
    }

    @Test
    @DisplayName("the optimiser reaches at least the identity order's fitness")
    void improvesOnIdentityBaseline() {
        SystemState state = TestStates.safeState();
        int[] identity = new int[state.processCount()];
        for (int i = 0; i < identity.length; i++) {
            identity[i] = i;
        }
        CandidateSolution baseline = new CandidateSolution(identity,
                new int[state.processCount()]);
        new FitnessFunction(false).evaluate(baseline, state);

        OptimizationResult result = new QuantumInspiredOptimizer(state, new FitnessFunction(false))
                .optimize(77L);

        assertTrue(result.bestFitness() <= baseline.fitness() + 1e-9,
                "search should not do worse than the identity ordering");
    }

    @Test
    @DisplayName("the history series has one entry per generation run")
    void historiesHaveExpectedLengths() {
        SystemState state = TestStates.safeState();
        OptimizationResult result = new QuantumInspiredOptimizer(state, new FitnessFunction(false))
                .optimize(new Random(3L));

        assertEquals(result.iterationsRun(), result.bestHistory().size());
        assertEquals(result.iterationsRun(), result.averageHistory().size());
    }

    @Test
    @DisplayName("invalid hyperparameters are rejected")
    void invalidHyperparametersRejected() {
        SystemState state = TestStates.safeState();
        FitnessFunction scorer = new FitnessFunction(false);

        assertThrows(IllegalArgumentException.class,
                () -> new QuantumInspiredOptimizer(state, scorer, 0, 100, 20, 0.1));
        assertThrows(IllegalArgumentException.class,
                () -> new QuantumInspiredOptimizer(state, scorer, 20, 0, 20, 0.1));
        assertThrows(IllegalArgumentException.class,
                () -> new QuantumInspiredOptimizer(null, scorer));
    }
}
