package io.github.phlekies.smarthome.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import io.github.phlekies.smarthome.simulation.CoverageStats;

class CoverageReportTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 30, 12, 0);

    private static SimulatorModel demo() {
        SimulatorModel model = new SimulatorModel();
        model.load(DemoScenario.smartApartment());
        return model;
    }

    @Test
    void listsEverySensorLinkAndTheKeyIndicators() {
        CoverageStats coverage = new CoverageStats(1000, 0.87, -63.0, -75.0, 9.5, 120.0, -92.0);
        String html = CoverageReport.html(demo(), coverage, null, NOW);

        assertTrue(html.startsWith("<!DOCTYPE html>"));
        assertTrue(html.contains("Generated on 2026-09-30 12:00"));
        assertTrue(html.contains("87 %"));
        for (String sensor : new String[] { "Thermostat", "Smoke detector", "Door sensor", "Security camera" }) {
            assertTrue(html.contains(sensor), sensor + " missing from the report");
        }
        assertEquals(6, html.split("Connected</td>").length - 1, "all six demo sensors reach the hub");
        assertFalse(html.contains("<img"), "no image was given");
    }

    @Test
    void embedsThePlanImage() {
        String html = CoverageReport.html(demo(), CoverageStats.EMPTY, new byte[] { 1, 2, 3 }, NOW);
        assertTrue(html.contains("src=\"data:image/png;base64,AQID\""));
    }

    @Test
    void explainsWhenThereIsNoHub() {
        SimulatorModel model = demo();
        model.removeHub();
        assertTrue(CoverageReport.html(model, null, null, NOW).contains("No hub has been placed."));
    }

    @Test
    void escapesUserText() {
        SimulatorModel model = demo();
        model.sensors().getFirst().setName("<script>alert(1)</script>");
        String html = CoverageReport.html(model, null, null, NOW);
        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("&lt;script&gt;"));
    }
}
