/**
 * compare.js — Shared matrix input + all-algorithm comparison demo.
 *
 * JS implementations mirror the Java exactly:
 *   - Banker: uses banker.js (checkSafety / computeNeed)
 *   - QEA:    quantum-inspired optimizer on user matrix (soft fitness)
 *   - Hybrid: same optimizer but with Banker as hard constraint
 *
 * Fitness (lower = better):
 *   1000 × DeadlockPenalty + 100 × Unfinished + WaitingTime + ResourceWaste
 */

import { checkSafety, computeNeed } from './banker.js';

// ── Constants (match Java FitnessFunction) ────────────────────────────────────
const DEADLOCK_WEIGHT   = 1000;
const UNFINISHED_WEIGHT = 100;
const WAIT_WEIGHT       = 1;
const WASTE_WEIGHT      = 1;
const HYBRID_PENALTY    = 1e15;

// QEA defaults
const POP_SIZE          = 20;
const MAX_ITER          = 100;
const CONVERGENCE_LIMIT = 20;
const DELTA_THETA       = 0.05 * Math.PI;

// ── Seeded LCG RNG ─────────────────────────────────────────────────────────────
function makeLCG(seed) {
  let s = seed >>> 0;
  return () => {
    s = (Math.imul(s, 1664525) + 1013904223) >>> 0;
    return s / 0x100000000;
  };
}

// ── Fitness simulation (mirrors FitnessFunction.java) ─────────────────────────
function evaluateFitness(order, n, m, alloc, need, available, hybrid) {
  // Hybrid: if Banker says UNSAFE, hard-penalise immediately
  if (hybrid) {
    const bankerNeed = need; // already computed
    const bResult = checkSafety(n, m, available.slice(), alloc, bankerNeed);
    if (!bResult.safe) {
      return { fitness: HYBRID_PENALTY, isSafe: false, completed: 0, waitTime: n * 100, waste: 0 };
    }
  }

  const work = available.slice();
  const done = new Array(n).fill(false);
  let queue = order.slice();
  let completedCount = 0;
  let totalWaiting = 0;
  let progress = true;

  while (queue.length > 0 && progress) {
    progress = false;
    const nextQueue = [];
    for (const pid of queue) {
      let canRun = true;
      for (let j = 0; j < m; j++) {
        if (need[pid][j] > work[j]) { canRun = false; break; }
      }
      if (canRun) {
        for (let j = 0; j < m; j++) work[j] += alloc[pid][j];
        done[pid] = true;
        completedCount++;
        progress = true;
      } else {
        totalWaiting += 1;
        nextQueue.push(pid);
      }
    }
    queue = nextQueue;
  }

  const unfinished = n - completedCount;
  const deadlockPenalty = unfinished > 0 ? 1 : 0;
  const resourceWaste = work.reduce((s, v) => s + v, 0);
  const isSafe = unfinished === 0;

  if (hybrid && !isSafe) {
    return { fitness: HYBRID_PENALTY, isSafe: false, completed: completedCount, waitTime: totalWaiting, waste: resourceWaste };
  }

  const fitness = DEADLOCK_WEIGHT * deadlockPenalty
                + UNFINISHED_WEIGHT * unfinished
                + WAIT_WEIGHT * totalWaiting
                + WASTE_WEIGHT * resourceWaste;

  return { fitness, isSafe, completed: completedCount, waitTime: totalWaiting, waste: resourceWaste };
}

// ── Qubit helpers ──────────────────────────────────────────────────────────────
const HALF_PI = Math.PI / 2;
function clamp(t) { return Math.max(0.001, Math.min(HALF_PI - 0.001, t)); }
function makeQubit(theta = Math.PI / 4) { return { theta: clamp(theta) }; }
function alpha2(q) { return Math.cos(q.theta) ** 2; }
function observe(q, r) { return r < alpha2(q) ? 0 : 1; }
function rotate(q, delta) { q.theta = clamp(q.theta + delta); }

// Convert bit array → permutation using priority keys (mirrors Java QuantumPopulation)
function bitsToOrder(bits, alphas, rng, n) {
  const keys = bits.map((b, i) => ({ i, key: b * 100 + (1 - alphas[i]) + rng() * 0.01 }));
  keys.sort((a, b) => a.key - b.key);
  return keys.map(k => k.i);
}

// ── QEA / Hybrid optimizer ────────────────────────────────────────────────────
export function runQEAReal({ n, m, alloc, need, available, hybrid = false, seed = 42 }) {
  const rng = makeLCG(seed);
  const HALF = Math.PI / 4;

  // Initialise population: popSize individuals, each with n qubits
  const population = Array.from({ length: POP_SIZE }, () =>
    Array.from({ length: n }, () => makeQubit(HALF))
  );

  let globalBest = null;
  let globalBestFit = Infinity;
  const bestHistory = [];
  const avgHistory  = [];
  let stagnation = 0;
  const t0 = performance.now();

  for (let iter = 0; iter < MAX_ITER; iter++) {
    // Observe → candidate permutations
    const candidates = population.map(ind => {
      const bits   = ind.map(q => observe(q, rng()));
      const alphas = ind.map(q => alpha2(q));
      return { bits, order: bitsToOrder(bits, alphas, rng, n) };
    });

    // Evaluate
    let genBest = null, genBestFit = Infinity;
    let sumFit = 0;

    for (const cand of candidates) {
      const ev = evaluateFitness(cand.order, n, m, alloc, need, available, hybrid);
      cand.fitness = ev.fitness;
      cand.eval    = ev;
      sumFit += Math.min(ev.fitness, 1e12); // cap for avg display
      if (ev.fitness < genBestFit) { genBestFit = ev.fitness; genBest = cand; }
    }

    // Update global best
    if (genBestFit < globalBestFit) {
      globalBestFit = genBestFit;
      globalBest = { ...genBest };
      stagnation = 0;
    } else {
      stagnation++;
    }

    bestHistory.push(globalBestFit);
    avgHistory.push(sumFit / POP_SIZE);

    // Q-gate rotation toward global best
    population.forEach((ind, k) => {
      const cur = candidates[k];
      for (let i = 0; i < n; i++) {
        const bestBit = globalBest.bits[i];
        const curBit  = cur.bits[i];
        if (bestBit === 0 && curBit === 1) rotate(ind[i], -Math.abs(DELTA_THETA));
        else if (bestBit === 1 && curBit === 0) rotate(ind[i], +Math.abs(DELTA_THETA));
      }
    });

    if (stagnation >= CONVERGENCE_LIMIT) break;
  }

  const elapsed = performance.now() - t0;
  return {
    order:       globalBest?.order || [],
    eval:        globalBest?.eval  || { fitness: Infinity, isSafe: false, completed: 0, waitTime: 0, waste: 0 },
    bestHistory,
    avgHistory,
    iters:       bestHistory.length,
    elapsed,
  };
}

// ── Matrix DOM helpers ────────────────────────────────────────────────────────
function matVal(prefix, i, j) {
  const el = document.getElementById(`${prefix}-${i}-${j}`);
  return el ? parseInt(el.value) || 0 : 0;
}
function availVal(j) {
  const el = document.getElementById(`cmp-avail-${j}`);
  return el ? parseInt(el.value) || 0 : 0;
}

function readState() {
  const nSel = document.getElementById('cmp-n');
  const mSel = document.getElementById('cmp-m');
  const n = parseInt(nSel?.value || 5);
  const m = parseInt(mSel?.value || 3);

  const alloc     = Array.from({ length: n }, (_, i) =>
    Array.from({ length: m }, (_, j) => matVal('cmp-alloc', i, j)));
  const max       = Array.from({ length: n }, (_, i) =>
    Array.from({ length: m }, (_, j) => matVal('cmp-max', i, j)));
  const available = Array.from({ length: m }, (_, j) => availVal(j));

  return { n, m, alloc, max, available };
}

// ── Build matrix grid HTML ────────────────────────────────────────────────────
function buildMatrixGrid(prefix, n, m, defaultVals) {
  const rows = Array.from({ length: n }, (_, i) => {
    const cells = Array.from({ length: m }, (_, j) => {
      const val = defaultVals?.[i]?.[j] ?? 0;
      return `<input class="matrix-cell cmp-cell" id="${prefix}-${i}-${j}"
        type="number" min="0" max="99" value="${val}"
        aria-label="${prefix} P${i} R${j}" />`;
    }).join('');
    return `<div class="matrix-row"><label>P${i}</label>${cells}</div>`;
  }).join('');
  return rows;
}

function buildAvailRow(m, defaultVals) {
  return Array.from({ length: m }, (_, j) => {
    const val = defaultVals?.[j] ?? 0;
    return `<input class="matrix-cell cmp-cell" id="cmp-avail-${j}"
      type="number" min="0" max="99" value="${val}"
      aria-label="Available R${j}" />`;
  }).join('');
}

// Silberschatz 5×3 defaults
const SILB_DEFAULTS = {
  n: 5, m: 3,
  alloc:     [[0,1,0],[2,0,0],[3,0,2],[2,1,1],[0,0,2]],
  max:       [[7,5,3],[3,2,2],[9,0,2],[2,2,2],[4,3,3]],
  available: [3,3,2],
};

// ── Re-render matrices when n/m dropdowns change ──────────────────────────────
function rebuildMatrices(n, m) {
  document.getElementById('cmp-alloc-body').innerHTML = buildMatrixGrid('cmp-alloc', n, m, null);
  document.getElementById('cmp-max-body').innerHTML   = buildMatrixGrid('cmp-max',   n, m, null);
  document.getElementById('cmp-avail-body').innerHTML = buildAvailRow(m, null);
  // Update resource column headers
  ['cmp-alloc-head','cmp-max-head'].forEach(id => {
    const el = document.getElementById(id);
    if (el) el.innerHTML = '<th>Proc</th>' + Array.from({length:m},(_,j)=>`<th>R${j}</th>`).join('');
  });
  clearResults();
}

function loadPreset(preset) {
  // Set dropdowns
  const nSel = document.getElementById('cmp-n');
  const mSel = document.getElementById('cmp-m');
  if (nSel) nSel.value = preset.n;
  if (mSel) mSel.value = preset.m;

  // Rebuild grids to correct size
  rebuildMatrices(preset.n, preset.m);

  // Fill values
  for (let i = 0; i < preset.n; i++) {
    for (let j = 0; j < preset.m; j++) {
      const aEl = document.getElementById(`cmp-alloc-${i}-${j}`);
      const mEl = document.getElementById(`cmp-max-${i}-${j}`);
      if (aEl) aEl.value = preset.alloc[i][j];
      if (mEl) mEl.value = preset.max[i][j];
    }
  }
  for (let j = 0; j < preset.m; j++) {
    const el = document.getElementById(`cmp-avail-${j}`);
    if (el) el.value = preset.available[j];
  }
  clearResults();
}

function clearResults() {
  ['cmp-banker-result','cmp-qea-result','cmp-hybrid-result'].forEach(id => {
    const el = document.getElementById(id);
    if (el) el.innerHTML = '<div class="cmp-placeholder">Awaiting run…</div>';
  });
}

// ── Build result card HTML ────────────────────────────────────────────────────
function seqChips(order, algo, n) {
  if (!order || order.length === 0) return '<span class="cmp-na">— no safe sequence —</span>';
  const MAX_SHOW = 10;
  const chips = order.map((pid, i) => {
    const arr = i < order.length - 1 ? '<span class="seq-arrow">›</span>' : '';
    return `<span class="seq-chip ${algo}" title="P${pid}">P${pid}</span>${arr}`;
  });
  if (order.length <= MAX_SHOW) {
    return `<div class="seq-wrap">${chips.join('')}</div>`;
  }
  const uid = `cmp-${algo}-${n}-${Math.random().toString(36).slice(2,5)}`;
  return `<div class="seq-wrap">
    ${chips.slice(0, MAX_SHOW).join('')}
    <span id="${uid}-r" style="display:none">${chips.slice(MAX_SHOW).join('')}</span>
    <button class="seq-toggle" onclick="var r=document.getElementById('${uid}-r');var shown=r.style.display!=='none';r.style.display=shown?'none':'inline';this.textContent=shown?'…+${order.length-MAX_SHOW} more':'‹ less';">…+${order.length-MAX_SHOW} more</button>
  </div>`;
}

function renderResult(containerId, algo, { order, isSafe, completed, n, fitness, elapsed, iters }) {
  const el = document.getElementById(containerId);
  if (!el) return;

  const safeIcon  = isSafe ? '✓' : '✗';
  const safeColor = isSafe ? 'var(--green)' : 'var(--red)';
  const fitnessDisplay = fitness >= 1e14 ? '∞ (unsafe)' : fitness.toFixed(1);

  el.innerHTML = `
    <div class="cmp-result-header">
      <span class="algo-pill ${algo}">${algo}</span>
      <span style="color:${safeColor}; font-weight:700; font-size:1rem">${safeIcon} ${isSafe ? 'SAFE' : 'UNSAFE'}</span>
    </div>
    <div class="cmp-metrics">
      <div class="cmp-metric"><span class="cmp-metric-label">Completed</span><span class="cmp-metric-val">${completed}/${n}</span></div>
      <div class="cmp-metric"><span class="cmp-metric-label">Fitness</span><span class="cmp-metric-val">${fitnessDisplay}</span></div>
      <div class="cmp-metric"><span class="cmp-metric-label">Time</span><span class="cmp-metric-val">${elapsed.toFixed(1)} ms</span></div>
      ${iters !== undefined ? `<div class="cmp-metric"><span class="cmp-metric-label">Iterations</span><span class="cmp-metric-val">${iters}</span></div>` : ''}
    </div>
    <div class="cmp-seq-label">Execution Sequence</div>
    ${seqChips(order, algo, n)}
  `;
}

// ── Main runner ────────────────────────────────────────────────────────────────
function runAllAlgorithms() {
  const statusEl = document.getElementById('cmp-status');
  if (statusEl) statusEl.textContent = 'Running…';

  let state;
  try {
    state = readState();
  } catch (e) {
    if (statusEl) statusEl.textContent = `Error reading matrix: ${e.message}`;
    return;
  }

  const { n, m, alloc, max, available } = state;

  // Compute need matrix
  let need;
  try {
    need = computeNeed(n, m, max, alloc);
  } catch (e) {
    if (statusEl) statusEl.textContent = `⚠ ${e.message}`;
    ['cmp-banker-result','cmp-qea-result','cmp-hybrid-result'].forEach(id => {
      const el = document.getElementById(id);
      if (el) el.innerHTML = `<div class="cmp-error">${e.message}</div>`;
    });
    return;
  }

  if (statusEl) statusEl.textContent = '';

  // ── 1. Banker ──────────────────────────────────────────────────────────────
  const t0 = performance.now();
  const bankerResult = checkSafety(n, m, available.slice(), alloc, need);
  const bankerElapsed = performance.now() - t0;

  // Evaluate Banker's safe sequence through the same fitness simulation
  // so metrics are directly comparable with QEA/Hybrid.
  // Note: wait is always 0 for a safe sequence — Banker only schedules a
  // process when Need[i] ≤ Work, so no process ever blocks. Waste is the
  // total idle resources remaining after all processes finish.
  let bankerEval;
  if (bankerResult.safe) {
    bankerEval = evaluateFitness(bankerResult.sequence, n, m, alloc, need, available, false);
  } else {
    bankerEval = { fitness: Infinity, isSafe: false, completed: 0, waitTime: n * 100, waste: 0 };
  }

  renderResult('cmp-banker-result', 'Banker', {
    order:    bankerResult.safe ? bankerResult.sequence : [],
    isSafe:   bankerResult.safe,
    completed: bankerEval.completed,
    n, fitness: bankerEval.fitness, elapsed: bankerElapsed,
    waitTime: bankerEval.waitTime, waste: bankerEval.waste,
  });

  // ── 2. QEA ────────────────────────────────────────────────────────────────
  const qeaSeed = Date.now() & 0xffff;
  const qeaOut = runQEAReal({ n, m, alloc, need, available, hybrid: false, seed: qeaSeed });

  renderResult('cmp-qea-result', 'QEA', {
    order:    qeaOut.order,
    isSafe:   qeaOut.eval.isSafe,
    completed:qeaOut.eval.completed,
    n, fitness: qeaOut.eval.fitness, elapsed: qeaOut.elapsed,
    iters: qeaOut.iters,
    waitTime: qeaOut.eval.waitTime, waste: qeaOut.eval.waste,
  });

  // ── 3. Hybrid ─────────────────────────────────────────────────────────────
  const hybSeed = (Date.now() + 1) & 0xffff;
  const hybOut = runQEAReal({ n, m, alloc, need, available, hybrid: true, seed: hybSeed });

  renderResult('cmp-hybrid-result', 'Hybrid', {
    order:    hybOut.order,
    isSafe:   hybOut.eval.isSafe,
    completed:hybOut.eval.completed,
    n, fitness: hybOut.eval.fitness, elapsed: hybOut.elapsed,
    iters: hybOut.iters,
    waitTime: hybOut.eval.waitTime, waste: hybOut.eval.waste,
  });
}

// ── Public init function ──────────────────────────────────────────────────────
export function initCompare() {
  // n/m dropdowns
  const nSel = document.getElementById('cmp-n');
  const mSel = document.getElementById('cmp-m');
  if (nSel) nSel.addEventListener('change', () => {
    const n = parseInt(nSel.value);
    const m = parseInt(mSel?.value || 3);
    rebuildMatrices(n, m);
  });
  if (mSel) mSel.addEventListener('change', () => {
    const n = parseInt(nSel?.value || 5);
    const m = parseInt(mSel.value);
    rebuildMatrices(n, m);
  });

  // Preset button
  const presetBtn = document.getElementById('cmp-preset-silb');
  if (presetBtn) presetBtn.addEventListener('click', () => loadPreset(SILB_DEFAULTS));

  // Run button
  const runBtn = document.getElementById('cmp-run');
  if (runBtn) runBtn.addEventListener('click', runAllAlgorithms);

  // Load preset on init
  loadPreset(SILB_DEFAULTS);
}
