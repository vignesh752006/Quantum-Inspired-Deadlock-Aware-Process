import java.util.*;

/**
 * ResourceAllocationGraph.java
 *
 * An adjacency-list directed graph built from a {@link SystemState}.
 *
 * Node encoding (single integer space, no separate node classes needed):
 *   Process nodes  :  id  =  i          (0 ≤ i < n)
 *   Resource nodes :  id  =  n + j      (0 ≤ j < m)
 *
 * Edge semantics:
 *   Allocation edge  :  Resource j  →  Process i   (when allocation[i][j] > 0)
 *   Request   edge   :  Process  i  →  Resource j  (when need[i][j]     > 0)
 *
 * Important limitation (documented, not hidden):
 *   A cycle in this RAG is a NECESSARY but not SUFFICIENT condition for
 *   deadlock when resource types have multiple instances.  Use
 *   {@link CycleDetector#hasCycle(ResourceAllocationGraph)} as a fast
 *   first-pass filter; always confirm with {@code BankersAlgorithm} for the
 *   definitive safe/unsafe verdict.
 *   (For single-instance resource types a cycle IS sufficient for deadlock.)
 *
 * Phase-2 note:
 *   The constructor accepts a {@link SystemState}, which in Phase 2 can be
 *   built from live VM agent data via {@code SystemState.fromAgentReport()}.
 *   No changes to this class are required.
 *
 * @author  OSDS-CP Group
 * @version 1.0  (Phase 1 – simulated data)
 */
public class ResourceAllocationGraph {

    // -------------------------------------------------------------------------
    // Inner classes for typed edges (useful for dashboard JSON export later)
    // -------------------------------------------------------------------------

    /** Represents a single directed edge in the RAG. */
    public static class Edge {
        public final int from;   // node id
        public final int to;     // node id
        public final int weight; // instance count (allocation or need amount)
        public final boolean isAllocation; // true = R→P, false = P→R (request)

        Edge(int from, int to, int weight, boolean isAllocation) {
            this.from = from; this.to = to;
            this.weight = weight; this.isAllocation = isAllocation;
        }

        @Override
        public String toString() {
            return (isAllocation ? "ALLOC" : "REQ")
                + " " + from + "→" + to + " [" + weight + "]";
        }
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final int n;   // number of processes
    private final int m;   // number of resource types
    private final int totalNodes; // n + m

    /** Adjacency list: adjacency.get(u) = list of node IDs reachable from u. */
    private final List<List<Integer>> adjacency;

    /** All edges in the graph (for inspection / export). */
    private final List<Edge> edges;

    /** Process names for display. */
    private final String[] procNames;

    /** Resource names for display. */
    private final String[] resNames;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Builds the RAG from the given {@link SystemState}.
     *
     * @param state the system allocation state (read-only; not stored)
     */
    public ResourceAllocationGraph(SystemState state) {
        this.n          = state.getNumProcesses();
        this.m          = state.getNumResources();
        this.totalNodes = n + m;

        this.procNames = new String[n];
        this.resNames  = new String[m];
        for (int i = 0; i < n; i++) procNames[i] = state.getProcess(i).getName();
        for (int j = 0; j < m; j++) resNames[j]  = state.getResource(j).getName();

        // Initialise adjacency list
        this.adjacency = new ArrayList<>(totalNodes);
        for (int k = 0; k < totalNodes; k++) adjacency.add(new ArrayList<>());

        this.edges = new ArrayList<>();

        // Build edges
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {

                int alloc = state.getAllocation(i, j);
                int need  = state.getNeed(i, j);

                // Allocation edge: resource node (n+j) → process node (i)
                if (alloc > 0) {
                    int from = n + j, to = i;
                    adjacency.get(from).add(to);
                    edges.add(new Edge(from, to, alloc, true));
                }

                // Request edge: process node (i) → resource node (n+j)
                if (need > 0) {
                    int from = i, to = n + j;
                    adjacency.get(from).add(to);
                    edges.add(new Edge(from, to, need, false));
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Graph queries
    // -------------------------------------------------------------------------

    /** @return total number of nodes (n + m) */
    public int getTotalNodes() { return totalNodes; }

    /** @return number of process nodes */
    public int getNumProcesses() { return n; }

    /** @return number of resource nodes */
    public int getNumResources() { return m; }

    /**
     * Returns an unmodifiable view of the neighbours of node {@code u}.
     *
     * @param u node id in [0, totalNodes)
     */
    public List<Integer> getNeighbours(int u) {
        rangeCheck(u);
        return Collections.unmodifiableList(adjacency.get(u));
    }

    /** @return unmodifiable list of all edges */
    public List<Edge> getEdges() { return Collections.unmodifiableList(edges); }

    /** @return number of edges */
    public int getEdgeCount() { return edges.size(); }

    /**
     * Returns true iff node {@code u} represents a process.
     *
     * @param u node id
     */
    public boolean isProcessNode(int u) { return u >= 0 && u < n; }

    /**
     * Returns true iff node {@code u} represents a resource type.
     *
     * @param u node id
     */
    public boolean isResourceNode(int u) { return u >= n && u < totalNodes; }

    /**
     * Returns the process index (0-based) for a process node.
     *
     * @param u process node id
     * @throws IllegalArgumentException if u is not a process node
     */
    public int processIndex(int u) {
        if (!isProcessNode(u)) throw new IllegalArgumentException("Node " + u + " is not a process node.");
        return u;
    }

    /**
     * Returns the resource index (0-based) for a resource node.
     *
     * @param u resource node id
     * @throws IllegalArgumentException if u is not a resource node
     */
    public int resourceIndex(int u) {
        if (!isResourceNode(u)) throw new IllegalArgumentException("Node " + u + " is not a resource node.");
        return u - n;
    }

    /**
     * Converts a process index {@code i} to its node id.
     */
    public int processNodeId(int i) { return i; }

    /**
     * Converts a resource index {@code j} to its node id.
     */
    public int resourceNodeId(int j) { return n + j; }

    /**
     * Returns a human-readable label for any node id.
     */
    public String nodeLabel(int u) {
        if (isProcessNode(u))  return procNames[u];
        if (isResourceNode(u)) return resNames[u - n];
        return "?(" + u + ")";
    }

    // -------------------------------------------------------------------------
    // Display
    // -------------------------------------------------------------------------

    /**
     * Prints the adjacency list to stdout.
     * Useful for debugging and unit-test output.
     */
    public void printAdjacencyList() {
        System.out.println("=== Resource Allocation Graph ===");
        System.out.println("Processes: " + n + "   Resource types: " + m
            + "   Nodes: " + totalNodes + "   Edges: " + edges.size());
        System.out.println();
        for (int u = 0; u < totalNodes; u++) {
            List<Integer> nbrs = adjacency.get(u);
            if (nbrs.isEmpty()) continue;
            String type = isProcessNode(u) ? "[P]" : "[R]";
            System.out.printf("  %s %-6s →", type, nodeLabel(u));
            for (int v : nbrs) {
                String vtype = isProcessNode(v) ? "[P]" : "[R]";
                System.out.printf("  %s%s", vtype, nodeLabel(v));
            }
            System.out.println();
        }
        System.out.println();
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private void rangeCheck(int u) {
        if (u < 0 || u >= totalNodes)
            throw new IndexOutOfBoundsException("Node " + u + " out of range [0," + (totalNodes-1) + "]");
    }
}
