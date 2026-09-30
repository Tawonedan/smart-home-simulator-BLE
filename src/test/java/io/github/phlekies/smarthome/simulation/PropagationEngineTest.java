package io.github.phlekies.smarthome.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.github.phlekies.smarthome.model.Environment;
import io.github.phlekies.smarthome.model.Sensor;
import io.github.phlekies.smarthome.model.Wall;
import io.github.phlekies.smarthome.model.material.Materials;
import io.github.phlekies.smarthome.model.template.FloorPlanTemplate;
import io.github.phlekies.smarthome.physics.RadioMath;

class PropagationEngineTest {

    private static final double FREQ = Environment.WIFI_2_4_GHZ_MHZ;

    /** Only the line-of-sight path, no fading: results must follow the link budget exactly. */
    private static SimulationSettings directPathOnly() {
        SimulationSettings settings = new SimulationSettings();
        settings.setFadingModel(FadingModel.NONE);
        settings.setMaxReflectionPaths(0);
        settings.setDiffractionEnabled(false);
        settings.setScatteringEnabled(false);
        settings.setLogDistanceExponent(2.0);
        return settings;
    }

    private static Sensor sensorAt(String id, int x, int y) {
        return new Sensor(id, id, x, y);
    }

    @Nested
    @DisplayName("Link budget")
    class LinkBudget {

        @Test
        void freeSpaceReceivedPowerIsTxPowerMinusPathLoss() {
            CellResult cell = PropagationEngine.computeCell(new Environment(), List.of(sensorAt("S1", 0, 0)),
                    10, 0, directPathOnly());
            assertEquals(20.0 - RadioMath.fsplDb(10, FREQ), cell.totalPowerDbm(), 1e-9);
            assertEquals(1, cell.pathCount());
            assertEquals(PathType.DIRECT, cell.contributions().getFirst().type());
        }

        @Test
        void aWallInTheWayCostsExactlyItsPenetrationLoss() {
            Environment open = new Environment();
            Environment walled = new Environment();
            walled.addWall(new Wall(5, -10, 5, 10, Materials.BRICK, 12));
            List<Sensor> sensors = List.of(sensorAt("S1", 0, 0));

            double openPower = PropagationEngine.computeCell(open, sensors, 10, 0, directPathOnly()).totalPowerDbm();
            double walledPower = PropagationEngine.computeCell(walled, sensors, 10, 0, directPathOnly()).totalPowerDbm();

            assertEquals(Materials.BRICK.transmissionLossDb(FREQ, 12), openPower - walledPower, 1e-9);
        }

        @Test
        void antennaGainsAndSystemGainAddUp() {
            Environment env = new Environment();
            env.setSystemGainDb(2.0);
            Sensor sensor = sensorAt("S1", 0, 0);
            sensor.setTxGainDb(3.0);
            SimulationSettings settings = directPathOnly();
            settings.setReceiverGainDb(1.5);

            double power = PropagationEngine.computeCell(env, List.of(sensor), 10, 0, settings).totalPowerDbm();
            assertEquals(20.0 + 3.0 + 1.5 + 2.0 - RadioMath.fsplDb(10, FREQ), power, 1e-9);
        }

        @Test
        void noiseSnrAndCapacityFollowFromThePower() {
            Environment env = new Environment();
            CellResult cell = PropagationEngine.computeCell(env, List.of(sensorAt("S1", 0, 0)), 10, 0, directPathOnly());
            assertEquals(env.noiseFloorDbm(), cell.noiseDbm(), 1e-9);
            assertEquals(cell.signalPowerDbm() - cell.noiseDbm(), cell.snrDb(), 1e-9);
            assertEquals(RadioMath.shannonCapacityMbps(cell.sinrDb(), env.getBandwidthHz()), cell.capacityMbps(), 1e-9);
        }

        @Test
        void specularReflectionTravelsTheImagePathLength() {
            Environment env = new Environment();
            env.addWall(new Wall(-100, 5, 100, 5, Materials.CONCRETE, 15));
            SimulationSettings settings = directPathOnly();
            settings.setMaxReflectionPaths(1);

            CellResult cell = PropagationEngine.computeCell(env, List.of(sensorAt("S1", 0, 0)), 10, 0, settings);
            PathContribution reflection = cell.contributions().stream()
                    .filter(path -> path.type() == PathType.REFLECTION).findFirst().orElseThrow();
            // Mirror image of (0, 0) across y = 5 is (0, 10); its distance to (10, 0) is √200.
            assertEquals(Math.sqrt(200.0), reflection.distanceMeters(), 1e-9);
        }
    }

    @Nested
    @DisplayName("Interference")
    class Interference {

        @Test
        void twoIdenticalSensorsAtTheSameDistanceGiveZeroDecibelSinr() {
            List<Sensor> sensors = List.of(sensorAt("S1", 0, 0), sensorAt("S2", 20, 0));
            CellResult cell = PropagationEngine.computeCell(new Environment(), sensors, 10, 0, directPathOnly());
            assertEquals(0.0, cell.sinrDb(), 1e-3);
            assertEquals(cell.signalPowerDbm(), cell.interferencePowerDbm(), 1e-9);
        }

        @Test
        void theStrongestSensorIsTheUsefulSignal() {
            List<Sensor> sensors = List.of(sensorAt("FAR", 40, 0), sensorAt("NEAR", 12, 0));
            CellResult cell = PropagationEngine.computeCell(new Environment(), sensors, 10, 0, directPathOnly());
            assertEquals("NEAR", cell.dominantSensorId());
            assertTrue(cell.sinrDb() > 0);
            assertTrue(cell.snrDb() > cell.sinrDb(), "interference must lower the SINR below the SNR");
        }

        @Test
        void incoherentModeAddsPathPowers() {
            Environment env = new Environment();
            env.setWalls(FloorPlanTemplate.HOTEL_FLOOR.walls());
            SimulationSettings settings = new SimulationSettings();
            settings.setPropagationMode(PropagationMode.RAYS);

            CellResult cell = PropagationEngine.computeCell(env, List.of(sensorAt("S1", 8, 8)), 20.5, 14.5, settings);
            double sumMw = cell.contributions().stream().mapToDouble(PathContribution::powerMilliwatt).sum();
            assertEquals(RadioMath.milliwattToDbm(sumMw), cell.totalPowerDbm(), 1e-9);
        }
    }

    @Nested
    @DisplayName("Path selection")
    class PathSelection {

        @Test
        void pathsBelowTheCullingThresholdAreDropped() {
            SimulationSettings settings = directPathOnly();
            settings.setCullingThresholdDbm(-30.0);
            CellResult cell = PropagationEngine.computeCell(new Environment(), List.of(sensorAt("S1", 0, 0)),
                    30, 0, settings);
            assertEquals(0, cell.pathCount());
            assertFalse(cell.hasEnergy());
        }

        @Test
        void reflectionsAreCappedToTheStrongestOnes() {
            Environment env = new Environment();
            env.setWalls(FloorPlanTemplate.CELLULAR_OFFICE.walls());
            SimulationSettings settings = new SimulationSettings();
            settings.setMaxReflectionPaths(1);

            CellResult cell = PropagationEngine.computeCell(env, List.of(sensorAt("S1", 10, 10)), 25.5, 20.5, settings);
            long reflections = cell.contributions().stream().filter(p -> p.type() == PathType.REFLECTION).count();
            assertTrue(reflections <= 1);
        }

        @Test
        void contributionsAreSortedFromStrongestToWeakest() {
            Environment env = new Environment();
            env.setWalls(FloorPlanTemplate.TWO_BEDROOM_APARTMENT.walls());
            CellResult cell = PropagationEngine.computeCell(env, List.of(sensorAt("S1", 7, 7)), 30.5, 20.5,
                    new SimulationSettings());
            List<PathContribution> paths = cell.contributions();
            for (int i = 1; i < paths.size(); i++) {
                assertTrue(paths.get(i - 1).powerDbm() >= paths.get(i).powerDbm());
            }
        }

        @Test
        void withoutSensorsTheCellIsEmpty() {
            CellResult cell = PropagationEngine.computeCell(new Environment(), List.of(), 5, 5, new SimulationSettings());
            assertEquals(0, cell.pathCount());
            assertEquals(-150.0, cell.totalPowerDbm());
            assertEquals(CellResult.NO_SENSOR, cell.dominantSensorId());
        }
    }

    @Nested
    @DisplayName("Heatmaps")
    class Heatmaps {

        private final Environment env = new Environment();
        private final List<Sensor> sensors = List.of(sensorAt("S1", 7, 7), sensorAt("S2", 33, 22));

        Heatmaps() {
            env.setWalls(FloorPlanTemplate.L_SHAPED_HOUSE.walls());
        }

        @Test
        void samplesTheCentreOfEveryCell() {
            HeatmapResult heatmap = PropagationEngine.computeHeatmap(env, sensors, 12, 9, new SimulationSettings());
            assertEquals(12, heatmap.width());
            assertEquals(9, heatmap.height());
            assertEquals(3.5, heatmap.cellAt(3, 7).xMeters());
            assertEquals(7.5, heatmap.cellAt(3, 7).yMeters());
            assertEquals(null, heatmap.cellAt(12, 0));
        }

        @Test
        void parallelAndSequentialRunsAreIdentical() {
            SimulationSettings parallel = new SimulationSettings();
            SimulationSettings sequential = new SimulationSettings();
            sequential.setParallelComputation(false);

            HeatmapResult a = PropagationEngine.computeHeatmap(env, sensors, 20, 15, parallel);
            HeatmapResult b = PropagationEngine.computeHeatmap(env, sensors, 20, 15, sequential);
            for (int x = 0; x < 20; x++) {
                for (int y = 0; y < 15; y++) {
                    assertEquals(a.cellAt(x, y).totalPowerDbm(), b.cellAt(x, y).totalPowerDbm(), 0.0);
                    assertEquals(a.cellAt(x, y).sinrDb(), b.cellAt(x, y).sinrDb(), 0.0);
                }
            }
        }

        @Test
        void reportsProgressUpToTheTotal() {
            AtomicLong last = new AtomicLong();
            AtomicInteger calls = new AtomicInteger();
            PropagationEngine.computeHeatmap(env, sensors, 10, 6, new SimulationSettings(), new ComputationMonitor() {
                @Override
                public void progress(long done, long total) {
                    last.accumulateAndGet(done, Math::max);
                    calls.incrementAndGet();
                    assertEquals(60, total);
                }

                @Override
                public boolean isCancelled() {
                    return false;
                }
            });
            assertEquals(60, last.get());
            assertTrue(calls.get() >= 6);
        }

        @Test
        void stopsWhenCancelled() {
            ComputationMonitor cancelled = new ComputationMonitor() {
                @Override
                public void progress(long done, long total) {
                }

                @Override
                public boolean isCancelled() {
                    return true;
                }
            };
            assertThrows(CancellationException.class,
                    () -> PropagationEngine.computeHeatmap(env, sensors, 10, 10, new SimulationSettings(), cancelled));
        }
    }

    /**
     * Regression guard: values produced by the original engine (before the refactoring into
     * packages) for three scenarios. The restructured engine must reproduce them exactly.
     */
    @ParameterizedTest(name = "{0} {1} {2} at ({3},{4})")
    @CsvSource({
            "TWO_BEDROOM_APARTMENT, RICIAN,   WAVES, 10, 10, -35.659444626, 29.598670968, 196.680943332,  9, S1",
            "TWO_BEDROOM_APARTMENT, RICIAN,   WAVES, 16, 15, -57.408975467,  5.514936746,  43.782941965, 12, S2",
            "TWO_BEDROOM_APARTMENT, RICIAN,   WAVES, 20, 12, -45.249716015,  2.966765439,  31.506747810, 12, S2",
            "HOTEL_FLOOR,           NONE,     RAYS,  30, 20, -26.815796666, 30.330275671, 201.536718329,  9, S2",
            "HOTEL_FLOOR,           NONE,     RAYS,  16, 15, -41.194734271,  1.235597442,  24.395530755, 12, S1",
            "HOTEL_FLOOR,           NONE,     RAYS,  45, 35, -61.608831754, 21.428803682, 142.576792672, 12, S2",
            "FACTORY_HALL,          RAYLEIGH, WAVES, 10, 10, -32.221630382, 58.428498853, 388.190585200,  6, S1",
            "FACTORY_HALL,          RAYLEIGH, WAVES, 45, 35, -71.100264147, 22.889425013, 152.222009317,  4, S2",
            "FACTORY_HALL,          RAYLEIGH, WAVES, 20, 12, -72.689829741, 11.852811681,  80.572780253,  8, S1",
    })
    void matchesTheOriginalEngine(FloorPlanTemplate template, FadingModel fading, PropagationMode mode,
                                  int x, int y, double totalDbm, double sinrDb, double capacityMbps,
                                  int paths, String dominant) {
        Environment env = new Environment();
        env.setWalls(template.walls());
        Sensor s1 = sensorAt("S1", 7, 7);
        Sensor s2 = sensorAt("S2", 33, 22);
        s2.setTxGainDb(3.0);
        s2.setPolarizationDeg(30.0);
        SimulationSettings settings = new SimulationSettings();
        settings.setFadingModel(fading);
        settings.setPropagationMode(mode);
        settings.setReceiverPolarizationDeg(10.0);

        CellResult cell = PropagationEngine.computeCell(env, List.of(s1, s2), x + 0.5, y + 0.5, settings);

        assertEquals(totalDbm, cell.totalPowerDbm(), 1e-8);
        assertEquals(sinrDb, cell.sinrDb(), 1e-8);
        assertEquals(capacityMbps, cell.capacityMbps(), 1e-7);
        assertEquals(paths, cell.pathCount());
        assertEquals(dominant, cell.dominantSensorId());
    }
}
