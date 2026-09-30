package io.github.phlekies.smarthome.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.scene.paint.Color;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.github.phlekies.smarthome.simulation.MapMetric;

class HeatmapRendererTest {

    @ParameterizedTest
    @EnumSource(MapMetric.class)
    void scalesMapTheirEndsToZeroAndOne(MapMetric metric) {
        HeatmapRenderer.Scale scale = HeatmapRenderer.scaleFor(metric);
        assertEquals(0.0, scale.quality(scale.worst()), 1e-9);
        assertEquals(1.0, scale.quality(scale.best()), 1e-9);
        assertEquals(0.5, scale.quality(scale.valueAt(0.5)), 1e-9);
    }

    @Test
    void valuesOutsideTheScaleAreClamped() {
        HeatmapRenderer.Scale power = HeatmapRenderer.scaleFor(MapMetric.POWER_DBM);
        assertEquals(0.0, power.quality(-200));
        assertEquals(1.0, power.quality(0));
    }

    @Test
    void lowerBitErrorRatesAreBetter() {
        HeatmapRenderer.Scale ber = HeatmapRenderer.scaleFor(MapMetric.BER);
        assertTrue(ber.quality(1e-6) > ber.quality(1e-3));
        assertEquals(1e-4, ber.valueAt(ber.quality(1e-4)), 1e-12);
    }

    @Test
    void turboGoesFromBlueToRed() {
        // Turbo starts in an almost black violet, turns blue quickly and ends in dark red.
        Color low = HeatmapRenderer.turbo(0.1);
        Color high = HeatmapRenderer.turbo(0.95);
        assertTrue(low.getBlue() > 2 * low.getRed(), "low qualities should be blue");
        assertTrue(high.getRed() > 2 * high.getBlue(), "high qualities should be red");
        assertTrue(HeatmapRenderer.turbo(0.0).getBrightness() < 0.3, "the lowest value is dark");
        Color middle = HeatmapRenderer.turbo(0.5);
        assertTrue(middle.getGreen() > 0.7, "the middle of the map should be green-yellow");
    }
}
