package io.github.phlekies.smarthome.ui;

import static io.github.phlekies.smarthome.ui.PlanCoordinates.PIXELS_PER_METER;
import static io.github.phlekies.smarthome.ui.PlanCoordinates.toPixelX;
import static io.github.phlekies.smarthome.ui.PlanCoordinates.toPixelY;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import javafx.animation.AnimationTimer;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import io.github.phlekies.smarthome.model.Hub;
import io.github.phlekies.smarthome.model.Sensor;

/** Concentric wavefronts expanding from every sensor; reports when each one reaches the hub. */
final class WaveAnimator {

    private static final double SPEED_M_PER_S = 12.0;
    private static final double MAX_RADIUS_M = 60.0;
    private static final int RINGS_PER_SENSOR = 3;
    private static final double RING_INTERVAL_S = 0.35;
    private static final Color WAVE_COLOR = Color.web("#1e88e5");

    private final PlanView view;
    private final List<Circle> rings = new ArrayList<>();
    private AnimationTimer timer;

    WaveAnimator(PlanView view) {
        this.view = view;
    }

    /**
     * @param hub       optional receiver
     * @param onArrival called once per sensor when its first wavefront reaches the hub
     */
    void play(List<Sensor> sensors, Hub hub, Consumer<Sensor> onArrival) {
        clear();
        for (Sensor sensor : sensors) {
            for (int i = 0; i < RINGS_PER_SENSOR; i++) {
                Circle ring = new Circle(toPixelX(sensor.getX()), toPixelY(sensor.getY()), 0, Color.TRANSPARENT);
                ring.setStroke(WAVE_COLOR);
                ring.setStrokeWidth(2.0);
                ring.setVisible(false);
                rings.add(ring);
            }
        }
        view.effectsLayer().getChildren().addAll(rings);

        Set<Sensor> arrived = new HashSet<>();
        timer = new AnimationTimer() {
            private long startNanos = -1;

            @Override
            public void handle(long now) {
                if (startNanos < 0) {
                    startNanos = now;
                }
                double elapsed = (now - startNanos) / 1e9;
                boolean alive = false;
                for (int s = 0; s < sensors.size(); s++) {
                    for (int i = 0; i < RINGS_PER_SENSOR; i++) {
                        double radiusMeters = (elapsed - i * RING_INTERVAL_S) * SPEED_M_PER_S;
                        Circle ring = rings.get(s * RINGS_PER_SENSOR + i);
                        boolean visible = radiusMeters > 0 && radiusMeters < MAX_RADIUS_M;
                        ring.setVisible(visible);
                        if (visible) {
                            ring.setRadius(radiusMeters * PIXELS_PER_METER);
                            ring.setOpacity(0.8 * (1.0 - radiusMeters / MAX_RADIUS_M));
                        }
                        alive |= radiusMeters < MAX_RADIUS_M;
                    }
                    Sensor sensor = sensors.get(s);
                    if (hub != null && !arrived.contains(sensor)
                            && elapsed * SPEED_M_PER_S >= sensor.distanceTo(hub)) {
                        arrived.add(sensor);
                        onArrival.accept(sensor);
                    }
                }
                if (!alive) {
                    clear();
                }
            }
        };
        timer.start();
    }

    void clear() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
        view.effectsLayer().getChildren().removeAll(rings);
        rings.clear();
    }
}
