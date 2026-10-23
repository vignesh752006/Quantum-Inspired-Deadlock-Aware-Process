package io.github.vignesh752006.quantumdeadlock.graph;

import io.github.vignesh752006.quantumdeadlock.util.Preconditions;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Directed-cycle detection over a {@link ResourceAllocationGraph} using an
 * <em>iterative</em> depth-first search.
 *
 * <p>Why iterative rather than recursive: the largest benchmark size is
 * 500 processes plus 30 resource types, i.e. a 530-node graph. The JVM's default
 * stack depth is in the same order of magnitude, so a recursive DFS risks
 * {@link StackOverflowError} exactly at the sizes the project cares about. An
 * explicit stack has no depth limit.</p>
 *
 * <p><strong>Interpretation caveat:</strong> a cycle in the RAG is necessary but
 * not sufficient for deadlock when resource types hold multiple instances.
 * Always confirm with the Banker's Algorithm before concluding deadlock.</p>
 */
public final class CycleDetector {

    /**
     * Outcome of one detection run.
     *
     * @param hasCycle       whether a directed cycle exists
     * @param cycleNodes     one cycle in traversal order, closed loop included;
     *                       empty when no cycle was found
     * @param executionTimeNs wall-clock duration
     */
    public record CycleResult(boolean hasCycle, List<Integer> cycleNodes, long executionTimeNs) {

        public CycleResult {
            cycleNodes = cycleNodes == null
                    ? List.of()
                    : Collections.unmodifiableList(new ArrayList<>(cycleNodes));
        }

        @Override
        public String toString() {
            if (!hasCycle) {
                return "CycleResult{NO CYCLE, " + executionTimeNs / 1_000 + " us}";
            }
            return "CycleResult{CYCLE DETECTED, nodes=" + cycleNodes
                    + ", " + executionTimeNs / 1_000 + " us}";
        }
    }

    private CycleDetector() {
        throw new AssertionError("No instances.");
    }

    /**
     * Runs cycle detection.
     *
     * @param graph the graph to analyse; must not be null
     * @return the verdict plus one representative cycle when found
     */
    public static CycleResult detect(ResourceAllocationGraph graph) {
        Preconditions.requireNonNull(graph, "graph must not be null.");

        long startNs = System.nanoTime();
        final int nodeCount = graph.totalNodes();
        boolean[] visited = new boolean[nodeCount];
        boolean[] onStack = new boolean[nodeCount];
        int[] parent = new int[nodeCount];
        Arrays.fill(parent, -1);

        for (int startNode = 0; startNode < nodeCount; startNode++) {
            if (visited[startNode]) {
                continue;
            }
            List<Integer> cycle = depthFirstSearch(graph, startNode, visited, onStack, parent);
            if (cycle != null) {
                return new CycleResult(true, cycle, System.nanoTime() - startNs);
            }
        }
        return new CycleResult(false, List.of(), System.nanoTime() - startNs);
    }

    /** Convenience wrapper when only the verdict is needed. */
    public static boolean hasCycle(ResourceAllocationGraph graph) {
        return detect(graph).hasCycle();
    }

    /**
     * Renders a report for a completed detection run.
     *
     * @param graph  the analysed graph, used for labels
     * @param result the result of {@link #detect}
     * @return a printable report
     */
    public static String formatReport(ResourceAllocationGraph graph, CycleResult result) {
        StringBuilder text = new StringBuilder();
        text.append("=== Cycle Detection Report ===\n");
        text.append("Nodes: ").append(graph.totalNodes())
                .append("  Edges: ").append(graph.edgeCount())
                .append("  Time: ").append(result.executionTimeNs() / 1_000).append(" us\n");

        if (!result.hasCycle()) {
            text.append("Result : NO CYCLE DETECTED\n");
            text.append("Note   : For multi-instance resources this does NOT\n");
            text.append("         guarantee safety - run Banker's Algorithm too.\n");
        } else {
            text.append("Result : CYCLE DETECTED\n");
            text.append("Cycle  : ");
            List<Integer> cycle = result.cycleNodes();
            for (int i = 0; i < cycle.size(); i++) {
                text.append(graph.nodeLabel(cycle.get(i)));
                if (i < cycle.size() - 1) {
                    text.append(" -> ");
                }
            }
            text.append('\n');
            text.append("Note   : For single-instance resources this confirms deadlock.\n");
            text.append("         For multi-instance resources confirm with Banker's Algorithm.\n");
        }
        return text.toString();
    }

    /** Immutable DFS frame: a node plus its remaining successors. */
    private record Frame(int node, Iterator<Integer> successors) {
    }

    /**
     * Iterative DFS from one start node, sharing the visited/on-stack/parent arrays.
     *
     * @return a closed cycle path when one is reachable from {@code startNode}, else null
     */
    private static List<Integer> depthFirstSearch(ResourceAllocationGraph graph, int startNode,
                                                  boolean[] visited, boolean[] onStack, int[] parent) {
        Deque<Frame> stack = new ArrayDeque<>();
        visited[startNode] = true;
        onStack[startNode] = true;
        stack.push(new Frame(startNode, graph.neighbours(startNode).iterator()));

        while (!stack.isEmpty()) {
            Frame frame = stack.peek();
            if (!frame.successors().hasNext()) {
                onStack[frame.node()] = false;
                stack.pop();
                continue;
            }

            int next = frame.successors().next();
            if (onStack[next]) {
                return reconstructCycle(parent, frame.node(), next);
            }
            if (!visited[next]) {
                visited[next] = true;
                onStack[next] = true;
                parent[next] = frame.node();
                stack.push(new Frame(next, graph.neighbours(next).iterator()));
            }
            // Visited but no longer on the stack means a cross/forward edge - no cycle here.
        }
        return null;
    }

    /**
     * Rebuilds a cycle path from the DFS parent links.
     *
     * @param parent    DFS parent array
     * @param cycleEnd  node the back edge leaves from
     * @param cycleStart node the back edge points at (currently on the stack)
     * @return {@code [cycleStart, ..., cycleEnd, cycleStart]}
     */
    private static List<Integer> reconstructCycle(int[] parent, int cycleEnd, int cycleStart) {
        LinkedList<Integer> path = new LinkedList<>();
        path.addFirst(cycleStart);
        int current = cycleEnd;
        while (current != cycleStart && current != -1) {
            path.addFirst(current);
            current = parent[current];
        }
        path.addFirst(cycleStart);
        return path;
    }
}
