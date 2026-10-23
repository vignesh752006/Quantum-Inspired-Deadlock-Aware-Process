package io.github.vignesh752006.quantumdeadlock.benchmark;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the export layer: CSV shape, JSON validity, and escaping of awkward labels.
 */
class PerformanceAnalyzerTest {

    private static Result sample(String algorithm, int repetition) {
        return new Result(algorithm, 10, 5, repetition, 42L, "safe",
                1_500_000L, 1234.5, 10, 3.0, 7.0, true, 12);
    }

    @Test
    @DisplayName("CSV has the documented header and one row per result")
    void csvShape(@TempDir Path directory) throws IOException {
        List<Result> results = List.of(sample("Banker", 0), sample("QEA", 1), sample("Hybrid", 2));
        Path csv = directory.resolve("results.csv");

        PerformanceAnalyzer.exportCsv(results, csv);

        List<String> lines = Files.readAllLines(csv, StandardCharsets.UTF_8);
        assertEquals(1 + results.size(), lines.size(), "header plus one line per result");
        assertTrue(lines.get(0).startsWith("algorithm,processCount,resourceCount"));
        assertTrue(lines.get(1).startsWith("Banker,10,5,0,42,safe,"));
    }

    @Test
    @DisplayName("JSON is a well-formed array with one object per result")
    void jsonShape(@TempDir Path directory) throws IOException {
        List<Result> results = List.of(sample("Banker", 0), sample("QEA", 1));
        Path json = directory.resolve("results.json");

        PerformanceAnalyzer.exportJson(results, json);

        String content = Files.readString(json, StandardCharsets.UTF_8).trim();
        assertTrue(content.startsWith("["));
        assertTrue(content.endsWith("]"));
        assertEquals(2, countOccurrences(content, "\"algorithm\""));
        assertTrue(content.contains("\"isSafe\": true"));
    }

    @Test
    @DisplayName("quotes and backslashes in labels are escaped")
    void escapesAwkwardLabels(@TempDir Path directory) throws IOException {
        Result tricky = new Result("Hy\"brid\\x", 5, 2, 0, 1L, "safe",
                100L, 1.0, 5, 0.0, 0.0, true, 3);
        Path json = directory.resolve("results.json");

        PerformanceAnalyzer.exportJson(List.of(tricky), json);
        String content = Files.readString(json, StandardCharsets.UTF_8);

        assertTrue(content.contains("Hy\\\"brid\\\\x"),
                "embedded quote and backslash must be escaped: " + content);
    }

    @Test
    @DisplayName("exportAll writes both files and creates the directory")
    void exportAllCreatesDirectory(@TempDir Path directory) throws IOException {
        Path nested = directory.resolve("output");
        List<Path> written = PerformanceAnalyzer.exportAll(List.of(sample("Banker", 0)), nested);

        assertEquals(2, written.size());
        for (Path path : written) {
            assertTrue(Files.exists(path), "expected " + path + " to exist");
        }
    }

    @Test
    @DisplayName("the CSV row builder matches the header column count")
    void rowMatchesHeaderWidth() {
        String row = PerformanceAnalyzer.toCsvRow(sample("Banker", 0));
        assertEquals(14, row.split(",", -1).length,
                "row width must equal the 14 header columns");
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
