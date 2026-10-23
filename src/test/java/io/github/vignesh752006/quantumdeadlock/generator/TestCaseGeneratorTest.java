package io.github.vignesh752006.quantumdeadlock.generator;

import io.github.vignesh752006.quantumdeadlock.banker.BankersAlgorithm;
import io.github.vignesh752006.quantumdeadlock.model.SystemState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers Step 5 of the original harness: the generator's contractual guarantees —
 * determinism per seed, safe-by-construction, and unsafe-by-construction.
 */
class TestCaseGeneratorTest {

    @Test
    @DisplayName("generateSafe produces a state the Banker accepts")
    void safeGeneratorIsActuallySafe() {
        for (int[] size : TestCaseGenerator.BENCHMARK_SIZES) {
            SystemState state = TestCaseGenerator.generateSafe(size[0], size[1], 99L);
            assertTrue(BankersAlgorithm.isSafe(state),
                    "generateSafe must produce a safe state for n=" + size[0] + " m=" + size[1]);
        }
    }

    @Test
    @DisplayName("generateUnsafe produces a state the Banker rejects")
    void unsafeGeneratorIsActuallyUnsafe() {
        for (int[] size : TestCaseGenerator.BENCHMARK_SIZES) {
            SystemState state = TestCaseGenerator.generateUnsafe(size[0], size[1], 99L);
            assertFalse(BankersAlgorithm.isSafe(state),
                    "generateUnsafe must produce an unsafe state for n=" + size[0] + " m=" + size[1]);
        }
    }

    @Test
    @DisplayName("the same seed yields an identical state")
    void deterministicForSeed() {
        SystemState first = TestCaseGenerator.generateSafe(20, 5, 7L);
        SystemState second = TestCaseGenerator.generateSafe(20, 5, 7L);

        assertEquals(first, second);
        assertEquals(first.label(), second.label());
    }

    @Test
    @DisplayName("different seeds yield different states")
    void differentSeedsDiffer() {
        SystemState first = TestCaseGenerator.generateSafe(20, 5, 7L);
        SystemState second = TestCaseGenerator.generateSafe(20, 5, 8L);

        assertNotEquals(first, second, "distinct seeds should not collapse to one state");
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 5, 10, 50})
    @DisplayName("requested dimensions are honoured exactly")
    void dimensionsHonoured(int processCount) {
        SystemState state = TestCaseGenerator.generateSafe(processCount, 4, 11L);

        assertEquals(processCount, state.processCount());
        assertEquals(4, state.resourceCount());
    }

    @Test
    @DisplayName("generateAllSizesSafe returns one state per benchmark size")
    void allSizesSafe() {
        SystemState[] states = TestCaseGenerator.generateAllSizesSafe(42L);

        assertEquals(TestCaseGenerator.BENCHMARK_SIZES.length, states.length);
        for (SystemState state : states) {
            assertTrue(BankersAlgorithm.isSafe(state));
        }
    }

    @Test
    @DisplayName("invalid dimensions are rejected")
    void invalidDimensionsRejected() {
        assertThrows(IllegalArgumentException.class, () -> TestCaseGenerator.generateSafe(1, 5, 1L));
        assertThrows(IllegalArgumentException.class, () -> TestCaseGenerator.generateSafe(10, 0, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> TestCaseGenerator.generateUnsafe(10, 1, 1L));
    }

    @Test
    @DisplayName("generated states satisfy the conservation invariant")
    void conservationHolds() {
        SystemState state = TestCaseGenerator.generateSafe(30, 6, 5L);

        for (int j = 0; j < state.resourceCount(); j++) {
            int allocated = 0;
            for (int i = 0; i < state.processCount(); i++) {
                allocated += state.allocation(i, j);
            }
            assertEquals(state.total(j), allocated + state.available(j),
                    "conservation broken for resource " + j);
        }
    }
}
