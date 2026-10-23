package io.github.vignesh752006.quantumdeadlock.banker;

import io.github.vignesh752006.quantumdeadlock.model.SystemState;
import io.github.vignesh752006.quantumdeadlock.testsupport.TestStates;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers Step 3 of the original harness: the safety verdict, the completeness of the
 * returned sequence, and agreement between the trace-building and fast code paths.
 */
class BankersAlgorithmTest {

    @Test
    @DisplayName("a textbook-safe state is reported safe")
    void safeStateReportedSafe() {
        BankersResult result = BankersAlgorithm.checkSafety(TestStates.safeState());

        assertTrue(result.safe());
        assertTrue(BankersAlgorithm.isSafe(TestStates.safeState()));
    }

    @Test
    @DisplayName("a circular-wait ring is reported unsafe")
    void unsafeRingReportedUnsafe() {
        BankersResult result = BankersAlgorithm.checkSafety(TestStates.unsafeRing());

        assertFalse(result.safe());
        assertFalse(BankersAlgorithm.isSafe(TestStates.unsafeRing()));
        assertEquals(0, result.safeSequence().length,
                "an unsafe state must not report a sequence");
    }

    @Test
    @DisplayName("the safe sequence is a complete permutation of all processes")
    void sequenceIsCompletePermutation() {
        SystemState state = TestStates.safeState();
        BankersResult result = BankersAlgorithm.checkSafety(state);

        int[] sequence = result.safeSequence();
        assertEquals(state.processCount(), sequence.length);

        Set<Integer> seen = new HashSet<>();
        for (int processIndex : sequence) {
            assertTrue(processIndex >= 0 && processIndex < state.processCount(),
                    "index out of range: " + processIndex);
            assertTrue(seen.add(processIndex), "duplicate index in sequence: " + processIndex);
        }
    }

    @Test
    @DisplayName("fast and traced checks always agree")
    void fastAndTracedChecksAgree() {
        BankersResult traced = BankersAlgorithm.checkSafety(TestStates.safeState());
        assertEquals(traced.safe(), BankersAlgorithm.isSafe(TestStates.safeState()));

        BankersResult tracedUnsafe = BankersAlgorithm.checkSafety(TestStates.unsafeRing());
        assertEquals(tracedUnsafe.safe(), BankersAlgorithm.isSafe(TestStates.unsafeRing()));
    }

    @Test
    @DisplayName("the trace records the decision and stays non-empty")
    void traceIsRecorded() {
        BankersResult result = BankersAlgorithm.checkSafety(TestStates.safeState());

        assertFalse(result.trace().isEmpty());
        assertTrue(result.trace().get(result.trace().size() - 1).startsWith("SAFE"),
                "final trace entry should state the verdict");
    }

    @Test
    @DisplayName("the report includes the verdict and process names")
    void reportIsHumanReadable() {
        SystemState state = TestStates.safeState();
        BankersResult result = BankersAlgorithm.checkSafety(state);
        String report = BankersAlgorithm.formatReport(state, result);

        assertTrue(report.contains("SAFE"));
        assertTrue(report.contains(state.label()));
        assertTrue(report.contains("P"), "report should name processes");
    }

    @Test
    @DisplayName("the state under test is never mutated")
    void stateIsNotMutated() {
        SystemState state = TestStates.safeState();
        int[] availableBefore = state.availableVector();
        int[][] allocationBefore = state.allocationMatrix();

        BankersAlgorithm.checkSafety(state);
        BankersAlgorithm.isSafe(state);

        assertEquals(Arrays.toString(availableBefore), Arrays.toString(state.availableVector()));
        assertTrue(Arrays.deepEquals(allocationBefore, state.allocationMatrix()));
    }

    @Test
    @DisplayName("sequenceString renders names, or a placeholder when unsafe")
    void sequenceStringRendering() {
        SystemState safe = TestStates.safeState();
        BankersResult safeResult = BankersAlgorithm.checkSafety(safe);
        String rendered = safeResult.sequenceString(safe);

        assertNotNull(rendered);
        assertTrue(rendered.startsWith("<") && rendered.endsWith(">"));

        SystemState unsafe = TestStates.unsafeRing();
        BankersResult unsafeResult = BankersAlgorithm.checkSafety(unsafe);
        assertTrue(unsafeResult.sequenceString(unsafe).contains("UNSAFE"));
    }
}
