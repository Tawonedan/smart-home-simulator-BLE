package io.github.phlekies.smarthome.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CancellationException;

import org.junit.jupiter.api.Test;

import io.github.phlekies.smarthome.model.Environment;
import io.github.phlekies.smarthome.model.Sensor;
import io.github.phlekies.smarthome.model.template.FloorPlanTemplate;

class HubPlacementOptimizerTest {

    private static final Area ROOM = new Area(0, 0, 20, 10);

    @Test
    void twoSymmetricSensorsPutTheHubHalfWay() {
        List<Sensor> sensors = List.of(new Sensor("S1", "S1", 2, 5), new Sensor("S2", "S2", 18, 5));
        HubPlacementOptimizer.Result result = HubPlacementOptimizer.optimize(new Environment(), sensors,
                new SimulationSettings(), ROOM);

        // Max-min fairness: the best spot balances both links, i.e. it is equidistant.
        HubPlacementOptimizer.Candidate best = result.best();
        assertEquals(10, best.x());
        assertEquals(0, best.unreachableSensors());
        assertEquals(best.worstMarginDb(), best.meanMarginDb(), 1e-9);
    }

    @Test
    void theBestCandidateBeatsEveryOtherOne() {
        Environment env = new Environment();
        env.setWalls(FloorPlanTemplate.CLINIC_WING.walls());
        List<Sensor> sensors = List.of(new Sensor("S1", "S1", 5, 5), new Sensor("S2", "S2", 38, 30),
                new Sensor("S3", "S3", 20, 20));
        HubPlacementOptimizer.Result result = HubPlacementOptimizer.optimize(env, sensors,
                new SimulationSettings(), Area.footprintOf(env.getWalls(), ROOM));

        HubPlacementOptimizer.Candidate best = result.best();
        for (HubPlacementOptimizer.Candidate other : result.candidates()) {
            assertTrue(HubPlacementOptimizer.BEST_FIRST.compare(best, other) <= 0);
        }
        assertEquals(best, result.candidates().getFirst());
    }

    @Test
    void reachingEverySensorComesBeforeMargin() {
        HubPlacementOptimizer.Candidate allReached =
                new HubPlacementOptimizer.Candidate(0, 0, 0, 1.0, 1.0, "S1");
        HubPlacementOptimizer.Candidate strongButMissingOne =
                new HubPlacementOptimizer.Candidate(1, 1, 1, 40.0, 40.0, "S2");
        assertTrue(HubPlacementOptimizer.BEST_FIRST.compare(allReached, strongButMissingOne) < 0);
    }

    @Test
    void candidatesCoverEveryWholeMetreOfTheArea() {
        HubPlacementOptimizer.Result result = HubPlacementOptimizer.optimize(new Environment(),
                List.of(new Sensor("S1", "S1", 1, 1)), new SimulationSettings(), new Area(0.5, 0.5, 4.5, 3.5));
        assertEquals(4 * 3, result.candidates().size()); // x = 1..4, y = 1..3
    }

    @Test
    void evaluationIgnoresFadingSoResultsAreStable() {
        SimulationSettings rician = new SimulationSettings();
        rician.setFadingModel(FadingModel.RICIAN);
        List<Sensor> sensors = List.of(new Sensor("S1", "S1", 2, 2));
        HubPlacementOptimizer.Candidate withFading =
                HubPlacementOptimizer.evaluate(new Environment(), sensors, rician, 8, 6);
        HubPlacementOptimizer.Candidate without =
                HubPlacementOptimizer.evaluate(new Environment(), sensors, new SimulationSettings(), 8, 6);
        assertEquals(without.worstMarginDb(), withFading.worstMarginDb(), 1e-12);
    }

    @Test
    void anUnreachableSensorIsReported() {
        Sensor silent = new Sensor("S1", "Silent", 0, 0);
        silent.setTxPowerDbm(-120); // below the engine's culling threshold everywhere
        HubPlacementOptimizer.Candidate candidate = HubPlacementOptimizer.evaluate(new Environment(),
                List.of(silent, new Sensor("S2", "Loud", 5, 5)), new SimulationSettings(), 6, 5);
        assertEquals(1, candidate.unreachableSensors());
        assertEquals("S1", candidate.weakestSensorId());
    }

    @Test
    void needsSensorsAndHonoursCancellation() {
        assertThrows(IllegalArgumentException.class, () -> HubPlacementOptimizer.optimize(new Environment(),
                List.of(), new SimulationSettings(), ROOM));
        ComputationMonitor cancelled = new ComputationMonitor() {
            @Override
            public void progress(long done, long total) {
            }

            @Override
            public boolean isCancelled() {
                return true;
            }
        };
        assertThrows(CancellationException.class, () -> HubPlacementOptimizer.optimize(new Environment(),
                List.of(new Sensor("S1", "S1", 1, 1)), new SimulationSettings(), ROOM, cancelled));
    }
}
