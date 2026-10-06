package io.github.phlekies.smarthome.ui;

import java.util.Locale;

import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import io.github.phlekies.smarthome.simulation.CoverageStats;
import io.github.phlekies.smarthome.simulation.MapMetric;

/** Floating card over the plan: colour scale of the heatmap and headline coverage figures. */
final class HeatmapLegend extends VBox {

    private static final double BAR_WIDTH = 220;
    private static final double BAR_HEIGHT = 12;

    private final Label title = new Label();
    private final Canvas bar = new Canvas(BAR_WIDTH, BAR_HEIGHT);
    private final Label worst = new Label();
    private final Label middle = new Label();
    private final Label best = new Label();
    private final Label coverage = new Label();

    HeatmapLegend() {
        getStyleClass().add("legend");
        title.getStyleClass().add("legend-title");
        coverage.getStyleClass().add("legend-coverage");
        coverage.setWrapText(true);
        for (Label tick : new Label[] { worst, middle, best }) {
            tick.getStyleClass().add("legend-tick");
        }
        Region leftGap = new Region();
        Region rightGap = new Region();
        HBox.setHgrow(leftGap, Priority.ALWAYS);
        HBox.setHgrow(rightGap, Priority.ALWAYS);
        HBox ticks = new HBox(worst, leftGap, middle, rightGap, best);
        ticks.setAlignment(Pos.CENTER);
        ticks.setMaxWidth(BAR_WIDTH);
        getChildren().addAll(title, bar, ticks, coverage);
        setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        drawBar();
    }

    void showMetric(MapMetric metric, boolean bleMode) {
        HeatmapRenderer.Scale scale = HeatmapRenderer.scaleFor(metric);
        title.setText(bleMode ? metric.toString() + " (BLE)" : metric.toString());
        worst.setText(format(scale, scale.valueAt(0.0)));
        middle.setText(format(scale, scale.valueAt(0.5)));
        best.setText(format(scale, scale.valueAt(1.0)));
    }

    void showMetric(MapMetric metric) {
        showMetric(metric, false);
    }

    void showCoverage(CoverageStats stats) {
        if (stats == null || stats.isEmpty()) {
            coverage.setText("");
            coverage.setManaged(false);
            return;
        }
        coverage.setManaged(true);
        coverage.setText(String.format(Locale.US, "Usable signal in %.0f%% of the building%nMedian %.0f dBm, 90%% of it above %.0f dBm",
                stats.coveredFraction() * 100, stats.medianSignalDbm(), stats.signalP10Dbm()));
    }

    private void drawBar() {
        GraphicsContext g = bar.getGraphicsContext2D();
        for (int x = 0; x < BAR_WIDTH; x++) {
            g.setFill(HeatmapRenderer.turbo(x / (BAR_WIDTH - 1)));
            g.fillRect(x, 0, 1, BAR_HEIGHT);
        }
    }

    private static String format(HeatmapRenderer.Scale scale, double value) {
        String number = scale.logarithmic()
                ? String.format(Locale.US, "%.0e", value)
                : String.format(Locale.US, "%.0f", value);
        return scale.unit().isEmpty() ? number : number + " " + scale.unit();
    }
}
