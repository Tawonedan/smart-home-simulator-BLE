package io.github.phlekies.smarthome.ui;

import java.util.Locale;
import java.util.stream.Collectors;

import javafx.scene.control.Tooltip;
import javafx.util.Duration;

import io.github.phlekies.smarthome.app.SimulatorModel;
import io.github.phlekies.smarthome.model.Wall;
import io.github.phlekies.smarthome.simulation.CellResult;
import io.github.phlekies.smarthome.simulation.HeatmapResult;
import io.github.phlekies.smarthome.simulation.PathContribution;

/** Tooltip that reports the link metrics (and the wall, if any) under the mouse pointer. */
final class CellProbe {

    private final SimulatorModel model;
    private final UiState state;
    private final HeatmapService heatmaps;
    private final PlanView view;
    private final Tooltip tooltip = new Tooltip();
    private boolean installed;

    CellProbe(SimulatorModel model, UiState state, HeatmapService heatmaps, PlanView view) {
        this.model = model;
        this.state = state;
        this.heatmaps = heatmaps;
        this.view = view;
        tooltip.setShowDelay(Duration.millis(120));
        tooltip.setShowDuration(Duration.INDEFINITE);
    }

    void show(double xMeters, double yMeters, Wall wall) {
        StringBuilder text = new StringBuilder();
        if (wall != null) {
            text.append(String.format(Locale.US, "Wall: %s, %.0f cm (%.1f dB penetration loss)%n",
                    wall.getMaterial().getName(), wall.getThicknessCm(),
                    wall.transmissionLossDb(model.environment().getFreqMHz())));
        }
        if (!model.sensors().isEmpty()) {
            text.append(describe(cellAt(xMeters, yMeters)));
        }
        if (text.isEmpty()) {
            hide();
            return;
        }
        tooltip.setText(text.toString().strip());
        if (!installed) {
            Tooltip.install(view, tooltip);
            installed = true;
        }
    }

    void hide() {
        if (installed) {
            Tooltip.uninstall(view, tooltip);
            installed = false;
        }
    }

    /** Uses the displayed heatmap when available so the tooltip matches the colours. */
    private CellResult cellAt(double xMeters, double yMeters) {
        HeatmapResult heatmap = heatmaps.latestProperty().get();
        if (state.heatmapVisible.get() && heatmap != null) {
            CellResult cell = heatmap.cellAt((int) Math.floor(xMeters), (int) Math.floor(yMeters));
            if (cell != null) {
                return cell;
            }
        }
        return model.probe(xMeters, yMeters);
    }

    private static String describe(CellResult cell) {
        String paths = cell.contributions().stream().limit(3)
                .map(PathContribution::summary)
                .collect(Collectors.joining("\n  ", "  ", ""));
        return String.format(Locale.US,
                "Point (%.1f, %.1f) m%n"
                        + "Dominant sensor: %s%n"
                        + "Total power: %.1f dBm%n"
                        + "Signal: %.1f dBm | Interference: %.1f dBm | Noise: %.1f dBm%n"
                        + "SNR: %.1f dB | SINR: %.1f dB%n"
                        + "BER: %.2e | Capacity: %.1f Mbps | Margin: %.1f dB%n"
                        + "Paths: %d%n%s",
                cell.xMeters(), cell.yMeters(), cell.dominantSensorId(), cell.totalPowerDbm(),
                cell.signalPowerDbm(), cell.interferencePowerDbm(), cell.noiseDbm(),
                cell.snrDb(), cell.sinrDb(), cell.ber(), cell.capacityMbps(), cell.linkMarginDb(),
                cell.pathCount(), cell.pathCount() == 0 ? "" : paths);
    }
}
