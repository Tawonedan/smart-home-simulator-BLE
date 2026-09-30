package io.github.phlekies.smarthome.ui;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.concurrent.Task;
import javafx.stage.Window;

import io.github.phlekies.smarthome.app.SimulatorModel;
import io.github.phlekies.smarthome.app.SimulatorModel.Change;
import io.github.phlekies.smarthome.model.Device;
import io.github.phlekies.smarthome.model.Environment;
import io.github.phlekies.smarthome.model.Hub;
import io.github.phlekies.smarthome.model.Sensor;
import io.github.phlekies.smarthome.simulation.Area;
import io.github.phlekies.smarthome.simulation.CellResult;
import io.github.phlekies.smarthome.simulation.ComputationMonitor;
import io.github.phlekies.smarthome.simulation.CoverageStats;
import io.github.phlekies.smarthome.simulation.HeatmapResult;
import io.github.phlekies.smarthome.simulation.HubPlacementOptimizer;
import io.github.phlekies.smarthome.simulation.PropagationMode;
import io.github.phlekies.smarthome.simulation.SimulationSettings;
import io.github.phlekies.smarthome.simulation.raytrace.HubHit;
import io.github.phlekies.smarthome.simulation.raytrace.RayTraceResult;
import io.github.phlekies.smarthome.simulation.raytrace.RayTracer;

/**
 * Keeps the plan view and the derived results (coverage, links) in sync with the model, and
 * runs the user-triggered simulations: heatmap, ray and wave animations, hub optimisation.
 */
final class SimulationController {

    private static final Logger LOG = Logger.getLogger(SimulationController.class.getName());

    private final SimulatorModel model;
    private final UiState state;
    private final PlanView view;
    private final HeatmapService heatmaps;
    private final RayAnimator rays;
    private final WaveAnimator waves;
    private final Window owner;
    private final ExecutorService optimiserExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "hub-optimiser");
        thread.setDaemon(true);
        return thread;
    });
    private final ReadOnlyBooleanWrapper optimising = new ReadOnlyBooleanWrapper(false);
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
        heatmaps.latestProperty().addListener((obs, old, result) -> onHeatmap(result));
        state.heatmapVisible.addListener((obs, old, visible) -> {
            if (visible) {
                recomputeHeatmap();
            } else {
                heatmaps.clear();
                view.hideHeatmap();
                state.coverage.set(CoverageStats.EMPTY);
            }
        });
        refreshLinks();
    }

    ReadOnlyBooleanProperty optimisingProperty() {
        return optimising.getReadOnlyProperty();
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
            onHeatmap(heatmaps.latestProperty().get());
            return;
        }
        if (change == Change.WALLS || change == Change.DEVICES) {
            rays.clear(); // the traced rays no longer match the scene
        }
        refreshLinks();
        if (state.heatmapVisible.get()) {
            recomputeHeatmap();
        }
    }

    private void refreshLinks() {
        state.links.set(model.linkSummaries());
    }

    // ---------------------------------------------------------------------------------------
    // Heatmap
    // ---------------------------------------------------------------------------------------

    void toggleHeatmap() {
        state.heatmapVisible.set(!state.heatmapVisible.get());
    }

    private void recomputeHeatmap() {
        if (model.sensors().isEmpty()) {
            heatmaps.clear();
            view.hideHeatmap();
            state.coverage.set(CoverageStats.EMPTY);
            state.status.set("Add at least one sensor to compute the coverage heatmap.");
            return;
        }
        heatmaps.request(model.snapshot());
    }

    private void onHeatmap(HeatmapResult result) {
        if (result == null || !state.heatmapVisible.get()) {
            return;
        }
        view.showHeatmap(HeatmapRenderer.render(result, model.settings().getMapMetric()));
        state.coverage.set(CoverageStats.of(result, model.footprint(), model.settings().getReceiverSensitivityDbm()));
    }

    // ---------------------------------------------------------------------------------------
    // Hub placement
    // ---------------------------------------------------------------------------------------

    void optimiseHub() {
        if (model.sensors().isEmpty()) {
            state.status.set("Add sensors first: the hub is placed to serve them.");
            return;
        }
        if (optimising.get()) {
            return;
        }
        Environment env = model.environment().copy();
        var sensors = model.sensors().stream().map(Sensor::copy).toList();
        SimulationSettings settings = model.receiverSettings();
        Area area = model.footprint();
        HubPlacementOptimizer.Candidate before = model.hub()
                .map(hub -> HubPlacementOptimizer.evaluate(env, sensors, settings, hub.getX(), hub.getY()))
                .orElse(null);

        Task<HubPlacementOptimizer.Result> task = new Task<>() {
            @Override
            protected HubPlacementOptimizer.Result call() {
                Task<HubPlacementOptimizer.Result> self = this;
                return HubPlacementOptimizer.optimize(env, sensors, settings, area, new ComputationMonitor() {
                    @Override
                    public void progress(long done, long total) {
                        updateProgress(done, total);
                    }

                    @Override
                    public boolean isCancelled() {
                        return self.isCancelled();
                    }
                });
            }
        };
        task.setOnSucceeded(e -> {
            optimising.set(false);
            applyOptimisation(task.getValue(), before);
        });
        task.setOnFailed(e -> {
            optimising.set(false);
            LOG.log(Level.SEVERE, "Hub optimisation failed", task.getException());
            state.status.set("Hub optimisation failed: " + task.getException().getMessage());
        });
        optimising.set(true);
        state.status.set("Searching the building for the hub position that best serves every sensor...");
        optimiserExecutor.execute(task);
    }

    private void applyOptimisation(HubPlacementOptimizer.Result result, HubPlacementOptimizer.Candidate before) {
        HubPlacementOptimizer.Candidate best = result.best();
        Hub hub = model.placeHub(best.x(), best.y());
        state.selectedDevice.set(hub);
        view.flashHub();

        String weakest = model.sensors().stream().filter(s -> s.getId().equals(best.weakestSensorId()))
                .map(Sensor::getName).findFirst().orElse(best.weakestSensorId());
        String summary = String.format(Locale.US, "Best of %d positions: (%d, %d).%nNow: %s.%nWeakest: %s.",
                result.candidates().size(), best.x(), best.y(), describe(best), weakest);
        if (before != null) {
            summary += String.format(Locale.US, "%nBefore: %s.", describe(before));
        }
        state.optimisation.set(summary);
        state.status.set(String.format(Locale.US, "Hub moved to (%d, %d): %s%s.", best.x(), best.y(),
                before == null ? "" : describe(before) + " → ", describe(best)));
    }

    private static String describe(HubPlacementOptimizer.Candidate candidate) {
        String margin = Double.isInfinite(candidate.worstMarginDb())
                ? "no sensor reaches the hub"
                : String.format(Locale.US, "weakest margin %.1f dB", candidate.worstMarginDb());
        return candidate.unreachableSensors() == 0
                ? margin
                : String.format(Locale.US, "%d sensor(s) out of reach, %s", candidate.unreachableSensors(), margin);
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

        // A selected sensor gets a finer fan of its own; otherwise every sensor is traced.
        RayTracer.Settings defaults = RayTracer.Settings.defaults();
        List<Sensor> sources = model.sensors();
        RayTracer.Settings settings = defaults;
        if (state.selectedDevice.get() instanceof Sensor selected) {
            sources = List.of(selected);
            settings = new RayTracer.Settings(5.0, defaults.maxInteractions(), defaults.maxPathLengthMeters(),
                    defaults.powerFloorDbm(), defaults.hubCaptureRadiusMeters(), defaults.maxSegments());
        }
        RayTraceResult result = RayTracer.trace(model.environment(), sources, model.hub().orElse(null),
                SimulatorModel.PLAN_WIDTH_METERS, SimulatorModel.PLAN_HEIGHT_METERS, settings);
        hubHitCount = 0;
        state.status.set(String.format(Locale.US, "Tracing %d ray segments from %s%s.", result.segments().size(),
                sources.size() == 1 ? sources.getFirst().getName() : "every sensor (select one to trace it alone)",
                model.hub().isPresent() ? "" : "; place a hub to measure arrivals"));
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

    void stopAnimations() {
        rays.clear();
        waves.clear();
    }

    void openSummary() {
        new SummaryWindow(model).show(owner);
    }

    void shutdown() {
        stopAnimations();
        heatmaps.shutdown();
        optimiserExecutor.shutdownNow();
    }
}
