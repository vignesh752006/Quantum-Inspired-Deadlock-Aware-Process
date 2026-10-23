package io.github.vignesh752006.quantumdeadlock.util;

import java.util.Arrays;
import java.util.StringJoiner;

/**
 * Defensive-copy and comparison helpers for the {@code int[]} / {@code int[][]}
 * structures that model process allocation state.
 *
 * <p>These were previously duplicated as private {@code deepCopy2D}/{@code deepCopy}
 * methods in {@code SystemState}, {@code Main} and several of the step tests.
 * Having one implementation removes the risk of the copies drifting apart.</p>
 */
public final class Matrices {

    private Matrices() {
        throw new AssertionError("No instances.");
    }

    /**
     * Returns an independent copy of a rectangular matrix.
     *
     * @param source matrix to copy; each row is cloned
     * @return a new outer array whose rows are new arrays
     */
    public static int[][] deepCopy(int[][] source) {
        int[][] copy = new int[source.length][];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i].clone();
        }
        return copy;
    }

    /** Returns a copy of {@code source}, or null when the input is null. */
    public static int[] copyOrNull(int[] source) {
        return source == null ? null : source.clone();
    }

    /** Element-wise comparison of two matrices. */
    public static boolean deepEquals(int[][] a, int[][] b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null || a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (!Arrays.equals(a[i], b[i])) {
                return false;
            }
        }
        return true;
    }

    /** Sums every element of a matrix. */
    public static long sum(int[][] matrix) {
        long total = 0L;
        for (int[] row : matrix) {
            for (int value : row) {
                total += value;
            }
        }
        return total;
    }

    /** Sums an array. */
    public static long sum(int[] values) {
        long total = 0L;
        for (int value : values) {
            total += value;
        }
        return total;
    }

    /** Renders an array as {@code [1, 2, 3]} for logs and error messages. */
    public static String format(int[] values) {
        StringJoiner joiner = new StringJoiner(", ", "[", "]");
        for (int value : values) {
            joiner.add(Integer.toString(value));
        }
        return joiner.toString();
    }
}
