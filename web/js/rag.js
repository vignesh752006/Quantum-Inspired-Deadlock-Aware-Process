/**
 * rag.js — Resource Allocation Graph + iterative DFS cycle detector.
 * Used by the interactive RAG demo and tests.html.
 */

/**
 * Build an adjacency list from process→resource request edges and
 * resource→process allocation edges.
 *
 * Node encoding (mirrors the Java):
 *   process i   → node i
 *   resource j  → node n + j
 *
 * @param {number} n - processes
 * @param {number} m - resource types
 * @param {number[][]} alloc - n×m allocation
 * @param {number[][]} need  - n×m need (request edges)
 * @returns {{ n, m, adj: Map<number,number[]>, edges: {from,to,type}[] }}
 */
export function buildRAG(n, m, alloc, need) {
  const totalNodes = n + m;
  const adj = new Map();
  for (let i = 0; i < totalNodes; i++) adj.set(i, []);
  const edges = [];

  for (let i = 0; i < n; i++) {
    for (let j = 0; j < m; j++) {
      // Allocation edge: resource j → process i
      if (alloc[i][j] > 0) {
        adj.get(n + j).push(i);
        edges.push({ from: n + j, to: i, type: 'alloc' });
      }
      // Request edge: process i → resource j
      if (need[i][j] > 0) {
        adj.get(i).push(n + j);
        edges.push({ from: i, to: n + j, type: 'request' });
      }
    }
  }
  return { n, m, adj, edges, totalNodes };
}

/**
 * Iterative DFS cycle detection (mirrors CycleDetector.java).
 * @returns {{ hasCycle: boolean, cycleNodes: number[] }}
 */
export function detectCycle({ n, m, adj, totalNodes }) {
  const visited = new Array(totalNodes).fill(false);
  const inStack = new Array(totalNodes).fill(false);
  const parent  = new Array(totalNodes).fill(-1);

  for (let start = 0; start < totalNodes; start++) {
    if (visited[start]) continue;

    // Iterative DFS using explicit stack of [node, iteratorIndex]
    const stack = [[start, 0]];
    visited[start] = true;
    inStack[start] = true;

    while (stack.length > 0) {
      const top = stack[stack.length - 1];
      const u = top[0];
      const neighbors = adj.get(u) || [];

      if (top[1] < neighbors.length) {
        const v = neighbors[top[1]++];
        if (!visited[v]) {
          visited[v] = true;
          inStack[v] = true;
          parent[v] = u;
          stack.push([v, 0]);
        } else if (inStack[v]) {
          // Cycle found — reconstruct it
          const cycle = [v];
          let cur = u;
          while (cur !== v) {
            cycle.push(cur);
            cur = parent[cur];
          }
          cycle.push(v);
          cycle.reverse();
          return { hasCycle: true, cycleNodes: cycle };
        }
      } else {
        inStack[u] = false;
        stack.pop();
      }
    }
  }
  return { hasCycle: false, cycleNodes: [] };
}

/**
 * Returns a human-readable label for a node.
 */
export function nodeLabel(node, n, processNames, resourceNames) {
  if (node < n) return processNames[node] || `P${node}`;
  return resourceNames[node - n] || `R${node - n}`;
}

// ── Presets ─────────────────────────────────────────────────────────────────

/** P0→R1→P1→R0→P0 circular wait (2-process deadlock ring) */
export const PRESET_RING = {
  n: 2, m: 2,
  processNames: ['P0','P1'],
  resourceNames: ['R0','R1'],
  alloc: [[1,0],[0,1]],   // R0→P0, R1→P1
  need:  [[0,1],[1,0]]    // P0→R1, P1→R0
};

/** 3-process safe chain (no cycle) */
export const PRESET_SAFE = {
  n: 3, m: 2,
  processNames: ['P0','P1','P2'],
  resourceNames: ['R0','R1'],
  alloc: [[1,0],[0,0],[0,1]],  // R0→P0, R1→P2
  need:  [[0,1],[1,0],[0,0]]   // P0→R1, P1→R0
};

// ── Canvas Renderer ──────────────────────────────────────────────────────────

const PI2 = Math.PI * 2;
const NODE_R = 22;

function getColor(isDark) {
  return {
    process:  isDark ? '#3b82f6' : '#2563eb',
    resource: isDark ? '#10b981' : '#059669',
    cycle:    '#ef4444',
    text:     isDark ? '#f1f5f9' : '#0f172a',
    bg:       isDark ? '#1e293b' : '#ffffff',
    edge:     isDark ? '#94a3b8' : '#64748b',
    arrow:    isDark ? '#94a3b8' : '#64748b',
  };
}

/**
 * Lay out nodes in two rows: processes top, resources bottom.
 */
function layout(n, m, W, H) {
  const positions = [];
  const py = H * 0.28;
  const ry = H * 0.72;
  for (let i = 0; i < n; i++) positions.push({ x: (W / (n + 1)) * (i + 1), y: py, type: 'process', idx: i });
  for (let j = 0; j < m; j++) positions.push({ x: (W / (m + 1)) * (j + 1), y: ry, type: 'resource', idx: j });
  return positions;
}

/**
 * Draw the RAG on a canvas element.
 */
export function drawRAG(canvas, ragData, cycleNodes = [], isDark = false) {
  const { n, m, edges, totalNodes } = ragData;
  const W = canvas.width = canvas.offsetWidth * window.devicePixelRatio || 600;
  const H = canvas.height = 320 * (window.devicePixelRatio || 1);
  const ctx = canvas.getContext('2d');
  const scale = window.devicePixelRatio || 1;
  ctx.scale(scale, scale);
  const w = W / scale, h = H / scale;

  const C = getColor(isDark);
  const pos = layout(n, m, w, h);

  ctx.fillStyle = C.bg;
  ctx.fillRect(0, 0, w, h);

  const cycleSet = new Set(cycleNodes);

  // Draw edges
  edges.forEach(({ from, to, type }) => {
    const f = pos[from], t = pos[to];
    const inCycle = cycleSet.has(from) && cycleSet.has(to);
    ctx.strokeStyle = inCycle ? C.cycle : C.edge;
    ctx.lineWidth   = inCycle ? 2.5 : 1.5;
    ctx.setLineDash(type === 'request' ? [5, 3] : []);
    drawArrow(ctx, f.x, f.y, t.x, t.y, NODE_R);
    ctx.setLineDash([]);
  });

  // Draw nodes
  pos.forEach((p, idx) => {
    const inCycle = cycleSet.has(idx);
    ctx.beginPath();
    if (p.type === 'resource') {
      ctx.rect(p.x - NODE_R, p.y - NODE_R, NODE_R * 2, NODE_R * 2);
    } else {
      ctx.arc(p.x, p.y, NODE_R, 0, PI2);
    }
    ctx.fillStyle = inCycle ? C.cycle :
                    (p.type === 'process' ? C.process : C.resource);
    ctx.fill();
    ctx.strokeStyle = C.bg;
    ctx.lineWidth = 2;
    ctx.stroke();

    const label = idx < n
      ? (ragData.processNames?.[idx] || `P${idx}`)
      : (ragData.resourceNames?.[idx - n] || `R${idx - n}`);
    ctx.fillStyle = '#fff';
    ctx.font = `bold ${13 / scale}px Inter, sans-serif`;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(label, p.x, p.y);
  });

  // Legend
  ctx.font = `${11}px Inter, sans-serif`;
  ctx.fillStyle = C.text;
  ctx.textAlign = 'left';
  ctx.textBaseline = 'top';
  ctx.fillStyle = C.process;  ctx.fillRect(8, 4, 10, 10); ctx.fillStyle = C.text; ctx.fillText('Process (circle)', 22, 4);
  ctx.fillStyle = C.resource; ctx.fillRect(8, 20, 10, 10); ctx.fillStyle = C.text; ctx.fillText('Resource (square)', 22, 20);
  if (cycleNodes.length > 0) {
    ctx.fillStyle = C.cycle; ctx.fillRect(8, 36, 10, 10); ctx.fillStyle = C.text; ctx.fillText('Cycle', 22, 36);
  }
}

function drawArrow(ctx, x1, y1, x2, y2, nodeR) {
  const dx = x2 - x1, dy = y2 - y1;
  const len = Math.hypot(dx, dy);
  const ux = dx / len, uy = dy / len;
  const sx = x1 + ux * nodeR, sy = y1 + uy * nodeR;
  const ex = x2 - ux * nodeR, ey = y2 - uy * nodeR;

  ctx.beginPath();
  ctx.moveTo(sx, sy);
  ctx.lineTo(ex, ey);
  ctx.stroke();

  // Arrowhead
  const angle = Math.atan2(ey - sy, ex - sx);
  const aSize = 8;
  ctx.beginPath();
  ctx.moveTo(ex, ey);
  ctx.lineTo(ex - aSize * Math.cos(angle - 0.4), ey - aSize * Math.sin(angle - 0.4));
  ctx.lineTo(ex - aSize * Math.cos(angle + 0.4), ey - aSize * Math.sin(angle + 0.4));
  ctx.closePath();
  ctx.fillStyle = ctx.strokeStyle;
  ctx.fill();
}
