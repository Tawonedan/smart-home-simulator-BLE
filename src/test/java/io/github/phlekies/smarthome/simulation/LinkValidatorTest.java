package io.github.phlekies.smarthome.simulation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.phlekies.smarthome.model.Environment;
import io.github.phlekies.smarthome.model.Sensor;
import io.github.phlekies.smarthome.model.template.FloorPlanTemplate;

class LinkValidatorTest {

    private final Environment env = new Environment();
    private final SimulationSettings settings = new SimulationSettings();
    private final List<Sensor> sensors = List.of(
            new Sensor("S1", "Sensor 1", 5, 5), new Sensor("S2", "Sensor 2", 30, 20), new Sensor("S3", "Sensor 3", 40, 8));

    LinkValidatorTest() {
        env.setWalls(FloorPlanTemplate.CLINIC_WING.walls());
    }

    @Test
    void environmentChecksPass() {
        assertTrue(LinkValidator.environmentChecks(env).stream().allMatch(LinkValidator.Check::ok));
    }

    @Test
    void engineOutputIsSelfConsistentEverywhere() {
        for (int x = 1; x < 45; x += 7) {
            for (int y = 1; y < 38; y += 6) {
                CellResult cell = PropagationEngine.computeCell(env, sensors, x, y, settings);
                List<LinkValidator.Check> checks = LinkValidator.cellChecks(cell, env, settings);
                assertTrue(checks.stream().allMatch(LinkValidator.Check::ok), "failed at " + x + "," + y + ": " + checks);
            }
        }
    }

    @Test
    void aggregateChecksPassForTheEngineOutput() {
        CellResult combined = PropagationEngine.computeCell(env, sensors, 20, 15, settings);
        List<CellResult> links = sensors.stream()
                .map(sensor -> PropagationEngine.computeCell(env, List.of(sensor), 20, 15, settings)).toList();
        List<String> ids = sensors.stream().map(Sensor::getId).toList();

        assertTrue(LinkValidator.aggregateChecks(combined, ids, links).stream().allMatch(LinkValidator.Check::ok));
    }

    @Test
    void detectsAnInconsistentValue() {
        CellResult cell = PropagationEngine.computeCell(env, sensors, 20, 15, settings);
        CellResult tampered = new CellResult(cell.xMeters(), cell.yMeters(), cell.totalPowerDbm(),
                cell.signalPowerDbm(), cell.interferencePowerDbm(), cell.noiseDbm(), cell.snrDb() + 3.0,
                cell.sinrDb(), cell.linkMarginDb(), cell.ber(), cell.capacityMbps(), cell.fieldPhaseRad(),
                cell.pathCount(), cell.dominantSensorId(), cell.contributions());

        List<LinkValidator.Check> checks = LinkValidator.cellChecks(tampered, env, settings);
        assertFalse(checks.stream().filter(check -> check.parameter().startsWith("SNR")).findFirst().orElseThrow().ok());
        assertTrue(checks.stream().filter(check -> check.parameter().startsWith("SINR")).findFirst().orElseThrow().ok());
    }
}
