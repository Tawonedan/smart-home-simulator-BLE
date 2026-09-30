package io.github.phlekies.smarthome.simulation.raytrace;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.phlekies.smarthome.model.AntennaType;
import io.github.phlekies.smarthome.model.Environment;
import io.github.phlekies.smarthome.model.Hub;
import io.github.phlekies.smarthome.model.Sensor;
import io.github.phlekies.smarthome.model.Wall;
import io.github.phlekies.smarthome.model.material.Materials;
import io.github.phlekies.smarthome.model.template.FloorPlanTemplate;
import io.github.phlekies.smarthome.physics.RadioMath;

class RayTracerTest {

    private static final double WIDTH = 50;
    private static final double HEIGHT = 40;

    /** A single ray pointing along +x (a 1° beam around 0°). */
    private static Sensor pencilBeam(int x, int y) {
        Sensor sensor = new Sensor("S1", "Sensor 1", x, y);
        sensor.setAntennaType(AntennaType.DIRECTIONAL);
        sensor.setBeamwidthDeg(1.0);
        sensor.setOrientationDeg(0.5);
        return sensor;
    }

    private static RayTracer.Settings settings(int maxInteractions) {
        RayTracer.Settings defaults = RayTracer.Settings.defaults();
        return new RayTracer.Settings(defaults.angularStepDeg(), maxInteractions, 200.0, -200.0,
                defaults.hubCaptureRadiusMeters(), defaults.maxSegments());
    }

    @Test
    void inAnEmptyPlanEveryRayRunsToTheBorder() {
        RayTraceResult result = RayTracer.trace(new Environment(), List.of(new Sensor("S1", "Sensor 1", 25, 20)),
                null, WIDTH, HEIGHT, settings(6));

        assertEquals(36, result.segments().size());
        for (RaySegment segment : result.segments()) {
            boolean onBorder = Math.abs(segment.x2()) < 1e-9 || Math.abs(segment.x2() - WIDTH) < 1e-9
                    || Math.abs(segment.y2()) < 1e-9 || Math.abs(segment.y2() - HEIGHT) < 1e-9;
            assertTrue(onBorder, "segment should end on the plan border: " + segment);
        }
    }

    @Test
    void aWallSplitsTheRayIntoTransmittedAndReflectedParts() {
        Environment env = new Environment();
        Wall wall = new Wall(20, 0, 20, 40, Materials.CONCRETE, 15);
        env.addWall(wall);

        RayTraceResult result = RayTracer.trace(env, List.of(pencilBeam(10, 20)), null, WIDTH, HEIGHT, settings(1));

        assertEquals(3, result.segments().size());
        RaySegment incident = result.segments().getFirst();
        assertEquals(20.0, incident.x2(), 1e-6);

        RaySegment transmitted = result.segments().stream()
                .filter(s -> s.interactions() == 1 && s.x2() > 20).findFirst().orElseThrow();
        RaySegment reflected = result.segments().stream()
                .filter(s -> s.interactions() == 1 && s.x2() < 20).findFirst().orElseThrow();

        double incidentPowerAtWall = incident.endPowerDbm();
        double expectedLoss = Materials.CONCRETE.transmissionLossDb(2400, 15);
        assertEquals(incidentPowerAtWall - expectedLoss, transmitted.startPowerDbm(), 1e-6);
        assertTrue(reflected.startPowerDbm() > transmitted.startPowerDbm(), "concrete reflects more than it lets through");
    }

    @Test
    void reflectionMirrorsTheDirectionAboutTheWall() {
        Wall vertical = new Wall(0, 0, 0, 10, Materials.GLASS, 4);
        assertArrayEquals(new double[] { -1.0, 0.0 }, RayTracer.reflect(1, 0, vertical), 1e-12);

        Wall horizontal = new Wall(0, 0, 10, 0, Materials.GLASS, 4);
        double s = Math.sqrt(0.5);
        assertArrayEquals(new double[] { s, -s }, RayTracer.reflect(s, s, horizontal), 1e-12);
    }

    @Test
    void interactionBudgetLimitsTheRayTree() {
        Environment env = new Environment();
        env.setWalls(FloorPlanTemplate.HOTEL_FLOOR.walls());
        List<Sensor> sensors = List.of(new Sensor("S1", "Sensor 1", 10, 10));

        RayTraceResult none = RayTracer.trace(env, sensors, null, WIDTH, HEIGHT, settings(0));
        RayTraceResult some = RayTracer.trace(env, sensors, null, WIDTH, HEIGHT, settings(3));

        assertEquals(36, none.segments().size());
        assertTrue(some.segments().size() > none.segments().size());
        assertTrue(some.segments().stream().allMatch(s -> s.interactions() <= 3));
    }

    @Test
    void raysNeverLeaveThePlan() {
        Environment env = new Environment();
        env.setWalls(FloorPlanTemplate.FACTORY_HALL.walls());
        RayTraceResult result = RayTracer.trace(env, List.of(new Sensor("S1", "Sensor 1", 3, 3)), null,
                WIDTH, HEIGHT, RayTracer.Settings.defaults());

        for (RaySegment segment : result.segments()) {
            for (double x : new double[] { segment.x1(), segment.x2() }) {
                assertTrue(x >= -1e-6 && x <= WIDTH + 1e-6);
            }
            for (double y : new double[] { segment.y1(), segment.y2() }) {
                assertTrue(y >= -1e-6 && y <= HEIGHT + 1e-6);
            }
        }
    }

    @Test
    void powerDecreasesAlongEveryRay() {
        Environment env = new Environment();
        env.setWalls(FloorPlanTemplate.TWO_BEDROOM_APARTMENT.walls());
        RayTraceResult result = RayTracer.trace(env, List.of(new Sensor("S1", "Sensor 1", 7, 7)), null,
                WIDTH, HEIGHT, RayTracer.Settings.defaults());
        assertTrue(result.segments().stream().allMatch(s -> s.endPowerDbm() <= s.startPowerDbm() + 1e-9));
    }

    @Test
    void aRayPassingThroughTheHubIsCaptured() {
        Hub hub = new Hub("H1", "Hub", 30, 20);
        Sensor sensor = pencilBeam(10, 20);
        RayTraceResult result = RayTracer.trace(new Environment(), List.of(sensor), hub, WIDTH, HEIGHT, settings(6));

        assertEquals(1, result.hubHits().size());
        HubHit hit = result.hubHits().getFirst();
        assertEquals(20.0, hit.pathLengthMeters(), 1e-3);
        assertEquals(0, hit.interactions());
        double launchedDbm = sensor.getTxPowerDbm() + sensor.gainTowardsDb(0.0);
        assertEquals(launchedDbm - RadioMath.fsplDb(hit.pathLengthMeters(), 2400), hit.receivedPowerDbm(), 1e-9);
        assertEquals(hit.receivedPowerDbm() - new Environment().noiseFloorDbm(), hit.snrDb(), 1e-9);
    }

    @Test
    void segmentBudgetTruncatesHugeTrees() {
        Environment env = new Environment();
        env.setWalls(FloorPlanTemplate.WAREHOUSE_WITH_AISLES.walls());
        RayTracer.Settings tiny = new RayTracer.Settings(10.0, 10, 200.0, -300.0, 0.3, 100);

        RayTraceResult result = RayTracer.trace(env, List.of(new Sensor("S1", "Sensor 1", 20, 15)), null,
                WIDTH, HEIGHT, tiny);
        assertTrue(result.truncated());
        assertEquals(100, result.segments().size());

        RayTraceResult normal = RayTracer.trace(env, List.of(new Sensor("S1", "Sensor 1", 20, 15)), null,
                WIDTH, HEIGHT, RayTracer.Settings.defaults());
        assertFalse(normal.truncated());
    }
}
