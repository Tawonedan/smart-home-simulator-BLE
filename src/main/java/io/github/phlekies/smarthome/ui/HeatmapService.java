package io.github.phlekies.smarthome.ui;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.concurrent.Task;

import io.github.phlekies.smarthome.app.SimulatorModel;
import io.github.phlekies.smarthome.simulation.ComputationMonitor;
import io.github.phlekies.smarthome.simulation.HeatmapResult;
import io.github.phlekies.smarthome.simulation.PropagationEngine;

/**
 * Computes coverage heatmaps off the JavaFX thread.
 *
 * <p>Only the latest request matters: a new request cancels the one in flight, so dragging
 * or typing never queues up stale work and the UI stays responsive.
 */
final class HeatmapService {

    private static final Logger LOG = Logger.getLogger(HeatmapService.class.getName());

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "heatmap-worker");
        thread.setDaemon(true);
        return thread;
    });

    private final ReadOnlyObjectWrapper<HeatmapResult> latest = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyBooleanWrapper running = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyDoubleWrapper progress = new ReadOnlyDoubleWrapper(0.0);
    private Task<HeatmapResult> current;

    /** Starts computing the heatmap for a snapshot of the model. Must be called on the FX thread. */
    void request(SimulatorModel.Snapshot snapshot) {
        cancelCurrent();

        Task<HeatmapResult> task = new Task<>() {
            @Override
            protected HeatmapResult call() {
                Task<HeatmapResult> self = this;
                return PropagationEngine.computeHeatmap(snapshot.environment(), snapshot.sensors(),
                        SimulatorModel.PLAN_WIDTH_METERS, SimulatorModel.PLAN_HEIGHT_METERS, snapshot.settings(),
                        new ComputationMonitor() {
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
        task.setOnSucceeded(e -> finish(task, task.getValue()));
        task.setOnFailed(e -> {
            LOG.log(Level.SEVERE, "Heatmap computation failed", task.getException());
            finish(task, null);
        });

        current = task;
        running.set(true);
        progress.bind(task.progressProperty());
        executor.execute(task);
    }

    /** Cancels any computation and forgets the last result. */
    void clear() {
        cancelCurrent();
        running.set(false);
        latest.set(null);
    }

    private void cancelCurrent() {
        progress.unbind();
        if (current != null) {
            current.cancel(false);
            current = null;
        }
    }

    private void finish(Task<HeatmapResult> task, HeatmapResult result) {
        if (task != current) {
            return; // superseded by a newer request
        }
        current = null;
        progress.unbind();
        progress.set(1.0);
        running.set(false);
        if (result != null) {
            latest.set(result);
        }
    }

    ReadOnlyObjectProperty<HeatmapResult> latestProperty() {
        return latest.getReadOnlyProperty();
    }

    ReadOnlyBooleanProperty runningProperty() {
        return running.getReadOnlyProperty();
    }

    ReadOnlyDoubleProperty progressProperty() {
        return progress.getReadOnlyProperty();
    }

    void shutdown() {
        cancelCurrent();
        executor.shutdownNow();
    }
}
