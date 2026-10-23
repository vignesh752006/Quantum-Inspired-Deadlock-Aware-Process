package io.github.vignesh752006.quantumdeadlock.banker;

import io.github.vignesh752006.quantumdeadlock.model.SystemState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable outcome of one Banker's Algorithm safety check.
 *
 * <p>Split out of {@code BankersAlgorithm} so the algorithm class holds only
 * behaviour and this value object can be serialised, asserted on, or replayed by
 * the dashboard without pulling in the algorithm.</p>
 */
public final class BankersResult {

    private final boolean safe;
    private final int[] safeSequence;
    private final long executionTimeNs;
    private final List<String> trace;
    private final int scanCount;

    /**
     * @param safe            whether a complete safe sequence was found
     * @param safeSequence    safe ordering, or an empty array when unsafe
     * @param executionTimeNs wall-clock duration of the check
     * @param trace           human-readable step log
     * @param scanCount       number of outer scan passes performed
     */
    public BankersResult(boolean safe, int[] safeSequence, long executionTimeNs,
                         List<String> trace, int scanCount) {
        this.safe = safe;
        this.safeSequence = safeSequence == null ? new int[0] : safeSequence.clone();
        this.executionTimeNs = executionTimeNs;
        this.trace = Collections.unmodifiableList(new ArrayList<>(
                trace == null ? List.of() : trace));
        this.scanCount = scanCount;
    }

    public boolean safe() {
        return safe;
    }

    /** @return a copy of the safe sequence; empty when the state is unsafe */
    public int[] safeSequence() {
        return safeSequence.clone();
    }

    public long executionTimeNs() {
        return executionTimeNs;
    }

    /** @return the unmodifiable step-by-step trace */
    public List<String> trace() {
        return trace;
    }

    public int scanCount() {
        return scanCount;
    }

    /**
     * Renders the safe sequence using process names, e.g. {@code <P1, P3, P4, P2, P0>}.
     *
     * @param state the state the check ran against, used for name lookup
     * @return a human-readable sequence, or a placeholder when unsafe
     */
    public String sequenceString(SystemState state) {
        if (!safe || safeSequence.length == 0) {
            return "<UNSAFE - no safe sequence>";
        }
        StringBuilder text = new StringBuilder("<");
        for (int k = 0; k < safeSequence.length; k++) {
            text.append(state.process(safeSequence[k]).name());
            if (k < safeSequence.length - 1) {
                text.append(", ");
            }
        }
        return text.append('>').toString();
    }

    @Override
    public String toString() {
        return String.format("BankersResult{safe=%b, scans=%d, timeNs=%d, sequenceLength=%d}",
                safe, scanCount, executionTimeNs, safeSequence.length);
    }
}
