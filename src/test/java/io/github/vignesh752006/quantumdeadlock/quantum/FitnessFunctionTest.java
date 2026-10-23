package io.github.vignesh752006.quantumdeadlock.quantum;

import io.github.vignesh752006.quantumdeadlock.generator.TestCaseGenerator;
import io.github.vignesh752006.quantumdeadlock.model.SystemState;
import io.github.vignesh752006.quantumdeadlock.testsupport.TestStates;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers Step 6's scoring component: the term weights, the hybrid hard constraint,
 * and the finiteness of the penalty value.
 */
class FitnessFunctionTest {

    @Test
    @DisplayName("a completing order scores far below a deadlocking one")
    void completingOrderBeatsDeadlockingOrder() {
        FitnessFunction scorer = new FitnessFunction(false);

        CandidateSolution completing = new CandidateSolution(new int[]{1, 0, 2}, new int[]{0, 0, 0});
        double completingFitness = scorer.evaluate(completing, TestStates.safeState());

        CandidateSolution deadlocking = new CandidateSolution(new int[]{0, 1}, new int[]{0, 0});
        double deadlockingFitness = scorer.evaluate(deadlocking, TestStates.unsafeRing());

        assertTrue(completing.isSafe());
        assertFalse(deadlocking.isSafe());
        assertTrue(completingFitness < deadlockingFitness,
                "safe order (" + completingFitness + ") must beat deadlocking order ("
                        + deadlockingFitness + ")");
    }

    @Test
    @DisplayName("a completing order costs less than the deadlock weight")
    void completingOrderPaysNoDeadlockPenalty() {
        SystemState state = TestStates.safeState();
        CandidateSolution completing = new CandidateSolution(new int[]{1, 0, 2}, new int[]{0, 0, 0});

        double fitness = new FitnessFunction(false).evaluate(completing, state);

        assertTrue(completing.isSafe());
        assertEquals(state.processCount(), completing.processesCompleted(),
                "the fixture's safe order should complete every process");
        assertTrue(fitness < FitnessFunction.DEADLOCK_WEIGHT,
                "no deadlock penalty should be charged, got " + fitness);
    }

    @Test
    @DisplayName("the deadlock penalty dominates any other term")
    void deadlockPenaltyDominates() {
        SystemState unsafe = TestStates.unsafeRing();
        CandidateSolution deadlocking = new CandidateSolution(new int[]{0, 1}, new int[]{0, 0});

        new FitnessFunction(false).evaluate(deadlocking, unsafe);

        assertFalse(deadlocking.isSafe());
        assertTrue(deadlocking.fitness() >= FitnessFunction.DEADLOCK_WEIGHT,
                "a deadlocking order must cost at least the deadlock weight, got "
                        + deadlocking.fitness());
    }

    @Test
    @DisplayName("the hybrid rejects an unsafe state outright")
    void hybridRejectsUnsafeState() {
        SystemState unsafe = TestStates.unsafeRing();
        CandidateSolution candidate = new CandidateSolution(new int[]{0, 1}, new int[]{0, 0});

        double fitness = new FitnessFunction(true).evaluate(candidate, unsafe);

        assertEquals(FitnessFunction.PENALTY_FITNESS, fitness, 1e-9);
        assertFalse(candidate.isSafe());
    }

    @Test
    @DisplayName("the penalty is finite, so averaging cannot overflow to infinity")
    void penaltyIsFinite() {
        assertTrue(Double.isFinite(FitnessFunction.PENALTY_FITNESS),
                "PENALTY_FITNESS must be finite");
        assertTrue(FitnessFunction.PENALTY_FITNESS < Double.POSITIVE_INFINITY);
    }

    @Test
    @DisplayName("hybrid and plain modes agree on a safe state")
    void modesAgreeOnSafeState() {
        SystemState state = TestStates.safeState();
        int[] order = {1, 0, 2};

        CandidateSolution plain = new CandidateSolution(order, new int[]{0, 0, 0});
        CandidateSolution hybrid = new CandidateSolution(order, new int[]{0, 0, 0});

        double plainFitness = new FitnessFunction(false).evaluate(plain, state);
        double hybridFitness = new FitnessFunction(true).evaluate(hybrid, state);

        assertEquals(plainFitness, hybridFitness, 1e-9,
                "on a safe state the hard constraint should not change the score");
    }

    @Test
    @DisplayName("evaluation records every metric on the candidate")
    void evaluationRecordsMetrics() {
        SystemState state = TestStates.safeState();
        CandidateSolution candidate = new CandidateSolution(new int[]{1, 0, 2}, new int[]{0, 0, 0});

        assertFalse(candidate.isEvaluated(), "fresh candidate must be unevaluated");
        new FitnessFunction(false).evaluate(candidate, state);

        assertTrue(candidate.isEvaluated());
        assertTrue(candidate.processesCompleted() >= 0);
        assertTrue(candidate.resourceWaste() >= 0.0);
        assertTrue(Double.isFinite(candidate.fitness()));
    }

    @Test
    @DisplayName("a candidate whose length does not match the state is rejected")
    void mismatchedCandidateRejected() {
        SystemState state = TestStates.safeState();
        CandidateSolution wrongSize = new CandidateSolution(new int[]{0, 1}, new int[]{0, 0});

        assertThrows(IllegalArgumentException.class,
                () -> new FitnessFunction(false).evaluate(wrongSize, state));
    }

    @Test
    @DisplayName("every generated benchmark state can be scored")
    void scoresEveryBenchmarkSize() {
        for (int[] size : TestCaseGenerator.BENCHMARK_SIZES) {
            SystemState state = TestCaseGenerator.generateSafe(size[0], size[1], 3L);
            int[] identity = new int[state.processCount()];
            for (int i = 0; i < identity.length; i++) {
                identity[i] = i;
            }
            CandidateSolution candidate = new CandidateSolution(identity,
                    new int[state.processCount()]);

            double fitness = new FitnessFunction(true).evaluate(candidate, state);
            assertTrue(Double.isFinite(fitness), "fitness must be finite for n=" + size[0]);
        }
    }
}
