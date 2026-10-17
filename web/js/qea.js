/**
 * qea.js — Simplified QEA visualizer for the interactive demo.
 * Simulates the quantum-inspired optimizer on a tiny toy scheduling problem.
 */

const PI_HALF = Math.PI / 2;

export class QuantumBit {
  constructor(theta = Math.PI / 4) {
    this.theta = Math.max(0.001, Math.min(PI_HALF - 0.001, theta));
  }
  get alpha2() { return Math.cos(this.theta) ** 2; }
  get beta2()  { return Math.sin(this.theta) ** 2; }
  observe(r)   { return r < this.alpha2 ? 0 : 1; }
  rotate(delta){ this.theta = Math.max(0.001, Math.min(PI_HALF - 0.001, this.theta + delta)); }
  rotateTo0(d) { this.rotate(-Math.abs(d)); }
  rotateTo1(d) { this.rotate(+Math.abs(d)); }
  reset()      { this.theta = Math.PI / 4; }
}

/**
 * Tiny deterministic fitness for demo:
 *   Evaluate a permutation order on a fixed 4-process toy state.
 *   Returns fitness (lower = better).
 */
export function toyFitness(order) {
  // Simulate: each process waits if not in a "good" slot
  // Optimal order is [0,2,1,3] giving fitness ~0
  const optimal = [0, 2, 1, 3];
  let score = 0;
  for (let i = 0; i < order.length; i++) {
    if (order[i] !== optimal[i]) score += (i + 1) * 2;
  }
  return score;
}

/** Convert bit array to a permutation via priority keys */
function bitsToOrder(bits, alphas, rngNoise) {
  const n = bits.length;
  const keys = bits.map((b, i) => ({ i, key: b * 100 + (1 - alphas[i]) + rngNoise[i] }));
  keys.sort((a, b) => a.key - b.key);
  return keys.map(k => k.i);
}

/** Simple seeded LCG random for reproducibility */
function lcgRandom(seed) {
  let s = seed;
  return () => { s = (s * 1664525 + 1013904223) & 0xffffffff; return (s >>> 0) / 0xffffffff; };
}

/**
 * Run one full QEA optimisation and return history for plotting.
 *
 * @param {object} params - { popSize, maxIter, convergenceLimit, deltaTheta, seed }
 * @returns {{ bestHistory: number[], avgHistory: number[], best: number, bestOrder: number[] }}
 */
export function runQEA({ popSize = 10, maxIter = 40, convergenceLimit = 10, deltaTheta = 0.05 * Math.PI, seed = 42 } = {}) {
  const n = 4; // fixed toy problem: 4 processes
  const rng = lcgRandom(seed);

  // Init population
  const qubits = Array.from({ length: popSize }, () =>
    Array.from({ length: n }, () => new QuantumBit())
  );

  let globalBest = null;
  let globalBestFit = Infinity;
  const bestHistory = [];
  const avgHistory  = [];
  let stagnation = 0;

  for (let iter = 0; iter < maxIter; iter++) {
    // Observe
    const candidates = qubits.map(ind => {
      const bits = ind.map(q => q.observe(rng()));
      const alphas = ind.map(q => q.alpha2);
      const noise = Array.from({ length: n }, () => rng() * 0.01);
      return { bits, order: bitsToOrder(bits, alphas, noise) };
    });

    // Evaluate
    const fitnesses = candidates.map(c => toyFitness(c.order));
    const genBestIdx = fitnesses.indexOf(Math.min(...fitnesses));
    const genBest = candidates[genBestIdx];
    const genBestFit = fitnesses[genBestIdx];

    if (genBestFit < globalBestFit) {
      globalBestFit = genBestFit;
      globalBest = { ...genBest };
      stagnation = 0;
    } else {
      stagnation++;
    }

    bestHistory.push(globalBestFit);
    avgHistory.push(fitnesses.reduce((a,b) => a+b,0) / popSize);

    // Rotate toward global best
    qubits.forEach((ind, k) => {
      const cur = candidates[k];
      for (let i = 0; i < n; i++) {
        if (globalBest.bits[i] === 0 && cur.bits[i] === 1) ind[i].rotateTo0(deltaTheta);
        else if (globalBest.bits[i] === 1 && cur.bits[i] === 0) ind[i].rotateTo1(deltaTheta);
      }
    });

    if (stagnation >= convergenceLimit) break;
  }

  return {
    bestHistory,
    avgHistory,
    best: globalBestFit,
    bestOrder: globalBest?.order || [],
    qubits: qubits[0].map(q => ({ alpha2: q.alpha2, beta2: q.beta2, theta: q.theta }))
  };
}
