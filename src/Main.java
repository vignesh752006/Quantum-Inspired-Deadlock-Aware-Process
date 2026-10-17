/**
 * Main.java — Console demonstration runner for Phase 1 (Steps 1 & 2)
 *
 * Run:  javac -d . src\*.java && java -cp . Main
 */
public class Main {

    public static void main(String[] args) {
        banner("OSDS Phase 1 — Quantum-Inspired Deadlock-Aware Scheduling");

        // ── DEMO 1: Classic Silberschatz example ──────────────────────────
        section("Demo 1: Classic Silberschatz 5-Process 3-Resource State");

        int[][] alloc = {{0,1,0},{2,0,0},{3,0,2},{2,1,1},{0,0,2}};
        int[][] max   = {{7,5,3},{3,2,2},{9,0,2},{2,2,2},{4,3,3}};
        int[]   avail = {3,3,2};
        int[]   total = {10,5,7};

        Process[]  procs = new Process[5];
        Resource[] res   = new Resource[3];
        for (int i = 0; i < 5; i++) procs[i] = new Process(i,"P"+i,alloc[i],max[i]);
        res[0] = new Resource(0,"A",10,3);
        res[1] = new Resource(1,"B",5,3);
        res[2] = new Resource(2,"C",7,2);

        SystemState classic = new SystemState(procs,res,alloc,max,avail,"silberschatz-5p-3r");
        classic.printTable();

        System.out.println("  Inline safety result: " + (isSafe(classic) ? "✓ SAFE" : "✗ UNSAFE"));
        System.out.println("  deepCopy() isolation : " + (classic.deepCopy() != classic ? "✓ OK" : "✗ FAIL"));

        // ── DEMO 2: Generated SAFE states at all benchmark sizes ───────────
        section("Demo 2: Generated SAFE States — All 6 Benchmark Sizes");
        System.out.printf("%-8s %-10s %-14s %-12s %-10s%n",
            "n", "m", "label-prefix", "safe?", "utilization");
        System.out.println("-".repeat(60));

        for (int[] cfg : TestCaseGenerator.BENCHMARK_SIZES) {
            int n = cfg[0], m = cfg[1];
            SystemState s = TestCaseGenerator.generateSafe(n, m, 42L);
            System.out.printf("%-8d %-10d %-14s %-12s %.1f%%%n",
                n, m, "SAFE",
                isSafe(s) ? "✓ SAFE" : "✗ UNSAFE",
                s.getOverallUtilization() * 100);
        }

        // ── DEMO 3: Generated UNSAFE states ───────────────────────────────
        section("Demo 3: Generated UNSAFE (Deadlocked) States — All 6 Sizes");
        System.out.printf("%-8s %-10s %-14s %-12s%n","n","m","label-prefix","safe?");
        System.out.println("-".repeat(50));

        for (int[] cfg : TestCaseGenerator.BENCHMARK_SIZES) {
            int n = cfg[0], m = cfg[1];
            SystemState u = TestCaseGenerator.generateUnsafe(n, m, 42L);
            System.out.printf("%-8d %-10d %-14s %-12s%n",
                n, m, "UNSAFE",
                isSafe(u) ? "✓ SAFE" : "✗ DEADLOCKED");
        }

        // ── DEMO 4: Reproducibility proof ─────────────────────────────────
        section("Demo 4: Reproducibility — Same Seed Produces Identical States");
        SystemState r1 = TestCaseGenerator.generateSafe(20, 5, 7777L);
        SystemState r2 = TestCaseGenerator.generateSafe(20, 5, 7777L);
        boolean same = true;
        for (int i = 0; i < 20 && same; i++)
            for (int j = 0; j < 5 && same; j++)
                if (r1.getNeed(i,j) != r2.getNeed(i,j)) same = false;
        System.out.println("  Seed 7777, run 1 vs run 2 — matrices identical: " + (same ? "✓ YES" : "✗ NO"));

        // ── DEMO 5: deepCopy fairness check ───────────────────────────────
        section("Demo 5: deepCopy() Benchmark Fairness");
        SystemState master = TestCaseGenerator.generateSafe(10, 5, 999L);
        SystemState copyA  = master.deepCopy();
        SystemState copyB  = master.deepCopy();
        System.out.println("  master != copyA  : " + (master != copyA ? "✓" : "✗"));
        System.out.println("  copyA  != copyB  : " + (copyA  != copyB ? "✓" : "✗"));
        System.out.println("  All need values match: " + needEqual(master,copyA) + " / " + needEqual(master,copyB));

        // ── DEMO 6: Resource Allocation Graph + Cycle Detection ───────────
        section("Demo 6: RAG + Cycle Detection");

        // 6a: 2-process deadlock ring
        SystemState ring = makeState(
            new String[]{"P0","P1"}, new String[]{"R0","R1"},
            new int[]{1,1},
            new int[][]{{1,0},{0,1}},
            new int[][]{{1,1},{1,1}},
            new int[]{0,0}
        );
        ResourceAllocationGraph ragRing = new ResourceAllocationGraph(ring);
        ragRing.printAdjacencyList();
        CycleDetector.CycleResult rRing = CycleDetector.detect(ragRing);
        CycleDetector.printReport(ragRing, rRing);

        // 6b: Silberschatz — cycle in RAG but state is SAFE (multi-instance nuance)
        int[][] alloc2 = {{0,1,0},{2,0,0},{3,0,2},{2,1,1},{0,0,2}};
        int[][] max2   = {{7,5,3},{3,2,2},{9,0,2},{2,2,2},{4,3,3}};
        int[]   avail2 = {3,3,2}, total2 = {10,5,7};
        Process[]  p2 = new Process[5];
        Resource[] res2 = {new Resource(0,"A",10,3),new Resource(1,"B",5,3),new Resource(2,"C",7,2)};
        for (int i=0;i<5;i++) p2[i]=new Process(i,"P"+i,alloc2[i],max2[i]);
        SystemState silb2 = new SystemState(p2,res2,alloc2,max2,avail2,"silberschatz-5p-3r");
        ResourceAllocationGraph ragSilb = new ResourceAllocationGraph(silb2);
        CycleDetector.CycleResult rSilb = CycleDetector.detect(ragSilb);
        System.out.println("  Silberschatz RAG cycle: " + rSilb.hasCycle
            + "  ← multi-instance; Banker needed for definitive verdict");

        // ── DEMO 7: Banker's Algorithm (Step 4) ───────────────────────────
        section("Demo 7: Banker's Algorithm Full Safety Check & Trace");
        BankersAlgorithm.BankersResult bRes = BankersAlgorithm.checkSafety(classic);
        BankersAlgorithm.printReport(classic, bRes);

        // ── DEMO 8: Quantum-Inspired Evolutionary Algorithm (Step 5) ──────
        section("Demo 8: Quantum-Inspired Optimization (QEA Alone)");
        SystemState testState = TestCaseGenerator.generateSafe(10, 5, 42L);
        FitnessFunction fitness = new FitnessFunction();
        QuantumInspiredOptimizer optimizer = new QuantumInspiredOptimizer(
            testState, fitness, 20, 50, 15, 0.05 * Math.PI
        );
        CandidateSolution best = optimizer.optimize(12345L);
        optimizer.printReport();

        // ── DEMO 9: Three-Way Comparison (Banker vs QEA vs Hybrid) ────────
        section("Demo 9: Three-Way Algorithm Comparison (Same State)");
        SystemState compState = TestCaseGenerator.generateSafe(20, 5, 888L);
        System.out.printf("State: %s (n=%d, m=%d)%n%n",
            compState.getLabel(), compState.getNumProcesses(), compState.getNumResources());

        // 9a: Banker alone
        long t0 = System.nanoTime();
        BankersAlgorithm.BankersResult bankerRes = BankersAlgorithm.checkSafety(compState);
        long bankerTime = System.nanoTime() - t0;
        System.out.printf("  [Banker Alone]  Safe: %b  Time: %,d ns (%.3f ms)%n",
            bankerRes.safe, bankerTime, bankerTime / 1_000_000.0);

        // 9b: QEA alone (on deepCopy for fairness)
        SystemState qeaCopy = compState.deepCopy();
        FitnessFunction qeaFit = new FitnessFunction(false);
        QuantumInspiredOptimizer qeaOpt = new QuantumInspiredOptimizer(
            qeaCopy, qeaFit, 20, 100, 20, 0.05 * Math.PI
        );
        CandidateSolution qeaBest = qeaOpt.optimize(42L);
        System.out.printf("  [QEA Alone]     Safe: %b  Fitness: %.1f  Completed: %d/%d  Iters: %d  Time: %,d ns (%.3f ms)%n",
            qeaBest.isSafe, qeaBest.fitness, qeaBest.processesCompleted,
            compState.getNumProcesses(), qeaOpt.getIterationsRun(),
            qeaOpt.getExecutionTimeNs(), qeaOpt.getExecutionTimeNs() / 1_000_000.0);

        // 9c: Hybrid (on another deepCopy for fairness)
        SystemState hybridCopy = compState.deepCopy();
        FitnessFunction hybridFit = new FitnessFunction(true);
        QuantumInspiredOptimizer hybridOpt = new QuantumInspiredOptimizer(
            hybridCopy, hybridFit, 20, 100, 20, 0.05 * Math.PI
        );
        CandidateSolution hybridBest = hybridOpt.optimize(42L);
        System.out.printf("  [Banker+QEA]    Safe: %b  Fitness: %.1f  Completed: %d/%d  Iters: %d  Time: %,d ns (%.3f ms)%n",
            hybridBest.isSafe, hybridBest.fitness, hybridBest.processesCompleted,
            compState.getNumProcesses(), hybridOpt.getIterationsRun(),
            hybridOpt.getExecutionTimeNs(), hybridOpt.getExecutionTimeNs() / 1_000_000.0);

        System.out.printf("%n  Banker is %.1fx faster than QEA, %.1fx faster than Hybrid%n",
            (double) qeaOpt.getExecutionTimeNs() / bankerTime,
            (double) hybridOpt.getExecutionTimeNs() / bankerTime);

        banner("Steps 1–6 complete — awaiting Steps 7–8");
    }

    static SystemState makeState(String[] pnames, String[] rnames,
                                  int[] total, int[][] alloc, int[][] max, int[] avail) {
        int n=pnames.length, m=rnames.length;
        Process[] procs=new Process[n]; Resource[] res=new Resource[m];
        for(int i=0;i<n;i++) procs[i]=new Process(i,pnames[i],alloc[i],max[i]);
        for(int j=0;j<m;j++) res[j]=new Resource(j,rnames[j],total[j],avail[j]);
        return new SystemState(procs,res,alloc,max,avail,"demo");
    }

    // ── inline Banker safety (will be replaced by BankersAlgorithm in Step 4) ──
    static boolean isSafe(SystemState s) {
        int n = s.getNumProcesses(), m = s.getNumResources();
        int[] work = s.getAvailableVector();
        boolean[] finish = new boolean[n];
        int count = 0;
        while (count < n) {
            boolean found = false;
            for (int i = 0; i < n; i++) {
                if (!finish[i]) {
                    boolean fits = true;
                    for (int j = 0; j < m; j++) if (s.getNeed(i,j) > work[j]) { fits=false; break; }
                    if (fits) {
                        for (int j = 0; j < m; j++) work[j] += s.getAllocation(i,j);
                        finish[i]=true; found=true; count++;
                    }
                }
            }
            if (!found) break;
        }
        return count == n;
    }

    static String needEqual(SystemState a, SystemState b) {
        for (int i = 0; i < a.getNumProcesses(); i++)
            for (int j = 0; j < a.getNumResources(); j++)
                if (a.getNeed(i,j) != b.getNeed(i,j)) return "✗ NO";
        return "✓ YES";
    }

    static void banner(String msg) {
        String line = "═".repeat(msg.length() + 4);
        System.out.println("\n" + line);
        System.out.println("  " + msg);
        System.out.println(line + "\n");
    }

    static void section(String msg) {
        System.out.println("\n── " + msg + " " + "─".repeat(Math.max(0, 65 - msg.length())));
    }
}
