# Quantum-Inspired Deadlock-Aware Process Scheduling

A controlled comparison of three approaches to deadlock-aware process scheduling:

| Approach | Safety | Optimises schedule quality? |
|---|---|---|
| **Banker's Algorithm** (Dijkstra) | Guaranteed | No — returns the first safe sequence it finds |
| **Quantum-Inspired Evolutionary Algorithm (QEA)** | Soft penalty | Yes — but may produce unsafe orders |
| **Banker + QEA Hybrid** | Guaranteed (hard constraint) | Yes — search confined to the safe region |

The question this project asks is narrow and answerable: *does borrowing the Q-bit
formalism buy anything over a classical safety algorithm, and at what cost?*

---

## What this is — and what it is not

This runs on ordinary JVM hardware with a classical random number generator. It uses
the **mathematical formalism** of quantum computing — qubits held as angles, probability
amplitudes, rotation gates — because that formalism gives a compact way to balance
exploration against convergence in an evolutionary search.

**There is no quantum computer involved, no quantum circuit is ever executed, and no
quantum speedup is claimed or possible here.** Any sentence in a write-up that implies
otherwise would be wrong. See [Limitations](#limitations).

---

## The problem

A system with $$n$$ processes and $$m$$ resource types must schedule execution so that:

1. **Safety** — no circular wait can form, so no subset of processes is permanently blocked
   (the four Coffman conditions).
2. **Efficiency** — waiting time, turnaround and idle resources stay low.

These pull against each other:

- **Banker's Algorithm** is safe by construction. It runs in $$O(n^2 m)$$ and is very fast.
  But it stops at the *first* safe sequence, not a good one. It has no notion of "which
  safe sequence wastes least".
- **A pure optimiser** searches orderings freely and can find much better schedules. But
  it can also return an ordering that deadlocks, because nothing in an unconstrained
  fitness landscape forbids it.

The hybrid resolves this by making Banker's safety test a **filter inside the fitness
function**: unsafe candidates receive a penalty so large they can never be selected.

---

## Quick start

Requires **JDK 17+** and **Maven 3.8+**.

```bash
# Build and run the test suite
mvn clean verify

# Single-state walkthrough (Banker, cycle detection, QEA, hybrid)
mvn -q exec:java

# Full benchmark sweep, writes output/results.csv and output/results.json
mvn -q exec:java -Dexec.args="benchmark"

# Or build a runnable jar
mvn clean package
java -jar target/quantum-deadlock.jar demo
```

Reproduce the charts and web dashboard:

```bash
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python analysis/plot_results.py

# Static dashboard, served over HTTP (file:// will fail the fetch due to CORS)
python3 -m http.server 8080
# then open http://localhost:8080/web/index.html
```

---

## Usage

```
java -jar quantum-deadlock.jar [command] [options]

  demo                Single-state walkthrough (default)
  benchmark [dir]     Full sweep; writes results.csv/results.json to [dir]
                      (default: output)
  help                Show usage
```

Exit codes: `0` success, `1` I/O failure, `2` bad usage.

---

## How it works

### 1. State generation

`TestCaseGenerator` produces reproducible states from an explicit seed, in three modes:

- `generateSafe(n, m, seed)` — safe by construction. It walks a random safe completion
  order; each process's need is constrained to resources already freed by its
  predecessors, so the walk is always feasible.
- `generateUnsafe(n, m, seed)` — builds a deliberate circular-wait ring of size
  $$k = \min(n, m, 10)$$: ring process $$i$$ holds every instance of resource $$i$$ and
  waits for resource $$(i+1) \bmod k$$, with nothing free in the ring. The Banker's
  Algorithm will always report unsafe.
- `generateRandom(n, m, seed)` — unconstrained; the verdict is whatever the draw produces.

Standard benchmark sizes: $$(10,5), (20,5), (50,10), (100,15), (200,20), (500,30)$$.

### 2. Banker's Algorithm

```
Work = Available;  Finish[i] = false for all i
repeat:
    find i with Finish[i] = false and Need[i] <= Work  (element-wise)
    if none: stop
    Work += Allocation[i];  Finish[i] = true
Safe iff every Finish[i] is true
```

Implemented as `BankersAlgorithm.isSafe(state)` — the fast boolean form used inside the
hybrid's inner loop — and `checkSafety(state)`, which additionally returns the sequence,
timing and a replayable step trace.

### 3. Quantum-Inspired Evolutionary Algorithm

Each individual is an array of $$n$$ qubits, one per process. A qubit stores an angle
$$\theta \in [0, \pi/2]$$:

$$
\alpha = \cos\theta, \quad \beta = \sin\theta, \quad |\alpha|^2 + |\beta|^2 = 1
$$

$$|\alpha|^2$$ is the probability of observing bit 0, $$|\beta|^2$$ the probability of
observing bit 1. Initial angle $$\pi/4$$ is the equal superposition: no bias either way.

Per generation:

1. **Observe** — collapse each qubit; a process's observed bit plus a continuous
   $$1 - |\alpha|^2$$ term plus a small jitter forms a priority key, and sorting those keys
   yields a process permutation.
2. **Evaluate** — score every candidate with the fitness function.
3. **Update** — record the global best; track stagnation.
4. **Rotate** — apply a rotation gate toward the global best's bitstring,
   pushing each qubit's angle toward 0 or $$\pi/2$$.
5. **Stop** — after `maxIterations`, or early once fitness has not improved for
   `convergenceLimit` generations.

Rotation replaces crossover and mutation entirely; there is no other variation operator.

### 4. Fitness function

$$
f = 1000 \cdot \text{deadlockPenalty} + 100 \cdot \text{unfinished} + \text{waitingTime} + \text{resourceWaste}
$$

The weights encode a lexicographic preference: never deadlock, then finish as many
processes as possible, then minimise waiting, then minimise idle resources.

In **hybrid** mode, Banker's safety test is applied first. An unsafe state — or an order
that deadlocks in simulation — receives `PENALTY_FITNESS` (`Double.MAX_VALUE / 2`, finite
on purpose, so averaging never overflows to infinity) and is excluded from selection.

### 5. Resource-allocation graph

`ResourceAllocationGraph` builds the classical bipartite RAG: allocation edges
(resource → process) where `allocation[i][j] > 0`, request edges (process → resource)
where `need[i][j] > 0`. `CycleDetector` runs an **iterative** DFS — explicit stack, not
recursion — because at $$(500, 30)$$ the graph has 530 nodes and a recursive DFS risks
`StackOverflowError` exactly at the sizes this project targets.

> A cycle in the RAG is necessary but **not sufficient** for deadlock when resource types
> hold multiple instances. Treat `CycleDetector` as a fast first-pass filter and always
> confirm with the Banker's Algorithm.

---

## Results

Every configuration is run with a fixed seed and **every repetition is recorded** —
nothing is filtered, no outlier is dropped. 390 runs in total: 5 Banker + 30 QEA +
30 hybrid, across 6 sizes.

### What the data shows

- **Banker is unbeatable on speed.** Sub-millisecond even at $$(500, 30)$$.
- **QEA finds lower-waste, tighter schedules** than the arbitrary first-safe-sequence
  Banker returns.
- **The hybrid is always safe** and still improves quality over Banker — it never returns
  an unsafe order.
- **The cost is real:** QEA runs roughly 4×–120× slower than Banker; the hybrid roughly
  5×–150× slower, since every evaluation carries an extra safety check.

The honest conclusion: **the quantum-inspired approach trades a large constant-factor
slowdown for modest schedule-quality gains, and is not competitive where safety alone is
required.** Reproduce with `mvn -q exec:java -Dexec.args="benchmark"` and compare against
`output/results.csv`.

---

## Project structure

```
.
├── pom.xml                        Maven build (Java 17)
├── README.md
├── LICENSE                        MIT
├── requirements.txt               Python deps for charting
├── .gitignore
├── .github/workflows/ci.yml       CI: build + test on JDK 17 & 21
│
├── src/main/java/io/github/vignesh752006/quantumdeadlock/
│   ├── model/                     Process, Resource, SystemState (immutable, validated)
│   ├── banker/                    BankersAlgorithm, BankersResult
│   ├── graph/                     ResourceAllocationGraph, CycleDetector
│   ├── quantum/                   QuantumBit, QuantumPopulation, CandidateSolution,
│   │                              FitnessFunction, QuantumInspiredOptimizer
│   ├── generator/                 TestCaseGenerator
│   ├── benchmark/                 Benchmark, Result, PerformanceAnalyzer
│   ├── util/                      Preconditions, Matrices, Statistics
│   └── cli/                       Main
│
├── src/test/java/.../             JUnit 5 suite (see Tests below)
│
├── analysis/
│   ├── plot_results.py            Pandas + Matplotlib chart generation
│   └── charts/                    6 PNGs
│
├── output/
│   ├── results.csv                committed baseline, 390 runs
│   └── results.json
│
└── web/                           static dashboard, no build step
    ├── index.html
    ├── tests.html
    ├── css/style.css
    ├── js/                        app.js, banker.js, rag.js, rag-sim.js, qea.js, results.js
    └── data/                      config.json, content.json, sample-results.json
```

### Design notes

- **`SystemState` is immutable** and every accessor returns a copy. Each algorithm gets
  its own `deepCopy()`, which is what makes the three-way comparison fair — no run can
  observe another's mutations.
- **Two invariants are enforced in the constructor**: `allocation[i][j] <= maximum[i][j]`
  (so `need` is never negative) and conservation,
  $$\sum_i \text{allocation}[i][j] + \text{available}[j] = \text{total}[j]$$. A state that
  breaks either makes a benchmark result meaningless rather than merely wrong, so it fails
  loudly at construction.
- **Algorithm classes never print.** Reporting returns strings
  (`BankersAlgorithm.formatReport`, `CycleDetector.formatReport`) and the benchmark writes
  to an injected `PrintStream`, so tests can assert on output and embedders can silence it.
- **`need` is always derived**, never stored independently, so it cannot go stale.

---

## Tests

The original `Step1`–`Step7` harness — seven `main` methods with manual assertions — is now
a JUnit 5 suite run by Surefire:

| Test class | Covers |
|---|---|
| `model/ProcessTest` | Construction, derived `need`, `canAllocate`, defensive copies, validation |
| `model/ResourceTest` | Capacity accounting, allocate/release bounds, utilisation |
| `model/SystemStateTest` | Shape validation, both invariants, immutability, deep copy independence |
| `banker/BankersAlgorithmTest` | Safe/unsafe verdicts, sequence completeness, trace, `isSafe` agreement |
| `graph/CycleDetectorTest` | Cycle detection on hand-built graphs, acyclic cases, large-graph non-recursion |
| `generator/TestCaseGeneratorTest` | Determinism per seed, `generateSafe` always safe, `generateUnsafe` never safe |
| `quantum/QuantumBitTest` | Amplitude normalisation, clamping, rotation direction, observation distribution |
| `quantum/FitnessFunctionTest` | Term weights, hybrid hard constraint, penalty finiteness |
| `quantum/QuantumInspiredOptimizerTest` | Convergence, reproducibility per seed, history length |
| `benchmark/PerformanceAnalyzerTest` | CSV header/row count, JSON escaping, round-trip stability |
| `cli/MainTest` | Exit codes, `help`, unknown command, `demo` produces expected sections |

```bash
mvn test                 # run everything
mvn test -Dtest=BankersAlgorithmTest   # one class
```

CI (`.github/workflows/ci.yml`) runs `mvn verify` on JDK 17 and 21 for every push and
pull request, and archives the Surefire reports as a build artifact.

---

## Limitations

Stated plainly, because overclaiming is the main failure mode of projects in this space:

1. **No quantum hardware.** Classical JVM, classical RNG. The Q-bit representation is a
   compact parameterisation, not quantum computation.
2. **No quantum speedup, and none is possible** for this formulation. The $$O(n^2 m)$$
   Banker baseline is asymptotically and practically faster.
3. **A RAG cycle does not prove deadlock** with multi-instance resources; it is a
   necessary condition only.
4. **The benchmark is single-machine and single-threaded.** JIT warm-up means small-$$n$$
   timings are noisy; treat sub-millisecond differences as measurement noise, not signal.
5. **Multi-VM validation is out of scope.** All results come from one JVM on one host.
6. **The QEA is stochastic.** Individual runs vary; the reported figures are means over
   30 seeded repetitions, and a single run may differ.

---

## License

[MIT](LICENSE) © 2026 Vignesh Shetty and contributors.
