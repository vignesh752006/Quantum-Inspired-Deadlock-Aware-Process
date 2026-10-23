package io.github.vignesh752006.quantumdeadlock.cli;

import io.github.vignesh752006.quantumdeadlock.banker.BankersAlgorithm;
import io.github.vignesh752006.quantumdeadlock.banker.BankersResult;
import io.github.vignesh752006.quantumdeadlock.benchmark.Benchmark;
import io.github.vignesh752006.quantumdeadlock.benchmark.PerformanceAnalyzer;
import io.github.vignesh752006.quantumdeadlock.benchmark.Result;
import io.github.vignesh752006.quantumdeadlock.generator.TestCaseGenerator;
import io.github.vignesh752006.quantumdeadlock.graph.CycleDetector;
import io.github.vignesh752006.quantumdeadlock.graph.ResourceAllocationGraph;
import io.github.vignesh752006.quantumdeadlock.model.SystemState;
import io.github.vignesh752006.quantumdeadlock.quantum.FitnessFunction;
import io.github.vignesh752006.quantumdeadlock.quantum.OptimizationResult;
import io.github.vignesh752006.quantumdeadlock.quantum.QuantumInspiredOptimizer;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Command-line entry point.
 *
 * <p>Subcommands:</p>
 * <ul>
 *   <li>{@code demo} (default) - run Banker, cycle detection, QEA and hybrid on one state</li>
 *   <li>{@code benchmark} - run the full sweep and export {@code output/results.csv} and
 *       {@code output/results.json}</li>
 *   <li>{@code help} - usage</li>
 * </ul>
 *
 * <p>Exit codes: {@code 0} success, {@code 1} I/O failure, {@code 2} bad usage.</p>
 */
public final class Main {

    static final int EXIT_OK = 0;
    static final int EXIT_IO_ERROR = 1;
    static final int EXIT_USAGE = 2;

    private static final int DEMO_PROCESSES = 5;
    private static final int DEMO_RESOURCES = 3;
    private static final long DEMO_SEED = 7L;

    private Main() {
        throw new AssertionError("No instances.");
    }

    /**
     * @param args subcommand and its options
     */
    public static void main(String[] args) {
        int status = run(args, System.out, System.err);
        if (status != EXIT_OK) {
            System.exit(status);
        }
    }

    /**
     * Runs the CLI without terminating the JVM, so tests can invoke it directly.
     *
     * @param args subcommand and its options
     * @param out  standard output stream
     * @param err  standard error stream
     * @return process exit code
     */
    public static int run(String[] args, PrintStream out, PrintStream err) {
        String command = (args == null || args.length == 0)
                ? "demo"
                : args[0].trim().toLowerCase(Locale.ROOT);

        try {
            return switch (command) {
                case "demo", "-d", "--demo" -> runDemo(out);
                case "benchmark", "-b", "--benchmark" -> runBenchmark(args, out, err);
                case "help", "-h", "--help" -> {
                    printUsage(out);
                    yield EXIT_OK;
                }
                default -> {
                    err.println("Unknown command: " + command);
                    printUsage(err);
                    yield EXIT_USAGE;
                }
            };
        } catch (IOException cause) {
            err.println("I/O error: " + cause.getMessage());
            return EXIT_IO_ERROR;
        } catch (RuntimeException cause) {
            err.println("Unexpected failure: " + cause.getMessage());
            return EXIT_IO_ERROR;
        }
    }

    private static int runDemo(PrintStream out) {
        SystemState state = TestCaseGenerator.generateSafe(DEMO_PROCESSES, DEMO_RESOURCES, DEMO_SEED);

        out.println("=== Demo: safe state, "
                + DEMO_PROCESSES + " processes, " + DEMO_RESOURCES + " resource types ===");
        out.println("State: " + state);
        out.printf("Overall utilization: %.1f%%%n%n", state.overallUtilization() * 100.0);

        printStateTable(state, out);

        BankersResult bankerResult = BankersAlgorithm.checkSafety(state);
        out.println(BankersAlgorithm.formatReport(state, bankerResult));

        ResourceAllocationGraph graph = new ResourceAllocationGraph(state);
        out.println(CycleDetector.formatReport(graph, CycleDetector.detect(graph)));

        out.println("=== QEA (soft deadlock penalty) ===");
        OptimizationResult qea = new QuantumInspiredOptimizer(state, new FitnessFunction(false))
                .optimize(2024L);
        printOptimization(qea, out);

        out.println();
        out.println("=== Hybrid (Banker safety as hard constraint) ===");
        OptimizationResult hybrid = new QuantumInspiredOptimizer(state, new FitnessFunction(true))
                .optimize(2024L);
        printOptimization(hybrid, out);

        return EXIT_OK;
    }

    private static void printStateTable(SystemState state, PrintStream out) {
        out.println("Processes");
        for (int i = 0; i < state.processCount(); i++) {
            out.println("  " + state.process(i));
        }
        out.println("Resources");
        for (int j = 0; j < state.resourceCount(); j++) {
            out.println("  " + state.resource(j));
        }
        out.println();
    }

    private static void printOptimization(OptimizationResult optimization, PrintStream out) {
        out.printf("Best fitness : %.2f%n", optimization.bestFitness());
        out.printf("Iterations   : %d%s%n", optimization.iterationsRun(),
                optimization.converged() ? " (converged early)" : "");
        out.printf("Time         : %.2f ms%n",
                optimization.executionTimeNs() / 1_000_000.0);

        if (optimization.bestSolution() != null) {
            out.printf("Safe         : %s%n", optimization.bestSolution().isSafe());
            out.printf("Completed    : %d%n", optimization.bestSolution().processesCompleted());
        }
        if (!optimization.bestHistory().isEmpty()) {
            out.printf("First/last best: %.1f -> %.1f%n",
                    optimization.bestHistory().get(0),
                    optimization.bestHistory().get(optimization.bestHistory().size() - 1));
        }
    }

    private static int runBenchmark(String[] args, PrintStream out, PrintStream err) throws IOException {
        Path outputDirectory = Path.of(PerformanceAnalyzer.DEFAULT_OUTPUT_DIRECTORY);
        if (args != null && args.length > 1) {
            outputDirectory = Path.of(args[1]);
        }

        Benchmark benchmark = new Benchmark(out);
        benchmark.runAll();

        List<Result> results = benchmark.allResults();
        List<Path> written = PerformanceAnalyzer.exportAll(results, outputDirectory);

        out.println();
        out.printf("Exported %d runs:%n", results.size());
        for (Path path : written) {
            out.printf("  %s%n", path.toAbsolutePath());
        }

        if (results.isEmpty()) {
            err.println("Warning: no results were produced.");
        }
        return EXIT_OK;
    }

    private static void printUsage(PrintStream out) {
        out.println("Quantum-Inspired Deadlock-Aware Process Scheduling");
        out.println();
        out.println("Usage: java -jar quantum-deadlock.jar [command] [options]");
        out.println();
        out.println("Commands:");
        out.println("  demo                  Single-state walkthrough (default)");
        out.println("  benchmark [dir]       Full sweep; writes results.csv/results.json to [dir]");
        out.println("                        (default: output)");
        out.println("  help                  Show this message");
        out.println();
        out.println("Note: this is a classical algorithm using the Q-bit formalism.");
        out.println("      No quantum hardware and no quantum speedup claim is implied.");
    }
}
