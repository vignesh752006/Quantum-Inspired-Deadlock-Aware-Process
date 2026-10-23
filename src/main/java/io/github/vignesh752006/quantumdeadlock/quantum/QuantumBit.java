package io.github.vignesh752006.quantumdeadlock.quantum;

/**
 * One qubit in the angle representation used by Quantum-Inspired Evolutionary
 * Algorithms.
 *
 * <p>The qubit stores an angle {@code theta} in {@code [0, pi/2]} such that</p>
 * <pre>
 *   alpha = cos(theta)   ->  alpha^2 = P(observe 0)
 *   beta  = sin(theta)   ->  beta^2  = P(observe 1)
 *   alpha^2 + beta^2 = 1
 * </pre>
 *
 * <p>The initial angle {@code pi/4} is the equal superposition: no bias toward
 * either bit. Rotation gates then push {@code theta} toward 0 (bias to 0) or
 * {@code pi/2} (bias to 1), steering the population toward the best-known
 * solution. {@code theta} is clamped just inside the endpoints so the amplitude
 * can never lock and remain unobservable.</p>
 *
 * <p>Not thread-safe; each individual owns its own qubit array.</p>
 */
public final class QuantumBit {

    /** Initial angle: equal superposition. */
    public static final double INITIAL_THETA = Math.PI / 4.0;

    /** Default rotation magnitude per update (0.05 pi). */
    public static final double DEFAULT_DELTA = 0.05 * Math.PI;

    private static final double THETA_MIN = 0.001;
    private static final double THETA_MAX = Math.PI / 2.0 - 0.001;

    private double theta;

    /** Creates a qubit at the equal superposition. */
    public QuantumBit() {
        this(INITIAL_THETA);
    }

    /**
     * @param theta initial angle, clamped into the legal open interval
     */
    public QuantumBit(double theta) {
        this.theta = clamp(theta);
    }

    public double theta() {
        return theta;
    }

    public double alpha() {
        return Math.cos(theta);
    }

    public double beta() {
        return Math.sin(theta);
    }

    public double alphaSquared() {
        return Math.cos(theta) * Math.cos(theta);
    }

    public double betaSquared() {
        return Math.sin(theta) * Math.sin(theta);
    }

    /**
     * Collapses the qubit to a classical bit.
     *
     * @param uniformSample a uniform value in {@code [0, 1)} from the caller's RNG
     * @return 0 with probability {@code alpha^2}, otherwise 1
     */
    public int observeBit(double uniformSample) {
        return uniformSample < alphaSquared() ? 0 : 1;
    }

    /** @return {@code alpha^2}, usable as a continuous priority key in {@code [0, 1]} */
    public double priorityKey() {
        return alphaSquared();
    }

    /**
     * Rotates by {@code deltaTheta} radians; positive values push toward bit 1.
     *
     * @param deltaTheta rotation in radians, may be negative
     */
    public void rotate(double deltaTheta) {
        theta = clamp(theta + deltaTheta);
    }

    /** Rotates toward bit 0 by {@code delta} (a positive magnitude). */
    public void rotateToBit0(double delta) {
        rotate(-Math.abs(delta));
    }

    /** Rotates toward bit 1 by {@code delta} (a positive magnitude). */
    public void rotateToBit1(double delta) {
        rotate(Math.abs(delta));
    }

    /** Resets to the equal superposition. */
    public void reset() {
        theta = INITIAL_THETA;
    }

    /** @return an independent copy */
    public QuantumBit copy() {
        return new QuantumBit(theta);
    }

    private static double clamp(double value) {
        return Math.max(THETA_MIN, Math.min(THETA_MAX, value));
    }

    @Override
    public String toString() {
        return String.format("QB[theta=%.3fpi alpha^2=%.3f beta^2=%.3f]",
                theta / Math.PI, alphaSquared(), betaSquared());
    }
}
