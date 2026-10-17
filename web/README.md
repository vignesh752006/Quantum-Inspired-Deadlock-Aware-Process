# QEA Scheduling — Phase 1 Website

Static website for the OSDS course project benchmark. Single-page, no build step.

## Prerequisites

- A modern web browser (Chrome, Firefox, Edge, Safari)
- Python 3 (for the local server, recommended)
- Java 17+ (to regenerate benchmark data)

## Quick Start

### Option A — HTTP server (recommended, loads real results.json)

```bash
# From the project root (d:\osds_cp_project\)
python -m http.server 8080

# Then open:
# http://localhost:8080/web/index.html
```

### Option B — Open directly (file://)

Open `web/index.html` directly in your browser.

> ⚠️ Under `file://`, the `fetch('../output/results.json')` call will be blocked by CORS in most browsers. The site will automatically fall back to `web/data/sample-results.json` and show a yellow **"Using sample data"** banner.

## Regenerating Benchmark Data

```bash
# From d:\osds_cp_project\
javac -d . src\*.java
java PerformanceAnalyzer
# → writes output\results.csv and output\results.json (390 rows)
```

Then serve via HTTP server to see live data.

## Running JS Tests

Open `web/tests.html` in a browser (served via HTTP or directly via file://).
All 28 assertions run automatically and display pass/fail.

## File Structure

```
web/
├── index.html              # Main site (single page, 8 sections)
├── tests.html              # JS test runner (28 assertions)
├── README.md               # This file
├── css/
│   └── style.css           # All styles, light/dark theme
├── js/
│   ├── app.js              # Main app entry point (ES module)
│   ├── banker.js           # Banker's Algorithm (correct JS implementation)
│   ├── rag.js              # RAG builder + DFS cycle detector + canvas renderer
│   ├── qea.js              # QEA toy visualizer
│   └── results.js          # Chart.js dashboard, data loading, CSV export
└── data/
    ├── config.json         # Algorithm parameters, benchmark sizes (edit without touching code)
    ├── content.json        # All text content (edit wording here)
    └── sample-results.json # Fallback data (subset of real results, labelled as sample)
```

## Editing Content

All text (descriptions, disclaimers, parameter rationale) lives in `web/data/content.json`.
Edit it to update wording without touching any HTML or JS.

Parameter status (`"approved"` / `"pending team approval"`) and values are in `web/data/config.json`.

## Light/Dark Mode

Click the 🌙/☀️ button in the top-right corner. Preference is saved to `localStorage`.

## Sections

1. **Overview** — Hero, approach cards (Banker / QEA / Hybrid)
2. **Scope** — What we are NOT claiming (5 explicit disclaimers)
3. **Concepts** — Coffman conditions, Banker data structures, comparison table, fitness formula
4. **Architecture** — Phase 1 pipeline, 16-file Java class table
5. **Demos** — RAG+cycle detector, Banker step-through, QEA visualizer (all pure JS)
6. **Results** — Live/sample charts, summary table, fairness panel, CSV download
7. **Exp. Design** — Fairness principles, benchmark sizes table
8. **Parameters** — Configurable parameter table with status badges

## Phase 2 Note

Phase 2 (VMware multi-VM validation) is out of scope for this site.
