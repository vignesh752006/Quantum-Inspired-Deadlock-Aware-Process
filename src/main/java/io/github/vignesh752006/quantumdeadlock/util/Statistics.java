package io.github.vignesh752006.quantumdeadlock.util;

import java.util.Collection;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Descriptive statistics for a single metric across all benchmark repetitions.
 *
 * <p>This replaces the four private {@code mean}/{@code stdDev}/{@code min}/{@code max}
 * helpers that used to live inside {@code Benchmark}. That older {@code max()}
 * helper seeded its accumulator with {@link Double#MIN_VALUE} — which in Java is the
 * smallest <em>positive</em> value, not the most negative one — so any array whose
 * elements were all negative produced a maximum of {@code 0.0}. Using
 * {@link Double#NEGATIVE_INFINITY} as the seed fixes that class of bug once, for
 * every metric.</p>
 *
 * @param count  number of observations (0 for an empty sample)
 * @param mean   arithmetic mean, or {@code 0.0} for an empty sample
 * @param stdDev sample standard deviation (n-1 denominator), or {@code 0.0} when count &lt; 2
 * @param min    smallest observation, or {@code 0.0} for an empty sample
 * @param max    largest observation, or {@code 0.0} for an empty sample
 */
public record Statistics(int count, double mean, double stdDev, double min, double max) {

    /** Builds the summary for a {@code double[]} sample. */
    public static Statistics of(double[] values) {
        if (values == null || values.length == 0) {
            return new Statistics(0, 0.0, 0.0, 0.0, 0.0);
        }

        double sum = 0.0;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (double value : values) {
            sum += value;
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        double mean = sum / values.length;

        double stdDev = 0.0;
        if (values.length > 1) {
            double sumSquaredDeviation = 0.0;
            for (double value : values) {
                double deviation = value - mean;
                sumSquaredDeviation += deviation * deviation;
            }
            stdDev = Math.sqrt(sumSquaredDeviation / (values.length - 1));
        }

        return new Statistics(values.length, mean, stdDev, min, max);
    }

    /** Builds the summary for a boxed collection. */
    public static Statistics of(Collection<Double> values) {
        if (values == null || values.isEmpty()) {
            return of(new double[0]);
        }
        double[] primitive = new double[values.size()];
        int index = 0;
        for (Double value : values) {
            primitive[index++] = (value == null) ? 0.0 : value;
        }
        return of(primitive);
    }

    /** Convenience: extract one metric from a list of items via a mapping function. */
    public static <T> Statistics of(List<T> items, ToDoubleFunction<T> extractor) {
        if (items == null || items.isEmpty()) {
            return of(new double[0]);
        }
        double[] primitive = new double[items.size()];
        for (int i = 0; i < items.size(); i++) {
            primitive[i] = extractor.applyAsDouble(items.get(i));
        }
        return of(primitive);
    }
}
