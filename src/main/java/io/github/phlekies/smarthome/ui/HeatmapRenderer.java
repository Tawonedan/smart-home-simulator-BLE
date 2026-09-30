package io.github.phlekies.smarthome.ui;

import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

import io.github.phlekies.smarthome.simulation.CellResult;
import io.github.phlekies.smarthome.simulation.HeatmapResult;
import io.github.phlekies.smarthome.simulation.MapMetric;

/** Turns a {@link HeatmapResult} into an image with one pixel per square metre. */
final class HeatmapRenderer {

    /**
     * Display range of a metric. Values are mapped linearly (or on a log10 scale) to a quality
     * between 0 (poor, blue) and 1 (excellent, red).
     */
    record Scale(double worst, double best, boolean logarithmic, String unit) {

        double quality(double value) {
            double v = logarithmic ? Math.log10(Math.max(value, 1e-12)) : value;
            double lo = logarithmic ? Math.log10(worst) : worst;
            double hi = logarithmic ? Math.log10(best) : best;
            return Math.max(0.0, Math.min(1.0, (v - lo) / (hi - lo)));
        }

        /** The metric value displayed at a given quality, for legend ticks. */
        double valueAt(double quality) {
            if (logarithmic) {
                double lo = Math.log10(worst);
                double hi = Math.log10(best);
                return Math.pow(10.0, lo + (hi - lo) * quality);
            }
            return worst + (best - worst) * quality;
        }
    }

    private HeatmapRenderer() {
    }

    static Scale scaleFor(MapMetric metric) {
        return switch (metric) {
            case POWER_DBM -> new Scale(-110.0, -35.0, false, "dBm");
            case SINR_DB -> new Scale(-10.0, 35.0, false, "dB");
            case BER -> new Scale(0.5, 1e-8, true, "");
            case CAPACITY_MBPS -> new Scale(0.0, 450.0, false, "Mbps");
        };
    }

    static WritableImage render(HeatmapResult result, MapMetric metric) {
        Scale scale = scaleFor(metric);
        WritableImage image = new WritableImage(result.width(), result.height());
        PixelWriter writer = image.getPixelWriter();
        for (int y = 0; y < result.height(); y++) {
            for (int x = 0; x < result.width(); x++) {
                CellResult cell = result.cellAt(x, y);
                Color color = (cell == null || !cell.hasEnergy())
                        ? Color.TRANSPARENT
                        : turbo(scale.quality(cell.valueFor(metric)));
                // Image rows grow downwards, plan rows grow upwards.
                writer.setColor(x, result.height() - 1 - y, color);
            }
        }
        return image;
    }

    /**
     * Google's Turbo colour map (polynomial approximation by A. Mikhailov): perceptually
     * smooth from dark blue (0) through green and yellow to dark red (1).
     */
    static Color turbo(double t) {
        double x = Math.max(0.0, Math.min(1.0, t));
        double r = 0.13572138 + x * (4.61539260 + x * (-42.66032258 + x * (132.13108234 + x * (-152.94239396 + x * 59.28637943))));
        double g = 0.09140261 + x * (2.19418839 + x * (4.84296658 + x * (-14.18503333 + x * (4.27729857 + x * 2.82956604))));
        double b = 0.10667330 + x * (12.64194608 + x * (-60.58204836 + x * (110.36276771 + x * (-89.90310912 + x * 27.34824973))));
        return Color.color(clamp(r), clamp(g), clamp(b));
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
