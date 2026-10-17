/**
 * Process.java
 *
 * Represents a single process in the deadlock-aware scheduling system.
 *
 * Design notes:
 *   - All arrays are defensively copied on construction and on access,
 *     so a SystemState deep-copy cannot inadvertently share array references.
 *   - need[] is DERIVED (= maximum[] - allocation[]) and validated on construction;
 *     it is never stored externally to avoid staleness.
 *   - Phase-2 hook: AgentProcess can extend this class and override
 *     getActualUsage() to supply live values from a VM agent report.
 *
 * @author  OSDS-CP Group
 * @version 1.0  (Phase 1 – simulated data)
 */
public class Process {

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /** Unique numeric identifier for this process (0-based index). */
    private final int id;

    /** Human-readable label, e.g. "P0", "Worker-3". */
    private final String name;

    /**
     * Number of resource instances currently allocated to this process.
     * Length == number of resource types (m).
     */
    private final int[] allocation;

    /**
     * Maximum number of resource instances this process may ever request.
     * Length == number of resource types (m).
     */
    private final int[] maximum;

    /**
     * Remaining need = maximum - allocation.
     * Computed once at construction; stored for O(1) access during Banker's
     * and fitness evaluations.
     */
    private final int[] need;

    /** Number of resource types (m). Stored for range-check convenience. */
    private final int numResources;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs a Process with explicit allocation and maximum arrays.
     * The need array is computed and validated here.
     *
     * @param id         unique 0-based process index
     * @param name       human-readable label
     * @param allocation resources currently held (defensive copy taken)
     * @param maximum    maximum resources that may ever be requested (defensive copy taken)
     * @throws IllegalArgumentException if arrays differ in length or
     *                                  any need[i] < 0 (allocation > maximum)
     */
    public Process(int id, String name, int[] allocation, int[] maximum) {
        if (allocation == null || maximum == null) {
            throw new IllegalArgumentException("Process " + id + ": allocation and maximum must not be null.");
        }
        if (allocation.length != maximum.length) {
            throw new IllegalArgumentException(
                "Process " + id + ": allocation and maximum must have the same length. "
                + "Got allocation.length=" + allocation.length
                + ", maximum.length=" + maximum.length);
        }

        this.id          = id;
        this.name        = (name != null) ? name : "P" + id;
        this.numResources = allocation.length;

        // Defensive copies
        this.allocation = allocation.clone();
        this.maximum    = maximum.clone();

        // Compute and validate need[]
        this.need = new int[numResources];
        for (int j = 0; j < numResources; j++) {
            int n = maximum[j] - allocation[j];
            if (n < 0) {
                throw new IllegalArgumentException(
                    "Process " + id + ", resource " + j
                    + ": allocation (" + allocation[j]
                    + ") exceeds maximum (" + maximum[j] + ").");
            }
            this.need[j] = n;
        }
    }

    // -------------------------------------------------------------------------
    // Accessors (all return defensive copies of arrays)
    // -------------------------------------------------------------------------

    /** Returns the unique process ID. */
    public int getId() { return id; }

    /** Returns the human-readable process name. */
    public String getName() { return name; }

    /** Returns the number of resource types. */
    public int getNumResources() { return numResources; }

    /**
     * Returns a copy of the allocation array.
     * Callers must not hold onto this array and mutate it between calls.
     */
    public int[] getAllocation() { return allocation.clone(); }

    /**
     * Returns a copy of the maximum array.
     */
    public int[] getMaximum() { return maximum.clone(); }

    /**
     * Returns a copy of the need array (maximum - allocation).
     */
    public int[] getNeed() { return need.clone(); }

    /**
     * Returns the allocation for a specific resource type.
     *
     * @param resourceIndex 0-based resource type index
     * @return allocated instances for that resource
     */
    public int getAllocation(int resourceIndex) {
        rangeCheck(resourceIndex);
        return allocation[resourceIndex];
    }

    /**
     * Returns the maximum for a specific resource type.
     *
     * @param resourceIndex 0-based resource type index
     */
    public int getMaximum(int resourceIndex) {
        rangeCheck(resourceIndex);
        return maximum[resourceIndex];
    }

    /**
     * Returns the need for a specific resource type.
     *
     * @param resourceIndex 0-based resource type index
     */
    public int getNeed(int resourceIndex) {
        rangeCheck(resourceIndex);
        return need[resourceIndex];
    }

    // -------------------------------------------------------------------------
    // Phase-2 hook
    // -------------------------------------------------------------------------

    /**
     * Returns the actual current resource usage reported by this process.
     *
     * In Phase 1 (simulated data) this is identical to {@link #getAllocation()}.
     * In Phase 2, {@code AgentProcess} will override this method to return
     * live usage data polled from the VM agent.
     *
     * @return copy of the actual usage array
     */
    public int[] getActualUsage() {
        return getAllocation();
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    /**
     * Checks whether this process's need can be satisfied by the given work vector.
     * Used by Banker's Algorithm in Step 2 of the safety check.
     *
     * @param work current Work vector (available-after-previous-grants)
     * @return true if need[j] <= work[j] for all j
     */
    public boolean canAllocate(int[] work) {
        if (work.length != numResources) {
            throw new IllegalArgumentException("Work vector length mismatch.");
        }
        for (int j = 0; j < numResources; j++) {
            if (need[j] > work[j]) return false;
        }
        return true;
    }

    /** Returns a deep copy of this Process (same id, name, data). */
    public Process deepCopy() {
        return new Process(id, name, allocation.clone(), maximum.clone());
    }

    private void rangeCheck(int index) {
        if (index < 0 || index >= numResources) {
            throw new IndexOutOfBoundsException(
                "Resource index " + index + " out of range [0, " + (numResources - 1) + "]");
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-6s", name));
        sb.append(" | Alloc: ");
        appendArray(sb, allocation);
        sb.append(" | Max: ");
        appendArray(sb, maximum);
        sb.append(" | Need: ");
        appendArray(sb, need);
        return sb.toString();
    }

    private static void appendArray(StringBuilder sb, int[] arr) {
        sb.append('[');
        for (int i = 0; i < arr.length; i++) {
            sb.append(arr[i]);
            if (i < arr.length - 1) sb.append(", ");
        }
        sb.append(']');
    }
}
