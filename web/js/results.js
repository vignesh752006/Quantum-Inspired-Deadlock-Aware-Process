/**
 * results.js — Data loading and Chart.js rendering for the Results section.
 */

import { Chart, registerables } from 'https://cdn.jsdelivr.net/npm/chart.js@4.4.3/+esm';
Chart.register(...registerables);

// ── Color scheme ─────────────────────────────────────────────────────────────
const ALGO_COLOR = {
  Banker: { border: '#10b981', bg: 'rgba(16,185,129,0.18)' },
  QEA:    { border: '#ef4444', bg: 'rgba(239,68,68,0.18)'  },
  Hybrid: { border: '#3b82f6', bg: 'rgba(59,130,246,0.18)' },
};
const ALGOS   = ['Banker','QEA','Hybrid'];
const SIZES   = [10, 20, 50, 100, 200, 500];

// ── State ────────────────────────────────────────────────────────────────────
let allData   = [];
let charts    = {};
let isSample  = false;

// ── Load Data ─────────────────────────────────────────────────────────────────
export async function loadData() {
  // Try real results first (works when served via http server)
  try {
    const res = await fetch('../output/results.json');
    if (!res.ok) throw new Error('not found');
    allData  = await res.json();
    isSample = false;
    return { data: allData, sample: false };
  } catch (_) {}

  // Fallback to bundled sample
  try {
    const res = await fetch('./data/sample-results.json');
    allData  = await res.json();
    isSample = true;
    return { data: allData, sample: true };
  } catch (e) {
    console.error('Could not load any results data', e);
    return { data: [], sample: true };
  }
}

// ── Aggregation helpers ───────────────────────────────────────────────────────
function mean(arr) {
  if (!arr.length) return 0;
  return arr.reduce((a,b) => a+b, 0) / arr.length;
}
function stdDev(arr) {
  if (arr.length <= 1) return 0;
  const m = mean(arr);
  return Math.sqrt(arr.reduce((s,v) => s + (v-m)**2, 0) / (arr.length-1));
}
function minVal(arr) { return arr.length ? Math.min(...arr) : 0; }
function maxVal(arr) { return arr.length ? Math.max(...arr) : 0; }

function aggregate(data, algo, size, field) {
  const vals = data
    .filter(r => r.algorithm === algo && r.processCount === size)
    .map(r => r[field])
    .filter(v => v !== undefined && v < 1e200);
  return { mean: mean(vals), std: stdDev(vals), min: minVal(vals), max: maxVal(vals), n: vals.length };
}

export function getFilteredData(data, algoFilter, sizeFilter) {
  return data.filter(r =>
    (algoFilter === 'all' || r.algorithm === algoFilter) &&
    (sizeFilter === 'all' || r.processCount === +sizeFilter)
  );
}

// ── Chart builders ────────────────────────────────────────────────────────────

function makeDatasets(data, field, sizes) {
  const activeAlgos = ALGOS.filter(algo => data.some(r => r.algorithm === algo));
  return activeAlgos.map(algo => {
    const vals = sizes.map(s => aggregate(data, algo, s, field).mean);
    const errs = sizes.map(s => aggregate(data, algo, s, field).std);
    return {
      label: algo,
      data: vals,
      borderColor: ALGO_COLOR[algo].border,
      backgroundColor: ALGO_COLOR[algo].bg,
      borderWidth: 2,
      pointRadius: 5,
      tension: 0.3,
      errorBars: errs,  // stored for custom plugin
    };
  });
}

const errorBarPlugin = {
  id: 'errorBars',
  afterDraw(chart) {
    const { ctx } = chart;
    chart.data.datasets.forEach((ds, dIdx) => {
      if (!ds.errorBars) return;
      const meta = chart.getDatasetMeta(dIdx);
      meta.data.forEach((pt, pIdx) => {
        const err = ds.errorBars[pIdx];
        if (!err || err === 0) return;
        const yScale = chart.scales.y;
        const yMean  = pt.y;
        const yUp    = yScale.getPixelForValue(yScale.getValueForPixel(yMean) + err);
        const yDown  = yScale.getPixelForValue(yScale.getValueForPixel(yMean) - err);
        ctx.save();
        ctx.strokeStyle = ds.borderColor;
        ctx.lineWidth = 1.5;
        ctx.beginPath();
        ctx.moveTo(pt.x, yUp); ctx.lineTo(pt.x, yDown);
        ctx.moveTo(pt.x - 5, yUp); ctx.lineTo(pt.x + 5, yUp);
        ctx.moveTo(pt.x - 5, yDown); ctx.lineTo(pt.x + 5, yDown);
        ctx.stroke();
        ctx.restore();
      });
    });
  }
};
Chart.register(errorBarPlugin);

function chartDefaults(title, yLabel, data, field, sizes, logY = false) {
  return {
    type: 'line',
    data: { labels: sizes.map(String), datasets: makeDatasets(data, field, sizes) },
    options: {
      responsive: true, maintainAspectRatio: false, animation: { duration: 400 },
      plugins: { legend: { position: 'top' }, title: { display: false } },
      scales: {
        x: { title: { display: true, text: 'Number of Processes' } },
        y: {
          type: logY ? 'logarithmic' : 'linear',
          title: { display: true, text: yLabel },
          beginAtZero: !logY,
        }
      }
    }
  };
}

export function buildAllCharts(data, sizes) {
  const effectiveSizes = sizes.filter(s => data.some(r => r.processCount === s));

  function make(id, field, yLabel, logY = false) {
    const el = document.getElementById(id);
    if (!el) return;
    if (charts[id]) charts[id].destroy();
    charts[id] = new Chart(el, chartDefaults(id, yLabel, data, field, effectiveSizes, logY));
  }

  make('chart-time',       'executionTimeMs', 'Execution Time (ms)', true);
  make('chart-fitness',    'fitnessValue',    'Fitness (lower=better)');
  make('chart-completed',  'processesCompleted', 'Processes Completed');
  make('chart-waste',      'resourceWaste',   'Resource Waste');
  make('chart-scalability','executionTimeMs', 'Execution Time (ms)', true);
}

// ── Summary table ─────────────────────────────────────────────────────────────
export function buildSummaryTable(data, sizes) {
  const tbody = document.getElementById('summary-tbody');
  if (!tbody) return;
  tbody.innerHTML = '';

  const effectiveSizes = sizes.filter(s => data.some(r => r.processCount === s));
  const activeAlgos    = ALGOS.filter(algo => data.some(r => r.algorithm === algo));

  effectiveSizes.forEach(s => {
    activeAlgos.forEach(algo => {
      const t  = aggregate(data, algo, s, 'executionTimeMs');
      const f  = aggregate(data, algo, s, 'fitnessValue');
      const it = aggregate(data, algo, s, 'iterations');
      const safeCount = data.filter(r => r.algorithm === algo && r.processCount === s && r.isSafe).length;
      const total     = data.filter(r => r.algorithm === algo && r.processCount === s).length;

      const tr = document.createElement('tr');
      tr.innerHTML = `
        <td><span class="algo-pill ${algo}">${algo}</span></td>
        <td>${s}</td>
        <td class="mono">${t.mean.toFixed(2)}</td>
        <td class="mono">±${t.std.toFixed(2)}</td>
        <td class="mono">${t.min.toFixed(2)}</td>
        <td class="mono">${t.max.toFixed(2)}</td>
        <td class="mono">${f.mean.toFixed(1)}</td>
        <td class="mono">±${f.std.toFixed(1)}</td>
        <td>${safeCount}/${total}</td>
        <td class="mono">${it.mean.toFixed(1)}</td>
      `;
      tbody.appendChild(tr);
    });
  });
}

// ── Fairness panel ────────────────────────────────────────────────────────────
export function buildFairness(data, sizes) {
  const el = document.getElementById('fairness-items');
  if (!el) return;
  const items = [];
  const effectiveSizes = sizes.filter(s => data.some(r => r.processCount === s));

  effectiveSizes.forEach(s => {
    const bTime = aggregate(data, 'Banker', s, 'executionTimeMs').mean;
    const qTime = aggregate(data, 'QEA',    s, 'executionTimeMs').mean;
    const hTime = aggregate(data, 'Hybrid', s, 'executionTimeMs').mean;
    const bFit  = aggregate(data, 'Banker', s, 'fitnessValue').mean;
    const qFit  = aggregate(data, 'QEA',    s, 'fitnessValue').mean;

    if (bTime > 0 && qTime > 0) {
      const ratio = (qTime / bTime).toFixed(1);
      items.push(`n=${s}: Banker is <strong>${ratio}×</strong> faster than QEA (${bTime.toFixed(2)} ms vs ${qTime.toFixed(2)} ms). Banker is always faster.`);
    }
    if (qFit >= bFit && bFit > 0 && qFit > 0) {
      const pct = (((qFit - bFit) / bFit) * 100).toFixed(1);
      items.push(`n=${s}: QEA fitness ${qFit.toFixed(1)} is ${pct}% worse than Banker ${bFit.toFixed(1)}. QEA brought no improvement on this state type.`);
    }
  });

  if (items.length === 0) {
    items.push('No fairness flags triggered — check that data covers multiple sizes.');
  }

  el.innerHTML = items.map(t => `<div class="fairness-item">⚠️ ${t}</div>`).join('');
}

// ── Download CSV ──────────────────────────────────────────────────────────────
export function downloadCSV(data) {
  const header = 'algorithm,processCount,resourceCount,repetition,stateSeed,stateType,executionTimeNs,executionTimeMs,fitnessValue,processesCompleted,waitingTime,resourceWaste,isSafe,iterations';
  const rows = data.map(r =>
    [r.algorithm,r.processCount,r.resourceCount,r.repetition,r.stateSeed,r.stateType,
     r.executionTimeNs,r.executionTimeMs?.toFixed(4),r.fitnessValue?.toFixed(4),
     r.processesCompleted,r.waitingTime?.toFixed(4),r.resourceWaste?.toFixed(4),
     r.isSafe,r.iterations].join(',')
  );
  const blob = new Blob([[header,...rows].join('\n')], { type: 'text/csv' });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = isSample ? 'sample-results.csv' : 'results.csv';
  a.click();
}
