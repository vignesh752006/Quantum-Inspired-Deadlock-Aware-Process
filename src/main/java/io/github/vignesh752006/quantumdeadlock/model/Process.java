package io.github.vignesh752006.quantumdeadlock.model;

import io.github.vignesh752006.quantumdeadlock.util.Matrices;
import io.github.vignesh752006.quantumdeadlock.util.Preconditions;

/**
 * An immutable process in the deadlock-aware scheduling model.
 *
 * <p>A process declares a bounded demand: it currently holds {@code allocation}
 * instances of each resource type and may never request more than
 * {@code maximum}. The remaining {@code need} is always derived as
 * {@code maximum - allocation}, so it can never go stale.</p>
 *
 * <p>All arrays are copied on construction and on every access, which is what
 * makes {@link SystemState} snapshots safe to hand to several algorithms at once.</p>
 */
public final class Process {

    private final int id;
    private final String name;
    private final int[] allocation;
    private final int[] maximum;
    private final int[] need;
    private final int resourceCount;

    /**
     * Creates a process.
     *
     * @param id         zero-based index, must be non-negative
     * @param name       display label; defaults to {@code "P" + id} when blank
     * @param allocation instances currently held, one entry per resource type
     * @param maximum    lifetime maximum demand, same length as {@code allocation}
     * @throws IllegalArgumentException if an array is null, the lengths disagree,
     *                                  a value is negative, or {@code allocation > maximum}
     */
    public Process(int id, String name, int[] allocation, int[] maximum) {
        Preconditions.requireNonNegative(id, "Process id");
        Preconditions.requireNonNull(allocation, "Process " + id + ": allocation must not be null.");
        Preconditions.requireNonNull(maximum, "Process " + id + ": maximum must not be null.");
        if (allocation.length != maximum.length) {
            throw new IllegalArgumentException("Process " + id
                    + ": allocation and maximum must have the same length, got "
                    + allocation.length + " and " + maximum.length + ".");
        }
        if (allocation.length == 0) {
            throw new IllegalArgumentException("Process " + id + ": at least one resource type is required.");
        }

        this.id = id;
        this.name = (name == null || name.isBlank()) ? "P" + id : name;
        this.resourceCount = allocation.length;
        this.allocation = allocation.clone();
        this.maximum = maximum.clone();
        this.need = new int[resourceCount];

        for (int j = 0; j < resourceCount; j++) {
            Preconditions.requireNonNegative(allocation[j], "Process " + id + ": allocation[" + j + "]");
            Preconditions.requireNonNegative(maximum[j], "Process " + id + ": maximum[" + j + "]");
            int remaining = maximum[j] - allocation[j];
            if (remaining < 0) {
                throw new IllegalArgumentException("Process " + id + ", resource " + j
                        + ": allocation (" + allocation[j] + ") exceeds maximum (" + maximum[j] + ").");
            }
            need[j] = remaining;
        }
    }

    public int id() {
        return id;
    }

    public String name() {
        return name;
    }

    public int resourceCount() {
        return resourceCount;
    }

    /** @return a copy of the allocation vector */
    public int[] allocation() {
        return allocation.clone();
    }

    /** @return a copy of the maximum-demand vector */
    public int[] maximum() {
        return maximum.clone();
    }

    /** @return a copy of the derived {@code maximum - allocation} vector */
    public int[] need() {
        return need.clone();
    }

    public int allocation(int resourceIndex) {
        rangeCheck(resourceIndex);
        return allocation[resourceIndex];
    }

    public int maximum(int resourceIndex) {
        rangeCheck(resourceIndex);
        return maximum[resourceIndex];
    }

    public int need(int resourceIndex) {
        rangeCheck(resourceIndex);
        return need[resourceIndex];
    }

    /**
     * Tests whether this process could complete if the given resources were free:
     * true iff {@code need[j] <= available[j]} for every resource type.
     *
     * @param available candidate free-resource vector
     * @return true when the process's full need can be satisfied
     * @throws IllegalArgumentException if {@code available} has the wrong length
     */
    public boolean canAllocate(int[] available) {
        Preconditions.requireNonNull(available, "available vector must not be null.");
        if (available.length != resourceCount) {
            throw new IllegalArgumentException("Work vector length mismatch: expected "
                    + resourceCount + ", got " + available.length + ".");
        }
        for (int j = 0; j < resourceCount; j++) {
            if (need[j] > available[j]) {
                return false;
            }
        }
        return true;
    }

    /** @return an independent copy of this process */
    public Process deepCopy() {
        return new Process(id, name, allocation, maximum);
    }

    private void rangeCheck(int resourceIndex) {
        if (resourceIndex < 0 || resourceIndex >= resourceCount) {
            throw new IndexOutOfBoundsException("Resource index " + resourceIndex
                    + " out of range [0, " + (resourceCount - 1) + "].");
        }
    }

    @Override
    public String toString() {
        return name + " | Alloc: " + Matrices.format(allocation)
                + " | Max: " + Matrices.format(maximum)
                + " | Need: " + Matrices.format(need);
    }
}
