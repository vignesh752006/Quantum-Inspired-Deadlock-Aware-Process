import java.util.Arrays;

/**
 * SystemState.java
 *
 * Captures the complete resource-allocation state of the system at a single
 * point in time.  This is the central data object passed (as a deep copy)
 * to every algorithm — Banker, QEA, and the Hybrid — guaranteeing that all
 * three operate on strictly identical inputs.
 *
 * Contained matrices (n = processes, m = resource types):
 *   allocation[n][m]  — resources currently held by each process
 *   maximum[n][m]     — declared maximum demand of each process
 *   need[n][m]        — remaining need = maximum - allocation (derived)
 *   available[m]      — free resource instances not held by any process
 *
 * Invariants enforced at construction:
 *   1. need[i][j] >= 0 for all i, j
 *   2. sum(allocation[i][j] for all i) + available[j] == total[j] for all j
 *   3. All dimensions are consistent (same n and m throughout)
 *
 * Phase-2 design:
 *   A factory method {@code fromAgentReport(AgentReport)} will be added in
 *   Phase 2 to build a SystemState from live VM agent data.  The constructor
 *   and all internal logic remain unchanged.
 *
 * @author  OSDS-CP Group
 * @version 1.0  (Phase 1 – simulated data)
 */
public class SystemState {

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /** Number of processes (rows). */
    private final int numProcesses;

    /** Number of resource types (columns). */
    private final int numResources;

    /** Process metadata array (indexed by process id 0..n-1). */
    private final Process[] processes;

    /** Resource metadata array (indexed by resource id 0..m-1). */
    private final Resource[] resources;

    /**
     * Allocation matrix [n][m].
     * allocation[i][j] = instances of resource j held by process i.
     */
    private final int[][] allocation;

    /**
     * Maximum matrix [n][m].
     * maximum[i][j] = max instances of resource j that process i may ever request.
     */
    private final int[][] maximum;

    /**
     * Need matrix [n][m] — derived: need[i][j] = maximum[i][j] - allocation[i][j].
     */
    private final int[][] need;

    /**
     * Available vector [m].
     * available[j] = free instances of resource j.
     */
    private final int[] available;

    /**
     * Total instances per resource type [m].
     * Stored separately to support the invariant check and utilization metrics.
     */
    private final int[] total;

    /**
     * Optional label for this test case (e.g. "safe-10p-5r-seed42").
     * Used in CSV/JSON export and log messages.
     */
    private final String label;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Builds a SystemState from raw 2-D matrices.
     *
     * @param processes  array of Process objects (must not be null; length == n)
     * @param resources  array of Resource objects (must not be null; length == m)
     * @param allocation allocation matrix [n][m] — defensive copy taken
     * @param maximum    maximum matrix [n][m]    — defensive copy taken
     * @param available  available vector [m]     — defensive copy taken
     * @param label      optional descriptive label (may be null)
     * @throws IllegalArgumentException if any invariant is violated
     */
    public SystemState(Process[] processes, Resource[] resources,
                       int[][] allocation, int[][] maximum,
                       int[] available, String label) {

        if (processes == null || resources == null || allocation == null
                || maximum == null || available == null) {
            throw new IllegalArgumentException("SystemState: no argument may be null.");
        }

        this.numProcesses = processes.length;
        this.numResources = resources.length;

        if (numProcesses == 0) throw new IllegalArgumentException("SystemState: must have at least one process.");
        if (numResources == 0) throw new IllegalArgumentException("SystemState: must have at least one resource type.");

        // Validate matrix dimensions
        if (allocation.length != numProcesses || maximum.length != numProcesses) {
            throw new IllegalArgumentException(
                "SystemState: allocation/maximum row count must equal numProcesses=" + numProcesses);
        }
        for (int i = 0; i < numProcesses; i++) {
            if (allocation[i].length != numResources) {
                throw new IllegalArgumentException(
                    "SystemState: allocation[" + i + "].length != numResources=" + numResources);
            }
            if (maximum[i].length != numResources) {
                throw new IllegalArgumentException(
                    "SystemState: maximum[" + i + "].length != numResources=" + numResources);
            }
        }
        if (available.length != numResources) {
            throw new IllegalArgumentException(
                "SystemState: available.length != numResources=" + numResources);
        }

        // Defensive deep copies
        this.allocation = deepCopy2D(allocation);
        this.maximum    = deepCopy2D(maximum);
        this.available  = available.clone();

        // Build need matrix and validate need >= 0
        this.need = new int[numProcesses][numResources];
        for (int i = 0; i < numProcesses; i++) {
            for (int j = 0; j < numResources; j++) {
                int n = this.maximum[i][j] - this.allocation[i][j];
                if (n < 0) {
                    throw new IllegalArgumentException(
                        "SystemState: need[" + i + "][" + j + "] = "
                        + this.maximum[i][j] + " - " + this.allocation[i][j]
                        + " = " + n + " < 0. allocation cannot exceed maximum.");
                }
                this.need[i][j] = n;
            }
        }

        // Build total[] from resource metadata
        this.total = new int[numResources];
        for (int j = 0; j < numResources; j++) {
            this.total[j] = resources[j].getTotalInstances();
        }

        // Validate conservation invariant: sum(alloc[i][j]) + available[j] == total[j]
        for (int j = 0; j < numResources; j++) {
            int sumAllocated = 0;
            for (int i = 0; i < numProcesses; i++) {
                sumAllocated += this.allocation[i][j];
            }
            if (sumAllocated + this.available[j] != this.total[j]) {
                throw new IllegalArgumentException(
                    "SystemState: conservation violated for resource " + j
                    + ". sum(alloc)=" + sumAllocated
                    + " + available=" + this.available[j]
                    + " != total=" + this.total[j]);
            }
        }

        // Defensive copies of metadata arrays
        this.processes = new Process[numProcesses];
        for (int i = 0; i < numProcesses; i++) {
            this.processes[i] = processes[i].deepCopy();
        }
        this.resources = new Resource[numResources];
        for (int j = 0; j < numResources; j++) {
            this.resources[j] = resources[j].deepCopy();
        }

        this.label = (label != null) ? label : "unlabelled";
    }

    // -------------------------------------------------------------------------
    // Accessors — scalars
    // -------------------------------------------------------------------------

    /** @return number of processes n */
    public int getNumProcesses() { return numProcesses; }

    /** @return number of resource types m */
    public int getNumResources() { return numResources; }

    /** @return descriptive label of this test case */
    public String getLabel() { return label; }

    /** @return Process object for index i */
    public Process getProcess(int i) {
        rangeCheckProcess(i);
        return processes[i];
    }

    /** @return Resource object for index j */
    public Resource getResource(int j) {
        rangeCheckResource(j);
        return resources[j];
    }

    /** @return total instances of resource j */
    public int getTotal(int j) {
        rangeCheckResource(j);
        return total[j];
    }

    // -------------------------------------------------------------------------
    // Accessors — matrix elements (safe, no array aliasing)
    // -------------------------------------------------------------------------

    /** @return allocation[i][j] */
    public int getAllocation(int i, int j) {
        rangeCheckProcess(i); rangeCheckResource(j);
        return allocation[i][j];
    }

    /** @return maximum[i][j] */
    public int getMaximum(int i, int j) {
        rangeCheckProcess(i); rangeCheckResource(j);
        return maximum[i][j];
    }

    /** @return need[i][j] */
    public int getNeed(int i, int j) {
        rangeCheckProcess(i); rangeCheckResource(j);
        return need[i][j];
    }

    /** @return available[j] */
    public int getAvailable(int j) {
        rangeCheckResource(j);
        return available[j];
    }

    // -------------------------------------------------------------------------
    // Accessors — full row/column copies (for Banker's and QEA inner loops)
    // -------------------------------------------------------------------------

    /** @return a copy of the allocation row for process i */
    public int[] getAllocationRow(int i) {
        rangeCheckProcess(i);
        return allocation[i].clone();
    }

    /** @return a copy of the need row for process i */
    public int[] getNeedRow(int i) {
        rangeCheckProcess(i);
        return need[i].clone();
    }

    /** @return a copy of the available vector */
    public int[] getAvailableVector() { return available.clone(); }

    /** @return a full deep copy of the allocation matrix */
    public int[][] getAllocationMatrix() { return deepCopy2D(allocation); }

    /** @return a full deep copy of the maximum matrix */
    public int[][] getMaximumMatrix() { return deepCopy2D(maximum); }

    /** @return a full deep copy of the need matrix */
    public int[][] getNeedMatrix() { return deepCopy2D(need); }

    /** @return a copy of the total-instances vector */
    public int[] getTotalVector() { return total.clone(); }

    // -------------------------------------------------------------------------
    // Deep copy — THE critical method for benchmark fairness
    // -------------------------------------------------------------------------

    /**
     * Returns a completely independent deep copy of this SystemState.
     *
     * Every algorithm in the Benchmark receives its own deep copy from this
     * method, guaranteeing that no algorithm can observe mutations made by
     * another algorithm running on the same test case.
     *
     * @return new SystemState with identical data but no shared references
     */
    public SystemState deepCopy() {
        return new SystemState(processes, resources,
                               deepCopy2D(allocation), deepCopy2D(maximum),
                               available.clone(), label);
    }

    // -------------------------------------------------------------------------
    // Derived metrics (useful for FitnessFunction and PerformanceAnalyzer)
    // -------------------------------------------------------------------------

    /**
     * Overall resource utilization across all resource types.
     * Computed as: sum(allocatedInstances[j]) / sum(total[j])
     *
     * @return utilization in [0.0, 1.0]
     */
    public double getOverallUtilization() {
        long totalCap = 0, totalUsed = 0;
        for (int j = 0; j < numResources; j++) {
            totalCap  += total[j];
            totalUsed += (total[j] - available[j]);
        }
        return totalCap == 0 ? 0.0 : (double) totalUsed / totalCap;
    }

    /**
     * Resource waste = total idle (available) resource instances summed across
     * all resource types.  Used as a component of the QEA fitness function.
     *
     * @return total idle resource instances
     */
    public int getTotalResourceWaste() {
        int waste = 0;
        for (int j = 0; j < numResources; j++) waste += available[j];
        return waste;
    }

    // -------------------------------------------------------------------------
    // Display
    // -------------------------------------------------------------------------

    /**
     * Prints the classic tabular view seen in OS textbooks (Silberschatz style).
     * Useful for debugging and for unit-test output.
     */
    public void printTable() {
        System.out.println("=== SystemState: " + label + " ===");
        System.out.printf("Processes: %d   Resource types: %d%n", numProcesses, numResources);

        // Header
        System.out.printf("%-8s", "Process");
        System.out.print(" | Allocation");
        padSpaces(numResources * 3 - 10);
        System.out.print(" | Maximum");
        padSpaces(numResources * 3 - 7);
        System.out.print(" | Need");
        padSpaces(numResources * 3 - 4);
        System.out.println();

        System.out.println("-".repeat(8 + 3 + numResources * 3 + 3 + numResources * 3 + 3 + numResources * 3 + 6));

        for (int i = 0; i < numProcesses; i++) {
            System.out.printf("%-8s", processes[i].getName());
            System.out.print(" | ");
            printRow(allocation[i]);
            System.out.print(" | ");
            printRow(maximum[i]);
            System.out.print(" | ");
            printRow(need[i]);
            System.out.println();
        }

        System.out.println();
        System.out.print("Available: ");
        printRow(available);
        System.out.println();
        System.out.print("Total:     ");
        printRow(total);
        System.out.println("\n");
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private static int[][] deepCopy2D(int[][] src) {
        int[][] copy = new int[src.length][];
        for (int i = 0; i < src.length; i++) {
            copy[i] = src[i].clone();
        }
        return copy;
    }

    private void rangeCheckProcess(int i) {
        if (i < 0 || i >= numProcesses)
            throw new IndexOutOfBoundsException("Process index " + i + " out of range [0," + (numProcesses-1) + "]");
    }

    private void rangeCheckResource(int j) {
        if (j < 0 || j >= numResources)
            throw new IndexOutOfBoundsException("Resource index " + j + " out of range [0," + (numResources-1) + "]");
    }

    private static void printRow(int[] row) {
        for (int v : row) System.out.printf("%3d", v);
    }

    private static void padSpaces(int n) {
        for (int i = 0; i < n; i++) System.out.print(' ');
    }

    @Override
    public String toString() {
        return "SystemState{label='" + label + "', n=" + numProcesses + ", m=" + numResources
             + ", utilization=" + String.format("%.1f%%", getOverallUtilization() * 100) + "}";
    }
}
