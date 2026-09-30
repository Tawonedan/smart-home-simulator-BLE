package io.github.phlekies.smarthome.ui;

import java.util.Locale;

import javafx.stage.Window;

import io.github.phlekies.smarthome.app.SimulatorModel;
import io.github.phlekies.smarthome.app.SimulatorModel.Change;
import io.github.phlekies.smarthome.model.Device;
import io.github.phlekies.smarthome.model.Sensor;
import io.github.phlekies.smarthome.simulation.CellResult;
import io.github.phlekies.smarthome.simulation.HeatmapResult;
import io.github.phlekies.smarthome.simulation.PropagationMode;
import io.github.phlekies.smarthome.simulation.raytrace.HubHit;
import io.github.phlekies.smarthome.simulation.raytrace.RayTraceResult;
import io.github.phlekies.smarthome.simulation.raytrace.RayTracer;

/**
 * Keeps the plan view in sync with the model and runs the user-triggered simulations
 * (heatmap, ray and wave animations, summary window).
 */
final class SimulationController {

    private final SimulatorModel model;
    private final UiState state;
    private final PlanView view;
    private final HeatmapService heatmaps;
    private final RayAnimator rays;
    private final WaveAnimator waves;
    private final Window owner;
    private int hubHitCount;

    SimulationController(SimulatorModel model, UiState state, PlanView view, HeatmapService heatmaps, Window owner) {
        this.model = model;
        this.state = state;
        this.view = view;
        this.heatmaps = heatmaps;
        this.owner = owner;
        this.rays = new RayAnimator(view);
        this.waves = new WaveAnimator(view);

        model.addListener(this::onModelChanged);
        heatmaps.latestProperty().addListener((obs, old, result) -> renderHeatmap(result));
    }

    private void onModelChanged(Change change) {
        switch (change) {
            case WALLS -> view.redrawWalls();
            case DEVICES -> {
                Device selected = state.selectedDevice.get();
                boolean stillExists = model.sensors().contains(selected)
                        || model.hub().filter(hub -> hub == selected).isPresent();
                if (selected != null && !stillExists) {
                    state.selectedDevice.set(null);
                }
                view.redrawDevices();
            }
            default -> {
            }
        }
        if (change == Change.DISPLAY) {
            renderHeatmap(heatmaps.latestProperty().get());
            return;
        }
        if (change == Change.WALLS || change == Change.DEVICES) {
            rays.clear(); // the traced rays no longer match the scene
        }
        if (state.heatmapVisible.get()) {
            recomputeHeatmap();
        }
    }

    // ---------------------------------------------------------------------------------------
    // Heatmap
    // ---------------------------------------------------------------------------------------

    void showHeatmap() {
        state.heatmapVisible.set(true);
        recomputeHeatmap();
    }

    void hideHeatmap() {
        state.heatmapVisible.set(false);
        heatmaps.clear();
        view.hideHeatmap();
        state.status.set("Heatmap hidden.");
    }

    private void recomputeHeatmap() {
        if (model.sensors().isEmpty()) {
            heatmaps.clear();
            view.hideHeatmap();
            state.status.set("Add at least one sensor to compute the coverage heatmap.");
            return;
        }
        heatmaps.request(model.snapshot());
    }

    private void renderHeatmap(HeatmapResult result) {
        if (result == null || !state.heatmapVisible.get()) {
            return;
        }
        view.showHeatmap(HeatmapRenderer.render(result, model.settings().getMapMetric()));
    }

    // ---------------------------------------------------------------------------------------
    // Animations
    // ---------------------------------------------------------------------------------------

    void launchRays() {
        if (model.sensors().isEmpty()) {
            state.status.set("Add at least one sensor to launch rays.");
            return;
        }
        waves.clear();
        model.settings().setPropagationMode(PropagationMode.RAYS);
        model.settingsChanged();

        RayTraceResult result = RayTracer.trace(model.environment(), model.sensors(), model.hub().orElse(null),
                SimulatorModel.PLAN_WIDTH_METERS, SimulatorModel.PLAN_HEIGHT_METERS, RayTracer.Settings.defaults());
        hubHitCount = 0;
        state.status.set(String.format(Locale.US, "Tracing %d ray segments%s...", result.segments().size(),
                model.hub().isPresent() ? "" : " (place a hub to measure arrivals)"));
        rays.play(result, this::onRayHit);
    }

    private void onRayHit(HubHit hit) {
        hubHitCount++;
        view.flashHub();
        state.status.set(String.format(Locale.US, "%d ray(s) reached the hub.", hubHitCount));
        if (hubHitCount == 1) {
            view.showHubMessage(String.format(Locale.US,
                    "First ray from %s%nPath length: %.2f m (%d interactions)%nFree-space loss: %.1f dB%n"
                            + "Wall/reflection losses: %.1f dB%nReceived power: %.1f dBm%nSNR: %.1f dB%n"
                            + "BER: %.2e%nCapacity: %.1f Mbps",
                    hit.sensorName(), hit.pathLengthMeters(), hit.interactions(), hit.fsplDb(),
                    hit.interactionLossDb(), hit.receivedPowerDbm(), hit.snrDb(), hit.ber(), hit.capacityMbps()));
        }
    }

    void launchWaves() {
        if (model.sensors().isEmpty()) {
            state.status.set("Add at least one sensor to launch waves.");
            return;
        }
        rays.clear();
        model.settings().setPropagationMode(PropagationMode.WAVES);
        model.settingsChanged();
        state.status.set(model.hub().isPresent()
                ? "Wavefronts expanding towards the hub..."
                : "Wavefronts expanding. Place a hub to measure the links.");
        waves.play(model.sensors(), model.hub().orElse(null), this::onWaveArrival);
    }

    private void onWaveArrival(Sensor sensor) {
        model.sensorLink(sensor).ifPresent(link -> {
            view.flashHub();
            view.showHubMessage(describeLink(sensor, link));
            state.status.set(String.format(Locale.US, "Wave from %s reached the hub: %.1f dBm, SNR %.1f dB.",
                    sensor.getName(), link.totalPowerDbm(), link.snrDb()));
        });
    }

    private String describeLink(Sensor sensor, CellResult link) {
        String strongest = link.contributions().isEmpty() ? "none" : link.contributions().getFirst().type().toString();
        return String.format(Locale.US,
                "%s -> hub%nDistance: %.2f m%nReceived power: %.1f dBm%nSNR: %.1f dB%nBER: %.2e%n"
                        + "Capacity: %.1f Mbps%nPaths: %d (strongest: %s)",
                sensor.getName(), sensor.distanceTo(model.hub().orElseThrow()), link.totalPowerDbm(), link.snrDb(),
                link.ber(), link.capacityMbps(), link.pathCount(), strongest);
    }

    void openSummary() {
        new SummaryWindow(model).show(owner);
    }

    void shutdown() {
        rays.stop();
        waves.clear();
        heatmaps.shutdown();
    }
}
