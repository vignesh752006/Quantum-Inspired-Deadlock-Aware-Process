package io.github.vignesh752006.quantumdeadlock.model;

import io.github.vignesh752006.quantumdeadlock.util.Matrices;
import io.github.vignesh752006.quantumdeadlock.util.Preconditions;

import java.util.Objects;

/**
 * An immutable snapshot of the complete resource-allocation state at one instant.
 *
 * <p>Holds the four matrices the Banker's Algorithm needs, for {@code n} processes
 * and {@code m} resource types:</p>
 * <pre>
 *   allocation[n][m]  resources currently held by each process
 *   maximum[n][m]     declared lifetime demand of each process
 *   need[n][m]        derived: maximum - allocation
 *   available[m]      free instances not held by any process
 * </pre>
 *
 * <p>Two invariants are enforced in the constructor, because a state that breaks
 * either one would make a benchmark result meaningless rather than merely wrong:</p>
 * <ol>
 *   <li>{@code allocation[i][j] >= 0} and {@code allocation[i][j] <= maximum[i][j]}
 *       (so {@code need} is never negative)</li>
 *   <li>conservation: {@code sum_i allocation[i][j] + available[j] == total[j]}</li>
 * </ol>
 *
 * <p>The class is immutable and every accessor returns a copy, so a single
 * canonical state can be {@linkplain #deepCopy() deep-copied} to each algorithm
 * under test without any risk of one run observing another's mutations.</p>
 */
public final class SystemState {

    private final int processCount;
    private final int resourceCount;
    private final Process[] processes;
    private final Resource[] resources;
    private final int[][] allocation;
    private final int[][] maximum;
    private final int[][] need;
    private final int[] available;
    private final int[] total;
    private final String label;

    /**
     * Builds and validates a state snapshot.
     *
     * @param processes  process metadata, length {@code n}
     * @param resources  resource metadata, length {@code m}; supplies the totals
     * @param allocation allocation matrix {@code [n][m]}
     * @param maximum    maximum-demand matrix {@code [n][m]}
     * @param available  free-instance vector {@code [m]}
     * @param label      optional descriptive label; {@code "unlabelled"} when blank
     * @throws IllegalArgumentException if any argument is null, any dimension is
     *                                  inconsistent, a value is negative, or either invariant fails
     */
    public SystemState(Process[] processes, Resource[] resources,
                       int[][] allocation, int[][] maximum,
                       int[] available, String label) {

        Preconditions.requireNonNull(processes, "SystemState: processes must not be null.");
        Preconditions.requireNonNull(resources, "SystemState: resources must not be null.");
        Preconditions.requireNonNull(allocation, "SystemState: allocation must not be null.");
        Preconditions.requireNonNull(maximum, "SystemState: maximum must not be null.");
        Preconditions.requireNonNull(available, "SystemState: available must not be null.");

        this.processCount = processes.length;
        this.resourceCount = resources.length;

        if (processCount == 0) {
            throw new IllegalArgumentException("SystemState: at least one process is required.");
        }
        if (resourceCount == 0) {
            throw new IllegalArgumentException("SystemState: at least one resource type is required.");
        }

        requireMatrixShape(allocation, "allocation");
        requireMatrixShape(maximum, "maximum");
        if (available.length != resourceCount) {
            throw new IllegalArgumentException("SystemState: available.length "
                    + available.length + " != resourceCount " + resourceCount + ".");
        }

        this.allocation = Matrices.deepCopy(allocation);
        this.maximum = Matrices.deepCopy(maximum);
        this.available = available.clone();
        this.need = new int[processCount][resourceCount];

        for (int i = 0; i < processCount; i++) {
            for (int j = 0; j < resourceCount; j++) {
                Preconditions.requireNonNegative(this.allocation[i][j],
                        "SystemState: allocation[" + i + "][" + j + "]");
                Preconditions.requireNonNegative(this.maximum[i][j],
                        "SystemState: maximum[" + i + "][" + j + "]");
                int remaining = this.maximum[i][j] - this.allocation[i][j];
                if (remaining < 0) {
                    throw new IllegalArgumentException("SystemState: need[" + i + "][" + j + "] = "
                            + this.maximum[i][j] + " - " + this.allocation[i][j] + " = " + remaining
                            + " < 0. allocation cannot exceed maximum.");
                }
                this.need[i][j] = remaining;
            }
        }

        this.total = new int[resourceCount];
        for (int j = 0; j < resourceCount; j++) {
            this.total[j] = resources[j].totalInstances();
        }
        requireConservation();

        this.processes = new Process[processCount];
        for (int i = 0; i < processCount; i++) {
            this.processes[i] = processes[i].deepCopy();
        }
        this.resources = new Resource[resourceCount];
        for (int j = 0; j < resourceCount; j++) {
            this.resources[j] = resources[j].deepCopy();
        }

        this.label = (label == null || label.isBlank()) ? "unlabelled" : label;
    }

    private void requireMatrixShape(int[][] matrix, String name) {
        if (matrix.length != processCount) {
            throw new IllegalArgumentException("SystemState: " + name + ".length "
                    + matrix.length + " != processCount " + processCount + ".");
        }
        for (int i = 0; i < processCount; i++) {
            if (matrix[i] == null || matrix[i].length != resourceCount) {
                throw new IllegalArgumentException("SystemState: " + name + "[" + i
                        + "].length must equal resourceCount " + resourceCount + ".");
            }
        }
    }

    private void requireConservation() {
        for (int j = 0; j < resourceCount; j++) {
            int allocated = 0;
            for (int i = 0; i < processCount; i++) {
                allocated += allocation[i][j];
            }
            if (allocated + available[j] != total[j]) {
                throw new IllegalArgumentException("SystemState: conservation violated for resource " + j
                        + ". sum(allocation)=" + allocated + " + available=" + available[j]
                        + " != total=" + total[j] + ".");
            }
        }
    }

    public int processCount() {
        return processCount;
    }

    public int resourceCount() {
        return resourceCount;
    }

    public String label() {
        return label;
    }

    public Process process(int index) {
        rangeCheckProcess(index);
        return processes[index];
    }

    public Resource resource(int index) {
        rangeCheckResource(index);
        return resources[index];
    }

    public int total(int resourceIndex) {
        rangeCheckResource(resourceIndex);
        return total[resourceIndex];
    }

    public int allocation(int processIndex, int resourceIndex) {
        rangeCheckProcess(processIndex);
        rangeCheckResource(resourceIndex);
        return allocation[processIndex][resourceIndex];
    }

    public int maximum(int processIndex, int resourceIndex) {
        rangeCheckProcess(processIndex);
        rangeCheckResource(resourceIndex);
        return maximum[processIndex][resourceIndex];
    }

    public int need(int processIndex, int resourceIndex) {
        rangeCheckProcess(processIndex);
        rangeCheckResource(resourceIndex);
        return need[processIndex][resourceIndex];
    }

    public int available(int resourceIndex) {
        rangeCheckResource(resourceIndex);
        return available[resourceIndex];
    }

    public int[] allocationRow(int processIndex) {
        rangeCheckProcess(processIndex);
        return allocation[processIndex].clone();
    }

    public int[] needRow(int processIndex) {
        rangeCheckProcess(processIndex);
        return need[processIndex].clone();
    }

    public int[] availableVector() {
        return available.clone();
    }

    public int[][] allocationMatrix() {
        return Matrices.deepCopy(allocation);
    }

    public int[][] maximumMatrix() {
        return Matrices.deepCopy(maximum);
    }

    public int[][] needMatrix() {
        return Matrices.deepCopy(need);
    }

    public int[] totalVector() {
        return total.clone();
    }

    /**
     * Returns a fully independent copy of this state.
     *
     * <p>This is the single guarantee that makes the three-way benchmark fair:
     * each algorithm receives its own copy, so none can observe another's work.</p>
     *
     * @return a new state with identical data and no shared arrays
     */
    public SystemState deepCopy() {
        return new SystemState(processes, resources, allocation, maximum, available, label);
    }

    /**
     * Fraction of all resource instances currently allocated.
     *
     * @return utilisation in {@code [0.0, 1.0]}, or {@code 0.0} when capacity is zero
     */
    public double overallUtilization() {
        long capacity = 0L;
        long used = 0L;
        for (int j = 0; j < resourceCount; j++) {
            capacity += total[j];
            used += total[j] - available[j];
        }
        return capacity == 0L ? 0.0 : (double) used / capacity;
    }

    /**
     * Total idle instances across all resource types; a component of the QEA
     * fitness function.
     *
     * @return sum of {@code available[j]}
     */
    public int totalResourceWaste() {
        int waste = 0;
        for (int j = 0; j < resourceCount; j++) {
            waste += available[j];
        }
        return waste;
    }

    private void rangeCheckProcess(int index) {
        if (index < 0 || index >= processCount) {
            throw new IndexOutOfBoundsException(
                    "Process index " + index + " out of range [0, " + (processCount - 1) + "].");
        }
    }

    private void rangeCheckResource(int index) {
        if (index < 0 || index >= resourceCount) {
            throw new IndexOutOfBoundsException(
                    "Resource index " + index + " out of range [0, " + (resourceCount - 1) + "].");
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SystemState that)) {
            return false;
        }
        return processCount == that.processCount
                && resourceCount == that.resourceCount
                && Matrices.deepEquals(allocation, that.allocation)
                && Matrices.deepEquals(maximum, that.maximum)
                && java.util.Arrays.equals(available, that.available);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(processCount, resourceCount);
        result = 31 * result + java.util.Arrays.deepHashCode(allocation);
        result = 31 * result + java.util.Arrays.deepHashCode(maximum);
        result = 31 * result + java.util.Arrays.hashCode(available);
        return result;
    }

    @Override
    public String toString() {
        return String.format("SystemState{label='%s', n=%d, m=%d, utilization=%.1f%%}",
                label, processCount, resourceCount, overallUtilization() * 100.0);
    }
}
