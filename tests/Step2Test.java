import java.util.Arrays;

/**
 * Step2Test.java
 *
 * Verifies TestCaseGenerator correctness WITHOUT using BankersAlgorithm
 * (which is built in Step 4).  Instead we apply the Banker safety-check
 * inline so this test is self-contained.
 *
 * Tests:
 *  T1  Same seed → identical state (reproducibility)
 *  T2  Different seed → different state
 *  T3  Safe state passes inline Banker safety check
 *  T4  Unsafe state fails inline Banker safety check (ring detected)
 *  T5  generateSafe passes for every benchmark size
 *  T6  generateUnsafe fails for every benchmark size
 *  T7  generateRandom respects conservation invariant (via SystemState ctor)
 *  T8  Need matrix is non-negative in all generated states
 *  T9  Safe state: need[ring-process][nextRes] > available[nextRes] in unsafe
 *  T10 Label strings are correctly formatted
 *
 * Compile + run:
 *   javac -d . src\*.java tests\Step2Test.java
 *   java  -cp . Step2Test
 */
public class Step2Test {

    // Inline Banker safety check (mirrors the real BankersAlgorithm in Step 4)
    private static boolean isSafe(SystemState s) {
        int n = s.getNumProcesses(), m = s.getNumResources();
        int[] work   = s.getAvailableVector();
        boolean[] finish = new boolean[n];
        int count = 0;

        while (count < n) {
            boolean found = false;
            for (int i = 0; i < n; i++) {
                if (!finish[i]) {
                    boolean fits = true;
                    for (int j = 0; j < m; j++) {
                        if (s.getNeed(i, j) > work[j]) { fits = false; break; }
                    }
                    if (fits) {
                        for (int j = 0; j < m; j++) work[j] += s.getAllocation(i, j);
                        finish[i] = true;
                        found = true;
                        count++;
                    }
                }
            }
            if (!found) break;
        }
        return count == n;
    }

    // Check need >= 0 everywhere
    private static boolean needNonNegative(SystemState s) {
        for (int i = 0; i < s.getNumProcesses(); i++)
            for (int j = 0; j < s.getNumResources(); j++)
                if (s.getNeed(i, j) < 0) return false;
        return true;
    }

    // Check conservation invariant manually
    private static boolean conservationHolds(SystemState s) {
        for (int j = 0; j < s.getNumResources(); j++) {
            int sum = s.getAvailable(j);
            for (int i = 0; i < s.getNumProcesses(); i++) sum += s.getAllocation(i, j);
            if (sum != s.getTotal(j)) return false;
        }
        return true;
    }

    // Simple comparator: are two states' need matrices identical?
    private static boolean needMatricesEqual(SystemState a, SystemState b) {
        if (a.getNumProcesses() != b.getNumProcesses()) return false;
        if (a.getNumResources() != b.getNumResources()) return false;
        for (int i = 0; i < a.getNumProcesses(); i++)
            for (int j = 0; j < a.getNumResources(); j++)
                if (a.getNeed(i,j) != b.getNeed(i,j)) return false;
        return true;
    }

    // -------------------------------------------------------------------------

    public static void main(String[] args) {
        int passed = 0, failed = 0;

        // T1: Reproducibility — same seed, same state
        SystemState s1a = TestCaseGenerator.generateSafe(10, 5, 42L);
        SystemState s1b = TestCaseGenerator.generateSafe(10, 5, 42L);
        boolean t1 = needMatricesEqual(s1a, s1b);
        report("T1  Same seed → identical need matrix", t1);
        if (t1) passed++; else failed++;

        // T2: Different seeds → different states
        SystemState s2  = TestCaseGenerator.generateSafe(10, 5, 99L);
        boolean t2 = !needMatricesEqual(s1a, s2);
        report("T2  Different seed → different need matrix", t2);
        if (t2) passed++; else failed++;

        // T3: generateSafe passes inline Banker
        SystemState safe = TestCaseGenerator.generateSafe(20, 5, 7L);
        boolean t3 = isSafe(safe);
        report("T3  generateSafe(20,5,7) passes inline Banker", t3);
        if (t3) passed++; else failed++;

        // T4: generateUnsafe fails inline Banker
        SystemState unsafe = TestCaseGenerator.generateUnsafe(20, 5, 7L);
        boolean t4 = !isSafe(unsafe);
        report("T4  generateUnsafe(20,5,7) fails  inline Banker", t4);
        if (t4) passed++; else failed++;

        // T5: generateSafe passes for all benchmark sizes
        System.out.println("\n  [T5] Checking generateSafe on all benchmark sizes...");
        boolean t5 = true;
        for (int[] cfg : TestCaseGenerator.BENCHMARK_SIZES) {
            int n = cfg[0], m = cfg[1];
            SystemState ss = TestCaseGenerator.generateSafe(n, m, 1234L);
            boolean ok = isSafe(ss) && needNonNegative(ss) && conservationHolds(ss);
            System.out.printf("       n=%-4d m=%-3d  safe=%b  needOk=%b  conservation=%b%n",
                n, m, isSafe(ss), needNonNegative(ss), conservationHolds(ss));
            if (!ok) t5 = false;
        }
        report("T5  generateSafe passes for ALL benchmark sizes", t5);
        if (t5) passed++; else failed++;

        // T6: generateUnsafe fails for all benchmark sizes
        System.out.println("\n  [T6] Checking generateUnsafe on all benchmark sizes...");
        boolean t6 = true;
        for (int[] cfg : TestCaseGenerator.BENCHMARK_SIZES) {
            int n = cfg[0], m = cfg[1];
            SystemState su = TestCaseGenerator.generateUnsafe(n, m, 1234L);
            boolean unsafe_ok = !isSafe(su) && needNonNegative(su) && conservationHolds(su);
            System.out.printf("       n=%-4d m=%-3d  safe=%b  needOk=%b  conservation=%b%n",
                n, m, isSafe(su), needNonNegative(su), conservationHolds(su));
            if (!unsafe_ok) t6 = false;
        }
        report("T6  generateUnsafe fails  for ALL benchmark sizes", t6);
        if (t6) passed++; else failed++;

        // T7: generateRandom respects conservation (SystemState ctor enforces it)
        boolean t7 = true;
        for (long seed = 0; seed < 20; seed++) {
            SystemState sr = TestCaseGenerator.generateRandom(50, 10, seed);
            if (!conservationHolds(sr) || !needNonNegative(sr)) { t7 = false; break; }
        }
        report("T7  generateRandom conservation + need≥0 over 20 seeds", t7);
        if (t7) passed++; else failed++;

        // T8: Need matrix non-negative for safe generator over many seeds
        boolean t8 = true;
        for (long seed = 0; seed < 20; seed++) {
            if (!needNonNegative(TestCaseGenerator.generateSafe(50, 10, seed))) {
                t8 = false; break;
            }
        }
        report("T8  Need matrix non-negative in generateSafe (20 seeds)", t8);
        if (t8) passed++; else failed++;

        // T9: Unsafe state — ring processes cannot get needed resources
        SystemState us = TestCaseGenerator.generateUnsafe(10, 5, 555L);
        // ring size k = min(10, 5, 10) = 5
        // resource j in ring: available[j] = 0
        boolean t9 = true;
        for (int j = 0; j < Math.min(5, us.getNumResources()); j++) {
            if (us.getAvailable(j) != 0) { t9 = false; break; }
        }
        report("T9  Unsafe ring: available[j]=0 for all ring resource types", t9);
        if (t9) passed++; else failed++;

        // T10: Label formatting
        SystemState ls = TestCaseGenerator.generateSafe(10, 5, 42L);
        boolean t10 = ls.getLabel().startsWith("SAFE-n10-m5-seed42");
        report("T10 Label correctly formatted for generateSafe", t10);
        if (t10) passed++; else failed++;

        // Summary
        System.out.println("\n============================");
        System.out.printf("PASSED: %d  FAILED: %d%n", passed, failed);
        System.out.println("============================");
        if (failed > 0) System.exit(1);
    }

    private static void report(String label, boolean ok) {
        System.out.printf("[%s] %s%n", ok ? "PASS" : "FAIL", label);
    }
}
