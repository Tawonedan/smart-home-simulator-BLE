package io.github.phlekies.smarthome.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.phlekies.smarthome.model.Environment;
import io.github.phlekies.smarthome.model.Sensor;
import io.github.phlekies.smarthome.model.Wall;
import io.github.phlekies.smarthome.model.material.Materials;

class CoverageStatsTest {

    private static HeatmapResult heatmap(Environment env, Sensor... sensors) {
        return PropagationEngine.computeHeatmap(env, List.of(sensors), 20, 10, new SimulationSettings());
    }

    @Test
    void percentilesInterpolateBetweenSamples() {
        double[] values = { 0, 10, 20, 30, 40 };
        assertEquals(20.0, CoverageStats.percentile(values, 0.5), 1e-12);
        assertEquals(4.0, CoverageStats.percentile(values, 0.1), 1e-12);
        assertEquals(40.0, CoverageStats.percentile(values, 1.0), 1e-12);
    }

    @Test
    void onlyCellsInsideTheAreaAreCounted() {
        HeatmapResult result = heatmap(new Environment(), new Sensor("S1", "S1", 5, 5));
        CoverageStats stats = CoverageStats.of(result, new Area(0, 0, 10, 10), -92);
        assertEquals(100, stats.cellCount()); // 10 × 10 cell centres inside the 20 × 10 map
    }

    @Test
    void aStrongSensorCoversTheWholeRoom() {
        CoverageStats stats = CoverageStats.of(heatmap(new Environment(), new Sensor("S1", "S1", 10, 5)),
                new Area(0, 0, 20, 10), -92);
        assertEquals(1.0, stats.coveredFraction(), 1e-12);
        assertTrue(stats.medianSignalDbm() > stats.signalP10Dbm(), "median must exceed the 10th percentile");
    }

    @Test
    void aShieldedHalfLosesCoverage() {
        Environment env = new Environment();
        // A metal partition down the middle, and a weak battery sensor on the left.
        env.addWall(new Wall(10, -1, 10, 11, Materials.METAL_DOOR, 4));
        Sensor weak = new Sensor("S1", "S1", 2, 5);
        weak.setTxPowerDbm(-30);

        CoverageStats stats = CoverageStats.of(heatmap(env, weak), new Area(0, 0, 20, 10), -92);
        assertTrue(stats.coveredFraction() > 0.3 && stats.coveredFraction() < 0.8,
                "only part of the room should be covered, got " + stats.coveredFraction());
    }

    @Test
    void anAreaOutsideTheMapIsEmpty() {
        CoverageStats stats = CoverageStats.of(heatmap(new Environment(), new Sensor("S1", "S1", 5, 5)),
                new Area(100, 100, 120, 120), -92);
        assertTrue(stats.isEmpty());
    }

    @Test
    void theFootprintIsTheBoundingBoxOfTheWalls() {
        Area area = Area.footprintOf(List.of(
                new Wall(3, 4, 9, 4, Materials.BRICK, 10),
                new Wall(9, 4, 9, 12, Materials.BRICK, 10)), new Area(0, 0, 50, 40));
        assertEquals(new Area(3, 4, 9, 12), area);
        assertEquals(new Area(0, 0, 50, 40), Area.footprintOf(List.of(), new Area(0, 0, 50, 40)));
    }
}
