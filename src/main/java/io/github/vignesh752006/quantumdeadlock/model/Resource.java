package io.github.vignesh752006.quantumdeadlock.model;

import io.github.vignesh752006.quantumdeadlock.util.Preconditions;

/**
 * A resource <em>type</em> (a class of interchangeable instances) with a fixed
 * capacity and a mutable count of free instances.
 *
 * <p>The mutable available count is deliberate: simulations grant and release
 * instances while searching for a schedule. Each {@link SystemState} owns deep
 * copies of its resources, so a simulation can never mutate the canonical
 * benchmark state.</p>
 */
public final class Resource {

    private final int id;
    private final String name;
    private final int totalInstances;
    private int availableInstances;

    /**
     * Creates a resource type with every instance free.
     *
     * @param id             zero-based index
     * @param name           display label; defaults to {@code "R" + id} when blank
     * @param totalInstances total capacity, must be at least 1
     */
    public Resource(int id, String name, int totalInstances) {
        this(id, name, totalInstances, totalInstances);
    }

    /**
     * Creates a resource type with a specific number of free instances.
     *
     * @param id                 zero-based index
     * @param name               display label; defaults to {@code "R" + id} when blank
     * @param totalInstances     total capacity, must be at least 1
     * @param availableInstances free instances, must be within {@code [0, totalInstances]}
     */
    public Resource(int id, String name, int totalInstances, int availableInstances) {
        Preconditions.requireNonNegative(id, "Resource id");
        if (totalInstances < 1) {
            throw new IllegalArgumentException(
                    "Resource " + id + ": totalInstances must be >= 1, got " + totalInstances + ".");
        }
        Preconditions.requireInRange(availableInstances, 0, totalInstances,
                "Resource " + id + ": availableInstances");

        this.id = id;
        this.name = (name == null || name.isBlank()) ? "R" + id : name;
        this.totalInstances = totalInstances;
        this.availableInstances = availableInstances;
    }

    public int id() {
        return id;
    }

    public String name() {
        return name;
    }

    public int totalInstances() {
        return totalInstances;
    }

    public int availableInstances() {
        return availableInstances;
    }

    public int allocatedInstances() {
        return totalInstances - availableInstances;
    }

    /** @return utilisation in {@code [0.0, 1.0]}; 1.0 means fully allocated */
    public double utilization() {
        return (double) allocatedInstances() / totalInstances;
    }

    /**
     * Returns instances to the free pool.
     *
     * @param count number of instances to release, must be non-negative
     * @throws IllegalArgumentException if {@code count} is negative
     * @throws IllegalStateException    if the release would exceed total capacity
     */
    public void release(int count) {
        Preconditions.requireNonNegative(count, "Resource " + name + ": release count");
        if (availableInstances + count > totalInstances) {
            throw new IllegalStateException("Resource " + name + ": releasing " + count
                    + " would exceed totalInstances (" + totalInstances
                    + "). Currently available: " + availableInstances + ".");
        }
        availableInstances += count;
    }

    /**
     * Removes instances from the free pool.
     *
     * @param count number of instances to allocate, must be non-negative
     * @throws IllegalArgumentException if {@code count} is negative
     * @throws IllegalStateException    if fewer than {@code count} instances are free
     */
    public void allocate(int count) {
        Preconditions.requireNonNegative(count, "Resource " + name + ": allocate count");
        if (count > availableInstances) {
            throw new IllegalStateException("Resource " + name + ": requested " + count
                    + " but only " + availableInstances + " available.");
        }
        availableInstances -= count;
    }

    /**
     * Current free-instance count.
     *
     * <p>Kept as an extension point: a future VM-backed resource can override this
     * to poll a live monitor instead of returning the simulated value.</p>
     *
     * @return number of free instances
     */
    public int pollAvailable() {
        return availableInstances;
    }

    /** @return an independent copy carrying the same id, capacity and free count */
    public Resource deepCopy() {
        return new Resource(id, name, totalInstances, availableInstances);
    }

    @Override
    public String toString() {
        return String.format("%-10s | total=%-3d | available=%-3d | utilization=%.1f%%",
                name, totalInstances, availableInstances, utilization() * 100.0);
    }
}
