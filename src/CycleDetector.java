import java.util.*;

/**
 * CycleDetector.java
 *
 * DFS-based cycle detection on a {@link ResourceAllocationGraph}.
 *
 * Algorithm — iterative DFS with explicit recursion-stack tracking:
 *   For each unvisited node u (outer loop):
 *     Push (u, iterator-over-neighbours, parent-path) onto an explicit stack.
 *     While stack non-empty:
 *       Advance the iterator.
 *       If a neighbour v is already in the current DFS path (inStack[v]),
 *         → back edge found → CYCLE detected; record the cycle path.
 *       Else if v is unvisited, push v and descend.
 *       If iterator exhausted, pop the frame and clear inStack for that node.
 *
 * Why iterative rather than recursive?
 *   For n=500 processes + m=30 resources the RAG can have up to 530 nodes.
 *   Java's default stack depth is ~500–1000 frames, so a recursive DFS risks
 *   StackOverflowError at the large benchmark sizes.  The iterative version
 *   has no stack-depth limit.
 *
 * Key caveat (repeated from ResourceAllocationGraph):
 *   Cycle in RAG ≠ deadlock when resource types have multiple instances.
 *   Always cross-check with {@code BankersAlgorithm} for a definitive verdict.
 *
 * @author  OSDS-CP Group
 * @version 1.0  (Phase 1 – simulated data)
 */
public class CycleDetector {

    // -------------------------------------------------------------------------
    // Result object
    // -------------------------------------------------------------------------

    /**
     * Encapsulates the outcome of a cycle-detection run.
     */
    public static class CycleResult {

        /** True iff at least one directed cycle was found. */
        public final boolean hasCycle;

        /**
         * Node IDs forming one detected cycle (in traversal order), or an
         * empty list if no cycle was found.  The last element equals the first
         * to show the closed loop: [a, b, c, a].
         */
        public final List<Integer> cycleNodes;

        /** Time taken for this detection run (nanoseconds). */
        public final long executionTimeNs;

        CycleResult(boolean hasCycle, List<Integer> cycleNodes, long ns) {
            this.hasCycle      = hasCycle;
            this.cycleNodes    = Collections.unmodifiableList(cycleNodes);
            this.executionTimeNs = ns;
        }

        @Override
        public String toString() {
            if (!hasCycle) return "CycleResult{NO CYCLE, " + executionTimeNs / 1000 + " µs}";
            return "CycleResult{CYCLE DETECTED, nodes=" + cycleNodes
                + ", " + executionTimeNs / 1000 + " µs}";
        }
    }

    // -------------------------------------------------------------------------
    // Frame used in the iterative DFS stack
    // -------------------------------------------------------------------------

    private static class Frame {
        final int node;
        final Iterator<Integer> iter;

        Frame(int node, Iterator<Integer> iter) {
            this.node = node;
            this.iter = iter;
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Runs cycle detection on the given {@link ResourceAllocationGraph}.
     *
     * @param rag the resource allocation graph to analyse
     * @return a {@link CycleResult} with the verdict and (if found) one cycle
     */
    public static CycleResult detect(ResourceAllocationGraph rag) {
        long start = System.nanoTime();

        int N = rag.getTotalNodes();
        boolean[] visited = new boolean[N];
        boolean[] inStack = new boolean[N];

        // parent array is used to reconstruct the cycle path
        int[] parent = new int[N];
        Arrays.fill(parent, -1);

        for (int start_node = 0; start_node < N; start_node++) {
            if (!visited[start_node]) {
                List<Integer> cycle = iterativeDFS(rag, start_node, visited, inStack, parent);
                if (cycle != null) {
                    long elapsed = System.nanoTime() - start;
                    return new CycleResult(true, cycle, elapsed);
                }
            }
        }

        long elapsed = System.nanoTime() - start;
        return new CycleResult(false, Collections.emptyList(), elapsed);
    }

    /**
     * Convenience method: returns true iff the RAG contains a directed cycle.
     * Slightly faster than {@link #detect} when the cycle path is not needed.
     */
    public static boolean hasCycle(ResourceAllocationGraph rag) {
        return detect(rag).hasCycle;
    }

    /**
     * Prints a human-readable cycle report to stdout.
     *
     * @param rag    the graph (used for node labels)
     * @param result the result of a previous {@link #detect} call
     */
    public static void printReport(ResourceAllocationGraph rag, CycleResult result) {
        System.out.println("=== Cycle Detection Report ===");
        System.out.println("Nodes: " + rag.getTotalNodes()
            + "  Edges: " + rag.getEdgeCount()
            + "  Time: " + result.executionTimeNs / 1000 + " µs");
        if (!result.hasCycle) {
            System.out.println("Result : NO CYCLE DETECTED");
            System.out.println("Note   : For multi-instance resources this does NOT");
            System.out.println("         guarantee safety — run Banker's Algorithm too.");
        } else {
            System.out.println("Result : CYCLE DETECTED ⚠");
            System.out.print("Cycle  : ");
            List<Integer> c = result.cycleNodes;
            for (int i = 0; i < c.size(); i++) {
                System.out.print(rag.nodeLabel(c.get(i)));
                if (i < c.size() - 1) System.out.print(" → ");
            }
            System.out.println();
            System.out.println("Note   : For single-instance resources this confirms deadlock.");
            System.out.println("         For multi-instance resources confirm with Banker's Algorithm.");
        }
        System.out.println();
    }

    // -------------------------------------------------------------------------
    // Core iterative DFS
    // -------------------------------------------------------------------------

    /**
     * Performs an iterative DFS from {@code startNode}, updating shared
     * {@code visited} and {@code inStack} arrays.
     *
     * @return the cycle path (closed loop) if one is found, or {@code null}
     */
    private static List<Integer> iterativeDFS(ResourceAllocationGraph rag,
                                               int startNode,
                                               boolean[] visited,
                                               boolean[] inStack,
                                               int[] parent) {
        Deque<Frame> stack = new ArrayDeque<>();

        visited[startNode] = true;
        inStack[startNode] = true;
        stack.push(new Frame(startNode,
                             rag.getNeighbours(startNode).iterator()));

        while (!stack.isEmpty()) {
            Frame top = stack.peek();
            int u = top.node;

            if (top.iter.hasNext()) {
                int v = top.iter.next();

                if (inStack[v]) {
                    // Back edge found → cycle from v back to v
                    return reconstructCycle(parent, u, v);
                }

                if (!visited[v]) {
                    visited[v] = true;
                    inStack[v] = true;
                    parent[v]  = u;
                    stack.push(new Frame(v, rag.getNeighbours(v).iterator()));
                }
                // If visited but not inStack → cross/forward edge → no cycle here
            } else {
                // Finished exploring u → pop and clear inStack
                inStack[u] = false;
                stack.pop();
            }
        }
        return null; // no cycle found from startNode
    }

    /**
     * Reconstructs the cycle path given:
     *   - {@code cycleEnd}: the node where the back edge originates
     *   - {@code cycleStart}: the node the back edge points to (already in stack)
     *   - {@code parent[]}: the DFS parent array
     *
     * Returns a list [cycleStart, ..., cycleEnd, cycleStart] (closed loop).
     */
    private static List<Integer> reconstructCycle(int[] parent, int cycleEnd, int cycleStart) {
        LinkedList<Integer> path = new LinkedList<>();
        path.addFirst(cycleStart); // close the loop

        int cur = cycleEnd;
        while (cur != cycleStart && cur != -1) {
            path.addFirst(cur);
            cur = parent[cur];
        }
        path.addFirst(cycleStart); // open of the loop

        return path;
    }
}
