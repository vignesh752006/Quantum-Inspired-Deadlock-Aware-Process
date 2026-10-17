import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

/**
 * PerformanceAnalyzer.java
 *
 * Exports benchmark {@link Result} data to CSV and JSON files for
 * consumption by Python plotting scripts (Matplotlib + Pandas).
 *
 * Output files:
 *   - output/results.csv   — one row per run, all metrics
 *   - output/results.json  — JSON array of objects, same data
 *
 * Usage:
 *   Benchmark bench = new Benchmark();
 *   bench.runAll();
 *   PerformanceAnalyzer.exportAll(bench.getAllResults());
 *
 * Phase-2 Extension:
 *   Add VM latency, inter-VM network metrics as extra CSV columns.
 *
 * @author  OSDS-CP Group
 * @version 1.0  (Phase 1)
 */
public class PerformanceAnalyzer {

    private static final String OUTPUT_DIR  = "output";
    private static final String CSV_FILE    = OUTPUT_DIR + File.separator + "results.csv";
    private static final String JSON_FILE   = OUTPUT_DIR + File.separator + "results.json";

    /**
     * Exports all results to both CSV and JSON.
     */
    public static void exportAll(List<Result> results) throws IOException {
        new File(OUTPUT_DIR).mkdirs();
        exportCsv(results);
        exportJson(results);
        System.out.printf("Exported %d results to:%n  CSV:  %s%n  JSON: %s%n",
            results.size(), CSV_FILE, JSON_FILE);
    }

    /**
     * Exports results to CSV format.
     */
    public static void exportCsv(List<Result> results) throws IOException {
        new File(OUTPUT_DIR).mkdirs();
        try (PrintWriter pw = new PrintWriter(new FileWriter(CSV_FILE))) {
            pw.println(Result.csvHeader());
            for (Result r : results) {
                pw.println(r.toCsvRow());
            }
        }
        System.out.printf("  CSV exported: %s (%d rows)%n", CSV_FILE, results.size());
    }

    /**
     * Exports results to JSON format (array of objects).
     */
    public static void exportJson(List<Result> results) throws IOException {
        new File(OUTPUT_DIR).mkdirs();
        try (PrintWriter pw = new PrintWriter(new FileWriter(JSON_FILE))) {
            pw.println("[");
            for (int i = 0; i < results.size(); i++) {
                Result r = results.get(i);
                pw.println("  {");
                pw.printf("    \"algorithm\": \"%s\",%n", r.algorithm);
                pw.printf("    \"processCount\": %d,%n", r.processCount);
                pw.printf("    \"resourceCount\": %d,%n", r.resourceCount);
                pw.printf("    \"repetition\": %d,%n", r.repetition);
                pw.printf("    \"stateSeed\": %d,%n", r.stateSeed);
                pw.printf("    \"stateType\": \"%s\",%n", r.stateType);
                pw.printf("    \"executionTimeNs\": %d,%n", r.executionTimeNs);
                pw.printf("    \"executionTimeMs\": %.4f,%n", r.executionTimeNs / 1_000_000.0);
                pw.printf("    \"fitnessValue\": %.4f,%n", r.fitnessValue);
                pw.printf("    \"processesCompleted\": %d,%n", r.processesCompleted);
                pw.printf("    \"waitingTime\": %.4f,%n", r.waitingTime);
                pw.printf("    \"resourceWaste\": %.4f,%n", r.resourceWaste);
                pw.printf("    \"isSafe\": %b,%n", r.isSafe);
                pw.printf("    \"iterations\": %d%n", r.iterations);
                pw.print("  }");
                if (i < results.size() - 1) pw.print(",");
                pw.println();
            }
            pw.println("]");
        }
        System.out.printf("  JSON exported: %s (%d entries)%n", JSON_FILE, results.size());
    }

    /**
     * Standalone runner: run benchmark + export.
     */
    public static void main(String[] args) throws IOException {
        System.out.println("Running benchmark suite and exporting results...\n");

        Benchmark bench = new Benchmark();
        bench.runAll();

        System.out.println();
        exportAll(bench.getAllResults());

        System.out.println("\nDone! Run 'python analysis/plot_results.py' to generate charts.");
    }
}
