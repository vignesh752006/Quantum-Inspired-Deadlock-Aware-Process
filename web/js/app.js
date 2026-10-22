/**
 * app.js — Main application entry point.
 * Loads content.json + config.json, wires up all sections,
 * demos, results dashboard, dark-mode toggle, and nav.
 */

import { checkSafety, computeNeed, SILBERSCHATZ, DEADLOCK_RING } from './banker.js';
import { buildRAG, detectCycle, drawRAG, PRESET_RING, PRESET_SAFE } from './rag.js';
import { runQEA } from './qea.js';
import { loadData, buildAllCharts, buildSummaryTable, buildFairness, downloadCSV, getFilteredData } from './results.js';
import { Chart, registerables } from 'https://cdn.jsdelivr.net/npm/chart.js@4.4.3/+esm';
Chart.register(...registerables);
import { initRAGSim } from './rag-sim.js';

// ── Theme ─────────────────────────────────────────────────────────────────────
(function initTheme() {
  const saved = localStorage.getItem('theme') ||
    (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
  document.documentElement.setAttribute('data-theme', saved);
})();

function toggleTheme() {
  const cur = document.documentElement.getAttribute('data-theme');
  const next = cur === 'dark' ? 'light' : 'dark';
  document.documentElement.setAttribute('data-theme', next);
  localStorage.setItem('theme', next);
  document.getElementById('theme-icon').textContent = next === 'dark' ? '☀️' : '🌙';
  // Redraw RAG if visible
  ragRender();
}

// ── Boot ──────────────────────────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', async () => {
  // Wire theme toggle
  document.getElementById('theme-toggle').addEventListener('click', toggleTheme);
  const isDark = () => document.documentElement.getAttribute('data-theme') === 'dark';
  document.getElementById('theme-icon').textContent = isDark() ? '☀️' : '🌙';

  // Load data files in parallel
  const [content, config] = await Promise.all([
    fetch('./data/content.json').then(r => r.json()).catch(() => ({})),
    fetch('./data/config.json').then(r => r.json()).catch(() => ({})),
  ]);

  // Build dynamic sections
  buildDisclaimers(content);
  buildConcepts(content);
  buildArchitecture(content);
  buildParams(config);
  buildDesign(content, config);

  // Demos
  initDemoTabs();
  initRAGDemo();
  initRAGSim();
  initBankerDemo();
  initQEADemo();

  // Results
  await initResults(config);

  // Nav active-section tracking
  initNavSpy();
});

// ── Section builders ──────────────────────────────────────────────────────────

function buildDisclaimers(c) {
  const el = document.getElementById('disclaimer-list');
  if (!el || !c.disclaimer) return;
  el.innerHTML = c.disclaimer.items.map(item => `
    <div class="disclaimer-item">
      <div class="disclaimer-icon">${item.icon}</div>
      <div class="disclaimer-body">
        <h4>${item.title}</h4>
        <p>${item.body}</p>
      </div>
    </div>`).join('');
}

function buildConcepts(c) {
  if (!c.concepts) return;

  // Coffman conditions
  const coffEl = document.getElementById('coffman-grid');
  if (coffEl) coffEl.innerHTML = c.concepts.coffman.conditions.map((cond, i) => `
    <div class="coffman-card">
      <div class="coffman-num">${i+1}</div>
      <div class="coffman-name">${cond.name}</div>
      <div class="coffman-desc">${cond.description}</div>
    </div>`).join('');

  // Banker data structures
  const dsEl = document.getElementById('ds-tbody');
  if (dsEl) dsEl.innerHTML = c.concepts.bankerStructures.items.map(item => `
    <tr><td><code>${item.name}</code></td><td>${item.description}</td></tr>`).join('');

  // Comparison table
  const cmpEl = document.getElementById('compare-tbody');
  if (cmpEl) cmpEl.innerHTML = c.concepts.comparisonTable.rows.map(row => `
    <tr>
      <td><strong>${row.method}</strong></td>
      <td>${row.question}</td>
      <td style="color:var(--green)">${row.answer_safe}</td>
      <td style="color:var(--red)">${row.answer_unsafe}</td>
      <td><code>${row.complexity}</code></td>
    </tr>`).join('');
}

function buildArchitecture(c) {
  if (!c.architecture) return;

  const pipeEl = document.getElementById('pipeline-nodes');
  if (pipeEl) {
    pipeEl.innerHTML = c.architecture.pipeline.map((node, i) => {
      const arrow = i < c.architecture.pipeline.length - 1
        ? '<span class="pipe-arrow" aria-hidden="true">→</span>' : '';
      return `<div class="pipe-node" title="${node.desc}"><span>${node.label}</span><span class="pipe-node-sub">${node.id}</span></div>${arrow}`;
    }).join('');
  }

  const clsEl = document.getElementById('class-tbody');
  if (clsEl) clsEl.innerHTML = c.architecture.classes.map(cls => `
    <tr>
      <td class="file">${cls.file}</td>
      <td><span class="step-badge">Step ${cls.step}</span></td>
      <td>${cls.desc}</td>
    </tr>`).join('');
}

function buildParams(cfg) {
  const el = document.getElementById('param-tbody');
  if (!el || !cfg.parameters) return;
  el.innerHTML = cfg.parameters.map(p => `
    <tr>
      <td><strong>${p.name}</strong></td>
      <td class="param-value">${p.value}</td>
      <td>${p.unit}</td>
      <td><span class="status-badge status-${p.status}">${p.status}</span></td>
      <td class="text-muted">${p.rationale}</td>
    </tr>`).join('');
}

function buildDesign(content, config) {
  // Sizes table
  const sizesEl = document.getElementById('sizes-tbody');
  if (sizesEl && config.benchmarkSizes) {
    sizesEl.innerHTML = config.benchmarkSizes.map(s => `
      <tr>
        <td>${s.n}</td><td>${s.m}</td><td>${s.label}</td>
        <td>5</td><td>30</td><td>30</td><td>${s.n * (5 + 60)}</td>
      </tr>`).join('');
  }
  // Principles
  const princEl = document.getElementById('principles-list');
  if (princEl && content.experimentalDesign) {
    princEl.innerHTML = content.experimentalDesign.principles.map(p =>
      `<li>${p}</li>`).join('');
  }
}

// ── Demo Tabs ─────────────────────────────────────────────────────────────────
function initDemoTabs() {
  document.querySelectorAll('.demo-tab').forEach(tab => {
    tab.addEventListener('click', () => {
      document.querySelectorAll('.demo-tab').forEach(t => t.classList.remove('active'));
      document.querySelectorAll('.demo-panel').forEach(p => p.classList.remove('active'));
      tab.classList.add('active');
      const target = document.getElementById(tab.dataset.panel);
      if (target) target.classList.add('active');
      if (tab.dataset.panel === 'demo-rag') setTimeout(ragRender, 50);
    });
  });
}

// ── RAG Demo ──────────────────────────────────────────────────────────────────
let currentRAG = null;
let currentCycle = [];

function ragRender() {
  const canvas = document.getElementById('rag-canvas');
  if (!canvas || !currentRAG) return;
  const isDark = document.documentElement.getAttribute('data-theme') === 'dark';
  drawRAG(canvas, currentRAG, currentCycle, isDark);
}

function initRAGDemo() {
  const ringBtn = document.getElementById('rag-preset-ring');
  const safeBtn = document.getElementById('rag-preset-safe');
  const runBtn  = document.getElementById('rag-run');
  const out     = document.getElementById('rag-output');

  function loadPreset(preset) {
    const { n, m, alloc, need, processNames, resourceNames } = preset;
    currentRAG = { ...buildRAG(n, m, alloc, need), processNames, resourceNames };
    currentCycle = [];
    ragRender();
    out.innerHTML = '<em>Click "Detect Cycles" to run DFS.</em>';
  }

  if (ringBtn) ringBtn.addEventListener('click', () => loadPreset(PRESET_RING));
  if (safeBtn) safeBtn.addEventListener('click', () => loadPreset(PRESET_SAFE));

  if (runBtn) runBtn.addEventListener('click', () => {
    if (!currentRAG) return;
    const result = detectCycle(currentRAG);
    currentCycle = result.cycleNodes;
    ragRender();

    if (result.hasCycle) {
      const labels = result.cycleNodes.map(n =>
        n < currentRAG.n
          ? (currentRAG.processNames?.[n] || `P${n}`)
          : (currentRAG.resourceNames?.[n - currentRAG.n] || `R${n - currentRAG.n}`)
      );
      out.innerHTML = `<span style="color:var(--red)">⚠ CYCLE DETECTED: ${labels.join(' → ')}</span>
        <br><small style="color:var(--text-muted)">Note: with multi-instance resources, a cycle is necessary but not sufficient for deadlock. Run Banker's Algorithm for a definitive verdict.</small>`;
    } else {
      out.innerHTML = `<span style="color:var(--green)">✓ NO CYCLE DETECTED — state appears deadlock-free (for single-instance resources this is definitive)</span>`;
    }
  });

  // Load ring preset on init
  loadPreset(PRESET_RING);
}

// ── Banker Demo ───────────────────────────────────────────────────────────────
let bankerSteps = [];
let bankerStepIdx = 0;

function initBankerDemo() {
  const presetSilbBtn = document.getElementById('banker-preset-silb');
  const presetRingBtn = document.getElementById('banker-preset-ring');
  const runBtn        = document.getElementById('banker-run');
  const stepBtn       = document.getElementById('banker-step');
  const resetBtn      = document.getElementById('banker-reset-btn');
  const logEl         = document.getElementById('banker-log');
  const verdictEl     = document.getElementById('banker-verdict');

  function loadBankerPreset(preset) {
    const { n, m, alloc, max, available } = preset;
    // Fill 5x3 inputs (zeroing out unneeded cells if preset has fewer)
    for (let i = 0; i < 5; i++) {
      for (let j = 0; j < 3; j++) {
        const aEl = document.getElementById(`alloc-${i}-${j}`);
        const mEl = document.getElementById(`max-${i}-${j}`);
        if (aEl) aEl.value = (i < n && j < m) ? alloc[i][j] : 0;
        if (mEl) mEl.value = (i < n && j < m) ? max[i][j] : 0;
      }
    }
    for (let j = 0; j < 3; j++) {
      const el = document.getElementById(`avail-${j}`);
      if (el) el.value = (j < m) ? available[j] : 0;
    }
    resetBankerLog();
  }

  function resetBankerLog() {
    bankerSteps = [];
    bankerStepIdx = 0;
    if (logEl) logEl.innerHTML = '<em class="text-muted">Click "Run All" or "Step" to execute.</em>';
    if (verdictEl) { verdictEl.textContent = 'Awaiting execution...'; verdictEl.className = 'verdict verdict-pending'; }
  }

  function readMatrix(name, n, m) {
    return Array.from({ length: n }, (_, i) =>
      Array.from({ length: m }, (_, j) => {
        const el = document.getElementById(`${name}-${i}-${j}`);
        return el ? parseInt(el.value) || 0 : 0;
      })
    );
  }

  function runBanker(stepByStep = false) {
    const n = 5, m = 3;
    const alloc = readMatrix('alloc', n, m);
    const max   = readMatrix('max', n, m);
    const available = Array.from({ length: m }, (_, j) => {
      const el = document.getElementById(`avail-${j}`);
      return el ? parseInt(el.value) || 0 : 0;
    });
    let need;
    try { need = computeNeed(n, m, max, alloc); }
    catch (e) { if (logEl) logEl.innerHTML = `<span class="log-unsafe">Error: ${e.message}</span>`; return; }

    const result = checkSafety(n, m, available, alloc, need);
    bankerSteps = result.trace;
    bankerStepIdx = 0;

    if (!stepByStep) {
      showAllSteps(result, n);
    }
  }

  function showAllSteps(result, n) {
    if (!logEl) return;
    let html = '';
    result.trace.forEach(step => {
      if (step.type === 'grant') {
        html += `<div class="log-step">GRANT P${step.pid}: Work ${JSON.stringify(step.before)} → ${JSON.stringify(step.after)}</div>`;
      } else if (step.type === 'blocked') {
        html += `<div class="log-block">BLOCKED: ${step.blocked.map(b => `P${b.pid} need=${JSON.stringify(b.need)}`).join(', ')}</div>`;
      } else if (step.type === 'safe') {
        html += `<div class="log-safe">✓ SAFE — Sequence: &lt;${step.sequence.map(i => `P${i}`).join(', ')}&gt;</div>`;
      }
    });
    logEl.innerHTML = html;
    logEl.scrollTop = logEl.scrollHeight;
    if (verdictEl) {
      if (result.safe) {
        verdictEl.textContent = `✓ SAFE — Sequence: <${result.sequence.map(i=>'P'+i).join(', ')}>`;
        verdictEl.className = 'verdict verdict-safe';
      } else {
        verdictEl.textContent = `✗ UNSAFE — No safe sequence exists`;
        verdictEl.className = 'verdict verdict-unsafe';
      }
    }
  }

  function showNextStep() {
    if (!bankerSteps.length) { runBanker(true); return; }
    if (bankerStepIdx >= bankerSteps.length) return;
    const step = bankerSteps[bankerStepIdx++];
    const logEl = document.getElementById('banker-log');
    if (!logEl) return;
    if (bankerStepIdx === 1) logEl.innerHTML = '';

    if (step.type === 'grant') {
      logEl.innerHTML += `<div class="log-step">Step ${bankerStepIdx}: GRANT P${step.pid}: Work ${JSON.stringify(step.before)} → ${JSON.stringify(step.after)}</div>`;
    } else if (step.type === 'blocked') {
      logEl.innerHTML += `<div class="log-block">BLOCKED: ${step.blocked.map(b => `P${b.pid} need=${JSON.stringify(b.need)}`).join(', ')}</div>`;
    } else if (step.type === 'safe') {
      logEl.innerHTML += `<div class="log-safe">✓ SAFE — &lt;${step.sequence.map(i=>'P'+i).join(', ')}&gt;</div>`;
      if (verdictEl) { verdictEl.textContent = `✓ SAFE — Sequence: <${step.sequence.map(i=>'P'+i).join(', ')}>`; verdictEl.className = 'verdict verdict-safe'; }
    }
    if (step.type === 'blocked') {
      if (verdictEl) { verdictEl.textContent = '✗ UNSAFE — No safe sequence exists'; verdictEl.className = 'verdict verdict-unsafe'; }
    }
    logEl.scrollTop = logEl.scrollHeight;
  }

  if (presetSilbBtn) presetSilbBtn.addEventListener('click', () => loadBankerPreset(SILBERSCHATZ));
  if (presetRingBtn) presetRingBtn.addEventListener('click', () => loadBankerPreset(DEADLOCK_RING));
  if (runBtn)        runBtn.addEventListener('click',   () => runBanker(false));
  if (stepBtn)       stepBtn.addEventListener('click',  showNextStep);
  if (resetBtn)      resetBtn.addEventListener('click', resetBankerLog);

  // Load Silberschatz by default
  loadBankerPreset(SILBERSCHATZ);
}

// ── QEA Demo ──────────────────────────────────────────────────────────────────
let qeaChart = null;

function initQEADemo() {
  const runBtn = document.getElementById('qea-run');
  const psEl   = document.getElementById('qea-popsize');
  const itEl   = document.getElementById('qea-maxiter');
  const dtEl   = document.getElementById('qea-delta');

  function updateLabels() {
    const psLbl = document.getElementById('qea-popsize-val');
    const itLbl = document.getElementById('qea-maxiter-val');
    const dtLbl = document.getElementById('qea-delta-val');
    if (psLbl && psEl) psLbl.textContent = psEl.value;
    if (itLbl && itEl) itLbl.textContent = itEl.value;
    if (dtLbl && dtEl) dtLbl.textContent = (parseFloat(dtEl.value) / Math.PI).toFixed(2) + 'π';
  }

  [psEl, itEl, dtEl].forEach(el => { if (el) el.addEventListener('input', updateLabels); });
  updateLabels();

  if (runBtn) runBtn.addEventListener('click', () => {
    const params = {
      popSize: parseInt(psEl?.value || 10),
      maxIter: parseInt(itEl?.value || 40),
      convergenceLimit: 10,
      deltaTheta: parseFloat(dtEl?.value || (0.05 * Math.PI)),
      seed: Date.now() & 0xffff
    };
    const result = runQEA(params);
    renderQEAResult(result);
  });

  // Run on first load
  renderQEAResult(runQEA({ popSize: 10, maxIter: 40, convergenceLimit: 10, deltaTheta: 0.05 * Math.PI, seed: 42 }));
}

function renderQEAResult(result) {
  // Qubit bars
  const barsEl = document.getElementById('qea-qubit-bars');
  if (barsEl) {
    barsEl.innerHTML = result.qubits.map((q, i) => {
      const a2pct = (q.alpha2 * 100).toFixed(0);
      const b2pct = (q.beta2  * 100).toFixed(0);
      return `<div class="qubit-bar">
        <div class="qubit-label">Q${i}</div>
        <div class="qubit-vis">
          <div class="qubit-alpha" style="height:${a2pct}%"></div>
          <div class="qubit-beta"  style="height:${b2pct}%"></div>
        </div>
        <div class="qubit-theta">θ=${(q.theta/Math.PI).toFixed(2)}π</div>
        <div class="qubit-label">α²=${a2pct}%</div>
      </div>`;
    }).join('');
  }

  // Fitness chart
  const canvas = document.getElementById('qea-chart');
  if (!canvas) return;
  if (qeaChart) qeaChart.destroy();

  const iters = result.bestHistory.map((_, i) => i + 1);

  qeaChart = new Chart(canvas, {
    type: 'line',
    data: {
      labels: iters,
      datasets: [
        { label: 'Best Fitness', data: result.bestHistory, borderColor: '#3b82f6', tension: 0.3, fill: false, borderWidth: 2 },
        { label: 'Avg Fitness',  data: result.avgHistory,  borderColor: '#94a3b8', tension: 0.3, fill: false, borderWidth: 1.5, borderDash: [4,3] }
      ]
    },
    options: {
      responsive: true, maintainAspectRatio: false, animation: { duration: 300 },
      plugins: { legend: { position: 'top' } },
      scales: {
        x: { title: { display: true, text: 'Generation' } },
        y: { title: { display: true, text: 'Fitness (lower=better)' }, beginAtZero: true }
      }
    }
  });

  // Info text
  const infoEl = document.getElementById('qea-info');
  if (infoEl) {
    infoEl.innerHTML = `Best fitness: <strong>${result.best}</strong> after <strong>${result.bestHistory.length}</strong> generations. Best order: [${result.bestOrder.join(',')}] (optimal for toy: [0,2,1,3])`;
  }
}

// ── Results Dashboard ─────────────────────────────────────────────────────────
let resultsData = [];
let resultsSizes = [];

async function initResults(config) {
  const { data, sample } = await loadData();
  resultsData = data;
  resultsSizes = (config.benchmarkSizes || []).map(s => s.n);
  if (!resultsSizes.length) resultsSizes = [...new Set(data.map(r => r.processCount))].sort((a,b) => a-b);

  // Show banner
  const banner = document.getElementById('data-banner');
  if (banner) {
    if (sample) {
      banner.innerHTML = '⚠️ <strong>Using sample data</strong> — run <code>java PerformanceAnalyzer</code> then serve via <code>python -m http.server</code> to see real results.';
      banner.className = 'data-banner sample';
    } else {
      banner.innerHTML = `✓ <strong>Live benchmark data</strong> — ${data.length} runs loaded from <code>output/results.json</code>.`;
      banner.className = 'data-banner live';
    }
    banner.style.display = 'flex';
  }

  renderResults();

  // Filters
  const algoSel = document.getElementById('filter-algo');
  const sizeSel = document.getElementById('filter-size');
  const dlBtn   = document.getElementById('btn-download-csv');

  if (algoSel) algoSel.addEventListener('change', renderResults);
  if (sizeSel) sizeSel.addEventListener('change', renderResults);
  if (dlBtn)   dlBtn.addEventListener('click', () => downloadCSV(resultsData));
}

function renderResults() {
  const algoFilter = document.getElementById('filter-algo')?.value || 'all';
  const sizeFilter = document.getElementById('filter-size')?.value || 'all';
  const filtered   = getFilteredData(resultsData, algoFilter, sizeFilter);
  const sizes      = sizeFilter === 'all' ? resultsSizes : [+sizeFilter];

  buildAllCharts(filtered, sizes);
  buildSummaryTable(filtered, sizes);
  buildFairness(filtered, resultsSizes);
}

// ── Nav Spy ───────────────────────────────────────────────────────────────────
function initNavSpy() {
  const sections = document.querySelectorAll('section[id]');
  const links    = document.querySelectorAll('.nav-links a');

  const obs = new IntersectionObserver(entries => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        links.forEach(l => {
          l.classList.toggle('active', l.getAttribute('href') === `#${entry.target.id}`);
        });
      }
    });
  }, { rootMargin: '-40% 0px -55% 0px' });

  sections.forEach(s => obs.observe(s));
}
