package io.github.phlekies.smarthome.physics;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GeometryTest {

    @Test
    void crossingDiagonalsMeetInTheMiddle() {
        double[] hit = Geometry.segmentIntersection(0, 0, 2, 2, 0, 2, 2, 0);
        assertArrayEquals(new double[] { 1.0, 1.0, 0.5 }, hit, 1e-12);
    }

    @Test
    void parallelSegmentsDoNotIntersect() {
        assertNull(Geometry.segmentIntersection(0, 0, 4, 0, 0, 1, 4, 1));
    }

    @Test
    void segmentsThatWouldMeetBeyondTheirEndsDoNotIntersect() {
        assertNull(Geometry.segmentIntersection(0, 0, 1, 0, 2, -1, 2, 1));
    }

    @Test
    void touchingAnEndpointCountsAsCrossing() {
        assertTrue(Geometry.segmentsIntersect(0, 0, 2, 0, 2, 0, 2, 5));
        assertFalse(Geometry.segmentsIntersect(0, 0, 1.9, 0, 2, 0, 2, 5));
    }

    @Test
    void distanceToASegmentUsesThePerpendicularInsideIt() {
        assertEquals(3.0, Geometry.distancePointToSegment(5, 3, 0, 0, 10, 0), 1e-12);
    }

    @Test
    void distanceToASegmentUsesTheNearestEndBeyondIt() {
        assertEquals(5.0, Geometry.distancePointToSegment(13, 4, 0, 0, 10, 0), 1e-12);
    }

    @Test
    void mirrorsAPointAcrossALine() {
        assertArrayEquals(new double[] { 2.0, -3.0 }, Geometry.mirrorPoint(2, 3, 0, 0, 1, 0), 1e-12);
        assertArrayEquals(new double[] { -4.0, 1.0 }, Geometry.mirrorPoint(4, 1, 0, -5, 0, 5), 1e-12);
    }

    @Test
    void incidenceAngleIsMeasuredFromTheWallNormal() {
        // Wall along the x axis; arriving straight down is normal incidence.
        assertEquals(0.0, Geometry.incidenceAngleDeg(0, 5, 0, 0, -1, 0, 1, 0), 1e-9);
        assertEquals(45.0, Geometry.incidenceAngleDeg(-5, 5, 0, 0, -1, 0, 1, 0), 1e-9);
    }
}
