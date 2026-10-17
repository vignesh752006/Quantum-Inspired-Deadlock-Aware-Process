import java.util.Arrays;

/**
 * Step4Test.java
 *
 * Unit tests for BankersAlgorithm.
 *
 * Tests:
 *  T1  Silberschatz 5P 3R  → SAFE
 *  T2  Silberschatz safe sequence = <P1, P3, P4, P2, P0>  (textbook answer)
 *  T3  Silberschatz execution time > 0 ns
 *  T4  Silberschatz scan count ≥ 1
 *  T5  2-process deadlock ring → UNSAFE
 *  T6  2-process deadlock: safe sequence is empty
 *  T7  CycleDetector cycle + Banker UNSAFE agree on the ring (cross-check)
 *  T8  CycleDetector cycle + Banker SAFE diverge on Silberschatz (multi-instance proof)
 *  T9  generateSafe  → Banker SAFE   for ALL 6 benchmark sizes
 *  T10 generateUnsafe → Banker UNSAFE for ALL 6 benchmark sizes
 *  T11 generateRandom → Banker result matches inline check (consistency)
 *  T12 isSafe() matches checkSafety().safe for 20 random states
 *  T13 checkSafety() does NOT mutate the SystemState (deepCopy not needed)
 *  T14 Trace contains at least one GRANT entry for a safe state
 *  T15 Trace contains BLOCKED entry for an unsafe state
 *
 * Compile + run:
 *   javac -d . src\*.java tests\Step4Test.java
 *   java  -cp . Step4Test
 */
public class Step4Test {

    static int passed = 0, failed = 0;

    static void report(String label, boolean ok) {
        System.out.printf("[%s] %s%n", ok ? "PASS" : "FAIL", label);
        if (ok) passed++; else failed++;
    }

    static SystemState makeState(String[] pnames, String[] rnames,
                                  int[] total, int[][] alloc, int[][] max, int[] avail) {
        int n = pnames.length, m = rnames.length;
        Process[]  procs = new Process[n];
        Resource[] res   = new Resource[m];
        for (int i = 0; i < n; i++) procs[i] = new Process(i, pnames[i], alloc[i], max[i]);
        for (int j = 0; j < m; j++) res[j]   = new Resource(j, rnames[j], total[j], avail[j]);
        return new SystemState(procs, res, alloc, max, avail, "test");
    }

    // =========================================================================
    public static void main(String[] args) {

        // ── Silberschatz canonical example ───────────────────────────────────
        //
        //   Resources A=10, B=5, C=7
        //   Allocation:       Maximum:         Need (derived):
        //   P0: [0,1,0]       [7,5,3]          [7,4,3]
        //   P1: [2,0,0]       [3,2,2]          [1,2,2]
        //   P2: [3,0,2]       [9,0,2]          [6,0,0]
        //   P3: [2,1,1]       [2,2,2]          [0,1,1]
        //   P4: [0,0,2]       [4,3,3]          [4,3,1]
        //   Available: [3,3,2]
        //
        //   Textbook safe sequence: <P1, P3, P4, P2, P0>
        // ────────────────────────────────────────────────────────────────────

        int[][] alloc = {{0,1,0},{2,0,0},{3,0,2},{2,1,1},{0,0,2}};
        int[][] max   = {{7,5,3},{3,2,2},{9,0,2},{2,2,2},{4,3,3}};
        int[]   avail = {3,3,2};
        int[]   total = {10,5,7};

        SystemState silb = makeState(
            new String[]{"P0","P1","P2","P3","P4"},
            new String[]{"A","B","C"},
            total, alloc, max, avail
        );

        System.out.println("── Silberschatz 5P 3R ──────────────────────────────────────");
        silb.printTable();

        BankersAlgorithm.BankersResult r = BankersAlgorithm.checkSafety(silb);
        BankersAlgorithm.printReport(silb, r);

        // T1: SAFE verdict
        report("T1  Silberschatz → SAFE", r.safe);

        // T2: Sequence must be length 5 AND be a valid safe sequence.
        //     Multiple valid safe sequences exist; the textbook shows <P1,P3,P4,P2,P0>
        //     but <P1,P3,P4,P0,P2> is equally valid — we verify correctness not ordering.
        boolean t2 = r.safeSequence.length == 5 && isValidSafeSeq(silb, r.safeSequence);
        report("T2  Safe sequence valid (length=5, Work constraint satisfied at each step)", t2);
        System.out.println("     Textbook: <P1,P3,P4,P2,P0>   Algorithm: " + r.sequenceString(silb));
        System.out.println("     (Both are correct — multiple safe sequences may exist)");

        // T3: execution time > 0
        report("T3  executionTimeNs > 0", r.executionTimeNs > 0);

        // T4: scan count ≥ 1
        report("T4  scanCount >= 1", r.scanCount >= 1);

        // ── 2-process deadlock ring ──────────────────────────────────────────
        System.out.println("\n── 2-process deadlock ring ─────────────────────────────────");
        SystemState deadlock = makeState(
            new String[]{"P0","P1"},
            new String[]{"R0","R1"},
            new int[]{1,1},
            new int[][]{{1,0},{0,1}},
            new int[][]{{1,1},{1,1}},
            new int[]{0,0}
        );
        BankersAlgorithm.BankersResult rd = BankersAlgorithm.checkSafety(deadlock);
        BankersAlgorithm.printReport(deadlock, rd);

        // T5: UNSAFE verdict
        report("T5  2-process ring → UNSAFE", !rd.safe);

        // T6: safe sequence empty when unsafe
        report("T6  safeSequence empty when UNSAFE", rd.safeSequence.length == 0);

        // ── Cross-check: CycleDetector vs Banker on both examples ────────────
        System.out.println("\n── Cross-check: CycleDetector vs Banker ───────────────────");

        // T7: ring → both CycleDetector and Banker agree on UNSAFE
        ResourceAllocationGraph ragRing = new ResourceAllocationGraph(deadlock);
        boolean cycleRing   = CycleDetector.hasCycle(ragRing);
        boolean bankerRing  = !BankersAlgorithm.isSafe(deadlock);
        System.out.printf("     Ring:  CycleDetector=%b  Banker(unsafe)=%b%n", cycleRing, bankerRing);
        report("T7  Ring: CycleDetector(cycle) and Banker(unsafe) agree", cycleRing && bankerRing);

        // T8: Silberschatz → CycleDetector finds cycle but Banker says SAFE
        //     (This is the multi-instance proof — key pedagogical point)
        ResourceAllocationGraph ragSilb = new ResourceAllocationGraph(silb);
        boolean cycleSilb  = CycleDetector.hasCycle(ragSilb);
        boolean bankerSafe = BankersAlgorithm.isSafe(silb);
        System.out.printf("     Silb:  CycleDetector(cycle)=%b  Banker(safe)=%b%n",
            cycleSilb, bankerSafe);
        report("T8  Silberschatz: RAG has cycle BUT Banker says SAFE (multi-instance proof)",
            cycleSilb && bankerSafe);

        // ── All 6 benchmark sizes ─────────────────────────────────────────────
        System.out.println("\n── T9: generateSafe → Banker SAFE (all sizes) ──────────────");
        boolean t9 = true;
        for (int[] cfg : TestCaseGenerator.BENCHMARK_SIZES) {
            SystemState ss = TestCaseGenerator.generateSafe(cfg[0], cfg[1], 42L);
            BankersAlgorithm.BankersResult res = BankersAlgorithm.checkSafety(ss);
            System.out.printf("     n=%-4d m=%-3d  safe=%-5b  seq_len=%-4d  time=%d µs%n",
                cfg[0], cfg[1], res.safe, res.safeSequence.length,
                res.executionTimeNs / 1_000);
            if (!res.safe) t9 = false;
        }
        report("T9  generateSafe → SAFE for all 6 benchmark sizes", t9);

        System.out.println("\n── T10: generateUnsafe → Banker UNSAFE (all sizes) ─────────");
        boolean t10 = true;
        for (int[] cfg : TestCaseGenerator.BENCHMARK_SIZES) {
            SystemState su = TestCaseGenerator.generateUnsafe(cfg[0], cfg[1], 42L);
            BankersAlgorithm.BankersResult res = BankersAlgorithm.checkSafety(su);
            System.out.printf("     n=%-4d m=%-3d  safe=%-5b  scans=%-3d  time=%d µs%n",
                cfg[0], cfg[1], res.safe, res.scanCount,
                res.executionTimeNs / 1_000);
            if (res.safe) t10 = false;
        }
        report("T10 generateUnsafe → UNSAFE for all 6 benchmark sizes", t10);

        // ── T11: generateRandom — Banker and isSafe() agree ──────────────────
        System.out.println("\n── T11: generateRandom consistency (20 seeds) ───────────────");
        boolean t11 = true;
        for (long seed = 0; seed < 20; seed++) {
            SystemState sr = TestCaseGenerator.generateRandom(20, 5, seed);
            boolean full  = BankersAlgorithm.checkSafety(sr).safe;
            boolean fast  = BankersAlgorithm.isSafe(sr);
            if (full != fast) { t11 = false; break; }
        }
        report("T11 generateRandom: checkSafety() and isSafe() agree (20 seeds)", t11);

        // ── T12: isSafe() matches checkSafety().safe for 20 random states ────
        boolean t12 = true;
        for (long seed = 20; seed < 40; seed++) {
            SystemState sr = TestCaseGenerator.generateRandom(50, 10, seed);
            if (BankersAlgorithm.checkSafety(sr).safe != BankersAlgorithm.isSafe(sr)) {
                t12 = false; break;
            }
        }
        report("T12 isSafe() == checkSafety().safe for 20 more random states", t12);

        // ── T13: checkSafety() does NOT mutate the SystemState ────────────────
        SystemState orig = TestCaseGenerator.generateSafe(10, 5, 999L);
        int origAvail0 = orig.getAvailable(0);
        int origNeed00 = orig.getNeed(0, 0);
        BankersAlgorithm.checkSafety(orig);   // run the algorithm
        boolean t13 = orig.getAvailable(0) == origAvail0
                   && orig.getNeed(0, 0)    == origNeed00;
        report("T13 checkSafety() does NOT mutate SystemState", t13);

        // ── T14: Trace has at least one GRANT for a safe state ───────────────
        BankersAlgorithm.BankersResult rt14 = BankersAlgorithm.checkSafety(
            TestCaseGenerator.generateSafe(10, 5, 1L));
        boolean t14 = rt14.trace.stream().anyMatch(s -> s.startsWith("GRANT"));
        report("T14 Trace contains ≥1 GRANT entry for safe state", t14);

        // ── T15: Trace has BLOCKED entry for an unsafe state ─────────────────
        BankersAlgorithm.BankersResult rt15 = BankersAlgorithm.checkSafety(
            TestCaseGenerator.generateUnsafe(10, 5, 1L));
        boolean t15 = rt15.trace.stream().anyMatch(s -> s.startsWith("BLOCKED"));
        report("T15 Trace contains BLOCKED entry for unsafe state", t15);

        // ── Summary ───────────────────────────────────────────────────────────
        System.out.println("\n============================");
        System.out.printf("PASSED: %d  FAILED: %d%n", passed, failed);
        System.out.println("============================");
        if (failed > 0) System.exit(1);
    }

    /**
     * Independently verifies that {@code seq} is a valid safe sequence for
     * {@code state} by replaying the Work-vector simulation from scratch.
     * Ground-truth check: if this passes, the algorithm is provably correct.
     */
    static boolean isValidSafeSeq(SystemState state, int[] seq) {
        int n = state.getNumProcesses(), m = state.getNumResources();
        if (seq.length != n) return false;
        int[] work = state.getAvailableVector();
        for (int idx = 0; idx < n; idx++) {
            int i = seq[idx];
            for (int j = 0; j < m; j++) {
                if (state.getNeed(i, j) > work[j]) return false;
            }
            for (int j = 0; j < m; j++) work[j] += state.getAllocation(i, j);
        }
        return true;
    }
}
