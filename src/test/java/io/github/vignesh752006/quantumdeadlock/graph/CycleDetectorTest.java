package io.github.vignesh752006.quantumdeadlock.graph;

import io.github.vignesh752006.quantumdeadlock.generator.TestCaseGenerator;
import io.github.vignesh752006.quantumdeadlock.model.SystemState;
import io.github.vignesh752006.quantumdeadlock.testsupport.TestStates;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers Step 4 of the original harness: RAG construction and cycle detection.
 */
class CycleDetectorTest {

    @Test
    @DisplayName("a circular-wait ring produces a cycle")
    void ringProducesCycle() {
        ResourceAllocationGraph graph = new ResourceAllocationGraph(TestStates.unsafeRing());
        CycleDetector.CycleResult result = CycleDetector.detect(graph);

        assertTrue(result.hasCycle());
        assertFalse(result.cycleNodes().isEmpty());
    }

    @Test
    @DisplayName("an acyclic state produces no cycle")
    void acyclicStateHasNoCycle() {
        ResourceAllocationGraph graph = new ResourceAllocationGraph(TestStates.acyclicGraphState());

        assertFalse(CycleDetector.hasCycle(graph));
    }

    @Test
    @DisplayName("the reported cycle is a real closed walk in the graph")
    void reportedCycleIsValid() {
        ResourceAllocationGraph graph = new ResourceAllocationGraph(TestStates.unsafeRing());
        CycleDetector.CycleResult result = CycleDetector.detect(graph);

        var cycle = result.cycleNodes();
        assertTrue(cycle.size() >= 3, "a cycle through a bipartite graph needs at least 3 nodes");

        for (int i = 0; i < cycle.size() - 1; i++) {
            int from = cycle.get(i);
            int to = cycle.get(i + 1);
            assertTrue(graph.neighbours(from).contains(to),
                    "edge " + graph.nodeLabel(from) + " -> " + graph.nodeLabel(to)
                            + " must exist in the graph");
        }
    }

    @Test
    @DisplayName("the graph has the expected nodes and edges")
    void graphShape() {
        ResourceAllocationGraph graph = new ResourceAllocationGraph(TestStates.unsafeRing());

        // 2 processes + 2 resource types
        assertEquals(4, graph.totalNodes());
        // P0 alloc R0, P0 req R1, P1 alloc R1, P1 req R0
        assertEquals(4, graph.edgeCount(), "expected 4 edges");
        assertTrue(graph.isProcessNode(0));
        assertTrue(graph.isResourceNode(2));
        assertFalse(graph.isProcessNode(2));
    }

    @Test
    @DisplayName("detection is iterative, so a large graph does not overflow the stack")
    void largeGraphDoesNotOverflow() {
        SystemState large = TestCaseGenerator.generateSafe(500, 30, 1234L);
        ResourceAllocationGraph graph = new ResourceAllocationGraph(large);

        assertEquals(530, graph.totalNodes());
        // The only requirement here is that it returns without StackOverflowError.
        CycleDetector.CycleResult result = CycleDetector.detect(graph);
        assertNotNull(result);
    }

    @Test
    @DisplayName("the report states the verdict and the multi-instance caveat")
    void reportMentionsCaveat() {
        ResourceAllocationGraph graph = new ResourceAllocationGraph(TestStates.unsafeRing());
        CycleDetector.CycleResult result = CycleDetector.detect(graph);
        String report = CycleDetector.formatReport(graph, result);

        assertTrue(report.contains("CYCLE DETECTED"));
        assertTrue(report.contains("Banker's Algorithm"),
                "the report must not present a cycle as proof of deadlock on its own");
    }
}
