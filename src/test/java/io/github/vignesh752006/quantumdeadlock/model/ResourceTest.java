package io.github.vignesh752006.quantumdeadlock.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers Step 1's resource accounting: capacity, allocate/release bounds and
 * utilisation.
 */
class ResourceTest {

    @Test
    @DisplayName("a new resource starts fully available")
    void startsFullyAvailable() {
        Resource resource = new Resource(0, "R0", 10);

        assertEquals(10, resource.totalInstances());
        assertEquals(10, resource.availableInstances());
        assertEquals(0, resource.allocatedInstances());
        assertEquals(0.0, resource.utilization());
    }

    @Test
    @DisplayName("allocate and release move instances symmetrically")
    void allocateAndReleaseAreSymmetric() {
        Resource resource = new Resource(0, "R0", 10);

        resource.allocate(4);
        assertEquals(6, resource.availableInstances());
        assertEquals(4, resource.allocatedInstances());
        assertEquals(0.4, resource.utilization(), 1e-9);

        resource.release(4);
        assertEquals(10, resource.availableInstances());
        assertEquals(0.0, resource.utilization());
    }

    @Test
    @DisplayName("allocating more than is available is rejected")
    void overAllocationRejected() {
        Resource resource = new Resource(0, "R0", 5, 2);
        assertThrows(IllegalStateException.class, () -> resource.allocate(3));
    }

    @Test
    @DisplayName("releasing beyond total capacity is rejected")
    void overReleaseRejected() {
        Resource resource = new Resource(0, "R0", 5, 4);
        assertThrows(IllegalStateException.class, () -> resource.release(2));
    }

    @Test
    @DisplayName("negative counts are rejected")
    void negativeCountsRejected() {
        Resource resource = new Resource(0, "R0", 5);
        assertThrows(IllegalArgumentException.class, () -> resource.allocate(-1));
        assertThrows(IllegalArgumentException.class, () -> resource.release(-1));
    }

    @Test
    @DisplayName("available above total is rejected at construction")
    void invalidConstructionRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Resource(0, "R0", 5, 6));
        assertThrows(IllegalArgumentException.class, () -> new Resource(0, "R0", 0));
    }

    @Test
    @DisplayName("deepCopy carries state without sharing it")
    void deepCopyCarriesState() {
        Resource original = new Resource(2, "R2", 8, 3);
        Resource copy = original.deepCopy();

        assertEquals(8, copy.totalInstances());
        assertEquals(3, copy.availableInstances());

        copy.allocate(3);
        assertEquals(0, copy.availableInstances());
        assertEquals(3, original.availableInstances(), "original must be untouched");
    }
}
