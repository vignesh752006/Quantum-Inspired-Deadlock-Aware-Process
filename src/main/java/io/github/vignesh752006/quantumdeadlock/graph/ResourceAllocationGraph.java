package io.github.vignesh752006.quantumdeadlock.graph;

import io.github.vignesh752006.quantumdeadlock.model.SystemState;
import io.github.vignesh752006.quantumdeadlock.util.Preconditions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Directed bipartite resource-allocation graph (RAG) derived from a
 * {@link SystemState}.
 *
 * <p>Nodes share one integer id space so no per-node class is needed:</p>
 * <ul>
 *   <li>process node for process {@code i} &rarr; id {@code i}</li>
 *   <li>resource node for resource type {@code j} &rarr; id {@code n + j}</li>
 * </ul>
 *
 * <p>Edges carry the classical semantics:</p>
 * <ul>
 *   <li>allocation edge {@code Resource j -> Process i} when {@code allocation[i][j] > 0}</li>
 *   <li>request edge {@code Process i -> Resource j} when {@code need[i][j] > 0}</li>
 * </ul>
 *
 * <p><strong>Documented limitation:</strong> for resource types with multiple
 * instances a cycle is necessary but <em>not</em> sufficient for deadlock. Use
 * {@link CycleDetector} as a fast first-pass filter and always confirm with the
 * Banker's Algorithm. Only for single-instance resource types is a cycle
 * equivalent to deadlock.</p>
 */
public final class ResourceAllocationGraph {

    /**
     * One directed edge.
     *
     * @param from         source node id
     * @param to           destination node id
     * @param weight       instance count behind the edge
     * @param isAllocation true for an allocation edge (resource to process),
     *                     false for a request edge (process to resource)
     */
    public record Edge(int from, int to, int weight, boolean isAllocation) {

        @Override
        public String toString() {
            return (isAllocation ? "ALLOC" : "REQ") + " " + from + "->" + to + " [" + weight + "]";
        }
    }

    private final int processCount;
    private final int resourceCount;
    private final int totalNodes;
    private final List<List<Integer>> adjacency;
    private final List<Edge> edges;
    private final String[] processNames;
    private final String[] resourceNames;

    /**
     * Builds the graph for a state.
     *
     * @param state the state to model; must not be null
     */
    public ResourceAllocationGraph(SystemState state) {
        Preconditions.requireNonNull(state, "state must not be null.");

        this.processCount = state.processCount();
        this.resourceCount = state.resourceCount();
        this.totalNodes = processCount + resourceCount;

        this.processNames = new String[processCount];
        this.resourceNames = new String[resourceCount];
        for (int i = 0; i < processCount; i++) {
            processNames[i] = state.process(i).name();
        }
        for (int j = 0; j < resourceCount; j++) {
            resourceNames[j] = state.resource(j).name();
        }

        this.adjacency = new ArrayList<>(totalNodes);
        for (int node = 0; node < totalNodes; node++) {
            adjacency.add(new ArrayList<>());
        }
        this.edges = new ArrayList<>();

        for (int i = 0; i < processCount; i++) {
            for (int j = 0; j < resourceCount; j++) {
                int allocated = state.allocation(i, j);
                if (allocated > 0) {
                    addEdge(resourceNodeId(j), processNodeId(i), allocated, true);
                }
                int needed = state.need(i, j);
                if (needed > 0) {
                    addEdge(processNodeId(i), resourceNodeId(j), needed, false);
                }
            }
        }
    }

    private void addEdge(int from, int to, int weight, boolean isAllocation) {
        adjacency.get(from).add(to);
        edges.add(new Edge(from, to, weight, isAllocation));
    }

    public int totalNodes() {
        return totalNodes;
    }

    public int processCount() {
        return processCount;
    }

    public int resourceCount() {
        return resourceCount;
    }

    /**
     * @param nodeId node in {@code [0, totalNodes)}
     * @return an unmodifiable view of that node's successors
     */
    public List<Integer> neighbours(int nodeId) {
        rangeCheck(nodeId);
        return Collections.unmodifiableList(adjacency.get(nodeId));
    }

    /** @return an unmodifiable view of every edge */
    public List<Edge> edges() {
        return Collections.unmodifiableList(edges);
    }

    public int edgeCount() {
        return edges.size();
    }

    public boolean isProcessNode(int nodeId) {
        return nodeId >= 0 && nodeId < processCount;
    }

    public boolean isResourceNode(int nodeId) {
        return nodeId >= processCount && nodeId < totalNodes;
    }

    /**
     * @param nodeId a process node
     * @return the zero-based process index
     * @throws IllegalArgumentException if the node is not a process node
     */
    public int processIndex(int nodeId) {
        if (!isProcessNode(nodeId)) {
            throw new IllegalArgumentException("Node " + nodeId + " is not a process node.");
        }
        return nodeId;
    }

    /**
     * @param nodeId a resource node
     * @return the zero-based resource index
     * @throws IllegalArgumentException if the node is not a resource node
     */
    public int resourceIndex(int nodeId) {
        if (!isResourceNode(nodeId)) {
            throw new IllegalArgumentException("Node " + nodeId + " is not a resource node.");
        }
        return nodeId - processCount;
    }

    public int processNodeId(int processIndex) {
        return processIndex;
    }

    public int resourceNodeId(int resourceIndex) {
        return processCount + resourceIndex;
    }

    /** @return a human-readable label for any node id */
    public String nodeLabel(int nodeId) {
        if (isProcessNode(nodeId)) {
            return processNames[nodeId];
        }
        if (isResourceNode(nodeId)) {
            return resourceNames[nodeId - processCount];
        }
        return "?(" + nodeId + ")";
    }

    /**
     * Renders the adjacency list, skipping isolated nodes.
     *
     * @return a printable adjacency listing
     */
    public String formatAdjacencyList() {
        StringBuilder text = new StringBuilder();
        text.append("=== Resource Allocation Graph ===\n");
        text.append("Processes: ").append(processCount)
                .append("   Resource types: ").append(resourceCount)
                .append("   Nodes: ").append(totalNodes)
                .append("   Edges: ").append(edges.size()).append("\n\n");

        for (int node = 0; node < totalNodes; node++) {
            List<Integer> successors = adjacency.get(node);
            if (successors.isEmpty()) {
                continue;
            }
            text.append("  ").append(isProcessNode(node) ? "[P]" : "[R]")
                    .append(' ').append(String.format("%-6s", nodeLabel(node))).append(" ->");
            for (int successor : successors) {
                text.append("  ").append(isProcessNode(successor) ? "[P]" : "[R]")
                        .append(nodeLabel(successor));
            }
            text.append('\n');
        }
        return text.toString();
    }

    private void rangeCheck(int nodeId) {
        if (nodeId < 0 || nodeId >= totalNodes) {
            throw new IndexOutOfBoundsException(
                    "Node " + nodeId + " out of range [0, " + (totalNodes - 1) + "].");
        }
    }
}
