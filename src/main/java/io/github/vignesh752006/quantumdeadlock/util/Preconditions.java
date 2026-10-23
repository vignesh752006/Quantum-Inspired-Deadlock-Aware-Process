package io.github.vignesh752006.quantumdeadlock.util;

/**
 * Argument-validation helpers.
 *
 * <p>Centralising the checks keeps failure messages consistent across the model,
 * the algorithms and the benchmark harness, and removes the hand-rolled
 * {@code if (x == null) throw new IllegalArgumentException(...)} blocks that were
 * previously repeated in every constructor.</p>
 */
public final class Preconditions {

    private Preconditions() {
        throw new AssertionError("No instances.");
    }

    /**
     * Returns {@code value} unchanged when it is non-null.
     *
     * @throws IllegalArgumentException when {@code value} is null
     */
    public static <T> T requireNonNull(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    /** Requires {@code value > 0}. */
    public static int requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive, got " + value);
        }
        return value;
    }

    /** Requires {@code value >= 0}. */
    public static int requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be >= 0, got " + value);
        }
        return value;
    }

    /** Requires {@code minInclusive <= value <= maxInclusive}. */
    public static int requireInRange(int value, int minInclusive, int maxInclusive, String name) {
        if (value < minInclusive || value > maxInclusive) {
            throw new IllegalArgumentException(
                    name + " must be in [" + minInclusive + ", " + maxInclusive + "], got " + value);
        }
        return value;
    }

    /** Requires {@code condition} to hold. */
    public static void requireTrue(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
