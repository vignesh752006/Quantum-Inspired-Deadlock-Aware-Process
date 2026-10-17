/**
 * banker.js — Correct implementation of Banker's Algorithm safety check.
 * Used by the interactive demo AND the tests page.
 * No external dependencies.
 */

/**
 * Run Banker's safety algorithm on a state snapshot.
 *
 * @param {number}   n         - number of processes
 * @param {number}   m         - number of resource types
 * @param {number[]} available - length m
 * @param {number[][]} alloc   - n×m allocation matrix
 * @param {number[][]} need    - n×m need matrix
 * @returns {{ safe: boolean, sequence: number[], trace: string[] }}
 */
export function checkSafety(n, m, available, alloc, need) {
  const work   = available.slice();
  const finish = new Array(n).fill(false);
  const seq    = [];
  const trace  = [];

  let found = true;
  while (found) {
    found = false;
    for (let i = 0; i < n; i++) {
      if (!finish[i] && canRun(need[i], work, m)) {
        const before = work.slice();
        for (let j = 0; j < m; j++) work[j] += alloc[i][j];
        trace.push({ type: 'grant', pid: i, before, after: work.slice() });
        finish[i] = true;
        seq.push(i);
        found = true;
      }
    }
  }

  const safe = seq.length === n;
  if (!safe) {
    const blocked = [];
    for (let i = 0; i < n; i++) if (!finish[i]) blocked.push({ pid: i, need: need[i] });
    trace.push({ type: 'blocked', blocked });
  } else {
    trace.push({ type: 'safe', sequence: seq.slice() });
  }

  return { safe, sequence: seq, trace };
}

function canRun(needRow, work, m) {
  for (let j = 0; j < m; j++) if (needRow[j] > work[j]) return false;
  return true;
}

/** Compute need matrix from max and alloc. Throws if any need[i][j] < 0. */
export function computeNeed(n, m, max, alloc) {
  return Array.from({ length: n }, (_, i) =>
    Array.from({ length: m }, (_, j) => {
      const v = max[i][j] - alloc[i][j];
      if (v < 0) throw new Error(`need[${i}][${j}] = ${v} < 0 (alloc > max)`);
      return v;
    })
  );
}

/** Validate conservation: sum(alloc[*][j]) + available[j] == total[j] */
export function validateConservation(n, m, alloc, available, total) {
  for (let j = 0; j < m; j++) {
    let sum = available[j];
    for (let i = 0; i < n; i++) sum += alloc[i][j];
    if (sum !== total[j]) return false;
  }
  return true;
}

// ── Silberschatz 5-process 3-resource preset ────────────────────────────────

export const SILBERSCHATZ = {
  n: 5, m: 3,
  processNames: ['P0','P1','P2','P3','P4'],
  resourceNames: ['A','B','C'],
  available: [3, 3, 2],
  total:     [10, 5, 7],
  alloc: [
    [0,1,0],
    [2,0,0],
    [3,0,2],
    [2,1,1],
    [0,0,2]
  ],
  max: [
    [7,5,3],
    [3,2,2],
    [9,0,2],
    [2,2,2],
    [4,3,3]
  ]
};

// 2-process deadlock ring preset
export const DEADLOCK_RING = {
  n: 2, m: 2,
  processNames: ['P0','P1'],
  resourceNames: ['R0','R1'],
  available: [0, 0],
  total:     [1, 1],
  alloc: [[1,0],[0,1]],
  max:   [[1,1],[1,1]]
};
