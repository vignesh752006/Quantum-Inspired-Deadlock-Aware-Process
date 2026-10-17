import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Step5Test.java
 *
 * Exhaustive unit tests for Step 5 QEA components:
 *   - QuantumBit: superposition, rotation, clamping, probabilistic collapse
 *   - CandidateSolution: permutation validity, comparison, deepCopy
 *   - QuantumPopulation: observation, permutation generation, amplitude updates
 *   - FitnessFunction: multi-objective weights, safe vs unsafe scoring, waiting time
 *   - QuantumInspiredOptimizer: optimization loop, convergence, monotonicity, reproducibility
 *
 * Run:
 *   javac -d . src\*.java tests\Step5Test.java
 *   java Step5Test
 */
public class Step5Test {

    private static int testsRun    = 0;
    private static int testsPassed = 0;

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("  Running Step 5 Unit Tests (QEA Components)     ");
        System.out.println("=================================================");

        // QuantumBit tests
        testQubitSuperposition();
        testQubitRotationAndClamping();
        testQubitObservationDistribution();

        // CandidateSolution tests
        testCandidateSolutionPermutationAndCompare();
        testCandidateSolutionDeepCopy();

        // QuantumPopulation tests
        testPopulationObservationPermutations();
        testPopulationAmplitudeConvergence();

        // FitnessFunction tests
        testFitnessFunctionSafeState();
        testFitnessFunctionUnsafeStatePenalty();

        // QuantumInspiredOptimizer tests
        testOptimizerOnKnownSafeState();
        testOptimizerFitnessMonotonicity();
        testOptimizerReproducibilityWithSeed();

        System.out.println("=================================================");
        System.out.printf("  Summary: %d / %d tests passed.%n", testsPassed, testsRun);
        System.out.println("=================================================");

        if (testsPassed != testsRun) {
            System.err.println("SOME STEP 5 TESTS FAILED!");
            System.exit(1);
        } else {
            System.out.println("ALL STEP 5 TESTS PASSED SUCCESSFULLY! (11/11)");
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

    // ── Tests ─────────────────────────────────────────────────────────────────

    private static void testQubitSuperposition() {
        QuantumBit qb = new QuantumBit();
        double sum = qb.getAlphaSquared() + qb.getBetaSquared();
        boolean valid = Math.abs(sum - 1.0) < 1e-9 &&
                        Math.abs(qb.getAlphaSquared() - 0.5) < 1e-9 &&
                        Math.abs(qb.getTheta() - Math.PI / 4.0) < 1e-9;
        assertTrue("T1: QuantumBit initial superposition θ=π/4, α²=β²=0.5, α²+β²=1", valid);
    }

    private static void testQubitRotationAndClamping() {
        QuantumBit qb = new QuantumBit();
        double initialAlpha2 = qb.getAlphaSquared();

        // Rotate toward bit 0 -> alpha^2 should increase
        qb.rotateToBit0(0.1);
        boolean increasedAlpha = qb.getAlphaSquared() > initialAlpha2;

        // Severe rotation beyond bounds -> must clamp without NaN or exception
        qb.rotateToBit0(100.0);
        boolean clampedMin = qb.getTheta() >= 0.001;

        qb.rotateToBit1(100.0);
        boolean clampedMax = qb.getTheta() <= Math.PI / 2.0 - 0.001;

        assertTrue("T2: QuantumBit rotation increases targeted amplitude and clamps safely",
            increasedAlpha && clampedMin && clampedMax);
    }

    private static void testQubitObservationDistribution() {
        QuantumBit qb = new QuantumBit(); // 50/50
        Random rng = new Random(42);
        int trials = 10_000;
        int zeros = 0;
        for (int i = 0; i < trials; i++) {
            if (qb.observeBit(rng.nextDouble()) == 0) zeros++;
        }
        double ratio = (double) zeros / trials;
        // Should be approximately 0.50 (+/- 0.03)
        boolean valid = Math.abs(ratio - 0.50) < 0.03;
        assertTrue("T3: QuantumBit observeBit collapses with expected probability distribution", valid);
    }

    private static void testCandidateSolutionPermutationAndCompare() {
        int[] order = {2, 0, 1};
        int[] bits = {0, 1, 0};
        CandidateSolution sol1 = new CandidateSolution(order, bits);
        CandidateSolution sol2 = new CandidateSolution(order, bits);
        sol1.fitness = 150.0;
        sol2.fitness = 200.0;

        boolean comparisonValid = sol1.compareTo(sol2) < 0 && sol1.isBetterThan(sol2);
        assertTrue("T4: CandidateSolution natural ordering prefers lower fitness", comparisonValid);
    }

    private static void testCandidateSolutionDeepCopy() {
        int[] order = {0, 1, 2};
        int[] bits = {1, 0, 1};
        CandidateSolution original = new CandidateSolution(order, bits);
        original.fitness = 42.0;
        original.processesCompleted = 3;
        original.isSafe = true;

        CandidateSolution copy = original.deepCopy();
        copy.fitness = 999.0;
        copy.isSafe = false;

        boolean isolated = (original.fitness == 42.0) && original.isSafe && (copy.fitness == 999.0);
        assertTrue("T5: CandidateSolution deepCopy isolates mutation", isolated);
    }

    private static void testPopulationObservationPermutations() {
        int popSize = 20;
        int n = 15;
        QuantumPopulation pop = new QuantumPopulation(popSize, n);
        Random rng = new Random(123);
        CandidateSolution[] solutions = pop.observe(rng);

        boolean allValidPermutations = (solutions.length == popSize);
        for (CandidateSolution sol : solutions) {
            if (sol.order.length != n) allValidPermutations = false;
            Set<Integer> unique = new HashSet<>();
            for (int p : sol.order) {
                if (p < 0 || p >= n) allValidPermutations = false;
                unique.add(p);
            }
            if (unique.size() != n) allValidPermutations = false;
        }

        assertTrue("T6: QuantumPopulation observe produces strictly valid permutations for all individuals",
            allValidPermutations);
    }

    private static void testPopulationAmplitudeConvergence() {
        int popSize = 5;
        int n = 4;
        QuantumPopulation pop = new QuantumPopulation(popSize, n);
        // Best solution observed all 0s
        int[] order = {0, 1, 2, 3};
        int[] allZeroBits = {0, 0, 0, 0};
        CandidateSolution best = new CandidateSolution(order, allZeroBits);

        // Current candidate observed all 1s
        int[] allOneBits = {1, 1, 1, 1};
        CandidateSolution current = new CandidateSolution(order, allOneBits);

        double alphaBefore = pop.getQubit(0, 0).getAlphaSquared();
        for (int step = 0; step < 10; step++) {
            pop.updateAmplitudes(0, best, current, 0.05 * Math.PI);
        }
        double alphaAfter = pop.getQubit(0, 0).getAlphaSquared();

        assertTrue("T7: Q-gate update shifts amplitudes toward target bit (α² increases toward 1)",
            alphaAfter > alphaBefore && alphaAfter > 0.80);
    }

    private static void testFitnessFunctionSafeState() {
        SystemState safeState = TestCaseGenerator.generateSafe(5, 3, 101);
        FitnessFunction fitness = new FitnessFunction();

        // Try Banker's safe sequence
        BankersAlgorithm.BankersResult bResult = BankersAlgorithm.checkSafety(safeState);
        assertTrue("Pre-condition: safe state is confirmed safe by Banker", bResult.safe);

        CandidateSolution sol = new CandidateSolution(bResult.safeSequence, new int[5]);
        double score = fitness.evaluate(sol, safeState);

        boolean perfectlySafe = (sol.processesCompleted == 5) &&
                                sol.isSafe &&
                                (score < 100.0); // No deadlock or unfinished penalties
        assertTrue("T8: FitnessFunction evaluates safe sequence with 0 deadlock/unfinished penalty", perfectlySafe);
    }

    private static void testFitnessFunctionUnsafeStatePenalty() {
        SystemState unsafeState = TestCaseGenerator.generateUnsafe(6, 4, 202);
        FitnessFunction fitness = new FitnessFunction();

        int[] naiveOrder = {0, 1, 2, 3, 4, 5};
        CandidateSolution sol = new CandidateSolution(naiveOrder, new int[6]);
        double score = fitness.evaluate(sol, unsafeState);

        boolean penalized = !sol.isSafe &&
                            (sol.processesCompleted < 6) &&
                            (score >= FitnessFunction.DEADLOCK_WEIGHT); // >= 1000 penalty
        assertTrue("T9: FitnessFunction penalizes deadlocked state with DEADLOCK_WEIGHT (>= 1000)", penalized);
    }

    private static void testOptimizerOnKnownSafeState() {
        SystemState safeState = TestCaseGenerator.generateSafe(10, 5, 303);
        FitnessFunction fitness = new FitnessFunction();
        QuantumInspiredOptimizer optimizer = new QuantumInspiredOptimizer(
            safeState, fitness, 20, 50, 15, 0.05 * Math.PI
        );

        CandidateSolution best = optimizer.optimize(new Random(777));
        boolean foundSafe = best != null && best.isSafe && best.processesCompleted == 10;
        assertTrue("T10: QuantumInspiredOptimizer discovers a valid safe schedule for safe state", foundSafe);
    }

    private static void testOptimizerFitnessMonotonicity() {
        SystemState state = TestCaseGenerator.generateRandom(10, 4, 404);
        FitnessFunction fitness = new FitnessFunction();
        QuantumInspiredOptimizer optimizer = new QuantumInspiredOptimizer(
            state, fitness, 15, 30, 10, 0.05 * Math.PI
        );

        optimizer.optimize(new Random(888));
        java.util.List<Double> history = optimizer.getBestFitnessHistory();

        boolean nonIncreasing = true;
        for (int i = 1; i < history.size(); i++) {
            if (history.get(i) > history.get(i - 1) + 1e-9) {
                nonIncreasing = false;
                break;
            }
        }

        assertTrue("T11: Optimizer best fitness trajectory is strictly non-increasing across generations",
            nonIncreasing && !history.isEmpty());
    }

    private static void testOptimizerReproducibilityWithSeed() {
        SystemState state = TestCaseGenerator.generateRandom(8, 3, 505);
        FitnessFunction fitness = new FitnessFunction();

        QuantumInspiredOptimizer opt1 = new QuantumInspiredOptimizer(state, fitness, 10, 20, 10, 0.05 * Math.PI);
        CandidateSolution best1 = opt1.optimize(new Random(99999));

        QuantumInspiredOptimizer opt2 = new QuantumInspiredOptimizer(state, fitness, 10, 20, 10, 0.05 * Math.PI);
        CandidateSolution best2 = opt2.optimize(new Random(99999));

        boolean match = Math.abs(best1.fitness - best2.fitness) < 1e-9 &&
                        Arrays.equals(best1.order, best2.order);
        assertTrue("T12: Optimizer output is perfectly deterministic when using identical seed", match);
    }
}
