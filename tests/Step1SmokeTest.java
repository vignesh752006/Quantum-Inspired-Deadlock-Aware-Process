/**
 * Step1SmokeTest.java
 * Quick verification that Process, Resource, and SystemState are wired correctly.
 * Run with: javac -d . src\*.java tests\Step1SmokeTest.java && java -cp . Step1SmokeTest
 */
public class Step1SmokeTest {

    public static void main(String[] args) {
        int passed = 0, failed = 0;

        // ------------------------------------------------------------------
        // 1. Classic Silberschatz 5-process, 3-resource example
        //    (Banker chapter, OS Concepts 10th ed., Table 8.3)
        //
        //    Resources:  A=10, B=5, C=7
        //    Allocation:     Maximum:
        //    P0: 0,1,0       7,5,3
        //    P1: 2,0,0       3,2,2
        //    P2: 3,0,2       9,0,2
        //    P3: 2,1,1       2,2,2
        //    P4: 0,0,2       4,3,3
        //    Available: 3,3,2
        // ------------------------------------------------------------------

        int[][] alloc = {
            {0,1,0},{2,0,0},{3,0,2},{2,1,1},{0,0,2}
        };
        int[][] max = {
            {7,5,3},{3,2,2},{9,0,2},{2,2,2},{4,3,3}
        };
        int[] avail = {3, 3, 2};
        int[] totals = {10, 5, 7};

        Process[] procs = new Process[5];
        for (int i = 0; i < 5; i++) procs[i] = new Process(i, "P"+i, alloc[i], max[i]);

        Resource[] res = {
            new Resource(0, "A", totals[0], avail[0]),
            new Resource(1, "B", totals[1], avail[1]),
            new Resource(2, "C", totals[2], avail[2])
        };

        SystemState state = new SystemState(procs, res, alloc, max, avail, "silberschatz-5p-3r");
        state.printTable();

        // Test 1: need matrix values
        int[][] expectedNeed = {
            {7,4,3},{1,2,2},{6,0,0},{0,1,1},{4,3,1}
        };
        boolean needOk = true;
        for (int i = 0; i < 5; i++)
            for (int j = 0; j < 3; j++)
                if (state.getNeed(i,j) != expectedNeed[i][j]) needOk = false;

        report("Need matrix matches Silberschatz values", needOk);
        if (needOk) passed++; else failed++;

        // Test 2: overall utilization
        //   Allocated = sum(alloc) per resource: A=7, B=2, C=5
        //   Total = 10,5,7  → utilization = (7+2+5)/(10+5+7) = 14/22 ≈ 0.636
        double util = state.getOverallUtilization();
        boolean utilOk = Math.abs(util - 14.0/22.0) < 1e-9;
        report("Overall utilization = 14/22", utilOk);
        if (utilOk) passed++; else failed++;

        // Test 3: deepCopy isolation
        SystemState copy = state.deepCopy();
        boolean isolationOk = (copy.getAvailable(0) == state.getAvailable(0))
                           && (copy != state);
        report("deepCopy() produces independent object with same data", isolationOk);
        if (isolationOk) passed++; else failed++;

        // Test 4: Process.canAllocate()
        int[] work = {3, 3, 2};
        // P1 need=[1,2,2] <= [3,3,2] → true
        // P0 need=[7,4,3] <= [3,3,2] → false
        boolean canAllocOk = procs[1].canAllocate(work) && !procs[0].canAllocate(work);
        report("Process.canAllocate() correct for P1 (true) and P0 (false)", canAllocOk);
        if (canAllocOk) passed++; else failed++;

        // Test 5: conservation invariant — deliberate violation should throw
        boolean invariantCaught = false;
        try {
            // Break conservation: available[0]=99 != 10 - 7 = 3
            new SystemState(procs, res, alloc, max, new int[]{99,3,2}, "bad-state");
        } catch (IllegalArgumentException e) {
            invariantCaught = true;
        }
        report("SystemState catches conservation-invariant violation", invariantCaught);
        if (invariantCaught) passed++; else failed++;

        // Test 6: need < 0 should throw
        boolean needNegCaught = false;
        int[][] badAlloc = deepCopy(alloc);
        badAlloc[0][0] = 8; // P0 alloc[0]=8 > max[0]=7
        try {
            new SystemState(procs, res, badAlloc, max, avail, "bad-need");
        } catch (IllegalArgumentException e) {
            needNegCaught = true;
        }
        report("SystemState catches negative-need (alloc > max)", needNegCaught);
        if (needNegCaught) passed++; else failed++;

        // Test 7: Resource utilization
        boolean resUtilOk = Math.abs(res[0].getUtilization() - 7.0/10.0) < 1e-9;
        report("Resource A utilization = 7/10", resUtilOk);
        if (resUtilOk) passed++; else failed++;

        // Summary
        System.out.println("\n============================");
        System.out.println("PASSED: " + passed + "  FAILED: " + failed);
        System.out.println("============================");
        if (failed > 0) System.exit(1);
    }

    private static void report(String label, boolean ok) {
        System.out.printf("[%s] %s%n", ok ? "PASS" : "FAIL", label);
    }

    private static int[][] deepCopy(int[][] src) {
        int[][] c = new int[src.length][];
        for (int i = 0; i < src.length; i++) c[i] = src[i].clone();
        return c;
    }
}
