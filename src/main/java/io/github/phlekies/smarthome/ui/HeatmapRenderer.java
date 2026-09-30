package io.github.phlekies.smarthome.ui;

import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

import io.github.phlekies.smarthome.simulation.CellResult;
import io.github.phlekies.smarthome.simulation.HeatmapResult;
import io.github.phlekies.smarthome.simulation.MapMetric;

/** Turns a {@link HeatmapResult} into an image with one pixel per square metre. */
final class HeatmapRenderer {

    private HeatmapRenderer() {
    }

    static WritableImage render(HeatmapResult result, MapMetric metric) {
        WritableImage image = new WritableImage(result.width(), result.height());
        PixelWriter writer = image.getPixelWriter();
        for (int y = 0; y < result.height(); y++) {
            for (int x = 0; x < result.width(); x++) {
                CellResult cell = result.cellAt(x, y);
                Color color = (cell == null || !cell.hasEnergy())
                        ? Color.TRANSPARENT
                        : colorFor(metric, cell.valueFor(metric));
                // Image rows grow downwards, plan rows grow upwards.
                writer.setColor(x, result.height() - 1 - y, color);
            }
        }
        return image;
    }

    /** Maps a metric value to a colour from violet (poor) to red (excellent). */
    static Color colorFor(MapMetric metric, double value) {
        double quality = switch (metric) {
            case POWER_DBM -> normalize(value, -110.0, -35.0);
            case SINR_DB -> normalize(value, -10.0, 35.0);
            case BER -> 1.0 - normalize(Math.log10(Math.max(1e-8, Math.min(0.5, value))), -8.0, -0.3);
            case CAPACITY_MBPS -> normalize(value, 0.0, 450.0);
        };
        return Color.hsb(260.0 * (1.0 - quality), 0.95, 0.95);
    }

    private static double normalize(double value, double min, double max) {
        return Math.max(0.0, Math.min(1.0, (value - min) / (max - min)));
    }
}
