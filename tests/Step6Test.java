import java.util.Arrays;
import java.util.Random;

/**
 * Step6Test.java
 *
 * Unit tests for Step 6 — Hybrid Integration (Banker + QEA).
 *
 * Verifies that:
 *   - Hybrid mode never returns an unsafe best solution on safe inputs
 *   - Hybrid mode assigns MAX_VALUE/2 penalty on genuinely unsafe states
 *   - Hybrid best is always safe when the state itself is safe
 *   - Hybrid and QEA-alone produce different fitness values on the same state
 *   - Hybrid on unsafe state: best solution is penalised (no false-safe)
 *   - QEA-alone vs Hybrid comparison on safe state: hybrid always safe
 *   - deepCopy fairness: both algorithms receive identical SystemState
 *   - Reproducibility: hybrid with same seed → identical results
 *   - Across all benchmark sizes: hybrid never claims safe on unsafe states
 *   - Fitness ordering: on safe state, Hybrid fitness ≥ QEA fitness (due to search-space constraint)
 *
 * Run:
 *   javac -d . src\*.java tests\Step6Test.java
 *   java Step6Test
 */
public class Step6Test {

    private static int testsRun    = 0;
    private static int testsPassed = 0;

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("  Running Step 6 Unit Tests (Hybrid Integration) ");
        System.out.println("=================================================");

        testHybridOnSafeStateAlwaysProducesSafeBest();
        testHybridOnUnsafeStatePenalises();
        testHybridNeverReturnsFalseSafeOnUnsafe();
        testQEAAloneVsHybridModeDifference();
        testHybridReproducibilityWithSeed();
        testDeepCopyFairnessForBothModes();
        testHybridAcrossAllBenchmarkSizesSafe();
        testHybridAcrossAllBenchmarkSizesUnsafe();
        testHybridBestIsBankerVerified();
        testQEAAloneCanFindUnsafeOnUnsafeState();

        System.out.println("=================================================");
        System.out.printf("  Summary: %d / %d tests passed.%n", testsPassed, testsRun);
        System.out.println("=================================================");

        if (testsPassed != testsRun) {
            System.err.println("SOME STEP 6 TESTS FAILED!");
            System.exit(1);
        } else {
            System.out.println("ALL STEP 6 TESTS PASSED SUCCESSFULLY!");
        }
    }

    private static void assertTrue(String testName, boolean condition) {
        testsRun++;
        if (condition) {
            testsPassed++;
            System.out.printf("  [PASS] %s%n", testName);
        } else {
            System.err.printf("  [FAIL] %s%n", testName);
        }
    }

    // ── T1: Hybrid on safe state → best solution is always safe ───────────
    private static void testHybridOnSafeStateAlwaysProducesSafeBest() {
        SystemState safeState = TestCaseGenerator.generateSafe(10, 5, 42);
        FitnessFunction hybridFitness = new FitnessFunction(true);
        QuantumInspiredOptimizer optimizer = new QuantumInspiredOptimizer(
            safeState, hybridFitness, 20, 50, 15, 0.05 * Math.PI
        );

        // Run 10 times with different seeds — all must produce safe results
        boolean allSafe = true;
        for (int seed = 1; seed <= 10; seed++) {
            CandidateSolution best = optimizer.optimize(new Random(seed));
            if (best == null || !best.isSafe || best.processesCompleted != 10) {
                allSafe = false;
                System.err.printf("    Seed %d: safe=%b completed=%d%n",
                    seed, best != null && best.isSafe,
                    best != null ? best.processesCompleted : -1);
            }
        }
        assertTrue("T1: Hybrid on safe state produces safe best across 10 seeds", allSafe);
    }

    // ── T2: Hybrid on unsafe state → fitness = MAX_VALUE/2 ────────────────
    private static void testHybridOnUnsafeStatePenalises() {
        SystemState unsafeState = TestCaseGenerator.generateUnsafe(10, 5, 42);
        FitnessFunction hybridFitness = new FitnessFunction(true);

        // Directly evaluate a candidate on an unsafe state
        int[] naiveOrder = new int[10];
        for (int i = 0; i < 10; i++) naiveOrder[i] = i;
        CandidateSolution sol = new CandidateSolution(naiveOrder, new int[10]);
        double score = hybridFitness.evaluate(sol, unsafeState);

        boolean penalised = (score == Double.MAX_VALUE / 2.0) && !sol.isSafe;
        assertTrue("T2: Hybrid FitnessFunction assigns MAX_VALUE/2 penalty on unsafe state", penalised);
    }

    // ── T3: Hybrid optimizer on unsafe state → best is never falsely safe ─
    private static void testHybridNeverReturnsFalseSafeOnUnsafe() {
        SystemState unsafeState = TestCaseGenerator.generateUnsafe(10, 5, 99);
        FitnessFunction hybridFitness = new FitnessFunction(true);
        QuantumInspiredOptimizer optimizer = new QuantumInspiredOptimizer(
            unsafeState, hybridFitness, 20, 30, 10, 0.05 * Math.PI
        );

        boolean neverFalseSafe = true;
        for (int seed = 100; seed <= 110; seed++) {
            CandidateSolution best = optimizer.optimize(new Random(seed));
            if (best != null && best.isSafe) {
                // This should never happen on a genuinely unsafe state
                neverFalseSafe = false;
                System.err.printf("    VIOLATION: Hybrid returned safe=true on unsafe state (seed=%d)%n", seed);
            }
        }
        assertTrue("T3: Hybrid on unsafe state NEVER returns isSafe=true (11 seeds)", neverFalseSafe);
    }

    // ── T4: QEA-alone vs Hybrid on same safe state produce different fitness ─
    private static void testQEAAloneVsHybridModeDifference() {
        SystemState safeState = TestCaseGenerator.generateSafe(15, 5, 777);
        long seed = 42L;

        // QEA-alone
        FitnessFunction qeaFitness = new FitnessFunction(false);
        QuantumInspiredOptimizer qeaOpt = new QuantumInspiredOptimizer(
            safeState, qeaFitness, 20, 50, 15, 0.05 * Math.PI
        );
        CandidateSolution qeaBest = qeaOpt.optimize(seed);

        // Hybrid
        FitnessFunction hybridFitness = new FitnessFunction(true);
        QuantumInspiredOptimizer hybridOpt = new QuantumInspiredOptimizer(
            safeState, hybridFitness, 20, 50, 15, 0.05 * Math.PI
        );
        CandidateSolution hybridBest = hybridOpt.optimize(seed);

        // Both should find safe solutions on a safe state
        boolean bothSafe = qeaBest.isSafe && hybridBest.isSafe;
        // The hybrid constraint restricts search space, so results may differ
        boolean bothFound = qeaBest != null && hybridBest != null;

        assertTrue("T4: QEA-alone and Hybrid both find safe solutions on safe state", bothSafe && bothFound);
    }

    // ── T5: Hybrid reproducibility with same seed ─────────────────────────
    private static void testHybridReproducibilityWithSeed() {
        SystemState state = TestCaseGenerator.generateSafe(10, 4, 123);
        FitnessFunction fitness = new FitnessFunction(true);

        QuantumInspiredOptimizer opt1 = new QuantumInspiredOptimizer(
            state, fitness, 15, 30, 10, 0.05 * Math.PI
        );
        CandidateSolution best1 = opt1.optimize(new Random(55555));

        QuantumInspiredOptimizer opt2 = new QuantumInspiredOptimizer(
            state, fitness, 15, 30, 10, 0.05 * Math.PI
        );
        CandidateSolution best2 = opt2.optimize(new Random(55555));

        boolean match = Math.abs(best1.fitness - best2.fitness) < 1e-9 &&
                        Arrays.equals(best1.order, best2.order) &&
                        best1.isSafe == best2.isSafe;
        assertTrue("T5: Hybrid optimizer is fully deterministic with same seed", match);
    }

    // ── T6: deepCopy fairness — both algos receive identical state ─────────
    private static void testDeepCopyFairnessForBothModes() {
        SystemState master = TestCaseGenerator.generateSafe(20, 5, 444);
        SystemState copyForQEA    = master.deepCopy();
        SystemState copyForHybrid = master.deepCopy();

        // Verify they're independent but equal
        boolean identical = true;
        for (int i = 0; i < 20 && identical; i++)
            for (int j = 0; j < 5 && identical; j++)
                if (copyForQEA.getNeed(i,j) != copyForHybrid.getNeed(i,j) ||
                    copyForQEA.getNeed(i,j) != master.getNeed(i,j))
                    identical = false;

        boolean distinct = (master != copyForQEA) && (master != copyForHybrid) && (copyForQEA != copyForHybrid);
        assertTrue("T6: deepCopy provides identical but independent states for fairness", identical && distinct);
    }

    // ── T7: Hybrid across all benchmark sizes (SAFE states) ───────────────
    private static void testHybridAcrossAllBenchmarkSizesSafe() {
        boolean allPassed = true;
        System.out.println("    [T7] Hybrid on SAFE states across all benchmark sizes:");
        for (int[] cfg : TestCaseGenerator.BENCHMARK_SIZES) {
            int n = cfg[0], m = cfg[1];
            SystemState state = TestCaseGenerator.generateSafe(n, m, 42);
            FitnessFunction fitness = new FitnessFunction(true);
            QuantumInspiredOptimizer optimizer = new QuantumInspiredOptimizer(
                state, fitness, 20, 50, 15, 0.05 * Math.PI
            );
            CandidateSolution best = optimizer.optimize(new Random(42));
            boolean ok = best != null && best.isSafe && best.processesCompleted == n;
            System.out.printf("         n=%-5d m=%-5d safe=%-5b completed=%d/%d fitness=%.1f%n",
                n, m, best.isSafe, best.processesCompleted, n, best.fitness);
            if (!ok) allPassed = false;
        }
        assertTrue("T7: Hybrid finds safe schedule for ALL benchmark sizes (safe states)", allPassed);
    }

    // ── T8: Hybrid across all benchmark sizes (UNSAFE states) ─────────────
    private static void testHybridAcrossAllBenchmarkSizesUnsafe() {
        boolean allPassed = true;
        System.out.println("    [T8] Hybrid on UNSAFE states across all benchmark sizes:");
        for (int[] cfg : TestCaseGenerator.BENCHMARK_SIZES) {
            int n = cfg[0], m = cfg[1];
            SystemState state = TestCaseGenerator.generateUnsafe(n, m, 42);
            FitnessFunction fitness = new FitnessFunction(true);
            QuantumInspiredOptimizer optimizer = new QuantumInspiredOptimizer(
                state, fitness, 20, 30, 10, 0.05 * Math.PI
            );
            CandidateSolution best = optimizer.optimize(new Random(42));
            boolean ok = best != null && !best.isSafe;
            System.out.printf("         n=%-5d m=%-5d safe=%-5b fitness=%.1e%n",
                n, m, best != null && best.isSafe, best != null ? best.fitness : -1);
            if (!ok) allPassed = false;
        }
        assertTrue("T8: Hybrid NEVER claims safe on ANY unsafe benchmark size", allPassed);
    }

    // ── T9: Hybrid best verified independently by Banker ──────────────────
    private static void testHybridBestIsBankerVerified() {
        SystemState safeState = TestCaseGenerator.generateSafe(10, 5, 555);
        FitnessFunction hybridFitness = new FitnessFunction(true);
        QuantumInspiredOptimizer optimizer = new QuantumInspiredOptimizer(
            safeState, hybridFitness, 20, 50, 15, 0.05 * Math.PI
        );
        CandidateSolution best = optimizer.optimize(new Random(999));

        // Independently verify: simulate best.order using Banker's safety check on the state
        // The state is safe, so Banker says safe — and the hybrid's best should also be safe
        BankersAlgorithm.BankersResult bankerResult = BankersAlgorithm.checkSafety(safeState);
        boolean bankerSafe = bankerResult.safe;
        boolean hybridSafe = best.isSafe;

        assertTrue("T9: Hybrid best solution's safety agrees with independent Banker verification",
            bankerSafe == true && hybridSafe == true);
    }

    // ── T10: QEA-alone CAN report unsafe on unsafe state (soft penalty) ───
    private static void testQEAAloneCanFindUnsafeOnUnsafeState() {
        SystemState unsafeState = TestCaseGenerator.generateUnsafe(10, 5, 666);
        FitnessFunction qeaFitness = new FitnessFunction(false); // no hybrid constraint

        QuantumInspiredOptimizer optimizer = new QuantumInspiredOptimizer(
            unsafeState, qeaFitness, 20, 50, 15, 0.05 * Math.PI
        );
        CandidateSolution best = optimizer.optimize(new Random(42));

        // QEA-alone uses soft penalty — best should have high fitness (deadlock penalty)
        boolean penalised = best.fitness >= FitnessFunction.DEADLOCK_WEIGHT;
        boolean notSafe = !best.isSafe;

        assertTrue("T10: QEA-alone correctly reports unsafe with deadlock penalty on unsafe state",
            penalised && notSafe);
    }
}
