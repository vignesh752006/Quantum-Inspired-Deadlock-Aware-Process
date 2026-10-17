"""
plot_results.py

Reads benchmark results from output/results.csv and generates publication-quality
comparison charts for the three scheduling algorithms:
  1. Banker's Algorithm (deterministic baseline)
  2. QEA Alone (Quantum-Inspired Evolutionary Algorithm)
  3. Banker + QEA Hybrid

Charts generated:
  1. Execution Time vs Process Count (log-scale, error bars)
  2. Fitness Value vs Process Count (error bars)
  3. Processes Completed vs Process Count
  4. Scalability Comparison (all 3 algorithms, side-by-side bar)
  5. QEA Convergence / Iterations vs Process Count
  6. Speed Ratio: QEA/Hybrid relative to Banker

Usage:
  python analysis/plot_results.py

Output:
  analysis/charts/ directory with PNG files

@author  OSDS-CP Group
@version 1.0  (Phase 1)
"""

import os
import sys
import pandas as pd
import matplotlib.pyplot as plt
import matplotlib.ticker as ticker
import numpy as np

# ── Paths ──────────────────────────────────────────────────────────────────────
SCRIPT_DIR  = os.path.dirname(os.path.abspath(__file__))
PROJECT_DIR = os.path.dirname(SCRIPT_DIR)
CSV_PATH    = os.path.join(PROJECT_DIR, "output", "results.csv")
CHART_DIR   = os.path.join(SCRIPT_DIR, "charts")

# ── Style ──────────────────────────────────────────────────────────────────────
COLORS = {
    "Banker": "#2ecc71",   # green
    "QEA":    "#e74c3c",   # red
    "Hybrid": "#3498db",   # blue
}
MARKERS = {"Banker": "s", "QEA": "o", "Hybrid": "D"}
ALGO_ORDER = ["Banker", "QEA", "Hybrid"]

plt.rcParams.update({
    "figure.figsize": (10, 6),
    "figure.dpi": 150,
    "font.size": 11,
    "font.family": "sans-serif",
    "axes.grid": True,
    "grid.alpha": 0.3,
    "axes.spines.top": False,
    "axes.spines.right": False,
})


def load_data():
    """Load and validate benchmark CSV data."""
    if not os.path.exists(CSV_PATH):
        print(f"ERROR: CSV file not found at {CSV_PATH}")
        print("Run 'java PerformanceAnalyzer' first to generate benchmark data.")
        sys.exit(1)

    df = pd.read_csv(CSV_PATH)
    df["executionTimeMs"] = df["executionTimeNs"] / 1_000_000.0
    print(f"Loaded {len(df)} results from {CSV_PATH}")
    print(f"Algorithms: {df['algorithm'].unique()}")
    print(f"Sizes: {sorted(df['processCount'].unique())}")
    return df


def save_chart(fig, name):
    """Save figure to charts directory."""
    os.makedirs(CHART_DIR, exist_ok=True)
    path = os.path.join(CHART_DIR, f"{name}.png")
    fig.savefig(path, bbox_inches="tight", facecolor="white")
    plt.close(fig)
    print(f"  Saved: {path}")


# ── Chart 1: Execution Time vs Process Count ──────────────────────────────────

def plot_execution_time(df):
    """Execution time (log scale) with error bars for each algorithm."""
    fig, ax = plt.subplots()

    for algo in ALGO_ORDER:
        subset = df[df["algorithm"] == algo]
        grouped = subset.groupby("processCount")["executionTimeMs"]
        means = grouped.mean()
        stds  = grouped.std().fillna(0)

        ax.errorbar(means.index, means.values, yerr=stds.values,
                    label=algo, color=COLORS[algo], marker=MARKERS[algo],
                    capsize=4, linewidth=2, markersize=7)

    ax.set_yscale("log")
    ax.set_xlabel("Number of Processes")
    ax.set_ylabel("Execution Time (ms) — log scale")
    ax.set_title("Execution Time vs Problem Size")
    ax.legend(framealpha=0.9)
    ax.set_xticks(sorted(df["processCount"].unique()))
    save_chart(fig, "1_execution_time")


# ── Chart 2: Fitness Value vs Process Count ───────────────────────────────────

def plot_fitness(df):
    """Fitness value comparison with error bars."""
    fig, ax = plt.subplots()

    for algo in ALGO_ORDER:
        subset = df[df["algorithm"] == algo]
        grouped = subset.groupby("processCount")["fitnessValue"]
        means = grouped.mean()
        stds  = grouped.std().fillna(0)

        ax.errorbar(means.index, means.values, yerr=stds.values,
                    label=algo, color=COLORS[algo], marker=MARKERS[algo],
                    capsize=4, linewidth=2, markersize=7)

    ax.set_xlabel("Number of Processes")
    ax.set_ylabel("Fitness Value (lower = better)")
    ax.set_title("Solution Quality (Fitness) vs Problem Size")
    ax.legend(framealpha=0.9)
    ax.set_xticks(sorted(df["processCount"].unique()))
    save_chart(fig, "2_fitness_value")


# ── Chart 3: Processes Completed vs Process Count ─────────────────────────────

def plot_completion(df):
    """Completion rate — should be 100% for safe states."""
    fig, ax = plt.subplots()

    sizes = sorted(df["processCount"].unique())
    x = np.arange(len(sizes))
    width = 0.25

    for i, algo in enumerate(ALGO_ORDER):
        subset = df[df["algorithm"] == algo]
        grouped = subset.groupby("processCount")["processesCompleted"]
        means = grouped.mean()
        rates = [means.get(s, 0) / s * 100 for s in sizes]

        ax.bar(x + i * width, rates, width, label=algo, color=COLORS[algo],
               edgecolor="white", linewidth=0.5)

    ax.set_xlabel("Number of Processes")
    ax.set_ylabel("Completion Rate (%)")
    ax.set_title("Process Completion Rate vs Problem Size")
    ax.set_xticks(x + width)
    ax.set_xticklabels(sizes)
    ax.set_ylim(0, 110)
    ax.legend(framealpha=0.9)
    ax.axhline(y=100, color="gray", linestyle="--", alpha=0.5)
    save_chart(fig, "3_completion_rate")


# ── Chart 4: Scalability Comparison (Side-by-Side Bar) ────────────────────────

def plot_scalability(df):
    """Side-by-side bar chart comparing execution time across algorithms."""
    fig, ax = plt.subplots()

    sizes = sorted(df["processCount"].unique())
    x = np.arange(len(sizes))
    width = 0.25

    for i, algo in enumerate(ALGO_ORDER):
        subset = df[df["algorithm"] == algo]
        grouped = subset.groupby("processCount")["executionTimeMs"]
        means = grouped.mean()
        stds  = grouped.std().fillna(0)
        vals = [means.get(s, 0) for s in sizes]
        errs = [stds.get(s, 0) for s in sizes]

        ax.bar(x + i * width, vals, width, yerr=errs, label=algo,
               color=COLORS[algo], edgecolor="white", linewidth=0.5,
               capsize=3)

    ax.set_xlabel("Number of Processes")
    ax.set_ylabel("Execution Time (ms)")
    ax.set_title("Scalability Comparison — All Three Algorithms")
    ax.set_xticks(x + width)
    ax.set_xticklabels(sizes)
    ax.legend(framealpha=0.9)
    save_chart(fig, "4_scalability_comparison")


# ── Chart 5: QEA Iterations vs Process Count ─────────────────────────────────

def plot_iterations(df):
    """Iterations used by QEA and Hybrid across problem sizes."""
    fig, ax = plt.subplots()

    for algo in ["QEA", "Hybrid"]:
        subset = df[df["algorithm"] == algo]
        grouped = subset.groupby("processCount")["iterations"]
        means = grouped.mean()
        stds  = grouped.std().fillna(0)

        ax.errorbar(means.index, means.values, yerr=stds.values,
                    label=algo, color=COLORS[algo], marker=MARKERS[algo],
                    capsize=4, linewidth=2, markersize=7)

    ax.axhline(y=100, color="gray", linestyle="--", alpha=0.5, label="Max iterations")
    ax.set_xlabel("Number of Processes")
    ax.set_ylabel("Iterations Run")
    ax.set_title("QEA Convergence: Iterations vs Problem Size")
    ax.legend(framealpha=0.9)
    ax.set_xticks(sorted(df["processCount"].unique()))
    save_chart(fig, "5_iterations")


# ── Chart 6: Speed Ratio (QEA/Hybrid vs Banker) ──────────────────────────────

def plot_speed_ratio(df):
    """How many times slower QEA/Hybrid are compared to Banker."""
    fig, ax = plt.subplots()

    sizes = sorted(df["processCount"].unique())
    banker_means = df[df["algorithm"] == "Banker"].groupby("processCount")["executionTimeMs"].mean()

    for algo in ["QEA", "Hybrid"]:
        subset = df[df["algorithm"] == algo]
        algo_means = subset.groupby("processCount")["executionTimeMs"].mean()
        ratios = [algo_means.get(s, 0) / banker_means.get(s, 1) for s in sizes]

        ax.plot(sizes, ratios, label=f"{algo} / Banker", color=COLORS[algo],
                marker=MARKERS[algo], linewidth=2, markersize=7)

    ax.set_xlabel("Number of Processes")
    ax.set_ylabel("Speed Ratio (× slower than Banker)")
    ax.set_title("Relative Speed: QEA and Hybrid vs Banker Baseline")
    ax.legend(framealpha=0.9)
    ax.set_xticks(sizes)
    ax.axhline(y=1.0, color="gray", linestyle="--", alpha=0.5, label="Banker baseline")
    save_chart(fig, "6_speed_ratio")


# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    print("=" * 60)
    print("  OSDS Phase 1 — Benchmark Analysis & Chart Generation")
    print("=" * 60)

    df = load_data()
    print(f"\nGenerating charts in {CHART_DIR}/...\n")

    plot_execution_time(df)
    plot_fitness(df)
    plot_completion(df)
    plot_scalability(df)
    plot_iterations(df)
    plot_speed_ratio(df)

    print(f"\nAll 6 charts saved to {CHART_DIR}/")
    print("Done!")


if __name__ == "__main__":
    main()
