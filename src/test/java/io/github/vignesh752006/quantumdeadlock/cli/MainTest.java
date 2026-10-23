package io.github.vignesh752006.quantumdeadlock.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the CLI contract: exit codes, help output and the demo walkthrough.
 */
class MainTest {

    private final ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
    private final PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
    private final PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

    private String stdout() {
        return outBytes.toString(StandardCharsets.UTF_8);
    }

    private String stderr() {
        return errBytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("no arguments runs the demo and exits 0")
    void defaultRunsDemo() {
        assertEquals(0, Main.run(new String[0], out, err));
        assertTrue(stdout().contains("=== Demo"));
    }

    @Test
    @DisplayName("help prints usage and exits 0")
    void helpExitsZero() {
        assertEquals(0, Main.run(new String[]{"help"}, out, err));
        assertTrue(stdout().contains("Usage:"));
        assertTrue(stdout().contains("benchmark"));
    }

    @Test
    @DisplayName("an unknown command exits 2 and explains itself")
    void unknownCommandExitsTwo() {
        assertEquals(2, Main.run(new String[]{"nonsense"}, out, err));
        assertTrue(stderr().contains("Unknown command"));
    }

    @Test
    @DisplayName("the demo reports all four stages")
    void demoReportsAllStages() {
        Main.run(new String[]{"demo"}, out, err);
        String output = stdout();

        assertTrue(output.contains("Banker's Algorithm Report"), "missing Banker section");
        assertTrue(output.contains("Cycle Detection Report"), "missing cycle section");
        assertTrue(output.contains("QEA"), "missing QEA section");
        assertTrue(output.contains("Hybrid"), "missing hybrid section");
    }

    @Test
    @DisplayName("the hybrid stage of the demo reports a safe solution")
    void demoHybridIsSafe() {
        Main.run(new String[]{"demo"}, out, err);
        assertTrue(stdout().contains("Safe         : true"),
                "the demo state should be safe under the hybrid constraint");
    }
}
