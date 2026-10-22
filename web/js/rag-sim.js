/**
 * rag-sim.js — Step-by-step RAG + DFS cycle detection + Banker's safety simulation.
 * Replaces the contents of the "RAG + Cycle Detection" demo card at runtime.
 * Usage (app.js):  import { initRAGSim } from './rag-sim.js';  ...  initRAGSim();
 */
import { SILBERSCHATZ } from './banker.js';

const PRESETS = {
  'Deadlock ring (cycle, UNSAFE)': { n:2, m:2, avail:[0,0], alloc:[[1,0],[0,1]], max:[[1,1],[1,1]] },
  'Safe chain (no cycle)':         { n:3, m:2, avail:[0,0], alloc:[[1,0],[0,0],[0,1]], max:[[1,1],[1,0],[0,1]] },
  'Cycle but SAFE (multi-instance)': { n:3, m:2, avail:[0,0], alloc:[[1,0],[0,1],[1,0]], max:[[1,1],[1,1],[1,0]] },
  'Silberschatz 5×3':              { n:5, m:3, avail:SILBERSCHATZ.available, alloc:SILBERSCHATZ.alloc, max:SILBERSCHATZ.max },
};
const clone = o => JSON.parse(JSON.stringify(o));
const nm = (n, k) => (k < n ? `P${k}` : `R${k - n}`);
const vec = a => `[${a.join(', ')}]`;
const needOf = S => S.max.map((r, i) => r.map((x, j) => x - S.alloc[i][j]));

// ── Step generation ─────────────────────────────────────────────────────────
function buildSteps(S) {
  const { n, m, avail, alloc, max } = S;
  const need = needOf(S), steps = [], N = k => nm(n, k);
  const push = (title, html, hl = {}) => steps.push({ title, html, hl });

  // 1. Need matrix
  let t = '<table class="sim-t"><tr><th></th><th>Alloc</th><th>Max</th><th>Need = Max − Alloc</th></tr>';
  for (let i = 0; i < n; i++)
    t += `<tr><td>P${i}</td><td>${vec(alloc[i])}</td><td>${vec(max[i])}</td><td>${vec(max[i])} − ${vec(alloc[i])} = <b>${vec(need[i])}</b></td></tr>`;
  push('Step 1 — Compute Need matrix', t + `</table>Available = ${vec(avail)}`);

  // 2. Build RAG
  const adj = Array.from({ length: n + m }, () => []);
  const ed = [];
  for (let i = 0; i < n; i++) for (let j = 0; j < m; j++) {
    if (alloc[i][j] > 0) { adj[n + j].push(i); ed.push(`R${j} → P${i} (assignment, ${alloc[i][j]})`); }
    if (need[i][j] > 0)  { adj[i].push(n + j); ed.push(`P${i} → R${j} (request, ${need[i][j]})`); }
  }
  push('Step 2 — Build the graph', `Assignment edge R→P if Alloc[i][j] &gt; 0; request edge P→R if Need[i][j] &gt; 0.<br>${ed.join('<br>') || 'No edges.'}`);

  // 3. DFS
  const vis = new Set(), st = []; let cyc = null;
  const snap = x => ({ vis: [...vis], st: [...st], cycle: cyc, ...x });
  (function run() {
    const dfs = u => {
      vis.add(u); st.push(u);
      push(`DFS: visit ${N(u)}`, `Path stack: ${st.map(N).join(' → ')}<br>Neighbours of ${N(u)}: ${adj[u].map(N).join(', ') || 'none'}`, snap({ cur: u }));
      for (const w of adj[u]) {
        if (cyc) return;
        if (!vis.has(w)) {
          push(`Edge ${N(u)} → ${N(w)}`, `${N(w)} not visited yet → descend.`, snap({ cur: u, edge: [u, w] }));
          dfs(w);
        } else if (st.includes(w)) {
          cyc = [...st.slice(st.indexOf(w)), w];
          push('🔴 Cycle found', `Back-edge ${N(u)} → ${N(w)}: ${N(w)} is already on the current path.<br>Cycle: <b>${cyc.map(N).join(' → ')}</b>`, snap({ cur: u, edge: [u, w] }));
          return;
        } else push(`Edge ${N(u)} → ${N(w)}`, `${N(w)} already fully explored → no cycle through this edge.`, snap({ cur: u, edge: [u, w] }));
      }
      if (cyc) return;
      st.pop();
      push(`DFS: finish ${N(u)}`, `All neighbours explored; pop ${N(u)} from the stack.`, snap({ cur: st[st.length - 1] }));
    };
    for (let s = 0; s < n + m && !cyc; s++) if (!vis.has(s)) dfs(s);
  })();
  if (!cyc) push('DFS complete — no cycle', 'Every node explored, no back-edge found.', snap({}));

  // 4. Banker's safety
  let work = avail.slice(); const fin = Array(n).fill(false), seq = [];
  const bh = () => ({ vis: [...vis], cycle: cyc, done: fin.map((f, i) => f ? i : -1).filter(i => i >= 0) });
  push("Banker's: initialise", `Work = Available = ${vec(work)}; Finish = all false.`, bh());
  let prog = true, pass = 0;
  while (prog) {
    prog = false; pass++;
    for (let i = 0; i < n; i++) {
      if (fin[i]) continue;
      const cmp = need[i].map((x, j) => `${x}${x <= work[j] ? '≤' : '>'}${work[j]}`).join(', ');
      if (need[i].every((x, j) => x <= work[j])) {
        const nw = work.map((w, j) => w + alloc[i][j]);
        fin[i] = true; seq.push(i);
        push(`Pass ${pass}: P${i} can finish ✓`, `Need ${vec(need[i])} ≤ Work ${vec(work)} (${cmp})<br>Work = Work + Alloc[P${i}] = ${vec(work)} + ${vec(alloc[i])} = <b>${vec(nw)}</b><br>Sequence so far: &lt;${seq.map(k => 'P' + k).join(', ')}&gt;`, bh());
        work = nw; prog = true;
      } else push(`Pass ${pass}: P${i} must wait ✗`, `Need ${vec(need[i])} ≰ Work ${vec(work)} (${cmp})`, bh());
    }
  }
  const safe = seq.length === n;
  const stuck = fin.map((f, i) => f ? -1 : i).filter(i => i >= 0);
  push(safe ? '✓ Banker: SAFE' : '✗ Banker: UNSAFE',
    safe ? `Safe sequence: <b>&lt;${seq.map(k => 'P' + k).join(', ')}&gt;</b>` : `No process in {${stuck.map(k => 'P' + k).join(', ')}} can ever get its Need. Finished: ${seq.map(k => 'P' + k).join(', ') || 'none'}.`, bh());

  // 5. Combined verdict
  const v = cyc && !safe ? ['bad', 'DEADLOCK RISK — cycle found AND state is unsafe.']
    : cyc && safe ? ['warn', 'Cycle found but state is SAFE. With multi-instance resources a cycle is necessary, not sufficient, for deadlock.']
    : !cyc && safe ? ['ok', 'No cycle and a safe sequence exists — deadlock-free.']
    : ['bad', 'No cycle in the graph, but Banker finds the state UNSAFE (the claim graph is not the full story).'];
  push('Final verdict', `<span class="sim-${v[0]}">${v[1]}</span>`, bh());
  return steps;
}

// ── Canvas drawing ──────────────────────────────────────────────────────────
function draw(cv, S, hl = {}) {
  const dpr = window.devicePixelRatio || 1, W = cv.clientWidth || 600, H = 320;
  cv.width = W * dpr; cv.height = H * dpr;
  const g = cv.getContext('2d'); g.setTransform(dpr, 0, 0, dpr, 0, 0);
  const dark = document.documentElement.dataset.theme === 'dark';
  const { n, m } = S, need = needOf(S), R = 22;
  const pos = [];
  for (let i = 0; i < n; i++) pos.push({ x: W / (n + 1) * (i + 1), y: H * 0.25 });
  for (let j = 0; j < m; j++) pos.push({ x: W / (m + 1) * (j + 1), y: H * 0.75 });
  g.fillStyle = dark ? '#1e293b' : '#fff'; g.fillRect(0, 0, W, H);
  const vis = new Set(hl.vis || []), st = new Set(hl.st || []), done = new Set(hl.done || []);
  const cyc = hl.cycle || [], ce = new Set(cyc.slice(1).map((x, k) => `${cyc[k]}>${x}`));
  const arrow = (a, b, off, col, dash, w, lbl) => {
    const dx = b.x - a.x, dy = b.y - a.y, L = Math.hypot(dx, dy) || 1, ux = dx / L, uy = dy / L, px = -uy * off, py = ux * off;
    const sx = a.x + ux * R + px, sy = a.y + uy * R + py, ex = b.x - ux * R + px, ey = b.y - uy * R + py;
    g.strokeStyle = g.fillStyle = col; g.lineWidth = w; g.setLineDash(dash ? [5, 3] : []);
    g.beginPath(); g.moveTo(sx, sy); g.lineTo(ex, ey); g.stroke(); g.setLineDash([]);
    const an = Math.atan2(ey - sy, ex - sx);
    g.beginPath(); g.moveTo(ex, ey);
    g.lineTo(ex - 9 * Math.cos(an - 0.4), ey - 9 * Math.sin(an - 0.4));
    g.lineTo(ex - 9 * Math.cos(an + 0.4), ey - 9 * Math.sin(an + 0.4)); g.fill();
    if (lbl > 1) { g.font = '11px sans-serif'; g.textAlign = 'center'; g.fillText(lbl, (sx + ex) / 2 + px, (sy + ey) / 2 + py - 3); }
  };
  const edges = [];
  for (let i = 0; i < n; i++) for (let j = 0; j < m; j++) {
    if (S.alloc[i][j] > 0) edges.push([n + j, i, S.alloc[i][j], false]);
    if (need[i][j] > 0) edges.push([i, n + j, need[i][j], true]);
  }
  edges.forEach(([f, t, c, req]) => {
    const cur = hl.edge && hl.edge[0] === f && hl.edge[1] === t;
    const col = ce.has(`${f}>${t}`) ? '#ef4444' : cur ? '#f59e0b' : (dark ? '#94a3b8' : '#64748b');
    arrow(pos[f], pos[t], req ? -5 : 5, col, req, cur || ce.has(`${f}>${t}`) ? 3 : 1.5, c);
  });
  pos.forEach((p, k) => {
    const isP = k < n, base = isP ? '#2563eb' : '#059669';
    g.globalAlpha = vis.has(k) || done.has(k) || !hl.vis ? 1 : 0.45;
    g.fillStyle = cyc.includes(k) ? '#ef4444' : hl.cur === k ? '#f59e0b' : done.has(k) ? '#16a34a' : base;
    g.beginPath(); if (isP) g.arc(p.x, p.y, R, 0, 7); else g.rect(p.x - R, p.y - R, 2 * R, 2 * R); g.fill();
    g.globalAlpha = 1;
    if (st.has(k)) { g.strokeStyle = '#f59e0b'; g.lineWidth = 3; g.stroke(); }
    g.fillStyle = '#fff'; g.font = 'bold 13px sans-serif'; g.textAlign = 'center'; g.textBaseline = 'middle';
    g.fillText(nm(n, k), p.x, p.y);
  });
  g.textAlign = 'left'; g.textBaseline = 'top'; g.font = '11px sans-serif'; g.fillStyle = dark ? '#e2e8f0' : '#0f172a';
  g.fillText('solid = assignment (R→P) · dashed = request (P→R) · amber = current/on path · red = cycle · green = finished in Banker', 8, 4);
}

// ── UI ──────────────────────────────────────────────────────────────────────
export function initRAGSim() {
  const card = document.querySelector('#demo-rag .demo-card');
  if (!card) return;
  const st = document.createElement('style');
  st.textContent = `.sim-t{border-collapse:collapse;margin:.4rem 0;font-family:var(--font-mono);font-size:.8rem}.sim-t td,.sim-t th{border:1px solid var(--border);padding:.2rem .5rem;text-align:left}
  .sim-panel{border:1px solid var(--border);border-radius:var(--radius-sm);padding:var(--gap);margin-top:var(--gap);font-size:.875rem;min-height:90px;line-height:1.6}
  .sim-panel h4{margin-bottom:.3rem}.sim-log{max-height:170px;overflow:auto;font-family:var(--font-mono);font-size:.78rem;margin-top:var(--gap);border:1px solid var(--border);border-radius:var(--radius-sm);padding:.5rem}
  .sim-log div{padding:1px 0;color:var(--text-muted);cursor:pointer}.sim-log .on{color:var(--blue);font-weight:700}
  .sim-ok{color:var(--green);font-weight:700}.sim-bad{color:var(--red);font-weight:700}.sim-warn{color:var(--amber);font-weight:700}
  .sim-ed input{width:42px;text-align:center;margin:1px;border:1px solid var(--border);border-radius:4px;background:var(--bg);color:var(--text);font-family:var(--font-mono)}
  .sim-ed{display:grid;grid-template-columns:repeat(auto-fit,minmax(190px,1fr));gap:var(--gap);margin-top:var(--gap)}`;
  document.head.appendChild(st);

  card.innerHTML = `
    <h3 style="margin-bottom:var(--gap)">Resource Allocation Graph + Step-by-Step Deadlock Detection</h3>
    <div class="btn-row" style="align-items:center">
      <select id="sim-preset" class="filter-select">${Object.keys(PRESETS).map(k => `<option>${k}</option>`).join('')}</select>
      <button class="btn btn-secondary btn-sm" id="sim-edit">✏️ Custom example</button>
      <span style="flex:1"></span>
      <button class="btn btn-secondary btn-sm" id="sim-prev">⏮ Prev</button>
      <button class="btn btn-primary btn-sm" id="sim-next">⏭ Next</button>
      <button class="btn btn-secondary btn-sm" id="sim-play">▶ Auto-play</button>
      <button class="btn btn-secondary btn-sm" id="sim-all">⏩ Run all</button>
      <button class="btn btn-secondary btn-sm" id="sim-reset">↺ Reset</button>
    </div>
    <div id="sim-editor" style="display:none"></div>
    <canvas id="sim-canvas" class="demo-canvas" style="height:320px;margin:var(--gap) 0"></canvas>
    <div class="sim-panel" id="sim-panel"></div>
    <div class="sim-log" id="sim-log"></div>
    <div class="callout" style="margin-top:var(--gap);padding:var(--gap)"><strong>⚠️ Note:</strong> with <em>multi-instance</em> resources a cycle is necessary but not sufficient for deadlock — the final verdict combines the DFS result with Banker's safety check.</div>`;

  const $ = id => document.getElementById(id);
  let S, steps = [], idx = -1, timer = null;
  const cv = $('sim-canvas');

  function load(preset) {
    stop(); S = clone(preset); steps = buildSteps(S); idx = -1; renderEditor(); render();
  }
  function stop() { if (timer) { clearInterval(timer); timer = null; $('sim-play').textContent = '▶ Auto-play'; } }
  function render() {
    const s = steps[idx];
    draw(cv, S, s ? s.hl : {});
    $('sim-panel').innerHTML = s
      ? `<h4>${s.title} <small class="text-muted">(${idx + 1}/${steps.length})</small></h4>${s.html}`
      : `<em class="text-muted">Press <b>Next</b> or <b>Auto-play</b> to start. ${steps.length} steps will be generated for this example.</em>`;
    $('sim-log').innerHTML = steps.slice(0, idx + 1).map((x, k) => `<div class="${k === idx ? 'on' : ''}" data-k="${k}">${k + 1}. ${x.title}</div>`).join('');
    const lg = $('sim-log'); lg.scrollTop = lg.scrollHeight;
  }
  function go(k) { idx = Math.max(-1, Math.min(steps.length - 1, k)); render(); }

  function renderEditor() {
    const mk = (key, rows, label) => `<div><b>${label}</b><br>` +
      rows.map((r, i) => `<div>${key === 'avail' ? '' : `P${i} `}${r.map((v, j) => `<input type="number" min="0" data-k="${key}" data-i="${i}" data-j="${j}" value="${v}">`).join('')}</div>`).join('') + '</div>';
    $('sim-editor').innerHTML = `<div class="btn-row" style="align-items:center">Processes <input type="number" id="sim-n" min="1" max="8" value="${S.n}" style="width:50px"> Resource types <input type="number" id="sim-m" min="1" max="6" value="${S.m}" style="width:50px">
      <button class="btn btn-secondary btn-sm" id="sim-resize">Resize</button><button class="btn btn-primary btn-sm" id="sim-apply">Load this example</button><span id="sim-err" class="sim-bad"></span></div>
      <div class="sim-ed sim-ed-in">${mk('alloc', S.alloc, 'Allocation')}${mk('max', S.max, 'Maximum')}${mk('avail', [S.avail], 'Available')}</div>`;
  }
  function readEditor() {
    const T = { alloc: S.alloc.map(r => r.slice()), max: S.max.map(r => r.slice()), avail: [S.avail.slice()] };
    document.querySelectorAll('#sim-editor input[data-k]').forEach(e => { T[e.dataset.k][+e.dataset.i][+e.dataset.j] = parseInt(e.value) || 0; });
    return { n: S.n, m: S.m, alloc: T.alloc, max: T.max, avail: T.avail[0] };
  }

  $('sim-preset').onchange = e => load(PRESETS[e.target.value]);
  $('sim-edit').onclick = () => { const e = $('sim-editor'); e.style.display = e.style.display === 'none' ? 'block' : 'none'; };
  $('sim-editor').onclick = e => {
    if (e.target.id === 'sim-resize') {
      const n = Math.max(1, Math.min(8, +$('sim-n').value || 1)), m = Math.max(1, Math.min(6, +$('sim-m').value || 1)), z = (r, c) => Array.from({ length: r }, () => Array(c).fill(0));
      S = { n, m, alloc: z(n, m), max: z(n, m), avail: Array(m).fill(0) }; steps = []; idx = -1; stop(); renderEditor(); render();
    }
    if (e.target.id === 'sim-apply') {
      const T = readEditor(), bad = T.max.some((r, i) => r.some((v, j) => v < T.alloc[i][j]));
      if (bad) { $('sim-err').textContent = ' Max must be ≥ Allocation everywhere.'; return; }
      load(T); $('sim-editor').style.display = 'block';
    }
  };
  $('sim-next').onclick = () => { stop(); go(idx + 1); };
  $('sim-prev').onclick = () => { stop(); go(idx - 1); };
  $('sim-all').onclick = () => { stop(); go(steps.length - 1); };
  $('sim-reset').onclick = () => { stop(); go(-1); };
  $('sim-play').onclick = () => {
    if (timer) return stop();
    if (idx >= steps.length - 1) idx = -1;
    $('sim-play').textContent = '⏸ Pause';
    timer = setInterval(() => { if (idx >= steps.length - 1) stop(); else go(idx + 1); }, 1100);
  };
  $('sim-log').onclick = e => { const k = e.target.dataset.k; if (k !== undefined) { stop(); go(+k); } };
  document.getElementById('theme-toggle')?.addEventListener('click', () => setTimeout(render, 30));
  window.addEventListener('resize', render);
  document.querySelectorAll('.demo-tab').forEach(t => t.addEventListener('click', () => { if (t.dataset.panel === 'demo-rag') setTimeout(render, 60); }));

  load(PRESETS[Object.keys(PRESETS)[0]]);
}