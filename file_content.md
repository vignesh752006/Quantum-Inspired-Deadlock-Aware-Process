# Project File Content Index

This document provides a single-line summary of what every file in the repository handles.

---

## 1. Root Directory

- `project-description.md` — Comprehensive architectural documentation detailing the project concepts, algorithms, pipeline flow, and empirical findings.
- `file_content.md` — Complete reference index defining the exact single-line responsibility of each file across the codebase.
- `requirements.txt` — Python dependencies specification (pandas, matplotlib, numpy) and execution prerequisites for analysis scripts.
- `Quantum_Inspired_Deadlock_Project_Group_Guide.docx` — Official university course project rubric, assignment instructions, and phase deliverable guidelines.

---

## 2. Core Java Simulation Source Code (`src/`)

- `src/BankersAlgorithm.java` — Implements Dijkstra's Banker's Algorithm to evaluate multi-instance resource safety and output a deadlock-free execution order.
- `src/Benchmark.java` — Orchestrates the automated benchmark suite comparing Banker, QEA, and Hybrid algorithms across 6 scaling problem configurations with multiple seed repetitions.
- `src/CandidateSolution.java` — Models an individual scheduling candidate (permutation of process IDs) with its calculated fitness and safety metrics.
- `src/CycleDetector.java` — Performs depth-first search (DFS) traversal on the Resource Allocation Graph to detect circular wait dependency cycles.
- `src/FitnessFunction.java` — Evaluates candidate process execution orders by calculating cumulative waiting time, idle resource waste, and deadlock penalty scores.
- `src/Main.java` — Command-line console runner demonstrating textbook Silberschatz state checks, test generation, RAG graphs, Banker's traces, and QEA scheduling.
- `src/PerformanceAnalyzer.java` — Formats and exports collected benchmark measurements into `output/results.csv` and `output/results.json`.
- `src/Process.java` — Encapsulates an operating system process containing ID, label, allocated resource counts, and maximum resource demand vectors.
- `src/QuantumBit.java` — Implements a quantum-inspired bit (Q-bit) defined by probability amplitudes $(\alpha, \beta)$ and rotation gate transformations.
- `src/QuantumInspiredOptimizer.java` — Implements the Quantum-Inspired Evolutionary Algorithm (QIEA) search loop with quantum rotation gate updates and optional Banker's safety gating.
- `src/QuantumPopulation.java` — Manages a collection of Q-bit individuals and handles probabilistic observation/collapse into valid classical process sequence permutations.
- `src/Resource.java` — Represents a system resource type tracking total existing instances and currently unallocated available units.
- `src/ResourceAllocationGraph.java` — Constructs a directed bipartite graph connecting Process nodes and Resource nodes via assignment and request edges.
- `src/Result.java` — Data structure storing all outcome metrics (algorithm name, problem size, seed, runtime, fitness, completed count, and safety status) for a single run.
- `src/SystemState.java` — Maintains a point-in-time snapshot of the system including allocation, max demand, need matrices, available vector, and capacity utilization.
- `src/TestCaseGenerator.java` — Produces deterministic, reproducible benchmark test cases (guaranteed safe, guaranteed deadlocked, or randomized) across 6 standardized problem sizes.

---

## 3. Java Unit & Integration Tests (`tests/`)

- `tests/Step1SmokeTest.java` — Verifies domain object initialization, matrix bounds checking, need calculation, and deep copy isolation for core state classes.
- `tests/Step2Test.java` — Validates the test case generator to confirm reproducible safe state construction, deadlock injection, and matrix invariants across seeds.
- `tests/Step3Test.java` — Verifies Resource Allocation Graph construction, edge direction correctness, and bipartite graph validation for diverse problem sizes.
- `tests/Step4Test.java` — Validates DFS cycle detection and proves that while RAG cycles mean deadlock for single-instance resources, multi-instance systems require Banker's algorithm.
- `tests/Step5Test.java` — Tests Q-bit probability constraints, rotation gate amplitude clamping, population observation validity, and standalone QEA convergence.
- `tests/Step6Test.java` — Tests Banker + QEA Hybrid integration, verifying that unsafe states receive maximum penalties and that the hybrid never returns an unsafe schedule.
- `tests/Step7Test.java` — Executes the full 390-run benchmark suite to verify reproducible execution, fairness invariants, and complete CSV serialization.

---

## 4. Python Analysis & Charting (`analysis/`)

- `analysis/plot_results.py` — Reads `output/results.csv` using Pandas and generates 6 publication-grade comparison charts saved into `analysis/charts/`.
- `analysis/dashboard.html` — Standalone offline web dashboard providing filterable tables, statistical metrics, and visual overviews of benchmark data.
- `analysis/charts/1_execution_time.png` — Plot comparing log-scale execution time and standard deviation across problem sizes for all three algorithms.
- `analysis/charts/2_fitness_value.png` — Plot comparing schedule quality (fitness value) showing optimization improvements achieved by QEA and Hybrid over Banker.
- `analysis/charts/3_completion_rate.png` — Bar chart showing 100% process completion rates across all evaluated problem configurations on safe states.
- `analysis/charts/4_scalability_comparison.png` — Side-by-side grouped bar chart illustrating algorithmic scalability as process count increases from 10 to 500.
- `analysis/charts/5_iterations.png` — Line plot tracking the average number of generations needed by QEA and Hybrid before reaching convergence or stagnation.
- `analysis/charts/6_speed_ratio.png` — Plot depicting the empirical slowdown factor ($4\times$ to $120\times$) of QEA and Hybrid relative to the Banker baseline.

---

## 5. Output Datasets (`output/`)

- `output/results.csv` — Comma-separated dataset recording all 390 benchmark trial runs with 13 metric columns per execution.
- `output/results.json` — JSON-formatted benchmark dataset used directly by the interactive web dashboard for in-browser rendering.

---

## 6. Web Visualization Dashboard (`web/`)

- `web/index.html` — Main single-page web application featuring project introduction, interactive algorithm simulators, and live benchmark dashboards.
- `web/tests.html` — Client-side test runner executing 28 browser-based unit tests for the JavaScript Banker, RAG, and QEA implementations.
- `web/README.md` — User guide detailing prerequisites, local HTTP server launch instructions, project structure, and web dashboard configuration.
- `web/css/style.css` — CSS stylesheet containing dark/light theme tokens, responsive layouts, glassmorphism cards, and interactive visual styling.
- `web/js/app.js` — Core frontend JavaScript controller orchestrating tab navigation, theme toggling, interactive demo controls, and dynamic content injection.
- `web/js/banker.js` — Client-side JavaScript implementation of Banker's Algorithm that performs safety checking and step-by-step trace generation.
- `web/js/rag.js` — Interactive Resource Allocation Graph visualizer rendering nodes, edges, and detected cycle paths on an HTML5 canvas.
- `web/js/qea.js` — Interactive QEA simulation visualizer showing live Q-bit angle rotations, population fitness sampling, and convergence curves.
- `web/js/results.js` — Data loader and Chart.js integration module generating interactive benchmark comparison graphs and statistical summary tables.
- `web/data/config.json` — Configuration file defining algorithm hyperparameters, benchmark problem sizes, and component approval statuses.
- `web/data/content.json` — Centralized JSON document storing all UI text, concept definitions, Coffman conditions, and academic disclaimers.
- `web/data/sample-results.json` — Fallback benchmark dataset enabling the web visualizer to function under the browser `file://` protocol when CORS restricts loading local files.
