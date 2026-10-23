package io.github.vignesh752006.quantumdeadlock.quantum;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers Step 6 of the original harness: amplitude normalisation, the legal angle
 * range, rotation direction, and observation behaviour.
 */
class QuantumBitTest {

    @Test
    @DisplayName("a new qubit is in the equal superposition")
    void startsInEqualSuperposition() {
        QuantumBit qubit = new QuantumBit();

        assertEquals(Math.PI / 4.0, qubit.theta(), 1e-12);
        assertEquals(0.5, qubit.alphaSquared(), 1e-12);
        assertEquals(0.5, qubit.betaSquared(), 1e-12);
    }

    @Test
    @DisplayName("amplitudes stay normalised after rotation")
    void amplitudesStayNormalised() {
        QuantumBit qubit = new QuantumBit();

        for (int step = 0; step < 100; step++) {
            qubit.rotate(0.03);
            assertEquals(1.0, qubit.alphaSquared() + qubit.betaSquared(), 1e-9,
                    "alpha^2 + beta^2 must remain 1 after step " + step);
        }
    }

    @Test
    @DisplayName("the angle is clamped to the legal open interval")
    void angleIsClamped() {
        QuantumBit high = new QuantumBit(Math.PI);
        QuantumBit low = new QuantumBit(-1.0);

        assertTrue(high.theta() < Math.PI / 2.0, "angle must stay below pi/2");
        assertTrue(high.theta() > 0.0, "angle must stay above 0");
        assertTrue(low.theta() > 0.0, "angle must stay above 0");
    }

    @Test
    @DisplayName("rotating toward bit 1 raises P(1), toward bit 0 lowers it")
    void rotationDirection() {
        QuantumBit qubit = new QuantumBit();
        double initialBetaSquared = qubit.betaSquared();

        qubit.rotateToBit1(0.2);
        assertTrue(qubit.betaSquared() > initialBetaSquared,
                "rotating toward 1 must raise P(observe 1)");

        QuantumBit other = new QuantumBit();
        other.rotateToBit0(0.2);
        assertTrue(other.betaSquared() < 0.5, "rotating toward 0 must lower P(observe 1)");
    }

    @Test
    @DisplayName("selecting a very low threshold always yields bit 0")
    void observeBitBoundaries() {
        QuantumBit qubit = new QuantumBit();

        for (int i = 0; i < 50; i++) {
            assertEquals(0, qubit.observeBit(0.0), "sample 0.0 is below alpha^2");
        }
    }

    @Test
    @DisplayName("observation follows the alpha^2 probability across many samples")
    void observationMatchesProbability() {
        QuantumBit qubit = new QuantumBit();
        qubit.rotateToBit1(0.4);
        double expectedProbabilityOfOne = qubit.betaSquared();

        Random random = new Random(12345L);
        int ones = 0;
        int samples = 20_000;
        for (int i = 0; i < samples; i++) {
            if (qubit.observeBit(random.nextDouble()) == 1) {
                ones++;
            }
        }
        double observedProbability = (double) ones / samples;

        assertEquals(expectedProbabilityOfOne, observedProbability, 0.03,
                "observed frequency should track beta^2 within sampling noise");
    }

    @Test
    @DisplayName("copy is independent of the original")
    void copyIsIndependent() {
        QuantumBit original = new QuantumBit();
        QuantumBit copy = original.copy();

        copy.rotateToBit1(0.5);

        assertEquals(Math.PI / 4.0, original.theta(), 1e-12);
        assertTrue(copy.theta() > original.theta());
    }
}
