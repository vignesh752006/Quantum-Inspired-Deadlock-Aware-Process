/**
 * QuantumBit.java
 *
 * Models a single qubit using the angle representation common in
 * Quantum-Inspired Evolutionary Algorithms (QEA / QIEA).
 *
 * The qubit stores an angle θ ∈ [0, π/2] such that:
 *   α = cos(θ)   →   α² = probability of observing bit 0  (favours "left")
 *   β = sin(θ)   →   β² = probability of observing bit 1  (favours "right")
 *   α² + β² = 1  (unit-circle normalisation, always satisfied)
 *
 * Initial state θ = π/4 → α² = β² = 0.5  (equal superposition: no bias).
 * As the QEA evolves, rotations push θ toward 0 (bias to 0) or π/2 (bias to 1),
 * driving the population toward the best-known solution.
 *
 * NOT thread-safe: each individual in the population owns its own QuantumBit[].
 *
 * @author  OSDS-CP Group
 * @version 1.0  (Phase 1)
 */
public class QuantumBit {

    // ── Defaults (kept in one place for easy tuning) ──────────────────────────
    /** Initial angle — equal superposition. */
    public static final double INITIAL_THETA = Math.PI / 4.0;

    /** Rotation step per update (0.05π ≈ 9°). */
    public static final double DEFAULT_DELTA  = 0.05 * Math.PI;

    /** Minimum allowed θ (exclusive of 0 to avoid locking). */
    private static final double THETA_MIN = 0.001;

    /** Maximum allowed θ (exclusive of π/2 to avoid locking). */
    private static final double THETA_MAX = Math.PI / 2.0 - 0.001;

    // ── State ─────────────────────────────────────────────────────────────────
    private double theta;

    // ── Constructors ──────────────────────────────────────────────────────────

    /** Creates a qubit at equal superposition (θ = π/4). */
    public QuantumBit() {
        this.theta = INITIAL_THETA;
    }

    /** Creates a qubit at a specific angle θ ∈ [0, π/2]. */
    public QuantumBit(double theta) {
        this.theta = clamp(theta);
    }

    // ── Amplitude accessors ───────────────────────────────────────────────────

    public double getTheta()        { return theta; }
    public double getAlpha()        { return Math.cos(theta); }
    public double getBeta()         { return Math.sin(theta); }
    public double getAlphaSquared() { return Math.cos(theta) * Math.cos(theta); }
    public double getBetaSquared()  { return Math.sin(theta) * Math.sin(theta); }

    // ── Observation ───────────────────────────────────────────────────────────

    /**
     * Collapses the qubit to a classical bit (0 or 1) probabilistically.
     * P(0) = α², P(1) = β².
     *
     * @param rngValue a uniform random value in [0, 1) from the caller's RNG
     * @return 0 or 1
     */
    public int observeBit(double rngValue) {
        return (rngValue < getAlphaSquared()) ? 0 : 1;
    }

    /**
     * Returns α² directly as a continuous priority key in [0, 1].
     * Used by QuantumPopulation to convert qubit states to a process ranking.
     */
    public double getPriorityKey() {
        return getAlphaSquared();
    }

    // ── Rotation ──────────────────────────────────────────────────────────────

    /**
     * Rotates the qubit by {@code deltaTheta} radians.
     * Positive Δθ increases β² (pushes toward bit 1).
     * Negative Δθ decreases β² (pushes toward bit 0).
     * θ is clamped to [THETA_MIN, THETA_MAX] to prevent amplitude locking.
     *
     * @param deltaTheta rotation in radians (may be negative)
     */
    public void rotate(double deltaTheta) {
        theta = clamp(theta + deltaTheta);
    }

    /**
     * Rotates the qubit toward bit 0 (decreases β², increases α²).
     *
     * @param delta magnitude of rotation (positive value)
     */
    public void rotateToBit0(double delta) { rotate(-Math.abs(delta)); }

    /**
     * Rotates the qubit toward bit 1 (increases β², decreases α²).
     *
     * @param delta magnitude of rotation (positive value)
     */
    public void rotateToBit1(double delta) { rotate(+Math.abs(delta)); }

    // ── Utility ───────────────────────────────────────────────────────────────

    /** Returns a deep copy of this qubit. */
    public QuantumBit copy() { return new QuantumBit(theta); }

    /** Resets to equal superposition. */
    public void reset() { theta = INITIAL_THETA; }

    private static double clamp(double t) {
        return Math.max(THETA_MIN, Math.min(THETA_MAX, t));
    }

    @Override
    public String toString() {
        return String.format("QB[θ=%.3fπ α²=%.3f β²=%.3f]",
            theta / Math.PI, getAlphaSquared(), getBetaSquared());
    }
}
