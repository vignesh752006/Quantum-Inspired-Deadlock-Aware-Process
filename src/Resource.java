/**
 * Resource.java
 *
 * Represents a single resource TYPE in the deadlock-aware scheduling system.
 *
 * Design notes:
 *   - A "resource" here is a resource CLASS (e.g. "CPU slots", "Memory tokens"),
 *     not a single physical unit.  The class tracks total instances and how many
 *     are currently available.
 *   - Mutability is intentional: available count changes as allocations are
 *     granted/released during the Banker simulation and QEA evaluation.
 *     The containing SystemState always operates on deep-copied Resource objects
 *     so that the original test-case data is never corrupted.
 *   - Phase-2 hook: VMResource can extend this class and override
 *     pollAvailable() to query the real VM resource monitor.
 *
 * @author  OSDS-CP Group
 * @version 1.0  (Phase 1 – simulated data)
 */
public class Resource {

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /** Unique numeric identifier for this resource type (0-based column index). */
    private final int id;

    /** Human-readable label, e.g. "CPU", "Memory", "R0". */
    private final String name;

    /**
     * Total number of instances of this resource type in the system.
     * Immutable after construction.
     */
    private final int totalInstances;

    /**
     * Number of instances not currently allocated to any process.
     * Mutable — changes during simulation but is always kept in [0, totalInstances].
     */
    private int availableInstances;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Creates a Resource with a given total capacity.
     * Initially all instances are available (no allocations yet).
     *
     * @param id             unique 0-based resource-type index
     * @param name           human-readable label
     * @param totalInstances total number of instances (must be >= 1)
     * @throws IllegalArgumentException if totalInstances < 1
     */
    public Resource(int id, String name, int totalInstances) {
        this(id, name, totalInstances, totalInstances);
    }

    /**
     * Creates a Resource with a specified available count (for restoring a
     * SystemState snapshot where some instances are already allocated).
     *
     * @param id                 unique 0-based resource-type index
     * @param name               human-readable label
     * @param totalInstances     total instances in the system (>= 1)
     * @param availableInstances currently free instances (0 <= avail <= total)
     * @throws IllegalArgumentException on invalid parameters
     */
    public Resource(int id, String name, int totalInstances, int availableInstances) {
        if (totalInstances < 1) {
            throw new IllegalArgumentException(
                "Resource " + id + ": totalInstances must be >= 1, got " + totalInstances);
        }
        if (availableInstances < 0 || availableInstances > totalInstances) {
            throw new IllegalArgumentException(
                "Resource " + id + ": availableInstances=" + availableInstances
                + " is out of range [0, " + totalInstances + "].");
        }

        this.id                 = id;
        this.name               = (name != null) ? name : "R" + id;
        this.totalInstances     = totalInstances;
        this.availableInstances = availableInstances;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** Returns the unique resource-type ID. */
    public int getId() { return id; }

    /** Returns the human-readable resource name. */
    public String getName() { return name; }

    /** Returns the total number of instances of this resource type. */
    public int getTotalInstances() { return totalInstances; }

    /**
     * Returns the number of currently available (unallocated) instances.
     *
     * Phase-2 hook: VMResource overrides this to poll the real monitor.
     */
    public int getAvailableInstances() { return availableInstances; }

    /** Returns the number of currently allocated instances. */
    public int getAllocatedInstances() { return totalInstances - availableInstances; }

    /**
     * Resource utilization in [0.0, 1.0].
     * 1.0 means fully allocated; 0.0 means completely idle.
     */
    public double getUtilization() {
        return (double) getAllocatedInstances() / totalInstances;
    }

    // -------------------------------------------------------------------------
    // Mutators (used during Banker simulation and QEA evaluation)
    // -------------------------------------------------------------------------

    /**
     * Releases {@code count} instances back to available.
     * Called when a process finishes and returns its allocation.
     *
     * @param count number of instances to release (must be > 0 and
     *              not exceed already-allocated count)
     * @throws IllegalArgumentException if count is out of range
     */
    public void release(int count) {
        if (count < 0) {
            throw new IllegalArgumentException(
                "Resource " + name + ": release count must be >= 0, got " + count);
        }
        if (availableInstances + count > totalInstances) {
            throw new IllegalStateException(
                "Resource " + name + ": releasing " + count
                + " would exceed totalInstances (" + totalInstances
                + "). Currently available: " + availableInstances);
        }
        availableInstances += count;
    }

    /**
     * Allocates {@code count} instances to a process.
     *
     * @param count number of instances to allocate (must be > 0 and
     *              not exceed availableInstances)
     * @throws IllegalArgumentException if count is out of range
     * @throws IllegalStateException    if insufficient instances available
     */
    public void allocate(int count) {
        if (count < 0) {
            throw new IllegalArgumentException(
                "Resource " + name + ": allocate count must be >= 0, got " + count);
        }
        if (count > availableInstances) {
            throw new IllegalStateException(
                "Resource " + name + ": requested " + count
                + " but only " + availableInstances + " available.");
        }
        availableInstances -= count;
    }

    // -------------------------------------------------------------------------
    // Phase-2 hook
    // -------------------------------------------------------------------------

    /**
     * Polls the real-time available count from an external monitor.
     *
     * In Phase 1 this simply returns {@link #getAvailableInstances()}.
     * In Phase 2, VMResource overrides this to query cgroup or the VM agent.
     *
     * @return number of currently available instances
     */
    public int pollAvailable() {
        return availableInstances;
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    /** Returns a deep copy of this Resource (same id, name, totals, available). */
    public Resource deepCopy() {
        return new Resource(id, name, totalInstances, availableInstances);
    }

    @Override
    public String toString() {
        return String.format("%-10s | total=%-3d | available=%-3d | utilization=%.1f%%",
            name, totalInstances, availableInstances, getUtilization() * 100);
    }
}
