package io.github.vignesh752006.quantumdeadlock.benchmark;

import io.github.vignesh752006.quantumdeadlock.util.Preconditions;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Exports benchmark runs to CSV and JSON for the Python charting scripts and the
 * web dashboard.
 *
 * <p>Both writers stream through {@link Writer} instead of collecting the whole
 * document in memory, and both escape their string fields, so a label containing a
 * quote or backslash cannot produce malformed output. Field order is fixed and
 * shared by both formats.</p>
 */
public final class PerformanceAnalyzer {

    public static final String DEFAULT_OUTPUT_DIRECTORY = "output";
    public static final String DEFAULT_CSV_NAME = "results.csv";
    public static final String DEFAULT_JSON_NAME = "results.json";

    private static final String CSV_HEADER =
            "algorithm,processCount,resourceCount,repetition,stateSeed,stateType,"
                    + "executionTimeNs,executionTimeMs,fitnessValue,processesCompleted,"
                    + "waitingTime,resourceWaste,isSafe,iterations";

    private PerformanceAnalyzer() {
        throw new AssertionError("No instances.");
    }

    /**
     * Writes both export files into {@code directory}, creating it if needed.
     *
     * @param results   runs to export
     * @param directory target directory
     * @return the two paths written, CSV first
     * @throws IOException if either file cannot be written
     */
    public static List<Path> exportAll(List<Result> results, Path directory) throws IOException {
        Preconditions.requireNonNull(results, "results must not be null.");
        Preconditions.requireNonNull(directory, "directory must not be null.");
        Files.createDirectories(directory);

        Path csvPath = directory.resolve(DEFAULT_CSV_NAME);
        Path jsonPath = directory.resolve(DEFAULT_JSON_NAME);
        exportCsv(results, csvPath);
        exportJson(results, jsonPath);
        return List.of(csvPath, jsonPath);
    }

    /**
     * Exports runs to {@link #DEFAULT_OUTPUT_DIRECTORY}.
     *
     * @param results runs to export
     * @return the two paths written
     * @throws IOException if either file cannot be written
     */
    public static List<Path> exportAll(List<Result> results) throws IOException {
        return exportAll(results, Path.of(DEFAULT_OUTPUT_DIRECTORY));
    }

    /**
     * Writes a CSV with a fixed header and one row per run.
     *
     * @param results runs to export
     * @param csvPath destination file
     * @throws IOException if the file cannot be written
     */
    public static void exportCsv(List<Result> results, Path csvPath) throws IOException {
        try (Writer writer = Files.newBufferedWriter(csvPath, StandardCharsets.UTF_8)) {
            writer.write(CSV_HEADER);
            writer.write('\n');
            for (Result result : results) {
                writer.write(toCsvRow(result));
                writer.write('\n');
            }
        }
    }

    /**
     * Writes a JSON array with one object per run.
     *
     * @param results runs to export
     * @param jsonPath destination file
     * @throws IOException if the file cannot be written
     */
    public static void exportJson(List<Result> results, Path jsonPath) throws IOException {
        try (Writer writer = Files.newBufferedWriter(jsonPath, StandardCharsets.UTF_8)) {
            writer.write("[\n");
            for (int i = 0; i < results.size(); i++) {
                writer.write(toJson(results.get(i), "  "));
                if (i < results.size() - 1) {
                    writer.write(',');
                }
                writer.write('\n');
            }
            writer.write("]\n");
        }
    }

    /** Formats one run as a CSV row, matching {@link #CSV_HEADER} order. */
    public static String toCsvRow(Result result) {
        return String.format(Locale.ROOT, "%s,%d,%d,%d,%d,%s,%d,%.4f,%.4f,%d,%.4f,%.4f,%b,%d",
                escapeCsv(result.algorithm()), result.processCount(), result.resourceCount(),
                result.repetition(), result.stateSeed(), escapeCsv(result.stateType()),
                result.executionTimeNs(), result.executionTimeMs(), result.fitnessValue(),
                result.processesCompleted(), result.waitingTime(), result.resourceWaste(),
                result.isSafe(), result.iterations());
    }

    /** Formats one run as a JSON object. */
    public static String toJson(Result result, String indent) {
        String inner = indent + "  ";
        StringBuilder json = new StringBuilder();
        json.append(indent).append("{\n");
        json.append(inner).append("\"algorithm\": \"").append(escapeJson(result.algorithm())).append("\",\n");
        json.append(inner).append("\"processCount\": ").append(result.processCount()).append(",\n");
        json.append(inner).append("\"resourceCount\": ").append(result.resourceCount()).append(",\n");
        json.append(inner).append("\"repetition\": ").append(result.repetition()).append(",\n");
        json.append(inner).append("\"stateSeed\": ").append(result.stateSeed()).append(",\n");
        json.append(inner).append("\"stateType\": \"").append(escapeJson(result.stateType())).append("\",\n");
        json.append(inner).append("\"executionTimeNs\": ").append(result.executionTimeNs()).append(",\n");
        json.append(inner).append(String.format(Locale.ROOT, "\"executionTimeMs\": %.4f,\n",
                result.executionTimeMs()));
        json.append(inner).append(String.format(Locale.ROOT, "\"fitnessValue\": %.4f,\n",
                result.fitnessValue()));
        json.append(inner).append("\"processesCompleted\": ").append(result.processesCompleted()).append(",\n");
        json.append(inner).append(String.format(Locale.ROOT, "\"waitingTime\": %.4f,\n",
                result.waitingTime()));
        json.append(inner).append(String.format(Locale.ROOT, "\"resourceWaste\": %.4f,\n",
                result.resourceWaste()));
        json.append(inner).append("\"isSafe\": ").append(result.isSafe()).append(",\n");
        json.append(inner).append("\"iterations\": ").append(result.iterations()).append('\n');
        json.append(indent).append('}');
        return json.toString();
    }

    /** Quotes a CSV field when it contains a delimiter, quote or newline. */
    private static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuotes = value.indexOf(',') >= 0 || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
        if (!needsQuotes) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    /** Escapes a JSON string body. */
    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
